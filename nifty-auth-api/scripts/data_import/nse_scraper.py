"""
NSE Bhavcopy Scraper for importing historical derivatives data.

This script downloads and parses NSE bhavcopy files to extract
historical option chain data for NIFTY, BANKNIFTY, and other indices.
"""

import asyncio
import csv
import io
import zipfile
from datetime import date, datetime, timedelta
from typing import List, Dict, Any, Optional
from pathlib import Path
import httpx
from decimal import Decimal

# Database imports (adjust based on your setup)
import sys
sys.path.insert(0, str(Path(__file__).parent.parent.parent))

from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from sqlalchemy import select, and_

from app.config import settings
from app.models.backtest import HistoricalSpotPrice, HistoricalOption, Index as IndexModel


class NSEBhavcopyImporter:
    """Import historical data from NSE bhavcopy files."""

    # Supported index symbols (as they appear in NSE data)
    NSE_INDICES = ["NIFTY", "BANKNIFTY", "MIDCPNIFTY", "FINNIFTY"]

    # Month name mapping
    MONTHS = {
        1: "JAN", 2: "FEB", 3: "MAR", 4: "APR",
        5: "MAY", 6: "JUN", 7: "JUL", 8: "AUG",
        9: "SEP", 10: "OCT", 11: "NOV", 12: "DEC"
    }

    # NSE archives base URL
    BASE_URL = "https://archives.nseindia.com/content/historical/DERIVATIVES"

    # Nifty Indices historical data URL
    INDICES_URL = "https://www.niftyindices.com/IndexConstituent/HistoricalData"

    def __init__(self, database_url: Optional[str] = None):
        """Initialize the importer."""
        self.database_url = database_url or settings.database_url
        self.engine = create_async_engine(self.database_url, echo=False)
        self.async_session = async_sessionmaker(
            self.engine,
            class_=AsyncSession,
            expire_on_commit=False,
        )
        self.client: Optional[httpx.AsyncClient] = None

    async def __aenter__(self):
        """Async context manager entry."""
        self.client = httpx.AsyncClient(
            timeout=60.0,
            headers={
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language": "en-US,en;q=0.5",
                "Connection": "keep-alive",
            }
        )
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        """Async context manager exit."""
        if self.client:
            await self.client.aclose()

    def _get_bhavcopy_url(self, trade_date: date) -> str:
        """
        Generate URL for bhavcopy file.

        NSE Format: https://archives.nseindia.com/content/historical/DERIVATIVES/
                    {YEAR}/{MONTH}/fo{DD}{MON}{YEAR}bhav.csv.zip
        """
        year = trade_date.year
        month = self.MONTHS[trade_date.month]
        day = trade_date.strftime("%d")

        filename = f"fo{day}{month}{year}bhav.csv.zip"
        return f"{self.BASE_URL}/{year}/{month}/{filename}"

    async def _download_bhavcopy(self, trade_date: date) -> Optional[str]:
        """Download and extract bhavcopy CSV for a date."""
        url = self._get_bhavcopy_url(trade_date)

        try:
            response = await self.client.get(url)
            if response.status_code != 200:
                print(f"No data for {trade_date}: HTTP {response.status_code}")
                return None

            # Extract ZIP
            zip_content = io.BytesIO(response.content)
            with zipfile.ZipFile(zip_content) as zf:
                # Get the CSV file name
                csv_name = zf.namelist()[0]
                csv_content = zf.read(csv_name).decode("utf-8")
                return csv_content

        except Exception as e:
            print(f"Error downloading {trade_date}: {e}")
            return None

    def _parse_bhavcopy(self, csv_content: str, trade_date: date) -> tuple[List[Dict], List[Dict]]:
        """
        Parse bhavcopy CSV content.

        Returns tuple of (spot_prices, options_data).
        """
        spot_prices = []
        options_data = []

        reader = csv.DictReader(io.StringIO(csv_content))

        # Track spot prices by symbol
        spot_by_symbol = {}

        for row in reader:
            symbol = row.get("SYMBOL", "").strip()
            instrument = row.get("INSTRUMENT", "").strip()

            # Only process index derivatives
            if symbol not in self.NSE_INDICES:
                continue

            # Process futures for spot price approximation
            if instrument == "FUTIDX":
                try:
                    close_price = Decimal(row.get("CLOSE", "0"))
                    if symbol not in spot_by_symbol or close_price > 0:
                        spot_by_symbol[symbol] = {
                            "symbol": symbol,
                            "exchange": "NSE",
                            "date": trade_date,
                            "open": Decimal(row.get("OPEN", "0")),
                            "high": Decimal(row.get("HIGH", "0")),
                            "low": Decimal(row.get("LOW", "0")),
                            "close": close_price,
                            "volume": int(row.get("CONTRACTS", "0")),
                        }
                except (ValueError, TypeError):
                    continue

            # Process options
            elif instrument == "OPTIDX":
                try:
                    expiry_str = row.get("EXPIRY_DT", "")
                    option_type = row.get("OPTION_TYP", "").strip()

                    # Parse expiry date (format: DD-MMM-YYYY)
                    expiry = datetime.strptime(expiry_str, "%d-%b-%Y").date()

                    option_data = {
                        "symbol": symbol,
                        "exchange": "NSE",
                        "date": trade_date,
                        "expiry": expiry,
                        "strike": Decimal(row.get("STRIKE_PR", "0")),
                        "option_type": option_type,
                        "open": Decimal(row.get("OPEN", "0")),
                        "high": Decimal(row.get("HIGH", "0")),
                        "low": Decimal(row.get("LOW", "0")),
                        "close": Decimal(row.get("CLOSE", "0")),
                        "settle_price": Decimal(row.get("SETTLE_PR", "0")),
                        "volume": int(row.get("CONTRACTS", "0")),
                        "open_interest": int(row.get("OPEN_INT", "0")),
                    }
                    options_data.append(option_data)

                except (ValueError, TypeError) as e:
                    continue

        spot_prices = list(spot_by_symbol.values())
        return spot_prices, options_data

    async def _save_to_database(
        self,
        db: AsyncSession,
        spot_prices: List[Dict],
        options_data: List[Dict],
    ) -> tuple[int, int]:
        """Save parsed data to database."""
        spots_saved = 0
        options_saved = 0

        # Save spot prices
        for spot in spot_prices:
            # Check if exists
            result = await db.execute(
                select(HistoricalSpotPrice).where(
                    and_(
                        HistoricalSpotPrice.symbol == spot["symbol"],
                        HistoricalSpotPrice.date == spot["date"],
                    )
                )
            )
            existing = result.scalar_one_or_none()

            if not existing:
                db.add(HistoricalSpotPrice(**spot))
                spots_saved += 1

        # Save options (batch for performance)
        for option in options_data:
            # Check if exists
            result = await db.execute(
                select(HistoricalOption).where(
                    and_(
                        HistoricalOption.symbol == option["symbol"],
                        HistoricalOption.date == option["date"],
                        HistoricalOption.expiry == option["expiry"],
                        HistoricalOption.strike == option["strike"],
                        HistoricalOption.option_type == option["option_type"],
                    )
                )
            )
            existing = result.scalar_one_or_none()

            if not existing:
                db.add(HistoricalOption(**option))
                options_saved += 1

        await db.commit()
        return spots_saved, options_saved

    async def import_date(self, trade_date: date) -> Dict[str, Any]:
        """Import data for a single date."""
        # Download bhavcopy
        csv_content = await self._download_bhavcopy(trade_date)
        if not csv_content:
            return {
                "date": trade_date.isoformat(),
                "success": False,
                "error": "No data available",
            }

        # Parse content
        spot_prices, options_data = self._parse_bhavcopy(csv_content, trade_date)

        # Save to database
        async with self.async_session() as db:
            spots_saved, options_saved = await self._save_to_database(
                db, spot_prices, options_data
            )

        return {
            "date": trade_date.isoformat(),
            "success": True,
            "spot_prices_saved": spots_saved,
            "options_saved": options_saved,
        }

    async def import_date_range(
        self,
        start_date: date,
        end_date: date,
        skip_weekends: bool = True,
    ) -> List[Dict[str, Any]]:
        """Import data for a date range."""
        results = []
        current = start_date

        while current <= end_date:
            # Skip weekends
            if skip_weekends and current.weekday() >= 5:
                current += timedelta(days=1)
                continue

            print(f"Importing {current}...")
            result = await self.import_date(current)
            results.append(result)

            # Rate limiting
            await asyncio.sleep(0.5)

            current += timedelta(days=1)

        return results

    async def import_month(self, year: int, month: int) -> List[Dict[str, Any]]:
        """Import data for an entire month."""
        start_date = date(year, month, 1)

        # Get end of month
        if month == 12:
            end_date = date(year + 1, 1, 1) - timedelta(days=1)
        else:
            end_date = date(year, month + 1, 1) - timedelta(days=1)

        return await self.import_date_range(start_date, end_date)

    async def import_year(self, year: int) -> Dict[str, Any]:
        """Import data for an entire year."""
        start_date = date(year, 1, 1)
        end_date = date(year, 12, 31)

        print(f"Importing year {year}...")
        results = await self.import_date_range(start_date, end_date)

        successful = sum(1 for r in results if r["success"])
        total_spots = sum(r.get("spot_prices_saved", 0) for r in results)
        total_options = sum(r.get("options_saved", 0) for r in results)

        return {
            "year": year,
            "days_processed": len(results),
            "successful_days": successful,
            "total_spot_prices": total_spots,
            "total_options": total_options,
        }

    async def import_all(
        self,
        start_year: int = 2015,
        end_year: int = None,
    ) -> List[Dict[str, Any]]:
        """Import all historical data."""
        if end_year is None:
            end_year = datetime.now().year

        results = []
        for year in range(start_year, end_year + 1):
            result = await self.import_year(year)
            results.append(result)
            print(f"Year {year} complete: {result}")

        return results


# CLI interface
async def main():
    """Main entry point for CLI usage."""
    import argparse

    parser = argparse.ArgumentParser(description="Import NSE historical data")
    parser.add_argument(
        "--date",
        type=str,
        help="Single date to import (YYYY-MM-DD)",
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
        "--year",
        type=int,
        help="Year to import",
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="Import all available data (2015-present)",
    )

    args = parser.parse_args()

    async with NSEBhavcopyImporter() as importer:
        if args.date:
            trade_date = datetime.strptime(args.date, "%Y-%m-%d").date()
            result = await importer.import_date(trade_date)
            print(result)

        elif args.start and args.end:
            start = datetime.strptime(args.start, "%Y-%m-%d").date()
            end = datetime.strptime(args.end, "%Y-%m-%d").date()
            results = await importer.import_date_range(start, end)
            print(f"Imported {len(results)} days")

        elif args.year:
            result = await importer.import_year(args.year)
            print(result)

        elif args.all:
            results = await importer.import_all()
            print(f"Import complete: {results}")

        else:
            parser.print_help()


if __name__ == "__main__":
    asyncio.run(main())
