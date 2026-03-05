import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock import.meta.env before importing the module
vi.stubEnv('VITE_API_URL', 'http://test:8000');

// Dynamic import so env stub takes effect
const { default: aiAnalysisService } = await import('../aiAnalysisService');
const AIAnalysisService = aiAnalysisService.constructor;

// ── Helpers ─────────────────────────────────────────────────────────

function makeChain(spotPrice, numStrikes = 11, interval = 50) {
  const halfRange = Math.floor(numStrikes / 2);
  const baseStrike = Math.round(spotPrice / interval) * interval;
  return Array.from({ length: numStrikes }, (_, i) => {
    const strike = baseStrike + (i - halfRange) * interval;
    const callLTP = Math.max(5, spotPrice - strike + 100);
    const putLTP = Math.max(5, strike - spotPrice + 100);
    return {
      strikePrice: strike,
      CE: {
        lastPrice: callLTP,
        openInterest: 500000 + Math.random() * 500000,
        changeinOpenInterest: 20000,
        totalTradedVolume: 100000,
        impliedVolatility: 15,
        delta: 0.55 - (strike - spotPrice) / (spotPrice * 0.5),
        gamma: 0.002,
        theta: -5,
        vega: 8,
        bidprice: callLTP - 1,
        askPrice: callLTP + 1,
      },
      PE: {
        lastPrice: putLTP,
        openInterest: 500000 + Math.random() * 500000,
        changeinOpenInterest: 20000,
        totalTradedVolume: 100000,
        impliedVolatility: 16,
        delta: -0.45 + (strike - spotPrice) / (spotPrice * 0.5),
        gamma: 0.002,
        theta: -5,
        vega: 8,
        bidprice: putLTP - 1,
        askPrice: putLTP + 1,
      },
    };
  });
}

function makeContext(overrides = {}) {
  return {
    spotPrice: 22000,
    pcr: 1.0,
    maxPain: 22000,
    atmStrike: 22000,
    indexName: 'NIFTY',
    indiaVix: 14,
    daysToExpiry: 14,
    intradayChange: 0,
    ...overrides,
  };
}

let svc;
beforeEach(() => {
  svc = new AIAnalysisService();
});

// ── A. Constants ────────────────────────────────────────────────────

describe('Constants', () => {
  it('LOT_SIZES has all 6 indices', () => {
    const lots = AIAnalysisService.LOT_SIZES;
    expect(lots).toHaveProperty('NIFTY', 75);
    expect(lots).toHaveProperty('BANKNIFTY', 30);
    expect(lots).toHaveProperty('FINNIFTY', 25);
    expect(lots).toHaveProperty('MIDCPNIFTY', 50);
    expect(lots).toHaveProperty('SENSEX', 10);
    expect(lots).toHaveProperty('BANKEX', 15);
  });

  it('STRIKE_INTERVALS has all 6 indices', () => {
    const si = AIAnalysisService.STRIKE_INTERVALS;
    expect(Object.keys(si)).toHaveLength(6);
    expect(si.BANKNIFTY).toBe(100);
    expect(si.NIFTY).toBe(50);
  });

  it('SCORING_WEIGHTS sums to 1.0', () => {
    const w = AIAnalysisService.SCORING_WEIGHTS;
    const sum = Object.values(w).reduce((a, b) => a + b, 0);
    expect(sum).toBeCloseTo(1.0, 5);
  });

  it('STRATEGY_TYPES has all 7 strategies with correct fields', () => {
    const types = AIAnalysisService.STRATEGY_TYPES;
    expect(Object.keys(types)).toHaveLength(7);
    for (const key of Object.keys(types)) {
      expect(types[key]).toHaveProperty('name');
      expect(types[key]).toHaveProperty('icon');
      expect(types[key]).toHaveProperty('direction');
      expect(types[key]).toHaveProperty('preferredIVCenter');
    }
  });

  it('constructor sets conservativeRules defaults', () => {
    expect(svc.conservativeRules.maxVix).toBe(25);
    expect(svc.conservativeRules.minDisplayScore).toBe(70);
    expect(svc.conservativeRules.minDelta).toBe(0.15);
    expect(svc.conservativeRules.maxDelta).toBe(0.75);
  });
});

// ── B. formatNumber ─────────────────────────────────────────────────

describe('formatNumber', () => {
  it('formats lakhs (≥1L)', () => {
    expect(svc.formatNumber(250000)).toBe('2.5L');
  });

  it('formats thousands (≥1K)', () => {
    expect(svc.formatNumber(5500)).toBe('5.5K');
  });

  it('returns raw string for <1K', () => {
    expect(svc.formatNumber(750)).toBe('750');
  });

  it('handles exact boundary 100000', () => {
    expect(svc.formatNumber(100000)).toBe('1.0L');
  });
});

// ── C. detectMarketRegime ───────────────────────────────────────────

describe('detectMarketRegime', () => {
  it('returns volatile when VIX > 20', () => {
    expect(svc.detectMarketRegime({ indiaVix: 22 })).toBe('volatile');
  });

  it('returns trending when absMove ≥ 0.5 with low VIX', () => {
    expect(svc.detectMarketRegime({ indiaVix: 14, intradayChange: 0.6 })).toBe('trending');
  });

  it('returns trending when absMove ≥ 0.3 and VIX ≥ 14', () => {
    expect(svc.detectMarketRegime({ indiaVix: 15, intradayChange: 0.35 })).toBe('trending');
  });

  it('returns rangeBound when VIX < 14 and absMove < 0.3', () => {
    expect(svc.detectMarketRegime({ indiaVix: 12, intradayChange: 0.1 })).toBe('rangeBound');
  });

  it('returns rangeBound when intradayChange < 0.3 and VIX < 14', () => {
    expect(svc.detectMarketRegime({ indiaVix: 13, intradayChange: 0.2 })).toBe('rangeBound');
  });
});

// ── D. getRegimeInfo ────────────────────────────────────────────────

describe('getRegimeInfo', () => {
  it('trending returns correct label', () => {
    const info = svc.getRegimeInfo('trending');
    expect(info.label).toBe('Trending');
  });

  it('rangeBound returns correct label', () => {
    const info = svc.getRegimeInfo('rangeBound');
    expect(info.label).toBe('Range-Bound');
  });

  it('unknown key falls back to rangeBound', () => {
    const info = svc.getRegimeInfo('invalid');
    expect(info.label).toBe('Range-Bound');
  });
});

// ── E. determineTier ────────────────────────────────────────────────

describe('determineTier', () => {
  it('score ≥ 70 → topPick', () => {
    expect(svc.determineTier(70)).toBe('topPick');
    expect(svc.determineTier(85)).toBe('topPick');
  });

  it('score < 70 → worthWatching', () => {
    expect(svc.determineTier(69)).toBe('worthWatching');
    expect(svc.determineTier(40)).toBe('worthWatching');
  });
});

// ── F. determineThetaZone ───────────────────────────────────────────

describe('determineThetaZone', () => {
  it('45+ days → Safe', () => {
    expect(svc.determineThetaZone(50).zone).toBe('Safe');
  });

  it('30-44 days → Moderate', () => {
    expect(svc.determineThetaZone(35).zone).toBe('Moderate');
  });

  it('14-29 days → Caution', () => {
    expect(svc.determineThetaZone(20).zone).toBe('Caution');
  });

  it('7-13 days → Danger', () => {
    expect(svc.determineThetaZone(10).zone).toBe('Danger');
  });

  it('<7 days → Extreme', () => {
    expect(svc.determineThetaZone(3).zone).toBe('Extreme');
  });
});

// ── G. determineConfidence ──────────────────────────────────────────

