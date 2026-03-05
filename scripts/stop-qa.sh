#!/usr/bin/env bash
set -euo pipefail

# QA ports (same as dev in this setup)
for port in 8000 5173; do
  pids=$(lsof -ti tcp:$port 2>/dev/null || true)
  if [ -n "$pids" ]; then
    echo "Stopping QA processes on port $port: $pids"
    for pid in $pids; do
      kill "$pid" || true
    done
  else
    echo "No QA process listening on port $port"
  fi
done

pkill -f "uvicorn app.main:app" 2>/dev/null || true
pkill -f "vite" 2>/dev/null || true

echo "QA services stopped."
