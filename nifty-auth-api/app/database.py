from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from sqlalchemy.orm import DeclarativeBase
from typing import AsyncGenerator

from app.config import settings


class Base(DeclarativeBase):
    pass


# SQLite doesn't support pool_size/max_overflow
is_sqlite = settings.database_url.startswith("sqlite")

engine_kwargs = {
    "echo": settings.debug,
}

if not is_sqlite:
    engine_kwargs.update({
        "pool_pre_ping": True,
        "pool_size": 10,
        "max_overflow": 20,
    })
else:
    # SQLite requires special handling for async concurrent access
    engine_kwargs["connect_args"] = {
        "check_same_thread": False,
        "timeout": 30,  # Wait up to 30 seconds for database lock
    }
    # Use NullPool to avoid connection pooling issues with SQLite
    from sqlalchemy.pool import StaticPool
    engine_kwargs["poolclass"] = StaticPool

engine = create_async_engine(settings.database_url, **engine_kwargs)

AsyncSessionLocal = async_sessionmaker(
    engine,
    class_=AsyncSession,
    expire_on_commit=False,
    autocommit=False,
    autoflush=False,
)


async def get_db() -> AsyncGenerator[AsyncSession, None]:
    async with AsyncSessionLocal() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise


async def init_db():
    """Initialize database tables."""
    # Import all models to register them with Base.metadata
    from app.models import (
        User, Session, OTPRequest, PaperPosition, PaperTrade, ChatSession, ChatMessage,
        SupportedIndex, HistoricalSpotPrice, HistoricalOption,
        BacktestStrategy, BacktestRun, BacktestTrade, BacktestResult,
        Alert, AlertNotification
    )
    # Import algo trading models
    from app.models.algo_trading import (
        AlgoConfig, AlgoTrade, AlgoPosition, AlgoLog, AlgoDailyStats
    )
    from app.models.trade_journal import TradeJournal
    from app.models.device_token import DeviceToken

    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
