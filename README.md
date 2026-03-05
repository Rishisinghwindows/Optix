<p align="center">
  <img src="AppStoreScreenshots/optix-logo.png" alt="Optix Logo" width="120" />
</p>

<h1 align="center">Optix</h1>

<p align="center">
  <strong>Indian Stock Options Trading Platform</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Python-3.11+-3776AB?logo=python&logoColor=white" alt="Python" />
  <img src="https://img.shields.io/badge/FastAPI-0.100+-009688?logo=fastapi&logoColor=white" alt="FastAPI" />
  <img src="https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black" alt="React" />
  <img src="https://img.shields.io/badge/Kotlin-Jetpack_Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Swift-SwiftUI-F05138?logo=swift&logoColor=white" alt="Swift" />
  <img src="https://img.shields.io/badge/PostgreSQL-15+-4169E1?logo=postgresql&logoColor=white" alt="PostgreSQL" />
  <img src="https://img.shields.io/badge/Redis-7+-DC382D?logo=redis&logoColor=white" alt="Redis" />
</p>

---

A multi-platform options trading application for Indian stock markets (NSE/BSE). Optix delivers real-time option chains, Black-Scholes pricing with Greeks, paper trading, AI-powered trade analysis, backtesting, algo trading, and educational tools — across web, Android, and iOS.

## Platforms

| Platform | Tech Stack | Directory |
|----------|-----------|-----------|
| **Backend API** | Python 3.11+, FastAPI, SQLAlchemy 2.0, PostgreSQL, Redis | `nifty-auth-api/` |
| **Web App** | React 18, Vite, Recharts, i18next | `NiftyOptionCalculator-Website-React/` |
| **Android** | Kotlin, Jetpack Compose, Material 3 | `NiftyOptionCalculator-Android/` |
| **iOS** | SwiftUI, Combine, Charts | `NiftyOptionCalculator-iOS/` |

## Features

| Feature | Description |
|---------|-------------|
| **Live Option Chain** | Real-time NSE option chain with OI analysis, IV, Greeks, and moneyness indicators |
| **Black-Scholes Calculator** | Option pricing with all Greeks (Delta, Gamma, Theta, Vega, Rho) and IV solver |
| **Paper Trading** | Virtual trading with configurable balance, position tracking, and P&L analytics |
| **AI Trade Analysis** | AI-powered trade suggestions using Google Gemini with IV-aware targets |
| **Backtesting** | Strategy simulation on historical data with performance metrics |
| **Algo Trading** | Automated strategies with signal generation and risk management |
| **Price Alerts** | Configurable alerts for price, IV, and OI changes with push notifications |
| **AI Chatbot** | Options trading assistant (OpenAI/Anthropic) with RAG knowledge base |
| **Option Screener** | Filter options by Greeks, IV, OI, volume, and custom criteria |
| **P&L Simulator** | What-if analysis with adjustable spot price, time, and volatility |
| **Strategy Builder** | Multi-leg strategy construction with payoff diagrams (iOS) |
| **OI Analysis** | Open Interest heatmaps, support/resistance zones, smart money tracking (iOS) |
| **Trade Journal** | Log and review trades with notes and performance tracking |
| **IPO Dashboard** | Upcoming IPO tracking and analysis |
| **Education & Quizzes** | Options trading lessons and interactive quizzes |
| **Multi-language** | i18n support (English, Hindi, Tamil, Telugu, Kannada, Malayalam, Bengali, Odia, Punjabi) |

## Supported Indices

| Index | Lot Size | Exchange |
|-------|----------|----------|
| NIFTY 50 | 75 | NSE |
| BANK NIFTY | 30 | NSE |
| FIN NIFTY | 25 | NSE |
| MIDCAP NIFTY | 50 | NSE |
| SENSEX | 10 | BSE |
| BANKEX | 15 | BSE |

## Quick Start

### Using optix.sh (Recommended)

```bash
# Start API + Web in development mode
./optix.sh dev all start

# Check status of all services
./optix.sh status

# View logs
./optix.sh dev api logs
./optix.sh dev web logs

# Stop everything
./optix.sh dev all stop
```

### Using Docker

```bash
cd nifty-auth-api

# Development (with hot reload)
docker compose up -d

# QA environment
docker compose -f docker-compose.qa.yml up -d

# Production
docker compose -f docker-compose.prod.yml up -d
```

## Setup

### Prerequisites

- Python 3.11+
- Node.js 18+
- PostgreSQL 15+
- Redis 7+
- Docker & Docker Compose (optional)

### Backend

```bash
cd nifty-auth-api

# Virtual environment
python -m venv venv
source venv/bin/activate

# Dependencies
pip install -r requirements.txt

# Environment
cp .env.example .env
# Edit .env — at minimum set DATABASE_URL and JWT_SECRET_KEY

# Database migrations
alembic upgrade head

# Start server
uvicorn app.main:app --reload --port 8000
```

