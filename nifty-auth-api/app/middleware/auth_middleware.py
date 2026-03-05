import uuid
from typing import Optional
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models.user import User
from app.services.jwt_service import JWTService
from app.services.auth_service import AuthService


security = HTTPBearer(auto_error=False)


async def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(security),
    db: AsyncSession = Depends(get_db),
) -> User:
    """
    Dependency that validates the JWT token and returns the current user.
    Raises 401 if token is invalid or user not found.
    """
    if not credentials:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Not authenticated",
        )

    token = credentials.credentials

    payload = JWTService.verify_access_token(token)
    if not payload:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired token",
            headers={"WWW-Authenticate": "Bearer"},
        )

    try:
        user_id = uuid.UUID(payload["sub"])
    except (KeyError, ValueError):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid token payload",
            headers={"WWW-Authenticate": "Bearer"},
        )

    user = await AuthService.get_user_by_id(db, user_id)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="User not found or inactive",
            headers={"WWW-Authenticate": "Bearer"},
        )

    return user


async def get_optional_user(
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(security),
    db: AsyncSession = Depends(get_db),
) -> Optional[User]:
    """
    Dependency that validates the JWT token if present.
    Returns the user if valid token, None otherwise.
    Does not raise an error for missing/invalid tokens.
    """
    if not credentials:
        return None

    token = credentials.credentials
    payload = JWTService.verify_access_token(token)
    if not payload:
        return None

    try:
        user_id = uuid.UUID(payload["sub"])
    except (KeyError, ValueError):
        return None

    return await AuthService.get_user_by_id(db, user_id)


class CurrentUser:
    """
    Class-based dependency for more flexibility.
    Can be subclassed to add additional checks (e.g., premium only).
    """

    def __init__(self, required: bool = True, verified_only: bool = False):
        self.required = required
        self.verified_only = verified_only

    async def __call__(
        self,
        credentials: Optional[HTTPAuthorizationCredentials] = Depends(security),
        db: AsyncSession = Depends(get_db),
    ) -> Optional[User]:
        if not credentials:
            if self.required:
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN,
                    detail="Not authenticated",
                )
            return None

        token = credentials.credentials
        payload = JWTService.verify_access_token(token)

        if not payload:
            if self.required:
                raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="Invalid or expired token",
                    headers={"WWW-Authenticate": "Bearer"},
                )
            return None

        try:
            user_id = uuid.UUID(payload["sub"])
        except (KeyError, ValueError):
            if self.required:
                raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="Invalid token payload",
                    headers={"WWW-Authenticate": "Bearer"},
                )
            return None

        user = await AuthService.get_user_by_id(db, user_id)

        if not user:
            if self.required:
                raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="User not found or inactive",
                    headers={"WWW-Authenticate": "Bearer"},
                )
            return None

        if self.verified_only and not user.is_verified:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Email verification required",
            )

        return user
