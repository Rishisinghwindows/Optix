"""
Greeks Calculator for historical options data.

Uses Black-Scholes model to calculate implied volatility and Greeks
for historical options data.
"""

import asyncio
import math
from datetime import date, timedelta
from decimal import Decimal
from typing import Optional, Dict, Any, List
from pathlib import Path
from scipy import stats
from scipy.optimize import brentq

import sys
sys.path.insert(0, str(Path(__file__).parent.parent.parent))

from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from sqlalchemy import select, and_, update

from app.config import settings
from app.models.backtest import HistoricalOption, HistoricalSpotPrice


class BlackScholes:
    """Black-Scholes option pricing model."""

    @staticmethod
    def d1(S: float, K: float, T: float, r: float, sigma: float) -> float:
        """Calculate d1 parameter."""
        if T <= 0 or sigma <= 0:
            return 0
        return (math.log(S / K) + (r + 0.5 * sigma ** 2) * T) / (sigma * math.sqrt(T))

    @staticmethod
    def d2(S: float, K: float, T: float, r: float, sigma: float) -> float:
        """Calculate d2 parameter."""
        if T <= 0 or sigma <= 0:
            return 0
        return BlackScholes.d1(S, K, T, r, sigma) - sigma * math.sqrt(T)

    @staticmethod
    def call_price(S: float, K: float, T: float, r: float, sigma: float) -> float:
        """Calculate call option price."""
        if T <= 0:
            return max(0, S - K)
        if sigma <= 0:
            return max(0, S - K * math.exp(-r * T))

        d1 = BlackScholes.d1(S, K, T, r, sigma)
        d2 = BlackScholes.d2(S, K, T, r, sigma)

        return S * stats.norm.cdf(d1) - K * math.exp(-r * T) * stats.norm.cdf(d2)

    @staticmethod
    def put_price(S: float, K: float, T: float, r: float, sigma: float) -> float:
        """Calculate put option price."""
        if T <= 0:
            return max(0, K - S)
        if sigma <= 0:
            return max(0, K * math.exp(-r * T) - S)

        d1 = BlackScholes.d1(S, K, T, r, sigma)
        d2 = BlackScholes.d2(S, K, T, r, sigma)

        return K * math.exp(-r * T) * stats.norm.cdf(-d2) - S * stats.norm.cdf(-d1)

    @staticmethod
    def implied_volatility(
        option_price: float,
        S: float,
        K: float,
        T: float,
        r: float,
        option_type: str,
    ) -> Optional[float]:
        """Calculate implied volatility using Brent's method."""
        if T <= 0 or option_price <= 0:
            return None

        # Intrinsic value check
        if option_type.upper() == "CE":
            intrinsic = max(0, S - K)
            price_func = BlackScholes.call_price
        else:
            intrinsic = max(0, K - S)
            price_func = BlackScholes.put_price

        if option_price < intrinsic * 0.99:
            return None

        def objective(sigma):
            return price_func(S, K, T, r, sigma) - option_price

        try:
            # Search between 1% and 500% volatility
            iv = brentq(objective, 0.01, 5.0, xtol=1e-6)
            return iv
        except (ValueError, RuntimeError):
            return None

    @staticmethod
    def delta(S: float, K: float, T: float, r: float, sigma: float, option_type: str) -> float:
        """Calculate option delta."""
        if T <= 0 or sigma <= 0:
            if option_type.upper() == "CE":
                return 1.0 if S > K else 0.0
            else:
                return -1.0 if S < K else 0.0

        d1 = BlackScholes.d1(S, K, T, r, sigma)

        if option_type.upper() == "CE":
            return stats.norm.cdf(d1)
        else:
            return stats.norm.cdf(d1) - 1

    @staticmethod
    def gamma(S: float, K: float, T: float, r: float, sigma: float) -> float:
        """Calculate option gamma (same for calls and puts)."""
        if T <= 0 or sigma <= 0:
            return 0

        d1 = BlackScholes.d1(S, K, T, r, sigma)
        return stats.norm.pdf(d1) / (S * sigma * math.sqrt(T))

    @staticmethod
    def theta(S: float, K: float, T: float, r: float, sigma: float, option_type: str) -> float:
        """Calculate option theta (per day)."""
        if T <= 0 or sigma <= 0:
            return 0

        d1 = BlackScholes.d1(S, K, T, r, sigma)
        d2 = BlackScholes.d2(S, K, T, r, sigma)

        term1 = -(S * stats.norm.pdf(d1) * sigma) / (2 * math.sqrt(T))

        if option_type.upper() == "CE":
            term2 = -r * K * math.exp(-r * T) * stats.norm.cdf(d2)
        else:
            term2 = r * K * math.exp(-r * T) * stats.norm.cdf(-d2)

        # Return per day (divide by 365)
        return (term1 + term2) / 365

    @staticmethod
    def vega(S: float, K: float, T: float, r: float, sigma: float) -> float:
        """Calculate option vega (per 1% move in IV)."""
        if T <= 0 or sigma <= 0:
            return 0

        d1 = BlackScholes.d1(S, K, T, r, sigma)
        # Return vega per 1% IV change
        return S * math.sqrt(T) * stats.norm.pdf(d1) / 100


