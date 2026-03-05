"""
================================================================================
WEBSOCKET SERVICE - Real-time Market Data Streaming
================================================================================

This service provides real-time market data to connected iOS/Web clients via
WebSocket. It eliminates the need for frequent REST API polling.

DATA FLOW:
┌─────────────────────────────────────────────────────────────────────────────┐
│  iOS/Web App                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │  1. Connect to ws://localhost:8000/ws/market                           ││
│  │  2. Subscribe: {"action":"subscribe","type":"spot","symbols":["NIFTY"]}││
│  │  3. Receive updates automatically every 1-2 seconds                    ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│                              │                                               │
│                              ▼                                               │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │  Backend WebSocket Service (this file)                                 ││
│  │  - Manages connections & subscriptions                                 ││
│  │  - Broadcasts data to subscribed clients                               ││
│  │  - Uses cached data (no duplicate Upstox calls)                        ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│                              │                                               │
│                              ▼                                               │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │  Upstox Service (upstox_service.py)                                    ││
│  │  - Fetches from Upstox API with caching                                ││
│  │  - 2s cache for spot prices                                            ││
│  │  - 5s cache for option chains                                          ││
│  └─────────────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────┘

SUBSCRIPTION TYPES:
- spot: Spot price updates every 1 second
- ticker: Fast LTP-only updates every 500ms
- option_chain: Full option chain every 5 seconds

USAGE FROM iOS:
    // Connect
    GuestWebSocketService.shared.connect()

    // Subscribe to NIFTY spot
    GuestWebSocketService.shared.subscribeToSpot(symbols: ["NIFTY"])

    // Receive updates via NotificationCenter
    NotificationCenter.default.publisher(for: .guestSpotPriceUpdated)
================================================================================
"""

import asyncio
import json
import logging
from typing import Dict, Set, Any, Optional
from datetime import datetime
from fastapi import WebSocket, WebSocketDisconnect
from enum import Enum

logger = logging.getLogger(__name__)


class SubscriptionType(str, Enum):
    """Types of market data subscriptions available"""
    SPOT = "spot"              # Full spot price data (every 1 second)
    OPTION_CHAIN = "option_chain"  # Full option chain (every 5 seconds)
    TICKER = "ticker"          # Fast LTP-only updates (every 500ms)


