"""
Alert Models for Real-time Price Alerts
"""

import uuid
from datetime import datetime
from decimal import Decimal
from typing import Optional, TYPE_CHECKING
from sqlalchemy import String, Boolean, DECIMAL, DateTime, Integer, ForeignKey, Text, Index, Enum as SQLEnum
from sqlalchemy.orm import Mapped, mapped_column, relationship
import enum

from app.database import Base

if TYPE_CHECKING:
    from app.models.user import User


class AlertType(str, enum.Enum):
    """Types of alerts supported"""
    SPOT_PRICE = "spot_price"           # Index spot price alert
    OPTION_PREMIUM = "option_premium"   # Option LTP alert
    PCR = "pcr"                         # Put-Call Ratio alert
    OI_CHANGE = "oi_change"             # Open Interest change alert
    IV = "iv"                           # Implied Volatility alert
    MAX_PAIN = "max_pain"               # Max pain level alert


class AlertCondition(str, enum.Enum):
    """Condition for triggering alert"""
    ABOVE = "above"         # Value goes above target
    BELOW = "below"         # Value goes below target
    CROSSES = "crosses"     # Value crosses target (either direction)


class Alert(Base):
    """
    Price Alert Model

    Stores user-defined alerts for various market conditions.
    Alert checker service monitors these and triggers notifications.
    """
    __tablename__ = "alerts"

    id: Mapped[str] = mapped_column(
        String(36),
        primary_key=True,
        default=lambda: str(uuid.uuid4()),
    )

    # User who created the alert
    user_id: Mapped[str] = mapped_column(
        String(36),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    # Alert configuration
    alert_type: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
    )  # AlertType enum value

    symbol: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
    )  # NIFTY, BANKNIFTY, etc.

    condition: Mapped[str] = mapped_column(
        String(10),
        nullable=False,
    )  # AlertCondition enum value

    target_value: Mapped[Decimal] = mapped_column(
        DECIMAL(15, 2),
        nullable=False,
    )  # Target price/value to trigger alert

    # Option-specific fields (nullable for spot/pcr alerts)
    strike_price: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(15, 2),
        nullable=True,
    )

    option_type: Mapped[Optional[str]] = mapped_column(
        String(2),
        nullable=True,
    )  # CE or PE

    expiry: Mapped[Optional[str]] = mapped_column(
        String(15),
        nullable=True,
    )  # dd-MMM-yyyy format

    # Alert state
    is_active: Mapped[bool] = mapped_column(
        Boolean,
        default=True,
        index=True,
    )

    is_triggered: Mapped[bool] = mapped_column(
        Boolean,
        default=False,
    )

    triggered_at: Mapped[Optional[datetime]] = mapped_column(
        DateTime,
        nullable=True,
    )

    triggered_value: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(15, 2),
        nullable=True,
    )  # The actual value when triggered

    # For tracking last checked value (to detect crosses)
    last_checked_value: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(15, 2),
        nullable=True,
    )

    last_checked_at: Mapped[Optional[datetime]] = mapped_column(
        DateTime,
        nullable=True,
    )

    # User-friendly name for the alert
    name: Mapped[Optional[str]] = mapped_column(
        String(100),
        nullable=True,
    )

    # Optional note from user
    note: Mapped[Optional[str]] = mapped_column(
        Text,
        nullable=True,
    )

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

    # Relationships
    user: Mapped["User"] = relationship(
        "User",
        back_populates="alerts",
    )

    notifications: Mapped[list["AlertNotification"]] = relationship(
        "AlertNotification",
        back_populates="alert",
        cascade="all, delete-orphan",
    )

    __table_args__ = (
        Index("idx_alerts_active", "is_active", "is_triggered"),
        Index("idx_alerts_user_active", "user_id", "is_active"),
        Index("idx_alerts_symbol", "symbol", "alert_type"),
    )

    def __repr__(self) -> str:
        return f"<Alert {self.id} ({self.alert_type}: {self.symbol} {self.condition} {self.target_value})>"

    def get_display_name(self) -> str:
        """Generate a human-readable name for the alert"""
        if self.name:
            return self.name

        if self.alert_type == AlertType.SPOT_PRICE.value:
            return f"{self.symbol} {self.condition} {self.target_value}"
        elif self.alert_type == AlertType.OPTION_PREMIUM.value:
            return f"{self.symbol} {self.strike_price} {self.option_type} {self.condition} ₹{self.target_value}"
        elif self.alert_type == AlertType.PCR.value:
            return f"{self.symbol} PCR {self.condition} {self.target_value}"
        elif self.alert_type == AlertType.OI_CHANGE.value:
            return f"{self.symbol} OI change {self.condition} {self.target_value}%"
        elif self.alert_type == AlertType.IV.value:
            return f"{self.symbol} IV {self.condition} {self.target_value}%"
        else:
            return f"{self.symbol} alert"


class AlertNotification(Base):
    """
    Notification Model

    Stores notifications generated when alerts are triggered.
    """
    __tablename__ = "alert_notifications"

    id: Mapped[str] = mapped_column(
        String(36),
        primary_key=True,
        default=lambda: str(uuid.uuid4()),
    )

    alert_id: Mapped[str] = mapped_column(
        String(36),
        ForeignKey("alerts.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    user_id: Mapped[str] = mapped_column(
        String(36),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    # Notification content
    title: Mapped[str] = mapped_column(
        String(200),
        nullable=False,
    )

    message: Mapped[str] = mapped_column(
        Text,
        nullable=False,
    )

    # The value that triggered the alert
    triggered_value: Mapped[Optional[Decimal]] = mapped_column(
        DECIMAL(15, 2),
        nullable=True,
    )

    # Read status
    is_read: Mapped[bool] = mapped_column(
        Boolean,
        default=False,
        index=True,
    )

    read_at: Mapped[Optional[datetime]] = mapped_column(
        DateTime,
        nullable=True,
    )

    # Metadata
    created_at: Mapped[datetime] = mapped_column(
        DateTime,
        default=datetime.utcnow,
        index=True,
    )

    # Relationships
    alert: Mapped["Alert"] = relationship(
        "Alert",
        back_populates="notifications",
    )

    user: Mapped["User"] = relationship(
        "User",
        back_populates="alert_notifications",
    )

    __table_args__ = (
        Index("idx_notifications_user_unread", "user_id", "is_read"),
    )

    def __repr__(self) -> str:
        return f"<AlertNotification {self.id} ({self.title})>"
