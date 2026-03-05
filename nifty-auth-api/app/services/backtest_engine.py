"""Backtest engine for running trading strategy simulations."""

import uuid
from datetime import date, datetime, timedelta
from typing import Optional, List, Dict, Any, Callable
from decimal import Decimal
from dataclasses import dataclass, field
from enum import Enum
from sqlalchemy.ext.asyncio import AsyncSession

from app.services.historical_data_service import historical_data_service
from app.services.metrics_service import metrics_service
from app.models.backtest import (
    BacktestRun,
    BacktestTrade,
    BacktestResult,
    BacktestStrategy,
    BacktestStatus,
    HistoricalOption,
)


@dataclass
class Position:
    """Represents an open position in the backtest."""
    id: str
    entry_date: date
    expiry: date
    strike: Decimal
    option_type: str
    action: str  # BUY or SELL
    quantity: int
    entry_price: Decimal
    entry_iv: Optional[Decimal] = None
    entry_delta: Optional[Decimal] = None
    unrealized_pnl: Decimal = Decimal("0")


@dataclass
class BacktestConfig:
    """Configuration for a backtest run."""
    symbol: str
    start_date: date
    end_date: date
    initial_capital: Decimal
    lot_size: int = 25
    max_positions: int = 5
    position_size_pct: Optional[Decimal] = None
    max_loss_per_trade: Optional[Decimal] = None
    max_daily_loss: Optional[Decimal] = None
    stop_loss_pct: Optional[Decimal] = None
    take_profit_pct: Optional[Decimal] = None
    strategy_config: Optional[Dict[str, Any]] = None


