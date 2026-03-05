"""
Greeks Service - Black-Scholes option pricing and IV computation.

Provides implied volatility calculation and all Greeks (Delta, Gamma, Theta, Vega)
for use across the application — option chain enrichment, AI insights, etc.
"""

import math
import logging
from typing import Optional, Dict, List, Any
from datetime import datetime

from scipy import stats
from scipy.optimize import brentq

logger = logging.getLogger(__name__)

# RBI repo rate (approximate risk-free rate for India)
RISK_FREE_RATE = 0.065  # 6.5% annual


class BlackScholes:
    """Black-Scholes option pricing model with IV solver."""

    @staticmethod
    def d1(S: float, K: float, T: float, r: float, sigma: float) -> float:
        if T <= 0 or sigma <= 0:
            return 0.0
        return (math.log(S / K) + (r + 0.5 * sigma ** 2) * T) / (sigma * math.sqrt(T))

    @staticmethod
    def d2(S: float, K: float, T: float, r: float, sigma: float) -> float:
        if T <= 0 or sigma <= 0:
            return 0.0
        return BlackScholes.d1(S, K, T, r, sigma) - sigma * math.sqrt(T)

    @staticmethod
    def call_price(S: float, K: float, T: float, r: float, sigma: float) -> float:
        if T <= 0:
            return max(0, S - K)
        if sigma <= 0:
            return max(0, S - K * math.exp(-r * T))
        d1 = BlackScholes.d1(S, K, T, r, sigma)
        d2 = BlackScholes.d2(S, K, T, r, sigma)
        return S * stats.norm.cdf(d1) - K * math.exp(-r * T) * stats.norm.cdf(d2)

    @staticmethod
    def put_price(S: float, K: float, T: float, r: float, sigma: float) -> float:
        if T <= 0:
            return max(0, K - S)
        if sigma <= 0:
            return max(0, K * math.exp(-r * T) - S)
        d1 = BlackScholes.d1(S, K, T, r, sigma)
        d2 = BlackScholes.d2(S, K, T, r, sigma)
        return K * math.exp(-r * T) * stats.norm.cdf(-d2) - S * stats.norm.cdf(-d1)

    @staticmethod
    def implied_volatility(
        market_price: float,
        S: float,
        K: float,
        T: float,
        r: float,
        option_type: str,
    ) -> Optional[float]:
        """Calculate implied volatility using Brent's method.

        Returns IV as a decimal (e.g., 0.20 for 20%) or None if unsolvable.
        """
        if T <= 0 or market_price <= 0 or S <= 0 or K <= 0:
            return None

        is_call = option_type.upper() in ("CE", "CALL")
        intrinsic = max(0, S - K) if is_call else max(0, K - S)

        # Price below intrinsic — can't solve for IV
        if market_price < intrinsic * 0.95:
            return None

        price_func = BlackScholes.call_price if is_call else BlackScholes.put_price

        def objective(sigma):
            return price_func(S, K, T, r, sigma) - market_price

        try:
            iv = brentq(objective, 0.01, 5.0, xtol=1e-6, maxiter=100)
            return iv
        except (ValueError, RuntimeError):
            return None

    @staticmethod
    def delta(S: float, K: float, T: float, r: float, sigma: float, option_type: str) -> float:
        if T <= 0 or sigma <= 0:
            if option_type.upper() in ("CE", "CALL"):
                return 1.0 if S > K else 0.0
            else:
                return -1.0 if S < K else 0.0
        d1 = BlackScholes.d1(S, K, T, r, sigma)
        if option_type.upper() in ("CE", "CALL"):
            return stats.norm.cdf(d1)
        else:
            return stats.norm.cdf(d1) - 1

    @staticmethod
    def gamma(S: float, K: float, T: float, r: float, sigma: float) -> float:
        if T <= 0 or sigma <= 0:
            return 0.0
        d1 = BlackScholes.d1(S, K, T, r, sigma)
        return stats.norm.pdf(d1) / (S * sigma * math.sqrt(T))

    @staticmethod
    def theta(S: float, K: float, T: float, r: float, sigma: float, option_type: str) -> float:
        """Per-day theta."""
        if T <= 0 or sigma <= 0:
            return 0.0
        d1 = BlackScholes.d1(S, K, T, r, sigma)
        d2 = BlackScholes.d2(S, K, T, r, sigma)
        term1 = -(S * stats.norm.pdf(d1) * sigma) / (2 * math.sqrt(T))
        if option_type.upper() in ("CE", "CALL"):
            term2 = -r * K * math.exp(-r * T) * stats.norm.cdf(d2)
        else:
            term2 = r * K * math.exp(-r * T) * stats.norm.cdf(-d2)
        return (term1 + term2) / 365

    @staticmethod
    def vega(S: float, K: float, T: float, r: float, sigma: float) -> float:
        """Vega per 1% move in IV."""
        if T <= 0 or sigma <= 0:
            return 0.0
        d1 = BlackScholes.d1(S, K, T, r, sigma)
        return S * math.sqrt(T) * stats.norm.pdf(d1) / 100