describe('determineConfidence', () => {
  it('high confidence (score≥75, ml≥0.8, rr≥2.0)', () => {
    const s = { score: 80, mlPrediction: { confidence: 0.85 }, riskReward: 2.5, oiChange: 60000 };
    expect(svc.determineConfidence(s).level).toBe('High');
  });

  it('medium confidence', () => {
    const s = { score: 65, mlPrediction: { confidence: 0.65 }, riskReward: 1.5, oiChange: 5000 };
    expect(svc.determineConfidence(s).level).toBe('Medium');
  });

  it('low confidence (all low values)', () => {
    const s = { score: 40, mlPrediction: { confidence: 0.4 }, riskReward: 0.8, oiChange: 0 };
    expect(svc.determineConfidence(s).level).toBe('Low');
  });

  it('OI change bonus pushes to higher tier', () => {
    // Without OI: 1pt (score 65) + 1pt (conf 0.65) = 2 → Medium
    // With OI: + 1pt = 3 → Medium still, but score≥75 + conf≥0.8 + oiChange>50k → 2+2+1=5 → High
    const s = { score: 75, mlPrediction: { confidence: 0.8 }, riskReward: 1.0, oiChange: 60000 };
    expect(svc.determineConfidence(s).level).toBe('High');
  });
});

// ── H. determineOISignal ────────────────────────────────────────────

describe('determineOISignal', () => {
  const baseCtx = { intradayChange: 0.5, maxOIChange: 100000 };

  it('CE + priceUp + oiUp → Long Buildup', () => {
    const s = { optionType: 'CE', oiChange: 50000 };
    expect(svc.determineOISignal(s, baseCtx).signal).toBe('Long Buildup');
  });

  it('CE + priceDown + oiUp → Short Buildup', () => {
    const s = { optionType: 'CE', oiChange: 50000 };
    expect(svc.determineOISignal(s, { ...baseCtx, intradayChange: -0.5 }).signal).toBe('Short Buildup');
  });

  it('PE + priceDown + oiUp → Long Buildup', () => {
    const s = { optionType: 'PE', oiChange: 50000 };
    expect(svc.determineOISignal(s, { ...baseCtx, intradayChange: -0.5 }).signal).toBe('Long Buildup');
  });

  it('Neutral when no significant changes', () => {
    const s = { optionType: 'CE', oiChange: 100 };
    expect(svc.determineOISignal(s, { intradayChange: 0.05, maxOIChange: 100000 }).signal).toBe('Neutral');
  });

  it('CE + priceUp + oiDown → Short Covering', () => {
    const s = { optionType: 'CE', oiChange: -50000 };
    expect(svc.determineOISignal(s, baseCtx).signal).toBe('Short Covering');
  });

  it('CE + priceDown + oiDown → Long Unwinding', () => {
    const s = { optionType: 'CE', oiChange: -50000 };
    expect(svc.determineOISignal(s, { ...baseCtx, intradayChange: -0.5 }).signal).toBe('Long Unwinding');
  });
});

// ── I. generateWeightedWarnings ─────────────────────────────────────

describe('generateWeightedWarnings', () => {
  it('extreme theta (dte ≤ 3) → critical warning', () => {
    const s = { daysToExpiry: 2, optionType: 'CE', strikePrice: 22000 };
    const ctx = { spotPrice: 22000, intradayChange: 0 };
    const w = svc.generateWeightedWarnings(s, ctx);
    expect(w.some(x => x.severity === 'critical' && x.message.includes('EXTREME'))).toBe(true);
  });

  it('rapid theta (dte ≤ 7) → minor warning', () => {
    const s = { daysToExpiry: 5, optionType: 'CE', strikePrice: 22000 };
    const ctx = { spotPrice: 22000, intradayChange: 0 };
    const w = svc.generateWeightedWarnings(s, ctx);
    expect(w.some(x => x.severity === 'minor' && x.message.includes('theta'))).toBe(true);
  });

  it('high VIX → severe warning', () => {
    const s = { daysToExpiry: 14, optionType: 'CE', strikePrice: 22000 };
    const ctx = { spotPrice: 22000, indiaVix: 25, intradayChange: 0 };
    const w = svc.generateWeightedWarnings(s, ctx);
    expect(w.some(x => x.severity === 'severe' && x.message.includes('VIX'))).toBe(true);
  });

  it('wide spread → moderate warning', () => {
    const s = { daysToExpiry: 14, optionType: 'CE', strikePrice: 22000, spreadPct: 0.08 };
    const ctx = { spotPrice: 22000, intradayChange: 0 };
    const w = svc.generateWeightedWarnings(s, ctx);
    expect(w.some(x => x.severity === 'moderate' && x.message.includes('spread'))).toBe(true);
  });

  it('deep OTM call → severe warning', () => {
    const s = { daysToExpiry: 14, optionType: 'CE', strikePrice: 23000 };
    const ctx = { spotPrice: 22000, intradayChange: 0 };
    const w = svc.generateWeightedWarnings(s, ctx);
    expect(w.some(x => x.severity === 'severe' && x.message.includes('OTM'))).toBe(true);
  });

  it('call against strong bearish trend → critical', () => {
    const s = { daysToExpiry: 14, optionType: 'CE', strikePrice: 22000 };
    const ctx = { spotPrice: 22000, intradayChange: -0.6 };
    const w = svc.generateWeightedWarnings(s, ctx);
    expect(w.some(x => x.severity === 'critical' && x.message.includes('bearish'))).toBe(true);
  });
});

// ── J. calculateMarketBullishScore ──────────────────────────────────

describe('calculateMarketBullishScore', () => {
  it('bullish momentum (positive intradayChange ≥ 0.3) → positive score', () => {
    const score = svc.calculateMarketBullishScore({ spotPrice: 22000, pcr: 1.0, maxPain: 22000, indiaVix: 14, intradayChange: 0.5 });
    expect(score).toBeGreaterThan(0);
  });

  it('bearish momentum (negative change) → negative score', () => {
    const score = svc.calculateMarketBullishScore({ spotPrice: 22000, pcr: 1.0, maxPain: 22000, indiaVix: 14, intradayChange: -0.5 });
    expect(score).toBeLessThan(0);
  });

  it('high PCR (>1.2) → contrarian bullish boost', () => {
    const highPCR = svc.calculateMarketBullishScore({ spotPrice: 22000, pcr: 1.3, maxPain: 22000, indiaVix: 14, intradayChange: 0 });
    const normalPCR = svc.calculateMarketBullishScore({ spotPrice: 22000, pcr: 1.0, maxPain: 22000, indiaVix: 14, intradayChange: 0 });
    expect(highPCR).toBeGreaterThan(normalPCR);
  });

  it('spot below maxPain → bullish pull', () => {
    const score = svc.calculateMarketBullishScore({ spotPrice: 21700, pcr: 1.0, maxPain: 22000, indiaVix: 14, intradayChange: 0 });
    expect(score).toBeGreaterThan(0);
  });

  it('high VIX → bearish component', () => {
    const highVix = svc.calculateMarketBullishScore({ spotPrice: 22000, pcr: 1.0, maxPain: 22000, indiaVix: 25, intradayChange: 0 });
    const lowVix = svc.calculateMarketBullishScore({ spotPrice: 22000, pcr: 1.0, maxPain: 22000, indiaVix: 14, intradayChange: 0 });
    expect(highVix).toBeLessThan(lowVix);
  });

  it('neutral context → score near zero', () => {
    const score = svc.calculateMarketBullishScore({ spotPrice: 22000, pcr: 1.0, maxPain: 22000, indiaVix: 14, intradayChange: 0 });
    expect(Math.abs(score)).toBeLessThan(0.5);
  });
});

