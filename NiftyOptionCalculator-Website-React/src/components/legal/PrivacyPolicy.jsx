import React from 'react'
import './Legal.css'

function PrivacyPolicy() {
  return (
    <div className="legal-page">
      <div className="legal-container">
        <h1>Privacy Policy</h1>
        <p className="legal-updated">Last updated: March 22, 2026</p>

        <section>
          <h2>1. Introduction</h2>
          <p>
            D23AI ("we", "our", or "us") operates the Optix mobile application and website
            (collectively, the "Service"). This Privacy Policy explains how we collect, use,
            disclose, and safeguard your information when you use our Service.
          </p>
          <p>
            By using Optix, you agree to the collection and use of information in accordance
            with this policy. If you do not agree, please do not use the Service.
          </p>
        </section>

        <section>
          <h2>2. Information We Collect</h2>

          <h3>2.1 Information You Provide</h3>
          <ul>
            <li><strong>Account Information:</strong> Phone number, name, and email address when you create an account via OTP or social login (Google/Apple).</li>
            <li><strong>Trade Journal Entries:</strong> Trading notes, strategies, and performance data you voluntarily log.</li>
            <li><strong>Paper Trading Data:</strong> Virtual trades, positions, and portfolio data created within the paper trading feature.</li>
            <li><strong>Alert Preferences:</strong> Price, IV, and OI alert configurations you set up.</li>
            <li><strong>Support Communications:</strong> Messages you send to our support team.</li>
          </ul>

          <h3>2.2 Information Collected Automatically</h3>
          <ul>
            <li><strong>Device Information:</strong> Device type, operating system, unique device identifiers, and push notification tokens (FCM).</li>
            <li><strong>Usage Data:</strong> Pages visited, features used, time spent, and interaction patterns.</li>
            <li><strong>Crash Reports:</strong> Technical crash data collected via Firebase Crashlytics to improve app stability.</li>
            <li><strong>Analytics Data:</strong> Anonymous usage statistics via Firebase Analytics.</li>
          </ul>

          <h3>2.3 Information from Third Parties</h3>
          <ul>
            <li><strong>Social Login Providers:</strong> Basic profile information (name, email) from Google or Apple when you use social sign-in.</li>
            <li><strong>Broker APIs:</strong> If you connect a broker (e.g., Upstox), we access market data on your behalf. We do not store your broker credentials — authentication is handled via OAuth.</li>
          </ul>
        </section>

        <section>
          <h2>3. How We Use Your Information</h2>
          <p>We use collected information to:</p>
          <ul>
            <li>Provide and maintain the Service, including option chain data, AI insights, and paper trading.</li>
            <li>Send push notifications for price alerts, market signals, and app updates.</li>
            <li>Generate AI-powered trade suggestions and market analysis.</li>
            <li>Improve app performance, fix bugs, and enhance user experience.</li>
            <li>Respond to your support requests and communications.</li>
            <li>Analyze usage patterns to develop new features.</li>
            <li>Ensure security and prevent fraud.</li>
          </ul>
        </section>

        <section>
          <h2>4. Data Sharing and Disclosure</h2>
          <p>We do <strong>not</strong> sell your personal information. We may share data with:</p>
          <ul>
            <li><strong>Firebase (Google):</strong> For authentication, push notifications, crash reporting, and analytics.</li>
            <li><strong>Google Gemini AI:</strong> Market data (not personal data) is sent for AI analysis. No personally identifiable information is shared.</li>
            <li><strong>Broker APIs (Upstox):</strong> Your OAuth token is used to fetch market data. We do not share your data with brokers.</li>
            <li><strong>Legal Requirements:</strong> If required by law, regulation, or legal process.</li>
          </ul>
        </section>

        <section>
          <h2>5. Data Storage and Security</h2>
          <p>
            Your data is stored on secure servers. We use industry-standard encryption (TLS/SSL)
            for data in transit and implement appropriate technical and organizational measures
            to protect your information.
          </p>
          <ul>
            <li>Authentication tokens are stored securely using platform-specific secure storage (iOS Keychain / Android EncryptedSharedPreferences).</li>
            <li>Paper trading and journal data is stored in our backend database with access controls.</li>
            <li>Crash reports and analytics are processed by Firebase and retained per Google's data retention policies.</li>
          </ul>
        </section>

        <section>
          <h2>6. Push Notifications</h2>
          <p>
            We use Firebase Cloud Messaging (FCM) to send push notifications for:
          </p>
          <ul>
            <li>Price, IV, and OI alerts you configure.</li>
            <li>Market monitor signals (PCR shifts, VIX changes, OI surges, etc.).</li>
            <li>App updates and announcements.</li>
          </ul>
          <p>
            You can disable push notifications at any time through your device settings.
            Your FCM device token is stored on our servers and deactivated when you uninstall the app.
          </p>
        </section>

        <section>
          <h2>7. Third-Party Services</h2>
          <p>Our Service uses the following third-party services, each with their own privacy policies:</p>
          <ul>
            <li><strong>Firebase</strong> (Google) — Authentication, Messaging, Crashlytics, Analytics</li>
            <li><strong>Google Sign-In</strong> — Social authentication</li>
            <li><strong>Apple Sign-In</strong> — Social authentication</li>
            <li><strong>Upstox API</strong> — Market data provider</li>
            <li><strong>NSE India</strong> — Market data source</li>
            <li><strong>Google Gemini</strong> — AI analysis engine</li>
          </ul>
        </section>

        <section>
          <h2>8. Your Rights</h2>
          <p>You have the right to:</p>
          <ul>
            <li><strong>Access:</strong> Request a copy of your personal data.</li>
            <li><strong>Correction:</strong> Update or correct inaccurate information.</li>
            <li><strong>Deletion:</strong> Request deletion of your account and associated data.</li>
            <li><strong>Opt-out:</strong> Disable push notifications or analytics collection.</li>
            <li><strong>Data Portability:</strong> Request your data in a machine-readable format.</li>
          </ul>
          <p>To exercise any of these rights, contact us at <a href="mailto:support@d23ai.in">support@d23ai.in</a>.</p>
        </section>

        <section>
          <h2>9. Data Retention</h2>
          <p>
            We retain your personal data only for as long as necessary to provide the Service
            and fulfill the purposes described in this policy. When you delete your account,
            we will delete or anonymize your data within 30 days, except where retention is
            required by law.
          </p>
        </section>

        <section>
          <h2>10. Children's Privacy</h2>
          <p>
            Optix is not intended for users under 18 years of age. We do not knowingly collect
            personal information from children. If you believe we have inadvertently collected
            data from a minor, please contact us and we will promptly delete it.
          </p>
        </section>

        <section>
          <h2>11. Changes to This Policy</h2>
          <p>
            We may update this Privacy Policy from time to time. We will notify you of significant
            changes through the app or via email. Your continued use of the Service after changes
            constitutes acceptance of the updated policy.
          </p>
        </section>

        <section>
          <h2>12. Contact Us</h2>
          <p>If you have questions about this Privacy Policy, contact us at:</p>
          <ul className="contact-list">
            <li><strong>Email:</strong> <a href="mailto:support@d23ai.in">support@d23ai.in</a></li>
            <li><strong>Website:</strong> <a href="https://optix.d23ai.in">optix.d23ai.in</a></li>
          </ul>
        </section>
      </div>
    </div>
  )
}

export default PrivacyPolicy
