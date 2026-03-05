"""
Knowledge Loader - Loads options trading knowledge into the RAG vector database
"""

from typing import List, Dict, Any
from app.services.rag_service import rag_service
from app.data.options_knowledge import (
    INDEX_INFO,
    GREEKS_EXPLAINED,
    OPTIONS_STRATEGIES,
    MARKET_INDICATORS,
    TECHNICAL_INDICATORS,
    RISK_MANAGEMENT,
    TRADING_TIMES,
    FAQ,
)


def format_index_info() -> List[Dict[str, Any]]:
    """Format index information for RAG"""
    documents = []

    for symbol, info in INDEX_INFO.items():
        content = f"""
{info['name']} ({symbol}) - Indian Stock Market Index

Overview:
{info['description']}

Trading Details:
- Lot Size: {info['lot_size']} units
- Tick Size: {info['tick_size']} points
- Exchange: {info['exchange']}
- Trading Hours: {info['trading_hours']}

Options Expiry Schedule:
- Weekly Expiry: {info['expiry_day']}
- Monthly Expiry: Last {info['expiry_day']} of the month

Typical Volatility Range:
- Normal IV Range: {info['typical_iv_range'][0]}% - {info['typical_iv_range'][1]}%
- Average Daily Range: {info['avg_daily_range']}

Key Characteristics:
{chr(10).join(f'- {char}' for char in info['characteristics'])}
"""
        documents.append({
            "content": content.strip(),
            "metadata": {
                "category": "Index Information",
                "topic": info['name'],
                "symbol": symbol,
                "type": "index"
            }
        })

    return documents


def format_greeks_info() -> List[Dict[str, Any]]:
    """Format Greeks information for RAG"""
    documents = []

    for greek, info in GREEKS_EXPLAINED.items():
        content = f"""
{info['name']} ({info['symbol']}) - Options Greek

Definition:
{info['definition']}

What it Measures:
{info['measures']}

Typical Range:
{info['range']}

How to Use {info['name']} in Trading:
{info['trading_use']}

Key Points for Traders:
{chr(10).join(f'- {point}' for point in info['key_points'])}

Example:
{info['example']}
"""
        documents.append({
            "content": content.strip(),
            "metadata": {
                "category": "Options Greeks",
                "topic": info['name'],
                "symbol": info['symbol'],
                "type": "greek"
            }
        })

    return documents


def format_strategies_info() -> List[Dict[str, Any]]:
    """Format options strategies for RAG"""
    documents = []

    for strategy_key, info in OPTIONS_STRATEGIES.items():
        legs_desc = []
        for leg in info.get('legs', []):
            legs_desc.append(f"  - {leg['action']} {leg['option_type']} at {leg['strike']}")

        content = f"""
{info['name']} - Options Trading Strategy

Market Outlook: {info['outlook']}
Risk Level: {info['risk_level']}
Complexity: {info['complexity']}

Strategy Description:
{info['description']}

How to Execute:
{info['execution']}

Strategy Legs:
{chr(10).join(legs_desc) if legs_desc else 'Single leg strategy'}

Profit & Loss Characteristics:
- Maximum Profit: {info['max_profit']}
- Maximum Loss: {info['max_loss']}
- Breakeven: {info['breakeven']}

Ideal Market Conditions:
{chr(10).join(f'- {condition}' for condition in info.get('ideal_conditions', []))}

Greeks Exposure:
- Delta: {info.get('greeks_exposure', {}).get('delta', 'Varies')}
- Theta: {info.get('greeks_exposure', {}).get('theta', 'Varies')}
- Vega: {info.get('greeks_exposure', {}).get('vega', 'Varies')}

Tips for Traders:
{chr(10).join(f'- {tip}' for tip in info.get('tips', []))}
"""
        documents.append({
            "content": content.strip(),
            "metadata": {
                "category": "Options Strategies",
                "topic": info['name'],
                "outlook": info['outlook'],
                "risk_level": info['risk_level'],
                "type": "strategy"
            }
        })

    return documents


def format_market_indicators() -> List[Dict[str, Any]]:
    """Format market indicators for RAG"""
    documents = []

    for indicator_key, info in MARKET_INDICATORS.items():
        interpretation = []
        for key, value in info.get('interpretation', {}).items():
            interpretation.append(f"- {key}: {value}")

        content = f"""
{info['name']} - Market Indicator

Definition:
{info['definition']}

Formula/Calculation:
{info.get('formula', 'N/A')}

How to Interpret:
{chr(10).join(interpretation)}

Trading Applications:
{chr(10).join(f'- {app}' for app in info.get('trading_applications', []))}

Limitations:
{chr(10).join(f'- {limit}' for limit in info.get('limitations', []))}

Typical Values:
{info.get('typical_range', 'Varies by market conditions')}
"""
        documents.append({
            "content": content.strip(),
            "metadata": {
                "category": "Market Indicators",
                "topic": info['name'],
                "type": "indicator"
            }
        })

    return documents


