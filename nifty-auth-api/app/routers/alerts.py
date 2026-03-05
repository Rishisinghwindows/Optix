"""
Alert API Router - CRUD operations for price alerts
"""

from datetime import datetime
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import select, func, and_
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.database import get_db
from app.models.alert import Alert, AlertNotification
from app.models.user import User
from app.schemas.alert import (
    AlertCreate,
    AlertUpdate,
    AlertResponse,
    AlertListResponse,
    NotificationResponse,
    NotificationListResponse,
    MarkReadRequest,
    MarkReadResponse,
    AlertStats,
)
from app.routers.auth import get_current_user
from app.services.alert_service import alert_checker_service

router = APIRouter(prefix="/alerts", tags=["Alerts"])


# ============== Helper Functions ==============

def alert_to_response(alert: Alert, current_value: Optional[float] = None) -> AlertResponse:
    """Convert Alert model to response schema"""
    # Calculate gap if we have current value
    gap = None
    gap_pct = None
    if current_value is not None and alert.target_value:
        target = float(alert.target_value)
        gap = target - current_value
        if current_value > 0:
            gap_pct = (gap / current_value) * 100

    return AlertResponse(
        id=alert.id,
        user_id=alert.user_id,
        alert_type=alert.alert_type,
        symbol=alert.symbol,
        condition=alert.condition,
        target_value=float(alert.target_value),
        strike_price=float(alert.strike_price) if alert.strike_price else None,
        option_type=alert.option_type,
        expiry=alert.expiry,
        is_active=alert.is_active,
        is_triggered=alert.is_triggered,
        triggered_at=alert.triggered_at,
        triggered_value=float(alert.triggered_value) if alert.triggered_value else None,
        last_checked_value=float(alert.last_checked_value) if alert.last_checked_value else None,
        last_checked_at=alert.last_checked_at,
        name=alert.name,
        display_name=alert.get_display_name(),
        note=alert.note,
        current_gap=round(gap, 2) if gap is not None else None,
        gap_percentage=round(gap_pct, 2) if gap_pct is not None else None,
        created_at=alert.created_at,
        updated_at=alert.updated_at,
    )


# ============== Alert CRUD ==============

