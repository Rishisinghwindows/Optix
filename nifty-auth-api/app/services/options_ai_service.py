"""
Options AI Service - AI-powered options trade analysis using Google Gemini
Uses Google Gemini 2.5 Flash for analysis (FREE tier)
"""

import json
import logging
import re
from typing import Dict, Any, List, Optional

import httpx

from app.config import settings

logger = logging.getLogger(__name__)


# Gemini API configuration
GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"


class OptionsAIService:
    """Service for AI-powered options analysis using Gemini."""

    def __init__(self):
        self._gemini_api_key = settings.gemini_api_key

    def is_configured(self) -> bool:
        """Check if Gemini API is configured."""
        return bool(self._gemini_api_key)

    async def analyze_trade(self, trade_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Analyze an options trade using Gemini AI.

        Args:
            trade_data: Dictionary containing option details, market context, and option chain

        Returns:
            Analysis result with verdict, key insights, and risk warnings
        """
        if not self._gemini_api_key:
            return self._generate_fallback_response("Gemini API key not configured")

        prompt = self._build_analysis_prompt(trade_data)

        try:
            response = await self._call_gemini(prompt)
            return self._parse_response(response)
        except Exception as e:
            logger.error(f"Gemini error: {e}")
            return self._generate_fallback_response(str(e))

    def _build_analysis_prompt(self, data: Dict[str, Any]) -> str:
        """Build comprehensive prompt for options analysis."""

        option = data.get('option', {})
        market = data.get('market_context', {})
        chain = data.get('option_chain', [])
        suggestion = data.get('suggestion', {})

        # Option details
        strike = option.get('strike_price', 0)
        option_type = option.get('option_type', 'CE')
        ltp = option.get('ltp', 0)
        iv = option.get('iv', 0)
        delta = option.get('delta', 0)
        theta = option.get('theta', 0)
        oi = option.get('open_interest', 0)
        oi_change = option.get('oi_change', 0)
        volume = option.get('volume', 0)
        days_to_expiry = option.get('days_to_expiry', 0)

        # Market context
        spot = market.get('spot_price', 0)
        pcr = market.get('pcr', 1.0)
        max_pain = market.get('max_pain')
        atm_strike = market.get('atm_strike', spot)
        index_name = market.get('index_name', 'NIFTY')
        india_vix = market.get('india_vix')
        total_call_oi = market.get('total_call_oi', 0)
        total_put_oi = market.get('total_put_oi', 0)
        support = market.get('support')
        resistance = market.get('resistance')

        # Build option chain summary
        chain_summary = self._build_chain_summary(chain, spot) if chain else "Not available"

        # Suggestion details
        entry = suggestion.get('entry', ltp)
        target = suggestion.get('target', 0)
        stop_loss = suggestion.get('stop_loss', 0)
        risk_reward = suggestion.get('risk_reward', '1:1')
        score = suggestion.get('score', 50)

        # Moneyness classification
        moneyness_pct = (strike - spot) / spot * 100 if spot else 0
        if option_type == "CE":
            if moneyness_pct < -1:
                moneyness = f"ITM by {abs(moneyness_pct):.1f}%"
            elif moneyness_pct > 1:
                moneyness = f"OTM by {moneyness_pct:.1f}%"
            else:
                moneyness = "ATM"
        else:
            if moneyness_pct > 1:
                moneyness = f"ITM by {moneyness_pct:.1f}%"
            elif moneyness_pct < -1:
                moneyness = f"OTM by {abs(moneyness_pct):.1f}%"
            else:
                moneyness = "ATM"

        prompt = f"""You are an expert Indian options market analyst specializing in NIFTY/BANKNIFTY options with deep knowledge of OI analysis, IV patterns, smart money tracking, and max pain theory. Your goal is to PROTECT THE TRADER FROM LOSSES. Analyze this trade with the COMPLETE OPTION CHAIN DATA provided.

## TRADE UNDER ANALYSIS
- Index: {index_name}
- Option: {int(strike)} {option_type} ({moneyness})
- LTP: ₹{ltp:.2f}
- Days to Expiry: {days_to_expiry}
- IV: {f'{iv*100:.1f}%' if iv > 0 else 'Not available'}
- Delta: {f'{delta:.3f}' if delta else 'N/A'} | Theta: {f'{theta:.3f}' if theta else 'N/A'}
- Open Interest: {self._format_number(oi)}
- OI Change: {self._format_number(oi_change)} ({"LONG BUILDUP" if oi_change > 0 else "LONG UNWINDING"})
- Volume: {self._format_number(volume)}
- Volume/OI Ratio: {f'{volume/oi*100:.1f}%' if oi > 0 else 'N/A'}

## MARKET SNAPSHOT
- Spot Price: {spot:.2f}
- ATM Strike: {int(atm_strike)}
- Overall PCR: {pcr:.2f} ({"Bullish" if pcr > 1.1 else "Bearish" if pcr < 0.9 else "Neutral"})
- Max Pain: {int(max_pain) if max_pain else 'N/A'}
- Distance from Max Pain: {f'{((spot - max_pain) / max_pain * 100):.1f}%' if max_pain else 'N/A'} {f'({"Below" if spot < max_pain else "Above"})' if max_pain else ''}
- India VIX: {f'{india_vix:.2f}' if india_vix else 'N/A'} {f'({"HIGH VOLATILITY >18 - premium selling favored" if india_vix and india_vix > 18 else "LOW VOLATILITY <13 - option buying favored" if india_vix and india_vix < 13 else "MODERATE"})' if india_vix else ''}
- Support (max put OI): {int(support) if support else 'N/A'}
- Resistance (max call OI): {int(resistance) if resistance else 'N/A'}
- Total Call OI: {self._format_number(total_call_oi) if total_call_oi else 'N/A'}
- Total Put OI: {self._format_number(total_put_oi) if total_put_oi else 'N/A'}

{chain_summary}

## TRADE SETUP
- Entry: ₹{entry}
- Target: ₹{target} ({((target-entry)/entry*100):.1f}% profit)
- Stop Loss: ₹{stop_loss} ({((entry-stop_loss)/entry*100):.1f}% loss)
- Risk:Reward: {risk_reward}
- System Score: {score}/100

## CRITICAL ANALYSIS CHECKLIST

1. **MANDATORY REJECTION CONDITIONS** (say NO if ANY apply):
   - OTM option with delta < 0.15 and < 5 DTE (will expire worthless)
   - Strike is beyond the max OI resistance (for CE) or support (for PE)
   - Negative OI change (unwinding) with falling volume
   - VIX > 25 and trade is a BUY (premiums too expensive)
   - PCR strongly against direction (CE needs PCR > 0.9, PE needs PCR < 1.2)

2. **OI-Based Level Analysis**: Is the strike near major support/resistance? Check OI buildup at nearby strikes.

3. **Smart Money Flow**: OI change pattern — long buildup (price up + OI up) or short buildup (price down + OI up)?

4. **IV Assessment**: Is IV high or low compared to ATM? High IV = expensive premium. For BUY trades, prefer low IV. For SELL trades, prefer high IV.

5. **Max Pain Gravity**: Price gravitates toward max pain near expiry. Does this help or hurt the trade?

6. **Time Decay**: With {days_to_expiry} days left, calculate approximate daily theta cost as % of premium.

7. **Strike Selection**: Is this the optimal strike? Would ATM or slightly OTM be better?

## RESPONSE FORMAT

Respond ONLY with valid JSON (no markdown, no code blocks):
{{"verdict":"YES" or "NO" or "WAIT","win_probability":0-100,"key_reason":"Specific reason citing data (e.g., 'Strong put OI support at 23000 with +2.5L OI buildup')","risk_warning":"Specific risk (e.g., 'Max pain at 23200 may pull price down from current 23350')","better_alternative":"If NO/WAIT: suggest specific strike/strategy with reasoning","support_level":{int(support) if support else int(atm_strike - 200)},"resistance_level":{int(resistance) if resistance else int(atm_strike + 200)}}}

RULES:
- PROTECT THE TRADER — be conservative, minimize loss probability
- Only say YES if win_probability > 65% AND at least 3 factors align
- Say NO immediately if any mandatory rejection condition applies
- Say WAIT if signals are mixed or data is insufficient
- Always provide a specific actionable alternative with strike price if verdict is NO/WAIT
- Cite specific numbers from the chain data in your reasoning"""

        return prompt

    def _build_chain_summary(self, chain: List[Dict], spot: float) -> str:
        """Build comprehensive option chain analysis for the prompt."""
        if not chain:
            return "Not available"

        # Calculate totals
        total_call_oi = sum(row.get('call_oi', 0) for row in chain)
        total_put_oi = sum(row.get('put_oi', 0) for row in chain)
        total_call_volume = sum(row.get('call_volume', 0) for row in chain)
        total_put_volume = sum(row.get('put_volume', 0) for row in chain)

        # Find top Call OI strikes (resistance levels)
        call_data = [(
            row.get('strike', 0),
            row.get('call_oi', 0),
            row.get('call_oi_change', 0),
            row.get('call_ltp', 0),
            (row.get('call_iv', 0) or 0) / 100 if (row.get('call_iv', 0) or 0) > 1 else (row.get('call_iv', 0) or 0),
            row.get('call_volume', 0)
        ) for row in chain if row.get('call_oi', 0) > 0]
        call_data.sort(key=lambda x: x[1], reverse=True)
        top_call_oi = call_data[:5]

        # Find top Put OI strikes (support levels)
        put_data = [(
            row.get('strike', 0),
            row.get('put_oi', 0),
            row.get('put_oi_change', 0),
            row.get('put_ltp', 0),
            (row.get('put_iv', 0) or 0) / 100 if (row.get('put_iv', 0) or 0) > 1 else (row.get('put_iv', 0) or 0),
            row.get('put_volume', 0)
        ) for row in chain if row.get('put_oi', 0) > 0]
        put_data.sort(key=lambda x: x[1], reverse=True)
        top_put_oi = put_data[:5]

        # Find strikes with highest OI change (smart money activity)
        call_oi_change = [(row.get('strike', 0), row.get('call_oi_change', 0), row.get('call_ltp', 0))
                         for row in chain if row.get('call_oi_change', 0) != 0]
        call_oi_change.sort(key=lambda x: abs(x[1]), reverse=True)
        top_call_change = call_oi_change[:3]

        put_oi_change = [(row.get('strike', 0), row.get('put_oi_change', 0), row.get('put_ltp', 0))
                        for row in chain if row.get('put_oi_change', 0) != 0]
        put_oi_change.sort(key=lambda x: abs(x[1]), reverse=True)
        top_put_change = put_oi_change[:3]

        # Calculate IV skew (IV values may be in percentage or decimal)
        atm_strikes = [row for row in chain if abs(row.get('strike', 0) - spot) <= spot * 0.01]
        avg_call_iv = sum(row.get('call_iv', 0) for row in atm_strikes) / len(atm_strikes) if atm_strikes else 0
        avg_put_iv = sum(row.get('put_iv', 0) for row in atm_strikes) / len(atm_strikes) if atm_strikes else 0
        # Normalize: if values are > 1, they're in percentage form; convert to decimal for display
        if avg_call_iv > 1:
            avg_call_iv /= 100
        if avg_put_iv > 1:
            avg_put_iv /= 100
        iv_skew = avg_put_iv - avg_call_iv

        # Find immediate support/resistance from OI
        strikes_below = [row for row in chain if row.get('strike', 0) < spot]
        strikes_above = [row for row in chain if row.get('strike', 0) > spot]

        immediate_support = max(strikes_below, key=lambda x: x.get('put_oi', 0)).get('strike', 0) if strikes_below else 0
        immediate_resistance = max(strikes_above, key=lambda x: x.get('call_oi', 0)).get('strike', 0) if strikes_above else 0

        summary = f"""## OPTION CHAIN OVERVIEW
- Total Call OI: {self._format_number(total_call_oi)} | Total Put OI: {self._format_number(total_put_oi)}
- Chain PCR: {(total_put_oi/total_call_oi if total_call_oi > 0 else 0):.2f}
- Total Call Volume: {self._format_number(total_call_volume)} | Total Put Volume: {self._format_number(total_put_volume)}
- ATM IV Skew: {iv_skew*100:.1f}% (Put IV - Call IV, +ve = bearish sentiment)
- Immediate Support (max Put OI below spot): {int(immediate_support)}
- Immediate Resistance (max Call OI above spot): {int(immediate_resistance)}

## TOP 5 CALL OI STRIKES (Resistance Levels):
| Strike | OI | OI Chg | LTP | IV | Vol |
|--------|-----|--------|-----|-----|-----|
"""
        for strike, oi, chg, ltp, iv, vol in top_call_oi:
            chg_sign = "+" if chg > 0 else ""
            iv_str = f"{iv*100:.1f}%" if iv > 0 else f"{iv:.1f}%" if iv > 0 else "N/A"
            summary += f"| {int(strike)} | {self._format_number(oi)} | {chg_sign}{self._format_number(chg)} | ₹{ltp:.1f} | {iv_str} | {self._format_number(vol)} |\n"

        summary += f"""
## TOP 5 PUT OI STRIKES (Support Levels):
| Strike | OI | OI Chg | LTP | IV | Vol |
|--------|-----|--------|-----|-----|-----|
"""
        for strike, oi, chg, ltp, iv, vol in top_put_oi:
            chg_sign = "+" if chg > 0 else ""
            iv_str = f"{iv*100:.1f}%" if iv > 0 else f"{iv:.1f}%" if iv > 0 else "N/A"
            summary += f"| {int(strike)} | {self._format_number(oi)} | {chg_sign}{self._format_number(chg)} | ₹{ltp:.1f} | {iv_str} | {self._format_number(vol)} |\n"

        summary += f"""
## SMART MONEY ACTIVITY (Highest OI Change):
CALLS:
"""
        for strike, chg, ltp in top_call_change:
            activity = "Long Buildup" if chg > 0 else "Long Unwinding"
            summary += f"- {int(strike)}: {'+' if chg > 0 else ''}{self._format_number(chg)} ({activity}) @ ₹{ltp:.1f}\n"

        summary += "\nPUTS:\n"
        for strike, chg, ltp in top_put_change:
            activity = "Long Buildup" if chg > 0 else "Long Unwinding"
            summary += f"- {int(strike)}: {'+' if chg > 0 else ''}{self._format_number(chg)} ({activity}) @ ₹{ltp:.1f}\n"

        return summary

    async def _call_gemini(self, prompt: str) -> str:
        """Call Google Gemini API."""
        url = f"{GEMINI_API_URL}?key={self._gemini_api_key}"

        payload = {
            "contents": [
                {
                    "parts": [
                        {"text": prompt}
                    ]
                }
            ],
            "generationConfig": {
                "temperature": 0.3,  # Lower for more consistent analysis
                "maxOutputTokens": 1024,
                "topP": 0.9,
                "topK": 40
            }
        }

        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(
                url,
                headers={"Content-Type": "application/json"},
                json=payload
            )

            if response.status_code != 200:
                error_data = response.json()
                error_msg = error_data.get('error', {}).get('message', 'Unknown error')
                raise Exception(f"Gemini API error ({response.status_code}): {error_msg}")

            data = response.json()

            candidates = data.get('candidates', [])
            if not candidates:
                raise Exception("No response from Gemini")

            content = candidates[0].get('content', {})
            parts = content.get('parts', [])
            if not parts:
                raise Exception("Empty response from Gemini")

            return parts[0].get('text', '')

    def _parse_response(self, response: str) -> Dict[str, Any]:
        """Parse Gemini response JSON."""
        logger.debug(f"Raw response: {response[:500]}")

        try:
            # Try to find complete JSON object
            # First try direct JSON parse
            clean_response = response.strip()
            if clean_response.startswith('{'):
                # Find matching closing brace
                brace_count = 0
                end_idx = 0
                for i, char in enumerate(clean_response):
                    if char == '{':
                        brace_count += 1
                    elif char == '}':
                        brace_count -= 1
                        if brace_count == 0:
                            end_idx = i + 1
                            break

                if end_idx > 0:
                    json_str = clean_response[:end_idx]
                    parsed = json.loads(json_str)

                    verdict = parsed.get('verdict', 'WAIT').upper()
                    if verdict not in ['YES', 'NO', 'WAIT']:
                        verdict = 'WAIT'

                    logger.debug(f"Parsed verdict: {verdict}, prob: {parsed.get('win_probability')}")

                    return {
                        'verdict': verdict,
                        'win_probability': min(100, max(0, parsed.get('win_probability', 50))),
                        'key_reason': parsed.get('key_reason', 'Analysis completed'),
                        'risk_warning': parsed.get('risk_warning', 'Always use stop-loss'),
                        'better_alternative': parsed.get('better_alternative'),
                        'support_level': parsed.get('support_level'),
                        'resistance_level': parsed.get('resistance_level'),
                        'ai_powered': True,
                        'disclaimer': 'AI analysis is for educational purposes only. Not financial advice. Always do your own research.',
                    }

            # Fallback: try regex
            json_match = re.search(r'\{[^{}]*\}', response)
            if json_match:
                parsed = json.loads(json_match.group())
                verdict = parsed.get('verdict', 'WAIT').upper()
                if verdict not in ['YES', 'NO', 'WAIT']:
                    verdict = 'WAIT'
                return {
                    'verdict': verdict,
                    'win_probability': min(100, max(0, parsed.get('win_probability', 50))),
                    'key_reason': parsed.get('key_reason', 'Analysis completed'),
                    'risk_warning': parsed.get('risk_warning', 'Always use stop-loss'),
                    'better_alternative': parsed.get('better_alternative'),
                    'support_level': parsed.get('support_level'),
                    'resistance_level': parsed.get('resistance_level'),
                    'ai_powered': True,
                    'disclaimer': 'AI analysis is for educational purposes only.',
                }

        except json.JSONDecodeError as e:
            logger.warning(f"JSON parse error: {e}")

        # Try to extract fields using regex even if JSON is malformed
        try:
            verdict_match = re.search(r'"verdict"\s*:\s*"(\w+)"', response)
            prob_match = re.search(r'"win_probability"\s*:\s*(\d+)', response)
            reason_match = re.search(r'"key_reason"\s*:\s*"([^"]+)"', response)
            warning_match = re.search(r'"risk_warning"\s*:\s*"([^"]+)"', response)
            alt_match = re.search(r'"better_alternative"\s*:\s*"([^"]+)"', response)

            verdict = verdict_match.group(1).upper() if verdict_match else 'WAIT'
            if verdict not in ['YES', 'NO', 'WAIT']:
                verdict = 'WAIT'

            win_prob = int(prob_match.group(1)) if prob_match else 50
            key_reason = reason_match.group(1) if reason_match else 'Analysis completed'
            risk_warning = warning_match.group(1) if warning_match else 'Always use strict stop-loss'
            better_alt = alt_match.group(1) if alt_match else None

            logger.debug(f"Regex extracted - verdict: {verdict}, prob: {win_prob}")

            return {
                'verdict': verdict,
                'win_probability': min(100, max(0, win_prob)),
                'key_reason': key_reason,
                'risk_warning': risk_warning,
                'better_alternative': better_alt,
                'ai_powered': True,
                'disclaimer': 'AI analysis is for educational purposes only.',
            }
        except Exception as ex:
            logger.warning(f"Regex extraction failed: {ex}")

        # Final fallback
        return {
            'verdict': 'WAIT',
            'win_probability': 50,
            'key_reason': 'Unable to analyze trade. Please try again.',
            'risk_warning': 'Always use strict stop-loss',
            'better_alternative': None,
            'ai_powered': True,
            'disclaimer': 'AI analysis is for educational purposes only.',
        }

    def _generate_fallback_response(self, error: str) -> Dict[str, Any]:
        """Generate fallback response when AI fails."""
        logger.warning(f"AI analysis unavailable: {error}")
        return {
            'verdict': 'WAIT',
            'win_probability': 50,
            'key_reason': 'AI analysis temporarily unavailable',
            'risk_warning': 'Unable to assess risk - proceed with caution',
            'better_alternative': 'Consider waiting for AI analysis to be available',
            'ai_powered': False,
            'disclaimer': 'AI analysis is for educational purposes only.',
        }

    def _format_number(self, num: int) -> str:
        """Format number in Indian style (L/Cr)."""
        if num >= 10_000_000:
            return f"{num/10_000_000:.2f}Cr"
        elif num >= 100_000:
            return f"{num/100_000:.2f}L"
        elif num >= 1000:
            return f"{num/1000:.1f}K"
        return str(num)


# Singleton instance
options_ai_service = OptionsAIService()
