package com.magcry.model

// -- Settlement & Scoring --

/** Compute P&L per player. Delegates to GameState.scoreboard(). */
fun settle(state: GameState): Map<String, Int> = state.scoreboard()

/** Sort players by score, highest first. */
fun leaderboard(scores: Map<String, Int>): List<Pair<String, Int>> =
    scores.entries.sortedByDescending { it.value }.map { Pair(it.key, it.value) }

/** All trades involving a player, each with its P&L. */
fun tradeBreakdown(state: GameState, playerID: String): List<Pair<Trade, Int>> {
    val total = state.finalTotal()
    return state.trades
        .filter { it.buyer == playerID || it.seller == playerID }
        .map { Pair(it, it.pnlFor(playerID, total)) }
}
