"""
Market Data Router - API endpoints for option chain and market data
Uses Upstox API for live data with fallback to demo data
"""

import logging
import math
from datetime import datetime, time as dt_time, timedelta
from typing import Optional
from zoneinfo import ZoneInfo

from fastapi import APIRouter, HTTPException, Query
from fastapi.responses import RedirectResponse
from scipy import stats

from ..services.upstox_service import upstox_service
from ..services.cache_service import cache
from ..schemas.market import (
    SpotPriceResponse,
    ExpiryDatesResponse,
    SupportedIndicesResponse,
    MarketStatusResponse,
    GreeksRequest,
    GreeksResponse,
)

logger = logging.getLogger(__name__)
IST = ZoneInfo("Asia/Kolkata")

router = APIRouter(prefix="/api/v1/market", tags=["Market Data"])


# ==================== Upstox Authentication ====================

@router.get("/upstox/login")
async def upstox_login():
    """
    Get Upstox OAuth login URL.
    Redirect user to this URL to authenticate with Upstox.
    """
    login_url = upstox_service.get_login_url()
    return {"loginUrl": login_url}


@router.get("/upstox/callback")
async def upstox_callback(code: str = Query(..., description="Authorization code from Upstox")):
    """
    OAuth callback endpoint for Upstox.
    Exchanges authorization code for access token.
    Redirects back to admin dashboard with status.
    """
    success, error_msg = await upstox_service.exchange_code_for_token(code)

    if success:
        # Redirect to admin dashboard with success parameter
        return RedirectResponse(url="/admin/dashboard?auth=success", status_code=302)
    else:
        # Redirect to admin dashboard with error parameter
        error_encoded = error_msg.replace(" ", "+") if error_msg else "unknown"
        return RedirectResponse(url=f"/admin/dashboard?auth=error&msg={error_encoded}", status_code=302)


@router.post("/upstox/disconnect")
async def disconnect_upstox():
    """
    Disconnect from Upstox (clear the access token).
    """
    upstox_service.disconnect()
    return {
        "status": "success",
        "message": "Disconnected from Upstox. Using demo data.",
        "isAuthenticated": False
    }


@router.post("/upstox/save-token")
async def save_upstox_token():
    """Save the current token to file for persistence across restarts"""
    if upstox_service.is_authenticated():
        upstox_service._save_token()
        return {"status": "success", "message": "Token saved to file"}
    return {"status": "error", "message": "No token to save"}


@router.post("/upstox/set-token")
async def set_upstox_token(token: str = Query(..., description="Upstox access token")):
    """
    Manually set Upstox access token.
    Use this if you already have a valid access token.
    """
    upstox_service.set_access_token(token)
    return {
        "status": "success",
        "message": "Token set successfully. Live data is now available.",
        "isAuthenticated": True
    }


@router.get("/upstox/status")
async def upstox_status():
    """Check Upstox authentication status with token expiry info"""
    from ..config import settings
    token_status = upstox_service.get_token_status()
    return {
        "isAuthenticated": upstox_service.is_authenticated(),
        "dataSource": "upstox_live" if upstox_service.is_authenticated() else "redis_cache",
        "configuredRedirectUri": settings.upstox_redirect_uri,
        "hasApiKey": bool(settings.upstox_api_key),
        "hasApiSecret": bool(settings.upstox_api_secret),
        "token": token_status
    }


@router.get("/upstox/debug-callback")
async def upstox_debug_callback(code: str = Query(..., description="Authorization code from Upstox")):
    """
    Debug version of callback - returns JSON instead of redirect.
    Use this to see the actual error message if token exchange fails.
    """
    success, error_msg = await upstox_service.exchange_code_for_token(code)

    return {
        "success": success,
        "error": error_msg if not success else None,
        "isAuthenticated": upstox_service.is_authenticated(),
        "message": "Token obtained successfully! Live data now available." if success else f"Token exchange failed: {error_msg}"
    }


# ==================== Market Data Endpoints ====================

@router.get("/indices", response_model=SupportedIndicesResponse)
async def get_supported_indices():
    """Get list of supported indices"""
    return {
        "indices": [
            {"symbol": symbol, **info}
            for symbol, info in upstox_service.INDICES.items()
        ]
    }


@router.get("/status", response_model=MarketStatusResponse)
async def get_market_status():
    """Check if market is currently open"""
    now = datetime.now(IST)
    weekday = now.weekday()
    current_time = now.time()

    # Market hours: 9:15 AM to 3:30 PM IST, Monday to Friday
    market_open = dt_time(9, 15)
    market_close = dt_time(15, 30)

    is_weekday = weekday < 5
    is_market_hours = market_open <= current_time <= market_close

    is_open = is_weekday and is_market_hours

    if is_open:
        message = "Market is open"
        next_open = None
    elif not is_weekday:
        message = "Market closed - Weekend"
        # Next Monday
        days_until_monday = 7 - weekday
        next_date = now.date() + timedelta(days=days_until_monday)
        next_open = f"{next_date} 09:15:00"
    elif current_time < market_open:
        message = "Market opens at 9:15 AM"
        next_open = f"{now.date()} 09:15:00"
    else:
        message = "Market closed for the day"
        next_date = now.date() + timedelta(days=1)
        if next_date.weekday() >= 5:
            days_until_monday = 7 - next_date.weekday()
            next_date = next_date + timedelta(days=days_until_monday)
        next_open = f"{next_date} 09:15:00"

    return {
        "isOpen": is_open,
        "message": message,
        "nextOpen": next_open,
        "timestamp": now.isoformat(),
        "dataSource": "upstox_live" if upstox_service.is_authenticated() else "demo"
    }


