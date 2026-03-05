"""Service for managing backtests, strategies, and results."""

import uuid
from datetime import datetime, date
from typing import Optional, List, Dict, Any
from decimal import Decimal
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, and_, delete

from app.models.backtest import (
    BacktestStrategy,
    BacktestRun,
    BacktestTrade,
    BacktestResult,
    BacktestStatus,
    SupportedIndex,
)
from app.models.user import User
from app.services.backtest_engine import run_backtest
from app.services.historical_data_service import historical_data_service


class BacktestService:
    """Service for managing backtests."""

    # ==================== Strategy Management ====================

    @staticmethod
    async def create_strategy(
        db: AsyncSession,
        user_id: str,
        name: str,
        strategy_type: str,
        entry_rules: Dict[str, Any],
        exit_rules: Dict[str, Any],
        description: Optional[str] = None,
        position_sizing: Optional[Dict[str, Any]] = None,
    ) -> BacktestStrategy:
        """Create a new backtest strategy."""
        strategy = BacktestStrategy(
            user_id=user_id,
            name=name,
            description=description,
            strategy_type=strategy_type,
            entry_rules=entry_rules,
            exit_rules=exit_rules,
            position_sizing=position_sizing,
            is_template=False,
        )
        db.add(strategy)
        await db.flush()
        return strategy

    @staticmethod
    async def get_strategy(
        db: AsyncSession,
        strategy_id: str,
        user_id: Optional[str] = None,
    ) -> Optional[BacktestStrategy]:
        """Get a strategy by ID."""
        query = select(BacktestStrategy).where(
            BacktestStrategy.id == strategy_id
        )

        if user_id:
            # Include user's strategies and templates
            query = query.where(
                (BacktestStrategy.user_id == user_id) |
                (BacktestStrategy.is_template == True)
            )

        result = await db.execute(query)
        return result.scalar_one_or_none()

    @staticmethod
    async def list_strategies(
        db: AsyncSession,
        user_id: str,
        include_templates: bool = True,
    ) -> List[BacktestStrategy]:
        """List strategies available to a user."""
        if include_templates:
            query = select(BacktestStrategy).where(
                (BacktestStrategy.user_id == user_id) |
                (BacktestStrategy.is_template == True)
            )
        else:
            query = select(BacktestStrategy).where(
                BacktestStrategy.user_id == user_id
            )

        query = query.order_by(BacktestStrategy.created_at.desc())
        result = await db.execute(query)
        return list(result.scalars().all())

    @staticmethod
    async def update_strategy(
        db: AsyncSession,
        strategy_id: str,
        user_id: str,
        **updates,
    ) -> Optional[BacktestStrategy]:
        """Update a user's strategy."""
        strategy = await BacktestService.get_strategy(db, strategy_id, user_id)

        if not strategy or strategy.user_id != user_id:
            return None

        for key, value in updates.items():
            if value is not None and hasattr(strategy, key):
                setattr(strategy, key, value)

        strategy.updated_at = datetime.utcnow()
        return strategy

    @staticmethod
    async def delete_strategy(
        db: AsyncSession,
        strategy_id: str,
        user_id: str,
    ) -> bool:
        """Delete a user's strategy."""
        strategy = await BacktestService.get_strategy(db, strategy_id, user_id)

        if not strategy or strategy.user_id != user_id or strategy.is_template:
            return False

        await db.delete(strategy)
        return True

    @staticmethod
    async def create_default_templates(db: AsyncSession) -> List[BacktestStrategy]:
        """Create default strategy templates."""
        templates = [
            {
                "name": "Iron Condor - High IV",
                "description": "Sell OTM call and put spreads when IV rank is high. Target 50% profit or 2x loss.",
                "strategy_type": "iron_condor",
                "entry_rules": {
                    "iv_rank_min": 50,
                    "days_to_expiry": [15, 45],
                    "wing_width": 100,
                },
                "exit_rules": {
                    "profit_target_pct": 50,
                    "stop_loss_pct": 200,
                    "days_before_expiry_exit": 3,
                },
            },
            {
                "name": "Short Straddle - Weekly",
                "description": "Sell ATM call and put on weekly options. Exit at 50% profit or 1 day before expiry.",
                "strategy_type": "straddle",
                "entry_rules": {
                    "days_to_expiry": [5, 10],
                },
                "exit_rules": {
                    "profit_target_pct": 50,
                    "days_before_expiry_exit": 1,
                },
            },
            {
                "name": "Short Strangle - Monthly",
                "description": "Sell OTM strangle on monthly options when IV is elevated.",
                "strategy_type": "strangle",
                "entry_rules": {
                    "iv_rank_min": 40,
                    "days_to_expiry": [20, 45],
                },
                "exit_rules": {
                    "profit_target_pct": 50,
                    "stop_loss_pct": 100,
                    "days_before_expiry_exit": 5,
                },
            },
        ]

        created = []
        for template_data in templates:
            # Check if template already exists
            result = await db.execute(
                select(BacktestStrategy).where(
                    and_(
                        BacktestStrategy.name == template_data["name"],
                        BacktestStrategy.is_template == True,
                    )
                )
            )
            existing = result.scalar_one_or_none()

            if not existing:
                template = BacktestStrategy(
                    user_id=None,
                    name=template_data["name"],
                    description=template_data["description"],
                    strategy_type=template_data["strategy_type"],
                    entry_rules=template_data["entry_rules"],
                    exit_rules=template_data["exit_rules"],
                    is_template=True,
                )
                db.add(template)
                created.append(template)

        if created:
            await db.flush()

        return created

    # ==================== Backtest Run Management ====================

    @staticmethod
    async def create_run(
        db: AsyncSession,
        user_id: str,
        symbol: str,
        start_date: date,
        end_date: date,
        initial_capital: Decimal,
        strategy_id: Optional[str] = None,
        strategy_config: Optional[Dict[str, Any]] = None,
        lot_size: int = 25,
        max_positions: int = 5,
        position_size_pct: Optional[Decimal] = None,
        max_loss_per_trade: Optional[Decimal] = None,
        max_daily_loss: Optional[Decimal] = None,
        stop_loss_pct: Optional[Decimal] = None,
        take_profit_pct: Optional[Decimal] = None,
    ) -> BacktestRun:
        """Create a new backtest run."""
        # If strategy_id provided, copy its config
        if strategy_id:
            strategy = await BacktestService.get_strategy(db, strategy_id, user_id)
            if strategy:
                strategy_config = {
                    "strategy_type": strategy.strategy_type,
                    "entry_rules": strategy.entry_rules,
                    "exit_rules": strategy.exit_rules,
                    "position_sizing": strategy.position_sizing,
                }

        run = BacktestRun(
            user_id=user_id,
            strategy_id=strategy_id,
            symbol=symbol.upper(),
            start_date=start_date,
            end_date=end_date,
            initial_capital=initial_capital,
            lot_size=lot_size,
            max_positions=max_positions,
            position_size_pct=position_size_pct,
            max_loss_per_trade=max_loss_per_trade,
            max_daily_loss=max_daily_loss,
            stop_loss_pct=stop_loss_pct,
            take_profit_pct=take_profit_pct,
            strategy_config=strategy_config,
            status=BacktestStatus.PENDING.value,
            progress=0,
        )
        db.add(run)
        await db.flush()
        return run

    @staticmethod
    async def get_run(
        db: AsyncSession,
        run_id: str,
        user_id: Optional[str] = None,
    ) -> Optional[BacktestRun]:
        """Get a backtest run by ID."""
        query = select(BacktestRun).where(BacktestRun.id == run_id)

        if user_id:
            query = query.where(BacktestRun.user_id == user_id)

        result = await db.execute(query)
        return result.scalar_one_or_none()

    @staticmethod
    async def list_runs(
        db: AsyncSession,
        user_id: str,
        limit: int = 50,
        offset: int = 0,
    ) -> tuple[List[BacktestRun], int]:
        """List backtest runs for a user."""
        # Count total
        count_result = await db.execute(
            select(BacktestRun.id).where(BacktestRun.user_id == user_id)
        )
        total = len(count_result.all())

        # Get runs
        result = await db.execute(
            select(BacktestRun)
            .where(BacktestRun.user_id == user_id)
            .order_by(BacktestRun.created_at.desc())
            .offset(offset)
            .limit(limit)
        )
        runs = list(result.scalars().all())

        return runs, total

    @staticmethod
    async def delete_run(
        db: AsyncSession,
        run_id: str,
        user_id: str,
    ) -> bool:
        """Delete a backtest run."""
        run = await BacktestService.get_run(db, run_id, user_id)

        if not run:
            return False

        await db.delete(run)
        return True

    @staticmethod
    async def execute_run(
        db: AsyncSession,
        run: BacktestRun,
    ) -> BacktestResult:
        """Execute a backtest run."""
        # Update status to running
        run.status = BacktestStatus.RUNNING.value
        run.started_at = datetime.utcnow()
        await db.commit()

        try:
            # Progress callback
            async def update_progress(progress: int):
                run.progress = progress
                await db.commit()

            # Run backtest
            results = await run_backtest(run, db, update_progress)

            # Save trades
            for trade_data in results.get("trades", []):
                trade = BacktestTrade(
                    run_id=run.id,
                    entry_date=trade_data["entry_date"],
                    exit_date=trade_data.get("exit_date"),
                    expiry=trade_data["expiry"],
                    strike=Decimal(str(trade_data["strike"])),
                    option_type=trade_data["option_type"],
                    action=trade_data["action"],
                    quantity=trade_data["quantity"],
                    entry_price=Decimal(str(trade_data["entry_price"])),
                    exit_price=Decimal(str(trade_data.get("exit_price", 0))) if trade_data.get("exit_price") else None,
                    entry_iv=Decimal(str(trade_data["entry_iv"])) if trade_data.get("entry_iv") else None,
                    entry_delta=Decimal(str(trade_data["entry_delta"])) if trade_data.get("entry_delta") else None,
                    realized_pnl=Decimal(str(trade_data.get("realized_pnl", 0))) if trade_data.get("realized_pnl") else None,
                    exit_reason=trade_data.get("exit_reason"),
                )
                db.add(trade)

            # Save results
            result = BacktestResult(
                run_id=run.id,
                total_trades=results["total_trades"],
                winning_trades=results["winning_trades"],
                losing_trades=results["losing_trades"],
                win_rate=Decimal(str(results["win_rate"])) if results.get("win_rate") else None,
                total_pnl=Decimal(str(results["total_pnl"])) if results.get("total_pnl") else None,
                total_return_pct=Decimal(str(results["total_return_pct"])) if results.get("total_return_pct") else None,
                cagr=Decimal(str(results["cagr"])) if results.get("cagr") else None,
                max_drawdown=Decimal(str(results["max_drawdown"])) if results.get("max_drawdown") else None,
                max_drawdown_pct=Decimal(str(results["max_drawdown_pct"])) if results.get("max_drawdown_pct") else None,
                sharpe_ratio=Decimal(str(results["sharpe_ratio"])) if results.get("sharpe_ratio") else None,
                sortino_ratio=Decimal(str(results["sortino_ratio"])) if results.get("sortino_ratio") else None,
                calmar_ratio=Decimal(str(results["calmar_ratio"])) if results.get("calmar_ratio") else None,
                avg_profit=Decimal(str(results["avg_profit"])) if results.get("avg_profit") else None,
                avg_loss=Decimal(str(results["avg_loss"])) if results.get("avg_loss") else None,
                profit_factor=Decimal(str(results["profit_factor"])) if results.get("profit_factor") else None,
                avg_holding_days=Decimal(str(results["avg_holding_days"])) if results.get("avg_holding_days") else None,
                equity_curve=results.get("equity_curve"),
                monthly_returns=results.get("monthly_returns"),
            )
            db.add(result)

            # Update run status
            run.status = BacktestStatus.COMPLETED.value
            run.progress = 100
            run.completed_at = datetime.utcnow()

            await db.commit()
            return result

        except Exception as e:
            run.status = BacktestStatus.FAILED.value
            run.error_message = str(e)
            run.completed_at = datetime.utcnow()
            await db.commit()
            raise

    @staticmethod
    async def get_run_trades(
        db: AsyncSession,
        run_id: str,
        user_id: str,
        limit: int = 100,
        offset: int = 0,
    ) -> tuple[List[BacktestTrade], int]:
        """Get trades for a backtest run."""
        # Verify ownership
        run = await BacktestService.get_run(db, run_id, user_id)
        if not run:
            return [], 0

        # Count total
        count_result = await db.execute(
            select(BacktestTrade.id).where(BacktestTrade.run_id == run_id)
        )
        total = len(count_result.all())

        # Get trades
        result = await db.execute(
            select(BacktestTrade)
            .where(BacktestTrade.run_id == run_id)
            .order_by(BacktestTrade.entry_date)
            .offset(offset)
            .limit(limit)
        )
        trades = list(result.scalars().all())

        return trades, total

    @staticmethod
    async def get_run_result(
        db: AsyncSession,
        run_id: str,
        user_id: str,
    ) -> Optional[BacktestResult]:
        """Get result for a backtest run."""
        run = await BacktestService.get_run(db, run_id, user_id)
        if not run:
            return None

        result = await db.execute(
            select(BacktestResult).where(BacktestResult.run_id == run_id)
        )
        return result.scalar_one_or_none()

    # ==================== Index Management ====================

    @staticmethod
    async def seed_indices(db: AsyncSession) -> List[SupportedIndex]:
        """Seed default indices."""
        indices_data = [
            {
                "symbol": "NIFTY",
                "name": "NIFTY 50",
                "exchange": "NSE",
                "lot_size": 25,
                "data_available_from": date(2015, 1, 1),
            },
            {
                "symbol": "BANKNIFTY",
                "name": "NIFTY Bank",
                "exchange": "NSE",
                "lot_size": 15,
                "data_available_from": date(2015, 1, 1),
            },
            {
                "symbol": "MIDCPNIFTY",
                "name": "MIDCAP NIFTY",
                "exchange": "NSE",
                "lot_size": 50,
                "data_available_from": date(2016, 1, 1),
            },
            {
                "symbol": "FINNIFTY",
                "name": "NIFTY Financial Services",
                "exchange": "NSE",
                "lot_size": 25,
                "data_available_from": date(2021, 1, 1),
            },
            {
                "symbol": "SENSEX",
                "name": "S&P BSE SENSEX",
                "exchange": "BSE",
                "lot_size": 10,
                "data_available_from": date(2015, 1, 1),
            },
        ]

        created = []
        for index_data in indices_data:
            # Check if exists
            result = await db.execute(
                select(SupportedIndex).where(SupportedIndex.symbol == index_data["symbol"])
            )
            existing = result.scalar_one_or_none()

            if not existing:
                index = SupportedIndex(**index_data)
                db.add(index)
                created.append(index)

        if created:
            await db.flush()

        return created


backtest_service = BacktestService()
