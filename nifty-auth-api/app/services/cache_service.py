"""
================================================================================
CACHE SERVICE - Abstraction Layer for Caching
================================================================================

This service provides a unified caching interface that can switch between
different backends (Memory or Redis) based on configuration.

CURRENT STATUS: Using in-memory cache (perfect for single server)

================================================================================
HOW TO USE
================================================================================

    from app.services.cache_service import cache

    # Store data with 5 second TTL
    await cache.set("spot:NIFTY", {"price": 25000}, ttl=5)

    # Retrieve data (returns None if expired or not found)
    data = await cache.get("spot:NIFTY")

    # Delete specific key
    await cache.delete("spot:NIFTY")

    # Clear all cache
    await cache.clear()

================================================================================
WHEN TO SWITCH TO REDIS
================================================================================

Switch to Redis when:
  - Running multiple server instances (load balanced)
  - Need cache to survive server restarts
  - Memory usage on server is too high
  - Concurrent users > 1000

================================================================================
HOW TO MIGRATE TO REDIS (5 minutes)
================================================================================

Step 1: Install Redis library
    pip install redis

Step 2: Set up Redis server (choose one):
    - AWS ElastiCache (~$15/month)
    - DigitalOcean Managed Redis (~$15/month)
    - Self-hosted: docker run -p 6379:6379 redis

Step 3: Update .env file:
    CACHE_BACKEND=redis
    REDIS_URL=redis://your-redis-host:6379

Step 4: Restart server - Done! No code changes needed.

================================================================================
ARCHITECTURE COMPARISON
================================================================================

MEMORY CACHE (Current - Single Server):
┌─────────────────────────────────────┐
│            Server                   │
│  ┌─────────┐      ┌─────────────┐   │
│  │  API    │ ───► │ Memory      │   │
│  │ Request │      │ Cache       │   │
│  └─────────┘      │ (In-Process)│   │
│                   └─────────────┘   │
└─────────────────────────────────────┘
Pros: Zero latency, no extra cost
Cons: Lost on restart, can't share between servers

REDIS CACHE (Scaled - Multiple Servers):
┌──────────┐  ┌──────────┐  ┌──────────┐
│ Server 1 │  │ Server 2 │  │ Server 3 │
└────┬─────┘  └────┬─────┘  └────┬─────┘
     │             │             │
     └─────────────┼─────────────┘
                   ▼
          ┌───────────────┐
          │     Redis     │
          │ (Shared Cache)│
          └───────────────┘
Pros: Shared across servers, survives restarts
Cons: Network latency (~1ms), extra cost

================================================================================
"""

import time
import json
from typing import Optional, Any, Dict
from abc import ABC, abstractmethod

from app.config import settings


# ==============================================================================
# ABSTRACT BASE CLASS - Defines the interface all cache backends must implement
# ==============================================================================

class CacheBackend(ABC):
    """
    Abstract base class for cache backends.

    Any new cache backend (Memory, Redis, Memcached, etc.) must implement
    these four methods to work with the CacheService.
    """

    @abstractmethod
    async def get(self, key: str) -> Optional[Any]:
        """
        Retrieve a value from cache.

        Args:
            key: The cache key to look up

        Returns:
            The cached value if found and not expired, None otherwise
        """
        pass

    @abstractmethod
    async def set(self, key: str, value: Any, ttl: int = 60) -> None:
        """
        Store a value in cache with expiration.

        Args:
            key: The cache key
            value: The value to store (will be JSON serialized for Redis)
            ttl: Time-to-live in seconds (default: 60)
        """
        pass

    @abstractmethod
    async def delete(self, key: str) -> None:
        """
        Delete a specific key from cache.

        Args:
            key: The cache key to delete
        """
        pass

    @abstractmethod
    async def clear(self) -> None:
        """
        Clear all cached data.

        Warning: In Redis mode, only clears keys with 'optix:' prefix
        to avoid affecting other applications sharing the same Redis.
        """
        pass


# ==============================================================================
# MEMORY CACHE BACKEND - For single server deployment (Current)
# ==============================================================================

