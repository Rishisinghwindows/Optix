"""
Trade Journal API Router — CRUD operations for trade journal entries
"""

import logging
from collections import Counter
from datetime import datetime
from typing import Optional
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import select, func, and_
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models.trade_journal import TradeJournal
from app.models.user import User
from app.schemas.trade_journal import (
    JournalCreate,
    JournalUpdate,
    JournalResponse,
    JournalListResponse,
    JournalStats,
)
from app.routers.auth import get_current_user

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/journal", tags=["Trade Journal"])


# ============== Helper Functions ==============

def journal_to_response(entry: TradeJournal) -> JournalResponse:
    """Convert TradeJournal model to response schema"""
    return JournalResponse(
        id=entry.id,
        user_id=entry.user_id,
        symbol=entry.symbol,
        strike_price=entry.strike_price,
        option_type=entry.option_type,
        direction=entry.direction,
        entry_price=entry.entry_price,
        exit_price=entry.exit_price,
        quantity=entry.quantity,
        lot_size=entry.lot_size,
        entry_date=entry.entry_date,
        exit_date=entry.exit_date,
        expiry_date=entry.expiry_date,
        realized_pnl=entry.realized_pnl,
        realized_pnl_percent=entry.realized_pnl_percent,
        title=entry.title,
        notes=entry.notes,
        tags=entry.tags,
        market_condition=entry.market_condition,
        mood=entry.mood,
        outcome=entry.outcome,
        paper_position_id=entry.paper_position_id,
        created_at=entry.created_at,
        updated_at=entry.updated_at,
    )


def compute_pnl(entry_price: float, exit_price: float, quantity: int, lot_size: int, direction: str) -> tuple:
    """Compute realized P&L and percentage"""
    direction_mult = 1.0 if direction == "buy" else -1.0
    pnl = (exit_price - entry_price) * quantity * lot_size * direction_mult
    investment = entry_price * quantity * lot_size
    pnl_percent = (pnl / investment * 100) if investment > 0 else 0
    return round(pnl, 2), round(pnl_percent, 2)


# ============== CRUD Endpoints ==============

