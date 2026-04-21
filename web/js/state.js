/**
 * state.js — Core game state classes and enums.
 */

/** Difficulty levels */
export const Difficulty = Object.freeze({
  EASY: 'easy',      // 4 simple bots, never adapt
  MEDIUM: 'medium',  // 2 simple + 2 strategic, adapt after 5 trades
  HARD: 'hard',      // 4 strategic bots, aggressive adaptation
});

/** Game phases */
export const Phase = Object.freeze({
  OPEN: 'open',
  REVEAL_1: 'reveal_1',
  REVEAL_2: 'reveal_2',
  REVEAL_3: 'reveal_3',
  SETTLE: 'settle',
});

export const PHASE_ORDER = [Phase.OPEN, Phase.REVEAL_1, Phase.REVEAL_2, Phase.REVEAL_3, Phase.SETTLE];

export const PHASE_LABELS = {
  [Phase.OPEN]:     'Phase 1 — Initial Trading (no central cards revealed)',
  [Phase.REVEAL_1]: 'Phase 2 — First central card revealed',
  [Phase.REVEAL_2]: 'Phase 3 — Second central card revealed',
  [Phase.REVEAL_3]: 'Phase 4 — Third central card revealed',
  [Phase.SETTLE]:   'Phase 5 — Settlement',
};

/** Short labels for the game UI header */
export const PHASE_SHORT_LABELS = {
  [Phase.OPEN]:     'Open Trading',
  [Phase.REVEAL_1]: 'Reveal 1',
  [Phase.REVEAL_2]: 'Reveal 2',
  [Phase.REVEAL_3]: 'Reveal 3',
  [Phase.SETTLE]:   'Settlement',
};

/**
 * A single executed transaction.
 */
export class Trade {
  constructor(buyer, seller, price, phase) {
    this.buyer = buyer;
    this.seller = seller;
    this.price = price;
    this.phase = phase;
  }

  /** Return the P&L this trade contributes for a given player. */
  pnlFor(playerId, finalTotal) {
    if (playerId === this.buyer) return finalTotal - this.price;
    if (playerId === this.seller) return this.price - finalTotal;
    return 0;
  }
}

/**
 * A two-way market: bid (buy price) and ask (sell price). Spread is always 2.
 */
export class Quote {
  constructor(bid, ask) {
    if (ask !== bid + 2) {
      throw new Error(`Spread must be exactly 2 (got bid=${bid} ask=${ask})`);
    }
    this.bid = bid;
    this.ask = ask;
  }

  toString() {
    return `${this.bid} – ${this.ask}`;
  }

  /** Create a quote centred on mid, rounding bid down. */
  static fromMid(mid) {
    const bid = Math.floor(mid) - 1;
    return new Quote(bid, bid + 2);
  }
}

/**
 * Complete mutable state for one round of MagCry.
 */
export class GameState {
  /**
   * @param {string[]} playerIds - index 0 is always the human
   * @param {Object} privateCards - {playerId: cardValue}
   * @param {number[]} centralCards - all 3 central cards (hidden until revealed)
   */
  constructor(playerIds, privateCards, centralCards) {
    this.playerIds = playerIds;
    this.privateCards = privateCards;
    this.centralCards = centralCards;
    this.revealedCentral = [];
    this.trades = [];
    this.markets = {};
    for (const pid of playerIds) {
      this.markets[pid] = null;
    }
    this.phase = Phase.OPEN;
    this.turn = 0;
  }

  /** Move to the next phase, revealing the next central card if applicable. */
  advancePhase() {
    const idx = PHASE_ORDER.indexOf(this.phase);
    if (idx + 1 < PHASE_ORDER.length) {
      this.phase = PHASE_ORDER[idx + 1];
    }

    // Reveal next central card when entering REVEAL phases
    const nRevealed = this.revealedCentral.length;
    if ([Phase.REVEAL_1, Phase.REVEAL_2, Phase.REVEAL_3].includes(this.phase)) {
      const revealIdx = PHASE_ORDER.indexOf(this.phase) - 1; // 0, 1, 2
      if (revealIdx < this.centralCards.length && revealIdx === nRevealed) {
        this.revealedCentral.push(this.centralCards[revealIdx]);
      }
    }

    this.turn = 0;
  }

  /** Sum of all 8 cards. */
  finalTotal() {
    const privateSum = Object.values(this.privateCards).reduce((a, b) => a + b, 0);
    const centralSum = this.centralCards.reduce((a, b) => a + b, 0);
    return privateSum + centralSum;
  }

  /** Cards visible to a specific player: own card + revealed central cards. */
  knownCardsFor(playerId) {
    return [this.privateCards[playerId], ...this.revealedCentral];
  }

  /** How many cards are still hidden from this player's perspective. */
  unknownCountFor(playerId) {
    return (this.playerIds.length - 1) + (this.centralCards.length - this.revealedCentral.length);
  }

  /** Return P&L per player. */
  scoreboard() {
    const T = this.finalTotal();
    const scores = {};
    for (const pid of this.playerIds) {
      scores[pid] = 0;
    }
    for (const trade of this.trades) {
      if (trade.buyer in scores) {
        scores[trade.buyer] += T - trade.price;
      }
      if (trade.seller in scores) {
        scores[trade.seller] += trade.price - T;
      }
    }
    return scores;
  }
}
