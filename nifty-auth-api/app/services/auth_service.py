import uuid
import hashlib
from datetime import datetime
from typing import Optional, Union
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials

from app.models.user import User
from app.models.session import Session
from app.services.jwt_service import JWTService
from app.schemas.auth import DeviceInfo, AuthResponse, UserInAuth
from app.database import get_db

# Security scheme for JWT bearer tokens
security = HTTPBearer()


class AuthService:
    @staticmethod
    def hash_token(token: str) -> str:
        """Hash a token for storage using SHA256."""
        return hashlib.sha256(token.encode()).hexdigest()

    @staticmethod
    def verify_token_hash(token: str, hashed: str) -> bool:
        """Verify a token against its hash."""
        return hashlib.sha256(token.encode()).hexdigest() == hashed

    @staticmethod
    async def get_or_create_user_by_phone(
        db: AsyncSession,
        phone: str,
    ) -> User:
        """Get existing user by phone or create a new one."""
        result = await db.execute(
            select(User).where(User.phone == phone)
        )
        user = result.scalar_one_or_none()

        if not user:
            user = User(
                phone=phone,
                auth_provider="phone",
                is_verified=True,
            )
            db.add(user)
            await db.flush()

        user.last_login_at = datetime.utcnow()
        return user

    @staticmethod
    async def get_or_create_user_by_social(
        db: AsyncSession,
        provider: str,
        provider_id: str,
        email: Optional[str] = None,
        full_name: Optional[str] = None,
        avatar_url: Optional[str] = None,
    ) -> User:
        """Get existing user by social provider or create a new one."""
        # First, try to find by provider_id
        result = await db.execute(
            select(User)
            .where(User.auth_provider == provider)
            .where(User.provider_id == provider_id)
        )
        user = result.scalar_one_or_none()

        if not user and email:
            # Try to find by email and link the account
            result = await db.execute(
                select(User).where(User.email == email)
            )
            user = result.scalar_one_or_none()

            if user:
                # Link social account to existing user
                if not user.provider_id:
                    user.auth_provider = provider
                    user.provider_id = provider_id

        if not user:
            # Create new user
            user = User(
                email=email,
                full_name=full_name,
                avatar_url=avatar_url,
                auth_provider=provider,
                provider_id=provider_id,
                is_verified=True,
            )
            db.add(user)
            await db.flush()
        else:
            # Update profile info if not set
            if not user.full_name and full_name:
                user.full_name = full_name
            if not user.avatar_url and avatar_url:
                user.avatar_url = avatar_url

        user.last_login_at = datetime.utcnow()
        return user

    @staticmethod
    async def get_user_by_id(
        db: AsyncSession,
        user_id: str,
    ) -> Optional[User]:
        """Get user by ID."""
        # Convert UUID to string if needed
        user_id_str = str(user_id) if isinstance(user_id, uuid.UUID) else user_id
        result = await db.execute(
            select(User).where(User.id == user_id_str).where(User.is_active == True)
        )
        return result.scalar_one_or_none()

    @staticmethod
    async def create_session(
        db: AsyncSession,
        user: User,
        device_info: Optional[DeviceInfo] = None,
        ip_address: Optional[str] = None,
    ) -> tuple[str, str, int]:
        """
        Create a new session for the user.
        Returns (access_token, refresh_token, expires_in_seconds).
        """
        session_id = str(uuid.uuid4())  # String for SQLite compatibility

        # Create tokens (user.id is already a string)
        access_token, expires_in = JWTService.create_access_token(str(user.id))
        refresh_token, refresh_expires_at = JWTService.create_refresh_token(
            str(user.id), session_id
        )

        # Store session with hashed refresh token
        session = Session(
            id=session_id,
            user_id=str(user.id),
            refresh_token_hash=AuthService.hash_token(refresh_token),
            device_info=device_info.model_dump() if device_info else None,
            ip_address=ip_address,
            expires_at=refresh_expires_at,
        )
        db.add(session)

        return access_token, refresh_token, expires_in

    @staticmethod
    async def build_auth_response(
        user: User,
        access_token: str,
        refresh_token: str,
        expires_in: int,
    ) -> AuthResponse:
        """Build the authentication response."""
        return AuthResponse(
            access_token=access_token,
            refresh_token=refresh_token,
            expires_in=expires_in,
            user=UserInAuth(
                id=str(user.id),
                phone=user.phone,
                email=user.email,
                full_name=user.full_name,
                avatar_url=user.avatar_url,
                is_verified=user.is_verified,
                paper_trading_balance=float(user.paper_trading_balance),
                is_premium=user.is_premium,
            ),
        )

    @staticmethod
    async def refresh_access_token(
        db: AsyncSession,
        refresh_token: str,
    ) -> Optional[tuple[str, int]]:
        """
        Refresh the access token using a valid refresh token.
        Returns (new_access_token, expires_in_seconds) or None if invalid.
        """
        # Verify refresh token
        payload = JWTService.verify_refresh_token(refresh_token)
        if not payload:
            return None

        # Keep as strings for SQLite compatibility
        user_id = payload["sub"]
        session_id = payload["session_id"]

        # Find the session
        result = await db.execute(
            select(Session)
            .where(Session.id == session_id)
            .where(Session.user_id == user_id)
            .where(Session.revoked_at.is_(None))
        )
        session = result.scalar_one_or_none()

        if not session or not session.is_valid:
            return None

        # Verify the token hash matches
        if not AuthService.verify_token_hash(refresh_token, session.refresh_token_hash):
            return None

        # Generate new access token
        return JWTService.create_access_token(user_id)

    @staticmethod
    async def revoke_session(
        db: AsyncSession,
        session_id: str,
        user_id: str,
    ) -> bool:
        """Revoke a specific session."""
        # Convert to strings if UUID objects
        session_id_str = str(session_id) if isinstance(session_id, uuid.UUID) else session_id
        user_id_str = str(user_id) if isinstance(user_id, uuid.UUID) else user_id

        result = await db.execute(
            select(Session)
            .where(Session.id == session_id_str)
            .where(Session.user_id == user_id_str)
            .where(Session.revoked_at.is_(None))
        )
        session = result.scalar_one_or_none()

        if not session:
            return False

        session.revoked_at = datetime.utcnow()
        return True

    @staticmethod
    async def revoke_all_sessions(
        db: AsyncSession,
        user_id: str,
        except_session_id: Optional[str] = None,
    ) -> int:
        """
        Revoke all sessions for a user.
        Returns the number of sessions revoked.
        """
        # Convert to strings if UUID objects
        user_id_str = str(user_id) if isinstance(user_id, uuid.UUID) else user_id
        except_id_str = str(except_session_id) if except_session_id and isinstance(except_session_id, uuid.UUID) else except_session_id

        result = await db.execute(
            select(Session)
            .where(Session.user_id == user_id_str)
            .where(Session.revoked_at.is_(None))
        )
        sessions = result.scalars().all()

        count = 0
        for session in sessions:
            if except_id_str and session.id == except_id_str:
                continue
            session.revoked_at = datetime.utcnow()
            count += 1

        return count

    @staticmethod
    async def get_user_sessions(
        db: AsyncSession,
        user_id: str,
    ) -> list[Session]:
        """Get all active sessions for a user."""
        # Convert to string if UUID object
        user_id_str = str(user_id) if isinstance(user_id, uuid.UUID) else user_id

        result = await db.execute(
            select(Session)
            .where(Session.user_id == user_id_str)
            .where(Session.revoked_at.is_(None))
            .where(Session.expires_at > datetime.utcnow())
            .order_by(Session.created_at.desc())
        )
        return list(result.scalars().all())


