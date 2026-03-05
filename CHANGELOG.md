# Changelog

All notable changes to Optix will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.0.0] - 2026-03-05

### Added
- **Backend**: FastAPI server with PostgreSQL and Redis
- **Backend**: JWT authentication with OTP (Twilio/MSG91) and social login (Google/Apple/Facebook)
- **Backend**: Market data pipeline with Upstox, NSE India, and fallback sources
- **Backend**: AI trade analysis using Google Gemini 2.5 Flash with IV-aware targets
- **Backend**: AI chatbot with OpenAI/Anthropic and RAG knowledge base (ChromaDB)
- **Backend**: Paper trading system with position tracking and P&L analytics
- **Backend**: Algo trading engine with signal generation and risk management
- **Backend**: Backtesting engine for strategy simulation
- **Backend**: Price, IV, and OI alert system with push notifications
- **Backend**: IPO dashboard data service
- **Backend**: Trade journal API
- **Backend**: WebSocket support for real-time market data
- **Backend**: Admin dashboard
- **Backend**: Black-Scholes Greeks service with Newton-Raphson IV solver
- **Web**: React 18 + Vite web application
- **Web**: Live option chain with Greeks and OI analysis
- **Web**: Black-Scholes calculator
- **Web**: Paper trading dashboard
- **Web**: AI insights and chatbot interfaces
- **Web**: Backtesting UI
- **Web**: Option screener
- **Web**: P&L simulator
- **Web**: IPO dashboard
- **Web**: Trade journal
- **Web**: Educational trading games (Price Predictor, Greeks Arena, Strategy Showdown, Trading Tournament)
- **Web**: Multi-language support (9 languages via i18next)
- **Android**: Kotlin + Jetpack Compose application
- **Android**: Option chain viewer
- **Android**: Black-Scholes calculator with Greeks
- **Android**: P&L simulator
- **Android**: Option screener
- **Android**: Trade journal
- **iOS**: SwiftUI + Combine application
- **iOS**: Full option chain with OI analysis dashboard
- **iOS**: AI trade analysis and insights
- **iOS**: Paper trading with performance analytics
- **iOS**: Strategy builder with payoff diagrams
- **iOS**: Technical analysis and candlestick pattern recognition
- **iOS**: Broker integrations (Upstox, Zerodha, Angel One, Dhan, Shoonya)
- **iOS**: Multi-language support (9 Indian languages)
- **DevOps**: Docker and Docker Compose for dev/QA/prod environments
- **DevOps**: Nginx reverse proxy configuration
- **DevOps**: optix.sh CLI for service management
- **Docs**: README.md with setup instructions
- **Docs**: ARCHITECTURE.md with comprehensive technical documentation
