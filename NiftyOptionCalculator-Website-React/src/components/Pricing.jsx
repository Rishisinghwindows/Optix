import React from 'react'
import { useTranslation } from 'react-i18next'

function Pricing() {
  const { t } = useTranslation()

  const freeFeatures = t('pricing.free.features', { returnObjects: true })
  const proFeatures = t('pricing.pro.features', { returnObjects: true })

  return (
    <section id="pricing" className="pricing">
      <div className="container">
        <div className="section-header">
          <span className="section-badge">{t('pricing.title')}</span>
          <h2 className="section-title">{t('pricing.subtitle')}</h2>
          <p className="section-subtitle">{t('pricing.free.description')}</p>
        </div>
        <div className="pricing-grid">
          <div className="pricing-card">
            <div className="pricing-header">
              <h3>{t('pricing.free.name')}</h3>
              <div className="pricing-price">
                <span className="currency">₹</span>
                <span className="amount">{t('pricing.free.price')}</span>
                <span className="period">{t('pricing.free.period')}</span>
              </div>
            </div>
            <ul className="pricing-features">
              {Array.isArray(freeFeatures) && freeFeatures.map((feature, index) => (
                <li key={index}>
                  <span className={feature.disabled ? 'x' : 'check'}>
                    {feature.disabled ? '✗' : '✓'}
                  </span> {feature.text}
                </li>
              ))}
            </ul>
            <a href="#download" className="btn btn-secondary btn-block">{t('pricing.free.cta')}</a>
          </div>

          <div className="pricing-card featured">
            <div className="pricing-badge">{t('pricing.pro.badge')}</div>
            <div className="pricing-header">
              <h3>{t('pricing.pro.name')}</h3>
              <div className="pricing-price">
                <span className="currency">₹</span>
                <span className="amount">{t('pricing.pro.price')}</span>
                <span className="period">{t('pricing.pro.period')}</span>
              </div>
            </div>
            <ul className="pricing-features">
              {Array.isArray(proFeatures) && proFeatures.map((feature, index) => (
                <li key={index}>
                  <span className="check">✓</span> {feature}
                </li>
              ))}
            </ul>
            <a href="#download" className="btn btn-primary btn-block">{t('pricing.pro.cta')}</a>
          </div>
        </div>
      </div>
    </section>
  )
}

export default Pricing
