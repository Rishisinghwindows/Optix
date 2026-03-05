"""
Trade Journal Schemas for API request/response validation
"""

from datetime import datetime
from typing import Optional, List
from pydantic import BaseModel, Field
from enum import Enum


class OptionTypeEnum(str, Enum):
    CE = "CE"
    PE = "PE"


class DirectionEnum(str, Enum):
    BUY = "buy"
    SELL = "sell"


class MarketConditionEnum(str, Enum):
    TRENDING_UP = "trending_up"
    TRENDING_DOWN = "trending_down"
    RANGE_BOUND = "range_bound"
    VOLATILE = "volatile"
    LOW_VOL = "low_vol"


class MoodEnum(str, Enum):
    CONFIDENT = "confident"
    NERVOUS = "nervous"
    NEUTRAL = "neutral"
    GREEDY = "greedy"
    FEARFUL = "fearful"
    DISCIPLINED = "disciplined"


class OutcomeEnum(str, Enum):
    PROFIT = "profit"
    LOSS = "loss"
    BREAKEVEN = "breakeven"


# ============== Create / Update ==============

class JournalCreate(BaseModel):
    """Schema for creating a journal entry"""
    symbol: str = Field(..., description="Index symbol (NIFTY, BANKNIFTY, etc.)")
    strike_price: float = Field(..., gt=0, description="Strike price")
    option_type: OptionTypeEnum = Field(..., description="CE or PE")
    direction: DirectionEnum = Field(..., description="buy or sell")
    entry_price: float = Field(..., gt=0, description="Entry premium")
    exit_price: Optional[float] = Field(None, ge=0, description="Exit premium")
    quantity: int = Field(..., gt=0, description="Number of lots")
    lot_size: int = Field(..., gt=0, description="Lot size")
    entry_date: datetime = Field(..., description="Entry timestamp")
    exit_date: Optional[datetime] = Field(None, description="Exit timestamp")
    expiry_date: Optional[str] = Field(None, description="Expiry date (dd-MMM-yyyy)")

    # Journal metadata
    title: Optional[str] = Field(None, max_length=200, description="Trade title")
    notes: Optional[str] = Field(None, description="Trade notes")
    tags: Optional[str] = Field(None, description="Comma-separated tags")
    market_condition: Optional[MarketConditionEnum] = None
    mood: Optional[MoodEnum] = None
    outcome: Optional[OutcomeEnum] = None
    paper_position_id: Optional[str] = None

    class Config:
        json_schema_extra = {
            "examples": [
                {
                    "symbol": "NIFTY",
                    "strike_price": 25000,
                    "option_type": "CE",
                    "direction": "buy",
                    "entry_price": 150.0,
                    "exit_price": 200.0,
                    "quantity": 1,
                    "lot_size": 75,
                    "entry_date": "2026-03-01T10:00:00",
                    "exit_date": "2026-03-01T14:00:00",
                    "title": "NIFTY breakout trade",
                    "tags": "directional,scalp",
                    "mood": "confident",
                    "outcome": "profit",
                }
            ]
        }


class JournalUpdate(BaseModel):
    """Schema for updating a journal entry"""
    exit_price: Optional[float] = Field(None, ge=0)
    exit_date: Optional[datetime] = None
    title: Optional[str] = Field(None, max_length=200)
    notes: Optional[str] = None
    tags: Optional[str] = None
    market_condition: Optional[MarketConditionEnum] = None
    mood: Optional[MoodEnum] = None
    outcome: Optional[OutcomeEnum] = None
    realized_pnl: Optional[float] = None
    realized_pnl_percent: Optional[float] = None


# ============== Response ==============

class JournalResponse(BaseModel):
    """Schema for journal entry response"""
    id: str
    user_id: str
    symbol: str
    strike_price: float
    option_type: str
    direction: str
    entry_price: float
    exit_price: Optional[float] = None
    quantity: int
    lot_size: int
    entry_date: datetime
    exit_date: Optional[datetime] = None
    expiry_date: Optional[str] = None
    realized_pnl: Optional[float] = None
    realized_pnl_percent: Optional[float] = None
    title: Optional[str] = None
    notes: Optional[str] = None
    tags: Optional[str] = None
    market_condition: Optional[str] = None
    mood: Optional[str] = None
    outcome: Optional[str] = None
    paper_position_id: Optional[str] = None
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True


class JournalListResponse(BaseModel):
    """Schema for listing journal entries"""
    entries: List[JournalResponse]
    total: int


class JournalStats(BaseModel):
    """Journal statistics"""
    total_trades: int
    winning_trades: int
    losing_trades: int
    breakeven_trades: int
    win_rate: float
    total_pnl: float
    average_pnl: float
    best_trade: Optional[float] = None
    worst_trade: Optional[float] = None
    average_holding_days: Optional[float] = None
    most_traded_symbol: Optional[str] = None
    most_used_tags: List[str] = []
    trades_by_mood: dict = {}
    trades_by_condition: dict = {}
