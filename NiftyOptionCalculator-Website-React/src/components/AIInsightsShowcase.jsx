import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import './AIInsightsShowcase.css';

const AI_FEATURE_KEYS = [
  { key: 'smartTrade', icon: '🎯' },
  { key: 'technical', icon: '📊' },
  { key: 'sentiment', icon: '🧠' },
  { key: 'risk', icon: '⚡' },
  { key: 'alerts', icon: '🔔' },
  { key: 'greeks', icon: '📈' }
];

const BOT_FEATURE_KEYS = [
  { key: 'nlp', icon: '💬' },
  { key: 'strategy', icon: '🤖' },
  { key: 'portfolio', icon: '📱' },
  { key: 'learn', icon: '🎓' }
];

const SAMPLE_INSIGHTS = [
  { type: 'call', strike: 25800, ltp: 156.50, score: 87, signal: 'Strong Buy', reason: 'High OI buildup, bullish PCR' },
  { type: 'put', strike: 25600, ltp: 89.25, score: 82, signal: 'Buy', reason: 'Support level, good R:R ratio' },
  { type: 'call', strike: 25900, ltp: 98.30, score: 78, signal: 'Buy', reason: 'Breakout candidate, low IV' },
];

// Chat Preview Component - Points users to the floating chatbot
function ChatPreview() {
  const { t } = useTranslation();

  // Sample conversation for preview
  const sampleConversation = [
    { role: 'user', content: 'What is a good strategy for sideways market?' },
    { role: 'assistant', content: 'For a sideways market, an **Iron Condor** is a great strategy. It profits from low volatility by selling both a call and put spread.\n\n**Max Profit**: Premium received\n**Max Loss**: Limited to spread width minus premium' },
  ];

  return (
    <div className="chat-window preview">
      <div className="chat-header">
        <div className="bot-avatar">AI</div>
        <div className="bot-info">
          <span className="bot-name">{t('aiShowcase.bot.name')}</span>
          <span className="bot-status online">● {t('aiShowcase.bot.online')}</span>
        </div>
      </div>

      <div className="chat-messages">
        {sampleConversation.map((msg, idx) => (
          <div key={idx} className={`message ${msg.role}`}>
            {msg.role === 'assistant' && <span className="msg-avatar">AI</span>}
            <div className="msg-content">
              {msg.content.split('\n').map((line, i) => (
                <span key={i}>
                  {line.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')}
                  {i < msg.content.split('\n').length - 1 && <br />}
                </span>
              ))}
            </div>
          </div>
        ))}
      </div>

      <div className="chat-cta">
        <p>{t('aiShowcase.bot.ctaText') || 'Try Optixia - Click the chat button below'}</p>
        <div className="chat-cta-arrow">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z"/>
          </svg>
        </div>
      </div>
    </div>
  );
}

export default function AIInsightsShowcase() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState('insights');

  const aiFeatures = AI_FEATURE_KEYS.map(item => ({
    icon: item.icon,
    title: t(`aiShowcase.features.${item.key}.title`),
    description: t(`aiShowcase.features.${item.key}.description`),
    highlight: t(`aiShowcase.features.${item.key}.highlight`)
  }));

  const botFeatures = BOT_FEATURE_KEYS.map(item => ({
    icon: item.icon,
    title: t(`aiShowcase.bot.features.${item.key}.title`),
    description: t(`aiShowcase.bot.features.${item.key}.description`)
  }));

  const benefitItems = t('aiShowcase.benefits.items', { returnObjects: true }) || [];

  return (
    <section className="ai-insights-showcase" id="ai-insights">
      <div className="container">
        {/* Section Header */}
        <div className="section-header">
          <div className="badge">
            <span className="pulse"></span>
            {t('aiShowcase.badge')}
          </div>
          <h2>{t('aiShowcase.title')}</h2>
          <p>{t('aiShowcase.subtitle')}</p>
        </div>

        {/* Tab Selector */}
        <div className="tab-selector">
          <button
            className={`tab-btn ${activeTab === 'insights' ? 'active' : ''}`}
            onClick={() => setActiveTab('insights')}
          >
            <span className="icon">🎯</span>
            {t('aiShowcase.tabs.insights')}
          </button>
          <button
            className={`tab-btn ${activeTab === 'bot' ? 'active' : ''}`}
            onClick={() => setActiveTab('bot')}
          >
            <span className="icon">🤖</span>
            {t('aiShowcase.tabs.bot')}
            <span className="live-badge">{t('aiShowcase.tabs.live')}</span>
          </button>
        </div>

        {/* AI Insights Content */}
        {activeTab === 'insights' && (
          <div className="insights-content">
            {/* Live Demo Card */}
            <div className="demo-section">
              <div className="demo-card">
                <div className="demo-header">
                  <div className="demo-title">
                    <span className="live-dot"></span>
                    {t('aiShowcase.demo.title')}
                  </div>
                  <span className="demo-badge">NIFTY 50</span>
                </div>

                {/* Market Overview */}
                <div className="market-overview">
                  <div className="market-stat">
                    <span className="stat-label">{t('aiShowcase.demo.marketSentiment')}</span>
                    <span className="stat-value bullish">{t('aiShowcase.demo.bullish')}</span>
                  </div>
                  <div className="market-stat">
                    <span className="stat-label">PCR</span>
                    <span className="stat-value">1.24</span>
                  </div>
                  <div className="market-stat">
                    <span className="stat-label">ATM IV</span>
                    <span className="stat-value">14.2%</span>
                  </div>
                  <div className="market-stat">
                    <span className="stat-label">Max Pain</span>
                    <span className="stat-value">25750</span>
                  </div>
                </div>

                {/* Sample Suggestions */}
                <div className="suggestions-preview">
                  <h4>{t('aiShowcase.demo.topSuggestions')}</h4>
                  {SAMPLE_INSIGHTS.map((insight, idx) => (
                    <div key={idx} className={`suggestion-item ${insight.type}`}>
                      <div className="suggestion-left">
                        <span className={`type-badge ${insight.type}`}>
                          {insight.type === 'call' ? 'CE' : 'PE'}
                        </span>
                        <div className="suggestion-details">
                          <span className="strike">{insight.strike}</span>
                          <span className="ltp">₹{insight.ltp}</span>
                        </div>
                      </div>
                      <div className="suggestion-middle">
                        <span className="reason">{insight.reason}</span>
                      </div>
                      <div className="suggestion-right">
                        <div className="score-ring" style={{ '--score': insight.score }}>
                          <span className="score-value">{insight.score}</span>
                        </div>
                        <span className={`signal ${insight.signal.toLowerCase().replace(' ', '-')}`}>
                          {insight.signal}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>

                <div className="demo-footer">
                  <span>{t('aiShowcase.demo.updatedEvery')}</span>
                </div>
              </div>

              {/* Benefits List */}
              <div className="benefits-list">
                <h3>{t('aiShowcase.benefits.title')}</h3>
                <ul>
                  {Array.isArray(benefitItems) && benefitItems.map((item, idx) => (
                    <li key={idx}>
                      <span className="check">✓</span>
                      <span>{item}</span>
                    </li>
                  ))}
                </ul>
                <a href="#download" className="cta-button">
                  <span>{t('aiShowcase.benefits.cta')}</span>
                  <span className="arrow">→</span>
                </a>
              </div>
            </div>

            {/* Features Grid */}
            <div className="features-grid">
              {aiFeatures.map((feature, idx) => (
                <div key={idx} className="feature-card">
                  <div className="feature-icon">{feature.icon}</div>
                  <h4>{feature.title}</h4>
                  <p>{feature.description}</p>
                  <span className="feature-highlight">{feature.highlight}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* AI Bot Content */}
        {activeTab === 'bot' && (
          <div className="bot-content">
            {/* Bot Preview - actual chatbot is the floating button */}
            <div className="bot-preview">
              <ChatPreview />

              {/* Bot Features */}
              <div className="bot-features">
                <h3>{t('aiShowcase.bot.whatCanDo')}</h3>
                <div className="bot-features-grid">
                  {botFeatures.map((feature, idx) => (
                    <div key={idx} className="bot-feature-card">
                      <span className="bot-feature-icon">{feature.icon}</span>
                      <h4>{feature.title}</h4>
                      <p>{feature.description}</p>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Bottom CTA */}
        <div className="bottom-cta">
          <div className="cta-content">
            <h3>{t('aiShowcase.cta.title')}</h3>
            <p>{t('aiShowcase.cta.subtitle')}</p>
          </div>
          <div className="cta-buttons">
            <a href="/app" className="btn-primary">
              {t('aiShowcase.cta.tryWeb')}
            </a>
            <span className="btn-secondary coming-soon" style={{ opacity: 0.6, cursor: 'default' }}>
              <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                <path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.81-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z"/>
              </svg>
              {t('aiShowcase.cta.downloadApp')}
            </span>
          </div>
        </div>
      </div>
    </section>
  );
}
