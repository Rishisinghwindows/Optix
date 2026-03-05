import React, { useState, useRef, useEffect } from 'react'
import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import FloatingChatbot from '../FloatingChatbot'
import { AdBanner } from '../ads'
import { ADS_CONFIG } from '../../config/adsConfig'
import './AppStyles.css'
import './LoginStyles.css'

// Theme icons
const SunIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <circle cx="12" cy="12" r="5"/>
    <line x1="12" y1="1" x2="12" y2="3"/>
    <line x1="12" y1="21" x2="12" y2="23"/>
    <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/>
    <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/>
    <line x1="1" y1="12" x2="3" y2="12"/>
    <line x1="21" y1="12" x2="23" y2="12"/>
    <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/>
    <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>
  </svg>
)

const MoonIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
  </svg>
)

const TabIcon = ({ name }) => {
  const icons = {
    chain: <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M3 13h2v8H3v-8zm4-6h2v14H7V7zm4 3h2v11h-2V10zm4-6h2v17h-2V4zm4 9h2v8h-2v-8z"/></svg>,
    screener: <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M10 18h4v-2h-4v2zM3 6v2h18V6H3zm3 7h12v-2H6v2z"/></svg>,
    calculator: <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-5 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V7h10v2z"/></svg>,
    trade: <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1.41 16.09V20h-2.67v-1.93c-1.71-.36-3.16-1.46-3.27-3.4h1.96c.1 1.05.82 1.87 2.65 1.87 1.96 0 2.4-.98 2.4-1.59 0-.83-.44-1.61-2.67-2.14-2.48-.6-4.18-1.62-4.18-3.67 0-1.72 1.39-2.84 3.11-3.21V4h2.67v1.95c1.86.45 2.79 1.86 2.85 3.39H14.3c-.05-1.11-.64-1.87-2.22-1.87-1.5 0-2.4.68-2.4 1.64 0 .84.65 1.39 2.67 1.91s4.18 1.39 4.18 3.91c-.01 1.83-1.38 2.83-3.12 3.16z"/></svg>,
    alerts: <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2zm-2 1H8v-6c0-2.48 1.51-4.5 4-4.5s4 2.02 4 4.5v6z"/></svg>,
    backtest: <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zM9 17H7v-7h2v7zm4 0h-2V7h2v10zm4 0h-2v-4h2v4z"/></svg>,
    ipo: <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-1 9h-4v4h-2v-4H9V9h4V5h2v4h4v2z"/></svg>,
    ai: <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M21 8c-1.45 0-2.26 1.44-1.93 2.51l-3.55 3.56c-.3-.09-.74-.09-1.04 0l-2.55-2.55C12.27 10.45 11.46 9 10 9c-1.45 0-2.27 1.44-1.93 2.52l-4.56 4.55C2.44 15.74 1 16.55 1 18c0 1.1.9 2 2 2 1.45 0 2.26-1.44 1.93-2.51l4.55-4.56c.3.09.74.09 1.04 0l2.55 2.55C12.73 16.55 13.54 18 15 18c1.45 0 2.27-1.44 1.93-2.52l3.56-3.55c1.07.33 2.51-.48 2.51-1.93 0-1.1-.9-2-2-2z"/></svg>,
    learn: <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M12 3L1 9l4 2.18v6L12 21l7-3.82v-6l2-1.09V17h2V9L12 3zm6.82 6L12 12.72 5.18 9 12 5.28 18.82 9zM17 15.99l-5 2.73-5-2.73v-3.72L12 15l5-2.73v3.72z"/></svg>,
    quiz: <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M11 18h2v-2h-2v2zm1-16C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm0-14c-2.21 0-4 1.79-4 4h2c0-1.1.9-2 2-2s2 .9 2 2c0 2-3 1.75-3 5h2c0-2.25 3-2.5 3-5 0-2.21-1.79-4-4-4z"/></svg>,
    journal: <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M19 3h-4.18C14.4 1.84 13.3 1 12 1c-1.3 0-2.4.84-2.82 2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 0c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zm2 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V7h10v2z"/></svg>
  }
  return icons[name] || null
}