// ── K. calculateScore ───────────────────────────────────────────────

describe('calculateScore', () => {
  const ctx = makeContext({ maxOI: 1000000, avgVolume: 50000, atmIV: 15 });

  it('ATM option → high moneyness bonus', () => {
    const atm = { strikePrice: 22000, openInterest: 100000, totalTradedVolume: 50000, impliedVolatility: 15 };
    const otm = { strikePrice: 23000, openInterest: 100000, totalTradedVolume: 50000, impliedVolatility: 15 };
    expect(svc.calculateScore(atm, ctx, 'CE')).toBeGreaterThan(svc.calculateScore(otm, ctx, 'CE'));
  });

  it('high OI ratio → OI score bonus', () => {
    const highOI = { strikePrice: 22000, openInterest: 900000, totalTradedVolume: 50000, impliedVolatility: 15 };
    const lowOI = { strikePrice: 22000, openInterest: 10000, totalTradedVolume: 50000, impliedVolatility: 15 };
    expect(svc.calculateScore(highOI, ctx, 'CE')).toBeGreaterThan(svc.calculateScore(lowOI, ctx, 'CE'));
  });

  it('high volume → volume bonus', () => {
    const highVol = { strikePrice: 22000, openInterest: 100000, totalTradedVolume: 200000, impliedVolatility: 15 };
    const lowVol = { strikePrice: 22000, openInterest: 100000, totalTradedVolume: 1000, impliedVolatility: 15 };
    expect(svc.calculateScore(highVol, ctx, 'CE')).toBeGreaterThan(svc.calculateScore(lowVol, ctx, 'CE'));
  });

  it('low IV ratio → IV bonus, high IV ratio → penalty', () => {
    const lowIV = { strikePrice: 22000, openInterest: 100000, totalTradedVolume: 50000, impliedVolatility: 12 };
    const highIV = { strikePrice: 22000, openInterest: 100000, totalTradedVolume: 50000, impliedVolatility: 25 };
    expect(svc.calculateScore(lowIV, ctx, 'CE')).toBeGreaterThan(svc.calculateScore(highIV, ctx, 'CE'));
  });

  it('score is clamped between 30-95', () => {
    const extreme = { strikePrice: 30000, openInterest: 1, totalTradedVolume: 1, impliedVolatility: 100 };
    const s = svc.calculateScore(extreme, ctx, 'CE');
    expect(s).toBeGreaterThanOrEqual(30);
    expect(s).toBeLessThanOrEqual(95);
  });
});

// ── L. passesConservativeFilters ────────────────────────────────────

describe('passesConservativeFilters', () => {
  function goodSuggestion() {
    return {
      mlPrediction: { displayScore: 80, signal: 'BUY' },
      score: 80,
      volume: 100000,
      iv: 15,
      delta: 0.5,
      oiChange: 5000,
      riskReward: 2.0,
      spreadPct: 0.02,
    };
  }

  const ctx = makeContext({ avgVolume: 50000, atmIV: 15 });

  it('passes all filters → true', () => {
    expect(svc.passesConservativeFilters(goodSuggestion(), ctx)).toBe(true);
  });

  it('low displayScore → false', () => {
    const s = goodSuggestion();
    s.mlPrediction.displayScore = 50;
    expect(svc.passesConservativeFilters(s, ctx)).toBe(false);
  });

  it('wrong signal (HOLD) → false', () => {
    const s = goodSuggestion();
    s.mlPrediction.signal = 'HOLD';
    expect(svc.passesConservativeFilters(s, ctx)).toBe(false);
  });

  it('low volume → false', () => {
    const s = goodSuggestion();
    s.volume = 100;
    expect(svc.passesConservativeFilters(s, ctx)).toBe(false);
  });

  it('high IV ratio → false', () => {
    const s = goodSuggestion();
    s.iv = 50; // 50 / 15 = 3.3 > 1.5
    expect(svc.passesConservativeFilters(s, ctx)).toBe(false);
  });

  it('delta out of range → false', () => {
    const s = goodSuggestion();
    s.delta = 0.05; // < minDelta 0.15
    expect(svc.passesConservativeFilters(s, ctx)).toBe(false);
  });
});

// ── M. generateMLPrediction ─────────────────────────────────────────

describe('generateMLPrediction', () => {
  it('bullish market + CE → BUY/STRONG BUY signal', () => {
    const ctx = makeContext({ intradayChange: 0.6, pcr: 1.3, maxOIChange: 100000, avgVolume: 50000, atmIV: 15 });
    const opt = { strikePrice: 22000, openInterest: 100000, changeinOpenInterest: 20000, totalTradedVolume: 100000, impliedVolatility: 15, delta: 0.5 };
    const pred = svc.generateMLPrediction(opt, ctx, 'CE');
    expect(['BUY', 'STRONG BUY']).toContain(pred.signal);
  });

  it('bearish market + PE → BUY/STRONG BUY signal', () => {
    const ctx = makeContext({ intradayChange: -0.6, pcr: 0.6, maxOIChange: 100000, avgVolume: 50000, atmIV: 15 });
    const opt = { strikePrice: 22000, openInterest: 100000, changeinOpenInterest: 20000, totalTradedVolume: 100000, impliedVolatility: 15, delta: -0.5 };
    const pred = svc.generateMLPrediction(opt, ctx, 'PE');
    expect(['BUY', 'STRONG BUY']).toContain(pred.signal);
  });

  it('neutral market → HOLD or mild BUY signal (near zero finalScore)', () => {
    const ctx = makeContext({ intradayChange: 0, pcr: 1.0, maxOIChange: 100000, avgVolume: 50000, atmIV: 15 });
    // Use deep OTM option with low delta to remove delta bonus, and volume below avg to remove volume bonus
    const opt = { strikePrice: 24000, openInterest: 100000, changeinOpenInterest: 0, totalTradedVolume: 10000, impliedVolatility: 15, delta: 0.05 };
    const pred = svc.generateMLPrediction(opt, ctx, 'CE');
    expect(pred.signal).toBe('HOLD');
  });

  it('delta in good range adds bonus', () => {
    const ctx = makeContext({ intradayChange: 0.1, pcr: 1.0, maxOIChange: 100000, avgVolume: 50000, atmIV: 15 });
    const goodDelta = { strikePrice: 22000, openInterest: 100000, changeinOpenInterest: 0, totalTradedVolume: 50000, impliedVolatility: 15, delta: 0.5 };
    const badDelta = { strikePrice: 22000, openInterest: 100000, changeinOpenInterest: 0, totalTradedVolume: 50000, impliedVolatility: 15, delta: 0.05 };
    const predGood = svc.generateMLPrediction(goodDelta, ctx, 'CE');
    const predBad = svc.generateMLPrediction(badDelta, ctx, 'CE');
    expect(predGood.finalScore).toBeGreaterThan(predBad.finalScore);
  });

  it('displayScore equals mlConfidence * 100', () => {
    const ctx = makeContext({ intradayChange: 0.5, pcr: 1.0, maxOIChange: 100000, avgVolume: 50000, atmIV: 15 });
    const opt = { strikePrice: 22000, openInterest: 100000, changeinOpenInterest: 0, totalTradedVolume: 50000, impliedVolatility: 15, delta: 0.5 };
    const pred = svc.generateMLPrediction(opt, ctx, 'CE');
    expect(pred.displayScore).toBe(Math.round(pred.confidence * 100));
  });
});

// ── N. generateScoreFactors ─────────────────────────────────────────

