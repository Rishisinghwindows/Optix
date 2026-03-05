import uuid
from datetime import datetime
from decimal import Decimal
from typing import Optional, TYPE_CHECKING
from sqlalchemy import String, Boolean, DECIMAL, DateTime, Index
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base

if TYPE_CHECKING:
    from app.models.session import Session
    from app.models.paper_trading import PaperPosition, PaperTrade
    from app.models.alert import Alert, AlertNotification
    from app.models.trade_journal import TradeJournal
    from app.models.device_token import DeviceToken


class User(Base):
    __tablename__ = "users"

    id: Mapped[str] = mapped_column(
        String(36),
        primary_key=True,
        default=lambda: str(uuid.uuid4()),
    )

    # Profile
    phone: Mapped[Optional[str]] = mapped_column(
        String(15),
        unique=True,
        nullable=True,
        index=True,
    )
    email: Mapped[Optional[str]] = mapped_column(
        String(255),
        unique=True,
        nullable=True,
        index=True,
    )
    full_name: Mapped[Optional[str]] = mapped_column(String(100), nullable=True)
    avatar_url: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)

    # Auth
    auth_provider: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
    )  # 'phone', 'google', 'apple', 'facebook'
    provider_id: Mapped[Optional[str]] = mapped_column(
        String(255),
        nullable=True,
    )  # Social provider user ID
    is_verified: Mapped[bool] = mapped_column(Boolean, default=False)

    # App Data
    paper_trading_balance: Mapped[Decimal] = mapped_column(
        DECIMAL(15, 2),
        default=Decimal("1000000.00"),
    )
    is_premium: Mapped[bool] = mapped_column(Boolean, default=False)

    # Metadata
    created_at: Mapped[datetime] = mapped_column(
        DateTime,
        default=datetime.utcnow,
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
    )
    last_login_at: Mapped[Optional[datetime]] = mapped_column(DateTime, nullable=True)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)

    # Relationships
    sessions: Mapped[list["Session"]] = relationship(
        "Session",
        back_populates="user",
        cascade="all, delete-orphan",
    )
    paper_positions: Mapped[list["PaperPosition"]] = relationship(
        "PaperPosition",
        back_populates="user",
        cascade="all, delete-orphan",
    )
    paper_trades: Mapped[list["PaperTrade"]] = relationship(
        "PaperTrade",
        back_populates="user",
        cascade="all, delete-orphan",
    )
    alerts: Mapped[list["Alert"]] = relationship(
        "Alert",
        back_populates="user",
        cascade="all, delete-orphan",
    )
    alert_notifications: Mapped[list["AlertNotification"]] = relationship(
        "AlertNotification",
        back_populates="user",
        cascade="all, delete-orphan",
    )
    journal_entries: Mapped[list["TradeJournal"]] = relationship(
        "TradeJournal",
        back_populates="user",
        cascade="all, delete-orphan",
    )
    device_tokens: Mapped[list["DeviceToken"]] = relationship(
        "DeviceToken",
        back_populates="user",
        cascade="all, delete-orphan",
    )

    __table_args__ = (
        Index("idx_users_provider", "auth_provider", "provider_id"),
    )

    def __repr__(self) -> str:
        return f"<User {self.id} ({self.phone or self.email})>"
