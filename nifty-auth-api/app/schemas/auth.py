from pydantic import BaseModel, Field, field_validator
from typing import Optional
import phonenumbers
import re


class DeviceInfo(BaseModel):
    device_name: Optional[str] = Field(None, max_length=100)
    os: Optional[str] = Field(None, max_length=50)
    app_version: Optional[str] = Field(None, max_length=20)


class OTPSendRequest(BaseModel):
    phone: str = Field(..., description="Phone number with country code")
    purpose: str = Field(default="login", pattern="^(login|verify|reset)$")

    @field_validator("phone")
    @classmethod
    def validate_phone(cls, v: str) -> str:
        # Remove any spaces or dashes
        cleaned = re.sub(r"[\s\-]", "", v)

        # Ensure it starts with +
        if not cleaned.startswith("+"):
            cleaned = "+" + cleaned

        try:
            parsed = phonenumbers.parse(cleaned, None)
            if not phonenumbers.is_valid_number(parsed):
                raise ValueError("Invalid phone number")
            return phonenumbers.format_number(
                parsed, phonenumbers.PhoneNumberFormat.E164
            )
        except phonenumbers.NumberParseException:
            raise ValueError("Invalid phone number format")


class OTPSendResponse(BaseModel):
    success: bool
    message: str
    expires_in: int = Field(description="OTP expiry time in seconds")
    resend_after: int = Field(description="Seconds before next OTP can be sent")


class OTPVerifyRequest(BaseModel):
    phone: str = Field(..., description="Phone number with country code")
    otp: str = Field(..., min_length=6, max_length=6, pattern="^[0-9]{6}$")
    device_info: Optional[DeviceInfo] = None

    @field_validator("phone")
    @classmethod
    def validate_phone(cls, v: str) -> str:
        cleaned = re.sub(r"[\s\-]", "", v)
        if not cleaned.startswith("+"):
            cleaned = "+" + cleaned

        try:
            parsed = phonenumbers.parse(cleaned, None)
            if not phonenumbers.is_valid_number(parsed):
                raise ValueError("Invalid phone number")
            return phonenumbers.format_number(
                parsed, phonenumbers.PhoneNumberFormat.E164
            )
        except phonenumbers.NumberParseException:
            raise ValueError("Invalid phone number format")


class SocialLoginRequest(BaseModel):
    id_token: Optional[str] = Field(None, description="ID token from provider")
    access_token: Optional[str] = Field(None, description="Access token (Facebook)")
    authorization_code: Optional[str] = Field(None, description="Auth code (Apple)")
    device_info: Optional[DeviceInfo] = None

    @field_validator("id_token", "access_token", "authorization_code")
    @classmethod
    def validate_token(cls, v: Optional[str]) -> Optional[str]:
        if v is not None and len(v) < 10:
            raise ValueError("Token too short")
        return v


class UserInAuth(BaseModel):
    id: str
    phone: Optional[str] = None
    email: Optional[str] = None
    full_name: Optional[str] = None
    avatar_url: Optional[str] = None
    is_verified: bool
    paper_trading_balance: float
    is_premium: bool

    class Config:
        from_attributes = True


class AuthResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    expires_in: int = Field(description="Access token expiry in seconds")
    user: UserInAuth


class RefreshTokenRequest(BaseModel):
    refresh_token: str = Field(..., min_length=10)


class RefreshTokenResponse(BaseModel):
    access_token: str
    expires_in: int = Field(description="Access token expiry in seconds")
