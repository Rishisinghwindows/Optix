"""Backtest-related database models for historical data and strategy backtesting."""

import uuid
import json
from datetime import datetime, date
from decimal import Decimal
from typing import Optional, TYPE_CHECKING
from enum import Enum as PyEnum
from sqlalchemy import (
    String, Boolean, DECIMAL, DateTime, Date, Integer, BigInteger,
    Index as SQLIndex, ForeignKey, Text, Enum, UniqueConstraint, TypeDecorator
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base

if TYPE_CHECKING:
    from app.models.user import User


# JSON type that works with both PostgreSQL and SQLite
class JSONType(TypeDecorator):
    impl = Text
    cache_ok = True

    def process_bind_param(self, value, dialect):
        if value is not None:
            return json.dumps(value)
        return None

    def process_result_value(self, value, dialect):
        if value is not None:
            return json.loads(value)
        return None


class BacktestStatus(str, PyEnum):
    """Status of a backtest run."""
    PENDING = "pending"
    RUNNING = "running"
    COMPLETED = "completed"
    FAILED = "failed"
    CANCELLED = "cancelled"


class StrategyType(str, PyEnum):
    """Types of trading strategies."""
    IRON_CONDOR = "iron_condor"
    STRADDLE = "straddle"
    STRANGLE = "strangle"
    BULL_CALL_SPREAD = "bull_call_spread"
    BEAR_PUT_SPREAD = "bear_put_spread"
    BUTTERFLY = "butterfly"
    MOMENTUM = "momentum"
    CUSTOM = "custom"


class TradeAction(str, PyEnum):
    """Trade action types."""
    BUY = "BUY"
    SELL = "SELL"


class ExitReason(str, PyEnum):
    """Reasons for exiting a trade."""
    TARGET = "target"
    STOPLOSS = "stoploss"
    EXPIRY = "expiry"
    SIGNAL = "signal"
    TIME_EXIT = "time_exit"
    MANUAL = "manual"


class SupportedIndex(Base):
    """Supported indices for backtesting."""
    __tablename__ = "indices"

    id: Mapped[str] = mapped_column(
        String(36),
        primary_key=True,
        default=lambda: str(uuid.uuid4()),
    )
    symbol: Mapped[str] = mapped_column(
        String(20),
        unique=True,
        nullable=False,
    )
    name: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
    )
    exchange: Mapped[str] = mapped_column(
        String(10),
        nullable=False,
    )  # 'NSE', 'BSE'
    lot_size: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
    )
    data_available_from: Mapped[Optional[date]] = mapped_column(
        Date,
        nullable=True,
    )
    is_active: Mapped[bool] = mapped_column(
        Boolean,
        default=True,
    )

    def __repr__(self) -> str:
        return f"<Index {self.symbol} ({self.exchange})>"


class HistoricalSpotPrice(Base):
    """Historical daily spot prices for indices."""
    __tablename__ = "historical_spot_prices"

    id: Mapped[str] = mapped_column(
        String(36),
        primary_key=True,
        default=lambda: str(uuid.uuid4()),
    )
    symbol: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        index=True,
    )
    exchange: Mapped[str] = mapped_column(
        String(10),
        nullable=False,
    )
    date: Mapped[date] = mapped_column(
        Date,
        nullable=False,
    )
    open: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(12, 2),
        nullable=True,
    )
    high: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(12, 2),
        nullable=True,
    )
    low: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(12, 2),
        nullable=True,
    )
    close: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(12, 2),
        nullable=True,
    )
    volume: Mapped[Optional[int]] = mapped_column(
        BigInteger,
        nullable=True,
    )

    __table_args__ = (
        UniqueConstraint("symbol", "date", name="uq_spot_symbol_date"),
        SQLIndex("idx_spot_symbol_date", "symbol", "date"),
        SQLIndex("idx_spot_exchange", "exchange"),
    )

    def __repr__(self) -> str:
        return f"<SpotPrice {self.symbol} {self.date} close={self.close}>"


