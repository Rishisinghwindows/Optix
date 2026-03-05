"""
WebSocket Router - Real-time market data streaming endpoints
"""

from fastapi import APIRouter, WebSocket, WebSocketDisconnect
import json
import logging

from ..services.websocket_service import connection_manager, broadcaster

logger = logging.getLogger(__name__)

router = APIRouter(tags=["WebSocket"])


@router.websocket("/ws/market")
async def market_websocket(websocket: WebSocket):
    """
    WebSocket endpoint for real-time market data streaming.

    ## Connection
    Connect to: `ws://localhost:8000/ws/market`

    ## Message Format (JSON)

    ### Subscribe to spot prices:
    ```json
    {
        "action": "subscribe",
        "type": "spot",
        "symbols": ["NIFTY", "BANKNIFTY"]
    }
    ```

    ### Subscribe to option chain:
    ```json
    {
        "action": "subscribe",
        "type": "option_chain",
        "symbols": ["NIFTY"]
    }
    ```

    ### Unsubscribe:
    ```json
    {
        "action": "unsubscribe",
        "type": "spot",
        "symbols": ["NIFTY"]
    }
    ```

    ### Ping (keep-alive):
    ```json
    {
        "action": "ping"
    }
    ```

    ## Response Messages

    ### Spot Price Update:
    ```json
    {
        "type": "spot_update",
        "symbol": "NIFTY",
        "data": {
            "lastPrice": 25300,
            "change": 50.5,
            "pChange": 0.2,
            ...
        },
        "timestamp": "2026-01-31T10:30:00"
    }
    ```

    ### Option Chain Update:
    ```json
    {
        "type": "option_chain_update",
        "symbol": "NIFTY",
        "expiry": "05-Feb-2026",
        "data": {...},
        "timestamp": "2026-01-31T10:30:00"
    }
    ```
    """
    await connection_manager.connect(websocket)

    try:
        # Start broadcaster if not running
        await broadcaster.start()

        while True:
            # Receive message from client
            data = await websocket.receive_text()

            try:
                message = json.loads(data)
                await connection_manager.handle_message(websocket, message)
            except json.JSONDecodeError:
                await websocket.send_json({
                    "type": "error",
                    "message": "Invalid JSON format"
                })

    except WebSocketDisconnect:
        connection_manager.disconnect(websocket)
        logger.info("WebSocket client disconnected")
    except Exception as e:
        logger.error(f"WebSocket error: {e}")
        connection_manager.disconnect(websocket)


@router.get("/ws/stats")
async def websocket_stats():
    """Get WebSocket connection statistics"""
    return {
        "connections": len(connection_manager.active_connections),
        "broadcaster_running": broadcaster._is_running
    }
