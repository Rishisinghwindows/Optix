from app.routers.auth import router as auth_router
from app.routers.user import router as user_router
from app.routers.health import router as health_router
from app.routers.market import router as market_router
from app.routers.admin import router as admin_router
from app.routers.paper_trading import router as paper_trading_router
from app.routers.chatbot import router as chatbot_router
from app.routers.backtest import router as backtest_router
from app.routers.ipo import router as ipo_router
from app.routers.options_ai import router as options_ai_router
from app.routers.options_ai_insights import router as options_ai_insights_router

__all__ = [
    "auth_router",
    "user_router",
    "health_router",
    "market_router",
    "admin_router",
    "paper_trading_router",
    "chatbot_router",
    "backtest_router",
    "ipo_router",
    "options_ai_router",
    "options_ai_insights_router",
]
