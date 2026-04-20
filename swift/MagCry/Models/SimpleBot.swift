import Foundation

// MARK: - Constants

private let edgeThreshold = 1
private let noiseRange = 1
private let adaptShiftPerTrade = 2
private let maxAdaptShift = 10

// MARK: - SimpleBot

/// EV-based bot for Easy and Medium difficulties.
/// Quotes around its expected value with small noise.
/// Adaptation: if enabled, shifts quotes against a player's trading direction.
final class SimpleBot: Bot {
    let playerID: String
    let tracker = BotTracker()
    private let rng: GameRNG
    private let adaptationThreshold: Int?  // nil = Easy (never adapt), Int = Medium

    init(playerID: String, rng: GameRNG, adaptationThreshold: Int? = nil) {
        self.playerID = playerID
        self.rng = rng
        self.adaptationThreshold = adaptationThreshold
    }

    // MARK: - Private

    private func expectedMid(state: GameState) -> Double {
        let known = state.knownCards(for: playerID)
        let unknown = state.unknownCount(for: playerID)
        return Deck.expectedTotal(knownCards: known, unknownCount: unknown)
    }

    /// Compute quote shift based on requester's trading pattern.
    /// Bearish requester → shift DOWN (worse sells for them).
    /// Bullish requester → shift UP (worse buys for them).
    private func adaptationShift(requester: String?) -> Int {
        guard let threshold = adaptationThreshold,
              let req = requester else { return 0 }

        let streak = tracker.opponentSameDirectionStreak(req)
        guard streak >= threshold else { return 0 }

        let direction = tracker.opponentDirection(req)
        guard direction != "neutral" else { return 0 }

        let excess = streak - threshold + 1
        let shift = min(excess * adaptShiftPerTrade, maxAdaptShift)
        return direction == "bearish" ? -shift : shift
    }

    // MARK: - Bot Protocol

    func getQuote(state: GameState, requester: String?) -> Quote {
        let ev = expectedMid(state: state)
        let noise = Double(rng.nextInt(in: -noiseRange...noiseRange))
        let adapt = Double(adaptationShift(requester: requester))
        let mid = ev + noise + adapt
        let bid = Int(mid.rounded()) - 1
        return Quote(bid: bid, ask: bid + 2)
    }

    func decideOnQuote(state: GameState, quoter: String, quote: Quote) -> String? {
        let ev = expectedMid(state: state)
        let buyEdge = ev - Double(quote.ask)
        let sellEdge = Double(quote.bid) - ev

        // If both edges exist, prefer the bigger one
        if buyEdge > Double(edgeThreshold) && sellEdge > Double(edgeThreshold) {
            return buyEdge >= sellEdge ? "buy" : "sell"
        }
        if buyEdge > Double(edgeThreshold) { return "buy" }
        if sellEdge > Double(edgeThreshold) { return "sell" }
        return nil
    }

    func decideAction(state: GameState) -> String? {
        guard state.phase != .settle else { return nil }
        let others = state.playerIDs.filter { $0 != playerID }
        return rng.randomElement(others)
    }
}
