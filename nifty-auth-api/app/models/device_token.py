"""
Device Token Model — FCM token storage for push notifications.
"""

import uuid
from datetime import datetime
from typing import Optional, TYPE_CHECKING
from sqlalchemy import String, Boolean, DateTime, ForeignKey, Index
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base

if TYPE_CHECKING:
    from app.models.user import User


class DeviceToken(Base):
    """
    Device Token Model

    Stores Firebase Cloud Messaging (FCM) tokens for push notifications.
    Each user can have multiple devices registered.
    """
    __tablename__ = "device_tokens"

    id: Mapped[str] = mapped_column(
        String(36),
        primary_key=True,
        default=lambda: str(uuid.uuid4()),
    )

    user_id: Mapped[Optional[str]] = mapped_column(
        String(36),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=True,
        index=True,
    )

    platform: Mapped[str] = mapped_column(
        String(10),
        nullable=False,
    )  # android, ios, web

    token: Mapped[str] = mapped_column(
        String(500),
        nullable=False,
        unique=True,
    )  # FCM registration token

    device_name: Mapped[Optional[str]] = mapped_column(
        String(100),
        nullable=True,
    )

    is_active: Mapped[bool] = mapped_column(
        Boolean,
        default=True,
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime,
        default=datetime.utcnow,
    )

    updated_at: Mapped[datetime] = mapped_column(
        DateTime,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
    )

    last_used_at: Mapped[Optional[datetime]] = mapped_column(
        DateTime,
        nullable=True,
    )

    # Relationships
    user: Mapped["User"] = relationship(
        "User",
        back_populates="device_tokens",
    )

    __table_args__ = (
        Index("idx_device_user_active", "user_id", "is_active"),
    )

    def __repr__(self) -> str:
        return f"<DeviceToken {self.id} ({self.platform}: {self.token[:20]}...)>"
