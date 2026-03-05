"""
Position Monitor Worker - Background task for monitoring algo positions

This module provides a standalone position monitor that can run independently
of the main algo engine. It's useful for:
1. Redundant monitoring
2. Running as a separate process
3. Integration with Celery if needed later
"""
import asyncio
import logging
from typing import Optional, List, Dict, Any
from datetime import datetime
import uuid

from sqlalchemy import select, and_
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import AsyncSessionLocal
from app.models.algo_trading import (
    AlgoConfig, AlgoPosition, AlgoTrade, AlgoLog,
    AlgoPositionStatus, AlgoOrderStatus, ExitReason
)
from app.services.upstox_service import upstox_service
from app.services.upstox_order_service import order_service, TransactionType, OrderType
from app.services.risk_manager import risk_manager

logger = logging.getLogger(__name__)


class PositionMonitorWorker:
    """
    Background worker that monitors all open algo positions.

    Features:
    - Checks positions every 5 seconds
    - Triggers stop-loss and target exits
    - Updates trailing stops
    - Time-based exits at market close
    - Independent of main algo engine
    """

    def __init__(self):
        self._is_running: bool = False
        self._monitor_task: Optional[asyncio.Task] = None
        self._check_interval: int = 5  # Seconds

    @property
    def is_running(self) -> bool:
        return self._is_running

    async def start(self) -> None:
        """Start the position monitor worker"""
        if self._is_running:
            logger.warning("Position monitor is already running")
            return

        self._is_running = True
        self._monitor_task = asyncio.create_task(self._run_monitor_loop())
        logger.info("Position monitor worker started")

    async def stop(self) -> None:
        """Stop the position monitor worker"""
        if not self._is_running:
            return

        self._is_running = False
        if self._monitor_task:
            self._monitor_task.cancel()
            try:
                await self._monitor_task
            except asyncio.CancelledError:
                pass

        logger.info("Position monitor worker stopped")

    async def _run_monitor_loop(self) -> None:
        """Main monitoring loop"""
        while self._is_running:
            try:
                await self._check_all_positions()
            except Exception as e:
                logger.error(f"Error in position monitor loop: {e}")
                import traceback
                traceback.print_exc()

            await asyncio.sleep(self._check_interval)

    async def _check_all_positions(self) -> None:
        """Check all open positions across all configs"""
        async with AsyncSessionLocal() as db:
            # Get all open positions
            result = await db.execute(
                select(AlgoPosition).where(
                    AlgoPosition.status == AlgoPositionStatus.OPEN
                )
            )
            positions = list(result.scalars().all())

            if not positions:
                return

            # Group positions by config
            config_ids = set(p.config_id for p in positions if p.config_id)

            # Load configs
            configs: Dict[str, AlgoConfig] = {}
            for config_id in config_ids:
                result = await db.execute(
                    select(AlgoConfig).where(AlgoConfig.id == config_id)
                )
                config = result.scalar_one_or_none()
                if config:
                    configs[config_id] = config

            # Check each position
            for position in positions:
                try:
                    config = configs.get(position.config_id)
                    if not config:
                        continue

                    await self._check_position(db, position, config)
                except Exception as e:
                    logger.error(f"Error checking position {position.id}: {e}")

            await db.commit()

    async def _check_position(
        self,
        db: AsyncSession,
        position: AlgoPosition,
        config: AlgoConfig
    ) -> None:
        """Check a single position for exit conditions"""
        try:
            # Get current price from option chain
            chain_data = await upstox_service.get_option_chain(
                position.symbol, position.expiry_date, strikes_around_atm=5
            )

            current_price = None
            if chain_data and chain_data.get("data"):
                for row in chain_data["data"]:
                    if row.get("strikePrice") == position.strike_price:
                        opt_key = position.option_type.value
                        if row.get(opt_key):
                            current_price = row[opt_key].get("lastPrice", 0)
                        break

            if current_price is None:
                logger.warning(f"Could not get price for position {position.id}")
                return

            # Update current price
            position.current_price = current_price

            # Update highest price for trailing stop
            if current_price > (position.highest_price or 0):
                position.highest_price = current_price

            # Check exit conditions
            should_exit = False
            exit_reason: Optional[ExitReason] = None

            # 1. Stop Loss
            if current_price <= position.stop_loss_price:
                should_exit = True
                exit_reason = ExitReason.STOP_LOSS
                logger.info(f"Stop loss triggered for {position.id}")

            # 2. Target
            elif current_price >= position.target_price:
                should_exit = True
                exit_reason = ExitReason.TARGET
                logger.info(f"Target hit for {position.id}")

            # 3. Trailing Stop
            else:
                # Check if trailing stop should be activated
                profit_pct = (current_price - position.entry_price) / position.entry_price
                if profit_pct >= config.trailing_trigger_pct and not position.trailing_stop_active:
                    position.trailing_stop_active = True
                    position.trailing_stop_price = position.highest_price * (1 - config.trailing_stop_pct)
                    logger.info(
                        f"Trailing stop activated for {position.id} at {position.trailing_stop_price:.2f}"
                    )

                # Update trailing stop
                if position.trailing_stop_active:
                    new_trailing = position.highest_price * (1 - config.trailing_stop_pct)
                    if new_trailing > (position.trailing_stop_price or 0):
                        position.trailing_stop_price = new_trailing

                    # Check if trailing stop hit
                    if current_price <= position.trailing_stop_price:
                        should_exit = True
                        exit_reason = ExitReason.TRAILING_STOP
                        logger.info(f"Trailing stop triggered for {position.id}")

            # 4. Time-based exit
            if not should_exit:
                import pytz
                ist = pytz.timezone('Asia/Kolkata')
                now = datetime.now(ist)

                end_hour, end_min = map(int, config.market_end_time.split(":"))
                market_end = now.replace(hour=end_hour, minute=end_min, second=0, microsecond=0)

                if now >= market_end:
                    should_exit = True
                    exit_reason = ExitReason.TIME_EXIT
                    logger.info(f"Time exit for {position.id}")

            # Execute exit if needed
            if should_exit and exit_reason:
                await self._exit_position(db, position, exit_reason)

        except Exception as e:
            logger.error(f"Error checking position {position.id}: {e}")

    async def _exit_position(
        self,
        db: AsyncSession,
        position: AlgoPosition,
        exit_reason: ExitReason
    ) -> bool:
        """Exit a position"""
        try:
            current_price = position.current_price or position.entry_price

            # Place exit order
            quantity = position.quantity * position.lot_size
            order_result = await order_service.place_order(
                instrument_key=position.instrument_key,
                quantity=quantity,
                transaction_type=TransactionType.SELL,
                order_type=OrderType.MARKET,
                is_paper=position.is_paper
            )

            if not order_result.success:
                logger.error(f"Exit order failed: {order_result.message}")
                return False

            # Calculate P&L
            entry_value = position.entry_price * position.quantity * position.lot_size
            exit_value = current_price * position.quantity * position.lot_size
            pnl = exit_value - entry_value
            pnl_percent = (pnl / entry_value) * 100 if entry_value > 0 else 0

            # Update position
            position.status = AlgoPositionStatus(exit_reason.value)
            position.exit_price = current_price
            position.exit_reason = exit_reason
            position.closed_at = datetime.utcnow()

            # Create exit trade
            trade = AlgoTrade(
                id=str(uuid.uuid4()),
                position_id=position.id,
                config_id=position.config_id,
                symbol=position.symbol,
                strike_price=position.strike_price,
                option_type=position.option_type,
                expiry_date=position.expiry_date,
                order_id=order_result.order_id,
                order_status=AlgoOrderStatus.EXECUTED,
                trade_type="exit",
                quantity=position.quantity,
                lot_size=position.lot_size,
                order_price=current_price,
                executed_price=current_price,
                pnl=pnl,
                pnl_percent=pnl_percent,
                exit_reason=exit_reason,
                instrument_key=position.instrument_key,
                trading_symbol=position.trading_symbol,
                is_paper=position.is_paper,
                executed_at=datetime.utcnow()
            )
            db.add(trade)

            # Log
            log_entry = AlgoLog(
                id=str(uuid.uuid4()),
                level="INFO",
                category="POSITION",
                message=f"Position closed: {position.symbol} {position.strike_price} "
                        f"{position.option_type.value} - P&L: ₹{pnl:.2f} ({pnl_percent:.1f}%)",
                config_id=position.config_id,
                position_id=position.id,
                context={
                    "exit_reason": exit_reason.value,
                    "pnl": pnl,
                    "exit_price": current_price
                }
            )
            db.add(log_entry)

            # Update risk manager
            risk_manager.update_daily_pnl(pnl)

            logger.info(
                f"Position {position.id} closed via monitor - "
                f"P&L: ₹{pnl:.2f} ({exit_reason.value})"
            )

            return True

        except Exception as e:
            logger.error(f"Error exiting position {position.id}: {e}")
            return False


# Singleton instance
position_monitor = PositionMonitorWorker()


# Function to run as standalone worker
async def run_position_monitor():
    """Run position monitor as standalone worker"""
    import signal

    monitor = PositionMonitorWorker()

    def shutdown_handler(signum, frame):
        logger.info("Shutdown signal received")
        asyncio.create_task(monitor.stop())

    signal.signal(signal.SIGINT, shutdown_handler)
    signal.signal(signal.SIGTERM, shutdown_handler)

    await monitor.start()

    # Keep running
    while monitor.is_running:
        await asyncio.sleep(1)


if __name__ == "__main__":
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
    )
    asyncio.run(run_position_monitor())
