import React, { useState } from 'react'
import { AdBanner } from '../ads'
import { ADS_CONFIG } from '../../config/adsConfig'

const categories = [
  { id: 'basics', label: 'Basics', icon: '📖' },
  { id: 'greeks', label: 'Greeks', icon: '🔢' },
  { id: 'strategies', label: 'Strategies', icon: '📊' },
]

const lessons = {
  basics: [
    {
      id: 'what-are-options',
      title: 'What are Options?',
      duration: '5 min',
      difficulty: 'Beginner',
      content: `
        <h3>Understanding Options</h3>
        <p>An option is a financial derivative that gives the buyer the right, but not the obligation, to buy or sell an underlying asset at a predetermined price within a specific time period.</p>

        <h4>Key Terms</h4>
        <ul>
          <li><strong>Call Option:</strong> Gives the right to BUY the underlying asset</li>
          <li><strong>Put Option:</strong> Gives the right to SELL the underlying asset</li>
          <li><strong>Strike Price:</strong> The predetermined price at which the option can be exercised</li>
          <li><strong>Expiry Date:</strong> The date when the option contract expires</li>
          <li><strong>Premium:</strong> The price paid to purchase the option</li>
        </ul>

        <h4>Example</h4>
        <p>If NIFTY is at 24,000 and you buy a 24,200 CE (Call) for ₹150, you have the right to buy NIFTY at 24,200. If NIFTY goes to 24,500, your option is worth at least ₹300 (24,500 - 24,200).</p>
      `
    },
    {
      id: 'call-vs-put',
      title: 'Call vs Put Options',
      duration: '7 min',
      difficulty: 'Beginner',
      content: `
        <h3>Call Options</h3>
        <p>A Call option gives you the right to BUY the underlying asset at the strike price. You buy calls when you expect the price to go UP.</p>
        <div class="info-box bullish">
          <strong>When to Buy Calls:</strong> When you're bullish on the market and expect prices to rise.
        </div>

        <h3>Put Options</h3>
        <p>A Put option gives you the right to SELL the underlying asset at the strike price. You buy puts when you expect the price to go DOWN.</p>
        <div class="info-box bearish">
          <strong>When to Buy Puts:</strong> When you're bearish on the market and expect prices to fall.
        </div>

        <h4>Quick Reference</h4>
        <table>
          <tr><th>Scenario</th><th>Call Buyer</th><th>Put Buyer</th></tr>
          <tr><td>Market Goes Up</td><td class="profit">Profit ✅</td><td class="loss">Loss ❌</td></tr>
          <tr><td>Market Goes Down</td><td class="loss">Loss ❌</td><td class="profit">Profit ✅</td></tr>
        </table>
      `
    },
    {
      id: 'itm-atm-otm',
      title: 'ITM, ATM & OTM',
      duration: '6 min',
      difficulty: 'Beginner',
      content: `
        <h3>Moneyness of Options</h3>
        <p>Moneyness describes the relationship between an option's strike price and the current price of the underlying asset.</p>

        <h4>In The Money (ITM)</h4>
        <ul>
          <li><strong>Call:</strong> Strike price < Current price (e.g., 24,000 CE when NIFTY is at 24,200)</li>
          <li><strong>Put:</strong> Strike price > Current price (e.g., 24,400 PE when NIFTY is at 24,200)</li>
          <li>Has intrinsic value</li>
        </ul>

        <h4>At The Money (ATM)</h4>
        <ul>
          <li>Strike price ≈ Current price</li>
          <li>Highest time value</li>
          <li>Most liquid options</li>
        </ul>

        <h4>Out of The Money (OTM)</h4>
        <ul>
          <li><strong>Call:</strong> Strike price > Current price</li>
          <li><strong>Put:</strong> Strike price < Current price</li>
          <li>No intrinsic value, only time value</li>
          <li>Lower premium but lower probability of profit</li>
        </ul>
      `
    },
    {
      id: 'intrinsic-time-value',
      title: 'Intrinsic & Time Value',
      duration: '8 min',
      difficulty: 'Intermediate',
      content: `
        <h3>Option Premium Components</h3>
        <p>Every option's premium consists of two parts:</p>

        <h4>Intrinsic Value</h4>
        <p>The real, tangible value of an option if it were exercised immediately.</p>
        <ul>
          <li>Call Intrinsic Value = Max(0, Spot Price - Strike Price)</li>
          <li>Put Intrinsic Value = Max(0, Strike Price - Spot Price)</li>
          <li>OTM options have zero intrinsic value</li>
        </ul>

        <h4>Time Value</h4>
        <p>The extra amount buyers are willing to pay for the possibility that the option might become more valuable before expiry.</p>
        <div class="formula">
          Time Value = Option Premium - Intrinsic Value
        </div>

        <h4>Time Decay</h4>
        <p>Time value decreases as the option approaches expiry. This is called <strong>theta decay</strong>. The decay accelerates in the last week before expiry.</p>
      `
    },
  ],
  greeks: [
    {
      id: 'delta',
      title: 'Delta (Δ)',
      duration: '8 min',
      difficulty: 'Intermediate',
      content: `
        <h3>What is Delta?</h3>
        <p>Delta measures how much an option's price changes for every ₹1 change in the underlying asset's price.</p>

        <h4>Delta Values</h4>
        <ul>
          <li><strong>Call Options:</strong> 0 to +1 (positive delta)</li>
          <li><strong>Put Options:</strong> -1 to 0 (negative delta)</li>
          <li><strong>ATM Options:</strong> Around ±0.5</li>
        </ul>

        <h4>Example</h4>
        <p>If a NIFTY Call has delta = 0.6, and NIFTY moves up by ₹100:</p>
        <div class="formula">
          Option Price Change = 0.6 × ₹100 = ₹60
        </div>

        <h4>Delta as Probability</h4>
        <p>Delta can be interpreted as the approximate probability of the option expiring ITM.</p>
        <ul>
          <li>Delta 0.7 ≈ 70% chance of expiring ITM</li>
          <li>Delta 0.3 ≈ 30% chance of expiring ITM</li>
        </ul>
      `
    },
    {
      id: 'gamma',
      title: 'Gamma (Γ)',
      duration: '7 min',
      difficulty: 'Advanced',
      content: `
        <h3>What is Gamma?</h3>
        <p>Gamma measures the rate of change of delta for every ₹1 change in the underlying price. It's the "delta of delta".</p>

        <h4>Key Points</h4>
        <ul>
          <li>Gamma is always positive for both calls and puts</li>
          <li>Highest for ATM options</li>
          <li>Increases as expiry approaches</li>
          <li>Low for deep ITM and deep OTM options</li>
        </ul>

        <h4>Gamma Risk</h4>
        <p>Option sellers face <strong>gamma risk</strong> - sudden moves can cause rapid delta changes, leading to large losses.</p>

        <div class="info-box warning">
          <strong>Warning:</strong> Short gamma positions can be dangerous during volatile markets. ATM options near expiry have the highest gamma risk.
        </div>
      `
    },
    {
      id: 'theta',
      title: 'Theta (Θ)',
      duration: '6 min',
      difficulty: 'Intermediate',
      content: `
        <h3>What is Theta?</h3>
        <p>Theta measures how much an option loses in value each day due to time decay, assuming all other factors remain constant.</p>

        <h4>Key Points</h4>
        <ul>
          <li>Theta is always negative for option buyers</li>
          <li>Theta is positive for option sellers (they benefit from decay)</li>
          <li>ATM options have the highest theta</li>
          <li>Theta accelerates in the last week before expiry</li>
        </ul>

        <h4>Example</h4>
        <p>If an option has theta = -15, it loses ₹15 in value every day.</p>

        <div class="info-box">
          <strong>Trading Tip:</strong> Option sellers love theta - every day that passes without significant movement, they profit from time decay.
        </div>
      `
    },
    {
      id: 'vega',
      title: 'Vega (V)',
      duration: '7 min',
      difficulty: 'Advanced',
      content: `
        <h3>What is Vega?</h3>
        <p>Vega measures how much an option's price changes for every 1% change in implied volatility (IV).</p>

        <h4>Key Points</h4>
        <ul>
          <li>Vega is always positive for long options</li>
          <li>Higher IV = Higher option premiums</li>
          <li>ATM options have the highest vega</li>
          <li>Longer-dated options have higher vega</li>
        </ul>

        <h4>IV Crush</h4>
        <p>After major events (earnings, elections), IV typically drops sharply, causing option prices to fall even if the underlying doesn't move much.</p>

        <div class="info-box warning">
          <strong>Important:</strong> Buying options before high-IV events can be risky due to IV crush after the event.
        </div>
      `
    },
  ],
  strategies: [
    {
      id: 'covered-call',
      title: 'Covered Call',
      duration: '10 min',
      difficulty: 'Intermediate',
      content: `
        <h3>Covered Call Strategy</h3>
        <p>Sell a call option while holding the underlying asset to generate additional income.</p>

        <h4>Setup</h4>
        <ul>
          <li>Own 1 lot of the underlying</li>
          <li>Sell 1 OTM Call option</li>
        </ul>

        <h4>When to Use</h4>
        <ul>
          <li>Neutral to slightly bullish outlook</li>
          <li>Want to generate income from holdings</li>
          <li>Willing to sell at the strike price if assigned</li>
        </ul>

        <h4>Risk/Reward</h4>
        <ul>
          <li><strong>Max Profit:</strong> Premium received + (Strike - Entry price)</li>
          <li><strong>Max Loss:</strong> Entry price - Premium (if underlying goes to zero)</li>
          <li><strong>Breakeven:</strong> Entry price - Premium</li>
        </ul>
      `
    },
    {
      id: 'bull-call-spread',
      title: 'Bull Call Spread',
      duration: '10 min',
      difficulty: 'Intermediate',
      content: `
        <h3>Bull Call Spread</h3>
        <p>A bullish strategy with limited risk and limited profit potential.</p>

        <h4>Setup</h4>
        <ul>
          <li>Buy 1 ATM/ITM Call</li>
          <li>Sell 1 OTM Call (higher strike)</li>
          <li>Same expiry date</li>
        </ul>

        <h4>Example (NIFTY at 24,200)</h4>
        <ul>
          <li>Buy 24,200 CE @ ₹180</li>
          <li>Sell 24,400 CE @ ₹90</li>
          <li>Net Debit: ₹90</li>
        </ul>

        <h4>Risk/Reward</h4>
        <ul>
          <li><strong>Max Profit:</strong> (Strike Difference - Net Premium) × Lot Size</li>
          <li><strong>Max Loss:</strong> Net Premium × Lot Size</li>
          <li><strong>Breakeven:</strong> Lower Strike + Net Premium</li>
        </ul>
      `
    },
    {
      id: 'iron-condor',
      title: 'Iron Condor',
      duration: '12 min',
      difficulty: 'Advanced',
      content: `
        <h3>Iron Condor</h3>
        <p>A non-directional strategy that profits from low volatility and time decay.</p>

        <h4>Setup</h4>
        <ul>
          <li>Sell 1 OTM Put (lower)</li>
          <li>Buy 1 OTM Put (even lower)</li>
          <li>Sell 1 OTM Call (higher)</li>
          <li>Buy 1 OTM Call (even higher)</li>
        </ul>

        <h4>When to Use</h4>
        <ul>
          <li>Expect low volatility</li>
          <li>Sideways market expected</li>
          <li>Want to collect premium</li>
        </ul>

        <h4>Risk/Reward</h4>
        <ul>
          <li><strong>Max Profit:</strong> Net premium collected</li>
          <li><strong>Max Loss:</strong> Width of spread - Net premium</li>
          <li><strong>Breakeven:</strong> Two points (lower put sold - premium, higher call sold + premium)</li>
        </ul>
      `
    },
    {
      id: 'straddle',
      title: 'Long Straddle',
      duration: '8 min',
      difficulty: 'Intermediate',
      content: `
        <h3>Long Straddle</h3>
        <p>A volatility strategy that profits from large moves in either direction.</p>

        <h4>Setup</h4>
        <ul>
          <li>Buy 1 ATM Call</li>
          <li>Buy 1 ATM Put</li>
          <li>Same strike, same expiry</li>
        </ul>

        <h4>When to Use</h4>
        <ul>
          <li>Expect a big move but unsure of direction</li>
          <li>Before major events (earnings, budget, RBI policy)</li>
          <li>IV is relatively low</li>
        </ul>

        <h4>Risk/Reward</h4>
        <ul>
          <li><strong>Max Profit:</strong> Unlimited</li>
          <li><strong>Max Loss:</strong> Total premium paid</li>
          <li><strong>Breakevens:</strong> Strike ± Total premium</li>
        </ul>

        <div class="info-box warning">
          <strong>Caution:</strong> IV crush after events can significantly reduce profits even if the underlying moves.
        </div>
      `
    },
  ]
}

