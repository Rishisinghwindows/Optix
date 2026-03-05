"""
Algo Trading Router - REST API endpoints for algorithmic trading
"""
import logging
from typing import Optional, List
from datetime import datetime, date
from fastapi import APIRouter, HTTPException, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, and_, desc
import uuid

from app.database import get_db
from app.models.algo_trading import (
    AlgoConfig, AlgoTrade, AlgoPosition, AlgoLog, AlgoDailyStats,
    AlgoPositionStatus, AlgoOrderStatus
)
from app.schemas.algo_trading import (
    AlgoConfigCreate, AlgoConfigUpdate, AlgoConfigResponse,
    AlgoPositionResponse, AlgoTradeResponse, AlgoLogResponse,
    AlgoDailyStatsResponse, AlgoStatusResponse,
    AlgoStartRequest, AlgoStopRequest,
    ManualEntryRequest, ManualExitRequest,
    SignalType, OptionType
)
from app.services.algo_engine import algo_engine
from app.services.risk_manager import risk_manager
from app.services.upstox_order_service import order_service

logger = logging.getLogger(__name__)

router = APIRouter(
    prefix="/api/v1/algo",
    tags=["Algo Trading"],
)


# ==================== Engine Control ====================

@router.post("/start", summary="Start algo trading engine")
async def start_algo_trading(
    request: AlgoStartRequest,
    db: AsyncSession = Depends(get_db)
):
    """
    Start the algorithmic trading engine.

    - **config_id**: Optional config ID to use (uses default if not provided)
    - **paper_mode**: If True, runs in paper trading mode (default: True)
    """
    success, message = await algo_engine.start(
        config_id=request.config_id,
        paper_mode=request.paper_mode
    )

    if not success:
        raise HTTPException(status_code=400, detail=message)

    return {
        "success": True,
        "message": message,
        "status": await algo_engine.get_status()
    }


@router.post("/stop", summary="Stop algo trading engine")
async def stop_algo_trading(
    request: AlgoStopRequest,
    db: AsyncSession = Depends(get_db)
):
    """
    Stop the algorithmic trading engine.

    - **close_all_positions**: If True, closes all open positions
    - **reason**: Optional reason for stopping
    """
    success, message = await algo_engine.stop(
        close_all_positions=request.close_all_positions,
        reason=request.reason or "Manual stop"
    )

    if not success:
        raise HTTPException(status_code=400, detail=message)

    return {
        "success": True,
        "message": message
    }


@router.post("/kill-switch", summary="Emergency stop with position closure")
async def kill_switch(db: AsyncSession = Depends(get_db)):
    """
    Emergency stop - immediately stops the engine and closes all positions.
    Use this in case of unexpected market conditions or system issues.
    """
    success, message = await algo_engine.stop(
        close_all_positions=True,
        reason="Kill switch activated"
    )

    return {
        "success": success,
        "message": message,
        "action": "All positions closed and engine stopped"
    }


@router.get("/status", summary="Get algo trading status", response_model=dict)
async def get_algo_status():
    """Get current status of the algo trading engine."""
    return await algo_engine.get_status()


# ==================== Configuration ====================

@router.get("/configs", summary="List all configs", response_model=List[AlgoConfigResponse])
async def list_configs(db: AsyncSession = Depends(get_db)):
    """Get all algo trading configurations."""
    result = await db.execute(
        select(AlgoConfig).order_by(desc(AlgoConfig.created_at))
    )
    configs = result.scalars().all()
    return configs


@router.post("/configs", summary="Create config", response_model=AlgoConfigResponse)
async def create_config(
    config: AlgoConfigCreate,
    db: AsyncSession = Depends(get_db)
):
    """Create a new algo trading configuration."""
    db_config = AlgoConfig(
        id=str(uuid.uuid4()),
        **config.model_dump()
    )
    db.add(db_config)
    await db.commit()
    await db.refresh(db_config)
    return db_config