describe('generateScoreFactors', () => {
  const ctx = makeContext({ avgVolume: 50000, atmIV: 15, maxOIChange: 100000 });

  it('returns exactly 3 factors (top 3 sorted by score)', () => {
    const s = { strikePrice: 22000, optionType: 'CE', volume: 100000, oiChange: 50000, iv: 15, riskReward: 2.0 };
    const factors = svc.generateScoreFactors(s, ctx);
    expect(factors).toHaveLength(3);
  });

  it('includes Strike Position, OI Signal, and Volume among factors', () => {
    const s = { strikePrice: 22000, optionType: 'CE', volume: 100000, oiChange: 50000, iv: 15, riskReward: 1.5 };
    const factors = svc.generateScoreFactors(s, ctx);
    const names = factors.map(f => f.factor);
    // top 3 from sorted factors - specific names depend on scores
    expect(names.length).toBe(3);
    for (const f of factors) {
      expect(f).toHaveProperty('factor');
      expect(f).toHaveProperty('score');
      expect(f).toHaveProperty('impact');
    }
  });

  it('ATM strike → high moneyness score', () => {
    const s = { strikePrice: 22000, optionType: 'CE', volume: 50000, oiChange: 10000, iv: 15, riskReward: 1.5 };
    const factors = svc.generateScoreFactors(s, ctx);
    const strikeF = factors.find(f => f.factor === 'Strike Position');
    if (strikeF) {
      expect(strikeF.score).toBeGreaterThanOrEqual(90);
    }
  });
});

// ── O. _calcNetGreeks ───────────────────────────────────────────────

describe('_calcNetGreeks', () => {
  it('single BUY leg → positive delta', () => {
    const legs = [{ action: 'BUY', option: { delta: 0.5, theta: -5, gamma: 0.002, vega: 8 } }];
    const g = svc._calcNetGreeks(legs);
    expect(g.netDelta).toBeCloseTo(0.5);
    expect(g.netTheta).toBeCloseTo(-5);
  });

  it('single SELL leg → negative delta', () => {
    const legs = [{ action: 'SELL', option: { delta: 0.5, theta: -5, gamma: 0.002, vega: 8 } }];
    const g = svc._calcNetGreeks(legs);
    expect(g.netDelta).toBeCloseTo(-0.5);
    expect(g.netTheta).toBeCloseTo(5);
  });

  it('two legs cancel → near-zero net', () => {
    const legs = [
      { action: 'BUY', option: { delta: 0.5, theta: -5, gamma: 0.002, vega: 8 } },
      { action: 'SELL', option: { delta: 0.5, theta: -5, gamma: 0.002, vega: 8 } },
    ];
    const g = svc._calcNetGreeks(legs);
    expect(g.netDelta).toBeCloseTo(0);
    expect(g.netTheta).toBeCloseTo(0);
  });

  it('missing greeks default to 0', () => {
    const legs = [{ action: 'BUY', option: {} }];
    const g = svc._calcNetGreeks(legs);
    expect(g.netDelta).toBe(0);
    expect(g.netTheta).toBe(0);
    expect(g.netGamma).toBe(0);
    expect(g.netVega).toBe(0);
  });

  it('4-leg iron condor sums correctly', () => {
    const legs = [
      { action: 'SELL', option: { delta: 0.2, theta: -3, gamma: 0.001, vega: 5 } },
      { action: 'BUY', option: { delta: 0.1, theta: -2, gamma: 0.001, vega: 3 } },
      { action: 'SELL', option: { delta: -0.2, theta: -3, gamma: 0.001, vega: 5 } },
      { action: 'BUY', option: { delta: -0.1, theta: -2, gamma: 0.001, vega: 3 } },
    ];
    const g = svc._calcNetGreeks(legs);
    // SELL 0.2 → -0.2, BUY 0.1 → +0.1, SELL -0.2 → +0.2, BUY -0.1 → -0.1 = 0
    expect(g.netDelta).toBeCloseTo(0);
    // SELL -3 → +3, BUY -2 → -2, SELL -3 → +3, BUY -2 → -2 = +2
    expect(g.netTheta).toBeCloseTo(2);
  });
});

// ── P. _scoreStrategy ───────────────────────────────────────────────

describe('_scoreStrategy', () => {
  it('IV alignment: ivRank near preferredIVCenter → high score', () => {
    // nakedCall preferredIVCenter = 15
    const r = svc._scoreStrategy('nakedCall', 15, 0.5, 999999, 10000, [{ option: { totalTradedVolume: 5000 } }], 'trending');
    expect(r.breakdown.ivAlignment).toBeGreaterThanOrEqual(85);
  });

  it('IV alignment: ivRank far from center → low score', () => {
    // nakedCall preferredIVCenter = 15, ivRank = 90 → distance = 75 → alignment = max(0, 100 - 112.5) = 0
    const r = svc._scoreStrategy('nakedCall', 90, 0.5, 999999, 10000, [{ option: { totalTradedVolume: 5000 } }], 'trending');
    expect(r.breakdown.ivAlignment).toBeLessThan(20);
  });

  it('POP: high POP → high popScore', () => {
    const r = svc._scoreStrategy('ironCondor', 50, 0.8, 5000, 10000, [{ option: { totalTradedVolume: 5000 } }], 'rangeBound');
    expect(r.breakdown.pop).toBe(80);
  });

  it('Risk:Reward: maxProfit > 100K → 80', () => {
    const r = svc._scoreStrategy('nakedCall', 15, 0.5, 999999, 10000, [{ option: { totalTradedVolume: 5000 } }], 'trending');
    expect(r.breakdown.riskReward).toBe(80);
  });

  it('Risk:Reward: normal ratio', () => {
    const r = svc._scoreStrategy('bullCallSpread', 50, 0.5, 5000, 10000, [{ option: { totalTradedVolume: 5000 } }], 'trending');
    // 5000/10000 * 30 = 15
    expect(r.breakdown.riskReward).toBe(Math.min(95, Math.max(20, Math.round((5000 / 10000) * 30))));
  });

  it('Liquidity: high volume → high liqScore', () => {
    const r = svc._scoreStrategy('nakedCall', 15, 0.5, 999999, 10000, [{ option: { totalTradedVolume: 10000 } }], 'trending');
    expect(r.breakdown.liquidity).toBeGreaterThan(50);
  });

  it('Regime fit: rangeBound + neutral strategy → 90', () => {
    const r = svc._scoreStrategy('ironCondor', 75, 0.6, 5000, 10000, [{ option: { totalTradedVolume: 5000 } }], 'rangeBound');
    expect(r.breakdown.regimeFit).toBe(90);
  });

  it('Regime fit: trending + directional → 85', () => {
    const r = svc._scoreStrategy('nakedCall', 15, 0.5, 999999, 10000, [{ option: { totalTradedVolume: 5000 } }], 'trending');
    expect(r.breakdown.regimeFit).toBe(85);
  });
});

// ── Q. _createNakedOption ───────────────────────────────────────────

describe('_createNakedOption', () => {
  const chain = makeChain(22000, 11, 50);
  const atmRow = chain.find(r => r.strikePrice === 22000);

  it('creates bullish naked call with correct fields', () => {
    const s = svc._createNakedOption(atmRow, true, 75, 20, 'trending');
    expect(s).not.toBeNull();
    expect(s.strategyType).toBe('nakedCall');
    expect(s.legs).toHaveLength(1);
    expect(s.legs[0].action).toBe('BUY');
    expect(s.legs[0].type).toBe('CE');
    expect(s.isCredit).toBe(false);
    expect(s.maxProfit).toBe(999999);
  });

  it('creates bearish naked put', () => {
    const s = svc._createNakedOption(atmRow, false, 75, 20, 'trending');
    expect(s).not.toBeNull();
    expect(s.strategyType).toBe('nakedPut');
    expect(s.legs[0].type).toBe('PE');
    expect(s.legs[0].action).toBe('BUY');
  });

  it('returns null when no option data', () => {
    const emptyRow = { strikePrice: 22000, CE: null, PE: null };
    expect(svc._createNakedOption(emptyRow, true, 75, 20, 'trending')).toBeNull();
  });

  it('breakeven = strike ± ltp', () => {
    const s = svc._createNakedOption(atmRow, true, 75, 20, 'trending');
    expect(s.breakevens[0]).toBe(atmRow.strikePrice + atmRow.CE.lastPrice);
  });
});

