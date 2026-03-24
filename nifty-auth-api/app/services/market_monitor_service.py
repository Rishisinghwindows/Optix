"""
Market Monitor Service — Detects significant market changes and broadcasts push notifications.

Monitors: PCR shifts, VIX spikes, OI buildup/unwinding, long/short patterns, max pain shifts.
Runs every 60 seconds during market hours.
"""

import asyncio
import logging
from datetime import datetime
from typing import Optional, Dict, Any, List

import pytz

from app.services.upstox_service import upstox_service
from app.services.cache_service import cache

logger = logging.getLogger(__name__)

IST = pytz.timezone("Asia/Kolkata")

# Indices to monitor (must match upstox_service.INDEX_KEYS)
MONITORED_INDICES = ["NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY", "SENSEX"]

# Thresholds for significant changes
THRESHOLDS = {
    "pcr_shift": 0.12,          # PCR change > 0.12
    "vix_change_pct": 3.0,      # VIX change > 3%
    "oi_surge_pct": 15.0,       # Total OI change > 15%
    "price_move_pct": 0.3,      # Price move for buildup/unwinding detection
    "oi_change_pct": 5.0,       # OI change for buildup/unwinding detection
    "max_pain_shift_pct": 1.0,  # Max pain shift > 1% from previous
}

# Cooldown per signal type per index (seconds)
SIGNAL_COOLDOWN = 900  # 15 minutes


