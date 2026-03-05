"""
Chatbot Router - API endpoints for AI-powered options trading assistant
"""

from fastapi import APIRouter, HTTPException, Depends, Query
from fastapi.responses import StreamingResponse
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func, desc
from typing import Optional, List
import json
import uuid
from datetime import datetime

from app.database import get_db
from app.models.chat import ChatSession, ChatMessage, MessageRole
from app.schemas.chat import (
    ChatMessageRequest,
    ChatResponse,
    ChatMessageResponse,
    ChatSessionResponse,
    ChatSessionDetailResponse,
    ChatSessionListResponse,
    CreateSessionRequest,
)
from app.services.ai_chatbot_service import chatbot_service
from app.config import settings
from app.data.options_knowledge import (
    INDEX_INFO,
    GREEKS_EXPLAINED,
    OPTIONS_STRATEGIES,
    MARKET_INDICATORS,
    RISK_MANAGEMENT,
    TRADING_TIMES,
    FAQ,
    get_index_info,
    get_strategy_info,
    get_greek_info,
    interpret_pcr,
    interpret_iv,
    interpret_rsi,
)

router = APIRouter(prefix="/api/v1/chat", tags=["AI Chatbot"])


# ==================== Configuration Endpoints ====================

@router.get("/status")
async def chatbot_status():
    """Check if the AI chatbot is configured and ready"""
    return {
        "configured": chatbot_service.is_configured(),
        "provider": chatbot_service._provider,
        "model": chatbot_service._model,
    }


@router.post("/configure")
async def configure_chatbot(
    openai_api_key: Optional[str] = None,
    anthropic_api_key: Optional[str] = None,
    model: Optional[str] = Query(None, description="Model to use (e.g., gpt-4o-mini, claude-3-haiku-20240307)"),
    provider: Optional[str] = Query(None, description="AI provider: openai or anthropic"),
):
    """
    Configure the AI chatbot with API keys and model preferences.
    This is typically done once by admin.
    """
    chatbot_service.configure(
        openai_api_key=openai_api_key,
        anthropic_api_key=anthropic_api_key,
        model=model,
        provider=provider,
    )
    return {
        "status": "configured",
        "provider": chatbot_service._provider,
        "model": chatbot_service._model,
        "is_ready": chatbot_service.is_configured(),
    }


# ==================== Chat Endpoints ====================

@router.post("/message", response_model=ChatResponse)
async def send_message(
    request: ChatMessageRequest,
    db: AsyncSession = Depends(get_db),
    user_id: Optional[str] = None,  # Will come from auth middleware when implemented
):
    """
    Send a message to the AI chatbot and get a response.

    - Creates a new session if session_id is not provided
    - Maintains conversation history within the session
    - Injects market context if provided for more relevant responses
    """
    if not chatbot_service.is_configured():
        raise HTTPException(
            status_code=503,
            detail="AI chatbot is not configured. Please contact administrator."
        )

    # Get or create session and store message
    db_ok = True
    messages = [{"role": "user", "content": request.message}]
    session = None
    session_id = request.session_id or str(uuid.uuid4())

    try:
        if request.session_id:
            # Verify session exists
            result = await db.execute(
                select(ChatSession).where(ChatSession.id == request.session_id)
            )
            session = result.scalar_one_or_none()
            if not session:
                raise HTTPException(status_code=404, detail="Chat session not found")
        else:
            # Create new session
            session = ChatSession(
                id=session_id,
                user_id=user_id,
                title=request.message[:50] + "..." if len(request.message) > 50 else request.message,
            )
            db.add(session)
            await db.flush()

        # Store user message
        user_message = ChatMessage(
            id=str(uuid.uuid4()),
            session_id=session_id,
            role=MessageRole.USER,
            content=request.message,
            metadata_json=json.dumps(request.market_context) if request.market_context else None,
        )
        db.add(user_message)
        await db.flush()

        # Get conversation history for context
        result = await db.execute(
            select(ChatMessage)
            .where(ChatMessage.session_id == session_id)
            .order_by(ChatMessage.created_at)
            .limit(20)  # Limit context to last 20 messages
        )
        history = result.scalars().all()

        # Build messages for AI
        messages = [
            {"role": msg.role.value, "content": msg.content}
            for msg in history
        ]
    except HTTPException:
        raise
    except Exception as e:
        # DB failed (tables missing, etc.) - continue without history
        print(f"[CHATBOT] DB error (degraded mode): {e}")
        db_ok = False
        messages = [{"role": "user", "content": request.message}]

    try:
        # Get AI response
        ai_response = await chatbot_service.chat(
            messages=messages,
            market_context=request.market_context,
        )
    except Exception as e:
        await db.rollback()
        raise HTTPException(
            status_code=500,
            detail=f"AI service error: {str(e)}"
        )

    assistant_message_id = str(uuid.uuid4())
    assistant_created_at = datetime.utcnow()

    if db_ok:
        try:
            # Store AI response
            assistant_message = ChatMessage(
                id=assistant_message_id,
                session_id=session_id,
                role=MessageRole.ASSISTANT,
                content=ai_response,
            )
            db.add(assistant_message)

            # Update session timestamp
            if session:
                session.updated_at = datetime.utcnow()

            await db.commit()
            assistant_created_at = assistant_message.created_at
        except Exception as e:
            await db.rollback()
            print(f"[CHATBOT] DB save error (non-fatal): {e}")

    # Generate follow-up suggestions
    suggestions = chatbot_service.generate_suggestions(request.message, ai_response)

    return ChatResponse(
        session_id=session_id,
        message=ChatMessageResponse(
            id=assistant_message_id,
            role=MessageRole.ASSISTANT,
            content=ai_response,
            created_at=assistant_created_at,
        ),
        suggestions=suggestions,
    )