def format_technical_indicators() -> List[Dict[str, Any]]:
    """Format technical indicators for RAG"""
    documents = []

    for indicator_key, info in TECHNICAL_INDICATORS.items():
        content = f"""
{info['name']} - Technical Analysis Indicator

Definition:
{info['definition']}

Parameters:
{info.get('parameters', 'Standard settings')}

How to Use:
{info['usage']}

Signal Interpretation:
- Bullish Signal: {info.get('bullish_signal', 'N/A')}
- Bearish Signal: {info.get('bearish_signal', 'N/A')}
- Neutral Zone: {info.get('neutral_zone', 'N/A')}

Best Practices:
{chr(10).join(f'- {practice}' for practice in info.get('best_practices', []))}

Common Mistakes to Avoid:
{chr(10).join(f'- {mistake}' for mistake in info.get('mistakes_to_avoid', []))}
"""
        documents.append({
            "content": content.strip(),
            "metadata": {
                "category": "Technical Analysis",
                "topic": info['name'],
                "type": "technical_indicator"
            }
        })

    return documents


def format_risk_management() -> List[Dict[str, Any]]:
    """Format risk management guidelines for RAG"""
    documents = []

    # Position Sizing
    position_sizing = RISK_MANAGEMENT.get('position_sizing', {})
    content = f"""
Position Sizing in Options Trading - Risk Management

Golden Rules:
{chr(10).join(f'- {rule}' for rule in position_sizing.get('rules', []))}

Recommended Position Sizes:
- Per Trade: {position_sizing.get('per_trade', '1-2% of capital')}
- Per Strategy: {position_sizing.get('per_strategy', '5% of capital')}
- Per Index: {position_sizing.get('per_index', '10% of capital')}
- Maximum Portfolio Heat: {position_sizing.get('max_portfolio_heat', '20% of capital')}

Position Sizing Formula:
{position_sizing.get('formula', 'Position Size = (Account Size × Risk %) / Stop Loss Distance')}
"""
    documents.append({
        "content": content.strip(),
        "metadata": {
            "category": "Risk Management",
            "topic": "Position Sizing",
            "type": "risk"
        }
    })

    # Stop Loss
    stop_loss = RISK_MANAGEMENT.get('stop_loss', {})
    content = f"""
Stop Loss Strategies for Options Trading - Risk Management

Types of Stop Losses:
{chr(10).join(f'- {sl_type}: {desc}' for sl_type, desc in stop_loss.get('types', {}).items())}

Recommended Stop Loss Levels:
- Option Buying: {stop_loss.get('option_buying', '30-50% of premium')}
- Option Selling: {stop_loss.get('option_selling', '2-3x premium received')}
- Spreads: {stop_loss.get('spreads', 'Max loss of spread or 50% of max loss')}

Important Tips:
{chr(10).join(f'- {tip}' for tip in stop_loss.get('tips', []))}
"""
    documents.append({
        "content": content.strip(),
        "metadata": {
            "category": "Risk Management",
            "topic": "Stop Loss",
            "type": "risk"
        }
    })

    # Capital Allocation
    capital = RISK_MANAGEMENT.get('capital_allocation', {})
    content = f"""
Capital Allocation for Options Trading - Risk Management

Recommended Allocation:
- Option Buying: {capital.get('option_buying', '20-30% of trading capital')}
- Option Selling: {capital.get('option_selling', '40-50% of trading capital')}
- Hedging: {capital.get('hedging', '10-20% of trading capital')}
- Cash Reserve: {capital.get('cash_reserve', '20-30% always in cash')}

Portfolio Diversification:
{chr(10).join(f'- {point}' for point in capital.get('diversification', []))}

Margin Management:
{capital.get('margin_management', 'Keep 50% buffer over required margin')}
"""
    documents.append({
        "content": content.strip(),
        "metadata": {
            "category": "Risk Management",
            "topic": "Capital Allocation",
            "type": "risk"
        }
    })

    return documents


