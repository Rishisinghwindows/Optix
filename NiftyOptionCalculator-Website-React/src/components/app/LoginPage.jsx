import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import {
  signInWithGoogle,
  checkRedirectResult,
  getPendingRedirect,
  clearPendingRedirect
} from '../../services/firebase';
import './LoginStyles.css';

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
);

const MoonIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
  </svg>
);

function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { loginWithGoogle, isAuthenticated, isLoading } = useAuth();

  const [error, setError] = useState('');
  const [socialLoading, setSocialLoading] = useState(null);
  const [theme, setTheme] = useState(() => {
    if (typeof window !== 'undefined') {
      const saved = localStorage.getItem('theme');
      if (saved) return saved;
      return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
    }
    return 'dark';
  });

  const from = location.state?.from?.pathname || '/app/chain';

  // Apply theme
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('theme', theme);
  }, [theme]);

  const toggleTheme = () => {
    setTheme(prev => prev === 'dark' ? 'light' : 'dark');
  };

  // Redirect if already authenticated
  useEffect(() => {
    if (isAuthenticated && !isLoading) {
      navigate(from, { replace: true });
    }
  }, [isAuthenticated, isLoading, navigate, from]);

  // Check for OAuth redirect result on page load
  useEffect(() => {
    const handleRedirectResult = async () => {
      const pendingProvider = getPendingRedirect();
      if (!pendingProvider) return;

      setSocialLoading(pendingProvider);
      setError('');

      try {
        const result = await checkRedirectResult();
        if (result) {
          clearPendingRedirect();
          if (result.provider === 'google') {
            await loginWithGoogle(result.idToken);
          }
          navigate(from, { replace: true });
        } else {
          clearPendingRedirect();
        }
      } catch (err) {
        clearPendingRedirect();
        if (err.code === 'auth/account-exists-with-different-credential') {
          setError('An account already exists with this email using a different sign-in method.');
        } else {
          setError(err.message || 'Login failed. Please try again.');
        }
      } finally {
        setSocialLoading(null);
      }
    };

    handleRedirectResult();
  }, [loginWithGoogle, navigate, from]);

  // Handle Google login with Firebase
  const handleGoogleLogin = async () => {
    setSocialLoading('google');
    setError('');

    try {
      const result = await signInWithGoogle();

      if (result.pending) {
        return;
      }

      await loginWithGoogle(result.idToken);
      navigate(from, { replace: true });
    } catch (err) {
      if (err.code === 'auth/popup-closed-by-user') {
        setError('Login cancelled.');
      } else if (err.code === 'auth/popup-blocked') {
        setError('Redirecting to Google sign-in...');
        try {
          await signInWithGoogle(true);
        } catch (redirectErr) {
          setError('Google login failed. Please try again.');
        }
      } else {
        setError(err.message || 'Google login failed. Please try again.');
      }
      setSocialLoading(null);
    }
  };

  const handleSkip = () => {
    navigate(from, { replace: true });
  };

  // Show loading when auth is loading or when there's a pending social login redirect
  const pendingProvider = getPendingRedirect();
  if (isLoading || (pendingProvider && socialLoading)) {
    return (
      <div className="login-page">
        <div className="login-container" style={{ textAlign: 'center', padding: '60px 20px' }}>
          <div className="loading-spinner" style={{ margin: '0 auto 20px' }}></div>
          {pendingProvider && (
            <p style={{ color: 'var(--text-secondary)', fontSize: '14px' }}>
              Completing Google sign-in...
            </p>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="login-page">
      {/* Theme Toggle */}
      <button
        className="login-theme-toggle"
        onClick={toggleTheme}
        aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`}
      >
        {theme === 'dark' ? <SunIcon /> : <MoonIcon />}
      </button>

      <div className="login-container">
        {/* Header */}
        <div className="login-header">
          <div className="login-logo">
            <span className="logo-icon">📊</span>
            <span className="logo-text">Optix</span>
          </div>
          <h1>Welcome to Optix</h1>
          <p>Login to access paper trading and personalized features</p>
        </div>

        <div className="login-form">
          {error && <div className="error-message">{error}</div>}

          <button
            type="button"
            className="social-btn google"
            onClick={handleGoogleLogin}
            disabled={socialLoading === 'google'}
            style={{ width: '100%', justifyContent: 'center' }}
          >
            {socialLoading === 'google' ? (
              <span className="btn-loading">Signing in...</span>
            ) : (
              <>
                <span className="social-icon google-icon">
                  <svg viewBox="0 0 24 24" width="18" height="18">
                    <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                    <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                    <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
                    <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
                  </svg>
                </span>
                Continue with Google
              </>
            )}
          </button>

          <button type="button" className="skip-btn" onClick={handleSkip}>
            Continue as Guest
          </button>
        </div>

        {/* Footer */}
        <div className="login-footer">
          <p>By continuing, you agree to our <a href="/legal/terms">Terms of Service</a> and <a href="/legal/privacy">Privacy Policy</a></p>
        </div>
      </div>
    </div>
  );
}

export default LoginPage;
