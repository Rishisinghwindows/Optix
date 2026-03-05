"""
Alert Checker Service - Background service to check and trigger alerts

This service runs periodically to:
1. Fetch current market data (spot prices, option premiums, PCR)
2. Check all active alerts against current values
3. Trigger notifications when conditions are met
"""

import asyncio
import logging
from datetime import datetime
from decimal import Decimal
from typing import Dict, Any, Optional, List
from sqlalchemy import select, and_
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import AsyncSessionLocal
from app.models.alert import Alert, AlertNotification, AlertType, AlertCondition
from app.services.upstox_service import upstox_service
from app.services.cache_service import cache

logger = logging.getLogger(__name__)


class AlertCheckerService:
    """
    Background service that checks alerts against live market data
    """

    def __init__(self):
        self._running = False
        self._check_interval = 30  # Check every 30 seconds
        self._task: Optional[asyncio.Task] = None

        # Cache for market data to avoid repeated API calls
        self._market_cache: Dict[str, Any] = {}
        self._cache_ttl = 5  # Cache TTL in seconds

    async def start(self):
        """Start the alert checker background task"""
        if self._running:
            logger.warning("Alert checker already running")
            return

        self._running = True
        self._task = asyncio.create_task(self._run_checker())
        logger.info("Alert checker service started")
        print("[ALERTS] Alert checker service started")

    async def stop(self):
        """Stop the alert checker background task"""
        self._running = False
        if self._task:
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
        logger.info("Alert checker service stopped")
        print("[ALERTS] Alert checker service stopped")

    async def _run_checker(self):
        """Main loop that checks alerts periodically"""
        while self._running:
            try:
                await self._check_all_alerts()
            except Exception as e:
                logger.error(f"Error in alert checker: {e}")
                print(f"[ALERTS] Error: {e}")

            await asyncio.sleep(self._check_interval)

    async def _check_all_alerts(self):
        """Check all active alerts"""
        async with AsyncSessionLocal() as db:
            # Get all active, non-triggered alerts
            result = await db.execute(
                select(Alert).where(
                    and_(
                        Alert.is_active == True,
                        Alert.is_triggered == False
                    )
                )
            )
            alerts = result.scalars().all()

            if not alerts:
                return

            # Group alerts by type for efficient data fetching
            spot_alerts = [a for a in alerts if a.alert_type == AlertType.SPOT_PRICE.value]
            option_alerts = [a for a in alerts if a.alert_type == AlertType.OPTION_PREMIUM.value]
            pcr_alerts = [a for a in alerts if a.alert_type == AlertType.PCR.value]

            # Check each type
            await self._check_spot_alerts(db, spot_alerts)
            await self._check_option_alerts(db, option_alerts)
            await self._check_pcr_alerts(db, pcr_alerts)

            await db.commit()

    async def _get_spot_price(self, symbol: str) -> Optional[float]:
        """Get spot price with caching"""
        cache_key = f"alert_spot:{symbol}"
        cached = self._market_cache.get(cache_key)
        if cached and (datetime.utcnow().timestamp() - cached['time']) < self._cache_ttl:
            return cached['value']

        try:
            data = await upstox_service.get_spot_price(symbol)
            price = data.get('lastPrice', data.get('price', 0))
            self._market_cache[cache_key] = {'value': price, 'time': datetime.utcnow().timestamp()}
            return price
        except Exception as e:
            logger.error(f"Error fetching spot price for {symbol}: {e}")
            return None

    async def _get_option_price(self, symbol: str, strike: float, option_type: str, expiry: str) -> Optional[float]:
        """Get option premium with caching"""
        cache_key = f"alert_option:{symbol}:{strike}:{option_type}:{expiry}"
        cached = self._market_cache.get(cache_key)
        if cached and (datetime.utcnow().timestamp() - cached['time']) < self._cache_ttl:
            return cached['value']

        try:
            # Fetch option chain for the symbol and expiry
            chain_data = await upstox_service.get_option_chain(symbol, expiry, strikes_around_atm=25)

            # Find the specific strike
            for row in chain_data.get('data', []):
                if row.get('strikePrice') == strike:
                    option_data = row.get(option_type, {})
                    price = option_data.get('lastPrice', 0)
                    self._market_cache[cache_key] = {'value': price, 'time': datetime.utcnow().timestamp()}
                    return price

            return None
        except Exception as e:
            logger.error(f"Error fetching option price: {e}")
            return None

    async def _get_pcr(self, symbol: str) -> Optional[float]:
        """Get PCR (Put-Call Ratio) for a symbol"""
        cache_key = f"alert_pcr:{symbol}"
        cached = self._market_cache.get(cache_key)
        if cached and (datetime.utcnow().timestamp() - cached['time']) < self._cache_ttl:
            return cached['value']

        try:
            # Get option chain and calculate PCR
            chain_data = await upstox_service.get_option_chain(symbol, strikes_around_atm=25, expiry=None)

            total_put_oi = 0
            total_call_oi = 0

            for row in chain_data.get('data', []):
                if row.get('CE'):
                    total_call_oi += row['CE'].get('openInterest', 0)
                if row.get('PE'):
                    total_put_oi += row['PE'].get('openInterest', 0)

            if total_call_oi > 0:
                pcr = total_put_oi / total_call_oi
                self._market_cache[cache_key] = {'value': pcr, 'time': datetime.utcnow().timestamp()}
                return round(pcr, 2)

            return None
        except Exception as e:
            logger.error(f"Error calculating PCR for {symbol}: {e}")
            return None

    async def _check_spot_alerts(self, db: AsyncSession, alerts: List[Alert]):
        """Check spot price alerts"""
        # Group by symbol to avoid redundant API calls
        symbols = set(a.symbol for a in alerts)

        for symbol in symbols:
            current_price = await self._get_spot_price(symbol)
            if current_price is None:
                continue

            symbol_alerts = [a for a in alerts if a.symbol == symbol]
            for alert in symbol_alerts:
                await self._check_and_trigger(db, alert, current_price)

    async def _check_option_alerts(self, db: AsyncSession, alerts: List[Alert]):
        """Check option premium alerts"""
        for alert in alerts:
            current_price = await self._get_option_price(
                alert.symbol,
                float(alert.strike_price),
                alert.option_type,
                alert.expiry
            )
            if current_price is None:
                continue

            await self._check_and_trigger(db, alert, current_price)

    async def _check_pcr_alerts(self, db: AsyncSession, alerts: List[Alert]):
        """Check PCR alerts"""
        symbols = set(a.symbol for a in alerts)

        for symbol in symbols:
            current_pcr = await self._get_pcr(symbol)
            if current_pcr is None:
                continue

            symbol_alerts = [a for a in alerts if a.symbol == symbol]
            for alert in symbol_alerts:
                await self._check_and_trigger(db, alert, current_pcr)

    async def _check_and_trigger(self, db: AsyncSession, alert: Alert, current_value: float):
        """Check if alert should be triggered and create notification"""
        target = float(alert.target_value)
        last_value = float(alert.last_checked_value) if alert.last_checked_value else None
        condition = alert.condition

        should_trigger = False

        if condition == AlertCondition.ABOVE.value:
            # Trigger when value goes above target
            should_trigger = current_value >= target
        elif condition == AlertCondition.BELOW.value:
            # Trigger when value goes below target
            should_trigger = current_value <= target
        elif condition == AlertCondition.CROSSES.value:
            # Trigger when value crosses target (either direction)
            if last_value is not None:
                crossed_up = last_value < target <= current_value
                crossed_down = last_value > target >= current_value
                should_trigger = crossed_up or crossed_down

        # Update last checked value
        alert.last_checked_value = Decimal(str(current_value))
        alert.last_checked_at = datetime.utcnow()

        if should_trigger:
            await self._trigger_alert(db, alert, current_value)

    async def _trigger_alert(self, db: AsyncSession, alert: Alert, triggered_value: float):
        """Trigger an alert and create notification"""
        now = datetime.utcnow()

        # Mark alert as triggered
        alert.is_triggered = True
        alert.triggered_at = now
        alert.triggered_value = Decimal(str(triggered_value))

        # Create notification
        title = self._generate_notification_title(alert, triggered_value)
        message = self._generate_notification_message(alert, triggered_value)

        notification = AlertNotification(
            alert_id=alert.id,
            user_id=alert.user_id,
            title=title,
            message=message,
            triggered_value=Decimal(str(triggered_value)),
        )
        db.add(notification)

        logger.info(f"Alert triggered: {alert.id} - {title}")
        print(f"[ALERTS] Triggered: {title}")

        # Store in Redis for real-time notification
        await self._push_realtime_notification(alert.user_id, {
            "type": "alert_triggered",
            "alert_id": alert.id,
            "title": title,
            "message": message,
            "triggered_value": triggered_value,
            "symbol": alert.symbol,
            "alert_type": alert.alert_type,
            "timestamp": now.isoformat(),
        })

        # Send push notification via FCM
        try:
            from app.services.push_notification_service import push_service
            await push_service.send_to_user(
                user_id=alert.user_id,
                title=title,
                body=message,
                data={
                    "type": "alert_triggered",
                    "alert_id": alert.id,
                    "symbol": alert.symbol,
                    "alert_type": alert.alert_type,
                },
            )
        except Exception as e:
            logger.error(f"Push notification failed for alert {alert.id}: {e}")

    def _generate_notification_title(self, alert: Alert, value: float) -> str:
        """Generate notification title"""
        if alert.alert_type == AlertType.SPOT_PRICE.value:
            return f"{alert.symbol} hit {value:.2f}"
        elif alert.alert_type == AlertType.OPTION_PREMIUM.value:
            return f"{alert.symbol} {int(alert.strike_price)} {alert.option_type} at ₹{value:.2f}"
        elif alert.alert_type == AlertType.PCR.value:
            return f"{alert.symbol} PCR at {value:.2f}"
        else:
            return f"Alert triggered for {alert.symbol}"

    def _generate_notification_message(self, alert: Alert, value: float) -> str:
        """Generate notification message"""
        condition_text = {
            AlertCondition.ABOVE.value: "crossed above",
            AlertCondition.BELOW.value: "dropped below",
            AlertCondition.CROSSES.value: "crossed",
        }.get(alert.condition, "reached")

        target = float(alert.target_value)

        if alert.alert_type == AlertType.SPOT_PRICE.value:
            return f"{alert.symbol} {condition_text} your target of {target:.2f}. Current value: {value:.2f}"
        elif alert.alert_type == AlertType.OPTION_PREMIUM.value:
            return f"{alert.symbol} {int(alert.strike_price)} {alert.option_type} {condition_text} ₹{target:.2f}. Current premium: ₹{value:.2f}"
        elif alert.alert_type == AlertType.PCR.value:
            return f"{alert.symbol} PCR {condition_text} {target:.2f}. Current PCR: {value:.2f}"
        else:
            return f"Your alert for {alert.symbol} was triggered. Value: {value}"

    async def _push_realtime_notification(self, user_id: str, notification: dict):
        """Push notification to Redis for real-time delivery"""
        try:
            await cache.set(
                f"notification:{user_id}:{notification['alert_id']}",
                notification,
                ttl=3600  # 1 hour
            )
            # Also push to a notification queue for the user
            await cache.set(
                f"notification_queue:{user_id}",
                {"has_new": True, "updated_at": datetime.utcnow().isoformat()},
                ttl=3600
            )
        except Exception as e:
            logger.error(f"Error pushing realtime notification: {e}")

    async def check_single_alert(self, alert_id: str) -> Dict[str, Any]:
        """Manually check a single alert (for testing/debugging)"""
        async with AsyncSessionLocal() as db:
            result = await db.execute(
                select(Alert).where(Alert.id == alert_id)
            )
            alert = result.scalar_one_or_none()

            if not alert:
                return {"error": "Alert not found"}

            # Get current value based on alert type
            current_value = None
            if alert.alert_type == AlertType.SPOT_PRICE.value:
                current_value = await self._get_spot_price(alert.symbol)
            elif alert.alert_type == AlertType.OPTION_PREMIUM.value:
                current_value = await self._get_option_price(
                    alert.symbol,
                    float(alert.strike_price),
                    alert.option_type,
                    alert.expiry
                )
            elif alert.alert_type == AlertType.PCR.value:
                current_value = await self._get_pcr(alert.symbol)

            if current_value is None:
                return {"error": "Could not fetch current value"}

            target = float(alert.target_value)
            gap = target - current_value

            return {
                "alert_id": alert.id,
                "alert_type": alert.alert_type,
                "symbol": alert.symbol,
                "condition": alert.condition,
                "target_value": target,
                "current_value": current_value,
                "gap": gap,
                "gap_percentage": (gap / current_value * 100) if current_value else 0,
                "is_triggered": alert.is_triggered,
                "would_trigger": self._would_trigger(alert.condition, current_value, target, alert.last_checked_value),
            }

    def _would_trigger(self, condition: str, current: float, target: float, last: Optional[Decimal]) -> bool:
        """Check if alert would trigger with current value"""
        if condition == AlertCondition.ABOVE.value:
            return current >= target
        elif condition == AlertCondition.BELOW.value:
            return current <= target
        elif condition == AlertCondition.CROSSES.value and last:
            last_val = float(last)
            return (last_val < target <= current) or (last_val > target >= current)
        return False


# Singleton instance
alert_checker_service = AlertCheckerService()
