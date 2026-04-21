/**
 * simpleBot.js — EV-based bot with optional adaptation.
 *
 * Difficulty modes:
 *   Easy   (adaptationThreshold=null)  — pure EV calculator, never adjusts.
 *   Medium (adaptationThreshold=5)     — tracks human trading direction and
 *           shifts quotes against them after 5+ same-direction trades.
 */

import { Bot } from './bot.js';
import { expectedTotal } from './deck.js';
import { Quote, Phase } from './state.js';
import { SeededRandom } from './deck.js';

export const EDGE_THRESHOLD = 1;       // Min free edge (pts) required to trade
const NOISE_RANGE = 1;                 // Max random noise added to mid price (+/-)
const ADAPT_SHIFT_PER_TRADE = 2;       // Pts shift per trade beyond adaptation threshold
const MAX_ADAPT_SHIFT = 10;            // Cap total shift

export class SimpleBot extends Bot {
  /**
   * @param {string} playerId
   * @param {SeededRandom|null} rng
   * @param {number|null} adaptationThreshold - null = never adapt (Easy), int = adapt after N trades
   */
  constructor(playerId, rng = null, adaptationThreshold = null) {
    super(playerId);
    this.rng = rng || new SeededRandom(Date.now());
    this.adaptationThreshold = adaptationThreshold;
  }

  // ── Internal helpers ──────────────────────────────────────────────────

  /** Compute the EV of the final total from this bot's perspective. */
  _expectedMid(state) {
    const known = state.knownCardsFor(this.playerId);
    const unknown = state.unknownCountFor(this.playerId);
    return expectedTotal(known, unknown);
  }

  /**
   * How much to shift the quote against requester's trading direction.
   * Returns 0 if no adaptation (Easy mode or below threshold).
   */
  _adaptationShift(requester) {
    if (this.adaptationThreshold === null || requester === null) {
      return 0;
    }

    const streak = this.opponentSameDirectionStreak(requester);
    if (streak < this.adaptationThreshold) {
      return 0;
    }

    const direction = this.opponentDirection(requester);
    const excess = streak - this.adaptationThreshold + 1;
    const shiftAmount = Math.min(excess * ADAPT_SHIFT_PER_TRADE, MAX_ADAPT_SHIFT);

    if (direction === 'bearish') return -shiftAmount;
    if (direction === 'bullish') return shiftAmount;
    return 0;
  }

  // ── Bot interface ─────────────────────────────────────────────────────

  getQuote(state, requester = null) {
    const mid = this._expectedMid(state);
    const noise = this.rng.randint(-NOISE_RANGE, NOISE_RANGE);
    const shift = this._adaptationShift(requester);
    const bid = Math.round(mid) + noise + shift - 1;
    return new Quote(bid, bid + 2);
  }

  decideOnQuote(state, quoter, quote) {
    const ev = this._expectedMid(state);

    const buyEdge = ev - quote.ask;    // positive if ask is cheap
    const sellEdge = quote.bid - ev;   // positive if bid is expensive

    if (buyEdge > EDGE_THRESHOLD && buyEdge >= sellEdge) {
      return 'buy';
    }
    if (sellEdge > EDGE_THRESHOLD) {
      return 'sell';
    }
    return null;
  }

  decideAction(state) {
    if (state.phase === Phase.SETTLE) return null;
    const others = state.playerIds.filter(p => p !== this.playerId);
    if (others.length === 0) return null;
    return this.rng.choice(others);
  }
}
