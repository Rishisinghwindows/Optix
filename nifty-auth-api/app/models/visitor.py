"""
Visitor Tracking Model
Tracks website visitors for analytics
"""

import uuid
from datetime import datetime
from typing import Optional
from sqlalchemy import String, DateTime, Index, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class Visitor(Base):
    __tablename__ = "visitors"

    id: Mapped[str] = mapped_column(
        String(36),
        primary_key=True,
        default=lambda: str(uuid.uuid4()),
    )

    # Visitor identification (anonymous tracking)
    visitor_id: Mapped[str] = mapped_column(
        String(64),
        nullable=False,
        index=True,
    )  # Fingerprint or session ID

    # Visit info
    ip_address: Mapped[Optional[str]] = mapped_column(String(45), nullable=True)
    user_agent: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    referrer: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)

    # Page info
    page_url: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)
    page_title: Mapped[Optional[str]] = mapped_column(String(200), nullable=True)

    # Device info
    device_type: Mapped[Optional[str]] = mapped_column(String(20), nullable=True)  # mobile, desktop, tablet
    browser: Mapped[Optional[str]] = mapped_column(String(50), nullable=True)
    os: Mapped[Optional[str]] = mapped_column(String(50), nullable=True)

    # Location (from IP)
    country: Mapped[Optional[str]] = mapped_column(String(100), nullable=True)
    city: Mapped[Optional[str]] = mapped_column(String(100), nullable=True)

    # Session tracking
    session_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True, index=True)
    is_new_visitor: Mapped[bool] = mapped_column(default=True)

    # Timestamps
    visited_at: Mapped[datetime] = mapped_column(
        DateTime,
        default=datetime.utcnow,
        index=True,
    )

    __table_args__ = (
        Index("idx_visitors_date", "visited_at"),
        Index("idx_visitors_visitor_date", "visitor_id", "visited_at"),
    )

    def __repr__(self) -> str:
        return f"<Visitor {self.visitor_id[:8]}... at {self.visited_at}>"


class PageView(Base):
    """Track individual page views for more detailed analytics"""
    __tablename__ = "page_views"

    id: Mapped[str] = mapped_column(
        String(36),
        primary_key=True,
        default=lambda: str(uuid.uuid4()),
    )

    visitor_id: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    session_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)

    page_url: Mapped[str] = mapped_column(String(500), nullable=False)
    page_title: Mapped[Optional[str]] = mapped_column(String(200), nullable=True)

    # Time spent on page (in seconds)
    time_spent: Mapped[Optional[int]] = mapped_column(nullable=True)

    viewed_at: Mapped[datetime] = mapped_column(
        DateTime,
        default=datetime.utcnow,
        index=True,
    )

    __table_args__ = (
        Index("idx_pageviews_date", "viewed_at"),
    )