class ConnectionManager:
    """
    Manages WebSocket connections and subscriptions.

    Features:
    - Maintains active connections with automatic cleanup
    - Handles subscriptions per connection
    - Caches latest data for immediate response on new subscriptions
    - Thread-safe operations

    Example:
        manager = ConnectionManager()
        await manager.connect(websocket)
        await manager.subscribe(websocket, SubscriptionType.SPOT, ["NIFTY"])
        await manager.broadcast_spot_price("NIFTY", data)
    """

    def __init__(self):
        # All active connections
        self.active_connections: Set[WebSocket] = set()

        # Subscriptions: {websocket: {type: [symbols]}}
        self.subscriptions: Dict[WebSocket, Dict[str, Set[str]]] = {}

        # Option chain expiry preferences: {websocket: {symbol: expiry}}
        self.option_chain_expiries: Dict[WebSocket, Dict[str, str]] = {}

        # Latest data cache for immediate response on subscribe
        self.latest_data: Dict[str, Any] = {}

        # Previous data for change detection (ticker optimization)
        self._previous_prices: Dict[str, float] = {}

        # Background task for broadcasting
        self._broadcast_task: Optional[asyncio.Task] = None
        self._is_running = False

        # Statistics
        self._messages_sent = 0
        self._connections_total = 0

    async def connect(self, websocket: WebSocket) -> None:
        """
        Accept a new WebSocket connection.

        Sends a welcome message with server info and available subscription types.
        """
        await websocket.accept()
        self.active_connections.add(websocket)
        self._connections_total += 1
        self.subscriptions[websocket] = {
            SubscriptionType.SPOT: set(),
            SubscriptionType.OPTION_CHAIN: set(),
            SubscriptionType.TICKER: set(),
        }
        self.option_chain_expiries[websocket] = {}
        logger.info(f"Client connected. Total: {len(self.active_connections)}, Lifetime: {self._connections_total}")

        # Send welcome message with available subscriptions
        await self._send_message(websocket, {
            "type": "connected",
            "message": "Connected to Optix Market Data Stream",
            "server_time": datetime.now().isoformat(),
            "available_subscriptions": {
                "spot": "Full spot price data (1s interval)",
                "ticker": "Fast LTP-only updates (500ms interval)",
                "option_chain": "Full option chain data (5s interval)"
            },
            "supported_symbols": ["NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY", "SENSEX"]
        })

    def disconnect(self, websocket: WebSocket) -> None:
        """Remove a WebSocket connection"""
        self.active_connections.discard(websocket)
        self.subscriptions.pop(websocket, None)
        self.option_chain_expiries.pop(websocket, None)
        logger.info(f"Client disconnected. Total connections: {len(self.active_connections)}")

    async def subscribe(
        self,
        websocket: WebSocket,
        subscription_type: SubscriptionType,
        symbols: list[str],
        expiry: Optional[str] = None
    ) -> None:
        """Subscribe to market data for symbols"""
        if websocket not in self.subscriptions:
            return

        for symbol in symbols:
            self.subscriptions[websocket][subscription_type].add(symbol.upper())
            if subscription_type == SubscriptionType.OPTION_CHAIN and expiry:
                self.option_chain_expiries[websocket][symbol.upper()] = expiry

        logger.info(f"Client subscribed to {subscription_type}: {symbols}")

        # Send acknowledgment
        await self._send_message(websocket, {
            "type": "subscribed",
            "subscription": subscription_type,
            "symbols": symbols,
            "expiry": expiry,
            "timestamp": datetime.now().isoformat()
        })

        # Send latest cached data immediately if available
        for symbol in symbols:
            if subscription_type == SubscriptionType.OPTION_CHAIN and expiry:
                cache_key = f"{SubscriptionType.OPTION_CHAIN}_{symbol.upper()}_{expiry}"
                if cache_key in self.latest_data:
                    await self._send_message(websocket, self.latest_data[cache_key])
            else:
                cache_key = f"{subscription_type}_{symbol.upper()}"
                if cache_key in self.latest_data:
                    await self._send_message(websocket, self.latest_data[cache_key])

    async def unsubscribe(
        self,
        websocket: WebSocket,
        subscription_type: SubscriptionType,
        symbols: list[str]
    ) -> None:
        """Unsubscribe from market data for symbols"""
        if websocket not in self.subscriptions:
            return

        for symbol in symbols:
            self.subscriptions[websocket][subscription_type].discard(symbol.upper())
            if subscription_type == SubscriptionType.OPTION_CHAIN:
                self.option_chain_expiries.get(websocket, {}).pop(symbol.upper(), None)

        await self._send_message(websocket, {
            "type": "unsubscribed",
            "subscription": subscription_type,
            "symbols": symbols,
            "timestamp": datetime.now().isoformat()
        })

    async def broadcast_spot_price(self, symbol: str, data: Dict[str, Any]) -> None:
        """Broadcast spot price update to subscribed clients"""
        message = {
            "type": "spot_update",
            "symbol": symbol.upper(),
            "data": data,
            "timestamp": datetime.now().isoformat()
        }

        # Cache latest data
        cache_key = f"{SubscriptionType.SPOT}_{symbol.upper()}"
        self.latest_data[cache_key] = message

        # Broadcast to subscribed clients
        await self._broadcast_to_subscribers(SubscriptionType.SPOT, symbol, message)

    async def broadcast_option_chain(self, symbol: str, expiry: str, data: Dict[str, Any]) -> None:
        """Broadcast option chain update to subscribed clients"""
        message = {
            "type": "option_chain_update",
            "symbol": symbol.upper(),
            "expiry": expiry,
            "data": data,
            "timestamp": datetime.now().isoformat()
        }

        # Cache latest data
        cache_key = f"{SubscriptionType.OPTION_CHAIN}_{symbol.upper()}_{expiry}"
        self.latest_data[cache_key] = message

        # Broadcast to subscribed clients
        await self._broadcast_to_subscribers(SubscriptionType.OPTION_CHAIN, symbol, message)

    async def broadcast_ticker(self, symbol: str, data: Dict[str, Any]) -> None:
        """Broadcast ticker update (LTP changes) to subscribed clients"""
        message = {
            "type": "ticker_update",
            "symbol": symbol.upper(),
            "data": data,
            "timestamp": datetime.now().isoformat()
        }

        # Cache latest data
        cache_key = f"{SubscriptionType.TICKER}_{symbol.upper()}"
        self.latest_data[cache_key] = message

        # Broadcast to subscribed clients
        await self._broadcast_to_subscribers(SubscriptionType.TICKER, symbol, message)

    async def _broadcast_to_subscribers(
        self,
        subscription_type: SubscriptionType,
        symbol: str,
        message: Dict[str, Any]
    ) -> None:
        """Send message to all clients subscribed to the symbol"""
        symbol = symbol.upper()
        disconnected = set()

        for websocket in self.active_connections:
            if websocket in self.subscriptions:
                if symbol in self.subscriptions[websocket][subscription_type]:
                    if subscription_type == SubscriptionType.OPTION_CHAIN:
                        requested = self.option_chain_expiries.get(websocket, {}).get(symbol)
                        if requested and requested != message.get("expiry"):
                            continue
                    try:
                        await self._send_message(websocket, message)
                    except Exception as e:
                        logger.error(f"Error sending to client: {e}")
                        disconnected.add(websocket)

        # Clean up disconnected clients
        for ws in disconnected:
            self.disconnect(ws)

    async def _send_message(self, websocket: WebSocket, message: Dict[str, Any]) -> None:
        """Send JSON message to a WebSocket"""
        try:
            await websocket.send_json(message)
        except Exception as e:
            logger.error(f"Error sending message: {e}")
            raise

    async def handle_message(self, websocket: WebSocket, data: Dict[str, Any]) -> None:
        """Handle incoming message from client"""
        action = data.get("action")

        if action == "subscribe":
            sub_type = data.get("type", SubscriptionType.SPOT)
            symbols = data.get("symbols", [])
            expiry = data.get("expiry")
            if symbols:
                await self.subscribe(websocket, sub_type, symbols, expiry=expiry)

        elif action == "unsubscribe":
            sub_type = data.get("type", SubscriptionType.SPOT)
            symbols = data.get("symbols", [])
            if symbols:
                await self.unsubscribe(websocket, sub_type, symbols)

        elif action == "ping":
            await self._send_message(websocket, {
                "type": "pong",
                "timestamp": datetime.now().isoformat()
            })

        else:
            await self._send_message(websocket, {
                "type": "error",
                "message": f"Unknown action: {action}",
                "timestamp": datetime.now().isoformat()
            })

    def get_stats(self) -> Dict[str, Any]:
        """
        Get comprehensive connection statistics.

        Returns:
            Dictionary with connection counts, subscription details, and performance metrics.
        """
        # Count subscriptions by type
        spot_subs = sum(len(subs.get(SubscriptionType.SPOT, set())) for subs in self.subscriptions.values())
        ticker_subs = sum(len(subs.get(SubscriptionType.TICKER, set())) for subs in self.subscriptions.values())
        chain_subs = sum(len(subs.get(SubscriptionType.OPTION_CHAIN, set())) for subs in self.subscriptions.values())

        # Get unique symbols being watched
        all_spot = set()
        all_ticker = set()
        all_chain = set()
        for subs in self.subscriptions.values():
            all_spot.update(subs.get(SubscriptionType.SPOT, set()))
            all_ticker.update(subs.get(SubscriptionType.TICKER, set()))
            all_chain.update(subs.get(SubscriptionType.OPTION_CHAIN, set()))

        return {
            "active_connections": len(self.active_connections),
            "total_connections_lifetime": self._connections_total,
            "messages_sent": self._messages_sent,
            "subscriptions": {
                "spot": {
                    "count": spot_subs,
                    "symbols": list(all_spot)
                },
                "ticker": {
                    "count": ticker_subs,
                    "symbols": list(all_ticker)
                },
                "option_chain": {
                    "count": chain_subs,
                    "symbols": list(all_chain)
                }
            },
            "cached_data_keys": list(self.latest_data.keys()),
            "server_time": datetime.now().isoformat()
        }

    def has_subscribers(self, sub_type: SubscriptionType = None) -> bool:
        """Check if there are any subscribers (optionally for a specific type)"""
        if sub_type is None:
            return len(self.active_connections) > 0

        for subs in self.subscriptions.values():
            if subs.get(sub_type, set()):
                return True
        return False


