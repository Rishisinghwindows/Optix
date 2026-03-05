/**
 * Options Screener Engine
 * Pure filter/sort utility for option chain data.
 *
 * Expected optionChain row format:
 * {
 *   strikePrice,
 *   callOption: { lastTradedPrice, openInterest, changeInOI, impliedVolatility, volume, delta, gamma, theta, vega, underlyingValue },
 *   putOption:  { lastTradedPrice, openInterest, changeInOI, impliedVolatility, volume, delta, gamma, theta, vega, underlyingValue }
 * }
 *
 * IV is stored as decimal (0.15 for 15%). Delta is a float.
 */

import { getMoneyness } from './blackScholes'

/**
 * Returns the default filter configuration with reasonable defaults.
 */
export function getDefaultFilters() {
  return {
    optionType: 'Both',    // 'CE' | 'PE' | 'Both'
    moneyness: 'All',      // 'All' | 'ATM' | 'ITM' | 'OTM'
    deltaMin: 0,
    deltaMax: 1,
    ivMin: 0,              // in percentage (0-200)
    ivMax: 200,
    oiMin: 0,
    volumeMin: 0,
    premiumMin: 0,
    premiumMax: 99999,
    sortBy: 'oi',          // 'delta' | 'iv' | 'oi' | 'volume' | 'premium'
    sortAscending: false,
  }
}

/**
 * Flattens an option side into a standard result object.
 */
function flattenOption(strikePrice, optionType, optionData) {
  return {
    strikePrice,
    optionType,
    ltp: optionData.lastTradedPrice || 0,
    iv: optionData.impliedVolatility || 0,
    delta: optionData.delta || 0,
    gamma: optionData.gamma || 0,
    theta: optionData.theta || 0,
    vega: optionData.vega || 0,
    oi: optionData.openInterest || 0,
    oiChange: optionData.changeInOI || 0,
    volume: optionData.volume || 0,
    underlyingValue: optionData.underlyingValue || 0,
  }
}

/**
 * Checks whether a single option passes all numeric filter criteria.
 */
function passesFilters(option, filters, spotPrice) {
  // Moneyness filter
  if (filters.moneyness !== 'All') {
    const bsType = option.optionType === 'CE' ? 'call' : 'put'
    const moneyness = getMoneyness(spotPrice, option.strikePrice, bsType)
    if (moneyness !== filters.moneyness) {
      return false
    }
  }

  // Delta filter (use absolute value)
  const absDelta = Math.abs(option.delta)
  if (absDelta < filters.deltaMin || absDelta > filters.deltaMax) {
    return false
  }

  // IV filter (IV stored as decimal, filters in percentage)
  const ivPercent = option.iv * 100
  if (ivPercent < filters.ivMin || ivPercent > filters.ivMax) {
    return false
  }

  // OI filter
  if (option.oi < filters.oiMin) {
    return false
  }

  // Volume filter
  if (option.volume < filters.volumeMin) {
    return false
  }

  // Premium filter
  if (option.ltp < filters.premiumMin || option.ltp > filters.premiumMax) {
    return false
  }

  return true
}

/**
 * Filters option chain data based on the provided filters.
 *
 * @param {Array} optionChain - Array of option chain row objects
 * @param {Object} filters - Filter criteria (see getDefaultFilters for shape)
 * @returns {Array} Flat array of matching options with standardized fields
 */
export function filterOptions(optionChain, filters) {
  if (!Array.isArray(optionChain) || optionChain.length === 0) {
    return []
  }

  const mergedFilters = { ...getDefaultFilters(), ...filters }
  const results = []

  for (const row of optionChain) {
    const strikePrice = row.strikePrice
    // Determine spot price from the option data
    const spotPrice =
      row.callOption?.underlyingValue ||
      row.putOption?.underlyingValue ||
      0

    // Process call option
    if (mergedFilters.optionType === 'CE' || mergedFilters.optionType === 'Both') {
      if (row.callOption) {
        const option = flattenOption(strikePrice, 'CE', row.callOption)
        option.underlyingValue = spotPrice
        if (passesFilters(option, mergedFilters, spotPrice)) {
          results.push(option)
        }
      }
    }

    // Process put option
    if (mergedFilters.optionType === 'PE' || mergedFilters.optionType === 'Both') {
      if (row.putOption) {
        const option = flattenOption(strikePrice, 'PE', row.putOption)
        option.underlyingValue = spotPrice
        if (passesFilters(option, mergedFilters, spotPrice)) {
          results.push(option)
        }
      }
    }
  }

  return results
}

/**
 * Sorts screener results by the specified field.
 *
 * @param {Array} results - Flat array of option results from filterOptions
 * @param {string} sortBy - Field to sort by: 'delta', 'iv', 'oi', 'volume', 'premium'
 * @param {boolean} ascending - Sort direction
 * @returns {Array} Sorted array (new array, does not mutate input)
 */
export function sortResults(results, sortBy = 'oi', ascending = false) {
  if (!Array.isArray(results) || results.length === 0) {
    return results
  }

  const fieldMap = {
    delta: 'delta',
    iv: 'iv',
    oi: 'oi',
    volume: 'volume',
    premium: 'ltp',
  }

  const field = fieldMap[sortBy] || 'oi'

  return [...results].sort((a, b) => {
    let valA = a[field]
    let valB = b[field]

    // For delta, sort by absolute value
    if (sortBy === 'delta') {
      valA = Math.abs(valA)
      valB = Math.abs(valB)
    }

    if (ascending) {
      return valA - valB
    }
    return valB - valA
  })
}
