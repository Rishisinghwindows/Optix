from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text
import redis.asyncio as redis
from pydantic import BaseModel
from typing import Optional

from app.database import get_db
from app.config import settings


router = APIRouter(tags=["Health"])


class HealthStatus(BaseModel):
    status: str
    database: str
    redis: str
    environment: str
    version: str = "1.0.0"


@router.get("/health", response_model=HealthStatus)
async def health_check(db: AsyncSession = Depends(get_db)):
    """
    Health check endpoint.
    Returns the status of the API and its dependencies.
    """
    # Check database connection
    db_status = "healthy"
    try:
        await db.execute(text("SELECT 1"))
    except Exception:
        db_status = "unhealthy"

    # Check Redis connection
    redis_status = "healthy"
    try:
        redis_client = redis.from_url(settings.redis_url)
        await redis_client.ping()
        await redis_client.close()
    except Exception:
        redis_status = "unhealthy"

    overall_status = "healthy" if db_status == "healthy" else "degraded"

    return HealthStatus(
        status=overall_status,
        database=db_status,
        redis=redis_status,
        environment=settings.environment,
    )


@router.get("/api/info")
async def api_info():
    """API info endpoint."""
    return {
        "name": settings.app_name,
        "description": "Authentication API for Optix - Options Trading App",
        "version": "1.0.0",
        "docs": "/docs",
    }
