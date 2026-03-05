import React from 'react'
import { useTranslation } from 'react-i18next'
import { NativeAd } from './ads'
import { ADS_CONFIG } from '../config/adsConfig'

const featureIcons = [
  {
    key: 'optionChain',
    icon: (
      <svg width="28" height="28" viewBox="0 0 24 24" fill="white">
        <path d="M3 13h2v8H3v-8zm4-6h2v14H7V7zm4 3h2v11h-2V10zm4-6h2v17h-2V4zm4 9h2v8h-2v-8z"/>
      </svg>
    ),
    gradient: 'linear-gradient(135deg, #00C853, #00E676)'
  },
  {
    key: 'calculator',
    icon: (
      <svg width="28" height="28" viewBox="0 0 24 24" fill="white">
        <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-5 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V7h10v2z"/>
      </svg>
    ),
    gradient: 'linear-gradient(135deg, #2196F3, #03A9F4)'
  },
  {
    key: 'greeks',
    icon: (
      <svg width="28" height="28" viewBox="0 0 24 24" fill="white">
        <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
      </svg>
    ),
    gradient: 'linear-gradient(135deg, #9C27B0, #E040FB)'
  },
  {
    key: 'paperTrading',
    icon: (
      <svg width="28" height="28" viewBox="0 0 24 24" fill="white">
        <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1.41 16.09V20h-2.67v-1.93c-1.71-.36-3.16-1.46-3.27-3.4h1.96c.1 1.05.82 1.87 2.65 1.87 1.96 0 2.4-.98 2.4-1.59 0-.83-.44-1.61-2.67-2.14-2.48-.6-4.18-1.62-4.18-3.67 0-1.72 1.39-2.84 3.11-3.21V4h2.67v1.95c1.86.45 2.79 1.86 2.85 3.39H14.3c-.05-1.11-.64-1.87-2.22-1.87-1.5 0-2.4.68-2.4 1.64 0 .84.65 1.39 2.67 1.91s4.18 1.39 4.18 3.91c-.01 1.83-1.38 2.83-3.12 3.16z"/>
      </svg>
    ),
    gradient: 'linear-gradient(135deg, #FF9800, #FFB74D)'
  },
  {
    key: 'learn',
    icon: (
      <svg width="28" height="28" viewBox="0 0 24 24" fill="white">
        <path d="M12 3L1 9l4 2.18v6L12 21l7-3.82v-6l2-1.09V17h2V9L12 3zm6.82 6L12 12.72 5.18 9 12 5.28 18.82 9zM17 15.99l-5 2.73-5-2.73v-3.72L12 15l5-2.73v3.72z"/>
      </svg>
    ),
    gradient: 'linear-gradient(135deg, #E91E63, #F48FB1)'
  },
  {
    key: 'aiInsights',
    icon: (
      <svg width="28" height="28" viewBox="0 0 24 24" fill="white">
        <path d="M21 8c-1.45 0-2.26 1.44-1.93 2.51l-3.55 3.56c-.3-.09-.74-.09-1.04 0l-2.55-2.55C12.27 10.45 11.46 9 10 9c-1.45 0-2.27 1.44-1.93 2.52l-4.56 4.55C2.44 15.74 1 16.55 1 18c0 1.1.9 2 2 2 1.45 0 2.26-1.44 1.93-2.51l4.55-4.56c.3.09.74.09 1.04 0l2.55 2.55C12.73 16.55 13.54 18 15 18c1.45 0 2.27-1.44 1.93-2.52l3.56-3.55c1.07.33 2.51-.48 2.51-1.93 0-1.1-.9-2-2-2z"/>
      </svg>
    ),
    gradient: 'linear-gradient(135deg, #00BCD4, #4DD0E1)'
  },
  {
    key: 'ipoDashboard',
    icon: (
      <svg width="28" height="28" viewBox="0 0 24 24" fill="white">
        <path d="M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-1 9h-4v4h-2v-4H9V9h4V5h2v4h4v2z"/>
      </svg>
    ),
    gradient: 'linear-gradient(135deg, #4CAF50, #8BC34A)'
  },
  {
    key: 'upstox',
    icon: (
      <svg width="28" height="28" viewBox="0 0 24 24" fill="white">
        <path d="M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM17 13l-5 5-5-5h3V9h4v4h3z"/>
      </svg>
    ),
    gradient: 'linear-gradient(135deg, #607D8B, #90A4AE)'
  },
  {
    key: 'multiLanguage',
    icon: (
      <svg width="28" height="28" viewBox="0 0 24 24" fill="white">
        <path d="M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zm4.24 16L12 15.45 7.77 18l1.12-4.81-3.73-3.23 4.92-.42L12 5l1.92 4.53 4.92.42-3.73 3.23L16.23 18z"/>
      </svg>
    ),
    gradient: 'linear-gradient(135deg, #FFC107, #FFEB3B)'
  }
]

function Features() {
  const { t } = useTranslation()

  const features = featureIcons.map(item => ({
    ...item,
    title: t(`features.items.${item.key}.title`),
    description: t(`features.items.${item.key}.description`)
  }))

  return (
    <section id="features" className="features">
      <div className="container">
        <div className="section-header">
          <span className="section-badge">{t('features.title')}</span>
          <h2 className="section-title">{t('features.subtitle')}</h2>
          <p className="section-subtitle">{t('features.description')}</p>
        </div>
        <div className="features-grid">
          {features.map((feature, index) => (
            <React.Fragment key={index}>
              <div className="feature-card">
                <div className="feature-icon" style={{ background: feature.gradient }}>
                  {feature.icon}
                </div>
                <h3>{feature.title}</h3>
                <p>{feature.description}</p>
              </div>
              {/* Insert native ad after every 3rd feature */}
              {(index + 1) % 3 === 0 && index < features.length - 1 && (
                <NativeAd
                  slot={ADS_CONFIG.adUnits.featuresNative}
                  variant="feature"
                  className="ad-features-native"
                />
              )}
            </React.Fragment>
          ))}
        </div>
      </div>
    </section>
  )
}

export default Features
