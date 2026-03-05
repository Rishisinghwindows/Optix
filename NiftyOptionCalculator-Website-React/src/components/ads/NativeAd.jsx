import { useEffect, useRef, useState } from 'react';
import { useAds } from '../../context/AdContext';
import '../../styles/Ads.css';

/**
 * NativeAd Component - In-feed ads that blend with content
 *
 * @param {string} slot - Ad slot ID from AdSense
 * @param {string} variant - Style variant: 'card', 'inline', 'feature'
 * @param {string} className - Additional CSS classes
 */
export default function NativeAd({ slot, variant = 'card', className = '' }) {
  const adRef = useRef(null);
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState(false);

  const { testMode, markAdLoaded, isAdBlockerDetected, publisherId, config } =
    useAds();

  // Check if native ads are enabled
  if (!config.enableNativeAds) {
    return null;
  }

  // Initialize ad
  useEffect(() => {
    if (testMode) {
      const timer = setTimeout(() => {
        setIsLoading(false);
        markAdLoaded(slot);
      }, 500);
      return () => clearTimeout(timer);
    }

    if (isAdBlockerDetected) {
      setHasError(true);
      setIsLoading(false);
      return;
    }

    try {
      if (window.adsbygoogle && adRef.current) {
        (window.adsbygoogle = window.adsbygoogle || []).push({});
        setIsLoading(false);
        markAdLoaded(slot);
      }
    } catch (error) {
      console.error('AdSense native ad error:', error);
      setHasError(true);
      setIsLoading(false);
    }
  }, [slot, testMode, markAdLoaded, isAdBlockerDetected]);

  // Test mode - show placeholder matching website style
  if (testMode) {
    return (
      <div className={`ad-native ad-native-${variant} ${className}`}>
        {isLoading ? (
          <div className="ad-loading">
            <div className="ad-loading-skeleton" />
          </div>
        ) : (
          <div className="ad-native-placeholder">
            <span className="ad-sponsored-label">Sponsored</span>
            <div className="ad-native-content">
              <div className="ad-native-icon">
                <svg
                  width="24"
                  height="24"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                >
                  <rect x="3" y="3" width="18" height="18" rx="2" />
                  <circle cx="8.5" cy="8.5" r="1.5" />
                  <path d="M21 15l-5-5L5 21" />
                </svg>
              </div>
              <div className="ad-native-text">
                <h4>Advertisement</h4>
                <p>Native ad placeholder - Test mode</p>
              </div>
            </div>
            <button className="ad-native-cta">Learn More</button>
          </div>
        )}
      </div>
    );
  }

  // Error state
  if (hasError) {
    return null;
  }

  return (
    <div className={`ad-native ad-native-${variant} ${className}`}>
      <span className="ad-sponsored-label">Sponsored</span>
      {isLoading && (
        <div className="ad-loading">
          <div className="ad-loading-skeleton" />
        </div>
      )}
      <ins
        ref={adRef}
        className="adsbygoogle"
        style={{ display: 'block' }}
        data-ad-client={publisherId}
        data-ad-slot={slot}
        data-ad-format="fluid"
        data-ad-layout-key="-gw-3+1f-3d+2z"
      />
    </div>
  );
}
