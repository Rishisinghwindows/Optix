import logging

from fastapi import APIRouter, Depends, HTTPException, status, Request
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.schemas.auth import (
    OTPSendRequest,
    OTPSendResponse,
    OTPVerifyRequest,
    AuthResponse,
    RefreshTokenRequest,
    RefreshTokenResponse,
    SocialLoginRequest,
)
from app.services.otp_service import otp_service
from app.services.auth_service import auth_service
from app.services.social_auth import social_auth_service
from app.utils.sms import sms_service
from app.utils.helpers import get_client_ip
from app.middleware.auth_middleware import get_current_user
from app.models.user import User
from app.config import settings

logger = logging.getLogger(__name__)


router = APIRouter(prefix="/auth", tags=["Authentication"])


@router.post("/otp/send", response_model=OTPSendResponse)
async def send_otp(
    request: OTPSendRequest,
    db: AsyncSession = Depends(get_db),
):
    """
    Send OTP to the specified phone number.
    Rate limited to 3 requests per hour per phone number.
    """
    # Check rate limit
    is_allowed, wait_seconds = await otp_service.check_rate_limit(
        request.phone, db
    )
    if not is_allowed:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail=f"Rate limit exceeded. Try again in {wait_seconds} seconds.",
        )

    # Check cooldown
    cooldown_ok, cooldown_remaining = await otp_service.check_cooldown(
        request.phone, db
    )
    if not cooldown_ok:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail=f"Please wait {cooldown_remaining} seconds before requesting another OTP.",
        )

    # Generate and store OTP
    otp = otp_service.generate_otp()
    stored = await otp_service.store_otp(
        request.phone, otp, request.purpose, db
    )

    if not stored:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to generate OTP. Please try again.",
        )

    # Send OTP via SMS
    sent = await sms_service.send_otp(request.phone, otp)
    if not sent and settings.environment != "development":
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to send OTP. Please try again.",
        )

    return OTPSendResponse(
        success=True,
        message="OTP sent successfully",
        expires_in=settings.otp_expire_minutes * 60,
        resend_after=settings.otp_resend_cooldown_seconds,
    )


@router.post("/otp/verify", response_model=AuthResponse)
async def verify_otp(
    request: OTPVerifyRequest,
    http_request: Request,
    db: AsyncSession = Depends(get_db),
):
    """
    Verify OTP and authenticate the user.
    Returns access and refresh tokens on success.
    """
    # Verify OTP
    is_valid, error_message = await otp_service.verify_otp(
        request.phone, request.otp, "login", db
    )

    if not is_valid:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=error_message,
        )

    # Get or create user
    user = await auth_service.get_or_create_user_by_phone(db, request.phone)

    # Create session and tokens
    ip_address = get_client_ip(http_request)
    access_token, refresh_token, expires_in = await auth_service.create_session(
        db, user, request.device_info, ip_address
    )

    await db.commit()

    return await auth_service.build_auth_response(
        user, access_token, refresh_token, expires_in
    )


@router.post("/social/google", response_model=AuthResponse)
async def google_login(
    request: SocialLoginRequest,
    http_request: Request,
    db: AsyncSession = Depends(get_db),
):
    """
    Authenticate with Google.
    Requires an ID token from Google Sign-In.
    """
    try:
        if not request.id_token:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="ID token is required for Google login",
            )

        # Verify Google token
        user_info = await social_auth_service.verify_google_token(request.id_token)
        if not user_info:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid Google token",
            )

        # Get or create user
        user = await auth_service.get_or_create_user_by_social(
            db,
            provider="google",
            provider_id=user_info["provider_id"],
            email=user_info.get("email"),
            full_name=user_info.get("full_name"),
            avatar_url=user_info.get("avatar_url"),
        )

        # Create session and tokens
        ip_address = get_client_ip(http_request)
        access_token, refresh_token, expires_in = await auth_service.create_session(
            db, user, request.device_info, ip_address
        )

        await db.commit()

        return await auth_service.build_auth_response(
            user, access_token, refresh_token, expires_in
        )
    except HTTPException:
        raise
    except Exception as e:
        logger.exception("Google login failed")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Authentication failed. Please try again.",
        )


@router.post("/social/apple", response_model=AuthResponse)
async def apple_login(
    request: SocialLoginRequest,
    http_request: Request,
    db: AsyncSession = Depends(get_db),
):
    """
    Authenticate with Apple.
    Requires an identity token from Sign in with Apple.
    """
    if not request.id_token:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Identity token is required for Apple login",
        )

    # Verify Apple token
    user_info = await social_auth_service.verify_apple_token(request.id_token)
    if not user_info:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid Apple token",
        )

    # Get or create user
    user = await auth_service.get_or_create_user_by_social(
        db,
        provider="apple",
        provider_id=user_info["provider_id"],
        email=user_info.get("email"),
        full_name=user_info.get("full_name"),
        avatar_url=user_info.get("avatar_url"),
    )

    # Create session and tokens
    ip_address = get_client_ip(http_request)
    access_token, refresh_token, expires_in = await auth_service.create_session(
        db, user, request.device_info, ip_address
    )

    await db.commit()

    return await auth_service.build_auth_response(
        user, access_token, refresh_token, expires_in
    )


@router.post("/social/facebook", response_model=AuthResponse)
async def facebook_login(
    request: SocialLoginRequest,
    http_request: Request,
    db: AsyncSession = Depends(get_db),
):
    """
    Authenticate with Facebook.
    Requires an access token from Facebook Login.
    """
    if not request.access_token:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Access token is required for Facebook login",
        )

    # Verify Facebook token
    user_info = await social_auth_service.verify_facebook_token(request.access_token)
    if not user_info:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid Facebook token",
        )

    # Get or create user
    user = await auth_service.get_or_create_user_by_social(
        db,
        provider="facebook",
        provider_id=user_info["provider_id"],
        email=user_info.get("email"),
        full_name=user_info.get("full_name"),
        avatar_url=user_info.get("avatar_url"),
    )

    # Create session and tokens
    ip_address = get_client_ip(http_request)
    access_token, refresh_token, expires_in = await auth_service.create_session(
        db, user, request.device_info, ip_address
    )

    await db.commit()

    return await auth_service.build_auth_response(
        user, access_token, refresh_token, expires_in
    )


@router.post("/refresh", response_model=RefreshTokenResponse)
async def refresh_token(
    request: RefreshTokenRequest,
    db: AsyncSession = Depends(get_db),
):
    """
    Refresh the access token using a valid refresh token.
    """
    result = await auth_service.refresh_access_token(db, request.refresh_token)

    if not result:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired refresh token",
        )

    access_token, expires_in = result

    return RefreshTokenResponse(
        access_token=access_token,
        expires_in=expires_in,
    )


@router.post("/logout")
async def logout(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Logout the current session.
    The access token in the Authorization header will be used to identify the session.
    """
    # Note: In a production environment, you might want to extract the session_id
    # from the access token and revoke that specific session.
    # For simplicity, we'll revoke the most recent session.

    sessions = await auth_service.get_user_sessions(db, current_user.id)
    if sessions:
        await auth_service.revoke_session(db, sessions[0].id, current_user.id)
        await db.commit()

    return {"message": "Logged out successfully"}


@router.post("/logout-all")
async def logout_all(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Logout from all devices.
    Revokes all active sessions for the current user.
    """
    count = await auth_service.revoke_all_sessions(db, current_user.id)
    await db.commit()

    return {
        "message": f"Logged out from {count} session(s)",
        "sessions_revoked": count,
    }
