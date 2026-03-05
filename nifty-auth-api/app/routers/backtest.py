"""API routes for backtesting functionality."""

from datetime import date
from decimal import Decimal
from typing import Optional, List
from fastapi import APIRouter, Depends, HTTPException, status, BackgroundTasks
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db, AsyncSessionLocal
from app.models.user import User
from app.models.backtest import BacktestStatus
from app.services.auth_service import get_current_user, get_current_user_optional
from app.services.backtest_service import backtest_service
from app.services.historical_data_service import historical_data_service
from app.schemas.backtest import (
    # Strategy schemas
    StrategyCreateRequest,
    StrategyUpdateRequest,
    StrategyResponse,
    StrategyListResponse,
    # Run schemas
    BacktestRunRequest,
    BacktestRunResponse,
    BacktestRunListResponse,
    BacktestRunWithResultResponse,
    # Result schemas
    BacktestResultResponse,
    TradeResponse,
    TradeListResponse,
    # Historical data schemas
    IndexResponse,
    SpotPriceResponse,
    OptionDataResponse,
    HistoricalSpotRequest,
    HistoricalOptionsRequest,
    ExpiryListResponse,
)


router = APIRouter(prefix="/backtest", tags=["Backtesting"])

# Demo user ID for unauthenticated backtest runs
DEMO_USER_ID = "00000000-0000-0000-0000-000000000001"


async def get_or_create_demo_user(db: AsyncSession) -> User:
    """Get or create a demo user for unauthenticated backtest runs."""
    from sqlalchemy import select

    result = await db.execute(select(User).where(User.id == DEMO_USER_ID))
    user = result.scalar_one_or_none()

    if not user:
        user = User(
            id=DEMO_USER_ID,
            phone="+910000000000",
            full_name="Demo User",
            auth_provider="demo",
            is_verified=True,
            is_active=True,
        )
        db.add(user)
        await db.flush()

    return user


# ==================== Strategy Endpoints ====================

@router.get("/strategies", response_model=StrategyListResponse)
async def list_strategies(
    include_templates: bool = True,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """List available strategies for the current user."""
    strategies = await backtest_service.list_strategies(
        db, current_user.id, include_templates
    )
    return StrategyListResponse(
        strategies=[StrategyResponse.model_validate(s) for s in strategies],
        total=len(strategies),
    )


@router.post("/strategies", response_model=StrategyResponse, status_code=status.HTTP_201_CREATED)
async def create_strategy(
    request: StrategyCreateRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Create a new backtest strategy."""
    strategy = await backtest_service.create_strategy(
        db,
        user_id=current_user.id,
        name=request.name,
        description=request.description,
        strategy_type=request.strategy_type.value,
        entry_rules=request.entry_rules,
        exit_rules=request.exit_rules,
        position_sizing=request.position_sizing,
    )
    await db.commit()
    return StrategyResponse.model_validate(strategy)


@router.get("/strategies/{strategy_id}", response_model=StrategyResponse)
async def get_strategy(
    strategy_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Get a strategy by ID."""
    strategy = await backtest_service.get_strategy(db, strategy_id, current_user.id)

    if not strategy:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Strategy not found",
        )

    return StrategyResponse.model_validate(strategy)


@router.patch("/strategies/{strategy_id}", response_model=StrategyResponse)
async def update_strategy(
    strategy_id: str,
    request: StrategyUpdateRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Update a strategy."""
    updates = request.model_dump(exclude_unset=True)
    strategy = await backtest_service.update_strategy(
        db, strategy_id, current_user.id, **updates
    )

    if not strategy:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Strategy not found or not owned by user",
        )

    await db.commit()
    return StrategyResponse.model_validate(strategy)


@router.delete("/strategies/{strategy_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_strategy(
    strategy_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Delete a strategy."""
    deleted = await backtest_service.delete_strategy(db, strategy_id, current_user.id)

    if not deleted:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Strategy not found, not owned by user, or is a template",
        )

    await db.commit()


# ==================== Backtest Run Endpoints ====================

@router.post("/run", response_model=BacktestRunResponse, status_code=status.HTTP_201_CREATED)
async def start_backtest(
    request: BacktestRunRequest,
    background_tasks: BackgroundTasks,
    current_user: Optional[User] = Depends(get_current_user_optional),
    db: AsyncSession = Depends(get_db),
):
    """Start a new backtest run. Authentication optional - uses demo user if not logged in."""
    # Use demo user if not authenticated
    if current_user is None:
        current_user = await get_or_create_demo_user(db)

    # Validate symbol
    index = await historical_data_service.get_index_by_symbol(db, request.symbol)
    if not index:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Invalid symbol: {request.symbol}",
        )

    # Check data availability
    data_range = await historical_data_service.get_data_date_range(db, request.symbol)
    if not data_range["start_date"]:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"No historical data available for {request.symbol}",
        )

    # Create run
    run = await backtest_service.create_run(
        db,
        user_id=current_user.id,
        symbol=request.symbol,
        start_date=request.start_date,
        end_date=request.end_date,
        initial_capital=Decimal(str(request.initial_capital)),
        strategy_id=request.strategy_id,
        strategy_config=request.strategy_config,
        lot_size=request.lot_size,
        max_positions=request.max_positions,
        position_size_pct=Decimal(str(request.position_size_pct)) if request.position_size_pct else None,
        max_loss_per_trade=Decimal(str(request.max_loss_per_trade)) if request.max_loss_per_trade else None,
        max_daily_loss=Decimal(str(request.max_daily_loss)) if request.max_daily_loss else None,
        stop_loss_pct=Decimal(str(request.stop_loss_pct)) if request.stop_loss_pct else None,
        take_profit_pct=Decimal(str(request.take_profit_pct)) if request.take_profit_pct else None,
    )
    await db.commit()

    # Store run_id for background task
    run_id = str(run.id)

    # Start backtest in background with a new database session
    async def run_backtest_task():
        async with AsyncSessionLocal() as bg_db:
            # Re-fetch the run in this session
            bg_run = await backtest_service.get_run(bg_db, run_id)
            if bg_run:
                await backtest_service.execute_run(bg_db, bg_run)

    background_tasks.add_task(run_backtest_task)

    return BacktestRunResponse.model_validate(run)


