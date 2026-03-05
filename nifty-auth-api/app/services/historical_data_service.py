"""Service for fetching historical market data."""

from datetime import date, timedelta
from typing import Optional, List, Dict, Any
from decimal import Decimal
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, and_, func

from app.models.backtest import (
    SupportedIndex,
    HistoricalSpotPrice,
    HistoricalOption,
)


class HistoricalDataService:
    """Service for accessing historical market data."""

    @staticmethod
    async def get_indices(db: AsyncSession) -> List[SupportedIndex]:
        """Get all available indices."""
        result = await db.execute(
            select(SupportedIndex).where(SupportedIndex.is_active == True)
        )
        return list(result.scalars().all())

    @staticmethod
    async def get_index_by_symbol(
        db: AsyncSession,
        symbol: str,
    ) -> Optional[SupportedIndex]:
        """Get index by symbol."""
        result = await db.execute(
            select(SupportedIndex).where(SupportedIndex.symbol == symbol.upper())
        )
        return result.scalar_one_or_none()

    @staticmethod
    async def get_spot_prices(
        db: AsyncSession,
        symbol: str,
        start_date: date,
        end_date: date,
    ) -> List[HistoricalSpotPrice]:
        """Get historical spot prices for a symbol."""
        result = await db.execute(
            select(HistoricalSpotPrice)
            .where(
                and_(
                    HistoricalSpotPrice.symbol == symbol.upper(),
                    HistoricalSpotPrice.date >= start_date,
                    HistoricalSpotPrice.date <= end_date,
                )
            )
            .order_by(HistoricalSpotPrice.date)
        )
        return list(result.scalars().all())

    @staticmethod
    async def get_spot_price_on_date(
        db: AsyncSession,
        symbol: str,
        trade_date: date,
    ) -> Optional[HistoricalSpotPrice]:
        """Get spot price for a specific date."""
        result = await db.execute(
            select(HistoricalSpotPrice)
            .where(
                and_(
                    HistoricalSpotPrice.symbol == symbol.upper(),
                    HistoricalSpotPrice.date == trade_date,
                )
            )
        )
        return result.scalar_one_or_none()

    @staticmethod
    async def get_options_chain(
        db: AsyncSession,
        symbol: str,
        trade_date: date,
        expiry: Optional[date] = None,
    ) -> List[HistoricalOption]:
        """Get option chain for a specific date."""
        query = select(HistoricalOption).where(
            and_(
                HistoricalOption.symbol == symbol.upper(),
                HistoricalOption.date == trade_date,
            )
        )

        if expiry:
            query = query.where(HistoricalOption.expiry == expiry)

        query = query.order_by(
            HistoricalOption.expiry,
            HistoricalOption.strike,
            HistoricalOption.option_type,
        )

        result = await db.execute(query)
        return list(result.scalars().all())

    @staticmethod
    async def get_option_price(
        db: AsyncSession,
        symbol: str,
        trade_date: date,
        expiry: date,
        strike: Decimal,
        option_type: str,
    ) -> Optional[HistoricalOption]:
        """Get specific option price."""
        result = await db.execute(
            select(HistoricalOption)
            .where(
                and_(
                    HistoricalOption.symbol == symbol.upper(),
                    HistoricalOption.date == trade_date,
                    HistoricalOption.expiry == expiry,
                    HistoricalOption.strike == strike,
                    HistoricalOption.option_type == option_type.upper(),
                )
            )
        )
        return result.scalar_one_or_none()

    @staticmethod
    async def get_available_expiries(
        db: AsyncSession,
        symbol: str,
        trade_date: date,
    ) -> List[date]:
        """Get available expiry dates for a symbol on a given date."""
        result = await db.execute(
            select(HistoricalOption.expiry)
            .where(
                and_(
                    HistoricalOption.symbol == symbol.upper(),
                    HistoricalOption.date == trade_date,
                    HistoricalOption.expiry >= trade_date,
                )
            )
            .distinct()
            .order_by(HistoricalOption.expiry)
        )
        return [row[0] for row in result.all()]

    @staticmethod
    async def get_available_strikes(
        db: AsyncSession,
        symbol: str,
        trade_date: date,
        expiry: date,
    ) -> List[Decimal]:
        """Get available strikes for a symbol/expiry combination."""
        result = await db.execute(
            select(HistoricalOption.strike)
            .where(
                and_(
                    HistoricalOption.symbol == symbol.upper(),
                    HistoricalOption.date == trade_date,
                    HistoricalOption.expiry == expiry,
                )
            )
            .distinct()
            .order_by(HistoricalOption.strike)
        )
        return [row[0] for row in result.all()]

    @staticmethod
    async def get_trading_dates(
        db: AsyncSession,
        symbol: str,
        start_date: date,
        end_date: date,
    ) -> List[date]:
        """Get list of trading dates (dates with data) for a symbol."""
        result = await db.execute(
            select(HistoricalSpotPrice.date)
            .where(
                and_(
                    HistoricalSpotPrice.symbol == symbol.upper(),
                    HistoricalSpotPrice.date >= start_date,
                    HistoricalSpotPrice.date <= end_date,
                )
            )
            .distinct()
            .order_by(HistoricalSpotPrice.date)
        )
        return [row[0] for row in result.all()]

    @staticmethod
    async def get_nearest_expiry(
        db: AsyncSession,
        symbol: str,
        trade_date: date,
        min_days: int = 0,
        max_days: int = 45,
    ) -> Optional[date]:
        """Get nearest expiry within specified range."""
        min_expiry = trade_date + timedelta(days=min_days)
        max_expiry = trade_date + timedelta(days=max_days)

        result = await db.execute(
            select(HistoricalOption.expiry)
            .where(
                and_(
                    HistoricalOption.symbol == symbol.upper(),
                    HistoricalOption.date == trade_date,
                    HistoricalOption.expiry >= min_expiry,
                    HistoricalOption.expiry <= max_expiry,
                )
            )
            .distinct()
            .order_by(HistoricalOption.expiry)
            .limit(1)
        )
        row = result.first()
        return row[0] if row else None

    @staticmethod
    async def get_atm_strike(
        db: AsyncSession,
        symbol: str,
        trade_date: date,
        expiry: date,
        spot_price: Decimal,
    ) -> Optional[Decimal]:
        """Get ATM (at-the-money) strike closest to spot price."""
        result = await db.execute(
            select(HistoricalOption.strike)
            .where(
                and_(
                    HistoricalOption.symbol == symbol.upper(),
                    HistoricalOption.date == trade_date,
                    HistoricalOption.expiry == expiry,
                )
            )
            .distinct()
            .order_by(func.abs(HistoricalOption.strike - spot_price))
            .limit(1)
        )
        row = result.first()
        return row[0] if row else None

    @staticmethod
    async def get_otm_strikes(
        db: AsyncSession,
        symbol: str,
        trade_date: date,
        expiry: date,
        spot_price: Decimal,
        option_type: str,
        num_strikes: int = 5,
    ) -> List[Decimal]:
        """Get OTM (out-of-the-money) strikes."""
        if option_type.upper() == "CE":
            # For calls, OTM means strike > spot
            query = (
                select(HistoricalOption.strike)
                .where(
                    and_(
                        HistoricalOption.symbol == symbol.upper(),
                        HistoricalOption.date == trade_date,
                        HistoricalOption.expiry == expiry,
                        HistoricalOption.option_type == "CE",
                        HistoricalOption.strike > spot_price,
                    )
                )
                .distinct()
                .order_by(HistoricalOption.strike)
                .limit(num_strikes)
            )
        else:
            # For puts, OTM means strike < spot
            query = (
                select(HistoricalOption.strike)
                .where(
                    and_(
                        HistoricalOption.symbol == symbol.upper(),
                        HistoricalOption.date == trade_date,
                        HistoricalOption.expiry == expiry,
                        HistoricalOption.option_type == "PE",
                        HistoricalOption.strike < spot_price,
                    )
                )
                .distinct()
                .order_by(HistoricalOption.strike.desc())
                .limit(num_strikes)
            )

        result = await db.execute(query)
        return [row[0] for row in result.all()]

    @staticmethod
    async def calculate_iv_rank(
        db: AsyncSession,
        symbol: str,
        current_date: date,
        lookback_days: int = 252,
    ) -> Optional[float]:
        """
        Calculate IV rank based on historical IV data.
        IV Rank = (Current IV - Lowest IV) / (Highest IV - Lowest IV) * 100
        """
        start_date = current_date - timedelta(days=lookback_days)

        # Get current ATM IV
        spot = await HistoricalDataService.get_spot_price_on_date(
            db, symbol, current_date
        )
        if not spot or not spot.close:
            return None

        expiry = await HistoricalDataService.get_nearest_expiry(
            db, symbol, current_date, min_days=15, max_days=45
        )
        if not expiry:
            return None

        atm_strike = await HistoricalDataService.get_atm_strike(
            db, symbol, current_date, expiry, spot.close
        )
        if not atm_strike:
            return None

        # Get current ATM option
        current_option = await HistoricalDataService.get_option_price(
            db, symbol, current_date, expiry, atm_strike, "CE"
        )
        if not current_option or not current_option.iv:
            return None

        current_iv = float(current_option.iv)

        # Get historical IV range (simplified - using ATM CE IV)
        result = await db.execute(
            select(
                func.min(HistoricalOption.iv),
                func.max(HistoricalOption.iv),
            )
            .where(
                and_(
                    HistoricalOption.symbol == symbol.upper(),
                    HistoricalOption.date >= start_date,
                    HistoricalOption.date <= current_date,
                    HistoricalOption.iv.isnot(None),
                    HistoricalOption.iv > 0,
                )
            )
        )
        row = result.first()
        if not row or not row[0] or not row[1]:
            return None

        min_iv, max_iv = float(row[0]), float(row[1])
        if max_iv == min_iv:
            return 50.0  # Default to middle if no range

        iv_rank = ((current_iv - min_iv) / (max_iv - min_iv)) * 100
        return round(max(0, min(100, iv_rank)), 2)

    @staticmethod
    async def get_data_date_range(
        db: AsyncSession,
        symbol: str,
    ) -> Dict[str, Optional[date]]:
        """Get the date range of available data for a symbol."""
        result = await db.execute(
            select(
                func.min(HistoricalSpotPrice.date),
                func.max(HistoricalSpotPrice.date),
            )
            .where(HistoricalSpotPrice.symbol == symbol.upper())
        )
        row = result.first()
        return {
            "start_date": row[0] if row else None,
            "end_date": row[1] if row else None,
        }


historical_data_service = HistoricalDataService()
