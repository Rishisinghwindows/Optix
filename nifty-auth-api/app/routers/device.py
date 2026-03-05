"""
Device Token API Router — FCM token registration for push notifications
"""

import logging
from datetime import datetime
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select, and_
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models.device_token import DeviceToken
from app.models.user import User
from app.schemas.device import (
    DeviceRegister,
    DeviceResponse,
    DeviceListResponse,
)
from app.routers.auth import get_current_user

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/devices", tags=["Devices"])


@router.post("/register", response_model=DeviceResponse)
async def register_device(
    data: DeviceRegister,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Register or update an FCM device token"""
    # Check if token already exists
    result = await db.execute(
        select(DeviceToken).where(DeviceToken.token == data.token)
    )
    existing = result.scalar_one_or_none()

    if existing:
        # Update ownership if different user or reactivate
        existing.user_id = current_user.id
        existing.platform = data.platform.value
        existing.device_name = data.device_name
        existing.is_active = True
        existing.last_used_at = datetime.utcnow()
        existing.updated_at = datetime.utcnow()
        await db.commit()
        await db.refresh(existing)
        logger.info(f"Device token updated: {existing.id} for user {current_user.id}")
        return DeviceResponse.model_validate(existing)

    # Create new token
    device = DeviceToken(
        user_id=current_user.id,
        platform=data.platform.value,
        token=data.token,
        device_name=data.device_name,
        last_used_at=datetime.utcnow(),
    )

    db.add(device)
    await db.commit()
    await db.refresh(device)

    logger.info(f"Device token registered: {device.id} ({data.platform.value}) for user {current_user.id}")
    return DeviceResponse.model_validate(device)


@router.get("", response_model=DeviceListResponse)
async def list_devices(
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """List all registered devices for the current user"""
    result = await db.execute(
        select(DeviceToken)
        .where(and_(DeviceToken.user_id == current_user.id, DeviceToken.is_active == True))
        .order_by(DeviceToken.last_used_at.desc())
    )
    devices = result.scalars().all()

    return DeviceListResponse(
        devices=[DeviceResponse.model_validate(d) for d in devices],
        total=len(devices),
    )


@router.delete("/{token}")
async def unregister_device(
    token: str,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Unregister (deactivate) a device token"""
    result = await db.execute(
        select(DeviceToken).where(
            and_(DeviceToken.token == token, DeviceToken.user_id == current_user.id)
        )
    )
    device = result.scalar_one_or_none()

    if not device:
        raise HTTPException(status_code=404, detail="Device token not found")

    device.is_active = False
    await db.commit()

    logger.info(f"Device token deactivated: {device.id}")
    return {"message": "Device unregistered", "id": device.id}
