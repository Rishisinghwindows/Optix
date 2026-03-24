import { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react';
import { authAPI } from '../services/authAPI';
import { setAnalyticsUserId, setAnalyticsUserProperties, logLogin } from '../services/analytics';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  // Sync Firebase user-property 'auth_state' whenever login status changes.
  // This lets us segment analytics reports by logged-in vs guest users.
  useEffect(() => {
    setAnalyticsUserProperties({
      auth_state: isAuthenticated ? 'logged_in' : 'guest',
    });
  }, [isAuthenticated]);

  // Check auth status on mount
  useEffect(() => {
    const checkAuth = async () => {
      try {
        if (authAPI.isAuthenticated()) {
          const userData = await authAPI.getCurrentUser();
          if (userData) {
            setUser(userData);
            setIsAuthenticated(true);
            authAPI.storeUser(userData);
          } else {
            authAPI.clearTokens();
            setUser(null);
            setIsAuthenticated(false);
          }
        }
      } catch {
        authAPI.clearTokens();
        setUser(null);
        setIsAuthenticated(false);
      } finally {
        setIsLoading(false);
      }
    };

    checkAuth();
  }, []);

  // Send OTP
  const sendOTP = useCallback(async (phone) => {
    return authAPI.sendOTP(phone);
  }, []);

  // Verify OTP and login
  const verifyOTP = useCallback(async (phone, otp) => {
    const response = await authAPI.verifyOTP(phone, otp);
    setUser(response.user);
    setIsAuthenticated(true);
    // Bind Firebase Analytics to this user so all subsequent events are attributed
    if (response.user?.id) setAnalyticsUserId(String(response.user.id));
    logLogin('otp'); // Firebase standard 'login' event with method param
    return response;
  }, []);

  // Google login
  const loginWithGoogle = useCallback(async (idToken) => {
    const response = await authAPI.loginWithGoogle(idToken);
    setUser(response.user);
    setIsAuthenticated(true);
    if (response.user?.id) setAnalyticsUserId(String(response.user.id));
    logLogin('google');
    return response;
  }, []);

  // Facebook login
  const loginWithFacebook = useCallback(async (accessToken) => {
    const response = await authAPI.loginWithFacebook(accessToken);
    setUser(response.user);
    setIsAuthenticated(true);
    if (response.user?.id) setAnalyticsUserId(String(response.user.id));
    logLogin('facebook');
    return response;
  }, []);

  // Logout
  const logout = useCallback(async () => {
    await authAPI.logout();
    setUser(null);
    setIsAuthenticated(false);
  }, []);

  // Update user data
  const updateUser = useCallback((userData) => {
    setUser(userData);
    authAPI.storeUser(userData);
  }, []);

  // Refresh user data from server
  const refreshUser = useCallback(async () => {
    try {
      const userData = await authAPI.getCurrentUser();
      if (userData) {
        setUser(userData);
        authAPI.storeUser(userData);
        return userData;
      }
    } catch {
      // Silently fail — user will be prompted to re-login if needed
    }
    return null;
  }, []);

  const value = useMemo(() => ({
    user,
    isLoading,
    isAuthenticated,
    sendOTP,
    verifyOTP,
    loginWithGoogle,
    loginWithFacebook,
    logout,
    updateUser,
    refreshUser,
  }), [user, isLoading, isAuthenticated, sendOTP, verifyOTP, loginWithGoogle, loginWithFacebook, logout, updateUser, refreshUser]);

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

export default AuthContext;