def format_trading_times() -> List[Dict[str, Any]]:
    """Format trading times and schedule for RAG"""
    documents = []

    content = f"""
Indian Stock Market Trading Hours and Schedule

Regular Trading Session:
- Pre-Open: {TRADING_TIMES.get('pre_open', '9:00 AM - 9:08 AM')}
- Market Open: {TRADING_TIMES.get('market_open', '9:15 AM')}
- Market Close: {TRADING_TIMES.get('market_close', '3:30 PM')}
- Post-Close: {TRADING_TIMES.get('post_close', '3:40 PM - 4:00 PM')}

Options Expiry Schedule:
- NIFTY Weekly: {TRADING_TIMES.get('nifty_weekly_expiry', 'Every Thursday')}
- BANKNIFTY Weekly: {TRADING_TIMES.get('banknifty_weekly_expiry', 'Every Wednesday')}
- FINNIFTY Weekly: {TRADING_TIMES.get('finnifty_weekly_expiry', 'Every Tuesday')}
- Monthly Expiry: {TRADING_TIMES.get('monthly_expiry', 'Last Thursday of month')}

Important Timing Notes:
{chr(10).join(f'- {note}' for note in TRADING_TIMES.get('important_notes', []))}

Best Times to Trade:
- High Activity: {TRADING_TIMES.get('high_activity', '9:15-10:30 AM and 2:00-3:30 PM')}
- Low Activity: {TRADING_TIMES.get('low_activity', '12:00-2:00 PM (lunch hours)')}
- Expiry Day: {TRADING_TIMES.get('expiry_day_note', 'High volatility, especially last 2 hours')}

Holiday Schedule:
NSE observes all major Indian national holidays. Check NSE website for holiday calendar.
"""
    documents.append({
        "content": content.strip(),
        "metadata": {
            "category": "Trading Schedule",
            "topic": "Market Hours",
            "type": "schedule"
        }
    })

    return documents


def format_faq() -> List[Dict[str, Any]]:
    """Format FAQ for RAG"""
    documents = []

    for faq in FAQ:
        content = f"""
Frequently Asked Question: {faq['question']}

Answer:
{faq['answer']}

Category: {faq.get('category', 'General')}
"""
        documents.append({
            "content": content.strip(),
            "metadata": {
                "category": "FAQ",
                "topic": faq['question'][:50],
                "type": "faq"
            }
        })

    return documents


