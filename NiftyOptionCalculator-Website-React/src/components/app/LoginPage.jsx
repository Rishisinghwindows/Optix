import React, { useState, useRef, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import {
  signInWithGoogle,
  signInWithFacebook,
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
  const { sendOTP, verifyOTP, loginWithGoogle, loginWithFacebook, isAuthenticated, isLoading } = useAuth();

  const [step, setStep] = useState('phone'); // 'phone' or 'otp'
  const [phone, setPhone] = useState('');
  const [otp, setOtp] = useState(['', '', '', '', '', '']);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [message, setMessage] = useState('');
  const [socialLoading, setSocialLoading] = useState(null); // 'google' or 'facebook'
  const [theme, setTheme] = useState(() => {
    if (typeof window !== 'undefined') {
      const saved = localStorage.getItem('theme');
      if (saved) return saved;
      return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
    }
    return 'dark';
  });

  const otpRefs = useRef([]);
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
          } else if (result.provider === 'facebook') {
            await loginWithFacebook(result.accessToken);
          }

          navigate(from, { replace: true });
        } else {
          // No result but had pending redirect - might have been cancelled
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
  }, [loginWithGoogle, loginWithFacebook, navigate, from]);

  // Countdown timer for resend
  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => setCountdown(countdown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [countdown]);

  // Handle Google login with Firebase
  const handleGoogleLogin = async () => {
    setSocialLoading('google');
    setError('');

    try {
      const result = await signInWithGoogle();

      // If using redirect flow, the page will reload - don't do anything else
      if (result.pending) {
        // Keep loading state - page will redirect
        return;
      }

      // Popup flow completed successfully
      await loginWithGoogle(result.idToken);
      navigate(from, { replace: true });
    } catch (err) {
      if (err.code === 'auth/popup-closed-by-user') {
        setError('Login cancelled.');
      } else if (err.code === 'auth/popup-blocked') {
        // This shouldn't happen anymore since we fall back to redirect
        setError('Redirecting to Google sign-in...');
        // Try again with redirect
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

  // Handle Facebook login with Firebase
  const handleFacebookLogin = async () => {
    setSocialLoading('facebook');
    setError('');

    try {
      const result = await signInWithFacebook();

      // If using redirect flow, the page will reload
      if (result.pending) {
        return;
      }

      // Popup flow completed successfully
      await loginWithFacebook(result.accessToken);
      navigate(from, { replace: true });
    } catch (err) {
      if (err.code === 'auth/popup-closed-by-user') {
        setError('Login cancelled.');
      } else if (err.code === 'auth/popup-blocked') {
        setError('Redirecting to Facebook sign-in...');
        try {
          await signInWithFacebook(true);
        } catch (redirectErr) {
          setError('Facebook login failed. Please try again.');
        }
      } else if (err.code === 'auth/account-exists-with-different-credential') {
        setError('An account already exists with this email using a different sign-in method.');
      } else {
        setError(err.message || 'Facebook login failed. Please try again.');
      }
      setSocialLoading(null);
    }
  };

  const handlePhoneChange = (e) => {
    const value = e.target.value.replace(/\D/g, '');
    if (value.length <= 10) {
      setPhone(value);
      setError('');
    }
  };

  const handleSendOTP = async (e) => {
    e.preventDefault();
    if (phone.length !== 10) {
      setError('Please enter a valid 10-digit phone number');
      return;
    }

    setLoading(true);
    setError('');

    try {
      const response = await sendOTP(`+91${phone}`);
      setMessage(response.message || 'OTP sent successfully!');
      setCountdown(response.resend_after || 30);
      setStep('otp');
      // Focus first OTP input
      setTimeout(() => otpRefs.current[0]?.focus(), 100);
    } catch (err) {
      setError(err.message || 'Failed to send OTP. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleOtpChange = (index, value) => {
    if (!/^\d*$/.test(value)) return;

    const newOtp = [...otp];
    newOtp[index] = value;
    setOtp(newOtp);
    setError('');

    // Auto-focus next input
    if (value && index < 5) {
      otpRefs.current[index + 1]?.focus();
    }

    // Auto-submit when all digits entered
    if (newOtp.every(digit => digit) && newOtp.join('').length === 6) {
      handleVerifyOTP(newOtp.join(''));
    }
  };

  const handleOtpKeyDown = (index, e) => {
    if (e.key === 'Backspace' && !otp[index] && index > 0) {
      otpRefs.current[index - 1]?.focus();
    }
  };

  const handleVerifyOTP = async (otpCode) => {
    const code = otpCode || otp.join('');
    if (code.length !== 6) {
      setError('Please enter the complete 6-digit OTP');
      return;
    }

    setLoading(true);
    setError('');

    try {
      await verifyOTP(`+91${phone}`, code);
      navigate(from, { replace: true });
    } catch (err) {
      setError(err.message || 'Invalid OTP. Please try again.');
      setOtp(['', '', '', '', '', '']);
      otpRefs.current[0]?.focus();
    } finally {
      setLoading(false);
    }
  };

  const handleResendOTP = async () => {
    if (countdown > 0) return;

    setLoading(true);
    setError('');
    setOtp(['', '', '', '', '', '']);

    try {
      const response = await sendOTP(`+91${phone}`);
      setMessage('OTP resent successfully!');
      setCountdown(response.resend_after || 30);
      otpRefs.current[0]?.focus();
    } catch (err) {
      setError(err.message || 'Failed to resend OTP');
    } finally {
      setLoading(false);
    }
  };

  const handleBack = () => {
    setStep('phone');
    setOtp(['', '', '', '', '', '']);
    setError('');
    setMessage('');
  };

  const handleSkip = () => {
    // Continue as guest
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
              Completing {pendingProvider === 'google' ? 'Google' : 'Facebook'} sign-in...
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

        {/* Phone Input Step */}
        {step === 'phone' && (
          <form className="login-form" onSubmit={handleSendOTP}>
            <div className="input-group">
              <label>Mobile Number</label>
              <div className="phone-input-wrapper">
                <span className="country-code">+91</span>
                <input
                  type="tel"
                  placeholder="Enter your mobile number"
                  value={phone}
                  onChange={handlePhoneChange}
                  maxLength={10}
                  autoFocus
                />
              </div>
            </div>

            {error && <div className="error-message">{error}</div>}

            <button
              type="submit"
              className="primary-btn"
              disabled={loading || phone.length !== 10}
            >
              {loading ? (
                <span className="btn-loading">Sending OTP...</span>
              ) : (
                'Get OTP'
              )}
            </button>

            <div className="divider">
              <span>or continue with</span>
            </div>

            <div className="social-buttons">
              <button
                type="button"
                className="social-btn google"
                onClick={handleGoogleLogin}
                disabled={socialLoading === 'google'}
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
                    Google
                  </>
                )}
              </button>
              <button
                type="button"
                className="social-btn facebook"
                onClick={handleFacebookLogin}
                disabled={socialLoading === 'facebook'}
              >
                {socialLoading === 'facebook' ? (
                  <span className="btn-loading">Signing in...</span>
                ) : (
                  <>
                    <span className="social-icon facebook-icon">
                      <svg viewBox="0 0 24 24" width="18" height="18" fill="#1877F2">
                        <path d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z"/>
                      </svg>
                    </span>
                    Facebook
                  </>
                )}
              </button>
            </div>

            <button type="button" className="skip-btn" onClick={handleSkip}>
              Continue as Guest
            </button>
          </form>
        )}

        {/* OTP Verification Step */}
        {step === 'otp' && (
          <div className="login-form">
            <button className="back-btn" onClick={handleBack}>
              ← Back
            </button>

            <div className="otp-header">
              <h2>Verify OTP</h2>
              <p>We've sent a 6-digit code to <strong>+91 {phone}</strong></p>
            </div>

            <div className="otp-inputs">
              {otp.map((digit, index) => (
                <input
                  key={index}
                  ref={(el) => (otpRefs.current[index] = el)}
                  type="text"
                  inputMode="numeric"
                  maxLength={1}
                  value={digit}
                  onChange={(e) => handleOtpChange(index, e.target.value)}
                  onKeyDown={(e) => handleOtpKeyDown(index, e)}
                  className={error ? 'error' : ''}
                />
              ))}
            </div>

            {error && <div className="error-message">{error}</div>}
            {message && !error && <div className="success-message">{message}</div>}

            <button
              type="button"
              className="primary-btn"
              onClick={() => handleVerifyOTP()}
              disabled={loading || otp.join('').length !== 6}
            >
              {loading ? 'Verifying...' : 'Verify & Login'}
            </button>

            <div className="resend-section">
              {countdown > 0 ? (
                <span>Resend OTP in {countdown}s</span>
              ) : (
                <button
                  type="button"
                  className="resend-btn"
                  onClick={handleResendOTP}
                  disabled={loading}
                >
                  Resend OTP
                </button>
              )}
            </div>
          </div>
        )}

        {/* Footer */}
        <div className="login-footer">
          <p>By continuing, you agree to our <a href="#">Terms of Service</a> and <a href="#">Privacy Policy</a></p>
        </div>
      </div>
    </div>
  );
}

export default LoginPage;
