/**
 * deck.js — Card deck definition, shuffling, and dealing logic.
 *
 * Deck: [-10, 1..15, 20]  (17 cards total, average = 130/17 ≈ 7.65)
 * Each round: 5 private cards (one per player) + 3 central cards = 8 in play.
 */

export const DECK = [-10, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 20];

export const DECK_SIZE = DECK.length;        // 17
export const DECK_SUM = DECK.reduce((a, b) => a + b, 0);  // 130
export const DECK_MEAN = DECK_SUM / DECK_SIZE;  // ≈ 7.647

export const N_PLAYERS = 5;
export const N_CENTRAL = 3;
export const N_IN_PLAY = N_PLAYERS + N_CENTRAL;  // 8

/**
 * Seedable pseudo-random number generator (mulberry32).
 * Provides the same interface as Python's random.Random for the methods we need.
 */
export class SeededRandom {
  constructor(seed) {
    this._state = seed >>> 0;
  }

  /** Returns a float in [0, 1) */
  random() {
    this._state = (this._state + 0x6D2B79F5) | 0;
    let t = Math.imul(this._state ^ (this._state >>> 15), 1 | this._state);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  }

  /** Returns an integer in [min, max] inclusive */
  randint(min, max) {
    return min + Math.floor(this.random() * (max - min + 1));
  }

  /** Returns a float in [min, max) */
  uniform(min, max) {
    return min + this.random() * (max - min);
  }

  /** Shuffles array in place (Fisher-Yates) */
  shuffle(arr) {
    for (let i = arr.length - 1; i > 0; i--) {
      const j = Math.floor(this.random() * (i + 1));
      [arr[i], arr[j]] = [arr[j], arr[i]];
    }
    return arr;
  }

  /** Returns a random element from array */
  choice(arr) {
    return arr[Math.floor(this.random() * arr.length)];
  }

  /** Returns k unique random elements from array */
  sample(arr, k) {
    const copy = [...arr];
    const result = [];
    for (let i = 0; i < Math.min(k, copy.length); i++) {
      const j = Math.floor(this.random() * copy.length);
      result.push(copy[j]);
      copy.splice(j, 1);
    }
    return result;
  }
}

/**
 * Expected value of a single unknown card, given we already know
 * some cards have been removed from the deck.
 */
export function deckMeanExcluding(knownCards) {
  const remaining = [...DECK];
  for (const c of knownCards) {
    const idx = remaining.indexOf(c);
    if (idx !== -1) {
      remaining.splice(idx, 1);
    }
  }
  if (remaining.length === 0) return 0.0;
  return remaining.reduce((a, b) => a + b, 0) / remaining.length;
}

/**
 * Expected final total given the cards we know and the number
 * of cards still hidden.
 */
export function expectedTotal(knownCards, unknownCount) {
  const knownSum = knownCards.reduce((a, b) => a + b, 0);
  const evPerUnknown = deckMeanExcluding(knownCards);
  return knownSum + unknownCount * evPerUnknown;
}

/**
 * Shuffle the deck and deal one private card to each player plus
 * three central cards face-down.
 *
 * Returns: { privateCards: {playerId: cardValue}, centralCards: [int, int, int] }
 */
export function deal(playerIds, rng) {
  if (!rng) rng = new SeededRandom(Date.now());

  if (playerIds.length !== N_PLAYERS) {
    throw new Error(`Expected ${N_PLAYERS} players, got ${playerIds.length}`);
  }

  const deck = [...DECK];
  rng.shuffle(deck);

  const privateCards = {};
  for (let i = 0; i < playerIds.length; i++) {
    privateCards[playerIds[i]] = deck[i];
  }

  const centralCards = deck.slice(N_PLAYERS, N_PLAYERS + N_CENTRAL);

  return { privateCards, centralCards };
}
