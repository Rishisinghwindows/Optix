/**
 * Google AdSense Configuration
 *
 * Replace the placeholder IDs with actual AdSense IDs from your dashboard:
 * - Publisher ID: Found in AdSense Account Settings
 * - Ad Unit IDs: Created in AdSense > Ads > By ad unit
 */

export const ADS_CONFIG = {
  // Google AdSense Publisher ID
  // Replace with your actual publisher ID (format: ca-pub-XXXXXXXXXXXXXXXX)
  publisherId: 'ca-pub-6616355428134778',

  // Ad Unit IDs for different placements
  adUnits: {
    // Landing Page Ad Units
    heroLeaderboard: 'XXXXXXXXXX',      // Post-hero banner (728x90 / 320x100)
    featuresNative: 'XXXXXXXXXX',       // Between feature cards (native)
    preFooterRectangle: 'XXXXXXXXXX',   // Before footer (336x280)
    anchorBottom: 'XXXXXXXXXX',         // Sticky bottom (728x90 / 320x50)

    // App Pages Ad Units
    appHeaderBanner: 'XXXXXXXXXX',      // Below app header tabs (728x90 / 320x100)
    optionChainSidebar: 'XXXXXXXXXX',   // Option chain sidebar (300x250)
    calculatorResults: 'XXXXXXXXXX',    // After calculator results (300x250)
    paperTradingNative: 'XXXXXXXXXX',   // Paper trading in-feed (native)
    educationSidebar: 'XXXXXXXXXX',     // Education sidebar (300x600 / 300x250)
  },

  // Feature Flags
  enableAnchorAds: true,          // Enable sticky bottom ads
  enableNativeAds: true,          // Enable in-feed native ads
  showAdsToLoggedIn: true,        // Show ads to authenticated users

  // Development Mode
  // Set to true to show placeholder ads instead of real ones
  testMode: false,

  // Ad refresh interval in milliseconds (0 = no refresh)
  // Note: AdSense policies limit refresh to minimum 30 seconds
  refreshInterval: 0,

  // Supported ad sizes (IAB standard)
  sizes: {
    leaderboard: { width: 728, height: 90 },
    mobileLeaderboard: { width: 320, height: 100 },
    mobileBanner: { width: 320, height: 50 },
    mediumRectangle: { width: 300, height: 250 },
    largeRectangle: { width: 336, height: 280 },
    halfPage: { width: 300, height: 600 },
    skyscraper: { width: 160, height: 600 },
  },
};

// Helper function to get responsive ad size
export const getResponsiveAdSize = (placement) => {
  const isMobile = typeof window !== 'undefined' && window.innerWidth < 768;

  const responsiveSizes = {
    heroLeaderboard: isMobile
      ? ADS_CONFIG.sizes.mobileLeaderboard
      : ADS_CONFIG.sizes.leaderboard,
    appHeaderBanner: isMobile
      ? ADS_CONFIG.sizes.mobileLeaderboard
      : ADS_CONFIG.sizes.leaderboard,
    anchorBottom: isMobile
      ? ADS_CONFIG.sizes.mobileBanner
      : ADS_CONFIG.sizes.leaderboard,
    preFooterRectangle: ADS_CONFIG.sizes.largeRectangle,
    optionChainSidebar: ADS_CONFIG.sizes.mediumRectangle,
    calculatorResults: ADS_CONFIG.sizes.mediumRectangle,
    educationSidebar: isMobile
      ? ADS_CONFIG.sizes.mediumRectangle
      : ADS_CONFIG.sizes.halfPage,
  };

  return responsiveSizes[placement] || ADS_CONFIG.sizes.mediumRectangle;
};

export default ADS_CONFIG;
