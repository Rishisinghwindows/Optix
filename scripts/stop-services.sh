#!/usr/bin/env bash
set -euo pipefail

# Stop common service ports
for port in 8000 5173; do
  pids=$(lsof -ti tcp:$port 2>/dev/null || true)
  if [ -n "$pids" ]; then
    echo "Stopping processes on port $port: $pids"
    for pid in $pids; do
      kill "$pid" || true
    done
  else
    echo "No process listening on port $port"
  fi
done

# Optional: stop any lingering vite/uvicorn by name
pkill -f "uvicorn app.main:app" 2>/dev/null || true
pkill -f "vite" 2>/dev/null || true

echo "Done."
