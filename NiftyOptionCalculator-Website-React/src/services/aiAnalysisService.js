/**
 * AI Analysis Service for Option Trade Suggestions
 * Provides intelligent trade analysis based on option chain data, ML predictions, and market context
 */

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8000';

// Standard normal CDF approximation (Abramowitz & Stegun)
function normalCDF(x) {
  const a1 = 0.254829592, a2 = -0.284496736, a3 = 1.421413741;
  const a4 = -1.453152027, a5 = 1.061405429, p = 0.3275911;
  const sign = x < 0 ? -1 : 1;
  x = Math.abs(x) / Math.sqrt(2);
  const t = 1.0 / (1.0 + p * x);
  const y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);
  return 0.5 * (1.0 + sign * y);
}

class AIAnalysisService {
  constructor() {
    this.apiBaseURL = `${API_BASE_URL}/api/v1`;
    this.conservativeRules = {
      maxVix: 25,
      minDaysToExpiry: 0,
      minDisplayScore: 55,
      minOI: 1000,
      minVolumeMultiplier: 0.8,
      maxIVRatio: 1.5,
      minDelta: 0.15,
      maxDelta: 0.75,
      minOIChange: 1000,
      minRiskReward: 1.2,
      maxSpreadPct: 0.15,
      requireStrongBuy: false,
    };
  }

  /**
   * Analyze a trade suggestion with AI
   * @param {Object} suggestion - Trade suggestion with option data
   * @param {Object} marketContext - Market context (spot, pcr, maxPain, etc.)
   * @param {Array} optionChain - Complete option chain data
   * @returns {Object} AI analysis result
   */
  async analyzeTradeWithAI(suggestion, marketContext, optionChain = null) {
    try {
      // Build request body
      const requestBody = this.buildRequestBody(suggestion, marketContext, optionChain);

      // Try backend API first
      const response = await fetch(`${this.apiBaseURL}/options/analyze`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
        },
        body: JSON.stringify(requestBody),
      });

