import React, { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import './FloatingChatbot.css';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8000';

// Helper to determine market sentiment from PCR
const getMarketSentiment = (pcr) => {
  if (!pcr || pcr === 0) return 'neutral';
  if (pcr > 1.1) return 'bullish';  // High PCR = more puts = bullish for market
  if (pcr < 0.9) return 'bearish';  // Low PCR = more calls = bearish signal
  return 'neutral';
};

export default function FloatingChatbot() {
  const { t } = useTranslation();
  const [isOpen, setIsOpen] = useState(false);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [messages, setMessages] = useState([]);
  const [inputValue, setInputValue] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [sessionId, setSessionId] = useState(null);
  const [suggestions, setSuggestions] = useState(() => t('chatbot.sampleQuestions', { returnObjects: true }));
  const [hasUnread, setHasUnread] = useState(false);
  const [marketContext, setMarketContext] = useState({ index: 'NIFTY', spot_price: 0, pcr: 0 });
  const [sentiment, setSentiment] = useState('neutral');
  const messagesEndRef = useRef(null);
  const inputRef = useRef(null);

  // Fetch market context on mount
  useEffect(() => {
    const fetchMarketContext = async () => {
      try {
        const response = await fetch(`${API_URL}/api/v1/market/spot/NIFTY`);
        if (response.ok) {
          const data = await response.json();
          const spotPrice = data.lastPrice || data.price || 0;

          // Try to get PCR from option chain
          let pcr = 1.0;
          try {
            const chainResponse = await fetch(`${API_URL}/api/v1/market/option-chain/NIFTY?strikes=10`);
            if (chainResponse.ok) {
              const chainData = await chainResponse.json();
              // Calculate PCR from totals or data
              if (chainData.totals) {
                const putOI = chainData.totals.PE?.totalOI || 0;
                const callOI = chainData.totals.CE?.totalOI || 0;
                if (callOI > 0) pcr = putOI / callOI;
              } else if (chainData.data) {
                let totalPutOI = 0, totalCallOI = 0;
                for (const row of chainData.data) {
                  totalPutOI += row.PE?.openInterest || 0;
                  totalCallOI += row.CE?.openInterest || 0;
                }
                if (totalCallOI > 0) pcr = totalPutOI / totalCallOI;
              }
            }
          } catch (pcrErr) {
            console.log('Could not calculate PCR:', pcrErr);
          }

          setMarketContext({ index: 'NIFTY', spot_price: spotPrice, pcr });
          setSentiment(getMarketSentiment(pcr));
        }
      } catch (err) {
        console.log('Could not fetch market context:', err);
      }
    };
    fetchMarketContext();
    // Refresh every 5 minutes
    const interval = setInterval(fetchMarketContext, 300000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  useEffect(() => {
    if (isOpen && inputRef.current) {
      inputRef.current.focus();
    }
    if (isOpen) {
      setHasUnread(false);
    }
  }, [isOpen]);

  // Handle escape key to exit fullscreen
  useEffect(() => {
    const handleEscape = (e) => {
      if (e.key === 'Escape' && isFullscreen) {
        setIsFullscreen(false);
      }
    };
    document.addEventListener('keydown', handleEscape);
    return () => document.removeEventListener('keydown', handleEscape);
  }, [isFullscreen]);

  // Prevent body scroll when fullscreen
  useEffect(() => {
    if (isFullscreen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }
    return () => {
      document.body.style.overflow = '';
    };
  }, [isFullscreen]);

  // Show welcome message after a delay
  useEffect(() => {
    const timer = setTimeout(() => {
      setHasUnread(true);
    }, 5000);
    return () => clearTimeout(timer);
  }, []);

  const sendMessage = async (messageText = inputValue) => {
    if (!messageText.trim() || isLoading) return;

    const userMessage = {
      id: Date.now(),
      role: 'user',
      content: messageText.trim(),
    };

    setMessages(prev => [...prev, userMessage]);
    setInputValue('');
    setIsLoading(true);

    try {
      const response = await fetch(`${API_URL}/api/v1/chat/message`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          message: messageText.trim(),
          session_id: sessionId,
          market_context: marketContext
        }),
      });

      if (!response.ok) {
        // Try fallback to /chat/quick (no DB required)
        const quickResponse = await fetch(`${API_URL}/api/v1/chat/quick`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            message: messageText.trim(),
            market_context: marketContext
          }),
        });

        if (!quickResponse.ok) {
          const errData = await quickResponse.json().catch(() => null);
          throw new Error(errData?.detail || `Server error (${quickResponse.status})`);
        }

        const quickData = await quickResponse.json();
        setMessages(prev => [...prev, {
          id: Date.now() + 1,
          role: 'assistant',
          content: quickData.response || quickData.message,
        }]);
        if (quickData.suggestions?.length > 0) {
          setSuggestions(quickData.suggestions);
        }
        return;
      }

      const data = await response.json();
      if (data.session_id) setSessionId(data.session_id);

      // Update sentiment from response if available
      if (data.market_context?.pcr) {
        setSentiment(getMarketSentiment(data.market_context.pcr));
      }

      setMessages(prev => [...prev, {
        id: Date.now() + 1,
        role: 'assistant',
        content: data.message.content,
      }]);

      if (data.suggestions?.length > 0) {
        setSuggestions(data.suggestions);
      }
    } catch (err) {
      console.error('Chat error:', err);
      setMessages(prev => [...prev, {
        id: Date.now() + 1,
        role: 'assistant',
        content: err.message && err.message !== 'Failed to fetch'
          ? `Sorry, something went wrong: ${err.message}`
          : t('chatbot.errors.failed'),
        isError: true,
      }]);
    } finally {
      setIsLoading(false);
    }
  };

  const escapeHtml = (str) => {
    return str
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  };

  const formatMessage = (content, role) => {
    // Escape HTML first to prevent XSS
    let escaped = escapeHtml(content);
    if (role === 'assistant') {
      return escaped
        .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
        .replace(/\n/g, '<br/>');
    }
    return escaped.replace(/\n/g, '<br/>');
  };

  const toggleChat = () => {
    setIsOpen(!isOpen);
  };

  const toggleFullscreen = () => {
    setIsFullscreen(!isFullscreen);
  };

  const closeChat = () => {
    setIsFullscreen(false);
    setIsOpen(false);
  };

  return (
    <div className={`floating-chatbot ${isFullscreen ? 'fullscreen-mode' : ''}`}>
      {/* Fullscreen Overlay */}
      {isFullscreen && <div className="fullscreen-overlay" onClick={() => setIsFullscreen(false)} />}

      {/* Chat Window */}
      <div className={`floating-chat-window ${isOpen ? 'open' : ''} ${isFullscreen ? 'fullscreen' : ''} sentiment-${sentiment}`}>
        {/* Header */}
        <div className={`floating-chat-header sentiment-${sentiment}`}>
          <div className="header-left">
            <div className="bot-avatar-floating">
              <span>AI</span>
            </div>
            <div className="bot-info-floating">
              <span className="bot-name-floating">{t('chatbot.title')}</span>
              <span className="bot-subtitle">{t('chatbot.subtitle')}</span>
            </div>
            {/* Sentiment Badge */}
            {sentiment !== 'neutral' && (
              <div className={`sentiment-badge ${sentiment}`}>
                {sentiment === 'bullish' ? '📈' : '📉'}
                <span>{sentiment === 'bullish' ? 'Bullish' : 'Bearish'}</span>
              </div>
            )}
          </div>
          <div className="header-actions">
            {/* Fullscreen Toggle Button */}
            <button className="header-btn fullscreen-btn" onClick={toggleFullscreen} title={isFullscreen ? "Exit fullscreen" : "Fullscreen"}>
              {isFullscreen ? (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M8 3v3a2 2 0 0 1-2 2H3m18 0h-3a2 2 0 0 1-2-2V3m0 18v-3a2 2 0 0 1 2-2h3M3 16h3a2 2 0 0 1 2 2v3"/>
                </svg>
              ) : (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3"/>
                </svg>
              )}
            </button>
            {/* Close Button */}
            <button className="header-btn close-btn" onClick={closeChat}>
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <line x1="18" y1="6" x2="6" y2="18"></line>
                <line x1="6" y1="6" x2="18" y2="18"></line>
              </svg>
            </button>
          </div>
        </div>

        {/* Messages */}
        <div className="floating-chat-messages">
          {messages.length === 0 ? (
            <div className="floating-welcome">
              <div className="welcome-avatar">
                <span>AI</span>
              </div>
              <h4>{t('chatbot.welcome')}</h4>
              <p>{t('chatbot.welcomeMessage')}</p>

              <div className="quick-questions">
                {suggestions.map((q, i) => (
                  <button key={i} onClick={() => sendMessage(q)} disabled={isLoading}>
                    {q}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <>
              {messages.map((msg) => (
                <div key={msg.id} className={`floating-message ${msg.role} ${msg.isError ? 'error' : ''}`}>
                  {msg.role === 'assistant' && (
                    <div className="msg-avatar-small">AI</div>
                  )}
                  <div
                    className="msg-bubble"
                    dangerouslySetInnerHTML={{ __html: formatMessage(msg.content, msg.role) }}
                  />
                </div>
              ))}
              {isLoading && (
                <div className="floating-message assistant">
                  <div className="msg-avatar-small">AI</div>
                  <div className="msg-bubble typing">
                    <span className="typing-dot"></span>
                    <span className="typing-dot"></span>
                    <span className="typing-dot"></span>
                  </div>
                </div>
              )}
            </>
          )}
          <div ref={messagesEndRef} />
        </div>

        {/* Suggestions */}
        {messages.length > 0 && messages.length < 6 && (
          <div className="floating-suggestions">
            {suggestions.slice(0, 2).map((s, i) => (
              <button key={i} onClick={() => sendMessage(s)} disabled={isLoading}>
                {s}
              </button>
            ))}
          </div>
        )}

        {/* Input */}
        <div className="floating-chat-input">
          <input
            ref={inputRef}
            type="text"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyPress={(e) => e.key === 'Enter' && sendMessage()}
            placeholder={t('chatbot.placeholder')}
            disabled={isLoading}
          />
          <button
            className="send-btn"
            onClick={() => sendMessage()}
            disabled={!inputValue.trim() || isLoading}
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
              <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
            </svg>
          </button>
        </div>

        {/* Footer */}
        <div className="floating-chat-footer">
          <span>{t('chatbot.poweredBy')}</span>
          {isFullscreen && <span className="esc-hint">{t('chatbot.escHint')}</span>}
        </div>
      </div>

      {/* Floating Button */}
      <button
        className={`floating-chat-button ${isOpen ? 'hidden' : ''} ${hasUnread ? 'has-unread' : ''}`}
        onClick={toggleChat}
        aria-label="Open chat"
      >
        <div className="chat-button-content">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="currentColor">
            <path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H5.17L4 17.17V4h16v12z"/>
            <circle cx="12" cy="10" r="1.5"/>
            <circle cx="8" cy="10" r="1.5"/>
            <circle cx="16" cy="10" r="1.5"/>
          </svg>
        </div>
        {hasUnread && <span className="unread-badge"></span>}
        <span className="chat-button-label">{t('chatbot.askAI')}</span>
      </button>
    </div>
  );
}
