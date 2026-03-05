// Firebase Configuration
import { initializeApp } from "firebase/app";
import { getAnalytics } from "firebase/analytics";
import {
  getAuth,
  GoogleAuthProvider,
  FacebookAuthProvider,
  signInWithPopup,
  signInWithRedirect,
  getRedirectResult,
  signOut,
  browserPopupRedirectResolver
} from "firebase/auth";

// Firebase configuration
const firebaseConfig = {
  apiKey: "AIzaSyD4Jn4PhyaqtkHdwzQJpPOFZwfJm1q8p-Y",
  authDomain: "d23ai-d3862.firebaseapp.com",
  projectId: "d23ai-d3862",
  storageBucket: "d23ai-d3862.firebasestorage.app",
  messagingSenderId: "40871333220",
  appId: "1:40871333220:web:c4adce3134f2966995fe71",
  measurementId: "G-1XQDDGG1Y2"
};

// Initialize Firebase
const app = initializeApp(firebaseConfig);
const analytics = getAnalytics(app);
const auth = getAuth(app);

// Auth providers
const googleProvider = new GoogleAuthProvider();
googleProvider.addScope('email');
googleProvider.addScope('profile');
// Set custom parameters to improve compatibility
googleProvider.setCustomParameters({
  prompt: 'select_account'
});

const facebookProvider = new FacebookAuthProvider();
facebookProvider.addScope('email');
facebookProvider.addScope('public_profile');

// Track if we're using redirect flow
let pendingRedirect = false;

/**
 * Check for redirect result on page load
 * Call this in your app initialization
 * @returns {Promise<{user: object, idToken: string, provider: string} | null>}
 */
export async function checkRedirectResult() {
  try {
    const result = await getRedirectResult(auth);
    if (result) {
      const idToken = await result.user.getIdToken();
      const providerId = result.providerId;

      // For Facebook, also get the access token
      if (providerId === 'facebook.com') {
        const credential = FacebookAuthProvider.credentialFromResult(result);
        return {
          user: result.user,
          idToken,
          accessToken: credential?.accessToken,
          provider: 'facebook'
        };
      }

      return {
        user: result.user,
        idToken,
        provider: 'google'
      };
    }
    return null;
  } catch (error) {
    console.error('Redirect result error:', error);
    // Clear any pending state
    pendingRedirect = false;
    throw error;
  }
}

/**
 * Sign in with Google - uses popup with fallback to redirect
 * @param {boolean} useRedirect - Force redirect flow instead of popup
 * @returns {Promise<{user: object, idToken: string}>}
 */
export async function signInWithGoogle(useRedirect = false) {
  try {
    // Try popup first (better UX when it works)
    if (!useRedirect) {
      try {
        const result = await signInWithPopup(auth, googleProvider, browserPopupRedirectResolver);
        const idToken = await result.user.getIdToken();
        return {
          user: result.user,
          idToken,
        };
      } catch (popupError) {
        // If popup fails due to COOP or being blocked, fall back to redirect
        if (
          popupError.code === 'auth/popup-blocked' ||
          popupError.code === 'auth/popup-closed-by-user' ||
          popupError.code === 'auth/cancelled-popup-request' ||
          popupError.message?.includes('Cross-Origin-Opener-Policy')
        ) {
          console.log('Popup blocked, falling back to redirect flow');
          useRedirect = true;
        } else {
          throw popupError;
        }
      }
    }

    // Use redirect flow
    if (useRedirect) {
      pendingRedirect = true;
      localStorage.setItem('auth_redirect_pending', 'google');
      await signInWithRedirect(auth, googleProvider);
      // This won't return - page will redirect
      return { pending: true };
    }
  } catch (error) {
    console.error('Google sign-in error:', error);
    pendingRedirect = false;
    localStorage.removeItem('auth_redirect_pending');
    throw error;
  }
}

/**
 * Sign in with Facebook - uses popup with fallback to redirect
 * @param {boolean} useRedirect - Force redirect flow instead of popup
 * @returns {Promise<{user: object, accessToken: string}>}
 */
export async function signInWithFacebook(useRedirect = false) {
  try {
    // Try popup first
    if (!useRedirect) {
      try {
        const result = await signInWithPopup(auth, facebookProvider, browserPopupRedirectResolver);
        const credential = FacebookAuthProvider.credentialFromResult(result);
        const accessToken = credential.accessToken;
        return {
          user: result.user,
          accessToken,
        };
      } catch (popupError) {
        // If popup fails, fall back to redirect
        if (
          popupError.code === 'auth/popup-blocked' ||
          popupError.code === 'auth/popup-closed-by-user' ||
          popupError.code === 'auth/cancelled-popup-request' ||
          popupError.message?.includes('Cross-Origin-Opener-Policy')
        ) {
          console.log('Popup blocked, falling back to redirect flow');
          useRedirect = true;
        } else {
          throw popupError;
        }
      }
    }

    // Use redirect flow
    if (useRedirect) {
      pendingRedirect = true;
      localStorage.setItem('auth_redirect_pending', 'facebook');
      await signInWithRedirect(auth, facebookProvider);
      // This won't return - page will redirect
      return { pending: true };
    }
  } catch (error) {
    console.error('Facebook sign-in error:', error);
    pendingRedirect = false;
    localStorage.removeItem('auth_redirect_pending');
    throw error;
  }
}

/**
 * Check if there's a pending redirect
 * @returns {string|null} - 'google', 'facebook', or null
 */
export function getPendingRedirect() {
  return localStorage.getItem('auth_redirect_pending');
}

/**
 * Clear pending redirect state
 */
export function clearPendingRedirect() {
  pendingRedirect = false;
  localStorage.removeItem('auth_redirect_pending');
}

/**
 * Sign out from Firebase
 */
export async function firebaseSignOut() {
  try {
    await signOut(auth);
    clearPendingRedirect();
  } catch (error) {
    console.error('Sign out error:', error);
  }
}

// ============== Push Notifications (FCM) ==============

let messaging = null

/**
 * Initialize FCM messaging (lazy, only when needed)
 */
async function getMessaging() {
  if (messaging) return messaging
  try {
    const { getMessaging: getMsg } = await import('firebase/messaging')
    messaging = getMsg(app)
    return messaging
  } catch (e) {
    console.warn('FCM messaging not available:', e)
    return null
  }
}

/**
 * Request notification permission and get FCM token
 * @returns {Promise<string|null>} FCM token or null if denied
 */
export async function getFCMToken() {
  try {
    const permission = await Notification.requestPermission()
    if (permission !== 'granted') return null

    const msg = await getMessaging()
    if (!msg) return null

    const { getToken } = await import('firebase/messaging')
    const token = await getToken(msg, {
      vapidKey: import.meta.env.VITE_FIREBASE_VAPID_KEY || '',
    })
    return token
  } catch (e) {
    console.warn('Failed to get FCM token:', e)
    return null
  }
}

/**
 * Listen for foreground messages
 * @param {function} callback - Called with message payload
 * @returns {function} Unsubscribe function
 */
export async function onForegroundMessage(callback) {
  try {
    const msg = await getMessaging()
    if (!msg) return () => {}

    const { onMessage } = await import('firebase/messaging')
    return onMessage(msg, (payload) => {
      callback(payload)
    })
  } catch (e) {
    console.warn('FCM foreground listener failed:', e)
    return () => {}
  }
}

export { app, analytics, auth };
