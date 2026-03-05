import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import StrategyShowdown from './StrategyShowdown';
import GreeksArena from './GreeksArena';
import PricePredictor from './PricePredictor';
import TradingTournament from './TradingTournament';
import './GamesHub.css';

const GAMES = [
  {
    id: 'strategy-showdown',
    title: 'Strategy Showdown',
    description: 'Test your knowledge of options strategies with real market scenarios',
    icon: '🎯',
    difficulty: 'All Levels',
    duration: '5-10 min',
    skills: ['Strategy Selection', 'Market Analysis', 'Risk Assessment'],
    color: '#6366f1'
  },
  {
    id: 'greeks-arena',
    title: 'Greeks Arena',
    description: 'Interactive playground to understand how Greeks affect option prices',
    icon: '🎮',
    difficulty: 'Beginner',
    duration: 'Unlimited',
    skills: ['Delta', 'Gamma', 'Theta', 'Vega', 'Black-Scholes'],
    color: '#22c55e'
  },
  {
    id: 'price-predictor',
    title: 'Price Predictor',
    description: 'Guess option prices and build intuition for pricing',
    icon: '🔮',
    difficulty: 'Intermediate',
    duration: '5-8 min',
    skills: ['Pricing Intuition', 'Quick Math', 'Black-Scholes'],
    color: '#f59e0b'
  },
  {
    id: 'trading-tournament',
    title: 'Trading Tournament',
    description: 'Compete against AI traders in paper trading competitions',
    icon: '🏆',
    difficulty: 'All Levels',
    duration: '10-15 min',
    skills: ['Trading', 'Risk Management', 'Competition'],
    color: '#ef4444'
  },
];

export default function GamesHub() {
  const { t } = useTranslation();
  const [activeGame, setActiveGame] = useState(null);

  if (activeGame === 'strategy-showdown') {
    return (
      <div className="game-container">
        <button className="back-to-hub" onClick={() => setActiveGame(null)}>
          ← Back to Games
        </button>
        <StrategyShowdown />
      </div>
    );
  }

  if (activeGame === 'greeks-arena') {
    return (
      <div className="game-container">
        <button className="back-to-hub" onClick={() => setActiveGame(null)}>
          ← Back to Games
        </button>
        <GreeksArena />
      </div>
    );
  }

  if (activeGame === 'price-predictor') {
    return (
      <div className="game-container">
        <button className="back-to-hub" onClick={() => setActiveGame(null)}>
          ← Back to Games
        </button>
        <PricePredictor />
      </div>
    );
  }

  if (activeGame === 'trading-tournament') {
    return (
      <div className="game-container">
        <button className="back-to-hub" onClick={() => setActiveGame(null)}>
          ← Back to Games
        </button>
        <TradingTournament />
      </div>
    );
  }

  return (
    <div className="games-hub">
      <Link to="/" className="back-to-home">
        ← Back to Home
      </Link>
      <div className="hub-header">
        <h1>🎮 Learn With Games</h1>
        <p>Master options trading through interactive games and challenges</p>
        <div className="hub-stats">
          <div className="hub-stat">
            <span className="stat-value">{GAMES.filter(g => !g.comingSoon).length}</span>
            <span className="stat-label">Games Available</span>
          </div>
          <div className="hub-stat">
            <span className="stat-value">{GAMES.filter(g => g.comingSoon).length}</span>
            <span className="stat-label">Coming Soon</span>
          </div>
        </div>
      </div>

      <div className="games-grid">
        {GAMES.map(game => (
          <div
            key={game.id}
            className={`game-card ${game.comingSoon ? 'coming-soon' : ''}`}
            style={{ '--game-color': game.color }}
            onClick={() => !game.comingSoon && setActiveGame(game.id)}
          >
            {game.comingSoon && <div className="coming-soon-badge">Coming Soon</div>}
            <div className="game-icon">{game.icon}</div>
            <h3>{game.title}</h3>
            <p>{game.description}</p>

            <div className="game-meta">
              <span className="meta-item">
                <span className="meta-icon">📊</span>
                {game.difficulty}
              </span>
              <span className="meta-item">
                <span className="meta-icon">⏱️</span>
                {game.duration}
              </span>
            </div>

            <div className="game-skills">
              {game.skills.slice(0, 3).map((skill, idx) => (
                <span key={idx} className="skill-tag">{skill}</span>
              ))}
            </div>

            {!game.comingSoon && (
              <button className="play-btn">
                Play Now →
              </button>
            )}
          </div>
        ))}
      </div>

      <div className="hub-features">
        <h2>Why Learn With Games?</h2>
        <div className="features-grid">
          <div className="feature-item">
            <span className="feature-icon">🧠</span>
            <h4>Active Learning</h4>
            <p>Engage with concepts hands-on instead of passive reading</p>
          </div>
          <div className="feature-item">
            <span className="feature-icon">🎯</span>
            <h4>Instant Feedback</h4>
            <p>Learn from mistakes immediately with detailed explanations</p>
          </div>
          <div className="feature-item">
            <span className="feature-icon">🏆</span>
            <h4>Track Progress</h4>
            <p>Earn badges, maintain streaks, and see your improvement</p>
          </div>
          <div className="feature-item">
            <span className="feature-icon">💡</span>
            <h4>Real Scenarios</h4>
            <p>Practice with scenarios based on actual Indian market conditions</p>
          </div>
        </div>
      </div>
    </div>
  );
}
