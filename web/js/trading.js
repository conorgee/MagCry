/**
 * trading.js — Quote validation, trade execution, and market management.
 */

import { Quote, Trade, Phase } from './state.js';

export class TradingError extends Error {
  constructor(message) {
    super(message);
    this.name = 'TradingError';
  }
}

/**
 * Validate and return a Quote. Spread must be exactly 2.
 */
export function validateQuote(bid, ask) {
  if (ask !== bid + 2) {
    throw new TradingError(`Spread must be exactly 2 (bid=${bid}, ask=${ask}). Try ask = bid + 2.`);
  }
  return new Quote(bid, ask);
}

/**
 * Set a player's two-way market. Validates spread and records it.
 */
export function setMarket(state, playerId, bid, ask) {
  if (!state.playerIds.includes(playerId)) {
    throw new TradingError(`Unknown player: ${playerId}`);
  }
  if (state.phase === Phase.SETTLE) {
    throw new TradingError('Cannot quote during settlement.');
  }
  const quote = validateQuote(bid, ask);
  state.markets[playerId] = quote;
  return quote;
}

/**
 * Execute a trade where aggressor hits market_maker's quote.
 */
export function executeTrade(state, aggressor, marketMaker, direction) {
  if (aggressor === marketMaker) {
    throw new TradingError('Cannot trade with yourself.');
  }
  if (state.phase === Phase.SETTLE) {
    throw new TradingError('Cannot trade during settlement.');
  }

  const quote = state.markets[marketMaker];
  if (!quote) {
    throw new TradingError(`${marketMaker} has not posted a market yet.`);
  }

  let trade;
  if (direction === 'buy') {
    trade = new Trade(aggressor, marketMaker, quote.ask, state.phase);
  } else if (direction === 'sell') {
    trade = new Trade(marketMaker, aggressor, quote.bid, state.phase);
  } else {
    throw new TradingError(`Direction must be 'buy' or 'sell', got '${direction}'.`);
  }

  state.trades.push(trade);
  return trade;
}

/**
 * Return the current market for a player, or null if not yet posted.
 */
export function getQuote(state, playerId) {
  return state.markets[playerId] || null;
}

/**
 * Return { nBuys, nSells, avgBuy, avgSell } for a player.
 */
export function tradeSummary(state, playerId) {
  const buys = state.trades.filter(t => t.buyer === playerId).map(t => t.price);
  const sells = state.trades.filter(t => t.seller === playerId).map(t => t.price);
  const avgBuy = buys.length > 0 ? buys.reduce((a, b) => a + b, 0) / buys.length : 0.0;
  const avgSell = sells.length > 0 ? sells.reduce((a, b) => a + b, 0) / sells.length : 0.0;
  return { nBuys: buys.length, nSells: sells.length, avgBuy, avgSell };
}

/**
 * Execute a trade directly at a specific price, without looking up
 * a standing market quote. Used by the V2 ask-for-a-price flow.
 */
export function executeTradeDirect(state, buyer, seller, price) {
  if (buyer === seller) {
    throw new TradingError('Cannot trade with yourself.');
  }
  if (state.phase === Phase.SETTLE) {
    throw new TradingError('Cannot trade during settlement.');
  }
  if (!state.playerIds.includes(buyer)) {
    throw new TradingError(`Unknown player: ${buyer}`);
  }
  if (!state.playerIds.includes(seller)) {
    throw new TradingError(`Unknown player: ${seller}`);
  }

  const trade = new Trade(buyer, seller, price, state.phase);
  state.trades.push(trade);
  return trade;
}