@router.get("/configs/{config_id}", summary="Get config", response_model=AlgoConfigResponse)
async def get_config(
    config_id: str,
    db: AsyncSession = Depends(get_db)
):
    """Get a specific configuration."""
    result = await db.execute(
        select(AlgoConfig).where(AlgoConfig.id == config_id)
    )
    config = result.scalar_one_or_none()
    if not config:
        raise HTTPException(status_code=404, detail="Config not found")
    return config


@router.put("/configs/{config_id}", summary="Update config", response_model=AlgoConfigResponse)
async def update_config(
    config_id: str,
    config_update: AlgoConfigUpdate,
    db: AsyncSession = Depends(get_db)
):
    """Update an existing configuration."""
    result = await db.execute(
        select(AlgoConfig).where(AlgoConfig.id == config_id)
    )
    config = result.scalar_one_or_none()
    if not config:
        raise HTTPException(status_code=404, detail="Config not found")

    # Update only provided fields
    update_data = config_update.model_dump(exclude_unset=True)
    for key, value in update_data.items():
        setattr(config, key, value)

    config.updated_at = datetime.utcnow()
    await db.commit()
    await db.refresh(config)
    return config


@router.delete("/configs/{config_id}", summary="Delete config")
async def delete_config(
    config_id: str,
    db: AsyncSession = Depends(get_db)
):
    """Delete a configuration (only if not active)."""
    result = await db.execute(
        select(AlgoConfig).where(AlgoConfig.id == config_id)
    )
    config = result.scalar_one_or_none()
    if not config:
        raise HTTPException(status_code=404, detail="Config not found")

    if config.is_active:
        raise HTTPException(
            status_code=400,
            detail="Cannot delete active config. Stop the engine first."
        )

    await db.delete(config)
    await db.commit()
    return {"success": True, "message": "Config deleted"}


# ==================== Positions ====================

@router.get("/positions", summary="Get positions")
async def get_positions(
    status: Optional[str] = Query(None, description="Filter by status (open/closed)"),
    limit: int = Query(50, ge=1, le=100),
    db: AsyncSession = Depends(get_db)
):
    """Get algo trading positions."""
    query = select(AlgoPosition).order_by(desc(AlgoPosition.opened_at))

    if status:
        if status == "open":
            query = query.where(AlgoPosition.status == AlgoPositionStatus.OPEN)
        elif status == "closed":
            query = query.where(AlgoPosition.status != AlgoPositionStatus.OPEN)

    query = query.limit(limit)
    result = await db.execute(query)
    positions = result.scalars().all()

    return [
        {
            "id": p.id,
            "symbol": p.symbol,
            "strike_price": p.strike_price,
            "option_type": p.option_type.value,
            "expiry_date": p.expiry_date,
            "quantity": p.quantity,
            "lot_size": p.lot_size,
            "entry_price": p.entry_price,
            "current_price": p.current_price,
            "stop_loss_price": p.stop_loss_price,
            "target_price": p.target_price,
            "trailing_stop_active": p.trailing_stop_active,
            "trailing_stop_price": p.trailing_stop_price,
            "status": p.status.value,
            "exit_price": p.exit_price,
            "exit_reason": p.exit_reason.value if p.exit_reason else None,
            "entry_signal": p.entry_signal.value if p.entry_signal else None,
            "is_paper": p.is_paper,
            "invested_amount": p.invested_amount,
            "unrealized_pnl": p.unrealized_pnl if p.status == AlgoPositionStatus.OPEN else 0,
            "realized_pnl": p.realized_pnl if p.status != AlgoPositionStatus.OPEN else 0,
            "opened_at": p.opened_at.isoformat() if p.opened_at else None,
            "closed_at": p.closed_at.isoformat() if p.closed_at else None
        }
        for p in positions
    ]


