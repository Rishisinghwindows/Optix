import React from 'react'
import './AnimatedLogo.css'

function AnimatedLogo() {
  return (
    <div className="animated-logo-container">
      {/* Outer pulsing ring */}
      <div className="outer-ring pulse-ring"></div>

      {/* Rotating dashed ring */}
      <div className="dashed-ring rotate-clockwise"></div>

      {/* Inner rotating ring */}
      <div className="inner-ring rotate-counter"></div>

      {/* Main circle with icon */}
      <div className="logo-circle">
        <div className="logo-content">
          <svg
            className="chart-icon"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <polyline points="22 7 13.5 15.5 8.5 10.5 2 17"></polyline>
            <polyline points="16 7 22 7 22 13"></polyline>
          </svg>
          <span className="fx-text">f(x)</span>
        </div>
      </div>

      {/* Green glow effect */}
      <div className="logo-glow"></div>
    </div>
  )
}

export default AnimatedLogo
