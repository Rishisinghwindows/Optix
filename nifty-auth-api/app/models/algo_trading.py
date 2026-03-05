"""
Algo Trading Models - For automated trading functionality
"""
from datetime import datetime
from sqlalchemy import Column, String, DateTime, Float, Integer, Boolean, Enum as SQLEnum, Text, JSON
from sqlalchemy.orm import relationship
import uuid
import enum

from app.database import Base


class SignalType(str, enum.Enum):
    STRONG_BUY = "STRONG_BUY"
    BUY = "BUY"
    HOLD = "HOLD"
    SELL = "SELL"
    STRONG_SELL = "STRONG_SELL"


class AlgoOrderStatus(str, enum.Enum):
    PENDING = "pending"
    PLACED = "placed"
    EXECUTED = "executed"
    PARTIALLY_FILLED = "partially_filled"
    CANCELLED = "cancelled"
    REJECTED = "rejected"
    FAILED = "failed"


class AlgoPositionStatus(str, enum.Enum):
    OPEN = "open"
    CLOSED = "closed"
    STOPPED_OUT = "stopped_out"
    TARGET_HIT = "target_hit"
    TRAILING_STOPPED = "trailing_stopped"
    TIME_EXIT = "time_exit"


class AlgoOptionType(str, enum.Enum):
    CE = "CE"
    PE = "PE"


class ExitReason(str, enum.Enum):
    STOP_LOSS = "stop_loss"
    TARGET = "target"
    TRAILING_STOP = "trailing_stop"
    TIME_EXIT = "time_exit"
    MANUAL = "manual"
    KILL_SWITCH = "kill_switch"
    DAILY_LOSS_LIMIT = "daily_loss_limit"


class AlgoConfig(Base):
    """Algo trading configuration and parameters"""
    __tablename__ = "algo_configs"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))

    # Basic settings
    name = Column(String(100), nullable=False, default="Default Strategy")
    is_active = Column(Boolean, default=False)
    is_paper_mode = Column(Boolean, default=True)  # Paper trading by default

    # Capital and risk management
    capital = Column(Float, nullable=False, default=100000.0)
    risk_per_trade = Column(Float, nullable=False, default=0.02)  # 2%
    max_daily_loss = Column(Float, nullable=False, default=0.05)  # 5%
    max_positions = Column(Integer, nullable=False, default=2)
    max_lots_per_trade = Column(Integer, nullable=False, default=10)

    # Stop loss and target settings
    stop_loss_pct = Column(Float, nullable=False, default=0.30)  # 30% of premium
    target_pct = Column(Float, nullable=False, default=0.50)  # 50% profit
    trailing_trigger_pct = Column(Float, nullable=False, default=0.25)  # Activate at 25%
    trailing_stop_pct = Column(Float, nullable=False, default=0.15)  # Trail 15% below high

    # Signal settings
    signal_threshold = Column(String(20), default="STRONG")  # STRONG or ALL
    signal_score_threshold = Column(Float, default=0.35)  # Min score for entry

    # Trading hours (IST, 24hr format "HH:MM")
    market_start_time = Column(String(10), default="09:20")
    market_end_time = Column(String(10), default="15:15")
    no_entry_after = Column(String(10), default="15:00")  # No new entries after

    # Indices to trade
    indices = Column(JSON, default=["NIFTY", "BANKNIFTY"])

    # Timestamps
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class AlgoTrade(Base):
    """Individual algo trades (both entry and exit)"""
    __tablename__ = "algo_trades"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    position_id = Column(String(36), nullable=True)  # Links to AlgoPosition
    config_id = Column(String(36), nullable=True)  # Links to AlgoConfig

    # Trade details
    symbol = Column(String(20), nullable=False)  # NIFTY, BANKNIFTY
    strike_price = Column(Float, nullable=False)
    option_type = Column(SQLEnum(AlgoOptionType), nullable=False)
    expiry_date = Column(String(20), nullable=False)

    # Order details
    order_id = Column(String(50), nullable=True)  # Upstox order ID
    order_status = Column(SQLEnum(AlgoOrderStatus), default=AlgoOrderStatus.PENDING)
    trade_type = Column(String(10), nullable=False)  # "entry" or "exit"

    # Quantity and pricing
    quantity = Column(Integer, nullable=False)  # Number of lots
    lot_size = Column(Integer, nullable=False)
    order_price = Column(Float, nullable=True)  # Limit price if any
    executed_price = Column(Float, nullable=True)

    # Signal that triggered this trade
    signal_type = Column(SQLEnum(SignalType), nullable=True)
    signal_score = Column(Float, nullable=True)

    # Upstox instrument key for execution
    instrument_key = Column(String(100), nullable=True)
    trading_symbol = Column(String(50), nullable=True)

    # P&L (for exit trades)
    pnl = Column(Float, nullable=True)
    pnl_percent = Column(Float, nullable=True)
    exit_reason = Column(SQLEnum(ExitReason), nullable=True)

    # Paper or live trade
    is_paper = Column(Boolean, default=True)

    # Timestamps
    created_at = Column(DateTime, default=datetime.utcnow)
    executed_at = Column(DateTime, nullable=True)

    # Error tracking
    error_message = Column(Text, nullable=True)


