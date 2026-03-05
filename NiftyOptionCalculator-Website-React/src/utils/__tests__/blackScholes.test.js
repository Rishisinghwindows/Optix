import { describe, it, expect } from 'vitest';
import {
  calculateBlackScholes,
  calculateImpliedVolatility,
  getMoneyness,
  formatCurrency,
} from '../blackScholes';

// ── calculateBlackScholes ───────────────────────────────────────────

describe('calculateBlackScholes', () => {
  const baseParams = {
    spotPrice: 22000,
    strikePrice: 22000,
    volatility: 0.15,
    daysToExpiry: 30,
    interestRate: 0.065,
    optionType: 'call',
  };

  it('prices ATM call correctly', () => {
    const r = calculateBlackScholes(baseParams);
    expect(r.price).toBeGreaterThan(0);
    expect(r.price).toBeLessThan(baseParams.spotPrice * 0.1); // premium < 10% of spot
  });

  it('prices ATM put correctly', () => {
    const r = calculateBlackScholes({ ...baseParams, optionType: 'put' });
    expect(r.price).toBeGreaterThan(0);
  });

  it('ITM call is more expensive than ATM call', () => {
    const atm = calculateBlackScholes(baseParams);
    const itm = calculateBlackScholes({ ...baseParams, strikePrice: 21500 });
    expect(itm.price).toBeGreaterThan(atm.price);
  });

  it('OTM call is cheaper than ATM call', () => {
    const atm = calculateBlackScholes(baseParams);
    const otm = calculateBlackScholes({ ...baseParams, strikePrice: 22500 });
    expect(otm.price).toBeLessThan(atm.price);
  });

  it('ITM put is more expensive than ATM put', () => {
    const atm = calculateBlackScholes({ ...baseParams, optionType: 'put' });
    const itm = calculateBlackScholes({ ...baseParams, optionType: 'put', strikePrice: 22500 });
    expect(itm.price).toBeGreaterThan(atm.price);
  });

  it('OTM put is cheaper than ATM put', () => {
    const atm = calculateBlackScholes({ ...baseParams, optionType: 'put' });
    const otm = calculateBlackScholes({ ...baseParams, optionType: 'put', strikePrice: 21500 });
    expect(otm.price).toBeLessThan(atm.price);
  });

  it('returns intrinsic value at expiry (T <= 0)', () => {
    const r = calculateBlackScholes({ ...baseParams, daysToExpiry: 0, strikePrice: 21800 });
    expect(r.price).toBeCloseTo(200, 0); // intrinsic = 22000-21800
    expect(r.delta).toBe(1);
    expect(r.gamma).toBe(0);
    expect(r.theta).toBe(0);
    expect(r.vega).toBe(0);
    expect(r.timeValue).toBe(0);
  });

  it('OTM call at expiry has zero value', () => {
    const r = calculateBlackScholes({ ...baseParams, daysToExpiry: 0, strikePrice: 22200 });
    expect(r.price).toBe(0);
    expect(r.delta).toBe(0);
  });

  it('satisfies put-call parity approximately', () => {
    const T = baseParams.daysToExpiry / 365;
    const call = calculateBlackScholes(baseParams);
    const put = calculateBlackScholes({ ...baseParams, optionType: 'put' });
    // C - P = S - K*e^(-rT)
    const lhs = call.price - put.price;
    const rhs = baseParams.spotPrice - baseParams.strikePrice * Math.exp(-baseParams.interestRate * T);
    expect(lhs).toBeCloseTo(rhs, 0);
  });

  it('call delta is positive and between 0 and 1', () => {
    const r = calculateBlackScholes(baseParams);
    expect(r.delta).toBeGreaterThan(0);
    expect(r.delta).toBeLessThanOrEqual(1);
  });

  it('put delta is negative', () => {
    const r = calculateBlackScholes({ ...baseParams, optionType: 'put' });
    expect(r.delta).toBeLessThan(0);
    expect(r.delta).toBeGreaterThanOrEqual(-1);
  });

  it('gamma is positive for both call and put', () => {
    expect(calculateBlackScholes(baseParams).gamma).toBeGreaterThan(0);
    expect(calculateBlackScholes({ ...baseParams, optionType: 'put' }).gamma).toBeGreaterThan(0);
  });

  it('theta is negative (time decay)', () => {
    expect(calculateBlackScholes(baseParams).theta).toBeLessThan(0);
    expect(calculateBlackScholes({ ...baseParams, optionType: 'put' }).theta).toBeLessThan(0);
  });

  it('vega is positive', () => {
    expect(calculateBlackScholes(baseParams).vega).toBeGreaterThan(0);
  });

  it('deep ITM call has delta near 1', () => {
    const r = calculateBlackScholes({ ...baseParams, strikePrice: 18000 });
    expect(r.delta).toBeGreaterThan(0.95);
  });

  it('deep OTM call has delta near 0', () => {
    const r = calculateBlackScholes({ ...baseParams, strikePrice: 26000 });
    expect(r.delta).toBeLessThan(0.05);
  });
});