@router.get("/spot/{symbol}", response_model=SpotPriceResponse)
async def get_spot_price(symbol: str):
    """
    Get current spot price for an index

    - **symbol**: Index symbol (NIFTY, BANKNIFTY, FINNIFTY, MIDCPNIFTY, SENSEX)

    Returns live data if Upstox is authenticated, otherwise returns demo data.
    """
    try:
        return await upstox_service.get_spot_price(symbol)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        logger.error(f"Failed to fetch data: {e}")
        raise HTTPException(status_code=503, detail="Failed to fetch market data")


@router.get("/expiries/{symbol}", response_model=ExpiryDatesResponse)
async def get_expiry_dates(symbol: str):
    """
    Get available expiry dates for an index

    - **symbol**: Index symbol (NIFTY, BANKNIFTY, FINNIFTY, MIDCPNIFTY, SENSEX)

    Returns live data if Upstox is authenticated, otherwise returns demo data.
    """
    try:
        expiry_dates = await upstox_service.get_expiry_dates(symbol)
        return {
            "symbol": symbol.upper(),
            "expiryDates": expiry_dates
        }
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        logger.error(f"Failed to fetch data: {e}")
        raise HTTPException(status_code=503, detail="Failed to fetch market data")


@router.get("/option-chain/{symbol}")
async def get_option_chain(
    symbol: str,
    expiry: Optional[str] = Query(None, description="Filter by expiry date (e.g., 30-Jan-2025)"),
    strikes: int = Query(50, ge=1, le=100, description="Number of strikes around ATM (max 100)")
):
    """
    Get option chain data for an index

    - **symbol**: Index symbol (NIFTY, BANKNIFTY, FINNIFTY, MIDCPNIFTY, SENSEX)
    - **expiry**: Expiry date (e.g., 05-Feb-2026)
    - **strikes**: Number of strikes to show around ATM (default: 15)

    Returns live data if Upstox is authenticated, otherwise returns demo data.
    """
    try:
        # If no expiry provided, get the first available expiry
        if not expiry:
            expiries = await upstox_service.get_expiry_dates(symbol)
            if expiries:
                expiry = expiries[0]
            else:
                raise ValueError("No expiry dates available")

        data = await upstox_service.get_option_chain(
            symbol,
            expiry,
            strikes_around_atm=strikes
        )

        return data

    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        logger.error(f"Failed to fetch data: {e}")
        raise HTTPException(status_code=503, detail="Failed to fetch market data")


@router.post("/calculate-greeks", response_model=GreeksResponse)
async def calculate_greeks(request: GreeksRequest):
    """
    Calculate option Greeks using Black-Scholes model

    - **spotPrice**: Current underlying price
    - **strikePrice**: Option strike price
    - **timeToExpiry**: Time to expiry in years (e.g., 7 days = 7/365)
    - **volatility**: Implied volatility in percentage (e.g., 15 for 15%)
    - **riskFreeRate**: Risk-free rate in percentage (default: 6.5%)
    - **optionType**: CE/CALL or PE/PUT
    """
    S = request.spotPrice
    K = request.strikePrice
    T = request.timeToExpiry
    sigma = request.volatility / 100  # Convert to decimal
    r = request.riskFreeRate / 100  # Convert to decimal
    is_call = request.optionType.upper() in ["CE", "CALL"]

    # Black-Scholes calculations
    try:
        d1 = (math.log(S / K) + (r + 0.5 * sigma ** 2) * T) / (sigma * math.sqrt(T))
        d2 = d1 - sigma * math.sqrt(T)

        # Option price
        if is_call:
            price = S * stats.norm.cdf(d1) - K * math.exp(-r * T) * stats.norm.cdf(d2)
            delta = stats.norm.cdf(d1)
            rho = K * T * math.exp(-r * T) * stats.norm.cdf(d2) / 100
        else:
            price = K * math.exp(-r * T) * stats.norm.cdf(-d2) - S * stats.norm.cdf(-d1)
            delta = stats.norm.cdf(d1) - 1
            rho = -K * T * math.exp(-r * T) * stats.norm.cdf(-d2) / 100

        # Greeks
        gamma = stats.norm.pdf(d1) / (S * sigma * math.sqrt(T))
        theta = (-(S * stats.norm.pdf(d1) * sigma) / (2 * math.sqrt(T))
                 - r * K * math.exp(-r * T) * (stats.norm.cdf(d2) if is_call else stats.norm.cdf(-d2))) / 365
        vega = S * stats.norm.pdf(d1) * math.sqrt(T) / 100

        # Intrinsic and time value
        if is_call:
            intrinsic = max(0, S - K)
        else:
            intrinsic = max(0, K - S)

        time_value = max(0, price - intrinsic)

        # Moneyness
        if is_call:
            if S > K:
                moneyness = "ITM"
            elif S < K:
                moneyness = "OTM"
            else:
                moneyness = "ATM"
        else:
            if S < K:
                moneyness = "ITM"
            elif S > K:
                moneyness = "OTM"
            else:
                moneyness = "ATM"

        return {
            "theoreticalPrice": round(price, 2),
            "intrinsicValue": round(intrinsic, 2),
            "timeValue": round(time_value, 2),
            "delta": round(delta, 4),
            "gamma": round(gamma, 6),
            "theta": round(theta, 4),
            "vega": round(vega, 4),
            "rho": round(rho, 4),
            "moneyness": moneyness
        }

    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Calculation error: {str(e)}")


@router.post("/clear-cache")
async def clear_cache():
    """Clear the market data cache"""
    await cache.clear()
    return {"message": "Cache cleared successfully"}