@router.get("/positions/open", summary="Get open positions")
async def get_open_positions():
    """Get currently open positions from the engine."""
    return await algo_engine.get_open_positions()


@router.post("/positions/close/{position_id}", summary="Manually close position")
async def close_position_manual(
    position_id: str,
    request: ManualExitRequest,
    db: AsyncSession = Depends(get_db)
):
    """Manually close an open position."""
    result = await db.execute(
        select(AlgoPosition).where(
            and_(
                AlgoPosition.id == position_id,
                AlgoPosition.status == AlgoPositionStatus.OPEN
            )
        )
    )
    position = result.scalar_one_or_none()
    if not position:
        raise HTTPException(status_code=404, detail="Open position not found")

    # Import here to avoid circular import
    from app.services.algo_engine import algo_engine
    from app.models.algo_trading import ExitReason

    # Close the position
    # This is a simplified version - the engine has the full logic
    position.status = AlgoPositionStatus.CLOSED
    position.exit_reason = ExitReason.MANUAL
    position.closed_at = datetime.utcnow()
    position.exit_price = position.current_price or position.entry_price

    await db.commit()

    return {
        "success": True,
        "message": f"Position {position_id} closed manually",
        "exit_price": position.exit_price
    }


# ==================== Trades ====================

@router.get("/trades", summary="Get trade history", response_model=List[AlgoTradeResponse])
async def get_trades(
    trade_type: Optional[str] = Query(None, description="Filter by type (entry/exit)"),
    symbol: Optional[str] = Query(None, description="Filter by symbol"),
    limit: int = Query(50, ge=1, le=200),
    db: AsyncSession = Depends(get_db)
):
    """Get algo trading history."""
    query = select(AlgoTrade).order_by(desc(AlgoTrade.created_at))

    if trade_type:
        query = query.where(AlgoTrade.trade_type == trade_type)
    if symbol:
        query = query.where(AlgoTrade.symbol == symbol.upper())

    query = query.limit(limit)
    result = await db.execute(query)
    return result.scalars().all()


@router.get("/trades/summary", summary="Get trade summary")
async def get_trade_summary(
    start_date: Optional[str] = Query(None, description="Start date (YYYY-MM-DD)"),
    end_date: Optional[str] = Query(None, description="End date (YYYY-MM-DD)"),
    db: AsyncSession = Depends(get_db)
):
    """Get summary of trading activity."""
    query = select(AlgoTrade).where(AlgoTrade.trade_type == "exit")

    if start_date:
        start_dt = datetime.strptime(start_date, "%Y-%m-%d")
        query = query.where(AlgoTrade.executed_at >= start_dt)
    if end_date:
        end_dt = datetime.strptime(end_date, "%Y-%m-%d")
        query = query.where(AlgoTrade.executed_at <= end_dt)

    result = await db.execute(query)
    trades = result.scalars().all()

    total_trades = len(trades)
    winning_trades = len([t for t in trades if (t.pnl or 0) > 0])
    losing_trades = len([t for t in trades if (t.pnl or 0) < 0])
    total_pnl = sum(t.pnl or 0 for t in trades)

    return {
        "total_trades": total_trades,
        "winning_trades": winning_trades,
        "losing_trades": losing_trades,
        "win_rate": (winning_trades / total_trades * 100) if total_trades > 0 else 0,
        "total_pnl": round(total_pnl, 2),
        "average_pnl": round(total_pnl / total_trades, 2) if total_trades > 0 else 0,
        "largest_win": max((t.pnl or 0 for t in trades), default=0),
        "largest_loss": min((t.pnl or 0 for t in trades), default=0)
    }


# ==================== Signals ====================

