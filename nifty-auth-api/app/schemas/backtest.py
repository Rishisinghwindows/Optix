"""Pydantic schemas for backtest API endpoints."""

from pydantic import BaseModel, Field, field_validator
from typing import Optional, List, Dict, Any
from datetime import date, datetime
from decimal import Decimal
from enum import Enum


class StrategyType(str, Enum):
    """Types of trading strategies."""
    IRON_CONDOR = "iron_condor"
    STRADDLE = "straddle"
    STRANGLE = "strangle"
    BULL_CALL_SPREAD = "bull_call_spread"
    BEAR_PUT_SPREAD = "bear_put_spread"
    BUTTERFLY = "butterfly"
    MOMENTUM = "momentum"
    CUSTOM = "custom"


class BacktestStatus(str, Enum):
    """Status of a backtest run."""
    PENDING = "pending"
    RUNNING = "running"
    COMPLETED = "completed"
    FAILED = "failed"
    CANCELLED = "cancelled"


class ExitReason(str, Enum):
    """Reasons for exiting a trade."""
    TARGET = "target"
    STOPLOSS = "stoploss"
    EXPIRY = "expiry"
    SIGNAL = "signal"
    TIME_EXIT = "time_exit"
    HOLDING_PERIOD = "holding_period"
    MANUAL = "manual"


# ==================== Index Schemas ====================

class IndexBase(BaseModel):
    """Base index schema."""
    symbol: str
    name: str
    exchange: str
    lot_size: int
    data_available_from: Optional[date] = None
    is_active: bool = True


class IndexResponse(IndexBase):
    """Index response schema."""
    id: str

    class Config:
        from_attributes = True


# ==================== Historical Data Schemas ====================

class SpotPriceBase(BaseModel):
    """Base spot price schema."""
    symbol: str
    exchange: str
    date: date
    open: Optional[float] = None
    high: Optional[float] = None
    low: Optional[float] = None
    close: Optional[float] = None
    volume: Optional[int] = None


class SpotPriceResponse(SpotPriceBase):
    """Spot price response schema."""
    id: str

    class Config:
        from_attributes = True


class OptionDataBase(BaseModel):
    """Base option data schema."""
    symbol: str
    exchange: str
    date: date
    expiry: date
    strike: float
    option_type: str
    open: Optional[float] = None
    high: Optional[float] = None
    low: Optional[float] = None
    close: Optional[float] = None
    settle_price: Optional[float] = None
    volume: Optional[int] = None
    open_interest: Optional[int] = None
    iv: Optional[float] = None
    delta: Optional[float] = None
    gamma: Optional[float] = None
    theta: Optional[float] = None
    vega: Optional[float] = None


class OptionDataResponse(OptionDataBase):
    """Option data response schema."""
    id: str

    class Config:
        from_attributes = True


class HistoricalSpotRequest(BaseModel):
    """Request for historical spot prices."""
    symbol: str = Field(..., description="Index symbol (e.g., 'NIFTY')")
    start_date: date = Field(..., description="Start date")
    end_date: date = Field(..., description="End date")

    @field_validator("end_date")
    @classmethod
    def validate_dates(cls, v, info):
        if info.data.get("start_date") and v < info.data["start_date"]:
            raise ValueError("end_date must be after start_date")
        return v


class HistoricalOptionsRequest(BaseModel):
    """Request for historical options data."""
    symbol: str = Field(..., description="Index symbol (e.g., 'NIFTY')")
    trade_date: date = Field(..., description="Trading date")
    expiry: Optional[date] = Field(None, description="Filter by expiry date")
    strike: Optional[float] = Field(None, description="Filter by strike price")
    option_type: Optional[str] = Field(None, pattern="^(CE|PE)$")


class ExpiryListResponse(BaseModel):
    """Response for available expiry dates."""
    symbol: str
    expiries: List[date]


# ==================== Strategy Schemas ====================

class EntryCondition(BaseModel):
    """Entry condition for a strategy."""
    type: str = Field(..., description="Condition type (e.g., 'iv_rank', 'spot_price')")
    operator: str = Field(..., description="Comparison operator")
    value: Any = Field(..., description="Value to compare against")


class EntryRules(BaseModel):
    """Entry rules configuration."""
    conditions: List[EntryCondition] = []
    logic: str = Field(default="AND", pattern="^(AND|OR)$")
    time_filter: Optional[Dict[str, Any]] = None
    iv_rank_min: Optional[float] = None
    iv_rank_max: Optional[float] = None
    days_to_expiry: Optional[List[int]] = None
    wing_width: Optional[int] = None
    delta_target: Optional[float] = None


class ExitRules(BaseModel):
    """Exit rules configuration."""
    profit_target_pct: Optional[float] = Field(None, description="Take profit percentage")
    stop_loss_pct: Optional[float] = Field(None, description="Stop loss percentage")
    max_loss: Optional[float] = Field(None, description="Max loss in absolute terms")
    days_before_expiry_exit: Optional[int] = Field(None, description="Exit N days before expiry")
    trailing_stop: Optional[Dict[str, Any]] = None
    time_exit: Optional[Dict[str, Any]] = None


class PositionSizingConfig(BaseModel):
    """Position sizing configuration."""
    type: str = Field(default="fixed", description="Sizing type: 'fixed' or 'percent'")
    value: float = Field(..., description="Size value (lots or percentage)")
    max_positions: int = Field(default=5, description="Maximum concurrent positions")


