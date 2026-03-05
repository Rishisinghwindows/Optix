"""Pydantic schemas for IPO Dashboard API endpoints."""

from pydantic import BaseModel, Field
from typing import Optional, List
from datetime import datetime
from enum import Enum


class IPOStatus(str, Enum):
    """IPO status types."""
    UPCOMING = "upcoming"
    OPEN = "open"
    CLOSED = "closed"
    LISTED = "listed"
    ALLOTMENT = "allotment"


class IPOType(str, Enum):
    """IPO types."""
    MAINBOARD = "mainboard"
    SME = "sme"


class GMPData(BaseModel):
    """Grey Market Premium data."""
    gmp_value: Optional[float] = Field(None, description="GMP value in rupees")
    estimated_listing_price: Optional[float] = Field(None, description="Estimated listing price")
    listing_gain_pct: Optional[float] = Field(None, description="Expected listing gain percentage")


class IPOItem(BaseModel):
    """Single IPO item."""
    company_name: str = Field(..., description="Company name")
    slug: str = Field(..., description="URL-friendly slug")
    status: IPOStatus = Field(..., description="IPO status")
    ipo_type: IPOType = Field(default=IPOType.MAINBOARD, description="IPO type")

    # Dates
    open_date: Optional[str] = Field(None, description="IPO open date")
    close_date: Optional[str] = Field(None, description="IPO close date")
    listing_date: Optional[str] = Field(None, description="Listing date")

    # Price info
    price_band_low: Optional[float] = Field(None, description="Lower price band")
    price_band_high: Optional[float] = Field(None, description="Upper price band")
    lot_size: Optional[int] = Field(None, description="Lot size")
    issue_size_cr: Optional[str] = Field(None, description="Issue size in crores (may be a range)")
    min_investment: Optional[float] = Field(None, description="Minimum investment required")

    # Exchange and GMP
    exchange: Optional[str] = Field(None, description="Exchange (NSE/BSE)")
    gmp: Optional[GMPData] = Field(None, description="Grey Market Premium data")

    # AI analysis
    ai_verdict: Optional[str] = Field(None, description="AI verdict: Subscribe/Avoid/Neutral")
    ai_analysis: Optional[str] = Field(None, description="AI analysis summary")

    class Config:
        from_attributes = True


class IPOListResponse(BaseModel):
    """Response for IPO list endpoint."""
    ipos: List[IPOItem] = Field(default_factory=list)
    total: int = Field(default=0)
    last_updated: Optional[str] = Field(None, description="Last data update timestamp")
    source: Optional[str] = Field(None, description="Data source")


class IPOAnalysisRequest(BaseModel):
    """Request for AI analysis of an IPO."""
    company_name: str = Field(..., description="Company name to analyze")


class IPOAnalysisResponse(BaseModel):
    """Response for AI analysis of an IPO."""
    verdict: str = Field(..., description="Subscribe/Avoid/Neutral")
    confidence: int = Field(..., ge=0, le=100, description="Confidence level 0-100")
    analysis: str = Field(..., description="Detailed analysis in markdown")
    key_positives: List[str] = Field(default_factory=list, description="Key positive factors")
    key_risks: List[str] = Field(default_factory=list, description="Key risk factors")
    expected_listing_gain: Optional[str] = Field(None, description="Expected listing gain")
    recommendation_summary: str = Field(..., description="Summary recommendation")
    disclaimer: str = Field(
        default="This is AI-generated analysis for informational purposes only. Not financial advice. Do your own research before investing.",
        description="Disclaimer"
    )


class GMPListResponse(BaseModel):
    """Response for GMP-only endpoint."""
    ipos: List[dict] = Field(default_factory=list)
    last_updated: Optional[str] = None
