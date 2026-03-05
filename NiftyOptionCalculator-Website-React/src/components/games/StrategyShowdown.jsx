import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import './StrategyShowdown.css';

const SCENARIOS = [
  {
    id: 1,
    title: "Bullish Breakout Expected",
    description: "NIFTY is consolidating near 25,800. Strong support at 25,700. FII data shows heavy call buying. RSI at 55 with bullish divergence. You expect a 2-3% upward move in the next week.",
    marketData: { spot: 25800, iv: 14, trend: "Bullish", timeframe: "1 Week" },
    options: [
      { id: "long_call", name: "Long Call", correct: true },
      { id: "long_put", name: "Long Put", correct: false },
      { id: "short_straddle", name: "Short Straddle", correct: false },
      { id: "iron_condor", name: "Iron Condor", correct: false }
    ],
    explanation: "Long Call is ideal when you're bullish. It offers unlimited profit potential with limited risk (premium paid). Since you expect a directional move, strategies like Iron Condor (neutral) or Short Straddle (high risk) are not suitable.",
    difficulty: "Beginner"
  },
  {
    id: 2,
    title: "Earnings Volatility Play",
    description: "Reliance results tomorrow. IV has spiked to 35% from usual 18%. You expect a big move but unsure of direction. Historical moves post-earnings: +/- 4% on average.",
    marketData: { spot: 2950, iv: 35, trend: "Uncertain", timeframe: "1 Day" },
    options: [
      { id: "long_straddle", name: "Long Straddle", correct: true },
      { id: "short_straddle", name: "Short Straddle", correct: false },
      { id: "covered_call", name: "Covered Call", correct: false },
      { id: "long_call", name: "Long Call", correct: false }
    ],
    explanation: "Long Straddle profits from big moves in either direction. You buy both ATM Call and Put. Even though IV is high (expensive), the expected 4% move can overcome the premium if realized. Short Straddle would be crushed by the big move.",
    difficulty: "Intermediate"
  },
  {
    id: 3,
    title: "Range-Bound Market",
    description: "BANKNIFTY stuck between 54,000-55,000 for 2 weeks. Low volatility, IV at 12%. No major events. PCR stable at 1.1. You expect this consolidation to continue for another week.",
    marketData: { spot: 54500, iv: 12, trend: "Sideways", timeframe: "1 Week" },
    options: [
      { id: "iron_condor", name: "Iron Condor", correct: true },
      { id: "long_straddle", name: "Long Straddle", correct: false },
      { id: "long_call", name: "Long Call", correct: false },
      { id: "long_put", name: "Long Put", correct: false }
    ],
    explanation: "Iron Condor is perfect for range-bound markets. You sell OTM Call and Put spreads, collecting premium. As long as BANKNIFTY stays in the range, you profit from time decay. Directional strategies (Long Call/Put) would lose in a sideways market.",
    difficulty: "Intermediate"
  },
  {
    id: 4,
    title: "Bearish with Limited Risk",
    description: "Global markets weak. NIFTY broke below 25,500 support. FII selling heavily. You're bearish but want to limit your risk to a fixed amount.",
    marketData: { spot: 25400, iv: 18, trend: "Bearish", timeframe: "2 Weeks" },
    options: [
      { id: "bear_put_spread", name: "Bear Put Spread", correct: true },
      { id: "naked_short_call", name: "Naked Short Call", correct: false },
      { id: "long_call", name: "Long Call", correct: false },
      { id: "short_straddle", name: "Short Straddle", correct: false }
    ],
    explanation: "Bear Put Spread (buy higher strike Put, sell lower strike Put) gives bearish exposure with defined risk. Naked Short Call has unlimited risk. The spread reduces cost compared to buying a Put outright while capping your maximum loss.",
    difficulty: "Intermediate"
  },
  {
    id: 5,
    title: "IV Crush Opportunity",
    description: "Budget day tomorrow. IV has exploded to 28%. You believe the event will be a non-event and IV will collapse post-budget regardless of direction.",
    marketData: { spot: 25800, iv: 28, trend: "Neutral", timeframe: "1 Day" },
    options: [
      { id: "short_straddle", name: "Short Straddle", correct: true },
      { id: "long_straddle", name: "Long Straddle", correct: false },
      { id: "long_call", name: "Long Call", correct: false },
      { id: "calendar_spread", name: "Calendar Spread", correct: false }
    ],
    explanation: "Short Straddle profits from IV crush. When IV drops from 28% to normal levels (~15%), option prices collapse. You profit even if there's a small move, as the IV drop benefit exceeds the directional loss. This is a high-risk strategy requiring proper position sizing.",
    difficulty: "Advanced"
  },
  {
    id: 6,
    title: "Protect Your Portfolio",
    description: "You hold ₹50L in NIFTY stocks. Markets at all-time high. You're worried about a 10% correction but don't want to sell your holdings. Budget: ₹50,000 for protection.",
    marketData: { spot: 25800, iv: 14, trend: "Cautious", timeframe: "1 Month" },
    options: [
      { id: "protective_put", name: "Protective Put", correct: true },
      { id: "covered_call", name: "Covered Call", correct: false },
      { id: "long_call", name: "Long Call", correct: false },
      { id: "short_put", name: "Short Put", correct: false }
    ],
    explanation: "Protective Put (buying Put options) acts as insurance for your portfolio. If markets crash, your Put gains offset stock losses. Covered Call provides income but doesn't protect against downside. It's like buying insurance - you pay a premium for peace of mind.",
    difficulty: "Beginner"
  },
  {
    id: 7,
    title: "Mild Bullish, Generate Income",
    description: "You own 500 shares of Infosys at ₹1,800. Stock has been flat. You're mildly bullish but want to generate some income while holding. Willing to sell at ₹1,900.",
    marketData: { spot: 1800, iv: 20, trend: "Mild Bullish", timeframe: "1 Month" },
    options: [
      { id: "covered_call", name: "Covered Call", correct: true },
      { id: "protective_put", name: "Protective Put", correct: false },
      { id: "long_straddle", name: "Long Straddle", correct: false },
      { id: "naked_call", name: "Naked Short Call", correct: false }
    ],
    explanation: "Covered Call: Sell 1900 CE against your shares. You collect premium as income. If stock stays below 1900, you keep premium + shares. If it goes above 1900, you sell shares at 1900 + keep premium. Win-win for mild bullish view.",
    difficulty: "Beginner"
  },
  {
    id: 8,
    title: "High Conviction Directional",
    description: "RBI policy announcement in 2 hours. Based on your analysis, you're 90% confident of a rate cut. Markets will rally 3%+ on this news. You want maximum leverage.",
    marketData: { spot: 25800, iv: 22, trend: "Very Bullish", timeframe: "Intraday" },
    options: [
      { id: "otm_call", name: "OTM Call (High Leverage)", correct: true },
      { id: "atm_call", name: "ATM Call", correct: false },
      { id: "bull_call_spread", name: "Bull Call Spread", correct: false },
      { id: "iron_condor", name: "Iron Condor", correct: false }
    ],
    explanation: "OTM Calls provide maximum leverage for high-conviction directional bets. A 3% move can turn a cheap OTM option into a multi-bagger. ATM Call is safer but less leveraged. Spreads cap your upside. High risk, high reward - only with strong conviction.",
    difficulty: "Advanced"
  },
  {
    id: 9,
    title: "Slow Theta Decay Play",
    description: "It's Monday, weekly expiry on Thursday. NIFTY opened flat at 25,800. VIX low at 11. No events this week. You expect a boring, range-bound week.",
    marketData: { spot: 25800, iv: 11, trend: "Neutral", timeframe: "4 Days" },
    options: [
      { id: "short_strangle", name: "Short Strangle", correct: true },
      { id: "long_straddle", name: "Long Straddle", correct: false },
      { id: "long_call", name: "Long Call", correct: false },
      { id: "calendar_spread", name: "Calendar Spread", correct: false }
    ],
    explanation: "Short Strangle (sell OTM Call + OTM Put) profits from theta decay in a boring market. Low VIX means options are cheap to sell, but theta decay is your friend. Keep strikes wide enough to survive small moves. Close before expiry to avoid gamma risk.",
    difficulty: "Advanced"
  },
  {
    id: 10,
    title: "Volatility Expansion Expected",
    description: "VIX at historic lows (10). Election results in 2 weeks. You expect volatility to spike significantly but unsure of market direction post-results.",
    marketData: { spot: 25800, iv: 10, trend: "Uncertain", timeframe: "2 Weeks" },
    options: [
      { id: "long_strangle", name: "Long Strangle", correct: true },
      { id: "short_strangle", name: "Short Strangle", correct: false },
      { id: "iron_butterfly", name: "Iron Butterfly", correct: false },
      { id: "covered_call", name: "Covered Call", correct: false }
    ],
    explanation: "Long Strangle (buy OTM Call + OTM Put) profits from volatility expansion. When VIX is at lows, options are cheap. As election approaches, IV will spike, increasing your option values even before results. You profit from both IV expansion and any big move.",
    difficulty: "Advanced"
  }
];