// ── calculateImpliedVolatility ──────────────────────────────────────

describe('calculateImpliedVolatility', () => {
  const params = {
    spotPrice: 22000,
    strikePrice: 22000,
    daysToExpiry: 30,
    interestRate: 0.065,
    optionType: 'call',
  };

  it('round-trips: computes price then recovers IV', () => {
    const originalIV = 0.20;
    const price = calculateBlackScholes({ ...params, volatility: originalIV }).price;
    const recoveredIV = calculateImpliedVolatility({ ...params, marketPrice: price });
    expect(recoveredIV).toBeCloseTo(originalIV, 2);
  });

  it('recovers IV for ATM put', () => {
    const originalIV = 0.18;
    const putParams = { ...params, optionType: 'put' };
    const price = calculateBlackScholes({ ...putParams, volatility: originalIV }).price;
    const recoveredIV = calculateImpliedVolatility({ ...putParams, marketPrice: price });
    expect(recoveredIV).toBeCloseTo(originalIV, 2);
  });

  it('converges for deep OTM option', () => {
    const iv = calculateImpliedVolatility({ ...params, strikePrice: 23000, marketPrice: 20 });
    expect(iv).toBeGreaterThan(0.01);
    expect(iv).toBeLessThan(5);
  });

  it('result is bounded between 0.01 and 5.0', () => {
    const iv = calculateImpliedVolatility({ ...params, marketPrice: 1 });
    expect(iv).toBeGreaterThanOrEqual(0.01);
    expect(iv).toBeLessThanOrEqual(5);
  });
});

// ── getMoneyness ────────────────────────────────────────────────────

describe('getMoneyness', () => {
  it('returns ATM when spot ≈ strike', () => {
    expect(getMoneyness(22000, 22000, 'call')).toBe('ATM');
    expect(getMoneyness(22000, 22000, 'put')).toBe('ATM');
  });

  it('call ITM when spot > strike', () => {
    expect(getMoneyness(22200, 22000, 'call')).toBe('ITM');
  });

  it('call OTM when spot < strike', () => {
    expect(getMoneyness(21800, 22000, 'call')).toBe('OTM');
  });

  it('put ITM when spot < strike', () => {
    expect(getMoneyness(21800, 22000, 'put')).toBe('ITM');
  });

  it('put OTM when spot > strike', () => {
    expect(getMoneyness(22200, 22000, 'put')).toBe('OTM');
  });
});

// ── formatCurrency ──────────────────────────────────────────────────

describe('formatCurrency', () => {
  it('formats crores in compact mode', () => {
    expect(formatCurrency(15000000, true)).toBe('₹1.50Cr');
  });

  it('formats lakhs in compact mode', () => {
    expect(formatCurrency(250000, true)).toBe('₹2.50L');
  });

  it('formats thousands in compact mode', () => {
    expect(formatCurrency(5500, true)).toBe('₹5.50K');
  });

  it('formats standard (non-compact) with Indian locale', () => {
    const result = formatCurrency(12345.67);
    expect(result).toContain('₹');
    expect(result).toContain('12');
  });

  it('compact mode falls through to standard for small values', () => {
    const result = formatCurrency(500, true);
    expect(result).toContain('₹');
    expect(result).toContain('500');
  });

  it('handles negative values in compact mode', () => {
    expect(formatCurrency(-500000, true)).toBe('₹-5.00L');
  });
});
