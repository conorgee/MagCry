package com.magcry.model

import java.util.UUID

// -- Enums --

enum class Difficulty(val value: Int, val label: String, val description: String) {
    EASY(1, "Easy", "4 simple bots, never adapt"),
    MEDIUM(2, "Medium", "2 simple + 2 strategic, adapt after ~5 trades"),
    HARD(3, "Hard", "4 strategic bots, bluff & adapt fast");
}

enum class Phase(val label: String) {
    OPEN("Open Trading"),
    REVEAL1("Reveal 1"),
    REVEAL2("Reveal 2"),
    REVEAL3("Reveal 3"),
    SETTLE("Settlement");
}

val PHASE_ORDER: List<Phase> = listOf(Phase.OPEN, Phase.REVEAL1, Phase.REVEAL2, Phase.REVEAL3, Phase.SETTLE)

// -- Quote --

/**
 * Two-way price with spread locked at exactly 2 (book rule).
 */
data class Quote(val bid: Int, val ask: Int) {
    init {
        require(ask == bid + 2) { "Quote spread must be exactly 2, got ${ask - bid}" }
    }

    companion object {
        /** Create a quote centered around a mid-price. bid = round(mid) - 1, ask = bid + 2 */
        fun fromMid(mid: Double): Quote {
            val bid = Math.round(mid).toInt() - 1
            return Quote(bid = bid, ask = bid + 2)
        }
    }
}

// -- Trade --

data class Trade(
    val id: String = UUID.randomUUID().toString(),
    val buyer: String,
    val seller: String,
    val price: Int,
    val phase: Phase
) {
    /** P&L for a given player at settlement. */
    fun pnlFor(playerID: String, finalTotal: Int): Int {
        return when (playerID) {
            buyer -> finalTotal - price
            seller -> price - finalTotal
            else -> 0
        }
    }

    /** Equality ignores ID (matches Swift behavior). */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Trade) return false
        return buyer == other.buyer &&
                seller == other.seller &&
                price == other.price &&
                phase == other.phase
    }

    override fun hashCode(): Int {
        var result = buyer.hashCode()
        result = 31 * result + seller.hashCode()
        result = 31 * result + price
        result = 31 * result + phase.hashCode()
        return result
    }
}

// -- GameState --

/** Mutable game state for one round. */
class GameState(
    val playerIDs: List<String>,
    val privateCards: Map<String, Int>,
    val centralCards: List<Int>,
    var phase: Phase = Phase.OPEN
) {
    var revealedCentral: MutableList<Int> = mutableListOf()
    val trades: MutableList<Trade> = mutableListOf()

    /** Sum of all 8 in-play cards (5 private + 3 central). */
    fun finalTotal(): Int {
        return privateCards.values.sum() + centralCards.sum()
    }

    /** Cards known to a specific player: their private card + revealed central cards. */
    fun knownCards(playerID: String): List<Int> {
        val cards = mutableListOf<Int>()
        privateCards[playerID]?.let { cards.add(it) }
        cards.addAll(revealedCentral)
        return cards
    }

    /** How many cards this player can't see: other players' cards + hidden central. */
    fun unknownCount(playerID: String): Int {
        val otherPlayers = playerIDs.size - 1
        val hiddenCentral = centralCards.size - revealedCentral.size
        return otherPlayers + hiddenCentral
    }

    /** Compute P&L per player from all trades. */
    fun scoreboard(): Map<String, Int> {
        val total = finalTotal()
        val scores = playerIDs.associateWith { 0 }.toMutableMap()
        for (trade in trades) {
            scores[trade.buyer] = (scores[trade.buyer] ?: 0) + (total - trade.price)
            scores[trade.seller] = (scores[trade.seller] ?: 0) + (trade.price - total)
        }
        return scores
    }
}
