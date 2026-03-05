#!/bin/bash

# ============================================================================
# Optix Services Management Script
# ============================================================================
# Usage: ./optix.sh [env] [service] [action]
#
# Environments: dev, qa, prod
# Services:     api, web, admin, all
# Actions:      start, stop, restart, status, logs
#
# Examples:
#   ./optix.sh dev api start      # Start API in dev mode
#   ./optix.sh qa all start       # Start all services in QA mode
#   ./optix.sh prod web stop      # Stop website in prod mode
#   ./optix.sh status             # Show status of all services
# ============================================================================

# Base directories
BASE_DIR="/Users/rishi/Desktop/WorkSpace/NiftyOptionPriceCalculatore"
API_DIR="$BASE_DIR/nifty-auth-api"
WEB_DIR="$BASE_DIR/NiftyOptionCalculator-Website-React"
# Admin is built into API at /admin/dashboard

# PID files directory
PID_DIR="$BASE_DIR/.pids"
mkdir -p "$PID_DIR"

# Log files directory
LOG_DIR="$BASE_DIR/.logs"
mkdir -p "$LOG_DIR"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Port configuration function
get_api_port() {
    case "$1" in
        dev)  echo 8000 ;;
        qa)   echo 8000 ;;
        prod) echo 8000 ;;
        *)    echo 8000 ;;
    esac
}

get_web_port() {
    case "$1" in
        dev)  echo 5173 ;;
        qa)   echo 5173 ;;
        prod) echo 3000 ;;
        *)    echo 5173 ;;
    esac
}

# ============================================================================
# Helper Functions
# ============================================================================

