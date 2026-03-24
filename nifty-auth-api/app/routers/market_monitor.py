"""
Market Monitor API — Recent market signals and monitor status
"""

import logging
from fastapi import APIRouter
from sqlalchemy import select, func
from app.services.cache_service import cache
from app.services.market_monitor_service import market_monitor_service, MONITORED_INDICES
from app.services.push_notification_service import push_service
from app.database import AsyncSessionLocal
from app.models.device_token import DeviceToken

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/market-monitor", tags=["Market Monitor"])


@router.get("/signals")
async def get_recent_signals(index: str = None):
    """Get recent market signals, optionally filtered by index."""
    all_signals = []

    indices = [index] if index else MONITORED_INDICES + ["global"]
    for idx in indices:
        cache_key = f"market_signals:{idx}"
        signals = await cache.get(cache_key) or []
        all_signals.extend(signals)

    # Sort by timestamp descending
    all_signals.sort(key=lambda s: s.get("timestamp", ""), reverse=True)

    return {
        "signals": all_signals[:50],
        "total": len(all_signals),
    }


@router.get("/status")
async def get_monitor_status():
    """Get market monitor service status."""
    return {
        "running": market_monitor_service._running,
        "monitored_indices": MONITORED_INDICES,
        "check_interval_seconds": market_monitor_service._check_interval,
        "is_market_hours": market_monitor_service._is_market_hours(),
        "active_snapshots": list(market_monitor_service._prev_snapshots.keys()),
    }


@router.get("/devices")
async def get_registered_devices():
    """List all registered device tokens (for debugging push delivery)."""
    async with AsyncSessionLocal() as db:
        result = await db.execute(
            select(
                DeviceToken.platform,
                func.count(DeviceToken.id).label("count"),
                func.sum(
                    func.cast(DeviceToken.is_active, DeviceToken.is_active.type)
                ).label("active"),
            ).group_by(DeviceToken.platform)
        )
        platform_stats = [
            {"platform": row.platform, "total": row.count, "active": row.active or 0}
            for row in result.all()
        ]

        total_result = await db.execute(
            select(func.count(DeviceToken.id)).where(DeviceToken.is_active == True)
        )
        total_active = total_result.scalar() or 0

    return {
        "total_active": total_active,
        "by_platform": platform_stats,
    }


@router.post("/test-push")
async def send_test_push():
    """Send a test push notification to all active devices."""
    result = await push_service.send_to_all(
        title="Optix Test Notification",
        body="If you see this, push notifications are working!",
        data={"type": "market_monitor", "signal": "test"},
    )
    return {
        "message": "Test push sent",
        **result,
    }