      if (response.ok) {
        const data = await response.json();
        return this.parseAPIResponse(data, suggestion, marketContext, optionChain);
      } else {
        // API error, fall back to local analysis
        return this.generateLocalAnalysis(suggestion, marketContext, optionChain);
      }
    } catch (error) {
      // Network error, fall back to local analysis
      return this.generateLocalAnalysis(suggestion, marketContext, optionChain);
    }
  }

  /**
   * Build request body for API
   */
  buildRequestBody(suggestion, context, optionChain) {
    const option = {
      strike_price: suggestion.strikePrice,
      option_type: suggestion.optionType,
      ltp: suggestion.ltp,
      iv: suggestion.iv || 0,
      delta: suggestion.delta || 0,
      theta: suggestion.theta || 0,
      open_interest: suggestion.oi || 0,
      oi_change: suggestion.oiChange || 0,
      volume: suggestion.volume || 0,
      days_to_expiry: suggestion.daysToExpiry || 0,
    };

    const market = {
      spot_price: context.spotPrice,
      pcr: context.pcr,
      max_pain: context.maxPain,
      atm_strike: context.atmStrike,
      index_name: context.indexName,
      support: context.support,
      resistance: context.resistance,
      india_vix: context.indiaVix,
      vix: context.vix || context.indiaVix,
    };

    // Ensure all required fields have valid numeric values
    const entry = parseFloat(suggestion.entryPrice) || parseFloat(suggestion.ltp) || 0;
    const target = parseFloat(suggestion.targetPrice) || entry * 1.6;
    const stopLoss = parseFloat(suggestion.stopLossPrice) || entry * 0.62;

    const suggestionData = {
      entry: entry,
      target: target,
      stop_loss: stopLoss,
      risk_reward: String((suggestion.riskReward || 1.6).toFixed(1)) + ':1', // Must be string like "1.6:1"
      score: Math.round(suggestion.score) || 50,
    };

    // Option chain data
    const chainData = [];
    if (optionChain && optionChain.length > 0) {
      for (const row of optionChain.slice(0, 40)) {
        const rowData = { strike: row.strikePrice };

        if (row.CE) {
          rowData.call_oi = row.CE.openInterest || 0;
          rowData.call_oi_change = row.CE.changeinOpenInterest || 0;
          rowData.call_ltp = row.CE.lastPrice || 0;
          rowData.call_iv = row.CE.impliedVolatility || 0;
          rowData.call_volume = row.CE.totalTradedVolume || 0;
          rowData.call_delta = row.CE.delta || 0;
          rowData.call_theta = row.CE.theta || 0;
          rowData.call_bid = row.CE.bidprice || 0;
          rowData.call_ask = row.CE.askPrice || 0;
        }

        if (row.PE) {
          rowData.put_oi = row.PE.openInterest || 0;
          rowData.put_oi_change = row.PE.changeinOpenInterest || 0;
          rowData.put_ltp = row.PE.lastPrice || 0;
          rowData.put_iv = row.PE.impliedVolatility || 0;
          rowData.put_volume = row.PE.totalTradedVolume || 0;
          rowData.put_delta = row.PE.delta || 0;
          rowData.put_theta = row.PE.theta || 0;
          rowData.put_bid = row.PE.bidprice || 0;
          rowData.put_ask = row.PE.askPrice || 0;
        }

        chainData.push(rowData);
      }
    }

    return {
      option,
      market_context: market,
      suggestion: suggestionData,
      option_chain: chainData,
    };
  }

  /**
   * Parse API response and validate against ML prediction
   */
  parseAPIResponse(json, suggestion, context, optionChain) {
    const aiPowered = json.ai_powered || false;
    const keyReason = json.key_reason || 'Analysis completed';

    // Check for error indicators
    const isError = keyReason.toLowerCase().includes('unavailable') ||
                    keyReason.toLowerCase().includes('error') ||
                    keyReason.toLowerCase().includes('quota') ||
                    keyReason.toLowerCase().includes('api');

    // Check for generic responses
    const isGeneric = keyReason.toLowerCase() === 'analysis completed' ||
                      keyReason.toLowerCase().includes('unable to') ||
                      keyReason.length < 15;

    if (!aiPowered || isError || isGeneric) {
      // API returned error/generic response, fall back to local analysis
      return this.generateLocalAnalysis(suggestion, context, optionChain);
    }

    const apiVerdict = (json.verdict || 'WAIT').toUpperCase();
    const apiWinProbability = json.win_probability || 50;

    // Validate API response against ML prediction
    if (suggestion.mlPrediction) {
      const mlProb = Math.round(suggestion.mlPrediction.probability * 100);
      const mlSignal = suggestion.mlPrediction.signal;
      const mlConfidence = suggestion.mlPrediction.confidence;

      let hasInconsistency = false;

      // ML says BUY/STRONG BUY with high confidence, but API says NO with low probability
      if ((mlSignal === 'STRONG BUY' || mlSignal === 'BUY') && mlConfidence >= 0.7) {
        if (apiVerdict === 'NO' || apiWinProbability < 30) {
          // API inconsistent with ML BUY signal
          hasInconsistency = true;
        }
      }

      // ML says SELL/STRONG SELL with high confidence, but API says YES
      if ((mlSignal === 'STRONG SELL' || mlSignal === 'SELL') && mlConfidence >= 0.7) {
        if (apiVerdict === 'YES' || apiWinProbability > 70) {
          // API inconsistent with ML SELL signal
          hasInconsistency = true;
        }
      }

      // Probability difference is too large
      if (Math.abs(mlProb - apiWinProbability) > 40) {
        // API probability differs too much from ML
        hasInconsistency = true;
      }

      if (hasInconsistency) {
        return this.generateLocalAnalysis(suggestion, context, optionChain);
      }
    }

    return {
      verdict: apiVerdict === 'YES' ? 'YES' : apiVerdict === 'NO' ? 'NO' : 'WAIT',
      winProbability: apiWinProbability,
      keyReason: keyReason,
      riskWarning: json.risk_warning || 'Always use stop-loss',
      betterAlternative: json.better_alternative,
      source: 'Gemini AI',
    };
  }

  /**
   * Generate local analysis based on option chain data and ML prediction
   */
  generateLocalAnalysis(suggestion, context, optionChain) {
    const isCall = suggestion.optionType === 'CE';
    const spot = context.spotPrice;
    const pcr = context.pcr || 1.0;
    const daysToExpiry = suggestion.daysToExpiry ?? 7;
    const oiChange = suggestion.oiChange || 0;
    const score = suggestion.score || 50;
    const riskReward = suggestion.riskReward || 1.5;
    const selectedStrike = suggestion.strikePrice;

    // Analyze option chain
    const chainAnalysis = this.analyzeOptionChain(optionChain, spot, selectedStrike, isCall);

    // Get ML prediction
    const mlPrediction = suggestion.mlPrediction;
    const mlSignal = mlPrediction?.signal;
    const mlConfidence = mlPrediction?.confidence || 0.5;
    const hasStrongMLSignal = mlConfidence >= 0.7 &&
      (mlSignal === 'STRONG BUY' || mlSignal === 'BUY' || mlSignal === 'STRONG SELL' || mlSignal === 'SELL');

    const reasons = [];
    const warnings = [];

    // === BASE PROBABILITY FROM ML ===
    let winProbability;
    if (mlPrediction) {
      winProbability = Math.round(mlPrediction.probability * 100);

      switch (mlSignal) {
        case 'STRONG BUY':
          reasons.push(`ML predicts STRONG BUY with ${Math.round(mlConfidence * 100)}% confidence`);
          break;
        case 'BUY':
          reasons.push('ML predicts BUY signal');
          break;
        case 'STRONG SELL':
          warnings.push('ML predicts STRONG SELL signal');
          break;
        case 'SELL':
          warnings.push('ML predicts SELL signal');
          break;
        case 'HOLD':
          warnings.push('ML shows neutral/HOLD - uncertain direction');
          break;
      }
    } else {
      winProbability = 50;
    }

    // === OPTION CHAIN ANALYSIS ADJUSTMENTS ===
    if (chainAnalysis.ivSkew !== null) {
      if (isCall) {
        if (chainAnalysis.ivSkew < -0.02) {
          winProbability -= 5;
          warnings.push('IV skew unfavorable for calls');
        } else if (chainAnalysis.ivSkew > 0.03) {
          winProbability += 5;
          reasons.push('IV skew supports call buying (put IV higher)');
        }
      } else {
        if (chainAnalysis.ivSkew > 0.03) {
          winProbability += 5;
          reasons.push('High put IV indicates fear - supports puts');
        } else if (chainAnalysis.ivSkew < -0.02) {
          winProbability -= 5;
          warnings.push('Low put IV - market not fearful');
        }
      }
    }

    // Support/Resistance from OI
    if (chainAnalysis.resistanceFromCallOI && isCall) {
      const distanceToResistance = chainAnalysis.resistanceFromCallOI - spot;
      if (distanceToResistance < 50) {
        winProbability -= 8;
        warnings.push(`Heavy call OI resistance at ${chainAnalysis.resistanceFromCallOI} near spot`);
      } else if (selectedStrike < chainAnalysis.resistanceFromCallOI) {
        winProbability += 3;
        reasons.push(`Target below call OI resistance (${chainAnalysis.resistanceFromCallOI})`);
      }
    }

    if (chainAnalysis.supportFromPutOI && !isCall) {
      const distanceToSupport = spot - chainAnalysis.supportFromPutOI;
      if (distanceToSupport < 50) {
        winProbability -= 8;
        warnings.push(`Heavy put OI support at ${chainAnalysis.supportFromPutOI} near spot`);
      } else if (selectedStrike > chainAnalysis.supportFromPutOI) {
        winProbability += 3;
        reasons.push(`Target above put OI support (${chainAnalysis.supportFromPutOI})`);
      }
    }

    // Smart Money Activity
    if (chainAnalysis.smartMoneyBullish && isCall) {
      winProbability += 7;
      reasons.push('Smart money accumulating calls');
    } else if (chainAnalysis.smartMoneyBearish && !isCall) {
      winProbability += 7;
      reasons.push('Smart money accumulating puts');
    } else if (chainAnalysis.smartMoneyBullish && !isCall) {
      winProbability -= 5;
      warnings.push('Smart money bullish - against puts');
    } else if (chainAnalysis.smartMoneyBearish && isCall) {
      winProbability -= 5;
      warnings.push('Smart money bearish - against calls');
    }

    // Volume Confirmation
    if (chainAnalysis.volumeConfirmation) {
      winProbability += 5;
      reasons.push('High volume confirms interest at this strike');
    }

    // === ADJUST BASED ON SCORE ===
    if (score >= 75) {
      winProbability += 8;
      reasons.push(`High score of ${score}/100`);
    } else if (score >= 65) {
      winProbability += 4;
      reasons.push(`Good score of ${score}/100`);
    } else if (score >= 50) {
      if (reasons.length === 0) {
        reasons.push(`Moderate score of ${score}/100`);
      }
    } else if (score < 40) {
      winProbability -= 8;
      warnings.push(`Low score of ${score}/100`);
    }

    // === ADJUST BASED ON RISK REWARD ===
    if (riskReward >= 2.5) {
      winProbability += 6;
      reasons.push(`Excellent R:R of ${riskReward.toFixed(1)}:1`);
    } else if (riskReward >= 2.0) {
      winProbability += 4;
    } else if (riskReward >= 1.5) {
      winProbability += 2;
    } else if (riskReward < 1.0) {
      winProbability -= 6;
      warnings.push(`Poor risk-reward ratio (${riskReward.toFixed(1)}:1)`);
    }

    // === PCR CONTEXT ===
    if (isCall) {
      if (pcr > 1.3) {
        winProbability += 5;
        reasons.push(`High PCR (${pcr.toFixed(2)}) supports calls`);
      } else if (pcr < 0.7) {
        winProbability -= 5;
        warnings.push('Low PCR - bearish sentiment');
      }
    } else {
      if (pcr < 0.7) {
        winProbability += 5;
        reasons.push('Low PCR supports puts');
      } else if (pcr > 1.3) {
        winProbability -= 5;
        warnings.push('High PCR - bullish sentiment');
      }
    }

    // === MAX PAIN ANALYSIS ===
    if (context.maxPain) {
      const distanceFromMaxPain = spot - context.maxPain;
      const percentFromMaxPain = Math.abs(distanceFromMaxPain) / spot * 100;

      if (isCall) {
        if (distanceFromMaxPain < -50 && percentFromMaxPain > 0.5) {
          winProbability += 5;
          reasons.push('Spot below max pain - bullish pull');
        } else if (distanceFromMaxPain > 100) {
          winProbability -= 3;
          warnings.push('Spot above max pain');
        }
      } else {
        if (distanceFromMaxPain > 50 && percentFromMaxPain > 0.5) {
          winProbability += 5;
          reasons.push('Spot above max pain - bearish pull');
        } else if (distanceFromMaxPain < -100) {
          winProbability -= 3;
          warnings.push('Spot below max pain');
        }
      }
    }

    // === TIME DECAY RISK ===
    if (daysToExpiry <= 2) {
      winProbability -= 12;
      warnings.push(`Critical: Only ${daysToExpiry} days left - extreme theta decay`);
    } else if (daysToExpiry <= 5) {
      winProbability -= 5;
      warnings.push(`Short expiry (${daysToExpiry} days) increases risk`);
    }

    // === OI ANALYSIS ===
    if (oiChange > 100000) {
      winProbability += 3;
      reasons.push(`Strong OI buildup (${this.formatNumber(oiChange)})`);
    } else if (oiChange < -50000) {
      winProbability -= 5;
      warnings.push('Long unwinding detected');
    }

    // === APPLY ML CONFIDENCE AS MODIFIER ===
    if (mlSignal === 'STRONG BUY' && mlConfidence >= 0.9) {
      winProbability = Math.max(winProbability, 75);
    } else if (mlSignal === 'STRONG SELL' && mlConfidence >= 0.9) {
      winProbability = Math.min(winProbability, 35);
    } else if (mlSignal === 'HOLD' || mlConfidence < 0.6) {
      winProbability = Math.min(winProbability, 60);
      winProbability = Math.max(winProbability, 40);
    }

    // Clamp final probability
    winProbability = Math.max(20, Math.min(90, winProbability));

    // === DETERMINE VERDICT ===
    let verdict, keyReason, riskWarning, alternative;

    // Strong BUY conditions
    if ((mlSignal === 'STRONG BUY' || mlSignal === 'BUY') && mlConfidence >= 0.7 && winProbability >= 65) {
      verdict = 'YES';
      keyReason = reasons[0] || 'ML and option chain analysis support this trade';
      riskWarning = warnings[0] || `Always use strict stop-loss at ₹${suggestion.stopLossPrice?.toFixed(2) || 'calculated SL'}`;
    }
    // Strong SELL conditions
    else if ((mlSignal === 'STRONG SELL' || mlSignal === 'SELL') && mlConfidence >= 0.7) {
      verdict = 'NO';
      keyReason = warnings[0] || 'ML signals against this trade';
      riskWarning = warnings[1] || 'Consider opposite direction';
      alternative = isCall ? 'Consider puts or wait for reversal' : 'Consider calls or wait for reversal';
    }
    // High probability with chain confirmation
    else if (winProbability >= 70 && (chainAnalysis.smartMoneyBullish && isCall || chainAnalysis.smartMoneyBearish && !isCall)) {
      verdict = 'YES';
      keyReason = reasons[0] || 'Strong chain analysis supports trade';
      riskWarning = warnings[0] || `Use stop-loss at ₹${suggestion.stopLossPrice?.toFixed(2) || 'calculated SL'}`;
    }
    // High probability without strong ML
    else if (winProbability >= 65 && score >= 70 && !hasStrongMLSignal) {
      verdict = 'WAIT';
      keyReason = reasons[0] || 'Good setup but ML is uncertain';
      riskWarning = 'ML shows HOLD - wait for clearer signal';
      alternative = 'Wait for ML confirmation or paper trade first';
    }
    // Low probability
    else if (winProbability <= 40) {
      verdict = 'NO';
      keyReason = warnings[0] || 'Risk factors outweigh potential';
      riskWarning = warnings[1] || 'Consider waiting for better setup';
      alternative = 'Look for higher probability setups';
    }
    // Everything else - WAIT
    else {
      verdict = 'WAIT';
      keyReason = reasons[0] || 'Mixed signals - proceed with caution';
      riskWarning = warnings[0] || 'Monitor price action before entering';
      alternative = 'Consider paper trading this setup first';
    }

    return {
      verdict,
      winProbability,
      keyReason,
      riskWarning,
      betterAlternative: alternative,
      source: 'Local Analysis',
      chainAnalysis: {
        ivSkew: chainAnalysis.ivSkew,
        support: chainAnalysis.supportFromPutOI,
        resistance: chainAnalysis.resistanceFromCallOI,
        smartMoneyBullish: chainAnalysis.smartMoneyBullish,
        smartMoneyBearish: chainAnalysis.smartMoneyBearish,
      },
      reasons,
      warnings,
    };
  }

  /**
   * Analyze option chain data for insights
   */
  analyzeOptionChain(optionChain, spot, selectedStrike, isCall) {
    const result = {
      ivSkew: null,
      resistanceFromCallOI: null,
      supportFromPutOI: null,
      maxCallOIStrike: null,
      maxPutOIStrike: null,
      smartMoneyBullish: false,
      smartMoneyBearish: false,
      volumeConfirmation: false,
      strikeNearMaxOI: false,
    };

    if (!optionChain || optionChain.length === 0) {
      return result;
    }

    // Find ATM strike
    const atmStrike = optionChain.reduce((prev, curr) =>
      Math.abs(curr.strikePrice - spot) < Math.abs(prev.strikePrice - spot) ? curr : prev
    ).strikePrice;

    // === IV SKEW ANALYSIS ===
    const atmRow = optionChain.find(row => row.strikePrice === atmStrike);
    if (atmRow && atmRow.CE?.impliedVolatility && atmRow.PE?.impliedVolatility) {
      const callIV = atmRow.CE.impliedVolatility;
      const putIV = atmRow.PE.impliedVolatility;
      if (callIV > 0 && putIV > 0) {
        result.ivSkew = (putIV - callIV) / 100; // Convert to decimal
      }
    }

    // === FIND MAX OI STRIKES ===
    let maxCallOI = 0, maxPutOI = 0;
    let maxCallOIStrike = 0, maxPutOIStrike = 0;

    for (const row of optionChain) {
      if (row.CE?.openInterest > maxCallOI) {
        maxCallOI = row.CE.openInterest;
        maxCallOIStrike = row.strikePrice;
      }
      if (row.PE?.openInterest > maxPutOI) {
        maxPutOI = row.PE.openInterest;
        maxPutOIStrike = row.strikePrice;
      }
    }

    if (maxCallOIStrike > spot) {
      result.resistanceFromCallOI = maxCallOIStrike;
      result.maxCallOIStrike = maxCallOIStrike;
    }

    if (maxPutOIStrike < spot) {
      result.supportFromPutOI = maxPutOIStrike;
      result.maxPutOIStrike = maxPutOIStrike;
    }

    // Check if selected strike is near max OI
    if (Math.abs(selectedStrike - maxCallOIStrike) <= 100 || Math.abs(selectedStrike - maxPutOIStrike) <= 100) {
      result.strikeNearMaxOI = true;
    }

    // === SMART MONEY ANALYSIS ===
    let totalCallOIChange = 0, totalPutOIChange = 0;
    let callOIBuildingCount = 0, putOIBuildingCount = 0;

    for (const row of optionChain) {
      const callChange = row.CE?.changeinOpenInterest || 0;
      const putChange = row.PE?.changeinOpenInterest || 0;

      totalCallOIChange += callChange;
      totalPutOIChange += putChange;

      if (callChange > 10000) callOIBuildingCount++;
      if (putChange > 10000) putOIBuildingCount++;
    }

    if (totalPutOIChange > 50000 && totalCallOIChange < totalPutOIChange / 2) {
      result.smartMoneyBullish = true;
    } else if (totalCallOIChange > 50000 && totalPutOIChange < totalCallOIChange / 2) {
      result.smartMoneyBearish = true;
    } else if (putOIBuildingCount > callOIBuildingCount + 2) {
      result.smartMoneyBullish = true;
    } else if (callOIBuildingCount > putOIBuildingCount + 2) {
      result.smartMoneyBearish = true;
    }

    // === VOLUME CONFIRMATION ===
    const selectedRow = optionChain.find(row => row.strikePrice === selectedStrike);
    if (selectedRow) {
      const selectedVolume = isCall ?
        (selectedRow.CE?.totalTradedVolume || 0) :
        (selectedRow.PE?.totalTradedVolume || 0);

      const avgVolume = optionChain.reduce((sum, row) => {
        return sum + (isCall ? (row.CE?.totalTradedVolume || 0) : (row.PE?.totalTradedVolume || 0));
      }, 0) / Math.max(optionChain.length, 1);

      if (selectedVolume > avgVolume * 2) {
        result.volumeConfirmation = true;
      }
    }

    return result;
  }

  /**
   * Generate trade suggestions from option chain
   * IMPORTANT: Uses ML-like scoring matching iOS/Android for consistent suggestions
   */
  generateTradeSuggestions(optionChain, context) {
    const suggestions = [];
    const candidates = [];
    if (!optionChain || optionChain.length === 0) return suggestions;

    const vix = context.indiaVix || context.vix;
    const daysToExpiry = context.daysToExpiry || 0;
    context.noTradeReason = null;

    // Set warnings but don't block suggestions — let scoring handle risk
    if (vix && vix >= this.conservativeRules.maxVix) {
      context.noTradeReason = `High volatility (VIX ${vix.toFixed(1)}) — trade with caution`;
    }
    if (daysToExpiry > 0 && daysToExpiry <= 1) {
      context.noTradeReason = `Expiry day trading — use strict stop-loss`;
    }

    const spot = context.spotPrice;

    // Calculate market bullish score (matching iOS/Android)
    const marketBullishScore = this.calculateMarketBullishScore(context);

    // Determine market bias from bullish score
    let marketBias = 'neutral';
    let biasStrength = 0;

    if (marketBullishScore >= 0.4) {
      marketBias = 'strong_bullish';
      biasStrength = 2;
    } else if (marketBullishScore >= 0.2) {
      marketBias = 'bullish';
      biasStrength = 1;
    } else if (marketBullishScore <= -0.4) {
      marketBias = 'strong_bearish';
      biasStrength = -2;
    } else if (marketBullishScore <= -0.2) {
      marketBias = 'bearish';
      biasStrength = -1;
    }

    // Find ATM strike
    const atmStrike = optionChain.reduce((prev, curr) =>
      Math.abs(curr.strikePrice - spot) < Math.abs(prev.strikePrice - spot) ? curr : prev
    ).strikePrice;

    // Calculate context values for scoring
    let maxOI = 0;
    let maxOIChange = 0;
    let totalVolume = 0;
    let optionCount = 0;

    optionChain.forEach(row => {
      if (row.CE) {
        maxOI = Math.max(maxOI, row.CE.openInterest || 0);
        maxOIChange = Math.max(maxOIChange, Math.abs(row.CE.changeinOpenInterest || 0));
        totalVolume += row.CE.totalTradedVolume || 0;
        optionCount++;
      }
      if (row.PE) {
        maxOI = Math.max(maxOI, row.PE.openInterest || 0);
        maxOIChange = Math.max(maxOIChange, Math.abs(row.PE.changeinOpenInterest || 0));
        totalVolume += row.PE.totalTradedVolume || 0;
        optionCount++;
      }
    });

    const avgVolume = optionCount > 0 ? totalVolume / optionCount : 50000;

    // Calculate ATM IV
    const atmRow = optionChain.find(r => Math.abs(r.strikePrice - atmStrike) < 50);
    const atmIV = atmRow?.CE?.impliedVolatility || atmRow?.PE?.impliedVolatility || 15;

    // Store context values for ML prediction
    context.marketBias = marketBias;
    context.biasStrength = biasStrength;
    context.marketBullishScore = marketBullishScore;
    context.maxOI = maxOI;
    context.maxOIChange = maxOIChange;
    context.avgVolume = avgVolume;
    context.atmIV = atmIV;

    // Detect market regime (matching iOS)
    const regime = this.detectMarketRegime(context);
    context.marketRegime = regime;

    // Generate suggestions for BOTH calls and puts
    for (const row of optionChain) {
      const distanceFromATM = Math.abs(row.strikePrice - atmStrike);

      // Skip strikes too far from ATM
      if (distanceFromATM > 500) continue;

      // Generate CALL suggestions
      if (row.CE) {
        const call = row.CE;
        if (call.lastPrice > 5 && call.lastPrice < 500 && call.openInterest > this.conservativeRules.minOI) {
          const suggestion = this.createSuggestion(row, 'CE', context, optionChain);
          if (suggestion) {
            if (suggestion.mlPrediction?.displayScore) {
              suggestion.score = suggestion.mlPrediction.displayScore;
            }

            // Market direction alignment bonus (matching iOS alignmentBonus)
            suggestion.score += this.calculateAlignmentBonus('CE', context, suggestion);

            // Market regime adjustments
            if (regime === 'rangeBound' || regime === 'flat') {
              const distPct = Math.abs(row.strikePrice - spot) / spot * 100;
              if (distPct > 5) suggestion.score = Math.max(30, suggestion.score - 5);
              if (distPct <= 1.5) suggestion.score = Math.min(95, suggestion.score + 3);
            } else if (regime === 'volatile') {
              if ((suggestion.volume || 0) < 1000) suggestion.score = Math.max(30, suggestion.score - 5);
            }

            suggestion.score = Math.max(30, Math.min(95, Math.round(suggestion.score)));

            // Update tier after score adjustments
            suggestion.tier = this.determineTier(suggestion.score);

            candidates.push(suggestion);
            if (this.passesConservativeFilters(suggestion, context)) {
              suggestions.push(suggestion);
            }
          }
        }
      }

      // Generate PUT suggestions
      if (row.PE) {
        const put = row.PE;
        if (put.lastPrice > 5 && put.lastPrice < 500 && put.openInterest > this.conservativeRules.minOI) {
          const suggestion = this.createSuggestion(row, 'PE', context, optionChain);
          if (suggestion) {
            if (suggestion.mlPrediction?.displayScore) {
              suggestion.score = suggestion.mlPrediction.displayScore;
            }

            // Market direction alignment bonus (matching iOS alignmentBonus)
            suggestion.score += this.calculateAlignmentBonus('PE', context, suggestion);

            // Market regime adjustments
            if (regime === 'rangeBound' || regime === 'flat') {
              const distPct = Math.abs(row.strikePrice - spot) / spot * 100;
              if (distPct > 5) suggestion.score = Math.max(30, suggestion.score - 5);
              if (distPct <= 1.5) suggestion.score = Math.min(95, suggestion.score + 3);
            } else if (regime === 'volatile') {
              if ((suggestion.volume || 0) < 1000) suggestion.score = Math.max(30, suggestion.score - 5);
            }

            suggestion.score = Math.max(30, Math.min(95, Math.round(suggestion.score)));

            suggestion.tier = this.determineTier(suggestion.score);

            candidates.push(suggestion);
            if (this.passesConservativeFilters(suggestion, context)) {
              suggestions.push(suggestion);
            }
          }
        }
      }
    }

    const callSuggestions = suggestions.filter(s => s.optionType === 'CE');
    const putSuggestions = suggestions.filter(s => s.optionType === 'PE');
    const needsFallback = suggestions.length < 4 || callSuggestions.length < 2 || putSuggestions.length < 2;

    if (needsFallback && candidates.length > 0) {
      const sortedCandidates = candidates.sort((a, b) => {
        const aScore = a.mlPrediction?.finalScore || 0;
        const bScore = b.mlPrediction?.finalScore || 0;
        return bScore - aScore;
      });

      const fallbackCalls = sortedCandidates.filter(s => s.optionType === 'CE' && !suggestions.includes(s));
      const fallbackPuts = sortedCandidates.filter(s => s.optionType === 'PE' && !suggestions.includes(s));

      while (callSuggestions.length < 2 && fallbackCalls.length > 0) {
        const fb = fallbackCalls.shift();
        suggestions.push({ ...fb, lowConfidence: true });
        callSuggestions.push(fb);
      }
      while (putSuggestions.length < 2 && fallbackPuts.length > 0) {
        const fb = fallbackPuts.shift();
        suggestions.push({ ...fb, lowConfidence: true });
        putSuggestions.push(fb);
      }

      if (suggestions.length === 0) {
        if (!context.noTradeReason) {
          context.noTradeReason = 'Only low-confidence setups found — showing top ideas';
        }
        return sortedCandidates.slice(0, 4).map(s => ({ ...s, lowConfidence: true }));
      }
    }

    // Sort by ML score, limit to 8 per side (matching iOS maxSuggestions=8)
    // BUG FIX: Previously no per-side limit, now 8 calls + 8 puts max
    const sortedSuggestions = suggestions.sort((a, b) => {
      const aScore = a.mlPrediction?.finalScore || 0;
      const bScore = b.mlPrediction?.finalScore || 0;
      return bScore - aScore;
    });

    const calls = sortedSuggestions.filter(s => s.optionType === 'CE').slice(0, 8);
    const puts = sortedSuggestions.filter(s => s.optionType === 'PE').slice(0, 8);
    return [...calls, ...puts];
  }

  /**
   * Create a trade suggestion from option data
   */
  createSuggestion(row, optionType, context, optionChain) {
    const option = optionType === 'CE' ? row.CE : row.PE;
    if (!option) return null;

    // Ensure ltp is a valid number - handle string/undefined/null cases
    const rawPrice = option.lastPrice ?? option.ltp ?? option.LTP ?? 0;
    const ltp = parseFloat(rawPrice) || 0;
    const strikePrice = row.strikePrice;

    // Skip if no valid price
    if (ltp <= 0 || isNaN(ltp)) return null;

    // Price-tiered target/SL (matching iOS)
    const entryPrice = parseFloat(ltp.toFixed(2));
    const iv = option.impliedVolatility || context.atmIV || 15;
    const ivFactor = Math.min(0.6, Math.max(0.15, iv / 100));
    const dte = context.daysToExpiry ?? 7;
    const timeFactor = Math.min(1.6, Math.max(0.6, Math.sqrt(dte / 7)));

    // Base target/SL percentages by entry price tier
    let baseTargetPct, baseSLPct;
    if (entryPrice >= 200) { baseTargetPct = 0.20; baseSLPct = 0.15; }
    else if (entryPrice >= 100) { baseTargetPct = 0.25; baseSLPct = 0.18; }
    else if (entryPrice >= 50) { baseTargetPct = 0.30; baseSLPct = 0.20; }
    else if (entryPrice >= 20) { baseTargetPct = 0.40; baseSLPct = 0.25; }
    else if (entryPrice >= 10) { baseTargetPct = 0.50; baseSLPct = 0.30; }
    else if (entryPrice >= 5) { baseTargetPct = 0.60; baseSLPct = 0.35; }
    else { baseTargetPct = 1.00; baseSLPct = 0.40; }

    const stopLossPct = Math.max(0.15, Math.min(0.50, baseSLPct * ivFactor * timeFactor * 3.0));
    const targetPct = Math.max(0.20, Math.min(1.50, baseTargetPct * ivFactor * timeFactor * 3.0));

    let stopLossPrice = parseFloat((entryPrice * (1 - stopLossPct)).toFixed(2));
    let targetPrice = parseFloat((entryPrice * (1 + targetPct)).toFixed(2));

    // OI-wall based smart targets
    const spot = context.spotPrice || context.atmStrike;
    const formulaTarget = targetPrice;
    const formulaSL = stopLossPrice;
    const oiWalls = this.calculateOIWallTargets(option, optionType, context, optionChain);
    if (oiWalls) {
      const wallDelta = Math.abs(option.delta || 0.5);
      const spotMoveTarget = oiWalls.targetSpot - spot;
      let oiTargetPrice = entryPrice + (spotMoveTarget * wallDelta);
      const spotMoveSL = oiWalls.supportSpot - spot;
      let oiStopPrice = entryPrice + (spotMoveSL * wallDelta);

      // Cap OI-wall target at 2x formula target to prevent absurd values for deep OTM
      oiTargetPrice = Math.min(oiTargetPrice, formulaTarget * 2.0);
      // Cap OI-wall SL — don't go below 2x the formula SL distance
      const minOISL = entryPrice - (entryPrice - formulaSL) * 2.0;
      oiStopPrice = Math.max(oiStopPrice, minOISL);

      // Blend: 60% OI-wall, 40% IV-formula
      if (oiTargetPrice > entryPrice * 1.05) {
        targetPrice = parseFloat((oiTargetPrice * 0.6 + targetPrice * 0.4).toFixed(2));
      }
      if (oiStopPrice < entryPrice * 0.95 && oiStopPrice > 0) {
        stopLossPrice = parseFloat((oiStopPrice * 0.6 + stopLossPrice * 0.4).toFixed(2));
      }

      // Enforce minimum R:R of 1.5
      const rr = (targetPrice - entryPrice) / Math.max(0.01, entryPrice - stopLossPrice);
      if (rr < 1.5) {
        targetPrice = parseFloat((entryPrice + (entryPrice - stopLossPrice) * 1.5).toFixed(2));
      }
    }
    // Final cap: target must not exceed 3x entry (300% gain is already extreme)
    targetPrice = Math.min(targetPrice, entryPrice * 3.0);

    const riskReward = (targetPrice - entryPrice) / Math.max(0.01, (entryPrice - stopLossPrice));

    // Add strikePrice to option for score calculation
    const optionWithStrike = { ...option, strikePrice };

    // Calculate score (11-factor weighted model)
    const scoreResult = this.calculateScore(optionWithStrike, context, optionType, optionChain);
    const score = scoreResult.score;

    // Generate ML prediction (simplified)
    const mlPrediction = this.generateMLPrediction(optionWithStrike, context, optionType);

    const bid = option.bidprice || option.bidPrice || option.bid || 0;
    const ask = option.askPrice || option.askprice || option.ask || 0;
    const mid = bid > 0 && ask > 0 ? (bid + ask) / 2 : null;
    const spreadPct = mid ? (ask - bid) / mid : null;

    const suggestionObj = {
      strikePrice,
      optionType,
      ltp,
      entryPrice,
      targetPrice,
      stopLossPrice,
      riskReward,
      targetPct,
      stopLossPct,
      score,
      bid,
      ask,
      spreadPct,
      iv: option.impliedVolatility,
      oi: option.openInterest,
      oiChange: option.changeinOpenInterest,
      volume: option.totalTradedVolume,
      delta: option.delta || null,
      gamma: option.gamma || null,
      theta: option.theta || null,
      vega: option.vega || null,
      daysToExpiry: context.daysToExpiry ?? 7,
      mlPrediction,
      lowConfidence: false,
    };

    // Enrich with new professional metrics (matching iOS)
    suggestionObj.tier = this.determineTier(mlPrediction?.displayScore || score);
    suggestionObj.confidence = this.determineConfidence(suggestionObj);
    suggestionObj.thetaZone = this.determineThetaZone(context.daysToExpiry ?? 7);
    suggestionObj.oiSignal = this.determineOISignal(suggestionObj, context);
    suggestionObj.riskWarnings = this.generateWeightedWarnings(suggestionObj, context);
    suggestionObj.scoreFactors = this.generateScoreFactors(suggestionObj, context);

    // Store detailed score factors and IV-RV ratio
    suggestionObj.scoreFactorsDetailed = scoreResult.factors;
    suggestionObj.ivRVRatio = scoreResult.ivRVRatio;

    // POP (probability of profit) using simplified Black-Scholes N(d2)
    const T = Math.max(1, context.daysToExpiry ?? 7) / 365;
    const sigma = (option.impliedVolatility || context.atmIV || 15) / 100;
    const r = 0.065;
    if (spot > 0 && strikePrice > 0 && sigma > 0) {
      const d2 = (Math.log(spot / strikePrice) + (r - 0.5 * sigma * sigma) * T) / (sigma * Math.sqrt(T));
      const rawPop = optionType === 'CE' ? normalCDF(d2) : normalCDF(-d2);
      suggestionObj.pop = Math.round(rawPop * 100);
    }

    // Inject actual POP into weighted score (replace placeholder of 50)
    if (suggestionObj.pop > 0) {
      const popWeight = 0.14;
      const currentPopContribution = 50 * popWeight;
      const newPopContribution = suggestionObj.pop * popWeight;
      suggestionObj.score = Math.round(Math.max(30, Math.min(95,
        suggestionObj.score - currentPopContribution + newPopContribution
      )));
    }

    // Term structure: compare option IV to VIX
    const vix = context.indiaVix || context.vix || 15;
    const optIV = option.impliedVolatility || context.atmIV || 15;
    const ivDiff = optIV - vix;
    suggestionObj.termStructure = ivDiff < -3 ? 'inverted' : ivDiff > 3 ? 'contango' : 'flat';

    return suggestionObj;
  }

  passesConservativeFilters(suggestion, context) {
    const rules = this.conservativeRules;
    const ml = suggestion.mlPrediction;
    const displayScore = ml?.displayScore || suggestion.score || 0;
    const signal = ml?.signal || 'HOLD';

    const regime = context.marketRegime || 'rangeBound';
    let effectiveMinDisplayScore = rules.minDisplayScore;
    if (regime === 'rangeBound' || regime === 'flat') {
        effectiveMinDisplayScore = 45;
    } else if (regime === 'volatile') {
        effectiveMinDisplayScore = 50;
    } else if (regime === 'trending') {
        effectiveMinDisplayScore = 55;
    }
    if (displayScore < effectiveMinDisplayScore) return false;
    if (rules.requireStrongBuy && signal !== 'STRONG BUY') return false;
    if (!rules.requireStrongBuy && (signal === 'SELL' || signal === 'STRONG SELL')) return false;

    const avgVolume = context.avgVolume || 1;
    let effectiveVolumeMultiplier = rules.minVolumeMultiplier;
    let effectiveMinOIChange = rules.minOIChange;
    if (regime === 'rangeBound' || regime === 'flat') {
        effectiveVolumeMultiplier = 0.3;
        effectiveMinOIChange = 100;
    } else if (regime === 'trending') {
        effectiveVolumeMultiplier = 0.5;
        effectiveMinOIChange = 500;
    }
    if ((suggestion.volume || 0) < avgVolume * effectiveVolumeMultiplier) return false;

    const iv = suggestion.iv || context.atmIV || 15;
    const atmIV = context.atmIV || 15;
    const ivRatio = atmIV > 0 ? iv / atmIV : 1.0;
    if (ivRatio > rules.maxIVRatio) return false;

    // Use backend-computed delta when available
    const delta = Math.abs(suggestion.delta || 0);
    if (delta > 0.01 && (delta < rules.minDelta || delta > rules.maxDelta)) return false;

    if ((suggestion.oiChange || 0) < effectiveMinOIChange) return false;
    if (suggestion.spreadPct !== null && suggestion.spreadPct > rules.maxSpreadPct) return false;

    // Delta hard filter (match iOS: 0.20-0.80)
    const absDelta = Math.abs(suggestion.delta || 0);
    if (absDelta > 0 && (absDelta < 0.20 || absDelta > 0.80)) return false;

    // Multiple critical warnings rejection (match iOS)
    const criticalWarnings = (suggestion.riskWarnings || []).filter(w => w.severity === 'critical');
    if (criticalWarnings.length >= 3) return false;

    // Low confidence + low score rejection
    if (suggestion.confidence?.level === 'Low' && suggestion.score < 55) return false;

    // DTE-aware R:R minimums (match iOS)
    const dte = suggestion.daysToExpiry ?? 7;
    let minRR;
    if (dte === 0) minRR = 0.8;
    else if (dte === 1) minRR = 1.0;
    else minRR = 1.5;
    if ((suggestion.riskReward || 0) < minRR) return false;

    return true;
  }

  /**
   * Calculate suggestion score — 11-factor weighted model matching iOS
   * @param {Object} option - option data with strikePrice
   * @param {Object} context - market context
   * @param {string} optionType - 'CE' or 'PE'
   * @param {Array} [optionChain] - full option chain for percentile/skew calculations
   * @returns {{score: number, factors: Object}}
   */
  calculateScore(option, context, optionType, optionChain) {
    const weights = {
      ivRank: 0.18,
      oiSignal: 0.18,
      pop: 0.14,
      greeks: 0.14,
      volume: 0.09,
      pcr: 0.09,
      ivPercentile: 0.06,
      maxPain: 0.04,
      liquidity: 0.04,
      skew: 0.02,
      termStructure: 0.02
    };

    const scores = {};

    // 1. IV Rank (18%) — lower IV = cheaper options = higher score
    const vix = context.indiaVix || context.vix || 15;
    const atmIV = context.atmIV || 15;
    const iv = option.impliedVolatility || atmIV;
    const vixRank = Math.min(100, Math.max(0, ((vix - 10) / 25) * 100));
    const ivVsATMRank = Math.min(100, Math.max(0, ((iv / atmIV - 0.8) / 0.4) * 100));
    const ivRank = vixRank * 0.6 + ivVsATMRank * 0.4;
    scores.ivRank = 100 - ivRank;

    // 2. OI Signal (18%) — aligned buildup = high score
    const oiSignal = this.determineOISignal(
      { optionType, oiChange: option.changeinOpenInterest || 0 },
      context
    );
    const callMap = { 'Long Buildup': 85, 'Short Covering': 70, 'Neutral': 50, 'Long Unwinding': 30, 'Short Buildup': 20 };
    const putMap = { 'Short Buildup': 85, 'Long Unwinding': 70, 'Neutral': 50, 'Short Covering': 30, 'Long Buildup': 20 };
    scores.oiSignal = (optionType === 'CE' ? callMap : putMap)[oiSignal.signal] || 50;

    // 3. POP (14%) — placeholder, overridden after createSuggestion computes it
    scores.pop = 50;

    // 4. Greeks (14%) — optimal delta range
    const delta = Math.abs(option.delta || 0);
    if (delta >= 0.4 && delta <= 0.6) scores.greeks = 80;
    else if (delta >= 0.3 && delta <= 0.7) scores.greeks = 65;
    else if (delta > 0.8) scores.greeks = 45;
    else if (delta < 0.2) scores.greeks = 35;
    else scores.greeks = 55;
    if ((option.gamma || 0) > 0.01) scores.greeks = Math.min(100, scores.greeks + 10);
    const ltp = option.lastPrice || option.ltp || 1;
    if (ltp > 0 && Math.abs(option.theta || 0) / ltp > 0.03) scores.greeks = Math.max(0, scores.greeks - 10);

    // 5. Volume (9%)
    const vol = option.totalTradedVolume || option.volume || 0;
    const avgVol = context.avgVolume || 1;
    const volOIRatio = (option.openInterest || 1) > 0 ? vol / (option.openInterest || 1) : 0;
    if (volOIRatio > 1.0) scores.volume = Math.min(100, 70 + volOIRatio * 10);
    else if (vol > avgVol * 2) scores.volume = Math.min(90, 65 + (vol / avgVol - 2) * 5);
    else if (vol > avgVol) scores.volume = 55 + (vol / avgVol - 1) * 10;
    else if (vol < 100) scores.volume = 30;
    else scores.volume = 45;

    // 6. PCR Context (9%) — contrarian
    const pcr = context.pcr || 1.0;
    if (optionType === 'CE') {
      scores.pcr = pcr > 1.2 ? 70 : pcr > 1.0 ? 60 : pcr < 0.8 ? 45 : 50;
    } else {
      scores.pcr = pcr < 0.8 ? 65 : pcr < 1.0 ? 55 : pcr > 1.2 ? 40 : 50;
    }

    // 7. IV Percentile (6%) — where this option's IV sits in the chain
    const allIVs = [];
    if (optionChain) {
      for (const row of optionChain) {
        const opt = row[optionType];
        if (opt && opt.impliedVolatility > 0) allIVs.push(opt.impliedVolatility);
      }
    }
    if (allIVs.length > 0) {
      const belowCount = allIVs.filter(v => v < iv).length;
      const percentile = (belowCount / allIVs.length) * 100;
      scores.ivPercentile = Math.min(100, Math.max(0, 100 - percentile));
    } else {
      scores.ivPercentile = 50;
    }

    // 8. Max Pain (4%)
    const spot = context.spotPrice || context.atmStrike || 0;
    const maxPain = context.maxPain || context.atmStrike || 0;
    const strikePrice = option.strikePrice || context.atmStrike || 0;
    if (maxPain > 0 && spot > 0) {
      const distPct = Math.abs(strikePrice - maxPain) / spot * 100;
      scores.maxPain = distPct < 1 ? 70 : distPct < 2 ? 60 : Math.max(20, 60 - distPct * 10);
      if (optionType === 'CE' && spot < maxPain) scores.maxPain = Math.min(100, scores.maxPain + 15);
      if (optionType === 'PE' && spot > maxPain) scores.maxPain = Math.min(100, scores.maxPain + 15);
    } else {
      scores.maxPain = 50;
    }

    // 9. Liquidity (4%) — bid-ask spread
    const bid = option.bidPrice || option.bidprice || 0;
    const ask = option.askPrice || option.askprice || 0;
    const spreadPct = ask > 0 ? ((ask - bid) / ask) * 100 : 5;
    if (spreadPct < 1) scores.liquidity = 90;
    else if (spreadPct < 3) scores.liquidity = 70;
    else if (spreadPct < 5) scores.liquidity = 50;
    else if (spreadPct < 10) scores.liquidity = 35;
    else scores.liquidity = 20;

    // 10. Skew (2%) — put-call IV difference
    scores.skew = 50;
    if (optionChain) {
      const row = optionChain.find(r => r.strikePrice === strikePrice);
      if (row && row.CE && row.PE) {
        const callIV = row.CE.impliedVolatility || 0;
        const putIV = row.PE.impliedVolatility || 0;
        const skewDiff = putIV - callIV;
        if (optionType === 'CE') {
          scores.skew = skewDiff <= 2 ? 65 : skewDiff >= 8 ? 35 : 50;
        } else {
          scores.skew = skewDiff >= 6 ? 70 : skewDiff <= 0 ? 35 : 50;
        }
      }
    }

    // 11. Term Structure (2%)
    const ivDiff = iv - vix;
    scores.termStructure = ivDiff > 3 ? 70 : ivDiff < -3 ? 35 : 55;

    // IV vs Realized Volatility proxy adjustment
    const intradayMove = Math.abs(context.intradayChange || context.pChange || 0);
    const realizedVolProxy = intradayMove * Math.sqrt(252);
    const ivRVRatio = realizedVolProxy > 1 ? iv / realizedVolProxy : 1.0;
    let ivRVAdjustment = 0;
    if (ivRVRatio > 2.0) ivRVAdjustment = -8;
    else if (ivRVRatio > 1.5) ivRVAdjustment = -4;
    else if (ivRVRatio < 0.7) ivRVAdjustment = +8;
    else if (ivRVRatio < 1.0) ivRVAdjustment = +4;

    // Weighted final score
    let finalScore = 0;
    for (const [factor, weight] of Object.entries(weights)) {
      finalScore += (scores[factor] || 50) * weight;
    }
    finalScore += ivRVAdjustment;

    return {
      score: Math.round(Math.max(30, Math.min(95, finalScore))),
      factors: scores,
      ivRVRatio: realizedVolProxy > 1 ? parseFloat(ivRVRatio.toFixed(2)) : null
    };
  }

  /**
   * Calculate OI-wall based smart targets
   * Finds next significant OI concentration as natural target/support
   */
  calculateOIWallTargets(option, optionType, context, optionChain) {
    const spot = context.spotPrice || context.atmStrike;
    const strikePrice = option.strikePrice;
    const entryPrice = option.lastPrice || option.ltp;
    if (!optionChain || !entryPrice || entryPrice <= 0) return null;

    const oiData = [];
    for (const row of optionChain) {
      const ce = row.CE, pe = row.PE;
      if (ce) oiData.push({ strike: row.strikePrice, type: 'CE', oi: ce.openInterest || 0 });
      if (pe) oiData.push({ strike: row.strikePrice, type: 'PE', oi: pe.openInterest || 0 });
    }

    const avgOI = oiData.reduce((s, d) => s + d.oi, 0) / (oiData.length || 1);
    const significantOI = oiData.filter(d => d.oi > avgOI * 2);

    let targetSpot, supportSpot;

    if (optionType === 'CE') {
      const callWalls = significantOI
        .filter(d => d.type === 'CE' && d.strike > spot)
        .sort((a, b) => a.strike - b.strike);
      targetSpot = callWalls[0]?.strike || (spot * 1.015);

      const putWalls = significantOI
        .filter(d => d.type === 'PE' && d.strike < spot)
        .sort((a, b) => b.strike - a.strike);
      supportSpot = putWalls[0]?.strike || (spot * 0.99);
    } else {
      const putWalls = significantOI
        .filter(d => d.type === 'PE' && d.strike < spot)
        .sort((a, b) => b.strike - a.strike);
      targetSpot = putWalls[0]?.strike || (spot * 0.985);

      const callWalls = significantOI
        .filter(d => d.type === 'CE' && d.strike > spot)
        .sort((a, b) => a.strike - b.strike);
      supportSpot = callWalls[0]?.strike || (spot * 1.01);
    }

    return { targetSpot, supportSpot };
  }

  /**
   * Calculate alignment bonus based on market direction + momentum + OI signal
   * Matching iOS alignmentBonus logic
   */
  calculateAlignmentBonus(optionType, context, suggestion) {
    let bonus = 0;
    const marketBullishScore = context.marketBullishScore || 0;
    const isCall = optionType === 'CE';
    const intradayChangePct = context.intradayChange || context.pChange || context.change || 0;
    const absChange = Math.abs(intradayChangePct);

    // 1. Market direction alignment bonus (+5 if option type matches market direction)
    const isAligned = isCall ? marketBullishScore > 0.1 : marketBullishScore < -0.1;
    if (isAligned) {
      bonus += 5.0;
    }

    // 2. Momentum bonus (strong intraday moves boost aligned options)
    let moveMultiplier = 1.0;
    if (absChange > 0.5) {
      moveMultiplier = 1.5;
    } else if (absChange > 0.3) {
      moveMultiplier = 1.2;
    }

    const momentumAligned = isCall ? intradayChangePct > 0.3 : intradayChangePct < -0.3;
    if (momentumAligned) {
      bonus += 5.0 * moveMultiplier;
    }

    // 3. OI signal alignment bonuses (matching iOS)
    const oiSignal = suggestion.oiSignal?.signal;
    if (isCall) {
      if (oiSignal === 'Long Buildup') bonus += 15;
      else if (oiSignal === 'Short Covering') bonus += 8;
      else if (oiSignal === 'Short Buildup') bonus -= 10;
      else if (oiSignal === 'Long Unwinding') bonus -= 5;
    } else {
      if (oiSignal === 'Short Buildup') bonus += 15;
      else if (oiSignal === 'Long Unwinding') bonus += 8;
      else if (oiSignal === 'Long Buildup') bonus -= 10;
      else if (oiSignal === 'Short Covering') bonus -= 5;
    }

    return bonus;
  }

  /**
   * ML Signal thresholds (matching iOS/Android MLOptionPredictor)
   */
  ML_THRESHOLDS = {
    STRONG_BUY: 0.35,
    BUY: 0.08,
    SELL: -0.08,
    STRONG_SELL: -0.35
  };

  /**
   * Calculate market bullish score (matching iOS/Android MLOptionPredictor logic)
   * Positive = bullish market, Negative = bearish market
   * Uses CONTRARIAN interpretation of PCR
   *
   * UPDATED: Added intraday momentum as PRIMARY factor (matching iOS)
   */
  calculateMarketBullishScore(context) {
    let marketBullishScore = 0.0;
    const pcr = context.pcr || 1.0;
    const spot = context.spotPrice;
    const maxPain = context.maxPain || spot;
    const vix = context.indiaVix || context.vix || 15;

    // Get intraday change from context (percentage change from previous close)
    const intradayChangePct = context.intradayChange || context.pChange || context.change || 0;

    // FACTOR 1 (PRIMARY): INTRADAY MOMENTUM - matching iOS logic
    // This is the key factor that determines market direction
    if (intradayChangePct !== 0) {
      const absChange = Math.abs(intradayChangePct);

      // Calculate move multiplier based on strength (iOS logic)
      let moveMultiplier = 1.0;
      if (absChange > 0.5) {
        moveMultiplier = 1.5;  // Strong move
      } else if (absChange > 0.3) {
        moveMultiplier = 1.2;  // Moderate move
      }

      // Apply momentum score
      if (intradayChangePct >= 0.3) {
        // Bullish momentum
        marketBullishScore += 0.25 * moveMultiplier;
        // Bullish momentum applied
      } else if (intradayChangePct <= -0.3) {
        // Bearish momentum
        marketBullishScore -= 0.25 * moveMultiplier;
        // Bearish momentum applied
      } else if (intradayChangePct > 0) {
        // Mild bullish
        marketBullishScore += 0.1;
      } else if (intradayChangePct < 0) {
        // Mild bearish
        marketBullishScore -= 0.1;
      }
    }

    // Factor 2: PCR (Put-Call Ratio) - CONTRARIAN signal
    // PCR > 1.0 = more puts = bearish sentiment = CONTRARIAN BULLISH
    // PCR < 0.8 = more calls = bullish sentiment = CONTRARIAN BEARISH
    if (pcr > 1.2) {
      marketBullishScore += 0.3;  // Strong contrarian bullish
    } else if (pcr > 1.0) {
      marketBullishScore += 0.15;  // Mild contrarian bullish
    } else if (pcr < 0.7) {
      marketBullishScore -= 0.2;  // Contrarian bearish
    } else if (pcr < 0.9) {
      marketBullishScore -= 0.1;  // Mild contrarian bearish
    }

    // Factor 3: Spot vs Max Pain
    // Spot below max pain = gravitational pull UP = bullish
    // Spot above max pain = gravitational pull DOWN = bearish
    const spotVsMaxPain = (spot - maxPain) / spot;
    if (spotVsMaxPain < -0.01) {
      marketBullishScore += 0.25;  // Spot below max pain
    } else if (spotVsMaxPain < 0) {
      marketBullishScore += 0.1;
    } else if (spotVsMaxPain > 0.01) {
      marketBullishScore -= 0.25;  // Spot above max pain
    } else if (spotVsMaxPain > 0) {
      marketBullishScore -= 0.1;
    }

    // Factor 4: VIX consideration (contrarian at extremes)
    if (vix > 30) {
      marketBullishScore += 0.05;  // Extreme fear = contrarian bullish
    } else if (vix > 22) {
      marketBullishScore -= 0.05;
    } else if (vix > 18) {
      marketBullishScore -= 0.03;
    } else if (vix < 12) {
      marketBullishScore += 0.1;
    }

    // Market bullish score calculated

    return marketBullishScore;
  }

  /**
   * Generate ML prediction (matching iOS/Android MLOptionPredictor logic)
   * Key insight: CE profits when market is BULLISH, PE profits when market is BEARISH
   */
  generateMLPrediction(option, context, optionType) {
    const oiChange = option.changeinOpenInterest || 0;
    const volume = option.totalTradedVolume || 0;
    const oi = option.openInterest || 0;
    const spot = context.spotPrice;
    const strikePrice = option.strikePrice || context.atmStrike;
    const iv = option.impliedVolatility || 15;
    const atmIV = context.atmIV || 15;
    const maxOIChange = context.maxOIChange || 100000;
    const avgVolume = context.avgVolume || 50000;

    // Calculate market bullish score first (matching iOS)
    const marketBullishScore = this.calculateMarketBullishScore(context);
    const isCall = optionType === 'CE';

    // CE profits when market is BULLISH (positive marketBullishScore)
    // PE profits when market is BEARISH (negative marketBullishScore)
    let optionScore = isCall ? marketBullishScore : -marketBullishScore;

    let finalScore = optionScore;

    // Factor: Volume ratio
    const volumeRatio = avgVolume > 0 ? volume / avgVolume : 1.0;
    if (volumeRatio > 1.5) {
      finalScore += 0.1;
    } else if (volumeRatio > 0.8) {
      finalScore += 0.05;
    }

    // Factor: IV relative to ATM
    const ivRatio = atmIV > 0 ? iv / atmIV : 1.0;
    if (ivRatio < 0.9) {
      finalScore += 0.1;  // Potentially undervalued
    } else if (ivRatio > 1.2) {
      finalScore -= 0.1;  // Potentially overvalued
    }

    // Factor: Delta — use computed value if available, otherwise estimate
    const computedDelta = option.delta ? Math.abs(option.delta) : null;
    let effectiveDelta;

    if (computedDelta && computedDelta > 0) {
      effectiveDelta = computedDelta;
    } else {
      // Fallback: estimate delta from moneyness
      const moneyness = (strikePrice - spot) / spot;
      const absMoneyness = Math.abs(moneyness);
      const isOTM = isCall ? moneyness > 0 : moneyness < 0;
      if (absMoneyness < 0.02) {
        effectiveDelta = 0.50;
      } else if (isOTM) {
        effectiveDelta = Math.max(0.05, 0.5 - absMoneyness * 3);
      } else {
        effectiveDelta = Math.min(0.95, 0.5 + absMoneyness * 3);
      }
    }

    // Delta consideration
    if (effectiveDelta >= 0.30 && effectiveDelta <= 0.70) {
      finalScore += 0.1;  // Good delta range
    } else if (effectiveDelta < 0.15) {
      finalScore -= 0.15;  // Too far OTM
    } else if (effectiveDelta > 0.85) {
      finalScore -= 0.05;  // Deep ITM
    }

    // Factor: OI change (normalized)
    const oiChangeNormalized = maxOIChange > 0 ? oiChange / maxOIChange : 0;
    if (oiChangeNormalized > 0.3) {
      finalScore += isCall ? 0.15 : -0.15;
    } else if (oiChangeNormalized < -0.3) {
      finalScore += isCall ? -0.1 : 0.1;
    }

    // Determine ML signal based on finalScore (matching iOS thresholds)
    let signal, mlConfidence;
    const { STRONG_BUY, BUY, SELL, STRONG_SELL } = this.ML_THRESHOLDS;

    if (finalScore > STRONG_BUY) {
      signal = 'STRONG BUY';
      mlConfidence = Math.min(0.95, 0.7 + finalScore);
    } else if (finalScore > BUY) {
      signal = 'BUY';
      mlConfidence = Math.min(0.85, 0.6 + finalScore);
    } else if (finalScore < STRONG_SELL) {
      signal = 'STRONG SELL';
      mlConfidence = Math.min(0.95, 0.7 + Math.abs(finalScore));
    } else if (finalScore < SELL) {
      signal = 'SELL';
      mlConfidence = Math.min(0.85, 0.6 + Math.abs(finalScore));
    } else {
      signal = 'HOLD';
      mlConfidence = 0.5 + Math.abs(finalScore) * 0.3;
    }

    // Clamp confidence
    mlConfidence = Math.max(0.3, Math.min(0.95, mlConfidence));

    // Use ML confidence as the displayed score (matching iOS behavior)
    const displayScore = Math.round(mlConfidence * 100);

    return {
      signal,
      probability: mlConfidence,  // For compatibility
      confidence: mlConfidence,
      displayScore,  // ML confidence as percentage (matching iOS)
      finalScore,    // Raw ML score for debugging
      expected: ((finalScore) * 100).toFixed(1) + '%',
    };
  }

  // =====================================================
  // NEW: Market Regime Detection (matching iOS)
  // =====================================================

  /**
   * Detect market regime from VIX + intraday move
   * @returns {'trending'|'rangeBound'|'volatile'}
   */
  detectMarketRegime(context) {
    const vix = context.indiaVix || context.vix || 14;
    const intradayChange = Math.abs(context.intradayChange || context.pChange || 0);

    if (vix > 20) return 'volatile';
    if (vix < 14 && intradayChange < 0.15) return 'flat';
    if (intradayChange >= 0.5) return 'trending';
    if (intradayChange >= 0.3 && vix >= 14) return 'trending';
    if (vix < 14 && intradayChange < 0.3) return 'rangeBound';
    if (intradayChange < 0.3) return 'rangeBound';
    return 'trending';
  }

  /**
   * Get market regime display info
   */
  getRegimeInfo(regime) {
    const info = {
      trending: { label: 'Trending', icon: '📈', color: '#4ade80', description: 'Momentum plays favored, OTM options viable' },
      rangeBound: { label: 'Range-Bound', icon: '↔️', color: '#60a5fa', description: 'ATM options preferred, avoid deep OTM' },
      volatile: { label: 'Volatile', icon: '⚡', color: '#fbbf24', description: 'High-liquidity options only, wider stops' },
      flat: { label: 'Flat', icon: '➖', color: '#94a3b8', description: 'Low volatility, ATM straddles/strangles preferred' },
    };
    return info[regime] || info.rangeBound;
  }

  // =====================================================
  // NEW: Suggestion Tier (matching iOS SuggestionTier)
  // =====================================================

  /**
   * Determine suggestion tier based on score
   * @returns {'topPick'|'worthWatching'}
   */
  determineTier(score) {
    return score >= 62 ? 'topPick' : 'worthWatching';
  }

  // =====================================================
  // NEW: Confidence Level (matching iOS ConfidenceLevel)
  // =====================================================

  /**
   * Determine confidence level from ML prediction and score
   */
  determineConfidence(suggestion) {
    const ml = suggestion.mlPrediction;
    const score = suggestion.score || 0;
    const rr = suggestion.riskReward || 0;

    let confidencePoints = 0;
    if (score >= 75) confidencePoints += 2;
    else if (score >= 60) confidencePoints += 1;

    if (ml?.confidence >= 0.8) confidencePoints += 2;
    else if (ml?.confidence >= 0.6) confidencePoints += 1;

    if (rr >= 2.0) confidencePoints += 1;
    if ((suggestion.oiChange || 0) > 50000) confidencePoints += 1;

    if (confidencePoints >= 4) return { level: 'High', color: '#4ade80', icon: '✓' };
    if (confidencePoints >= 2) return { level: 'Medium', color: '#fbbf24', icon: '!' };
    return { level: 'Low', color: '#f87171', icon: '?' };
  }

  // =====================================================
  // NEW: Theta Decay Zone (matching iOS ThetaDecayZone)
  // =====================================================

  /**
   * Determine theta decay zone from days to expiry
   */
  determineThetaZone(daysToExpiry) {
    if (daysToExpiry >= 45) return { zone: 'Safe', color: '#4ade80', label: '45+ days' };
    if (daysToExpiry >= 30) return { zone: 'Moderate', color: '#86efac', label: '30-45 days' };
    if (daysToExpiry >= 14) return { zone: 'Caution', color: '#fbbf24', label: '14-30 days' };
    if (daysToExpiry >= 7) return { zone: 'Danger', color: '#fb923c', label: '7-14 days' };
    return { zone: 'Extreme', color: '#f87171', label: '<7 days' };
  }

  // =====================================================
  // NEW: OI Signal Detection (matching iOS 4-quadrant model)
  // =====================================================

  /**
   * Determine OI signal using 4-quadrant Price + OI model
   */
  determineOISignal(suggestion, context) {
    const oiChange = suggestion.oiChange || 0;
    const intradayChange = context.intradayChange || context.pChange || 0;
    const isCall = suggestion.optionType === 'CE';
    const maxOIChange = context.maxOIChange || 100000;

    const significantOI = maxOIChange > 0
      ? Math.abs(oiChange) / maxOIChange > 0.1
      : Math.abs(oiChange) > 1000;
    const significantPrice = Math.abs(intradayChange) >= 0.3;

    if (!significantOI && !significantPrice) {
      return { signal: 'Neutral', color: '#94a3b8', icon: '−' };
    }

    const priceUp = intradayChange > 0;
    const oiUp = oiChange > 0;

    if (isCall) {
      if (priceUp && oiUp) return { signal: 'Long Buildup', color: '#4ade80', icon: '↗' };
      if (!priceUp && oiUp) return { signal: 'Short Buildup', color: '#f87171', icon: '↘' };
      if (priceUp && !oiUp) return { signal: 'Short Covering', color: '#86efac', icon: '↗' };
      if (!priceUp && !oiUp) return { signal: 'Long Unwinding', color: '#fb923c', icon: '↘' };
    } else {
      if (!priceUp && oiUp) return { signal: 'Long Buildup', color: '#f87171', icon: '↘' };
      if (priceUp && oiUp) return { signal: 'Short Buildup', color: '#4ade80', icon: '↗' };
      if (!priceUp && !oiUp) return { signal: 'Short Covering', color: '#fb923c', icon: '↘' };
      if (priceUp && !oiUp) return { signal: 'Long Unwinding', color: '#86efac', icon: '↗' };
    }

    return { signal: 'Neutral', color: '#94a3b8', icon: '−' };
  }

  // =====================================================
  // NEW: Weighted Risk Warnings (matching iOS RiskSeverity)
  // =====================================================

  /**
   * Generate severity-weighted risk warnings
   * @returns {Array<{message: string, severity: string, penalty: number}>}
   */
  generateWeightedWarnings(suggestion, context) {
    const warnings = [];
    const isCall = suggestion.optionType === 'CE';
    const dte = suggestion.daysToExpiry ?? 7;
    const intradayChange = context.intradayChange || context.pChange || 0;

    // Theta decay (severity matches iOS)
    if (dte <= 0) {
      warnings.push({ message: 'Expiry day — extreme theta decay', severity: 'critical', penalty: 10 });
    } else if (dte <= 3) {
      warnings.push({ message: 'EXTREME theta decay (<3 days)', severity: 'critical', penalty: 10 });
    } else if (dte <= 7) {
      warnings.push({ message: 'Rapid theta decay (3-5% daily)', severity: 'severe', penalty: 8 });
    }

    // IV Rank (estimate from VIX)
    const vix = context.indiaVix || context.vix || 14;
    if (vix > 22) {
      warnings.push({ message: `High VIX (${vix.toFixed(1)}) — IV crush risk`, severity: 'severe', penalty: 8 });
    }

    // Wide spread
    if (suggestion.spreadPct && suggestion.spreadPct > 0.05) {
      warnings.push({ message: `Wide spread (${(suggestion.spreadPct * 100).toFixed(1)}%)`, severity: 'moderate', penalty: 5 });
    }

    // Deep OTM
    const spot = context.spotPrice;
    const distPct = Math.abs(suggestion.strikePrice - spot) / spot * 100;
    const isOTM = isCall ? suggestion.strikePrice > spot : suggestion.strikePrice < spot;
    if (isOTM && distPct > 3) {
      warnings.push({ message: `Deep OTM (${distPct.toFixed(1)}%)`, severity: 'severe', penalty: 8 });
    }

    // Against trend
    if (isCall && intradayChange < -0.5) {
      warnings.push({ message: 'Against strong bearish momentum', severity: 'critical', penalty: 10 });
    } else if (isCall && intradayChange < -0.3) {
      warnings.push({ message: 'Against bearish momentum', severity: 'severe', penalty: 8 });
    } else if (!isCall && intradayChange > 0.5) {
      warnings.push({ message: 'Against strong bullish momentum', severity: 'critical', penalty: 10 });
    } else if (!isCall && intradayChange > 0.3) {
      warnings.push({ message: 'Against bullish momentum', severity: 'severe', penalty: 8 });
    }

    // Low volume
    if ((suggestion.volume || 0) < 100) {
      warnings.push({ message: `Low volume (${suggestion.volume || 0})`, severity: 'moderate', penalty: 5 });
    }

    return warnings;
  }

  /**
   * Generate top score factors for "Why this trade?" section
   */
  generateScoreFactors(suggestion, context) {
    const factors = [];
    const isCall = suggestion.optionType === 'CE';
    const spot = context.spotPrice;
    const strikePrice = suggestion.strikePrice;
    const moneyness = Math.abs((strikePrice - spot) / spot);

    // Moneyness
    let moneynessScore;
    if (moneyness < 0.02) moneynessScore = 95;
    else if (moneyness < 0.04) moneynessScore = 80;
    else if (moneyness < 0.06) moneynessScore = 65;
    else moneynessScore = 40;
    factors.push({ factor: 'Strike Position', score: moneynessScore, impact: moneynessScore >= 70 ? 'bullish' : 'neutral' });

    // OI Signal
    const oiSignal = this.determineOISignal(suggestion, context);
    let oiScore = 50;
    if (oiSignal.signal === 'Long Buildup') oiScore = isCall ? 85 : 70;
    else if (oiSignal.signal === 'Short Buildup') oiScore = isCall ? 30 : 80;
    else if (oiSignal.signal === 'Short Covering') oiScore = 65;
    factors.push({ factor: 'OI Signal', score: oiScore, impact: oiScore >= 60 ? 'bullish' : oiScore <= 40 ? 'bearish' : 'neutral' });

    // Volume
    const avgVol = context.avgVolume || 50000;
    const volRatio = avgVol > 0 ? (suggestion.volume || 0) / avgVol : 1;
    const volScore = Math.min(95, Math.max(30, 50 + volRatio * 20));
    factors.push({ factor: 'Volume', score: Math.round(volScore), impact: volRatio > 1.5 ? 'bullish' : 'neutral' });

    // Risk:Reward
    const rr = suggestion.riskReward || 1.5;
    const rrScore = Math.min(95, Math.max(30, rr * 30));
    factors.push({ factor: 'Risk/Reward', score: Math.round(rrScore), impact: rr >= 2.0 ? 'bullish' : 'neutral' });

    // IV
    const iv = suggestion.iv || 15;
    const atmIV = context.atmIV || 15;
    const ivRatio = atmIV > 0 ? iv / atmIV : 1;
    const ivScore = ivRatio < 0.9 ? 80 : ivRatio > 1.2 ? 35 : 60;
    factors.push({ factor: 'IV Value', score: ivScore, impact: ivRatio < 0.9 ? 'bullish' : ivRatio > 1.2 ? 'bearish' : 'neutral' });

    // Sort by score descending and return top 3
    return factors.sort((a, b) => b.score - a.score).slice(0, 3);
  }

  formatNumber(num) {
    if (num >= 100000) {
      return (num / 100000).toFixed(1) + 'L';
    } else if (num >= 1000) {
      return (num / 1000).toFixed(1) + 'K';
    }
    return num.toString();
  }

  // ==========================================
  // Multi-Leg Strategy Generation Engine
  // ==========================================

  static LOT_SIZES = { NIFTY: 75, BANKNIFTY: 30, FINNIFTY: 25, MIDCPNIFTY: 50, SENSEX: 10, BANKEX: 15 };
  static STRIKE_INTERVALS = { NIFTY: 50, BANKNIFTY: 100, FINNIFTY: 50, MIDCPNIFTY: 50, SENSEX: 100, BANKEX: 100 };
  static SCORING_WEIGHTS = { ivAlignment: 0.25, pop: 0.20, riskReward: 0.20, liquidity: 0.15, regimeFit: 0.20 };
  static STRATEGY_TYPES = {
    nakedCall:      { name: 'Naked Call',       icon: '↑', direction: 'Bullish',  preferredIVCenter: 15 },
    nakedPut:       { name: 'Naked Put',        icon: '↓', direction: 'Bearish',  preferredIVCenter: 15 },
    bullCallSpread: { name: 'Bull Call Spread',  icon: '↗', direction: 'Bullish',  preferredIVCenter: 50 },
    bearPutSpread:  { name: 'Bear Put Spread',   icon: '↘', direction: 'Bearish',  preferredIVCenter: 50 },
    ironCondor:     { name: 'Iron Condor',       icon: '⬌', direction: 'Neutral',  preferredIVCenter: 75 },
    strangle:       { name: 'Short Strangle',    icon: '↔', direction: 'Neutral',  preferredIVCenter: 75 },
    straddle:       { name: 'Long Straddle',     icon: '↕', direction: 'Neutral',  preferredIVCenter: 15 },
  };

  /**
   * Generate multi-leg strategy suggestions
   * @param {Array} optionChain - option chain rows
   * @param {Object} context - market context (spotPrice, indexName, marketBias, indiaVix, etc.)
   * @returns {Array} sorted strategy suggestions (max 5)
   */
  generateStrategySuggestions(optionChain, context) {
    if (!optionChain || optionChain.length === 0) return [];

    const spot = context.spotPrice;
    const indexName = context.indexName || 'NIFTY';
    const lotSize = AIAnalysisService.LOT_SIZES[indexName] || 75;
    const interval = AIAnalysisService.STRIKE_INTERVALS[indexName] || 50;
    const vix = context.indiaVix || context.vix || 15;
    const ivRank = Math.min(100, Math.max(0, (vix - 10) / 20 * 100));
    const bias = (context.marketBias || 'Neutral').toLowerCase().replace(/\s+/g, '');
    const isBullish = bias.includes('bullish');
    const isBearish = bias.includes('bearish');

    // Detect regime
    const absMove = Math.abs(context.intradayChange || context.pChange || 0);
    let regime = 'rangeBound';
    if (vix > 20 || absMove > 1.0) regime = 'volatile';
    else if (absMove > 0.3) regime = 'trending';

    const strategies = [];

    // Helper: find option chain row closest to target strike
    const findRow = (target) => optionChain.reduce((prev, curr) =>
      Math.abs(curr.strikePrice - target) < Math.abs(prev.strikePrice - target) ? curr : prev
    );
    const atmStrike = findRow(spot).strikePrice;

    // 1. Directional strategies
    if (isBullish) {
      if (ivRank < 30) {
        const s = this._createNakedOption(findRow(atmStrike), true, lotSize, ivRank, regime);
        if (s) strategies.push(s);
      }
      const s = this._createBullCallSpread(optionChain, atmStrike, interval, lotSize, ivRank, regime, findRow);
      if (s) strategies.push(s);
    } else if (isBearish) {
      if (ivRank < 30) {
        const s = this._createNakedOption(findRow(atmStrike), false, lotSize, ivRank, regime);
        if (s) strategies.push(s);
      }
      const s = this._createBearPutSpread(optionChain, atmStrike, interval, lotSize, ivRank, regime, findRow);
      if (s) strategies.push(s);
    }

    // 2. Neutral strategies
    if (regime === 'rangeBound' || !isBullish && !isBearish || ivRank > 50) {
      if (ivRank > 50) {
        const s = this._createIronCondor(atmStrike, interval, lotSize, ivRank, regime, findRow);
        if (s) strategies.push(s);
      }
      if (ivRank > 40) {
        const s = this._createStrangle(atmStrike, interval, lotSize, ivRank, regime, findRow);
        if (s) strategies.push(s);
      }
    }

    // 3. Straddle for volatile regime or low IV neutral
    if ((regime === 'volatile' && ivRank < 60) || (ivRank < 30 && !isBullish && !isBearish)) {
      const s = this._createStraddle(findRow(atmStrike), lotSize, ivRank, regime);
      if (s) strategies.push(s);
    }

    // Sort by score desc, limit 5
    strategies.sort((a, b) => (b.score || 0) - (a.score || 0));
    return strategies.slice(0, 5);
  }

  // --- Strategy Creators ---

  _createNakedOption(row, isCall, lotSize, ivRank, regime) {
    const opt = isCall ? row.CE : row.PE;
    if (!opt || !opt.lastPrice) return null;
    const ltp = opt.lastPrice;
    const strike = row.strikePrice;
    const premium = ltp * lotSize;
    const delta = opt.delta || 0.5;
    const pop = isCall ? Math.abs(delta) : (1 - Math.abs(delta));
    const type = isCall ? 'nakedCall' : 'nakedPut';
    const legs = [{ option: opt, strike, type: isCall ? 'CE' : 'PE', action: 'BUY', qty: 1, ltp, premium, lotSize }];
    const greeks = this._calcNetGreeks(legs, opt, null);
    const scoring = this._scoreStrategy(type, ivRank, pop, 999999, premium, legs, regime);

    return {
      id: `${type}-${strike}`,
      strategyType: type,
      ...AIAnalysisService.STRATEGY_TYPES[type],
      legs,
      netPremium: -premium,
      isCredit: false,
      maxProfit: 999999,
      maxLoss: premium,
      breakevens: [isCall ? strike + ltp : strike - ltp],
      pop,
      ...greeks,
      score: scoring.score,
      scoreBreakdown: scoring.breakdown,
      reasoning: [
        `${isCall ? 'Bullish' : 'Bearish'} directional trade`,
        `IV Rank ${Math.round(ivRank)} — options are cheap`,
        `Max loss limited to premium: ${this.formatNumber(Math.round(premium))}`,
      ],
      marketCondition: `${isCall ? 'Bullish' : 'Bearish'} + Low IV`,
    };
  }

  _createBullCallSpread(optionChain, atmStrike, interval, lotSize, ivRank, regime, findRow) {
    const buyRow = findRow(atmStrike);
    const sellTarget = atmStrike + interval * 2;
    const sellRow = findRow(sellTarget);
    if (!buyRow.CE || !sellRow.CE || buyRow.strikePrice === sellRow.strikePrice) return null;

    const buyOpt = buyRow.CE, sellOpt = sellRow.CE;
    const buyLTP = buyOpt.lastPrice || 0, sellLTP = sellOpt.lastPrice || 0;
    if (buyLTP <= 0) return null;

    const netDebit = (buyLTP - sellLTP) * lotSize;
    const width = (sellRow.strikePrice - buyRow.strikePrice) * lotSize;
    const maxProfit = width - netDebit;
    const sellDelta = Math.abs(sellOpt.delta || 0.3);
    const pop = Math.min(1, Math.max(0, 1 - sellDelta));

    const legs = [
      { option: buyOpt, strike: buyRow.strikePrice, type: 'CE', action: 'BUY', qty: 1, ltp: buyLTP, premium: buyLTP * lotSize, lotSize },
      { option: sellOpt, strike: sellRow.strikePrice, type: 'CE', action: 'SELL', qty: 1, ltp: sellLTP, premium: sellLTP * lotSize, lotSize },
    ];
    const greeks = this._calcNetGreeks(legs, buyOpt, sellOpt);
    const scoring = this._scoreStrategy('bullCallSpread', ivRank, pop, maxProfit, netDebit, legs, regime);

    return {
      id: `bullCallSpread-${buyRow.strikePrice}-${sellRow.strikePrice}`,
      strategyType: 'bullCallSpread',
      ...AIAnalysisService.STRATEGY_TYPES.bullCallSpread,
      legs,
      netPremium: -netDebit,
      isCredit: false,
      maxProfit: Math.max(0, maxProfit),
      maxLoss: Math.max(0, netDebit),
      breakevens: [buyRow.strikePrice + (buyLTP - sellLTP)],
      pop,
      ...greeks,
      score: scoring.score,
      scoreBreakdown: scoring.breakdown,
      reasoning: [
        'Bullish spread — limited risk and reward',
        `Net debit: ${this.formatNumber(Math.round(netDebit))}`,
        'Sell call reduces cost when IV is elevated',
        `Max profit at ${sellRow.strikePrice}`,
      ],
      marketCondition: `Bullish + ${ivRank > 40 ? 'Elevated' : 'Low'} IV`,
    };
  }

  _createBearPutSpread(optionChain, atmStrike, interval, lotSize, ivRank, regime, findRow) {
    const buyRow = findRow(atmStrike);
    const sellTarget = atmStrike - interval * 2;
    const sellRow = findRow(sellTarget);
    if (!buyRow.PE || !sellRow.PE || buyRow.strikePrice === sellRow.strikePrice) return null;

    const buyOpt = buyRow.PE, sellOpt = sellRow.PE;
    const buyLTP = buyOpt.lastPrice || 0, sellLTP = sellOpt.lastPrice || 0;
    if (buyLTP <= 0) return null;

    const netDebit = (buyLTP - sellLTP) * lotSize;
    const width = (buyRow.strikePrice - sellRow.strikePrice) * lotSize;
    const maxProfit = width - netDebit;
    const sellDelta = Math.abs(sellOpt.delta || 0.3);
    const pop = Math.min(1, Math.max(0, 1 - sellDelta));

    const legs = [
      { option: buyOpt, strike: buyRow.strikePrice, type: 'PE', action: 'BUY', qty: 1, ltp: buyLTP, premium: buyLTP * lotSize, lotSize },
      { option: sellOpt, strike: sellRow.strikePrice, type: 'PE', action: 'SELL', qty: 1, ltp: sellLTP, premium: sellLTP * lotSize, lotSize },
    ];
    const greeks = this._calcNetGreeks(legs, buyOpt, sellOpt);
    const scoring = this._scoreStrategy('bearPutSpread', ivRank, pop, maxProfit, netDebit, legs, regime);

    return {
      id: `bearPutSpread-${buyRow.strikePrice}-${sellRow.strikePrice}`,
      strategyType: 'bearPutSpread',
      ...AIAnalysisService.STRATEGY_TYPES.bearPutSpread,
      legs,
      netPremium: -netDebit,
      isCredit: false,
      maxProfit: Math.max(0, maxProfit),
      maxLoss: Math.max(0, netDebit),
      breakevens: [buyRow.strikePrice - (buyLTP - sellLTP)],
      pop,
      ...greeks,
      score: scoring.score,
      scoreBreakdown: scoring.breakdown,
      reasoning: [
        'Bearish spread — limited risk and reward',
        `Net debit: ${this.formatNumber(Math.round(netDebit))}`,
        'Sell put reduces cost when IV is elevated',
        `Max profit at ${sellRow.strikePrice}`,
      ],
      marketCondition: `Bearish + ${ivRank > 40 ? 'Elevated' : 'Low'} IV`,
    };
  }

  _createIronCondor(atmStrike, interval, lotSize, ivRank, regime, findRow) {
    const scRow = findRow(atmStrike + interval * 4);
    const bcRow = findRow(atmStrike + interval * 6);
    const spRow = findRow(atmStrike - interval * 4);
    const bpRow = findRow(atmStrike - interval * 6);

    if (!scRow.CE || !bcRow.CE || !spRow.PE || !bpRow.PE) return null;
    const sc = scRow.CE, bc = bcRow.CE, sp = spRow.PE, bp = bpRow.PE;
    const scLTP = sc.lastPrice || 0, bcLTP = bc.lastPrice || 0;
    const spLTP = sp.lastPrice || 0, bpLTP = bp.lastPrice || 0;

    const netCredit = (scLTP + spLTP - bcLTP - bpLTP) * lotSize;
    if (netCredit <= 0) return null;
    const wingWidth = (bcRow.strikePrice - scRow.strikePrice) * lotSize;
    const maxLoss = wingWidth - netCredit;

    const scDelta = Math.abs(sc.delta || 0.2);
    const spDelta = Math.abs(sp.delta || 0.2);
    const pop = Math.min(1, Math.max(0, 1 - scDelta - spDelta));

    const legs = [
      { option: sc, strike: scRow.strikePrice, type: 'CE', action: 'SELL', qty: 1, ltp: scLTP, premium: scLTP * lotSize, lotSize },
      { option: bc, strike: bcRow.strikePrice, type: 'CE', action: 'BUY', qty: 1, ltp: bcLTP, premium: bcLTP * lotSize, lotSize },
      { option: sp, strike: spRow.strikePrice, type: 'PE', action: 'SELL', qty: 1, ltp: spLTP, premium: spLTP * lotSize, lotSize },
      { option: bp, strike: bpRow.strikePrice, type: 'PE', action: 'BUY', qty: 1, ltp: bpLTP, premium: bpLTP * lotSize, lotSize },
    ];
    const greeks = this._calcNetGreeksMulti(legs);
    const scoring = this._scoreStrategy('ironCondor', ivRank, pop, netCredit, maxLoss, legs, regime);

    return {
      id: `ironCondor-${spRow.strikePrice}-${scRow.strikePrice}`,
      strategyType: 'ironCondor',
      ...AIAnalysisService.STRATEGY_TYPES.ironCondor,
      legs,
      netPremium: netCredit,
      isCredit: true,
      maxProfit: Math.max(0, netCredit),
      maxLoss: Math.max(0, maxLoss),
      breakevens: [spRow.strikePrice - netCredit / lotSize, scRow.strikePrice + netCredit / lotSize],
      pop,
      ...greeks,
      score: scoring.score,
      scoreBreakdown: scoring.breakdown,
      reasoning: [
        `High IV (${Math.round(ivRank)}) — premium selling strategy`,
        `Net credit: ${this.formatNumber(Math.round(netCredit))}`,
        `Profit if market stays between ${spRow.strikePrice} - ${scRow.strikePrice}`,
        'Defined risk with wing protection',
      ],
      marketCondition: 'Neutral + High IV',
    };
  }

  _createStrangle(atmStrike, interval, lotSize, ivRank, regime, findRow) {
    const cRow = findRow(atmStrike + interval * 3);
    const pRow = findRow(atmStrike - interval * 3);
    if (!cRow.CE || !pRow.PE) return null;

    const call = cRow.CE, put = pRow.PE;
    const cLTP = call.lastPrice || 0, pLTP = put.lastPrice || 0;
    const totalPremium = (cLTP + pLTP) * lotSize;
    if (totalPremium <= 0) return null;

    const cDelta = Math.abs(call.delta || 0.2);
    const pDelta = Math.abs(put.delta || 0.2);
    const pop = Math.min(1, Math.max(0, 1 - cDelta - pDelta));

    const legs = [
      { option: call, strike: cRow.strikePrice, type: 'CE', action: 'SELL', qty: 1, ltp: cLTP, premium: cLTP * lotSize, lotSize },
      { option: put, strike: pRow.strikePrice, type: 'PE', action: 'SELL', qty: 1, ltp: pLTP, premium: pLTP * lotSize, lotSize },
    ];
    const greeks = this._calcNetGreeksMulti(legs);
    const scoring = this._scoreStrategy('strangle', ivRank, pop, totalPremium, 999999, legs, regime);

    return {
      id: `strangle-${pRow.strikePrice}-${cRow.strikePrice}`,
      strategyType: 'strangle',
      ...AIAnalysisService.STRATEGY_TYPES.strangle,
      legs,
      netPremium: totalPremium,
      isCredit: true,
      maxProfit: totalPremium,
      maxLoss: 999999,
      breakevens: [pRow.strikePrice - totalPremium / lotSize, cRow.strikePrice + totalPremium / lotSize],
      pop,
      ...greeks,
      score: scoring.score,
      scoreBreakdown: scoring.breakdown,
      reasoning: [
        `High IV (${Math.round(ivRank)}) — premium selling opportunity`,
        `Net credit: ${this.formatNumber(Math.round(totalPremium))}`,
        `Profit if market stays between ${pRow.strikePrice} - ${cRow.strikePrice}`,
      ],
      marketCondition: 'Neutral + High IV',
    };
  }

  _createStraddle(row, lotSize, ivRank, regime) {
    if (!row.CE || !row.PE) return null;
    const call = row.CE, put = row.PE;
    const cLTP = call.lastPrice || 0, pLTP = put.lastPrice || 0;
    const totalPremium = (cLTP + pLTP) * lotSize;
    if (totalPremium <= 0) return null;
    const strike = row.strikePrice;
    const beDist = totalPremium / lotSize;

    const legs = [
      { option: call, strike, type: 'CE', action: 'BUY', qty: 1, ltp: cLTP, premium: cLTP * lotSize, lotSize },
      { option: put, strike, type: 'PE', action: 'BUY', qty: 1, ltp: pLTP, premium: pLTP * lotSize, lotSize },
    ];
    const greeks = this._calcNetGreeksMulti(legs);
    const scoring = this._scoreStrategy('straddle', ivRank, 0.35, 999999, totalPremium, legs, regime);

    return {
      id: `straddle-${strike}`,
      strategyType: 'straddle',
      ...AIAnalysisService.STRATEGY_TYPES.straddle,
      legs,
      netPremium: -totalPremium,
      isCredit: false,
      maxProfit: 999999,
      maxLoss: totalPremium,
      breakevens: [strike - beDist, strike + beDist],
      pop: 0.35,
      ...greeks,
      score: scoring.score,
      scoreBreakdown: scoring.breakdown,
      reasoning: [
        `Low IV (${Math.round(ivRank)}) — options are cheap`,
        'ATM straddle for maximum gamma',
        'Profit from big move in either direction',
        `Need ${Math.round(beDist)} point move to breakeven`,
      ],
      marketCondition: 'Expecting Big Move + Low IV',
    };
  }

  // --- Greeks & Scoring Helpers ---

  _calcNetGreeks(legs, buyOpt, sellOpt) {
    let d = 0, t = 0, g = 0, v = 0;
    for (const leg of legs) {
      const sign = leg.action === 'BUY' ? 1 : -1;
      const o = leg.option || {};
      d += sign * (o.delta || 0);
      t += sign * (o.theta || 0);
      g += sign * (o.gamma || 0);
      v += sign * (o.vega || 0);
    }
    return { netDelta: d, netTheta: t, netGamma: g, netVega: v };
  }

  _calcNetGreeksMulti(legs) {
    return this._calcNetGreeks(legs);
  }

  _scoreStrategy(type, ivRank, pop, maxProfit, maxLoss, legs, regime) {
    const meta = AIAnalysisService.STRATEGY_TYPES[type] || { preferredIVCenter: 50 };
    const w = AIAnalysisService.SCORING_WEIGHTS;

    // IV Alignment
    const ivDist = Math.abs(ivRank - meta.preferredIVCenter);
    const ivAlignment = Math.max(0, Math.min(100, 100 - ivDist * 1.5));

    // POP
    const popScore = pop > 0 ? Math.min(100, pop * 100) : 50;

    // Risk:Reward
    let rrScore = 50;
    if (maxLoss > 0 && maxProfit > 0) {
      if (maxProfit > 100000) rrScore = 80;
      else rrScore = Math.min(95, Math.max(20, (maxProfit / maxLoss) * 30));
    }

    // Liquidity
    const avgVol = legs.length > 0
      ? legs.reduce((s, l) => s + ((l.option?.totalTradedVolume || l.option?.volume) || 0), 0) / legs.length
      : 0;
    const liqScore = Math.min(100, Math.max(10, avgVol / 50));

    // Regime Fit
    const dir = meta.direction || 'Neutral';
    const isNeutral = dir === 'Neutral';
    const isDirectional = dir === 'Bullish' || dir === 'Bearish';
    let regimeFit = 50;
    if (regime === 'rangeBound') regimeFit = isNeutral ? 90 : 40;
    else if (regime === 'volatile') regimeFit = (type === 'straddle' || type === 'strangle') ? 85 : (isNeutral ? 50 : 40);
    else if (regime === 'trending') regimeFit = isDirectional ? 85 : (type === 'ironCondor' ? 25 : 50);

    const score = ivAlignment * w.ivAlignment + popScore * w.pop + rrScore * w.riskReward + liqScore * w.liquidity + regimeFit * w.regimeFit;

    return {
      score: Math.min(100, Math.max(0, score)),
      breakdown: { ivAlignment: Math.round(ivAlignment), pop: Math.round(popScore), riskReward: Math.round(rrScore), liquidity: Math.round(liqScore), regimeFit: Math.round(regimeFit) },
    };
  }
}

export const aiAnalysisService = new AIAnalysisService();
export default aiAnalysisService;
