"""
Algo Trading Engine - Main orchestrator for automated trading
"""
import asyncio
import logging
from typing import Optional, Dict, Any, List, Tuple
from datetime import datetime, date
from dataclasses import dataclass
import uuid

from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, and_

from app.models.algo_trading import (
    AlgoConfig, AlgoTrade, AlgoPosition, AlgoLog, AlgoDailyStats,
    AlgoOrderStatus, AlgoPositionStatus, AlgoOptionType, SignalType, ExitReason
)
from app.services.upstox_service import upstox_service
from app.services.upstox_order_service import order_service, TransactionType, OrderType
from app.services.risk_manager import risk_manager, RiskCheckResult
from app.database import AsyncSessionLocal

logger = logging.getLogger(__name__)


@dataclass
class Signal:
    """Trading signal from market analysis"""
    symbol: str
    signal_type: SignalType
    signal_score: float
    pcr: float
    max_pain: float
    vix: Optional[float]
    spot_price: float
    atm_strike: float
    recommended_option_type: AlgoOptionType
    recommended_strike: float
    recommended_expiry: str
    timestamp: datetime


class AlgoTradingEngine:
    """
    Main algo trading engine that:
    1. Monitors market data
    2. Generates trading signals using AI insights logic
    3. Executes trades based on signals and risk management
    4. Monitors positions and triggers exits
    """

    def __init__(self):
        self._is_running: bool = False
        self._config: Optional[AlgoConfig] = None
        self._config_id: Optional[str] = None
        self._monitor_task: Optional[asyncio.Task] = None
        self._last_signal: Optional[Signal] = None
        self._open_positions: List[AlgoPosition] = []

        # Monitor settings
        self._signal_check_interval: int = 30  # Check for signals every 30 seconds
        self._position_monitor_interval: int = 5  # Monitor positions every 5 seconds

    @property
    def is_running(self) -> bool:
        return self._is_running

    @property
    def config(self) -> Optional[AlgoConfig]:
        return self._config

    @property
    def last_signal(self) -> Optional[Signal]:
        return self._last_signal

    # ==================== Engine Lifecycle ====================

    async def start(
        self,
        config_id: Optional[str] = None,
        paper_mode: bool = True
    ) -> Tuple[bool, str]:
        """Start the algo trading engine"""
        if self._is_running:
            return False, "Engine is already running"

        try:
            # Load or create config
            async with AsyncSessionLocal() as db:
                if config_id:
                    result = await db.execute(
                        select(AlgoConfig).where(AlgoConfig.id == config_id)
                    )
                    self._config = result.scalar_one_or_none()
                    if not self._config:
                        return False, f"Config {config_id} not found"
                else:
                    # Get first active config or create default
                    result = await db.execute(
                        select(AlgoConfig).where(AlgoConfig.is_active == True)
                    )
                    self._config = result.scalar_one_or_none()

                    if not self._config:
                        self._config = AlgoConfig(
                            name="Default Strategy",
                            is_active=True,
                            is_paper_mode=paper_mode
                        )
                        db.add(self._config)
                        await db.commit()
                        await db.refresh(self._config)

                self._config_id = self._config.id
                self._config.is_active = True
                self._config.is_paper_mode = paper_mode
                await db.commit()

                # Load open positions
                await self._load_open_positions(db)

                # Log start
                await self._log(
                    db, "INFO", "SYSTEM",
                    f"Algo engine started in {'PAPER' if paper_mode else 'LIVE'} mode",
                    {"config_id": self._config_id}
                )

            # Reset daily stats
            risk_manager.reset_daily_stats(self._config)

            # Start monitoring task
            self._is_running = True
            self._monitor_task = asyncio.create_task(self._run_monitor_loop())

            logger.info(f"Algo engine started - Config: {self._config_id}")
            return True, "Engine started successfully"

        except Exception as e:
            logger.error(f"Failed to start engine: {e}")
            return False, str(e)

    async def stop(
        self,
        close_all_positions: bool = False,
        reason: str = "Manual stop"
    ) -> Tuple[bool, str]:
        """Stop the algo trading engine"""
        if not self._is_running:
            return False, "Engine is not running"

        try:
            self._is_running = False

            # Cancel monitor task
            if self._monitor_task:
                self._monitor_task.cancel()
                try:
                    await self._monitor_task
                except asyncio.CancelledError:
                    pass

            # Close all positions if requested
            if close_all_positions:
                async with AsyncSessionLocal() as db:
                    for position in self._open_positions:
                        if position.status == AlgoPositionStatus.OPEN:
                            await self._close_position(
                                db, position, ExitReason.KILL_SWITCH, reason
                            )
                    await db.commit()

            # Update config
            async with AsyncSessionLocal() as db:
                if self._config_id:
                    result = await db.execute(
                        select(AlgoConfig).where(AlgoConfig.id == self._config_id)
                    )
                    config = result.scalar_one_or_none()
                    if config:
                        config.is_active = False
                        await db.commit()

                await self._log(
                    db, "INFO", "SYSTEM",
                    f"Algo engine stopped: {reason}",
                    {"close_all": close_all_positions}
                )

            self._config = None
            self._config_id = None
            self._open_positions = []

            logger.info(f"Algo engine stopped: {reason}")
            return True, "Engine stopped successfully"

        except Exception as e:
            logger.error(f"Error stopping engine: {e}")
            return False, str(e)

    # ==================== Main Monitor Loop ====================

    async def _run_monitor_loop(self) -> None:
        """Main monitoring loop"""
        signal_counter = 0

        while self._is_running:
            try:
                async with AsyncSessionLocal() as db:
                    # Reload config
                    if self._config_id:
                        result = await db.execute(
                            select(AlgoConfig).where(AlgoConfig.id == self._config_id)
                        )
                        self._config = result.scalar_one_or_none()

                    if not self._config:
                        logger.error("Config not found, stopping engine")
                        await self.stop(reason="Config not found")
                        return

                    # Reset daily stats if new day
                    risk_manager.reset_daily_stats(self._config)

                    # Check if within market hours
                    market_check = risk_manager.check_market_hours(self._config)
                    if not market_check.passed:
                        logger.debug(f"Outside market hours: {market_check.reason}")
                        await asyncio.sleep(60)  # Sleep longer outside market hours
                        continue

                    # Monitor existing positions (every iteration)
                    await self._monitor_positions(db)

                    # Check for new signals (every signal_check_interval)
                    signal_counter += self._position_monitor_interval
                    if signal_counter >= self._signal_check_interval:
                        signal_counter = 0
                        await self._check_and_execute_signals(db)

                    await db.commit()

            except Exception as e:
                logger.error(f"Error in monitor loop: {e}")
                import traceback
                traceback.print_exc()

            await asyncio.sleep(self._position_monitor_interval)

    # ==================== Signal Generation ====================

    async def _generate_signal(self, symbol: str) -> Optional[Signal]:
        """
        Generate trading signal using AI insights logic.

        Market Bullish Score = PCR Factor + Max Pain Factor + VIX Factor

        Thresholds:
        - STRONG_BUY  > 0.35  → Buy CE (Call Option)
        - BUY         > 0.15  → Buy CE with smaller position
        - HOLD        [-0.15, 0.15] → No action
        - SELL        < -0.15 → Buy PE (Put Option)
        - STRONG_SELL < -0.35 → Buy PE with larger position
        """
        try:
            # Get market data
            spot_data = await upstox_service.get_spot_price(symbol)
            spot_price = spot_data.get("lastPrice", 0) or spot_data.get("price", 0)

            if not spot_price:
                logger.warning(f"No spot price available for {symbol}")
                return None

            # Get expiry dates
            expiries = await upstox_service.get_expiry_dates(symbol)
            if not expiries:
                logger.warning(f"No expiries available for {symbol}")
                return None

            # Use nearest expiry (weekly)
            nearest_expiry = expiries[0]

            # Get option chain for analysis
            chain_data = await upstox_service.get_option_chain(
                symbol, nearest_expiry, strikes_around_atm=10
            )

            if not chain_data or not chain_data.get("data"):
                logger.warning(f"No option chain data for {symbol}")
                return None

            # Calculate PCR (Put-Call Ratio)
            total_call_oi = 0
            total_put_oi = 0

            for row in chain_data["data"]:
                if row.get("CE"):
                    total_call_oi += row["CE"].get("openInterest", 0)
                if row.get("PE"):
                    total_put_oi += row["PE"].get("openInterest", 0)

            pcr = total_put_oi / total_call_oi if total_call_oi > 0 else 1.0

            # Calculate Max Pain
            max_pain = self._calculate_max_pain(chain_data["data"], spot_price)

            # Get VIX if available
            vix = None
            try:
                vix_data = await upstox_service.get_spot_price("INDIAVIX")
                vix = vix_data.get("lastPrice", 0) or vix_data.get("price", 0)
            except:
                pass

            # Calculate bullish score
            score = self._calculate_bullish_score(pcr, max_pain, spot_price, vix)

            # Determine signal type
            if score > 0.35:
                signal_type = SignalType.STRONG_BUY
                option_type = AlgoOptionType.CE
            elif score > 0.15:
                signal_type = SignalType.BUY
                option_type = AlgoOptionType.CE
            elif score < -0.35:
                signal_type = SignalType.STRONG_SELL
                option_type = AlgoOptionType.PE
            elif score < -0.15:
                signal_type = SignalType.SELL
                option_type = AlgoOptionType.PE
            else:
                signal_type = SignalType.HOLD
                option_type = AlgoOptionType.CE  # Default, won't be used

            # Calculate ATM strike
            strike_interval = {
                "NIFTY": 50, "BANKNIFTY": 100, "FINNIFTY": 50,
                "MIDCPNIFTY": 25, "SENSEX": 100
            }.get(symbol, 50)
            atm_strike = round(spot_price / strike_interval) * strike_interval

            signal = Signal(
                symbol=symbol,
                signal_type=signal_type,
                signal_score=score,
                pcr=pcr,
                max_pain=max_pain,
                vix=vix,
                spot_price=spot_price,
                atm_strike=atm_strike,
                recommended_option_type=option_type,
                recommended_strike=atm_strike,
                recommended_expiry=nearest_expiry,
                timestamp=datetime.utcnow()
            )

            self._last_signal = signal
            logger.info(
                f"Signal generated for {symbol}: {signal_type.value} "
                f"(score: {score:.2f}, PCR: {pcr:.2f})"
            )

            return signal

        except Exception as e:
            logger.error(f"Error generating signal for {symbol}: {e}")
            return None

    def _calculate_max_pain(
        self,
        chain_data: List[Dict],
        spot_price: float
    ) -> float:
        """Calculate Max Pain strike"""
        strike_losses = {}

        for row in chain_data:
            strike = row.get("strikePrice", 0)
            call_oi = row.get("CE", {}).get("openInterest", 0) if row.get("CE") else 0
            put_oi = row.get("PE", {}).get("openInterest", 0) if row.get("PE") else 0

            total_loss = 0
            for other_row in chain_data:
                other_strike = other_row.get("strikePrice", 0)
                other_call_oi = other_row.get("CE", {}).get("openInterest", 0) if other_row.get("CE") else 0
                other_put_oi = other_row.get("PE", {}).get("openInterest", 0) if other_row.get("PE") else 0

                # Call losses (strike > settlement)
                if strike > other_strike:
                    total_loss += other_call_oi * (strike - other_strike)

                # Put losses (strike < settlement)
                if strike < other_strike:
                    total_loss += other_put_oi * (other_strike - strike)

            strike_losses[strike] = total_loss

        if not strike_losses:
            return spot_price

        # Max pain is the strike with minimum total loss
        max_pain_strike = min(strike_losses, key=strike_losses.get)
        return max_pain_strike

    def _calculate_bullish_score(
        self,
        pcr: float,
        max_pain: float,
        spot_price: float,
        vix: Optional[float]
    ) -> float:
        """
        Calculate bullish score from market indicators.

        PCR Factor: PCR > 1.2 is bullish (contrarian), PCR < 0.8 is bearish
        Max Pain Factor: Spot below max pain is bullish, above is bearish
        VIX Factor: Low VIX is bullish, high VIX is bearish
        """
        score = 0.0

        # PCR Factor (-0.3 to +0.3)
        if pcr > 1.2:
            score += 0.3 * min((pcr - 1.2) / 0.5, 1)  # Bullish (too many puts)
        elif pcr < 0.8:
            score -= 0.3 * min((0.8 - pcr) / 0.3, 1)  # Bearish (too many calls)

        # Max Pain Factor (-0.3 to +0.3)
        max_pain_distance = (max_pain - spot_price) / spot_price
        if max_pain_distance > 0:
            score += 0.3 * min(max_pain_distance / 0.02, 1)  # Bullish (spot below max pain)
        else:
            score -= 0.3 * min(abs(max_pain_distance) / 0.02, 1)  # Bearish

        # VIX Factor (-0.2 to +0.2)
        if vix:
            if vix < 13:
                score += 0.2  # Low VIX = Bullish
            elif vix > 20:
                score -= 0.2  # High VIX = Bearish
            else:
                score += 0.1 * (1 - (vix - 13) / 7)  # Gradual

        return round(score, 3)

    # ==================== Trade Execution ====================

    async def _check_and_execute_signals(self, db: AsyncSession) -> None:
        """Check for signals and execute trades"""
        if not self._config:
            return

        for symbol in self._config.indices:
            try:
                signal = await self._generate_signal(symbol)

                if not signal:
                    continue

                # Only act on strong signals if configured
                if self._config.signal_threshold == "STRONG":
                    if signal.signal_type not in [SignalType.STRONG_BUY, SignalType.STRONG_SELL]:
                        logger.debug(f"Signal {signal.signal_type.value} not strong enough")
                        continue

                if signal.signal_type == SignalType.HOLD:
                    continue

                # Perform risk checks
                all_passed, risk_results = risk_manager.perform_all_checks(
                    self._config,
                    self._open_positions,
                    symbol,
                    signal.signal_type,
                    signal.signal_score
                )

                if not all_passed:
                    failed_checks = [r for r in risk_results if not r.passed]
                    await self._log(
                        db, "WARNING", "RISK",
                        f"Risk checks failed for {symbol}: {[r.reason for r in failed_checks]}",
                        {"signal": signal.signal_type.value, "checks": [r.reason for r in failed_checks]}
                    )
                    continue

                # Execute trade
                await self._execute_entry(db, signal)

            except Exception as e:
                logger.error(f"Error processing signal for {symbol}: {e}")
                await self._log(db, "ERROR", "SIGNAL", f"Signal error for {symbol}: {e}")

    async def _execute_entry(self, db: AsyncSession, signal: Signal) -> bool:
        """Execute entry trade based on signal"""
        try:
            # Get option details
            chain_data = await upstox_service.get_option_chain(
                signal.symbol, signal.recommended_expiry, strikes_around_atm=5
            )

            if not chain_data or not chain_data.get("data"):
                logger.error(f"No chain data for entry: {signal.symbol}")
                return False

            # Find the option at recommended strike
            option_data = None
            for row in chain_data["data"]:
                if row.get("strikePrice") == signal.recommended_strike:
                    opt_key = signal.recommended_option_type.value
                    if row.get(opt_key):
                        option_data = row[opt_key]
                    break

            if not option_data:
                logger.error(
                    f"Option not found: {signal.symbol} "
                    f"{signal.recommended_strike} {signal.recommended_option_type.value}"
                )
                return False

            entry_price = option_data.get("lastPrice", 0)
            if not entry_price:
                logger.error("No price available for option")
                return False

            instrument_key = option_data.get("instrumentKey")
            trading_symbol = option_data.get("tradingSymbol")
            lot_size = chain_data.get("lotSize", 50)

            # Calculate position size
            position_size = risk_manager.calculate_position_size(
                self._config,
                signal.signal_type,
                signal.signal_score,
                entry_price,
                lot_size
            )

            # Calculate stops
            stop_loss = risk_manager.calculate_stop_loss(entry_price, self._config)
            target = risk_manager.calculate_target(entry_price, self._config)

            # Place order
            quantity = position_size.lots * lot_size
            order_result = await order_service.place_order(
                instrument_key=instrument_key,
                quantity=quantity,
                transaction_type=TransactionType.BUY,
                order_type=OrderType.MARKET,
                is_paper=self._config.is_paper_mode
            )

            if not order_result.success:
                await self._log(
                    db, "ERROR", "ORDER",
                    f"Order failed: {order_result.message}",
                    {"signal": signal.__dict__}
                )
                return False

            # Create position
            position = AlgoPosition(
                id=str(uuid.uuid4()),
                config_id=self._config_id,
                symbol=signal.symbol,
                strike_price=signal.recommended_strike,
                option_type=signal.recommended_option_type,
                expiry_date=signal.recommended_expiry,
                quantity=position_size.lots,
                lot_size=lot_size,
                entry_price=entry_price,
                current_price=entry_price,
                highest_price=entry_price,
                stop_loss_price=stop_loss,
                target_price=target,
                trailing_stop_active=False,
                status=AlgoPositionStatus.OPEN,
                entry_signal=signal.signal_type,
                entry_signal_score=signal.signal_score,
                instrument_key=instrument_key,
                trading_symbol=trading_symbol,
                is_paper=self._config.is_paper_mode,
                opened_at=datetime.utcnow()
            )
            db.add(position)

            # Create trade record
            trade = AlgoTrade(
                id=str(uuid.uuid4()),
                position_id=position.id,
                config_id=self._config_id,
                symbol=signal.symbol,
                strike_price=signal.recommended_strike,
                option_type=signal.recommended_option_type,
                expiry_date=signal.recommended_expiry,
                order_id=order_result.order_id,
                order_status=AlgoOrderStatus.EXECUTED,
                trade_type="entry",
                quantity=position_size.lots,
                lot_size=lot_size,
                order_price=entry_price,
                executed_price=entry_price,
                signal_type=signal.signal_type,
                signal_score=signal.signal_score,
                instrument_key=instrument_key,
                trading_symbol=trading_symbol,
                is_paper=self._config.is_paper_mode,
                executed_at=datetime.utcnow()
            )
            db.add(trade)

            # Add to open positions
            self._open_positions.append(position)

            await self._log(
                db, "INFO", "ORDER",
                f"Entry executed: {signal.symbol} {signal.recommended_strike} "
                f"{signal.recommended_option_type.value} x{position_size.lots} lots @ ₹{entry_price:.2f}",
                {
                    "position_id": position.id,
                    "order_id": order_result.order_id,
                    "stop_loss": stop_loss,
                    "target": target
                }
            )

            logger.info(
                f"Position opened: {position.symbol} {position.strike_price} "
                f"{position.option_type.value} - SL: {stop_loss}, Target: {target}"
            )

            return True

        except Exception as e:
            logger.error(f"Entry execution error: {e}")
            import traceback
            traceback.print_exc()
            await self._log(db, "ERROR", "ORDER", f"Entry error: {e}")
            return False

    # ==================== Position Monitoring ====================

    async def _monitor_positions(self, db: AsyncSession) -> None:
        """Monitor all open positions for exit conditions"""
        if not self._config:
            return

        for position in list(self._open_positions):
            if position.status != AlgoPositionStatus.OPEN:
                continue

            try:
                # Get current price
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
                    logger.warning(f"Could not get current price for position {position.id}")
                    continue

                # Update position price
                position.current_price = current_price

                # Check exit conditions
                should_exit, exit_reason = risk_manager.update_position_stops(
                    position, current_price, self._config
                )

                # Check time-based exit
                if not should_exit:
                    should_exit, exit_reason = self._check_time_exit(position)

                if should_exit and exit_reason:
                    await self._close_position(
                        db, position, ExitReason(exit_reason),
                        f"Auto exit: {exit_reason}"
                    )

            except Exception as e:
                logger.error(f"Error monitoring position {position.id}: {e}")

    def _check_time_exit(self, position: AlgoPosition) -> Tuple[bool, Optional[str]]:
        """Check if position should be closed due to time"""
        import pytz

        ist = pytz.timezone('Asia/Kolkata')
        now = datetime.now(ist)

        # Close all positions by market end time
        if self._config:
            end_hour, end_min = map(int, self._config.market_end_time.split(":"))
            market_end = now.replace(hour=end_hour, minute=end_min, second=0, microsecond=0)

            if now >= market_end:
                return True, "time_exit"

        return False, None

    async def _close_position(
        self,
        db: AsyncSession,
        position: AlgoPosition,
        exit_reason: ExitReason,
        message: str
    ) -> bool:
        """Close a position"""
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
                await self._log(
                    db, "ERROR", "ORDER",
                    f"Exit order failed: {order_result.message}",
                    {"position_id": position.id}
                )
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

            # Create exit trade record
            trade = AlgoTrade(
                id=str(uuid.uuid4()),
                position_id=position.id,
                config_id=self._config_id,
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

            # Update risk manager
            risk_manager.update_daily_pnl(pnl)

            # Remove from open positions
            self._open_positions = [
                p for p in self._open_positions if p.id != position.id
            ]

            await self._log(
                db, "INFO", "ORDER",
                f"Position closed: {position.symbol} {position.strike_price} "
                f"{position.option_type.value} - P&L: ₹{pnl:.2f} ({pnl_percent:.1f}%) - {exit_reason.value}",
                {
                    "position_id": position.id,
                    "pnl": pnl,
                    "exit_reason": exit_reason.value
                }
            )

            logger.info(
                f"Position closed: {position.id} - P&L: ₹{pnl:.2f} ({pnl_percent:.1f}%)"
            )

            return True

        except Exception as e:
            logger.error(f"Error closing position {position.id}: {e}")
            await self._log(db, "ERROR", "ORDER", f"Close error: {e}")
            return False

    # ==================== Helper Methods ====================

    async def _load_open_positions(self, db: AsyncSession) -> None:
        """Load open positions from database"""
        result = await db.execute(
            select(AlgoPosition).where(
                and_(
                    AlgoPosition.status == AlgoPositionStatus.OPEN,
                    AlgoPosition.config_id == self._config_id
                )
            )
        )
        self._open_positions = list(result.scalars().all())
        logger.info(f"Loaded {len(self._open_positions)} open positions")

    async def _log(
        self,
        db: AsyncSession,
        level: str,
        category: str,
        message: str,
        context: Optional[Dict] = None
    ) -> None:
        """Log an event to the database"""
        log_entry = AlgoLog(
            id=str(uuid.uuid4()),
            level=level,
            category=category,
            message=message,
            config_id=self._config_id,
            context=context
        )
        db.add(log_entry)

    # ==================== Status Methods ====================

    async def get_status(self) -> Dict[str, Any]:
        """Get current engine status"""
        import pytz

        ist = pytz.timezone('Asia/Kolkata')
        now = datetime.now(ist)

        daily_stats = risk_manager.get_daily_stats()

        # Calculate totals
        total_invested = sum(
            p.invested_amount for p in self._open_positions
            if p.status == AlgoPositionStatus.OPEN
        )
        total_unrealized_pnl = sum(
            p.unrealized_pnl for p in self._open_positions
            if p.status == AlgoPositionStatus.OPEN
        )

        return {
            "is_running": self._is_running,
            "is_paper_mode": self._config.is_paper_mode if self._config else True,
            "config_id": self._config_id,
            "is_market_hours": risk_manager.check_market_hours(self._config).passed if self._config else False,
            "current_time": now.strftime("%H:%M:%S"),
            "market_start": self._config.market_start_time if self._config else "09:15",
            "market_end": self._config.market_end_time if self._config else "15:30",
            "open_positions": len([p for p in self._open_positions if p.status == AlgoPositionStatus.OPEN]),
            "max_positions": self._config.max_positions if self._config else 2,
            "total_invested": total_invested,
            "total_unrealized_pnl": total_unrealized_pnl,
            "daily_trades": daily_stats["daily_trades"],
            "daily_pnl": daily_stats["daily_pnl"],
            "daily_pnl_percent": (daily_stats["daily_pnl"] / self._config.capital * 100) if self._config and self._config.capital else 0,
            "daily_loss_limit_hit": risk_manager.is_daily_loss_limit_hit(self._config) if self._config else False,
            "last_signal": {
                "symbol": self._last_signal.symbol,
                "signal_type": self._last_signal.signal_type.value,
                "signal_score": self._last_signal.signal_score,
                "timestamp": self._last_signal.timestamp.isoformat()
            } if self._last_signal else None,
            "available_capital": (self._config.capital - total_invested) if self._config else 0,
            "total_capital": self._config.capital if self._config else 0
        }

    async def get_open_positions(self) -> List[Dict[str, Any]]:
        """Get all open positions"""
        return [
            {
                "id": p.id,
                "symbol": p.symbol,
                "strike_price": p.strike_price,
                "option_type": p.option_type.value,
                "expiry_date": p.expiry_date,
                "quantity": p.quantity,
                "lot_size": p.lot_size,
                "entry_price": p.entry_price,
                "current_price": p.current_price,
                "stop_loss_price": p.stop_loss_price,
                "target_price": p.target_price,
                "trailing_stop_active": p.trailing_stop_active,
                "trailing_stop_price": p.trailing_stop_price,
                "unrealized_pnl": p.unrealized_pnl,
                "unrealized_pnl_percent": p.unrealized_pnl_percent,
                "is_paper": p.is_paper,
                "opened_at": p.opened_at.isoformat() if p.opened_at else None
            }
            for p in self._open_positions
            if p.status == AlgoPositionStatus.OPEN
        ]


# Singleton instance
algo_engine = AlgoTradingEngine()