@router.get("/signals/current", summary="Get current signal")
async def get_current_signal():
    """Get the most recent signal generated by the engine."""
    signal = algo_engine.last_signal
    if not signal:
        return {"signal": None, "message": "No signal generated yet"}

    return {
        "symbol": signal.symbol,
        "signal_type": signal.signal_type.value,
        "signal_score": signal.signal_score,
        "pcr": signal.pcr,
        "max_pain": signal.max_pain,
        "vix": signal.vix,
        "spot_price": signal.spot_price,
        "recommended_option_type": signal.recommended_option_type.value,
        "recommended_strike": signal.recommended_strike,
        "recommended_expiry": signal.recommended_expiry,
        "timestamp": signal.timestamp.isoformat()
    }


@router.post("/signals/generate", summary="Generate signal manually")
async def generate_signal_manual(
    symbol: str = Query(..., description="Index symbol (NIFTY/BANKNIFTY)")
):
    """Manually trigger signal generation for a symbol."""
    try:
        signal = await algo_engine._generate_signal(symbol.upper())
        if not signal:
            return {"success": False, "message": "Could not generate signal"}

        return {
            "success": True,
            "signal": {
                "symbol": signal.symbol,
                "signal_type": signal.signal_type.value,
                "signal_score": signal.signal_score,
                "pcr": signal.pcr,
                "max_pain": signal.max_pain,
                "vix": signal.vix,
                "spot_price": signal.spot_price,
                "recommended_option_type": signal.recommended_option_type.value,
                "recommended_strike": signal.recommended_strike,
                "recommended_expiry": signal.recommended_expiry,
                "timestamp": signal.timestamp.isoformat()
            }
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ==================== Manual Trading ====================

@router.post("/manual/entry", summary="Manual trade entry")
async def manual_entry(
    request: ManualEntryRequest,
    db: AsyncSession = Depends(get_db)
):
    """
    Manually place an entry trade (bypass signal generation).
    Useful for testing or discretionary entries.
    """
    from app.services.upstox_service import upstox_service
    from app.services.upstox_order_service import TransactionType, OrderType
    from app.models.algo_trading import AlgoOptionType

    try:
        # Get option chain to find instrument details
        chain_data = await upstox_service.get_option_chain(
            request.symbol.upper(), request.expiry_date, strikes_around_atm=10
        )

        if not chain_data:
            raise HTTPException(status_code=400, detail="Could not get option chain")

        # Find the option
        option_data = None
        for row in chain_data.get("data", []):
            if row.get("strikePrice") == request.strike_price:
                opt_key = request.option_type.value
                if row.get(opt_key):
                    option_data = row[opt_key]
                break

        if not option_data:
            raise HTTPException(status_code=404, detail="Option not found in chain")

        entry_price = option_data.get("lastPrice", 0)
        instrument_key = option_data.get("instrumentKey")
        trading_symbol = option_data.get("tradingSymbol")
        lot_size = chain_data.get("lotSize", 50)

        # Place order
        quantity = request.quantity * lot_size
        order_result = await order_service.place_order(
            instrument_key=instrument_key,
            quantity=quantity,
            transaction_type=TransactionType.BUY,
            order_type=OrderType.MARKET,
            is_paper=request.paper_mode
        )

        if not order_result.success:
            raise HTTPException(status_code=400, detail=order_result.message)

        # Create position record
        position = AlgoPosition(
            id=str(uuid.uuid4()),
            symbol=request.symbol.upper(),
            strike_price=request.strike_price,
            option_type=AlgoOptionType(request.option_type.value),
            expiry_date=request.expiry_date,
            quantity=request.quantity,
            lot_size=lot_size,
            entry_price=entry_price,
            current_price=entry_price,
            highest_price=entry_price,
            stop_loss_price=entry_price * 0.7,  # Default 30% SL
            target_price=entry_price * 1.5,  # Default 50% target
            status=AlgoPositionStatus.OPEN,
            instrument_key=instrument_key,
            trading_symbol=trading_symbol,
            is_paper=request.paper_mode,
            opened_at=datetime.utcnow()
        )
        db.add(position)

        # Create trade record
        trade = AlgoTrade(
            id=str(uuid.uuid4()),
            position_id=position.id,
            symbol=request.symbol.upper(),
            strike_price=request.strike_price,
            option_type=AlgoOptionType(request.option_type.value),
            expiry_date=request.expiry_date,
            order_id=order_result.order_id,
            order_status=AlgoOrderStatus.EXECUTED,
            trade_type="entry",
            quantity=request.quantity,
            lot_size=lot_size,
            order_price=entry_price,
            executed_price=entry_price,
            instrument_key=instrument_key,
            trading_symbol=trading_symbol,
            is_paper=request.paper_mode,
            executed_at=datetime.utcnow()
        )
        db.add(trade)
        await db.commit()

        return {
            "success": True,
            "position_id": position.id,
            "order_id": order_result.order_id,
            "entry_price": entry_price,
            "quantity": request.quantity,
            "lot_size": lot_size,
            "invested_amount": entry_price * request.quantity * lot_size,
            "is_paper": request.paper_mode
        }

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Manual entry error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ==================== Logs ====================

@router.get("/logs", summary="Get algo logs", response_model=List[AlgoLogResponse])
async def get_logs(
    level: Optional[str] = Query(None, description="Filter by level (INFO/WARNING/ERROR)"),
    category: Optional[str] = Query(None, description="Filter by category"),
    limit: int = Query(100, ge=1, le=500),
    db: AsyncSession = Depends(get_db)
):
    """Get algo trading logs."""
    query = select(AlgoLog).order_by(desc(AlgoLog.created_at))

    if level:
        query = query.where(AlgoLog.level == level.upper())
    if category:
        query = query.where(AlgoLog.category == category.upper())

    query = query.limit(limit)
    result = await db.execute(query)
    return result.scalars().all()


# ==================== Daily Stats ====================

@router.get("/stats/daily", summary="Get daily statistics")
async def get_daily_stats(
    date_str: Optional[str] = Query(None, description="Date (YYYY-MM-DD)"),
    db: AsyncSession = Depends(get_db)
):
    """Get daily trading statistics."""
    if not date_str:
        date_str = date.today().isoformat()

    result = await db.execute(
        select(AlgoDailyStats).where(AlgoDailyStats.trading_date == date_str)
    )
    stats = result.scalar_one_or_none()

    if not stats:
        # Return live stats from risk manager
        live_stats = risk_manager.get_daily_stats()
        return {
            "trading_date": date_str,
            "total_trades": live_stats["daily_trades"],
            "daily_pnl": live_stats["daily_pnl"],
            "source": "live"
        }

    return {
        "trading_date": stats.trading_date,
        "total_trades": stats.total_trades,
        "winning_trades": stats.winning_trades,
        "losing_trades": stats.losing_trades,
        "win_rate": (stats.winning_trades / stats.total_trades * 100) if stats.total_trades > 0 else 0,
        "gross_pnl": stats.gross_pnl,
        "net_pnl": stats.net_pnl,
        "max_drawdown": stats.max_drawdown,
        "signals_generated": stats.signals_generated,
        "signals_acted_on": stats.signals_acted_on,
        "source": "database"
    }


# ==================== Risk Info ====================

@router.get("/risk/status", summary="Get risk management status")
async def get_risk_status():
    """Get current risk management status."""
    stats = risk_manager.get_daily_stats()
    config = algo_engine.config

    return {
        "daily_pnl": stats["daily_pnl"],
        "daily_trades": stats["daily_trades"],
        "daily_loss_limit": config.capital * config.max_daily_loss if config else 0,
        "daily_loss_limit_hit": risk_manager.is_daily_loss_limit_hit(config) if config else False,
        "max_positions": config.max_positions if config else 2,
        "risk_per_trade": config.risk_per_trade if config else 0.02,
        "stop_loss_pct": config.stop_loss_pct if config else 0.30,
        "target_pct": config.target_pct if config else 0.50
    }
