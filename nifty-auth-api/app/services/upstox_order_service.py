"""
Upstox Order Service - Handles order placement, modification, and tracking
"""
import logging
from typing import Optional, Dict, Any, List
from datetime import datetime
from dataclasses import dataclass
from enum import Enum
import httpx

from app.services.upstox_service import upstox_service

logger = logging.getLogger(__name__)


class OrderType(str, Enum):
    MARKET = "MARKET"
    LIMIT = "LIMIT"
    SL = "SL"
    SL_M = "SL-M"


class TransactionType(str, Enum):
    BUY = "BUY"
    SELL = "SELL"


class ProductType(str, Enum):
    INTRADAY = "I"  # MIS - Intraday
    DELIVERY = "D"  # CNC - Delivery (not for F&O)
    CARRYFORWARD = "M"  # NRML - Carryforward


class OrderValidity(str, Enum):
    DAY = "DAY"
    IOC = "IOC"  # Immediate or Cancel


@dataclass
class OrderResponse:
    """Response from order placement"""
    success: bool
    order_id: Optional[str]
    message: str
    status: Optional[str] = None
    details: Optional[Dict[str, Any]] = None


@dataclass
class OrderStatus:
    """Order status details"""
    order_id: str
    status: str
    quantity: int
    filled_quantity: int
    pending_quantity: int
    price: float
    average_price: float
    transaction_type: str
    order_type: str
    trading_symbol: str
    exchange: str
    timestamp: Optional[str] = None


