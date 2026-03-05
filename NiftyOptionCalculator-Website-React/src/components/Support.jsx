import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'

function Support() {
  const { t } = useTranslation()
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    type: 'feedback',
    message: ''
  })
  const [submitted, setSubmitted] = useState(false)

  const handleSubmit = (e) => {
    e.preventDefault()
    // Open email client with pre-filled data
    const subject = encodeURIComponent(`Optix ${formData.type}: ${formData.name}`)
    const body = encodeURIComponent(`Name: ${formData.name}\nEmail: ${formData.email}\nType: ${formData.type}\n\nMessage:\n${formData.message}`)
    window.location.href = `mailto:support@d23ai.in?subject=${subject}&body=${body}`
    setSubmitted(true)
  }

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value })
  }

  return (
    <section className="support-page">
      <div className="container">
        <div className="support-content">
          <h1>Support & Feedback</h1>
          <p className="support-subtitle">
            We'd love to hear from you! Share your feedback, report issues, or ask questions.
          </p>

          {submitted ? (
            <div className="success-message">
              <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="var(--accent-primary)" strokeWidth="2">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                <polyline points="22 4 12 14.01 9 11.01"/>
              </svg>
              <h2>Thank You!</h2>
              <p>Your email client should open with your message. If not, please email us directly at:</p>
              <a href="mailto:support@d23ai.in" className="email-link">support@d23ai.in</a>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="support-form">
              <div className="form-group">
                <label htmlFor="name">Name</label>
                <input
                  type="text"
                  id="name"
                  name="name"
                  value={formData.name}
                  onChange={handleChange}
                  required
                  placeholder="Your name"
                />
              </div>

              <div className="form-group">
                <label htmlFor="email">Email</label>
                <input
                  type="email"
                  id="email"
                  name="email"
                  value={formData.email}
                  onChange={handleChange}
                  required
                  placeholder="your@email.com"
                />
              </div>

              <div className="form-group">
                <label htmlFor="type">Type</label>
                <select
                  id="type"
                  name="type"
                  value={formData.type}
                  onChange={handleChange}
                >
                  <option value="feedback">Feedback</option>
                  <option value="bug">Bug Report</option>
                  <option value="feature">Feature Request</option>
                  <option value="question">Question</option>
                  <option value="other">Other</option>
                </select>
              </div>

              <div className="form-group">
                <label htmlFor="message">Message</label>
                <textarea
                  id="message"
                  name="message"
                  value={formData.message}
                  onChange={handleChange}
                  required
                  placeholder="Tell us what's on your mind..."
                  rows="5"
                />
              </div>

              <button type="submit" className="btn btn-primary btn-lg">
                Send Feedback
              </button>
            </form>
          )}

          <div className="contact-info">
            <h3>Other Ways to Reach Us</h3>
            <div className="contact-methods">
              <a href="mailto:support@d23ai.in" className="contact-method">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z"/>
                </svg>
                <span>support@d23ai.in</span>
              </a>
            </div>
          </div>
        </div>
      </div>

      <style>{`
        .support-page {
          min-height: 100vh;
          padding: 120px 20px 60px;
          background: var(--background);
        }
        .support-content {
          max-width: 600px;
          margin: 0 auto;
        }
        .support-content h1 {
          font-size: 2.5rem;
          font-weight: 700;
          margin-bottom: 12px;
          color: var(--text-primary);
        }
        .support-subtitle {
          font-size: 1.1rem;
          color: var(--text-secondary);
          margin-bottom: 40px;
        }
        .support-form {
          background: var(--surface);
          padding: 32px;
          border-radius: 16px;
          border: 1px solid var(--border-light);
        }
        .form-group {
          margin-bottom: 24px;
        }
        .form-group label {
          display: block;
          font-weight: 600;
          margin-bottom: 8px;
          color: var(--text-primary);
        }
        .form-group input,
        .form-group select,
        .form-group textarea {
          width: 100%;
          padding: 12px 16px;
          border: 1px solid var(--border-light);
          border-radius: 8px;
          background: var(--background);
          color: var(--text-primary);
          font-size: 16px;
          transition: border-color 0.2s;
        }
        .form-group input:focus,
        .form-group select:focus,
        .form-group textarea:focus {
          outline: none;
          border-color: var(--accent-primary);
        }
        .form-group textarea {
          resize: vertical;
          min-height: 120px;
        }
        .support-form .btn {
          width: 100%;
          margin-top: 8px;
        }
        .success-message {
          text-align: center;
          padding: 60px 20px;
          background: var(--surface);
          border-radius: 16px;
          border: 1px solid var(--border-light);
        }
        .success-message svg {
          margin-bottom: 24px;
        }
        .success-message h2 {
          color: var(--text-primary);
          margin-bottom: 12px;
        }
        .success-message p {
          color: var(--text-secondary);
          margin-bottom: 16px;
        }
        .email-link {
          color: var(--accent-primary);
          font-weight: 600;
          font-size: 1.1rem;
        }
        .contact-info {
          margin-top: 48px;
          text-align: center;
        }
        .contact-info h3 {
          color: var(--text-primary);
          margin-bottom: 20px;
        }
        .contact-methods {
          display: flex;
          justify-content: center;
          gap: 24px;
        }
        .contact-method {
          display: flex;
          align-items: center;
          gap: 8px;
          color: var(--text-secondary);
          text-decoration: none;
          transition: color 0.2s;
        }
        .contact-method:hover {
          color: var(--accent-primary);
        }
      `}</style>
    </section>
  )
}

export default Support