@router.post("", response_model=AlertResponse)
async def create_alert(
    alert_data: AlertCreate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Create a new price alert"""
    # Validate option fields for option_premium alerts
    if alert_data.alert_type.value == "option_premium":
        if not all([alert_data.strike_price, alert_data.option_type, alert_data.expiry]):
            raise HTTPException(
                status_code=400,
                detail="strike_price, option_type, and expiry are required for option_premium alerts"
            )

    # Check alert limit (max 20 active alerts per user)
    result = await db.execute(
        select(func.count(Alert.id)).where(
            and_(Alert.user_id == current_user.id, Alert.is_active == True)
        )
    )
    active_count = result.scalar()
    if active_count >= 20:
        raise HTTPException(
            status_code=400,
            detail="Maximum 20 active alerts allowed. Please delete or deactivate some alerts."
        )

    # Create alert
    alert = Alert(
        user_id=current_user.id,
        alert_type=alert_data.alert_type.value,
        symbol=alert_data.symbol.value,
        condition=alert_data.condition.value,
        target_value=alert_data.target_value,
        strike_price=alert_data.strike_price,
        option_type=alert_data.option_type.value if alert_data.option_type else None,
        expiry=alert_data.expiry,
        name=alert_data.name,
        note=alert_data.note,
    )

    db.add(alert)
    await db.commit()
    await db.refresh(alert)

    return alert_to_response(alert)


@router.get("", response_model=AlertListResponse)
async def list_alerts(
    is_active: Optional[bool] = Query(None, description="Filter by active status"),
    symbol: Optional[str] = Query(None, description="Filter by symbol"),
    alert_type: Optional[str] = Query(None, description="Filter by alert type"),
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """List all alerts for the current user"""
    query = select(Alert).where(Alert.user_id == current_user.id)

    if is_active is not None:
        query = query.where(Alert.is_active == is_active)
    if symbol:
        query = query.where(Alert.symbol == symbol.upper())
    if alert_type:
        query = query.where(Alert.alert_type == alert_type)

    query = query.order_by(Alert.created_at.desc())

    result = await db.execute(query)
    alerts = result.scalars().all()

    # Count stats
    active_count = sum(1 for a in alerts if a.is_active)
    triggered_count = sum(1 for a in alerts if a.is_triggered)

    return AlertListResponse(
        alerts=[alert_to_response(a) for a in alerts],
        total=len(alerts),
        active_count=active_count,
        triggered_count=triggered_count,
    )


@router.get("/stats", response_model=AlertStats)
async def get_alert_stats(
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Get alert statistics for the current user"""
    # Get all user's alerts
    result = await db.execute(
        select(Alert).where(Alert.user_id == current_user.id)
    )
    alerts = result.scalars().all()

    # Get unread notifications count
    notif_result = await db.execute(
        select(func.count(AlertNotification.id)).where(
            and_(
                AlertNotification.user_id == current_user.id,
                AlertNotification.is_read == False
            )
        )
    )
    unread_count = notif_result.scalar()

    # Calculate stats
    today = datetime.utcnow().date()
    triggered_today = sum(
        1 for a in alerts
        if a.triggered_at and a.triggered_at.date() == today
    )

    alerts_by_type = {}
    alerts_by_symbol = {}
    for a in alerts:
        alerts_by_type[a.alert_type] = alerts_by_type.get(a.alert_type, 0) + 1
        alerts_by_symbol[a.symbol] = alerts_by_symbol.get(a.symbol, 0) + 1

    return AlertStats(
        total_alerts=len(alerts),
        active_alerts=sum(1 for a in alerts if a.is_active),
        triggered_today=triggered_today,
        unread_notifications=unread_count,
        alerts_by_type=alerts_by_type,
        alerts_by_symbol=alerts_by_symbol,
    )


@router.get("/{alert_id}", response_model=AlertResponse)
async def get_alert(
    alert_id: str,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Get a specific alert"""
    result = await db.execute(
        select(Alert).where(
            and_(Alert.id == alert_id, Alert.user_id == current_user.id)
        )
    )
    alert = result.scalar_one_or_none()

    if not alert:
        raise HTTPException(status_code=404, detail="Alert not found")

    return alert_to_response(alert)


@router.put("/{alert_id}", response_model=AlertResponse)
async def update_alert(
    alert_id: str,
    alert_data: AlertUpdate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Update an alert"""
    result = await db.execute(
        select(Alert).where(
            and_(Alert.id == alert_id, Alert.user_id == current_user.id)
        )
    )
    alert = result.scalar_one_or_none()

    if not alert:
        raise HTTPException(status_code=404, detail="Alert not found")

    # Update fields
    update_data = alert_data.model_dump(exclude_unset=True)
    for field, value in update_data.items():
        setattr(alert, field, value)

    # If reactivating a triggered alert, reset triggered state
    if alert_data.is_active and alert.is_triggered:
        alert.is_triggered = False
        alert.triggered_at = None
        alert.triggered_value = None

    await db.commit()
    await db.refresh(alert)

    return alert_to_response(alert)


@router.delete("/{alert_id}")
async def delete_alert(
    alert_id: str,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Delete an alert"""
    result = await db.execute(
        select(Alert).where(
            and_(Alert.id == alert_id, Alert.user_id == current_user.id)
        )
    )
    alert = result.scalar_one_or_none()

    if not alert:
        raise HTTPException(status_code=404, detail="Alert not found")

    await db.delete(alert)
    await db.commit()

    return {"message": "Alert deleted successfully"}


# ============== Notifications ==============

@router.get("/notifications/list", response_model=NotificationListResponse)
async def list_notifications(
    limit: int = Query(50, le=100),
    offset: int = Query(0),
    unread_only: bool = Query(False),
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """List notifications for the current user"""
    query = select(AlertNotification).where(
        AlertNotification.user_id == current_user.id
    )

    if unread_only:
        query = query.where(AlertNotification.is_read == False)

    query = query.order_by(AlertNotification.created_at.desc())
    query = query.offset(offset).limit(limit)

    result = await db.execute(query)
    notifications = result.scalars().all()

    # Get unread count
    unread_result = await db.execute(
        select(func.count(AlertNotification.id)).where(
            and_(
                AlertNotification.user_id == current_user.id,
                AlertNotification.is_read == False
            )
        )
    )
    unread_count = unread_result.scalar()

    # Get total count
    total_result = await db.execute(
        select(func.count(AlertNotification.id)).where(
            AlertNotification.user_id == current_user.id
        )
    )
    total = total_result.scalar()

    # Convert to response with alert info
    notif_responses = []
    for n in notifications:
        # Get alert info
        alert_result = await db.execute(
            select(Alert).where(Alert.id == n.alert_id)
        )
        alert = alert_result.scalar_one_or_none()

        notif_responses.append(NotificationResponse(
            id=n.id,
            alert_id=n.alert_id,
            user_id=n.user_id,
            title=n.title,
            message=n.message,
            triggered_value=float(n.triggered_value) if n.triggered_value else None,
            is_read=n.is_read,
            read_at=n.read_at,
            created_at=n.created_at,
            alert_type=alert.alert_type if alert else None,
            symbol=alert.symbol if alert else None,
        ))

    return NotificationListResponse(
        notifications=notif_responses,
        total=total,
        unread_count=unread_count,
    )


@router.post("/notifications/mark-read", response_model=MarkReadResponse)
async def mark_notifications_read(
    request: MarkReadRequest,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Mark notifications as read"""
    result = await db.execute(
        select(AlertNotification).where(
            and_(
                AlertNotification.id.in_(request.notification_ids),
                AlertNotification.user_id == current_user.id,
                AlertNotification.is_read == False
            )
        )
    )
    notifications = result.scalars().all()

    now = datetime.utcnow()
    for n in notifications:
        n.is_read = True
        n.read_at = now

    await db.commit()

    return MarkReadResponse(
        marked_count=len(notifications),
        message=f"Marked {len(notifications)} notifications as read"
    )


@router.post("/notifications/mark-all-read", response_model=MarkReadResponse)
async def mark_all_notifications_read(
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Mark all notifications as read"""
    result = await db.execute(
        select(AlertNotification).where(
            and_(
                AlertNotification.user_id == current_user.id,
                AlertNotification.is_read == False
            )
        )
    )
    notifications = result.scalars().all()

    now = datetime.utcnow()
    for n in notifications:
        n.is_read = True
        n.read_at = now

    await db.commit()

    return MarkReadResponse(
        marked_count=len(notifications),
        message=f"Marked {len(notifications)} notifications as read"
    )


# ============== Test/Debug Endpoints ==============

@router.get("/{alert_id}/check")
async def check_alert(
    alert_id: str,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Manually check an alert against current market data.
    Useful for testing if an alert would trigger.
    """
    # Verify user owns this alert
    result = await db.execute(
        select(Alert).where(
            and_(Alert.id == alert_id, Alert.user_id == current_user.id)
        )
    )
    alert = result.scalar_one_or_none()

    if not alert:
        raise HTTPException(status_code=404, detail="Alert not found")

    # Check the alert
    check_result = await alert_checker_service.check_single_alert(alert_id)
    return check_result


@router.get("/service/status")
async def alert_service_status():
    """Get the status of the alert checker service"""
    return {
        "running": alert_checker_service._running,
        "check_interval_seconds": alert_checker_service._check_interval,
        "cache_size": len(alert_checker_service._market_cache),
    }
