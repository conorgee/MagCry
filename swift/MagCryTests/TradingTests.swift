import XCTest
@testable import MagCry

final class TradingTests: XCTestCase {

    // MARK: - Helpers

    private let playerIDs = ["You", "Alice", "Bob", "Carol", "Dave"]

    /// Standard test state: private cards sum to 43, central=[14,13,9], T=79.
    private func makeState(phase: Phase = .open) -> GameState {
        let privateCards: [String: Int] = [
            "You": 7, "Alice": 10, "Bob": 5, "Carol": 6, "Dave": 15,
        ]
        let centralCards = [14, 13, 9]
        let state = GameState(
            playerIDs: playerIDs,
            privateCards: privateCards,
            centralCards: centralCards,
            phase: phase
        )
        state.revealedCentral = centralCards  // all revealed for scoring tests
        return state
    }

    private func makeTrade(_ buyer: String, _ seller: String, _ price: Int) -> Trade {
        Trade(buyer: buyer, seller: seller, price: price, phase: .open)
    }

    // MARK: - ValidateQuote

    func testValidSpreadReturnsQuote() throws {
        let q = try validateQuote(bid: 60, ask: 62)
        XCTAssertEqual(q.bid, 60)
        XCTAssertEqual(q.ask, 62)
    }

    func testSpreadOf3Throws() {
        XCTAssertThrowsError(try validateQuote(bid: 60, ask: 63)) { error in
            XCTAssertTrue(error is TradingError)
        }
    }

    func testSpreadOf1Throws() {
        XCTAssertThrowsError(try validateQuote(bid: 60, ask: 61))
    }

    func testSpreadOf0Throws() {
        XCTAssertThrowsError(try validateQuote(bid: 60, ask: 60))
    }

    func testNegativeBidValid() throws {
        let q = try validateQuote(bid: -4, ask: -2)
        XCTAssertEqual(q.bid, -4)
        XCTAssertEqual(q.ask, -2)
    }

    // MARK: - ExecuteTradeDirectly

    func testBasicDirectTrade() throws {
        let state = makeState()
        let trade = try executeTradeDirectly(
            state: state, buyer: "You", seller: "Alice", price: 60)
        XCTAssertEqual(trade.buyer, "You")
        XCTAssertEqual(trade.seller, "Alice")
        XCTAssertEqual(trade.price, 60)
        XCTAssertEqual(trade.phase, .open)
    }

    func testTradeRecordedInState() throws {
        let state = makeState()
        try executeTradeDirectly(state: state, buyer: "You", seller: "Alice", price: 60)
        XCTAssertEqual(state.trades.count, 1)
    }

    func testMultipleDirectTradesAccumulate() throws {
        let state = makeState()
        try executeTradeDirectly(state: state, buyer: "You", seller: "Alice", price: 60)
        try executeTradeDirectly(state: state, buyer: "Bob", seller: "Carol", price: 65)
        XCTAssertEqual(state.trades.count, 2)
    }

    func testSelfTradeThrows() {
        let state = makeState()
        XCTAssertThrowsError(
            try executeTradeDirectly(state: state, buyer: "You", seller: "You", price: 60)
        ) { error in
            guard case TradingError.selfTrade = error else {
                XCTFail("Expected selfTrade error, got \(error)")
                return
            }
        }
    }

    func testSettlePhaseThrows() {
        let state = makeState(phase: .settle)
        XCTAssertThrowsError(
            try executeTradeDirectly(state: state, buyer: "You", seller: "Alice", price: 60)
        ) { error in
            guard case TradingError.settlePhase = error else {
                XCTFail("Expected settlePhase error, got \(error)")
                return
            }
        }
    }

    func testUnknownBuyerThrows() {
        let state = makeState()
        XCTAssertThrowsError(
            try executeTradeDirectly(state: state, buyer: "Ghost", seller: "Alice", price: 60)
        ) { error in
            guard case TradingError.unknownPlayer("Ghost") = error else {
                XCTFail("Expected unknownPlayer error, got \(error)")
                return
            }
        }
    }

    func testUnknownSellerThrows() {
        let state = makeState()
        XCTAssertThrowsError(
            try executeTradeDirectly(state: state, buyer: "You", seller: "Ghost", price: 60)
        ) { error in
            guard case TradingError.unknownPlayer("Ghost") = error else {
                XCTFail("Expected unknownPlayer error, got \(error)")
                return
            }
        }
    }

    func testRecordsCorrectPhase() throws {
        let state = makeState(phase: .reveal2)
        let trade = try executeTradeDirectly(
            state: state, buyer: "You", seller: "Alice", price: 58)
        XCTAssertEqual(trade.phase, .reveal2)
    }

