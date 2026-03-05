"""
Pydantic schemas for Algo Trading API
"""
from pydantic import BaseModel, Field
from typing import Optional, List
from datetime import datetime
from enum import Enum


class SignalType(str, Enum):
    STRONG_BUY = "STRONG_BUY"
    BUY = "BUY"
    HOLD = "HOLD"
    SELL = "SELL"
    STRONG_SELL = "STRONG_SELL"


class AlgoOrderStatus(str, Enum):
    PENDING = "pending"
    PLACED = "placed"
    EXECUTED = "executed"
    PARTIALLY_FILLED = "partially_filled"
    CANCELLED = "cancelled"
    REJECTED = "rejected"
    FAILED = "failed"


class AlgoPositionStatus(str, Enum):
    OPEN = "open"
    CLOSED = "closed"
    STOPPED_OUT = "stopped_out"
    TARGET_HIT = "target_hit"
    TRAILING_STOPPED = "trailing_stopped"
    TIME_EXIT = "time_exit"


class OptionType(str, Enum):
    CE = "CE"
    PE = "PE"


class ExitReason(str, Enum):
    STOP_LOSS = "stop_loss"
    TARGET = "target"
    TRAILING_STOP = "trailing_stop"
    TIME_EXIT = "time_exit"
    MANUAL = "manual"
    KILL_SWITCH = "kill_switch"
    DAILY_LOSS_LIMIT = "daily_loss_limit"


# ==================== Config Schemas ====================

class AlgoConfigBase(BaseModel):
    name: str = Field(default="Default Strategy", description="Strategy name")
    is_paper_mode: bool = Field(default=True, description="Paper trading mode")

    # Capital and risk
    capital: float = Field(default=100000.0, ge=10000, description="Trading capital in INR")
    risk_per_trade: float = Field(default=0.02, ge=0.005, le=0.1, description="Risk per trade (0.02 = 2%)")
    max_daily_loss: float = Field(default=0.05, ge=0.01, le=0.2, description="Max daily loss (0.05 = 5%)")
    max_positions: int = Field(default=2, ge=1, le=10, description="Max concurrent positions")
    max_lots_per_trade: int = Field(default=10, ge=1, le=50, description="Max lots per trade")

    # Stop loss and target
    stop_loss_pct: float = Field(default=0.30, ge=0.1, le=0.5, description="Stop loss percentage of premium")
    target_pct: float = Field(default=0.50, ge=0.2, le=1.0, description="Target profit percentage")
    trailing_trigger_pct: float = Field(default=0.25, ge=0.1, le=0.5, description="Trailing stop trigger")
    trailing_stop_pct: float = Field(default=0.15, ge=0.05, le=0.3, description="Trailing stop percentage")

    # Signal settings
    signal_threshold: str = Field(default="STRONG", description="STRONG or ALL signals")
    signal_score_threshold: float = Field(default=0.35, ge=0.1, le=0.5, description="Min signal score")

    # Trading hours
    market_start_time: str = Field(default="09:20", description="Start trading time (HH:MM)")
    market_end_time: str = Field(default="15:15", description="End trading time (HH:MM)")
    no_entry_after: str = Field(default="15:00", description="No new entries after (HH:MM)")

    # Indices
    indices: List[str] = Field(default=["NIFTY", "BANKNIFTY"], description="Indices to trade")


class AlgoConfigCreate(AlgoConfigBase):
    pass


class AlgoConfigUpdate(BaseModel):
    name: Optional[str] = None
    is_paper_mode: Optional[bool] = None
    capital: Optional[float] = None
    risk_per_trade: Optional[float] = None
    max_daily_loss: Optional[float] = None
    max_positions: Optional[int] = None
    max_lots_per_trade: Optional[int] = None
    stop_loss_pct: Optional[float] = None
    target_pct: Optional[float] = None
    trailing_trigger_pct: Optional[float] = None
    trailing_stop_pct: Optional[float] = None
    signal_threshold: Optional[str] = None
    signal_score_threshold: Optional[float] = None
    market_start_time: Optional[str] = None
    market_end_time: Optional[str] = None
    no_entry_after: Optional[str] = None
    indices: Optional[List[str]] = None


class AlgoConfigResponse(AlgoConfigBase):
    id: str
    is_active: bool
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True


# ==================== Position Schemas ====================

class AlgoPositionBase(BaseModel):
    symbol: str
    strike_price: float
    option_type: OptionType
    expiry_date: str
    quantity: int
    lot_size: int


