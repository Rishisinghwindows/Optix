from pydantic import BaseModel, Field, EmailStr
from typing import Optional
from datetime import datetime
import uuid


class UserResponse(BaseModel):
    id: uuid.UUID
    phone: Optional[str] = None
    email: Optional[str] = None
    full_name: Optional[str] = None
    avatar_url: Optional[str] = None
    auth_provider: str
    is_verified: bool
    paper_trading_balance: float
    is_premium: bool
    created_at: datetime
    last_login_at: Optional[datetime] = None

    class Config:
        from_attributes = True


class UserUpdate(BaseModel):
    full_name: Optional[str] = Field(None, max_length=100)
    email: Optional[EmailStr] = None
    avatar_url: Optional[str] = Field(None, max_length=500)


class DeviceInfoResponse(BaseModel):
    device_name: Optional[str] = None
    os: Optional[str] = None
    app_version: Optional[str] = None


class SessionResponse(BaseModel):
    id: uuid.UUID
    device_info: Optional[DeviceInfoResponse] = None
    ip_address: Optional[str] = None
    created_at: datetime
    expires_at: datetime
    is_current: bool = False

    class Config:
        from_attributes = True
