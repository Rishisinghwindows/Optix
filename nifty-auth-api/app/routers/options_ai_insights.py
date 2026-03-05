"""
AI Insights Router - Generates AI-powered trade suggestions
This endpoint provides the AI insights that the Android app expects
"""

import logging
from typing import Optional, List
from pydantic import BaseModel, Field, ConfigDict
from fastapi import APIRouter, HTTPException, Query
from datetime import datetime

from ..services.upstox_service import upstox_service

logger = logging.getLogger(__name__)


router = APIRouter(prefix="/api/v1/options-ai", tags=["AI Insights"])


# Helper function to convert camelCase to snake_case
def to_snake_case(name: str) -> str:
    import re
    return re.sub(r'(?<!^)(?=[A-Z])', '_', name).lower()


# Response Models matching Android app expectations (snake_case JSON output)

class ScoreReasoningResponse(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_snake_case,
        populate_by_name=True,
        json_encoders={float: lambda v: round(v, 2)}
    )

    factor: str
    score: int
    max_score: int = Field(default=100, serialization_alias="max_score")
    description: str
    is_positive: bool = Field(default=True, serialization_alias="is_positive")


class AISuggestionResponse(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_snake_case,
        populate_by_name=True
    )

    id: str
    strike_price: float = Field(serialization_alias="strike_price")
    option_type: str = Field(serialization_alias="option_type")  # "CE" or "PE"
    expiry: str
    action: str  # "BUY" or "SELL"
    entry_price: float = Field(serialization_alias="entry_price")
    target_price: float = Field(serialization_alias="target_price")
    stop_loss: float = Field(serialization_alias="stop_loss")
    confidence: str  # "HIGH", "MEDIUM", "LOW"
    score: int
    reasoning: List[ScoreReasoningResponse]
    risk_reward: float = Field(serialization_alias="risk_reward")
    max_profit: float = Field(serialization_alias="max_profit")
    max_loss: float = Field(serialization_alias="max_loss")


class MarketInsightResponse(BaseModel):
    title: str
    description: str
    sentiment: str  # "bullish", "bearish", "neutral"
    importance: str  # "high", "medium", "low"


class AIInsightsResponse(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_snake_case,
        populate_by_name=True
    )

    symbol: str
    spot_price: float = Field(serialization_alias="spot_price")
    sentiment: str
    suggestions: List[AISuggestionResponse]
    insights: List[MarketInsightResponse]
    pcr: float
    iv_percentile: float = Field(serialization_alias="iv_percentile")
    max_pain: float = Field(serialization_alias="max_pain")
    india_vix: Optional[float] = Field(default=None, serialization_alias="india_vix")
    vix_status: Optional[str] = Field(default=None, serialization_alias="vix_status")


def calculate_pcr(option_chain: list) -> float:
    """Calculate Put-Call Ratio from option chain"""
    total_put_oi = sum(row.get('PE', {}).get('openInterest', 0) or 0 for row in option_chain)
    total_call_oi = sum(row.get('CE', {}).get('openInterest', 0) or 0 for row in option_chain)
    if total_call_oi == 0:
        return 1.0
    return round(total_put_oi / total_call_oi, 2)


