import XCTest
@testable import MagCry

final class DeckTests: XCTestCase {

    // MARK: - Deck Composition

    func testDeckHas17Cards() {
        XCTAssertEqual(Deck.cards.count, 17)
        XCTAssertEqual(Deck.size, 17)
    }

    func testDeckContainsMinusTen() {
        XCTAssertTrue(Deck.cards.contains(-10))
    }

    func testDeckContainsTwenty() {
        XCTAssertTrue(Deck.cards.contains(20))
    }

    func testDeckContains1Through15() {
        for i in 1...15 {
            XCTAssertTrue(Deck.cards.contains(i), "Deck missing card \(i)")
        }
    }

    func testDeckHasNoDuplicates() {
        XCTAssertEqual(Deck.cards.count, Set(Deck.cards).count)
    }

    func testDeckSumIs130() {
        XCTAssertEqual(Deck.cards.reduce(0, +), 130)
        XCTAssertEqual(Deck.sum, 130)
    }

    func testDeckMeanApprox7_65() {
        XCTAssertEqual(Deck.mean, Double(130) / Double(17), accuracy: 0.00001)
    }

    func testNPlayersIs5() {
        XCTAssertEqual(Deck.nPlayers, 5)
    }

    func testNCentralIs3() {
        XCTAssertEqual(Deck.nCentral, 3)
    }

    func testNInPlayIs8() {
        XCTAssertEqual(Deck.nInPlay, 8)
    }

    // MARK: - Deal

    private let playerIDs = ["You", "Alice", "Bob", "Carol", "Dave"]

    func testDealReturnsCorrectNumberOfPrivateCards() {
        let rng = GameRNG(seed: 42)
        let (privateCards, _) = Deck.deal(playerIDs: playerIDs, rng: rng)
        XCTAssertEqual(privateCards.count, 5)
    }

    func testDealReturnsCorrectNumberOfCentralCards() {
        let rng = GameRNG(seed: 42)
        let (_, centralCards) = Deck.deal(playerIDs: playerIDs, rng: rng)
        XCTAssertEqual(centralCards.count, 3)
    }

    func testAllDealtCardsFromDeck() {
        let rng = GameRNG(seed: 42)
        let (privateCards, centralCards) = Deck.deal(playerIDs: playerIDs, rng: rng)
        let allDealt = Array(privateCards.values) + centralCards
        for card in allDealt {
            XCTAssertTrue(Deck.cards.contains(card), "Dealt card \(card) not in deck")
        }
    }

    func testNoDuplicateCardsInDeal() {
        let rng = GameRNG(seed: 42)
        let (privateCards, centralCards) = Deck.deal(playerIDs: playerIDs, rng: rng)
        let allDealt = Array(privateCards.values) + centralCards
        XCTAssertEqual(allDealt.count, Set(allDealt).count)
    }

    func testEachPlayerGetsOneCard() {
        let rng = GameRNG(seed: 42)
        let (privateCards, _) = Deck.deal(playerIDs: playerIDs, rng: rng)
        for pid in playerIDs {
            XCTAssertNotNil(privateCards[pid], "Player \(pid) missing card")
        }
    }

    func testDeterministicWithSameSeed() {
        let rng1 = GameRNG(seed: 99)
        let rng2 = GameRNG(seed: 99)
        let (p1, c1) = Deck.deal(playerIDs: playerIDs, rng: rng1)
        let (p2, c2) = Deck.deal(playerIDs: playerIDs, rng: rng2)
        XCTAssertEqual(p1, p2)
        XCTAssertEqual(c1, c2)
    }

    func testDifferentSeedsGiveDifferentDeals() {
        let rng1 = GameRNG(seed: 1)
        let rng2 = GameRNG(seed: 2)
        let (p1, c1) = Deck.deal(playerIDs: playerIDs, rng: rng1)
        let (p2, c2) = Deck.deal(playerIDs: playerIDs, rng: rng2)
        XCTAssertTrue(p1 != p2 || c1 != c2)
    }

    // MARK: - Expected Total

    func testExpectedTotalAllUnknown() {
        // 8 unknown cards from full deck: 8 * (130/17) ≈ 61.18
        let ev = Deck.expectedTotal(knownCards: [], unknownCount: 8)
        XCTAssertEqual(ev, 8.0 * (130.0 / 17.0), accuracy: 0.01)
    }

