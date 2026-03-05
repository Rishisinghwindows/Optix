"""
Upstox Data Service - Fetches live market data from Upstox API
Caches data on the server to serve to all clients (iOS, Android, Web)
"""

import asyncio
import time
import logging
from typing import Optional, Dict, Any, List
from datetime import datetime, timedelta
import httpx
import os

logger = logging.getLogger(__name__)
from .cache_service import cache


class UpstoxService:
    """
    Service to fetch live market data from Upstox API.
    Caches data with short TTL to reduce API calls.
    """

    BASE_URL = "https://api.upstox.com/v2"
    AUTH_URL = "https://api.upstox.com/v2/login/authorization/dialog"
    TOKEN_URL = "https://api.upstox.com/v2/login/authorization/token"

    # Index instrument keys for Upstox
    INDEX_KEYS = {
        "NIFTY": "NSE_INDEX|Nifty 50",
        "BANKNIFTY": "NSE_INDEX|Nifty Bank",
        "FINNIFTY": "NSE_INDEX|Nifty Fin Service",
        "MIDCPNIFTY": "NSE_INDEX|NIFTY MID SELECT",
        "SENSEX": "BSE_INDEX|SENSEX",
        "INDIAVIX": "NSE_INDEX|India VIX",
    }

    # Index info
    INDICES = {
        "NIFTY": {"name": "NIFTY 50", "lot_size": 65, "exchange": "NSE_FO"},
        "BANKNIFTY": {"name": "BANK NIFTY", "lot_size": 30, "exchange": "NSE_FO"},
        "FINNIFTY": {"name": "FIN NIFTY", "lot_size": 60, "exchange": "NSE_FO"},
        "MIDCPNIFTY": {"name": "MIDCAP NIFTY", "lot_size": 120, "exchange": "NSE_FO"},
        "SENSEX": {"name": "SENSEX", "lot_size": 20, "exchange": "BSE_FO"},
        "INDIAVIX": {"name": "India VIX", "lot_size": 0, "exchange": "NSE_INDEX"},
    }

    def __init__(self):
        self._client: Optional[httpx.AsyncClient] = None
        self._access_token: Optional[str] = None
        self._token_expiry: float = 0

        # Cache TTLs (seconds)
        self._spot_cache_ttl: int = 1
        self._option_chain_cache_ttl: int = 2
        self._expiry_cache_ttl: int = 300
        self._last_live_ttl: int = 604800  # 7 days - fallback cache for when Upstox is unavailable

        # Load credentials from settings
        from ..config import settings
        self._api_key = settings.upstox_api_key or ""
        self._api_secret = settings.upstox_api_secret or ""
        self._redirect_uri = settings.upstox_redirect_uri

        # For storing refresh token
        self._refresh_token: Optional[str] = None

        # Token persistence file
        self._token_file = os.path.join(os.path.dirname(__file__), ".upstox_token")
        self._load_token()

    async def _get_last_live(self, key: str) -> Optional[Any]:
        return await cache.get(f"last_live:{key}")

    async def _set_last_live(self, key: str, value: Any) -> None:
        await cache.set(f"last_live:{key}", value, ttl=self._last_live_ttl)

    def _load_token(self) -> None:
        """Load token from file if exists"""
        try:
            import json
            if os.path.exists(self._token_file):
                with open(self._token_file, "r") as f:
                    data = json.load(f)
                    self._access_token = data.get("access_token")
                    self._refresh_token = data.get("refresh_token")
                    # Extract actual JWT expiry instead of using stored value
                    if self._access_token:
                        self._token_expiry = self._extract_jwt_expiry(self._access_token)
                    else:
                        self._token_expiry = 0
                    if self.is_authenticated():
                        logger.info("Loaded Upstox token from file")
                        print(f"[UPSTOX] Token loaded from file, valid until {datetime.fromtimestamp(self._token_expiry)}")
                    else:
                        print(f"[UPSTOX] Token expired at {datetime.fromtimestamp(self._token_expiry)}, needs re-login")
        except Exception as e:
            logger.error(f"Error loading token: {e}")

    def _save_token(self) -> None:
        """Save token to file for persistence"""
        try:
            import json
            with open(self._token_file, "w") as f:
                json.dump({
                    "access_token": self._access_token,
                    "refresh_token": self._refresh_token,
                    "expiry": self._token_expiry
                }, f)
            logger.info("Saved Upstox token to file")
        except Exception as e:
            logger.error(f"Error saving token: {e}")

    async def _get_client(self) -> httpx.AsyncClient:
        """Get or create HTTP client"""
        if self._client is None or self._client.is_closed:
            self._client = httpx.AsyncClient(timeout=30.0)
        return self._client

    def _get_headers(self) -> Dict[str, str]:
        """Get headers with access token"""
        return {
            "Authorization": f"Bearer {self._access_token}",
            "Accept": "application/json",
            "Content-Type": "application/json",
        }

    # ==================== Authentication ====================

    def get_login_url(self) -> str:
        """Get Upstox OAuth login URL"""
        return (
            f"{self.AUTH_URL}?"
            f"client_id={self._api_key}&"
            f"redirect_uri={self._redirect_uri}&"
            f"response_type=code"
        )

    async def exchange_code_for_token(self, auth_code: str) -> tuple[bool, str]:
        """Exchange authorization code for access token. Returns (success, error_message)"""
        try:
            client = await self._get_client()

            logger.info(f"Exchanging code for token with redirect_uri: {self._redirect_uri}")

            response = await client.post(
                self.TOKEN_URL,
                data={
                    "code": auth_code,
                    "client_id": self._api_key,
                    "client_secret": self._api_secret,
                    "redirect_uri": self._redirect_uri,
                    "grant_type": "authorization_code",
                },
                headers={"Content-Type": "application/x-www-form-urlencoded"}
            )

            logger.info(f"Token response status: {response.status_code}")

            if response.status_code == 200:
                data = response.json()
                self._access_token = data.get("access_token")
                self._refresh_token = data.get("refresh_token")
                # Extract actual expiry from JWT token
                self._token_expiry = self._extract_jwt_expiry(self._access_token)
                self._save_token()  # Persist token
                logger.info("Upstox token obtained successfully")
                print(f"[UPSTOX] Token obtained! Expires at: {datetime.fromtimestamp(self._token_expiry)}")
                return True, ""
            else:
                error_data = response.json() if response.headers.get("content-type", "").startswith("application/json") else {"message": response.text}
                error_msg = error_data.get("message", error_data.get("error", response.text))
                logger.error(f"Failed to get token: {error_msg}")
                print(f"[UPSTOX] Token exchange failed: {error_msg}")
                return False, error_msg

        except Exception as e:
            logger.error(f"Error exchanging code: {e}")
            print(f"[UPSTOX] Exception during token exchange: {e}")
            return False, str(e)

    def _extract_jwt_expiry(self, token: str) -> float:
        """Extract expiry time from JWT token"""
        try:
            import base64
            import json
            # JWT format: header.payload.signature
            parts = token.split('.')
            if len(parts) >= 2:
                # Decode payload (add padding if needed)
                payload = parts[1]
                padding = 4 - len(payload) % 4
                if padding != 4:
                    payload += '=' * padding
                decoded = base64.b64decode(payload)
                data = json.loads(decoded)
                exp = data.get('exp', 0)
                if exp > 0:
                    logger.info(f"JWT expiry extracted: {datetime.fromtimestamp(exp)}")
                    return float(exp)
        except Exception as e:
            logger.warning(f"Could not extract JWT expiry: {e}")
        # Fallback to 8 hours from now (market close)
        return time.time() + 28800

    def set_access_token(self, token: str) -> None:
        """Manually set access token (for testing or direct token input)"""
        self._access_token = token
        self._token_expiry = self._extract_jwt_expiry(token)
        logger.info("Access token set manually")

    def is_authenticated(self) -> bool:
        """Check if we have a valid access token"""
        return self._access_token is not None and time.time() < self._token_expiry

    def get_token_status(self) -> Dict[str, Any]:
        """Get detailed token status for admin dashboard"""
        now = time.time()
        has_token = self._access_token is not None
        is_expired = has_token and now >= self._token_expiry
        is_valid = has_token and now < self._token_expiry

        # Calculate time remaining or time since expired
        if has_token:
            if is_valid:
                seconds_remaining = self._token_expiry - now
                hours_remaining = seconds_remaining / 3600
                expiry_str = datetime.fromtimestamp(self._token_expiry).strftime("%d-%b-%Y %H:%M:%S")
            else:
                seconds_expired = now - self._token_expiry
                hours_expired = seconds_expired / 3600
                expiry_str = datetime.fromtimestamp(self._token_expiry).strftime("%d-%b-%Y %H:%M:%S")
        else:
            expiry_str = None

        return {
            "hasToken": has_token,
            "isValid": is_valid,
            "isExpired": is_expired,
            "expiryTime": expiry_str,
            "expiryTimestamp": self._token_expiry if has_token else None,
            "hoursRemaining": round(hours_remaining, 1) if (has_token and is_valid) else None,
            "hoursExpiredAgo": round(hours_expired, 1) if (has_token and is_expired) else None,
            "message": (
                "Token valid" if is_valid else
                f"Token expired {round(hours_expired, 1)} hours ago - please reconnect" if is_expired else
                "No token - please connect to Upstox"
            )
        }

    def disconnect(self) -> None:
        """Clear access token and disconnect from Upstox"""
        self._access_token = None
        self._refresh_token = None
        self._token_expiry = 0
        try:
            loop = asyncio.get_running_loop()
            loop.create_task(cache.clear())
        except RuntimeError:
            # No running loop; skip cache clear to avoid blocking sync call
            pass
        # Delete token file
        if os.path.exists(self._token_file):
            os.remove(self._token_file)
        logger.info("Disconnected from Upstox")
        print("[UPSTOX] Disconnected - will use Redis cache if available")

    # ==================== Caching ====================

    # ==================== Market Data ====================

    async def get_spot_price(self, symbol: str) -> Dict[str, Any]:
        """Get current spot price for an index"""
        symbol = symbol.upper()

        if symbol not in self.INDEX_KEYS:
            raise ValueError(f"Unsupported symbol: {symbol}")

        # Check cache
        cache_key = f"spot:{symbol}"
        cached = await cache.get(cache_key)
        if cached:
            return cached

        if not self.is_authenticated():
            logger.warning("Not authenticated, returning last live cache if available")
            cached_live = await self._get_last_live(cache_key)
            if cached_live:
                cached_live["isLive"] = False
                cached_live["isDemo"] = False
                cached_live["dataSource"] = "redis_cache"
                cached_live["timestamp"] = datetime.now().isoformat()
                return cached_live
            logger.error(f"No Redis cache available for {cache_key}")
            raise Exception("Upstox not connected and no cached data available. Please connect from Admin dashboard.")

        try:
            client = await self._get_client()
            instrument_key = self.INDEX_KEYS[symbol]

            response = await client.get(
                f"{self.BASE_URL}/market-quote/quotes",
                params={"instrument_key": instrument_key},
                headers=self._get_headers()
            )

            if response.status_code == 200:
                data = response.json()
                # Upstox returns key with ":" instead of "|"
                response_key = instrument_key.replace("|", ":")
                quote_data = data.get("data", {}).get(response_key, {})

                # Calculate percentage change
                last_price = quote_data.get("last_price", 0)
                net_change = quote_data.get("net_change", 0)
                ohlc_close = quote_data.get("ohlc", {}).get("close", 0)
                prev_close = ohlc_close if ohlc_close and ohlc_close != last_price else (last_price - net_change if net_change else last_price)
                pct_change = (net_change / prev_close * 100) if prev_close else 0

                result = {
                    "symbol": symbol,
                    "name": self.INDICES[symbol]["name"],
                    "price": last_price,  # Main price field
                    "lastPrice": last_price,
                    "change": net_change,
                    "pChange": round(pct_change, 2),
                    "open": quote_data.get("ohlc", {}).get("open", 0),
                    "high": quote_data.get("ohlc", {}).get("high", 0),
                    "low": quote_data.get("ohlc", {}).get("low", 0),
                    "previousClose": prev_close,
                    "timestamp": datetime.now().isoformat(),
                    "isLive": True,
                    "isDemo": False,
                    "dataSource": "upstox_live",
                }

                await cache.set(cache_key, result, ttl=self._spot_cache_ttl)
                await self._set_last_live(cache_key, result)
                return result
            else:
                logger.error(f"Failed to get spot price: {response.text}")
                cached_live = await self._get_last_live(cache_key)
                if cached_live:
                    cached_live["isLive"] = False
                    cached_live["isDemo"] = False
                    cached_live["dataSource"] = "redis_cache"
                    cached_live["timestamp"] = datetime.now().isoformat()
                    return cached_live
                raise Exception("Failed to fetch live data from Upstox and no cache available")

        except Exception as e:
            logger.error(f"Error fetching spot price: {e}")
            cached_live = await self._get_last_live(cache_key)
            if cached_live:
                cached_live["isLive"] = False
                cached_live["isDemo"] = False
                cached_live["dataSource"] = "redis_cache"
                cached_live["timestamp"] = datetime.now().isoformat()
                return cached_live
            raise

    async def get_expiry_dates(self, symbol: str) -> List[str]:
        """Get available expiry dates for an index"""
        symbol = symbol.upper()

        if symbol not in self.INDICES:
            raise ValueError(f"Unsupported symbol: {symbol}")

        # Check cache
        cache_key = f"expiry:{symbol}"
        cached = await cache.get(cache_key)
        if cached:
            return cached

        if not self.is_authenticated():
            logger.warning("Not authenticated, returning last live expiries if available")
            cached_live = await self._get_last_live(cache_key)
            if cached_live:
                return cached_live
            logger.error(f"No Redis cache available for {cache_key}")
            raise Exception("Upstox not connected and no cached data available. Please connect from Admin dashboard.")

        try:
            client = await self._get_client()

            # Fetch option contracts to get expiry dates
            response = await client.get(
                f"{self.BASE_URL}/option/contract",
                params={"instrument_key": self.INDEX_KEYS[symbol]},
                headers=self._get_headers()
            )

            if response.status_code == 200:
                data = response.json()
                contracts = data.get("data", [])

                # Extract unique expiry dates from contracts
                expiry_set = set()
                for contract in contracts:
                    exp = contract.get("expiry")
                    if exp:
                        expiry_set.add(exp)

                # Sort and format dates
                sorted_expiries = sorted(list(expiry_set))
                formatted = []
                for exp in sorted_expiries[:12]:  # Limit to 12 expiries
                    try:
                        dt = datetime.strptime(exp, "%Y-%m-%d")
                        formatted.append(dt.strftime("%d-%b-%Y"))
                    except:
                        formatted.append(exp)

                await cache.set(cache_key, formatted, ttl=self._expiry_cache_ttl)
                await self._set_last_live(cache_key, formatted)
                return formatted
            else:
                logger.error(f"Failed to get expiries: {response.text}")
                cached_live = await self._get_last_live(cache_key)
                if cached_live:
                    return cached_live
                raise Exception("Live data unavailable and no cache available")

        except Exception as e:
            logger.error(f"Error fetching expiries: {e}")
            cached_live = await self._get_last_live(cache_key)
            if cached_live:
                return cached_live
            raise

    async def get_option_chain(
        self,
        symbol: str,
        expiry: str,
        strikes_around_atm: int = 15
    ) -> Dict[str, Any]:
        """Get option chain for an index and expiry"""
        symbol = symbol.upper()

        if symbol not in self.INDICES:
            raise ValueError(f"Unsupported symbol: {symbol}")

        # Check cache
        cache_key = f"chain:{symbol}:{expiry}:{strikes_around_atm}"
        cached = await cache.get(cache_key)
        if cached:
            return cached

        if not self.is_authenticated():
            logger.warning("Not authenticated, returning last live chain if available")
            cached_live = await self._get_last_live(cache_key)
            if not cached_live:
                # Try other strike counts if exact match not found
                for alt_strikes in [15, 25, 50, 10]:
                    if alt_strikes == strikes_around_atm:
                        continue
                    alt_key = f"chain:{symbol}:{expiry}:{alt_strikes}"
                    cached_live = await self._get_last_live(alt_key)
                    if cached_live:
                        logger.info(f"Found cached chain with {alt_strikes} strikes instead of {strikes_around_atm}")
                        break
            if cached_live:
                cached_live["isLive"] = False
                cached_live["isDemo"] = False
                cached_live["dataSource"] = "redis_cache"
                cached_live["timestamp"] = datetime.now().strftime("%d-%b-%Y %H:%M:%S")
                return cached_live
            logger.error(f"No Redis cache available for {cache_key}")
            raise Exception("Upstox not connected and no cached data available. Please connect from Admin dashboard.")

        try:
            client = await self._get_client()

            # Convert expiry format if needed (dd-MMM-yyyy to yyyy-MM-dd)
            try:
                expiry_dt = datetime.strptime(expiry, "%d-%b-%Y")
                expiry_api = expiry_dt.strftime("%Y-%m-%d")
            except:
                expiry_api = expiry

            # Get spot price first
            spot_data = await self.get_spot_price(symbol)
            spot_price = spot_data.get("lastPrice", 0) or spot_data.get("price", 0)

            # Get contracts for this expiry
            contracts_response = await client.get(
                f"{self.BASE_URL}/option/contract",
                params={"instrument_key": self.INDEX_KEYS[symbol]},
                headers=self._get_headers()
            )

            if contracts_response.status_code != 200:
                logger.error(f"Failed to get contracts: {contracts_response.text}")
                cached_live = await self._get_last_live(cache_key)
                if not cached_live:
                    for alt_strikes in [15, 25, 50, 10]:
                        if alt_strikes == strikes_around_atm:
                            continue
                        cached_live = await self._get_last_live(f"chain:{symbol}:{expiry}:{alt_strikes}")
                        if cached_live:
                            break
                if cached_live:
                    cached_live["isLive"] = False
                    cached_live["isDemo"] = False
                    cached_live["dataSource"] = "redis_cache"
                    cached_live["timestamp"] = datetime.now().strftime("%d-%b-%Y %H:%M:%S")
                    return cached_live
                raise Exception("Live data unavailable and no cache available")

            contracts_data = contracts_response.json().get("data", [])

            # Filter contracts for this expiry and get strikes around ATM
            strike_interval = {"NIFTY": 50, "BANKNIFTY": 100, "FINNIFTY": 50, "MIDCPNIFTY": 25, "SENSEX": 100}.get(symbol, 50)
            atm_strike = round(spot_price / strike_interval) * strike_interval
            min_strike = atm_strike - (strikes_around_atm * strike_interval)
            max_strike = atm_strike + (strikes_around_atm * strike_interval)

            # Organize ALL contracts for this expiry (for full-chain totals)
            strikes_map = {}       # Filtered strikes (for display)
            instrument_keys = []   # Filtered instruments (for display)
            all_instrument_keys = []  # ALL instruments (for full-chain OI totals)
            all_contracts_map = {}    # Map instrument_key -> contract info

            for contract in contracts_data:
                if contract.get("expiry") != expiry_api:
                    continue
                strike = contract.get("strike_price", 0)
                inst_key = contract.get("instrument_key")
                opt_type = contract.get("instrument_type")
                all_instrument_keys.append(inst_key)
                all_contracts_map[inst_key] = {"strike": strike, "type": opt_type}

                if min_strike <= strike <= max_strike:
                    if strike not in strikes_map:
                        strikes_map[strike] = {"CE": None, "PE": None}
                    strikes_map[strike][opt_type] = contract
                    instrument_keys.append(inst_key)

            # Fetch market quotes for ALL instruments (for full-chain OI totals + display)
            quotes_map = {}
            quotes_ok = True
            keys_to_fetch = all_instrument_keys if all_instrument_keys else instrument_keys
            if keys_to_fetch:
                # Upstox allows up to 500 instruments per request
                for i in range(0, len(keys_to_fetch), 100):
                    batch = keys_to_fetch[i:i+100]
                    quotes_response = await client.get(
                        f"{self.BASE_URL}/market-quote/quotes",
                        params={"instrument_key": ",".join(batch)},
                        headers=self._get_headers()
                    )
                    if quotes_response.status_code != 200:
                        quotes_ok = False
                        logger.error(f"Failed to get option quotes: {quotes_response.status_code} {quotes_response.text}")
                        break

                    quotes_data = quotes_response.json().get("data", {})
                    for key, quote in quotes_data.items():
                        # Map by multiple possible identifiers to avoid key mismatches
                        normalized_key = key.replace(":", "|")
                        quotes_map[key] = quote
                        quotes_map[normalized_key] = quote
                        inst_key = quote.get("instrument_key") or quote.get("instrument_token")
                        if inst_key:
                            quotes_map[inst_key] = quote

            if not quotes_ok or (keys_to_fetch and not quotes_map):
                cached_live = await self._get_last_live(cache_key)
                if not cached_live:
                    for alt_strikes in [15, 25, 50, 10]:
                        if alt_strikes == strikes_around_atm:
                            continue
                        cached_live = await self._get_last_live(f"chain:{symbol}:{expiry}:{alt_strikes}")
                        if cached_live:
                            break
                if cached_live:
                    cached_live["isLive"] = False
                    cached_live["isDemo"] = False
                    cached_live["dataSource"] = "cache"
                    cached_live["timestamp"] = datetime.now().strftime("%d-%b-%Y %H:%M:%S")
                    return cached_live
                raise Exception("Live data unavailable")

            # Compute full-chain OI totals from ALL strikes (for accurate PCR)
            total_call_oi = 0
            total_put_oi = 0
            total_call_volume = 0
            total_put_volume = 0
            for inst_key in all_instrument_keys:
                quote = quotes_map.get(inst_key, {})
                contract_info = all_contracts_map.get(inst_key, {})
                oi = quote.get("oi", 0) or 0
                vol = quote.get("volume", 0) or 0
                if contract_info.get("type") == "CE":
                    total_call_oi += oi
                    total_call_volume += vol
                elif contract_info.get("type") == "PE":
                    total_put_oi += oi
                    total_put_volume += vol

            # Build option chain data (filtered strikes for display)
            chain_data = []
            for strike in sorted(strikes_map.keys()):
                contracts = strikes_map[strike]
                row = {
                    "strikePrice": strike,
                    "expiryDate": expiry,
                    "CE": None,
                    "PE": None
                }

                for opt_type in ["CE", "PE"]:
                    contract = contracts.get(opt_type)
                    if contract:
                        inst_key = contract.get("instrument_key")
                        quote = quotes_map.get(inst_key, {})
                        row[opt_type] = {
                            "openInterest": quote.get("oi", 0) or 0,
                            "changeinOpenInterest": quote.get("oi_day_change", 0) or 0,
                            "totalTradedVolume": quote.get("volume", 0) or 0,
                            "impliedVolatility": 0,  # Computed below via Black-Scholes
                            "lastPrice": quote.get("last_price", 0) or 0,
                            "change": quote.get("net_change", 0) or 0,
                            "pChange": quote.get("percentage_change", 0) or 0,
                            "bidQty": quote.get("depth", {}).get("buy", [{}])[0].get("quantity", 0) if quote.get("depth", {}).get("buy") else 0,
                            "bidprice": quote.get("depth", {}).get("buy", [{}])[0].get("price", 0) if quote.get("depth", {}).get("buy") else 0,
                            "askQty": quote.get("depth", {}).get("sell", [{}])[0].get("quantity", 0) if quote.get("depth", {}).get("sell") else 0,
                            "askPrice": quote.get("depth", {}).get("sell", [{}])[0].get("price", 0) if quote.get("depth", {}).get("sell") else 0,
                            "instrumentKey": inst_key,
                            "tradingSymbol": contract.get("trading_symbol", "")
                        }

                chain_data.append(row)

            # Compute IV and Greeks via Black-Scholes for all options
            from .greeks_service import enrich_option_chain
            enrich_option_chain(chain_data, spot_price, expiry)

            result = {
                "symbol": symbol,
                "name": self.INDICES[symbol]["name"],
                "lotSize": contracts_data[0].get("lot_size", self.INDICES[symbol]["lot_size"]) if contracts_data else self.INDICES[symbol]["lot_size"],
                "underlyingValue": spot_price,
                "atmStrike": atm_strike,
                "expiryDates": [expiry],
                "strikePrices": sorted(list(strikes_map.keys())),
                "timestamp": datetime.now().strftime("%d-%b-%Y %H:%M:%S"),
                "data": chain_data,
                "totals": {
                    "CE": {"totalOI": int(total_call_oi), "totalVolume": int(total_call_volume)},
                    "PE": {"totalOI": int(total_put_oi), "totalVolume": int(total_put_volume)},
                },
                "isLive": True,
                "fetchedAt": datetime.now().isoformat()
            }

            await cache.set(cache_key, result, ttl=self._option_chain_cache_ttl)
            await self._set_last_live(cache_key, result)
            return result

        except Exception as e:
            logger.error(f"Error fetching option chain: {e}")
            import traceback
            traceback.print_exc()
            cached_live = await self._get_last_live(cache_key)
            if not cached_live:
                for alt_strikes in [15, 25, 50, 10]:
                    if alt_strikes == strikes_around_atm:
                        continue
                    cached_live = await self._get_last_live(f"chain:{symbol}:{expiry}:{alt_strikes}")
                    if cached_live:
                        break
            if cached_live:
                cached_live["isLive"] = False
                cached_live["isDemo"] = False
                cached_live["dataSource"] = "redis_cache"
                cached_live["timestamp"] = datetime.now().strftime("%d-%b-%Y %H:%M:%S")
                return cached_live
            raise

    def _process_option_chain(
        self,
        symbol: str,
        chain_data: List[Dict],
        spot_price: float,
        expiry: str,
        strikes_around_atm: int
    ) -> Dict[str, Any]:
        """Process raw option chain data into our format"""

        # Get strike interval
        strike_intervals = {
            "NIFTY": 50,
            "BANKNIFTY": 100,
            "FINNIFTY": 50,
            "MIDCPNIFTY": 25,
            "SENSEX": 100
        }
        strike_interval = strike_intervals.get(symbol, 50)

        # Calculate ATM strike
        atm_strike = round(spot_price / strike_interval) * strike_interval

        # Filter strikes around ATM
        min_strike = atm_strike - (strikes_around_atm * strike_interval)
        max_strike = atm_strike + (strikes_around_atm * strike_interval)

        processed_data = []
        all_strikes = set()

        # Compute full-chain totals from ALL strikes (for accurate PCR)
        total_call_oi = 0
        total_put_oi = 0
        total_call_volume = 0
        total_put_volume = 0
        for item in chain_data:
            ce_md = item.get("call_options", {}).get("market_data", {})
            pe_md = item.get("put_options", {}).get("market_data", {})
            total_call_oi += ce_md.get("oi", 0) or 0
            total_put_oi += pe_md.get("oi", 0) or 0
            total_call_volume += ce_md.get("volume", 0) or 0
            total_put_volume += pe_md.get("volume", 0) or 0

        for item in chain_data:
            strike = item.get("strike_price", 0)
            if min_strike <= strike <= max_strike:
                all_strikes.add(strike)

                ce_data = item.get("call_options", {}).get("market_data", {})
                pe_data = item.get("put_options", {}).get("market_data", {})

                processed_data.append({
                    "strikePrice": strike,
                    "expiryDate": expiry,
                    "CE": {
                        "openInterest": ce_data.get("oi", 0),
                        "changeinOpenInterest": ce_data.get("oi_day_change", 0),
                        "totalTradedVolume": ce_data.get("volume", 0),
                        "impliedVolatility": item.get("call_options", {}).get("option_greeks", {}).get("iv", 0),
                        "lastPrice": ce_data.get("ltp", 0),
                        "change": ce_data.get("net_change", 0),
                        "pChange": ce_data.get("percentage_change", 0),
                        "bidQty": ce_data.get("bid_qty", 0),
                        "bidprice": ce_data.get("bid_price", 0),
                        "askQty": ce_data.get("ask_qty", 0),
                        "askPrice": ce_data.get("ask_price", 0),
                    } if ce_data else None,
                    "PE": {
                        "openInterest": pe_data.get("oi", 0),
                        "changeinOpenInterest": pe_data.get("oi_day_change", 0),
                        "totalTradedVolume": pe_data.get("volume", 0),
                        "impliedVolatility": item.get("put_options", {}).get("option_greeks", {}).get("iv", 0),
                        "lastPrice": pe_data.get("ltp", 0),
                        "change": pe_data.get("net_change", 0),
                        "pChange": pe_data.get("percentage_change", 0),
                        "bidQty": pe_data.get("bid_qty", 0),
                        "bidprice": pe_data.get("bid_price", 0),
                        "askQty": pe_data.get("ask_qty", 0),
                        "askPrice": pe_data.get("ask_price", 0),
                    } if pe_data else None,
                })

        # Enrich with computed IV and Greeks where Upstox didn't provide them
        from .greeks_service import enrich_option_chain
        sorted_data = sorted(processed_data, key=lambda x: x["strikePrice"])
        enrich_option_chain(sorted_data, spot_price, expiry)

        return {
            "symbol": symbol,
            "name": self.INDICES[symbol]["name"],
            "lotSize": self.INDICES[symbol]["lot_size"],
            "underlyingValue": spot_price,
            "atmStrike": atm_strike,
            "expiryDates": [expiry],  # Current expiry
            "strikePrices": sorted(list(all_strikes)),
            "timestamp": datetime.now().strftime("%d-%b-%Y %H:%M:%S"),
            "data": sorted_data,
            "totals": {
                "CE": {"totalOI": int(total_call_oi), "totalVolume": int(total_call_volume)},
                "PE": {"totalOI": int(total_put_oi), "totalVolume": int(total_put_volume)},
            },
            "isLive": True,
            "fetchedAt": datetime.now().isoformat()
        }

    async def close(self) -> None:
        """Close HTTP client"""
        if self._client and not self._client.is_closed:
            await self._client.aclose()


# Singleton instance
upstox_service = UpstoxService()
