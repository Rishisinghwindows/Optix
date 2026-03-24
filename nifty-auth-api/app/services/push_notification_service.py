"""
Push Notification Service — Sends push notifications via Firebase Cloud Messaging
"""

import logging
from datetime import datetime
from typing import Optional

from sqlalchemy import select, and_
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import AsyncSessionLocal
from app.models.device_token import DeviceToken

logger = logging.getLogger(__name__)

# Firebase Admin SDK (lazy init)
_firebase_initialized = False


def _init_firebase():
    """Initialize Firebase Admin SDK if not already done."""
    global _firebase_initialized
    if _firebase_initialized:
        return True

    try:
        import firebase_admin
        from firebase_admin import credentials

        # Resolve credentials path
        import os
        from app.config import settings
        cred_path = settings.firebase_credentials_path or os.getenv("FIREBASE_CREDENTIALS_PATH")
        if cred_path and not os.path.isabs(cred_path):
            project_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
            cred_path = os.path.join(project_root, cred_path)

        # Check if already initialized with valid credentials
        try:
            app = firebase_admin.get_app()
            # Verify the app has a project ID (properly configured)
            if app.project_id:
                _firebase_initialized = True
                return True
            # App exists but without project ID — delete and reinitialize
            firebase_admin.delete_app(app)
        except (ValueError, AttributeError):
            pass

        if cred_path and os.path.exists(cred_path):
            cred = credentials.Certificate(cred_path)
            firebase_admin.initialize_app(cred)
        else:
            try:
                firebase_admin.initialize_app()
            except Exception:
                logger.warning(
                    "Firebase not configured. Set FIREBASE_CREDENTIALS_PATH env var. "
                    "Push notifications will be logged only."
                )
                return False

        _firebase_initialized = True
        logger.info("Firebase Admin SDK initialized for push notifications")
        return True
    except ImportError:
        logger.warning("firebase-admin not installed. Push notifications disabled.")
        return False
    except Exception as e:
        logger.error(f"Firebase initialization failed: {e}")
        return False


class PushNotificationService:
    """Service for sending push notifications via FCM"""

    async def send_to_user(
        self,
        user_id: str,
        title: str,
        body: str,
        data: Optional[dict] = None,
    ) -> dict:
        """
        Send push notification to all active devices of a user.

        Returns dict with sent_count, failed_count, and details.
        """
        async with AsyncSessionLocal() as db:
            result = await db.execute(
                select(DeviceToken).where(
                    and_(
                        DeviceToken.user_id == user_id,
                        DeviceToken.is_active == True,
                    )
                )
            )
            tokens = result.scalars().all()

        if not tokens:
            logger.debug(f"No active devices for user {user_id}")
            return {"sent_count": 0, "failed_count": 0, "message": "No active devices"}

        sent = 0
        failed = 0
        invalid_tokens = []

        for device in tokens:
            success = await self._send_to_device(
                token=device.token,
                title=title,
                body=body,
                data=data,
                platform=device.platform,
            )
            if success:
                sent += 1
                # Update last_used_at
                async with AsyncSessionLocal() as db:
                    result = await db.execute(
                        select(DeviceToken).where(DeviceToken.id == device.id)
                    )
                    d = result.scalar_one_or_none()
                    if d:
                        d.last_used_at = datetime.utcnow()
                        await db.commit()
            else:
                failed += 1
                invalid_tokens.append(device.id)

        # Deactivate invalid tokens
        if invalid_tokens:
            await self._deactivate_tokens(invalid_tokens)

        logger.info(
            f"Push notification to user {user_id}: sent={sent}, failed={failed}, "
            f"title='{title}'"
        )
        return {"sent_count": sent, "failed_count": failed}

    async def _send_to_device(
        self,
        token: str,
        title: str,
        body: str,
        data: Optional[dict] = None,
        platform: str = "android",
    ) -> bool:
        """Send a single push notification to a device."""
        if not _init_firebase():
            logger.info(f"[DRY RUN] Push notification: title='{title}', body='{body}', token={token[:20]}...")
            return True  # Pretend success in dev mode

        try:
            from firebase_admin import messaging

            # Build notification
            notification = messaging.Notification(
                title=title,
                body=body,
            )

            # Platform-specific config
            android_config = messaging.AndroidConfig(
                priority="high",
                notification=messaging.AndroidNotification(
                    icon="ic_notification",
                    color="#448AFF",
                    sound="default",
                    channel_id="optix_alerts",
                ),
            )

            apns_config = messaging.APNSConfig(
                payload=messaging.APNSPayload(
                    aps=messaging.Aps(
                        alert=messaging.ApsAlert(title=title, body=body),
                        sound="default",
                        badge=1,
                    ),
                ),
            )

            web_config = messaging.WebpushConfig(
                notification=messaging.WebpushNotification(
                    title=title,
                    body=body,
                    icon="/icon-192.png",
                ),
            )

            message = messaging.Message(
                token=token,
                notification=notification,
                data=data or {},
                android=android_config if platform == "android" else None,
                apns=apns_config if platform == "ios" else None,
                webpush=web_config if platform == "web" else None,
            )

            response = messaging.send(message)
            logger.debug(f"FCM message sent: {response}")
            return True

        except Exception as e:
            error_str = str(e)
            # Token-related errors mean the token is invalid
            if any(err in error_str.lower() for err in [
                "not-registered", "invalid-registration", "invalid-argument"
            ]):
                logger.warning(f"Invalid FCM token {token[:20]}...: {e}")
                return False
            else:
                logger.error(f"FCM send error for token {token[:20]}...: {e}")
                return False

    async def send_to_all(
        self,
        title: str,
        body: str,
        data: Optional[dict] = None,
    ) -> dict:
        """
        Broadcast push notification to ALL active device tokens.
        Used for market-wide alerts (VIX spikes, OI changes, etc.)
        """
        async with AsyncSessionLocal() as db:
            result = await db.execute(
                select(DeviceToken).where(DeviceToken.is_active == True)
            )
            tokens = result.scalars().all()

        if not tokens:
            logger.debug("No active devices for broadcast")
            return {"sent_count": 0, "failed_count": 0}

        sent = 0
        failed = 0
        invalid_tokens = []

        for device in tokens:
            success = await self._send_to_device(
                token=device.token,
                title=title,
                body=body,
                data=data,
                platform=device.platform,
            )
            if success:
                sent += 1
            else:
                failed += 1
                invalid_tokens.append(device.id)

        if invalid_tokens:
            await self._deactivate_tokens(invalid_tokens)

        logger.info(
            f"Broadcast push notification: sent={sent}, failed={failed}, title='{title}'"
        )
        return {"sent_count": sent, "failed_count": failed}

    async def _deactivate_tokens(self, device_ids: list):
        """Deactivate invalid device tokens."""
        async with AsyncSessionLocal() as db:
            for device_id in device_ids:
                result = await db.execute(
                    select(DeviceToken).where(DeviceToken.id == device_id)
                )
                device = result.scalar_one_or_none()
                if device:
                    device.is_active = False
                    logger.info(f"Deactivated invalid FCM token: {device_id}")
            await db.commit()


# Singleton instance
push_service = PushNotificationService()
