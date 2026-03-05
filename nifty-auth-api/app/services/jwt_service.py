from datetime import datetime, timedelta
from typing import Optional
from jose import jwt, JWTError

from app.config import settings


class JWTService:
    @staticmethod
    def create_access_token(user_id: str) -> tuple[str, int]:
        """
        Create an access token for the user.
        Returns tuple of (token, expires_in_seconds).
        """
        expires_delta = timedelta(minutes=settings.access_token_expire_minutes)
        expire = datetime.utcnow() + expires_delta

        payload = {
            "sub": str(user_id),
            "type": "access",
            "iat": datetime.utcnow(),
            "exp": expire,
        }

        token = jwt.encode(
            payload,
            settings.jwt_secret_key,
            algorithm=settings.jwt_algorithm,
        )

        return token, settings.access_token_expire_minutes * 60

    @staticmethod
    def create_refresh_token(user_id: str, session_id: str) -> tuple[str, datetime]:
        """
        Create a refresh token for the user.
        Returns tuple of (token, expires_at_datetime).
        """
        expires_delta = timedelta(days=settings.refresh_token_expire_days)
        expire = datetime.utcnow() + expires_delta

        payload = {
            "sub": str(user_id),
            "type": "refresh",
            "session_id": str(session_id),
            "iat": datetime.utcnow(),
            "exp": expire,
        }

        token = jwt.encode(
            payload,
            settings.jwt_secret_key,
            algorithm=settings.jwt_algorithm,
        )

        return token, expire

    @staticmethod
    def verify_access_token(token: str) -> Optional[dict]:
        """
        Verify an access token and return its payload.
        Returns None if token is invalid.
        """
        try:
            payload = jwt.decode(
                token,
                settings.jwt_secret_key,
                algorithms=[settings.jwt_algorithm],
            )

            if payload.get("type") != "access":
                return None

            return payload
        except JWTError:
            return None

    @staticmethod
    def verify_refresh_token(token: str) -> Optional[dict]:
        """
        Verify a refresh token and return its payload.
        Returns None if token is invalid.
        """
        try:
            payload = jwt.decode(
                token,
                settings.jwt_secret_key,
                algorithms=[settings.jwt_algorithm],
            )

            if payload.get("type") != "refresh":
                return None

            return payload
        except JWTError:
            return None

    @staticmethod
    def decode_token_unverified(token: str) -> Optional[dict]:
        """
        Decode a token without verification.
        Useful for extracting claims from expired tokens.
        """
        try:
            return jwt.decode(
                token,
                settings.jwt_secret_key,
                algorithms=[settings.jwt_algorithm],
                options={"verify_exp": False},
            )
        except JWTError:
            return None
