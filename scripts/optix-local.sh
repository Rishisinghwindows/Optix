#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="/Users/rishi/Desktop/WorkSpace/NiftyOptionPriceCalculatore"
API_DIR="$ROOT_DIR/nifty-auth-api"
WEB_DIR="$ROOT_DIR/NiftyOptionCalculator-Website-React"

API_PORT=8000
WEB_PORT=5173

API_PID_FILE="/tmp/optix-api.pid"
WEB_PID_FILE="/tmp/optix-web.pid"
API_LOG="/tmp/optix-api.log"
WEB_LOG="/tmp/optix-web.log"

start_api() {
  if [ -f "$API_PID_FILE" ] && ps -p "$(cat "$API_PID_FILE")" >/dev/null 2>&1; then
    echo "API already running (PID: $(cat "$API_PID_FILE"))"
    return 0
  fi

  (
    cd "$API_DIR"
    if [ -d "venv" ]; then
      source venv/bin/activate
    elif [ -d ".venv" ]; then
      source .venv/bin/activate
    fi
    nohup python -m uvicorn app.main:app --host 127.0.0.1 --port "$API_PORT" --reload > "$API_LOG" 2>&1 &
    echo $! > "$API_PID_FILE"
  )

  echo "API started: http://127.0.0.1:${API_PORT} (PID: $(cat "$API_PID_FILE"))"
}

start_web() {
  if [ -f "$WEB_PID_FILE" ] && ps -p "$(cat "$WEB_PID_FILE")" >/dev/null 2>&1; then
    echo "Web already running (PID: $(cat "$WEB_PID_FILE"))"
    return 0
  fi

  (
    cd "$WEB_DIR"
    nohup npm run dev -- --host 127.0.0.1 --port "$WEB_PORT" > "$WEB_LOG" 2>&1 &
    echo $! > "$WEB_PID_FILE"
  )

  echo "Web started: http://127.0.0.1:${WEB_PORT} (PID: $(cat "$WEB_PID_FILE"))"
}

stop_pid() {
  local pid_file="$1"
  local name="$2"
  if [ -f "$pid_file" ]; then
    local pid
    pid="$(cat "$pid_file")"
    if ps -p "$pid" >/dev/null 2>&1; then
      kill "$pid" || true
      sleep 1
      if ps -p "$pid" >/dev/null 2>&1; then
        kill -9 "$pid" || true
      fi
      echo "$name stopped (PID: $pid)"
    else
      echo "$name not running (stale PID file)"
    fi
    rm -f "$pid_file"
  else
    echo "$name not running"
  fi
}

status_pid() {
  local pid_file="$1"
  local name="$2"
  if [ -f "$pid_file" ] && ps -p "$(cat "$pid_file")" >/dev/null 2>&1; then
    echo "$name running (PID: $(cat "$pid_file"))"
  else
    echo "$name not running"
  fi
}

case "${1:-}" in
  start)
    start_api
    start_web
    ;;
  stop)
    stop_pid "$WEB_PID_FILE" "Web"
    stop_pid "$API_PID_FILE" "API"
    ;;
  restart)
    "$0" stop
    "$0" start
    ;;
  status)
    status_pid "$API_PID_FILE" "API"
    status_pid "$WEB_PID_FILE" "Web"
    ;;
  *)
    echo "Usage: $0 {start|stop|restart|status}"
    exit 1
    ;;
esac
