const STORAGE_KEY = 'ai_scorecard_picks'
const MAX_PICKS = 100
const RESET_KEY = 'scorecard_reset_v1_1'

// One-time reset for v1.1 scoring fix
if (!localStorage.getItem(RESET_KEY)) {
  localStorage.removeItem(STORAGE_KEY)
  localStorage.setItem(RESET_KEY, '1')
}

function loadPicks() {
  try {
    const data = localStorage.getItem(STORAGE_KEY)
    return data ? JSON.parse(data) : []
  } catch {
    return []
  }
}

function savePicks(picks) {
  const trimmed = picks.slice(-MAX_PICKS)
  localStorage.setItem(STORAGE_KEY, JSON.stringify(trimmed))
}

export function trackSuggestions(suggestions, indexName, expiryDate) {
  if (!suggestions?.length) return
  const picks = loadPicks()
  const existingIds = new Set(picks.map(p => p.id))

  for (const s of suggestions) {
    const id = `${indexName}_${Math.round(s.strikePrice)}_${s.optionType}_${expiryDate}`
    if (existingIds.has(id)) continue

    picks.push({
      id,
      indexName,
      strikePrice: s.strikePrice,
      optionType: s.optionType,
      entryPrice: s.entryPrice || s.ltp,
      targetPrice: s.targetPrice,
      stopLossPrice: s.stopLossPrice,
      score: s.score,
      tier: s.tier || 'worthWatching',
      createdAt: new Date().toISOString(),
      expiryDate: expiryDate || '',
      outcome: 'active',
      exitPrice: null,
      returnPct: null,
      highWaterMark: s.entryPrice || s.ltp,
      resolvedAt: null,
    })
  }

  savePicks(picks)
}

export function resolveFromOptionChain(optionChain, indexName, expiryDate) {
  if (!optionChain?.length) return
  const priceMap = {}
  for (const row of optionChain) {
    const strike = Math.round(row.strikePrice)
    const expiry = expiryDate || row.expiryDate || ''
    if (row.CE?.lastPrice > 0) {
      priceMap[`${indexName}_${strike}_CE_${expiry}`] = row.CE.lastPrice
    }
    if (row.PE?.lastPrice > 0) {
      priceMap[`${indexName}_${strike}_PE_${expiry}`] = row.PE.lastPrice
    }
  }
  if (Object.keys(priceMap).length > 0) {
    resolveOutcomes(priceMap)
  }
}

export function resolveOutcomes(currentPrices) {
  // currentPrices: { pickId: currentLTP }
  const picks = loadPicks()
  let changed = false
  const now = new Date()

  for (let i = 0; i < picks.length; i++) {
    if (picks[i].outcome !== 'active') continue

    const currentPrice = currentPrices?.[picks[i].id]

    // Check expiry date
    const isExpired = picks[i].expiryDate && new Date(picks[i].expiryDate) < now

    if (currentPrice != null) {
      // Update HWM
      if (currentPrice > (picks[i].highWaterMark || 0)) {
        picks[i].highWaterMark = currentPrice
        changed = true
      }

      // Target hit
      if (currentPrice >= picks[i].targetPrice) {
        picks[i].outcome = 'win'
        picks[i].exitPrice = picks[i].targetPrice
        picks[i].returnPct = ((picks[i].targetPrice - picks[i].entryPrice) / picks[i].entryPrice) * 100
        picks[i].resolvedAt = now.toISOString()
        changed = true
      }
      // SL hit
      else if (currentPrice <= picks[i].stopLossPrice) {
        picks[i].outcome = 'loss'
        picks[i].exitPrice = picks[i].stopLossPrice
        picks[i].returnPct = ((picks[i].stopLossPrice - picks[i].entryPrice) / picks[i].entryPrice) * 100
        picks[i].resolvedAt = now.toISOString()
        changed = true
      }
      // Expired with current price
      else if (isExpired) {
        picks[i].outcome = 'expired'
        picks[i].exitPrice = currentPrice
        picks[i].returnPct = ((currentPrice - picks[i].entryPrice) / picks[i].entryPrice) * 100
        picks[i].resolvedAt = now.toISOString()
        changed = true
      }
    } else if (isExpired) {
      picks[i].outcome = 'expired'
      picks[i].exitPrice = picks[i].entryPrice * 0.5
      picks[i].returnPct = -50
      picks[i].resolvedAt = now.toISOString()
      changed = true
    }
  }

  if (changed) savePicks(picks)
}

export function getStats() {
  const picks = loadPicks()
  const resolved = picks.filter(p => p.outcome !== 'active')
  const wins = resolved.filter(p => p.outcome === 'win')
  const losses = resolved.filter(p => p.outcome === 'loss')
  const expired = resolved.filter(p => p.outcome === 'expired')
  const active = picks.filter(p => p.outcome === 'active')

  const winRate = resolved.length ? (wins.length / resolved.length) * 100 : 0
  const avgReturn = resolved.length
    ? resolved.reduce((sum, p) => sum + (p.returnPct || 0), 0) / resolved.length
    : 0

  const topPickResolved = resolved.filter(p => p.tier === 'topPick')
  const topPickWins = topPickResolved.filter(p => p.outcome === 'win')
  const topPickWinRate = topPickResolved.length
    ? (topPickWins.length / topPickResolved.length) * 100
    : 0

  return {
    totalPicks: picks.length,
    activePicks: active.length,
    wins: wins.length,
    losses: losses.length,
    expired: expired.length,
    winRate,
    avgReturn,
    topPickWinRate,
    recentPicks: [...picks].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)).slice(0, 10),
  }
}

export function clearScorecard() {
  localStorage.removeItem(STORAGE_KEY)
}