@router.post("/message/stream")
async def stream_message(
    request: ChatMessageRequest,
    db: AsyncSession = Depends(get_db),
    user_id: Optional[str] = None,
):
    """
    Send a message and stream the AI response.
    Useful for real-time typing effect in the UI.

    Returns Server-Sent Events (SSE) stream.
    """
    if not chatbot_service.is_configured():
        raise HTTPException(
            status_code=503,
            detail="AI chatbot is not configured. Please contact administrator."
        )

    # Get or create session and store message
    stream_db_ok = True
    session = None
    session_id = request.session_id or str(uuid.uuid4())
    messages = [{"role": "user", "content": request.message}]

    try:
        if request.session_id:
            result = await db.execute(
                select(ChatSession).where(ChatSession.id == request.session_id)
            )
            session = result.scalar_one_or_none()
            if not session:
                raise HTTPException(status_code=404, detail="Chat session not found")
        else:
            session = ChatSession(
                id=session_id,
                user_id=user_id,
                title=request.message[:50] + "..." if len(request.message) > 50 else request.message,
            )
            db.add(session)
            await db.flush()

        # Store user message
        user_message = ChatMessage(
            id=str(uuid.uuid4()),
            session_id=session_id,
            role=MessageRole.USER,
            content=request.message,
            metadata_json=json.dumps(request.market_context) if request.market_context else None,
        )
        db.add(user_message)
        await db.flush()

        # Get conversation history
        result = await db.execute(
            select(ChatMessage)
            .where(ChatMessage.session_id == session_id)
            .order_by(ChatMessage.created_at)
            .limit(20)
        )
        history = result.scalars().all()

        messages = [
            {"role": msg.role.value, "content": msg.content}
            for msg in history
        ]
    except HTTPException:
        raise
    except Exception as e:
        print(f"[CHATBOT STREAM] DB error (degraded mode): {e}")
        stream_db_ok = False
        messages = [{"role": "user", "content": request.message}]

    async def generate():
        full_response = []
        try:
            # Send session_id first
            yield f"data: {json.dumps({'session_id': session_id, 'type': 'start'})}\n\n"

            # Stream AI response
            async for chunk in chatbot_service.stream_chat(
                messages=messages,
                market_context=request.market_context,
            ):
                full_response.append(chunk)
                yield f"data: {json.dumps({'content': chunk, 'type': 'chunk'})}\n\n"

            # Store complete response if DB is available
            if stream_db_ok and session:
                try:
                    complete_response = "".join(full_response)
                    assistant_message = ChatMessage(
                        id=str(uuid.uuid4()),
                        session_id=session_id,
                        role=MessageRole.ASSISTANT,
                        content=complete_response,
                    )
                    db.add(assistant_message)
                    session.updated_at = datetime.utcnow()
                    await db.commit()
                except Exception as e:
                    await db.rollback()
                    print(f"[CHATBOT STREAM] DB save error (non-fatal): {e}")

            # Send completion with suggestions
            complete_response = "".join(full_response)
            suggestions = chatbot_service.generate_suggestions(request.message, complete_response)
            yield f"data: {json.dumps({'type': 'done', 'done': True, 'suggestions': suggestions})}\n\n"
            yield "data: [DONE]\n\n"

        except Exception as e:
            if stream_db_ok:
                await db.rollback()
            yield f"data: {json.dumps({'type': 'error', 'error': str(e), 'done': True})}\n\n"
            yield "data: [DONE]\n\n"

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
        },
    )


