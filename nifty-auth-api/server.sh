#!/bin/bash

# Optix API Server Management Script
# Usage: ./server.sh [start|stop|restart|status|logs]

APP_DIR="/Users/rishi/Desktop/WorkSpace/NiftyOptionPriceCalculatore/nifty-auth-api"
PID_FILE="$APP_DIR/.server.pid"
LOG_FILE="$APP_DIR/server.log"
PORT=8000

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

cd "$APP_DIR"

start_server() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p $PID > /dev/null 2>&1; then
            echo -e "${YELLOW}Server is already running (PID: $PID)${NC}"
            return 1
        fi
    fi

    echo -e "${GREEN}Starting Optix API Server...${NC}"

    # Activate virtual environment if exists
    if [ -d "venv" ]; then
        source venv/bin/activate
    elif [ -d ".venv" ]; then
        source .venv/bin/activate
    fi

    # Start server in background
    nohup python -m uvicorn app.main:app --host 0.0.0.0 --port $PORT --reload > "$LOG_FILE" 2>&1 &

    PID=$!
    echo $PID > "$PID_FILE"

    sleep 2

    if ps -p $PID > /dev/null 2>&1; then
        echo -e "${GREEN}Server started successfully!${NC}"
        echo -e "PID: $PID"
        echo -e "URL: http://localhost:$PORT"
        echo -e "Docs: http://localhost:$PORT/docs"
        echo -e "Logs: $LOG_FILE"
    else
        echo -e "${RED}Failed to start server. Check logs: $LOG_FILE${NC}"
        rm -f "$PID_FILE"
        return 1
    fi
}

stop_server() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p $PID > /dev/null 2>&1; then
            echo -e "${YELLOW}Stopping server (PID: $PID)...${NC}"
            kill $PID
            sleep 2

            # Force kill if still running
            if ps -p $PID > /dev/null 2>&1; then
                kill -9 $PID
            fi

            rm -f "$PID_FILE"
            echo -e "${GREEN}Server stopped${NC}"
        else
            echo -e "${YELLOW}Server not running (stale PID file)${NC}"
            rm -f "$PID_FILE"
        fi
    else
        echo -e "${YELLOW}Server is not running${NC}"

        # Try to find and kill any uvicorn process on the port
        PIDS=$(lsof -ti:$PORT 2>/dev/null)
        if [ -n "$PIDS" ]; then
            echo -e "${YELLOW}Found process on port $PORT, stopping...${NC}"
            echo $PIDS | xargs kill -9 2>/dev/null
            echo -e "${GREEN}Done${NC}"
        fi
    fi
}

server_status() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p $PID > /dev/null 2>&1; then
            echo -e "${GREEN}Server is running${NC}"
            echo -e "PID: $PID"
            echo -e "URL: http://localhost:$PORT"

            # Check if chatbot is configured
            CHAT_STATUS=$(curl -s "http://localhost:$PORT/api/v1/chat/status" 2>/dev/null)
            if [ -n "$CHAT_STATUS" ]; then
                echo -e "Chat Status: $CHAT_STATUS"
            fi
            return 0
        fi
    fi

    echo -e "${RED}Server is not running${NC}"
    return 1
}

show_logs() {
    if [ -f "$LOG_FILE" ]; then
        echo -e "${GREEN}Showing last 50 lines of logs:${NC}"
        tail -50 "$LOG_FILE"
        echo ""
        echo -e "${YELLOW}To follow logs in real-time: tail -f $LOG_FILE${NC}"
    else
        echo -e "${YELLOW}No log file found${NC}"
    fi
}

case "$1" in
    start)
        start_server
        ;;
    stop)
        stop_server
        ;;
    restart)
        stop_server
        sleep 1
        start_server
        ;;
    status)
        server_status
        ;;
    logs)
        show_logs
        ;;
    *)
        echo "Optix API Server Management"
        echo ""
        echo "Usage: $0 {start|stop|restart|status|logs}"
        echo ""
        echo "Commands:"
        echo "  start   - Start the server"
        echo "  stop    - Stop the server"
        echo "  restart - Restart the server"
        echo "  status  - Check server status"
        echo "  logs    - Show recent logs"
        echo ""
        echo "Make sure to add OPENAI_API_KEY to .env for chatbot to work:"
        echo "  echo 'OPENAI_API_KEY=your-key-here' >> .env"
        ;;
esac