# ==============================================================================
# GLOBAL INSTANCES - Import these in your code
# ==============================================================================

# Global connection manager (singleton)
connection_manager = ConnectionManager()


class MarketDataBroadcaster:
    """
    Background service that fetches market data and broadcasts to connected clients.

    BROADCAST INTERVALS:
    ┌────────────────────┬───────────┬─────────────────────────────────────┐
    │ Subscription Type  │ Interval  │ Data Sent                           │
    ├────────────────────┼───────────┼─────────────────────────────────────┤
    │ ticker             │ 500ms     │ LTP only (minimal bandwidth)        │
    │ spot               │ 1 second  │ Full spot data (LTP, change, OHLC)  │
    │ option_chain       │ 5 seconds │ Full option chain data              │
    └────────────────────┴───────────┴─────────────────────────────────────┘

    OPTIMIZATION:
    - Only fetches data when there are active subscribers
    - Uses cached data from upstox_service (no duplicate API calls)
    - Ticker sends only when price changes (saves bandwidth)
    - Runs multiple concurrent tasks for different intervals
    """

    def __init__(self, manager: ConnectionManager):
        self.manager = manager
        self._is_running = False
        self._tasks: list[asyncio.Task] = []

        # Intervals (in seconds)
        self._ticker_interval = 0.5    # 500ms for fast LTP updates
        self._spot_interval = 1.0      # 1 second for full spot data
        self._chain_interval = 2.0     # 2 seconds for option chain

    async def start(self) -> None:
        """Start all broadcast loops"""
        if self._is_running:
            return

        self._is_running = True

        # Start separate loops for different intervals
        self._tasks = [
            asyncio.create_task(self._ticker_loop()),     # Fast LTP updates
            asyncio.create_task(self._spot_loop()),       # Full spot data
            asyncio.create_task(self._option_chain_loop())  # Option chain data
        ]

        logger.info("Market data broadcaster started with 3 concurrent loops")
        logger.info(f"  - Ticker: every {self._ticker_interval}s")
        logger.info(f"  - Spot: every {self._spot_interval}s")
        logger.info(f"  - Option Chain: every {self._chain_interval}s")

    async def stop(self) -> None:
        """Stop all broadcast loops"""
        self._is_running = False
        for task in self._tasks:
            task.cancel()
            try:
                await task
            except asyncio.CancelledError:
                pass
        self._tasks = []
        logger.info("Market data broadcaster stopped")

    def _get_subscribed_symbols(self, sub_type: SubscriptionType) -> Set[str]:
        """Get all symbols subscribed for a given type"""
        symbols = set()
        for subs in self.manager.subscriptions.values():
            symbols.update(subs.get(sub_type, set()))
        return symbols

    async def _ticker_loop(self) -> None:
        """
        Fast ticker loop (500ms) - sends only LTP when price changes.
        Optimized for minimal bandwidth and latency.
        """
        from .upstox_service import upstox_service

        while self._is_running:
            try:
                # Get symbols subscribed to ticker
                symbols = self._get_subscribed_symbols(SubscriptionType.TICKER)

                if symbols:
                    for symbol in symbols:
                        try:
                            data = await upstox_service.get_spot_price(symbol)
                            if data:
                                current_ltp = data.get("lastPrice", 0)
                                prev_ltp = self.manager._previous_prices.get(symbol, 0)

                                # Only send if price changed
                                if current_ltp != prev_ltp:
                                    self.manager._previous_prices[symbol] = current_ltp
                                    await self.manager.broadcast_ticker(symbol, {
                                        "ltp": current_ltp,
                                        "change": data.get("change", 0),
                                        "pChange": data.get("pChange", 0)
                                    })
                        except Exception as e:
                            logger.error(f"Ticker error for {symbol}: {e}")

                await asyncio.sleep(self._ticker_interval)

            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Ticker loop error: {e}")
                await asyncio.sleep(1)

    async def _spot_loop(self) -> None:
        """
        Spot price loop (1 second) - sends full spot data.
        Includes LTP, change, percentage change, OHLC, etc.
        """
        from .upstox_service import upstox_service

        while self._is_running:
            try:
                # Get symbols subscribed to spot
                symbols = self._get_subscribed_symbols(SubscriptionType.SPOT)

                if symbols:
                    for symbol in symbols:
                        try:
                            data = await upstox_service.get_spot_price(symbol)
                            if data:
                                await self.manager.broadcast_spot_price(symbol, data)
                        except Exception as e:
                            logger.error(f"Spot error for {symbol}: {e}")

                await asyncio.sleep(self._spot_interval)

            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Spot loop error: {e}")
                await asyncio.sleep(2)

    async def _option_chain_loop(self) -> None:
        """
        Option chain loop (5 seconds) - sends full option chain data.
        Heavier payload, so less frequent updates.
        """
        from .upstox_service import upstox_service

        while self._is_running:
            try:
                # Get symbols subscribed to option chain
                symbols = self._get_subscribed_symbols(SubscriptionType.OPTION_CHAIN)

                if symbols:
                    for symbol in symbols:
                        try:
                            # Gather requested expiries for this symbol (per connection)
                            requested_expiries = set()
                            for ws, mapping in self.manager.option_chain_expiries.items():
                                if symbol in mapping:
                                    requested_expiries.add(mapping[symbol])

                            # Fallback to first expiry if none requested
                            if not requested_expiries:
                                expiries = await upstox_service.get_expiry_dates(symbol)
                                if expiries:
                                    requested_expiries.add(expiries[0])

                            for expiry in requested_expiries:
                                data = await upstox_service.get_option_chain(symbol, expiry)
                                if data:
                                    await self.manager.broadcast_option_chain(symbol, expiry, data)
                        except Exception as e:
                            logger.error(f"Option chain error for {symbol}: {e}")

                await asyncio.sleep(self._chain_interval)

            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Option chain loop error: {e}")
                await asyncio.sleep(5)


# Global broadcaster
broadcaster = MarketDataBroadcaster(connection_manager)