print_header() {
    echo ""
    echo -e "${CYAN}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║${NC}           ${BLUE}Optix Services Manager${NC}                          ${CYAN}║${NC}"
    echo -e "${CYAN}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

print_service() {
    local name=$1
    local status=$2
    local port=$3
    local pid=$4

    if [ "$status" = "running" ]; then
        echo -e "  ${GREEN}●${NC} $name ${GREEN}running${NC} on port $port (PID: $pid)"
    else
        echo -e "  ${RED}○${NC} $name ${RED}stopped${NC}"
    fi
}

get_pid() {
    local service=$1
    local env=$2
    local pid_file="$PID_DIR/${service}_${env}.pid"

    if [ -f "$pid_file" ]; then
        cat "$pid_file"
    else
        echo ""
    fi
}

is_running() {
    local pid=$1
    if [ -n "$pid" ] && ps -p "$pid" > /dev/null 2>&1; then
        return 0
    fi
    return 1
}

kill_port() {
    local port=$1
    local pids=$(lsof -ti:$port 2>/dev/null)
    if [ -n "$pids" ]; then
        echo "$pids" | xargs kill -9 2>/dev/null
        sleep 1
    fi
}

# ============================================================================
# API Service
# ============================================================================

start_api() {
    local env=$1
    local port=$(get_api_port "$env")
    local pid_file="$PID_DIR/api_${env}.pid"
    local log_file="$LOG_DIR/api_${env}.log"

    echo -e "${YELLOW}Starting API ($env) on port $port...${NC}"

    # Check if already running
    local pid=$(get_pid "api" "$env")
    if is_running "$pid"; then
        echo -e "${YELLOW}API is already running (PID: $pid)${NC}"
        return 0
    fi

    # Kill anything on the port
    kill_port $port

    cd "$API_DIR"

    # Activate venv
    if [ -d "venv" ]; then
        source venv/bin/activate
    elif [ -d ".venv" ]; then
        source .venv/bin/activate
    fi

    # Set environment
    export OPTIX_ENV=$env

    # Copy appropriate .env file
    if [ -f ".env.$env" ]; then
        cp ".env.$env" ".env"
        echo -e "  Using .env.$env"
    fi

    # Start server
    if [ "$env" = "dev" ]; then
        nohup python -m uvicorn app.main:app --host 0.0.0.0 --port $port --reload > "$log_file" 2>&1 &
    else
        nohup python -m uvicorn app.main:app --host 127.0.0.1 --port $port > "$log_file" 2>&1 &
    fi
    local new_pid=$!
    echo $new_pid > "$pid_file"

    sleep 3

    if is_running "$new_pid"; then
        echo -e "${GREEN}API started successfully!${NC}"
        echo -e "  URL: http://localhost:$port"
        echo -e "  Docs: http://localhost:$port/docs"
        echo -e "  External: https://api.optix.d23ai.in"
        echo -e "  Logs: $log_file"

        # Check chatbot status
        sleep 2
        local chat_status=$(curl -s "http://localhost:$port/api/v1/chat/status" 2>/dev/null)
        if [ -n "$chat_status" ]; then
            echo -e "  Chatbot: $chat_status"
        fi
    else
        echo -e "${RED}Failed to start API. Check logs: $log_file${NC}"
        tail -20 "$log_file" 2>/dev/null
        rm -f "$pid_file"
        return 1
    fi
}

stop_api() {
    local env=$1
    local port=$(get_api_port "$env")
    local pid_file="$PID_DIR/api_${env}.pid"

    echo -e "${YELLOW}Stopping API ($env)...${NC}"

    local pid=$(get_pid "api" "$env")
    if is_running "$pid"; then
        kill $pid 2>/dev/null
        sleep 2
        if is_running "$pid"; then
            kill -9 $pid 2>/dev/null
        fi
    fi

    # Also kill anything on the port
    kill_port $port

    rm -f "$pid_file"
    echo -e "${GREEN}API stopped${NC}"
}

# ============================================================================
# Website Service
# ============================================================================

start_web() {
    local env=$1
    local port=$(get_web_port "$env")
    local pid_file="$PID_DIR/web_${env}.pid"
    local log_file="$LOG_DIR/web_${env}.log"

    echo -e "${YELLOW}Starting Website ($env) on port $port...${NC}"

    # Check if already running
    local pid=$(get_pid "web" "$env")
    if is_running "$pid"; then
        echo -e "${YELLOW}Website is already running (PID: $pid)${NC}"
        return 0
    fi

    # Kill anything on the port
    kill_port $port

    cd "$WEB_DIR"

    # Set environment
    export VITE_ENV=$env

    if [ "$env" = "prod" ]; then
        # Production: build and serve
        echo -e "${YELLOW}Building for production...${NC}"
        npm run build > "$log_file" 2>&1
        nohup npm run preview -- --host 127.0.0.1 --port $port >> "$log_file" 2>&1 &
    else
        # Dev/QA: run dev server
        nohup npm run dev -- --host 127.0.0.1 --port $port > "$log_file" 2>&1 &
    fi

    local new_pid=$!
    echo $new_pid > "$pid_file"

    sleep 4

    if is_running "$new_pid"; then
        echo -e "${GREEN}Website started successfully!${NC}"
        echo -e "  URL: http://localhost:$port"
        echo -e "  External: https://optix.d23ai.in"
        echo -e "  Logs: $log_file"
    else
        echo -e "${RED}Failed to start Website. Check logs: $log_file${NC}"
        tail -20 "$log_file" 2>/dev/null
        rm -f "$pid_file"
        return 1
    fi
}

stop_web() {
    local env=$1
    local port=$(get_web_port "$env")
    local pid_file="$PID_DIR/web_${env}.pid"

    echo -e "${YELLOW}Stopping Website ($env)...${NC}"

    local pid=$(get_pid "web" "$env")
    if is_running "$pid"; then
        kill $pid 2>/dev/null
        sleep 2
        if is_running "$pid"; then
            kill -9 $pid 2>/dev/null
        fi
    fi

    # Kill anything on the port
    kill_port $port

    # Also kill any node processes for vite on this port
    pkill -f "vite.*$port" 2>/dev/null
    pkill -f "node.*$port" 2>/dev/null

    rm -f "$pid_file"
    echo -e "${GREEN}Website stopped${NC}"
}

# ============================================================================
# Status & Logs
# ============================================================================

show_status() {
    print_header

    echo -e "${BLUE}Service Status:${NC}"
    echo ""

    for env in dev qa prod; do
        echo -e "  ${CYAN}[$env]${NC}"

        # API (includes Admin dashboard)
        local api_pid=$(get_pid "api" "$env")
        local api_port=$(get_api_port "$env")
        if is_running "$api_pid"; then
            print_service "API    " "running" "$api_port" "$api_pid"
            echo -e "           ${CYAN}Admin: http://localhost:$api_port/admin/dashboard${NC}"

            # Check chatbot status
            local chat_status=$(curl -s "http://localhost:$api_port/api/v1/chat/status" 2>/dev/null | grep -o '"configured":[^,}]*' | cut -d: -f2)
            if [ "$chat_status" = "true" ]; then
                echo -e "           ${GREEN}Chatbot: configured${NC}"
            elif [ "$chat_status" = "false" ]; then
                echo -e "           ${RED}Chatbot: not configured${NC}"
            fi
        else
            print_service "API    " "stopped" "$api_port" ""
        fi

        # Website
        local web_pid=$(get_pid "web" "$env")
        local web_port=$(get_web_port "$env")
        if is_running "$web_pid"; then
            print_service "Website" "running" "$web_port" "$web_pid"
        else
            print_service "Website" "stopped" "$web_port" ""
        fi

        echo ""
    done

    # Nginx status
    echo -e "  ${CYAN}[nginx]${NC}"
    if pgrep nginx > /dev/null 2>&1; then
        echo -e "  ${GREEN}●${NC} nginx ${GREEN}running${NC}"
        echo -e "       Proxies:"
        echo -e "         api.optix.d23ai.in   -> localhost:8000"
        echo -e "         optix.d23ai.in       -> localhost:5173"
        echo -e "         admin.optix.d23ai.in -> localhost:8000/admin"
    else
        echo -e "  ${RED}○${NC} nginx ${RED}stopped${NC}"
        echo -e "       Run: ${YELLOW}brew services start nginx${NC}"
    fi

    echo ""
}

show_logs() {
    local env=$1
    local service=$2
    local log_file="$LOG_DIR/${service}_${env}.log"

    if [ -f "$log_file" ]; then
        echo -e "${GREEN}Showing last 50 lines of $service ($env) logs:${NC}"
        echo ""
        tail -50 "$log_file"
        echo ""
        echo -e "${YELLOW}To follow logs: tail -f $log_file${NC}"
    else
        echo -e "${YELLOW}No log file found for $service ($env)${NC}"
    fi
}

# ============================================================================
# All Services
# ============================================================================

start_all() {
    local env=$1
    echo -e "${BLUE}Starting all services for $env...${NC}"
    echo ""
    start_api "$env"
    echo ""
    start_web "$env"
}

stop_all() {
    local env=$1
    echo -e "${BLUE}Stopping all services for $env...${NC}"
    echo ""
    stop_api "$env"
    echo ""
    stop_web "$env"
}

restart_all() {
    local env=$1
    stop_all "$env"
    echo ""
    sleep 2
    start_all "$env"
}

# ============================================================================
# Main
# ============================================================================

show_help() {
    print_header
    echo "Usage: $0 [environment] [service] [action]"
    echo ""
    echo -e "${BLUE}Environments:${NC}"
    echo "  dev     Development environment"
    echo "  qa      QA/Testing environment"
    echo "  prod    Production environment"
    echo ""
    echo -e "${BLUE}Services:${NC}"
    echo "  api     Backend API + Admin dashboard (FastAPI on port 8000)"
    echo "  web     Frontend website (Vite/React on port 5173)"
    echo "  all     All services (API + Website)"
    echo ""
    echo -e "${BLUE}Actions:${NC}"
    echo "  start   Start the service(s)"
    echo "  stop    Stop the service(s)"
    echo "  restart Restart the service(s)"
    echo "  status  Show status (no env/service needed)"
    echo "  logs    Show recent logs"
    echo ""
    echo -e "${BLUE}Examples:${NC}"
    echo "  $0 status              # Show all services status"
    echo "  $0 dev api start       # Start API + Admin in dev mode"
    echo "  $0 dev web start       # Start Website in dev mode"
    echo "  $0 qa all start        # Start all services in QA"
    echo "  $0 prod web restart    # Restart website in prod"
    echo "  $0 dev api logs        # Show API dev logs"
    echo ""
    echo -e "${BLUE}Quick Commands:${NC}"
    echo "  $0 dev                 # Start all dev services"
    echo "  $0 qa                  # Start all QA services"
    echo "  $0 stop                # Stop all services (all envs)"
    echo ""
    echo -e "${BLUE}URLs (via nginx):${NC}"
    echo "  API:     https://api.optix.d23ai.in"
    echo "  Website: https://optix.d23ai.in"
    echo "  Admin:   https://admin.optix.d23ai.in"
    echo ""
}

# Parse arguments
ENV=""
SERVICE=""
ACTION=""

case "$1" in
    status)
        show_status
        exit 0
        ;;
    stop)
        if [ -z "$2" ]; then
            # Stop all services in all environments
            for e in dev qa prod; do
                stop_all "$e"
            done
            exit 0
        fi
        ENV=$2
        SERVICE=${3:-all}
        ACTION="stop"
        ;;
    dev|qa|prod)
        ENV=$1
        SERVICE=${2:-all}
        ACTION=${3:-start}
        ;;
    help|--help|-h|"")
        show_help
        exit 0
        ;;
    *)
        echo -e "${RED}Unknown command: $1${NC}"
        show_help
        exit 1
        ;;
