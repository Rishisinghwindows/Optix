import React from 'react'

const indices = [
  { code: 'N50', name: 'NIFTY 50', expiry: 'Weekly expiry every Thursday', className: 'nifty' },
  { code: 'BN', name: 'BANK NIFTY', expiry: 'Weekly expiry every Wednesday', className: 'banknifty' },
  { code: 'FN', name: 'FIN NIFTY', expiry: 'Weekly expiry every Tuesday', className: 'finnifty' },
  { code: 'SX', name: 'SENSEX', expiry: 'Weekly expiry every Friday', className: 'sensex' },
  { code: 'MC', name: 'MIDCAP NIFTY', expiry: 'Weekly expiry every Monday', className: 'midcap' }
]

function Indices() {
  return (
    <section className="indices">
      <div className="container">
        <div className="section-header">
          <span className="section-badge">Supported Markets</span>
          <h2 className="section-title">Trade All Major Indian Indices</h2>
        </div>
        <div className="indices-grid">
          {indices.map((index, i) => (
            <div className="index-card" key={i}>
              <div className={`index-icon ${index.className}`}>{index.code}</div>
              <h4>{index.name}</h4>
              <p>{index.expiry}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

export default Indices
