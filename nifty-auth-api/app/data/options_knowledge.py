"""
Options Trading Knowledge Base
Comprehensive data for Indian markets (NSE/BSE) options trading
Used by AI chatbot to provide accurate, contextual responses
"""

# ==================== INDEX INFORMATION ====================

INDEX_INFO = {
    "NIFTY50": {
        "full_name": "NIFTY 50",
        "exchange": "NSE",
        "lot_size": 25,
        "tick_size": 0.05,
        "expiry": "Every Thursday (weekly), Last Thursday (monthly)",
        "trading_hours": "9:15 AM - 3:30 PM IST",
        "components": 50,
        "description": "Benchmark index of NSE representing top 50 companies by market cap",
        "typical_range": "50-150 points daily",
        "average_iv": "12-18%",
        "margin_requirement": "Approx ₹1-1.5 lakh per lot for selling",
    },
    "BANKNIFTY": {
        "full_name": "BANK NIFTY",
        "exchange": "NSE",
        "lot_size": 15,
        "tick_size": 0.05,
        "expiry": "Every Wednesday (weekly), Last Thursday (monthly)",
        "trading_hours": "9:15 AM - 3:30 PM IST",
        "components": 12,
        "description": "Banking sector index with 12 most liquid banking stocks",
        "typical_range": "200-400 points daily",
        "average_iv": "15-25%",
        "margin_requirement": "Approx ₹1.2-1.8 lakh per lot for selling",
        "note": "More volatile than NIFTY, preferred by aggressive traders"
    },
    "FINNIFTY": {
        "full_name": "NIFTY Financial Services",
        "exchange": "NSE",
        "lot_size": 25,
        "tick_size": 0.05,
        "expiry": "Every Tuesday (weekly)",
        "trading_hours": "9:15 AM - 3:30 PM IST",
        "components": 20,
        "description": "Financial services sector including banks, NBFCs, insurance",
        "typical_range": "80-150 points daily",
        "average_iv": "13-20%",
    },
    "MIDCPNIFTY": {
        "full_name": "NIFTY Midcap Select",
        "exchange": "NSE",
        "lot_size": 50,
        "tick_size": 0.05,
        "expiry": "Every Monday (weekly)",
        "trading_hours": "9:15 AM - 3:30 PM IST",
        "components": 25,
        "description": "Mid-cap companies index for broader market exposure",
        "typical_range": "100-200 points daily",
        "average_iv": "15-22%",
    },
    "SENSEX": {
        "full_name": "S&P BSE SENSEX",
        "exchange": "BSE",
        "lot_size": 10,
        "tick_size": 0.05,
        "expiry": "Every Friday (weekly)",
        "trading_hours": "9:15 AM - 3:30 PM IST",
        "components": 30,
        "description": "Oldest Indian index with 30 large-cap companies",
        "typical_range": "150-350 points daily",
        "average_iv": "12-18%",
    }
}

# ==================== OPTION GREEKS ====================