# ==================== Session Management ====================

@router.post("/sessions", response_model=ChatSessionResponse)
async def create_session(
    request: CreateSessionRequest,
    db: AsyncSession = Depends(get_db),
    user_id: Optional[str] = None,
):
    """Create a new chat session"""
    session = ChatSession(
        id=str(uuid.uuid4()),
        user_id=user_id,
        title=request.title,
    )
    db.add(session)
    await db.commit()

    return ChatSessionResponse(
        id=session.id,
        title=session.title,
        created_at=session.created_at,
        updated_at=session.updated_at,
        message_count=0,
    )


@router.get("/sessions", response_model=ChatSessionListResponse)
async def list_sessions(
    db: AsyncSession = Depends(get_db),
    user_id: Optional[str] = None,
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0),
):
    """List chat sessions for the current user (or all sessions for anonymous)"""
    # Build query
    query = select(ChatSession)
    if user_id:
        query = query.where(ChatSession.user_id == user_id)

    # Get total count
    count_query = select(func.count()).select_from(ChatSession)
    if user_id:
        count_query = count_query.where(ChatSession.user_id == user_id)
    total_result = await db.execute(count_query)
    total = total_result.scalar()

    # Get sessions with pagination
    query = query.order_by(desc(ChatSession.updated_at)).offset(offset).limit(limit)
    result = await db.execute(query)
    sessions = result.scalars().all()

    # Get message counts
    session_responses = []
    for session in sessions:
        count_result = await db.execute(
            select(func.count()).select_from(ChatMessage).where(ChatMessage.session_id == session.id)
        )
        message_count = count_result.scalar()

        session_responses.append(ChatSessionResponse(
            id=session.id,
            title=session.title,
            created_at=session.created_at,
            updated_at=session.updated_at,
            message_count=message_count,
        ))

    return ChatSessionListResponse(sessions=session_responses, total=total)


@router.get("/sessions/{session_id}", response_model=ChatSessionDetailResponse)
async def get_session(
    session_id: str,
    db: AsyncSession = Depends(get_db),
):
    """Get a chat session with full message history"""
    result = await db.execute(
        select(ChatSession).where(ChatSession.id == session_id)
    )
    session = result.scalar_one_or_none()

    if not session:
        raise HTTPException(status_code=404, detail="Chat session not found")

    # Get messages
    messages_result = await db.execute(
        select(ChatMessage)
        .where(ChatMessage.session_id == session_id)
        .order_by(ChatMessage.created_at)
    )
    messages = messages_result.scalars().all()

    return ChatSessionDetailResponse(
        id=session.id,
        title=session.title,
        created_at=session.created_at,
        updated_at=session.updated_at,
        messages=[
            ChatMessageResponse(
                id=msg.id,
                role=msg.role,
                content=msg.content,
                created_at=msg.created_at,
            )
            for msg in messages
        ],
    )


@router.delete("/sessions/{session_id}")
async def delete_session(
    session_id: str,
    db: AsyncSession = Depends(get_db),
):
    """Delete a chat session and all its messages"""
    result = await db.execute(
        select(ChatSession).where(ChatSession.id == session_id)
    )
    session = result.scalar_one_or_none()

    if not session:
        raise HTTPException(status_code=404, detail="Chat session not found")

    await db.delete(session)
    await db.commit()

    return {"status": "deleted", "session_id": session_id}


@router.delete("/sessions")
async def clear_all_sessions(
    db: AsyncSession = Depends(get_db),
    user_id: Optional[str] = None,
):
    """Clear all chat sessions for the current user"""
    query = select(ChatSession)
    if user_id:
        query = query.where(ChatSession.user_id == user_id)

    result = await db.execute(query)
    sessions = result.scalars().all()

    for session in sessions:
        await db.delete(session)

    await db.commit()

    return {"status": "cleared", "deleted_count": len(sessions)}


# ==================== Quick Chat (No Persistence) ====================