    func testAllNonSettlePhasesAllowTrading() throws {
        for phase in [Phase.open, .reveal1, .reveal2, .reveal3] {
            let state = makeState(phase: phase)
            let trade = try executeTradeDirectly(
                state: state, buyer: "You", seller: "Alice", price: 60)
            XCTAssertEqual(trade.phase, phase)
        }
    }

    func testPnlWorksOnDirectTrade() throws {
        let state = makeState()
        let trade = try executeTradeDirectly(
            state: state, buyer: "You", seller: "Alice", price: 60)
        let T = state.finalTotal()  // 79
        XCTAssertEqual(trade.pnlFor(playerID: "You", finalTotal: T), T - 60)
        XCTAssertEqual(trade.pnlFor(playerID: "Alice", finalTotal: T), 60 - T)
    }

    func testPnlForUninvolvedPlayerIsZero() throws {
        let state = makeState()
        let trade = try executeTradeDirectly(
            state: state, buyer: "You", seller: "Alice", price: 60)
        let T = state.finalTotal()
        XCTAssertEqual(trade.pnlFor(playerID: "Bob", finalTotal: T), 0)
    }

    // MARK: - GameState

    func testFinalTotal() {
        let state = makeState()
        // 7+10+5+6+15 + 14+13+9 = 79
        XCTAssertEqual(state.finalTotal(), 79)
    }

    func testKnownCardsForPlayer() {
        let state = makeState()
        state.revealedCentral = [14]
        let known = state.knownCards(for: "You")
        XCTAssertTrue(known.contains(7))     // private card
        XCTAssertTrue(known.contains(14))    // revealed central
        XCTAssertEqual(known.count, 2)
    }

    func testKnownCardsWithNoReveals() {
        let state = makeState()
        state.revealedCentral = []
        let known = state.knownCards(for: "You")
        XCTAssertEqual(known, [7])  // just private card
    }

    func testUnknownCount() {
        let state = makeState()
        state.revealedCentral = [14]
        // 4 other players + 2 hidden central = 6
        XCTAssertEqual(state.unknownCount(for: "You"), 6)
    }

    func testUnknownCountWithAllRevealed() {
        let state = makeState()
        state.revealedCentral = [14, 13, 9]
        // 4 other players + 0 hidden central = 4
        XCTAssertEqual(state.unknownCount(for: "You"), 4)
    }

    // MARK: - Quote

    func testQuoteFromMid() {
        let q = Quote.fromMid(61.0)
        XCTAssertEqual(q.ask - q.bid, 2)
    }

    func testQuoteFromMidCentersCorrectly() {
        let q = Quote.fromMid(68.0)
        // bid = Int(68.0.rounded()) - 1 = 67, ask = 69
        XCTAssertEqual(q.bid, 67)
        XCTAssertEqual(q.ask, 69)
    }

    func testQuoteEquality() {
        let q1 = Quote(bid: 60, ask: 62)
        let q2 = Quote(bid: 60, ask: 62)
        XCTAssertEqual(q1, q2)
    }

    func testQuoteInequality() {
        let q1 = Quote(bid: 60, ask: 62)
        let q2 = Quote(bid: 58, ask: 60)
        XCTAssertNotEqual(q1, q2)
    }

    // MARK: - Phase

    func testPhaseComparable() {
        XCTAssertLessThan(Phase.open, Phase.reveal1)
        XCTAssertLessThan(Phase.reveal1, Phase.reveal2)
        XCTAssertLessThan(Phase.reveal2, Phase.reveal3)
        XCTAssertLessThan(Phase.reveal3, Phase.settle)
    }

    func testPhaseLabels() {
        XCTAssertEqual(Phase.open.label, "Open Trading")
        XCTAssertEqual(Phase.settle.label, "Settlement")
    }

    // MARK: - Settle / Scoring

    func testNoTradesAllZero() {
        let state = makeState()
        let scores = settle(state: state)
        for pid in playerIDs {
            XCTAssertEqual(scores[pid], 0)
        }
    }

    func testSingleBuyCorrectPnl() {
        let state = makeState()
        state.trades.append(makeTrade("You", "Alice", 60))
        let T = state.finalTotal()  // 79
        let scores = settle(state: state)
        XCTAssertEqual(scores["You"], T - 60)    // +19
        XCTAssertEqual(scores["Alice"], 60 - T)  // -19
    }