GREEKS_EXPLAINED = {
    "delta": {
        "definition": "Rate of change of option price with respect to underlying price",
        "range": "-1 to +1",
        "call_behavior": "Positive (0 to 1). ATM calls have delta ~0.5",
        "put_behavior": "Negative (-1 to 0). ATM puts have delta ~-0.5",
        "trading_tips": [
            "Delta also represents probability of expiring ITM",
            "Use delta-neutral strategies to reduce directional risk",
            "High delta options move more with underlying but cost more",
            "For hedging, delta tells you how many shares one option hedges"
        ],
        "example": "If NIFTY CE has delta 0.6, for every 100-point move in NIFTY, option moves ₹60"
    },
    "gamma": {
        "definition": "Rate of change of delta with respect to underlying price",
        "characteristics": "Highest for ATM options, decreases for ITM/OTM",
        "trading_tips": [
            "High gamma means delta changes rapidly - good for scalping",
            "Gamma risk increases near expiry for ATM options",
            "Option sellers fear gamma - positions can turn against quickly",
            "Gamma is always positive for long options"
        ],
        "example": "If gamma is 0.05, a 100-point move changes delta by 0.05"
    },
    "theta": {
        "definition": "Rate of time decay - how much option loses value per day",
        "characteristics": "Always negative for long options, positive for short",
        "trading_tips": [
            "Theta decay accelerates in last week before expiry",
            "ATM options have highest theta",
            "Sell options to benefit from theta, buy to suffer from it",
            "Weekend theta: Friday closing to Monday opening"
        ],
        "example": "Theta of -5 means option loses ₹5 per day"
    },
    "vega": {
        "definition": "Sensitivity of option price to implied volatility changes",
        "characteristics": "Highest for ATM options with longer expiry",
        "trading_tips": [
            "Buy options before expected volatility events (results, elections)",
            "Sell options when IV is high (after events)",
            "IV crush after events can destroy option value even if direction is right",
            "Vega is always positive for long options"
        ],
        "example": "Vega of 10 means 1% IV increase adds ₹10 to option price"
    },
    "rho": {
        "definition": "Sensitivity to interest rate changes",
        "characteristics": "Less important for short-term options in India",
        "note": "Generally ignored for weekly options trading"
    }
}

# ==================== OPTIONS STRATEGIES ====================

OPTIONS_STRATEGIES = {
    # Bullish Strategies
    "long_call": {
        "outlook": "Bullish",
        "structure": "Buy 1 Call option",
        "max_profit": "Unlimited",
        "max_loss": "Premium paid",
        "breakeven": "Strike + Premium",
        "when_to_use": "Expecting significant upward move",
        "greeks_profile": "Long delta, long gamma, long vega, short theta",
        "example": "Buy NIFTY 25000 CE @ ₹150. Breakeven at 25150."
    },
    "bull_call_spread": {
        "outlook": "Moderately Bullish",
        "structure": "Buy 1 ITM/ATM Call, Sell 1 OTM Call",
        "max_profit": "Difference between strikes - Net premium",
        "max_loss": "Net premium paid",
        "breakeven": "Lower strike + Net premium",
        "when_to_use": "Bullish but want to reduce cost",
        "example": "Buy NIFTY 25000 CE @ ₹200, Sell NIFTY 25200 CE @ ₹100. Max profit ₹100, Max loss ₹100."
    },
    "bull_put_spread": {
        "outlook": "Moderately Bullish to Neutral",
        "structure": "Sell 1 ATM/OTM Put, Buy 1 further OTM Put",
        "max_profit": "Net premium received",
        "max_loss": "Difference between strikes - Net premium",
        "breakeven": "Higher strike - Net premium",
        "when_to_use": "Expecting price to stay above support",
        "example": "Sell NIFTY 24800 PE @ ₹150, Buy NIFTY 24600 PE @ ₹80. Max profit ₹70."
    },

    # Bearish Strategies
    "long_put": {
        "outlook": "Bearish",
        "structure": "Buy 1 Put option",
        "max_profit": "Strike - Premium (if underlying goes to 0)",
        "max_loss": "Premium paid",
        "breakeven": "Strike - Premium",
        "when_to_use": "Expecting significant downward move",
        "greeks_profile": "Short delta, long gamma, long vega, short theta"
    },
    "bear_put_spread": {
        "outlook": "Moderately Bearish",
        "structure": "Buy 1 ITM/ATM Put, Sell 1 OTM Put",
        "max_profit": "Difference between strikes - Net premium",
        "max_loss": "Net premium paid",
        "when_to_use": "Bearish but want to reduce cost"
    },
    "bear_call_spread": {
        "outlook": "Moderately Bearish to Neutral",
        "structure": "Sell 1 ATM/OTM Call, Buy 1 further OTM Call",
        "max_profit": "Net premium received",
        "max_loss": "Difference between strikes - Net premium",
        "when_to_use": "Expecting price to stay below resistance"
    },

    # Neutral Strategies
    "long_straddle": {
        "outlook": "Neutral expecting big move",
        "structure": "Buy 1 ATM Call + Buy 1 ATM Put (same strike)",
        "max_profit": "Unlimited",
        "max_loss": "Total premium paid",
        "breakeven": "Strike ± Total premium",
        "when_to_use": "Before major events (results, RBI policy, elections)",
        "tip": "IV should be low when entering; benefits from IV expansion"
    },
    "short_straddle": {
        "outlook": "Neutral expecting range-bound",
        "structure": "Sell 1 ATM Call + Sell 1 ATM Put (same strike)",
        "max_profit": "Total premium received",
        "max_loss": "Unlimited",
        "breakeven": "Strike ± Total premium",
        "when_to_use": "After events when IV is high, expecting consolidation",
        "risk": "Very risky - unlimited loss potential"
    },
    "long_strangle": {
        "outlook": "Neutral expecting big move",
        "structure": "Buy 1 OTM Call + Buy 1 OTM Put",
        "max_profit": "Unlimited",
        "max_loss": "Total premium paid",
        "when_to_use": "Cheaper alternative to straddle, needs bigger move"
    },
    "short_strangle": {
        "outlook": "Neutral expecting range-bound",
        "structure": "Sell 1 OTM Call + Sell 1 OTM Put",
        "max_profit": "Total premium received",
        "max_loss": "Unlimited",
        "when_to_use": "Range-bound markets, high IV environment"
    },
    "iron_condor": {
        "outlook": "Neutral, range-bound",
        "structure": "Bull Put Spread + Bear Call Spread",
        "max_profit": "Net premium received",
        "max_loss": "Width of wider spread - Net premium",
        "when_to_use": "Expecting price to stay within range",
        "tip": "Popular strategy for consistent income in sideways markets"
    },
    "iron_butterfly": {
        "outlook": "Neutral, expecting minimal move",
        "structure": "Short Straddle + Long Strangle for protection",
        "max_profit": "Net premium received",
        "max_loss": "Width of spread - Net premium",
        "when_to_use": "Expecting price to stay at current level"
    }
}