function Education() {
  const [selectedCategory, setSelectedCategory] = useState('basics')
  const [selectedLesson, setSelectedLesson] = useState(null)

  const currentLessons = lessons[selectedCategory]

  return (
    <div className="education-page">
      {selectedLesson ? (
        <div className="lesson-view">
          <button className="back-btn" onClick={() => setSelectedLesson(null)}>
            ← Back to Lessons
          </button>

          <div className="lesson-header">
            <h2>{selectedLesson.title}</h2>
            <div className="lesson-meta">
              <span className="duration">⏱️ {selectedLesson.duration}</span>
              <span className={`difficulty ${selectedLesson.difficulty.toLowerCase()}`}>
                {selectedLesson.difficulty}
              </span>
            </div>
          </div>

          <div
            className="lesson-content"
            dangerouslySetInnerHTML={{ __html: selectedLesson.content }}
          />

          <div className="lesson-footer">
            <button className="quiz-btn" onClick={() => window.location.href = '/app/quiz'}>
              Take Quiz →
            </button>
          </div>
        </div>
      ) : (
        <>
          {/* Category Tabs */}
          <div className="category-tabs">
            {categories.map(cat => (
              <button
                key={cat.id}
                className={`category-tab ${selectedCategory === cat.id ? 'active' : ''}`}
                onClick={() => setSelectedCategory(cat.id)}
              >
                <span className="cat-icon">{cat.icon}</span>
                <span>{cat.label}</span>
              </button>
            ))}
          </div>

          {/* Progress */}
          <div className="progress-section">
            <div className="progress-header">
              <span>Your Progress</span>
              <span>0/{currentLessons.length} completed</span>
            </div>
            <div className="progress-bar">
              <div className="progress-fill" style={{ width: '0%' }}></div>
            </div>
          </div>

          {/* Lessons List */}
          <div className="lessons-list">
            {currentLessons.map((lesson, idx) => (
              <div
                key={lesson.id}
                className="lesson-card"
                onClick={() => setSelectedLesson(lesson)}
              >
                <div className="lesson-number">{idx + 1}</div>
                <div className="lesson-info">
                  <h4>{lesson.title}</h4>
                  <div className="lesson-meta">
                    <span className="duration">⏱️ {lesson.duration}</span>
                    <span className={`difficulty ${lesson.difficulty.toLowerCase()}`}>
                      {lesson.difficulty}
                    </span>
                  </div>
                </div>
                <div className="lesson-arrow">→</div>
              </div>
            ))}
          </div>

          {/* Education Sidebar Ad */}
          <div className="education-sidebar-ad">
            <AdBanner
              slot={ADS_CONFIG.adUnits.educationSidebar}
              placement="educationSidebar"
              className="ad-sidebar"
            />
          </div>
        </>
      )}
    </div>
  )
}

export default Education
