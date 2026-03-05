import { useEffect, useRef, useState } from 'react';
import { useAds } from '../../context/AdContext';
import { getResponsiveAdSize } from '../../config/adsConfig';
import '../../styles/Ads.css';

/**
 * AdBanner Component - Display ads (standard IAB sizes)
 *
 * @param {string} slot - Ad slot ID from AdSense
 * @param {string} placement - Placement name for responsive sizing
 * @param {string} format - Ad format: 'auto', 'horizontal', 'vertical', 'rectangle'
 * @param {boolean} responsive - Enable responsive sizing
 * @param {string} className - Additional CSS classes
 */
export default function AdBanner({
  slot,
  placement = 'mediumRectangle',
  format = 'auto',
  responsive = true,
  className = '',
}) {
  const adRef = useRef(null);
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState(false);
  const [adSize, setAdSize] = useState(getResponsiveAdSize(placement));

  const { testMode, markAdLoaded, isAdBlockerDetected, publisherId } = useAds();

  // Update size on window resize
  useEffect(() => {
    const handleResize = () => {
      setAdSize(getResponsiveAdSize(placement));
    };

    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, [placement]);

  // Initialize ad
  useEffect(() => {
    if (testMode) {
      // In test mode, simulate ad loading
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

    // Push ad to AdSense
    try {
      if (window.adsbygoogle && adRef.current) {
        (window.adsbygoogle = window.adsbygoogle || []).push({});
        setIsLoading(false);
        markAdLoaded(slot);
      }
    } catch (error) {
      console.error('AdSense error:', error);
      setHasError(true);
      setIsLoading(false);
    }
  }, [slot, testMode, markAdLoaded, isAdBlockerDetected]);

  // Test mode - show placeholder
  if (testMode) {
    return (
      <div
        className={`ad-container ad-banner ${className}`}
        style={{
          width: responsive ? '100%' : `${adSize.width}px`,
          maxWidth: `${adSize.width}px`,
          height: `${adSize.height}px`,
        }}
      >
        {isLoading ? (
          <div className="ad-loading">
            <div className="ad-loading-skeleton" />
          </div>
        ) : (
          <div className="ad-placeholder">
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
    );
  }

  // Error state
  if (hasError) {
    return null; // Silently fail - don't show empty ad space
  }

  return (
    <div
      className={`ad-container ad-banner ${className}`}
      style={{
        width: responsive ? '100%' : `${adSize.width}px`,
        maxWidth: `${adSize.width}px`,
        minHeight: `${adSize.height}px`,
      }}
    >
      {isLoading && (
        <div className="ad-loading">
          <div className="ad-loading-skeleton" />
        </div>
      )}
      <ins
        ref={adRef}
        className="adsbygoogle"
        style={{
          display: 'block',
          width: responsive ? '100%' : `${adSize.width}px`,
          height: `${adSize.height}px`,
        }}
        data-ad-client={publisherId}
        data-ad-slot={slot}
        data-ad-format={format}
        data-full-width-responsive={responsive ? 'true' : 'false'}
      />
    </div>
  );
}