@router.post("/quick")
async def quick_chat(
    request: Optional[ChatMessageRequest] = None,
    message: Optional[str] = Query(None, min_length=1, max_length=2000),
    index: Optional[str] = Query(None, description="Index symbol for context"),
    spot_price: Optional[float] = Query(None, description="Current spot price"),
    pcr: Optional[float] = Query(None, description="Put-Call Ratio"),
):
    """
    Quick chat without session persistence.
    Useful for one-off questions or anonymous users.
    Accepts message either as query param or JSON body.
    """
    if not chatbot_service.is_configured():
        raise HTTPException(
            status_code=503,
            detail="AI chatbot is not configured. Please contact administrator."
        )

    # Get message from body or query param
    actual_message = None
    market_context = {}

    if request and request.message:
        actual_message = request.message
        if request.market_context:
            market_context = request.market_context.dict() if hasattr(request.market_context, 'dict') else request.market_context
    elif message:
        actual_message = message
        # Build market context from query params
        if index:
            market_context["index"] = index
        if spot_price:
            market_context["spot_price"] = spot_price
        if pcr:
            market_context["pcr"] = pcr

    if not actual_message:
        raise HTTPException(status_code=400, detail="Message is required")

    try:
        response = await chatbot_service.chat(
            messages=[{"role": "user", "content": actual_message}],
            market_context=market_context if market_context else None,
        )

        suggestions = chatbot_service.generate_suggestions(actual_message, response)

        return {
            "response": response,
            "message": response,  # For backward compatibility
            "suggestions": suggestions,
        }

    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"AI service error: {str(e)}"
        )


# ==================== Knowledge Base Endpoints ====================

@router.get("/knowledge/indices")
async def get_indices_info():
    """Get information about all supported indices"""
    return {
        "indices": INDEX_INFO,
        "count": len(INDEX_INFO)
    }


@router.get("/knowledge/indices/{symbol}")
async def get_index_info_endpoint(symbol: str):
    """Get detailed information about a specific index"""
    info = get_index_info(symbol)
    if not info:
        raise HTTPException(status_code=404, detail=f"Index '{symbol}' not found")
    return info


@router.get("/knowledge/greeks")
async def get_greeks_info():
    """Get information about all option Greeks"""
    return {
        "greeks": GREEKS_EXPLAINED,
        "count": len(GREEKS_EXPLAINED)
    }


@router.get("/knowledge/greeks/{greek}")
async def get_greek_info_endpoint(greek: str):
    """Get detailed information about a specific Greek"""
    info = get_greek_info(greek)
    if not info:
        raise HTTPException(status_code=404, detail=f"Greek '{greek}' not found")
    return info


@router.get("/knowledge/strategies")
async def get_strategies_info():
    """Get information about all options strategies"""
    # Group by outlook
    bullish = {k: v for k, v in OPTIONS_STRATEGIES.items() if "bullish" in v.get("outlook", "").lower()}
    bearish = {k: v for k, v in OPTIONS_STRATEGIES.items() if "bearish" in v.get("outlook", "").lower()}
    neutral = {k: v for k, v in OPTIONS_STRATEGIES.items() if "neutral" in v.get("outlook", "").lower()}

    return {
        "strategies": OPTIONS_STRATEGIES,
        "by_outlook": {
            "bullish": bullish,
            "bearish": bearish,
            "neutral": neutral
        },
        "count": len(OPTIONS_STRATEGIES)
    }


@router.get("/knowledge/strategies/{strategy}")
async def get_strategy_info_endpoint(strategy: str):
    """Get detailed information about a specific strategy"""
    info = get_strategy_info(strategy)
    if not info:
        raise HTTPException(status_code=404, detail=f"Strategy '{strategy}' not found")
    return info


@router.get("/knowledge/indicators")
async def get_indicators_info():
    """Get information about market indicators"""
    return {
        "indicators": MARKET_INDICATORS,
        "count": len(MARKET_INDICATORS)
    }


@router.get("/knowledge/risk-management")
async def get_risk_management_info():
    """Get risk management guidelines"""
    return RISK_MANAGEMENT


@router.get("/knowledge/trading-times")
async def get_trading_times_info():
    """Get trading hours and expiry schedule"""
    return TRADING_TIMES


@router.get("/knowledge/faq")
async def get_faq():
    """Get frequently asked questions and answers"""
    return {
        "faq": FAQ,
        "count": len(FAQ)
    }


@router.get("/knowledge/interpret/pcr/{value}")
async def interpret_pcr_endpoint(value: float):
    """Interpret a Put-Call Ratio value"""
    interpretation = interpret_pcr(value)
    return {
        "pcr": value,
        "interpretation": interpretation,
        "sentiment": "bullish" if value > 1.0 else "bearish" if value < 0.85 else "neutral"
    }


@router.get("/knowledge/interpret/iv/{value}")
async def interpret_iv_endpoint(
    value: float,
    index: str = Query("NIFTY", description="Index for context")
):
    """Interpret an Implied Volatility value"""
    interpretation = interpret_iv(value, index)
    return {
        "iv": value,
        "index": index,
        "interpretation": interpretation,
        "recommendation": "buy options" if "cheap" in interpretation.lower() else "sell options" if "expensive" in interpretation.lower() else "neutral"
    }