API docs available at `http://localhost:8000/docs`.

### Web Frontend

```bash
cd NiftyOptionCalculator-Website-React

npm install

cp .env.example .env.local
# Set VITE_API_URL=http://localhost:8000

npm run dev
```

### Android

Open `NiftyOptionCalculator-Android/` in Android Studio. Sync Gradle and run on device/emulator.

### iOS

Open `NiftyOptionCalculator-iOS/NiftyOptionCalculator.xcodeproj` in Xcode. Build and run on simulator or device.

## Environment Variables

### Required

| Variable | Description |
|----------|-------------|
| `DATABASE_URL` | PostgreSQL connection string |
| `JWT_SECRET_KEY` | JWT signing key (generate with `openssl rand -hex 64`) |

### Market Data

| Variable | Description |
|----------|-------------|
| `UPSTOX_API_KEY` | Upstox API key for live market data |
| `UPSTOX_API_SECRET` | Upstox API secret |
| `UPSTOX_REDIRECT_URI` | OAuth redirect URI for Upstox |

### Authentication

| Variable | Description |
|----------|-------------|
| `TWILIO_ACCOUNT_SID` | Twilio SID for OTP delivery |
| `TWILIO_AUTH_TOKEN` | Twilio auth token |
| `MSG91_AUTH_KEY` | MSG91 API key (India SMS alternative) |
| `GOOGLE_CLIENT_ID` | Google OAuth client ID |
| `APPLE_TEAM_ID` | Apple Sign In team ID |
| `FACEBOOK_APP_ID` | Facebook app ID |

### AI Services

| Variable | Description |
|----------|-------------|
| `GEMINI_API_KEY` | Google Gemini API key (free tier — trade analysis) |
| `OPENAI_API_KEY` | OpenAI API key (chatbot) |
| `ANTHROPIC_API_KEY` | Anthropic API key (chatbot alternative) |

### Infrastructure

| Variable | Description |
|----------|-------------|
| `REDIS_URL` | Redis connection string (default: `redis://localhost:6379`) |
| `ENABLE_ALERTS` | Enable alert checker background task (default: `true`) |
| `ENABLE_POSITION_MONITOR` | Enable algo trading position monitor (default: `true`) |

## API Endpoints

| Group | Prefix | Auth |
|-------|--------|------|
| Health | `/health` | No |
| Authentication | `/api/v1/auth` | No |
| User Profile | `/api/v1/user` | Yes |
| Market Data | `/api/v1/market` | No |
| Paper Trading | `/api/v1/paper-trading` | Yes |
| Backtesting | `/api/v1/backtest` | Optional |
| AI Analysis | `/api/v1/options-ai` | No |
| AI Insights | `/api/v1/options-ai-insights` | No |
| Chatbot | `/api/v1/chatbot` | Yes |
| Alerts | `/api/v1/alerts` | Yes |
| Algo Trading | `/api/v1/algo-trading` | Yes |
| Trade Journal | `/api/v1/trade-journal` | Yes |
| IPO Dashboard | `/api/v1/ipo` | No |
| Devices | `/api/v1/device` | Yes |
| Admin | `/api/v1/admin` | Yes |
| WebSocket | `/ws` | No |

Interactive API docs at `/docs` (Swagger UI) and `/redoc` (ReDoc).

## Project Structure

