import { useEffect, useRef, useState } from 'react';
import { useAds } from '../../context/AdContext';
import { getResponsiveAdSize } from '../../config/adsConfig';
import '../../styles/Ads.css';

/**
 * AnchorAd Component - Sticky bottom ad
 *
 * @param {string} slot - Ad slot ID from AdSense
 * @param {string} className - Additional CSS classes
 */
export default function AnchorAd({ slot, className = '' }) {
  const adRef = useRef(null);
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState(false);
  const [adSize, setAdSize] = useState(getResponsiveAdSize('anchorBottom'));

  const {
    testMode,
    markAdLoaded,
    isAdBlockerDetected,
    publisherId,
    config,
    anchorDismissed,
    dismissAnchorAd,
  } = useAds();

  // Update size on window resize
  useEffect(() => {
    const handleResize = () => {
      setAdSize(getResponsiveAdSize('anchorBottom'));
    };

    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  // Initialize ad
  useEffect(() => {
    if (!config.enableAnchorAds || anchorDismissed) {
      return;
    }

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
      console.error('AdSense anchor ad error:', error);
      setHasError(true);
      setIsLoading(false);
    }
  }, [
    slot,
    testMode,
    markAdLoaded,
    isAdBlockerDetected,
    config.enableAnchorAds,
    anchorDismissed,
  ]);

  // Don't render if anchor ads disabled or dismissed
  if (!config.enableAnchorAds || anchorDismissed || hasError) {
    return null;
  }

  // Test mode - show placeholder
  if (testMode) {
    return (
      <div className={`ad-anchor ${className}`}>
        <div
          className="ad-anchor-container"
          style={{ maxWidth: `${adSize.width}px` }}
        >
          <button
            className="ad-anchor-close"
            onClick={dismissAnchorAd}
            aria-label="Close advertisement"
          >
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
            >
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
          {isLoading ? (
            <div className="ad-loading" style={{ height: `${adSize.height}px` }}>
              <div className="ad-loading-skeleton" />
            </div>
          ) : (
            <div
              className="ad-placeholder"
              style={{ height: `${adSize.height}px` }}
            >
              <div className="ad-placeholder-content">
                <span className="ad-placeholder-label">Advertisement</span>
                <span className="ad-placeholder-size">
                  {adSize.width} x {adSize.height}
                </span>
                <span className="ad-placeholder-test">Test Mode</span>
              </div>
            </div>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className={`ad-anchor ${className}`}>
      <div
        className="ad-anchor-container"
        style={{ maxWidth: `${adSize.width}px` }}
      >
        <button
          className="ad-anchor-close"
          onClick={dismissAnchorAd}
          aria-label="Close advertisement"
        >
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
          >
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
        {isLoading && (
          <div className="ad-loading" style={{ height: `${adSize.height}px` }}>
            <div className="ad-loading-skeleton" />
          </div>
        )}
        <ins
          ref={adRef}
          className="adsbygoogle"
          style={{
            display: 'block',
            width: '100%',
            height: `${adSize.height}px`,
          }}
          data-ad-client={publisherId}
          data-ad-slot={slot}
          data-ad-format="horizontal"
          data-full-width-responsive="true"
        />
      </div>
    </div>
  );
}
