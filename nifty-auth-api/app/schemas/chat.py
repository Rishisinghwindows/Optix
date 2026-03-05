"""
Chat Schemas - Request/Response models for AI chatbot
"""

from pydantic import BaseModel, Field
from typing import Optional, List, Dict, Any
from datetime import datetime
from enum import Enum


class MessageRole(str, Enum):
    USER = "user"
    ASSISTANT = "assistant"
    SYSTEM = "system"


# ==================== Request Schemas ====================

class ChatMessageRequest(BaseModel):
    """Request to send a message to the chatbot"""
    message: str = Field(..., min_length=1, max_length=4000, description="User's message")
    session_id: Optional[str] = Field(None, description="Existing session ID to continue conversation")

    # Optional market context to inject
    market_context: Optional[Dict[str, Any]] = Field(
        None,
        description="Current market data to provide context (spot price, PCR, etc.)"
    )

    class Config:
        json_schema_extra = {
            "example": {
                "message": "What are the best call options for NIFTY today?",
                "session_id": None,
                "market_context": {
                    "index": "NIFTY",
                    "spot_price": 25800,
                    "pcr": 1.24,
                    "atm_iv": 14.2
                }
            }
        }


class CreateSessionRequest(BaseModel):
    """Request to create a new chat session"""
    title: Optional[str] = Field(None, max_length=200, description="Optional session title")


# ==================== Response Schemas ====================

class ChatMessageResponse(BaseModel):
    """Single message in conversation"""
    id: str
    role: MessageRole
    content: str
    created_at: datetime

    class Config:
        from_attributes = True


class ChatResponse(BaseModel):
    """Response from the chatbot"""
    session_id: str
    message: ChatMessageResponse
    suggestions: Optional[List[str]] = Field(
        None,
        description="Follow-up question suggestions"
    )

    class Config:
        json_schema_extra = {
            "example": {
                "session_id": "abc123",
                "message": {
                    "id": "msg123",
                    "role": "assistant",
                    "content": "Based on the current market conditions...",
                    "created_at": "2024-01-15T10:30:00Z"
                },
                "suggestions": [
                    "What's the risk-reward ratio?",
                    "Show me put options instead",
                    "Explain the Greeks for this option"
                ]
            }
        }


class ChatSessionResponse(BaseModel):
    """Chat session summary"""
    id: str
    title: Optional[str]
    created_at: datetime
    updated_at: datetime
    message_count: int = 0

    class Config:
        from_attributes = True


class ChatSessionDetailResponse(BaseModel):
    """Chat session with full message history"""
    id: str
    title: Optional[str]
    created_at: datetime
    updated_at: datetime
    messages: List[ChatMessageResponse]

    class Config:
        from_attributes = True


class ChatSessionListResponse(BaseModel):
    """List of chat sessions"""
    sessions: List[ChatSessionResponse]
    total: int


class StreamChunk(BaseModel):
    """Chunk of streamed response"""
    content: str
    done: bool = False
    session_id: Optional[str] = None


# ==================== Internal Schemas ====================

class ConversationMessage(BaseModel):
    """Internal representation for AI API calls"""
    role: str
    content: str
