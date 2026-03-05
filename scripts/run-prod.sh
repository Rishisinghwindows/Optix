#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="/Users/rishi/Desktop/WorkSpace/NiftyOptionPriceCalculatore"
API_DIR="$ROOT_DIR/nifty-auth-api"
WEB_DIR="$ROOT_DIR/NiftyOptionCalculator-Website-React"

# Start API (prod)
(
  cd "$API_DIR"
  set -a
  source .env.prod
  set +a
  nohup venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8000 > /tmp/optix-api.log 2>&1 &
  echo "API (prod) started: http://127.0.0.1:8000"
)

# Start Website (prod)
(
  cd "$WEB_DIR"
  nohup npm run dev -- --host 127.0.0.1 --port 5173 --mode prod > /tmp/nifty-web.log 2>&1 &
  echo "Website (prod) started: http://127.0.0.1:5173"
)
