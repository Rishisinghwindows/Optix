import React, { useState, useEffect, useCallback } from 'react';
import './PricePredictor.css';

// Black-Scholes calculation for reference price
const normalCDF = (x) => {
  const a1 = 0.254829592, a2 = -0.284496736, a3 = 1.421413741;
  const a4 = -1.453152027, a5 = 1.061405429, p = 0.3275911;
  const sign = x < 0 ? -1 : 1;
  x = Math.abs(x) / Math.sqrt(2);
  const t = 1.0 / (1.0 + p * x);
  const y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);
  return 0.5 * (1.0 + sign * y);
};

const calculateOptionPrice = (spot, strike, timeToExpiry, volatility, riskFreeRate, optionType) => {
  const T = timeToExpiry / 365;
  const d1 = (Math.log(spot / strike) + (riskFreeRate + 0.5 * volatility * volatility) * T) / (volatility * Math.sqrt(T));
  const d2 = d1 - volatility * Math.sqrt(T);

  if (optionType === 'call') {
    return spot * normalCDF(d1) - strike * Math.exp(-riskFreeRate * T) * normalCDF(d2);
  } else {
    return strike * Math.exp(-riskFreeRate * T) * normalCDF(-d2) - spot * normalCDF(-d1);
  }
};

// Generate random scenarios
const generateScenario = (difficulty) => {
  const baseSpot = [19500, 20000, 20500, 21000, 21500, 22000][Math.floor(Math.random() * 6)];

  let strikeOffset, ivRange, dteRange;

  switch (difficulty) {
    case 'easy':
      strikeOffset = [-100, 0, 100];
      ivRange = [12, 18];
      dteRange = [7, 30];
      break;
    case 'medium':
      strikeOffset = [-300, -200, -100, 0, 100, 200, 300];
      ivRange = [10, 25];
      dteRange = [3, 45];
      break;
    case 'hard':
      strikeOffset = [-500, -400, -300, -200, -100, 0, 100, 200, 300, 400, 500];
      ivRange = [8, 35];
      dteRange = [1, 60];
      break;
    default:
      strikeOffset = [-200, -100, 0, 100, 200];
      ivRange = [12, 20];
      dteRange = [5, 30];
  }

  const strike = baseSpot + strikeOffset[Math.floor(Math.random() * strikeOffset.length)];
  const iv = (Math.random() * (ivRange[1] - ivRange[0]) + ivRange[0]) / 100;
  const dte = Math.floor(Math.random() * (dteRange[1] - dteRange[0]) + dteRange[0]);
  const optionType = Math.random() > 0.5 ? 'call' : 'put';
  const riskFreeRate = 0.065; // 6.5% risk-free rate

  const actualPrice = calculateOptionPrice(baseSpot, strike, dte, iv, riskFreeRate, optionType);

  // Determine moneyness
  let moneyness;
  const diff = baseSpot - strike;
  if (optionType === 'call') {
    if (diff > 100) moneyness = 'ITM';
    else if (diff < -100) moneyness = 'OTM';
    else moneyness = 'ATM';
  } else {
    if (diff < -100) moneyness = 'ITM';
    else if (diff > 100) moneyness = 'OTM';
    else moneyness = 'ATM';
  }

  return {
    spot: baseSpot,
    strike,
    iv: iv * 100,
    dte,
    optionType,
    actualPrice: Math.max(0.05, actualPrice),
    moneyness,
    riskFreeRate: riskFreeRate * 100
  };
};

const DIFFICULTY_CONFIG = {
  easy: { label: 'Easy', rounds: 5, timePerRound: 30, tolerance: 20 },
  medium: { label: 'Medium', rounds: 8, timePerRound: 25, tolerance: 15 },
  hard: { label: 'Hard', rounds: 10, timePerRound: 20, tolerance: 10 }
};

const BADGES = [
  { id: 'first_guess', name: 'First Guess', icon: '🎯', description: 'Complete your first prediction' },
  { id: 'sharp_eye', name: 'Sharp Eye', icon: '👁️', description: 'Guess within 5% of actual price' },
  { id: 'speed_demon', name: 'Speed Demon', icon: '⚡', description: 'Answer in under 5 seconds' },
  { id: 'perfect_round', name: 'Perfect Round', icon: '💯', description: 'Get 100 points on a single guess' },
  { id: 'streak_master', name: 'Streak Master', icon: '🔥', description: 'Get 5 correct in a row' },
  { id: 'pricing_guru', name: 'Pricing Guru', icon: '🧙', description: 'Score over 800 points in a game' }
];