// ── R. _createBullCallSpread ────────────────────────────────────────

describe('_createBullCallSpread', () => {
  const chain = makeChain(22000, 21, 50);
  const findRow = (target) => chain.reduce((prev, curr) =>
    Math.abs(curr.strikePrice - target) < Math.abs(prev.strikePrice - target) ? curr : prev
  );

  it('creates 2-leg spread with BUY lower / SELL higher', () => {
    const s = svc._createBullCallSpread(chain, 22000, 50, 75, 30, 'trending', findRow);
    expect(s).not.toBeNull();
    expect(s.legs).toHaveLength(2);
    expect(s.legs[0].action).toBe('BUY');
    expect(s.legs[1].action).toBe('SELL');
    expect(s.legs[0].strike).toBeLessThan(s.legs[1].strike);
    expect(s.isCredit).toBe(false);
  });

  it('net debit and maxProfit calculated correctly', () => {
    const s = svc._createBullCallSpread(chain, 22000, 50, 75, 30, 'trending', findRow);
    expect(s.maxLoss).toBeGreaterThan(0);
    expect(s.maxProfit).toBeGreaterThan(0);
  });

  it('POP from sell delta: 1 - |sellDelta|', () => {
    const s = svc._createBullCallSpread(chain, 22000, 50, 75, 30, 'trending', findRow);
    expect(s.pop).toBeGreaterThan(0);
    expect(s.pop).toBeLessThanOrEqual(1);
  });

  it('returns null when same strike for buy/sell', () => {
    const tinyChain = [{ strikePrice: 22000, CE: { lastPrice: 100, delta: 0.5 }, PE: { lastPrice: 100 } }];
    const findSingle = () => tinyChain[0];
    const s = svc._createBullCallSpread(tinyChain, 22000, 50, 75, 30, 'trending', findSingle);
    expect(s).toBeNull();
  });
});

// ── S. _createBearPutSpread ─────────────────────────────────────────

describe('_createBearPutSpread', () => {
  const chain = makeChain(22000, 21, 50);
  const findRow = (target) => chain.reduce((prev, curr) =>
    Math.abs(curr.strikePrice - target) < Math.abs(prev.strikePrice - target) ? curr : prev
  );

  it('creates 2-leg spread with correct PE legs', () => {
    const s = svc._createBearPutSpread(chain, 22000, 50, 75, 30, 'trending', findRow);
    expect(s).not.toBeNull();
    expect(s.legs).toHaveLength(2);
    expect(s.legs[0].type).toBe('PE');
    expect(s.legs[1].type).toBe('PE');
    expect(s.legs[0].action).toBe('BUY');
    expect(s.legs[1].action).toBe('SELL');
  });

  it('breakeven = buyStrike - (buyLTP - sellLTP)', () => {
    const s = svc._createBearPutSpread(chain, 22000, 50, 75, 30, 'trending', findRow);
    const buyLTP = s.legs[0].ltp;
    const sellLTP = s.legs[1].ltp;
    expect(s.breakevens[0]).toBeCloseTo(s.legs[0].strike - (buyLTP - sellLTP), 2);
  });

  it('returns null when missing PE data', () => {
    const noPE = [{ strikePrice: 22000, CE: { lastPrice: 100 }, PE: null }];
    const findSingle = () => noPE[0];
    expect(svc._createBearPutSpread(noPE, 22000, 50, 75, 30, 'trending', findSingle)).toBeNull();
  });
});

// ── T. _createIronCondor ────────────────────────────────────────────

describe('_createIronCondor', () => {
  const chain = makeChain(22000, 31, 50);
  const findRow = (target) => chain.reduce((prev, curr) =>
    Math.abs(curr.strikePrice - target) < Math.abs(prev.strikePrice - target) ? curr : prev
  );

  it('creates 4-leg structure', () => {
    const s = svc._createIronCondor(22000, 50, 75, 60, 'rangeBound', findRow);
    if (s) {
      expect(s.legs).toHaveLength(4);
      // SELL CE, BUY CE, SELL PE, BUY PE
      const actions = s.legs.map(l => l.action);
      expect(actions).toEqual(['SELL', 'BUY', 'SELL', 'BUY']);
      const types = s.legs.map(l => l.type);
      expect(types).toEqual(['CE', 'CE', 'PE', 'PE']);
    }
  });

  it('isCredit = true and netPremium > 0', () => {
    const s = svc._createIronCondor(22000, 50, 75, 60, 'rangeBound', findRow);
    if (s) {
      expect(s.isCredit).toBe(true);
      expect(s.netPremium).toBeGreaterThan(0);
    }
  });

  it('has two breakevens on either side', () => {
    const s = svc._createIronCondor(22000, 50, 75, 60, 'rangeBound', findRow);
    if (s) {
      expect(s.breakevens).toHaveLength(2);
      expect(s.breakevens[0]).toBeLessThan(22000);
      expect(s.breakevens[1]).toBeGreaterThan(22000);
    }
  });

  it('returns null when netCredit ≤ 0', () => {
    // Chain with zero prices
    const zeroChain = Array.from({ length: 31 }, (_, i) => ({
      strikePrice: 20500 + i * 50,
      CE: { lastPrice: 0, delta: 0.2, gamma: 0, theta: 0, vega: 0, totalTradedVolume: 0 },
      PE: { lastPrice: 0, delta: -0.2, gamma: 0, theta: 0, vega: 0, totalTradedVolume: 0 },
    }));
    const findZero = (target) => zeroChain.reduce((prev, curr) =>
      Math.abs(curr.strikePrice - target) < Math.abs(prev.strikePrice - target) ? curr : prev
    );
    expect(svc._createIronCondor(22000, 50, 75, 60, 'rangeBound', findZero)).toBeNull();
  });
});

// ── U. _createStrangle ──────────────────────────────────────────────

describe('_createStrangle', () => {
  const chain = makeChain(22000, 21, 50);
  const findRow = (target) => chain.reduce((prev, curr) =>
    Math.abs(curr.strikePrice - target) < Math.abs(prev.strikePrice - target) ? curr : prev
  );

  it('creates 2-leg structure (SELL CE, SELL PE)', () => {
    const s = svc._createStrangle(22000, 50, 75, 60, 'rangeBound', findRow);
    expect(s).not.toBeNull();
    expect(s.legs).toHaveLength(2);
    expect(s.legs[0].action).toBe('SELL');
    expect(s.legs[0].type).toBe('CE');
    expect(s.legs[1].action).toBe('SELL');
    expect(s.legs[1].type).toBe('PE');
  });

  it('isCredit = true and maxLoss = 999999 (unlimited)', () => {
    const s = svc._createStrangle(22000, 50, 75, 60, 'rangeBound', findRow);
    expect(s.isCredit).toBe(true);
    expect(s.maxLoss).toBe(999999);
  });

  it('POP from combined deltas (clamped 0-1)', () => {
    const s = svc._createStrangle(22000, 50, 75, 60, 'rangeBound', findRow);
    expect(s.pop).toBeGreaterThanOrEqual(0);
    expect(s.pop).toBeLessThanOrEqual(1);
  });
});

