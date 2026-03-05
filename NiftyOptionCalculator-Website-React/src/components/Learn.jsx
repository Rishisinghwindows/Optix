import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'

const topicKeys = ['basics', 'moneyness', 'greeks', 'strategies', 'indianMarkets', 'riskManagement']

const topicConfig = {
  basics: {
    icon: '📚',
    color: '#34c759',
    lessons: [
      {
        title: 'What are Options?',
        description: 'Options are contracts that give you the right (not obligation) to buy or sell an asset at a fixed price before a certain date.',
        example: 'Think of it like booking a movie ticket in advance - you pay a small amount now to lock in the price, and can decide later whether to watch the movie or not.',
        keyPoints: [
          'Right to buy/sell, not obligation',
          'Pay a small "premium" upfront',
          'Has an expiry date',
          'Can make money when market goes up OR down'
        ]
      },
      {
        title: 'Call vs Put Options',
        description: 'Call = Right to BUY (bullish bet). Put = Right to SELL (bearish bet).',
        example: 'If you think NIFTY will go UP, buy a CALL. If you think it will go DOWN, buy a PUT.',
        keyPoints: [
          'CALL: Profit when price goes UP',
          'PUT: Profit when price goes DOWN',
          'Maximum loss = Premium paid',
          'Potential profit can be unlimited'
        ]
      },
      {
        title: 'Strike Price & Premium',
        description: 'Strike price is the price at which you can buy/sell. Premium is what you pay for the option.',
        example: 'NIFTY is at 25,000. You buy 25,200 CE (Call) for ₹150. Here 25,200 is strike price, ₹150 is premium.',
        keyPoints: [
          'Strike price = Your target price',
          'Premium = Cost of the option',
          'Higher premium = Higher probability',
          'Premium changes every second'
        ]
      },
      {
        title: 'Expiry & Lot Size',
        description: 'Options expire on specific dates. Lot size is the minimum quantity you must trade.',
        example: 'NIFTY lot size is 75. Weekly expiry is every Thursday. If you buy 1 lot, you control 75 units of NIFTY.',
        keyPoints: [
          'NIFTY lot size: 75',
          'BANKNIFTY lot size: 15',
          'Weekly expiry: Every Thursday',
          'Monthly expiry: Last Thursday'
        ]
      }
    ]
  },
  moneyness: {
    icon: '💰',
    color: '#007aff',
    lessons: [
      {
        title: 'In The Money (ITM)',
        description: 'An option that already has real value. For CALL: Strike < Current Price. For PUT: Strike > Current Price.',
        example: 'NIFTY at 25,000. A 24,800 CE is ITM because you can buy at 24,800 and sell at 25,000 = ₹200 profit already built in.',
        keyPoints: [
          'Has intrinsic value',
          'More expensive premium',
          'Higher delta (moves more with stock)',
          'Lower risk of total loss'
        ]
      },
      {
        title: 'At The Money (ATM)',
        description: 'When strike price equals current price. Most liquid and actively traded.',
        example: 'NIFTY at 25,000. The 25,000 CE and 25,000 PE are both ATM options.',
        keyPoints: [
          'Strike ≈ Current price',
          'Delta around 0.5',
          'Highest time value',
          'Best liquidity'
        ]
      },
      {
        title: 'Out of The Money (OTM)',
        description: 'No real value yet - only hope value. For CALL: Strike > Current Price. For PUT: Strike < Current Price.',
        example: 'NIFTY at 25,000. A 25,500 CE is OTM. NIFTY needs to go up 500 points for this to have value.',
        keyPoints: [
          'Cheaper premium',
          'Higher risk (can go to zero)',
          'Lower delta',
          'Popular for speculation'
        ]
      }
    ]
  },
  greeks: {
    icon: '🔢',
    color: '#5856d6',
    lessons: [
      {
        title: 'Delta (Δ) - Price Sensitivity',
        description: 'How much the option price moves when the underlying moves ₹1. Delta of 0.5 means option moves ₹0.50 for every ₹1 move.',
        example: 'Your call has Delta 0.6. NIFTY goes up ₹100. Your option goes up approximately ₹60.',
        keyPoints: [
          'CALL delta: 0 to +1',
          'PUT delta: -1 to 0',
          'ATM options: Delta ~0.5',
          'Also shows probability of ITM'
        ]
      },
      {
        title: 'Theta (Θ) - Time Decay',
        description: 'How much value the option loses each day. Time is the enemy of option buyers!',
        example: 'Your option has Theta -15. Every day it loses ₹15 in value even if nothing else changes.',
        keyPoints: [
          'Always negative for buyers',
          'Positive for sellers (they earn)',
          'Accelerates near expiry',
          'ATM has highest theta'
        ]
      },
      {
        title: 'Gamma (Γ) - Delta Acceleration',
        description: 'How fast delta changes. High gamma = delta can change rapidly with price moves.',
        example: 'Near expiry ATM options have high gamma. A small move can suddenly make delta jump from 0.5 to 0.8.',
        keyPoints: [
          'Rate of change of delta',
          'Highest for ATM options',
          'Increases near expiry',
          'Risk for option sellers'
        ]
      },
      {
        title: 'Vega (V) - Volatility Sensitivity',
        description: 'How option price changes with volatility. Higher volatility = Higher option prices.',
        example: 'Before election results, volatility jumps. Your option with Vega 10 gains ₹10 for every 1% IV increase.',
        keyPoints: [
          'Measures IV sensitivity',
          'Higher for longer expiry',
          'IV crush after events',
          'Important for premium sellers'
        ]
      }
    ]
  },
  strategies: {
    icon: '📊',
    color: '#ff9500',
    lessons: [
      {
        title: 'Long Call (Bullish)',
        description: 'Buy a call when you expect price to go UP. Simple and most basic strategy.',
        example: 'NIFTY at 25,000. Buy 25,100 CE for ₹120. If NIFTY goes to 25,400, your option is worth at least ₹300. Profit = ₹180.',
        keyPoints: [
          'Max loss: Premium paid',
          'Max profit: Unlimited',
          'Best for strong bullish view',
          'Time decay works against you'
        ]
      },
      {
        title: 'Long Put (Bearish)',
        description: 'Buy a put when you expect price to go DOWN. Profit from falling markets.',
        example: 'NIFTY at 25,000. Buy 24,900 PE for ₹100. If NIFTY falls to 24,600, your option is worth at least ₹300. Profit = ₹200.',
        keyPoints: [
          'Max loss: Premium paid',
          'Max profit: Strike price - Premium',
          'Best for bearish view',
          'Good for hedging portfolio'
        ]
      },
      {
        title: 'Bull Call Spread',
        description: 'Buy one call, sell a higher strike call. Reduces cost but caps profit.',
        example: 'Buy 25,000 CE @ ₹150, Sell 25,200 CE @ ₹80. Net cost = ₹70. Max profit = ₹200-₹70 = ₹130.',
        keyPoints: [
          'Lower cost than plain call',
          'Limited risk and reward',
          'Best for moderate bullish view',
          'Good for beginners'
        ]
      },
      {
        title: 'Iron Condor (Neutral)',
        description: 'Sell both OTM call and put spreads. Profit if price stays in a range.',
        example: 'NIFTY at 25,000. Sell 24,700 PE, Buy 24,500 PE, Sell 25,300 CE, Buy 25,500 CE. Collect premium if NIFTY stays between 24,700-25,300.',
        keyPoints: [
          'Profit from sideways market',
          'Collect premium',
          'Defined risk',
          'Time decay helps you'
        ]
      }
    ]
  },
  indianMarkets: {
    icon: '🇮🇳',
    color: '#ff3b30',
    lessons: [
      {
        title: 'NIFTY 50 Options',
        description: 'Options on India\'s benchmark index representing top 50 companies.',
        example: 'NIFTY at 25,000. Lot size is 75. One lot controls ₹18,75,000 worth of NIFTY (25,000 × 75).',
        keyPoints: [
          'Lot size: 75',
          'Tick size: ₹0.05',
          'Weekly expiry: Thursday',
          'Most liquid options in India'
        ]
      },
      {
        title: 'Bank NIFTY Options',
        description: 'Options on the banking sector index. More volatile than NIFTY.',
        example: 'Bank NIFTY at 52,000. Lot size is 15. Higher volatility means bigger premiums and bigger moves.',
        keyPoints: [
          'Lot size: 15',
          'Weekly expiry: Wednesday',
          'Higher volatility than NIFTY',
          'Popular for quick trades'
        ]
      },
      {
        title: 'Trading Hours & Settlement',
        description: 'NSE derivatives market timings and how settlement works.',
        example: 'Market opens 9:15 AM, closes 3:30 PM. Options are cash-settled - no physical delivery.',
        keyPoints: [
          'Pre-market: 9:00-9:15 AM',
          'Trading: 9:15 AM - 3:30 PM',
          'Cash settlement (no delivery)',
          'Settlement on expiry day'
        ]
      },
      {
        title: 'Margin Requirements',
        description: 'How much capital you need to trade options in India.',
        example: 'Buying options needs full premium. Selling options needs margin (can be ₹1-2 lakhs per lot).',
        keyPoints: [
          'Option buying: Pay full premium',
          'Option selling: SPAN + Exposure margin',
          'Use margin calculator',
          'Can start with ₹5,000-15,000'
        ]
      }
    ]
  },
  riskManagement: {
    icon: '⚠️',
    color: '#8e8e93',
    lessons: [
      {
        title: 'Position Sizing',
        description: 'Never risk more than 2-5% of your capital on a single trade.',
        example: 'Capital: ₹1,00,000. Max risk per trade: ₹2,000-5,000. This allows you to survive losing streaks.',
        keyPoints: [
          'Risk 2-5% per trade max',
          'Calculate position size first',
          'Account for worst case',
          'Preserve capital to trade again'
        ]
      },
      {
        title: 'Stop Loss Strategies',
        description: 'Always have an exit plan before entering a trade.',
        example: 'Buy option at ₹100. Set stop loss at ₹70 (30% loss). If it hits ₹70, exit immediately - no emotions.',
        keyPoints: [
          'Set SL before entering',
          'Use percentage-based SL',
          'Stick to your plan',
          'Never average losing positions'
        ]
      },
      {
        title: 'The 90-90-90 Rule',
        description: '90% of traders lose 90% of their capital in 90 days. Don\'t be one of them.',
        example: 'Start with paper trading. Trade small. Learn for 6 months before going big.',
        keyPoints: [
          'Paper trade first',
          'Start with small capital',
          'Track every trade',
          'Learn from losses'
        ]
      },
      {
        title: 'Common Mistakes',
        description: 'Avoid these beginner mistakes that destroy accounts.',
        example: 'Overtrading, no stop loss, revenge trading, trading without a plan, overleveraging.',
        keyPoints: [
          'Don\'t overtrade',
          'Don\'t chase losses',
          'Don\'t ignore theta decay',
          'Don\'t trade without a plan'
        ]
      }
    ]
  }
}

