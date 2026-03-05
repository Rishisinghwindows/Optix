"""
Paper Trading Router - API endpoints for virtual trading
"""
from fastapi import APIRouter, HTTPException, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from typing import List, Optional
from datetime import datetime
from decimal import Decimal
from pydantic import BaseModel
import uuid

from app.database import get_db
from app.models import User, PaperPosition, PaperTrade, TradeDirection, OptionType, PositionStatus
from app.services.auth_service import get_current_user

router = APIRouter(prefix="/api/v1/paper-trading", tags=["Paper Trading"])


# ==================== Pydantic Schemas ====================

class CreateTradeRequest(BaseModel):
    symbol: str  # NIFTY, BANKNIFTY, etc.
    strike_price: float
    option_type: str  # CE or PE
    direction: str  # buy or sell
    expiry_date: str  # "05-Feb-2026"
    quantity: int  # Number of lots
    lot_size: int
    price: float  # Entry price


class SquareOffRequest(BaseModel):
    exit_price: float


class PositionResponse(BaseModel):
    id: str
    symbol: str
    strike_price: float
    option_type: str
    direction: str
    expiry_date: str
    quantity: int
    lot_size: int
    entry_price: float
    current_price: Optional[float]
    invested_amount: float
    current_value: float
    pnl: float
    pnl_percent: float
    opened_at: str
    status: str

    class Config:
        from_attributes = True


class TradeResponse(BaseModel):
    id: str
    symbol: str
    strike_price: float
    option_type: str
    direction: str
    expiry_date: str
    quantity: int
    lot_size: int
    price: float
    trade_type: str
    pnl: Optional[float]
    pnl_percent: Optional[float]
    executed_at: str

    class Config:
        from_attributes = True


class PortfolioResponse(BaseModel):
    cash_balance: float
    invested_amount: float
    current_value: float
    total_pnl: float
    total_pnl_percent: float
    open_positions_count: int
    total_trades: int


class PerformanceResponse(BaseModel):
    total_trades: int
    winning_trades: int
    losing_trades: int
    win_rate: float
    total_pnl: float
    best_trade: Optional[float]
    worst_trade: Optional[float]
    average_pnl: float


# ==================== Helper Functions ====================

def position_to_response(position: PaperPosition) -> PositionResponse:
    invested = position.entry_price * position.quantity * position.lot_size
    current = (position.current_price or position.entry_price) * position.quantity * position.lot_size

    if position.direction == TradeDirection.BUY:
        pnl = current - invested
    else:
        pnl = invested - current

    pnl_percent = (pnl / invested * 100) if invested > 0 else 0

    return PositionResponse(
        id=str(position.id),
        symbol=position.symbol,
        strike_price=position.strike_price,
        option_type=position.option_type.value,
        direction=position.direction.value,
        expiry_date=position.expiry_date,
        quantity=position.quantity,
        lot_size=position.lot_size,
        entry_price=position.entry_price,
        current_price=position.current_price,
        invested_amount=invested,
        current_value=current,
        pnl=round(pnl, 2),
        pnl_percent=round(pnl_percent, 2),
        opened_at=position.opened_at.isoformat(),
        status=position.status.value,
    )


def trade_to_response(trade: PaperTrade) -> TradeResponse:
    return TradeResponse(
        id=str(trade.id),
        symbol=trade.symbol,
        strike_price=trade.strike_price,
        option_type=trade.option_type.value,
        direction=trade.direction.value,
        expiry_date=trade.expiry_date,
        quantity=trade.quantity,
        lot_size=trade.lot_size,
        price=trade.price,
        trade_type=trade.trade_type,
        pnl=trade.pnl,
        pnl_percent=trade.pnl_percent,
        executed_at=trade.executed_at.isoformat(),
    )


# ==================== Endpoints ====================