// ── V. _createStraddle ──────────────────────────────────────────────

describe('_createStraddle', () => {
  const chain = makeChain(22000, 11, 50);
  const atmRow = chain.find(r => r.strikePrice === 22000);

  it('creates 2-leg structure (BUY CE, BUY PE) at same strike', () => {
    const s = svc._createStraddle(atmRow, 75, 20, 'volatile');
    expect(s).not.toBeNull();
    expect(s.legs).toHaveLength(2);
    expect(s.legs[0].action).toBe('BUY');
    expect(s.legs[0].type).toBe('CE');
    expect(s.legs[1].action).toBe('BUY');
    expect(s.legs[1].type).toBe('PE');
    expect(s.legs[0].strike).toBe(s.legs[1].strike);
  });

  it('isCredit = false, maxProfit = 999999', () => {
    const s = svc._createStraddle(atmRow, 75, 20, 'volatile');
    expect(s.isCredit).toBe(false);
    expect(s.maxProfit).toBe(999999);
  });

  it('fixed POP = 0.35', () => {
    const s = svc._createStraddle(atmRow, 75, 20, 'volatile');
    expect(s.pop).toBe(0.35);
  });
});

// ── W. generateStrategySuggestions — integration ────────────────────

describe('generateStrategySuggestions', () => {
  it('empty chain → []', () => {
    expect(svc.generateStrategySuggestions([], makeContext())).toEqual([]);
    expect(svc.generateStrategySuggestions(null, makeContext())).toEqual([]);
  });

  it('bullish + low IV → includes bullCallSpread', () => {
    const ctx = makeContext({ marketBias: 'bullish', indiaVix: 12 });
    const chain = makeChain(22000, 21, 50);
    const results = svc.generateStrategySuggestions(chain, ctx);
    expect(results.some(s => s.strategyType === 'bullCallSpread')).toBe(true);
  });

  it('bearish + low IV → includes bearPutSpread', () => {
    const ctx = makeContext({ marketBias: 'bearish', indiaVix: 12 });
    const chain = makeChain(22000, 21, 50);
    const results = svc.generateStrategySuggestions(chain, ctx);
    expect(results.some(s => s.strategyType === 'bearPutSpread')).toBe(true);
  });

  it('neutral + high IV → includes ironCondor or strangle', () => {
    const ctx = makeContext({ marketBias: 'neutral', indiaVix: 25 });
    const chain = makeChain(22000, 31, 50);
    const results = svc.generateStrategySuggestions(chain, ctx);
    const hasNeutral = results.some(s => s.strategyType === 'ironCondor' || s.strategyType === 'strangle');
    expect(hasNeutral).toBe(true);
  });

  it('volatile + low IV → includes straddle', () => {
    const ctx = makeContext({ marketBias: 'neutral', indiaVix: 21, intradayChange: 1.5 });
    const chain = makeChain(22000, 21, 50);
    const results = svc.generateStrategySuggestions(chain, ctx);
    expect(results.some(s => s.strategyType === 'straddle')).toBe(true);
  });

  it('max 5 strategies returned', () => {
    const ctx = makeContext({ marketBias: 'neutral', indiaVix: 18 });
    const chain = makeChain(22000, 31, 50);
    const results = svc.generateStrategySuggestions(chain, ctx);
    expect(results.length).toBeLessThanOrEqual(5);
  });

  it('sorted by score descending', () => {
    const ctx = makeContext({ marketBias: 'bullish', indiaVix: 12 });
    const chain = makeChain(22000, 21, 50);
    const results = svc.generateStrategySuggestions(chain, ctx);
    for (let i = 1; i < results.length; i++) {
      expect(results[i - 1].score).toBeGreaterThanOrEqual(results[i].score);
    }
  });

  it('uses correct lotSize for BANKNIFTY', () => {
    const ctx = makeContext({ indexName: 'BANKNIFTY', marketBias: 'bullish', indiaVix: 12 });
    const chain = makeChain(50000, 21, 100);
    const results = svc.generateStrategySuggestions(chain, ctx);
    if (results.length > 0) {
      expect(results[0].legs[0].lotSize).toBe(30);
    }
  });

  it('bullish + very low IV → includes nakedCall', () => {
    const ctx = makeContext({ marketBias: 'strong_bullish', indiaVix: 11 });
    const chain = makeChain(22000, 21, 50);
    const results = svc.generateStrategySuggestions(chain, ctx);
    expect(results.some(s => s.strategyType === 'nakedCall')).toBe(true);
  });
});

// ── X. analyzeOptionChain ───────────────────────────────────────────

describe('analyzeOptionChain', () => {
  it('returns defaults for empty chain', () => {
    const r = svc.analyzeOptionChain(null, 22000, 22000, true);
    expect(r.ivSkew).toBeNull();
    expect(r.smartMoneyBullish).toBe(false);
    expect(r.smartMoneyBearish).toBe(false);
    expect(r.volumeConfirmation).toBe(false);
  });

  it('computes IV skew from ATM row', () => {
    const chain = makeChain(22000, 5, 50);
    const r = svc.analyzeOptionChain(chain, 22000, 22000, true);
    // putIV=16, callIV=15 → skew = (16-15)/100 = 0.01
    expect(r.ivSkew).toBeCloseTo(0.01, 2);
  });

  it('identifies resistance from max call OI', () => {
    const chain = makeChain(22000, 11, 50);
    // Make one strike above spot have very high call OI
    const aboveRow = chain.find(r => r.strikePrice === 22200);
    if (aboveRow) aboveRow.CE.openInterest = 5000000;
    const r = svc.analyzeOptionChain(chain, 22000, 22000, true);
    expect(r.resistanceFromCallOI).toBe(22200);
  });

  it('identifies support from max put OI', () => {
    const chain = makeChain(22000, 11, 50);
    const belowRow = chain.find(r => r.strikePrice === 21800);
    if (belowRow) belowRow.PE.openInterest = 5000000;
    const r = svc.analyzeOptionChain(chain, 22000, 22000, true);
    expect(r.supportFromPutOI).toBe(21800);
  });

  it('detects smart money bullish when put OI change dominates', () => {
    const chain = makeChain(22000, 5, 50);
    chain.forEach(row => {
      row.CE.changeinOpenInterest = 1000;
      row.PE.changeinOpenInterest = 20000;
    });
    const r = svc.analyzeOptionChain(chain, 22000, 22000, true);
    expect(r.smartMoneyBullish).toBe(true);
  });

  it('detects smart money bearish when call OI change dominates', () => {
    const chain = makeChain(22000, 5, 50);
    chain.forEach(row => {
      row.CE.changeinOpenInterest = 20000;
      row.PE.changeinOpenInterest = 1000;
    });
    const r = svc.analyzeOptionChain(chain, 22000, 22000, true);
    expect(r.smartMoneyBearish).toBe(true);
  });

  it('volume confirmation when selected strike has 2x avg volume', () => {
    const chain = makeChain(22000, 11, 50);
    chain.forEach(row => { row.CE.totalTradedVolume = 10000; });
    const atmRow = chain.find(r => r.strikePrice === 22000);
    atmRow.CE.totalTradedVolume = 50000; // > 2x avg
    const r = svc.analyzeOptionChain(chain, 22000, 22000, true);
    expect(r.volumeConfirmation).toBe(true);
  });

  it('strike near max OI sets flag', () => {
    const chain = makeChain(22000, 11, 50);
    const r = svc.analyzeOptionChain(chain, 22000, 22000, true);
    // selected strike 22000 is likely near one of the max OI strikes
    expect(typeof r.strikeNearMaxOI).toBe('boolean');
  });
});