class BacktestEngine:
    """Core backtesting simulation engine."""

    def __init__(
        self,
        config: BacktestConfig,
        db: AsyncSession,
        progress_callback: Optional[Callable[[int], None]] = None,
    ):
        self.config = config
        self.db = db
        self.progress_callback = progress_callback

        # State
        self.capital = float(config.initial_capital)
        self.positions: List[Position] = []
        self.trades: List[Dict[str, Any]] = []
        self.equity_curve: List[Dict[str, Any]] = []
        self.daily_pnl = Decimal("0")

        # Strategy config
        self.entry_rules = config.strategy_config.get("entry_rules", {}) if config.strategy_config else {}
        self.exit_rules = config.strategy_config.get("exit_rules", {}) if config.strategy_config else {}

    async def run(self) -> Dict[str, Any]:
        """Execute backtest simulation."""
        # Get trading dates
        trading_dates = await historical_data_service.get_trading_dates(
            self.db,
            self.config.symbol,
            self.config.start_date,
            self.config.end_date,
        )

        if not trading_dates:
            raise ValueError(f"No trading data found for {self.config.symbol} in specified date range")

        total_days = len(trading_dates)

        for i, trade_date in enumerate(trading_dates):
            # Get market data for this date
            spot = await historical_data_service.get_spot_price_on_date(
                self.db, self.config.symbol, trade_date
            )

            if not spot or not spot.close:
                continue

            spot_price = float(spot.close)

            # Check exit conditions for open positions
            await self._check_exits(trade_date, spot_price)

            # Check entry conditions
            if await self._should_enter(trade_date, spot_price):
                await self._open_position(trade_date, spot_price)

            # Update unrealized P&L for open positions
            await self._update_positions(trade_date)

            # Record equity
            self._record_equity(trade_date)

            # Reset daily P&L
            self.daily_pnl = Decimal("0")

            # Update progress
            if self.progress_callback:
                progress = int((i + 1) / total_days * 100)
                self.progress_callback(progress)

        # Close any remaining positions at end
        if trading_dates:
            await self._close_all_positions(trading_dates[-1], "end_of_backtest")

        # Calculate final metrics
        return self._calculate_results()

    async def _should_enter(self, trade_date: date, spot_price: float) -> bool:
        """Check if entry conditions are met."""
        # Check max positions
        if len(self.positions) >= self.config.max_positions:
            return False

        # Check daily loss limit
        if self.config.max_daily_loss and self.daily_pnl < -self.config.max_daily_loss:
            return False

        # Get entry rules
        entry_rules = self.entry_rules
        if not entry_rules:
            # Default: enter every week if no positions
            return len(self.positions) == 0 and trade_date.weekday() == 0  # Monday

        # Check IV rank condition
        if "iv_rank_min" in entry_rules:
            iv_rank = await historical_data_service.calculate_iv_rank(
                self.db, self.config.symbol, trade_date
            )
            if iv_rank is None or iv_rank < entry_rules["iv_rank_min"]:
                return False

        if "iv_rank_max" in entry_rules:
            iv_rank = await historical_data_service.calculate_iv_rank(
                self.db, self.config.symbol, trade_date
            )
            if iv_rank is None or iv_rank > entry_rules["iv_rank_max"]:
                return False

        # Check days to expiry
        if "days_to_expiry" in entry_rules:
            min_dte, max_dte = entry_rules["days_to_expiry"]
            expiry = await historical_data_service.get_nearest_expiry(
                self.db, self.config.symbol, trade_date, min_dte, max_dte
            )
            if not expiry:
                return False

        return True

    async def _open_position(self, trade_date: date, spot_price: float) -> None:
        """Open a new position based on strategy type."""
        strategy_type = self.config.strategy_config.get("strategy_type", "straddle") if self.config.strategy_config else "straddle"

        # Get nearest expiry
        min_dte = self.entry_rules.get("days_to_expiry", [15, 45])[0] if "days_to_expiry" in self.entry_rules else 15
        max_dte = self.entry_rules.get("days_to_expiry", [15, 45])[1] if "days_to_expiry" in self.entry_rules else 45

        expiry = await historical_data_service.get_nearest_expiry(
            self.db, self.config.symbol, trade_date, min_dte, max_dte
        )

        if not expiry:
            return

        # Get ATM strike
        atm_strike = await historical_data_service.get_atm_strike(
            self.db, self.config.symbol, trade_date, expiry, Decimal(str(spot_price))
        )

        if not atm_strike:
            return

        if strategy_type == "straddle":
            await self._open_straddle(trade_date, expiry, atm_strike)
        elif strategy_type == "iron_condor":
            await self._open_iron_condor(trade_date, expiry, atm_strike, Decimal(str(spot_price)))
        elif strategy_type == "strangle":
            await self._open_strangle(trade_date, expiry, atm_strike, Decimal(str(spot_price)))
        else:
            # Default to selling ATM straddle
            await self._open_straddle(trade_date, expiry, atm_strike)

    async def _open_straddle(
        self,
        trade_date: date,
        expiry: date,
        atm_strike: Decimal,
    ) -> None:
        """Open a short straddle (sell ATM call and put)."""
        # Get option prices
        call = await historical_data_service.get_option_price(
            self.db, self.config.symbol, trade_date, expiry, atm_strike, "CE"
        )
        put = await historical_data_service.get_option_price(
            self.db, self.config.symbol, trade_date, expiry, atm_strike, "PE"
        )

        if not call or not put or not call.close or not put.close:
            return

        # Create positions (selling both legs)
        for option, opt_type in [(call, "CE"), (put, "PE")]:
            position = Position(
                id=str(uuid.uuid4()),
                entry_date=trade_date,
                expiry=expiry,
                strike=atm_strike,
                option_type=opt_type,
                action="SELL",
                quantity=self.config.lot_size,
                entry_price=option.close,
                entry_iv=option.iv,
                entry_delta=option.delta,
            )
            self.positions.append(position)

            # Credit received (selling)
            self.capital += float(option.close) * self.config.lot_size

    async def _open_iron_condor(
        self,
        trade_date: date,
        expiry: date,
        atm_strike: Decimal,
        spot_price: Decimal,
    ) -> None:
        """Open an iron condor position."""
        wing_width = self.entry_rules.get("wing_width", 100)

        # Get OTM strikes for calls
        call_strikes = await historical_data_service.get_otm_strikes(
            self.db, self.config.symbol, trade_date, expiry, spot_price, "CE", 2
        )
        # Get OTM strikes for puts
        put_strikes = await historical_data_service.get_otm_strikes(
            self.db, self.config.symbol, trade_date, expiry, spot_price, "PE", 2
        )

        if len(call_strikes) < 2 or len(put_strikes) < 2:
            return

        # Short call spread (sell lower strike, buy higher strike)
        short_call_strike = call_strikes[0]
        long_call_strike = call_strikes[1]

        # Short put spread (sell higher strike, buy lower strike)
        short_put_strike = put_strikes[0]
        long_put_strike = put_strikes[1]

        legs = [
            (short_call_strike, "CE", "SELL"),
            (long_call_strike, "CE", "BUY"),
            (short_put_strike, "PE", "SELL"),
            (long_put_strike, "PE", "BUY"),
        ]

        for strike, opt_type, action in legs:
            option = await historical_data_service.get_option_price(
                self.db, self.config.symbol, trade_date, expiry, strike, opt_type
            )

            if not option or not option.close:
                continue

            position = Position(
                id=str(uuid.uuid4()),
                entry_date=trade_date,
                expiry=expiry,
                strike=strike,
                option_type=opt_type,
                action=action,
                quantity=self.config.lot_size,
                entry_price=option.close,
                entry_iv=option.iv,
                entry_delta=option.delta,
            )
            self.positions.append(position)

            # Update capital
            if action == "SELL":
                self.capital += float(option.close) * self.config.lot_size
            else:
                self.capital -= float(option.close) * self.config.lot_size

    async def _open_strangle(
        self,
        trade_date: date,
        expiry: date,
        atm_strike: Decimal,
        spot_price: Decimal,
    ) -> None:
        """Open a short strangle (sell OTM call and put)."""
        # Get OTM strikes
        call_strikes = await historical_data_service.get_otm_strikes(
            self.db, self.config.symbol, trade_date, expiry, spot_price, "CE", 1
        )
        put_strikes = await historical_data_service.get_otm_strikes(
            self.db, self.config.symbol, trade_date, expiry, spot_price, "PE", 1
        )

        if not call_strikes or not put_strikes:
            return

        call_strike = call_strikes[0]
        put_strike = put_strikes[0]

        for strike, opt_type in [(call_strike, "CE"), (put_strike, "PE")]:
            option = await historical_data_service.get_option_price(
                self.db, self.config.symbol, trade_date, expiry, strike, opt_type
            )

            if not option or not option.close:
                continue

            position = Position(
                id=str(uuid.uuid4()),
                entry_date=trade_date,
                expiry=expiry,
                strike=strike,
                option_type=opt_type,
                action="SELL",
                quantity=self.config.lot_size,
                entry_price=option.close,
                entry_iv=option.iv,
                entry_delta=option.delta,
            )
            self.positions.append(position)
            self.capital += float(option.close) * self.config.lot_size

    async def _check_exits(self, trade_date: date, spot_price: float) -> None:
        """Check exit conditions for all open positions."""
        positions_to_close = []

        for position in self.positions:
            exit_reason = await self._should_exit(position, trade_date, spot_price)
            if exit_reason:
                positions_to_close.append((position, exit_reason))

        for position, exit_reason in positions_to_close:
            await self._close_position(position, trade_date, exit_reason)

    async def _should_exit(
        self,
        position: Position,
        trade_date: date,
        spot_price: float,
    ) -> Optional[str]:
        """Check if position should be exited."""
        # Check expiry
        if trade_date >= position.expiry:
            return "expiry"

        # Check days before expiry exit
        days_before = self.exit_rules.get("days_before_expiry_exit", 1)
        if (position.expiry - trade_date).days <= days_before:
            return "time_exit"

        # Check holding period (BTST / Positional)
        holding_type = self.exit_rules.get("holding_period_type")
        if holding_type == "btst":
            # Exit on next trading day after entry
            if trade_date > position.entry_date:
                return "holding_period"
        elif holding_type == "positional":
            holding_days = self.exit_rules.get("holding_period_days", 3)
            if (trade_date - position.entry_date).days >= holding_days:
                return "holding_period"

        # Get current option price
        option = await historical_data_service.get_option_price(
            self.db,
            self.config.symbol,
            trade_date,
            position.expiry,
            position.strike,
            position.option_type,
        )

        if not option or not option.close:
            return None

        current_price = float(option.close)
        entry_price = float(position.entry_price)

        # Calculate P&L based on position type
        if position.action == "SELL":
            pnl_pct = ((entry_price - current_price) / entry_price) * 100
        else:
            pnl_pct = ((current_price - entry_price) / entry_price) * 100

        # Check take profit
        take_profit = self.exit_rules.get("profit_target_pct")
        if take_profit and pnl_pct >= take_profit:
            return "target"

        # Check stop loss
        stop_loss = self.exit_rules.get("stop_loss_pct")
        if stop_loss and pnl_pct <= -stop_loss:
            return "stoploss"

        return None

    async def _close_position(
        self,
        position: Position,
        trade_date: date,
        exit_reason: str,
    ) -> None:
        """Close a position and record the trade."""
        # Get exit price
        option = await historical_data_service.get_option_price(
            self.db,
            self.config.symbol,
            trade_date,
            position.expiry,
            position.strike,
            position.option_type,
        )

        if option and option.close:
            exit_price = float(option.close)
        else:
            # Use intrinsic value at expiry
            spot = await historical_data_service.get_spot_price_on_date(
                self.db, self.config.symbol, trade_date
            )
            if spot and spot.close:
                spot_price = float(spot.close)
                strike = float(position.strike)
                if position.option_type == "CE":
                    exit_price = max(0, spot_price - strike)
                else:
                    exit_price = max(0, strike - spot_price)
            else:
                exit_price = 0

        # Calculate P&L
        entry_price = float(position.entry_price)
        if position.action == "SELL":
            # Short position: profit = entry - exit
            realized_pnl = (entry_price - exit_price) * position.quantity
            self.capital -= exit_price * position.quantity  # Buy back
        else:
            # Long position: profit = exit - entry
            realized_pnl = (exit_price - entry_price) * position.quantity
            self.capital += exit_price * position.quantity  # Sell

        self.daily_pnl += Decimal(str(realized_pnl))

        # Record trade
        trade = {
            "id": str(uuid.uuid4()),
            "entry_date": position.entry_date,
            "exit_date": trade_date,
            "expiry": position.expiry,
            "strike": float(position.strike),
            "option_type": position.option_type,
            "action": position.action,
            "quantity": position.quantity,
            "entry_price": entry_price,
            "exit_price": exit_price,
            "entry_iv": float(position.entry_iv) if position.entry_iv else None,
            "entry_delta": float(position.entry_delta) if position.entry_delta else None,
            "realized_pnl": round(realized_pnl, 2),
            "exit_reason": exit_reason,
        }
        self.trades.append(trade)

        # Remove from positions
        self.positions.remove(position)

    async def _close_all_positions(self, trade_date: date, reason: str) -> None:
        """Close all open positions."""
        for position in list(self.positions):
            await self._close_position(position, trade_date, reason)

    async def _update_positions(self, trade_date: date) -> None:
        """Update unrealized P&L for open positions."""
        for position in self.positions:
            option = await historical_data_service.get_option_price(
                self.db,
                self.config.symbol,
                trade_date,
                position.expiry,
                position.strike,
                position.option_type,
            )

            if option and option.close:
                current_price = float(option.close)
                entry_price = float(position.entry_price)

                if position.action == "SELL":
                    unrealized = (entry_price - current_price) * position.quantity
                else:
                    unrealized = (current_price - entry_price) * position.quantity

                position.unrealized_pnl = Decimal(str(unrealized))

    def _record_equity(self, trade_date: date) -> None:
        """Record equity at end of day."""
        # Calculate total unrealized P&L
        unrealized_pnl = sum(float(p.unrealized_pnl) for p in self.positions)

        # Total equity = capital + unrealized P&L
        equity = self.capital + unrealized_pnl

        self.equity_curve.append({
            "date": trade_date.isoformat(),
            "equity": round(equity, 2),
        })

    def _calculate_results(self) -> Dict[str, Any]:
        """Calculate final backtest results."""
        return metrics_service.calculate_all_metrics(
            trades=self.trades,
            equity_curve=self.equity_curve,
            initial_capital=float(self.config.initial_capital),
            start_date=self.config.start_date,
            end_date=self.config.end_date,
        )


async def run_backtest(
    run: BacktestRun,
    db: AsyncSession,
    progress_callback: Optional[Callable[[int], None]] = None,
) -> Dict[str, Any]:
    """Run a backtest from a BacktestRun model."""
    config = BacktestConfig(
        symbol=run.symbol,
        start_date=run.start_date,
        end_date=run.end_date,
        initial_capital=run.initial_capital,
        lot_size=run.lot_size,
        max_positions=run.max_positions,
        position_size_pct=run.position_size_pct,
        max_loss_per_trade=run.max_loss_per_trade,
        max_daily_loss=run.max_daily_loss,
        stop_loss_pct=run.stop_loss_pct,
        take_profit_pct=run.take_profit_pct,
        strategy_config=run.strategy_config,
    )

    engine = BacktestEngine(config, db, progress_callback)
    return await engine.run()