    func testSingleSellCorrectPnl() {
        let state = makeState()
        state.trades.append(makeTrade("Alice", "You", 70))
        let T = state.finalTotal()  // 79
        let scores = settle(state: state)
        XCTAssertEqual(scores["You"], 70 - T)     // seller: -9
        XCTAssertEqual(scores["Alice"], T - 70)    // buyer: +9
    }

    func testBuyerProfitsWhenTotalAbovePrice() {
        let state = makeState()
        state.trades.append(makeTrade("You", "Alice", 50))
        let scores = settle(state: state)
        XCTAssertGreaterThan(scores["You"]!, 0)
    }

    func testSellerProfitsWhenTotalBelowPrice() {
        // You sell at 100 (well above T=79)
        let state = makeState()
        state.trades.append(makeTrade("Alice", "You", 100))
        let scores = settle(state: state)
        XCTAssertGreaterThan(scores["You"]!, 0)
    }

    func testAllPlayersInScores() {
        let state = makeState()
        let scores = settle(state: state)
        for pid in playerIDs {
            XCTAssertNotNil(scores[pid])
        }
    }

    func testMultipleTradesAccumulatedCorrectly() {
        let state = makeState()
        state.trades.append(makeTrade("You", "Alice", 60))  // You buy at 60
        state.trades.append(makeTrade("Bob", "You", 70))    // You sell at 70
        let T = state.finalTotal()  // 79
        let scores = settle(state: state)
        let expectedYou = (T - 60) + (70 - T)  // +19 - 9 = +10
        XCTAssertEqual(scores["You"], expectedYou)
    }

    // MARK: - Gary Arbitrage

    func testArbitrageProfitIs15ForAllTotals() {
        for T in -10...130 {
            let buyPnl = T - 52
            let sellPnl = 67 - T
            XCTAssertEqual(buyPnl + sellPnl, 15,
                "Arbitrage profit should be 15 for T=\(T)")
        }
    }

    func testGaryArbitrageViaSettle() {
        // Gary buys at 52 from Alice, sells at 67 to Bob
        let privateCards: [String: Int] = [
            "You": -10, "Alice": 20, "Bob": 8, "Carol": 7, "Dave": 6,
        ]
        let centralCards = [5, 4, 3]
        // T = -10+20+8+7+6 + 5+4+3 = 43
        let state = GameState(
            playerIDs: playerIDs,
            privateCards: privateCards,
            centralCards: centralCards
        )
        state.revealedCentral = centralCards
        state.trades = [
            Trade(buyer: "You", seller: "Alice", price: 52, phase: .open),
            Trade(buyer: "Bob", seller: "You", price: 67, phase: .open),
        ]
        let scores = settle(state: state)
        XCTAssertEqual(scores["You"], 15,
            "Gary's arbitrage should profit exactly 15 (T=\(state.finalTotal()))")
    }

    func testArbitrageIsRiskFreeAcrossMultipleTotals() {
        let centralScenarios = [
            [1, 2, 3],
            [10, 11, 12],
            [13, 14, 15],
            [20, 9, 8],
        ]
        for central in centralScenarios {
            let privateCards: [String: Int] = [
                "You": -10, "Alice": 5, "Bob": 6, "Carol": 7, "Dave": 8,
            ]
            let state = GameState(
                playerIDs: playerIDs,
                privateCards: privateCards,
                centralCards: central
            )
            state.revealedCentral = central
            state.trades = [
                Trade(buyer: "You", seller: "Alice", price: 52, phase: .open),
                Trade(buyer: "Bob", seller: "You", price: 67, phase: .open),
            ]
            let scores = settle(state: state)
            XCTAssertEqual(scores["You"], 15,
                "Arbitrage failed for central=\(central), T=\(state.finalTotal())")
        }
    }

    // MARK: - Zero Sum

    func testSingleTradeZeroSum() {
        let state = makeState()
        state.trades.append(makeTrade("You", "Alice", 65))
        let scores = settle(state: state)
        XCTAssertEqual(scores.values.reduce(0, +), 0)
    }

    func testMultipleTradesZeroSum() {
        let state = makeState()
        state.trades = [
            makeTrade("You", "Alice", 58),
            makeTrade("Bob", "Carol", 65),
            makeTrade("Dave", "You", 72),
            makeTrade("Alice", "Bob", 61),
        ]
        let scores = settle(state: state)
        XCTAssertEqual(scores.values.reduce(0, +), 0)
    }