# ==================== MARKET INDICATORS ====================

MARKET_INDICATORS = {
    "pcr": {
        "name": "Put-Call Ratio",
        "description": "Ratio of Put OI to Call OI, indicates market sentiment",
        "interpretation": {
            "below_0.7": "Extremely bullish (contrarian bearish signal)",
            "0.7_to_0.9": "Bullish sentiment",
            "0.9_to_1.1": "Neutral",
            "1.1_to_1.3": "Bearish sentiment",
            "above_1.3": "Extremely bearish (contrarian bullish signal)"
        },
        "usage_tips": [
            "Use as contrarian indicator at extremes",
            "Track changes in PCR, not just absolute value",
            "Combine with price action for confirmation"
        ]
    },
    "max_pain": {
        "name": "Max Pain Strike",
        "description": "Strike price where option buyers lose maximum money",
        "theory": "Price tends to gravitate towards max pain on expiry",
        "usage_tips": [
            "More relevant on expiry day",
            "Works better in range-bound markets",
            "Large OI at strikes act as support/resistance"
        ]
    },
    "iv_percentile": {
        "name": "IV Percentile",
        "description": "Current IV compared to historical IV range",
        "interpretation": {
            "below_20": "Low IV - good time to buy options",
            "20_to_50": "Normal IV",
            "50_to_80": "Elevated IV - consider selling options",
            "above_80": "Very high IV - strong sell signal for options"
        }
    },
    "india_vix": {
        "name": "India VIX",
        "description": "Volatility index derived from NIFTY options",
        "interpretation": {
            "below_12": "Very low fear - complacent market",
            "12_to_15": "Low volatility - bullish environment",
            "15_to_20": "Normal volatility",
            "20_to_25": "Elevated fear - uncertainty",
            "above_25": "High fear - potential market bottom"
        },
        "trading_tips": [
            "VIX tends to spike during market falls",
            "Mean reversion - extreme VIX levels don't sustain",
            "Use for timing option strategies"
        ]
    },
    "oi_analysis": {
        "name": "Open Interest Analysis",
        "patterns": {
            "call_oi_buildup": "Writers expect price to stay below that strike (resistance)",
            "put_oi_buildup": "Writers expect price to stay above that strike (support)",
            "long_buildup": "Price up + OI up = Bullish",
            "short_buildup": "Price down + OI up = Bearish",
            "long_unwinding": "Price down + OI down = Weak bearish",
            "short_covering": "Price up + OI down = Weak bullish"
        }
    }
}