class MemoryCache(CacheBackend):
    """
    In-memory cache implementation using Python dictionary.

    Best for:
        - Single server deployments
        - Development and testing
        - Low to medium traffic (< 1000 concurrent users)

    Limitations:
        - Cache is lost when server restarts
        - Cannot be shared between multiple server instances
        - Uses server RAM (monitor memory usage)

    Example:
        cache = MemoryCache()
        await cache.set("user:123", {"name": "John"}, ttl=300)
        user = await cache.get("user:123")  # Returns {"name": "John"}
    """

    def __init__(self):
        # Dictionary to store cached items
        # Structure: { "key": { "data": <value>, "expires_at": <timestamp> } }
        self._cache: Dict[str, Dict[str, Any]] = {}

    async def get(self, key: str) -> Optional[Any]:
        """
        Get value from memory cache.

        Automatically removes expired entries when accessed (lazy expiration).
        """
        if key in self._cache:
            cached = self._cache[key]

            # Check if still valid
            if time.time() < cached["expires_at"]:
                return cached["data"]
            else:
                # Expired - remove from cache (lazy cleanup)
                del self._cache[key]

        return None

    async def set(self, key: str, value: Any, ttl: int = 60) -> None:
        """
        Store value in memory cache with TTL.

        Args:
            key: Cache key (e.g., "spot:NIFTY", "chain:BANKNIFTY:05-Feb-2026")
            value: Any Python object (dict, list, str, etc.)
            ttl: Seconds until expiration (default: 60)
        """
        self._cache[key] = {
            "data": value,
            "expires_at": time.time() + ttl
        }

    async def delete(self, key: str) -> None:
        """Remove a specific key from cache."""
        self._cache.pop(key, None)  # pop with default avoids KeyError

    async def clear(self) -> None:
        """Clear entire cache."""
        self._cache.clear()


# ==============================================================================
# REDIS CACHE BACKEND - For multi-server deployment (Future)
# ==============================================================================

class RedisCache(CacheBackend):
    """
    Redis cache implementation for distributed systems.

    Best for:
        - Multiple server instances (load balanced)
        - High traffic applications (> 1000 concurrent users)
        - When cache must survive server restarts
        - Microservices architecture

    Requirements:
        - pip install redis
        - Running Redis server
        - REDIS_URL in environment

    Key Prefix:
        All keys are prefixed with 'optix:' to namespace our cache
        and avoid conflicts with other applications using same Redis.

    Example:
        cache = RedisCache("redis://localhost:6379")
        await cache.set("user:123", {"name": "John"}, ttl=300)
        # Stored in Redis as: optix:user:123
    """

    def __init__(self, redis_url: str):
        """
        Initialize Redis cache.

        Args:
            redis_url: Redis connection URL (e.g., "redis://localhost:6379")
        """
        self._redis_url = redis_url
        self._redis = None  # Lazy connection - only connect when first used

    async def _get_client(self):
        """
        Get or create Redis client connection.

        Uses lazy initialization - connection is only established
        when first cache operation is performed.
        """
        if self._redis is None:
            # Import here to avoid error if redis not installed
            import redis.asyncio as redis
            self._redis = await redis.from_url(
                self._redis_url,
                encoding="utf-8",
                decode_responses=True  # Auto-decode bytes to strings
            )
        return self._redis

    async def get(self, key: str) -> Optional[Any]:
        """
        Get value from Redis.

        Redis handles expiration automatically (TTL),
        so no need to check timestamps like in MemoryCache.
        """
        try:
            client = await self._get_client()
            data = await client.get(f"optix:{key}")

            # Redis returns None for missing/expired keys
            if data is None:
                return None

            # Deserialize JSON string back to Python object
            return json.loads(data)

        except Exception as e:
            # Log error but don't crash - cache miss is not fatal
            print(f"[CACHE] Redis GET error for '{key}': {e}")
            return None

    async def set(self, key: str, value: Any, ttl: int = 60) -> None:
        """
        Store value in Redis with automatic expiration.

        Args:
            key: Cache key (will be prefixed with 'optix:')
            value: Any JSON-serializable Python object
            ttl: Seconds until Redis automatically deletes the key
        """
        try:
            client = await self._get_client()

            # Serialize Python object to JSON string
            json_value = json.dumps(value)

            # SETEX = SET with EXpiration (atomic operation)
            await client.setex(
                f"optix:{key}",  # Namespaced key
                ttl,             # Expiration in seconds
                json_value       # JSON string value
            )

        except Exception as e:
            # Log error but don't crash - failed cache write is not fatal
            print(f"[CACHE] Redis SET error for '{key}': {e}")

    async def delete(self, key: str) -> None:
        """Delete a specific key from Redis."""
        try:
            client = await self._get_client()
            await client.delete(f"optix:{key}")
        except Exception as e:
            print(f"[CACHE] Redis DELETE error for '{key}': {e}")

    async def clear(self) -> None:
        """
        Clear all optix cache keys from Redis.

        Only deletes keys with 'optix:' prefix to avoid
        affecting other applications sharing the same Redis.
        """
        try:
            client = await self._get_client()

            # Find all keys with our prefix
            keys = await client.keys("optix:*")

            if keys:
                # Delete all found keys in one operation
                await client.delete(*keys)
                print(f"[CACHE] Cleared {len(keys)} keys from Redis")

        except Exception as e:
            print(f"[CACHE] Redis CLEAR error: {e}")


# ==============================================================================
# CACHE SERVICE - Main interface (auto-selects backend based on config)
# ==============================================================================