@router.get("/knowledge/interpret/rsi/{value}")
async def interpret_rsi_endpoint(value: float):
    """Interpret an RSI value"""
    interpretation = interpret_rsi(value)
    return {
        "rsi": value,
        "interpretation": interpretation,
        "signal": "oversold" if value < 30 else "overbought" if value > 70 else "neutral"
    }


# ==================== RAG (Vector Database) Endpoints ====================

# RAG is optional
try:
    from app.services.rag_service import rag_service
    from app.services.knowledge_loader import load_knowledge_base
    RAG_AVAILABLE = True
except ImportError:
    RAG_AVAILABLE = False
    rag_service = None
    load_knowledge_base = None


@router.get("/rag/status")
async def rag_status():
    """Check RAG (vector database) status"""
    if not RAG_AVAILABLE or rag_service is None:
        return {
            "available": False,
            "message": "RAG not available due to dependency issues. Chatbot will work without RAG.",
            "chatbot_rag_ready": False,
        }
    return {
        "available": True,
        "configured": rag_service.is_configured(),
        "initialized": rag_service.is_initialized(),
        "stats": rag_service.get_stats() if rag_service.is_configured() else None,
        "chatbot_rag_ready": chatbot_service.is_rag_ready(),
    }


@router.post("/rag/load")
async def load_rag_knowledge():
    """
    Load all options trading knowledge into the RAG vector database.
    This creates embeddings and stores them for semantic search.

    Note: Requires OPENAI_API_KEY to be configured for embeddings.
    """
    if not RAG_AVAILABLE or rag_service is None:
        raise HTTPException(
            status_code=503,
            detail="RAG not available due to dependency issues."
        )
    if not rag_service.is_configured():
        raise HTTPException(
            status_code=503,
            detail="RAG service not configured. Set OPENAI_API_KEY first."
        )

    try:
        result = await load_knowledge_base()
        return result
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Failed to load knowledge base: {str(e)}"
        )


@router.post("/rag/reset")
async def reset_rag_knowledge():
    """Reset/clear the RAG vector database"""
    if not RAG_AVAILABLE or rag_service is None:
        raise HTTPException(status_code=503, detail="RAG not available")
    try:
        rag_service.reset_knowledge_base()
        return {
            "success": True,
            "message": "Knowledge base has been reset"
        }
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Failed to reset knowledge base: {str(e)}"
        )


@router.get("/rag/search")
async def search_rag(
    query: str = Query(..., min_length=3, description="Search query"),
    n_results: int = Query(5, ge=1, le=20, description="Number of results"),
    min_score: float = Query(0.3, ge=0, le=1, description="Minimum similarity score"),
):
    """
    Search the RAG knowledge base for relevant information.
    Returns the most relevant chunks based on semantic similarity.
    """
    if not RAG_AVAILABLE or rag_service is None:
        raise HTTPException(status_code=503, detail="RAG not available")
    if not rag_service.is_configured():
        raise HTTPException(
            status_code=503,
            detail="RAG service not configured. Set OPENAI_API_KEY first."
        )

    if not rag_service.is_initialized():
        raise HTTPException(
            status_code=503,
            detail="Knowledge base not initialized. Call POST /rag/load first."
        )

    try:
        results = rag_service.retrieve(query, n_results=n_results, min_score=min_score)
        return {
            "query": query,
            "results": [
                {
                    "content": r.content,
                    "metadata": r.metadata,
                    "score": round(r.score, 4)
                }
                for r in results
            ],
            "count": len(results)
        }
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Search failed: {str(e)}"
        )


@router.post("/rag/add")
async def add_to_rag(
    content: str = Query(..., min_length=10, description="Content to add"),
    category: str = Query("Custom", description="Category for the content"),
    topic: str = Query("", description="Topic name"),
):
    """
    Add custom content to the RAG knowledge base.
    Useful for adding new knowledge or FAQs.
    """
    if not RAG_AVAILABLE or rag_service is None:
        raise HTTPException(status_code=503, detail="RAG not available")
    if not rag_service.is_configured():
        raise HTTPException(
            status_code=503,
            detail="RAG service not configured. Set OPENAI_API_KEY first."
        )

    try:
        chunks_added = rag_service.add_document(
            content=content,
            metadata={
                "category": category,
                "topic": topic,
                "type": "custom"
            }
        )
        return {
            "success": True,
            "chunks_added": chunks_added,
            "message": f"Added {chunks_added} chunks to knowledge base"
        }
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Failed to add content: {str(e)}"
        )