# ==================== TECHNICAL ANALYSIS ====================

TECHNICAL_INDICATORS = {
    "rsi": {
        "name": "Relative Strength Index",
        "period": "14 (standard)",
        "interpretation": {
            "below_30": "Oversold - potential bounce",
            "30_to_50": "Bearish momentum",
            "50": "Neutral",
            "50_to_70": "Bullish momentum",
            "above_70": "Overbought - potential pullback"
        },
        "divergence": "RSI divergence from price often signals reversal"
    },
    "macd": {
        "name": "Moving Average Convergence Divergence",
        "settings": "12, 26, 9 (standard)",
        "signals": {
            "bullish_crossover": "MACD crosses above signal line",
            "bearish_crossover": "MACD crosses below signal line",
            "zero_line_cross": "Strong trend confirmation"
        }
    },
    "moving_averages": {
        "types": ["SMA (Simple)", "EMA (Exponential)"],
        "key_levels": {
            "20_ma": "Short-term trend",
            "50_ma": "Medium-term trend",
            "200_ma": "Long-term trend"
        },
        "signals": {
            "golden_cross": "50 MA crosses above 200 MA - bullish",
            "death_cross": "50 MA crosses below 200 MA - bearish"
        }
    },
    "support_resistance": {
        "identification": [
            "Previous swing highs and lows",
            "Round numbers (NIFTY 25000, 25500, etc.)",
            "High OI strikes",
            "Moving averages",
            "Fibonacci retracement levels"
        ],
        "trading_tips": [
            "Support becomes resistance after breakdown",
            "Multiple timeframe confluence is stronger",
            "Volume confirmation at levels adds validity"
        ]
    }
}

# ==================== RISK MANAGEMENT ====================

RISK_MANAGEMENT = {
    "position_sizing": {
        "rule_1": "Never risk more than 2% of capital on single trade",
        "rule_2": "Maximum 5-6 positions at a time",
        "rule_3": "Diversify across indices and expiries",
        "calculation": "Position size = (Risk amount) / (Stop loss distance)"
    },
    "stop_loss_guidelines": {
        "option_buying": {
            "percentage_based": "30-50% of premium paid",
            "time_based": "Exit if trade doesn't work within expected timeframe",
            "underlying_based": "Based on underlying support/resistance"
        },
        "option_selling": {
            "percentage_based": "2x to 3x of premium received",
            "underlying_based": "Exit if underlying breaches key level",
            "adjustment": "Consider rolling instead of full stop loss"
        }
    },
    "common_mistakes": [
        "Averaging losing positions",
        "Over-leveraging with far OTM options",
        "Ignoring theta decay in long options",
        "Selling options without hedges",
        "Trading without stop loss",
        "Revenge trading after losses"
    ],
    "golden_rules": [
        "Plan your trade and trade your plan",
        "Never risk money you can't afford to lose",
        "Cut losses quickly, let winners run",
        "Don't chase trades - wait for setups",
        "Maintain trading journal",
        "Take breaks after consecutive losses"
    ]
}

# ==================== TRADING TIMES ====================

