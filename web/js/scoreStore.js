/**
 * scoreStore.js — Persistent stats storage backed by localStorage.
 *
 * Mirrors Swift's ScoreStore: tracks best P&L, games played/won,
 * streaks, and totals per difficulty. Uses localStorage with key
 * "magcry_stats_v1".
 */

const STORAGE_KEY = 'magcry_stats_v1';

function emptyStats() {
  return {
    bestPnL: null,
    gamesPlayed: 0,
    gamesWon: 0,
    totalPnL: 0,
    totalTrades: 0,
    currentStreak: 0,
    bestStreak: 0,
  };
}

export class ScoreStore {
  constructor() {
    this.stats = {};       // keyed by difficulty string: "easy", "medium", "hard"
    this.isNewBest = false;
    this._load();
  }

  /**
   * Record the result of a completed game.
   * @param {string} difficulty - "easy", "medium", or "hard"
   * @param {number} pnl - Player's final P&L (rounded to integer)
   * @param {number} rank - Leaderboard rank (1 = best)
   * @param {number} tradeCount - Number of trades the player made
   */
  record(difficulty, pnl, rank, tradeCount) {
    const intPnl = Math.round(pnl);
    const s = this.stats[difficulty] || emptyStats();

    s.gamesPlayed += 1;
    s.totalPnL += intPnl;
    s.totalTrades += tradeCount;

    // Win tracking (1st place = win)
    const won = rank === 1;
    if (won) {
      s.gamesWon += 1;
      s.currentStreak += 1;
      s.bestStreak = Math.max(s.bestStreak, s.currentStreak);
    } else {
      s.currentStreak = 0;
    }

    // Best P&L tracking
    if (s.bestPnL !== null) {
      if (intPnl > s.bestPnL) {
        s.bestPnL = intPnl;
        this.isNewBest = true;
      } else {
        this.isNewBest = false;
      }
    } else {
      // First game at this difficulty
      s.bestPnL = intPnl;
      this.isNewBest = true;
    }

    this.stats[difficulty] = s;
    this._save();
  }

  /**
   * Get stats for a specific difficulty.
   * @param {string} difficulty
   * @returns {object} Stats object with computed winRate and averagePnL
   */
  statsFor(difficulty) {
    const s = this.stats[difficulty] || emptyStats();
    return {
      ...s,
      winRate: s.gamesPlayed > 0 ? s.gamesWon / s.gamesPlayed : 0,
      averagePnL: s.gamesPlayed > 0 ? s.totalPnL / s.gamesPlayed : 0,
    };
  }

  /** Reset all stats across all difficulties. */
  resetAll() {
    this.stats = {};
    this.isNewBest = false;
    try { localStorage.removeItem(STORAGE_KEY); } catch (_) { /* ignore */ }
  }

  // ── Persistence ─────────────────────────────────────────────────────────

  _load() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) this.stats = JSON.parse(raw);
    } catch (_) { /* ignore */ }
  }

  _save() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.stats));
    } catch (_) { /* ignore */ }
  }
}
