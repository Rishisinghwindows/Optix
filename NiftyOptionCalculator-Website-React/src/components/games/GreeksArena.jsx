import React, { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import './GreeksArena.css';

// Black-Scholes calculation functions
const normalCDF = (x) => {
  const a1 = 0.254829592, a2 = -0.284496736, a3 = 1.421413741;
  const a4 = -1.453152027, a5 = 1.061405429, p = 0.3275911;
  const sign = x < 0 ? -1 : 1;
  x = Math.abs(x) / Math.sqrt(2);
  const t = 1.0 / (1.0 + p * x);
  const y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);
  return 0.5 * (1.0 + sign * y);
};

const calculateBS = (spot, strike, time, rate, vol, type = 'call') => {
  if (time <= 0) time = 0.001;
  const d1 = (Math.log(spot / strike) + (rate + vol * vol / 2) * time) / (vol * Math.sqrt(time));
  const d2 = d1 - vol * Math.sqrt(time);

  let price, delta, gamma, theta, vega, rho;

  if (type === 'call') {
    price = spot * normalCDF(d1) - strike * Math.exp(-rate * time) * normalCDF(d2);
    delta = normalCDF(d1);
    rho = strike * time * Math.exp(-rate * time) * normalCDF(d2) / 100;
  } else {
    price = strike * Math.exp(-rate * time) * normalCDF(-d2) - spot * normalCDF(-d1);
    delta = normalCDF(d1) - 1;
    rho = -strike * time * Math.exp(-rate * time) * normalCDF(-d2) / 100;
  }

  gamma = Math.exp(-d1 * d1 / 2) / (spot * vol * Math.sqrt(2 * Math.PI * time));
  theta = -(spot * vol * Math.exp(-d1 * d1 / 2)) / (2 * Math.sqrt(2 * Math.PI * time)) / 365;
  vega = spot * Math.sqrt(time) * Math.exp(-d1 * d1 / 2) / Math.sqrt(2 * Math.PI) / 100;

  return { price, delta, gamma, theta, vega, rho };
};

const CHALLENGES = [
  {
    id: 1,
    title: "Delta Master",
    description: "Make Delta reach exactly 0.70 (±0.02)",
    target: { greek: 'delta', value: 0.70, tolerance: 0.02 },
    hint: "Tip: Move spot price closer to strike, or reduce time to expiry",
    difficulty: "Easy"
  },
  {
    id: 2,
    title: "Gamma Spike",
    description: "Maximize Gamma above 0.003",
    target: { greek: 'gamma', value: 0.003, tolerance: 0, comparison: 'gte' },
    hint: "Tip: ATM options near expiry have highest Gamma",
    difficulty: "Easy"
  },
  {
    id: 3,
    title: "Theta Crusher",
    description: "Make Theta decay greater than ₹15/day",
    target: { greek: 'theta', value: -15, tolerance: 0, comparison: 'lte' },
    hint: "Tip: Short-dated ATM options have highest Theta decay",
    difficulty: "Medium"
  },
  {
    id: 4,
    title: "Vega Volcano",
    description: "Increase Vega above 100",
    target: { greek: 'vega', value: 100, tolerance: 0, comparison: 'gte' },
    hint: "Tip: Long-dated options with high spot price have more Vega",
    difficulty: "Medium"
  },
  {
    id: 5,
    title: "Deep ITM",
    description: "Get Delta above 0.95 (deep in the money)",
    target: { greek: 'delta', value: 0.95, tolerance: 0, comparison: 'gte' },
    hint: "Tip: Move spot price well above strike",
    difficulty: "Easy"
  },
  {
    id: 6,
    title: "Delta Neutral",
    description: "Make Delta exactly 0.50 (±0.01) - perfect ATM",
    target: { greek: 'delta', value: 0.50, tolerance: 0.01 },
    hint: "Tip: Spot should equal Strike for ATM",
    difficulty: "Medium"
  },
  {
    id: 7,
    title: "Time Decay Expert",
    description: "Set Theta between -5 and -7 per day",
    target: { greek: 'theta', min: -7, max: -5 },
    hint: "Tip: Balance time to expiry and moneyness",
    difficulty: "Hard"
  },
  {
    id: 8,
    title: "Low Vega",
    description: "Reduce Vega below 20",
    target: { greek: 'vega', value: 20, tolerance: 0, comparison: 'lte' },
    hint: "Tip: Short-dated or deep ITM/OTM options have lower Vega",
    difficulty: "Medium"
  },
];

