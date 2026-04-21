/**
 * bot.js — Abstract base class for all bot players.
 *
 * Bots track opponent trading history and adapt their behaviour based on
 * difficulty level. This is the primary constraint in the game — smart
 * opponents ARE the position limit (book-accurate).
 */

/**
 * Abstract bot base class.
 * Subclasses must implement: getQuote(), decideOnQuote(), decideAction()
 */
export class Bot {
  constructor(playerId) {
    this.playerId = playerId;
    // Track every player's directional trades: {playerId: ["buy","sell",...]}
    this._opponentHistory = {};
  }

  // ── Trade tracking ──────────────────────────────────────────────────────

  /**
   * Record that a player bought or sold. Call after every trade.
   * We track the direction from the perspective of playerOfInterest.
   */
  recordTrade(tradeBuyer, tradeSeller, playerOfInterest) {
    if (!this._opponentHistory[playerOfInterest]) {
      this._opponentHistory[playerOfInterest] = [];
    }
    if (playerOfInterest === tradeBuyer) {
      this._opponentHistory[playerOfInterest].push('buy');
    } else if (playerOfInterest === tradeSeller) {
      this._opponentHistory[playerOfInterest].push('sell');
    }
  }

  /**
   * Infer a player's directional bias from their trade history.
   * Returns "bullish", "bearish", or "neutral".
   */
  opponentDirection(playerId) {
    const history = this._opponentHistory[playerId] || [];
    if (history.length < 2) return 'neutral';
    const buys = history.filter(d => d === 'buy').length;
    const sells = history.filter(d => d === 'sell').length;
    if (sells >= buys + 2) return 'bearish';
    if (buys >= sells + 2) return 'bullish';
    return 'neutral';
  }

  /** Total number of trades recorded for a player. */
  opponentTradeCount(playerId) {
    return (this._opponentHistory[playerId] || []).length;
  }

  /**
   * How many consecutive same-direction trades has this player made?
   * Returns 0 if no history.
   */
  opponentSameDirectionStreak(playerId) {
    const history = this._opponentHistory[playerId] || [];
    if (history.length === 0) return 0;
    const last = history[history.length - 1];
    let streak = 0;
    for (let i = history.length - 1; i >= 0; i--) {
      if (history[i] === last) {
        streak++;
      } else {
        break;
      }
    }
    return streak;
  }

  // ── Bot interface (must implement) ──────────────────────────────────────

  /**
   * Return the two-way price this bot quotes when asked.
   * @param {GameState} state
   * @param {string|null} requester
   * @returns {Quote}
   */
  getQuote(state, requester = null) {
    throw new Error('getQuote() must be implemented by subclass');
  }

  /**
   * When we asked another player for a price and they gave us a quote,
   * decide what to do.
   * @returns {"buy"|"sell"|null}
   */
  decideOnQuote(state, quoter, quote) {
    throw new Error('decideOnQuote() must be implemented by subclass');
  }

  /**
   * Proactively decide whether to ask another player for a price.
   * @returns {string|null} target player id, or null
   */
  decideAction(state) {
    throw new Error('decideAction() must be implemented by subclass');
  }

  /**
   * Hook called when the phase advances. Default: no-op.
   */
  onPhaseChange(state) {
    // no-op — subclasses may override
  }
}
