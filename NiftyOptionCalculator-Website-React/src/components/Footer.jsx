import React from 'react'
import { useTranslation } from 'react-i18next'

function Footer() {
  const { t } = useTranslation()

  return (
    <footer className="footer">
      <div className="container">
        <div className="footer-content">
          <div className="footer-brand">
            <a href="#" className="logo">
              <span className="logo-icon">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                  <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2"/>
                  <path d="M8 14L12 8L16 14" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              </span>
              <span className="logo-text">Optix</span>
            </a>
            <p>{t('footer.description')}</p>
          </div>
          <div className="footer-links">
            <div className="footer-column">
              <h4>{t('footer.product')}</h4>
              <a href="#calculator">{t('footer.links.calculator')}</a>
              <a href="#features">{t('footer.links.features')}</a>
              <a href="#screenshots">{t('footer.links.screenshots')}</a>
              <a href="#pricing">{t('footer.links.pricing')}</a>
            </div>
            <div className="footer-column">
              <h4>{t('footer.legal')}</h4>
              <a href="/legal/privacy">{t('footer.links.privacyPolicy')}</a>
              <a href="/legal/terms">{t('footer.links.termsOfService')}</a>
              <a href="/legal/disclaimer">{t('footer.links.disclaimer')}</a>
            </div>
            <div className="footer-column">
              <h4>{t('footer.support')}</h4>
              <a href="#">{t('footer.links.contact')}</a>
              <a href="#">{t('footer.links.faq')}</a>
            </div>
          </div>
        </div>
        <div className="footer-bottom">
          <p>{t('footer.copyright')}</p>
          <p className="disclaimer">
            {t('footer.disclaimer')}
          </p>
        </div>
      </div>
    </footer>
  )
}

export default Footer