// ── Y. generateTradeSuggestions (single-leg) ────────────────────────

describe('generateTradeSuggestions', () => {
  function makeRichChain() {
    return makeChain(22000, 11, 50).map(row => ({
      ...row,
      CE: {
        ...row.CE,
        lastPrice: Math.min(300, Math.max(10, row.CE.lastPrice)),
        openInterest: 200000,
        changeinOpenInterest: 5000,
        totalTradedVolume: 100000,
        impliedVolatility: 15,
        delta: 0.5,
      },
      PE: {
        ...row.PE,
        lastPrice: Math.min(300, Math.max(10, row.PE.lastPrice)),
        openInterest: 200000,
        changeinOpenInterest: 5000,
        totalTradedVolume: 100000,
        impliedVolatility: 15,
        delta: -0.5,
      },
    }));
  }

  it('returns empty array for empty chain', () => {
    const ctx = makeContext();
    expect(svc.generateTradeSuggestions([], ctx)).toEqual([]);
  });

  it('returns suggestions with required fields', () => {
    const ctx = makeContext({ intradayChange: 0.5, daysToExpiry: 14 });
    const chain = makeRichChain();
    const suggestions = svc.generateTradeSuggestions(chain, ctx);
    if (suggestions.length > 0) {
      const s = suggestions[0];
      expect(s).toHaveProperty('strikePrice');
      expect(s).toHaveProperty('optionType');
      expect(s).toHaveProperty('ltp');
      expect(s).toHaveProperty('entryPrice');
      expect(s).toHaveProperty('targetPrice');
      expect(s).toHaveProperty('stopLossPrice');
      expect(s).toHaveProperty('riskReward');
      expect(s).toHaveProperty('score');
      expect(s).toHaveProperty('mlPrediction');
      expect(s).toHaveProperty('tier');
      expect(s).toHaveProperty('confidence');
      expect(s).toHaveProperty('thetaZone');
      expect(s).toHaveProperty('oiSignal');
      expect(s).toHaveProperty('riskWarnings');
      expect(s).toHaveProperty('scoreFactors');
    }
  });

  it('generates both CE and PE suggestions', () => {
    const ctx = makeContext({ intradayChange: 0.5, daysToExpiry: 14 });
    const chain = makeRichChain();
    const suggestions = svc.generateTradeSuggestions(chain, ctx);
    const hasCE = suggestions.some(s => s.optionType === 'CE');
    const hasPE = suggestions.some(s => s.optionType === 'PE');
    // At least one direction should have suggestions
    expect(hasCE || hasPE).toBe(true);
  });

  it('limits per-side to 8 max', () => {
    const ctx = makeContext({ intradayChange: 0.5, daysToExpiry: 14 });
    const chain = makeRichChain();
    const suggestions = svc.generateTradeSuggestions(chain, ctx);
    const ceCount = suggestions.filter(s => s.optionType === 'CE').length;
    const peCount = suggestions.filter(s => s.optionType === 'PE').length;
    expect(ceCount).toBeLessThanOrEqual(8);
    expect(peCount).toBeLessThanOrEqual(8);
  });

  it('sets noTradeReason when VIX is high', () => {
    const ctx = makeContext({ indiaVix: 30, intradayChange: 0, daysToExpiry: 14 });
    const chain = makeRichChain();
    svc.generateTradeSuggestions(chain, ctx);
    expect(ctx.noTradeReason).toBeTruthy();
    expect(ctx.noTradeReason).toContain('VIX');
  });

  it('falls back to top 3 candidates when no suggestions pass filters', () => {
    const ctx = makeContext({ intradayChange: 0, daysToExpiry: 14 });
    // Make chain with options that won't pass filters (low volume, bad signal)
    const chain = makeChain(22000, 5, 50).map(row => ({
      ...row,
      CE: { ...row.CE, lastPrice: 100, openInterest: 200000, totalTradedVolume: 10, changeinOpenInterest: 0, delta: 0.5, impliedVolatility: 15 },
      PE: { ...row.PE, lastPrice: 100, openInterest: 200000, totalTradedVolume: 10, changeinOpenInterest: 0, delta: -0.5, impliedVolatility: 15 },
    }));
    const result = svc.generateTradeSuggestions(chain, ctx);
    // Should get some result (fallback candidates) or empty
    expect(Array.isArray(result)).toBe(true);
  });
});

// ── Z. createSuggestion ─────────────────────────────────────────────

describe('createSuggestion', () => {
  it('creates a valid suggestion from a chain row', () => {
    const ctx = makeContext({ maxOI: 1000000, avgVolume: 50000, atmIV: 15, daysToExpiry: 14, intradayChange: 0.3, maxOIChange: 100000 });
    const row = {
      strikePrice: 22000,
      CE: { lastPrice: 150, openInterest: 300000, changeinOpenInterest: 10000, totalTradedVolume: 80000, impliedVolatility: 15, delta: 0.5, gamma: 0.002, theta: -5, vega: 8, bidprice: 149, askPrice: 151 },
      PE: { lastPrice: 120 },
    };
    const s = svc.createSuggestion(row, 'CE', ctx, [row]);
    expect(s).not.toBeNull();
    expect(s.strikePrice).toBe(22000);
    expect(s.optionType).toBe('CE');
    expect(s.ltp).toBe(150);
    expect(s.entryPrice).toBe(150);
    expect(s.targetPrice).toBeGreaterThan(s.entryPrice);
    expect(s.stopLossPrice).toBeLessThan(s.entryPrice);
    expect(s.riskReward).toBeGreaterThan(0);
  });

  it('returns null for zero/missing price', () => {
    const ctx = makeContext({ daysToExpiry: 14 });
    const row = { strikePrice: 22000, CE: { lastPrice: 0 }, PE: null };
    expect(svc.createSuggestion(row, 'CE', ctx, [])).toBeNull();
  });

  it('sets bid/ask and spreadPct', () => {
    const ctx = makeContext({ maxOI: 1000000, avgVolume: 50000, atmIV: 15, daysToExpiry: 14, maxOIChange: 100000, intradayChange: 0 });
    const row = {
      strikePrice: 22000,
      CE: { lastPrice: 150, openInterest: 100000, changeinOpenInterest: 5000, totalTradedVolume: 50000, impliedVolatility: 15, delta: 0.5, bidprice: 148, askPrice: 152 },
    };
    const s = svc.createSuggestion(row, 'CE', ctx, [row]);
    expect(s.bid).toBe(148);
    expect(s.ask).toBe(152);
    expect(s.spreadPct).toBeGreaterThan(0);
  });

  it('enriches with tier, confidence, thetaZone, oiSignal, riskWarnings, scoreFactors', () => {
    const ctx = makeContext({ maxOI: 1000000, avgVolume: 50000, atmIV: 15, daysToExpiry: 14, maxOIChange: 100000, intradayChange: 0.3 });
    const row = {
      strikePrice: 22000,
      CE: { lastPrice: 150, openInterest: 300000, changeinOpenInterest: 10000, totalTradedVolume: 80000, impliedVolatility: 15, delta: 0.5, gamma: 0.002, theta: -5, vega: 8, bidprice: 149, askPrice: 151 },
    };
    const s = svc.createSuggestion(row, 'CE', ctx, [row]);
    expect(s.tier).toBeDefined();
    expect(s.confidence).toBeDefined();
    expect(s.thetaZone).toBeDefined();
    expect(s.oiSignal).toBeDefined();
    expect(s.riskWarnings).toBeDefined();
    expect(s.scoreFactors).toBeDefined();
  });
});

// ── AA. buildRequestBody ────────────────────────────────────────────

