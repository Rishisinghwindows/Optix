import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { ADS_CONFIG } from '../config/adsConfig';

const AdContext = createContext(null);

export function AdProvider({ children }) {
  const [isAdBlockerDetected, setIsAdBlockerDetected] = useState(false);
  const [adsLoaded, setAdsLoaded] = useState({});
  const [anchorDismissed, setAnchorDismissed] = useState(false);
  const [isScriptLoaded, setIsScriptLoaded] = useState(false);

  // Detect ad blocker
  useEffect(() => {
    const detectAdBlocker = async () => {
      try {
        // Try to fetch a common ad-related URL
        const response = await fetch(
          'https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js',
          { method: 'HEAD', mode: 'no-cors' }
        );
        setIsAdBlockerDetected(false);
      } catch (error) {
        setIsAdBlockerDetected(true);
      }
    };

    // Also check if adsbygoogle is blocked
    const checkAdsByGoogle = () => {
      if (typeof window !== 'undefined') {
        setTimeout(() => {
          const testAd = document.createElement('div');
          testAd.innerHTML = '&nbsp;';
          testAd.className = 'adsbox';
          testAd.style.cssText = 'position:absolute;left:-9999px;';
          document.body.appendChild(testAd);

          setTimeout(() => {
            if (testAd.offsetHeight === 0) {
              setIsAdBlockerDetected(true);
            }
            document.body.removeChild(testAd);
          }, 100);
        }, 100);
      }
    };

    detectAdBlocker();
    checkAdsByGoogle();
  }, []);

  // Check if AdSense script is loaded
  useEffect(() => {
    const checkScript = () => {
      if (typeof window !== 'undefined' && window.adsbygoogle) {
        setIsScriptLoaded(true);
      }
    };

    checkScript();
    // Re-check after a short delay
    const timer = setTimeout(checkScript, 1000);
    return () => clearTimeout(timer);
  }, []);

  // Mark an ad as loaded
  const markAdLoaded = useCallback((adSlot) => {
    setAdsLoaded((prev) => ({ ...prev, [adSlot]: true }));
  }, []);

  // Dismiss anchor ad
  const dismissAnchorAd = useCallback(() => {
    setAnchorDismissed(true);
    // Re-show after 60 seconds
    if (ADS_CONFIG.enableAnchorAds) {
      setTimeout(() => {
        setAnchorDismissed(false);
      }, 60000);
    }
  }, []);

  // Reset anchor ad visibility (e.g., on page navigation)
  const resetAnchorAd = useCallback(() => {
    setAnchorDismissed(false);
  }, []);

  // Check if ads should be shown
  const shouldShowAds = useCallback(
    (isAuthenticated = false) => {
      // In test mode, always show placeholder ads
      if (ADS_CONFIG.testMode) {
        return true;
      }

      // Check if user is logged in and config allows ads for logged-in users
      if (isAuthenticated && !ADS_CONFIG.showAdsToLoggedIn) {
        return false;
      }

      // Don't show if ad blocker detected (unless in test mode)
      if (isAdBlockerDetected && !ADS_CONFIG.testMode) {
        return false;
      }

      return true;
    },
    [isAdBlockerDetected]
  );

  // Get ad unit ID by placement name
  const getAdUnitId = useCallback((placement) => {
    return ADS_CONFIG.adUnits[placement] || null;
  }, []);

  const value = {
    // State
    isAdBlockerDetected,
    adsLoaded,
    anchorDismissed,
    isScriptLoaded,

    // Config
    config: ADS_CONFIG,
    publisherId: ADS_CONFIG.publisherId,
    testMode: ADS_CONFIG.testMode,

    // Actions
    markAdLoaded,
    dismissAnchorAd,
    resetAnchorAd,
    shouldShowAds,
    getAdUnitId,
  };

  return <AdContext.Provider value={value}>{children}</AdContext.Provider>;
}

export function useAds() {
  const context = useContext(AdContext);
  if (!context) {
    throw new Error('useAds must be used within an AdProvider');
  }
  return context;
}

export default AdContext;
