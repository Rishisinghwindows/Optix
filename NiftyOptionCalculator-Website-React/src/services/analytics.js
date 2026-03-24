/**
 * @file analytics.js — Unified analytics service for Optix Web.
 *
 * Two tracking channels:
 *  1. Custom visitor tracking — POST /api/v1/track/visit to our backend
 *     (powers the admin analytics dashboard, identified by a canvas-fingerprint visitor ID).
 *  2. Firebase Analytics — standard Google Analytics events (screen_view, login, feature_used, etc.)
 *     enriched with PLATFORM_PARAMS so web events can be distinguished in the Firebase console.
 *
 * All public functions are safe to call before Firebase is ready — they silently no-op.
 */

import { getAnalytics, logEvent as firebaseLogEvent, setUserId, setUserProperties } from 'firebase/analytics';
import { app as firebaseApp } from './firebase';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'https://api.optix.d23ai.in';

// Firebase Analytics instance
let analyticsInstance = null;

// Platform params injected into every event
const PLATFORM_PARAMS = {
  platform: 'web',
  app_version: import.meta.env.VITE_APP_VERSION || '1.0.0',
};

// ============== Custom Visitor Tracking ==============

/**
 * Generate a semi-stable visitor ID using canvas fingerprinting + random salt.
 * Persisted in localStorage so the same browser gets the same ID across sessions.
 */
function getVisitorId() {
  let visitorId = localStorage.getItem('optix_visitor_id');
  if (!visitorId) {
    // Generate a fingerprint-like ID based on browser characteristics
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    ctx.textBaseline = 'top';
    ctx.font = '14px Arial';
    ctx.fillText('Optix Analytics', 2, 2);
    const canvasData = canvas.toDataURL();

    const fingerprint = [
      navigator.userAgent,
      navigator.language,
      screen.width + 'x' + screen.height,
      new Date().getTimezoneOffset(),
      canvasData.slice(-50),
      Math.random().toString(36).substring(2, 15)
    ].join('|');

    // Simple hash function
    let hash = 0;
    for (let i = 0; i < fingerprint.length; i++) {
      const char = fingerprint.charCodeAt(i);
      hash = ((hash << 5) - hash) + char;
      hash = hash & hash;
    }

    visitorId = 'v_' + Math.abs(hash).toString(36) + '_' + Date.now().toString(36);
    localStorage.setItem('optix_visitor_id', visitorId);
  }
  return visitorId;
}

/** Session-scoped ID (sessionStorage) — new tab/restart = new session. */
function getSessionId() {
  let sessionId = sessionStorage.getItem('optix_session_id');
  if (!sessionId) {
    sessionId = 's_' + Date.now().toString(36) + '_' + Math.random().toString(36).substring(2, 9);
    sessionStorage.setItem('optix_session_id', sessionId);
  }
  return sessionId;
}

/** Fire-and-forget POST to the backend visitor tracking endpoint. */
export async function trackVisit(pageUrl = null, pageTitle = null) {
  try {
    const visitorId = getVisitorId();
    const sessionId = getSessionId();

    const data = {
      visitor_id: visitorId,
      session_id: sessionId,
      page_url: pageUrl || window.location.pathname,
      page_title: pageTitle || document.title,
      referrer: document.referrer || null
    };

    // Send tracking data (fire and forget - don't wait for response)
    fetch(`${API_BASE_URL}/api/v1/track/visit`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(data),
    }).catch(err => {
      // Silently fail - analytics shouldn't break the app
      console.debug('Analytics tracking failed:', err);
    });
  } catch (err) {
    // Silently fail
    console.debug('Analytics error:', err);
  }
}

/** Convenience alias for trackVisit — called on React Router route changes. */
export function trackPageView(pageUrl, pageTitle) {
  trackVisit(pageUrl, pageTitle);
}

/**
 * Bootstrap both tracking channels. Must be called once (typically in AnalyticsTracker).
 * - Sends the first custom visit event
 * - Lazily initialises the Firebase Analytics SDK
 */
export function initAnalytics() {
  if (typeof window !== 'undefined') {
    trackVisit();

    // Firebase Analytics — may fail in dev/ad-blocked environments
    try {
      analyticsInstance = getAnalytics(firebaseApp);
    } catch (e) {
      console.warn('Firebase Analytics not available:', e.message);
    }

    // Placeholder: could send session-duration data on unload
    window.addEventListener('beforeunload', () => {});
  }
}

// ============== Firebase Analytics Functions ==============

/**
 * Log a custom event to Firebase Analytics
 * @param {string} eventName - Event name (e.g. 'button_click', 'feature_used')
 * @param {object} params - Optional event parameters
 */
export function logEvent(eventName, params = {}) {
  if (analyticsInstance) {
    try {
      firebaseLogEvent(analyticsInstance, eventName, { ...PLATFORM_PARAMS, ...params });
    } catch (e) {
      console.debug('Firebase logEvent failed:', e.message);
    }
  }
}

/**
 * Set the user ID for Firebase Analytics
 * @param {string} userId
 */
export function setAnalyticsUserId(userId) {
  if (analyticsInstance) {
    try {
      setUserId(analyticsInstance, userId);
      setUserProperties(analyticsInstance, { platform: 'web' });
    } catch (e) {
      console.debug('Firebase setUserId failed:', e.message);
    }
  }
}

/**
 * Set user properties for Firebase Analytics (e.g. auth state)
 * @param {object} properties - Key-value pairs of user properties
 */
export function setAnalyticsUserProperties(properties) {
  if (analyticsInstance) {
    try {
      setUserProperties(analyticsInstance, properties);
    } catch (e) {
      console.debug('Firebase setUserProperties failed:', e.message);
    }
  }
}

/**
 * Log a screen/page view to Firebase Analytics
 * @param {string} screenName
 */
export function logScreenView(screenName) {
  if (analyticsInstance) {
    try {
      firebaseLogEvent(analyticsInstance, 'screen_view', {
        ...PLATFORM_PARAMS,
        firebase_screen: screenName,
        firebase_screen_class: screenName,
      });
    } catch (e) {
      console.debug('Firebase screen_view failed:', e.message);
    }
  }
}

/**
 * Log a login event to Firebase Analytics
 * @param {string} method - Login method (e.g. 'google', 'facebook', 'otp')
 */
export function logLogin(method) {
  if (analyticsInstance) {
    try {
      firebaseLogEvent(analyticsInstance, 'login', { ...PLATFORM_PARAMS, method });
    } catch (e) {
      console.debug('Firebase login event failed:', e.message);
    }
  }
}

/**
 * Log a feature usage event to Firebase Analytics
 * @param {string} feature - Feature name
 */
export function logFeatureUsed(feature) {
  if (analyticsInstance) {
    try {
      firebaseLogEvent(analyticsInstance, 'feature_used', { ...PLATFORM_PARAMS, feature_name: feature });
    } catch (e) {
      console.debug('Firebase feature_used event failed:', e.message);
    }
  }
}

export default {
  trackVisit,
  trackPageView,
  initAnalytics,
  getVisitorId,
  getSessionId,
  logEvent,
  setAnalyticsUserId,
  setAnalyticsUserProperties,
  logScreenView,
  logLogin,
  logFeatureUsed,
};