auth_service = AuthService()


# ==================== FastAPI Dependencies ====================

async def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(security),
    db: AsyncSession = Depends(get_db),
) -> User:
    """
    FastAPI dependency to get the current authenticated user.
    Raises HTTPException if not authenticated.
    """
    token = credentials.credentials

    # Verify the access token
    payload = JWTService.verify_access_token(token)
    if not payload:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired token",
            headers={"WWW-Authenticate": "Bearer"},
        )

    try:
        # Keep user_id as string
        user_id = payload["sub"]
    except KeyError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid token payload",
            headers={"WWW-Authenticate": "Bearer"},
        )

    # Get the user from database
    user = await auth_service.get_user_by_id(db, user_id)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="User not found",
            headers={"WWW-Authenticate": "Bearer"},
        )

    if not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="User account is inactive",
            headers={"WWW-Authenticate": "Bearer"},
        )

    return user


async def get_current_user_optional(
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(HTTPBearer(auto_error=False)),
    db: AsyncSession = Depends(get_db),
) -> Optional[User]:
    """
    FastAPI dependency to optionally get the current user.
    Returns None if not authenticated (no exception).
    """
    if not credentials:
        return None

    try:
        payload = JWTService.verify_access_token(credentials.credentials)
        if not payload:
            return None

        # Keep user_id as string
        user_id = payload["sub"]
        user = await auth_service.get_user_by_id(db, user_id)
        return user if user and user.is_active else None
    except Exception:
        return None