class GreeksCalculator:
    """Calculate and store Greeks for historical options."""

    # Risk-free rate (use RBI repo rate or similar)
    RISK_FREE_RATE = 0.065  # 6.5% annual

    def __init__(self, database_url: Optional[str] = None):
        """Initialize calculator."""
        self.database_url = database_url or settings.database_url
        self.engine = create_async_engine(self.database_url, echo=False)
        self.async_session = async_sessionmaker(
            self.engine,
            class_=AsyncSession,
            expire_on_commit=False,
        )

    async def calculate_greeks(
        self,
        option: HistoricalOption,
        spot_price: float,
    ) -> Dict[str, Optional[float]]:
        """Calculate IV and Greeks for an option."""
        # Time to expiry in years
        days_to_expiry = (option.expiry - option.date).days
        T = days_to_expiry / 365.0

        if T <= 0 or not option.close or float(option.close) <= 0:
            return {"iv": None, "delta": None, "gamma": None, "theta": None, "vega": None}

        S = spot_price
        K = float(option.strike)
        price = float(option.close)
        r = self.RISK_FREE_RATE
        opt_type = option.option_type

        # Calculate IV
        iv = BlackScholes.implied_volatility(price, S, K, T, r, opt_type)

        if iv is None:
            return {"iv": None, "delta": None, "gamma": None, "theta": None, "vega": None}

        # Calculate Greeks
        delta = BlackScholes.delta(S, K, T, r, iv, opt_type)
        gamma = BlackScholes.gamma(S, K, T, r, iv)
        theta = BlackScholes.theta(S, K, T, r, iv, opt_type)
        vega = BlackScholes.vega(S, K, T, r, iv)

        return {
            "iv": round(iv, 4),
            "delta": round(delta, 4),
            "gamma": round(gamma, 6),
            "theta": round(theta, 4),
            "vega": round(vega, 4),
        }

    async def process_date(self, trade_date: date, symbol: str = "NIFTY") -> Dict[str, Any]:
        """Process all options for a specific date."""
        async with self.async_session() as db:
            # Get spot price
            spot_result = await db.execute(
                select(HistoricalSpotPrice).where(
                    and_(
                        HistoricalSpotPrice.symbol == symbol,
                        HistoricalSpotPrice.date == trade_date,
                    )
                )
            )
            spot = spot_result.scalar_one_or_none()

            if not spot or not spot.close:
                return {
                    "date": trade_date.isoformat(),
                    "symbol": symbol,
                    "error": "No spot price found",
                    "options_updated": 0,
                }

            spot_price = float(spot.close)

            # Get all options for this date
            options_result = await db.execute(
                select(HistoricalOption).where(
                    and_(
                        HistoricalOption.symbol == symbol,
                        HistoricalOption.date == trade_date,
                        HistoricalOption.iv.is_(None),  # Only update missing Greeks
                    )
                )
            )
            options = options_result.scalars().all()

            updated = 0
            for option in options:
                greeks = await self.calculate_greeks(option, spot_price)

                if greeks["iv"] is not None:
                    option.iv = Decimal(str(greeks["iv"]))
                    option.delta = Decimal(str(greeks["delta"]))
                    option.gamma = Decimal(str(greeks["gamma"]))
                    option.theta = Decimal(str(greeks["theta"]))
                    option.vega = Decimal(str(greeks["vega"]))
                    updated += 1

            await db.commit()

            return {
                "date": trade_date.isoformat(),
                "symbol": symbol,
                "spot_price": spot_price,
                "options_processed": len(options),
                "options_updated": updated,
            }

    async def process_date_range(
        self,
        start_date: date,
        end_date: date,
        symbol: str = "NIFTY",
    ) -> List[Dict[str, Any]]:
        """Process Greeks for a date range."""
        results = []
        current = start_date

        while current <= end_date:
            # Skip weekends
            if current.weekday() >= 5:
                current += timedelta(days=1)
                continue

            print(f"Processing {symbol} {current}...")
            result = await self.process_date(current, symbol)
            results.append(result)

            current += timedelta(days=1)

        return results

    async def process_all_symbols(
        self,
        start_date: date,
        end_date: date,
    ) -> Dict[str, List[Dict[str, Any]]]:
        """Process Greeks for all supported symbols."""
        symbols = ["NIFTY", "BANKNIFTY", "MIDCPNIFTY", "FINNIFTY"]
        all_results = {}

        for symbol in symbols:
            print(f"\nProcessing {symbol}...")
            results = await self.process_date_range(start_date, end_date, symbol)
            all_results[symbol] = results

            # Summary
            total_updated = sum(r.get("options_updated", 0) for r in results)
            print(f"{symbol}: {total_updated} options updated")

        return all_results


# CLI interface
async def main():
    """Main entry point for CLI usage."""
    import argparse

    parser = argparse.ArgumentParser(description="Calculate Greeks for historical options")
    parser.add_argument(
        "--date",
        type=str,
        help="Single date to process (YYYY-MM-DD)",
    )
    parser.add_argument(
        "--start",
        type=str,
        help="Start date for range (YYYY-MM-DD)",
    )
    parser.add_argument(
        "--end",
        type=str,
        help="End date for range (YYYY-MM-DD)",
    )
    parser.add_argument(
        "--symbol",
        type=str,
        default="NIFTY",
        help="Symbol to process (default: NIFTY)",
    )
    parser.add_argument(
        "--all-symbols",
        action="store_true",
        help="Process all supported symbols",
    )

    args = parser.parse_args()

    calculator = GreeksCalculator()

    if args.date:
        trade_date = date.fromisoformat(args.date)
        result = await calculator.process_date(trade_date, args.symbol)
        print(result)

    elif args.start and args.end:
        start = date.fromisoformat(args.start)
        end = date.fromisoformat(args.end)

        if args.all_symbols:
            results = await calculator.process_all_symbols(start, end)
            for symbol, symbol_results in results.items():
                total = sum(r.get("options_updated", 0) for r in symbol_results)
                print(f"{symbol}: {total} options updated")
        else:
            results = await calculator.process_date_range(start, end, args.symbol)
            total = sum(r.get("options_updated", 0) for r in results)
            print(f"Total: {total} options updated")

    else:
        parser.print_help()


if __name__ == "__main__":
    asyncio.run(main())
