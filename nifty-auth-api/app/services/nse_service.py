"""
NSE Data Service - Fetches live option chain data from NSE India
Uses multiple strategies to handle NSE's bot detection
"""

import asyncio
import time
import logging
import random
from typing import Optional, Dict, Any, List
from datetime import datetime, timedelta
import httpx

logger = logging.getLogger(__name__)


class NSEService:
    """Service to fetch option chain and market data from NSE India"""

    BASE_URL = "https://www.nseindia.com"

    # Fallback API (nseoptionchain.com)
    FALLBACK_URL = "https://www.nseoptionchain.com/api"

    # Supported indices
    INDICES = {
        "NIFTY": {"name": "NIFTY 50", "lot_size": 25},
        "BANKNIFTY": {"name": "BANK NIFTY", "lot_size": 15},
        "FINNIFTY": {"name": "FIN NIFTY", "lot_size": 25},
        "MIDCPNIFTY": {"name": "MIDCAP NIFTY", "lot_size": 50},
        "SENSEX": {"name": "SENSEX", "lot_size": 10},
    }

    # Rotate User-Agents to avoid detection
    USER_AGENTS = [
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
    ]

    def __init__(self):
        self._session: Optional[httpx.AsyncClient] = None
        self._cookies: Dict[str, str] = {}
        self._cookie_expiry: float = 0
        self._cache: Dict[str, Dict[str, Any]] = {}
        self._cache_ttl: int = 30  # Cache TTL in seconds
        self._rate_limit_delay: float = 1.0  # Delay between requests
        self._last_request_time: float = 0
        self._retry_count: int = 3

    def _get_headers(self) -> Dict[str, str]:
        """Get headers with random User-Agent"""
        return {
            "User-Agent": random.choice(self.USER_AGENTS),
            "Accept": "application/json, text/javascript, */*; q=0.01",
            "Accept-Language": "en-US,en;q=0.9,hi;q=0.8",
            "Accept-Encoding": "gzip, deflate, br",
            "Connection": "keep-alive",
            "Referer": "https://www.nseindia.com/option-chain",
            "X-Requested-With": "XMLHttpRequest",
            "Sec-Fetch-Dest": "empty",
            "Sec-Fetch-Mode": "cors",
            "Sec-Fetch-Site": "same-origin",
        }

    async def _get_session(self) -> httpx.AsyncClient:
        """Get or create HTTP session"""
        if self._session is None or self._session.is_closed:
            self._session = httpx.AsyncClient(
                timeout=30.0,
                follow_redirects=True,
                http2=True,
            )
        return self._session

    async def _refresh_cookies(self) -> bool:
        """Fetch fresh cookies by visiting NSE website"""
        try:
            client = await self._get_session()

            # First visit the main page
            headers = self._get_headers()
            headers["Accept"] = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"

            response = await client.get(
                f"{self.BASE_URL}/option-chain",
                headers=headers
            )

            if response.status_code == 200:
                self._cookies = dict(response.cookies)
                self._cookie_expiry = time.time() + 300  # Valid for 5 min
                logger.info("NSE cookies refreshed successfully")
                return True

            logger.warning(f"Failed to get cookies: {response.status_code}")
            return False

        except Exception as e:
            logger.error(f"Error refreshing cookies: {e}")
            return False

    async def _ensure_cookies(self) -> bool:
        """Ensure we have valid cookies"""
        if time.time() > self._cookie_expiry or not self._cookies:
            return await self._refresh_cookies()
        return True

    async def _rate_limit(self) -> None:
        """Implement rate limiting"""
        elapsed = time.time() - self._last_request_time
        if elapsed < self._rate_limit_delay:
            await asyncio.sleep(self._rate_limit_delay - elapsed + random.uniform(0.1, 0.5))
        self._last_request_time = time.time()

    def _get_from_cache(self, key: str) -> Optional[Dict[str, Any]]:
        """Get data from cache if valid"""
        if key in self._cache:
            cached = self._cache[key]
            if time.time() - cached["timestamp"] < self._cache_ttl:
                return cached["data"]
        return None

    def _set_cache(self, key: str, data: Dict[str, Any]) -> None:
        """Store data in cache"""
        self._cache[key] = {"data": data, "timestamp": time.time()}

    async def _fetch_from_nse(self, symbol: str) -> Optional[Dict[str, Any]]:
        """Fetch option chain directly from NSE"""
        await self._rate_limit()

        for attempt in range(self._retry_count):
            try:
                await self._ensure_cookies()
                client = await self._get_session()

                url = f"{self.BASE_URL}/api/option-chain-indices?symbol={symbol}"
                response = await client.get(
                    url,
                    headers=self._get_headers(),
                    cookies=self._cookies
                )

                if response.status_code == 200:
                    data = response.json()
                    # Check if we got actual data
                    if data.get("records", {}).get("data"):
                        return data

                # If empty, refresh cookies and retry
                logger.warning(f"Empty response from NSE (attempt {attempt + 1})")
                self._cookies = {}
                self._cookie_expiry = 0
                await asyncio.sleep(2 ** attempt)  # Exponential backoff

            except Exception as e:
                logger.error(f"NSE fetch error (attempt {attempt + 1}): {e}")
                await asyncio.sleep(2 ** attempt)

        return None

    async def _fetch_from_fallback(self, symbol: str) -> Optional[Dict[str, Any]]:
        """Fetch from fallback API"""
        try:
            client = await self._get_session()
            url = f"{self.FALLBACK_URL}/optionchain?symbol={symbol}"

            response = await client.get(url, timeout=15.0)
            if response.status_code == 200:
                return response.json()
        except Exception as e:
            logger.error(f"Fallback API error: {e}")
        return None

    def _generate_demo_data(self, symbol: str) -> Dict[str, Any]:
        """Generate demo data when live data unavailable"""
        # Base prices for different indices (updated Jan 2026)
        base_prices = {
            "NIFTY": 25300,
            "BANKNIFTY": 53500,
            "FINNIFTY": 25500,
            "MIDCPNIFTY": 13200,
            "SENSEX": 83000,
        }

        spot = base_prices.get(symbol, 23500)
        # Add some randomness
        spot += random.randint(-100, 100)

        # Generate expiry dates
        today = datetime.now()
        expiry_dates = []
        for i in range(4):
            # Next Thursday
            days_until_thursday = (3 - today.weekday()) % 7
            if days_until_thursday == 0 and i > 0:
                days_until_thursday = 7
            next_expiry = today + timedelta(days=days_until_thursday + (7 * i))
            expiry_dates.append(next_expiry.strftime("%d-%b-%Y"))

        # Generate strike prices around spot
        strike_step = 50 if symbol in ["NIFTY", "FINNIFTY"] else 100
        atm_strike = round(spot / strike_step) * strike_step
        strikes = [atm_strike + (i * strike_step) for i in range(-10, 11)]

        # Generate option chain data
        data = []
        for strike in strikes:
            for expiry in expiry_dates[:1]:  # Just first expiry for demo
                # Calculate rough option prices
                diff = spot - strike
                ce_intrinsic = max(0, diff)
                pe_intrinsic = max(0, -diff)

                # Add time value
                time_value = random.uniform(20, 80)

                ce_price = ce_intrinsic + time_value + random.uniform(-10, 10)
                pe_price = pe_intrinsic + time_value + random.uniform(-10, 10)

                # Generate IVs
                base_iv = 15 + abs(diff) / spot * 100

                data.append({
                    "strikePrice": strike,
                    "expiryDate": expiry,
                    "CE": {
                        "openInterest": random.randint(10000, 500000),
                        "changeinOpenInterest": random.randint(-50000, 50000),
                        "totalTradedVolume": random.randint(1000, 100000),
                        "impliedVolatility": round(base_iv + random.uniform(-2, 2), 2),
                        "lastPrice": round(max(0.05, ce_price), 2),
                        "change": round(random.uniform(-20, 20), 2),
                        "pChange": round(random.uniform(-10, 10), 2),
                        "bidQty": random.randint(100, 5000),
                        "bidprice": round(max(0.05, ce_price - 0.5), 2),
                        "askQty": random.randint(100, 5000),
                        "askPrice": round(ce_price + 0.5, 2),
                    },
                    "PE": {
                        "openInterest": random.randint(10000, 500000),
                        "changeinOpenInterest": random.randint(-50000, 50000),
                        "totalTradedVolume": random.randint(1000, 100000),
                        "impliedVolatility": round(base_iv + random.uniform(-2, 2), 2),
                        "lastPrice": round(max(0.05, pe_price), 2),
                        "change": round(random.uniform(-20, 20), 2),
                        "pChange": round(random.uniform(-10, 10), 2),
                        "bidQty": random.randint(100, 5000),
                        "bidprice": round(max(0.05, pe_price - 0.5), 2),
                        "askQty": random.randint(100, 5000),
                        "askPrice": round(pe_price + 0.5, 2),
                    }
                })

        return {
            "records": {
                "underlyingValue": spot,
                "expiryDates": expiry_dates,
                "strikePrices": strikes,
                "timestamp": datetime.now().strftime("%d-%b-%Y %H:%M:%S"),
                "data": data,
            },
            "filtered": {
                "CE": {"totOI": sum(d["CE"]["openInterest"] for d in data), "totVol": sum(d["CE"]["totalTradedVolume"] for d in data)},
                "PE": {"totOI": sum(d["PE"]["openInterest"] for d in data), "totVol": sum(d["PE"]["totalTradedVolume"] for d in data)},
            },
            "isDemo": True
        }

    async def get_option_chain(self, symbol: str, use_demo: bool = False) -> Dict[str, Any]:
        """
        Fetch option chain data for an index

        Args:
            symbol: Index symbol (NIFTY, BANKNIFTY, etc.)
            use_demo: Force use of demo data

        Returns:
            Processed option chain data
        """
        symbol = symbol.upper()

        if symbol not in self.INDICES:
            raise ValueError(f"Unsupported symbol: {symbol}")

        # Check cache
        cache_key = f"option_chain_{symbol}"
        if not use_demo:
            cached = self._get_from_cache(cache_key)
            if cached:
                return cached

        raw_data = None

        if not use_demo:
            # Try NSE first
            raw_data = await self._fetch_from_nse(symbol)

            # If NSE fails, try fallback
            if not raw_data:
                raw_data = await self._fetch_from_fallback(symbol)

        # If all fails or demo requested, use demo data
        if not raw_data:
            logger.warning(f"Using demo data for {symbol}")
            raw_data = self._generate_demo_data(symbol)

        # Process the data
        processed = self._process_option_chain(symbol, raw_data)

        # Only cache if we got real data (not demo)
        if not raw_data.get("isDemo"):
            self._set_cache(cache_key, processed)

        return processed

    def _process_option_chain(self, symbol: str, raw_data: Dict[str, Any]) -> Dict[str, Any]:
        """Process raw data into clean format"""
        records = raw_data.get("records", {})
        filtered = raw_data.get("filtered", {})

        underlying_value = records.get("underlyingValue", 0)
        expiry_dates = records.get("expiryDates", [])
        strike_prices = records.get("strikePrices", [])

        # Process chain data
        chain_data = []
        for item in records.get("data", []):
            ce = item.get("CE", {})
            pe = item.get("PE", {})

            chain_data.append({
                "strikePrice": item.get("strikePrice"),
                "expiryDate": item.get("expiryDate"),
                "CE": {
                    "openInterest": ce.get("openInterest", 0),
                    "changeinOpenInterest": ce.get("changeinOpenInterest", 0),
                    "totalTradedVolume": ce.get("totalTradedVolume", 0),
                    "impliedVolatility": ce.get("impliedVolatility", 0),
                    "lastPrice": ce.get("lastPrice", 0),
                    "change": ce.get("change", 0),
                    "pChange": ce.get("pChange", 0),
                    "bidQty": ce.get("bidQty", 0),
                    "bidprice": ce.get("bidprice", 0),
                    "askQty": ce.get("askQty", 0),
                    "askPrice": ce.get("askPrice", 0),
                } if ce else None,
                "PE": {
                    "openInterest": pe.get("openInterest", 0),
                    "changeinOpenInterest": pe.get("changeinOpenInterest", 0),
                    "totalTradedVolume": pe.get("totalTradedVolume", 0),
                    "impliedVolatility": pe.get("impliedVolatility", 0),
                    "lastPrice": pe.get("lastPrice", 0),
                    "change": pe.get("change", 0),
                    "pChange": pe.get("pChange", 0),
                    "bidQty": pe.get("bidQty", 0),
                    "bidprice": pe.get("bidprice", 0),
                    "askQty": pe.get("askQty", 0),
                    "askPrice": pe.get("askPrice", 0),
                } if pe else None,
            })

        # Calculate ATM strike
        atm_strike = min(strike_prices, key=lambda x: abs(x - underlying_value)) if strike_prices else 0

        return {
            "symbol": symbol,
            "name": self.INDICES[symbol]["name"],
            "lotSize": self.INDICES[symbol]["lot_size"],
            "underlyingValue": underlying_value,
            "atmStrike": atm_strike,
            "expiryDates": expiry_dates,
            "strikePrices": strike_prices,
            "timestamp": records.get("timestamp", ""),
            "data": chain_data,
            "totals": {
                "CE": {
                    "totalOI": filtered.get("CE", {}).get("totOI", 0),
                    "totalVolume": filtered.get("CE", {}).get("totVol", 0),
                },
                "PE": {
                    "totalOI": filtered.get("PE", {}).get("totOI", 0),
                    "totalVolume": filtered.get("PE", {}).get("totVol", 0),
                }
            },
            "isDemo": raw_data.get("isDemo", False),
            "fetchedAt": datetime.now().isoformat()
        }

    async def get_spot_price(self, symbol: str) -> Dict[str, Any]:
        """Get current spot price for an index"""
        # Get from option chain data
        chain = await self.get_option_chain(symbol)
        return {
            "symbol": symbol.upper(),
            "name": chain["name"],
            "lastPrice": chain["underlyingValue"],
            "change": 0,  # Would need additional API call
            "pChange": 0,
            "open": chain["underlyingValue"],
            "high": chain["underlyingValue"],
            "low": chain["underlyingValue"],
            "previousClose": chain["underlyingValue"],
            "isDemo": chain.get("isDemo", False),
            "timestamp": datetime.now().isoformat()
        }

    async def get_expiry_dates(self, symbol: str) -> List[str]:
        """Get available expiry dates"""
        chain = await self.get_option_chain(symbol)
        return chain.get("expiryDates", [])

    async def get_option_chain_by_expiry(
        self,
        symbol: str,
        expiry: str,
        strikes_around_atm: int = 10
    ) -> Dict[str, Any]:
        """Get option chain filtered by expiry"""
        full_chain = await self.get_option_chain(symbol)

        # Filter by expiry
        filtered_data = [
            item for item in full_chain["data"]
            if item["expiryDate"] == expiry
        ]

        # Filter strikes around ATM
        if strikes_around_atm > 0 and filtered_data:
            atm = full_chain["atmStrike"]
            strikes = sorted(set(item["strikePrice"] for item in filtered_data))

            if strikes:
                atm_idx = min(range(len(strikes)), key=lambda i: abs(strikes[i] - atm))
                start = max(0, atm_idx - strikes_around_atm)
                end = min(len(strikes), atm_idx + strikes_around_atm + 1)
                valid_strikes = set(strikes[start:end])

                filtered_data = [
                    item for item in filtered_data
                    if item["strikePrice"] in valid_strikes
                ]

        return {
            **full_chain,
            "data": filtered_data,
            "selectedExpiry": expiry,
            "strikesAroundATM": strikes_around_atm
        }

    def clear_cache(self) -> None:
        """Clear all cached data"""
        self._cache.clear()

    async def close(self) -> None:
        """Close HTTP session"""
        if self._session and not self._session.is_closed:
            await self._session.aclose()


# Singleton instance
nse_service = NSEService()
