import Foundation

// MARK: - Enums

enum Difficulty: Int, CaseIterable {
    case easy = 1
    case medium = 2
    case hard = 3

    var label: String {
        switch self {
        case .easy:   return "Easy"
        case .medium: return "Medium"
        case .hard:   return "Hard"
        }
    }

    var description: String {
        switch self {
        case .easy:   return "4 simple bots, never adapt"
        case .medium: return "2 simple + 2 strategic, adapt after ~5 trades"
        case .hard:   return "4 strategic bots, bluff & adapt fast"
        }
    }
}

enum Phase: Int, CaseIterable, Comparable {
    case open
    case reveal1
    case reveal2
    case reveal3
    case settle

    static func < (lhs: Phase, rhs: Phase) -> Bool {
        lhs.rawValue < rhs.rawValue
    }

    var label: String {
        switch self {
        case .open:    return "Open Trading"
        case .reveal1: return "Reveal 1"
        case .reveal2: return "Reveal 2"
        case .reveal3: return "Reveal 3"
        case .settle:  return "Settlement"
        }
    }
}

let phaseOrder: [Phase] = [.open, .reveal1, .reveal2, .reveal3, .settle]

// MARK: - Quote

/// Two-way price with spread locked at exactly 2 (book rule).
struct Quote: Equatable {
    let bid: Int
    let ask: Int

    init(bid: Int, ask: Int) {
        precondition(ask == bid + 2, "Quote spread must be exactly 2, got \(ask - bid)")
        self.bid = bid
        self.ask = ask
    }

    /// Create a quote centered around a mid-price.
    /// bid = Int(mid) - 1, ask = bid + 2
    static func fromMid(_ mid: Double) -> Quote {
        let bid = Int(mid.rounded()) - 1
        return Quote(bid: bid, ask: bid + 2)
    }
}

// MARK: - Trade

struct Trade: Equatable, Identifiable {
    let id = UUID()
    let buyer: String
    let seller: String
    let price: Int
    let phase: Phase

    /// P&L for a given player at settlement.
    /// Buyer: finalTotal - price, Seller: price - finalTotal, Others: 0
    func pnlFor(playerID: String, finalTotal: Int) -> Int {
        if playerID == buyer {
            return finalTotal - price
        } else if playerID == seller {
            return price - finalTotal
        }
        return 0
    }

    static func == (lhs: Trade, rhs: Trade) -> Bool {
        lhs.buyer == rhs.buyer &&
        lhs.seller == rhs.seller &&
        lhs.price == rhs.price &&
        lhs.phase == rhs.phase
    }
}

// MARK: - GameState

/// Mutable game state for one round. Reference type because it's shared and mutated.
final class GameState {
    let playerIDs: [String]
    let privateCards: [String: Int]
    let centralCards: [Int]
    var revealedCentral: [Int] = []
    var trades: [Trade] = []
    var phase: Phase

    init(
        playerIDs: [String],
        privateCards: [String: Int],
        centralCards: [Int],
        phase: Phase = .open
    ) {
        self.playerIDs = playerIDs
        self.privateCards = privateCards
        self.centralCards = centralCards
        self.phase = phase
    }

    /// Sum of all 8 in-play cards (5 private + 3 central).
    func finalTotal() -> Int {
        privateCards.values.reduce(0, +) + centralCards.reduce(0, +)
    }

    /// Cards known to a specific player: their private card + revealed central cards.
    func knownCards(for playerID: String) -> [Int] {
        var cards: [Int] = []
        if let card = privateCards[playerID] {
            cards.append(card)
        }
        cards.append(contentsOf: revealedCentral)
        return cards
    }

    /// How many cards this player can't see: other players' cards + hidden central.
    func unknownCount(for playerID: String) -> Int {
        let otherPlayers = playerIDs.count - 1
        let hiddenCentral = centralCards.count - revealedCentral.count
        return otherPlayers + hiddenCentral
    }

    /// Compute P&L per player from all trades.
    func scoreboard() -> [String: Int] {
        let total = finalTotal()
        var scores: [String: Int] = Dictionary(uniqueKeysWithValues: playerIDs.map { ($0, 0) })
        for trade in trades {
            scores[trade.buyer, default: 0] += total - trade.price
            scores[trade.seller, default: 0] += trade.price - total
        }
        return scores
    }
}