    func testManyTradesZeroSum() {
        let pairs: [(String, String, Int)] = [
            ("You", "Alice", 60), ("Bob", "Carol", 65), ("Dave", "You", 70),
            ("Alice", "Bob", 55), ("Carol", "Dave", 68), ("You", "Bob", 62),
            ("Alice", "Dave", 71), ("Carol", "You", 59), ("Bob", "Dave", 66),
            ("Dave", "Alice", 63),
        ]
        let state = makeState()
        state.trades = pairs.map { makeTrade($0.0, $0.1, $0.2) }
        let scores = settle(state: state)
        XCTAssertEqual(scores.values.reduce(0, +), 0)
    }

    // MARK: - Leaderboard

    func testLeaderboardSortedHighestFirst() {
        let scores = ["You": 10, "Alice": -5, "Bob": 25, "Carol": 0, "Dave": -15]
        let board = leaderboard(scores: scores)
        XCTAssertEqual(board[0].0, "Bob")
        XCTAssertEqual(board[0].1, 25)
        XCTAssertEqual(board.last!.0, "Dave")
    }

    func testLeaderboardAllZero() {
        let scores = ["You": 0, "Alice": 0, "Bob": 0]
        let board = leaderboard(scores: scores)
        XCTAssertEqual(board.count, 3)
        XCTAssertTrue(board.allSatisfy { $0.1 == 0 })
    }

    func testLeaderboardAllNegative() {
        let scores = ["You": -10, "Alice": -5, "Bob": -20]
        let board = leaderboard(scores: scores)
        XCTAssertEqual(board[0].0, "Alice")  // least negative = winner
    }

    func testLeaderboardSinglePlayer() {
        let scores = ["You": 42]
        let board = leaderboard(scores: scores)
        XCTAssertEqual(board[0].0, "You")
        XCTAssertEqual(board[0].1, 42)
    }

    // MARK: - Trade Breakdown

    func testTradeBreakdownFiltersToPlayerTradesOnly() {
        let state = makeState()
        state.trades = [
            makeTrade("You", "Alice", 60),
            makeTrade("Bob", "Carol", 65),  // doesn't involve You
            makeTrade("Dave", "You", 70),
        ]
        let breakdown = tradeBreakdown(state: state, playerID: "You")
        XCTAssertEqual(breakdown.count, 2)
    }

    func testUninvolvedPlayerGetsEmptyBreakdown() {
        let state = makeState()
        state.trades.append(makeTrade("Alice", "Bob", 60))
        let breakdown = tradeBreakdown(state: state, playerID: "Carol")
        XCTAssertEqual(breakdown.count, 0)
    }

    func testPnlInBreakdownIsCorrect() {
        // You buy at 60, T=79 → pnl = +19
        let state = makeState()
        state.trades.append(makeTrade("You", "Alice", 60))
        let breakdown = tradeBreakdown(state: state, playerID: "You")
        XCTAssertEqual(breakdown.count, 1)
        XCTAssertEqual(breakdown[0].pnl, state.finalTotal() - 60)
    }

    func testBreakdownIncludesBothBuyAndSell() {
        let state = makeState()
        state.trades = [
            makeTrade("You", "Alice", 60),  // You buy
            makeTrade("Bob", "You", 70),    // You sell
        ]
        let breakdown = tradeBreakdown(state: state, playerID: "You")
        XCTAssertEqual(breakdown.count, 2)
    }

    // MARK: - Difficulty

    func testDifficultyCaseIterable() {
        XCTAssertEqual(Difficulty.allCases.count, 3)
    }

    func testDifficultyRawValues() {
        XCTAssertEqual(Difficulty.easy.rawValue, 1)
        XCTAssertEqual(Difficulty.medium.rawValue, 2)
        XCTAssertEqual(Difficulty.hard.rawValue, 3)
    }

    // MARK: - Trade Identity

    func testTradeEqualityIgnoresID() {
        let t1 = Trade(buyer: "You", seller: "Alice", price: 60, phase: .open)
        let t2 = Trade(buyer: "You", seller: "Alice", price: 60, phase: .open)
        XCTAssertEqual(t1, t2)  // Equal by content, despite different UUIDs
    }

    func testTradeInequalityOnPrice() {
        let t1 = Trade(buyer: "You", seller: "Alice", price: 60, phase: .open)
        let t2 = Trade(buyer: "You", seller: "Alice", price: 65, phase: .open)
        XCTAssertNotEqual(t1, t2)
    }

    func testTradeInequalityOnPhase() {
        let t1 = Trade(buyer: "You", seller: "Alice", price: 60, phase: .open)
        let t2 = Trade(buyer: "You", seller: "Alice", price: 60, phase: .reveal1)
        XCTAssertNotEqual(t1, t2)
    }
}