esac

# Validate
if [ -z "$ENV" ]; then
    show_help
    exit 1
fi

# Execute
case "$SERVICE" in
    api)
        case "$ACTION" in
            start)   start_api "$ENV" ;;
            stop)    stop_api "$ENV" ;;
            restart) stop_api "$ENV"; sleep 1; start_api "$ENV" ;;
            logs)    show_logs "$ENV" "api" ;;
            status)  show_status ;;
            *)       echo -e "${RED}Unknown action: $ACTION${NC}"; exit 1 ;;
        esac
        ;;
    web|website)
        case "$ACTION" in
            start)   start_web "$ENV" ;;
            stop)    stop_web "$ENV" ;;
            restart) stop_web "$ENV"; sleep 1; start_web "$ENV" ;;
            logs)    show_logs "$ENV" "web" ;;
            status)  show_status ;;
            *)       echo -e "${RED}Unknown action: $ACTION${NC}"; exit 1 ;;
        esac
        ;;
    all)
        case "$ACTION" in
            start)   start_all "$ENV" ;;
            stop)    stop_all "$ENV" ;;
            restart) restart_all "$ENV" ;;
            status)  show_status ;;
            *)       echo -e "${RED}Unknown action: $ACTION${NC}"; exit 1 ;;
        esac
        ;;
    *)
        echo -e "${RED}Unknown service: $SERVICE${NC}"
        show_help
        exit 1
        ;;
esac

echo ""
echo -e "${GREEN}Done!${NC}"
