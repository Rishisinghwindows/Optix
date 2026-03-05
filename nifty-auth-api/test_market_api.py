"""
Simple test server for Market Data API
Run with: python test_market_api.py
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import uvicorn

# Import the market router
from app.routers.market import router as market_router

app = FastAPI(
    title="Optix Market Data API",
    description="Live NSE Option Chain Data - No Authentication Required",
    version="1.0.0"
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include market router
app.include_router(market_router)


@app.get("/")
async def root():
    return {
        "name": "Optix Market Data API",
        "version": "1.0.0",
        "endpoints": {
            "indices": "/api/v1/market/indices",
            "status": "/api/v1/market/status",
            "spot": "/api/v1/market/spot/{symbol}",
            "expiries": "/api/v1/market/expiries/{symbol}",
            "option_chain": "/api/v1/market/option-chain/{symbol}",
            "greeks": "/api/v1/market/calculate-greeks",
        },
        "docs": "/docs"
    }


if __name__ == "__main__":
    print("\n🚀 Starting Optix Market Data API...")
    print("📊 Endpoints:")
    print("   - GET  /api/v1/market/indices")
    print("   - GET  /api/v1/market/status")
    print("   - GET  /api/v1/market/spot/{symbol}")
    print("   - GET  /api/v1/market/expiries/{symbol}")
    print("   - GET  /api/v1/market/option-chain/{symbol}")
    print("   - POST /api/v1/market/calculate-greeks")
    print("\n📖 API Docs: http://localhost:8000/docs\n")

    uvicorn.run(app, host="0.0.0.0", port=8000)
