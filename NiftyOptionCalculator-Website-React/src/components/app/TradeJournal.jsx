import React, { useState, useEffect, useCallback } from 'react'
import { useAuth } from '../../context/AuthContext'
import { useNavigate } from 'react-router-dom'
import {
  getJournalEntries,
  createJournalEntry,
  updateJournalEntry,
  deleteJournalEntry,
  getJournalStats,
} from '../../services/journalAPI'

const TAGS = ['scalp', 'swing', 'hedging', 'spread', 'directional', 'expiry_day']
const MOODS = ['confident', 'nervous', 'neutral', 'greedy', 'fearful', 'disciplined']
const MARKET_CONDITIONS = ['trending_up', 'trending_down', 'range_bound', 'volatile', 'low_vol']
const OUTCOMES = ['profit', 'loss', 'breakeven']
const SYMBOLS = ['NIFTY', 'BANKNIFTY', 'FINNIFTY', 'SENSEX', 'MIDCPNIFTY']
const LOT_SIZES = { NIFTY: 75, BANKNIFTY: 30, FINNIFTY: 25, SENSEX: 10, MIDCPNIFTY: 50 }

const moodEmoji = { confident: '\u{1F4AA}', nervous: '\u{1F630}', neutral: '\u{1F610}', greedy: '\u{1F911}', fearful: '\u{1F628}', disciplined: '\u{1F9D8}' }
const outcomeColor = { profit: 'var(--profit, #00e676)', loss: 'var(--loss, #ff5252)', breakeven: 'var(--text-secondary, #aaa)' }

