import React from 'react'
import './Legal.css'

function TermsOfService() {
  return (
    <div className="legal-page">
      <div className="legal-container">
        <h1>Terms of Service</h1>
        <p className="legal-updated">Last updated: March 22, 2026</p>

        <section>
          <h2>1. Acceptance of Terms</h2>
          <p>
            By downloading, installing, or using the Optix application and website ("Service"),
            you agree to be bound by these Terms of Service ("Terms"). If you do not agree to
            these Terms, do not use the Service.
          </p>
        </section>

        <section>
          <h2>2. Description of Service</h2>
          <p>
            Optix is an options trading analysis platform for Indian stock markets. The Service provides:
          </p>
          <ul>
            <li>Real-time and delayed option chain data for NSE/BSE indices.</li>
            <li>Black-Scholes option pricing calculator with Greeks.</li>
            <li>AI-powered trade suggestions and market analysis.</li>
            <li>Paper trading with virtual capital for practice purposes.</li>
            <li>Price, IV, and OI alerts with push notifications.</li>
            <li>Trade journaling and P&L simulation tools.</li>
            <li>Educational content about options trading.</li>
          </ul>
        </section>

        <section>
          <h2>3. Not Financial Advice</h2>
          <p className="legal-important">
            IMPORTANT: Optix is for <strong>educational and informational purposes only</strong>.
            The Service does not provide financial, investment, or trading advice. All information,
            including AI-generated suggestions, option prices, Greeks, and market signals, is
            provided "as is" without any warranty of accuracy or completeness.
          </p>
          <ul>
            <li>Options trading involves significant risk of loss and is not suitable for all investors.</li>
            <li>Past performance does not guarantee future results.</li>
            <li>AI-generated insights are algorithmic suggestions, not recommendations to trade.</li>
            <li>Theoretical prices calculated using Black-Scholes may differ significantly from actual market prices.</li>
            <li>You are solely responsible for your own trading decisions.</li>
          </ul>
        </section>

        <section>
          <h2>4. User Accounts</h2>
          <ul>
            <li>You must provide accurate information when creating an account.</li>
            <li>You are responsible for maintaining the security of your account credentials.</li>
            <li>You must be at least 18 years old to use the Service.</li>
            <li>One account per person. Duplicate or fraudulent accounts may be terminated.</li>
          </ul>
        </section>

        <section>
          <h2>5. Paper Trading</h2>
          <p>
            The paper trading feature uses virtual capital (not real money) for educational purposes.
          </p>
          <ul>
            <li>Virtual trades do not represent real market orders and have no financial value.</li>
            <li>Paper trading results may not reflect actual trading conditions (slippage, liquidity, etc.).</li>
            <li>Virtual capital cannot be withdrawn, transferred, or converted to real money.</li>
          </ul>
        </section>

        <section>
          <h2>6. Broker Integration</h2>
          <p>
            Optix supports integration with third-party brokers (e.g., Upstox) for market data access.
          </p>
          <ul>
            <li>Broker connections are made via secure OAuth authentication.</li>
            <li>We do not store your broker login credentials.</li>
            <li>Broker services are subject to the respective broker's terms and conditions.</li>
            <li>We are not responsible for broker service availability, data accuracy, or trading execution.</li>
          </ul>
        </section>

        <section>
          <h2>7. Market Data</h2>
          <ul>
            <li>Market data is sourced from third-party providers and may be delayed or inaccurate.</li>
            <li>We do not guarantee real-time accuracy of option prices, OI, volume, or other market data.</li>
            <li>Data availability depends on broker connectivity and exchange feed status.</li>
            <li>Demo/sample data may be displayed when live data sources are unavailable.</li>
          </ul>
        </section>

        <section>
          <h2>8. AI-Powered Features</h2>
          <p>
            Optix uses artificial intelligence (Google Gemini) to generate trade suggestions and market analysis.
          </p>
          <ul>
            <li>AI outputs are algorithmic and may contain errors or inaccuracies.</li>
            <li>AI suggestions should not be interpreted as financial advice or trade recommendations.</li>
            <li>You should conduct your own research before making any trading decisions.</li>
            <li>AI models may change without notice, affecting the nature of suggestions.</li>
          </ul>
        </section>

        <section>
          <h2>9. Intellectual Property</h2>
          <p>
            All content, design, code, and trademarks in the Service are owned by D23AI.
            You may not copy, modify, distribute, or reverse-engineer any part of the Service
            without our written consent.
          </p>
        </section>

        <section>
          <h2>10. Prohibited Conduct</h2>
          <p>You agree not to:</p>
          <ul>
            <li>Use the Service for any illegal purpose.</li>
            <li>Scrape, crawl, or extract data from the Service programmatically.</li>
            <li>Attempt to gain unauthorized access to the Service or its systems.</li>
            <li>Interfere with or disrupt the Service's operation.</li>
            <li>Resell or redistribute market data obtained through the Service.</li>
            <li>Misrepresent your identity or create fraudulent accounts.</li>
          </ul>
        </section>

        <section>
          <h2>11. Limitation of Liability</h2>
          <p className="legal-important">
            TO THE MAXIMUM EXTENT PERMITTED BY LAW, D23AI SHALL NOT BE LIABLE FOR ANY INDIRECT,
            INCIDENTAL, SPECIAL, CONSEQUENTIAL, OR PUNITIVE DAMAGES, INCLUDING BUT NOT LIMITED
            TO LOSS OF PROFITS, TRADING LOSSES, DATA LOSS, OR BUSINESS INTERRUPTION, ARISING
            FROM YOUR USE OF THE SERVICE.
          </p>
          <p>
            In no event shall our total liability exceed the amount you paid us in the
            twelve (12) months preceding the claim.
          </p>
        </section>

        <section>
          <h2>12. Termination</h2>
          <p>
            We may suspend or terminate your access to the Service at any time, with or without
            cause, with or without notice. You may delete your account at any time by contacting
            support. Upon termination, your right to use the Service ceases immediately.
          </p>
        </section>

        <section>
          <h2>13. Governing Law</h2>
          <p>
            These Terms shall be governed by and construed in accordance with the laws of India.
            Any disputes arising from these Terms shall be subject to the exclusive jurisdiction
            of the courts in Bengaluru, Karnataka, India.
          </p>
        </section>

        <section>
          <h2>14. Changes to Terms</h2>
          <p>
            We reserve the right to modify these Terms at any time. Material changes will be
            communicated through the app or via email. Continued use of the Service after
            changes constitutes acceptance.
          </p>
        </section>

        <section>
          <h2>15. Contact Us</h2>
          <p>For questions about these Terms, contact us at:</p>
          <ul className="contact-list">
            <li><strong>Email:</strong> <a href="mailto:support@d23ai.in">support@d23ai.in</a></li>
            <li><strong>Website:</strong> <a href="https://optix.d23ai.in">optix.d23ai.in</a></li>
          </ul>
        </section>
      </div>
    </div>
  )
}

export default TermsOfService