class AlgoPositionResponse(AlgoPositionBase):
    id: str
    entry_price: float
    current_price: Optional[float]
    highest_price: Optional[float]

    stop_loss_price: float
    target_price: float
    trailing_stop_active: bool
    trailing_stop_price: Optional[float]

    status: AlgoPositionStatus
    exit_price: Optional[float]
    exit_reason: Optional[ExitReason]

    entry_signal: Optional[SignalType]
    entry_signal_score: Optional[float]

    instrument_key: Optional[str]
    trading_symbol: Optional[str]
    is_paper: bool

    opened_at: datetime
    closed_at: Optional[datetime]

    # Computed fields
    invested_amount: float
    current_value: float
    unrealized_pnl: float
    unrealized_pnl_percent: float

    class Config:
        from_attributes = True


# ==================== Trade Schemas ====================

class AlgoTradeResponse(BaseModel):
    id: str
    position_id: Optional[str]
    symbol: str
    strike_price: float
    option_type: OptionType
    expiry_date: str

    order_id: Optional[str]
    order_status: AlgoOrderStatus
    trade_type: str

    quantity: int
    lot_size: int
    order_price: Optional[float]
    executed_price: Optional[float]

    signal_type: Optional[SignalType]
    signal_score: Optional[float]

    pnl: Optional[float]
    pnl_percent: Optional[float]
    exit_reason: Optional[ExitReason]

    is_paper: bool
    created_at: datetime
    executed_at: Optional[datetime]
    error_message: Optional[str]

    class Config:
        from_attributes = True


# ==================== Signal Schemas ====================

class SignalResponse(BaseModel):
    symbol: str
    signal_type: SignalType
    signal_score: float
    pcr: float
    max_pain: float
    vix: Optional[float]
    spot_price: float
    atm_strike: float

    recommended_option_type: OptionType
    recommended_strike: float
    recommended_expiry: str

    timestamp: datetime


# ==================== Status Schemas ====================

class AlgoStatusResponse(BaseModel):
    is_running: bool
    is_paper_mode: bool
    config_id: Optional[str]

    # Market status
    is_market_hours: bool
    current_time: str
    market_start: str
    market_end: str

    # Position summary
    open_positions: int
    max_positions: int
    total_invested: float
    total_unrealized_pnl: float

    # Daily stats
    daily_trades: int
    daily_pnl: float
    daily_pnl_percent: float
    daily_loss_limit_hit: bool

    # Last activity
    last_signal: Optional[SignalResponse]
    last_trade_time: Optional[datetime]

    # Capital
    available_capital: float
    total_capital: float


class AlgoStartRequest(BaseModel):
    config_id: Optional[str] = None
    paper_mode: bool = Field(default=True, description="Run in paper trading mode")


class AlgoStopRequest(BaseModel):
    close_all_positions: bool = Field(default=False, description="Close all open positions")
    reason: Optional[str] = Field(default=None, description="Reason for stopping")


# ==================== Log Schemas ====================

class AlgoLogResponse(BaseModel):
    id: str
    level: str
    category: str
    message: str
    context: Optional[dict]
    created_at: datetime

    class Config:
        from_attributes = True


# ==================== Daily Stats Schemas ====================

class AlgoDailyStatsResponse(BaseModel):
    id: str
    trading_date: str

    total_trades: int
    winning_trades: int
    losing_trades: int

    gross_pnl: float
    charges: float
    net_pnl: float

    max_drawdown: float
    signals_generated: int
    signals_acted_on: int

    starting_capital: float
    ending_capital: float

    win_rate: float = Field(default=0.0)

    class Config:
        from_attributes = True


# ==================== Manual Trade Schemas ====================

class ManualEntryRequest(BaseModel):
    symbol: str = Field(..., description="Index symbol (NIFTY/BANKNIFTY)")
    strike_price: float = Field(..., description="Strike price")
    option_type: OptionType = Field(..., description="CE or PE")
    expiry_date: str = Field(..., description="Expiry date (dd-MMM-yyyy)")
    quantity: int = Field(default=1, ge=1, description="Number of lots")
    paper_mode: bool = Field(default=True, description="Paper trade or live")


class ManualExitRequest(BaseModel):
    position_id: str = Field(..., description="Position ID to exit")
    reason: Optional[str] = Field(default="manual", description="Exit reason")