class UpstoxOrderService:
    """
    Service for placing and managing orders via Upstox API.
    Supports both paper trading (simulated) and live trading.
    """

    BASE_URL = "https://api.upstox.com/v2"

    def __init__(self):
        self._client: Optional[httpx.AsyncClient] = None

    async def _get_client(self) -> httpx.AsyncClient:
        """Get or create HTTP client"""
        if self._client is None or self._client.is_closed:
            self._client = httpx.AsyncClient(timeout=30.0)
        return self._client

    def _get_headers(self) -> Dict[str, str]:
        """Get headers with access token from upstox_service"""
        return {
            "Authorization": f"Bearer {upstox_service._access_token}",
            "Accept": "application/json",
            "Content-Type": "application/json",
        }

    def is_authenticated(self) -> bool:
        """Check if Upstox is authenticated"""
        return upstox_service.is_authenticated()

    # ==================== Order Placement ====================

    async def place_order(
        self,
        instrument_key: str,
        quantity: int,
        transaction_type: TransactionType,
        order_type: OrderType = OrderType.MARKET,
        price: float = 0,
        trigger_price: float = 0,
        product_type: ProductType = ProductType.INTRADAY,
        validity: OrderValidity = OrderValidity.DAY,
        is_paper: bool = True
    ) -> OrderResponse:
        """
        Place an order via Upstox API.

        Args:
            instrument_key: Upstox instrument key (e.g., "NSE_FO|NIFTY25FEB24000CE")
            quantity: Number of shares (not lots)
            transaction_type: BUY or SELL
            order_type: MARKET, LIMIT, SL, SL-M
            price: Limit price (for LIMIT orders)
            trigger_price: Trigger price (for SL orders)
            product_type: Intraday (I), Delivery (D), or Carryforward (M)
            validity: DAY or IOC
            is_paper: If True, simulate order (paper trading)

        Returns:
            OrderResponse with order_id and status
        """
        if is_paper:
            return await self._place_paper_order(
                instrument_key, quantity, transaction_type, order_type, price
            )

        if not self.is_authenticated():
            return OrderResponse(
                success=False,
                order_id=None,
                message="Upstox not authenticated. Please connect from Admin dashboard."
            )

        try:
            client = await self._get_client()

            # Build order payload
            payload = {
                "quantity": quantity,
                "product": product_type.value,
                "validity": validity.value,
                "price": price if order_type == OrderType.LIMIT else 0,
                "tag": "AlgoTrade",
                "instrument_token": instrument_key,
                "order_type": order_type.value,
                "transaction_type": transaction_type.value,
                "disclosed_quantity": 0,
                "trigger_price": trigger_price if order_type in [OrderType.SL, OrderType.SL_M] else 0,
                "is_amo": False
            }

            logger.info(f"Placing order: {payload}")

            response = await client.post(
                f"{self.BASE_URL}/order/place",
                json=payload,
                headers=self._get_headers()
            )

            if response.status_code == 200:
                data = response.json()
                order_id = data.get("data", {}).get("order_id")
                logger.info(f"Order placed successfully: {order_id}")
                return OrderResponse(
                    success=True,
                    order_id=order_id,
                    message="Order placed successfully",
                    status="placed",
                    details=data.get("data")
                )
            else:
                error_data = response.json() if response.headers.get("content-type", "").startswith("application/json") else {"message": response.text}
                error_msg = error_data.get("message", error_data.get("errors", [{}])[0].get("message", response.text))
                logger.error(f"Order placement failed: {error_msg}")
                return OrderResponse(
                    success=False,
                    order_id=None,
                    message=f"Order failed: {error_msg}",
                    status="failed",
                    details=error_data
                )

        except Exception as e:
            logger.error(f"Order placement error: {e}")
            return OrderResponse(
                success=False,
                order_id=None,
                message=f"Order error: {str(e)}"
            )

    async def _place_paper_order(
        self,
        instrument_key: str,
        quantity: int,
        transaction_type: TransactionType,
        order_type: OrderType,
        price: float
    ) -> OrderResponse:
        """Simulate order placement for paper trading"""
        import uuid
        paper_order_id = f"PAPER-{uuid.uuid4().hex[:8].upper()}"

        logger.info(
            f"Paper order placed: {paper_order_id} - "
            f"{transaction_type.value} {quantity} of {instrument_key}"
        )

        return OrderResponse(
            success=True,
            order_id=paper_order_id,
            message="Paper order placed successfully",
            status="executed",
            details={
                "order_id": paper_order_id,
                "instrument_key": instrument_key,
                "quantity": quantity,
                "transaction_type": transaction_type.value,
                "order_type": order_type.value,
                "price": price,
                "is_paper": True
            }
        )

    # ==================== Order Modification ====================

    async def modify_order(
        self,
        order_id: str,
        quantity: Optional[int] = None,
        price: Optional[float] = None,
        order_type: Optional[OrderType] = None,
        trigger_price: Optional[float] = None,
        validity: OrderValidity = OrderValidity.DAY,
        is_paper: bool = True
    ) -> OrderResponse:
        """Modify an existing order"""
        if is_paper or order_id.startswith("PAPER-"):
            logger.info(f"Paper order modification: {order_id}")
            return OrderResponse(
                success=True,
                order_id=order_id,
                message="Paper order modified successfully",
                status="modified"
            )

        if not self.is_authenticated():
            return OrderResponse(
                success=False,
                order_id=order_id,
                message="Upstox not authenticated"
            )

        try:
            client = await self._get_client()

            payload = {
                "order_id": order_id,
                "validity": validity.value
            }

            if quantity is not None:
                payload["quantity"] = quantity
            if price is not None:
                payload["price"] = price
            if order_type is not None:
                payload["order_type"] = order_type.value
            if trigger_price is not None:
                payload["trigger_price"] = trigger_price

            response = await client.put(
                f"{self.BASE_URL}/order/modify",
                json=payload,
                headers=self._get_headers()
            )

            if response.status_code == 200:
                data = response.json()
                logger.info(f"Order modified: {order_id}")
                return OrderResponse(
                    success=True,
                    order_id=order_id,
                    message="Order modified successfully",
                    status="modified",
                    details=data.get("data")
                )
            else:
                error_msg = response.json().get("message", response.text)
                return OrderResponse(
                    success=False,
                    order_id=order_id,
                    message=f"Modification failed: {error_msg}"
                )

        except Exception as e:
            logger.error(f"Order modification error: {e}")
            return OrderResponse(
                success=False,
                order_id=order_id,
                message=f"Modification error: {str(e)}"
            )

    # ==================== Order Cancellation ====================

    async def cancel_order(
        self,
        order_id: str,
        is_paper: bool = True
    ) -> OrderResponse:
        """Cancel an existing order"""
        if is_paper or order_id.startswith("PAPER-"):
            logger.info(f"Paper order cancelled: {order_id}")
            return OrderResponse(
                success=True,
                order_id=order_id,
                message="Paper order cancelled successfully",
                status="cancelled"
            )

        if not self.is_authenticated():
            return OrderResponse(
                success=False,
                order_id=order_id,
                message="Upstox not authenticated"
            )

        try:
            client = await self._get_client()

            response = await client.delete(
                f"{self.BASE_URL}/order/cancel",
                params={"order_id": order_id},
                headers=self._get_headers()
            )

            if response.status_code == 200:
                logger.info(f"Order cancelled: {order_id}")
                return OrderResponse(
                    success=True,
                    order_id=order_id,
                    message="Order cancelled successfully",
                    status="cancelled"
                )
            else:
                error_msg = response.json().get("message", response.text)
                return OrderResponse(
                    success=False,
                    order_id=order_id,
                    message=f"Cancellation failed: {error_msg}"
                )

        except Exception as e:
            logger.error(f"Order cancellation error: {e}")
            return OrderResponse(
                success=False,
                order_id=order_id,
                message=f"Cancellation error: {str(e)}"
            )

    # ==================== Order Status ====================

    async def get_order_status(
        self,
        order_id: str,
        is_paper: bool = True
    ) -> Optional[OrderStatus]:
        """Get status of a specific order"""
        if is_paper or order_id.startswith("PAPER-"):
            return OrderStatus(
                order_id=order_id,
                status="complete",
                quantity=0,
                filled_quantity=0,
                pending_quantity=0,
                price=0,
                average_price=0,
                transaction_type="BUY",
                order_type="MARKET",
                trading_symbol="PAPER",
                exchange="NSE_FO"
            )

        if not self.is_authenticated():
            return None

        try:
            client = await self._get_client()

            response = await client.get(
                f"{self.BASE_URL}/order/details",
                params={"order_id": order_id},
                headers=self._get_headers()
            )

            if response.status_code == 200:
                data = response.json().get("data", {})
                return OrderStatus(
                    order_id=data.get("order_id"),
                    status=data.get("status"),
                    quantity=data.get("quantity", 0),
                    filled_quantity=data.get("filled_quantity", 0),
                    pending_quantity=data.get("pending_quantity", 0),
                    price=data.get("price", 0),
                    average_price=data.get("average_price", 0),
                    transaction_type=data.get("transaction_type"),
                    order_type=data.get("order_type"),
                    trading_symbol=data.get("tradingsymbol"),
                    exchange=data.get("exchange"),
                    timestamp=data.get("order_timestamp")
                )
            else:
                logger.error(f"Failed to get order status: {response.text}")
                return None

        except Exception as e:
            logger.error(f"Error getting order status: {e}")
            return None

    async def get_order_book(self, is_paper: bool = True) -> List[Dict[str, Any]]:
        """Get all orders for the day"""
        if is_paper:
            return []

        if not self.is_authenticated():
            return []

        try:
            client = await self._get_client()

            response = await client.get(
                f"{self.BASE_URL}/order/retrieve-all",
                headers=self._get_headers()
            )

            if response.status_code == 200:
                return response.json().get("data", [])
            else:
                logger.error(f"Failed to get order book: {response.text}")
                return []

        except Exception as e:
            logger.error(f"Error getting order book: {e}")
            return []

    # ==================== Positions ====================

    async def get_positions(self, is_paper: bool = True) -> List[Dict[str, Any]]:
        """Get current positions from Upstox"""
        if is_paper:
            return []

        if not self.is_authenticated():
            return []

        try:
            client = await self._get_client()

            response = await client.get(
                f"{self.BASE_URL}/portfolio/short-term-positions",
                headers=self._get_headers()
            )

            if response.status_code == 200:
                return response.json().get("data", [])
            else:
                logger.error(f"Failed to get positions: {response.text}")
                return []

        except Exception as e:
            logger.error(f"Error getting positions: {e}")
            return []

    async def get_holdings(self, is_paper: bool = True) -> List[Dict[str, Any]]:
        """Get holdings from Upstox (for delivery trades)"""
        if is_paper:
            return []

        if not self.is_authenticated():
            return []

        try:
            client = await self._get_client()

            response = await client.get(
                f"{self.BASE_URL}/portfolio/long-term-holdings",
                headers=self._get_headers()
            )

            if response.status_code == 200:
                return response.json().get("data", [])
            else:
                logger.error(f"Failed to get holdings: {response.text}")
                return []

        except Exception as e:
            logger.error(f"Error getting holdings: {e}")
            return []

    # ==================== Fund Information ====================

    async def get_funds_and_margin(self, is_paper: bool = True) -> Dict[str, Any]:
        """Get available funds and margin"""
        if is_paper:
            return {
                "available_margin": 100000.0,
                "used_margin": 0.0,
                "payin_amount": 0.0,
                "is_paper": True
            }

        if not self.is_authenticated():
            return {}

        try:
            client = await self._get_client()

            response = await client.get(
                f"{self.BASE_URL}/user/get-funds-and-margin",
                headers=self._get_headers()
            )

            if response.status_code == 200:
                data = response.json().get("data", {})
                # Extract relevant margin info (NSE segment)
                nse_data = data.get("equity", {})
                return {
                    "available_margin": nse_data.get("available_margin", 0),
                    "used_margin": nse_data.get("used_margin", 0),
                    "payin_amount": nse_data.get("payin_amount", 0),
                    "is_paper": False
                }
            else:
                logger.error(f"Failed to get funds: {response.text}")
                return {}

        except Exception as e:
            logger.error(f"Error getting funds: {e}")
            return {}

    async def close(self) -> None:
        """Close HTTP client"""
        if self._client and not self._client.is_closed:
            await self._client.aclose()


# Singleton instance
order_service = UpstoxOrderService()