const tabs = [
  { path: '/app/chain', icon: 'chain', label: 'Chain' },
  { path: '/app/screener', icon: 'screener', label: 'Screener' },
  { path: '/app/calculator', icon: 'calculator', label: 'Calculator' },
  { path: '/app/paper-trading', icon: 'trade', label: 'Trade' },
  { path: '/app/journal', icon: 'journal', label: 'Journal' },
  { path: '/app/alerts', icon: 'alerts', label: 'Alerts' },
  { path: '/app/backtest', icon: 'backtest', label: 'Backtest' },
  { path: '/app/ipo', icon: 'ipo', label: 'IPO' },
  { path: '/app/ai-insights', icon: 'ai', label: 'AI' },
  { path: '/app/education', icon: 'learn', label: 'Learn' },
]

function UserMenu() {
  const { user, isAuthenticated, logout } = useAuth()
  const navigate = useNavigate()
  const [isOpen, setIsOpen] = useState(false)
  const menuRef = useRef(null)

  // Close menu on outside click
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (menuRef.current && !menuRef.current.contains(event.target)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const handleLogout = async () => {
    await logout()
    setIsOpen(false)
    navigate('/app/chain')
  }

  if (!isAuthenticated) {
    return (
      <button className="login-btn-header" onClick={() => navigate('/login')}>
        Login
      </button>
    )
  }

  const getInitial = () => {
    if (user?.full_name) return user.full_name.charAt(0).toUpperCase()
    if (user?.phone) return user.phone.slice(-2)
    return 'U'
  }

  const getDisplayName = () => {
    if (user?.full_name) return user.full_name
    if (user?.phone) return user.phone.replace('+91', '')
    return 'User'
  }

  return (
    <div className="user-menu" ref={menuRef}>
      <button className="user-avatar-btn" onClick={() => setIsOpen(!isOpen)}>
        <div className="user-avatar">{getInitial()}</div>
        <span className="user-name">{getDisplayName()}</span>
        <span className="dropdown-icon">▼</span>
      </button>

      {isOpen && (
        <div className="user-dropdown">
          <div className="dropdown-header">
            <strong>{getDisplayName()}</strong>
            {user?.phone && <div className="user-phone">{user.phone}</div>}
          </div>
          <button className="dropdown-item" onClick={() => { setIsOpen(false); navigate('/app/paper-trading'); }}>
            <span>📊</span> Portfolio
          </button>
          <button className="dropdown-item" onClick={() => { setIsOpen(false); }}>
            <span>⚙️</span> Settings
          </button>
          <button className="dropdown-item logout" onClick={handleLogout}>
            <span>🚪</span> Logout
          </button>
        </div>
      )}
    </div>
  )
}

function AppLayout() {
  const { isAuthenticated, user } = useAuth()
  const [theme, setTheme] = useState(() => {
    if (typeof window !== 'undefined') {
      const saved = localStorage.getItem('theme')
      if (saved) return saved
      return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark'
    }
    return 'dark'
  })

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
    localStorage.setItem('theme', theme)
  }, [theme])

  const toggleTheme = () => {
    setTheme(prev => prev === 'dark' ? 'light' : 'dark')
  }

  return (
    <div className="app-layout">
      <header className="app-header">
        <NavLink to="/" className="app-logo">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="var(--accent-primary)" strokeWidth="2"/>
            <path d="M8 14L12 8L16 14" stroke="var(--profit)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          <span>Optix</span>
        </NavLink>
        <nav className="app-tab-bar">
          {tabs.map(tab => (
            <NavLink
              key={tab.path}
              to={tab.path}
              className={({ isActive }) => `tab-item ${isActive ? 'active' : ''}`}
            >
              <span className="tab-icon"><TabIcon name={tab.icon} /></span>
              <span className="tab-label">{tab.label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="app-header-right">
          <button
            className="theme-toggle-app"
            onClick={toggleTheme}
            aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`}
          >
            {theme === 'dark' ? <SunIcon /> : <MoonIcon />}
          </button>
          {isAuthenticated && user?.paper_trading_balance && (
            <span className="balance-badge">
              ₹{Number(user.paper_trading_balance).toLocaleString('en-IN')}
            </span>
          )}
          <UserMenu />
        </div>
      </header>

      {/* App Header Banner Ad */}
      <div className="app-header-ad">
        <AdBanner
          slot={ADS_CONFIG.adUnits.appHeaderBanner}
          placement="appHeaderBanner"
          className="ad-app-header"
        />
      </div>

      <main className="app-main">
        <Outlet />
      </main>

      {/* Floating Chatbot */}
      <FloatingChatbot />
    </div>
  )
}

export default AppLayout
