import React from 'react'
import './Legal.css'

function Disclaimer() {
  return (
    <div className="legal-page">
      <div className="legal-container">
        <h1>Disclaimer</h1>
        <p className="legal-updated">Last updated: March 22, 2026</p>

        <section>
          <h2>Investment Risk Disclaimer</h2>
          <p className="legal-important">
            Options trading involves substantial risk of loss and is not appropriate for all
            investors. You should carefully consider whether trading is suitable for you in
            light of your financial condition. The risk of loss in trading can be substantial.
            You may sustain a total loss of the funds you deposit with your broker.
          </p>
        </section>

        <section>
          <h2>No Financial Advice</h2>
          <p>
            Optix and D23AI do not provide financial, investment, tax, or legal advice.
            All content provided through the Optix application, website, and related services
            is for <strong>educational and informational purposes only</strong>.
          </p>
          <ul>
            <li>Nothing in this Service constitutes a recommendation to buy, sell, or hold any security or financial instrument.</li>
            <li>AI-generated trade suggestions are algorithmic outputs, not personalized investment advice.</li>
            <li>You should consult a qualified financial advisor before making any investment decisions.</li>
          </ul>
        </section>

        <section>
          <h2>Data Accuracy</h2>
          <p>
            While we strive to provide accurate and timely information, we make no warranties
            regarding the accuracy, completeness, or reliability of any data displayed in the Service.
          </p>
          <ul>
            <li>Option prices, Greeks, and IV values are theoretical calculations based on the Black-Scholes model and may differ from actual market prices.</li>
            <li>Market data may be delayed, incomplete, or temporarily unavailable.</li>
            <li>Historical data and backtesting results do not guarantee future performance.</li>
            <li>AI analysis and probability calculations are estimates, not guarantees.</li>
            <li>Open Interest, volume, and PCR data may not reflect real-time market conditions.</li>
          </ul>
        </section>

        <section>
          <h2>Paper Trading Disclaimer</h2>
          <p>
            The paper trading feature simulates trading with virtual capital and does not
            involve real money or actual market orders. Paper trading results may not accurately
            reflect actual trading outcomes due to:
          </p>
          <ul>
            <li>Absence of real market slippage and execution delays.</li>
            <li>No impact of actual liquidity constraints.</li>
            <li>Simplified order execution (instant fill at displayed price).</li>
            <li>No brokerage charges, taxes, or transaction costs in simulation.</li>
          </ul>
        </section>

        <section>
          <h2>Third-Party Services</h2>
          <p>
            Optix integrates with third-party services including but not limited to Upstox,
            NSE India, Google Gemini AI, and Firebase. We are not responsible for:
          </p>
          <ul>
            <li>Service availability or downtime of third-party providers.</li>
            <li>Accuracy of data provided by third-party APIs.</li>
            <li>Changes to third-party service terms, pricing, or availability.</li>
            <li>Any losses resulting from third-party service failures.</li>
          </ul>
        </section>

        <section>
          <h2>SEBI Compliance Notice</h2>
          <p>
            Optix is not registered with the Securities and Exchange Board of India (SEBI) as
            an investment advisor, research analyst, or portfolio manager. The Service does not
            provide SEBI-regulated advisory services. Users trading in Indian securities markets
            must comply with all applicable SEBI regulations.
          </p>
        </section>

        <section>
          <h2>Limitation of Liability</h2>
          <p className="legal-important">
            D23AI, its directors, employees, and affiliates shall not be liable for any
            trading losses, financial damages, or other losses arising from the use of the
            Service. By using Optix, you acknowledge that you are solely responsible for your
            own investment decisions and any resulting gains or losses.
          </p>
        </section>

        <section>
          <h2>Contact</h2>
          <p>For questions about this disclaimer, contact us at:</p>
          <ul className="contact-list">
            <li><strong>Email:</strong> <a href="mailto:support@d23ai.in">support@d23ai.in</a></li>
            <li><strong>Website:</strong> <a href="https://optix.d23ai.in">optix.d23ai.in</a></li>
          </ul>
        </section>
      </div>
    </div>
  )
}

export default Disclaimer
