package com.magcry.model

// -- Bot Interface --

/** All bots must implement these methods. */
interface Bot {
    val playerID: String
    val tracker: BotTracker

    /** Provide a two-way price when asked. Spread is always 2. */
    fun getQuote(state: GameState, requester: String?): Quote

    /** Decide what to do when we receive a quote from another player.
     *  Returns "buy" (hit ask), "sell" (hit bid), or null (walk away). */
    fun decideOnQuote(state: GameState, quoter: String, quote: Quote): String?

    /** Choose a target player to ask for a price, or null to do nothing. */
    fun decideAction(state: GameState): String?

    /** Called when the phase advances (e.g. a central card is revealed). */
    fun onPhaseChange(state: GameState) {} // default no-op
}

// -- BotTracker --

/**
 * Tracks opponent trading patterns. Shared base for all bot types.
 * This is the core adaptation mechanic — bots read your trading direction.
 */
class BotTracker(private val rng: GameRNG = GameRNG()) {
    /** player_id -> ["buy", "sell", ...] */
    private val opponentHistory: MutableMap<String, MutableList<String>> = mutableMapOf()

    /** Per-opponent accumulated price shift from direct trades WITH this bot. */
    private val directShifts: MutableMap<String, Int> = mutableMapOf()

    /** Record that a player bought or sold (global tracking for all bots). */
    fun recordTrade(buyer: String, seller: String, playerOfInterest: String) {
        when (playerOfInterest) {
            buyer -> opponentHistory.getOrPut(playerOfInterest) { mutableListOf() }.add("buy")
            seller -> opponentHistory.getOrPut(playerOfInterest) { mutableListOf() }.add("sell")
        }
    }

    /**
     * Record a direct trade between this bot and an opponent.
     * Shifts this bot's future quotes for that specific opponent.
     * Shift is randomized between 2 and 6 (inclusive).
     */
    fun recordDirectTrade(opponent: String, opponentBought: Boolean) {
        val shift = rng.nextInt(2..6)
        if (opponentBought) {
            // They bought from us -> raise our price for them
            directShifts[opponent] = (directShifts[opponent] ?: 0) + shift
        } else {
            // They sold to us -> lower our price for them
            directShifts[opponent] = (directShifts[opponent] ?: 0) - shift
        }
    }

    /**
     * Record that an opponent passed on our quote, but read their recent
     * trades with OTHER bots to softly adjust. Shift is randomized 1-2.
     */
    fun recordPassWithMarketRead(opponent: String) {
        val direction = opponentDirection(opponent)
        if (direction == "neutral") return
        val softShift = rng.nextInt(1..2)
        if (direction == "bullish") {
            directShifts[opponent] = (directShifts[opponent] ?: 0) + softShift
        } else {
            directShifts[opponent] = (directShifts[opponent] ?: 0) - softShift
        }
    }

    /** Get the accumulated direct shift for an opponent. */
    fun directShiftFor(playerID: String): Int = directShifts[playerID] ?: 0

    /**
     * Partial decay on phase change — shifts reduce by 2 toward zero.
     * New information means bots partially recalibrate.
     */
    fun decayDirectShifts() {
        for (key in directShifts.keys) {
            val v = directShifts[key]!!
            directShifts[key] = when {
                v > 0 -> maxOf(0, v - 2)
                v < 0 -> minOf(0, v + 2)
                else -> 0
            }
        }
    }

    /** Infer directional bias: "bullish", "bearish", or "neutral". */
    fun opponentDirection(playerID: String): String {
        val history = opponentHistory[playerID] ?: return "neutral"
        if (history.size < 2) return "neutral"
        val buys = history.count { it == "buy" }
        val sells = history.count { it == "sell" }
        if (sells >= buys + 2) return "bearish"
        if (buys >= sells + 2) return "bullish"
        return "neutral"
    }

    /** Total trades recorded for a player. */
    fun opponentTradeCount(playerID: String): Int =
        (opponentHistory[playerID] ?: emptyList()).size

    /** Consecutive same-direction trades from the end of history. */
    fun opponentSameDirectionStreak(playerID: String): Int {
        val history = opponentHistory[playerID] ?: return 0
        if (history.isEmpty()) return 0
        val last = history.last()
        var streak = 0
        for (direction in history.asReversed()) {
            if (direction == last) streak++ else break
        }
        return streak
    }
}