const BADGES = [
  { id: 'first_win', name: 'First Victory', description: 'Get your first correct answer', icon: '🏆', requirement: 1 },
  { id: 'streak_3', name: 'Hot Streak', description: '3 correct answers in a row', icon: '🔥', requirement: 3 },
  { id: 'streak_5', name: 'On Fire', description: '5 correct answers in a row', icon: '💥', requirement: 5 },
  { id: 'perfect_round', name: 'Perfect Round', description: 'Complete a round with 100% accuracy', icon: '⭐', requirement: 'perfect' },
  { id: 'beginner_master', name: 'Beginner Master', description: 'Complete all beginner scenarios', icon: '🎓', requirement: 'beginner' },
  { id: 'advanced_trader', name: 'Advanced Trader', description: 'Complete all advanced scenarios', icon: '🚀', requirement: 'advanced' },
];

export default function StrategyShowdown() {
  const { t } = useTranslation();
  const [gameState, setGameState] = useState('menu'); // menu, playing, result, completed
  const [currentScenario, setCurrentScenario] = useState(0);
  const [selectedOption, setSelectedOption] = useState(null);
  const [showExplanation, setShowExplanation] = useState(false);
  const [score, setScore] = useState(0);
  const [streak, setStreak] = useState(0);
  const [maxStreak, setMaxStreak] = useState(0);
  const [answers, setAnswers] = useState([]);
  const [difficulty, setDifficulty] = useState('all');
  const [earnedBadges, setEarnedBadges] = useState([]);
  const [showBadgePopup, setShowBadgePopup] = useState(null);
  const [scenarios, setScenarios] = useState([]);
  const [timeLeft, setTimeLeft] = useState(30);
  const [timerActive, setTimerActive] = useState(false);

  useEffect(() => {
    // Load saved progress
    const saved = localStorage.getItem('strategyShowdown');
    if (saved) {
      const data = JSON.parse(saved);
      setEarnedBadges(data.badges || []);
      setMaxStreak(data.maxStreak || 0);
    }
  }, []);

  useEffect(() => {
    // Timer logic
    if (timerActive && timeLeft > 0) {
      const timer = setTimeout(() => setTimeLeft(timeLeft - 1), 1000);
      return () => clearTimeout(timer);
    } else if (timeLeft === 0 && timerActive) {
      handleTimeout();
    }
  }, [timeLeft, timerActive]);

  const filterScenarios = () => {
    if (difficulty === 'all') return [...SCENARIOS].sort(() => Math.random() - 0.5).slice(0, 5);
    return SCENARIOS.filter(s => s.difficulty.toLowerCase() === difficulty).sort(() => Math.random() - 0.5);
  };

  const startGame = () => {
    const filtered = filterScenarios();
    setScenarios(filtered);
    setCurrentScenario(0);
    setScore(0);
    setStreak(0);
    setAnswers([]);
    setSelectedOption(null);
    setShowExplanation(false);
    setGameState('playing');
    setTimeLeft(30);
    setTimerActive(true);
  };

  const handleTimeout = () => {
    setTimerActive(false);
    setStreak(0);
    setAnswers([...answers, { correct: false, timeout: true }]);
    setShowExplanation(true);
  };

  const handleOptionSelect = (option) => {
    if (showExplanation) return;
    setSelectedOption(option);
  };

  const handleSubmit = () => {
    if (!selectedOption) return;
    setTimerActive(false);

    const correct = selectedOption.correct;
    const newAnswers = [...answers, { correct, scenarioId: scenarios[currentScenario].id }];
    setAnswers(newAnswers);

    if (correct) {
      const timeBonus = Math.floor(timeLeft / 3);
      const streakBonus = streak * 10;
      const newScore = score + 100 + timeBonus + streakBonus;
      setScore(newScore);
      setStreak(streak + 1);
      if (streak + 1 > maxStreak) {
        setMaxStreak(streak + 1);
        saveProgress(streak + 1);
      }
      checkBadges(streak + 1, newAnswers);
    } else {
      setStreak(0);
    }

    setShowExplanation(true);
  };

  const checkBadges = (currentStreak, currentAnswers) => {
    const newBadges = [...earnedBadges];

    // First win
    if (!earnedBadges.includes('first_win') && currentAnswers.filter(a => a.correct).length >= 1) {
      newBadges.push('first_win');
      setShowBadgePopup(BADGES.find(b => b.id === 'first_win'));
    }

    // Streaks
    if (!earnedBadges.includes('streak_3') && currentStreak >= 3) {
      newBadges.push('streak_3');
      setShowBadgePopup(BADGES.find(b => b.id === 'streak_3'));
    }
    if (!earnedBadges.includes('streak_5') && currentStreak >= 5) {
      newBadges.push('streak_5');
      setShowBadgePopup(BADGES.find(b => b.id === 'streak_5'));
    }

    if (newBadges.length !== earnedBadges.length) {
      setEarnedBadges(newBadges);
      saveProgress(maxStreak, newBadges);
    }
  };

  const saveProgress = (streak, badges = earnedBadges) => {
    localStorage.setItem('strategyShowdown', JSON.stringify({
      maxStreak: streak,
      badges: badges
    }));
  };

  const nextScenario = () => {
    if (currentScenario + 1 >= scenarios.length) {
      // Check for perfect round
      const allCorrect = answers.every(a => a.correct);
      if (allCorrect && !earnedBadges.includes('perfect_round')) {
        const newBadges = [...earnedBadges, 'perfect_round'];
        setEarnedBadges(newBadges);
        setShowBadgePopup(BADGES.find(b => b.id === 'perfect_round'));
        saveProgress(maxStreak, newBadges);
      }
      setGameState('completed');
    } else {
      setCurrentScenario(currentScenario + 1);
      setSelectedOption(null);
      setShowExplanation(false);
      setTimeLeft(30);
      setTimerActive(true);
    }
  };

  const scenario = scenarios[currentScenario];

  return (
    <div className="strategy-showdown">
      {/* Badge Popup */}
      {showBadgePopup && (
        <div className="badge-popup-overlay" onClick={() => setShowBadgePopup(null)}>
          <div className="badge-popup" onClick={e => e.stopPropagation()}>
            <div className="badge-icon-large">{showBadgePopup.icon}</div>
            <h3>Badge Earned!</h3>
            <p className="badge-name">{showBadgePopup.name}</p>
            <p className="badge-desc">{showBadgePopup.description}</p>
            <button onClick={() => setShowBadgePopup(null)}>Awesome!</button>
          </div>
        </div>
      )}

      {gameState === 'menu' && (
        <div className="game-menu">
          <div className="game-header">
            <h1>🎯 Strategy Showdown</h1>
            <p>Test your options strategy knowledge with real market scenarios</p>
          </div>

          <div className="difficulty-select">
            <h3>Select Difficulty</h3>
            <div className="difficulty-buttons">
              <button
                className={difficulty === 'all' ? 'active' : ''}
                onClick={() => setDifficulty('all')}
              >
                🎲 Mixed (5 Random)
              </button>
              <button
                className={difficulty === 'beginner' ? 'active' : ''}
                onClick={() => setDifficulty('beginner')}
              >
                🌱 Beginner
              </button>
              <button
                className={difficulty === 'intermediate' ? 'active' : ''}
                onClick={() => setDifficulty('intermediate')}
              >
                📈 Intermediate
              </button>
              <button
                className={difficulty === 'advanced' ? 'active' : ''}
                onClick={() => setDifficulty('advanced')}
              >
                🚀 Advanced
              </button>
            </div>
          </div>

          <div className="stats-preview">
            <div className="stat-item">
              <span className="stat-value">🔥 {maxStreak}</span>
              <span className="stat-label">Best Streak</span>
            </div>
            <div className="stat-item">
              <span className="stat-value">🏆 {earnedBadges.length}/{BADGES.length}</span>
              <span className="stat-label">Badges</span>
            </div>
          </div>

          <button className="start-button" onClick={startGame}>
            Start Game
          </button>

          <div className="badges-section">
            <h3>Badges</h3>
            <div className="badges-grid">
              {BADGES.map(badge => (
                <div
                  key={badge.id}
                  className={`badge-item ${earnedBadges.includes(badge.id) ? 'earned' : 'locked'}`}
                  title={badge.description}
                >
                  <span className="badge-icon">{earnedBadges.includes(badge.id) ? badge.icon : '🔒'}</span>
                  <span className="badge-name">{badge.name}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {gameState === 'playing' && scenario && (
        <div className="game-play">
          <div className="game-hud">
            <div className="hud-left">
              <span className="scenario-count">
                {currentScenario + 1} / {scenarios.length}
              </span>
              <span className={`difficulty-badge ${scenario.difficulty.toLowerCase()}`}>
                {scenario.difficulty}
              </span>
            </div>
            <div className="hud-center">
              <div className={`timer ${timeLeft <= 10 ? 'warning' : ''}`}>
                ⏱️ {timeLeft}s
              </div>
            </div>
            <div className="hud-right">
              <span className="score">Score: {score}</span>
              <span className="streak">🔥 {streak}</span>
            </div>
          </div>

          <div className="scenario-card">
            <h2>{scenario.title}</h2>
            <p className="scenario-description">{scenario.description}</p>

            <div className="market-data">
              <div className="data-item">
                <span className="data-label">Spot</span>
                <span className="data-value">{scenario.marketData.spot.toLocaleString()}</span>
              </div>
              <div className="data-item">
                <span className="data-label">IV</span>
                <span className="data-value">{scenario.marketData.iv}%</span>
              </div>
              <div className="data-item">
                <span className="data-label">Outlook</span>
                <span className={`data-value trend-${scenario.marketData.trend.toLowerCase().replace(' ', '-')}`}>
                  {scenario.marketData.trend}
                </span>
              </div>
              <div className="data-item">
                <span className="data-label">Timeframe</span>
                <span className="data-value">{scenario.marketData.timeframe}</span>
              </div>
            </div>
          </div>

          <div className="options-section">
            <h3>Choose the Best Strategy:</h3>
            <div className="options-grid">
              {scenario.options.map(option => (
                <button
                  key={option.id}
                  className={`option-btn ${selectedOption?.id === option.id ? 'selected' : ''} ${
                    showExplanation ? (option.correct ? 'correct' : selectedOption?.id === option.id ? 'incorrect' : '') : ''
                  }`}
                  onClick={() => handleOptionSelect(option)}
                  disabled={showExplanation}
                >
                  {option.name}
                  {showExplanation && option.correct && <span className="check">✓</span>}
                  {showExplanation && selectedOption?.id === option.id && !option.correct && <span className="cross">✗</span>}
                </button>
              ))}
            </div>
          </div>

          {!showExplanation && (
            <button
              className="submit-btn"
              onClick={handleSubmit}
              disabled={!selectedOption}
            >
              Submit Answer
            </button>
          )}

          {showExplanation && (
            <div className={`explanation ${selectedOption?.correct ? 'correct' : 'incorrect'}`}>
              <div className="explanation-header">
                {selectedOption?.correct ? (
                  <span className="result-icon">✅ Correct! +{100 + Math.floor(timeLeft/3) + streak * 10} points</span>
                ) : (
                  <span className="result-icon">❌ {answers[answers.length-1]?.timeout ? 'Time\'s Up!' : 'Incorrect'}</span>
                )}
              </div>
              <p>{scenario.explanation}</p>
              <button className="next-btn" onClick={nextScenario}>
                {currentScenario + 1 >= scenarios.length ? 'See Results' : 'Next Scenario →'}
              </button>
            </div>
          )}
        </div>
      )}

      {gameState === 'completed' && (
        <div className="game-completed">
          <h1>🎉 Game Complete!</h1>

          <div className="final-stats">
            <div className="big-score">
              <span className="score-value">{score}</span>
              <span className="score-label">Total Score</span>
            </div>

            <div className="stats-row">
              <div className="stat-box">
                <span className="stat-num">{answers.filter(a => a.correct).length}</span>
                <span className="stat-text">Correct</span>
              </div>
              <div className="stat-box">
                <span className="stat-num">{answers.filter(a => !a.correct).length}</span>
                <span className="stat-text">Incorrect</span>
              </div>
              <div className="stat-box">
                <span className="stat-num">{Math.round(answers.filter(a => a.correct).length / answers.length * 100)}%</span>
                <span className="stat-text">Accuracy</span>
              </div>
              <div className="stat-box">
                <span className="stat-num">🔥 {maxStreak}</span>
                <span className="stat-text">Best Streak</span>
              </div>
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
      )}
    </div>
  );
}
