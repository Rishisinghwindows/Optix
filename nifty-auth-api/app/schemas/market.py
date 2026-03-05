"""
Market Data Schemas - Pydantic models for option chain and market data
"""

from typing import Optional, List, Dict, Any
from pydantic import BaseModel, Field
from datetime import datetime


class OptionData(BaseModel):
    """Single option (CE or PE) data"""
    openInterest: int = Field(default=0, description="Open Interest")
    changeinOpenInterest: int = Field(default=0, description="Change in OI")
    totalTradedVolume: int = Field(default=0, description="Total traded volume")
    impliedVolatility: float = Field(default=0, description="Implied Volatility")
    lastPrice: float = Field(default=0, description="Last traded price")
    change: float = Field(default=0, description="Price change")
    pChange: float = Field(default=0, description="Percentage change")
    bidQty: int = Field(default=0, description="Bid quantity")
    bidprice: float = Field(default=0, description="Bid price")
    askQty: int = Field(default=0, description="Ask quantity")
    askPrice: float = Field(default=0, description="Ask price")


class StrikeData(BaseModel):
    """Option chain data for a single strike"""
    strikePrice: float
    expiryDate: str
    CE: Optional[OptionData] = None
    PE: Optional[OptionData] = None


class OptionTotals(BaseModel):
    """Total OI and Volume for CE/PE"""
    totalOI: int = 0
    totalVolume: int = 0


class OptionChainTotals(BaseModel):
    """Combined totals"""
    CE: OptionTotals
    PE: OptionTotals


class OptionChainResponse(BaseModel):
    """Complete option chain response"""
    symbol: str
    name: str
    lotSize: int
    underlyingValue: float
    atmStrike: float
    expiryDates: List[str]
    strikePrices: List[float]
    timestamp: str
    data: List[StrikeData]
    totals: OptionChainTotals
    fetchedAt: str
    isLive: Optional[bool] = None
    isDemo: Optional[bool] = None
    selectedExpiry: Optional[str] = None
    strikesAroundATM: Optional[int] = None


class SpotPriceResponse(BaseModel):
    """Spot price response for an index"""
    symbol: str
    name: str
    lastPrice: float
    change: float
    pChange: float
    open: float
    high: float
    low: float
    previousClose: float
    timestamp: str
    isLive: Optional[bool] = None
    isDemo: Optional[bool] = None
    dataSource: Optional[str] = None


class ExpiryDatesResponse(BaseModel):
    """Available expiry dates"""
    symbol: str
    expiryDates: List[str]


class SupportedIndicesResponse(BaseModel):
    """List of supported indices"""
    indices: List[Dict[str, Any]]


class MarketStatusResponse(BaseModel):
    """Market status"""
    isOpen: bool
    message: str
    nextOpen: Optional[str] = None
    timestamp: str


class GreeksRequest(BaseModel):
    """Request for Greeks calculation"""
    spotPrice: float = Field(..., gt=0, description="Current spot price")
    strikePrice: float = Field(..., gt=0, description="Strike price")
    timeToExpiry: float = Field(..., gt=0, description="Time to expiry in years")
    volatility: float = Field(..., gt=0, le=500, description="Implied volatility in %")
    riskFreeRate: float = Field(default=6.5, description="Risk-free rate in %")
    optionType: str = Field(..., pattern="^(CE|PE|CALL|PUT)$", description="Option type")


class GreeksResponse(BaseModel):
    """Calculated Greeks response"""
    theoreticalPrice: float
    intrinsicValue: float
    timeValue: float
    delta: float
    gamma: float
    theta: float
    vega: float
    rho: float
    moneyness: str  # ITM, ATM, OTM