export default function PricePredictor() {
  const [gameState, setGameState] = useState('menu'); // menu, playing, result, completed
  const [difficulty, setDifficulty] = useState('easy');
  const [currentRound, setCurrentRound] = useState(0);
  const [scenario, setScenario] = useState(null);
  const [userGuess, setUserGuess] = useState('');
  const [timeLeft, setTimeLeft] = useState(30);
  const [score, setScore] = useState(0);
  const [roundResults, setRoundResults] = useState([]);
  const [streak, setStreak] = useState(0);
  const [bestStreak, setBestStreak] = useState(0);
  const [showResult, setShowResult] = useState(false);
  const [roundScore, setRoundScore] = useState(0);
  const [badges, setBadges] = useState(() => {
    const saved = localStorage.getItem('pricePredictorBadges');
    return saved ? JSON.parse(saved) : [];
  });
  const [newBadge, setNewBadge] = useState(null);
  const [stats, setStats] = useState(() => {
    const saved = localStorage.getItem('pricePredictorStats');
    return saved ? JSON.parse(saved) : { gamesPlayed: 0, totalScore: 0, bestScore: 0 };
  });

  // Save badges and stats
  useEffect(() => {
    localStorage.setItem('pricePredictorBadges', JSON.stringify(badges));
  }, [badges]);

  useEffect(() => {
    localStorage.setItem('pricePredictorStats', JSON.stringify(stats));
  }, [stats]);

  // Timer
  useEffect(() => {
    if (gameState !== 'playing' || showResult) return;

    if (timeLeft <= 0) {
      handleSubmitGuess();
      return;
    }

    const timer = setInterval(() => {
      setTimeLeft(prev => prev - 1);
    }, 1000);

    return () => clearInterval(timer);
  }, [gameState, timeLeft, showResult]);

  const checkBadge = useCallback((badgeId) => {
    if (!badges.includes(badgeId)) {
      setBadges(prev => [...prev, badgeId]);
      const badge = BADGES.find(b => b.id === badgeId);
      setNewBadge(badge);
      setTimeout(() => setNewBadge(null), 3000);
    }
  }, [badges]);

  const startGame = () => {
    setGameState('playing');
    setCurrentRound(1);
    setScore(0);
    setStreak(0);
    setRoundResults([]);
    const newScenario = generateScenario(difficulty);
    setScenario(newScenario);
    setTimeLeft(DIFFICULTY_CONFIG[difficulty].timePerRound);
    setUserGuess('');
    setShowResult(false);
  };

  const calculateScore = (guess, actual, timeRemaining) => {
    const percentError = Math.abs(guess - actual) / actual * 100;
    const tolerance = DIFFICULTY_CONFIG[difficulty].tolerance;

    let baseScore = 0;
    if (percentError <= 5) baseScore = 100;
    else if (percentError <= 10) baseScore = 80;
    else if (percentError <= tolerance) baseScore = 60;
    else if (percentError <= tolerance * 1.5) baseScore = 40;
    else if (percentError <= tolerance * 2) baseScore = 20;
    else baseScore = 0;

    // Time bonus (up to 20 points)
    const timeBonus = Math.floor((timeRemaining / DIFFICULTY_CONFIG[difficulty].timePerRound) * 20);

    // Streak bonus
    const streakBonus = Math.min(streak * 5, 30);

    return {
      baseScore,
      timeBonus,
      streakBonus,
      total: baseScore + timeBonus + streakBonus,
      percentError
    };
  };

  const handleSubmitGuess = () => {
    const guess = parseFloat(userGuess) || 0;
    const actual = scenario.actualPrice;
    const scoreResult = calculateScore(guess, actual, timeLeft);

    setRoundScore(scoreResult);
    setScore(prev => prev + scoreResult.total);
    setShowResult(true);

    const isCorrect = scoreResult.baseScore >= 60;

    // Update streak
    if (isCorrect) {
      const newStreak = streak + 1;
      setStreak(newStreak);
      if (newStreak > bestStreak) setBestStreak(newStreak);
      if (newStreak >= 5) checkBadge('streak_master');
    } else {
      setStreak(0);
    }

    // Check badges
    checkBadge('first_guess');
    if (scoreResult.percentError <= 5) checkBadge('sharp_eye');
    if (timeLeft >= DIFFICULTY_CONFIG[difficulty].timePerRound - 5) checkBadge('speed_demon');
    if (scoreResult.total >= 100) checkBadge('perfect_round');

    // Save round result
    setRoundResults(prev => [...prev, {
      round: currentRound,
      scenario,
      guess,
      actual,
      score: scoreResult.total,
      percentError: scoreResult.percentError
    }]);
  };

  const nextRound = () => {
    const totalRounds = DIFFICULTY_CONFIG[difficulty].rounds;

    if (currentRound >= totalRounds) {
      // Game completed
      const finalScore = score;
      if (finalScore >= 800) checkBadge('pricing_guru');

      setStats(prev => ({
        gamesPlayed: prev.gamesPlayed + 1,
        totalScore: prev.totalScore + finalScore,
        bestScore: Math.max(prev.bestScore, finalScore)
      }));

      setGameState('completed');
    } else {
      setCurrentRound(prev => prev + 1);
      setScenario(generateScenario(difficulty));
      setTimeLeft(DIFFICULTY_CONFIG[difficulty].timePerRound);
      setUserGuess('');
      setShowResult(false);
    }
  };

  const getAccuracyLabel = (percentError) => {
    if (percentError <= 5) return { text: 'Excellent!', class: 'excellent' };
    if (percentError <= 10) return { text: 'Great!', class: 'great' };
    if (percentError <= 20) return { text: 'Good', class: 'good' };
    if (percentError <= 30) return { text: 'Close', class: 'close' };
    return { text: 'Keep practicing', class: 'miss' };
  };

  // Render Menu
  if (gameState === 'menu') {
    return (
      <div className="price-predictor">
        <div className="game-menu">
          <div className="game-header">
            <h1>🔮 Price Predictor</h1>
            <p>Build your pricing intuition by guessing option prices</p>
          </div>

          <div className="difficulty-select">
            <h3>Select Difficulty</h3>
            <div className="difficulty-buttons">
              {Object.entries(DIFFICULTY_CONFIG).map(([key, config]) => (
                <button
                  key={key}
                  className={difficulty === key ? 'active' : ''}
                  onClick={() => setDifficulty(key)}
                >
                  <span className="diff-label">{config.label}</span>
                  <span className="diff-info">{config.rounds} rounds • {config.timePerRound}s each</span>
                </button>
              ))}
            </div>
          </div>

          <div className="stats-preview">
            <div className="stat-item">
              <span className="stat-value">{stats.gamesPlayed}</span>
              <span className="stat-label">Games Played</span>
            </div>
            <div className="stat-item">
              <span className="stat-value">{stats.bestScore}</span>
              <span className="stat-label">Best Score</span>
            </div>
            <div className="stat-item">
              <span className="stat-value">{badges.length}/{BADGES.length}</span>
              <span className="stat-label">Badges</span>
            </div>
          </div>

          <button className="start-button" onClick={startGame}>
            Start Game →
          </button>

          <div className="how-to-play">
            <h3>How to Play</h3>
            <ul>
              <li>You'll see option details: Spot price, Strike, IV, Days to expiry</li>
              <li>Guess the option premium before time runs out</li>
              <li>Closer guesses earn more points</li>
              <li>Build streaks for bonus points</li>
            </ul>
          </div>

          <div className="badges-section">
            <h3>Badges ({badges.length}/{BADGES.length})</h3>
            <div className="badges-grid">
              {BADGES.map(badge => (
                <div key={badge.id} className={`badge-item ${badges.includes(badge.id) ? 'earned' : 'locked'}`}>
                  <span className="badge-icon">{badges.includes(badge.id) ? badge.icon : '🔒'}</span>
                  <span className="badge-name">{badge.name}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {newBadge && (
          <div className="badge-popup-overlay" onClick={() => setNewBadge(null)}>
            <div className="badge-popup">
              <div className="badge-icon-large">{newBadge.icon}</div>
              <h3>Badge Earned!</h3>
              <div className="badge-name">{newBadge.name}</div>
              <div className="badge-desc">{newBadge.description}</div>
              <button onClick={() => setNewBadge(null)}>Awesome!</button>
            </div>
          </div>
        )}
      </div>
    );
  }

  // Render Game Play
  if (gameState === 'playing') {
    const totalRounds = DIFFICULTY_CONFIG[difficulty].rounds;

    return (
      <div className="price-predictor">
        <div className="game-play">
          <div className="game-hud">
            <div className="hud-left">
              <span className="round-count">Round {currentRound}/{totalRounds}</span>
              <span className={`difficulty-badge ${difficulty}`}>
                {DIFFICULTY_CONFIG[difficulty].label}
              </span>
            </div>
            <div className={`timer ${timeLeft <= 5 ? 'warning' : ''}`}>
              ⏱️ {timeLeft}s
            </div>
            <div className="hud-right">
              <span className="score">💰 {score}</span>
              {streak > 0 && <span className="streak">🔥 {streak}</span>}
            </div>
          </div>

          <div className="scenario-card">
            <div className="option-type-badge">
              <span className={`type ${scenario.optionType}`}>
                {scenario.optionType.toUpperCase()}
              </span>
              <span className={`moneyness ${scenario.moneyness.toLowerCase()}`}>
                {scenario.moneyness}
              </span>
            </div>

            <h2>Guess the Premium</h2>

            <div className="option-details">
              <div className="detail-row">
                <div className="detail-item">
                  <span className="detail-label">Nifty Spot</span>
                  <span className="detail-value">₹{scenario.spot.toLocaleString()}</span>
                </div>
                <div className="detail-item">
                  <span className="detail-label">Strike Price</span>
                  <span className="detail-value">₹{scenario.strike.toLocaleString()}</span>
                </div>
              </div>
              <div className="detail-row">
                <div className="detail-item">
                  <span className="detail-label">Implied Volatility</span>
                  <span className="detail-value">{scenario.iv.toFixed(1)}%</span>
                </div>
                <div className="detail-item">
                  <span className="detail-label">Days to Expiry</span>
                  <span className="detail-value">{scenario.dte} days</span>
                </div>
              </div>
            </div>

            {!showResult ? (
              <div className="guess-section">
                <div className="guess-input-wrapper">
                  <span className="currency-symbol">₹</span>
                  <input
                    type="number"
                    className="guess-input"
                    placeholder="Enter your guess"
                    value={userGuess}
                    onChange={(e) => setUserGuess(e.target.value)}
                    onKeyPress={(e) => e.key === 'Enter' && userGuess && handleSubmitGuess()}
                    autoFocus
                  />
                </div>
                <button
                  className="submit-btn"
                  onClick={handleSubmitGuess}
                  disabled={!userGuess}
                >
                  Submit Guess
                </button>
              </div>
            ) : (
              <div className="result-section">
                <div className={`accuracy-badge ${getAccuracyLabel(roundScore.percentError).class}`}>
                  {getAccuracyLabel(roundScore.percentError).text}
                </div>

                <div className="price-comparison">
                  <div className="price-item your-guess">
                    <span className="price-label">Your Guess</span>
                    <span className="price-value">₹{parseFloat(userGuess || 0).toFixed(2)}</span>
                  </div>
                  <div className="vs">vs</div>
                  <div className="price-item actual-price">
                    <span className="price-label">Actual Price</span>
                    <span className="price-value">₹{scenario.actualPrice.toFixed(2)}</span>
                  </div>
                </div>

                <div className="error-display">
                  Error: {roundScore.percentError.toFixed(1)}%
                </div>

                <div className="score-breakdown">
                  <div className="score-row">
                    <span>Base Score</span>
                    <span>+{roundScore.baseScore}</span>
                  </div>
                  <div className="score-row">
                    <span>Time Bonus</span>
                    <span>+{roundScore.timeBonus}</span>
                  </div>
                  {roundScore.streakBonus > 0 && (
                    <div className="score-row streak">
                      <span>Streak Bonus</span>
                      <span>+{roundScore.streakBonus}</span>
                    </div>
                  )}
                  <div className="score-row total">
                    <span>Round Total</span>
                    <span>+{roundScore.total}</span>
                  </div>
                </div>

                <button className="next-btn" onClick={nextRound}>
                  {currentRound >= totalRounds ? 'See Results' : 'Next Round →'}
                </button>
              </div>
            )}
          </div>

          <div className="pricing-hint">
            <h4>💡 Pricing Factors</h4>
            <ul>
              <li><strong>Intrinsic Value:</strong> {scenario.optionType === 'call'
                ? `Max(0, ${scenario.spot} - ${scenario.strike}) = ₹${Math.max(0, scenario.spot - scenario.strike)}`
                : `Max(0, ${scenario.strike} - ${scenario.spot}) = ₹${Math.max(0, scenario.strike - scenario.spot)}`
              }</li>
              <li><strong>Time Value:</strong> Higher with more DTE and higher IV</li>
              <li><strong>IV Effect:</strong> {scenario.iv > 20 ? 'High IV = Higher premiums' : scenario.iv < 12 ? 'Low IV = Lower premiums' : 'Moderate IV'}</li>
            </ul>
          </div>
        </div>

        {newBadge && (
          <div className="badge-popup-overlay" onClick={() => setNewBadge(null)}>
            <div className="badge-popup">
              <div className="badge-icon-large">{newBadge.icon}</div>
              <h3>Badge Earned!</h3>
              <div className="badge-name">{newBadge.name}</div>
              <div className="badge-desc">{newBadge.description}</div>
              <button onClick={() => setNewBadge(null)}>Awesome!</button>
            </div>
          </div>
        )}
      </div>
    );
  }

  // Render Game Completed
  if (gameState === 'completed') {
    const avgError = roundResults.reduce((sum, r) => sum + r.percentError, 0) / roundResults.length;
    const perfectRounds = roundResults.filter(r => r.score >= 100).length;

    return (
      <div className="price-predictor">
        <div className="game-completed">
          <h1>🎉 Game Complete!</h1>

          <div className="final-stats">
            <div className="big-score">
              <span className="score-value">{score}</span>
              <span className="score-label">Total Score</span>
            </div>

            <div className="stats-row">
              <div className="stat-box">
                <span className="stat-num">{avgError.toFixed(1)}%</span>
                <span className="stat-text">Avg Error</span>
              </div>
              <div className="stat-box">
                <span className="stat-num">{bestStreak}</span>
                <span className="stat-text">Best Streak</span>
              </div>
              <div className="stat-box">
                <span className="stat-num">{perfectRounds}</span>
                <span className="stat-text">Perfect</span>
              </div>
              <div className="stat-box">
                <span className="stat-num">{DIFFICULTY_CONFIG[difficulty].rounds}</span>
                <span className="stat-text">Rounds</span>
              </div>
            </div>
          </div>

          <div className="round-history">
            <h3>Round History</h3>
            <div className="history-list">
              {roundResults.map((result, idx) => (
                <div key={idx} className="history-item">
                  <span className="history-round">#{result.round}</span>
                  <span className="history-type">{result.scenario.optionType.toUpperCase()} {result.scenario.strike}</span>
                  <span className="history-guess">₹{result.guess.toFixed(2)}</span>
                  <span className="history-actual">₹{result.actual.toFixed(2)}</span>
                  <span className={`history-error ${result.percentError <= 10 ? 'good' : result.percentError <= 20 ? 'ok' : 'bad'}`}>
                    {result.percentError.toFixed(1)}%
                  </span>
                  <span className="history-score">+{result.score}</span>
                </div>
              ))}
            </div>
          </div>

          <div className="action-buttons">
            <button className="play-again-btn" onClick={startGame}>
              Play Again
            </button>
            <button className="menu-btn" onClick={() => setGameState('menu')}>
              Back to Menu
            </button>
          </div>
        </div>

        {newBadge && (
          <div className="badge-popup-overlay" onClick={() => setNewBadge(null)}>
            <div className="badge-popup">
              <div className="badge-icon-large">{newBadge.icon}</div>
              <h3>Badge Earned!</h3>
              <div className="badge-name">{newBadge.name}</div>
              <div className="badge-desc">{newBadge.description}</div>
              <button onClick={() => setNewBadge(null)}>Awesome!</button>
            </div>
          </div>
        )}
      </div>
    );
  }

  return null;
}
