#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="/Users/rishi/Desktop/WorkSpace/NiftyOptionPriceCalculatore"
API_DIR="$ROOT_DIR/nifty-auth-api"
WEB_DIR="$ROOT_DIR/NiftyOptionCalculator-Website-React"

# Start API (dev)
(
  cd "$API_DIR"
  set -a
  source .env.dev
  set +a
  nohup venv/bin/uvicorn app.main:app --reload --host 127.0.0.1 --port 8010 > /tmp/optix-api.log 2>&1 &
  echo "API (dev) started: http://127.0.0.1:8010"
)

# Start Website (dev)
(
  cd "$WEB_DIR"
  nohup npm run dev -- --host 127.0.0.1 --port 5183 --mode dev > /tmp/nifty-web.log 2>&1 &
  echo "Website (dev) started: http://127.0.0.1:5183"
)
