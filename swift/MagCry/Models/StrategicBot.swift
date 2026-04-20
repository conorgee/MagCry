import Foundation

// MARK: - Constants

private let bluffOffsetEarly = 8   // OPEN phase — big bluffs
private let bluffOffsetMid = 4     // After 1 reveal
private let bluffOffsetLate = 1    // After 2+ reveals — nearly honest
private let stratEdgeThreshold = 1
private let maxTradesPerTurn = 3
private let stratAdaptationThreshold = 2  // Much faster than SimpleBot's 5
private let stratAdaptShiftPerTrade = 3   // Steeper per-trade shift
private let stratMaxAdaptShift = 15       // Higher cap

// MARK: - StrategicBot

/// Bluffing bot for Hard difficulty (and 2 of 4 in Medium).
/// - High card → quotes LOW to suppress market, then buys cheap.
/// - Low card  → quotes HIGH to inflate market, then sells expensive.
/// - Bluffs converge to honest as cards are revealed (8 → 4 → 1).
/// - Adapts aggressively after just 2 same-direction trades.
final class StrategicBot: Bot {
    let playerID: String
    let tracker = BotTracker()
    private let rng: GameRNG
    private var phaseTradeCount = 0
    private var lastPhase: Phase?

    init(playerID: String, rng: GameRNG) {
        self.playerID = playerID
        self.rng = rng
    }

    // MARK: - Private

    /// True expected value (what we actually believe, not what we quote).
    private func trueEV(state: GameState) -> Double {
        let known = state.knownCards(for: playerID)
        let unknown = state.unknownCount(for: playerID)
        return Deck.expectedTotal(knownCards: known, unknownCount: unknown)
    }

    /// Bluffed mid-price for quoting. Opposite of our actual belief.
    private func bluffMid(state: GameState) -> Double {
        let ev = trueEV(state: state)

        let offset: Int
        switch state.revealedCentral.count {
        case 0:  offset = bluffOffsetEarly
        case 1:  offset = bluffOffsetMid
        default: offset = bluffOffsetLate
        }

        let myCard = state.privateCards[playerID]!
        if Double(myCard) > Deck.mean {
            // High card: want to buy → quote LOW to suppress market
            return ev - Double(offset)
        } else {
            // Low card: want to sell → quote HIGH to inflate market
            return ev + Double(offset)
        }
    }

    /// Aggressive adaptation — kicks in after just 2 same-direction trades.
    private func adaptationShift(requester: String?) -> Int {
        guard let req = requester else { return 0 }
        let streak = tracker.opponentSameDirectionStreak(req)
        guard streak >= stratAdaptationThreshold else { return 0 }

        let direction = tracker.opponentDirection(req)
        guard direction != "neutral" else { return 0 }

        let excess = streak - stratAdaptationThreshold + 1
        let shift = min(excess * stratAdaptShiftPerTrade, stratMaxAdaptShift)
        return direction == "bearish" ? -shift : shift
    }

    // MARK: - Bot Protocol

    func getQuote(state: GameState, requester: String?) -> Quote {
        let mid = bluffMid(state: state)
        let noise = rng.nextDouble() - 0.5  // [-0.5, 0.5]
        let adapt = Double(adaptationShift(requester: requester))
        let adjusted = mid + noise + adapt
        let bid = Int(adjusted.rounded()) - 1
        return Quote(bid: bid, ask: bid + 2)
    }

    func decideOnQuote(state: GameState, quoter: String, quote: Quote) -> String? {
        // Decide based on TRUE EV, not bluff
        let ev = trueEV(state: state)
        let buyEdge = ev - Double(quote.ask)
        let sellEdge = Double(quote.bid) - ev
        let myCard = state.privateCards[playerID]!
        let highCard = Double(myCard) > Deck.mean

        if highCard {
            // Prefers buying
            if buyEdge > Double(stratEdgeThreshold) { return "buy" }
            // Opportunistic sell only if edge is very large (3x threshold)
            if sellEdge > Double(stratEdgeThreshold) * 3.0 { return "sell" }
        } else {
            // Prefers selling
            if sellEdge > Double(stratEdgeThreshold) { return "sell" }
            // Opportunistic buy only if edge is very large
            if buyEdge > Double(stratEdgeThreshold) * 3.0 { return "buy" }
        }
        return nil
    }

    func decideAction(state: GameState) -> String? {
        guard state.phase != .settle else { return nil }

        // Reset trade count on phase change
        if state.phase != lastPhase {
            phaseTradeCount = 0
            lastPhase = state.phase
        }
        guard phaseTradeCount < maxTradesPerTurn else { return nil }
        phaseTradeCount += 1

        let others = state.playerIDs.filter { $0 != playerID }
        let myCard = state.privateCards[playerID]!
        let highCard = Double(myCard) > Deck.mean

        // Prefer players with OPPOSITE direction
        // If we want to buy, look for bearish players (they might quote low)
        let preferred = others.filter { pid in
            let dir = tracker.opponentDirection(pid)
            return highCard ? dir == "bearish" : dir == "bullish"
        }
        if let target = rng.randomElement(preferred) { return target }

        // Fall back to neutral players
        let neutral = others.filter { tracker.opponentDirection($0) == "neutral" }
        if let target = rng.randomElement(neutral) { return target }

        // Anyone
        return rng.randomElement(others)
    }

    func onPhaseChange(state: GameState) {
        phaseTradeCount = 0
        lastPhase = state.phase
    }
}