```
NiftyOptionPriceCalculatore/
├── nifty-auth-api/                    # Backend API
│   ├── app/
│   │   ├── main.py                    # FastAPI entry point
│   │   ├── config.py                  # Settings (Pydantic BaseSettings)
│   │   ├── database.py                # SQLAlchemy async engine
│   │   ├── routers/                   # API route handlers (16 routers)
│   │   ├── models/                    # SQLAlchemy ORM models (11 models)
│   │   ├── services/                  # Business logic (24 services)
│   │   ├── schemas/                   # Pydantic request/response schemas
│   │   ├── middleware/                # JWT auth middleware
│   │   ├── utils/                     # Helpers, SMS utilities
│   │   ├── workers/                   # Background tasks (position monitor)
│   │   └── data/                      # Static data (options knowledge base)
│   ├── alembic/                       # Database migrations
│   ├── tests/                         # pytest test suite
│   ├── scripts/                       # Data import scripts
│   ├── nginx/                         # Nginx reverse proxy configs
│   ├── Dockerfile
│   ├── docker-compose.yml             # Dev compose
│   ├── docker-compose.qa.yml          # QA compose
│   ├── docker-compose.prod.yml        # Production compose
│   ├── server.sh                      # Server start script
│   └── deploy.sh                      # Deployment script
│
├── NiftyOptionCalculator-Website-React/  # React web app
│   ├── src/
│   │   ├── App.jsx                    # Root component with routing
│   │   ├── components/                # Landing page components
│   │   ├── components/app/            # App pages (16 pages)
│   │   ├── components/games/          # Educational trading games
│   │   ├── components/ads/            # Ad components
│   │   ├── context/                   # React context (Auth, Ads)
│   │   ├── services/                  # API client services (10 services)
│   │   ├── utils/                     # Black-Scholes, screener engine
│   │   ├── config/                    # App configuration
│   │   └── i18n/                      # Internationalization (9 languages)
│   └── package.json
│
├── NiftyOptionCalculator-Android/        # Android app
│   └── app/src/main/java/com/niftyoption/calculator/
│       ├── MainActivity.kt               # Entry point
│       ├── domain/                        # Black-Scholes engine
│       ├── ui/screens/                    # Compose UI screens
│       ├── ui/viewmodels/                 # MVVM ViewModels
│       ├── ui/theme/                      # Material 3 theme
│       └── data/                          # Models & API clients
│
├── NiftyOptionCalculator-iOS/            # iOS app
│   └── NiftyOptionCalculator/
│       ├── App/                           # SwiftUI app entry
│       ├── Views/                         # SwiftUI views (40+ views)
│       ├── ViewModels/                    # MVVM ViewModels (10)
│       ├── Models/                        # Data models (13)
│       ├── Services/                      # API & computation services (29)
│       ├── Theme/                         # Theme configuration
│       └── Localization/                  # Multi-language support (9 languages)
│
├── scripts/                              # Environment management scripts
├── optix.sh                              # Main CLI — service orchestration
├── start-dev.sh                          # Quick-start dev
├── start-qa.sh                           # Quick-start QA
├── start-prod.sh                         # Quick-start prod
└── stop-all.sh                           # Stop all services
```

## Data Flow

```
Market Data Sources (prioritized failover):
  Upstox API ──→ NSE India ──→ nseoptionchain.com ──→ Demo Data
       │
       ▼
  Backend (FastAPI) ── Redis Cache (60s TTL)
       │
       ├── Greeks enrichment (Black-Scholes IV solver)
       ├── AI Analysis (Gemini 2.5 Flash)
       ├── Algo Engine (signal generation + risk management)
       └── WebSocket broadcast
       │
       ▼
  Clients (React / Android / iOS)
```

## Scripts Reference

| Script | Description |
|--------|-------------|
| `./optix.sh <env> <service> <action>` | Main CLI: environments (`dev`, `qa`, `prod`), services (`api`, `web`, `all`), actions (`start`, `stop`, `restart`, `logs`) |
| `./optix.sh status` | Show status of all services across environments |
| `./start-dev.sh` | Start all services in dev mode |
| `./start-qa.sh` | Start all services in QA mode |
| `./start-prod.sh` | Start all services in production mode |
| `./stop-all.sh` | Stop all running services |
| `nifty-auth-api/deploy.sh` | Deploy backend to production |
| `nifty-auth-api/server.sh` | Start backend server directly |

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Backend Framework** | FastAPI + Uvicorn |
| **ORM** | SQLAlchemy 2.0 (async) |
| **Database** | PostgreSQL 15+ |
| **Cache** | Redis 7+ |
| **Migrations** | Alembic |
| **Auth** | JWT (PyJWT) + OTP (Twilio/MSG91) + Social (Google/Apple/Facebook) |
| **AI - Analysis** | Google Gemini 2.5 Flash |
| **AI - Chatbot** | OpenAI GPT-4o-mini / Anthropic Claude |
| **AI - RAG** | ChromaDB |
| **Frontend** | React 18 + Vite |
| **Charts** | Recharts (Web), Swift Charts (iOS) |
| **Android** | Kotlin + Jetpack Compose + Material 3 |
| **iOS** | SwiftUI + Combine |
| **Containerization** | Docker + Docker Compose |
| **Reverse Proxy** | Nginx |
| **Testing** | pytest (backend), XCTest (iOS), JUnit (Android) |

## Deployment

### Development

```bash
./optix.sh dev all start
# API: http://localhost:8000
# Web: http://localhost:5173
```

### QA

```bash
./optix.sh qa all start
# Proxied via nginx: api.optix.d23ai.in / optix.d23ai.in
```

### Production

```bash
./optix.sh prod all start
# Or via Docker:
cd nifty-auth-api && docker compose -f docker-compose.prod.yml up -d
```

## Architecture

For detailed technical architecture documentation, see [ARCHITECTURE.md](ARCHITECTURE.md).

## License

Private — All rights reserved.