def get_additional_knowledge() -> List[Dict[str, Any]]:
    """Additional curated knowledge about options trading"""
    return [
        {
            "content": """
Options Trading Basics for Beginners

What are Options?
Options are financial derivatives that give you the right, but not the obligation, to buy or sell an underlying asset at a predetermined price within a specified time period.

Two Types of Options:
1. Call Option (CE): Gives the right to BUY the underlying asset
   - Buy Call: Bullish view, profit when price goes up
   - Sell Call: Neutral to bearish, profit from premium decay

2. Put Option (PE): Gives the right to SELL the underlying asset
   - Buy Put: Bearish view, profit when price goes down
   - Sell Put: Neutral to bullish, profit from premium decay

Key Terms:
- Strike Price: The price at which you can buy/sell the underlying
- Premium: The price you pay to buy an option
- Expiry: The date when the option contract expires
- ITM (In The Money): Option has intrinsic value
- ATM (At The Money): Strike price equals current market price
- OTM (Out of The Money): Option has no intrinsic value

Why Trade Options?
1. Leverage: Control large positions with small capital
2. Limited Risk: For buyers, max loss is the premium paid
3. Flexibility: Profit in any market direction
4. Income Generation: Sell options to collect premium
5. Hedging: Protect existing positions
""",
            "metadata": {
                "category": "Options Basics",
                "topic": "Introduction to Options",
                "type": "educational"
            }
        },
        {
            "content": """
Option Pricing Components and Factors

Option Premium = Intrinsic Value + Time Value

1. Intrinsic Value:
   - Call Option: Max(0, Spot Price - Strike Price)
   - Put Option: Max(0, Strike Price - Spot Price)
   - Only ITM options have intrinsic value

2. Time Value (Extrinsic Value):
   - Decreases as expiry approaches (Theta decay)
   - Higher for ATM options
   - Affected by implied volatility

Factors Affecting Option Prices:

1. Underlying Price Movement
   - Call prices increase when underlying rises
   - Put prices increase when underlying falls

2. Time to Expiry
   - More time = Higher premium
   - Theta decay accelerates near expiry

3. Implied Volatility (IV)
   - Higher IV = Higher premium (both calls and puts)
   - IV crush after events can cause premium collapse

4. Interest Rates
   - Higher rates slightly increase call prices
   - Higher rates slightly decrease put prices

5. Dividends
   - Expected dividends reduce call prices
   - Expected dividends increase put prices

Black-Scholes Model:
The most common pricing model for European-style options, considering all factors above.
""",
            "metadata": {
                "category": "Options Basics",
                "topic": "Option Pricing",
                "type": "educational"
            }
        },
        {
            "content": """
Implied Volatility (IV) Deep Dive

What is Implied Volatility?
IV is the market's expectation of future price movement, derived from option prices. Higher IV means the market expects larger price swings.

IV vs Historical Volatility:
- Historical Volatility (HV): Measures past price movements
- Implied Volatility (IV): Reflects expected future movements
- When IV > HV: Options may be overpriced
- When IV < HV: Options may be underpriced

India VIX:
- India's volatility index, derived from NIFTY option prices
- Normal range: 10-20
- High VIX (>20): Market fear, good for option sellers after peak
- Low VIX (<12): Calm markets, cheap options for buyers

IV Percentile and IV Rank:
- IV Percentile: % of days IV was lower than current IV
- IV Rank: Current IV position relative to 52-week range
- High IV Rank (>50%): Consider selling strategies
- Low IV Rank (<30%): Consider buying strategies

Trading IV:
1. High IV Environment:
   - Sell options (Iron Condors, Credit Spreads)
   - Strangles and Straddles as sellers
   - Premium is rich

2. Low IV Environment:
   - Buy options (Long Calls/Puts)
   - Debit Spreads
   - Premium is cheap

IV Crush:
- Sharp drop in IV after known events (earnings, elections)
- Even correct directional bets can lose money
- Plan for IV crush when trading around events
""",
            "metadata": {
                "category": "Volatility",
                "topic": "Implied Volatility",
                "type": "educational"
            }
        },
        {
            "content": """
Open Interest (OI) Analysis for Options Trading

What is Open Interest?
Total number of outstanding option contracts that have not been settled. Each trade involves a buyer and seller, creating one OI unit.

OI vs Volume:
- Volume: Number of contracts traded today
- Open Interest: Total open positions (cumulative)
- High volume + OI increase = New positions being created
- High volume + OI decrease = Positions being closed

Interpreting OI Changes:

Price Up + OI Up = Bullish (New longs entering)
Price Up + OI Down = Short covering (Weak rally)
Price Down + OI Up = Bearish (New shorts entering)
Price Down + OI Down = Long unwinding (Weak decline)

OI-Based Support and Resistance:
- High Put OI at a strike = Potential support level
- High Call OI at a strike = Potential resistance level
- Max Pain: Strike where option sellers profit most

OI Analysis Tips:
1. Look at OI buildup during the day, not just absolute numbers
2. Compare OI changes across multiple strikes
3. Use OI with price action, not in isolation
4. Heavy OI at round numbers (24000, 25000) acts as magnet

PCR with OI:
- PCR = Put OI / Call OI
- PCR > 1: More puts written, bullish sentiment
- PCR < 0.7: More calls written, bearish sentiment
- Extreme PCR often indicates reversal
""",
            "metadata": {
                "category": "Market Analysis",
                "topic": "Open Interest Analysis",
                "type": "educational"
            }
        },
        {
            "content": """
Expiry Day Trading Strategies

Characteristics of Expiry Day:
- Higher volatility and wider spreads
- Rapid time decay (Theta at maximum)
- OTM options can become worthless quickly
- ITM options converge to intrinsic value

Popular Expiry Day Strategies:

1. Short Straddle/Strangle (Risky):
   - Sell ATM Call and ATM Put
   - Profit if market stays in range
   - High risk of unlimited loss

2. Iron Fly:
   - Sell ATM straddle + Buy OTM wings
   - Limited risk version of short straddle
   - Works in range-bound expiry

3. Directional Buying (High Risk/Reward):
   - Buy OTM options for big moves
   - Small premium, potential large gains
   - Most expire worthless

4. 0 DTE Strategy:
   - Trade options on their expiry day
   - Fast Theta decay benefits sellers
   - Requires strict risk management

Risk Management on Expiry:
- Use strict stop losses
- Trade with small position sizes
- Avoid holding naked shorts
- Be prepared for sudden moves
- Close positions before last 30 minutes if unsure

Best Practices:
- Don't trade if not experienced
- Understand gamma risk near expiry
- ATM options have highest gamma
- Pin risk: Stock closing near sold strike
""",
            "metadata": {
                "category": "Trading Strategies",
                "topic": "Expiry Day Trading",
                "type": "educational"
            }
        },
        {
            "content": """
Common Options Trading Mistakes to Avoid

1. Not Understanding Greeks:
   - Trading without knowing Delta, Theta, Vega impact
   - Solution: Study Greeks before taking positions

2. Ignoring Implied Volatility:
   - Buying options when IV is extremely high
   - Solution: Check IV percentile before trading

3. Over-leveraging:
   - Putting too much capital in single trade
   - Solution: Never risk more than 2% per trade

4. Holding Losing Positions:
   - Hope is not a strategy
   - Solution: Set stop loss before entering trade

5. Trading Without a Plan:
   - No entry/exit criteria defined
   - Solution: Write down plan before each trade

6. Ignoring Time Decay:
   - Holding OTM options for too long
   - Solution: Be aware of Theta, especially near expiry

7. Not Adjusting Positions:
   - Set and forget mentality
   - Solution: Monitor and adjust as market moves

8. Trading Illiquid Options:
   - Wide bid-ask spreads eat profits
   - Solution: Trade liquid strikes with tight spreads

9. Revenge Trading:
   - Trading emotionally after losses
   - Solution: Take a break after consecutive losses

10. Overtrading:
    - Trading too frequently
    - Solution: Quality over quantity, wait for setups
""",
            "metadata": {
                "category": "Risk Management",
                "topic": "Common Mistakes",
                "type": "educational"
            }
        },
        {
            "content": """
NIFTY and BANKNIFTY Specific Trading Tips

NIFTY 50 Characteristics:
- Benchmark index of 50 large-cap stocks
- Less volatile than BANKNIFTY
- Lot size: 25 (previously 75, changed in 2023)
- Weekly expiry: Thursday
- Good for beginners due to lower volatility

BANKNIFTY Characteristics:
- Index of 12 major banking stocks
- More volatile than NIFTY
- Lot size: 15 (previously 25)
- Weekly expiry: Wednesday
- Better premium but higher risk

Strike Selection Tips:

For NIFTY:
- ATM for directional plays
- 100-200 points OTM for selling
- Weekly options for short-term
- Monthly for positional trades

For BANKNIFTY:
- ATM to 1 strike OTM for buying
- 300-500 points OTM for selling
- Wider stops due to higher volatility
- More premium in weekly options

Correlation Tips:
- BANKNIFTY usually moves faster than NIFTY
- On banking-specific news, BANKNIFTY leads
- On broad market news, NIFTY leads
- Use this for hedging strategies

Best Trading Times:
- 9:15-10:00 AM: Gap analysis, initial moves
- 10:00-11:30 AM: Trend establishment
- 2:00-3:30 PM: Final trend push
- Avoid: 12:00-1:30 PM (low volume)
""",
            "metadata": {
                "category": "Index Trading",
                "topic": "NIFTY BANKNIFTY Tips",
                "type": "educational"
            }
        }
    ]