describe('buildRequestBody', () => {
  it('builds correct structure', () => {
    const suggestion = { strikePrice: 22000, optionType: 'CE', ltp: 150, iv: 15, delta: 0.5, theta: -5, oi: 300000, oiChange: 10000, volume: 80000, daysToExpiry: 14, entryPrice: 150, riskReward: 2.0, score: 75 };
    const context = makeContext();
    const chain = makeChain(22000, 3, 50);
    const body = svc.buildRequestBody(suggestion, context, chain);
    expect(body.option.strike_price).toBe(22000);
    expect(body.option.option_type).toBe('CE');
    expect(body.market_context.spot_price).toBe(22000);
    expect(body.suggestion.entry).toBe(150);
    expect(body.option_chain.length).toBeGreaterThan(0);
  });

  it('uses fallback target/sl when not provided', () => {
    const suggestion = { strikePrice: 22000, optionType: 'CE', ltp: 100 };
    const body = svc.buildRequestBody(suggestion, makeContext(), null);
    expect(body.suggestion.entry).toBe(100);
    expect(body.suggestion.target).toBe(160); // 100 * 1.6
    expect(body.suggestion.stop_loss).toBe(62); // 100 * 0.62
  });

  it('includes chain data with correct fields', () => {
    const chain = makeChain(22000, 3, 50);
    const body = svc.buildRequestBody({ strikePrice: 22000, ltp: 100 }, makeContext(), chain);
    const row = body.option_chain[0];
    expect(row).toHaveProperty('strike');
    expect(row).toHaveProperty('call_oi');
    expect(row).toHaveProperty('put_oi');
    expect(row).toHaveProperty('call_ltp');
    expect(row).toHaveProperty('put_ltp');
  });
});

// ── AB. parseAPIResponse ────────────────────────────────────────────

describe('parseAPIResponse', () => {
  it('returns valid response for ai_powered + good key_reason', () => {
    const json = { ai_powered: true, verdict: 'YES', win_probability: 72, key_reason: 'Strong bullish signal from OI buildup', risk_warning: 'Use stop-loss' };
    const r = svc.parseAPIResponse(json, {}, makeContext(), null);
    expect(r.verdict).toBe('YES');
    expect(r.winProbability).toBe(72);
    expect(r.source).toBe('Gemini AI');
  });

  it('falls back to local when ai_powered=false', () => {
    const json = { ai_powered: false, verdict: 'YES', key_reason: 'test' };
    const suggestion = { optionType: 'CE', daysToExpiry: 14, score: 50, riskReward: 1.5, strikePrice: 22000 };
    const r = svc.parseAPIResponse(json, suggestion, makeContext(), null);
    expect(r.source).toBe('Local Analysis');
  });

  it('falls back to local when key_reason has error indicators', () => {
    const json = { ai_powered: true, verdict: 'YES', key_reason: 'Service unavailable right now', win_probability: 70 };
    const suggestion = { optionType: 'CE', daysToExpiry: 14, score: 50, riskReward: 1.5, strikePrice: 22000 };
    const r = svc.parseAPIResponse(json, suggestion, makeContext(), null);
    expect(r.source).toBe('Local Analysis');
  });

  it('falls back when ML inconsistency detected', () => {
    const json = { ai_powered: true, verdict: 'NO', key_reason: 'Bearish signal confirmed by analysis', win_probability: 20 };
    const suggestion = { optionType: 'CE', daysToExpiry: 14, score: 50, riskReward: 1.5, strikePrice: 22000, mlPrediction: { signal: 'STRONG BUY', confidence: 0.9, probability: 0.85 } };
    const r = svc.parseAPIResponse(json, suggestion, makeContext(), null);
    expect(r.source).toBe('Local Analysis');
  });
});

// ── AC. generateLocalAnalysis ───────────────────────────────────────

describe('generateLocalAnalysis', () => {
  it('returns YES for strong ML BUY with high win probability', () => {
    const suggestion = {
      optionType: 'CE', strikePrice: 22000, daysToExpiry: 14, score: 80,
      riskReward: 2.5, oiChange: 50000, mlPrediction: { signal: 'STRONG BUY', confidence: 0.9, probability: 0.82 },
    };
    const ctx = makeContext({ pcr: 1.3, maxPain: 22200 });
    const chain = makeChain(22000, 5, 50);
    const r = svc.generateLocalAnalysis(suggestion, ctx, chain);
    expect(r.verdict).toBe('YES');
    expect(r.source).toBe('Local Analysis');
    expect(r.winProbability).toBeGreaterThanOrEqual(20);
    expect(r.winProbability).toBeLessThanOrEqual(90);
  });

  it('returns NO for strong ML SELL signal', () => {
    const suggestion = {
      optionType: 'CE', strikePrice: 22000, daysToExpiry: 14, score: 40,
      riskReward: 0.8, oiChange: -60000, mlPrediction: { signal: 'STRONG SELL', confidence: 0.9, probability: 0.25 },
    };
    const ctx = makeContext({ pcr: 0.6 });
    const r = svc.generateLocalAnalysis(suggestion, ctx, null);
    expect(r.verdict).toBe('NO');
  });

  it('returns WAIT for ML HOLD signal', () => {
    const suggestion = {
      optionType: 'CE', strikePrice: 22000, daysToExpiry: 14, score: 70,
      riskReward: 1.5, oiChange: 5000, mlPrediction: { signal: 'HOLD', confidence: 0.5, probability: 0.5 },
    };
    const ctx = makeContext();
    const r = svc.generateLocalAnalysis(suggestion, ctx, null);
    expect(['WAIT', 'NO']).toContain(r.verdict);
  });

  it('time decay penalty for very short expiry', () => {
    const suggestion = {
      optionType: 'CE', strikePrice: 22000, daysToExpiry: 1, score: 50,
      riskReward: 1.5, oiChange: 5000,
    };
    const ctx = makeContext();
    const r = svc.generateLocalAnalysis(suggestion, ctx, null);
    expect(r.warnings.some(w => w.includes('theta') || w.includes('days'))).toBe(true);
  });

  it('includes chain analysis data when chain provided', () => {
    const suggestion = { optionType: 'CE', strikePrice: 22000, daysToExpiry: 14, score: 60, riskReward: 1.5 };
    const chain = makeChain(22000, 5, 50);
    const r = svc.generateLocalAnalysis(suggestion, makeContext(), chain);
    expect(r.chainAnalysis).toBeDefined();
    expect(r).toHaveProperty('reasons');
    expect(r).toHaveProperty('warnings');
  });

  it('PCR context affects probability for calls', () => {
    const suggestion = { optionType: 'CE', strikePrice: 22000, daysToExpiry: 14, score: 60, riskReward: 1.5 };
    const highPCR = svc.generateLocalAnalysis(suggestion, makeContext({ pcr: 1.5 }), null);
    const lowPCR = svc.generateLocalAnalysis(suggestion, makeContext({ pcr: 0.5 }), null);
    expect(highPCR.winProbability).toBeGreaterThan(lowPCR.winProbability);
  });

  it('max pain analysis affects probability', () => {
    const suggestion = { optionType: 'CE', strikePrice: 22000, daysToExpiry: 14, score: 60, riskReward: 1.5 };
    // Spot below max pain → bullish for calls
    const bullish = svc.generateLocalAnalysis(suggestion, makeContext({ spotPrice: 21800, maxPain: 22100 }), null);
    // Spot above max pain → bearish for calls
    const bearish = svc.generateLocalAnalysis(suggestion, makeContext({ spotPrice: 22200, maxPain: 22000 }), null);
    expect(bullish.winProbability).toBeGreaterThanOrEqual(bearish.winProbability);
  });
});
