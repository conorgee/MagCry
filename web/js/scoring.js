/**
 * scoring.js — Settlement calculation and leaderboard formatting.
 */

/**
 * Calculate final P&L for every player.
 * For each trade: buyer scores (finalTotal - price), seller scores (price - finalTotal).
 * Returns { playerId: totalPnl }
 */
export function settle(state) {
  const T = state.finalTotal();
  const scores = {};
  for (const pid of state.playerIds) {
    scores[pid] = 0;
  }
  for (const trade of state.trades) {
    scores[trade.buyer] += T - trade.price;
    scores[trade.seller] += trade.price - T;
  }
  return scores;
}

/**
 * Return a sorted array of [playerId, score] from highest to lowest.
 */
export function leaderboard(scores) {
  return Object.entries(scores).sort((a, b) => b[1] - a[1]);
}

/**
 * Return an array of { trade, pnl } for all trades involving playerId,
 * evaluated at the final total.
 */
export function tradeBreakdown(state, playerId) {
  const T = state.finalTotal();
  const result = [];
  for (const trade of state.trades) {
    if (trade.buyer === playerId || trade.seller === playerId) {
      const pnl = trade.pnlFor(playerId, T);
      result.push({ trade, pnl });
    }
  }
  return result;
}
