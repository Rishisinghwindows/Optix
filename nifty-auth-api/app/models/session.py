import uuid
import json
from datetime import datetime
from typing import Optional, TYPE_CHECKING
from sqlalchemy import String, DateTime, ForeignKey, Index, Text, TypeDecorator

from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base

if TYPE_CHECKING:
    from app.models.user import User


# JSON type that works with both PostgreSQL and SQLite
class JSONType(TypeDecorator):
    impl = Text
    cache_ok = True

    def process_bind_param(self, value, dialect):
        if value is not None:
            return json.dumps(value)
        return None

    def process_result_value(self, value, dialect):
        if value is not None:
            return json.loads(value)
        return None


class Session(Base):
    __tablename__ = "sessions"

    id: Mapped[str] = mapped_column(
        String(36),
        primary_key=True,
        default=lambda: str(uuid.uuid4()),
    )

    user_id: Mapped[str] = mapped_column(
        String(36),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    refresh_token_hash: Mapped[str] = mapped_column(
        String(255),
        nullable=False,
        index=True,
    )

    device_info: Mapped[Optional[dict]] = mapped_column(
        JSONType,
        nullable=True,
    )  # {device_name, os, app_version}

    ip_address: Mapped[Optional[str]] = mapped_column(
        String(45),  # IPv6 max length
        nullable=True,
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime,
        default=datetime.utcnow,
    )

    expires_at: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
    )

    revoked_at: Mapped[Optional[datetime]] = mapped_column(
        DateTime,
        nullable=True,
    )

    # Relationships
    user: Mapped["User"] = relationship("User", back_populates="sessions")

    __table_args__ = (
        Index("idx_sessions_user_active", "user_id", "revoked_at"),
    )

    @property
    def is_valid(self) -> bool:
        """Check if session is still valid."""
        now = datetime.utcnow()
        return self.revoked_at is None and self.expires_at > now

    def __repr__(self) -> str:
        return f"<Session {self.id} user={self.user_id}>"
