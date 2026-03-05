"""
Paper Trading Models - For virtual trading functionality
"""
from datetime import datetime
from sqlalchemy import Column, String, DateTime, ForeignKey, Float, Integer, Boolean, Enum as SQLEnum
from sqlalchemy.orm import relationship
import uuid
import enum

from app.database import Base


class TradeDirection(str, enum.Enum):
    BUY = "buy"
    SELL = "sell"


class OptionType(str, enum.Enum):
    CALL = "CE"
    PUT = "PE"


class PositionStatus(str, enum.Enum):
    OPEN = "open"
    CLOSED = "closed"


class PaperPosition(Base):
    """Paper trading open positions"""
    __tablename__ = "paper_positions"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)

    # Position details
    symbol = Column(String(20), nullable=False)  # NIFTY, BANKNIFTY, etc.
    strike_price = Column(Float, nullable=False)
    option_type = Column(SQLEnum(OptionType), nullable=False)
    direction = Column(SQLEnum(TradeDirection), nullable=False)
    expiry_date = Column(String(20), nullable=False)  # "05-Feb-2026"

    # Quantity and pricing
    quantity = Column(Integer, nullable=False)  # Number of lots
    lot_size = Column(Integer, nullable=False)
    entry_price = Column(Float, nullable=False)
    current_price = Column(Float, nullable=True)

    # Status
    status = Column(SQLEnum(PositionStatus), default=PositionStatus.OPEN)

    # Timestamps
    opened_at = Column(DateTime, default=datetime.utcnow)
    closed_at = Column(DateTime, nullable=True)
    exit_price = Column(Float, nullable=True)

    # Relationships
    user = relationship("User", back_populates="paper_positions")

    @property
    def invested_amount(self):
        return self.entry_price * self.quantity * self.lot_size

    @property
    def current_value(self):
        price = self.current_price or self.entry_price
        return price * self.quantity * self.lot_size

    @property
    def pnl(self):
        if self.status == PositionStatus.CLOSED:
            exit_val = self.exit_price * self.quantity * self.lot_size
            entry_val = self.entry_price * self.quantity * self.lot_size
            return exit_val - entry_val if self.direction == TradeDirection.BUY else entry_val - exit_val
        else:
            if self.direction == TradeDirection.BUY:
                return self.current_value - self.invested_amount
            else:
                return self.invested_amount - self.current_value


class PaperTrade(Base):
    """Paper trading transaction history"""
    __tablename__ = "paper_trades"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    position_id = Column(String(36), ForeignKey("paper_positions.id", ondelete="SET NULL"), nullable=True)

    # Trade details
    symbol = Column(String(20), nullable=False)
    strike_price = Column(Float, nullable=False)
    option_type = Column(SQLEnum(OptionType), nullable=False)
    direction = Column(SQLEnum(TradeDirection), nullable=False)
    expiry_date = Column(String(20), nullable=False)

    # Execution details
    quantity = Column(Integer, nullable=False)
    lot_size = Column(Integer, nullable=False)
    price = Column(Float, nullable=False)
    trade_type = Column(String(10), nullable=False)  # "entry" or "exit"

    # P&L (for exit trades)
    pnl = Column(Float, nullable=True)
    pnl_percent = Column(Float, nullable=True)

    # Timestamps
    executed_at = Column(DateTime, default=datetime.utcnow)

    # Relationships
    user = relationship("User", back_populates="paper_trades")