@router.get("/portfolio", response_model=PortfolioResponse)
async def get_portfolio(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Get user's paper trading portfolio summary"""
    # Get open positions
    positions_result = await db.execute(
        select(PaperPosition).where(
            PaperPosition.user_id == current_user.id,
            PaperPosition.status == PositionStatus.OPEN,
        )
    )
    positions = positions_result.scalars().all()

    # Calculate totals
    invested_amount = 0.0
    current_value = 0.0

    for pos in positions:
        invested = pos.entry_price * pos.quantity * pos.lot_size
        current = (pos.current_price or pos.entry_price) * pos.quantity * pos.lot_size
        invested_amount += invested
        current_value += current

    # Get total trades count
    trades_count = await db.execute(
        select(func.count(PaperTrade.id)).where(PaperTrade.user_id == current_user.id)
    )
    total_trades = trades_count.scalar() or 0

    # Calculate P&L
    total_pnl = current_value - invested_amount
    pnl_percent = (total_pnl / invested_amount * 100) if invested_amount > 0 else 0

    return PortfolioResponse(
        cash_balance=float(current_user.paper_trading_balance),
        invested_amount=round(invested_amount, 2),
        current_value=round(current_value, 2),
        total_pnl=round(total_pnl, 2),
        total_pnl_percent=round(pnl_percent, 2),
        open_positions_count=len(positions),
        total_trades=total_trades,
    )


@router.get("/positions", response_model=List[PositionResponse])
async def get_positions(
    status: Optional[str] = Query(None, description="Filter by status: open, closed"),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Get user's paper trading positions"""
    query = select(PaperPosition).where(PaperPosition.user_id == current_user.id)

    if status == "open":
        query = query.where(PaperPosition.status == PositionStatus.OPEN)
    elif status == "closed":
        query = query.where(PaperPosition.status == PositionStatus.CLOSED)

    query = query.order_by(PaperPosition.opened_at.desc())

    result = await db.execute(query)
    positions = result.scalars().all()

    return [position_to_response(pos) for pos in positions]


@router.get("/trades", response_model=List[TradeResponse])
async def get_trades(
    limit: int = Query(50, ge=1, le=200),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Get user's trade history"""
    result = await db.execute(
        select(PaperTrade)
        .where(PaperTrade.user_id == current_user.id)
        .order_by(PaperTrade.executed_at.desc())
        .limit(limit)
    )
    trades = result.scalars().all()

    return [trade_to_response(trade) for trade in trades]


@router.post("/trades", response_model=PositionResponse)
async def create_trade(
    trade: CreateTradeRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Create a new paper trade (open a position)"""
    # Validate option type
    try:
        option_type = OptionType(trade.option_type.upper())
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid option type. Use CE or PE")

    # Validate direction
    try:
        direction = TradeDirection(trade.direction.lower())
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid direction. Use buy or sell")

    # Calculate required margin
    trade_value = trade.price * trade.quantity * trade.lot_size

    # Check if user has enough balance
    if float(current_user.paper_trading_balance) < trade_value:
        raise HTTPException(status_code=400, detail="Insufficient balance for this trade")

    # Create position
    position = PaperPosition(
        user_id=current_user.id,
        symbol=trade.symbol.upper(),
        strike_price=trade.strike_price,
        option_type=option_type,
        direction=direction,
        expiry_date=trade.expiry_date,
        quantity=trade.quantity,
        lot_size=trade.lot_size,
        entry_price=trade.price,
        current_price=trade.price,
        status=PositionStatus.OPEN,
    )
    db.add(position)

    # Create trade record
    trade_record = PaperTrade(
        user_id=current_user.id,
        position_id=position.id,
        symbol=trade.symbol.upper(),
        strike_price=trade.strike_price,
        option_type=option_type,
        direction=direction,
        expiry_date=trade.expiry_date,
        quantity=trade.quantity,
        lot_size=trade.lot_size,
        price=trade.price,
        trade_type="entry",
    )
    db.add(trade_record)

    # Deduct from balance
    current_user.paper_trading_balance = Decimal(str(float(current_user.paper_trading_balance) - trade_value))

    await db.commit()
    await db.refresh(position)

    return position_to_response(position)


@router.post("/positions/{position_id}/square-off", response_model=TradeResponse)
async def square_off_position(
    position_id: str,
    request: SquareOffRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Square off (close) a position"""
    # Get position (IDs are stored as strings)
    result = await db.execute(
        select(PaperPosition).where(
            PaperPosition.id == position_id,
            PaperPosition.user_id == current_user.id,
            PaperPosition.status == PositionStatus.OPEN,
        )
    )
    position = result.scalar_one_or_none()

    if not position:
        raise HTTPException(status_code=404, detail="Position not found or already closed")

    # Calculate P&L
    entry_value = position.entry_price * position.quantity * position.lot_size
    exit_value = request.exit_price * position.quantity * position.lot_size

    if position.direction == TradeDirection.BUY:
        pnl = exit_value - entry_value
    else:
        pnl = entry_value - exit_value

    pnl_percent = (pnl / entry_value * 100) if entry_value > 0 else 0

    # Update position
    position.status = PositionStatus.CLOSED
    position.closed_at = datetime.utcnow()
    position.exit_price = request.exit_price

    # Create exit trade record
    trade_record = PaperTrade(
        user_id=current_user.id,
        position_id=position.id,
        symbol=position.symbol,
        strike_price=position.strike_price,
        option_type=position.option_type,
        direction=TradeDirection.SELL if position.direction == TradeDirection.BUY else TradeDirection.BUY,
        expiry_date=position.expiry_date,
        quantity=position.quantity,
        lot_size=position.lot_size,
        price=request.exit_price,
        trade_type="exit",
        pnl=round(pnl, 2),
        pnl_percent=round(pnl_percent, 2),
    )
    db.add(trade_record)

    # Return balance (entry value + pnl)
    current_user.paper_trading_balance = Decimal(str(float(current_user.paper_trading_balance) + entry_value + pnl))

    await db.commit()
    await db.refresh(trade_record)

    return trade_to_response(trade_record)


@router.post("/portfolio/reset")
async def reset_portfolio(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Reset portfolio to initial balance and close all positions"""
    from sqlalchemy import delete
    await db.execute(delete(PaperTrade).where(PaperTrade.user_id == current_user.id))
    await db.execute(delete(PaperPosition).where(PaperPosition.user_id == current_user.id))

    # Reset balance
    current_user.paper_trading_balance = Decimal("1000000.00")

    await db.commit()

    return {
        "status": "success",
        "message": "Portfolio reset successfully",
        "new_balance": 1000000.00,
    }


@router.get("/performance", response_model=PerformanceResponse)
async def get_performance(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Get trading performance metrics"""
    # Get all exit trades (completed trades)
    result = await db.execute(
        select(PaperTrade).where(
            PaperTrade.user_id == current_user.id,
            PaperTrade.trade_type == "exit",
        )
    )
    exit_trades = result.scalars().all()

    total_trades = len(exit_trades)
    winning_trades = sum(1 for t in exit_trades if t.pnl and t.pnl > 0)
    losing_trades = sum(1 for t in exit_trades if t.pnl and t.pnl < 0)
    win_rate = (winning_trades / total_trades * 100) if total_trades > 0 else 0

    pnls = [t.pnl for t in exit_trades if t.pnl is not None]
    total_pnl = sum(pnls) if pnls else 0
    best_trade = max(pnls) if pnls else None
    worst_trade = min(pnls) if pnls else None
    average_pnl = (total_pnl / len(pnls)) if pnls else 0

    return PerformanceResponse(
        total_trades=total_trades,
        winning_trades=winning_trades,
        losing_trades=losing_trades,
        win_rate=round(win_rate, 2),
        total_pnl=round(total_pnl, 2),
        best_trade=round(best_trade, 2) if best_trade else None,
        worst_trade=round(worst_trade, 2) if worst_trade else None,
        average_pnl=round(average_pnl, 2),
    )


@router.patch("/positions/{position_id}/price")
async def update_position_price(
    position_id: str,
    current_price: float = Query(..., description="Current market price"),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Update position's current price (for real-time P&L)"""
    result = await db.execute(
        select(PaperPosition).where(
            PaperPosition.id == position_id,
            PaperPosition.user_id == current_user.id,
            PaperPosition.status == PositionStatus.OPEN,
        )
    )
    position = result.scalar_one_or_none()

    if not position:
        raise HTTPException(status_code=404, detail="Position not found")

    position.current_price = current_price
    await db.commit()

    return {"status": "success", "current_price": current_price}
