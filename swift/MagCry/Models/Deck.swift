import Foundation

// MARK: - Seeded RNG for deterministic gameplay and testing

/// Class-based RNG so bots can hold a reference. Uses xorshift64* algorithm.
/// Conforms to RandomNumberGenerator so it works with Swift stdlib (e.g. .shuffled(using:)).
final class GameRNG: RandomNumberGenerator {
    private var state: UInt64

    init(seed: UInt64 = UInt64.random(in: 1...UInt64.max)) {
        // Avoid zero state — xorshift breaks on 0
        self.state = seed == 0 ? 1 : seed
    }

    func next() -> UInt64 {
        state ^= state >> 12
        state ^= state << 25
        state ^= state >> 27
        return state &* 0x2545F4914F6CDD1D
    }

    // MARK: - Convenience

    func nextInt(in range: ClosedRange<Int>) -> Int {
        let width = UInt64(range.upperBound - range.lowerBound + 1)
        return range.lowerBound + Int(next() % width)
    }

    func nextDouble() -> Double {
        Double(next()) / Double(UInt64.max)
    }

    func shuffled<T>(_ array: [T]) -> [T] {
        var result = array
        for i in stride(from: result.count - 1, through: 1, by: -1) {
            let j = Int(next() % UInt64(i + 1))
            result.swapAt(i, j)
        }
        return result
    }

    func sample<T>(_ array: [T], count: Int) -> [T] {
        var pool = array
        var result: [T] = []
        for _ in 0..<min(count, pool.count) {
            let idx = Int(next() % UInt64(pool.count))
            result.append(pool.remove(at: idx))
        }
        return result
    }

    func randomElement<T>(_ array: [T]) -> T? {
        guard !array.isEmpty else { return nil }
        return array[Int(next() % UInt64(array.count))]
    }

    /// Create a child RNG with a derived seed (for giving each bot its own RNG).
    func child() -> GameRNG {
        GameRNG(seed: next())
    }
}

// MARK: - Deck

enum Deck {
    static let cards: [Int] = [-10] + Array(1...15) + [20]
    static let size = 17
    static let sum = 130
    static let mean: Double = Double(sum) / Double(size)  // ≈ 7.647
    static let nPlayers = 5
    static let nCentral = 3
    static let nInPlay = 8  // 5 private + 3 central

    /// Mean of remaining deck after removing known cards.
    static func meanExcluding(_ knownCards: [Int]) -> Double {
        var remaining = cards
        for card in knownCards {
            if let idx = remaining.firstIndex(of: card) {
                remaining.remove(at: idx)
            }
        }
        guard !remaining.isEmpty else { return 0.0 }
        return Double(remaining.reduce(0, +)) / Double(remaining.count)
    }

    /// Expected total of all 8 in-play cards given what we know.
    /// EV = sum(known) + unknownCount * meanExcluding(known)
    static func expectedTotal(knownCards: [Int], unknownCount: Int) -> Double {
        let knownSum = Double(knownCards.reduce(0, +))
        return knownSum + Double(unknownCount) * meanExcluding(knownCards)
    }

    /// Shuffle deck, deal 1 private card per player + 3 central cards.
    static func deal(
        playerIDs: [String],
        rng: GameRNG
    ) -> (privateCards: [String: Int], centralCards: [Int]) {
        precondition(playerIDs.count == nPlayers,
                     "Expected \(nPlayers) players, got \(playerIDs.count)")

        let shuffled = rng.shuffled(cards)
        var privateCards: [String: Int] = [:]
        for (i, pid) in playerIDs.enumerated() {
            privateCards[pid] = shuffled[i]
        }
        let centralCards = Array(shuffled[nPlayers..<(nPlayers + nCentral)])
        return (privateCards, centralCards)
    }
}