TRADING_TIMES = {
    "pre_market": {
        "time": "9:00 AM - 9:15 AM IST",
        "activity": "Pre-open session, order matching",
        "tip": "Gaps often get filled in first 30 minutes"
    },
    "opening_hour": {
        "time": "9:15 AM - 10:15 AM IST",
        "characteristics": "High volatility, price discovery",
        "tip": "Wait for initial volatility to settle before trading"
    },
    "mid_session": {
        "time": "10:15 AM - 2:30 PM IST",
        "characteristics": "More stable, trend formation",
        "tip": "Best time for positional trades"
    },
    "closing_hour": {
        "time": "2:30 PM - 3:30 PM IST",
        "characteristics": "Position squaring, volatility spike",
        "tip": "Be cautious with new positions"
    },
    "expiry_day": {
        "weekly_expiry": "NIFTY Thu, BANKNIFTY Wed, FINNIFTY Tue, MIDCPNIFTY Mon, SENSEX Fri",
        "characteristics": "High theta decay, gamma spike for ATM options",
        "tip": "Avoid selling naked options on expiry day"
    }
}

# ==================== COMMON QUESTIONS & ANSWERS ====================

FAQ = {
    "what_is_atm_itm_otm": {
        "question": "What is ATM, ITM, and OTM?",
        "answer": """
ATM (At The Money): Strike price closest to current spot price
ITM (In The Money):
  - Call: Strike < Spot (has intrinsic value)
  - Put: Strike > Spot (has intrinsic value)
OTM (Out of The Money):
  - Call: Strike > Spot (only time value)
  - Put: Strike < Spot (only time value)
"""
    },
    "why_option_losing_value": {
        "question": "Why is my option losing value even when underlying moved in my direction?",
        "answer": """
Possible reasons:
1. Time decay (theta) - especially near expiry
2. IV crush - volatility dropped after event
3. You bought far OTM option - low delta
4. The move wasn't big enough to overcome theta + IV drop
Solution: Buy ATM/slightly ITM options for directional trades
"""
    },
    "best_time_to_trade_options": {
        "question": "What's the best time to trade options?",
        "answer": """
For buying options:
- When IV is low (IV percentile < 30)
- Before expected volatility events
- When you have clear directional view

For selling options:
- When IV is high (IV percentile > 70)
- After events (IV crush)
- In sideways/range-bound markets

Avoid: First 15-30 minutes (high spreads), last 15 minutes (erratic moves)
"""
    },
    "how_much_capital_needed": {
        "question": "How much capital do I need to start options trading?",
        "answer": """
Option Buying: ₹20,000 - ₹50,000 is a good start
  - Allows 2-3 lots of NIFTY options

Option Selling: ₹2,00,000 - ₹5,00,000 minimum
  - Margin requirements are high
  - Need buffer for MTM losses

Paper trading first is highly recommended for beginners
"""
    },
    "weekly_vs_monthly": {
        "question": "Should I trade weekly or monthly options?",
        "answer": """
Weekly Options:
  - Pros: Lower premium, higher gamma (quick profits possible)
  - Cons: Faster theta decay, higher gamma risk
  - Best for: Intraday/short-term directional trades

Monthly Options:
  - Pros: Slower decay, more time for thesis to play out
  - Cons: Higher premium, lower gamma
  - Best for: Positional trades, spreads, selling strategies
"""
    }
}

# ==================== HELPER FUNCTIONS ====================

def get_index_info(symbol: str) -> dict:
    """Get detailed information about an index"""
    symbol = symbol.upper().replace(" ", "")
    if symbol in INDEX_INFO:
        return INDEX_INFO[symbol]
    # Try partial match
    for key, value in INDEX_INFO.items():
        if symbol in key or key in symbol:
            return value
    return None

def get_strategy_info(strategy_name: str) -> dict:
    """Get information about an options strategy"""
    strategy_key = strategy_name.lower().replace(" ", "_").replace("-", "_")
    if strategy_key in OPTIONS_STRATEGIES:
        return OPTIONS_STRATEGIES[strategy_key]
    # Try partial match
    for key, value in OPTIONS_STRATEGIES.items():
        if strategy_key in key or key in strategy_key:
            return value
    return None

def get_greek_info(greek_name: str) -> dict:
    """Get information about an option Greek"""
    greek_key = greek_name.lower()
    if greek_key in GREEKS_EXPLAINED:
        return GREEKS_EXPLAINED[greek_key]
    return None

