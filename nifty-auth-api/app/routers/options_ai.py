"""API routes for Options AI Analysis using Gemini."""

from typing import Optional, List, Dict, Any
from pydantic import BaseModel, Field
from fastapi import APIRouter, HTTPException, status

from app.services.options_ai_service import options_ai_service


router = APIRouter(prefix="/api/v1/options", tags=["Options AI"])


# Request/Response Models

class OptionData(BaseModel):
    """Option details for analysis."""
    strike_price: float = Field(..., description="Strike price")
    option_type: str = Field(..., description="CE or PE")
    ltp: float = Field(..., description="Last traded price")
    iv: float = Field(0, description="Implied volatility (0-1)")
    delta: Optional[float] = Field(None, description="Delta greek")
    theta: Optional[float] = Field(None, description="Theta greek")
    open_interest: int = Field(0, description="Open interest")
    oi_change: int = Field(0, description="Change in OI")
    volume: int = Field(0, description="Volume")
    days_to_expiry: int = Field(0, description="Days to expiry")


class MarketContext(BaseModel):
    """Market context for analysis."""
    spot_price: float = Field(..., description="Current spot price")
    pcr: float = Field(1.0, description="Put-call ratio")
    max_pain: Optional[float] = Field(None, description="Max pain strike")
    atm_strike: Optional[float] = Field(None, description="ATM strike")
    index_name: str = Field("NIFTY", description="Index name")
    support: Optional[float] = Field(None, description="Support level from OI")
    resistance: Optional[float] = Field(None, description="Resistance level from OI")
    india_vix: Optional[float] = Field(None, description="India VIX value")
    total_call_oi: Optional[int] = Field(None, description="Total call OI")
    total_put_oi: Optional[int] = Field(None, description="Total put OI")


class OptionChainRow(BaseModel):
    """Single row of option chain."""
    strike: float
    call_oi: int = 0
    call_oi_change: int = 0
    call_ltp: float = 0
    call_iv: float = 0
    call_volume: int = 0
    call_delta: Optional[float] = None
    call_theta: Optional[float] = None
    put_oi: int = 0
    put_oi_change: int = 0
    put_ltp: float = 0
    put_iv: float = 0
    put_volume: int = 0
    put_delta: Optional[float] = None
    put_theta: Optional[float] = None


class TradeSuggestion(BaseModel):
    """Trade suggestion details."""
    entry: float = Field(..., description="Entry price")
    target: float = Field(..., description="Target price")
    stop_loss: float = Field(..., description="Stop loss price")
    risk_reward: str = Field("1:1", description="Risk reward ratio")
    score: int = Field(50, description="System score 0-100")


class AnalyzeTradeRequest(BaseModel):
    """Request body for trade analysis."""
    option: OptionData
    market_context: MarketContext
    suggestion: TradeSuggestion
    option_chain: Optional[List[OptionChainRow]] = Field(None, description="Full option chain data")


class AnalyzeTradeResponse(BaseModel):
    """Response for trade analysis."""
    verdict: str = Field(..., description="YES, NO, or WAIT")
    win_probability: int = Field(..., description="Estimated win probability 0-100")
    key_reason: str = Field(..., description="Primary reason for verdict")
    risk_warning: str = Field(..., description="Main risk to watch")
    better_alternative: Optional[str] = Field(None, description="Suggested alternative if NO/WAIT")
    support_level: Optional[int] = Field(None, description="Key support level")
    resistance_level: Optional[int] = Field(None, description="Key resistance level")
    ai_powered: bool = Field(True, description="Whether response is from AI")
    disclaimer: str = Field(..., description="Legal disclaimer")


# Endpoints

@router.post("/analyze", response_model=AnalyzeTradeResponse)
async def analyze_trade(request: AnalyzeTradeRequest):
    """
    Analyze an options trade using AI (Google Gemini).

    Provides:
    - Verdict (YES/NO/WAIT)
    - Win probability estimate
    - Key reasoning
    - Risk warnings
    - Alternative suggestions

    The analysis considers:
    - Option Greeks (Delta, Theta, IV)
    - Open Interest patterns
    - PCR and market sentiment
    - Max pain levels
    - Support/Resistance from OI
    """
    # Build data dict for service
    trade_data = {
        'option': {
            'strike_price': request.option.strike_price,
            'option_type': request.option.option_type,
            'ltp': request.option.ltp,
            'iv': request.option.iv,
            'delta': request.option.delta or 0,
            'theta': request.option.theta or 0,
            'open_interest': request.option.open_interest,
            'oi_change': request.option.oi_change,
            'volume': request.option.volume,
            'days_to_expiry': request.option.days_to_expiry,
        },
        'market_context': {
            'spot_price': request.market_context.spot_price,
            'pcr': request.market_context.pcr,
            'max_pain': request.market_context.max_pain,
            'atm_strike': request.market_context.atm_strike or request.market_context.spot_price,
            'index_name': request.market_context.index_name,
            'support': request.market_context.support,
            'resistance': request.market_context.resistance,
            'india_vix': request.market_context.india_vix,
            'total_call_oi': request.market_context.total_call_oi,
            'total_put_oi': request.market_context.total_put_oi,
        },
        'suggestion': {
            'entry': request.suggestion.entry,
            'target': request.suggestion.target,
            'stop_loss': request.suggestion.stop_loss,
            'risk_reward': request.suggestion.risk_reward,
            'score': request.suggestion.score,
        },
        'option_chain': [
            {
                'strike': row.strike,
                'call_oi': row.call_oi,
                'call_oi_change': row.call_oi_change,
                'call_ltp': row.call_ltp,
                'call_iv': row.call_iv,
                'call_volume': row.call_volume,
                'call_delta': row.call_delta,
                'call_theta': row.call_theta,
                'put_oi': row.put_oi,
                'put_oi_change': row.put_oi_change,
                'put_ltp': row.put_ltp,
                'put_iv': row.put_iv,
                'put_volume': row.put_volume,
                'put_delta': row.put_delta,
                'put_theta': row.put_theta,
            }
            for row in (request.option_chain or [])
        ],
    }

    # Get AI analysis
    result = await options_ai_service.analyze_trade(trade_data)

    return AnalyzeTradeResponse(
        verdict=result.get('verdict', 'WAIT'),
        win_probability=result.get('win_probability', 50),
        key_reason=result.get('key_reason', 'Analysis completed'),
        risk_warning=result.get('risk_warning', 'Always use stop-loss'),
        better_alternative=result.get('better_alternative'),
        support_level=result.get('support_level'),
        resistance_level=result.get('resistance_level'),
        ai_powered=result.get('ai_powered', False),
        disclaimer=result.get('disclaimer', 'AI analysis is for educational purposes only.'),
    )


@router.get("/status")
async def get_ai_status():
    """Check if AI analysis is configured and available."""
    return {
        "configured": options_ai_service.is_configured(),
        "provider": "Google Gemini 2.5 Flash",
        "status": "ready" if options_ai_service.is_configured() else "not_configured",
    }
