import React from 'react'

function Screenshots() {
  return (
    <section id="screenshots" className="screenshots">
      <div className="container">
        <div className="section-header">
          <span className="section-badge">Screenshots</span>
          <h2 className="section-title">Beautiful & Intuitive Interface</h2>
          <p className="section-subtitle">Designed for traders who demand the best</p>
        </div>
        <div className="screenshots-carousel">
          <div className="screenshot-card">
            <div className="screenshot-frame">
              <div className="screenshot-content option-chain-preview">
                <div className="ss-header">Option Chain</div>
                <div className="ss-subheader">NIFTY 50 • 24,250</div>
                <div className="ss-table">
                  <div className="ss-row header">
                    <span>CE LTP</span>
                    <span>Strike</span>
                    <span>PE LTP</span>
                  </div>
                  <div className="ss-row">
                    <span className="green">285.50</span>
                    <span className="strike">24200</span>
                    <span className="red">125.75</span>
                  </div>
                  <div className="ss-row atm">
                    <span className="green">185.25</span>
                    <span className="strike">24250</span>
                    <span className="red">175.50</span>
                  </div>
                  <div className="ss-row">
                    <span className="green">95.80</span>
                    <span className="strike">24300</span>
                    <span className="red">245.25</span>
                  </div>
                </div>
              </div>
            </div>
            <h4>Option Chain</h4>
            <p>Real-time data with color-coded ITM/OTM</p>
          </div>

          <div className="screenshot-card">
            <div className="screenshot-frame">
              <div className="screenshot-content calculator-preview">
                <div className="ss-header">Calculator</div>
                <div className="calc-result">
                  <div className="calc-price">₹185.50</div>
                  <div className="calc-label">Theoretical Price</div>
                </div>
                <div className="calc-greeks">
                  <div className="calc-greek">
                    <span className="greek-symbol">Δ</span>
                    <span className="greek-value">0.55</span>
                  </div>
                  <div className="calc-greek">
                    <span className="greek-symbol">Θ</span>
                    <span className="greek-value">-12.5</span>
                  </div>
                  <div className="calc-greek">
                    <span className="greek-symbol">Γ</span>
                    <span className="greek-value">0.003</span>
                  </div>
                  <div className="calc-greek">
                    <span className="greek-symbol">V</span>
                    <span className="greek-value">8.2</span>
                  </div>
                </div>
              </div>
            </div>
            <h4>Price Calculator</h4>
            <p>Black-Scholes with Greeks analysis</p>
          </div>

          <div className="screenshot-card">
            <div className="screenshot-frame">
              <div className="screenshot-content paper-trade-preview">
                <div className="ss-header">Paper Trade</div>
                <div className="pt-balance">
                  <div className="pt-value">₹10,25,450</div>
                  <div className="pt-label">Portfolio Value</div>
                  <div className="pt-pnl positive">+₹25,450 (+2.5%)</div>
                </div>
                <div className="pt-stats">
                  <div className="pt-stat">
                    <span>Win Rate</span>
                    <span className="green">68%</span>
                  </div>
                  <div className="pt-stat">
                    <span>Trades</span>
                    <span>24</span>
                  </div>
                </div>
              </div>
            </div>
            <h4>Paper Trading</h4>
            <p>Practice risk-free with virtual money</p>
          </div>

          <div className="screenshot-card">
            <div className="screenshot-frame">
              <div className="screenshot-content quiz-preview">
                <div className="ss-header">Quiz</div>
                <div className="quiz-question">What does Delta measure?</div>
                <div className="quiz-options">
                  <div className="quiz-option">Time decay</div>
                  <div className="quiz-option correct">Price sensitivity</div>
                  <div className="quiz-option">Volatility impact</div>
                  <div className="quiz-option">Interest rate</div>
                </div>
              </div>
            </div>
            <h4>Interactive Quizzes</h4>
            <p>Test your options knowledge</p>
          </div>
        </div>
      </div>
    </section>
  )
}

export default Screenshots
