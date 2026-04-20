"""
trading.py — Quote validation, trade execution, and market management.
"""

from __future__ import annotations

from typing import Optional, Tuple

from game.state import GameState, Quote, Trade, Phase


class TradingError(Exception):
    """Raised when an invalid trade or quote is attempted."""


def validate_quote(bid: int, ask: int) -> Quote:
    """
    Validate and return a Quote. Spread must be exactly 2.
    Raises TradingError on invalid input.
    """
    if ask != bid + 2:
        raise TradingError(f"Spread must be exactly 2 (bid={bid}, ask={ask}). Try ask = bid + 2.")
    return Quote(bid=bid, ask=ask)


def set_market(state: GameState, player_id: str, bid: int, ask: int) -> Quote:
    """
    Set a player's two-way market. Validates spread and records it.
    Returns the Quote.
    """
    if player_id not in state.player_ids:
        raise TradingError(f"Unknown player: {player_id}")
    if state.phase == Phase.SETTLE:
        raise TradingError("Cannot quote during settlement.")
    quote = validate_quote(bid, ask)
    state.markets[player_id] = quote
    return quote


def execute_trade(
    state: GameState,
    aggressor: str,
    market_maker: str,
    direction: str,  # "buy" or "sell"
) -> Trade:
    """
    Execute a trade where `aggressor` hits `market_maker`'s quote.

    direction="buy"  → aggressor buys at market_maker's ask price
    direction="sell" → aggressor sells at market_maker's bid price

    Records the trade and returns the Trade object.
    """
    if aggressor == market_maker:
        raise TradingError("Cannot trade with yourself.")
    if state.phase == Phase.SETTLE:
        raise TradingError("Cannot trade during settlement.")

    quote = state.markets.get(market_maker)
    if quote is None:
        raise TradingError(f"{market_maker} has not posted a market yet.")

    if direction == "buy":
        price = quote.ask
        trade = Trade(buyer=aggressor, seller=market_maker, price=price, phase=state.phase)
    elif direction == "sell":
        price = quote.bid
        trade = Trade(buyer=market_maker, seller=aggressor, price=price, phase=state.phase)
    else:
        raise TradingError(f"Direction must be 'buy' or 'sell', got '{direction}'.")

    state.trades.append(trade)
    return trade


def get_quote(state: GameState, player_id: str) -> Optional[Quote]:
    """Return the current market for a player, or None if not yet posted."""
    return state.markets.get(player_id)


def trade_summary(state: GameState, player_id: str) -> Tuple[int, int, float, float]:
    """
    Return (n_buys, n_sells, avg_buy_price, avg_sell_price) for a player.
    Averages are 0.0 if no trades of that type.
    """
    buys = [t.price for t in state.trades if t.buyer == player_id]
    sells = [t.price for t in state.trades if t.seller == player_id]
    avg_buy = sum(buys) / len(buys) if buys else 0.0
    avg_sell = sum(sells) / len(sells) if sells else 0.0
    return len(buys), len(sells), avg_buy, avg_sell


# ── V2: Direct trade execution (no standing markets required) ─────────────────

def execute_trade_direct(
    state: GameState,
    buyer: str,
    seller: str,
    price: int,
) -> Trade:
    """
    Execute a trade directly at a specific price, without looking up
    a standing market quote. Used by the V2 ask-for-a-price flow.

    buyer:  player who buys (gains if final_total > price)
    seller: player who sells (gains if final_total < price)
    price:  agreed transaction price
    """
    if buyer == seller:
        raise TradingError("Cannot trade with yourself.")
    if state.phase == Phase.SETTLE:
        raise TradingError("Cannot trade during settlement.")
    if buyer not in state.player_ids:
        raise TradingError(f"Unknown player: {buyer}")
    if seller not in state.player_ids:
        raise TradingError(f"Unknown player: {seller}")

    trade = Trade(buyer=buyer, seller=seller, price=price, phase=state.phase)
    state.trades.append(trade)
    return trade
