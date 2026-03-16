import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { calculateBlackScholes, getMoneyness, formatCurrency } from '../utils/blackScholes'

const INDICES = {
  NIFTY: { name: 'NIFTY', lotSize: 75, defaultSpot: 24250 },
  BANKNIFTY: { name: 'BANKNIFTY', lotSize: 30, defaultSpot: 51500 },
  FINNIFTY: { name: 'FINNIFTY', lotSize: 25, defaultSpot: 23100 },
  SENSEX: { name: 'SENSEX', lotSize: 10, defaultSpot: 79500 },
  MIDCPNIFTY: { name: 'MIDCPNIFTY', lotSize: 50, defaultSpot: 12800 },
  BANKEX: { name: 'BANKEX', lotSize: 15, defaultSpot: 57000 }
}

function Calculator() {
  const { t } = useTranslation()
  const [spotPrice, setSpotPrice] = useState(24250)
  const [strikePrice, setStrikePrice] = useState(24300)
  const [volatility, setVolatility] = useState(15)
  const [daysToExpiry, setDaysToExpiry] = useState(7)
  const [interestRate, setInterestRate] = useState(6.5)
  const [optionType, setOptionType] = useState('call')
  const [selectedIndex, setSelectedIndex] = useState('NIFTY')

  const lotSize = INDICES[selectedIndex].lotSize

  // Calculate option values
  const result = useMemo(() => {
    return calculateBlackScholes({
      spotPrice,
      strikePrice,
      volatility: volatility / 100,
      daysToExpiry,
      interestRate: interestRate / 100,
      optionType
    })
  }, [spotPrice, strikePrice, volatility, daysToExpiry, interestRate, optionType])

  // Calculate scenarios
  const scenarios = useMemo(() => {
    const spotUp1 = calculateBlackScholes({
      spotPrice: spotPrice * 1.01,
      strikePrice,
      volatility: volatility / 100,
      daysToExpiry,
      interestRate: interestRate / 100,
      optionType
    })

    const spotDown1 = calculateBlackScholes({
      spotPrice: spotPrice * 0.99,
      strikePrice,
      volatility: volatility / 100,
      daysToExpiry,
      interestRate: interestRate / 100,
      optionType
    })

    const ivUp2 = calculateBlackScholes({
      spotPrice,
      strikePrice,
      volatility: (volatility + 2) / 100,
      daysToExpiry,
      interestRate: interestRate / 100,
      optionType
    })

    const tomorrow = calculateBlackScholes({
      spotPrice,
      strikePrice,
      volatility: volatility / 100,
      daysToExpiry: Math.max(0, daysToExpiry - 1),
      interestRate: interestRate / 100,
      optionType
    })

    return {
      spotUp1: (spotUp1.price - result.price) * lotSize,
      spotDown1: (spotDown1.price - result.price) * lotSize,
      ivUp2: (ivUp2.price - result.price) * lotSize,
      tomorrow: (tomorrow.price - result.price) * lotSize
    }
  }, [spotPrice, strikePrice, volatility, daysToExpiry, interestRate, optionType, lotSize, result.price])

  const moneyness = getMoneyness(spotPrice, strikePrice, optionType)
  const lotValue = result.price * lotSize

  const handleIndexChange = (index) => {
    setSelectedIndex(index)
    setSpotPrice(INDICES[index].defaultSpot)
    // Set ATM strike
    const step = index === 'SENSEX' ? 100 : 50
    setStrikePrice(Math.round(INDICES[index].defaultSpot / step) * step)
  }

  return (
    <section id="calculator" className="calculator-section">
      <div className="container">
        <div className="section-header">
          <span className="section-badge">{t('calculator.badge')}</span>
          <h2 className="section-title">{t('calculator.title')}</h2>
          <p className="section-subtitle">{t('calculator.subtitle')}</p>
        </div>

        <div className="calculator-container">
          <div className="calc-inputs">
            <h3>{t('calculator.inputParameters')}</h3>

            <div className="input-group">
              <label>{t('calculator.index')}</label>
              <div className="index-selector">
                {Object.keys(INDICES).map(index => (
                  <button
                    key={index}
                    className={`index-btn ${selectedIndex === index ? 'active' : ''}`}
                    onClick={() => handleIndexChange(index)}
                  >
                    {index} ({INDICES[index].lotSize})
                  </button>
                ))}
              </div>
            </div>

            <div className="input-group">
              <label htmlFor="spotPrice">{t('calculator.spotPrice')}</label>
              <input
                type="number"
                id="spotPrice"
                value={spotPrice}
                onChange={(e) => setSpotPrice(Number(e.target.value))}
                step="50"
                min="0"
              />
            </div>

            <div className="input-group">
              <label htmlFor="strikePrice">{t('calculator.strikePrice')}</label>
              <input
                type="number"
                id="strikePrice"
                value={strikePrice}
                onChange={(e) => setStrikePrice(Number(e.target.value))}
                step="50"
                min="0"
              />
            </div>

            <div className="input-group">
              <label htmlFor="volatility">{t('calculator.impliedVolatility', { value: volatility })}</label>
              <input
                type="range"
                id="volatility"
                value={volatility}
                onChange={(e) => setVolatility(Number(e.target.value))}
                min="5"
                max="80"
                step="0.5"
                className="slider"
              />
              <div className="slider-labels">
                <span>5%</span>
                <span>80%</span>
              </div>
            </div>

            <div className="input-group">
              <label htmlFor="daysToExpiry">{t('calculator.daysToExpiry', { value: daysToExpiry })}</label>
              <input
                type="range"
                id="daysToExpiry"
                value={daysToExpiry}
                onChange={(e) => setDaysToExpiry(Number(e.target.value))}
                min="0"
                max="90"
                step="1"
                className="slider"
              />
              <div className="slider-labels">
                <span>0</span>
                <span>90</span>
              </div>
            </div>

            <div className="input-group">
              <label htmlFor="interestRate">{t('calculator.interestRate')}</label>
              <input
                type="number"
                id="interestRate"
                value={interestRate}
                onChange={(e) => setInterestRate(Number(e.target.value))}
                step="0.25"
                min="0"
                max="20"
              />
            </div>

            <div className="input-group">
              <label>{t('calculator.optionType')}</label>
              <div className="option-type-toggle">
                <button
                  className={`type-btn ${optionType === 'call' ? 'active call' : ''}`}
                  onClick={() => setOptionType('call')}
                >
                  {t('calculator.call')}
                </button>
                <button
                  className={`type-btn ${optionType === 'put' ? 'active put' : ''}`}
                  onClick={() => setOptionType('put')}
                >
                  {t('calculator.put')}
                </button>
              </div>
            </div>
          </div>

          <div className="calc-results">
            <div className="result-main">
              <div className="result-label">{t('calculator.results.theoreticalPrice')}</div>
              <div className="result-price">{formatCurrency(result.price)}</div>
              <div className="result-sublabel">
                <span className={`option-badge ${optionType}`}>{optionType.toUpperCase()}</span>
                <span className={`moneyness-badge ${moneyness.toLowerCase()}`}>{moneyness}</span>
                <span className="strike-info">{t('calculator.results.strike', { value: strikePrice })}</span>
              </div>
            </div>

            <div className="result-lot-value">
              <span className="lot-label">{t('calculator.results.lotValue', { lotSize })}</span>
              <span className="lot-amount">{formatCurrency(lotValue)}</span>
            </div>

            <div className="greeks-grid">
              <div className="greek-card">
                <div className="greek-header">
                  <span className="greek-symbol delta">Δ</span>
                  <span className="greek-name">{t('calculator.greeks.delta')}</span>
                </div>
                <div className="greek-value">{result.delta.toFixed(4)}</div>
                <div className="greek-bar">
                  <div
                    className="greek-bar-fill delta-fill"
                    style={{ width: `${Math.abs(result.delta) * 100}%` }}
                  ></div>
                </div>
                <div className="greek-desc">
                  {t('calculator.greeks.deltaDesc', { value: Math.abs(result.delta).toFixed(2) })}
                </div>
              </div>

              <div className="greek-card">
                <div className="greek-header">
                  <span className="greek-symbol gamma">Γ</span>
                  <span className="greek-name">{t('calculator.greeks.gamma')}</span>
                </div>
                <div className="greek-value">{result.gamma.toFixed(6)}</div>
                <div className="greek-desc">
                  {t('calculator.greeks.gammaDesc', { value: result.gamma.toFixed(6) })}
                </div>
              </div>

              <div className="greek-card">
                <div className="greek-header">
                  <span className="greek-symbol theta">Θ</span>
                  <span className="greek-name">{t('calculator.greeks.theta')}</span>
                </div>
                <div className="greek-value negative">{result.theta.toFixed(2)}</div>
                <div className="greek-desc">
                  {t('calculator.greeks.thetaDesc', { value: Math.abs(result.theta).toFixed(2) })}
                </div>
              </div>

              <div className="greek-card">
                <div className="greek-header">
                  <span className="greek-symbol vega">V</span>
                  <span className="greek-name">{t('calculator.greeks.vega')}</span>
                </div>
                <div className="greek-value">{result.vega.toFixed(2)}</div>
                <div className="greek-desc">
                  {t('calculator.greeks.vegaDesc', { value: result.vega.toFixed(2) })}
                </div>
              </div>

              <div className="greek-card">
                <div className="greek-header">
                  <span className="greek-symbol rho">ρ</span>
                  <span className="greek-name">{t('calculator.greeks.rho')}</span>
                </div>
                <div className="greek-value">{result.rho.toFixed(2)}</div>
                <div className="greek-desc">
                  {t('calculator.greeks.rhoDesc', { value: result.rho.toFixed(2) })}
                </div>
              </div>

              <div className="greek-card intrinsic-card">
                <div className="greek-header">
                  <span className="greek-symbol intrinsic">IV</span>
                  <span className="greek-name">{t('calculator.greeks.valueSplit')}</span>
                </div>
                <div className="greek-value">{formatCurrency(result.intrinsicValue)}</div>
                <div className="value-split">
                  <div className="split-bar">
                    <div
                      className="split-intrinsic"
                      style={{ width: `${result.price > 0 ? (result.intrinsicValue / result.price) * 100 : 0}%` }}
                    ></div>
                    <div
                      className="split-time"
                      style={{ width: `${result.price > 0 ? (result.timeValue / result.price) * 100 : 100}%` }}
                    ></div>
                  </div>
                  <div className="split-labels">
                    <span>{t('calculator.greeks.intrinsic')}: {formatCurrency(result.intrinsicValue)}</span>
                    <span>{t('calculator.greeks.time')}: {formatCurrency(result.timeValue)}</span>
                  </div>
                </div>
              </div>
            </div>

            <div className="profit-scenarios">
              <h4>{t('calculator.scenarios.title')}</h4>
              <div className="scenario-grid">
                <div className="scenario-card">
                  <span className="scenario-label">{t('calculator.scenarios.spotUp')}</span>
                  <span className={`scenario-value ${scenarios.spotUp1 >= 0 ? 'positive' : 'negative'}`}>
                    {scenarios.spotUp1 >= 0 ? '+' : ''}{formatCurrency(scenarios.spotUp1)}
                  </span>
                </div>
                <div className="scenario-card">
                  <span className="scenario-label">{t('calculator.scenarios.spotDown')}</span>
                  <span className={`scenario-value ${scenarios.spotDown1 >= 0 ? 'positive' : 'negative'}`}>
                    {scenarios.spotDown1 >= 0 ? '+' : ''}{formatCurrency(scenarios.spotDown1)}
                  </span>
                </div>
                <div className="scenario-card">
                  <span className="scenario-label">{t('calculator.scenarios.ivUp')}</span>
                  <span className={`scenario-value ${scenarios.ivUp2 >= 0 ? 'positive' : 'negative'}`}>
                    {scenarios.ivUp2 >= 0 ? '+' : ''}{formatCurrency(scenarios.ivUp2)}
                  </span>
                </div>
                <div className="scenario-card">
                  <span className="scenario-label">{t('calculator.scenarios.tomorrow')}</span>
                  <span className={`scenario-value ${scenarios.tomorrow >= 0 ? 'positive' : 'negative'}`}>
                    {scenarios.tomorrow >= 0 ? '+' : ''}{formatCurrency(scenarios.tomorrow)}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}

export default Calculator
