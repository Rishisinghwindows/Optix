"""
Chat Message Model - Stores conversation history for AI chatbot
"""

import uuid
from datetime import datetime
from typing import Optional, TYPE_CHECKING
from sqlalchemy import String, Text, DateTime, ForeignKey, Enum as SQLEnum, Index
from sqlalchemy.orm import Mapped, mapped_column, relationship
import enum

from app.database import Base

if TYPE_CHECKING:
    from app.models.user import User


class MessageRole(str, enum.Enum):
    USER = "user"
    ASSISTANT = "assistant"
    SYSTEM = "system"


class ChatSession(Base):
    """Chat session to group messages by conversation"""
    __tablename__ = "chat_sessions"

    id: Mapped[str] = mapped_column(
        String(36),
        primary_key=True,
        default=lambda: str(uuid.uuid4()),
    )

    user_id: Mapped[Optional[str]] = mapped_column(
        String(36),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=True,  # Allow anonymous sessions
        index=True,
    )

    # Session metadata
    title: Mapped[Optional[str]] = mapped_column(String(200), nullable=True)

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
    messages: Mapped[list["ChatMessage"]] = relationship(
        "ChatMessage",
        back_populates="session",
        cascade="all, delete-orphan",
        order_by="ChatMessage.created_at",
    )
    user: Mapped[Optional["User"]] = relationship("User")

    __table_args__ = (
        Index("idx_chat_sessions_user_updated", "user_id", "updated_at"),
    )

    def __repr__(self) -> str:
        return f"<ChatSession {self.id}>"


class ChatMessage(Base):
    """Individual chat message"""
    __tablename__ = "chat_messages"

    id: Mapped[str] = mapped_column(
        String(36),
        primary_key=True,
        default=lambda: str(uuid.uuid4()),
    )

    session_id: Mapped[str] = mapped_column(
        String(36),
        ForeignKey("chat_sessions.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    role: Mapped[MessageRole] = mapped_column(
        SQLEnum(MessageRole),
        nullable=False,
    )

    content: Mapped[str] = mapped_column(Text, nullable=False)

    # Optional metadata (e.g., market context at time of message)
    metadata_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)

    # Timestamps
    created_at: Mapped[datetime] = mapped_column(
        DateTime,
        default=datetime.utcnow,
        index=True,
    )

    # Relationships
    session: Mapped["ChatSession"] = relationship(
        "ChatSession",
        back_populates="messages",
    )

    __table_args__ = (
        Index("idx_chat_messages_session_created", "session_id", "created_at"),
    )

    def __repr__(self) -> str:
        return f"<ChatMessage {self.id} ({self.role})>"