class CacheService:
    """
    Main cache service that automatically selects the appropriate backend.

    This is the class you should use in your application code.
    It reads the CACHE_BACKEND setting and creates the appropriate backend.

    Configuration (.env):
        CACHE_BACKEND=memory  # Use in-memory cache (default)
        CACHE_BACKEND=redis   # Use Redis cache
        REDIS_URL=redis://localhost:6379  # Required for Redis

    Usage:
        from app.services.cache_service import cache

        # These work the same regardless of backend
        await cache.set("spot:NIFTY", data, ttl=5)
        data = await cache.get("spot:NIFTY")
        await cache.delete("spot:NIFTY")
        await cache.clear()

    Switching Backends:
        Just change CACHE_BACKEND in .env and restart.
        No code changes required!
    """

    def __init__(self):
        # Backend is created lazily on first use
        self._backend: Optional[CacheBackend] = None

    def _get_backend(self) -> CacheBackend:
        """
        Get or create the cache backend based on configuration.

        Checks settings.cache_backend to determine which backend to use:
        - "memory" (default): Uses MemoryCache
        - "redis": Uses RedisCache (requires REDIS_URL)
        """
        if self._backend is None:
            # Read configuration
            redis_url = getattr(settings, 'redis_url', None)
            cache_backend = getattr(settings, 'cache_backend', 'memory')

            # Select backend based on config
            if cache_backend == 'redis' and redis_url:
                print("[CACHE] Initializing Redis backend")
                print(f"[CACHE] Redis URL: {redis_url}")
                self._backend = RedisCache(redis_url)
            else:
                print("[CACHE] Initializing in-memory backend")
                if cache_backend == 'redis':
                    print("[CACHE] Warning: Redis requested but REDIS_URL not set, falling back to memory")
                self._backend = MemoryCache()

        return self._backend

    async def get(self, key: str) -> Optional[Any]:
        """
        Get a value from cache.

        Args:
            key: Cache key to look up

        Returns:
            Cached value or None if not found/expired

        Example:
            spot_data = await cache.get("spot:NIFTY")
            if spot_data:
                return spot_data  # Cache hit!
            else:
                # Cache miss - fetch from API
                spot_data = await fetch_from_upstox()
                await cache.set("spot:NIFTY", spot_data, ttl=5)
                return spot_data
        """
        return await self._get_backend().get(key)

    async def set(self, key: str, value: Any, ttl: int = 60) -> None:
        """
        Store a value in cache.

        Args:
            key: Cache key (use namespacing like "spot:NIFTY", "chain:BANKNIFTY")
            value: Any JSON-serializable value
            ttl: Time-to-live in seconds (default: 60)

        Common TTL values:
            - Spot prices: 2-5 seconds (real-time data)
            - Option chain: 5-10 seconds (semi-real-time)
            - Expiry dates: 300 seconds (changes rarely)
            - User sessions: 900 seconds (15 minutes)
        """
        await self._get_backend().set(key, value, ttl)

    async def delete(self, key: str) -> None:
        """
        Delete a specific key from cache.

        Use this when you know data has changed and cache is stale.

        Example:
            # User updated their profile - invalidate cache
            await cache.delete(f"user:{user_id}")
        """
        await self._get_backend().delete(key)

    async def clear(self) -> None:
        """
        Clear all cached data.

        Use sparingly - usually only needed for:
        - Testing/development
        - Major data changes
        - Deployment of new version

        In production, prefer targeted cache.delete() calls.
        """
        await self._get_backend().clear()


# ==============================================================================
# SINGLETON INSTANCE - Import this in your code
# ==============================================================================

# Create a single instance to be shared across the application
# This ensures all parts of the app use the same cache
cache = CacheService()


# ==============================================================================
# USAGE EXAMPLES (for reference)
# ==============================================================================
"""
# Example 1: Caching spot prices in upstox_service.py
# --------------------------------------------------

async def get_spot_price(self, symbol: str) -> Dict[str, Any]:
    # Try cache first
    cache_key = f"spot:{symbol}"
    cached = await cache.get(cache_key)

    if cached:
        return cached  # Return cached data (fast!)

    # Cache miss - fetch from Upstox API
    data = await self._fetch_from_upstox(symbol)

    # Store in cache for next request (2 second TTL for live data)
    await cache.set(cache_key, data, ttl=2)

    return data


# Example 2: Caching option chain
# --------------------------------

async def get_option_chain(self, symbol: str, expiry: str) -> Dict[str, Any]:
    cache_key = f"chain:{symbol}:{expiry}"
    cached = await cache.get(cache_key)

    if cached:
        return cached

    data = await self._fetch_option_chain(symbol, expiry)
    await cache.set(cache_key, data, ttl=5)  # 5 seconds for option chain

    return data


# Example 3: Invalidating cache on update
# ---------------------------------------

async def update_user_settings(user_id: str, new_settings: dict):
    # Update in database
    await db.update_user(user_id, new_settings)

    # Invalidate cache so next read gets fresh data
    await cache.delete(f"user:{user_id}")
    await cache.delete(f"user_settings:{user_id}")
"""
