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

    /// Per-opponent accumulated price shift from direct trades WITH this bot.
    /// Positive = bot has raised price (opponent was buying).
    /// Negative = bot has lowered price (opponent was selling).
    private var directShifts: [String: Int] = [:]

    /// RNG for randomized shift amounts.
    private let rng: GameRNG

    init(rng: GameRNG? = nil) {
        self.rng = rng ?? GameRNG()
    }

    /// Record that a player bought or sold (global tracking for all bots).
    func recordTrade(buyer: String, seller: String, playerOfInterest: String) {
        if playerOfInterest == buyer {
            opponentHistory[playerOfInterest, default: []].append("buy")
        } else if playerOfInterest == seller {
            opponentHistory[playerOfInterest, default: []].append("sell")
        }
    }

    /// Record a direct trade between this bot and an opponent.
    /// Shifts this bot's future quotes for that specific opponent.
    /// Shift is randomized between 2 and 6 (inclusive).
    func recordDirectTrade(opponent: String, opponentBought: Bool) {
        let shift = rng.nextInt(in: 2...6)
        if opponentBought {
            // They bought from us → raise our price for them
            directShifts[opponent, default: 0] += shift
        } else {
            // They sold to us → lower our price for them
            directShifts[opponent, default: 0] -= shift
        }
    }

    /// Record that an opponent passed on our quote, but read their recent
    /// trades with OTHER bots to softly adjust. Shift is randomized 1-2.
    func recordPassWithMarketRead(opponent: String) {
        let direction = opponentDirection(opponent)
        guard direction != "neutral" else { return }
        let softShift = rng.nextInt(in: 1...2)
        if direction == "bullish" {
            // They've been buying elsewhere → nudge our price up
            directShifts[opponent, default: 0] += softShift
        } else {
            // They've been selling elsewhere → nudge our price down
            directShifts[opponent, default: 0] -= softShift
        }
    }

    /// Get the accumulated direct shift for an opponent.
    func directShiftFor(_ playerID: String) -> Int {
        directShifts[playerID, default: 0]
    }

    /// Partial decay on phase change — shifts reduce by 2 toward zero.
    /// New information means bots partially recalibrate.
    func decayDirectShifts() {
        for key in directShifts.keys {
            let val = directShifts[key]!
            if val > 0 {
                directShifts[key] = max(0, val - 2)
            } else if val < 0 {
                directShifts[key] = min(0, val + 2)
            }
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