class MarketMonitorService:
    """Background service that monitors market data for significant changes."""

    def __init__(self):
        self._running = False
        self._check_interval = 60  # seconds
        self._task: Optional[asyncio.Task] = None

        # Copy module-level defaults into instance attributes
        self._thresholds: Dict[str, float] = dict(THRESHOLDS)
        self._signal_cooldown: int = SIGNAL_COOLDOWN
        self._monitored_indices: List[str] = list(MONITORED_INDICES)

        # Previous snapshot per index: {index: {pcr, vix, total_oi, spot, max_pain, call_oi, put_oi}}
        self._prev_snapshots: Dict[str, Dict[str, float]] = {}

        # Cooldown tracker: {signal_key: last_sent_timestamp}
        self._cooldowns: Dict[str, float] = {}

    def get_thresholds(self) -> Dict[str, Any]:
        """Return current thresholds, cooldown, and monitored indices."""
        return {
            **self._thresholds,
            "cooldown_seconds": self._signal_cooldown,
        }

    def update_thresholds(self, new_thresholds: Dict[str, Any]) -> None:
        """Update specific threshold keys (ignores unknown keys)."""
        valid_keys = set(THRESHOLDS.keys())
        for key, value in new_thresholds.items():
            if key in valid_keys:
                self._thresholds[key] = float(value)

    def get_monitored_indices(self) -> List[str]:
        """Return the current list of monitored indices."""
        return list(self._monitored_indices)

    def update_monitored_indices(self, indices: List[str]) -> None:
        """Replace the monitored indices list."""
        self._monitored_indices = list(indices)

    async def start(self):
        if self._running:
            logger.warning("Market monitor already running")
            return

        # Restore persisted config from cache
        try:
            saved = await cache.get("market_monitor:config")
            if saved:
                if "thresholds" in saved:
                    self.update_thresholds(saved["thresholds"])
                    if "cooldown_seconds" in saved["thresholds"]:
                        self._signal_cooldown = int(saved["thresholds"]["cooldown_seconds"])
                if "monitored_indices" in saved:
                    self._monitored_indices = list(saved["monitored_indices"])
                logger.info(f"Restored market monitor config from cache: indices={self._monitored_indices}")
        except Exception as e:
            logger.warning(f"Could not restore market monitor config: {e}")

        self._running = True
        self._task = asyncio.create_task(self._run_loop())
        logger.info("Market monitor service started (60s interval)")

    async def stop(self):
        self._running = False
        if self._task:
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
        logger.info("Market monitor service stopped")

    def _is_market_hours(self) -> bool:
        now = datetime.now(IST)
        # Market hours: 9:15 to 15:30 IST, weekdays only
        if now.weekday() >= 5:  # Saturday=5, Sunday=6
            return False
        market_open = now.replace(hour=9, minute=15, second=0, microsecond=0)
        market_close = now.replace(hour=15, minute=30, second=0, microsecond=0)
        return market_open <= now <= market_close

    def _can_send(self, signal_key: str) -> bool:
        last_sent = self._cooldowns.get(signal_key, 0)
        return (datetime.utcnow().timestamp() - last_sent) >= self._signal_cooldown

    def _mark_sent(self, signal_key: str):
        self._cooldowns[signal_key] = datetime.utcnow().timestamp()

    async def _run_loop(self):
        # Wait a bit on startup before first check
        await asyncio.sleep(10)

        while self._running:
            try:
                if self._is_market_hours():
                    await self._check_all_indices()
                else:
                    logger.debug("Market monitor: outside market hours, skipping")
            except Exception as e:
                logger.error(f"Market monitor error: {e}")

            await asyncio.sleep(self._check_interval)

    async def _check_all_indices(self):
        # Check VIX first (applies globally)
        await self._check_vix()

        for index_name in self._monitored_indices:
            try:
                await self._check_index(index_name)
            except Exception as e:
                logger.error(f"Market monitor error for {index_name}: {e}")

    async def _check_vix(self):
        """Check India VIX for significant changes."""
        try:
            vix_data = await upstox_service.get_spot_price("INDIAVIX")
            current_vix = vix_data.get("lastPrice", vix_data.get("price", 0))
            if not current_vix or current_vix <= 0:
                return

            prev_vix = self._prev_snapshots.get("_vix", {}).get("value", 0)

            if prev_vix > 0:
                vix_change_pct = ((current_vix - prev_vix) / prev_vix) * 100

                if abs(vix_change_pct) >= self._thresholds["vix_change_pct"]:
                    signal_key = "vix_spike"
                    if self._can_send(signal_key):
                        direction = "surged" if vix_change_pct > 0 else "dropped"
                        await self._broadcast_signal(
                            signal_type="vix_change",
                            title=f"VIX {direction} {abs(vix_change_pct):.1f}%",
                            body=f"India VIX {direction} from {prev_vix:.2f} to {current_vix:.2f}. "
                                 f"{'Higher volatility expected — options premiums rising.' if vix_change_pct > 0 else 'Volatility cooling — premiums may decline.'}",
                            data={
                                "signal": "vix_change",
                                "vix": str(round(current_vix, 2)),
                                "change_pct": str(round(vix_change_pct, 1)),
                            },
                        )
                        self._mark_sent(signal_key)

            self._prev_snapshots["_vix"] = {"value": current_vix}

        except Exception as e:
            logger.debug(f"VIX check failed: {e}")

    async def _check_index(self, index_name: str):
        """Check a single index for significant changes."""
        # Fetch spot price
        spot_data = await upstox_service.get_spot_price(index_name)
        current_spot = spot_data.get("lastPrice", spot_data.get("price", 0))
        if not current_spot or current_spot <= 0:
            return

        # Get nearest expiry
        expiries = await upstox_service.get_expiry_dates(index_name)
        if not expiries:
            logger.debug(f"No expiries available for {index_name}")
            return
        nearest_expiry = expiries[0]

        # Fetch option chain for PCR, OI, max pain
        chain_data = await upstox_service.get_option_chain(index_name, expiry=nearest_expiry, strikes_around_atm=15)
        if not chain_data or not chain_data.get("data"):
            return

        rows = chain_data["data"]
        total_call_oi = 0
        total_put_oi = 0
        max_pain_strike = 0
        max_pain_pain = float("inf")

        # Gather all strikes for max pain calculation
        strikes_data = []

        for row in rows:
            ce = row.get("CE", {})
            pe = row.get("PE", {})
            strike = row.get("strikePrice", 0)
            c_oi = ce.get("openInterest", 0) or 0
            p_oi = pe.get("openInterest", 0) or 0
            total_call_oi += c_oi
            total_put_oi += p_oi
            strikes_data.append({"strike": strike, "call_oi": c_oi, "put_oi": p_oi})

        # Calculate PCR
        current_pcr = (total_put_oi / total_call_oi) if total_call_oi > 0 else 0
        total_oi = total_call_oi + total_put_oi

        # Calculate max pain
        for target in strikes_data:
            pain = 0
            for s in strikes_data:
                if s["strike"] < target["strike"]:
                    pain += s["call_oi"] * (target["strike"] - s["strike"])
                elif s["strike"] > target["strike"]:
                    pain += s["put_oi"] * (s["strike"] - target["strike"])
            if pain < max_pain_pain:
                max_pain_pain = pain
                max_pain_strike = target["strike"]

        # Build current snapshot
        current = {
            "spot": current_spot,
            "pcr": current_pcr,
            "total_oi": total_oi,
            "call_oi": total_call_oi,
            "put_oi": total_put_oi,
            "max_pain": max_pain_strike,
        }

        prev = self._prev_snapshots.get(index_name)
        short_name = self._short_name(index_name)

        if prev:
            signals = self._detect_signals(index_name, short_name, prev, current)
            for signal in signals:
                signal_key = f"{index_name}:{signal['signal_type']}"
                if self._can_send(signal_key):
                    await self._broadcast_signal(**signal)
                    self._mark_sent(signal_key)

        self._prev_snapshots[index_name] = current

    def _detect_signals(
        self, index_name: str, short_name: str, prev: Dict, current: Dict
    ) -> List[Dict[str, Any]]:
        """Compare prev vs current snapshot and return list of signals to broadcast."""
        signals = []

        spot = current["spot"]
        prev_spot = prev["spot"]
        pcr = current["pcr"]
        prev_pcr = prev["pcr"]
        total_oi = current["total_oi"]
        prev_oi = prev["total_oi"]
        max_pain = current["max_pain"]
        prev_max_pain = prev["max_pain"]

        # Price change %
        price_change_pct = ((spot - prev_spot) / prev_spot * 100) if prev_spot > 0 else 0
        # OI change %
        oi_change_pct = ((total_oi - prev_oi) / prev_oi * 100) if prev_oi > 0 else 0

        # 1. PCR Shift
        pcr_change = pcr - prev_pcr
        if abs(pcr_change) >= self._thresholds["pcr_shift"] and prev_pcr > 0:
            if pcr_change > 0:
                sentiment = "Bearish shift — put writers increasing"
                direction = "rose"
            else:
                sentiment = "Bullish shift — call writers increasing"
                direction = "fell"
            signals.append({
                "signal_type": "pcr_shift",
                "title": f"{short_name} PCR {direction} to {pcr:.2f}",
                "body": f"PCR moved from {prev_pcr:.2f} to {pcr:.2f} ({pcr_change:+.2f}). {sentiment}.",
                "data": {
                    "signal": "pcr_shift",
                    "index": index_name,
                    "pcr": str(round(pcr, 2)),
                    "change": str(round(pcr_change, 2)),
                },
            })

        # 2. Long Buildup (price up + OI up)
        if (price_change_pct >= self._thresholds["price_move_pct"]
                and oi_change_pct >= self._thresholds["oi_change_pct"]):
            signals.append({
                "signal_type": "long_buildup",
                "title": f"{short_name} Long Buildup Detected",
                "body": (
                    f"Spot up {price_change_pct:+.2f}% ({spot:.0f}) with OI up {oi_change_pct:+.1f}%. "
                    f"Fresh longs being added — bullish momentum."
                ),
                "data": {
                    "signal": "long_buildup",
                    "index": index_name,
                    "spot": str(round(spot)),
                    "price_change": str(round(price_change_pct, 2)),
                    "oi_change": str(round(oi_change_pct, 1)),
                },
            })

        # 3. Long Unwinding (price down + OI down)
        if (price_change_pct <= -self._thresholds["price_move_pct"]
                and oi_change_pct <= -self._thresholds["oi_change_pct"]):
            signals.append({
                "signal_type": "long_unwinding",
                "title": f"{short_name} Long Unwinding",
                "body": (
                    f"Spot down {price_change_pct:+.2f}% ({spot:.0f}) with OI down {oi_change_pct:+.1f}%. "
                    f"Longs exiting positions — weakening support."
                ),
                "data": {
                    "signal": "long_unwinding",
                    "index": index_name,
                    "spot": str(round(spot)),
                    "price_change": str(round(price_change_pct, 2)),
                    "oi_change": str(round(oi_change_pct, 1)),
                },
            })

        # 4. Short Buildup (price down + OI up)
        if (price_change_pct <= -self._thresholds["price_move_pct"]
                and oi_change_pct >= self._thresholds["oi_change_pct"]):
            signals.append({
                "signal_type": "short_buildup",
                "title": f"{short_name} Short Buildup Detected",
                "body": (
                    f"Spot down {price_change_pct:+.2f}% ({spot:.0f}) with OI up {oi_change_pct:+.1f}%. "
                    f"Fresh shorts being added — bearish pressure."
                ),
                "data": {
                    "signal": "short_buildup",
                    "index": index_name,
                    "spot": str(round(spot)),
                    "price_change": str(round(price_change_pct, 2)),
                    "oi_change": str(round(oi_change_pct, 1)),
                },
            })

        # 5. Short Covering (price up + OI down)
        if (price_change_pct >= self._thresholds["price_move_pct"]
                and oi_change_pct <= -self._thresholds["oi_change_pct"]):
            signals.append({
                "signal_type": "short_covering",
                "title": f"{short_name} Short Covering Rally",
                "body": (
                    f"Spot up {price_change_pct:+.2f}% ({spot:.0f}) with OI down {oi_change_pct:+.1f}%. "
                    f"Shorts covering positions — potential squeeze."
                ),
                "data": {
                    "signal": "short_covering",
                    "index": index_name,
                    "spot": str(round(spot)),
                    "price_change": str(round(price_change_pct, 2)),
                    "oi_change": str(round(oi_change_pct, 1)),
                },
            })

        # 6. OI Surge (massive OI change without clear direction)
        if (abs(oi_change_pct) >= self._thresholds["oi_surge_pct"]
                and abs(price_change_pct) < self._thresholds["price_move_pct"]):
            direction = "surged" if oi_change_pct > 0 else "dropped"
            signals.append({
                "signal_type": "oi_surge",
                "title": f"{short_name} OI {direction} {abs(oi_change_pct):.0f}%",
                "body": (
                    f"Total OI {direction} by {abs(oi_change_pct):.1f}% while spot "
                    f"stayed flat ({price_change_pct:+.2f}%). "
                    f"Major position buildup — expect a big move."
                ),
                "data": {
                    "signal": "oi_surge",
                    "index": index_name,
                    "oi_change": str(round(oi_change_pct, 1)),
                },
            })

        # 7. Max Pain Shift
        if prev_max_pain > 0 and max_pain > 0:
            mp_change_pct = ((max_pain - prev_max_pain) / prev_max_pain) * 100
            if abs(mp_change_pct) >= self._thresholds["max_pain_shift_pct"]:
                direction = "up" if mp_change_pct > 0 else "down"
                signals.append({
                    "signal_type": "max_pain_shift",
                    "title": f"{short_name} Max Pain Shifted {direction}",
                    "body": (
                        f"Max pain moved from {prev_max_pain:.0f} to {max_pain:.0f} "
                        f"({mp_change_pct:+.1f}%). Institutional positioning may be shifting."
                    ),
                    "data": {
                        "signal": "max_pain_shift",
                        "index": index_name,
                        "max_pain": str(round(max_pain)),
                        "prev_max_pain": str(round(prev_max_pain)),
                    },
                })

        return signals

    def _short_name(self, index_name: str) -> str:
        return {
            "NIFTY": "Nifty",
            "BANKNIFTY": "BankNifty",
            "FINNIFTY": "FinNifty",
            "MIDCPNIFTY": "MidcpNifty",
            "SENSEX": "Sensex",
        }.get(index_name, index_name)

    async def _broadcast_signal(
        self, signal_type: str, title: str, body: str, data: Dict[str, str]
    ):
        """Send push notification to all registered devices."""
        logger.info(f"[MARKET MONITOR] {title}: {body}")

        # Add common data fields
        data["type"] = "market_monitor"

        try:
            from app.services.push_notification_service import push_service
            await push_service.send_to_all(
                title=title,
                body=body,
                data=data,
            )
        except Exception as e:
            logger.error(f"Failed to broadcast market signal: {e}")

        # Also store in cache for API access
        try:
            signal_record = {
                "signal_type": signal_type,
                "title": title,
                "body": body,
                "data": data,
                "timestamp": datetime.utcnow().isoformat(),
            }
            # Store latest signals (list)
            cache_key = f"market_signals:{data.get('index', 'global')}"
            existing = await cache.get(cache_key) or []
            existing.insert(0, signal_record)
            existing = existing[:20]  # Keep last 20
            await cache.set(cache_key, existing, ttl=3600)
        except Exception as e:
            logger.debug(f"Cache storage for signal failed: {e}")


# Singleton
market_monitor_service = MarketMonitorService()
