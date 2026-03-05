import secrets
import hashlib
from datetime import datetime, timedelta
from typing import Optional
import redis.asyncio as redis
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, delete

from app.config import settings
from app.models.otp import OTPRequest


class OTPService:
    def __init__(self):
        self._redis: Optional[redis.Redis] = None

    async def get_redis(self) -> Optional[redis.Redis]:
        """Get Redis connection, return None if unavailable."""
        if self._redis is None:
            try:
                self._redis = redis.from_url(
                    settings.redis_url,
                    encoding="utf-8",
                    decode_responses=True,
                )
                await self._redis.ping()
            except Exception:
                self._redis = None
        return self._redis

    @staticmethod
    def generate_otp() -> str:
        """Generate a 6-digit OTP."""
        return f"{secrets.randbelow(1000000):06d}"

    @staticmethod
    def hash_otp(otp: str) -> str:
        """Hash OTP for storage."""
        return hashlib.sha256(otp.encode()).hexdigest()

    async def check_rate_limit(
        self,
        phone: str,
        db: Optional[AsyncSession] = None,
    ) -> tuple[bool, int]:
        """
        Check if phone has exceeded rate limit.
        Returns (is_allowed, seconds_until_next_allowed).
        """
        redis_client = await self.get_redis()
        rate_key = f"otp_rate:{phone}"

        if redis_client:
            # Use Redis for rate limiting
            count = await redis_client.get(rate_key)
            if count and int(count) >= settings.otp_rate_limit_per_hour:
                ttl = await redis_client.ttl(rate_key)
                return False, ttl if ttl > 0 else 0
            return True, 0
        elif db:
            # Fallback to database
            one_hour_ago = datetime.utcnow() - timedelta(hours=1)
            result = await db.execute(
                select(OTPRequest)
                .where(OTPRequest.phone == phone)
                .where(OTPRequest.created_at > one_hour_ago)
            )
            requests = result.scalars().all()
            if len(requests) >= settings.otp_rate_limit_per_hour:
                oldest = min(r.created_at for r in requests)
                seconds_until_allowed = int(
                    (oldest + timedelta(hours=1) - datetime.utcnow()).total_seconds()
                )
                return False, max(0, seconds_until_allowed)
            return True, 0

        return True, 0

    async def check_cooldown(
        self,
        phone: str,
        db: Optional[AsyncSession] = None,
    ) -> tuple[bool, int]:
        """
        Check if OTP resend cooldown has passed.
        Returns (is_allowed, seconds_remaining).
        """
        redis_client = await self.get_redis()
        cooldown_key = f"otp_cooldown:{phone}"

        if redis_client:
            ttl = await redis_client.ttl(cooldown_key)
            if ttl > 0:
                return False, ttl
            return True, 0
        elif db:
            cooldown_ago = datetime.utcnow() - timedelta(
                seconds=settings.otp_resend_cooldown_seconds
            )
            result = await db.execute(
                select(OTPRequest)
                .where(OTPRequest.phone == phone)
                .where(OTPRequest.created_at > cooldown_ago)
                .order_by(OTPRequest.created_at.desc())
                .limit(1)
            )
            recent = result.scalar_one_or_none()
            if recent:
                seconds_remaining = int(
                    (
                        recent.created_at
                        + timedelta(seconds=settings.otp_resend_cooldown_seconds)
                        - datetime.utcnow()
                    ).total_seconds()
                )
                return False, max(0, seconds_remaining)
            return True, 0

        return True, 0

    async def store_otp(
        self,
        phone: str,
        otp: str,
        purpose: str = "login",
        db: Optional[AsyncSession] = None,
    ) -> bool:
        """Store OTP in Redis (preferred) or database."""
        otp_hash = self.hash_otp(otp)
        expire_seconds = settings.otp_expire_minutes * 60
        redis_client = await self.get_redis()

        if redis_client:
            # Store in Redis
            otp_key = f"otp:{phone}:{purpose}"
            rate_key = f"otp_rate:{phone}"
            cooldown_key = f"otp_cooldown:{phone}"

            pipe = redis_client.pipeline()
            await pipe.setex(otp_key, expire_seconds, otp_hash)
            await pipe.setex(f"{otp_key}:attempts", expire_seconds, "0")
            await pipe.incr(rate_key)
            await pipe.expire(rate_key, 3600)  # 1 hour window
            await pipe.setex(cooldown_key, settings.otp_resend_cooldown_seconds, "1")
            await pipe.execute()
            return True
        elif db:
            # Store in database
            # Delete old unverified OTPs for this phone
            await db.execute(
                delete(OTPRequest)
                .where(OTPRequest.phone == phone)
                .where(OTPRequest.purpose == purpose)
                .where(OTPRequest.verified_at.is_(None))
            )

            otp_request = OTPRequest(
                phone=phone,
                otp_hash=otp_hash,
                purpose=purpose,
                expires_at=datetime.utcnow() + timedelta(seconds=expire_seconds),
            )
            db.add(otp_request)
            await db.commit()
            return True

        return False

    async def verify_otp(
        self,
        phone: str,
        otp: str,
        purpose: str = "login",
        db: Optional[AsyncSession] = None,
    ) -> tuple[bool, str]:
        """
        Verify OTP.
        Returns (is_valid, error_message).
        """
        otp_hash = self.hash_otp(otp)
        redis_client = await self.get_redis()

        if redis_client:
            otp_key = f"otp:{phone}:{purpose}"
            attempts_key = f"{otp_key}:attempts"

            # Check attempts
            attempts = await redis_client.get(attempts_key)
            if attempts and int(attempts) >= settings.otp_max_attempts:
                # Delete OTP after max attempts
                await redis_client.delete(otp_key, attempts_key)
                return False, "Maximum verification attempts exceeded"

            # Increment attempts
            await redis_client.incr(attempts_key)

            # Verify OTP
            stored_hash = await redis_client.get(otp_key)
            if not stored_hash:
                return False, "OTP expired or not found"

            if stored_hash != otp_hash:
                remaining = settings.otp_max_attempts - int(attempts or 0) - 1
                return False, f"Invalid OTP. {remaining} attempts remaining"

            # Delete OTP after successful verification
            await redis_client.delete(otp_key, attempts_key)
            return True, ""
        elif db:
            # Verify from database
            result = await db.execute(
                select(OTPRequest)
                .where(OTPRequest.phone == phone)
                .where(OTPRequest.purpose == purpose)
                .where(OTPRequest.verified_at.is_(None))
                .order_by(OTPRequest.created_at.desc())
                .limit(1)
            )
            otp_request = result.scalar_one_or_none()

            if not otp_request:
                return False, "OTP not found"

            if otp_request.is_expired:
                return False, "OTP expired"

            if otp_request.attempts >= settings.otp_max_attempts:
                return False, "Maximum verification attempts exceeded"

            # Increment attempts
            otp_request.attempts += 1

            if otp_request.otp_hash != otp_hash:
                remaining = settings.otp_max_attempts - otp_request.attempts
                await db.commit()
                return False, f"Invalid OTP. {remaining} attempts remaining"

            # Mark as verified
            otp_request.verified_at = datetime.utcnow()
            await db.commit()
            return True, ""

        return False, "OTP service unavailable"


otp_service = OTPService()