def calculate_max_pain(option_chain: list) -> float:
    """Calculate Max Pain strike from option chain"""
    if not option_chain:
        return 0.0

    strikes = []
    for row in option_chain:
        strike = row.get('strikePrice', 0)
        call_oi = row.get('CE', {}).get('openInterest', 0) or 0
        put_oi = row.get('PE', {}).get('openInterest', 0) or 0
        strikes.append({
            'strike': strike,
            'call_oi': call_oi,
            'put_oi': put_oi
        })

    # Max pain = strike where total option buyer loss is maximum
    min_pain = float('inf')
    max_pain_strike = strikes[len(strikes)//2]['strike'] if strikes else 0

    for i, s in enumerate(strikes):
        pain = 0
        for row in strikes:
            # Call buyers lose if strike < max_pain
            if row['strike'] < s['strike']:
                pain += row['call_oi'] * (s['strike'] - row['strike'])
            # Put buyers lose if strike > max_pain
            if row['strike'] > s['strike']:
                pain += row['put_oi'] * (row['strike'] - s['strike'])

        if pain < min_pain:
            min_pain = pain
            max_pain_strike = s['strike']

    return max_pain_strike


def get_vix_status(vix: float) -> str:
    """Get VIX status description"""
    if vix < 12:
        return "low"  # Low fear - complacent market
    elif vix < 15:
        return "normal"  # Normal volatility
    elif vix < 20:
        return "elevated"  # Elevated fear
    elif vix < 25:
        return "high"  # High fear
    else:
        return "extreme"  # Extreme fear/panic


def determine_sentiment(
    pcr: float,
    spot: float,
    max_pain: float,
    vix: Optional[float] = None,
    intraday_change_pct: Optional[float] = None
) -> tuple:
    """
    Determine market sentiment based on indicators including VIX and intraday movement.
    Returns (sentiment, bias_score, bias_details) to match iOS logic.
    """
    bullish_points = 0
    bearish_points = 0
    bias_details = []

    # 1. INTRADAY MOVEMENT - PRIMARY FACTOR (like iOS)
    # This is the key factor that was missing!
    if intraday_change_pct is not None:
        move_strength = abs(intraday_change_pct)

        # Calculate multiplier based on move strength (iOS logic)
        if move_strength > 0.5:
            move_multiplier = 1.5
        elif move_strength > 0.3:
            move_multiplier = 1.2
        else:
            move_multiplier = 1.0

        if intraday_change_pct >= 0.3:  # Bullish move threshold
            points = int(2.0 * move_multiplier)
            bullish_points += points
            bias_details.append(f"Intraday +{intraday_change_pct:.2f}% (+{points} bullish)")
        elif intraday_change_pct <= -0.3:  # Bearish move threshold
            points = int(2.0 * move_multiplier)
            bearish_points += points
            bias_details.append(f"Intraday {intraday_change_pct:.2f}% (+{points} bearish)")
        else:
            bias_details.append(f"Intraday {intraday_change_pct:.2f}% (neutral)")

    # 2. PCR analysis (2 points)
    if pcr > 1.2:
        bullish_points += 2  # High put writing = bullish
        bias_details.append(f"PCR {pcr:.2f} > 1.2 (+2 bullish)")
    elif pcr < 0.8:
        bearish_points += 2  # High call writing = bearish
        bias_details.append(f"PCR {pcr:.2f} < 0.8 (+2 bearish)")
    else:
        bias_details.append(f"PCR {pcr:.2f} (neutral)")

    # 3. Max pain analysis (2 points)
    max_pain_distance = (spot - max_pain) / spot * 100  # Percentage distance
    if spot < max_pain and max_pain_distance < -0.3:
        bullish_points += 2  # Spot below max pain = potential upside
        bias_details.append(f"Spot below MaxPain by {abs(max_pain_distance):.2f}% (+2 bullish)")
    elif spot > max_pain and max_pain_distance > 0.3:
        bearish_points += 2  # Spot above max pain = potential downside
        bias_details.append(f"Spot above MaxPain by {max_pain_distance:.2f}% (+2 bearish)")
    else:
        bias_details.append(f"Spot near MaxPain (neutral)")

    # 4. VIX analysis (contrarian indicator - 1 point)
    if vix is not None:
        if vix > 25:
            # Extreme fear often marks bottoms - contrarian bullish
            bullish_points += 1
            bias_details.append(f"VIX {vix:.1f} > 25 (extreme fear, +1 bullish contrarian)")
        elif vix < 12:
            # Complacency often precedes corrections - contrarian bearish
            bearish_points += 1
            bias_details.append(f"VIX {vix:.1f} < 12 (complacency, +1 bearish contrarian)")
        else:
            bias_details.append(f"VIX {vix:.1f} (normal)")

    # Calculate net bias
    net_bias = bullish_points - bearish_points

    # Determine sentiment with thresholds matching iOS
    if net_bias >= 4:
        sentiment = "strong_bullish"
    elif net_bias >= 2:
        sentiment = "bullish"
    elif net_bias <= -4:
        sentiment = "strong_bearish"
    elif net_bias <= -2:
        sentiment = "bearish"
    else:
        sentiment = "neutral"

    return sentiment, net_bias, bias_details


def score_option(
    option_data: dict,
    option_type: str,  # "CE" or "PE"
    spot_price: float,
    atm_strike: float,
    pcr: float,
    max_pain: float,
    vix: Optional[float],
    sentiment: str,
    total_call_oi: int,
    total_put_oi: int,
    rank_index: int = 0,  # Position in the option chain for variation
    intraday_change_pct: Optional[float] = None,  # Intraday momentum
    bias_score: int = 0  # Net bias score from sentiment calculation
) -> tuple:
    """Score an individual option based on multiple factors (like iOS)"""
    strike = option_data.get('strikePrice', 0)
    opt = option_data.get(option_type, {})

    ltp = opt.get('lastPrice', 0) or 0
    oi = opt.get('openInterest', 0) or 0
    volume = opt.get('totalTradedVolume', 0) or opt.get('volume', 0) or 0
    iv = opt.get('impliedVolatility', 0) or 0
    change_in_oi = opt.get('changeinOpenInterest', 0) or 0
    delta = opt.get('delta')
    theta = opt.get('theta')

    if ltp <= 0:
        return (0, [], "")

    score = 50  # Base score
    reasoning = []

    # 1. Moneyness Score (20 points max) - More granular scoring
    distance_from_atm = abs(strike - spot_price)
    atm_range = spot_price * 0.01  # 1% range for more granularity
    distance_ratio = distance_from_atm / (spot_price * 0.05)  # 0 to 1 within 5%

    if distance_from_atm < atm_range:
        moneyness_score = 20
        moneyness_desc = "ATM - High delta, optimal risk/reward"
    elif distance_from_atm < atm_range * 2:
        moneyness_score = 18
        moneyness_desc = "Near ATM - Excellent balance"
    elif distance_from_atm < atm_range * 3:
        moneyness_score = 16
        moneyness_desc = "Slightly OTM - Good leverage"
    elif distance_from_atm < atm_range * 4:
        moneyness_score = 14
        moneyness_desc = "OTM - Lower premium, higher risk"
    elif distance_from_atm < atm_range * 5:
        moneyness_score = 11
        moneyness_desc = "Deep OTM - Speculative"
    else:
        moneyness_score = 8
        moneyness_desc = "Far OTM - Very speculative"

    # Add fine-grained variation based on exact distance
    moneyness_score = max(6, min(20, moneyness_score - int(distance_ratio * 3)))
    score += moneyness_score - 10
    reasoning.append(ScoreReasoningResponse(factor="Moneyness", score=moneyness_score, max_score=20, description=moneyness_desc, is_positive=moneyness_score >= 14))

    # 2. OI Score (20 points max) - More granular based on actual OI ratio
    total_oi = total_call_oi if option_type == "CE" else total_put_oi
    oi_ratio = oi / max(total_oi, 1) * 100

    if oi_ratio > 10:
        oi_score = 20
        oi_desc = f"Highest OI concentration ({oi_ratio:.1f}%)"
    elif oi_ratio > 7:
        oi_score = 18
        oi_desc = f"Very high OI ({oi_ratio:.1f}%)"
    elif oi_ratio > 5:
        oi_score = 16
        oi_desc = f"High OI concentration ({oi_ratio:.1f}%)"
    elif oi_ratio > 3:
        oi_score = 14
        oi_desc = f"Good OI ({oi_ratio:.1f}%)"
    elif oi_ratio > 2:
        oi_score = 12
        oi_desc = f"Moderate OI ({oi_ratio:.1f}%)"
    elif oi_ratio > 1:
        oi_score = 10
        oi_desc = f"Low OI ({oi_ratio:.1f}%)"
    else:
        oi_score = 8
        oi_desc = f"Very low OI ({oi_ratio:.1f}%)"

    score += oi_score - 10
    reasoning.append(ScoreReasoningResponse(factor="OI Analysis", score=oi_score, max_score=20, description=oi_desc, is_positive=oi_score >= 14))

    # 3. OI Change Score (15 points max)
    if change_in_oi > 10000:
        oi_change_score = 15
        oi_change_desc = f"Strong OI buildup (+{change_in_oi:,})"
    elif change_in_oi > 0:
        oi_change_score = 12
        oi_change_desc = f"Positive OI change (+{change_in_oi:,})"
    elif change_in_oi > -5000:
        oi_change_score = 8
        oi_change_desc = "Stable OI"
    else:
        oi_change_score = 5
        oi_change_desc = f"OI unwinding ({change_in_oi:,})"
    score += oi_change_score - 7
    reasoning.append(ScoreReasoningResponse(factor="OI Change", score=oi_change_score, max_score=15, description=oi_change_desc, is_positive=change_in_oi > 0))

    # 4. Volume Score (15 points max)
    vol_oi_ratio = volume / max(oi, 1) * 100
    if vol_oi_ratio > 10:
        vol_score = 15
        vol_desc = "High activity - Strong interest"
    elif vol_oi_ratio > 3:
        vol_score = 12
        vol_desc = "Good liquidity"
    else:
        vol_score = 8
        vol_desc = "Low activity"
    score += vol_score - 7
    reasoning.append(ScoreReasoningResponse(factor="Volume", score=vol_score, max_score=15, description=vol_desc, is_positive=vol_score >= 12))

    # 5. IV Score (15 points max) — uses computed IV from Black-Scholes
    if iv > 0:
        if iv < 12:
            iv_score = 15
            iv_desc = f"Low IV ({iv:.1f}%) - Cheap premium, good for buying"
        elif iv < 18:
            iv_score = 13
            iv_desc = f"Moderate IV ({iv:.1f}%) - Fair pricing"
        elif iv < 25:
            iv_score = 10
            iv_desc = f"Elevated IV ({iv:.1f}%) - Consider selling"
        elif iv < 35:
            iv_score = 7
            iv_desc = f"High IV ({iv:.1f}%) - Expensive, favor selling"
        else:
            iv_score = 5
            iv_desc = f"Very high IV ({iv:.1f}%) - Premium is overpriced"
    else:
        iv_score = 10
        iv_desc = "IV not available"
    score += iv_score - 7
    reasoning.append(ScoreReasoningResponse(factor="IV Analysis", score=iv_score, max_score=15, description=iv_desc, is_positive=0 < iv < 20))

    # 5b. Delta Score (10 points max) — uses computed Greeks
    if delta is not None:
        abs_delta = abs(delta)
        if 0.35 <= abs_delta <= 0.65:
            delta_score = 10
            delta_desc = f"Optimal delta ({delta:.2f}) - Good risk/reward balance"
        elif 0.25 <= abs_delta <= 0.75:
            delta_score = 8
            delta_desc = f"Acceptable delta ({delta:.2f})"
        elif abs_delta < 0.15:
            delta_score = 4
            delta_desc = f"Very low delta ({delta:.2f}) - Far OTM, low probability"
        elif abs_delta > 0.85:
            delta_score = 6
            delta_desc = f"Deep ITM delta ({delta:.2f}) - High premium cost"
        else:
            delta_score = 6
            delta_desc = f"Delta ({delta:.2f})"
        score += delta_score - 5
        reasoning.append(ScoreReasoningResponse(factor="Delta", score=delta_score, max_score=10, description=delta_desc, is_positive=0.30 <= abs_delta <= 0.70))

    # 6. VIX Score (15 points max)
    if vix is not None:
        if vix > 20:
            vix_score = 15 if sentiment != "neutral" else 12
            vix_desc = f"High VIX ({vix:.1f}) - Elevated premiums"
        elif vix < 13:
            vix_score = 14
            vix_desc = f"Low VIX ({vix:.1f}) - Cheap options"
        else:
            vix_score = 11
            vix_desc = f"Normal VIX ({vix:.1f})"
        score += vix_score - 7
        reasoning.append(ScoreReasoningResponse(factor="India VIX", score=vix_score, max_score=15, description=vix_desc, is_positive=vix < 18))

    # 7. Momentum Score (20 points max) - NEW: Match iOS logic
    if intraday_change_pct is not None:
        is_bullish_option = (option_type == "CE")
        is_bullish_move = intraday_change_pct > 0

        if is_bullish_option and is_bullish_move:
            # CE option + bullish move = aligned
            momentum_score = min(20, int(10 + abs(intraday_change_pct) * 15))
            momentum_desc = f"Bullish momentum +{intraday_change_pct:.2f}% favors calls"
            score += momentum_score - 10
            reasoning.append(ScoreReasoningResponse(factor="Momentum", score=momentum_score, max_score=20, description=momentum_desc, is_positive=True))
        elif not is_bullish_option and not is_bullish_move:
            # PE option + bearish move = aligned
            momentum_score = min(20, int(10 + abs(intraday_change_pct) * 15))
            momentum_desc = f"Bearish momentum {intraday_change_pct:.2f}% favors puts"
            score += momentum_score - 10
            reasoning.append(ScoreReasoningResponse(factor="Momentum", score=momentum_score, max_score=20, description=momentum_desc, is_positive=True))
        elif is_bullish_option and not is_bullish_move:
            # CE option + bearish move = counter-trend
            momentum_score = max(5, int(10 - abs(intraday_change_pct) * 10))
            momentum_desc = f"Bearish momentum {intraday_change_pct:.2f}% against calls"
            score += momentum_score - 10
            reasoning.append(ScoreReasoningResponse(factor="Momentum", score=momentum_score, max_score=20, description=momentum_desc, is_positive=False))
        else:
            # PE option + bullish move = counter-trend
            momentum_score = max(5, int(10 - abs(intraday_change_pct) * 10))
            momentum_desc = f"Bullish momentum +{intraday_change_pct:.2f}% against puts"
            score += momentum_score - 10
            reasoning.append(ScoreReasoningResponse(factor="Momentum", score=momentum_score, max_score=20, description=momentum_desc, is_positive=False))

    # 8. Sentiment alignment bonus (using enhanced sentiment)
    is_bullish_option = (option_type == "CE")
    sentiment_aligned = False

    if sentiment in ["strong_bullish", "bullish"] and is_bullish_option:
        sentiment_aligned = True
        bonus = 8 if sentiment == "strong_bullish" else 5
        score += bonus
        reasoning.append(ScoreReasoningResponse(factor="Sentiment", score=15 + bonus, max_score=20, description=f"Strong alignment with {sentiment} bias", is_positive=True))
    elif sentiment in ["strong_bearish", "bearish"] and not is_bullish_option:
        sentiment_aligned = True
        bonus = 8 if sentiment == "strong_bearish" else 5
        score += bonus
        reasoning.append(ScoreReasoningResponse(factor="Sentiment", score=15 + bonus, max_score=20, description=f"Strong alignment with {sentiment} bias", is_positive=True))
    elif sentiment == "neutral":
        reasoning.append(ScoreReasoningResponse(factor="Sentiment", score=10, max_score=20, description="Neutral market", is_positive=True))
    else:
        # Counter-sentiment position
        reasoning.append(ScoreReasoningResponse(factor="Sentiment", score=6, max_score=20, description=f"Against {sentiment} sentiment", is_positive=False))

    # Determine action
    if vix and vix > 20:
        action = "SELL"  # High VIX favors selling
    elif vix and vix < 13:
        action = "BUY"  # Low VIX favors buying
    elif sentiment == "bullish" and option_type == "CE":
        action = "BUY"
    elif sentiment == "bearish" and option_type == "PE":
        action = "BUY"
    elif sentiment == "bullish" and option_type == "PE":
        action = "SELL"
    elif sentiment == "bearish" and option_type == "CE":
        action = "SELL"
    else:
        action = "SELL" if iv > 20 else "BUY"

    # Cap score at 98 (allow 95%+ for strong alignments like iOS)
    return (min(98, max(30, score)), reasoning, action)


def _compute_target_sl(ltp: float, iv_pct: float, action: str) -> tuple:
    """Compute IV-aware target and stop-loss prices.

    Higher IV → wider target/SL (more room for price movement).
    Lower IV → tighter target/SL.
    Maintains minimum 1.5:1 risk-reward for BUY trades.
    """
    iv_factor = max(0.15, min(0.60, iv_pct / 100))

    if action == "BUY":
        # BUY: target above entry, SL below
        sl_pct = max(0.25, min(0.45, 0.20 + iv_factor * 0.5))
        target_pct = max(0.40, sl_pct * 2.0)  # Ensure >= 1.5:1 R:R
        target = ltp * (1 + target_pct)
        stop_loss = ltp * (1 - sl_pct)
    else:
        # SELL: target below entry (premium decays), SL above
        target_pct = max(0.50, min(0.80, 0.60 + iv_factor * 0.3))
        sl_pct = max(0.50, min(1.0, 0.40 + iv_factor * 1.0))
        target = ltp * (1 - target_pct)
        stop_loss = ltp * (1 + sl_pct)

    return target, stop_loss


def generate_suggestions(
    symbol: str,
    spot_price: float,
    option_chain: list,
    expiry: str,
    sentiment: str,
    pcr: float,
    max_pain: float,
    vix: Optional[float] = None,
    max_suggestions: int = 10,
    intraday_change_pct: Optional[float] = None,
    bias_score: int = 0
) -> List[AISuggestionResponse]:
    """Generate AI trade suggestions by scoring all options (like iOS)"""
    suggestions = []

    if not option_chain:
        return suggestions

    # Find ATM strike
    atm_strike = min(option_chain, key=lambda x: abs(x.get('strikePrice', 0) - spot_price)).get('strikePrice', spot_price)

    # Calculate totals for scoring
    total_call_oi = sum(row.get('CE', {}).get('openInterest', 0) or 0 for row in option_chain)
    total_put_oi = sum(row.get('PE', {}).get('openInterest', 0) or 0 for row in option_chain)

    # VIX-based strategy adjustment
    vix_favors_selling = vix is not None and vix > 20
    vix_favors_buying = vix is not None and vix < 13

    # Score all options
    scored_calls = []
    scored_puts = []

    for idx, row in enumerate(option_chain):
        strike = row.get('strikePrice', 0)

        # Skip strikes too far from ATM
        if abs(strike - spot_price) > spot_price * 0.05:  # Within 5%
            continue

        # Score Call
        if row.get('CE', {}).get('lastPrice', 0):
            call_score, call_reasoning, call_action = score_option(
                row, "CE", spot_price, atm_strike, pcr, max_pain, vix, sentiment,
                total_call_oi, total_put_oi, idx, intraday_change_pct, bias_score
            )
            if call_score > 40:
                scored_calls.append({
                    'strike': strike,
                    'score': call_score,
                    'reasoning': call_reasoning,
                    'action': call_action,
                    'data': row.get('CE', {})
                })

        # Score Put
        if row.get('PE', {}).get('lastPrice', 0):
            put_score, put_reasoning, put_action = score_option(
                row, "PE", spot_price, atm_strike, pcr, max_pain, vix, sentiment,
                total_call_oi, total_put_oi, idx + 100, intraday_change_pct, bias_score
            )
            if put_score > 40:
                scored_puts.append({
                    'strike': strike,
                    'score': put_score,
                    'reasoning': put_reasoning,
                    'action': put_action,
                    'data': row.get('PE', {})
                })

    # Sort by score
    scored_calls.sort(key=lambda x: x['score'], reverse=True)
    scored_puts.sort(key=lambda x: x['score'], reverse=True)

    suggestion_id = 1

    # Get lot size for P&L calculation
    lot_sizes = {"NIFTY": 75, "BANKNIFTY": 30, "FINNIFTY": 25, "MIDCPNIFTY": 50, "SENSEX": 10}
    lot_size = lot_sizes.get(symbol, 25)

    # Add top calls (up to half of max)
    for call in scored_calls[:max_suggestions // 2]:
        ltp = call['data'].get('lastPrice', 100)
        action = call['action']
        iv_pct = call['data'].get('impliedVolatility', 0) or 15

        # IV-aware target/stop-loss
        target, stop_loss = _compute_target_sl(ltp, iv_pct, action)

        suggestions.append(AISuggestionResponse(
            id=str(suggestion_id),
            strike_price=call['strike'],
            option_type="CE",
            expiry=expiry,
            action=action,
            entry_price=round(ltp, 2),
            target_price=round(target, 2),
            stop_loss=round(stop_loss, 2),
            confidence="HIGH" if call['score'] >= 70 else "MEDIUM" if call['score'] >= 50 else "LOW",
            score=call['score'],
            reasoning=call['reasoning'],
            risk_reward=round(abs(target - ltp) / max(0.01, abs(ltp - stop_loss)), 1),
            max_profit=round(abs(target - ltp) * lot_size, 2),
            max_loss=round(abs(ltp - stop_loss) * lot_size, 2)
        ))
        suggestion_id += 1

    # Add top puts (up to half of max)
    for put in scored_puts[:max_suggestions // 2]:
        ltp = put['data'].get('lastPrice', 100)
        action = put['action']
        iv_pct = put['data'].get('impliedVolatility', 0) or 15

        # IV-aware target/stop-loss
        target, stop_loss = _compute_target_sl(ltp, iv_pct, action)

        suggestions.append(AISuggestionResponse(
            id=str(suggestion_id),
            strike_price=put['strike'],
            option_type="PE",
            expiry=expiry,
            action=action,
            entry_price=round(ltp, 2),
            target_price=round(target, 2),
            stop_loss=round(stop_loss, 2),
            confidence="HIGH" if put['score'] >= 70 else "MEDIUM" if put['score'] >= 50 else "LOW",
            score=put['score'],
            reasoning=put['reasoning'],
            risk_reward=round(abs(target - ltp) / max(0.01, abs(ltp - stop_loss)), 1),
            max_profit=round(abs(target - ltp) * lot_size, 2),
            max_loss=round(abs(ltp - stop_loss) * lot_size, 2)
        ))
        suggestion_id += 1

    # Sort all suggestions by score
    suggestions.sort(key=lambda x: x.score, reverse=True)

    return suggestions[:max_suggestions]



def generate_insights(
    sentiment: str,
    pcr: float,
    max_pain: float,
    spot: float,
    vix: Optional[float] = None,
    intraday_change_pct: Optional[float] = None,
    enhanced_sentiment: Optional[str] = None,
    bias_score: int = 0
) -> List[MarketInsightResponse]:
    """Generate market insights based on analysis including VIX and momentum"""
    insights = []

    # MOMENTUM insight (first - most important like iOS)
    if intraday_change_pct is not None:
        abs_change = abs(intraday_change_pct)
        if abs_change >= 0.5:
            # Strong momentum
            direction = "bullish" if intraday_change_pct > 0 else "bearish"
            insights.append(MarketInsightResponse(
                title=f"Strong {'Upward' if intraday_change_pct > 0 else 'Downward'} Momentum",
                description=f"Market has moved {'+' if intraday_change_pct > 0 else ''}{intraday_change_pct:.2f}% today. "
                           f"Strong momentum favors {'call' if intraday_change_pct > 0 else 'put'} buying strategies.",
                sentiment=direction,
                importance="high"
            ))
        elif abs_change >= 0.3:
            # Moderate momentum
            direction = "bullish" if intraday_change_pct > 0 else "bearish"
            insights.append(MarketInsightResponse(
                title=f"{'Positive' if intraday_change_pct > 0 else 'Negative'} Momentum",
                description=f"Market up {'+' if intraday_change_pct > 0 else ''}{intraday_change_pct:.2f}% today. "
                           f"Moderate momentum supports {'bullish' if intraday_change_pct > 0 else 'bearish'} bias.",
                sentiment=direction,
                importance="medium"
            ))

    # VIX insight (first as it's a key market indicator)
    if vix is not None:
        if vix > 25:
            insights.append(MarketInsightResponse(
                title="Extreme Fear (VIX)",
                description=f"India VIX at {vix:.1f} indicates panic. Option premiums are expensive. Consider selling strategies or wait for VIX to cool down before buying.",
                sentiment="bearish",
                importance="high"
            ))
        elif vix > 20:
            insights.append(MarketInsightResponse(
                title="High Volatility (VIX)",
                description=f"India VIX at {vix:.1f} shows elevated fear. Good opportunity for option sellers. Premiums are rich.",
                sentiment="neutral",
                importance="high"
            ))
        elif vix > 15:
            insights.append(MarketInsightResponse(
                title="Moderate Volatility (VIX)",
                description=f"India VIX at {vix:.1f} is in normal range. Both buying and selling strategies viable.",
                sentiment="neutral",
                importance="medium"
            ))
        elif vix > 12:
            insights.append(MarketInsightResponse(
                title="Low Volatility (VIX)",
                description=f"India VIX at {vix:.1f} indicates calm markets. Option premiums are cheap - good for buying strategies.",
                sentiment="bullish",
                importance="medium"
            ))
        else:
            insights.append(MarketInsightResponse(
                title="Extreme Calm (VIX)",
                description=f"India VIX at {vix:.1f} shows complacency. Historically low VIX often precedes volatility spikes. Consider buying cheap options for potential moves.",
                sentiment="bullish",
                importance="high"
            ))

    # PCR insight
    if pcr > 1.2:
        insights.append(MarketInsightResponse(
            title="High Put-Call Ratio",
            description=f"PCR at {pcr:.2f} indicates heavy put writing, suggesting bullish sentiment",
            sentiment="bullish",
            importance="high"
        ))
    elif pcr < 0.8:
        insights.append(MarketInsightResponse(
            title="Low Put-Call Ratio",
            description=f"PCR at {pcr:.2f} shows call dominance, indicating bearish pressure",
            sentiment="bearish",
            importance="high"
        ))
    else:
        insights.append(MarketInsightResponse(
            title="Balanced PCR",
            description=f"PCR at {pcr:.2f} suggests neutral market stance",
            sentiment="neutral",
            importance="medium"
        ))

    # Max Pain insight
    diff_from_max_pain = spot - max_pain
    if abs(diff_from_max_pain) > 100:
        direction = "above" if diff_from_max_pain > 0 else "below"
        move_direction = "down" if diff_from_max_pain > 0 else "up"
        insights.append(MarketInsightResponse(
            title="Max Pain Deviation",
            description=f"Spot is {abs(diff_from_max_pain):.0f} points {direction} max pain ({max_pain:.0f}). May gravitate {move_direction} towards expiry.",
            sentiment="bearish" if diff_from_max_pain > 0 else "bullish",
            importance="medium"
        ))

    # Overall sentiment insight
    insights.append(MarketInsightResponse(
        title=f"{sentiment.title()} Outlook",
        description=f"Overall market sentiment is {sentiment} based on OI patterns, PCR{' and VIX' if vix else ''} analysis",
        sentiment=sentiment,
        importance="high"
    ))

    return insights


@router.get("/insights/{symbol}", response_model=AIInsightsResponse)
async def get_ai_insights(
    symbol: str,
    expiry: Optional[str] = Query(None, description="Expiry date (e.g., 13-Feb-2025)")
):
    """
    Get AI-powered trade insights and suggestions for an index.

    Returns:
    - Market sentiment analysis
    - Trade suggestions with entry/target/stop-loss
    - Confidence scores and reasoning
    - Market insights (PCR, Max Pain analysis)
    """
    symbol = symbol.upper()

    # Validate symbol
    valid_symbols = ["NIFTY", "NIFTY50", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY", "SENSEX"]
    if symbol not in valid_symbols:
        raise HTTPException(status_code=400, detail=f"Invalid symbol. Use one of: {valid_symbols}")

    # Normalize symbol
    if symbol == "NIFTY50":
        symbol = "NIFTY"

    try:
        # Get spot price with intraday change
        spot_data = await upstox_service.get_spot_price(symbol)
        spot_price = spot_data.get('price', 0)
        intraday_change_pct = spot_data.get('pChange', 0)  # Percentage change from previous close

        # Get India VIX
        india_vix = None
        vix_status = None
        try:
            vix_data = await upstox_service.get_spot_price("INDIAVIX")
            india_vix = vix_data.get('price', 0)
            if india_vix and india_vix > 0:
                vix_status = get_vix_status(india_vix)
            else:
                india_vix = None
        except Exception as vix_error:
            logger.warning(f"Could not fetch India VIX: {vix_error}")
            india_vix = None

        # Get expiry dates if not provided
        if not expiry:
            expiries = await upstox_service.get_expiry_dates(symbol)
            expiry = expiries[0] if expiries else datetime.now().strftime("%d-%b-%Y")

        # Get option chain
        chain_data = await upstox_service.get_option_chain(symbol, expiry, strikes_around_atm=20)
        option_chain = chain_data.get('data', [])

        # Calculate metrics
        pcr = calculate_pcr(option_chain)
        max_pain = calculate_max_pain(option_chain)

        # NEW: Enhanced sentiment calculation with intraday movement (like iOS)
        sentiment, bias_score, bias_details = determine_sentiment(
            pcr, spot_price, max_pain, india_vix, intraday_change_pct
        )

        # Map enhanced sentiment to simple sentiment for backward compatibility
        simple_sentiment = sentiment
        if sentiment == "strong_bullish":
            simple_sentiment = "bullish"
        elif sentiment == "strong_bearish":
            simple_sentiment = "bearish"

        logger.info(f"{symbol}: {sentiment} (bias={bias_score}), intraday={intraday_change_pct:.2f}%, PCR={pcr:.2f}, VIX={india_vix}")
        for detail in bias_details:
            logger.debug(f"  {detail}")

        # Generate suggestions with momentum data
        suggestions = generate_suggestions(
            symbol=symbol,
            spot_price=spot_price,
            option_chain=option_chain,
            expiry=expiry,
            sentiment=sentiment,
            pcr=pcr,
            max_pain=max_pain,
            vix=india_vix,
            intraday_change_pct=intraday_change_pct,
            bias_score=bias_score
        )

        # Generate insights with VIX and momentum
        insights = generate_insights(
            simple_sentiment, pcr, max_pain, spot_price, india_vix,
            intraday_change_pct, sentiment, bias_score
        )

        # Calculate IV percentile from ATM options (now computed via Black-Scholes)
        avg_iv = 0.0
        if option_chain:
            atm_strike = min(option_chain, key=lambda x: abs(x.get('strikePrice', 0) - spot_price)).get('strikePrice', spot_price)
            atm_ivs = []
            for row in option_chain:
                if abs(row.get('strikePrice', 0) - atm_strike) <= spot_price * 0.02:
                    ce_iv = row.get('CE', {}).get('impliedVolatility', 0) or 0
                    pe_iv = row.get('PE', {}).get('impliedVolatility', 0) or 0
                    if ce_iv > 0:
                        atm_ivs.append(ce_iv)
                    if pe_iv > 0:
                        atm_ivs.append(pe_iv)
            if atm_ivs:
                avg_iv = sum(atm_ivs) / len(atm_ivs)
        # IV percentile: NIFTY historical IV range ~8-35%, BANKNIFTY ~10-45%
        # Use 35% as the high-IV benchmark
        iv_percentile = min(100, max(0, (avg_iv / 35) * 100))

        return AIInsightsResponse(
            symbol=symbol,
            spot_price=spot_price,
            sentiment=simple_sentiment,  # Use simple sentiment for backward compatibility
            suggestions=suggestions,
            insights=insights,
            pcr=pcr,
            iv_percentile=round(iv_percentile, 1),
            max_pain=max_pain,
            india_vix=round(india_vix, 2) if india_vix else None,
            vix_status=vix_status
        )

    except Exception as e:
        # Return demo data on error
        return generate_demo_insights(symbol)


def generate_demo_insights(symbol: str) -> AIInsightsResponse:
    """Generate demo insights when live data is unavailable"""
    spot_prices = {
        "NIFTY": 23150.0,
        "BANKNIFTY": 49200.0,
        "FINNIFTY": 23450.0,
        "MIDCPNIFTY": 12850.0,
        "SENSEX": 76500.0
    }

    spot = spot_prices.get(symbol, 23000.0)
    atm = round(spot / 50) * 50

    return AIInsightsResponse(
        symbol=symbol,
        spot_price=spot,
        sentiment="neutral",
        suggestions=[
            AISuggestionResponse(
                id="1",
                strike_price=atm,
                option_type="CE",
                expiry=datetime.now().strftime("%d-%b-%Y"),
                action="BUY",
                entry_price=165.0,
                target_price=250.0,
                stop_loss=120.0,
                confidence="MEDIUM",
                score=72,
                reasoning=[
                    ScoreReasoningResponse(factor="Technical", score=15, max_score=20, description="Near support levels", is_positive=True),
                    ScoreReasoningResponse(factor="OI Data", score=14, max_score=20, description="Moderate call writing", is_positive=True),
                    ScoreReasoningResponse(factor="Momentum", score=13, max_score=20, description="Sideways with upward bias", is_positive=True),
                ],
                risk_reward=1.9,
                max_profit=4250.0,
                max_loss=2250.0
            ),
            AISuggestionResponse(
                id="2",
                strike_price=atm,
                option_type="PE",
                expiry=datetime.now().strftime("%d-%b-%Y"),
                action="BUY",
                entry_price=155.0,
                target_price=240.0,
                stop_loss=115.0,
                confidence="MEDIUM",
                score=68,
                reasoning=[
                    ScoreReasoningResponse(factor="Hedging", score=14, max_score=20, description="Good for portfolio protection", is_positive=True),
                    ScoreReasoningResponse(factor="IV Level", score=13, max_score=20, description="IV at reasonable levels", is_positive=True),
                    ScoreReasoningResponse(factor="Risk Mgmt", score=12, max_score=20, description="Defined risk trade", is_positive=True),
                ],
                risk_reward=2.1,
                max_profit=4250.0,
                max_loss=2000.0
            ),
        ],
        insights=[
            MarketInsightResponse(
                title="Demo Mode",
                description="Live market data unavailable. Showing sample suggestions.",
                sentiment="neutral",
                importance="high"
            ),
            MarketInsightResponse(
                title="Connect for Live Data",
                description="Enable Upstox integration for real-time AI insights",
                sentiment="neutral",
                importance="medium"
            ),
        ],
        pcr=1.05,
        iv_percentile=45.0,
        max_pain=atm - 50,
        india_vix=15.5,
        vix_status="normal"
    )


@router.get("/status")
async def ai_insights_status():
    """Check AI insights service status"""
    return {
        "status": "ready",
        "dataSource": "upstox_live" if upstox_service.is_authenticated() else "demo",
        "features": ["trade_suggestions", "market_sentiment", "pcr_analysis", "max_pain"]
    }