const GREEK_INFO = {
  delta: {
    name: 'Delta (Δ)',
    description: 'Measures how much the option price changes when spot moves ₹1',
    range: 'Call: 0 to 1 | Put: -1 to 0',
    color: '#22c55e'
  },
  gamma: {
    name: 'Gamma (Γ)',
    description: 'Rate of change of Delta - highest for ATM options near expiry',
    range: 'Always positive, peaks at ATM',
    color: '#f59e0b'
  },
  theta: {
    name: 'Theta (Θ)',
    description: 'Daily time decay - how much value the option loses per day',
    range: 'Usually negative (time works against buyers)',
    color: '#ef4444'
  },
  vega: {
    name: 'Vega (ν)',
    description: 'Sensitivity to 1% change in implied volatility',
    range: 'Always positive, higher for longer-dated options',
    color: '#8b5cf6'
  },
  rho: {
    name: 'Rho (ρ)',
    description: 'Sensitivity to 1% change in interest rate',
    range: 'Positive for calls, negative for puts',
    color: '#06b6d4'
  }
};

export default function GreeksArena() {
  const { t } = useTranslation();
  const [mode, setMode] = useState('sandbox'); // sandbox, challenge
  const [currentChallenge, setCurrentChallenge] = useState(0);
  const [completedChallenges, setCompletedChallenges] = useState([]);
  const [showSuccess, setShowSuccess] = useState(false);

  // Option parameters
  const [spot, setSpot] = useState(25800);
  const [strike, setStrike] = useState(25800);
  const [daysToExpiry, setDaysToExpiry] = useState(7);
  const [volatility, setVolatility] = useState(15);
  const [interestRate, setInterestRate] = useState(7);
  const [optionType, setOptionType] = useState('call');

  // Calculated values
  const [greeks, setGreeks] = useState({});
  const [priceHistory, setPriceHistory] = useState([]);

  // Calculate Greeks whenever parameters change
  useEffect(() => {
    const time = daysToExpiry / 365;
    const result = calculateBS(spot, strike, time, interestRate / 100, volatility / 100, optionType);
    setGreeks(result);

    // Track price history for mini chart
    setPriceHistory(prev => {
      const newHistory = [...prev, result.price];
      return newHistory.slice(-20);
    });
  }, [spot, strike, daysToExpiry, volatility, interestRate, optionType]);

  // Check challenge completion
  useEffect(() => {
    if (mode !== 'challenge') return;

    const challenge = CHALLENGES[currentChallenge];
    if (!challenge) return;

    const value = greeks[challenge.target.greek];
    if (value === undefined) return;

    let success = false;

    if (challenge.target.min !== undefined && challenge.target.max !== undefined) {
      success = value >= challenge.target.min && value <= challenge.target.max;
    } else if (challenge.target.comparison === 'gte') {
      success = value >= challenge.target.value;
    } else if (challenge.target.comparison === 'lte') {
      success = value <= challenge.target.value;
    } else {
      success = Math.abs(value - challenge.target.value) <= challenge.target.tolerance;
    }

    if (success && !completedChallenges.includes(challenge.id)) {
      setShowSuccess(true);
      setCompletedChallenges([...completedChallenges, challenge.id]);
      setTimeout(() => setShowSuccess(false), 2000);
    }
  }, [greeks, currentChallenge, mode, completedChallenges]);

  const resetParameters = () => {
    setSpot(25800);
    setStrike(25800);
    setDaysToExpiry(7);
    setVolatility(15);
    setInterestRate(7);
    setPriceHistory([]);
  };

  const formatGreek = (value, greek) => {
    if (greek === 'price') return `₹${value.toFixed(2)}`;
    if (greek === 'delta') return value.toFixed(3);
    if (greek === 'gamma') return value.toFixed(5);
    if (greek === 'theta') return `₹${value.toFixed(2)}/day`;
    if (greek === 'vega') return value.toFixed(2);
    if (greek === 'rho') return value.toFixed(3);
    return value.toFixed(2);
  };

  const challenge = CHALLENGES[currentChallenge];

  return (
    <div className="greeks-arena">
      {/* Success Animation */}
      {showSuccess && (
        <div className="success-overlay">
          <div className="success-content">
            <span className="success-icon">🎉</span>
            <h2>Challenge Complete!</h2>
          </div>
        </div>
      )}

      <div className="arena-header">
        <h1>🎮 Greeks Arena</h1>
        <p>Learn how Greeks affect option prices through interactive exploration</p>

        <div className="mode-toggle">
          <button
            className={mode === 'sandbox' ? 'active' : ''}
            onClick={() => setMode('sandbox')}
          >
            🧪 Sandbox
          </button>
          <button
            className={mode === 'challenge' ? 'active' : ''}
            onClick={() => setMode('challenge')}
          >
            🎯 Challenges
          </button>
        </div>
      </div>

      {mode === 'challenge' && challenge && (
        <div className="challenge-banner">
          <div className="challenge-info">
            <span className={`difficulty ${challenge.difficulty.toLowerCase()}`}>
              {challenge.difficulty}
            </span>
            <h3>{challenge.title}</h3>
            <p>{challenge.description}</p>
            <p className="hint">{challenge.hint}</p>
          </div>
          <div className="challenge-progress">
            <span>{completedChallenges.length}/{CHALLENGES.length} Completed</span>
            <div className="challenge-nav">
              <button
                disabled={currentChallenge === 0}
                onClick={() => setCurrentChallenge(currentChallenge - 1)}
              >
                ←
              </button>
              <span>{currentChallenge + 1}/{CHALLENGES.length}</span>
              <button
                disabled={currentChallenge === CHALLENGES.length - 1}
                onClick={() => setCurrentChallenge(currentChallenge + 1)}
              >
                →
              </button>
            </div>
          </div>
          {completedChallenges.includes(challenge.id) && (
            <div className="completed-badge">✓ Completed</div>
          )}
        </div>
      )}

      <div className="arena-content">
        {/* Controls Panel */}
        <div className="controls-panel">
          <div className="control-section">
            <h3>Option Parameters</h3>

            <div className="option-type-toggle">
              <button
                className={optionType === 'call' ? 'active call' : ''}
                onClick={() => setOptionType('call')}
              >
                📈 Call
              </button>
              <button
                className={optionType === 'put' ? 'active put' : ''}
                onClick={() => setOptionType('put')}
              >
                📉 Put
              </button>
            </div>

            <div className="slider-control">
              <div className="slider-header">
                <label>Spot Price</label>
                <span className="slider-value">₹{spot.toLocaleString()}</span>
              </div>
              <input
                type="range"
                min={24000}
                max={28000}
                step={50}
                value={spot}
                onChange={(e) => setSpot(Number(e.target.value))}
              />
              <div className="slider-range">
                <span>₹24,000</span>
                <span>₹28,000</span>
              </div>
            </div>

            <div className="slider-control">
              <div className="slider-header">
                <label>Strike Price</label>
                <span className="slider-value">₹{strike.toLocaleString()}</span>
              </div>
              <input
                type="range"
                min={24000}
                max={28000}
                step={50}
                value={strike}
                onChange={(e) => setStrike(Number(e.target.value))}
              />
              <div className="slider-range">
                <span>₹24,000</span>
                <span>₹28,000</span>
              </div>
            </div>

            <div className="slider-control">
              <div className="slider-header">
                <label>Days to Expiry</label>
                <span className="slider-value">{daysToExpiry} days</span>
              </div>
              <input
                type="range"
                min={1}
                max={90}
                value={daysToExpiry}
                onChange={(e) => setDaysToExpiry(Number(e.target.value))}
              />
              <div className="slider-range">
                <span>1 day</span>
                <span>90 days</span>
              </div>
            </div>

            <div className="slider-control">
              <div className="slider-header">
                <label>Implied Volatility</label>
                <span className="slider-value">{volatility}%</span>
              </div>
              <input
                type="range"
                min={5}
                max={50}
                value={volatility}
                onChange={(e) => setVolatility(Number(e.target.value))}
              />
              <div className="slider-range">
                <span>5%</span>
                <span>50%</span>
              </div>
            </div>

            <button className="reset-btn" onClick={resetParameters}>
              🔄 Reset Parameters
            </button>
          </div>
        </div>

        {/* Greeks Display */}
        <div className="greeks-panel">
          <div className="price-display">
            <div className="price-main">
              <span className="price-label">Option Price</span>
              <span className="price-value">₹{greeks.price?.toFixed(2) || '0.00'}</span>
            </div>
            <div className="moneyness">
              {spot > strike ? (
                <span className="itm">{optionType === 'call' ? 'ITM' : 'OTM'}</span>
              ) : spot < strike ? (
                <span className="otm">{optionType === 'call' ? 'OTM' : 'ITM'}</span>
              ) : (
                <span className="atm">ATM</span>
              )}
              <span className="moneyness-value">
                {Math.abs(spot - strike).toLocaleString()} {spot > strike ? 'above' : spot < strike ? 'below' : 'at'} strike
              </span>
            </div>
          </div>

          <div className="greeks-grid">
            {Object.entries(GREEK_INFO).map(([key, info]) => (
              <div
                key={key}
                className={`greek-card ${mode === 'challenge' && challenge?.target.greek === key ? 'highlighted' : ''}`}
                style={{ '--greek-color': info.color }}
              >
                <div className="greek-header">
                  <span className="greek-name">{info.name}</span>
                  <span className="greek-value" style={{ color: info.color }}>
                    {formatGreek(greeks[key] || 0, key)}
                  </span>
                </div>
                <p className="greek-desc">{info.description}</p>
                <div className="greek-bar">
                  <div
                    className="greek-fill"
                    style={{
                      width: `${Math.min(Math.abs(greeks[key] || 0) * (key === 'delta' ? 100 : key === 'gamma' ? 10000 : key === 'vega' ? 0.5 : 5), 100)}%`,
                      backgroundColor: info.color
                    }}
                  />
                </div>
                <span className="greek-range">{info.range}</span>
              </div>
            ))}
          </div>

          {/* Quick Experiments */}
          <div className="experiments">
            <h3>Quick Experiments</h3>
            <div className="experiment-buttons">
              <button onClick={() => { setSpot(strike + 500); }}>
                Make ITM (+500)
              </button>
              <button onClick={() => { setSpot(strike - 500); }}>
                Make OTM (-500)
              </button>
              <button onClick={() => { setDaysToExpiry(1); }}>
                1 Day to Expiry
              </button>
              <button onClick={() => { setVolatility(30); }}>
                High IV (30%)
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Learning Tips */}
      <div className="learning-tips">
        <h3>💡 Key Insights</h3>
        <div className="tips-grid">
          <div className="tip-card">
            <span className="tip-icon" style={{ color: '#22c55e' }}>Δ</span>
            <p><strong>Delta</strong> approaches 1 as options go deep ITM, and 0 as they go OTM</p>
          </div>
          <div className="tip-card">
            <span className="tip-icon" style={{ color: '#f59e0b' }}>Γ</span>
            <p><strong>Gamma</strong> is highest for ATM options near expiry - small moves cause big Delta changes</p>
          </div>
          <div className="tip-card">
            <span className="tip-icon" style={{ color: '#ef4444' }}>Θ</span>
            <p><strong>Theta</strong> accelerates near expiry - time decay is non-linear!</p>
          </div>
          <div className="tip-card">
            <span className="tip-icon" style={{ color: '#8b5cf6' }}>ν</span>
            <p><strong>Vega</strong> is higher for longer-dated options - they're more sensitive to IV changes</p>
          </div>
        </div>
      </div>
    </div>
  );
}
