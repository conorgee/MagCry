/**
 * strategicBot.js — Strategic bluffing bot with aggressive adaptation.
 *
 * Difficulty: Hard (Citi Final level)
 *
 * Behaviour:
 *   - Calculates true EV but BLUFFS when quoting
 *   - High card → quotes LOW (suppress market, then buy cheap)
 *   - Low card  → quotes HIGH (inflate market, then sell expensive)
 *   - Bluff offset is large early, converges to near-zero after 2+ reveals
 *   - Aggressively adapts after 2-3 same-direction trades
 */

import { Bot } from './bot.js';
import { expectedTotal, DECK_MEAN, SeededRandom } from './deck.js';
import { Quote, Phase } from './state.js';

// Bluff offsets (how far to shift quoted mid from true EV)
export const BLUFF_OFFSET_EARLY = 8;   // Phase.OPEN — maximum deception
export const BLUFF_OFFSET_MID = 4;     // After first reveal
export const BLUFF_OFFSET_LATE = 1;    // After second+ reveal — almost honest

export const EDGE_THRESHOLD = 1;       // Min edge to pull the trigger
export const MAX_TRADES_PER_TURN = 3;  // Don't over-expose on a single turn

// Adaptation: shift 3 pts per trade beyond threshold, cap at 15
export const ADAPTATION_THRESHOLD = 2;
const ADAPT_SHIFT_PER_TRADE = 3;
const MAX_ADAPT_SHIFT = 15;

export class StrategicBot extends Bot {
  constructor(playerId, rng = null) {
    super(playerId);
    this.rng = rng || new SeededRandom(Date.now());
    this._phaseTradeCount = 0;
    this._lastPhase = null;
  }

  // ── Internal helpers ──────────────────────────────────────────────────

  /** True expected total — never shown directly when bluffing. */
  _trueEv(state) {
    const known = state.knownCardsFor(this.playerId);
    const unknown = state.unknownCountFor(this.playerId);
    return expectedTotal(known, unknown);
  }

  _myCard(state) {
    return state.privateCards[this.playerId];
  }

  /** True if our card is above deck mean — we want to buy. */
  _isHighCard(state) {
    return this._myCard(state) > DECK_MEAN;
  }

  /** Return current bluff offset magnitude based on phase. */
  _bluffOffset(state) {
    const nRevealed = state.revealedCentral.length;
    if (nRevealed === 0) return BLUFF_OFFSET_EARLY;
    if (nRevealed === 1) return BLUFF_OFFSET_MID;
    return BLUFF_OFFSET_LATE;
  }

  /**
   * The mid price we will QUOTE (not necessarily our true belief).
   * High card → quote low (to suppress market, then buy).
   * Low card  → quote high (to inflate market, then sell).
   */
  _bluffMid(state) {
    const trueEv = this._trueEv(state);
    const offset = this._bluffOffset(state);
    if (this._isHighCard(state)) {
      return trueEv - offset;
    } else {
      return trueEv + offset;
    }
  }

  /**
   * Aggressive adaptation: shift quotes against opponent's direction.
   * Kicks in after just 2-3 same-direction trades.
   */
  _adaptationShift(requester) {
    if (requester === null) return 0;

    const streak = this.opponentSameDirectionStreak(requester);
    if (streak < ADAPTATION_THRESHOLD) return 0;

    const direction = this.opponentDirection(requester);
    const excess = streak - ADAPTATION_THRESHOLD + 1;
    const shiftAmount = Math.min(excess * ADAPT_SHIFT_PER_TRADE, MAX_ADAPT_SHIFT);

    if (direction === 'bearish') return -shiftAmount;
    if (direction === 'bullish') return shiftAmount;
    return 0;
  }

  /**
   * Infer what card an opponent likely holds from their trade direction.
   */
  _inferCardFromDirection(playerId) {
    const direction = this.opponentDirection(playerId);
    if (direction === 'bullish') return 14.0;
    if (direction === 'bearish') return -2.0;
    return DECK_MEAN;
  }

  // ── Bot interface ─────────────────────────────────────────────────────

  getQuote(state, requester = null) {
    const mid = this._bluffMid(state);
    const noise = this.rng.uniform(-0.5, 0.5);
    const shift = this._adaptationShift(requester);
    const bid = Math.round(mid + noise) + shift - 1;
    return new Quote(bid, bid + 2);
  }

  decideOnQuote(state, quoter, quote) {
    const trueEv = this._trueEv(state);

    if (this._isHighCard(state)) {
      // We want to buy — is their ask cheap enough?
      const buyEdge = trueEv - quote.ask;
      if (buyEdge > EDGE_THRESHOLD) return 'buy';
    } else {
      // We want to sell — is their bid expensive enough?
      const sellEdge = quote.bid - trueEv;
      if (sellEdge > EDGE_THRESHOLD) return 'sell';
    }

    // Also opportunistically take big edges either direction
    const buyEdge = trueEv - quote.ask;
    const sellEdge = quote.bid - trueEv;
    if (buyEdge > EDGE_THRESHOLD * 3) return 'buy';
    if (sellEdge > EDGE_THRESHOLD * 3) return 'sell';

    return null;
  }

  decideAction(state) {
    if (state.phase === Phase.SETTLE) return null;

    // Reset trade count tracker on new phase
    if (this._lastPhase !== state.phase) {
      this._phaseTradeCount = 0;
      this._lastPhase = state.phase;
    }

    if (this._phaseTradeCount >= MAX_TRADES_PER_TURN) return null;

    const others = state.playerIds.filter(p => p !== this.playerId);
    if (others.length === 0) return null;

    // Prefer targets with opposite direction (more likely to give us edge)
    const highCard = this._isHighCard(state);
    const preferred = [];
    const neutral = [];
    for (const pid of others) {
      const d = this.opponentDirection(pid);
      if (highCard && d === 'bearish') {
        preferred.push(pid);
      } else if (!highCard && d === 'bullish') {
        preferred.push(pid);
      } else if (d === 'neutral') {
        neutral.push(pid);
      }
    }

    const pool = preferred.length > 0 ? preferred : (neutral.length > 0 ? neutral : others);
    const target = this.rng.choice(pool);

    this._phaseTradeCount++;
    return target;
  }

  onPhaseChange(state) {
    this._phaseTradeCount = 0;
    this._lastPhase = state.phase;
  }
}
