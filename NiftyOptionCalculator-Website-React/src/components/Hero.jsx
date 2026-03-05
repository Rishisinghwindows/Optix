import React, { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import AnimatedLogo from './AnimatedLogo'
import { AdBanner } from './ads'
import { ADS_CONFIG } from '../config/adsConfig'

// Animated counter component
function AnimatedCounter({ end, suffix = '', duration = 2000 }) {
  const [count, setCount] = useState(0)

  useEffect(() => {
    let startTime
    const animate = (timestamp) => {
      if (!startTime) startTime = timestamp
      const progress = Math.min((timestamp - startTime) / duration, 1)
      setCount(Math.floor(progress * end))
      if (progress < 1) requestAnimationFrame(animate)
    }
    requestAnimationFrame(animate)
  }, [end, duration])

  return <>{count}{suffix}</>
}

// Typing effect component
function TypeWriter({ words, typingSpeed = 100, deletingSpeed = 50, pauseDuration = 2000 }) {
  const [text, setText] = useState('')
  const [wordIndex, setWordIndex] = useState(0)
  const [isDeleting, setIsDeleting] = useState(false)

  useEffect(() => {
    const currentWord = words[wordIndex]

    const timeout = setTimeout(() => {
      if (!isDeleting) {
        setText(currentWord.substring(0, text.length + 1))
        if (text === currentWord) {
          setTimeout(() => setIsDeleting(true), pauseDuration)
        }
      } else {
        setText(currentWord.substring(0, text.length - 1))
        if (text === '') {
          setIsDeleting(false)
          setWordIndex((prev) => (prev + 1) % words.length)
        }
      }
    }, isDeleting ? deletingSpeed : typingSpeed)

    return () => clearTimeout(timeout)
  }, [text, isDeleting, wordIndex, words, typingSpeed, deletingSpeed, pauseDuration])

  return <span className="typewriter-text">{text}<span className="cursor">|</span></span>
}

function Hero() {
  const { t } = useTranslation()
  const [activeFeature, setActiveFeature] = useState(0)

  const features = [
    { icon: '🎯', title: 'AI Insights', desc: 'Smart trade suggestions' },
    { icon: '📊', title: 'Live Option Chain', desc: 'Real-time NSE/BSE data' },
    { icon: '🧮', title: 'Greeks Calculator', desc: 'Black-Scholes pricing' },
    { icon: '📈', title: 'Paper Trading', desc: 'Practice with ₹10L virtual' },
  ]

  const rotatingWords = ['Profits', 'Accuracy', 'Insights', 'Success']

  useEffect(() => {
    const interval = setInterval(() => {
      setActiveFeature((prev) => (prev + 1) % features.length)
    }, 3000)
    return () => clearInterval(interval)
  }, [])

  return (
    <section className="hero-enhanced">
      {/* Animated Background */}
      <div className="hero-bg-enhanced">
        <div className="gradient-orb orb-1"></div>
        <div className="gradient-orb orb-2"></div>
        <div className="gradient-orb orb-3"></div>
        <div className="grid-pattern"></div>
        <div className="floating-shapes">
          <div className="shape shape-1">📈</div>
          <div className="shape shape-2">💹</div>
          <div className="shape shape-3">🎯</div>
          <div className="shape shape-4">⚡</div>
        </div>
      </div>

      <div className="container hero-container-enhanced">
        {/* Left Content */}
        <div className="hero-content-enhanced">
          {/* Badge */}
          <div className="hero-badge-enhanced">
            <span className="badge-pulse"></span>
            <span>AI-Powered Trading Platform</span>
          </div>

          {/* Main Headline */}
          <h1 className="hero-headline">
            Trade Smarter with AI &
            <br />
            <span className="headline-gradient">
              Maximize Your <TypeWriter words={rotatingWords} />
            </span>
          </h1>

          {/* Subheadline */}
          <p className="hero-subheadline">
            India's most advanced options analytics platform. Get AI-powered trade suggestions,
            real-time Greeks, Black-Scholes pricing, and practice with ₹10 Lakh virtual money.
          </p>

          {/* Feature Pills */}
          <div className="hero-features">
            {features.map((feature, idx) => (
              <div
                key={idx}
                className={`feature-pill ${activeFeature === idx ? 'active' : ''}`}
                onMouseEnter={() => setActiveFeature(idx)}
              >
                <span className="feature-icon">{feature.icon}</span>
                <div className="feature-text">
                  <span className="feature-title">{feature.title}</span>
                  <span className="feature-desc">{feature.desc}</span>
                </div>
              </div>
            ))}
          </div>

          {/* CTA Buttons */}
          <div className="hero-cta-group">
            <a href="/app" className="cta-primary">
              <span>Launch Web App</span>
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M5 12h14M12 5l7 7-7 7"/>
              </svg>
            </a>
            <a
              className="coming-soon-badge"
              href="/android-build/Optix-Android-debug.apk"
              download
            >
              Download Android Build
            </a>
            <a
              className="coming-soon-badge ios-badge"
              href="https://testflight.apple.com/join/r5r4HBzt"
              target="_blank"
              rel="noreferrer"
              aria-label="iOS TestFlight"
              title="iOS TestFlight"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.81-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z"/>
              </svg>
            </a>
            <a
              className="coming-soon-badge android-badge"
              href="/android-build/Optix-Android-debug.apk"
              download
              aria-label="Android Download"
              title="Android Download"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M7.5 7.2 5.7 5.4l.8-.8 1.9 1.9.9-.9 1.1 1.1-.9.9 1.9 1.9-.8.8-1.9-1.9-.9.9-1.1-1.1.9-.9z"/>
                <path d="M16.5 7.2 14.7 5.4l.8-.8 1.9 1.9.9-.9 1.1 1.1-.9.9 1.9 1.9-.8.8-1.9-1.9-.9.9-1.1-1.1.9-.9z"/>
                <path d="M7 9h10a4 4 0 0 1 4 4v4h-2v-4a2 2 0 0 0-2-2h-1v8h-2v-8H10v8H8v-8H7a2 2 0 0 0-2 2v4H3v-4a4 4 0 0 1 4-4z"/>
                <path d="M8 19h2v2H8v-2zm6 0h2v2h-2v-2z"/>
              </svg>
            </a>
          </div>

          {/* Stats with Animation */}
          <div className="hero-stats-enhanced">
            <div className="stat-item">
              <span className="stat-number"><AnimatedCounter end={5} />+</span>
              <span className="stat-text">Indices Supported</span>
            </div>
            <div className="stat-divider"></div>
            <div className="stat-item">
              <span className="stat-number">₹<AnimatedCounter end={10} />L</span>
              <span className="stat-text">Paper Trading</span>
            </div>
            <div className="stat-divider"></div>
            <div className="stat-item">
              <span className="stat-number"><AnimatedCounter end={15} />+</span>
              <span className="stat-text">Option Strategies</span>
            </div>
            <div className="stat-divider"></div>
            <div className="stat-item">
              <span className="stat-number live-indicator">Live</span>
              <span className="stat-text">IPO GMP Tracker</span>
            </div>
          </div>

          {/* Trust Badges */}
          <div className="trust-badges">
            <div className="trust-badge">
              <span className="trust-icon">🔒</span>
              <span>Bank-Grade Security</span>
            </div>
            <div className="trust-badge">
              <span className="trust-icon">⚡</span>
              <span>Real-time Data</span>
            </div>
            <div className="trust-badge">
              <span className="trust-icon">🇮🇳</span>
              <span>Made for India</span>
            </div>
          </div>
        </div>

        {/* Right Side - Phone Mockup */}
        <div className="hero-visual">
          {/* Large Logo Backdrop */}
          <div className="logo-backdrop">
            <AnimatedLogo />
          </div>

          <div className="phone-mockup-enhanced">
            <div className="phone-frame">
              <div className="phone-notch"></div>
              <div className="phone-screen">
                {/* Animated App Preview */}
                <div className="app-preview-enhanced">
                  {/* Header */}
                  <div className="preview-header-new">
                    <div className="header-left">
                      <span className="index-badge">NIFTY 50</span>
                      <span className="live-dot"></span>
                    </div>
                    <div className="header-right">
                      <span className="preview-price">₹25,650</span>
                      <span className="preview-change up">+1.25%</span>
                    </div>
                  </div>

                  {/* Animated Chart */}
                  <div className="preview-chart-new">
                    <svg viewBox="0 0 280 80" className="animated-chart">
                      <defs>
                        <linearGradient id="chartGradientNew" x1="0%" y1="0%" x2="0%" y2="100%">
                          <stop offset="0%" stopColor="#22c55e" stopOpacity="0.4"/>
                          <stop offset="100%" stopColor="#22c55e" stopOpacity="0"/>
                        </linearGradient>
                      </defs>
                      <path
                        className="chart-line-animated"
                        d="M0,60 Q30,55 60,45 T120,35 T180,25 T240,30 T280,20"
                        fill="none"
                        stroke="#22c55e"
                        strokeWidth="2.5"
                      />
                      <path
                        className="chart-fill-animated"
                        d="M0,60 Q30,55 60,45 T120,35 T180,25 T240,30 T280,20 V80 H0 Z"
                        fill="url(#chartGradientNew)"
                      />
                    </svg>
                  </div>

                  {/* AI Insight Card */}
                  <div className="ai-insight-card">
                    <div className="ai-header">
                      <span className="ai-icon">🤖</span>
                      <span className="ai-title">AI Suggestion</span>
                      <span className="ai-badge">NEW</span>
                    </div>
                    <div className="ai-content">
                      <span className="ai-action buy">BUY</span>
                      <span className="ai-strike">NIFTY 25700 CE</span>
                      <span className="ai-confidence">85%</span>
                    </div>
                  </div>

                  {/* Quick Stats */}
                  <div className="preview-stats-new">
                    <div className="stat-box">
                      <span className="stat-label">PCR</span>
                      <span className="stat-value green">1.15</span>
                    </div>
                    <div className="stat-box">
                      <span className="stat-label">Max Pain</span>
                      <span className="stat-value">25600</span>
                    </div>
                    <div className="stat-box">
                      <span className="stat-label">VIX</span>
                      <span className="stat-value orange">13.5</span>
                    </div>
                  </div>

                  {/* Mini Option Chain */}
                  <div className="preview-chain-new">
                    <div className="chain-row-new header">
                      <span>CE</span>
                      <span>Strike</span>
                      <span>PE</span>
                    </div>
                    <div className="chain-row-new">
                      <span className="ce">245.50</span>
                      <span className="strike">25600</span>
                      <span className="pe">142.30</span>
                    </div>
                    <div className="chain-row-new atm">
                      <span className="ce">185.75</span>
                      <span className="strike">25650</span>
                      <span className="pe">168.40</span>
                    </div>
                    <div className="chain-row-new">
                      <span className="ce">134.20</span>
                      <span className="strike">25700</span>
                      <span className="pe">205.60</span>
                    </div>
                  </div>

                  {/* Bottom Navigation */}
                  <div className="preview-nav">
                    <div className="nav-item active">
                      <span className="nav-icon">📊</span>
                      <span>Chain</span>
                    </div>
                    <div className="nav-item">
                      <span className="nav-icon">🧮</span>
                      <span>Calculate</span>
                    </div>
                    <div className="nav-item">
                      <span className="nav-icon">🤖</span>
                      <span>AI</span>
                    </div>
                    <div className="nav-item">
                      <span className="nav-icon">💼</span>
                      <span>Trade</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Floating Cards */}
            <div className="floating-card card-1">
              <span className="card-icon">📈</span>
              <div className="card-content">
                <span className="card-title">Greeks</span>
                <span className="card-value">Δ 0.55 | Θ -12</span>
              </div>
            </div>
            <div className="floating-card card-2">
              <span className="card-icon">💰</span>
              <div className="card-content">
                <span className="card-title">P&L</span>
                <span className="card-value green">+₹15,420</span>
              </div>
            </div>
            <div className="floating-card card-3">
              <span className="card-icon">🎯</span>
              <div className="card-content">
                <span className="card-title">Accuracy</span>
                <span className="card-value">78%</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Post-Hero Banner Ad */}
      <div className="container">
        <AdBanner
          slot={ADS_CONFIG.adUnits.heroLeaderboard}
          placement="heroLeaderboard"
          className="ad-hero-banner"
        />
      </div>
    </section>
  )
}

export default Hero
