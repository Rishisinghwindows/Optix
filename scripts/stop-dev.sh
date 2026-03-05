#!/usr/bin/env bash
set -euo pipefail

# Dev ports
for port in 8010 5183; do
  pids=$(lsof -ti tcp:$port 2>/dev/null || true)
  if [ -n "$pids" ]; then
    echo "Stopping dev processes on port $port: $pids"
    for pid in $pids; do
      kill "$pid" || true
    done
  else
    echo "No dev process listening on port $port"
  fi
done

pkill -f "uvicorn app.main:app" 2>/dev/null || true
pkill -f "vite" 2>/dev/null || true

echo "Dev services stopped."