function TradeJournal() {
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()

  const [entries, setEntries] = useState([])
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)
  const [activeTab, setActiveTab] = useState('entries') // entries | stats | add
  const [filters, setFilters] = useState({ outcome: '', symbol: '', tag: '' })
  const [showForm, setShowForm] = useState(false)
  const [editingEntry, setEditingEntry] = useState(null)

  // Form state
  const [form, setForm] = useState({
    symbol: 'NIFTY', strike_price: '', option_type: 'CE', direction: 'buy',
    entry_price: '', exit_price: '', quantity: 1, lot_size: 75,
    entry_date: new Date().toISOString().slice(0, 16), exit_date: '',
    expiry_date: '', title: '', notes: '', tags: '',
    market_condition: '', mood: '', outcome: '',
  })

  const loadEntries = useCallback(async () => {
    try {
      setLoading(true)
      const data = await getJournalEntries(filters)
      setEntries(data.entries || [])
    } catch (err) {
      if (err.message === 'Not authenticated') return
    } finally {
      setLoading(false)
    }
  }, [filters])

  const loadStats = useCallback(async () => {
    try {
      const data = await getJournalStats()
      setStats(data)
    } catch (err) {
      // ignore
    }
  }, [])

  useEffect(() => {
    if (isAuthenticated) {
      loadEntries()
      loadStats()
    }
  }, [isAuthenticated, loadEntries, loadStats])

  const handleSubmit = async (e) => {
    e.preventDefault()
    try {
      const payload = {
        ...form,
        strike_price: parseFloat(form.strike_price),
        entry_price: parseFloat(form.entry_price),
        exit_price: form.exit_price ? parseFloat(form.exit_price) : null,
        quantity: parseInt(form.quantity),
        lot_size: parseInt(form.lot_size),
        entry_date: new Date(form.entry_date).toISOString(),
        exit_date: form.exit_date ? new Date(form.exit_date).toISOString() : null,
        market_condition: form.market_condition || null,
        mood: form.mood || null,
        outcome: form.outcome || null,
        tags: form.tags || null,
      }

      if (editingEntry) {
        await updateJournalEntry(editingEntry.id, payload)
      } else {
        await createJournalEntry(payload)
      }

      setShowForm(false)
      setEditingEntry(null)
      resetForm()
      loadEntries()
      loadStats()
    } catch (err) {
      alert(err.message)
    }
  }

  const handleDelete = async (id) => {
    if (!confirm('Delete this journal entry?')) return
    try {
      await deleteJournalEntry(id)
      loadEntries()
      loadStats()
    } catch (err) {
      alert(err.message)
    }
  }

  const handleEdit = (entry) => {
    setEditingEntry(entry)
    setForm({
      symbol: entry.symbol,
      strike_price: entry.strike_price,
      option_type: entry.option_type,
      direction: entry.direction,
      entry_price: entry.entry_price,
      exit_price: entry.exit_price || '',
      quantity: entry.quantity,
      lot_size: entry.lot_size,
      entry_date: entry.entry_date ? entry.entry_date.slice(0, 16) : '',
      exit_date: entry.exit_date ? entry.exit_date.slice(0, 16) : '',
      expiry_date: entry.expiry_date || '',
      title: entry.title || '',
      notes: entry.notes || '',
      tags: entry.tags || '',
      market_condition: entry.market_condition || '',
      mood: entry.mood || '',
      outcome: entry.outcome || '',
    })
    setShowForm(true)
  }

  const resetForm = () => {
    setForm({
      symbol: 'NIFTY', strike_price: '', option_type: 'CE', direction: 'buy',
      entry_price: '', exit_price: '', quantity: 1, lot_size: 75,
      entry_date: new Date().toISOString().slice(0, 16), exit_date: '',
      expiry_date: '', title: '', notes: '', tags: '',
      market_condition: '', mood: '', outcome: '',
    })
  }

  if (!isAuthenticated) {
    return (
      <div className="paper-trading-container" style={{ textAlign: 'center', padding: '60px 20px' }}>
        <h2>Trade Journal</h2>
        <p style={{ color: 'var(--text-secondary)', margin: '16px 0' }}>Log in to access your trade journal</p>
        <button className="btn-primary" onClick={() => navigate('/login')}>Login</button>
      </div>
    )
  }

  return (
    <div className="paper-trading-container">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <h2 style={{ margin: 0, color: 'var(--text-primary)' }}>Trade Journal</h2>
        <button
          onClick={() => { setShowForm(!showForm); setEditingEntry(null); resetForm() }}
          style={{
            padding: '8px 20px', borderRadius: 8, border: 'none',
            background: 'var(--accent-primary, #448aff)', color: '#fff', cursor: 'pointer',
            fontWeight: 600, fontSize: 14,
          }}
        >
          {showForm ? 'Cancel' : '+ New Entry'}
        </button>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        {['entries', 'stats'].map(tab => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            style={{
              padding: '8px 16px', borderRadius: 8, border: 'none', cursor: 'pointer',
              background: activeTab === tab ? 'var(--accent-primary, #448aff)' : 'var(--card-bg, #1e1e2e)',
              color: activeTab === tab ? '#fff' : 'var(--text-secondary)',
              fontWeight: 600, fontSize: 13, textTransform: 'capitalize',
            }}
          >
            {tab}
          </button>
        ))}
      </div>

      {/* Entry Form */}
      {showForm && (
        <div style={{
          background: 'var(--card-bg, #1e1e2e)', borderRadius: 12, padding: 20,
          marginBottom: 16, border: '1px solid var(--border-color, #333)',
        }}>
          <h3 style={{ margin: '0 0 16px', color: 'var(--text-primary)' }}>
            {editingEntry ? 'Edit Entry' : 'New Journal Entry'}
          </h3>
          <form onSubmit={handleSubmit}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: 12 }}>
              <div>
                <label style={labelStyle}>Symbol</label>
                <select value={form.symbol} onChange={e => {
                  const sym = e.target.value
                  setForm(f => ({ ...f, symbol: sym, lot_size: LOT_SIZES[sym] || 75 }))
                }} style={inputStyle}>
                  {SYMBOLS.map(s => <option key={s} value={s}>{s}</option>)}
                </select>
              </div>
              <div>
                <label style={labelStyle}>Strike Price</label>
                <input type="number" value={form.strike_price} onChange={e => setForm(f => ({ ...f, strike_price: e.target.value }))} style={inputStyle} required />
              </div>
              <div>
                <label style={labelStyle}>Type</label>
                <select value={form.option_type} onChange={e => setForm(f => ({ ...f, option_type: e.target.value }))} style={inputStyle}>
                  <option value="CE">CE (Call)</option>
                  <option value="PE">PE (Put)</option>
                </select>
              </div>
              <div>
                <label style={labelStyle}>Direction</label>
                <select value={form.direction} onChange={e => setForm(f => ({ ...f, direction: e.target.value }))} style={inputStyle}>
                  <option value="buy">Buy</option>
                  <option value="sell">Sell</option>
                </select>
              </div>
              <div>
                <label style={labelStyle}>Entry Price</label>
                <input type="number" step="0.01" value={form.entry_price} onChange={e => setForm(f => ({ ...f, entry_price: e.target.value }))} style={inputStyle} required />
              </div>
              <div>
                <label style={labelStyle}>Exit Price</label>
                <input type="number" step="0.01" value={form.exit_price} onChange={e => setForm(f => ({ ...f, exit_price: e.target.value }))} style={inputStyle} placeholder="Open" />
              </div>
              <div>
                <label style={labelStyle}>Lots</label>
                <input type="number" min="1" value={form.quantity} onChange={e => setForm(f => ({ ...f, quantity: e.target.value }))} style={inputStyle} />
              </div>
              <div>
                <label style={labelStyle}>Lot Size</label>
                <input type="number" value={form.lot_size} onChange={e => setForm(f => ({ ...f, lot_size: e.target.value }))} style={inputStyle} />
              </div>
              <div>
                <label style={labelStyle}>Entry Date</label>
                <input type="datetime-local" value={form.entry_date} onChange={e => setForm(f => ({ ...f, entry_date: e.target.value }))} style={inputStyle} required />
              </div>
              <div>
                <label style={labelStyle}>Exit Date</label>
                <input type="datetime-local" value={form.exit_date} onChange={e => setForm(f => ({ ...f, exit_date: e.target.value }))} style={inputStyle} />
              </div>
            </div>

            <div style={{ marginTop: 16 }}>
              <label style={labelStyle}>Title</label>
              <input type="text" value={form.title} onChange={e => setForm(f => ({ ...f, title: e.target.value }))} style={{ ...inputStyle, width: '100%' }} placeholder="e.g. NIFTY breakout trade" />
            </div>

            <div style={{ marginTop: 12 }}>
              <label style={labelStyle}>Notes</label>
              <textarea value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))} style={{ ...inputStyle, width: '100%', minHeight: 80, resize: 'vertical' }} placeholder="What was your reasoning?" />
            </div>

            {/* Tags */}
            <div style={{ marginTop: 12 }}>
              <label style={labelStyle}>Tags</label>
              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                {TAGS.map(tag => {
                  const selected = (form.tags || '').split(',').map(t => t.trim()).includes(tag)
                  return (
                    <button key={tag} type="button" onClick={() => {
                      const current = (form.tags || '').split(',').map(t => t.trim()).filter(Boolean)
                      const next = selected ? current.filter(t => t !== tag) : [...current, tag]
                      setForm(f => ({ ...f, tags: next.join(',') }))
                    }} style={{
                      padding: '4px 12px', borderRadius: 16, border: 'none', cursor: 'pointer', fontSize: 12,
                      background: selected ? 'var(--accent-primary, #448aff)' : 'var(--surface-bg, #2a2a3a)',
                      color: selected ? '#fff' : 'var(--text-secondary)',
                    }}>{tag}</button>
                  )
                })}
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12, marginTop: 12 }}>
              <div>
                <label style={labelStyle}>Mood</label>
                <select value={form.mood} onChange={e => setForm(f => ({ ...f, mood: e.target.value }))} style={inputStyle}>
                  <option value="">Select...</option>
                  {MOODS.map(m => <option key={m} value={m}>{moodEmoji[m]} {m}</option>)}
                </select>
              </div>
              <div>
                <label style={labelStyle}>Market Condition</label>
                <select value={form.market_condition} onChange={e => setForm(f => ({ ...f, market_condition: e.target.value }))} style={inputStyle}>
                  <option value="">Select...</option>
                  {MARKET_CONDITIONS.map(c => <option key={c} value={c}>{c.replace('_', ' ')}</option>)}
                </select>
              </div>
              <div>
                <label style={labelStyle}>Outcome</label>
                <select value={form.outcome} onChange={e => setForm(f => ({ ...f, outcome: e.target.value }))} style={inputStyle}>
                  <option value="">Auto-detect</option>
                  {OUTCOMES.map(o => <option key={o} value={o}>{o}</option>)}
                </select>
              </div>
            </div>

            <button type="submit" style={{
              marginTop: 16, padding: '10px 24px', borderRadius: 8, border: 'none',
              background: 'var(--accent-primary, #448aff)', color: '#fff',
              cursor: 'pointer', fontWeight: 600, fontSize: 14,
            }}>
              {editingEntry ? 'Update Entry' : 'Save Entry'}
            </button>
          </form>
        </div>
      )}

      {/* Filters */}
      {activeTab === 'entries' && (
        <>
          <div style={{ display: 'flex', gap: 8, marginBottom: 16, flexWrap: 'wrap' }}>
            <select value={filters.outcome} onChange={e => setFilters(f => ({ ...f, outcome: e.target.value }))} style={{ ...inputStyle, width: 'auto' }}>
              <option value="">All Outcomes</option>
              {OUTCOMES.map(o => <option key={o} value={o}>{o}</option>)}
            </select>
            <select value={filters.symbol} onChange={e => setFilters(f => ({ ...f, symbol: e.target.value }))} style={{ ...inputStyle, width: 'auto' }}>
              <option value="">All Symbols</option>
              {SYMBOLS.map(s => <option key={s} value={s}>{s}</option>)}
            </select>
            <select value={filters.tag} onChange={e => setFilters(f => ({ ...f, tag: e.target.value }))} style={{ ...inputStyle, width: 'auto' }}>
              <option value="">All Tags</option>
              {TAGS.map(t => <option key={t} value={t}>{t}</option>)}
            </select>
          </div>

          {/* Entries List */}
          {loading ? (
            <div style={{ textAlign: 'center', padding: 40, color: 'var(--text-secondary)' }}>Loading...</div>
          ) : entries.length === 0 ? (
            <div style={{ textAlign: 'center', padding: 40, color: 'var(--text-secondary)' }}>
              <p style={{ fontSize: 18, marginBottom: 8 }}>No journal entries yet</p>
              <p>Start logging your trades to track patterns and improve</p>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {entries.map(entry => (
                <div key={entry.id} style={{
                  background: 'var(--card-bg, #1e1e2e)', borderRadius: 10, padding: 16,
                  border: '1px solid var(--border-color, #333)',
                  borderLeft: `4px solid ${outcomeColor[entry.outcome] || 'var(--border-color)'}`,
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                    <div>
                      <div style={{ fontWeight: 600, color: 'var(--text-primary)', fontSize: 15 }}>
                        {entry.symbol} {entry.strike_price} {entry.option_type}
                        <span style={{ color: entry.direction === 'buy' ? 'var(--profit)' : 'var(--loss)', marginLeft: 8, fontSize: 12 }}>
                          {entry.direction.toUpperCase()}
                        </span>
                      </div>
                      {entry.title && <div style={{ color: 'var(--text-secondary)', fontSize: 13, marginTop: 2 }}>{entry.title}</div>}
                    </div>
                    <div style={{ textAlign: 'right' }}>
                      {entry.realized_pnl != null && (
                        <div style={{ fontWeight: 700, fontSize: 16, color: entry.realized_pnl >= 0 ? 'var(--profit)' : 'var(--loss)' }}>
                          {entry.realized_pnl >= 0 ? '+' : ''}{entry.realized_pnl.toLocaleString('en-IN', { style: 'currency', currency: 'INR' })}
                        </div>
                      )}
                      {entry.realized_pnl_percent != null && (
                        <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                          {entry.realized_pnl_percent >= 0 ? '+' : ''}{entry.realized_pnl_percent.toFixed(1)}%
                        </div>
                      )}
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: 16, marginTop: 8, fontSize: 12, color: 'var(--text-secondary)' }}>
                    <span>Entry: {'\u20B9'}{entry.entry_price}</span>
                    {entry.exit_price && <span>Exit: {'\u20B9'}{entry.exit_price}</span>}
                    <span>{entry.quantity}L x {entry.lot_size}</span>
                    <span>{new Date(entry.entry_date).toLocaleDateString('en-IN')}</span>
                  </div>
                  <div style={{ display: 'flex', gap: 6, marginTop: 8, flexWrap: 'wrap', alignItems: 'center' }}>
                    {entry.mood && <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 12, background: 'var(--surface-bg, #2a2a3a)', color: 'var(--text-secondary)' }}>{moodEmoji[entry.mood]} {entry.mood}</span>}
                    {entry.market_condition && <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 12, background: 'var(--surface-bg, #2a2a3a)', color: 'var(--text-secondary)' }}>{entry.market_condition.replace('_', ' ')}</span>}
                    {entry.tags && entry.tags.split(',').map(t => t.trim()).filter(Boolean).map(tag => (
                      <span key={tag} style={{ fontSize: 11, padding: '2px 8px', borderRadius: 12, background: 'rgba(68,138,255,0.15)', color: 'var(--accent-primary, #448aff)' }}>{tag}</span>
                    ))}
                    <div style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
                      <button onClick={() => handleEdit(entry)} style={{ fontSize: 11, padding: '2px 10px', borderRadius: 6, border: 'none', cursor: 'pointer', background: 'var(--surface-bg, #2a2a3a)', color: 'var(--text-secondary)' }}>Edit</button>
                      <button onClick={() => handleDelete(entry.id)} style={{ fontSize: 11, padding: '2px 10px', borderRadius: 6, border: 'none', cursor: 'pointer', background: 'rgba(255,82,82,0.15)', color: 'var(--loss, #ff5252)' }}>Delete</button>
                    </div>
                  </div>
                  {entry.notes && <div style={{ marginTop: 8, fontSize: 12, color: 'var(--text-secondary)', fontStyle: 'italic', padding: '8px 12px', background: 'var(--surface-bg, #2a2a3a)', borderRadius: 6 }}>{entry.notes}</div>}
                </div>
              ))}
            </div>
          )}
        </>
      )}

      {/* Stats Tab */}
      {activeTab === 'stats' && stats && (
        <div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))', gap: 12 }}>
            {[
              { label: 'Total Trades', value: stats.total_trades },
              { label: 'Win Rate', value: `${stats.win_rate}%`, color: stats.win_rate >= 50 ? 'var(--profit)' : 'var(--loss)' },
              { label: 'Total P&L', value: `\u20B9${stats.total_pnl?.toLocaleString('en-IN')}`, color: stats.total_pnl >= 0 ? 'var(--profit)' : 'var(--loss)' },
              { label: 'Avg P&L', value: `\u20B9${stats.average_pnl?.toLocaleString('en-IN')}` },
              { label: 'Best Trade', value: stats.best_trade ? `\u20B9${stats.best_trade.toLocaleString('en-IN')}` : '-', color: 'var(--profit)' },
              { label: 'Worst Trade', value: stats.worst_trade ? `\u20B9${stats.worst_trade.toLocaleString('en-IN')}` : '-', color: 'var(--loss)' },
              { label: 'Winning', value: stats.winning_trades, color: 'var(--profit)' },
              { label: 'Losing', value: stats.losing_trades, color: 'var(--loss)' },
              { label: 'Avg Hold', value: stats.average_holding_days ? `${stats.average_holding_days}d` : '-' },
              { label: 'Top Symbol', value: stats.most_traded_symbol || '-' },
            ].map(({ label, value, color }) => (
              <div key={label} style={{
                background: 'var(--card-bg, #1e1e2e)', borderRadius: 10, padding: 16,
                border: '1px solid var(--border-color, #333)', textAlign: 'center',
              }}>
                <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginBottom: 4 }}>{label}</div>
                <div style={{ fontSize: 20, fontWeight: 700, color: color || 'var(--text-primary)' }}>{value}</div>
              </div>
            ))}
          </div>

          {stats.most_used_tags?.length > 0 && (
            <div style={{ marginTop: 16 }}>
              <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 8 }}>Most Used Tags</div>
              <div style={{ display: 'flex', gap: 6 }}>
                {stats.most_used_tags.map(tag => (
                  <span key={tag} style={{ padding: '4px 12px', borderRadius: 12, background: 'rgba(68,138,255,0.15)', color: 'var(--accent-primary)', fontSize: 12 }}>{tag}</span>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

const labelStyle = { display: 'block', fontSize: 12, color: 'var(--text-secondary)', marginBottom: 4, fontWeight: 500 }
const inputStyle = {
  width: '100%', padding: '8px 12px', borderRadius: 6, fontSize: 13,
  border: '1px solid var(--border-color, #333)', color: 'var(--text-primary)',
  background: 'var(--surface-bg, #2a2a3a)', outline: 'none',
}

export default TradeJournal