@router.get("/runs", response_model=BacktestRunListResponse)
async def list_runs(
    limit: int = 50,
    offset: int = 0,
    current_user: Optional[User] = Depends(get_current_user_optional),
    db: AsyncSession = Depends(get_db),
):
    """List backtest runs for the current user (or demo user if not authenticated)."""
    if current_user is None:
        current_user = await get_or_create_demo_user(db)

    runs, total = await backtest_service.list_runs(
        db, current_user.id, limit, offset
    )
    return BacktestRunListResponse(
        runs=[BacktestRunResponse.model_validate(r) for r in runs],
        total=total,
    )


@router.get("/runs/{run_id}", response_model=BacktestRunWithResultResponse)
async def get_run(
    run_id: str,
    current_user: Optional[User] = Depends(get_current_user_optional),
    db: AsyncSession = Depends(get_db),
):
    """Get a backtest run with its results."""
    if current_user is None:
        current_user = await get_or_create_demo_user(db)

    run = await backtest_service.get_run(db, run_id, current_user.id)

    if not run:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Backtest run not found",
        )

    # Manually construct response to avoid lazy-loading issues
    response_data = {
        "id": str(run.id),
        "user_id": str(run.user_id),
        "strategy_id": str(run.strategy_id) if run.strategy_id else None,
        "symbol": run.symbol,
        "start_date": run.start_date,
        "end_date": run.end_date,
        "initial_capital": float(run.initial_capital),
        "lot_size": run.lot_size,
        "max_positions": run.max_positions,
        "position_size_pct": float(run.position_size_pct) if run.position_size_pct else None,
        "status": run.status,
        "progress": run.progress,
        "error_message": run.error_message,
        "created_at": run.created_at,
        "started_at": run.started_at,
        "completed_at": run.completed_at,
        "result": None,
        "strategy": None,
    }

    # Add result if completed
    if run.status == BacktestStatus.COMPLETED.value:
        result = await backtest_service.get_run_result(db, run_id, current_user.id)
        if result:
            response_data["result"] = BacktestResultResponse.model_validate(result)

    # Add strategy if available
    if run.strategy_id:
        strategy = await backtest_service.get_strategy(db, run.strategy_id, current_user.id)
        if strategy:
            response_data["strategy"] = StrategyResponse.model_validate(strategy)

    return BacktestRunWithResultResponse(**response_data)


@router.delete("/runs/{run_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_run(
    run_id: str,
    current_user: Optional[User] = Depends(get_current_user_optional),
    db: AsyncSession = Depends(get_db),
):
    """Delete a backtest run."""
    if current_user is None:
        current_user = await get_or_create_demo_user(db)

    deleted = await backtest_service.delete_run(db, run_id, current_user.id)

    if not deleted:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Backtest run not found",
        )

    await db.commit()


@router.get("/runs/{run_id}/trades", response_model=TradeListResponse)
async def get_run_trades(
    run_id: str,
    limit: int = 100,
    offset: int = 0,
    current_user: Optional[User] = Depends(get_current_user_optional),
    db: AsyncSession = Depends(get_db),
):
    """Get trades for a backtest run."""
    if current_user is None:
        current_user = await get_or_create_demo_user(db)

    trades, total = await backtest_service.get_run_trades(
        db, run_id, current_user.id, limit, offset
    )

    return TradeListResponse(
        trades=[TradeResponse.model_validate(t) for t in trades],
        total=total,
    )


