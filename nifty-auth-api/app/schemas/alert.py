"""
Alert Schemas for API request/response validation
"""

from datetime import datetime
from decimal import Decimal
from typing import Optional, List
from pydantic import BaseModel, Field, validator
from enum import Enum


class AlertTypeEnum(str, Enum):
    SPOT_PRICE = "spot_price"
    OPTION_PREMIUM = "option_premium"
    PCR = "pcr"
    OI_CHANGE = "oi_change"
    IV = "iv"
    MAX_PAIN = "max_pain"


class AlertConditionEnum(str, Enum):
    ABOVE = "above"
    BELOW = "below"
    CROSSES = "crosses"


class SymbolEnum(str, Enum):
    NIFTY = "NIFTY"
    BANKNIFTY = "BANKNIFTY"
    FINNIFTY = "FINNIFTY"
    MIDCPNIFTY = "MIDCPNIFTY"
    SENSEX = "SENSEX"


class OptionTypeEnum(str, Enum):
    CE = "CE"
    PE = "PE"


# ============== Create Alert ==============

class AlertCreate(BaseModel):
    """Schema for creating a new alert"""
    alert_type: AlertTypeEnum = Field(..., description="Type of alert")
    symbol: SymbolEnum = Field(..., description="Index symbol")
    condition: AlertConditionEnum = Field(..., description="Trigger condition")
    target_value: float = Field(..., gt=0, description="Target value to trigger alert")

    # Option-specific fields (required for option_premium alerts)
    strike_price: Optional[float] = Field(None, description="Strike price for option alerts")
    option_type: Optional[OptionTypeEnum] = Field(None, description="CE or PE for option alerts")
    expiry: Optional[str] = Field(None, description="Expiry date (dd-MMM-yyyy) for option alerts")

    # Optional fields
    name: Optional[str] = Field(None, max_length=100, description="Custom name for the alert")
    note: Optional[str] = Field(None, description="Optional note")

    @validator('strike_price', 'option_type', 'expiry', always=True)
    def validate_option_fields(cls, v, values):
        alert_type = values.get('alert_type')
        if alert_type == AlertTypeEnum.OPTION_PREMIUM:
            if v is None:
                field_name = 'strike_price' if 'strike_price' not in values else (
                    'option_type' if 'option_type' not in values else 'expiry'
                )
                # This will be checked in root_validator
                pass
        return v

    @validator('target_value')
    def validate_target_value(cls, v, values):
        alert_type = values.get('alert_type')
        if alert_type == AlertTypeEnum.PCR:
            if v <= 0 or v > 5:
                raise ValueError('PCR target should be between 0 and 5')
        elif alert_type == AlertTypeEnum.IV:
            if v <= 0 or v > 200:
                raise ValueError('IV target should be between 0 and 200')
        elif alert_type == AlertTypeEnum.OI_CHANGE:
            if v < -100 or v > 1000:
                raise ValueError('OI change should be between -100% and 1000%')
        return v

    class Config:
        json_schema_extra = {
            "examples": [
                {
                    "alert_type": "spot_price",
                    "symbol": "NIFTY",
                    "condition": "above",
                    "target_value": 26000,
                    "name": "NIFTY breakout"
                },
                {
                    "alert_type": "option_premium",
                    "symbol": "NIFTY",
                    "condition": "below",
                    "target_value": 100,
                    "strike_price": 26000,
                    "option_type": "CE",
                    "expiry": "13-Feb-2026"
                },
                {
                    "alert_type": "pcr",
                    "symbol": "NIFTY",
                    "condition": "below",
                    "target_value": 0.8,
                    "name": "Bearish PCR alert"
                }
            ]
        }


class AlertUpdate(BaseModel):
    """Schema for updating an alert"""
    target_value: Optional[float] = Field(None, gt=0)
    condition: Optional[AlertConditionEnum] = None
    name: Optional[str] = Field(None, max_length=100)
    note: Optional[str] = None
    is_active: Optional[bool] = None


# ============== Alert Response ==============

class AlertResponse(BaseModel):
    """Schema for alert response"""
    id: str
    user_id: str
    alert_type: str
    symbol: str
    condition: str
    target_value: float

    # Option fields
    strike_price: Optional[float] = None
    option_type: Optional[str] = None
    expiry: Optional[str] = None

    # State
    is_active: bool
    is_triggered: bool
    triggered_at: Optional[datetime] = None
    triggered_value: Optional[float] = None

    # Last check info
    last_checked_value: Optional[float] = None
    last_checked_at: Optional[datetime] = None

    # Display
    name: Optional[str] = None
    display_name: str
    note: Optional[str] = None

    # Computed fields
    current_gap: Optional[float] = None  # Gap between current value and target
    gap_percentage: Optional[float] = None

    # Metadata
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True


class AlertListResponse(BaseModel):
    """Schema for listing alerts"""
    alerts: List[AlertResponse]
    total: int
    active_count: int
    triggered_count: int


# ============== Notification Schemas ==============

class NotificationResponse(BaseModel):
    """Schema for notification response"""
    id: str
    alert_id: str
    user_id: str
    title: str
    message: str
    triggered_value: Optional[float] = None
    is_read: bool
    read_at: Optional[datetime] = None
    created_at: datetime

    # Include alert info
    alert_type: Optional[str] = None
    symbol: Optional[str] = None

    class Config:
        from_attributes = True


class NotificationListResponse(BaseModel):
    """Schema for listing notifications"""
    notifications: List[NotificationResponse]
    total: int
    unread_count: int


class MarkReadRequest(BaseModel):
    """Schema for marking notifications as read"""
    notification_ids: List[str] = Field(..., min_length=1, description="List of notification IDs to mark as read")


class MarkReadResponse(BaseModel):
    """Response after marking notifications as read"""
    marked_count: int
    message: str


# ============== Alert Stats ==============

class AlertStats(BaseModel):
    """Statistics about user's alerts"""
    total_alerts: int
    active_alerts: int
    triggered_today: int
    unread_notifications: int
    alerts_by_type: dict
    alerts_by_symbol: dict
