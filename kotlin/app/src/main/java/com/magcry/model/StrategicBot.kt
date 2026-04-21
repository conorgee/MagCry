package com.magcry.model

// Constants
private const val BLUFF_OFFSET_EARLY = 8   // OPEN phase — big bluffs
private const val BLUFF_OFFSET_MID = 4     // After 1 reveal
private const val BLUFF_OFFSET_LATE = 1    // After 2+ reveals — nearly honest
private const val STRAT_EDGE_THRESHOLD = 1
private const val MAX_TRADES_PER_TURN = 3
private const val STRAT_ADAPTATION_THRESHOLD = 2  // Much faster than SimpleBot's 5
private const val STRAT_ADAPT_SHIFT_PER_TRADE = 3 // Steeper per-trade shift
private const val STRAT_MAX_ADAPT_SHIFT = 15      // Higher cap

/**
 * Bluffing bot for Hard difficulty (and 2 of 4 in Medium).
 * - High card -> quotes LOW to suppress market, then buys cheap.
 * - Low card  -> quotes HIGH to inflate market, then sells expensive.
 * - Bluffs converge to honest as cards are revealed (8 -> 4 -> 1).
 * - Adapts aggressively after just 2 same-direction trades.
 */
class StrategicBot(
    override val playerID: String,
    private val rng: GameRNG
) : Bot {

    override val tracker = BotTracker(rng)
    private var phaseTradeCount = 0
    private var lastPhase: Phase? = null

    // -- Private --

    /** True expected value (what we actually believe, not what we quote). */
    private fun trueEV(state: GameState): Double {
        val known = state.knownCards(playerID)
        val unknown = state.unknownCount(playerID)
        return Deck.expectedTotal(known, unknown)
    }

    /** Bluffed mid-price for quoting. Opposite of our actual belief. */
    private fun bluffMid(state: GameState): Double {
        val ev = trueEV(state)
        val offset = when (state.revealedCentral.size) {
            0 -> BLUFF_OFFSET_EARLY
            1 -> BLUFF_OFFSET_MID
            else -> BLUFF_OFFSET_LATE
        }

        val myCard = state.privateCards[playerID]!!
        return if (myCard.toDouble() > Deck.MEAN) {
            // High card: want to buy -> quote LOW to suppress market
            ev - offset.toDouble()
        } else {
            // Low card: want to sell -> quote HIGH to inflate market
            ev + offset.toDouble()
        }
    }

    /** Aggressive adaptation — kicks in after just 2 same-direction trades. */
    private fun adaptationShift(requester: String?): Int {
        val req = requester ?: return 0
        val streak = tracker.opponentSameDirectionStreak(req)
        if (streak < STRAT_ADAPTATION_THRESHOLD) return 0

        val direction = tracker.opponentDirection(req)
        if (direction == "neutral") return 0

        val excess = streak - STRAT_ADAPTATION_THRESHOLD + 1
        val shift = minOf(excess * STRAT_ADAPT_SHIFT_PER_TRADE, STRAT_MAX_ADAPT_SHIFT)
        return if (direction == "bearish") -shift else shift
    }

    // -- Bot Protocol --

    override fun getQuote(state: GameState, requester: String?): Quote {
        val mid = bluffMid(state)
        val noise = rng.nextDouble() - 0.5 // [-0.5, 0.5]
        val adapt = adaptationShift(requester).toDouble()
        val direct = tracker.directShiftFor(requester ?: "").toDouble()
        val adjusted = mid + noise + adapt + direct
        val bid = Math.round(adjusted).toInt() - 1
        return Quote(bid = bid, ask = bid + 2)
    }

    override fun decideOnQuote(state: GameState, quoter: String, quote: Quote): String? {
        // Decide based on TRUE EV, not bluff
        val ev = trueEV(state)
        val buyEdge = ev - quote.ask.toDouble()
        val sellEdge = quote.bid.toDouble() - ev
        val myCard = state.privateCards[playerID]!!
        val highCard = myCard.toDouble() > Deck.MEAN

        if (highCard) {
            // Prefers buying
            if (buyEdge > STRAT_EDGE_THRESHOLD.toDouble()) return "buy"
            // Opportunistic sell only if edge is very large (3x threshold)
            if (sellEdge > STRAT_EDGE_THRESHOLD.toDouble() * 3.0) return "sell"
        } else {
            // Prefers selling
            if (sellEdge > STRAT_EDGE_THRESHOLD.toDouble()) return "sell"
            // Opportunistic buy only if edge is very large
            if (buyEdge > STRAT_EDGE_THRESHOLD.toDouble() * 3.0) return "buy"
        }
        return null
    }

    override fun decideAction(state: GameState): String? {
        if (state.phase == Phase.SETTLE) return null

        // Reset trade count on phase change
        if (state.phase != lastPhase) {
            phaseTradeCount = 0
            lastPhase = state.phase
        }
        if (phaseTradeCount >= MAX_TRADES_PER_TURN) return null
        phaseTradeCount++

        val others = state.playerIDs.filter { it != playerID }
        val myCard = state.privateCards[playerID]!!
        val highCard = myCard.toDouble() > Deck.MEAN

        // Prefer players with OPPOSITE direction
        val preferred = others.filter { pid ->
            val dir = tracker.opponentDirection(pid)
            if (highCard) dir == "bearish" else dir == "bullish"
        }
        rng.randomElement(preferred)?.let { return it }

        // Fall back to neutral players
        val neutral = others.filter { tracker.opponentDirection(it) == "neutral" }
        rng.randomElement(neutral)?.let { return it }

        // Anyone
        return rng.randomElement(others)
    }

    override fun onPhaseChange(state: GameState) {
        phaseTradeCount = 0
        lastPhase = state.phase
    }
}
