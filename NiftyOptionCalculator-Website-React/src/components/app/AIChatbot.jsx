import React, { useState, useRef, useEffect } from 'react'
import './AIChatbot.css'

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8000'

const SAMPLE_QUESTIONS = [
  "What are the best NIFTY options to trade today?",
  "Explain Put-Call Ratio",
  "Suggest a low-risk strategy",
  "What is theta decay?",
  "How to use Iron Condor?",
]

function AIChatbot({ isOpen, onClose, embedded = false, marketContext = null }) {
  const [messages, setMessages] = useState([])
  const [inputValue, setInputValue] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [sessionId, setSessionId] = useState(null)
  const [suggestions, setSuggestions] = useState(SAMPLE_QUESTIONS.slice(0, 3))
  const [error, setError] = useState(null)
  const [isConfigured, setIsConfigured] = useState(true)
  const messagesEndRef = useRef(null)
  const inputRef = useRef(null)

  // Check if chatbot is configured
  useEffect(() => {
    checkStatus()
  }, [])

  // Scroll to bottom when messages change
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  // Focus input when opened
  useEffect(() => {
    if (isOpen && inputRef.current) {
      inputRef.current.focus()
    }
  }, [isOpen])

  const checkStatus = async () => {
    try {
      const response = await fetch(`${API_URL}/api/v1/chat/status`)
      const data = await response.json()
      setIsConfigured(data.configured)
    } catch (err) {
      console.error('Failed to check chatbot status:', err)
      setIsConfigured(false)
    }
  }

  const sendMessage = async (messageText = inputValue) => {
    if (!messageText.trim() || isLoading) return

    const userMessage = {
      id: Date.now(),
      role: 'user',
      content: messageText.trim(),
      timestamp: new Date(),
    }

    setMessages(prev => [...prev, userMessage])
    setInputValue('')
    setIsLoading(true)
    setError(null)

    try {
      const response = await fetch(`${API_URL}/api/v1/chat/message`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          message: messageText.trim(),
          session_id: sessionId,
          market_context: marketContext,
        }),
      })

      if (!response.ok) {
        // Try fallback to /chat/quick (no DB required)
        const quickResponse = await fetch(`${API_URL}/api/v1/chat/quick`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            message: messageText.trim(),
            market_context: marketContext,
          }),
        })

        if (!quickResponse.ok) {
          const errData = await quickResponse.json().catch(() => null)
          throw new Error(errData?.detail || `Server error (${quickResponse.status})`)
        }

        const quickData = await quickResponse.json()
        setMessages(prev => [...prev, {
          id: Date.now() + 1,
          role: 'assistant',
          content: quickData.response || quickData.message,
          timestamp: new Date(),
        }])
        if (quickData.suggestions?.length > 0) {
          setSuggestions(quickData.suggestions)
        }
        return
      }

      const data = await response.json()

      // Update session ID
      if (data.session_id) {
        setSessionId(data.session_id)
      }

      // Add assistant message
      const assistantMessage = {
        id: Date.now() + 1,
        role: 'assistant',
        content: data.message.content,
        timestamp: new Date(data.message.created_at),
      }

      setMessages(prev => [...prev, assistantMessage])

      // Update suggestions
      if (data.suggestions && data.suggestions.length > 0) {
        setSuggestions(data.suggestions)
      }

    } catch (err) {
      console.error('Chat error:', err)
      setError(err.message || 'Failed to get response. Please try again.')

      // Add error message with actual detail
      const errorMessage = {
        id: Date.now() + 1,
        role: 'assistant',
        content: err.message && err.message !== 'Failed to fetch'
          ? `Sorry, something went wrong: ${err.message}`
          : 'Sorry, I encountered an error. Please try again.',
        timestamp: new Date(),
        isError: true,
      }
      setMessages(prev => [...prev, errorMessage])
    } finally {
      setIsLoading(false)
    }
  }

  const handleKeyPress = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
  }

  const handleSuggestionClick = (suggestion) => {
    sendMessage(suggestion)
  }

  const clearChat = () => {
    setMessages([])
    setSessionId(null)
    setSuggestions(SAMPLE_QUESTIONS.slice(0, 3))
    setError(null)
  }

  const escapeHtml = (str) => {
    return str
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;')
  }

  const formatMessage = (content, role) => {
    // Escape HTML first to prevent XSS
    let escaped = escapeHtml(content)
    // Only apply markdown formatting for assistant messages
    if (role === 'assistant') {
      return escaped
        .split('\n')
        .map((line, i) => {
          // Bold
          line = line.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
          // Bullet points
          if (line.startsWith('- ') || line.startsWith('* ')) {
            return `<li key=${i}>${line.slice(2)}</li>`
          }
          return line
        })
        .join('<br/>')
    }
    // For user messages, just convert newlines to <br/>
    return escaped.replace(/\n/g, '<br/>')
  }

  if (!isOpen && !embedded) return null

  const chatContent = (
    <>
      {/* Header */}
      <div className="chatbot-header">
        <div className="chatbot-title">
          <span className="chatbot-icon">🤖</span>
          <div>
            <h3>Optixia</h3>
            <span className="chatbot-status">
              {isConfigured ? 'Online' : 'Offline'}
            </span>
          </div>
        </div>
        <div className="chatbot-actions">
          <button className="clear-btn" onClick={clearChat} title="Clear chat">
            🗑️
          </button>
          {!embedded && (
            <button className="close-btn" onClick={onClose} title="Close">
              ✕
            </button>
          )}
        </div>
      </div>

      {/* Messages */}
      <div className="chatbot-messages">
        {messages.length === 0 ? (
          <div className="chatbot-welcome">
            <div className="welcome-icon">🤖</div>
            <h4>Welcome to Optixia</h4>
            <p>Your AI-powered options trading assistant. Ask me about:</p>
            <ul>
              <li>Option strategies & Greeks</li>
              <li>Market analysis & PCR</li>
              <li>NIFTY, BANKNIFTY insights</li>
              <li>Risk management tips</li>
            </ul>
          </div>
        ) : (
          messages.map((msg) => (
            <div
              key={msg.id}
              className={`message ${msg.role} ${msg.isError ? 'error' : ''}`}
            >
              {msg.role === 'assistant' && (
                <span className="message-avatar">🤖</span>
              )}
              <div className="message-content">
                <div
                  className="message-text"
                  dangerouslySetInnerHTML={{ __html: formatMessage(msg.content, msg.role) }}
                />
                <span className="message-time">
                  {msg.timestamp.toLocaleTimeString('en-IN', {
                    hour: '2-digit',
                    minute: '2-digit',
                  })}
                </span>
              </div>
            </div>
          ))
        )}

        {isLoading && (
          <div className="message assistant loading">
            <span className="message-avatar">🤖</span>
            <div className="message-content">
              <div className="typing-indicator">
                <span></span>
                <span></span>
                <span></span>
              </div>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Suggestions */}
      {suggestions.length > 0 && messages.length < 6 && (
        <div className="chatbot-suggestions">
          {suggestions.map((suggestion, idx) => (
            <button
              key={idx}
              className="suggestion-chip"
              onClick={() => handleSuggestionClick(suggestion)}
              disabled={isLoading}
            >
              {suggestion}
            </button>
          ))}
        </div>
      )}

      {/* Input */}
      <div className="chatbot-input-container">
        {!isConfigured && (
          <div className="chatbot-offline-notice">
            AI is currently offline. Please try again later.
          </div>
        )}
        <div className="chatbot-input">
          <textarea
            ref={inputRef}
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyPress={handleKeyPress}
            placeholder={isConfigured ? "Ask about options trading..." : "AI is offline"}
            disabled={!isConfigured || isLoading}
            rows={1}
          />
          <button
            className="send-btn"
            onClick={() => sendMessage()}
            disabled={!inputValue.trim() || isLoading || !isConfigured}
          >
            {isLoading ? (
              <span className="sending-spinner"></span>
            ) : (
              '➤'
            )}
          </button>
        </div>
        <div className="chatbot-disclaimer">
          ⚠️ AI responses are not investment advice. Investment in securities market is subject to market risks. Consult a SEBI-registered advisor.
        </div>
      </div>
    </>
  )

  if (embedded) {
    return <div className="chatbot-embedded">{chatContent}</div>
  }

  return (
    <div className="chatbot-overlay" onClick={onClose}>
      <div className="chatbot-container" onClick={(e) => e.stopPropagation()}>
        {chatContent}
      </div>
    </div>
  )
}

// Floating chat button component
export function ChatButton({ onClick }) {
  return (
    <button className="chat-fab" onClick={onClick} title="Chat with Optixia">
      <span className="chat-fab-icon">🤖</span>
      <span className="chat-fab-pulse"></span>
    </button>
  )
}

export default AIChatbot
