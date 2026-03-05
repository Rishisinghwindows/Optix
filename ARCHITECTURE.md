# Optix — Architecture Documentation

This document describes the technical architecture of Optix, a multi-platform Indian stock options trading platform.

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Backend Architecture](#2-backend-architecture)
3. [Market Data Pipeline](#3-market-data-pipeline)
4. [Authentication System](#4-authentication-system)
5. [AI/ML Pipeline](#5-aiml-pipeline)
6. [Black-Scholes Engine](#6-black-scholes-engine)
7. [Paper Trading System](#7-paper-trading-system)
8. [Algo Trading Engine](#8-algo-trading-engine)
9. [Frontend Architecture](#9-frontend-architecture)
10. [Mobile Architecture](#10-mobile-architecture)
11. [Database Schema](#11-database-schema)
12. [Caching Strategy](#12-caching-strategy)
13. [Deployment Architecture](#13-deployment-architecture)
14. [WebSocket & Real-time Data](#14-websocket--real-time-data)

---

## 1. System Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                          CLIENTS                                    │
│  ┌──────────┐    ┌───────────────┐    ┌──────────────────────────┐  │
│  │ React    │    │ Android       │    │ iOS                      │  │
│  │ Web App  │    │ Jetpack       │    │ SwiftUI + Combine        │  │
│  │ (Vite)   │    │ Compose       │    │                          │  │
│  └────┬─────┘    └──────┬────────┘    └────────────┬─────────────┘  │
│       │                 │                          │                │
└───────┼─────────────────┼──────────────────────────┼────────────────┘
        │                 │                          │
        ▼                 ▼                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     NGINX REVERSE PROXY                             │
│              api.optix.d23ai.in / optix.d23ai.in                   │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                    ┌──────────┴──────────┐
                    │   REST API + WS     │
                    ▼                     ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     FASTAPI APPLICATION                              │
│                                                                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────────┐   │
│  │  Auth    │  │  Market  │  │  Paper   │  │  AI Analysis     │   │
│  │  Router  │  │  Router  │  │  Trading │  │  (Gemini)        │   │
│  ├──────────┤  ├──────────┤  ├──────────┤  ├──────────────────┤   │
│  │  Alerts  │  │  Algo    │  │  Backtest│  │  Chatbot         │   │
│  │  Router  │  │  Trading │  │  Router  │  │  (OpenAI/Claude) │   │
│  ├──────────┤  ├──────────┤  ├──────────┤  ├──────────────────┤   │
│  │  IPO     │  │  Journal │  │  Admin   │  │  WebSocket       │   │
│  │  Router  │  │  Router  │  │  Router  │  │  Router          │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────────────┘   │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    SERVICE LAYER                              │   │
│  │  auth_service · upstox_service · nse_service · greeks_service│   │
│  │  options_ai_service · ai_chatbot_service · rag_service       │   │
│  │  algo_engine · risk_manager · backtest_engine                │   │
│  │  alert_service · cache_service · websocket_service           │   │
│  │  jwt_service · otp_service · social_auth · ipo_service       │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌────────────────────┐  ┌──────────────────────────────────────┐   │
│  │  WORKERS            │  │  MIDDLEWARE                          │   │
│  │  position_monitor   │  │  auth_middleware (JWT verification) │   │
│  └────────────────────┘  └──────────────────────────────────────┘   │
└──────────────┬──────────────────────────┬───────────────────────────┘
               │                          │
               ▼                          ▼
┌──────────────────────┐   ┌──────────────────────────────────────────┐
│   PostgreSQL 15+     │   │           Redis 7+                       │
│   (SQLAlchemy 2.0    │   │   - Option chain cache (60s TTL)         │
│    async engine)     │   │   - OTP rate limiting                    │
│                      │   │   - Session store                        │
│   Users, Sessions,   │   │   - WebSocket pub/sub                    │
│   Trades, Alerts,    │   │                                          │
│   Backtests, Chat,   │   │                                          │
│   Algo Strategies    │   │                                          │
└──────────────────────┘   └──────────────────────────────────────────┘
               │
               ▼
┌──────────────────────┐
│   ChromaDB           │
│   (RAG vector store) │
│   Options knowledge  │
│   base embeddings    │
└──────────────────────┘
```

### External Services

| Service | Purpose |
|---------|---------|
| Upstox API | Primary live market data source |
| NSE India | Fallback market data |
| nseoptionchain.com | Secondary fallback |
| Google Gemini 2.5 Flash | AI trade analysis (free tier) |
| OpenAI GPT-4o-mini | AI chatbot |
| Anthropic Claude | Alternative AI chatbot |
| Twilio | OTP SMS delivery (international) |
| MSG91 | OTP SMS delivery (India-focused) |
| Google OAuth | Social login |
| Apple Sign In | Social login (iOS) |
| Facebook Login | Social login |

---

## 2. Backend Architecture

### Application Structure

```
nifty-auth-api/
├── app/
│   ├── main.py              # FastAPI app initialization, CORS, lifespan events
│   ├── config.py            # Pydantic BaseSettings — env var management
│   ├── database.py          # SQLAlchemy async engine + session factory
│   │
│   ├── routers/             # API layer (16 routers)
│   │   ├── auth.py          # Login, OTP, social auth, token refresh
│   │   ├── user.py          # Profile CRUD
│   │   ├── market.py        # Option chain, indices, spot prices
│   │   ├── paper_trading.py # Virtual trading positions & history
│   │   ├── backtest.py      # Strategy backtesting
│   │   ├── options_ai.py    # AI analysis endpoint (Gemini)
│   │   ├── options_ai_insights.py  # AI trade suggestions
│   │   ├── chatbot.py       # Conversational AI
│   │   ├── alerts.py        # Price/IV/OI alerts CRUD
│   │   ├── algo_trading.py  # Automated strategies
│   │   ├── trade_journal.py # Trade logging
│   │   ├── ipo.py           # IPO data
│   │   ├── health.py        # Health check
│   │   ├── admin.py         # Admin dashboard
│   │   ├── device.py        # Push notification tokens
│   │   └── websocket.py     # WebSocket connections
│   │
│   ├── models/              # SQLAlchemy ORM (11 models)
│   │   ├── user.py          # User account
│   │   ├── session.py       # Active sessions
│   │   ├── otp.py           # OTP codes + attempts
│   │   ├── paper_trading.py # Positions, orders, history
│   │   ├── backtest.py      # Backtest runs + results
│   │   ├── alert.py         # Alert configurations
│   │   ├── algo_trading.py  # Strategies, signals, positions
│   │   ├── chat.py          # Chat sessions + messages
│   │   ├── trade_journal.py # Journal entries
│   │   ├── device_token.py  # Push notification tokens
│   │   └── visitor.py       # Anonymous visitor tracking
│   │
│   ├── services/            # Business logic (24 services)
│   ├── schemas/             # Pydantic validation (10 schema modules)
│   ├── middleware/          # JWT auth middleware
│   ├── utils/               # Helpers, SMS abstraction
│   ├── workers/             # Background tasks (position monitor)
│   └── data/                # Static knowledge base for RAG
│
├── alembic/                 # Database migrations (4 versions)
├── tests/                   # pytest suite
├── scripts/                 # Data import (NSE scraper, Greeks calc)
└── nginx/                   # Reverse proxy configs
```

### Request Lifecycle

```
Client Request
     │
     ▼
CORS Middleware (allow all origins in dev)
     │
     ▼
Auth Middleware ──→ JWT verification via jwt_service
     │                    │
     │              (skips public routes)
     ▼
Router Handler
     │
     ▼
Service Layer ──→ Business logic, external API calls
     │
     ▼
SQLAlchemy Async Session ──→ PostgreSQL
     │
     ▼
Pydantic Schema ──→ Response serialization
     │
     ▼
JSON Response
```

### Configuration

Settings are managed via `app/config.py` using Pydantic `BaseSettings`. Environment variables are loaded from `.env` files with environment-specific overrides (`.env.dev`, `.env.qa`, `.env.prod`).

---

## 3. Market Data Pipeline

### Data Source Chain

The system uses a prioritized failover chain for market data:

```
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐     ┌──────────────┐
│   Upstox API     │────▶│   NSE India      │────▶│ nseoptionchain   │────▶│  Demo Data   │
│   (Primary)      │fail │   (Fallback 1)   │fail │ .com (Fallback 2)│fail │  (Static)    │
│                  │     │                  │     │                  │     │              │
│  - Live LTP      │     │  - Delayed data  │     │  - Scraped data  │     │  - Synthetic │
│  - WebSocket     │     │  - HTTP scraping  │     │  - HTTP scraping │     │  - Testing   │
│  - Full chain    │     │  - Rate limited  │     │  - Last resort   │     │  - Offline   │
└──────────────────┘     └──────────────────┘     └──────────────────┘     └──────────────┘
```

### Data Flow

1. **Upstox Service** (`upstox_service.py`): Primary source. Authenticates via OAuth, fetches option chain data via REST API, supports WebSocket streaming for real-time LTP updates.

2. **NSE Service** (`nse_service.py`): Fallback. Scrapes NSE India website. Handles session cookies and rate limiting.

3. **nseoptionchain.com**: Secondary fallback, scraped data source.

4. **Demo Data**: Static synthetic data for testing and offline mode.

### Greeks Enrichment

After raw chain data is fetched, the `greeks_service.py` enriches each option with computed Greeks:

```
Raw Option Chain Data
     │
     ▼
┌─────────────────────────────┐
│  greeks_service.py          │
│                             │
│  For each option strike:    │
│  1. Compute IV from LTP     │
│     (Newton-Raphson solver)  │
│  2. Compute Delta            │
│  3. Compute Gamma            │
│  4. Compute Theta            │
│  5. Compute Vega             │
│  6. Compute Rho              │
└─────────────────────────────┘
     │
     ▼
Enriched Option Chain (with IV + Greeks)
     │
     ├──→ Redis Cache (60s TTL)
     ├──→ REST API response
     └──→ WebSocket broadcast
```

### Caching

Option chain data is cached in Redis with a 60-second TTL. Cache keys are structured by index and expiry date.

---

## 4. Authentication System

### Auth Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    AUTH METHODS                               │
│                                                             │
│  ┌─────────────┐  ┌────────────┐  ┌──────────────────────┐ │
│  │ Phone OTP   │  │ Google     │  │ Apple / Facebook     │ │
│  │ (Twilio/    │  │ OAuth 2.0  │  │ Sign In              │ │
│  │  MSG91)     │  │            │  │                      │ │
│  └──────┬──────┘  └─────┬──────┘  └──────────┬───────────┘ │
│         │               │                    │             │
│         ▼               ▼                    ▼             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              auth_service.py                         │  │
│  │  - Verify OTP / social token                        │  │
│  │  - Find or create user                              │  │
│  │  - Generate JWT token pair                          │  │
│  └─────────────────────┬──────────────────────────────┘   │
│                        │                                   │
│                        ▼                                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              jwt_service.py                          │  │
│  │  - Access token  (short-lived)                      │  │
│  │  - Refresh token (long-lived)                       │  │
│  │  - Signed with JWT_SECRET_KEY (HS256)               │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### OTP Flow

```
1. Client → POST /api/v1/auth/send-otp { phone }
2. Server → Generate 6-digit OTP → Store in DB (otp table) → Send via Twilio/MSG91
3. Client → POST /api/v1/auth/verify-otp { phone, otp }
4. Server → Verify OTP → Create/fetch user → Generate JWT pair → Return tokens
```

### JWT Lifecycle

- **Access Token**: Short-lived, included in `Authorization: Bearer <token>` header
- **Refresh Token**: Long-lived, used to obtain new access tokens
- **Session Model**: Tracks active sessions per user with device info
- **Middleware**: `auth_middleware.py` extracts and verifies JWT on protected routes

### Social Auth

- **Google**: `social_auth.py` verifies Google ID token, extracts email/name
- **Apple**: Verifies Apple identity token with Apple's JWKS endpoint
- **Facebook**: Validates access token against Facebook Graph API

---

## 5. AI/ML Pipeline

### AI Trade Analysis (Gemini)

```
Option Chain Data (with Greeks + IV)
     │
     ▼
┌─────────────────────────────────────────────┐
│  options_ai_service.py                       │
│                                              │
│  1. Filter candidate options                 │
│     - Liquidity (volume, OI thresholds)     │
│     - Moneyness (near ATM preferred)        │
│     - Valid expiry                           │
│                                              │
│  2. Score each option                        │
│     - IV percentile (ATM IV baseline)       │
│     - Delta factor                          │
│     - OI/Volume analysis                    │
│     - Time decay assessment                 │
│                                              │
│  3. Compute IV-aware targets                 │
│     - _compute_target_sl() using IV + DTE   │
│     - Probabilistic target/stop-loss        │
│                                              │
│  4. Send to Gemini 2.5 Flash                │
│     - Full chain data (LTP, IV, OI, Greeks) │
│     - Conservative framework prompt         │
│     - Mandatory rejection conditions        │
│                                              │
│  5. Parse Gemini response                    │
│     - Structured trade suggestions          │
│     - Risk warnings                         │
│     - Confidence scores                     │
└─────────────────────────────────────────────┘
     │
     ▼
AI Suggestions (with targets, stop-losses, confidence)
```

### AI Chatbot

```
User Message
     │
     ▼
┌─────────────────────────────────────────────┐
│  ai_chatbot_service.py                       │
│                                              │
│  1. Retrieve chat history (session-based)   │
│                                              │
│  2. RAG Context Retrieval (optional)        │
│     ├── rag_service.py                      │
│     │   └── ChromaDB vector store           │
│     │       └── options_knowledge.py data    │
│     └── Retrieve relevant knowledge chunks  │
│                                              │
│  3. Build prompt with context               │
│     - System prompt (options trading expert)│
│     - RAG context (if relevant)             │
│     - Chat history                          │
│     - User message                          │
│                                              │
│  4. Call LLM                                │
│     ├── OpenAI GPT-4o-mini (primary)       │
│     └── Anthropic Claude (alternative)      │
│                                              │
│  5. Store response in chat history          │
└─────────────────────────────────────────────┘
     │
     ▼
AI Response
```

### RAG Knowledge Base

- **Vector Store**: ChromaDB (embedded, file-based)
- **Knowledge Source**: `app/data/options_knowledge.py` — curated options trading knowledge
- **Loader**: `knowledge_loader.py` — chunks and embeds knowledge into ChromaDB at startup
- **Retrieval**: Semantic similarity search, top-k relevant chunks injected into LLM prompt

---

## 6. Black-Scholes Engine

The Black-Scholes pricing engine is implemented independently on each platform for offline capability.

### Implementations

| Platform | File | Language |
|----------|------|----------|
| Backend | `app/services/greeks_service.py` | Python |
| Web | `src/utils/blackScholes.js` | JavaScript |
| Android | `domain/BlackScholesEngine.kt` | Kotlin |
| iOS | `Services/BlackScholesEngine.swift` | Swift |

### Core Formulas

```
Black-Scholes Call Price:
  C = S·N(d₁) - K·e^(-rT)·N(d₂)

Black-Scholes Put Price:
  P = K·e^(-rT)·N(-d₂) - S·N(-d₁)

Where:
  d₁ = [ln(S/K) + (r + σ²/2)T] / (σ√T)
  d₂ = d₁ - σ√T

  S = Spot price
  K = Strike price
  T = Time to expiry (years)
  r = Risk-free rate (6.5% — RBI repo rate)
  σ = Implied volatility
  N(·) = Standard normal CDF
```

### Greeks

| Greek | Formula | Interpretation |
|-------|---------|----------------|
| **Delta** | N(d₁) for calls, N(d₁)-1 for puts | Price sensitivity to spot |
| **Gamma** | φ(d₁) / (S·σ·√T) | Delta sensitivity to spot |
| **Theta** | -(S·φ(d₁)·σ)/(2√T) - rKe^(-rT)N(d₂) | Time decay per day |
| **Vega** | S·φ(d₁)·√T | Price sensitivity to IV |
| **Rho** | KTe^(-rT)N(d₂) for calls | Price sensitivity to rate |

### IV Solver (Newton-Raphson)

Implied Volatility is computed by inverting the Black-Scholes formula using Newton-Raphson iteration:

```
Given: market_price, S, K, T, r, option_type

Initial guess: σ₀ = 0.3 (30%)

Iterate until convergence:
  1. Compute BS_price(σₙ)
  2. Compute Vega(σₙ)
  3. σₙ₊₁ = σₙ - (BS_price(σₙ) - market_price) / Vega(σₙ)
  4. If |σₙ₊₁ - σₙ| < 1e-6, converged

Max iterations: 100
Bounds: 0.01 ≤ σ ≤ 5.0
```

### IV Format Conventions

- **Backend**: Stores IV as percentage (e.g., 15.0 for 15%)
- **iOS**: Stores IV as decimal (e.g., 0.15 for 15%). Upstox/NSE responses are divided by 100 on parse
- **Web/Android**: Uses percentage format from backend directly

---

## 7. Paper Trading System

### Position Lifecycle

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│  OPEN ORDER  │───▶│   POSITION   │───▶│   CLOSED     │
│              │    │              │    │              │
│  - Buy/Sell  │    │  - Track LTP │    │  - Final P&L │
│  - Lots      │    │  - Live P&L  │    │  - History   │
│  - Price     │    │  - Greeks    │    │  - Analytics │
└──────────────┘    └──────────────┘    └──────────────┘
```

### Key Details

- **Starting Balance**: Configurable (default Rs 10,00,000)
- **Order Types**: Market orders at current LTP
- **P&L Computation**: `(current_ltp - entry_price) × lots × lot_size` (adjusted for buy/sell direction)
- **Lot Sizes**: NIFTY 75, BANKNIFTY 30, FINNIFTY 25, MIDCPNIFTY 50, SENSEX 10, BANKEX 15
- **Database Models**: `paper_trading.py` — PaperPosition, PaperOrder, PaperTrade

### Data Model

```
PaperPosition
├── user_id (FK → User)
├── symbol (e.g., "NIFTY")
├── strike_price
├── option_type (CE/PE)
├── expiry_date
├── entry_price
├── quantity (lots)
├── direction (BUY/SELL)
├── status (OPEN/CLOSED)
├── exit_price (nullable)
├── pnl (computed on close)
└── timestamps
```

---

## 8. Algo Trading Engine

### Architecture

```
┌─────────────────────────────────────────────┐
│              ALGO ENGINE                     │
│         (algo_engine.py)                     │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │  SIGNAL GENERATION                     │  │
│  │  - Technical indicators (RSI, MACD)    │  │
│  │  - IV-based signals                   │  │
│  │  - OI change detection               │  │
│  │  - Multi-timeframe analysis           │  │
│  └──────────────────┬─────────────────────┘  │
│                     │                        │
│                     ▼                        │
│  ┌────────────────────────────────────────┐  │
│  │  RISK MANAGER                          │  │
│  │  (risk_manager.py)                    │  │
│  │                                        │  │
│  │  - Position sizing (% of capital)     │  │
│  │  - Max positions per strategy         │  │
│  │  - Stop-loss enforcement              │  │
│  │  - Daily loss limits                  │  │
│  │  - Margin requirements                │  │
│  └──────────────────┬─────────────────────┘  │
│                     │                        │
│                     ▼                        │
│  ┌────────────────────────────────────────┐  │
│  │  POSITION MONITOR                      │  │
│  │  (workers/position_monitor.py)        │  │
│  │                                        │  │
│  │  - Background task                    │  │
│  │  - Polls LTP for open positions       │  │
│  │  - Triggers stop-loss / target exits  │  │
│  │  - Updates P&L in real-time           │  │
│  └────────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
```

### Strategy Model

```
AlgoStrategy
├── user_id (FK → User)
├── name
├── index (NIFTY/BANKNIFTY/etc.)
├── strategy_type (MOMENTUM/MEAN_REVERSION/IV_BASED/etc.)
├── parameters (JSON — indicator configs)
├── risk_params (JSON — position size, stop-loss %)
├── status (ACTIVE/PAUSED/STOPPED)
├── signals → [AlgoSignal]
└── positions → [AlgoPosition]
```

---

## 9. Frontend Architecture

### React Application Structure

```
┌─────────────────────────────────────────────────────────────┐
│                      App.jsx (Router)                        │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  Landing Pages (public)                              │    │
│  │  Hero · Features · Calculator · Pricing · Download  │    │
│  │  Screenshots · Learn · Support · Footer              │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  App Pages (AppLayout wrapper)                       │    │
│  │  ┌────────────┐ ┌──────────────┐ ┌──────────────┐  │    │
│  │  │OptionChain │ │ AIInsights   │ │ PaperTrading │  │    │
│  │  ├────────────┤ ├──────────────┤ ├──────────────┤  │    │
│  │  │ Calculator │ │ AIChatbot    │ │ Backtest     │  │    │
│  │  ├────────────┤ ├──────────────┤ ├──────────────┤  │    │
│  │  │ Screener   │ │ Alerts       │ │ IPODashboard │  │    │
│  │  ├────────────┤ ├──────────────┤ ├──────────────┤  │    │
│  │  │ PnLSim     │ │ TradeJournal │ │ Education    │  │    │
│  │  └────────────┘ └──────────────┘ └──────────────┘  │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  Games (GamesHub)                                    │    │
│  │  PricePredictor · GreeksArena · StrategyShowdown    │    │
│  │  TradingTournament                                   │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  Global Components                                   │    │
│  │  Navbar · FloatingChatbot · LanguageSelector         │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

### State Management

```
┌──────────────────────────────┐
│  React Context               │
│                              │
│  AuthContext                 │
│  ├── user (current user)     │
│  ├── token (JWT)             │
│  ├── login() / logout()      │
│  └── isAuthenticated         │
│                              │
│  AdContext                   │
│  ├── ad configuration        │
│  └── ad display state        │
└──────────────────────────────┘
```

### Service Layer

API communication is abstracted through service modules:

| Service | Purpose |
|---------|---------|
| `authAPI.js` | Login, OTP, social auth, token management |
| `marketAPI.js` | Option chain, indices, spot prices |
| `paperTradingAPI.js` | Paper trading CRUD |
| `alertsAPI.js` | Alert management |
| `journalAPI.js` | Trade journal entries |
| `ipoAPI.js` | IPO data |
| `deviceAPI.js` | Push notification registration |
| `aiAnalysisService.js` | AI analysis with client-side scoring |
| `analytics.js` | Firebase analytics |
| `firebase.js` | Firebase initialization |

### Client-Side AI Scoring

`aiAnalysisService.js` applies additional scoring on top of backend AI suggestions:
- Uses backend-computed Delta (not client-side moneyness estimation)
- Probability of Profit (POP) calculation
- Risk/reward ratio assessment
- Composite score generation

---

## 10. Mobile Architecture

### Android (Kotlin + Jetpack Compose)

```
┌────────────────────────────────────────────────────┐
│  ANDROID APP — MVVM Architecture                    │
│                                                    │
│  ┌──────────────────────────────────────────────┐  │
│  │  UI Layer (Jetpack Compose)                  │  │
│  │  ┌──────────────┐  ┌─────────────────────┐  │  │
│  │  │ Calculator   │  │ OptionChain         │  │  │
│  │  │ Screen       │  │ Screen              │  │  │
│  │  ├──────────────┤  ├─────────────────────┤  │  │
│  │  │ PnLSimulator │  │ TradeJournal        │  │  │
│  │  │ Screen       │  │ Screen              │  │  │
│  │  ├──────────────┤  ├─────────────────────┤  │  │
│  │  │ OptionScreen │  │                     │  │  │
│  │  │ erScreen     │  │                     │  │  │
│  │  └──────────────┘  └─────────────────────┘  │  │
│  └────────────────────────┬─────────────────────┘  │
│                           │                        │
│  ┌────────────────────────▼─────────────────────┐  │
│  │  ViewModel Layer                              │  │
│  │  OptionChainViewModel · PnLSimulatorViewModel│  │
│  │  TradeJournalViewModel · OptionScreenerVM    │  │
│  │  - StateFlow for reactive UI updates         │  │
│  │  - Coroutines for async operations           │  │
│  └────────────────────────┬─────────────────────┘  │
│                           │                        │
│  ┌────────────────────────▼─────────────────────┐  │
│  │  Domain Layer                                 │  │
│  │  BlackScholesEngine · TargetCalculator       │  │
│  │  - Pure computation, no Android dependencies │  │
│  └────────────────────────┬─────────────────────┘  │
│                           │                        │
│  ┌────────────────────────▼─────────────────────┐  │
│  │  Data Layer                                   │  │
│  │  NSEApiService · JournalApiService           │  │
│  │  OptionData · TradeJournal · ScreenerFilter  │  │
│  │  SimulationResult                            │  │
│  └──────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────┘
```

### iOS (SwiftUI + Combine)

```
┌────────────────────────────────────────────────────────────┐
│  iOS APP — MVVM Architecture                                │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Views (SwiftUI — 40+ views)                         │  │
│  │                                                      │  │
│  │  OptionChain · Calculator · AIInsights · PaperTrade │  │
│  │  OIAnalysis · StrategyBuilder · Charts · Alerts     │  │
│  │  Chat · Education · Screener · Simulator · IPO      │  │
│  │  Journal · Settings · Auth · Onboarding              │  │
│  └───────────────────────┬──────────────────────────────┘  │
│                          │                                  │
│  ┌───────────────────────▼──────────────────────────────┐  │
│  │  ViewModels (10 ViewModels — ObservableObject)       │  │
│  │                                                      │  │
│  │  OptionChainVM · AIAnalysisVM · PaperTradingVM      │  │
│  │  ChatVM · OIAnalysisVM · StrategyBuilderVM          │  │
│  │  OptionScreenerVM · PnLSimulatorVM · IPOVM          │  │
│  │  TradeJournalVM                                      │  │
│  │                                                      │  │
│  │  - @Published properties for reactive binding       │  │
│  │  - Combine pipelines for async data flow            │  │
│  └───────────────────────┬──────────────────────────────┘  │
│                          │                                  │
│  ┌───────────────────────▼──────────────────────────────┐  │
│  │  Services (29 services)                              │  │
│  │                                                      │  │
│  │  Market Data:                                        │  │
│  │    UpstoxAPIService · NSEAPIService                  │  │
│  │    GuestDataService · DataProvider                   │  │
│  │                                                      │  │
│  │  Broker APIs:                                        │  │
│  │    ZerodhaAPIService · AngelOneAPIService            │  │
│  │    DhanAPIService · ShoonyaAPIService                │  │
│  │                                                      │  │
│  │  Computation:                                        │  │
│  │    BlackScholesEngine · TargetCalculator             │  │
│  │    OIAnalysisEngine · StrategyCalculationEngine      │  │
│  │    TechnicalAnalysisService · CandlestickPatternSvc  │  │
│  │    MLOptionPredictor                                  │  │
│  │                                                      │  │
│  │  AI:                                                 │  │
│  │    AIAnalysisService · ClaudeAIService · ChatService │  │
│  │                                                      │  │
│  │  Trading:                                            │  │
│  │    PaperTradingEngine · PaperTradingAPIService       │  │
│  │                                                      │  │
│  │  Infrastructure:                                     │  │
│  │    AuthManager · AuthAPIService · AlertManager       │  │
│  │    PushNotificationService · MarketAlertService      │  │
│  │    AppEnvironment                                    │  │
│  │                                                      │  │
│  │  WebSocket:                                          │  │
│  │    UpstoxWebSocketService · GuestWebSocketService    │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Models (13 model files)                             │  │
│  │  OptionData · GreeksResult · AIInsight · Strategy   │  │
│  │  PaperTrading · SmartAlert · ChatModels · AuthModels│  │
│  │  ChartData · OIAnalysis · IPOModels · TradeJournal  │  │
│  │  EducationContent                                    │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Localization (9 languages)                          │  │
│  │  English · Hindi · Tamil · Telugu · Kannada         │  │
│  │  Malayalam · Bengali · Odia · Punjabi                │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────┘
```

### iOS-Specific Features (not on other platforms)

- **OI Analysis Dashboard**: Open interest heatmaps, support/resistance zones, smart money tracking, IV surface visualization
- **Strategy Builder**: Multi-leg option strategy construction with payoff diagrams
- **Broker Integrations**: Zerodha, Angel One, Dhan, Shoonya API services
- **Technical Analysis**: Candlestick pattern recognition, technical indicators
- **ML Predictor**: On-device ML option price prediction

---

## 11. Database Schema

### Entity Relationships

```
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│    User      │──1:N─▶│   Session    │       │   Visitor    │
│              │       │              │       │  (anonymous) │
│  id (PK)     │       │  user_id(FK) │       │              │
│  phone       │       │  token       │       │  ip_address  │
│  email       │       │  device_info │       │  user_agent  │
│  name        │       │  expires_at  │       │  timestamp   │
│  avatar_url  │       └──────────────┘       └──────────────┘
│  auth_provider│
│  created_at  │
└──────┬───────┘
       │
       ├──1:N──▶ OTP (phone, code, attempts, expires_at)
       │
       ├──1:N──▶ PaperPosition (symbol, strike, entry_price, pnl, status)
       │
       ├──1:N──▶ BacktestRun (strategy, params, results, metrics)
       │
       ├──1:N──▶ Alert (symbol, condition, threshold, triggered)
       │
       ├──1:N──▶ AlgoStrategy ──1:N──▶ AlgoSignal
       │              └──1:N──▶ AlgoPosition
       │
       ├──1:N──▶ ChatSession ──1:N──▶ ChatMessage
       │
       ├──1:N──▶ TradeJournalEntry (trade details, notes, outcome)
       │
       └──1:N──▶ DeviceToken (platform, token, active)
```

### Migration History

| Version | Description |
|---------|-------------|
| 001 | Initial migration — users, sessions, OTP tables |
| 002 | Backtest tables |
| 003 | Chat tables (sessions, messages) |
| 004 | Visitor tracking tables |

---

## 12. Caching Strategy

### Redis Cache Layers

```
┌─────────────────────────────────────────────────────┐
│                   REDIS CACHE                        │
│                                                     │
│  ┌─────────────────────────────────────────────┐    │
│  │  Option Chain Cache                          │    │
│  │  Key: option_chain:{index}:{expiry}         │    │
│  │  TTL: 60 seconds                            │    │
│  │  Data: Full chain with Greeks + IV          │    │
│  └─────────────────────────────────────────────┘    │
│                                                     │
│  ┌─────────────────────────────────────────────┐    │
│  │  OTP Rate Limiting                           │    │
│  │  Key: otp_rate:{phone}                      │    │
│  │  TTL: 60 seconds (per-request cooldown)     │    │
│  │  Purpose: Prevent OTP spam                  │    │
│  └─────────────────────────────────────────────┘    │
│                                                     │
│  ┌─────────────────────────────────────────────┐    │
│  │  Session Cache                               │    │
│  │  Key: session:{user_id}                     │    │
│  │  Purpose: Quick session validation          │    │
│  └─────────────────────────────────────────────┘    │
│                                                     │
│  ┌─────────────────────────────────────────────┐    │
│  │  WebSocket Pub/Sub                           │    │
│  │  Channel: market_data                       │    │
│  │  Purpose: Broadcast LTP updates             │    │
│  └─────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
```

### Cache Service

`cache_service.py` provides a unified interface:
- `get(key)` / `set(key, value, ttl)`
- Falls back to in-memory dict if Redis is unavailable
- JSON serialization for complex objects

---

## 13. Deployment Architecture

### Environment Overview

```
┌───────────────────────────────────────────────────────────────┐
│                    DEPLOYMENT ENVIRONMENTS                      │
│                                                               │
│  ┌─────────────┐    ┌──────────────┐    ┌──────────────────┐ │
│  │    DEV       │    │     QA       │    │    PRODUCTION    │ │
│  │             │    │              │    │                  │ │
│  │ API: 8000   │    │ Nginx proxy  │    │ Nginx proxy     │ │
│  │ Web: 5173   │    │ api.optix.   │    │ Docker Compose  │ │
│  │ Hot reload  │    │ d23ai.in     │    │ (prod.yml)      │ │
│  │             │    │              │    │                  │ │
│  │ .env.dev    │    │ .env.qa      │    │ .env.prod       │ │
│  └─────────────┘    └──────────────┘    └──────────────────┘ │
└───────────────────────────────────────────────────────────────┘
```

### Docker Architecture

```
docker-compose.prod.yml
│
├── api (FastAPI)
│   ├── Build: nifty-auth-api/Dockerfile
│   ├── Port: 8000
│   ├── Healthcheck: curl http://localhost:8000/health
│   ├── Env: .env.prod
│   └── Depends: postgres, redis
│
├── postgres
│   ├── Image: postgres:15
│   ├── Port: 5432
│   ├── Volume: pgdata (persistent)
│   └── Env: POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD
│
└── redis
    ├── Image: redis:7-alpine
    ├── Port: 6379
    └── Volume: redisdata (persistent)
```

### Nginx Reverse Proxy

```
┌──────────────────────────────────────────────────┐
│  NGINX                                            │
│                                                  │
│  api.optix.d23ai.in ──▶ localhost:8000 (API)    │
│  optix.d23ai.in     ──▶ localhost:3000 (Web)    │
│                                                  │
│  - SSL termination                               │
│  - WebSocket upgrade support (/ws)              │
│  - Static file serving (production web build)   │
│  - Gzip compression                             │
│  - Proxy headers (X-Real-IP, X-Forwarded-For)   │
└──────────────────────────────────────────────────┘
```

### Service Management (optix.sh)

The `optix.sh` CLI manages all services across environments:

```bash
./optix.sh <environment> <service> <action>

# Environments: dev, qa, prod
# Services:     api, web, all
# Actions:      start, stop, restart, logs, status
```

PID files are stored in `.pids/` and logs in `.logs/` at the project root.

---

## 14. WebSocket & Real-time Data

### WebSocket Architecture

```
┌───────────────┐     ┌───────────────────────┐     ┌──────────────┐
│   Client      │     │  FastAPI WebSocket     │     │  Upstox      │
│   (Browser/   │◀───▶│  Handler              │◀────│  WebSocket   │
│    Mobile)    │     │  (/ws)                │     │  Feed        │
└───────────────┘     └───────────┬───────────┘     └──────────────┘
                                  │
                                  ▼
                      ┌───────────────────────┐
                      │  websocket_service.py  │
                      │                       │
                      │  - Connection pool    │
                      │  - Client registry    │
                      │  - Message routing    │
                      │  - Heartbeat/ping     │
                      └───────────────────────┘
```

### Real-time Data Flow

1. **Upstox WebSocket** connects to Upstox streaming API for live LTP updates
2. **WebSocket Service** maintains a registry of connected clients
3. **Market data updates** are broadcast to all subscribed clients
4. **iOS** uses both `UpstoxWebSocketService` (authenticated) and `GuestWebSocketService` (unauthenticated fallback)
5. **Heartbeat** mechanism keeps connections alive and detects stale clients

### Message Types

| Type | Direction | Description |
|------|-----------|-------------|
| `subscribe` | Client → Server | Subscribe to index/expiry updates |
| `unsubscribe` | Client → Server | Unsubscribe from updates |
| `market_data` | Server → Client | LTP, OI, volume updates |
| `ping/pong` | Bidirectional | Connection keepalive |

---

## Appendix: Key Constants

| Constant | Value | Usage |
|----------|-------|-------|
| Risk-free rate | 6.5% | Black-Scholes calculations (RBI repo rate) |
| Cache TTL | 60s | Option chain Redis cache |
| IV solver max iterations | 100 | Newton-Raphson convergence |
| IV solver tolerance | 1e-6 | Convergence threshold |
| IV bounds | 0.01 – 5.0 | Valid IV range |
| NIFTY lot size | 75 | Position sizing |
| BANKNIFTY lot size | 30 | Position sizing |
| FINNIFTY lot size | 25 | Position sizing |
| MIDCPNIFTY lot size | 50 | Position sizing |
| SENSEX lot size | 10 | Position sizing |
| BANKEX lot size | 15 | Position sizing |
