import Foundation

// MARK: - Bot Protocol

/// All bots must implement these methods.
/// The Bot protocol is the V2 "ask for a price" interface.
protocol Bot: AnyObject {
    var playerID: String { get }
    var tracker: BotTracker { get }

    /// Provide a two-way price when asked. Spread is always 2.
    func getQuote(state: GameState, requester: String?) -> Quote

    /// Decide what to do when we receive a quote from another player.
    /// Returns "buy" (hit ask), "sell" (hit bid), or nil (walk away).
    func decideOnQuote(state: GameState, quoter: String, quote: Quote) -> String?

    /// Choose a target player to ask for a price, or nil to do nothing.
    func decideAction(state: GameState) -> String?

    /// Called when the phase advances (e.g. a central card is revealed).
    func onPhaseChange(state: GameState)
}

extension Bot {
    func onPhaseChange(state: GameState) {}  // default no-op
}

// MARK: - BotTracker

/// Tracks opponent trading patterns. Shared base for all bot types.
/// This is the core adaptation mechanic — bots read your trading direction.
final class BotTracker {
    /// player_id -> ["buy", "sell", ...]
    private var opponentHistory: [String: [String]] = [:]

    /// Record that a player bought or sold.
    func recordTrade(buyer: String, seller: String, playerOfInterest: String) {
        if playerOfInterest == buyer {
            opponentHistory[playerOfInterest, default: []].append("buy")
        } else if playerOfInterest == seller {
            opponentHistory[playerOfInterest, default: []].append("sell")
        }
    }

    /// Infer directional bias: "bullish", "bearish", or "neutral".
    func opponentDirection(_ playerID: String) -> String {
        let history = opponentHistory[playerID, default: []]
        guard history.count >= 2 else { return "neutral" }
        let buys = history.filter { $0 == "buy" }.count
        let sells = history.filter { $0 == "sell" }.count
        if sells >= buys + 2 { return "bearish" }
        if buys >= sells + 2 { return "bullish" }
        return "neutral"
    }

    /// Total trades recorded for a player.
    func opponentTradeCount(_ playerID: String) -> Int {
        opponentHistory[playerID, default: []].count
    }

    /// Consecutive same-direction trades from the end of history.
    func opponentSameDirectionStreak(_ playerID: String) -> Int {
        let history = opponentHistory[playerID, default: []]
        guard let last = history.last else { return 0 }
        var streak = 0
        for direction in history.reversed() {
            if direction == last {
                streak += 1
            } else {
                break
            }
        }
        return streak
    }
}
