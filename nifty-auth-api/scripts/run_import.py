#!/usr/bin/env python3
"""
Main orchestrator script for importing historical options data.

Usage:
    python scripts/run_import.py --help
    python scripts/run_import.py import --year 2024
    python scripts/run_import.py import --start 2024-01-01 --end 2024-12-31
    python scripts/run_import.py greeks --start 2024-01-01 --end 2024-12-31
    python scripts/run_import.py full --year 2024
"""

import asyncio
import argparse
from datetime import date, datetime
from pathlib import Path
import sys

# Add project root to path
sys.path.insert(0, str(Path(__file__).parent.parent))

from scripts.data_import.nse_scraper import NSEBhavcopyImporter
from scripts.data_import.greeks_calculator import GreeksCalculator


async def import_data(args):
    """Import historical data from NSE."""
    async with NSEBhavcopyImporter() as importer:
        if args.date:
            trade_date = date.fromisoformat(args.date)
            result = await importer.import_date(trade_date)
            print(f"Result: {result}")

        elif args.year:
            result = await importer.import_year(args.year)
            print(f"\nYear {args.year} import complete:")
            print(f"  Days processed: {result['days_processed']}")
            print(f"  Successful days: {result['successful_days']}")
            print(f"  Spot prices saved: {result['total_spot_prices']}")
            print(f"  Options saved: {result['total_options']}")

        elif args.start and args.end:
            start = date.fromisoformat(args.start)
            end = date.fromisoformat(args.end)
            results = await importer.import_date_range(start, end)

            successful = sum(1 for r in results if r.get("success"))
            total_spots = sum(r.get("spot_prices_saved", 0) for r in results)
            total_options = sum(r.get("options_saved", 0) for r in results)

            print(f"\nImport complete:")
            print(f"  Days processed: {len(results)}")
            print(f"  Successful days: {successful}")
            print(f"  Spot prices saved: {total_spots}")
            print(f"  Options saved: {total_options}")

        elif args.all:
            start_year = args.from_year or 2015
            end_year = args.to_year or datetime.now().year
            results = await importer.import_all(start_year, end_year)

            print(f"\nFull import complete ({start_year}-{end_year}):")
            for result in results:
                print(f"  {result['year']}: {result['total_options']} options")


async def calculate_greeks(args):
    """Calculate Greeks for imported data."""
    calculator = GreeksCalculator()

    if args.date:
        trade_date = date.fromisoformat(args.date)
        result = await calculator.process_date(trade_date, args.symbol)
        print(f"Result: {result}")

    elif args.start and args.end:
        start = date.fromisoformat(args.start)
        end = date.fromisoformat(args.end)

        if args.all_symbols:
            results = await calculator.process_all_symbols(start, end)
            print("\nGreeks calculation complete:")
            for symbol, symbol_results in results.items():
                total = sum(r.get("options_updated", 0) for r in symbol_results)
                print(f"  {symbol}: {total} options updated")
        else:
            results = await calculator.process_date_range(start, end, args.symbol)
            total = sum(r.get("options_updated", 0) for r in results)
            print(f"\nGreeks calculation complete:")
            print(f"  Symbol: {args.symbol}")
            print(f"  Options updated: {total}")


async def full_import(args):
    """Run full import and Greeks calculation."""
    print("=" * 50)
    print("STEP 1: Importing historical data from NSE")
    print("=" * 50)

    async with NSEBhavcopyImporter() as importer:
        if args.year:
            result = await importer.import_year(args.year)
            print(f"Imported {result['total_options']} options for {args.year}")
        elif args.start and args.end:
            start = date.fromisoformat(args.start)
            end = date.fromisoformat(args.end)
            results = await importer.import_date_range(start, end)
            total_options = sum(r.get("options_saved", 0) for r in results)
            print(f"Imported {total_options} options")

    print("\n" + "=" * 50)
    print("STEP 2: Calculating Greeks")
    print("=" * 50)

    calculator = GreeksCalculator()

    if args.year:
        start = date(args.year, 1, 1)
        end = date(args.year, 12, 31)
    else:
        start = date.fromisoformat(args.start)
        end = date.fromisoformat(args.end)

    results = await calculator.process_all_symbols(start, end)

    print("\nGreeks calculation complete:")
    for symbol, symbol_results in results.items():
        total = sum(r.get("options_updated", 0) for r in symbol_results)
        print(f"  {symbol}: {total} options updated")

    print("\n" + "=" * 50)
    print("FULL IMPORT COMPLETE")
    print("=" * 50)


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(
        description="Import historical options data and calculate Greeks"
    )
    subparsers = parser.add_subparsers(dest="command", help="Command to run")

    # Import command
    import_parser = subparsers.add_parser("import", help="Import data from NSE")
    import_parser.add_argument("--date", type=str, help="Single date (YYYY-MM-DD)")
    import_parser.add_argument("--start", type=str, help="Start date (YYYY-MM-DD)")
    import_parser.add_argument("--end", type=str, help="End date (YYYY-MM-DD)")
    import_parser.add_argument("--year", type=int, help="Year to import")
    import_parser.add_argument("--all", action="store_true", help="Import all data")
    import_parser.add_argument("--from-year", type=int, help="Start year for --all")
    import_parser.add_argument("--to-year", type=int, help="End year for --all")

    # Greeks command
    greeks_parser = subparsers.add_parser("greeks", help="Calculate Greeks")
    greeks_parser.add_argument("--date", type=str, help="Single date (YYYY-MM-DD)")
    greeks_parser.add_argument("--start", type=str, help="Start date (YYYY-MM-DD)")
    greeks_parser.add_argument("--end", type=str, help="End date (YYYY-MM-DD)")
    greeks_parser.add_argument(
        "--symbol",
        type=str,
        default="NIFTY",
        help="Symbol to process (default: NIFTY)"
    )
    greeks_parser.add_argument(
        "--all-symbols",
        action="store_true",
        help="Process all symbols"
    )

    # Full import command
    full_parser = subparsers.add_parser("full", help="Full import + Greeks calculation")
    full_parser.add_argument("--year", type=int, help="Year to process")
    full_parser.add_argument("--start", type=str, help="Start date (YYYY-MM-DD)")
    full_parser.add_argument("--end", type=str, help="End date (YYYY-MM-DD)")

    args = parser.parse_args()

    if args.command == "import":
        asyncio.run(import_data(args))
    elif args.command == "greeks":
        asyncio.run(calculate_greeks(args))
    elif args.command == "full":
        asyncio.run(full_import(args))
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
