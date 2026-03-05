"""
Risk Manager Service - Handles position sizing, stop-loss, and risk controls
"""
import logging
from typing import Optional, Dict, Any, List, Tuple
from datetime import datetime, date
from dataclasses import dataclass

from app.models.algo_trading import (
    AlgoConfig, AlgoPosition, AlgoPositionStatus, SignalType, AlgoOptionType
)

logger = logging.getLogger(__name__)


@dataclass
class RiskCheckResult:
    """Result of a risk check"""
    passed: bool
    reason: str
    details: Optional[Dict[str, Any]] = None


@dataclass
class PositionSizeResult:
    """Result of position sizing calculation"""
    lots: int
    risk_amount: float
    stop_loss_amount: float
    invested_amount: float
    reasoning: str


class RiskManager:
    """
    Risk management service for algo trading.
    Handles position sizing, stop-loss calculation, and risk validation.
    """

    def __init__(self):
        self._daily_pnl: float = 0.0
        self._daily_trades: int = 0
        self._current_date: str = ""
        self._peak_capital: float = 0.0

    def reset_daily_stats(self, config: AlgoConfig) -> None:
        """Reset daily statistics at start of new trading day"""
        today = date.today().isoformat()
        if self._current_date != today:
            self._daily_pnl = 0.0
            self._daily_trades = 0
            self._current_date = today
            self._peak_capital = config.capital
            logger.info(f"Daily stats reset for {today}")

    def update_daily_pnl(self, pnl: float) -> None:
        """Update daily P&L after a trade closes"""
        self._daily_pnl += pnl
        self._daily_trades += 1
        logger.info(f"Daily P&L updated: {self._daily_pnl:.2f} (trades: {self._daily_trades})")

    def get_daily_stats(self) -> Dict[str, Any]:
        """Get current daily statistics"""
        return {
            "daily_pnl": self._daily_pnl,
            "daily_trades": self._daily_trades,
            "current_date": self._current_date,
            "peak_capital": self._peak_capital
        }

    # ==================== Position Sizing ====================

    def calculate_position_size(
        self,
        config: AlgoConfig,
        signal_type: SignalType,
        signal_score: float,
        entry_price: float,
        lot_size: int
    ) -> PositionSizeResult:
        """
        Calculate optimal position size based on risk parameters.

        Position Size = (Capital × Risk%) / Stop Loss Amount

        For STRONG signals, use full risk allocation.
        For regular signals, use 50% of risk allocation.
        """
        # Determine risk multiplier based on signal strength
        if signal_type in [SignalType.STRONG_BUY, SignalType.STRONG_SELL]:
            risk_multiplier = 1.0
            signal_strength = "STRONG"
        else:
            risk_multiplier = 0.5
            signal_strength = "NORMAL"

        # Calculate risk amount
        base_risk = config.capital * config.risk_per_trade
        adjusted_risk = base_risk * risk_multiplier

        # Calculate stop loss amount per lot
        stop_loss_pct = config.stop_loss_pct
        stop_loss_per_share = entry_price * stop_loss_pct
        stop_loss_per_lot = stop_loss_per_share * lot_size

        # Calculate number of lots
        if stop_loss_per_lot > 0:
            raw_lots = adjusted_risk / stop_loss_per_lot
            lots = max(1, min(int(raw_lots), config.max_lots_per_trade))
        else:
            lots = 1

        # Calculate actual values
        invested_amount = entry_price * lots * lot_size
        actual_stop_loss = stop_loss_per_lot * lots

        reasoning = (
            f"{signal_strength} signal (score: {signal_score:.2f}) → "
            f"Risk: ₹{adjusted_risk:.0f} ({config.risk_per_trade*100*risk_multiplier:.1f}%) → "
            f"SL/lot: ₹{stop_loss_per_lot:.0f} → {lots} lots"
        )

        logger.info(f"Position size calculated: {lots} lots - {reasoning}")

        return PositionSizeResult(
            lots=lots,
            risk_amount=adjusted_risk,
            stop_loss_amount=actual_stop_loss,
            invested_amount=invested_amount,
            reasoning=reasoning
        )

    # ==================== Stop Loss & Target ====================

    def calculate_stop_loss(
        self,
        entry_price: float,
        config: AlgoConfig
    ) -> float:
        """Calculate stop loss price based on entry price and config"""
        stop_loss_price = entry_price * (1 - config.stop_loss_pct)
        return round(stop_loss_price, 2)

    def calculate_target(
        self,
        entry_price: float,
        config: AlgoConfig
    ) -> float:
        """Calculate target price based on entry price and config"""
        target_price = entry_price * (1 + config.target_pct)
        return round(target_price, 2)

    def calculate_trailing_stop(
        self,
        highest_price: float,
        config: AlgoConfig
    ) -> float:
        """Calculate trailing stop price based on highest price reached"""
        trailing_stop = highest_price * (1 - config.trailing_stop_pct)
        return round(trailing_stop, 2)

    def should_activate_trailing_stop(
        self,
        entry_price: float,
        current_price: float,
        config: AlgoConfig
    ) -> bool:
        """Check if trailing stop should be activated"""
        profit_pct = (current_price - entry_price) / entry_price
        return profit_pct >= config.trailing_trigger_pct

    def update_position_stops(
        self,
        position: AlgoPosition,
        current_price: float,
        config: AlgoConfig
    ) -> Tuple[bool, Optional[str]]:
        """
        Update position stops and check exit conditions.

        Returns: (should_exit, exit_reason)
        """
        # Update highest price for trailing stop
        if current_price > (position.highest_price or 0):
            position.highest_price = current_price

        # Check trailing stop activation
        if not position.trailing_stop_active:
            if self.should_activate_trailing_stop(
                position.entry_price, current_price, config
            ):
                position.trailing_stop_active = True
                position.trailing_stop_price = self.calculate_trailing_stop(
                    position.highest_price, config
                )
                logger.info(
                    f"Trailing stop activated for {position.symbol} "
                    f"at ₹{position.trailing_stop_price:.2f}"
                )

        # Update trailing stop if active
        if position.trailing_stop_active:
            new_trailing_stop = self.calculate_trailing_stop(
                position.highest_price, config
            )
            if new_trailing_stop > (position.trailing_stop_price or 0):
                position.trailing_stop_price = new_trailing_stop

        # Check exit conditions
        # 1. Stop Loss
        if current_price <= position.stop_loss_price:
            return True, "stop_loss"

        # 2. Target
        if current_price >= position.target_price:
            return True, "target"

        # 3. Trailing Stop
        if position.trailing_stop_active and position.trailing_stop_price:
            if current_price <= position.trailing_stop_price:
                return True, "trailing_stop"

        return False, None

    # ==================== Risk Validation ====================

    def check_can_open_position(
        self,
        config: AlgoConfig,
        open_positions: List[AlgoPosition],
        symbol: str
    ) -> RiskCheckResult:
        """
        Validate if a new position can be opened.
        Checks: max positions, daily loss limit, duplicate positions
        """
        # Check max positions
        active_positions = [
            p for p in open_positions if p.status == AlgoPositionStatus.OPEN
        ]
        if len(active_positions) >= config.max_positions:
            return RiskCheckResult(
                passed=False,
                reason=f"Max positions reached ({len(active_positions)}/{config.max_positions})",
                details={"current_positions": len(active_positions)}
            )

        # Check daily loss limit
        if self.is_daily_loss_limit_hit(config):
            return RiskCheckResult(
                passed=False,
                reason=f"Daily loss limit hit (₹{self._daily_pnl:.0f})",
                details={"daily_pnl": self._daily_pnl}
            )

        # Check for duplicate position in same symbol
        for pos in active_positions:
            if pos.symbol == symbol:
                return RiskCheckResult(
                    passed=False,
                    reason=f"Already have open position in {symbol}",
                    details={"existing_position_id": pos.id}
                )

        return RiskCheckResult(
            passed=True,
            reason="All risk checks passed"
        )

    def is_daily_loss_limit_hit(self, config: AlgoConfig) -> bool:
        """Check if daily loss limit has been hit"""
        max_loss = config.capital * config.max_daily_loss
        return self._daily_pnl <= -max_loss

    def check_market_hours(self, config: AlgoConfig) -> RiskCheckResult:
        """Check if current time is within trading hours"""
        from datetime import datetime
        import pytz

        ist = pytz.timezone('Asia/Kolkata')
        now = datetime.now(ist)

        # Parse market times
        start_hour, start_min = map(int, config.market_start_time.split(":"))
        end_hour, end_min = map(int, config.market_end_time.split(":"))

        market_start = now.replace(hour=start_hour, minute=start_min, second=0, microsecond=0)
        market_end = now.replace(hour=end_hour, minute=end_min, second=0, microsecond=0)

        if market_start <= now <= market_end:
            return RiskCheckResult(
                passed=True,
                reason="Within market hours",
                details={
                    "current_time": now.strftime("%H:%M"),
                    "market_start": config.market_start_time,
                    "market_end": config.market_end_time
                }
            )
        else:
            return RiskCheckResult(
                passed=False,
                reason="Outside market hours",
                details={
                    "current_time": now.strftime("%H:%M"),
                    "market_start": config.market_start_time,
                    "market_end": config.market_end_time
                }
            )

    def check_no_entry_window(self, config: AlgoConfig) -> RiskCheckResult:
        """Check if we're in the no-entry window (e.g., last 30 mins)"""
        from datetime import datetime
        import pytz

        ist = pytz.timezone('Asia/Kolkata')
        now = datetime.now(ist)

        no_entry_hour, no_entry_min = map(int, config.no_entry_after.split(":"))
        no_entry_time = now.replace(
            hour=no_entry_hour, minute=no_entry_min, second=0, microsecond=0
        )

        if now >= no_entry_time:
            return RiskCheckResult(
                passed=False,
                reason=f"No new entries after {config.no_entry_after}",
                details={"current_time": now.strftime("%H:%M")}
            )

        return RiskCheckResult(
            passed=True,
            reason="Within entry window"
        )

    def check_signal_validity(
        self,
        signal_type: SignalType,
        signal_score: float,
        config: AlgoConfig
    ) -> RiskCheckResult:
        """Check if signal is strong enough for entry"""
        # Check signal threshold
        if config.signal_threshold == "STRONG":
            if signal_type not in [SignalType.STRONG_BUY, SignalType.STRONG_SELL]:
                return RiskCheckResult(
                    passed=False,
                    reason=f"Signal {signal_type.value} not strong enough (need STRONG_BUY/SELL)",
                    details={"signal_type": signal_type.value}
                )

        # Check signal score
        if abs(signal_score) < config.signal_score_threshold:
            return RiskCheckResult(
                passed=False,
                reason=f"Signal score {signal_score:.2f} below threshold {config.signal_score_threshold}",
                details={"signal_score": signal_score}
            )

        return RiskCheckResult(
            passed=True,
            reason="Signal validated",
            details={
                "signal_type": signal_type.value,
                "signal_score": signal_score
            }
        )

    def perform_all_checks(
        self,
        config: AlgoConfig,
        open_positions: List[AlgoPosition],
        symbol: str,
        signal_type: SignalType,
        signal_score: float
    ) -> Tuple[bool, List[RiskCheckResult]]:
        """
        Perform all risk checks before opening a position.

        Returns: (all_passed, list_of_results)
        """
        results = []

        # Market hours check
        market_check = self.check_market_hours(config)
        results.append(market_check)

        # No entry window check
        entry_window_check = self.check_no_entry_window(config)
        results.append(entry_window_check)

        # Position limits check
        position_check = self.check_can_open_position(config, open_positions, symbol)
        results.append(position_check)

        # Signal validity check
        signal_check = self.check_signal_validity(signal_type, signal_score, config)
        results.append(signal_check)

        all_passed = all(r.passed for r in results)

        if not all_passed:
            failed = [r for r in results if not r.passed]
            logger.warning(
                f"Risk checks failed: {[r.reason for r in failed]}"
            )

        return all_passed, results


# Singleton instance
risk_manager = RiskManager()
