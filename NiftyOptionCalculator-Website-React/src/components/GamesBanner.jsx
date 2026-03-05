import React from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import './GamesBanner.css';

export default function GamesBanner() {
  const { t } = useTranslation();

  const games = [
    { icon: '🎯', name: 'Strategy Showdown', desc: 'Pick the right strategy' },
    { icon: '🎮', name: 'Greeks Arena', desc: 'Master the Greeks' },
    { icon: '🔮', name: 'Price Predictor', desc: 'Guess option prices' },
    { icon: '🏆', name: 'Trading Tournament', desc: 'Compete & trade' },
  ];

  return (
    <section className="games-banner" id="games">
      <div className="container">
        <div className="games-banner-content">
          <div className="games-text">
            <span className="games-badge">New Feature</span>
            <h2>Learn Options With Games</h2>
            <p>
              Master options trading through interactive games and challenges.
              Practice strategies, understand Greeks, and build trading intuition - all while having fun!
            </p>
            <div className="games-features">
              <div className="game-feature">
                <span className="feature-check">✓</span>
                <span>Real market scenarios</span>
              </div>
              <div className="game-feature">
                <span className="feature-check">✓</span>
                <span>Earn badges & track progress</span>
              </div>
              <div className="game-feature">
                <span className="feature-check">✓</span>
                <span>Instant feedback & explanations</span>
              </div>
            </div>
            <Link to="/games" className="games-cta">
              Play Games Free →
            </Link>
          </div>

          <div className="games-preview">
            <div className="games-grid-preview">
              {games.map((game, idx) => (
                <div key={idx} className="game-preview-card">
                  <span className="game-icon">{game.icon}</span>
                  <span className="game-name">{game.name}</span>
                  <span className="game-desc">{game.desc}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