class HistoricalOption(Base):
    """Historical daily option chain data."""
    __tablename__ = "historical_options"

    id: Mapped[str] = mapped_column(
        String(36),
        primary_key=True,
        default=lambda: str(uuid.uuid4()),
    )
    symbol: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        index=True,
    )
    exchange: Mapped[str] = mapped_column(
        String(10),
        nullable=False,
    )
    date: Mapped[date] = mapped_column(
        Date,
        nullable=False,
    )
    expiry: Mapped[date] = mapped_column(
        Date,
        nullable=False,
    )
    strike: Mapped[Decimal] = mapped_column(
        DECIMAL(10, 2),
        nullable=False,
    )
    option_type: Mapped[str] = mapped_column(
        String(2),
        nullable=False,
    )  # 'CE', 'PE'

    # OHLC data
    open: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(10, 2),
        nullable=True,
    )
    high: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(10, 2),
        nullable=True,
    )
    low: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(10, 2),
        nullable=True,
    )
    close: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(10, 2),
        nullable=True,
    )
    settle_price: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(10, 2),
        nullable=True,
    )

    # Volume and OI
    volume: Mapped[Optional[int]] = mapped_column(
        BigInteger,
        nullable=True,
    )
    open_interest: Mapped[Optional[int]] = mapped_column(
        BigInteger,
        nullable=True,
    )

    # Greeks (calculated or pre-computed)
    iv: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(8, 4),
        nullable=True,
    )
    delta: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(8, 4),
        nullable=True,
    )
    gamma: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(8, 4),
        nullable=True,
    )
    theta: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(8, 4),
        nullable=True,
    )
    vega: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(8, 4),
        nullable=True,
    )

    __table_args__ = (
        UniqueConstraint(
            "symbol", "date", "expiry", "strike", "option_type",
            name="uq_option_unique"
        ),
        SQLIndex("idx_options_symbol_date", "symbol", "date"),
        SQLIndex("idx_options_exchange", "exchange"),
        SQLIndex("idx_options_expiry", "expiry"),
        SQLIndex("idx_options_strike", "strike"),
    )

    def __repr__(self) -> str:
        return f"<Option {self.symbol} {self.date} {self.strike}{self.option_type}>"


class BacktestStrategy(Base):
    """Strategy definitions for backtesting."""
    __tablename__ = "backtest_strategies"

    id: Mapped[str] = mapped_column(
        String(36),
        primary_key=True,
        default=lambda: str(uuid.uuid4()),
    )
    user_id: Mapped[Optional[str]] = mapped_column(
        String(36),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=True,  # Null for system templates
        index=True,
    )

    name: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
    )
    description: Mapped[Optional[str]] = mapped_column(
        Text,
        nullable=True,
    )
    strategy_type: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
    )

    # Strategy parameters as JSON
    entry_rules: Mapped[dict] = mapped_column(
        JSONType,
        nullable=False,
    )
    exit_rules: Mapped[dict] = mapped_column(
        JSONType,
        nullable=False,
    )
    position_sizing: Mapped[Optional[dict]] = mapped_column(
        JSONType,
        nullable=True,
    )

    is_template: Mapped[bool] = mapped_column(
        Boolean,
        default=False,
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime,
        default=datetime.utcnow,
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
    )

    # Relationships
    user: Mapped[Optional["User"]] = relationship(
        "User",
        backref="backtest_strategies",
    )
    runs: Mapped[list["BacktestRun"]] = relationship(
        "BacktestRun",
        back_populates="strategy",
        cascade="all, delete-orphan",
    )

    def __repr__(self) -> str:
        return f"<Strategy {self.name} ({self.strategy_type})>"


class BacktestRun(Base):
    """Backtest run configuration and status."""
    __tablename__ = "backtest_runs"

    id: Mapped[str] = mapped_column(
        String(36),
        primary_key=True,
        default=lambda: str(uuid.uuid4()),
    )
    user_id: Mapped[str] = mapped_column(
        String(36),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    strategy_id: Mapped[Optional[str]] = mapped_column(
        String(36),
        ForeignKey("backtest_strategies.id", ondelete="SET NULL"),
        nullable=True,
    )

    # Configuration
    symbol: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
    )
    start_date: Mapped[date] = mapped_column(
        Date,
        nullable=False,
    )
    end_date: Mapped[date] = mapped_column(
        Date,
        nullable=False,
    )
    initial_capital: Mapped[Decimal] = mapped_column(
        DECIMAL(15, 2),
        nullable=False,
    )

    # Position sizing
    lot_size: Mapped[int] = mapped_column(
        Integer,
        default=25,
    )
    max_positions: Mapped[int] = mapped_column(
        Integer,
        default=5,
    )
    position_size_pct: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(5, 2),
        nullable=True,
    )

    # Risk management
    max_loss_per_trade: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(10, 2),
        nullable=True,
    )
    max_daily_loss: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(10, 2),
        nullable=True,
    )
    stop_loss_pct: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(5, 2),
        nullable=True,
    )
    take_profit_pct: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(5, 2),
        nullable=True,
    )

    # Strategy config (copied from strategy at run time for historical record)
    strategy_config: Mapped[Optional[dict]] = mapped_column(
        JSONType,
        nullable=True,
    )

    # Status
    status: Mapped[str] = mapped_column(
        String(20),
        default=BacktestStatus.PENDING.value,
    )
    progress: Mapped[int] = mapped_column(
        Integer,
        default=0,
    )  # 0-100
    error_message: Mapped[Optional[str]] = mapped_column(
        Text,
        nullable=True,
    )

    # Execution times
    created_at: Mapped[datetime] = mapped_column(
        DateTime,
        default=datetime.utcnow,
    )
    started_at: Mapped[Optional[datetime]] = mapped_column(
        DateTime,
        nullable=True,
    )
    completed_at: Mapped[Optional[datetime]] = mapped_column(
        DateTime,
        nullable=True,
    )

    # Relationships
    user: Mapped["User"] = relationship(
        "User",
        backref="backtest_runs",
    )
    strategy: Mapped[Optional["BacktestStrategy"]] = relationship(
        "BacktestStrategy",
        back_populates="runs",
    )
    trades: Mapped[list["BacktestTrade"]] = relationship(
        "BacktestTrade",
        back_populates="run",
        cascade="all, delete-orphan",
    )
    result: Mapped[Optional["BacktestResult"]] = relationship(
        "BacktestResult",
        back_populates="run",
        uselist=False,
        cascade="all, delete-orphan",
    )

    def __repr__(self) -> str:
        return f"<BacktestRun {self.id} {self.symbol} ({self.status})>"


