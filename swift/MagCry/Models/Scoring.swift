import Foundation

// MARK: - Settlement & Scoring

/// Compute P&L per player. Delegates to GameState.scoreboard().
func settle(state: GameState) -> [String: Int] {
    state.scoreboard()
}

/// Sort players by score, highest first.
func leaderboard(scores: [String: Int]) -> [(String, Int)] {
    scores.sorted { $0.value > $1.value }
}

/// All trades involving a player, each with its P&L.
func tradeBreakdown(
    state: GameState,
    playerID: String
) -> [(trade: Trade, pnl: Int)] {
    let total = state.finalTotal()
    return state.trades
        .filter { $0.buyer == playerID || $0.seller == playerID }
        .map { ($0, $0.pnlFor(playerID: playerID, finalTotal: total)) }
}
