package com.magcry.model

// Constants
private const val EDGE_THRESHOLD = 1
private const val NOISE_RANGE = 1
private const val ADAPT_SHIFT_PER_TRADE = 2
private const val MAX_ADAPT_SHIFT = 10

/**
 * EV-based bot for Easy and Medium difficulties.
 * Quotes around its expected value with small noise.
 * Adaptation: if enabled, shifts quotes against a player's trading direction.
 */
class SimpleBot(
    override val playerID: String,
    private val rng: GameRNG,
    private val adaptationThreshold: Int? = null // null = Easy (never adapt), Int = Medium
) : Bot {

    override val tracker = BotTracker(rng)

    // -- Private --

    private fun expectedMid(state: GameState): Double {
        val known = state.knownCards(playerID)
        val unknown = state.unknownCount(playerID)
        return Deck.expectedTotal(known, unknown)
    }

    /**
     * Compute quote shift based on requester's trading pattern.
     * Bearish requester -> shift DOWN (worse sells for them).
     * Bullish requester -> shift UP (worse buys for them).
     */
    private fun adaptationShift(requester: String?): Int {
        val threshold = adaptationThreshold ?: return 0
        val req = requester ?: return 0

        val streak = tracker.opponentSameDirectionStreak(req)
        if (streak < threshold) return 0

        val direction = tracker.opponentDirection(req)
        if (direction == "neutral") return 0

        val excess = streak - threshold + 1
        val shift = minOf(excess * ADAPT_SHIFT_PER_TRADE, MAX_ADAPT_SHIFT)
        return if (direction == "bearish") -shift else shift
    }

    // -- Bot Protocol --

    override fun getQuote(state: GameState, requester: String?): Quote {
        val ev = expectedMid(state)
        val noise = rng.nextInt(-NOISE_RANGE..NOISE_RANGE).toDouble()
        val adapt = adaptationShift(requester).toDouble()
        val direct = tracker.directShiftFor(requester ?: "").toDouble()
        val mid = ev + noise + adapt + direct
        val bid = Math.round(mid).toInt() - 1
        return Quote(bid = bid, ask = bid + 2)
    }

    override fun decideOnQuote(state: GameState, quoter: String, quote: Quote): String? {
        val ev = expectedMid(state)
        val buyEdge = ev - quote.ask.toDouble()
        val sellEdge = quote.bid.toDouble() - ev

        // If both edges exist, prefer the bigger one
        if (buyEdge > EDGE_THRESHOLD.toDouble() && sellEdge > EDGE_THRESHOLD.toDouble()) {
            return if (buyEdge >= sellEdge) "buy" else "sell"
        }
        if (buyEdge > EDGE_THRESHOLD.toDouble()) return "buy"
        if (sellEdge > EDGE_THRESHOLD.toDouble()) return "sell"
        return null
    }

    override fun decideAction(state: GameState): String? {
        if (state.phase == Phase.SETTLE) return null
        val others = state.playerIDs.filter { it != playerID }
        return rng.randomElement(others)
    }
}
