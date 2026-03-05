from typing import Optional
import httpx
from twilio.rest import Client as TwilioClient

from app.config import settings


class SMSService:
    def __init__(self):
        self._twilio_client: Optional[TwilioClient] = None

    @property
    def twilio_client(self) -> Optional[TwilioClient]:
        """Get Twilio client, initialize if needed."""
        if self._twilio_client is None and settings.twilio_account_sid:
            self._twilio_client = TwilioClient(
                settings.twilio_account_sid,
                settings.twilio_auth_token,
            )
        return self._twilio_client

    async def send_otp_twilio(self, phone: str, otp: str) -> bool:
        """Send OTP via Twilio."""
        if not self.twilio_client:
            return False

        try:
            message = self.twilio_client.messages.create(
                body=f"Your Optix verification code is: {otp}. Valid for {settings.otp_expire_minutes} minutes.",
                from_=settings.twilio_phone_number,
                to=phone,
            )
            return message.sid is not None
        except Exception as e:
            print(f"Twilio error: {e}")
            return False

    async def send_otp_msg91(self, phone: str, otp: str) -> bool:
        """Send OTP via MSG91 (for Indian numbers)."""
        if not settings.msg91_auth_key:
            return False

        try:
            # Remove '+' from phone for MSG91
            phone_clean = phone.lstrip("+")

            async with httpx.AsyncClient() as client:
                response = await client.post(
                    "https://api.msg91.com/api/v5/otp",
                    headers={
                        "authkey": settings.msg91_auth_key,
                        "Content-Type": "application/json",
                    },
                    json={
                        "template_id": settings.msg91_template_id,
                        "mobile": phone_clean,
                        "otp": otp,
                    },
                )

                data = response.json()
                return data.get("type") == "success"
        except Exception as e:
            print(f"MSG91 error: {e}")
            return False

    async def send_otp(self, phone: str, otp: str) -> bool:
        """
        Send OTP using the appropriate provider.
        Uses MSG91 for Indian numbers (+91), Twilio for others.
        In development mode, just logs the OTP.
        """
        # In development, just log the OTP
        if settings.environment == "development":
            print(f"[DEV MODE] OTP for {phone}: {otp}")
            return True

        # Use MSG91 for Indian numbers
        if phone.startswith("+91") and settings.msg91_auth_key:
            return await self.send_otp_msg91(phone, otp)

        # Use Twilio for other numbers
        if self.twilio_client:
            return await self.send_otp_twilio(phone, otp)

        # No SMS provider configured
        print(f"[WARNING] No SMS provider configured. OTP for {phone}: {otp}")
        return True  # Return True for testing purposes


sms_service = SMSService()
