"""
Device Token API Router — FCM token registration for push notifications
"""

import logging
from datetime import datetime
from typing import Optional
from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
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

# Optional auth — returns user if authenticated, None otherwise
_optional_bearer = HTTPBearer(auto_error=False)


async def get_optional_user(
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(_optional_bearer),
    db: AsyncSession = Depends(get_db),
) -> Optional[User]:
    """Return current user if valid token provided, else None."""
    if not credentials:
        return None
    try:
        from app.services.jwt_service import JWTService
        jwt_service = JWTService()
        payload = jwt_service.decode_token(credentials.credentials)
        user_id = payload.get("sub")
        if not user_id:
            return None
        result = await db.execute(select(User).where(User.id == user_id))
        return result.scalar_one_or_none()
    except Exception:
        return None


@router.post("/register", response_model=DeviceResponse)
async def register_device(
    data: DeviceRegister,
    db: AsyncSession = Depends(get_db),
    current_user: Optional[User] = Depends(get_optional_user),
):
    """Register or update an FCM device token (works with or without auth)"""
    user_id = current_user.id if current_user else None

    # Check if token already exists
    result = await db.execute(
        select(DeviceToken).where(DeviceToken.token == data.token)
    )
    existing = result.scalar_one_or_none()

    if existing:
        # Update ownership if user is authenticated
        if user_id:
            existing.user_id = user_id
        existing.platform = data.platform.value
        existing.device_name = data.device_name
        existing.is_active = True
        existing.last_used_at = datetime.utcnow()
        existing.updated_at = datetime.utcnow()
        await db.commit()
        await db.refresh(existing)
        logger.info(f"Device token updated: {existing.id} for user {user_id or 'anonymous'}")
        return DeviceResponse.model_validate(existing)

    # Create new token
    device = DeviceToken(
        user_id=user_id,
        platform=data.platform.value,
        token=data.token,
        device_name=data.device_name,
        last_used_at=datetime.utcnow(),
    )

    db.add(device)
    await db.commit()
    await db.refresh(device)

    logger.info(f"Device token registered: {device.id} ({data.platform.value}) for user {user_id or 'anonymous'}")
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