const glossary = [
  { term: 'ATM', definition: 'At The Money - Strike price equals current price' },
  { term: 'CE', definition: 'Call European - A call option' },
  { term: 'Delta', definition: 'Measures price sensitivity to underlying movement' },
  { term: 'Expiry', definition: 'Date when option contract expires' },
  { term: 'Gamma', definition: 'Rate of change of delta' },
  { term: 'IV', definition: 'Implied Volatility - Expected future volatility' },
  { term: 'ITM', definition: 'In The Money - Option has intrinsic value' },
  { term: 'Lot Size', definition: 'Minimum quantity to trade (NIFTY: 75)' },
  { term: 'OI', definition: 'Open Interest - Total outstanding contracts' },
  { term: 'OTM', definition: 'Out of The Money - Option has no intrinsic value' },
  { term: 'PE', definition: 'Put European - A put option' },
  { term: 'Premium', definition: 'Price paid to buy an option' },
  { term: 'Strike', definition: 'Price at which option can be exercised' },
  { term: 'Theta', definition: 'Time decay - Value lost per day' },
  { term: 'Vega', definition: 'Sensitivity to volatility changes' },
]

function Learn() {
  const { t } = useTranslation()
  const [selectedTopic, setSelectedTopic] = useState(null)
  const [selectedLesson, setSelectedLesson] = useState(null)
  const [showGlossary, setShowGlossary] = useState(false)

  // Build topics array with translations
  const topics = topicKeys.map(key => ({
    id: key,
    icon: topicConfig[key].icon,
    title: t(`learn.topics.${key}.title`),
    subtitle: t(`learn.topics.${key}.description`),
    color: topicConfig[key].color,
    lessons: topicConfig[key].lessons
  }))

  return (
    <section className="learn-section" id="learn">
      <div className="container">
        <div className="section-header">
          <span className="section-badge">Education</span>
          <h2 className="section-title">
            {t('learn.title')} <span className="gradient-text">{t('learn.subtitle')}</span>
          </h2>
          <p className="section-subtitle">
            Master options trading with simple explanations, real examples, and
            practical knowledge designed for Indian markets.
          </p>
        </div>

        {/* Topic Cards Grid */}
        {!selectedTopic && (
          <>
            <div className="learn-grid">
              {topics.map(topic => (
                <div
                  key={topic.id}
                  className="learn-card"
                  onClick={() => setSelectedTopic(topic)}
                  style={{ '--card-color': topic.color }}
                >
                  <div className="learn-card-icon" style={{ background: `${topic.color}15`, color: topic.color }}>
                    {topic.icon}
                  </div>
                  <h3>{topic.title}</h3>
                  <p>{topic.subtitle}</p>
                  <div className="learn-card-meta">
                    <span>{topic.lessons.length} lessons</span>
                    <span className="arrow">→</span>
                  </div>
                </div>
              ))}

              {/* Glossary Card */}
              <div
                className="learn-card glossary-card"
                onClick={() => setShowGlossary(true)}
              >
                <div className="learn-card-icon" style={{ background: '#34c75915', color: '#34c759' }}>
                  📖
                </div>
                <h3>Glossary</h3>
                <p>Quick reference for all terms</p>
                <div className="learn-card-meta">
                  <span>{glossary.length} terms</span>
                  <span className="arrow">→</span>
                </div>
              </div>
            </div>

            {/* Quick Stats */}
            <div className="learn-stats">
              <div className="learn-stat">
                <span className="stat-number">6</span>
                <span className="stat-label">Topics</span>
              </div>
              <div className="learn-stat">
                <span className="stat-number">24</span>
                <span className="stat-label">Lessons</span>
              </div>
              <div className="learn-stat">
                <span className="stat-number">Free</span>
                <span className="stat-label">Forever</span>
              </div>
            </div>

            {/* CTA */}
            <div className="learn-cta">
              <p>Want interactive learning with quizzes?</p>
              <a href="/app/education" className="btn btn-primary">
                {t('learn.openCourse')} →
              </a>
            </div>
          </>
        )}

        {/* Topic Detail View */}
        {selectedTopic && !selectedLesson && (
          <div className="topic-detail">
            <button className="back-btn" onClick={() => setSelectedTopic(null)}>
              ← Back to Topics
            </button>

            <div className="topic-header" style={{ '--topic-color': selectedTopic.color }}>
              <span className="topic-icon">{selectedTopic.icon}</span>
              <div>
                <h2>{selectedTopic.title}</h2>
                <p>{selectedTopic.subtitle}</p>
              </div>
            </div>

            <div className="lessons-grid">
              {selectedTopic.lessons.map((lesson, idx) => (
                <div
                  key={idx}
                  className="lesson-preview"
                  onClick={() => setSelectedLesson(lesson)}
                >
                  <span className="lesson-number">{idx + 1}</span>
                  <div className="lesson-info">
                    <h4>{lesson.title}</h4>
                    <p>{lesson.description}</p>
                  </div>
                  <span className="lesson-arrow">→</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Lesson Detail View */}
        {selectedLesson && (
          <div className="lesson-detail">
            <button className="back-btn" onClick={() => setSelectedLesson(null)}>
              ← Back to {selectedTopic.title}
            </button>

            <div className="lesson-content-card">
              <h2>{selectedLesson.title}</h2>

              <div className="lesson-section">
                <h4>What is it?</h4>
                <p>{selectedLesson.description}</p>
              </div>

              <div className="lesson-section example-box">
                <h4>Simple Example</h4>
                <p>{selectedLesson.example}</p>
              </div>

              <div className="lesson-section">
                <h4>Key Points to Remember</h4>
                <ul className="key-points">
                  {selectedLesson.keyPoints.map((point, idx) => (
                    <li key={idx}>
                      <span className="check-icon">✓</span>
                      {point}
                    </li>
                  ))}
                </ul>
              </div>

              <div className="lesson-nav">
                {selectedTopic.lessons.indexOf(selectedLesson) > 0 && (
                  <button
                    className="btn btn-secondary"
                    onClick={() => setSelectedLesson(selectedTopic.lessons[selectedTopic.lessons.indexOf(selectedLesson) - 1])}
                  >
                    ← Previous
                  </button>
                )}
                {selectedTopic.lessons.indexOf(selectedLesson) < selectedTopic.lessons.length - 1 && (
                  <button
                    className="btn btn-primary"
                    onClick={() => setSelectedLesson(selectedTopic.lessons[selectedTopic.lessons.indexOf(selectedLesson) + 1])}
                  >
                    Next →
                  </button>
                )}
              </div>
            </div>
          </div>
        )}

        {/* Glossary Modal */}
        {showGlossary && (
          <div className="glossary-modal">
            <div className="glossary-content">
              <div className="glossary-header">
                <h2>Options Glossary</h2>
                <button className="close-btn" onClick={() => setShowGlossary(false)}>×</button>
              </div>
              <div className="glossary-list">
                {glossary.map((item, idx) => (
                  <div key={idx} className="glossary-item">
                    <span className="glossary-term">{item.term}</span>
                    <span className="glossary-def">{item.definition}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </div>
    </section>
  )
}

export default Learn
