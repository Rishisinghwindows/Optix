import React from 'react'
import { useTranslation } from 'react-i18next'

function Download() {
  const { t } = useTranslation()
  const features = t('download.features', { returnObjects: true })

  return (
    <section id="download" className="download">
      <div className="container">
        <div className="download-content">
          <h2>{t('download.title')}</h2>
          <p>{t('download.subtitle')}</p>
          <div className="download-buttons">
            <a href="#" className="app-store-btn">
              <svg width="30" height="30" viewBox="0 0 24 24" fill="currentColor">
                <path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.81-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z"/>
              </svg>
              <div className="btn-text">
                <span className="btn-label">{t('download.appStore')}</span>
                <span className="btn-store">App Store</span>
              </div>
            </a>
          </div>
          {Array.isArray(features) && features.length > 0 && (
            <ul className="download-features">
              {features.map((feature, index) => (
                <li key={index}>{feature}</li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </section>
  )
}

export default Download
