// Standard Normal Distribution CDF
function normalCDF(x) {
  const a1 = 0.254829592;
  const a2 = -0.284496736;
  const a3 = 1.421413741;
  const a4 = -1.453152027;
  const a5 = 1.061405429;
  const p = 0.3275911;

  const sign = x < 0 ? -1 : 1;
  x = Math.abs(x) / Math.sqrt(2);

  const t = 1.0 / (1.0 + p * x);
  const y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);

  return 0.5 * (1.0 + sign * y);
}

// Standard Normal Distribution PDF
function normalPDF(x) {
  return Math.exp(-0.5 * x * x) / Math.sqrt(2 * Math.PI);
}

// Black-Scholes Calculator
export function calculateBlackScholes(params) {
  const {
    spotPrice,
    strikePrice,
    volatility,      // as decimal (0.15 for 15%)
    daysToExpiry,
    interestRate,    // as decimal (0.07 for 7%)
    optionType       // 'call' or 'put'
  } = params;

  // Convert days to years
  const T = daysToExpiry / 365;

  // Handle edge cases
  if (T <= 0) {
    const intrinsic = optionType === 'call'
      ? Math.max(0, spotPrice - strikePrice)
      : Math.max(0, strikePrice - spotPrice);
    return {
      price: intrinsic,
      delta: optionType === 'call' ? (spotPrice > strikePrice ? 1 : 0) : (spotPrice < strikePrice ? -1 : 0),
      gamma: 0,
      theta: 0,
      vega: 0,
      rho: 0,
      intrinsicValue: intrinsic,
      timeValue: 0,
      d1: 0,
      d2: 0
    };
  }

  const S = spotPrice;
  const K = strikePrice;
  const r = interestRate;
  const sigma = volatility;

  // Calculate d1 and d2
  const d1 = (Math.log(S / K) + (r + 0.5 * sigma * sigma) * T) / (sigma * Math.sqrt(T));
  const d2 = d1 - sigma * Math.sqrt(T);

  // Calculate option price
  let price, delta, rho;

  if (optionType === 'call') {
    price = S * normalCDF(d1) - K * Math.exp(-r * T) * normalCDF(d2);
    delta = normalCDF(d1);
    rho = K * T * Math.exp(-r * T) * normalCDF(d2) / 100;
  } else {
    price = K * Math.exp(-r * T) * normalCDF(-d2) - S * normalCDF(-d1);
    delta = normalCDF(d1) - 1;
    rho = -K * T * Math.exp(-r * T) * normalCDF(-d2) / 100;
  }

  // Calculate other Greeks
  const gamma = normalPDF(d1) / (S * sigma * Math.sqrt(T));
  const vega = S * normalPDF(d1) * Math.sqrt(T) / 100;

  // Theta (per day)
  const thetaAnnual = optionType === 'call'
    ? -((S * normalPDF(d1) * sigma) / (2 * Math.sqrt(T))) - r * K * Math.exp(-r * T) * normalCDF(d2)
    : -((S * normalPDF(d1) * sigma) / (2 * Math.sqrt(T))) + r * K * Math.exp(-r * T) * normalCDF(-d2);
  const theta = thetaAnnual / 365;

  // Intrinsic and Time Value
  const intrinsicValue = optionType === 'call'
    ? Math.max(0, S - K)
    : Math.max(0, K - S);
  const timeValue = price - intrinsicValue;

  return {
    price: Math.max(0, price),
    delta,
    gamma,
    theta,
    vega,
    rho,
    intrinsicValue,
    timeValue: Math.max(0, timeValue),
    d1,
    d2
  };
}

// Calculate IV from market price (Newton-Raphson method)
export function calculateImpliedVolatility(params) {
  const { marketPrice, spotPrice, strikePrice, daysToExpiry, interestRate, optionType } = params;

  let sigma = 0.2; // Initial guess
  const tolerance = 0.0001;
  const maxIterations = 100;

  for (let i = 0; i < maxIterations; i++) {
    const result = calculateBlackScholes({
      spotPrice,
      strikePrice,
      volatility: sigma,
      daysToExpiry,
      interestRate,
      optionType
    });

    const diff = result.price - marketPrice;
    if (Math.abs(diff) < tolerance) break;

    // Newton-Raphson: sigma_new = sigma - f(sigma) / f'(sigma)
    // f'(sigma) = vega * 100 (since vega is per 1% change)
    const vega = result.vega * 100;
    if (vega < 0.0001) break; // Avoid division by zero

    sigma = sigma - diff / vega;
    sigma = Math.max(0.01, Math.min(5, sigma)); // Bound between 1% and 500%
  }

  return sigma;
}

// Moneyness calculation
export function getMoneyness(spotPrice, strikePrice, optionType) {
  const diff = ((spotPrice - strikePrice) / strikePrice) * 100;

  if (Math.abs(diff) < 0.5) return 'ATM';

  if (optionType === 'call') {
    return diff > 0 ? 'ITM' : 'OTM';
  } else {
    return diff < 0 ? 'ITM' : 'OTM';
  }
}

// Format currency
export function formatCurrency(value, compact = false) {
  if (compact) {
    if (Math.abs(value) >= 10000000) {
      return `₹${(value / 10000000).toFixed(2)}Cr`;
    } else if (Math.abs(value) >= 100000) {
      return `₹${(value / 100000).toFixed(2)}L`;
    } else if (Math.abs(value) >= 1000) {
      return `₹${(value / 1000).toFixed(2)}K`;
    }
  }
  return `₹${value.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}
