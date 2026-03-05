"""
Trade Journal Model — Log trades with notes, tags, mood, and review patterns.
"""

import uuid
from datetime import datetime
from typing import Optional, TYPE_CHECKING
from sqlalchemy import String, Boolean, Float, DateTime, Integer, ForeignKey, Text, Index
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base

if TYPE_CHECKING:
    from app.models.user import User


class TradeJournal(Base):
    """
    Trade Journal Entry Model

    Users log trades with notes, tags, mood, and market conditions.
    Can be auto-populated from paper trading closes.
    """
    __tablename__ = "trade_journal"

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

    # Trade details
    symbol: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
    )  # NIFTY, BANKNIFTY, etc.

    strike_price: Mapped[float] = mapped_column(
        Float,
        nullable=False,
    )

    option_type: Mapped[str] = mapped_column(
        String(2),
        nullable=False,
    )  # CE or PE

    direction: Mapped[str] = mapped_column(
        String(4),
        nullable=False,
    )  # buy or sell

    entry_price: Mapped[float] = mapped_column(
        Float,
        nullable=False,
    )

    exit_price: Mapped[Optional[float]] = mapped_column(
        Float,
        nullable=True,
    )

    quantity: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
    )  # number of lots

    lot_size: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
    )

    entry_date: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
    )

    exit_date: Mapped[Optional[datetime]] = mapped_column(
        DateTime,
        nullable=True,
    )

    expiry_date: Mapped[Optional[str]] = mapped_column(
        String(15),
        nullable=True,
    )  # dd-MMM-yyyy format

    # P&L
    realized_pnl: Mapped[Optional[float]] = mapped_column(
        Float,
        nullable=True,
    )

    realized_pnl_percent: Mapped[Optional[float]] = mapped_column(
        Float,
        nullable=True,
    )

    # Journal metadata
    title: Mapped[Optional[str]] = mapped_column(
        String(200),
        nullable=True,
    )

    notes: Mapped[Optional[str]] = mapped_column(
        Text,
        nullable=True,
    )

    tags: Mapped[Optional[str]] = mapped_column(
        String(500),
        nullable=True,
    )  # comma-separated: scalp,swing,hedging,spread,directional,expiry_day

    market_condition: Mapped[Optional[str]] = mapped_column(
        String(20),
        nullable=True,
    )  # trending_up, trending_down, range_bound, volatile, low_vol

    mood: Mapped[Optional[str]] = mapped_column(
        String(20),
        nullable=True,
    )  # confident, nervous, neutral, greedy, fearful, disciplined

    outcome: Mapped[Optional[str]] = mapped_column(
        String(10),
        nullable=True,
    )  # profit, loss, breakeven

    # Link to paper trading
    paper_position_id: Mapped[Optional[str]] = mapped_column(
        String(36),
        nullable=True,
    )

    # Timestamps
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
        back_populates="journal_entries",
    )

    __table_args__ = (
        Index("idx_journal_user_date", "user_id", "entry_date"),
        Index("idx_journal_user_outcome", "user_id", "outcome"),
        Index("idx_journal_symbol", "user_id", "symbol"),
    )

    def __repr__(self) -> str:
        return f"<TradeJournal {self.id} ({self.symbol} {self.strike_price} {self.option_type} {self.direction})>"