class AlgoPosition(Base):
    """Open algo positions with real-time tracking"""
    __tablename__ = "algo_positions"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    config_id = Column(String(36), nullable=True)

    # Position details
    symbol = Column(String(20), nullable=False)
    strike_price = Column(Float, nullable=False)
    option_type = Column(SQLEnum(AlgoOptionType), nullable=False)
    expiry_date = Column(String(20), nullable=False)

    # Quantity
    quantity = Column(Integer, nullable=False)  # Number of lots
    lot_size = Column(Integer, nullable=False)

    # Pricing
    entry_price = Column(Float, nullable=False)
    current_price = Column(Float, nullable=True)
    highest_price = Column(Float, nullable=True)  # For trailing stop

    # Stop loss and target
    stop_loss_price = Column(Float, nullable=False)
    target_price = Column(Float, nullable=False)
    trailing_stop_active = Column(Boolean, default=False)
    trailing_stop_price = Column(Float, nullable=True)

    # Status
    status = Column(SQLEnum(AlgoPositionStatus), default=AlgoPositionStatus.OPEN)
    exit_price = Column(Float, nullable=True)
    exit_reason = Column(SQLEnum(ExitReason), nullable=True)

    # Signal info
    entry_signal = Column(SQLEnum(SignalType), nullable=True)
    entry_signal_score = Column(Float, nullable=True)

    # Upstox instrument
    instrument_key = Column(String(100), nullable=True)
    trading_symbol = Column(String(50), nullable=True)

    # Paper or live
    is_paper = Column(Boolean, default=True)

    # Timestamps
    opened_at = Column(DateTime, default=datetime.utcnow)
    closed_at = Column(DateTime, nullable=True)
    last_updated = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    @property
    def invested_amount(self) -> float:
        return self.entry_price * self.quantity * self.lot_size

    @property
    def current_value(self) -> float:
        price = self.current_price or self.entry_price
        return price * self.quantity * self.lot_size

    @property
    def unrealized_pnl(self) -> float:
        if self.status != AlgoPositionStatus.OPEN:
            return 0.0
        return self.current_value - self.invested_amount

    @property
    def unrealized_pnl_percent(self) -> float:
        if self.invested_amount == 0:
            return 0.0
        return (self.unrealized_pnl / self.invested_amount) * 100

    @property
    def realized_pnl(self) -> float:
        if self.status == AlgoPositionStatus.OPEN:
            return 0.0
        if self.exit_price is None:
            return 0.0
        exit_value = self.exit_price * self.quantity * self.lot_size
        return exit_value - self.invested_amount


class AlgoLog(Base):
    """Audit log for all algo actions"""
    __tablename__ = "algo_logs"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))

    # Log details
    level = Column(String(20), default="INFO")  # INFO, WARNING, ERROR, CRITICAL
    category = Column(String(50), nullable=False)  # SIGNAL, ORDER, POSITION, RISK, SYSTEM
    message = Column(Text, nullable=False)

    # Related entities
    config_id = Column(String(36), nullable=True)
    position_id = Column(String(36), nullable=True)
    trade_id = Column(String(36), nullable=True)

    # Context data
    context = Column(JSON, nullable=True)  # Additional data as JSON

    # Timestamp
    created_at = Column(DateTime, default=datetime.utcnow)


class AlgoDailyStats(Base):
    """Daily statistics for algo trading"""
    __tablename__ = "algo_daily_stats"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    config_id = Column(String(36), nullable=True)

    # Date
    trading_date = Column(String(10), nullable=False)  # YYYY-MM-DD

    # Statistics
    total_trades = Column(Integer, default=0)
    winning_trades = Column(Integer, default=0)
    losing_trades = Column(Integer, default=0)

    gross_pnl = Column(Float, default=0.0)
    charges = Column(Float, default=0.0)  # Brokerage, taxes
    net_pnl = Column(Float, default=0.0)

    max_drawdown = Column(Float, default=0.0)
    peak_value = Column(Float, default=0.0)

    # Capital tracking
    starting_capital = Column(Float, default=0.0)
    ending_capital = Column(Float, default=0.0)

    # Trading activity
    signals_generated = Column(Integer, default=0)
    signals_acted_on = Column(Integer, default=0)

    # Timestamps
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
