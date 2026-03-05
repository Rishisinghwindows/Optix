import React, { useState, useEffect, useCallback } from 'react';
import './TradingTournament.css';

// Simulated market movement
const generateMarketMove = () => {
  const moves = [
    { change: -2.5, description: 'Major selloff on global cues', sentiment: 'bearish' },
    { change: -1.5, description: 'Profit booking pressure', sentiment: 'bearish' },
    { change: -0.8, description: 'Weak opening, consolidation', sentiment: 'slightly-bearish' },
    { change: -0.3, description: 'Sideways with negative bias', sentiment: 'neutral' },
    { change: 0, description: 'Range-bound session', sentiment: 'neutral' },
    { change: 0.3, description: 'Mild recovery in late trading', sentiment: 'neutral' },
    { change: 0.8, description: 'Positive momentum building', sentiment: 'slightly-bullish' },
    { change: 1.5, description: 'Strong buying interest', sentiment: 'bullish' },
    { change: 2.5, description: 'Rally on positive news', sentiment: 'bullish' },
  ];
  return moves[Math.floor(Math.random() * moves.length)];
};

// Generate option chain
const generateOptions = (spotPrice, dte) => {
  const strikes = [];
  const baseStrike = Math.round(spotPrice / 50) * 50;

  for (let i = -5; i <= 5; i++) {
    const strike = baseStrike + (i * 50);
    const iv = 15 + Math.random() * 10;

    // Simplified pricing
    const timeValue = Math.sqrt(dte / 365) * iv * spotPrice / 100;
    const callIntrinsic = Math.max(0, spotPrice - strike);
    const putIntrinsic = Math.max(0, strike - spotPrice);

    strikes.push({
      strike,
      callPrice: Math.max(0.5, callIntrinsic + timeValue * (1 + Math.random() * 0.2)),
      putPrice: Math.max(0.5, putIntrinsic + timeValue * (1 + Math.random() * 0.2)),
      callIV: iv + (strike > spotPrice ? 2 : -1),
      putIV: iv + (strike < spotPrice ? 2 : -1),
    });
  }

  return strikes;
};

// Calculate option price change based on market move
const calculateNewPrice = (oldPrice, spotChange, optionType, strike, currentSpot, dte) => {
  const delta = optionType === 'call'
    ? (currentSpot > strike ? 0.7 : 0.3)
    : (currentSpot < strike ? -0.7 : -0.3);

  const spotChangeAmount = currentSpot * (spotChange / 100);
  const priceChange = delta * spotChangeAmount;
  const timeDecay = oldPrice * (1 / dte) * 0.5;

  return Math.max(0.05, oldPrice + priceChange - timeDecay);
};

const TOURNAMENT_CONFIG = {
  rounds: 5,
  startingCapital: 100000,
  maxPositions: 3,
  lotSize: 50,
};

const LEADERBOARD_NAMES = [
  'TradingPro99', 'NiftyNinja', 'OptionsKing', 'BullishBear', 'DeltaHunter',
  'GammaGuru', 'ThetaTrader', 'VegaVictor', 'StrikeSeeker', 'PremiumPro'
];

const BADGES = [
  { id: 'first_trade', name: 'First Trade', icon: '📈', description: 'Execute your first trade' },
  { id: 'profitable', name: 'In the Green', icon: '💚', description: 'End a round with profit' },
  { id: 'big_winner', name: 'Big Winner', icon: '🎰', description: 'Make 20%+ return on a trade' },
  { id: 'risk_manager', name: 'Risk Manager', icon: '🛡️', description: 'Close a losing position early' },
  { id: 'tournament_winner', name: 'Champion', icon: '🏆', description: 'Finish #1 in tournament' },
  { id: 'consistent', name: 'Consistent', icon: '📊', description: 'Profit in 3+ consecutive rounds' }
];

