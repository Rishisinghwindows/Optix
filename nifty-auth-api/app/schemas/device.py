"""
Device Token Schemas for push notification registration
"""

from datetime import datetime
from typing import Optional, List
from pydantic import BaseModel, Field
from enum import Enum


class PlatformEnum(str, Enum):
    ANDROID = "android"
    IOS = "ios"
    WEB = "web"


class DeviceRegister(BaseModel):
    """Schema for registering a device token"""
    token: str = Field(..., min_length=10, description="FCM registration token")
    platform: PlatformEnum = Field(..., description="Device platform")
    device_name: Optional[str] = Field(None, max_length=100, description="Device name")


class DeviceResponse(BaseModel):
    """Schema for device token response"""
    id: str
    user_id: Optional[str] = None
    platform: str
    token: str
    device_name: Optional[str] = None
    is_active: bool
    created_at: datetime
    last_used_at: Optional[datetime] = None

    class Config:
        from_attributes = True


class DeviceListResponse(BaseModel):
    """Schema for listing devices"""
    devices: List[DeviceResponse]
    total: int
