/**
 * Visitor Analytics Service
 * Tracks visitors and page views for admin analytics dashboard
 */

const API_BASE_URL = import.meta.env.VITE_API_URL || 'https://api.optix.d23ai.in';

// Generate a unique visitor ID (stored in localStorage)
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

// Get or create session ID
function getSessionId() {
  let sessionId = sessionStorage.getItem('optix_session_id');
  if (!sessionId) {
    sessionId = 's_' + Date.now().toString(36) + '_' + Math.random().toString(36).substring(2, 9);
    sessionStorage.setItem('optix_session_id', sessionId);
  }
  return sessionId;
}

// Track a page visit
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

// Track page view (can be called on route changes)
export function trackPageView(pageUrl, pageTitle) {
  trackVisit(pageUrl, pageTitle);
}

// Initialize tracking on page load
export function initAnalytics() {
  // Track initial page load
  if (typeof window !== 'undefined') {
    trackVisit();

    // Track when user leaves (for time spent calculation in future)
    window.addEventListener('beforeunload', () => {
      // Could send time spent data here
    });
  }
}

export default {
  trackVisit,
  trackPageView,
  initAnalytics,
  getVisitorId,
  getSessionId
};