@router.get("/runs/{run_id}/equity-curve")
async def get_equity_curve(
    run_id: str,
    current_user: Optional[User] = Depends(get_current_user_optional),
    db: AsyncSession = Depends(get_db),
):
    """Get equity curve data for a backtest run."""
    if current_user is None:
        current_user = await get_or_create_demo_user(db)

    result = await backtest_service.get_run_result(db, run_id, current_user.id)

    if not result:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Backtest result not found",
        )

    return {
        "equity_curve": result.equity_curve or [],
        "monthly_returns": result.monthly_returns or [],
    }


# ==================== Historical Data Endpoints ====================

@router.get("/historical/indices", response_model=List[IndexResponse])
async def list_indices(
    db: AsyncSession = Depends(get_db),
):
    """List available indices for backtesting."""
    indices = await historical_data_service.get_indices(db)
    return [IndexResponse.model_validate(i) for i in indices]


@router.get("/historical/spot")
async def get_spot_prices(
    symbol: str,
    start_date: date,
    end_date: date,
    db: AsyncSession = Depends(get_db),
):
    """Get historical spot prices for a symbol."""
    prices = await historical_data_service.get_spot_prices(
        db, symbol, start_date, end_date
    )
    return {
        "symbol": symbol.upper(),
        "start_date": start_date.isoformat(),
        "end_date": end_date.isoformat(),
        "data": [
            {
                "date": p.date.isoformat(),
                "open": float(p.open) if p.open else None,
                "high": float(p.high) if p.high else None,
                "low": float(p.low) if p.low else None,
                "close": float(p.close) if p.close else None,
                "volume": p.volume,
            }
            for p in prices
        ],
    }


@router.get("/historical/options")
async def get_options_chain(
    symbol: str,
    trade_date: date,
    expiry: Optional[date] = None,
    db: AsyncSession = Depends(get_db),
):
    """Get historical option chain for a specific date."""
    options = await historical_data_service.get_options_chain(
        db, symbol, trade_date, expiry
    )
    return {
        "symbol": symbol.upper(),
        "date": trade_date.isoformat(),
        "expiry_filter": expiry.isoformat() if expiry else None,
        "data": [
            {
                "expiry": o.expiry.isoformat(),
                "strike": float(o.strike),
                "option_type": o.option_type,
                "open": float(o.open) if o.open else None,
                "high": float(o.high) if o.high else None,
                "low": float(o.low) if o.low else None,
                "close": float(o.close) if o.close else None,
                "volume": o.volume,
                "open_interest": o.open_interest,
                "iv": float(o.iv) if o.iv else None,
                "delta": float(o.delta) if o.delta else None,
                "gamma": float(o.gamma) if o.gamma else None,
                "theta": float(o.theta) if o.theta else None,
                "vega": float(o.vega) if o.vega else None,
            }
            for o in options
        ],
    }


@router.get("/historical/expiries", response_model=ExpiryListResponse)
async def get_expiries(
    symbol: str,
    trade_date: date,
    db: AsyncSession = Depends(get_db),
):
    """Get available expiry dates for a symbol on a given date."""
    expiries = await historical_data_service.get_available_expiries(
        db, symbol, trade_date
    )
    return ExpiryListResponse(
        symbol=symbol.upper(),
        expiries=expiries,
    )


@router.get("/historical/data-range")
async def get_data_range(
    symbol: str,
    db: AsyncSession = Depends(get_db),
):
    """Get the available date range for a symbol's historical data."""
    data_range = await historical_data_service.get_data_date_range(db, symbol)
    return {
        "symbol": symbol.upper(),
        "start_date": data_range["start_date"].isoformat() if data_range["start_date"] else None,
        "end_date": data_range["end_date"].isoformat() if data_range["end_date"] else None,
    }


# ==================== Admin Endpoints ====================

@router.post("/admin/seed-templates", include_in_schema=False)
async def seed_templates(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Seed default strategy templates (admin only)."""
    # TODO: Add proper admin check
    templates = await backtest_service.create_default_templates(db)
    await db.commit()
    return {
        "message": f"Created {len(templates)} templates",
        "templates": [t.name for t in templates],
    }


@router.post("/admin/seed-indices", include_in_schema=False)
async def seed_indices(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Seed default indices (admin only)."""
    indices = await backtest_service.seed_indices(db)
    await db.commit()
    return {
        "message": f"Created {len(indices)} indices",
        "indices": [i.symbol for i in indices],
    }