def interpret_pcr(pcr_value: float) -> str:
    """Interpret Put-Call Ratio value"""
    if pcr_value < 0.7:
        return "Extremely bullish sentiment (contrarian bearish signal - market may be overbought)"
    elif pcr_value < 0.9:
        return "Bullish sentiment - market participants are optimistic"
    elif pcr_value < 1.1:
        return "Neutral sentiment - no clear directional bias"
    elif pcr_value < 1.3:
        return "Bearish sentiment - market participants are cautious"
    else:
        return "Extremely bearish sentiment (contrarian bullish signal - market may be oversold)"

def interpret_iv(iv_value: float, index: str = "NIFTY") -> str:
    """Interpret Implied Volatility value"""
    if index.upper() in ["BANKNIFTY", "BANK NIFTY"]:
        # BANKNIFTY typically has higher IV
        if iv_value < 15:
            return "Low IV - options are cheap, good for buying"
        elif iv_value < 22:
            return "Normal IV range for BANKNIFTY"
        elif iv_value < 30:
            return "Elevated IV - consider selling strategies"
        else:
            return "Very high IV - options are expensive, strong sell signal"
    else:
        # NIFTY and others
        if iv_value < 12:
            return "Low IV - options are cheap, good for buying"
        elif iv_value < 18:
            return "Normal IV range"
        elif iv_value < 25:
            return "Elevated IV - consider selling strategies"
        else:
            return "Very high IV - options are expensive, strong sell signal"

def interpret_rsi(rsi_value: float) -> str:
    """Interpret RSI value"""
    if rsi_value < 30:
        return "Oversold - potential bounce opportunity, but don't catch falling knife"
    elif rsi_value < 40:
        return "Weak/bearish momentum"
    elif rsi_value < 60:
        return "Neutral momentum"
    elif rsi_value < 70:
        return "Bullish momentum"
    else:
        return "Overbought - potential pullback, but strong trends can stay overbought"


# Build comprehensive knowledge string for AI context
def build_knowledge_context() -> str:
    """Build a comprehensive knowledge string for AI system prompt"""
    context = """
## Indian Options Market Knowledge Base

### Index Specifications:
"""
    for symbol, info in INDEX_INFO.items():
        context += f"""
**{info['full_name']} ({symbol})**
- Lot Size: {info['lot_size']}
- Expiry: {info['expiry']}
- Typical Daily Range: {info.get('typical_range', 'N/A')}
- Average IV: {info.get('average_iv', 'N/A')}
"""

    context += """
### Key Market Indicators:

**Put-Call Ratio (PCR) Interpretation:**
- Below 0.7: Extremely bullish (contrarian bearish)
- 0.7-0.9: Bullish
- 0.9-1.1: Neutral
- 1.1-1.3: Bearish
- Above 1.3: Extremely bearish (contrarian bullish)

**India VIX Interpretation:**
- Below 12: Very low fear, complacent
- 12-15: Low volatility, bullish
- 15-20: Normal
- 20-25: Elevated fear
- Above 25: High fear, potential bottom

### Popular Strategies:
1. **For Bullish View**: Long Call, Bull Call Spread, Bull Put Spread
2. **For Bearish View**: Long Put, Bear Put Spread, Bear Call Spread
3. **For Neutral/Range**: Iron Condor, Short Strangle, Iron Butterfly
4. **For Big Move**: Long Straddle, Long Strangle

### Risk Management Rules:
- Never risk more than 2% per trade
- Use stop losses (30-50% for buying, 2-3x premium for selling)
- Avoid naked option selling without hedges
- Be cautious on expiry days (gamma risk)

### Trading Hours:
- Pre-market: 9:00-9:15 AM
- Regular: 9:15 AM - 3:30 PM IST
- Weekly expiries: Mon (MIDCPNIFTY), Tue (FINNIFTY), Wed (BANKNIFTY), Thu (NIFTY), Fri (SENSEX)
"""
    return context
