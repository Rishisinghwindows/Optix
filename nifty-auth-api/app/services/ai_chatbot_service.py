"""
AI Chatbot Service - Handles AI interactions for options trading assistant
Supports OpenAI GPT and Anthropic Claude models with RAG-based retrieval
Includes live market data analysis for intelligent option recommendations
"""

import json
import httpx
import re
from typing import Optional, List, Dict, Any, AsyncGenerator
from datetime import datetime

from app.config import settings
from app.services.cache_service import cache
# Note: Chatbot reads from Redis cache only - does not call Upstox API directly

# RAG is optional - may not be available due to dependency issues
try:
    from app.services.rag_service import rag_service
    RAG_AVAILABLE = True
except ImportError as e:
    print(f"RAG service not available: {e}")
    RAG_AVAILABLE = False
    rag_service = None


class AIChatbotService:
    """Service for AI-powered options trading chatbot with RAG support"""

    # Base system prompt - knowledge comes from RAG
    @property
    def SYSTEM_PROMPT(self) -> str:
        today = datetime.now().strftime("%d %B %Y")
        year = datetime.now().year
        return f"""You are Optix AI, an expert options trading assistant for the Indian stock market.

**IMPORTANT: Today's date is {today}. The current year is {year}. Always use current/upcoming expiry dates, never use old dates from 2023 or 2024.**

Your expertise includes:
- NSE/BSE options trading (NIFTY 50, BANK NIFTY, FINNIFTY, MIDCPNIFTY, SENSEX)
- Option Greeks (Delta, Gamma, Theta, Vega, Rho) and their practical applications
- Options strategies (straddles, strangles, spreads, iron condors, butterflies)
- Technical analysis (RSI, MACD, support/resistance, trend analysis)
- Market sentiment analysis using Put-Call Ratio (PCR), Max Pain, Open Interest
- Risk management and position sizing
- Black-Scholes model and options pricing

Guidelines:
1. Always provide actionable insights with specific strike prices when recommending trades
2. Include risk warnings and stop-loss recommendations
3. Explain the reasoning behind your suggestions
4. When given market context, use it to provide relevant advice
5. Keep responses concise but informative (2-4 paragraphs max)
6. Use Indian market terminology (CE for Call, PE for Put, expiry dates format)
7. If asked about topics outside options trading, politely redirect to your expertise
8. Never provide guaranteed returns or certainty - always mention risks
9. Format numbers with Indian notation where appropriate (lakhs, crores)
10. When discussing strategies, mention max profit, max loss, and breakeven points
11. Always consider the current IV environment when suggesting option trades
12. Use the provided knowledge context to give accurate, detailed answers

Remember: You're helping traders make informed decisions, not providing financial advice that guarantees profits."""

    # Keywords that indicate user wants option recommendations or market analysis
    OPTION_RECOMMENDATION_KEYWORDS = [
        # Option recommendations
        "which call", "which put", "best call", "best put", "best option",
        "suggest call", "suggest put", "suggest option", "recommend call",
        "recommend put", "recommend option", "buy call", "buy put",
        "what to buy", "option to buy", "call to buy", "put to buy",
        "potential", "opportunity", "high potential", "best strike",
        "which strike", "profitable", "good call", "good put",
        "ce to buy", "pe to buy", "best ce", "best pe",
        "today's call", "today's put", "intraday option",
        "option for today", "trade today", "best trade",
        # Market sentiment queries - need live PCR data
        "bullish", "bearish", "sentiment", "pcr", "put call ratio",
        "market trend", "market direction", "oi analysis", "open interest",
        "support", "resistance", "max pain", "option chain",
        "market view", "trend today", "outlook", "analysis",
    ]

    def __init__(self):
        self._openai_api_key: Optional[str] = None
        self._anthropic_api_key: Optional[str] = None
        self._model = "gpt-4o-mini"  # Default to cost-effective model
        self._provider = "openai"  # Default provider
        self._use_rag = True  # Enable RAG by default
        self._use_live_data = True  # Enable live market data analysis

    def configure(
        self,
        openai_api_key: Optional[str] = None,
        anthropic_api_key: Optional[str] = None,
        model: Optional[str] = None,
        provider: Optional[str] = None,
        use_rag: bool = True,
    ):
        """Configure the AI service with API keys and model preferences"""
        if openai_api_key:
            self._openai_api_key = openai_api_key
            # Also configure RAG service with the same key for embeddings
            if RAG_AVAILABLE and rag_service is not None:
                rag_service.configure(openai_api_key)
        if anthropic_api_key:
            self._anthropic_api_key = anthropic_api_key
        if model:
            self._model = model
        if provider:
            self._provider = provider
        self._use_rag = use_rag

    def is_configured(self) -> bool:
        """Check if the service has valid API keys"""
        if self._provider == "openai":
            return bool(self._openai_api_key)
        elif self._provider == "anthropic":
            return bool(self._anthropic_api_key)
        return False

    def is_rag_ready(self) -> bool:
        """Check if RAG is configured and has knowledge loaded"""
        if not RAG_AVAILABLE or rag_service is None:
            return False
        return rag_service.is_configured() and rag_service.is_initialized()

    def _get_rag_context(self, query: str) -> str:
        """Retrieve relevant context from RAG for the query"""
        if not RAG_AVAILABLE or rag_service is None:
            return ""
        if not self._use_rag or not rag_service.is_configured():
            return ""

        try:
            context = rag_service.get_context_for_query(
                query=query,
                max_tokens=1500,  # Limit context size
                n_results=5  # Get top 5 relevant chunks
            )
            if context:
                return f"\n\n## Relevant Knowledge:\n{context}"
            return ""
        except Exception as e:
            print(f"RAG retrieval error: {e}")
            return ""

    def _build_market_context_message(self, market_context: Optional[Dict[str, Any]]) -> str:
        """Build a context message from market data"""
        if not market_context:
            return ""

        context_parts = ["\n\n## Current Market Context:"]

        if "index" in market_context:
            context_parts.append(f"- **Index**: {market_context['index']}")

        if "spot_price" in market_context:
            context_parts.append(f"- **Spot Price**: ₹{market_context['spot_price']:,.2f}")

        if "pcr" in market_context:
            pcr = market_context['pcr']
            if pcr < 0.7:
                interpretation = "Extremely bullish (contrarian bearish)"
            elif pcr < 0.9:
                interpretation = "Bullish sentiment"
            elif pcr < 1.1:
                interpretation = "Neutral"
            elif pcr < 1.3:
                interpretation = "Bearish sentiment"
            else:
                interpretation = "Extremely bearish (contrarian bullish)"
            context_parts.append(f"- **Put-Call Ratio**: {pcr:.2f} ({interpretation})")

        if "atm_iv" in market_context:
            iv = market_context['atm_iv']
            context_parts.append(f"- **ATM Implied Volatility**: {iv:.1f}%")

        if "max_pain" in market_context:
            context_parts.append(f"- **Max Pain Strike**: {market_context['max_pain']}")

        if "expiry" in market_context:
            context_parts.append(f"- **Current Expiry**: {market_context['expiry']}")

        if "days_to_expiry" in market_context:
            dte = market_context['days_to_expiry']
            if dte == 0:
                context_parts.append(f"  - ⚠️ EXPIRY DAY - High gamma risk")
            else:
                context_parts.append(f"  - {dte} days to expiry")

        if "trend" in market_context:
            context_parts.append(f"- **Market Trend**: {market_context['trend']}")

        if "support" in market_context:
            context_parts.append(f"- **Key Support**: {market_context['support']}")

        if "resistance" in market_context:
            context_parts.append(f"- **Key Resistance**: {market_context['resistance']}")

        if "vix" in market_context:
            vix = market_context['vix']
            if vix < 12:
                vix_status = "Low fear - complacent"
            elif vix < 20:
                vix_status = "Normal"
            elif vix < 25:
                vix_status = "Elevated fear"
            else:
                vix_status = "High fear"
            context_parts.append(f"- **India VIX**: {vix:.2f} ({vix_status})")

        return "\n".join(context_parts)

    def _is_option_recommendation_query(self, query: str) -> tuple[bool, str, str]:
        """
        Detect if user is asking for option recommendations.
        Returns: (is_recommendation_query, detected_index, option_type)
        """
        query_lower = query.lower()

        # Check for recommendation keywords
        is_recommendation = any(kw in query_lower for kw in self.OPTION_RECOMMENDATION_KEYWORDS)

        # Detect index
        detected_index = "NIFTY"  # Default
        if "banknifty" in query_lower or "bank nifty" in query_lower:
            detected_index = "BANKNIFTY"
        elif "finnifty" in query_lower or "fin nifty" in query_lower:
            detected_index = "FINNIFTY"
        elif "midcpnifty" in query_lower or "midcap" in query_lower:
            detected_index = "MIDCPNIFTY"
        elif "sensex" in query_lower:
            detected_index = "SENSEX"

        # Detect option type preference
        option_type = "both"  # Default to analyze both
        if any(w in query_lower for w in ["call", "ce", "bullish"]):
            option_type = "CE"
        elif any(w in query_lower for w in ["put", "pe", "bearish"]):
            option_type = "PE"

        return is_recommendation, detected_index, option_type

    async def _fetch_and_analyze_options(self, index: str, option_type: str = "both") -> str:
        """
        Get option chain data from Redis cache (populated by Upstox service).
        Does NOT make direct API calls - only reads cached data.
        Returns formatted analysis string for AI context.
        """
        try:
            # Read spot price from cache
            spot_cache_key = f"spot:{index}"
            spot_data = await cache.get(spot_cache_key)

            # If not in short cache, try last_live cache
            if not spot_data:
                spot_data = await cache.get(f"last_live:{spot_cache_key}")

            if not spot_data:
                return "\n\n⚠️ **Market Data Not Available**: No cached data found. Please ensure market data is being fetched (visit the website or wait for data refresh)."

            spot_price = spot_data.get("lastPrice", 0) or spot_data.get("price", 0)

            # Read VIX from cache
            vix = None
            try:
                vix_data = await cache.get("spot:INDIAVIX") or await cache.get("last_live:spot:INDIAVIX")
                if vix_data:
                    vix = vix_data.get("lastPrice", 0) or vix_data.get("price", 0)
            except:
                pass

            # Read expiry dates from cache
            expiry_cache_key = f"expiry:{index}"
            expiries = await cache.get(expiry_cache_key) or await cache.get(f"last_live:{expiry_cache_key}")

            if not expiries:
                return "\n\n⚠️ **Expiry Data Not Cached**: Please visit option chain on website to populate cache."

            current_expiry = expiries[0] if expiries else None

            # Read option chain from cache - try different strike counts
            chain_data = None
            for strikes in [10, 15, 20, 50]:
                chain_cache_key = f"chain:{index}:{current_expiry}:{strikes}"
                chain_data = await cache.get(chain_cache_key) or await cache.get(f"last_live:{chain_cache_key}")
                if chain_data:
                    print(f"[CHATBOT] Found cached chain for {index} with {strikes} strikes")
                    break

            if not chain_data or not chain_data.get("data"):
                return "\n\n⚠️ **Option Chain Not Cached**: Please visit option chain on website to populate cache."

            # Analyze the cached data
            analysis = self._analyze_option_chain(
                chain_data,
                spot_price,
                option_type,
                vix
            )

            # Add cache info
            timestamp = chain_data.get("timestamp", "Unknown")
            data_source = chain_data.get("dataSource", spot_data.get("dataSource", "cache"))
            analysis += f"\n\n*Data Source: {data_source} | Last Updated: {timestamp}*"

            return analysis

        except Exception as e:
            print(f"[CHATBOT] Error reading from cache: {e}")
            return f"\n\n⚠️ **Cache Error**: {str(e)}"

    def _analyze_option_chain(
        self,
        chain_data: Dict[str, Any],
        spot_price: float,
        option_type: str,
        vix: Optional[float] = None
    ) -> str:
        """
        Analyze option chain data and identify high-potential options.
        """
        # Get today's date for context
        today = datetime.now().strftime("%d %B %Y")

        analysis_parts = ["\n\n## 📊 LIVE OPTION CHAIN ANALYSIS"]
        analysis_parts.append(f"**Today's Date**: {today}")

        symbol = chain_data.get("symbol", "")
        atm_strike = chain_data.get("atmStrike", 0)

        # Get expiry - try multiple sources
        expiry = chain_data.get("selectedExpiry", "")
        if not expiry:
            expiry_dates = chain_data.get("expiryDates", [])
            expiry = expiry_dates[0] if expiry_dates else ""

        lot_size = chain_data.get("lotSize", 0)
        data = chain_data.get("data", [])

        analysis_parts.append(f"\n**Index**: {symbol}")
        analysis_parts.append(f"**Spot Price**: ₹{spot_price:,.2f}")
        analysis_parts.append(f"**ATM Strike**: {atm_strike}")
        analysis_parts.append(f"**⚠️ CURRENT EXPIRY DATE**: {expiry}")
        analysis_parts.append(f"**Lot Size**: {lot_size}")
        if vix:
            analysis_parts.append(f"**India VIX**: {vix:.2f}")

        # Add strong instruction about expiry
        analysis_parts.append(f"\n**IMPORTANT**: All recommendations must use the expiry date: {expiry}. DO NOT use any other date.")

        # Calculate total OI and PCR
        total_call_oi = 0
        total_put_oi = 0

        for row in data:
            if row.get("CE"):
                total_call_oi += row["CE"].get("openInterest", 0)
            if row.get("PE"):
                total_put_oi += row["PE"].get("openInterest", 0)

        pcr = total_put_oi / total_call_oi if total_call_oi > 0 else 0
        analysis_parts.append(f"**Put-Call Ratio (OI)**: {pcr:.2f}")

        if pcr > 1.2:
            analysis_parts.append("  → *Sentiment: Bullish (High put writing)*")
        elif pcr < 0.8:
            analysis_parts.append("  → *Sentiment: Bearish (High call writing)*")
        else:
            analysis_parts.append("  → *Sentiment: Neutral*")

        # Analyze CALL options
        if option_type in ["both", "CE"]:
            call_analysis = self._analyze_calls(data, spot_price, atm_strike, lot_size)
            analysis_parts.append(call_analysis)

        # Analyze PUT options
        if option_type in ["both", "PE"]:
            put_analysis = self._analyze_puts(data, spot_price, atm_strike, lot_size)
            analysis_parts.append(put_analysis)

        # Add trading notes
        analysis_parts.append("\n### ⚠️ Key Observations:")

        # Find max OI strikes (support/resistance)
        max_call_oi_strike = 0
        max_put_oi_strike = 0
        max_call_oi = 0
        max_put_oi = 0

        for row in data:
            if row.get("CE") and row["CE"].get("openInterest", 0) > max_call_oi:
                max_call_oi = row["CE"]["openInterest"]
                max_call_oi_strike = row["strikePrice"]
            if row.get("PE") and row["PE"].get("openInterest", 0) > max_put_oi:
                max_put_oi = row["PE"]["openInterest"]
                max_put_oi_strike = row["strikePrice"]

        analysis_parts.append(f"- **Max Call OI (Resistance)**: {max_call_oi_strike} ({max_call_oi:,} OI)")
        analysis_parts.append(f"- **Max Put OI (Support)**: {max_put_oi_strike} ({max_put_oi:,} OI)")

        # Range prediction
        analysis_parts.append(f"- **Expected Range**: {max_put_oi_strike} - {max_call_oi_strike}")

        return "\n".join(analysis_parts)

    def _analyze_calls(
        self,
        data: List[Dict],
        spot_price: float,
        atm_strike: float,
        lot_size: int
    ) -> str:
        """Analyze call options and find best opportunities"""
        parts = ["\n### 📈 TOP CALL OPTIONS (CE):"]

        call_options = []
        for row in data:
            ce = row.get("CE")
            if not ce:
                continue

            strike = row["strikePrice"]
            ltp = ce.get("lastPrice", 0)
            oi = ce.get("openInterest", 0)
            oi_change = ce.get("changeinOpenInterest", 0)
            volume = ce.get("totalTradedVolume", 0)
            change = ce.get("change", 0)
            pchange = ce.get("pChange", 0)

            # Calculate score based on multiple factors
            score = 0

            # Prefer slightly OTM calls (1-3% above spot)
            otm_pct = (strike - spot_price) / spot_price * 100
            if 0 < otm_pct <= 1:
                score += 30  # ATM/slightly OTM - best
            elif 1 < otm_pct <= 2:
                score += 25  # Good OTM
            elif 2 < otm_pct <= 3:
                score += 15  # Moderate OTM
            elif otm_pct <= 0:
                score += 10  # ITM

            # High OI change (fresh positions) is bullish for calls
            if oi_change > 0:
                score += min(20, oi_change / 10000)  # Cap at 20 points

            # High volume indicates interest
            if volume > 0:
                score += min(15, volume / 50000)

            # Positive price change is bullish
            if change > 0:
                score += min(15, pchange)

            # Reasonable premium (not too cheap, not too expensive)
            if 50 < ltp < 500:
                score += 10

            call_options.append({
                "strike": strike,
                "ltp": ltp,
                "oi": oi,
                "oi_change": oi_change,
                "volume": volume,
                "change": change,
                "pchange": pchange,
                "score": score,
                "otm_pct": otm_pct,
                "lot_value": ltp * lot_size
            })

        # Sort by score
        call_options.sort(key=lambda x: x["score"], reverse=True)

        # Show top 3
        for i, opt in enumerate(call_options[:3], 1):
            parts.append(f"\n**{i}. Strike {opt['strike']} CE** (Score: {opt['score']:.0f})")
            parts.append(f"   - LTP: ₹{opt['ltp']:.2f} ({opt['pchange']:+.1f}%)")
            parts.append(f"   - OI: {opt['oi']:,} | OI Change: {opt['oi_change']:+,}")
            parts.append(f"   - Volume: {opt['volume']:,}")
            parts.append(f"   - Lot Value: ₹{opt['lot_value']:,.0f}")

            if opt['otm_pct'] > 0:
                parts.append(f"   - {opt['otm_pct']:.1f}% OTM")
            elif opt['otm_pct'] < 0:
                parts.append(f"   - {abs(opt['otm_pct']):.1f}% ITM")
            else:
                parts.append(f"   - ATM")

        return "\n".join(parts)

    def _analyze_puts(
        self,
        data: List[Dict],
        spot_price: float,
        atm_strike: float,
        lot_size: int
    ) -> str:
        """Analyze put options and find best opportunities"""
        parts = ["\n### 📉 TOP PUT OPTIONS (PE):"]

        put_options = []
        for row in data:
            pe = row.get("PE")
            if not pe:
                continue

            strike = row["strikePrice"]
            ltp = pe.get("lastPrice", 0)
            oi = pe.get("openInterest", 0)
            oi_change = pe.get("changeinOpenInterest", 0)
            volume = pe.get("totalTradedVolume", 0)
            change = pe.get("change", 0)
            pchange = pe.get("pChange", 0)

            # Calculate score
            score = 0

            # Prefer slightly OTM puts (1-3% below spot)
            otm_pct = (spot_price - strike) / spot_price * 100
            if 0 < otm_pct <= 1:
                score += 30
            elif 1 < otm_pct <= 2:
                score += 25
            elif 2 < otm_pct <= 3:
                score += 15
            elif otm_pct <= 0:
                score += 10

            # High OI change
            if oi_change > 0:
                score += min(20, oi_change / 10000)

            # High volume
            if volume > 0:
                score += min(15, volume / 50000)

            # Positive price change (puts gaining)
            if change > 0:
                score += min(15, pchange)

            # Reasonable premium
            if 50 < ltp < 500:
                score += 10

            put_options.append({
                "strike": strike,
                "ltp": ltp,
                "oi": oi,
                "oi_change": oi_change,
                "volume": volume,
                "change": change,
                "pchange": pchange,
                "score": score,
                "otm_pct": otm_pct,
                "lot_value": ltp * lot_size
            })

        # Sort by score
        put_options.sort(key=lambda x: x["score"], reverse=True)

        # Show top 3
        for i, opt in enumerate(put_options[:3], 1):
            parts.append(f"\n**{i}. Strike {opt['strike']} PE** (Score: {opt['score']:.0f})")
            parts.append(f"   - LTP: ₹{opt['ltp']:.2f} ({opt['pchange']:+.1f}%)")
            parts.append(f"   - OI: {opt['oi']:,} | OI Change: {opt['oi_change']:+,}")
            parts.append(f"   - Volume: {opt['volume']:,}")
            parts.append(f"   - Lot Value: ₹{opt['lot_value']:,.0f}")

            if opt['otm_pct'] > 0:
                parts.append(f"   - {opt['otm_pct']:.1f}% OTM")
            elif opt['otm_pct'] < 0:
                parts.append(f"   - {abs(opt['otm_pct']):.1f}% ITM")
            else:
                parts.append(f"   - ATM")

        return "\n".join(parts)

    async def chat(
        self,
        messages: List[Dict[str, str]],
        market_context: Optional[Dict[str, Any]] = None,
    ) -> str:
        """
        Send a chat request to the AI provider and get a response.
        Uses RAG to retrieve relevant knowledge for the query.

        Args:
            messages: List of conversation messages [{"role": "user/assistant", "content": "..."}]
            market_context: Optional market data to inject into context

        Returns:
            AI response string
        """
        if self._provider == "openai":
            return await self._chat_openai(messages, market_context)
        elif self._provider == "anthropic":
            return await self._chat_anthropic(messages, market_context)
        else:
            raise ValueError(f"Unsupported provider: {self._provider}")

    async def _chat_openai(
        self,
        messages: List[Dict[str, str]],
        market_context: Optional[Dict[str, Any]] = None,
    ) -> str:
        """Send chat request to OpenAI API with RAG context and live market data"""
        if not self._openai_api_key:
            raise ValueError("OpenAI API key not configured")

        # Get the latest user message for RAG query
        user_query = ""
        for msg in reversed(messages):
            if msg.get("role") == "user":
                user_query = msg.get("content", "")
                break

        # Build system message with RAG context
        system_content = self.SYSTEM_PROMPT

        # Add RAG-retrieved knowledge
        rag_context = self._get_rag_context(user_query)
        if rag_context:
            system_content += rag_context

        # Add market context
        if market_context:
            system_content += self._build_market_context_message(market_context)

        # Check if user is asking for option recommendations and fetch live data
        if self._use_live_data:
            is_recommendation, detected_index, option_type = self._is_option_recommendation_query(user_query)
            if is_recommendation:
                live_analysis = await self._fetch_and_analyze_options(detected_index, option_type)
                system_content += live_analysis
                system_content += f"\n\n**CRITICAL INSTRUCTIONS**:"
                system_content += f"\n1. Use ONLY the LIVE OPTION CHAIN ANALYSIS above for your recommendations"
                system_content += f"\n2. Use ONLY the expiry date shown as 'CURRENT EXPIRY DATE' - DO NOT make up dates or use old dates like 2023/2024"
                system_content += f"\n3. Today is {datetime.now().strftime('%d %B %Y')} - current year is {datetime.now().year}"
                system_content += f"\n4. Mention actual strike prices, premiums (LTP), and OI data from the analysis"
                system_content += f"\n5. For any strategy (Iron Condor, Straddle, etc.), use the exact expiry shown in the data"

        # Prepare messages for API
        api_messages = [{"role": "system", "content": system_content}]
        api_messages.extend(messages)

        async with httpx.AsyncClient(timeout=60.0) as client:
            response = await client.post(
                "https://api.openai.com/v1/chat/completions",
                headers={
                    "Authorization": f"Bearer {self._openai_api_key}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": self._model,
                    "messages": api_messages,
                    "temperature": 0.7,
                    "max_tokens": 1500,
                },
            )

            if response.status_code != 200:
                error_data = response.json()
                raise Exception(f"OpenAI API error: {error_data.get('error', {}).get('message', 'Unknown error')}")

            data = response.json()
            return data["choices"][0]["message"]["content"]

    async def _chat_anthropic(
        self,
        messages: List[Dict[str, str]],
        market_context: Optional[Dict[str, Any]] = None,
    ) -> str:
        """Send chat request to Anthropic API with RAG context and live market data"""
        if not self._anthropic_api_key:
            raise ValueError("Anthropic API key not configured")

        # Get the latest user message for RAG query
        user_query = ""
        for msg in reversed(messages):
            if msg.get("role") == "user":
                user_query = msg.get("content", "")
                break

        # Build system message with RAG context
        system_content = self.SYSTEM_PROMPT

        # Add RAG-retrieved knowledge
        rag_context = self._get_rag_context(user_query)
        if rag_context:
            system_content += rag_context

        # Add market context
        if market_context:
            system_content += self._build_market_context_message(market_context)

        # Check if user is asking for option recommendations and fetch live data
        if self._use_live_data:
            is_recommendation, detected_index, option_type = self._is_option_recommendation_query(user_query)
            if is_recommendation:
                live_analysis = await self._fetch_and_analyze_options(detected_index, option_type)
                system_content += live_analysis
                system_content += f"\n\n**CRITICAL INSTRUCTIONS**:"
                system_content += f"\n1. Use ONLY the LIVE OPTION CHAIN ANALYSIS above for your recommendations"
                system_content += f"\n2. Use ONLY the expiry date shown as 'CURRENT EXPIRY DATE' - DO NOT make up dates or use old dates like 2023/2024"
                system_content += f"\n3. Today is {datetime.now().strftime('%d %B %Y')} - current year is {datetime.now().year}"
                system_content += f"\n4. Mention actual strike prices, premiums (LTP), and OI data from the analysis"
                system_content += f"\n5. For any strategy (Iron Condor, Straddle, etc.), use the exact expiry shown in the data"

        async with httpx.AsyncClient(timeout=60.0) as client:
            response = await client.post(
                "https://api.anthropic.com/v1/messages",
                headers={
                    "x-api-key": self._anthropic_api_key,
                    "anthropic-version": "2023-06-01",
                    "Content-Type": "application/json",
                },
                json={
                    "model": self._model if "claude" in self._model else "claude-3-haiku-20240307",
                    "system": system_content,
                    "messages": messages,
                    "max_tokens": 1500,
                },
            )

            if response.status_code != 200:
                error_data = response.json()
                raise Exception(f"Anthropic API error: {error_data.get('error', {}).get('message', 'Unknown error')}")

            data = response.json()
            return data["content"][0]["text"]

    async def stream_chat(
        self,
        messages: List[Dict[str, str]],
        market_context: Optional[Dict[str, Any]] = None,
    ) -> AsyncGenerator[str, None]:
        """
        Stream a chat response from the AI provider with RAG context.

        Args:
            messages: List of conversation messages
            market_context: Optional market data to inject

        Yields:
            Chunks of the AI response
        """
        if self._provider == "openai":
            async for chunk in self._stream_openai(messages, market_context):
                yield chunk
        elif self._provider == "anthropic":
            async for chunk in self._stream_anthropic(messages, market_context):
                yield chunk
        else:
            raise ValueError(f"Unsupported provider: {self._provider}")

    async def _stream_openai(
        self,
        messages: List[Dict[str, str]],
        market_context: Optional[Dict[str, Any]] = None,
    ) -> AsyncGenerator[str, None]:
        """Stream response from OpenAI API with RAG context and live market data"""
        if not self._openai_api_key:
            raise ValueError("OpenAI API key not configured")

        # Get the latest user message for RAG query
        user_query = ""
        for msg in reversed(messages):
            if msg.get("role") == "user":
                user_query = msg.get("content", "")
                break

        # Build system content with RAG
        system_content = self.SYSTEM_PROMPT
        rag_context = self._get_rag_context(user_query)
        if rag_context:
            system_content += rag_context
        if market_context:
            system_content += self._build_market_context_message(market_context)

        # Check if user is asking for option recommendations and fetch live data
        if self._use_live_data:
            is_recommendation, detected_index, option_type = self._is_option_recommendation_query(user_query)
            if is_recommendation:
                live_analysis = await self._fetch_and_analyze_options(detected_index, option_type)
                system_content += live_analysis
                system_content += f"\n\n**CRITICAL**: Use ONLY the expiry date shown in the analysis. Today is {datetime.now().strftime('%d %B %Y')} (year {datetime.now().year}). DO NOT use old dates."

        api_messages = [{"role": "system", "content": system_content}]
        api_messages.extend(messages)

        async with httpx.AsyncClient(timeout=120.0) as client:
            async with client.stream(
                "POST",
                "https://api.openai.com/v1/chat/completions",
                headers={
                    "Authorization": f"Bearer {self._openai_api_key}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": self._model,
                    "messages": api_messages,
                    "temperature": 0.7,
                    "max_tokens": 1500,
                    "stream": True,
                },
            ) as response:
                if response.status_code != 200:
                    error_text = await response.aread()
                    raise Exception(f"OpenAI API error: {error_text}")

                async for line in response.aiter_lines():
                    if line.startswith("data: "):
                        data = line[6:]
                        if data == "[DONE]":
                            break
                        try:
                            chunk = json.loads(data)
                            content = chunk["choices"][0]["delta"].get("content", "")
                            if content:
                                yield content
                        except json.JSONDecodeError:
                            continue

    async def _stream_anthropic(
        self,
        messages: List[Dict[str, str]],
        market_context: Optional[Dict[str, Any]] = None,
    ) -> AsyncGenerator[str, None]:
        """Stream response from Anthropic API with RAG context and live market data"""
        if not self._anthropic_api_key:
            raise ValueError("Anthropic API key not configured")

        # Get the latest user message for RAG query
        user_query = ""
        for msg in reversed(messages):
            if msg.get("role") == "user":
                user_query = msg.get("content", "")
                break

        # Build system content with RAG
        system_content = self.SYSTEM_PROMPT
        rag_context = self._get_rag_context(user_query)
        if rag_context:
            system_content += rag_context
        if market_context:
            system_content += self._build_market_context_message(market_context)

        # Check if user is asking for option recommendations and fetch live data
        if self._use_live_data:
            is_recommendation, detected_index, option_type = self._is_option_recommendation_query(user_query)
            if is_recommendation:
                live_analysis = await self._fetch_and_analyze_options(detected_index, option_type)
                system_content += live_analysis
                system_content += f"\n\n**CRITICAL**: Use ONLY the expiry date shown in the analysis. Today is {datetime.now().strftime('%d %B %Y')} (year {datetime.now().year}). DO NOT use old dates."

        async with httpx.AsyncClient(timeout=120.0) as client:
            async with client.stream(
                "POST",
                "https://api.anthropic.com/v1/messages",
                headers={
                    "x-api-key": self._anthropic_api_key,
                    "anthropic-version": "2023-06-01",
                    "Content-Type": "application/json",
                },
                json={
                    "model": self._model if "claude" in self._model else "claude-3-haiku-20240307",
                    "system": system_content,
                    "messages": messages,
                    "max_tokens": 1500,
                    "stream": True,
                },
            ) as response:
                if response.status_code != 200:
                    error_text = await response.aread()
                    raise Exception(f"Anthropic API error: {error_text}")

                async for line in response.aiter_lines():
                    if line.startswith("data: "):
                        data = line[6:]
                        try:
                            chunk = json.loads(data)
                            if chunk["type"] == "content_block_delta":
                                yield chunk["delta"]["text"]
                        except json.JSONDecodeError:
                            continue

    def generate_suggestions(self, user_message: str, ai_response: str) -> List[str]:
        """Generate follow-up question suggestions based on conversation"""
        suggestions = []

        user_lower = user_message.lower()
        response_lower = ai_response.lower()

        # Index-specific suggestions
        if "nifty" in user_lower and "bank" not in user_lower:
            suggestions.append("What about BANK NIFTY options?")
            suggestions.append("What's the max pain for NIFTY?")
        elif "banknifty" in user_lower or "bank nifty" in user_lower:
            suggestions.append("Compare with NIFTY options")
            suggestions.append("Why is BANKNIFTY more volatile?")

        # Option type specific
        if "call" in user_lower or "ce" in response_lower:
            suggestions.append("What are the risks with calls?")
            suggestions.append("Show me put options for hedging")

        if "put" in user_lower or "pe" in response_lower:
            suggestions.append("What's the breakeven for puts?")
            suggestions.append("Should I use protective puts?")

        # Strategy related
        if any(s in user_lower for s in ["straddle", "strangle", "iron condor", "spread", "butterfly"]):
            suggestions.append("Explain max profit and loss")
            suggestions.append("What's the margin requirement?")

        # Greeks related
        if any(g in user_lower for g in ["delta", "gamma", "theta", "vega", "greeks"]):
            suggestions.append("How do Greeks change near expiry?")
            suggestions.append("Explain gamma risk on expiry")

        # Market analysis
        if "pcr" in user_lower or "put call ratio" in user_lower:
            suggestions.append("How to use PCR as contrarian indicator?")

        if "iv" in user_lower or "volatility" in user_lower or "vix" in user_lower:
            suggestions.append("Best strategies in high IV?")
            suggestions.append("How does IV crush work?")

        # Risk management
        if any(r in user_lower for r in ["stop loss", "risk", "position size"]):
            suggestions.append("What stop loss should I use?")
            suggestions.append("How much capital per trade?")

        # Expiry related
        if "expiry" in user_lower:
            suggestions.append("Should I trade on expiry day?")
            suggestions.append("Weekly vs monthly options?")

        # Beginner questions
        if any(b in user_lower for b in ["beginner", "start", "new", "learn", "basic"]):
            suggestions.append("What capital do I need?")
            suggestions.append("Best strategy for beginners?")

        # Remove duplicates
        seen = set()
        unique_suggestions = []
        for s in suggestions:
            if s not in seen:
                seen.add(s)
                unique_suggestions.append(s)

        # Default suggestions if none specific
        if not unique_suggestions:
            unique_suggestions = [
                "Suggest a low-risk strategy",
                "Explain Put-Call Ratio",
                "What strikes to trade today?",
            ]

        return unique_suggestions[:3]


# Singleton instance
chatbot_service = AIChatbotService()