    func testExpectedTotalHolding20() {
        // Book states: holding 20 → expected total ≈ 68
        let ev = Deck.expectedTotal(knownCards: [20], unknownCount: 7)
        XCTAssertEqual(ev, 68.0, accuracy: 0.6)
    }

    func testExpectedTotalHoldingMinus10() {
        // Book states: holding -10 → expected total ≈ 51.2
        let ev = Deck.expectedTotal(knownCards: [-10], unknownCount: 7)
        XCTAssertEqual(ev, 51.2, accuracy: 0.6)
    }

    func testExpectedTotalAllKnown() {
        // If all cards are known, EV = their sum exactly
        let known = [-10, 1, 2, 3, 4, 5, 6, 7]
        let ev = Deck.expectedTotal(knownCards: known, unknownCount: 0)
        XCTAssertEqual(ev, Double(known.reduce(0, +)))
    }

    func testExpectedTotalIncreasesWithHighKnownCard() {
        let evLow = Deck.expectedTotal(knownCards: [1], unknownCount: 7)
        let evHigh = Deck.expectedTotal(knownCards: [15], unknownCount: 7)
        XCTAssertGreaterThan(evHigh, evLow)
    }

    func testMeanExcludingRemovesCard() {
        let meanExcl = Deck.meanExcluding([20])
        XCTAssertLessThan(meanExcl, Deck.mean)
    }

    func testMeanExcludingMultiple() {
        let meanExcl = Deck.meanExcluding([15, 14, 13])
        XCTAssertLessThan(meanExcl, Deck.mean)
    }

    // MARK: - GameRNG

    func testRNGDeterministic() {
        let rng1 = GameRNG(seed: 42)
        let rng2 = GameRNG(seed: 42)
        for _ in 0..<100 {
            XCTAssertEqual(rng1.next(), rng2.next())
        }
    }

    func testRNGDifferentSeeds() {
        let rng1 = GameRNG(seed: 1)
        let rng2 = GameRNG(seed: 2)
        var allSame = true
        for _ in 0..<10 {
            if rng1.next() != rng2.next() { allSame = false }
        }
        XCTAssertFalse(allSame)
    }

    func testRNGNextIntInRange() {
        let rng = GameRNG(seed: 42)
        for _ in 0..<100 {
            let val = rng.nextInt(in: 3...7)
            XCTAssertGreaterThanOrEqual(val, 3)
            XCTAssertLessThanOrEqual(val, 7)
        }
    }

    func testRNGNextDoubleInRange() {
        let rng = GameRNG(seed: 42)
        for _ in 0..<100 {
            let val = rng.nextDouble()
            XCTAssertGreaterThanOrEqual(val, 0.0)
            XCTAssertLessThanOrEqual(val, 1.0)
        }
    }

    func testRNGShuffledPreservesElements() {
        let rng = GameRNG(seed: 42)
        let original = [1, 2, 3, 4, 5]
        let shuffled = rng.shuffled(original)
        XCTAssertEqual(shuffled.sorted(), original)
    }

    func testRNGSampleReturnsRequestedCount() {
        let rng = GameRNG(seed: 42)
        let result = rng.sample([1, 2, 3, 4, 5], count: 3)
        XCTAssertEqual(result.count, 3)
    }

    func testRNGSampleNoDuplicates() {
        let rng = GameRNG(seed: 42)
        let result = rng.sample([1, 2, 3, 4, 5], count: 4)
        XCTAssertEqual(result.count, Set(result).count)
    }

    func testRNGChildIsDeterministic() {
        let parent1 = GameRNG(seed: 42)
        let parent2 = GameRNG(seed: 42)
        let child1 = parent1.child()
        let child2 = parent2.child()
        for _ in 0..<10 {
            XCTAssertEqual(child1.next(), child2.next())
        }
    }

    func testRNGZeroSeedBecomesNonZero() {
        // Seed 0 should be clamped to 1 (xorshift breaks on 0)
        let rng = GameRNG(seed: 0)
        let val = rng.next()
        XCTAssertNotEqual(val, 0)
    }
}
