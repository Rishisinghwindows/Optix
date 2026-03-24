import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse
import os

from app.config import settings
from app.database import init_db
from app.routers import (
    auth_router, user_router, health_router, market_router, admin_router,
    paper_trading_router, chatbot_router, backtest_router, ipo_router,
    options_ai_router, options_ai_insights_router,
)
from app.routers.alerts import router as alerts_router
from app.routers.websocket import router as websocket_router
from app.routers.algo_trading import router as algo_trading_router
from app.routers.trade_journal import router as trade_journal_router
from app.routers.device import router as device_router
from app.routers.market_monitor import router as market_monitor_router
from app.workers.position_monitor import position_monitor
from app.services.ai_chatbot_service import chatbot_service
from app.services.alert_service import alert_checker_service
from app.services.market_monitor_service import market_monitor_service
from app.services.nse_service import nse_service

logger = logging.getLogger(__name__)

# RAG is optional
try:
    from app.services.rag_service import rag_service
    from app.services.knowledge_loader import load_knowledge_base
    RAG_AVAILABLE = True
except ImportError as e:
    logger.info(f"RAG not available: {e}")
    RAG_AVAILABLE = False
    rag_service = None
    load_knowledge_base = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan handler."""
    logger.info(f"Starting {settings.app_name} API...")

    if settings.environment == "development":
        try:
            await init_db()
            logger.info("Database tables initialized")
        except Exception as e:
            logger.warning(f"Database initialization skipped: {e}")

    # Configure AI chatbot if API keys are set
    if settings.openai_api_key or settings.anthropic_api_key:
        chatbot_service.configure(
            openai_api_key=settings.openai_api_key,
            anthropic_api_key=settings.anthropic_api_key,
            model=settings.ai_model,
            provider=settings.ai_provider,
        )
        logger.info(f"AI Chatbot configured with {settings.ai_provider} ({settings.ai_model})")

        if RAG_AVAILABLE and rag_service and settings.openai_api_key and not rag_service.is_initialized():
            try:
                result = await load_knowledge_base()
                if result.get("success"):
                    logger.info(f"RAG knowledge base loaded: {result.get('chunks_created', 0)} chunks")
                else:
                    logger.warning(f"RAG loading failed: {result.get('error', 'Unknown error')}")
            except Exception as e:
                logger.warning(f"RAG initialization error (non-fatal): {e}")

    # Start alert checker service
    if settings.enable_alerts:
        try:
            await alert_checker_service.start()
            logger.info("Alert checker service started")
        except Exception as e:
            logger.error(f"Alert checker service failed to start: {e}")

    # Start market monitor service (push notifications for significant changes)
    if settings.enable_market_monitor:
        try:
            await market_monitor_service.start()
            logger.info("Market monitor service started")
        except Exception as e:
            logger.error(f"Market monitor service failed to start: {e}")

    # Start position monitor (for algo trading)
    if settings.enable_position_monitor:
        try:
            await position_monitor.start()
            logger.info("Position monitor started")
        except Exception as e:
            logger.error(f"Position monitor failed to start: {e}")

    yield

    # Shutdown
    logger.info("Shutting down...")
    await nse_service.close()

    if settings.enable_position_monitor:
        try:
            await position_monitor.stop()
        except Exception as e:
            logger.error(f"Error stopping position monitor: {e}")

    if settings.enable_market_monitor:
        try:
            await market_monitor_service.stop()
        except Exception as e:
            logger.error(f"Error stopping market monitor: {e}")

    if settings.enable_alerts:
        try:
            await alert_checker_service.stop()
        except Exception as e:
            logger.error(f"Error stopping alert checker: {e}")


app = FastAPI(
    title=f"{settings.app_name} Auth API",
    description="Authentication API for Optix - Options Trading App",
    version="1.0.0",
    docs_url="/docs" if settings.debug else None,
    redoc_url="/redoc" if settings.debug else None,
    lifespan=lifespan,
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:3000",
        "http://localhost:3001",
        "http://localhost:3002",
        "http://localhost:3007",
        "http://localhost:8080",
        "http://localhost:5173",
        "https://optix.d23ai.in",
        "https://api.optix.d23ai.in",
        "https://optix.app",
        "https://www.optix.app",
        "https://api.optix.app",
    ],
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type", "Accept"],
)

# Include routers
app.include_router(health_router)
app.include_router(auth_router, prefix="/api/v1")
app.include_router(user_router, prefix="/api/v1")
app.include_router(market_router)  # Market data routes (includes its own /api/v1/market prefix)
app.include_router(websocket_router)  # WebSocket for real-time data
app.include_router(admin_router)  # Admin authentication and dashboard
app.include_router(paper_trading_router)  # Paper trading endpoints
app.include_router(chatbot_router)  # AI chatbot endpoints
app.include_router(backtest_router, prefix="/api/v1")  # Backtesting endpoints
app.include_router(ipo_router)  # IPO Dashboard endpoints
app.include_router(options_ai_router)  # Options AI Analysis endpoints
app.include_router(options_ai_insights_router)  # AI Trade Insights (for Android app)
app.include_router(alerts_router, prefix="/api/v1")  # Price Alerts endpoints
app.include_router(algo_trading_router)  # Algo Trading endpoints
app.include_router(trade_journal_router, prefix="/api/v1")  # Trade Journal endpoints
app.include_router(device_router, prefix="/api/v1")  # Device token registration (push notifications)
app.include_router(market_monitor_router, prefix="/api/v1")  # Market monitor signals

# Serve static files
static_dir = os.path.join(os.path.dirname(__file__), "static")
if os.path.exists(static_dir):
    app.mount("/static", StaticFiles(directory=static_dir), name="static")


@app.get("/")
async def root():
    """Return basic API info."""
    return {
        "name": settings.app_name,
        "version": app.version,
        "docs": "/docs" if settings.debug else None,
    }


@app.get("/algo-dashboard")
async def algo_dashboard():
    """Serve the algo trading dashboard."""
    return FileResponse(
        os.path.join(os.path.dirname(__file__), "static", "algo-dashboard.html")
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=8000,
        reload=settings.debug,
    )
