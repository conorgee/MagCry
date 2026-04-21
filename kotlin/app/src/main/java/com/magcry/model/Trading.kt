package com.magcry.model

// -- Errors --

sealed class TradingException(message: String) : Exception(message) {
    class InvalidSpread(spread: Int) : TradingException("Spread must be exactly 2, got $spread")
    class UnknownPlayer(id: String) : TradingException("Unknown player: $id")
    class SelfTrade : TradingException("Cannot trade with yourself")
    class SettlePhase : TradingException("Cannot trade during settlement")
}

// -- Functions --

/** Validate that a bid/ask pair has spread of exactly 2. */
fun validateQuote(bid: Int, ask: Int): Quote {
    val spread = ask - bid
    if (spread != 2) throw TradingException.InvalidSpread(spread)
    return Quote(bid = bid, ask = ask)
}

/** Execute a trade directly at a specified price (V2 mechanic). */
fun executeTradeDirectly(
    state: GameState,
    buyer: String,
    seller: String,
    price: Int
): Trade {
    if (buyer == seller) throw TradingException.SelfTrade()
    if (state.phase == Phase.SETTLE) throw TradingException.SettlePhase()
    if (buyer !in state.playerIDs) throw TradingException.UnknownPlayer(buyer)
    if (seller !in state.playerIDs) throw TradingException.UnknownPlayer(seller)

    val trade = Trade(buyer = buyer, seller = seller, price = price, phase = state.phase)
    state.trades.add(trade)
    return trade
}