@router.post("", response_model=JournalResponse)
async def create_journal_entry(
    data: JournalCreate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Create a new trade journal entry"""
    # Auto-compute P&L if exit price is provided
    realized_pnl = None
    realized_pnl_percent = None
    outcome = data.outcome
    if data.exit_price is not None:
        realized_pnl, realized_pnl_percent = compute_pnl(
            data.entry_price, data.exit_price, data.quantity, data.lot_size, data.direction.value
        )
        if outcome is None:
            if realized_pnl > 0:
                outcome = "profit"
            elif realized_pnl < 0:
                outcome = "loss"
            else:
                outcome = "breakeven"

    entry = TradeJournal(
        user_id=current_user.id,
        symbol=data.symbol.upper(),
        strike_price=data.strike_price,
        option_type=data.option_type.value,
        direction=data.direction.value,
        entry_price=data.entry_price,
        exit_price=data.exit_price,
        quantity=data.quantity,
        lot_size=data.lot_size,
        entry_date=data.entry_date,
        exit_date=data.exit_date,
        expiry_date=data.expiry_date,
        realized_pnl=realized_pnl,
        realized_pnl_percent=realized_pnl_percent,
        title=data.title,
        notes=data.notes,
        tags=data.tags,
        market_condition=data.market_condition.value if data.market_condition else None,
        mood=data.mood.value if data.mood else None,
        outcome=outcome.value if hasattr(outcome, 'value') else outcome,
        paper_position_id=data.paper_position_id,
    )

    db.add(entry)
    await db.commit()
    await db.refresh(entry)

    logger.info(f"Journal entry created: {entry.id} for user {current_user.id}")
    return journal_to_response(entry)


@router.get("", response_model=JournalListResponse)
async def list_journal_entries(
    date_from: Optional[datetime] = Query(None, description="Filter from date"),
    date_to: Optional[datetime] = Query(None, description="Filter to date"),
    tag: Optional[str] = Query(None, description="Filter by tag"),
    outcome: Optional[str] = Query(None, description="Filter by outcome (profit/loss/breakeven)"),
    symbol: Optional[str] = Query(None, description="Filter by symbol"),
    mood: Optional[str] = Query(None, description="Filter by mood"),
    limit: int = Query(50, ge=1, le=200, description="Max entries"),
    offset: int = Query(0, ge=0, description="Offset for pagination"),
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """List journal entries with optional filters"""
    query = select(TradeJournal).where(TradeJournal.user_id == current_user.id)

    if date_from:
        query = query.where(TradeJournal.entry_date >= date_from)
    if date_to:
        query = query.where(TradeJournal.entry_date <= date_to)
    if tag:
        query = query.where(TradeJournal.tags.contains(tag))
    if outcome:
        query = query.where(TradeJournal.outcome == outcome)
    if symbol:
        query = query.where(TradeJournal.symbol == symbol.upper())
    if mood:
        query = query.where(TradeJournal.mood == mood)

    # Get total count
    count_query = select(func.count()).select_from(query.subquery())
    total = (await db.execute(count_query)).scalar() or 0

    # Get entries
    query = query.order_by(TradeJournal.entry_date.desc()).offset(offset).limit(limit)
    result = await db.execute(query)
    entries = result.scalars().all()

    return JournalListResponse(
        entries=[journal_to_response(e) for e in entries],
        total=total,
    )


@router.get("/stats", response_model=JournalStats)
async def get_journal_stats(
    date_from: Optional[datetime] = Query(None),
    date_to: Optional[datetime] = Query(None),
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Get journal statistics"""
    query = select(TradeJournal).where(TradeJournal.user_id == current_user.id)

    if date_from:
        query = query.where(TradeJournal.entry_date >= date_from)
    if date_to:
        query = query.where(TradeJournal.entry_date <= date_to)

    result = await db.execute(query)
    entries = result.scalars().all()

    total = len(entries)
    winning = sum(1 for e in entries if e.outcome == "profit")
    losing = sum(1 for e in entries if e.outcome == "loss")
    breakeven = sum(1 for e in entries if e.outcome == "breakeven")
    win_rate = (winning / total * 100) if total > 0 else 0

    pnls = [e.realized_pnl for e in entries if e.realized_pnl is not None]
    total_pnl = sum(pnls) if pnls else 0
    avg_pnl = (total_pnl / len(pnls)) if pnls else 0
    best_trade = max(pnls) if pnls else None
    worst_trade = min(pnls) if pnls else None

    # Average holding days
    holding_days = []
    for e in entries:
        if e.exit_date and e.entry_date:
            days = (e.exit_date - e.entry_date).total_seconds() / 86400
            holding_days.append(days)
    avg_holding = (sum(holding_days) / len(holding_days)) if holding_days else None

    # Most traded symbol
    symbols = [e.symbol for e in entries]
    most_traded = Counter(symbols).most_common(1)[0][0] if symbols else None

    # Most used tags
    all_tags = []
    for e in entries:
        if e.tags:
            all_tags.extend(t.strip() for t in e.tags.split(",") if t.strip())
    top_tags = [t for t, _ in Counter(all_tags).most_common(5)]

    # Trades by mood
    mood_counts = Counter(e.mood for e in entries if e.mood)
    condition_counts = Counter(e.market_condition for e in entries if e.market_condition)

    return JournalStats(
        total_trades=total,
        winning_trades=winning,
        losing_trades=losing,
        breakeven_trades=breakeven,
        win_rate=round(win_rate, 1),
        total_pnl=round(total_pnl, 2),
        average_pnl=round(avg_pnl, 2),
        best_trade=round(best_trade, 2) if best_trade is not None else None,
        worst_trade=round(worst_trade, 2) if worst_trade is not None else None,
        average_holding_days=round(avg_holding, 1) if avg_holding is not None else None,
        most_traded_symbol=most_traded,
        most_used_tags=top_tags,
        trades_by_mood=dict(mood_counts),
        trades_by_condition=dict(condition_counts),
    )


@router.get("/{entry_id}", response_model=JournalResponse)
async def get_journal_entry(
    entry_id: str,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Get a single journal entry"""
    result = await db.execute(
        select(TradeJournal).where(
            and_(TradeJournal.id == entry_id, TradeJournal.user_id == current_user.id)
        )
    )
    entry = result.scalar_one_or_none()
    if not entry:
        raise HTTPException(status_code=404, detail="Journal entry not found")
    return journal_to_response(entry)


@router.put("/{entry_id}", response_model=JournalResponse)
async def update_journal_entry(
    entry_id: str,
    data: JournalUpdate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Update a journal entry"""
    result = await db.execute(
        select(TradeJournal).where(
            and_(TradeJournal.id == entry_id, TradeJournal.user_id == current_user.id)
        )
    )
    entry = result.scalar_one_or_none()
    if not entry:
        raise HTTPException(status_code=404, detail="Journal entry not found")

    update_data = data.model_dump(exclude_unset=True)

    for field, value in update_data.items():
        if hasattr(value, 'value'):
            value = value.value
        setattr(entry, field, value)

    # Recompute P&L if exit_price changed
    if "exit_price" in update_data and entry.exit_price is not None:
        pnl, pnl_pct = compute_pnl(
            entry.entry_price, entry.exit_price, entry.quantity, entry.lot_size, entry.direction
        )
        entry.realized_pnl = pnl
        entry.realized_pnl_percent = pnl_pct
        if entry.outcome is None:
            entry.outcome = "profit" if pnl > 0 else ("loss" if pnl < 0 else "breakeven")

    await db.commit()
    await db.refresh(entry)

    logger.info(f"Journal entry updated: {entry.id}")
    return journal_to_response(entry)


@router.delete("/{entry_id}")
async def delete_journal_entry(
    entry_id: str,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Delete a journal entry"""
    result = await db.execute(
        select(TradeJournal).where(
            and_(TradeJournal.id == entry_id, TradeJournal.user_id == current_user.id)
        )
    )
    entry = result.scalar_one_or_none()
    if not entry:
        raise HTTPException(status_code=404, detail="Journal entry not found")

    await db.delete(entry)
    await db.commit()

    logger.info(f"Journal entry deleted: {entry_id}")
    return {"message": "Journal entry deleted", "id": entry_id}
