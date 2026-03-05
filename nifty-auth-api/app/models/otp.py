import uuid
from datetime import datetime
from typing import Optional
from sqlalchemy import String, Integer, DateTime, Index
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class OTPRequest(Base):
    """
    OTP storage table - used as fallback when Redis is unavailable.
    For production, prefer Redis for OTP storage.
    """
    __tablename__ = "otp_requests"

    id: Mapped[str] = mapped_column(
        String(36),
        primary_key=True,
        default=lambda: str(uuid.uuid4()),
    )

    phone: Mapped[str] = mapped_column(
        String(15),
        nullable=False,
        index=True,
    )

    otp_hash: Mapped[str] = mapped_column(
        String(255),
        nullable=False,
    )

    purpose: Mapped[str] = mapped_column(
        String(20),
        default="login",
    )  # 'login', 'verify', 'reset'

    attempts: Mapped[int] = mapped_column(
        Integer,
        default=0,
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime,
        default=datetime.utcnow,
    )

    expires_at: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
    )

    verified_at: Mapped[Optional[datetime]] = mapped_column(
        DateTime,
        nullable=True,
    )

    __table_args__ = (
        Index("idx_otp_phone_purpose", "phone", "purpose"),
    )

    @property
    def is_expired(self) -> bool:
        """Check if OTP has expired."""
        return datetime.utcnow() > self.expires_at

    @property
    def is_verified(self) -> bool:
        """Check if OTP has been verified."""
        return self.verified_at is not None

    def __repr__(self) -> str:
        return f"<OTPRequest {self.id} phone={self.phone}>"