def compute_greeks(
    market_price: float,
    spot: float,
    strike: float,
    T: float,
    option_type: str,
    r: float = RISK_FREE_RATE,
) -> Dict[str, Optional[float]]:
    """Compute IV and all Greeks for a single option.

    Args:
        market_price: Current option LTP
        spot: Underlying spot price
        strike: Option strike price
        T: Time to expiry in years
        option_type: "CE" or "PE"
        r: Risk-free rate (default: RBI repo rate)

    Returns:
        Dict with iv, delta, gamma, theta, vega (all may be None if unsolvable)
    """
    iv = BlackScholes.implied_volatility(market_price, spot, strike, T, r, option_type)

    if iv is None:
        return {"iv": None, "delta": None, "gamma": None, "theta": None, "vega": None}

    return {
        "iv": round(iv, 4),
        "delta": round(BlackScholes.delta(spot, strike, T, r, iv, option_type), 4),
        "gamma": round(BlackScholes.gamma(spot, strike, T, r, iv), 6),
        "theta": round(BlackScholes.theta(spot, strike, T, r, iv, option_type), 4),
        "vega": round(BlackScholes.vega(spot, strike, T, r, iv), 4),
    }


def enrich_option_chain(
    chain_data: List[Dict[str, Any]],
    spot_price: float,
    expiry_str: str,
) -> List[Dict[str, Any]]:
    """Enrich option chain rows with computed IV and Greeks.

    Modifies each row's CE/PE dicts in-place by adding:
    - impliedVolatility (computed from BS if market price available)
    - delta, gamma, theta, vega

    Args:
        chain_data: List of option chain rows (each with strikePrice, CE, PE)
        spot_price: Current underlying spot price
        expiry_str: Expiry date string (e.g., "13-Feb-2025" or "2025-02-13")
    """
    # Parse expiry date
    expiry_dt = None
    for fmt in ("%d-%b-%Y", "%Y-%m-%d", "%d-%B-%Y"):
        try:
            expiry_dt = datetime.strptime(expiry_str, fmt)
            break
        except ValueError:
            continue

    if expiry_dt is None:
        logger.warning(f"Could not parse expiry date: {expiry_str}, skipping Greeks")
        return chain_data

    now = datetime.now()
    days_to_expiry = max(0, (expiry_dt - now).days)
    T = max(days_to_expiry, 1) / 365.0  # At least 1 day to avoid division by zero

    enriched_count = 0

    for row in chain_data:
        strike = row.get("strikePrice", 0)
        if strike <= 0:
            continue

        for opt_type in ("CE", "PE"):
            opt = row.get(opt_type)
            if not opt:
                continue

            ltp = opt.get("lastPrice", 0) or 0
            if ltp <= 0:
                continue

            greeks = compute_greeks(ltp, spot_price, strike, T, opt_type)

            if greeks["iv"] is not None:
                opt["impliedVolatility"] = round(greeks["iv"] * 100, 2)  # Store as percentage
                opt["delta"] = greeks["delta"]
                opt["gamma"] = greeks["gamma"]
                opt["theta"] = greeks["theta"]
                opt["vega"] = greeks["vega"]
                enriched_count += 1

    logger.info(f"Enriched {enriched_count} options with IV and Greeks (T={T:.4f}y, {days_to_expiry}d)")
    return chain_data