class StrategyCreateRequest(BaseModel):
    """Request to create a new strategy."""
    name: str = Field(..., min_length=1, max_length=100)
    description: Optional[str] = None
    strategy_type: StrategyType
    entry_rules: Dict[str, Any]
    exit_rules: Dict[str, Any]
    position_sizing: Optional[Dict[str, Any]] = None


class StrategyUpdateRequest(BaseModel):
    """Request to update a strategy."""
    name: Optional[str] = Field(None, min_length=1, max_length=100)
    description: Optional[str] = None
    entry_rules: Optional[Dict[str, Any]] = None
    exit_rules: Optional[Dict[str, Any]] = None
    position_sizing: Optional[Dict[str, Any]] = None


class StrategyResponse(BaseModel):
    """Strategy response schema."""
    id: str
    user_id: Optional[str]
    name: str
    description: Optional[str]
    strategy_type: str
    entry_rules: Dict[str, Any]
    exit_rules: Dict[str, Any]
    position_sizing: Optional[Dict[str, Any]]
    is_template: bool
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True


class StrategyListResponse(BaseModel):
    """Response for strategy list."""
    strategies: List[StrategyResponse]
    total: int


# ==================== Backtest Run Schemas ====================

class BacktestRunRequest(BaseModel):
    """Request to start a backtest run."""
    strategy_id: Optional[str] = Field(None, description="Strategy ID (optional for custom)")
    strategy_config: Optional[Dict[str, Any]] = Field(None, description="Custom strategy config")
    symbol: str = Field(..., description="Index symbol to backtest")
    start_date: date = Field(..., description="Backtest start date")
    end_date: date = Field(..., description="Backtest end date")
    initial_capital: float = Field(default=1000000, gt=0)
    lot_size: int = Field(default=25, gt=0)
    max_positions: int = Field(default=5, gt=0)
    position_size_pct: Optional[float] = Field(None, gt=0, le=100)
    max_loss_per_trade: Optional[float] = None
    max_daily_loss: Optional[float] = None
    stop_loss_pct: Optional[float] = None
    take_profit_pct: Optional[float] = None

    @field_validator("end_date")
    @classmethod
    def validate_dates(cls, v, info):
        if info.data.get("start_date") and v <= info.data["start_date"]:
            raise ValueError("end_date must be after start_date")
        return v


class BacktestRunResponse(BaseModel):
    """Response for a backtest run."""
    id: str
    user_id: str
    strategy_id: Optional[str]
    symbol: str
    start_date: date
    end_date: date
    initial_capital: float
    lot_size: int
    max_positions: int
    position_size_pct: Optional[float]
    status: str
    progress: int
    error_message: Optional[str]
    created_at: datetime
    started_at: Optional[datetime]
    completed_at: Optional[datetime]

    class Config:
        from_attributes = True


class BacktestRunListResponse(BaseModel):
    """Response for backtest run list."""
    runs: List[BacktestRunResponse]
    total: int


# ==================== Trade Schemas ====================

class TradeResponse(BaseModel):
    """Response for a backtest trade."""
    id: str
    run_id: str
    entry_date: date
    exit_date: Optional[date]
    expiry: date
    strike: float
    option_type: str
    action: str
    quantity: int
    entry_price: float
    exit_price: Optional[float]
    entry_iv: Optional[float]
    entry_delta: Optional[float]
    realized_pnl: Optional[float]
    unrealized_pnl: Optional[float]
    exit_reason: Optional[str]
    created_at: datetime

    class Config:
        from_attributes = True


class TradeListResponse(BaseModel):
    """Response for trade list."""
    trades: List[TradeResponse]
    total: int


# ==================== Result Schemas ====================

class EquityCurvePoint(BaseModel):
    """Single point in equity curve."""
    date: str
    equity: float
    drawdown: float


class MonthlyReturn(BaseModel):
    """Monthly return data."""
    month: str
    return_pct: float


class BacktestResultResponse(BaseModel):
    """Response for backtest results."""
    id: str
    run_id: str

    # Performance metrics
    total_trades: int
    winning_trades: int
    losing_trades: int
    win_rate: Optional[float]

    # Returns
    total_pnl: Optional[float]
    total_return_pct: Optional[float]
    cagr: Optional[float]

    # Risk metrics
    max_drawdown: Optional[float]
    max_drawdown_pct: Optional[float]
    sharpe_ratio: Optional[float]
    sortino_ratio: Optional[float]
    calmar_ratio: Optional[float]

    # Trade statistics
    avg_profit: Optional[float]
    avg_loss: Optional[float]
    profit_factor: Optional[float]
    avg_holding_days: Optional[float]

    # Charts data
    equity_curve: Optional[List[Dict[str, Any]]]
    monthly_returns: Optional[List[Dict[str, Any]]]

    created_at: datetime

    class Config:
        from_attributes = True


class BacktestRunWithResultResponse(BacktestRunResponse):
    """Response for backtest run with results included."""
    result: Optional[BacktestResultResponse] = None
    strategy: Optional[StrategyResponse] = None


# ==================== Summary Response ====================

class BacktestSummaryResponse(BaseModel):
    """Summary of a completed backtest."""
    run: BacktestRunResponse
    result: BacktestResultResponse
    strategy: Optional[StrategyResponse]
    trades_summary: Dict[str, Any]