class BacktestTrade(Base):
    """Individual trades in a backtest run."""
    __tablename__ = "backtest_trades"

    id: Mapped[str] = mapped_column(
        String(36),
        primary_key=True,
        default=lambda: str(uuid.uuid4()),
    )
    run_id: Mapped[str] = mapped_column(
        String(36),
        ForeignKey("backtest_runs.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    # Trade details
    entry_date: Mapped[date] = mapped_column(
        Date,
        nullable=False,
    )
    exit_date: Mapped[Optional[date]] = mapped_column(
        Date,
        nullable=True,
    )
    expiry: Mapped[date] = mapped_column(
        Date,
        nullable=False,
    )
    strike: Mapped[Decimal] = mapped_column(
        DECIMAL(10, 2),
        nullable=False,
    )
    option_type: Mapped[str] = mapped_column(
        String(2),
        nullable=False,
    )  # 'CE', 'PE'

    # Position
    action: Mapped[str] = mapped_column(
        String(4),
        nullable=False,
    )  # 'BUY', 'SELL'
    quantity: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
    )

    # Prices
    entry_price: Mapped[Decimal] = mapped_column(
        DECIMAL(10, 2),
        nullable=False,
    )
    exit_price: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(10, 2),
        nullable=True,
    )

    # Greeks at entry
    entry_iv: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(8, 4),
        nullable=True,
    )
    entry_delta: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(8, 4),
        nullable=True,
    )

    # P&L
    realized_pnl: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(12, 2),
        nullable=True,
    )
    unrealized_pnl: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(12, 2),
        nullable=True,
    )

    # Exit reason
    exit_reason: Mapped[Optional[str]] = mapped_column(
        String(50),
        nullable=True,
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime,
        default=datetime.utcnow,
    )

    # Relationships
    run: Mapped["BacktestRun"] = relationship(
        "BacktestRun",
        back_populates="trades",
    )

    __table_args__ = (
        SQLIndex("idx_trades_run", "run_id"),
    )

    def __repr__(self) -> str:
        return f"<Trade {self.action} {self.strike}{self.option_type} @ {self.entry_price}>"


class BacktestResult(Base):
    """Summary results for a completed backtest run."""
    __tablename__ = "backtest_results"

    id: Mapped[str] = mapped_column(
        String(36),
        primary_key=True,
        default=lambda: str(uuid.uuid4()),
    )
    run_id: Mapped[str] = mapped_column(
        String(36),
        ForeignKey("backtest_runs.id", ondelete="CASCADE"),
        unique=True,
        nullable=False,
    )

    # Performance metrics
    total_trades: Mapped[int] = mapped_column(
        Integer,
        default=0,
    )
    winning_trades: Mapped[int] = mapped_column(
        Integer,
        default=0,
    )
    losing_trades: Mapped[int] = mapped_column(
        Integer,
        default=0,
    )
    win_rate: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(5, 2),
        nullable=True,
    )

    # Returns
    total_pnl: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(15, 2),
        nullable=True,
    )
    total_return_pct: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(8, 2),
        nullable=True,
    )
    cagr: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(8, 2),
        nullable=True,
    )

    # Risk metrics
    max_drawdown: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(15, 2),
        nullable=True,
    )
    max_drawdown_pct: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(8, 2),
        nullable=True,
    )
    sharpe_ratio: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(6, 2),
        nullable=True,
    )
    sortino_ratio: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(6, 2),
        nullable=True,
    )
    calmar_ratio: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(6, 2),
        nullable=True,
    )

    # Trade statistics
    avg_profit: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(12, 2),
        nullable=True,
    )
    avg_loss: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(12, 2),
        nullable=True,
    )
    profit_factor: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(6, 2),
        nullable=True,
    )
    avg_holding_days: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(6, 2),
        nullable=True,
    )

    # Detailed data as JSON
    equity_curve: Mapped[Optional[list]] = mapped_column(
        JSONType,
        nullable=True,
    )  # [{date, equity, drawdown}]
    monthly_returns: Mapped[Optional[list]] = mapped_column(
        JSONType,
        nullable=True,
    )  # [{month, return_pct}]

    created_at: Mapped[datetime] = mapped_column(
        DateTime,
        default=datetime.utcnow,
    )

    # Relationships
    run: Mapped["BacktestRun"] = relationship(
        "BacktestRun",
        back_populates="result",
    )

    def __repr__(self) -> str:
        return f"<BacktestResult run={self.run_id} pnl={self.total_pnl}>"