async def load_knowledge_base() -> Dict[str, Any]:
    """
    Load all knowledge into the RAG vector database

    Returns:
        Dict with loading statistics
    """
    if not rag_service.is_configured():
        return {
            "success": False,
            "error": "RAG service not configured. Set OPENAI_API_KEY first."
        }

    # Reset existing knowledge base
    rag_service.reset_knowledge_base()

    all_documents = []

    # Load all knowledge categories
    all_documents.extend(format_index_info())
    all_documents.extend(format_greeks_info())
    all_documents.extend(format_strategies_info())
    all_documents.extend(format_market_indicators())
    all_documents.extend(format_technical_indicators())
    all_documents.extend(format_risk_management())
    all_documents.extend(format_trading_times())
    all_documents.extend(format_faq())
    all_documents.extend(get_additional_knowledge())

    # Add to RAG
    total_chunks = rag_service.add_documents_batch(all_documents)

    return {
        "success": True,
        "documents_processed": len(all_documents),
        "chunks_created": total_chunks,
        "categories": [
            "Index Information",
            "Options Greeks",
            "Options Strategies",
            "Market Indicators",
            "Technical Analysis",
            "Risk Management",
            "Trading Schedule",
            "FAQ",
            "Educational Content"
        ]
    }


def load_knowledge_base_sync() -> Dict[str, Any]:
    """Synchronous version of load_knowledge_base"""
    import asyncio
    return asyncio.get_event_loop().run_until_complete(load_knowledge_base())