export default function TradingTournament() {
  const [gameState, setGameState] = useState('menu'); // menu, playing, round-end, completed
  const [currentRound, setCurrentRound] = useState(0);
  const [capital, setCapital] = useState(TOURNAMENT_CONFIG.startingCapital);
  const [positions, setPositions] = useState([]);
  const [spotPrice, setSpotPrice] = useState(21500);
  const [dte, setDte] = useState(7);
  const [optionChain, setOptionChain] = useState([]);
  const [selectedOption, setSelectedOption] = useState(null);
  const [tradeType, setTradeType] = useState('buy');
  const [quantity, setQuantity] = useState(1);
  const [roundHistory, setRoundHistory] = useState([]);
  const [marketNews, setMarketNews] = useState(null);
  const [showTradeModal, setShowTradeModal] = useState(false);
  const [leaderboard, setLeaderboard] = useState([]);
  const [badges, setBadges] = useState(() => {
    const saved = localStorage.getItem('tournamentBadges');
    return saved ? JSON.parse(saved) : [];
  });
  const [newBadge, setNewBadge] = useState(null);
  const [stats, setStats] = useState(() => {
    const saved = localStorage.getItem('tournamentStats');
    return saved ? JSON.parse(saved) : { gamesPlayed: 0, wins: 0, bestReturn: 0 };
  });
  const [consecutiveProfits, setConsecutiveProfits] = useState(0);

  // Save data
  useEffect(() => {
    localStorage.setItem('tournamentBadges', JSON.stringify(badges));
  }, [badges]);

  useEffect(() => {
    localStorage.setItem('tournamentStats', JSON.stringify(stats));
  }, [stats]);

  const checkBadge = useCallback((badgeId) => {
    if (!badges.includes(badgeId)) {
      setBadges(prev => [...prev, badgeId]);
      const badge = BADGES.find(b => b.id === badgeId);
      setNewBadge(badge);
      setTimeout(() => setNewBadge(null), 3000);
    }
  }, [badges]);

  // Generate initial leaderboard
  const generateLeaderboard = () => {
    const bots = LEADERBOARD_NAMES.map(name => ({
      name,
      capital: TOURNAMENT_CONFIG.startingCapital,
      isPlayer: false
    }));
    return [
      { name: 'You', capital: TOURNAMENT_CONFIG.startingCapital, isPlayer: true },
      ...bots.slice(0, 9)
    ].sort((a, b) => b.capital - a.capital);
  };

  const startTournament = () => {
    const initialSpot = 21000 + Math.floor(Math.random() * 1000);
    setGameState('playing');
    setCurrentRound(1);
    setCapital(TOURNAMENT_CONFIG.startingCapital);
    setPositions([]);
    setSpotPrice(initialSpot);
    setDte(7);
    setOptionChain(generateOptions(initialSpot, 7));
    setRoundHistory([]);
    setMarketNews(null);
    setLeaderboard(generateLeaderboard());
    setConsecutiveProfits(0);
  };

  const openTradeModal = (option, type) => {
    setSelectedOption(option);
    setTradeType(type);
    setQuantity(1);
    setShowTradeModal(true);
  };

  const executeTrade = () => {
    if (!selectedOption) return;

    const price = tradeType === 'call' ? selectedOption.callPrice : selectedOption.putPrice;
    const totalCost = price * TOURNAMENT_CONFIG.lotSize * quantity;

    if (totalCost > capital) {
      alert('Insufficient capital!');
      return;
    }

    if (positions.length >= TOURNAMENT_CONFIG.maxPositions) {
      alert('Maximum positions reached! Close a position first.');
      return;
    }

    const newPosition = {
      id: Date.now(),
      strike: selectedOption.strike,
      type: tradeType,
      quantity,
      entryPrice: price,
      currentPrice: price,
      lots: quantity,
    };

    setPositions(prev => [...prev, newPosition]);
    setCapital(prev => prev - totalCost);
    setShowTradeModal(false);
    checkBadge('first_trade');
  };

  const closePosition = (positionId) => {
    const position = positions.find(p => p.id === positionId);
    if (!position) return;

    const exitValue = position.currentPrice * TOURNAMENT_CONFIG.lotSize * position.quantity;
    const entryValue = position.entryPrice * TOURNAMENT_CONFIG.lotSize * position.quantity;
    const pnl = exitValue - entryValue;
    const pnlPercent = (pnl / entryValue) * 100;

    setCapital(prev => prev + exitValue);
    setPositions(prev => prev.filter(p => p.id !== positionId));

    if (pnl < 0) {
      checkBadge('risk_manager');
    }
    if (pnlPercent >= 20) {
      checkBadge('big_winner');
    }
  };

  const simulateRound = () => {
    // Generate market move
    const move = generateMarketMove();
    setMarketNews(move);

    // Update spot price
    const newSpot = spotPrice * (1 + move.change / 100);
    setSpotPrice(newSpot);

    // Update option chain
    const newChain = generateOptions(newSpot, Math.max(1, dte - 1));
    setOptionChain(newChain);

    // Update positions
    const updatedPositions = positions.map(pos => {
      const chainOption = newChain.find(o => o.strike === pos.strike);
      if (chainOption) {
        const newPrice = pos.type === 'call' ? chainOption.callPrice : chainOption.putPrice;
        return { ...pos, currentPrice: newPrice };
      }
      return pos;
    });
    setPositions(updatedPositions);

    // Calculate round P&L
    const positionsPnL = updatedPositions.reduce((sum, pos) => {
      return sum + (pos.currentPrice - pos.entryPrice) * TOURNAMENT_CONFIG.lotSize * pos.quantity;
    }, 0);

    // Update leaderboard with simulated bot performance
    const updatedLeaderboard = leaderboard.map(entry => {
      if (entry.isPlayer) {
        const totalCapital = capital + updatedPositions.reduce(
          (sum, pos) => sum + pos.currentPrice * TOURNAMENT_CONFIG.lotSize * pos.quantity, 0
        );
        return { ...entry, capital: totalCapital };
      } else {
        // Simulate bot performance
        const botReturn = (Math.random() - 0.45) * 0.1; // Slight negative bias
        return { ...entry, capital: entry.capital * (1 + botReturn) };
      }
    }).sort((a, b) => b.capital - a.capital);
    setLeaderboard(updatedLeaderboard);

    // Check consecutive profits
    if (positionsPnL > 0) {
      const newConsecutive = consecutiveProfits + 1;
      setConsecutiveProfits(newConsecutive);
      checkBadge('profitable');
      if (newConsecutive >= 3) {
        checkBadge('consistent');
      }
    } else {
      setConsecutiveProfits(0);
    }

    // Save round history
    setRoundHistory(prev => [...prev, {
      round: currentRound,
      spotChange: move.change,
      description: move.description,
      pnl: positionsPnL,
      capital: capital + positionsPnL
    }]);

    setDte(prev => Math.max(1, prev - 1));
    setGameState('round-end');
  };

  const nextRound = () => {
    if (currentRound >= TOURNAMENT_CONFIG.rounds) {
      // Tournament completed
      const finalRank = leaderboard.findIndex(e => e.isPlayer) + 1;
      if (finalRank === 1) {
        checkBadge('tournament_winner');
      }

      const totalReturn = ((capital / TOURNAMENT_CONFIG.startingCapital) - 1) * 100;
      setStats(prev => ({
        gamesPlayed: prev.gamesPlayed + 1,
        wins: finalRank === 1 ? prev.wins + 1 : prev.wins,
        bestReturn: Math.max(prev.bestReturn, totalReturn)
      }));

      setGameState('completed');
    } else {
      setCurrentRound(prev => prev + 1);
      setGameState('playing');
      setMarketNews(null);
    }
  };

  // Calculate total portfolio value
  const portfolioValue = capital + positions.reduce(
    (sum, pos) => sum + pos.currentPrice * TOURNAMENT_CONFIG.lotSize * pos.quantity, 0
  );
  const totalReturn = ((portfolioValue / TOURNAMENT_CONFIG.startingCapital) - 1) * 100;

  // Render Menu
  if (gameState === 'menu') {
    return (
      <div className="trading-tournament">
        <div className="game-menu">
          <div className="game-header">
            <h1>🏆 Trading Tournament</h1>
            <p>Compete against AI traders in a {TOURNAMENT_CONFIG.rounds}-round options trading competition</p>
          </div>

          <div className="tournament-info">
            <div className="info-item">
              <span className="info-icon">💰</span>
              <span className="info-text">Starting Capital: ₹{TOURNAMENT_CONFIG.startingCapital.toLocaleString()}</span>
            </div>
            <div className="info-item">
              <span className="info-icon">🎯</span>
              <span className="info-text">{TOURNAMENT_CONFIG.rounds} Trading Rounds</span>
            </div>
            <div className="info-item">
              <span className="info-icon">📊</span>
              <span className="info-text">Max {TOURNAMENT_CONFIG.maxPositions} Positions</span>
            </div>
            <div className="info-item">
              <span className="info-icon">📦</span>
              <span className="info-text">Lot Size: {TOURNAMENT_CONFIG.lotSize}</span>
            </div>
          </div>

          <div className="stats-preview">
            <div className="stat-item">
              <span className="stat-value">{stats.gamesPlayed}</span>
              <span className="stat-label">Tournaments</span>
            </div>
            <div className="stat-item">
              <span className="stat-value">{stats.wins}</span>
              <span className="stat-label">Wins</span>
            </div>
            <div className="stat-item">
              <span className="stat-value">{stats.bestReturn.toFixed(1)}%</span>
              <span className="stat-label">Best Return</span>
            </div>
          </div>

          <button className="start-button" onClick={startTournament}>
            Enter Tournament →
          </button>

          <div className="badges-section">
            <h3>Achievements ({badges.length}/{BADGES.length})</h3>
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
              <h3>Achievement Unlocked!</h3>
              <div className="badge-name">{newBadge.name}</div>
              <div className="badge-desc">{newBadge.description}</div>
              <button onClick={() => setNewBadge(null)}>Awesome!</button>
            </div>
          </div>
        )}
      </div>
    );
  }

  // Render Trading Screen
  if (gameState === 'playing') {
    const playerRank = leaderboard.findIndex(e => e.isPlayer) + 1;

    return (
      <div className="trading-tournament">
        <div className="trading-screen">
          <div className="trading-hud">
            <div className="hud-section">
              <span className="round-badge">Round {currentRound}/{TOURNAMENT_CONFIG.rounds}</span>
              <span className="dte-badge">{dte} DTE</span>
            </div>
            <div className="hud-section center">
              <span className="spot-price">NIFTY: ₹{spotPrice.toFixed(0)}</span>
            </div>
            <div className="hud-section right">
              <span className="rank-badge">Rank #{playerRank}</span>
              <span className={`portfolio-value ${totalReturn >= 0 ? 'positive' : 'negative'}`}>
                ₹{portfolioValue.toFixed(0)} ({totalReturn >= 0 ? '+' : ''}{totalReturn.toFixed(1)}%)
              </span>
            </div>
          </div>

          <div className="trading-layout">
            <div className="main-panel">
              <div className="option-chain-card">
                <h3>Option Chain - NIFTY</h3>
                <div className="chain-header">
                  <span>CALLS</span>
                  <span>STRIKE</span>
                  <span>PUTS</span>
                </div>
                <div className="chain-body">
                  {optionChain.map((option, idx) => {
                    const isATM = Math.abs(option.strike - spotPrice) < 25;
                    return (
                      <div key={idx} className={`chain-row ${isATM ? 'atm' : ''}`}>
                        <div className="call-side" onClick={() => openTradeModal(option, 'call')}>
                          <span className="price">₹{option.callPrice.toFixed(2)}</span>
                          <span className="iv">{option.callIV.toFixed(1)}%</span>
                        </div>
                        <div className="strike-col">
                          <span className={isATM ? 'atm-strike' : ''}>{option.strike}</span>
                        </div>
                        <div className="put-side" onClick={() => openTradeModal(option, 'put')}>
                          <span className="price">₹{option.putPrice.toFixed(2)}</span>
                          <span className="iv">{option.putIV.toFixed(1)}%</span>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>

              <button className="simulate-btn" onClick={simulateRound}>
                Simulate Market Move →
              </button>
            </div>

            <div className="side-panel">
              <div className="capital-card">
                <h4>Available Capital</h4>
                <span className="capital-amount">₹{capital.toFixed(0)}</span>
              </div>

              <div className="positions-card">
                <h4>Your Positions ({positions.length}/{TOURNAMENT_CONFIG.maxPositions})</h4>
                {positions.length === 0 ? (
                  <p className="no-positions">No open positions. Click on the option chain to trade.</p>
                ) : (
                  <div className="positions-list">
                    {positions.map(pos => {
                      const pnl = (pos.currentPrice - pos.entryPrice) * TOURNAMENT_CONFIG.lotSize * pos.quantity;
                      const pnlPercent = ((pos.currentPrice - pos.entryPrice) / pos.entryPrice) * 100;
                      return (
                        <div key={pos.id} className="position-item">
                          <div className="position-info">
                            <span className={`position-type ${pos.type}`}>
                              {pos.type.toUpperCase()} {pos.strike}
                            </span>
                            <span className="position-qty">{pos.quantity} lot(s)</span>
                          </div>
                          <div className="position-prices">
                            <span className="entry">Entry: ₹{pos.entryPrice.toFixed(2)}</span>
                            <span className="current">Current: ₹{pos.currentPrice.toFixed(2)}</span>
                          </div>
                          <div className={`position-pnl ${pnl >= 0 ? 'profit' : 'loss'}`}>
                            {pnl >= 0 ? '+' : ''}₹{pnl.toFixed(0)} ({pnlPercent.toFixed(1)}%)
                          </div>
                          <button className="close-btn" onClick={() => closePosition(pos.id)}>
                            Close
                          </button>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>

              <div className="leaderboard-mini">
                <h4>Leaderboard</h4>
                <div className="leaderboard-list">
                  {leaderboard.slice(0, 5).map((entry, idx) => (
                    <div key={idx} className={`leaderboard-item ${entry.isPlayer ? 'player' : ''}`}>
                      <span className="rank">#{idx + 1}</span>
                      <span className="name">{entry.name}</span>
                      <span className="lb-capital">₹{(entry.capital / 1000).toFixed(1)}K</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Trade Modal */}
        {showTradeModal && selectedOption && (
          <div className="trade-modal-overlay" onClick={() => setShowTradeModal(false)}>
            <div className="trade-modal" onClick={e => e.stopPropagation()}>
              <h3>
                {tradeType === 'call' ? 'BUY CALL' : 'BUY PUT'} - Strike {selectedOption.strike}
              </h3>
              <div className="trade-details">
                <div className="trade-row">
                  <span>Premium</span>
                  <span>₹{(tradeType === 'call' ? selectedOption.callPrice : selectedOption.putPrice).toFixed(2)}</span>
                </div>
                <div className="trade-row">
                  <span>Lot Size</span>
                  <span>{TOURNAMENT_CONFIG.lotSize}</span>
                </div>
                <div className="trade-row">
                  <span>Quantity</span>
                  <div className="quantity-control">
                    <button onClick={() => setQuantity(Math.max(1, quantity - 1))}>-</button>
                    <span>{quantity}</span>
                    <button onClick={() => setQuantity(quantity + 1)}>+</button>
                  </div>
                </div>
                <div className="trade-row total">
                  <span>Total Cost</span>
                  <span>₹{((tradeType === 'call' ? selectedOption.callPrice : selectedOption.putPrice) * TOURNAMENT_CONFIG.lotSize * quantity).toFixed(0)}</span>
                </div>
              </div>
              <div className="trade-actions">
                <button className="cancel-btn" onClick={() => setShowTradeModal(false)}>Cancel</button>
                <button className="confirm-btn" onClick={executeTrade}>Confirm Trade</button>
              </div>
            </div>
          </div>
        )}

        {newBadge && (
          <div className="badge-popup-overlay" onClick={() => setNewBadge(null)}>
            <div className="badge-popup">
              <div className="badge-icon-large">{newBadge.icon}</div>
              <h3>Achievement Unlocked!</h3>
              <div className="badge-name">{newBadge.name}</div>
              <div className="badge-desc">{newBadge.description}</div>
              <button onClick={() => setNewBadge(null)}>Awesome!</button>
            </div>
          </div>
        )}
      </div>
    );
  }

  // Render Round End
  if (gameState === 'round-end') {
    const playerRank = leaderboard.findIndex(e => e.isPlayer) + 1;
    const lastRound = roundHistory[roundHistory.length - 1];

    return (
      <div className="trading-tournament">
        <div className="round-end">
          <div className={`market-news ${marketNews?.sentiment}`}>
            <h2>Market Update</h2>
            <div className="news-change">
              NIFTY {marketNews?.change >= 0 ? '+' : ''}{marketNews?.change}%
            </div>
            <p>{marketNews?.description}</p>
          </div>

          <div className="round-summary">
            <h3>Round {currentRound} Summary</h3>
            <div className="summary-stats">
              <div className="summary-item">
                <span className="summary-label">Portfolio Value</span>
                <span className="summary-value">₹{portfolioValue.toFixed(0)}</span>
              </div>
              <div className="summary-item">
                <span className="summary-label">Round P&L</span>
                <span className={`summary-value ${lastRound?.pnl >= 0 ? 'positive' : 'negative'}`}>
                  {lastRound?.pnl >= 0 ? '+' : ''}₹{lastRound?.pnl.toFixed(0)}
                </span>
              </div>
              <div className="summary-item">
                <span className="summary-label">Total Return</span>
                <span className={`summary-value ${totalReturn >= 0 ? 'positive' : 'negative'}`}>
                  {totalReturn >= 0 ? '+' : ''}{totalReturn.toFixed(1)}%
                </span>
              </div>
              <div className="summary-item">
                <span className="summary-label">Current Rank</span>
                <span className="summary-value rank">#{playerRank}</span>
              </div>
            </div>
          </div>

          <div className="full-leaderboard">
            <h3>Leaderboard</h3>
            <div className="leaderboard-table">
              {leaderboard.map((entry, idx) => {
                const ret = ((entry.capital / TOURNAMENT_CONFIG.startingCapital) - 1) * 100;
                return (
                  <div key={idx} className={`lb-row ${entry.isPlayer ? 'player' : ''}`}>
                    <span className="lb-rank">#{idx + 1}</span>
                    <span className="lb-name">{entry.name}</span>
                    <span className="lb-capital">₹{entry.capital.toFixed(0)}</span>
                    <span className={`lb-return ${ret >= 0 ? 'positive' : 'negative'}`}>
                      {ret >= 0 ? '+' : ''}{ret.toFixed(1)}%
                    </span>
                  </div>
                );
              })}
            </div>
          </div>

          <button className="next-round-btn" onClick={nextRound}>
            {currentRound >= TOURNAMENT_CONFIG.rounds ? 'See Final Results' : `Start Round ${currentRound + 1} →`}
          </button>
        </div>

        {newBadge && (
          <div className="badge-popup-overlay" onClick={() => setNewBadge(null)}>
            <div className="badge-popup">
              <div className="badge-icon-large">{newBadge.icon}</div>
              <h3>Achievement Unlocked!</h3>
              <div className="badge-name">{newBadge.name}</div>
              <div className="badge-desc">{newBadge.description}</div>
              <button onClick={() => setNewBadge(null)}>Awesome!</button>
            </div>
          </div>
        )}
      </div>
    );
  }

  // Render Tournament Completed
  if (gameState === 'completed') {
    const finalRank = leaderboard.findIndex(e => e.isPlayer) + 1;

    return (
      <div className="trading-tournament">
        <div className="tournament-completed">
          <div className={`result-banner ${finalRank <= 3 ? 'winner' : ''}`}>
            {finalRank === 1 && <span className="trophy">🏆</span>}
            {finalRank === 2 && <span className="trophy">🥈</span>}
            {finalRank === 3 && <span className="trophy">🥉</span>}
            <h1>Tournament Complete!</h1>
            <div className="final-rank">You finished #{finalRank}</div>
          </div>

          <div className="final-stats">
            <div className="big-stat">
              <span className="stat-value">₹{portfolioValue.toFixed(0)}</span>
              <span className="stat-label">Final Portfolio</span>
            </div>
            <div className="stats-grid">
              <div className="stat-box">
                <span className="stat-num">{totalReturn >= 0 ? '+' : ''}{totalReturn.toFixed(1)}%</span>
                <span className="stat-text">Total Return</span>
              </div>
              <div className="stat-box">
                <span className="stat-num">{roundHistory.filter(r => r.pnl > 0).length}/{TOURNAMENT_CONFIG.rounds}</span>
                <span className="stat-text">Profitable Rounds</span>
              </div>
              <div className="stat-box">
                <span className="stat-num">#{finalRank}</span>
                <span className="stat-text">Final Rank</span>
              </div>
            </div>
          </div>

          <div className="final-leaderboard">
            <h3>Final Standings</h3>
            <div className="leaderboard-table">
              {leaderboard.slice(0, 5).map((entry, idx) => {
                const ret = ((entry.capital / TOURNAMENT_CONFIG.startingCapital) - 1) * 100;
                return (
                  <div key={idx} className={`lb-row ${entry.isPlayer ? 'player' : ''}`}>
                    <span className="lb-rank">
                      {idx === 0 ? '🥇' : idx === 1 ? '🥈' : idx === 2 ? '🥉' : `#${idx + 1}`}
                    </span>
                    <span className="lb-name">{entry.name}</span>
                    <span className="lb-capital">₹{entry.capital.toFixed(0)}</span>
                    <span className={`lb-return ${ret >= 0 ? 'positive' : 'negative'}`}>
                      {ret >= 0 ? '+' : ''}{ret.toFixed(1)}%
                    </span>
                  </div>
                );
              })}
            </div>
          </div>

          <div className="action-buttons">
            <button className="play-again-btn" onClick={startTournament}>
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
              <h3>Achievement Unlocked!</h3>
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
