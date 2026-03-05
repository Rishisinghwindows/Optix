from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.schemas.user import UserResponse, UserUpdate, SessionResponse, DeviceInfoResponse
from app.middleware.auth_middleware import get_current_user
from app.services.auth_service import auth_service
from app.models.user import User


router = APIRouter(prefix="/user", tags=["User"])


@router.get("/me", response_model=UserResponse)
async def get_current_user_profile(
    current_user: User = Depends(get_current_user),
):
    """
    Get the current user's profile.
    """
    return UserResponse.model_validate(current_user)


@router.patch("/me", response_model=UserResponse)
async def update_user_profile(
    updates: UserUpdate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Update the current user's profile.
    Only provided fields will be updated.
    """
    update_data = updates.model_dump(exclude_unset=True)

    if not update_data:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="No fields to update",
        )

    # Check email uniqueness if being updated
    if "email" in update_data and update_data["email"]:
        from sqlalchemy import select
        from app.models.user import User as UserModel

        result = await db.execute(
            select(UserModel)
            .where(UserModel.email == update_data["email"])
            .where(UserModel.id != current_user.id)
        )
        if result.scalar_one_or_none():
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Email already in use",
            )

    # Update user fields
    for field, value in update_data.items():
        setattr(current_user, field, value)

    await db.commit()
    await db.refresh(current_user)

    return UserResponse.model_validate(current_user)


@router.delete("/me")
async def delete_user_account(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Delete the current user's account.
    This action is irreversible.
    """
    # Soft delete - mark as inactive
    current_user.is_active = False

    # Revoke all sessions
    await auth_service.revoke_all_sessions(db, current_user.id)

    await db.commit()

    return {"message": "Account deleted successfully"}


@router.get("/sessions", response_model=list[SessionResponse])
async def get_user_sessions(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Get all active sessions for the current user.
    """
    sessions = await auth_service.get_user_sessions(db, current_user.id)

    # Mark the current session (most recent one)
    result = []
    for i, session in enumerate(sessions):
        session_response = SessionResponse(
            id=session.id,
            device_info=(
                DeviceInfoResponse(**session.device_info)
                if session.device_info
                else None
            ),
            ip_address=session.ip_address,
            created_at=session.created_at,
            expires_at=session.expires_at,
            is_current=(i == 0),  # Most recent session
        )
        result.append(session_response)

    return result


@router.delete("/sessions/{session_id}")
async def revoke_session(
    session_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Revoke a specific session.
    """
    import uuid

    try:
        session_uuid = uuid.UUID(session_id)
    except ValueError:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid session ID",
        )

    success = await auth_service.revoke_session(db, session_uuid, current_user.id)

    if not success:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Session not found",
        )

    await db.commit()

    return {"message": "Session revoked successfully"}
