import XCTest
@testable import MagCry

final class BotTests: XCTestCase {

    // MARK: - Helpers

    /// State for SimpleBot tests. Bot is "Alice" (first player).
    private func makeSimpleState(
        botCard: Int,
        otherCards: [Int] = [7, 5, 6, 8],
        centralCards: [Int] = [9, 10, 11],
        revealedCentral: [Int] = [],
        phase: Phase = .open
    ) -> GameState {
        let playerIDs = ["Alice", "You", "Bob", "Carol", "Dave"]
        let allCards = [botCard] + otherCards
        let privateCards = Dictionary(uniqueKeysWithValues: zip(playerIDs, allCards))
        let state = GameState(
            playerIDs: playerIDs,
            privateCards: privateCards,
            centralCards: centralCards,
            phase: phase
        )
        state.revealedCentral = revealedCentral
        return state
    }

    /// State for StrategicBot tests. Bot is "Bob" (first player).
    private func makeStrategicState(
        botCard: Int,
        otherCards: [Int] = [7, 5, 6, 8],
        centralCards: [Int] = [9, 10, 11],
        revealedCentral: [Int] = [],
        phase: Phase = .open
    ) -> GameState {
        let playerIDs = ["Bob", "You", "Alice", "Carol", "Dave"]
        let allCards = [botCard] + otherCards
        let privateCards = Dictionary(uniqueKeysWithValues: zip(playerIDs, allCards))
        let state = GameState(
            playerIDs: playerIDs,
            privateCards: privateCards,
            centralCards: centralCards,
            phase: phase
        )
        state.revealedCentral = revealedCentral
        return state
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: - BotTracker
    // ═══════════════════════════════════════════════════════════════════════

    func testTrackerRecordsBuyDirection() {
        let tracker = BotTracker()
        tracker.recordTrade(buyer: "You", seller: "Alice", playerOfInterest: "You")
        XCTAssertEqual(tracker.opponentTradeCount("You"), 1)
    }

    func testTrackerRecordsSellDirection() {
        let tracker = BotTracker()
        tracker.recordTrade(buyer: "Alice", seller: "You", playerOfInterest: "You")
        XCTAssertEqual(tracker.opponentTradeCount("You"), 1)
    }

    func testTrackerNeutralWithFewTrades() {
        let tracker = BotTracker()
        tracker.recordTrade(buyer: "You", seller: "Alice", playerOfInterest: "You")
        XCTAssertEqual(tracker.opponentDirection("You"), "neutral")
    }

    func testTrackerBullishAfterManyBuys() {
        let tracker = BotTracker()
        for _ in 0..<4 {
            tracker.recordTrade(buyer: "You", seller: "Alice", playerOfInterest: "You")
        }
        XCTAssertEqual(tracker.opponentDirection("You"), "bullish")
    }

    func testTrackerBearishAfterManySells() {
        let tracker = BotTracker()
        for _ in 0..<4 {
            tracker.recordTrade(buyer: "Alice", seller: "You", playerOfInterest: "You")
        }
        XCTAssertEqual(tracker.opponentDirection("You"), "bearish")
    }

    func testTrackerStreakCounting() {
        let tracker = BotTracker()
        // 3 sells in a row
        for _ in 0..<3 {
            tracker.recordTrade(buyer: "Alice", seller: "You", playerOfInterest: "You")
        }
        XCTAssertEqual(tracker.opponentSameDirectionStreak("You"), 3)
    }

    func testTrackerStreakResetsByDirectionChange() {
        let tracker = BotTracker()
        tracker.recordTrade(buyer: "Alice", seller: "You", playerOfInterest: "You")  // sell
        tracker.recordTrade(buyer: "Alice", seller: "You", playerOfInterest: "You")  // sell
        tracker.recordTrade(buyer: "You", seller: "Alice", playerOfInterest: "You")  // buy (breaks streak)
        XCTAssertEqual(tracker.opponentSameDirectionStreak("You"), 1)
    }

    func testTrackerUnknownPlayerIsNeutral() {
        let tracker = BotTracker()
        XCTAssertEqual(tracker.opponentDirection("Ghost"), "neutral")
        XCTAssertEqual(tracker.opponentTradeCount("Ghost"), 0)
        XCTAssertEqual(tracker.opponentSameDirectionStreak("Ghost"), 0)
    }

    // MARK: - BotTracker: Direct Shifts (Anti-Spam)

    func testDirectShiftZeroByDefault() {
        let tracker = BotTracker(rng: GameRNG(seed: 1))
        XCTAssertEqual(tracker.directShiftFor("You"), 0)
    }

    func testDirectShiftIncreasesWhenOpponentBuys() {
        let tracker = BotTracker(rng: GameRNG(seed: 1))
        tracker.recordDirectTrade(opponent: "You", opponentBought: true)
        let shift = tracker.directShiftFor("You")
        XCTAssertGreaterThanOrEqual(shift, 2)
        XCTAssertLessThanOrEqual(shift, 6)
    }

    func testDirectShiftDecreasesWhenOpponentSells() {
        let tracker = BotTracker(rng: GameRNG(seed: 1))
        tracker.recordDirectTrade(opponent: "You", opponentBought: false)
        let shift = tracker.directShiftFor("You")
        XCTAssertGreaterThanOrEqual(shift, -6)
        XCTAssertLessThanOrEqual(shift, -2)
    }

    func testDirectShiftAccumulatesFromMultipleBuys() {
        let tracker = BotTracker(rng: GameRNG(seed: 42))
        tracker.recordDirectTrade(opponent: "You", opponentBought: true)
        tracker.recordDirectTrade(opponent: "You", opponentBought: true)
        tracker.recordDirectTrade(opponent: "You", opponentBought: true)
        let shift = tracker.directShiftFor("You")
        // 3 buys, each 2-6: total should be 6-18
        XCTAssertGreaterThanOrEqual(shift, 6)
        XCTAssertLessThanOrEqual(shift, 18)
    }

    func testDirectShiftAccumulatesFromMultipleSells() {
        let tracker = BotTracker(rng: GameRNG(seed: 42))
        tracker.recordDirectTrade(opponent: "You", opponentBought: false)
        tracker.recordDirectTrade(opponent: "You", opponentBought: false)
        tracker.recordDirectTrade(opponent: "You", opponentBought: false)
        let shift = tracker.directShiftFor("You")
        // 3 sells, each -2 to -6: total should be -18 to -6
        XCTAssertGreaterThanOrEqual(shift, -18)
        XCTAssertLessThanOrEqual(shift, -6)
    }

    func testDirectShiftPerPlayerIndependent() {
        let tracker = BotTracker(rng: GameRNG(seed: 1))
        tracker.recordDirectTrade(opponent: "You", opponentBought: true)
        tracker.recordDirectTrade(opponent: "Bob", opponentBought: false)
        XCTAssertGreaterThan(tracker.directShiftFor("You"), 0)
        XCTAssertLessThan(tracker.directShiftFor("Bob"), 0)
    }

    func testDecayDirectShiftsReducesByTwo() {
        let tracker = BotTracker(rng: GameRNG(seed: 1))
        // Force a known shift: buy 3 times (will accumulate positive)
        tracker.recordDirectTrade(opponent: "You", opponentBought: true)
        let shiftBefore = tracker.directShiftFor("You")
        XCTAssertGreaterThanOrEqual(shiftBefore, 2)

        tracker.decayDirectShifts()
        let shiftAfter = tracker.directShiftFor("You")
        XCTAssertEqual(shiftAfter, max(0, shiftBefore - 2))
    }

    func testDecayDirectShiftsNeverCrossesZero() {
        let tracker = BotTracker(rng: GameRNG(seed: 1))
        // One small trade (shift 2-6), then decay multiple times
        tracker.recordDirectTrade(opponent: "You", opponentBought: true)
        for _ in 0..<10 {
            tracker.decayDirectShifts()
        }
        XCTAssertGreaterThanOrEqual(tracker.directShiftFor("You"), 0)
    }

    func testDecayDirectShiftsNegativeNeverCrossesZero() {
        let tracker = BotTracker(rng: GameRNG(seed: 1))
        tracker.recordDirectTrade(opponent: "You", opponentBought: false)
        for _ in 0..<10 {
            tracker.decayDirectShifts()
        }
        XCTAssertLessThanOrEqual(tracker.directShiftFor("You"), 0)
    }

    func testPassWithMarketReadBullishShiftsUp() {
        let tracker = BotTracker(rng: GameRNG(seed: 1))
        // Make the opponent bullish (4 buys → bullish direction)
        for _ in 0..<4 {
            tracker.recordTrade(buyer: "You", seller: "Alice", playerOfInterest: "You")
        }
        XCTAssertEqual(tracker.opponentDirection("You"), "bullish")

        tracker.recordPassWithMarketRead(opponent: "You")
        let shift = tracker.directShiftFor("You")
        // Bullish → positive shift of 1-2
        XCTAssertGreaterThanOrEqual(shift, 1)
        XCTAssertLessThanOrEqual(shift, 2)
    }

    func testPassWithMarketReadBearishShiftsDown() {
        let tracker = BotTracker(rng: GameRNG(seed: 1))
        // Make the opponent bearish (4 sells)
        for _ in 0..<4 {
            tracker.recordTrade(buyer: "Alice", seller: "You", playerOfInterest: "You")
        }
        XCTAssertEqual(tracker.opponentDirection("You"), "bearish")

        tracker.recordPassWithMarketRead(opponent: "You")
        let shift = tracker.directShiftFor("You")
        // Bearish → negative shift of -1 to -2
        XCTAssertGreaterThanOrEqual(shift, -2)
        XCTAssertLessThanOrEqual(shift, -1)
    }

    func testPassWithMarketReadNeutralNoShift() {
        let tracker = BotTracker(rng: GameRNG(seed: 1))
        // No trades → neutral
        tracker.recordPassWithMarketRead(opponent: "You")
        XCTAssertEqual(tracker.directShiftFor("You"), 0)
    }

    func testDirectShiftAffectsSimpleBotQuote() {
        let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1))
        let state = makeSimpleState(botCard: 7)

        let qBefore = bot.getQuote(state: state, requester: "You")

        // Simulate 3 buys from "You" → positive shift
        bot.tracker.recordDirectTrade(opponent: "You", opponentBought: true)
        bot.tracker.recordDirectTrade(opponent: "You", opponentBought: true)
        bot.tracker.recordDirectTrade(opponent: "You", opponentBought: true)

        let qAfter = bot.getQuote(state: state, requester: "You")
        // Prices should have shifted up (more expensive for buyer)
        XCTAssertGreaterThan(qAfter.bid, qBefore.bid)
    }

    func testDirectShiftAffectsStrategicBotQuote() {
        let bot = StrategicBot(playerID: "Bob", rng: GameRNG(seed: 1))
        let state = makeStrategicState(botCard: 7)

        let qBefore = bot.getQuote(state: state, requester: "You")

        // Simulate 3 sells from "You" → negative shift
        bot.tracker.recordDirectTrade(opponent: "You", opponentBought: false)
        bot.tracker.recordDirectTrade(opponent: "You", opponentBought: false)
        bot.tracker.recordDirectTrade(opponent: "You", opponentBought: false)

        let qAfter = bot.getQuote(state: state, requester: "You")
        // Prices should have shifted down (worse for seller)
        XCTAssertLessThan(qAfter.bid, qBefore.bid)
    }

    func testDirectShiftDoesNotAffectOtherRequesters() {
        let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1))
        let state = makeSimpleState(botCard: 7)

        // Shift only applies to "You"
        bot.tracker.recordDirectTrade(opponent: "You", opponentBought: true)
        bot.tracker.recordDirectTrade(opponent: "You", opponentBought: true)
        bot.tracker.recordDirectTrade(opponent: "You", opponentBought: true)

        let qForYou = bot.getQuote(state: state, requester: "You")
        let qForBob = bot.getQuote(state: state, requester: "Bob")
        // Bob should get a lower price (no shift)
        XCTAssertGreaterThan(qForYou.bid, qForBob.bid)
    }

    func testDirectShiftSpreadAlways2() {
        let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1))
        let state = makeSimpleState(botCard: 7)

        for _ in 0..<5 {
            bot.tracker.recordDirectTrade(opponent: "You", opponentBought: true)
        }

        let q = bot.getQuote(state: state, requester: "You")
        XCTAssertEqual(q.ask - q.bid, 2)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: - SimpleBot: Quotes
    // ═══════════════════════════════════════════════════════════════════════

    func testSimpleBotSpreadAlways2() {
        let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1))
        for card in [-10, 1, 7, 15, 20] {
            let state = makeSimpleState(botCard: card)
            let q = bot.getQuote(state: state, requester: nil)
            XCTAssertEqual(q.ask - q.bid, 2, "Spread != 2 for card=\(card)")
        }
    }

    func testSimpleBotSpreadAlways2WithRequester() {
        let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1))
        for card in [-10, 1, 7, 15, 20] {
            let state = makeSimpleState(botCard: card)
            let q = bot.getQuote(state: state, requester: "You")
            XCTAssertEqual(q.ask - q.bid, 2, "Spread != 2 with requester for card=\(card)")
        }
    }

    func testSimpleBotQuoteNearEVForAverageCard() {
        let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1))
        let state = makeSimpleState(botCard: 7)
        let q = bot.getQuote(state: state, requester: nil)
        let mid = Double(q.bid + q.ask) / 2.0
        let ev = Deck.expectedTotal(knownCards: [7], unknownCount: 7)
        XCTAssertEqual(mid, ev, accuracy: 2.0)
    }

    func testSimpleBotQuoteHigherForHighCard() {
        let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 2))
        let stateHigh = makeSimpleState(botCard: 20)
        let stateLow = makeSimpleState(botCard: 1)
        let qHigh = bot.getQuote(state: stateHigh, requester: nil)
        let qLow = bot.getQuote(state: stateLow, requester: nil)
        XCTAssertGreaterThan(qHigh.bid, qLow.bid)
    }

    func testSimpleBotQuoteLowerForLowCard() {
        let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 3))
        let stateNeg = makeSimpleState(botCard: -10)
        let statePos = makeSimpleState(botCard: 15)
        let qNeg = bot.getQuote(state: stateNeg, requester: nil)
        let qPos = bot.getQuote(state: statePos, requester: nil)
        XCTAssertLessThan(qNeg.bid, qPos.bid)
    }

    func testSimpleBotQuoteNearBookValueFor20() {
        // Book states: holding 20 → EV ≈ 68
        let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1))
        let state = makeSimpleState(botCard: 20)
        let q = bot.getQuote(state: state, requester: nil)
        let mid = Double(q.bid + q.ask) / 2.0
        XCTAssertEqual(mid, 68.0, accuracy: 3.0)
    }

    func testSimpleBotQuoteNearBookValueForMinus10() {
        // Book states: holding -10 → EV ≈ 51.2
        let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1))
        let state = makeSimpleState(botCard: -10)
        let q = bot.getQuote(state: state, requester: nil)
        let mid = Double(q.bid + q.ask) / 2.0
        XCTAssertEqual(mid, 51.2, accuracy: 3.0)
    }

    func testSimpleBotQuoteUpdatesAfterCentralReveal() {
        let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1))
        let stateBefore = makeSimpleState(botCard: 7, revealedCentral: [])
        let stateAfter = makeSimpleState(
            botCard: 7, centralCards: [15, 9, 8], revealedCentral: [15])
        let qBefore = bot.getQuote(state: stateBefore, requester: nil)
        let qAfter = bot.getQuote(state: stateAfter, requester: nil)
        // Revealing a high card (15) should raise the quote
        XCTAssertGreaterThan(qAfter.bid, qBefore.bid)
    }

    // MARK: - SimpleBot: DecideOnQuote

    func testSimpleBotBuysWhenAskWellBelowEV() {
        // Bot has card=20, EV≈68. Quote ask=55 → huge buy edge.
        let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1))
        let state = makeSimpleState(botCard: 20)
        let decision = bot.decideOnQuote(
            state: state, quoter: "You", quote: Quote(bid: 53, ask: 55))
        XCTAssertEqual(decision, "buy")
    }

    func testSimpleBotSellsWhenBidWellAboveEV() {
        // Bot has card=-10, EV≈51. Quote bid=70 → huge sell edge.
        let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1))
        let state = makeSimpleState(botCard: -10)
        let decision = bot.decideOnQuote(
            state: state, quoter: "You", quote: Quote(bid: 70, ask: 72))
        XCTAssertEqual(decision, "sell")
    }

    func testSimpleBotWalksWhenNoEdge() {
        // Bot has card=7, EV≈61. Quote 60-62 → right at EV, no edge.
        let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1))
        let state = makeSimpleState(botCard: 7)
        let decision = bot.decideOnQuote(
            state: state, quoter: "You", quote: Quote(bid: 60, ask: 62))
        XCTAssertNil(decision)
    }

    func testSimpleBotPrefersBiggerEdge() {
        // EV≈61, bid=80 → sell_edge=19, buy_edge=61-82=-21 (no buy)
        // Should sell
        let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1))
        let state = makeSimpleState(botCard: 7)
        let decision = bot.decideOnQuote(
            state: state, quoter: "You", quote: Quote(bid: 80, ask: 82))
        XCTAssertEqual(decision, "sell")
    }

    // MARK: - SimpleBot: DecideAction

    func testSimpleBotReturnsValidTarget() {
        let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 42))
        let state = makeSimpleState(botCard: 7)
        let target = bot.decideAction(state: state)
        XCTAssertNotNil(target)
        XCTAssertTrue(state.playerIDs.contains(target!))
        XCTAssertNotEqual(target, "Alice")
    }

    func testSimpleBotReturnsNilDuringSettle() {
        let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 42))
        let state = makeSimpleState(botCard: 7, phase: .settle)
        let target = bot.decideAction(state: state)
        XCTAssertNil(target)
    }

    func testSimpleBotNeverReturnsSelf() {
        for seed in 1...100 {
            let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: UInt64(seed)))
            let state = makeSimpleState(botCard: 7)
            let target = bot.decideAction(state: state)
            if let t = target {
                XCTAssertNotEqual(t, "Alice")
            }
        }
    }

    // MARK: - SimpleBot: Easy Mode (No Adaptation)

    func testEasyModeNoAdaptationAfterManySells() {
        let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1), adaptationThreshold: nil)
        let state = makeSimpleState(botCard: 7)

        // Record 10 sells by "You"
        for _ in 0..<10 {
            bot.tracker.recordTrade(buyer: "Alice", seller: "You", playerOfInterest: "You")
        }

        let qBaseline = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1))
            .getQuote(state: state, requester: nil)
        let qAdapted = bot.getQuote(state: state, requester: "You")

        // Quotes should be identical (no adaptation)
        XCTAssertEqual(qBaseline.bid, qAdapted.bid)
    }

    func testEasyModeNoAdaptationAfterManyBuys() {
        let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1), adaptationThreshold: nil)
        let state = makeSimpleState(botCard: 7)

        for _ in 0..<10 {
            bot.tracker.recordTrade(buyer: "You", seller: "Alice", playerOfInterest: "You")
        }

        let qBaseline = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1))
            .getQuote(state: state, requester: nil)
        let qAdapted = bot.getQuote(state: state, requester: "You")
        XCTAssertEqual(qBaseline.bid, qAdapted.bid)
    }

    // MARK: - SimpleBot: Medium Mode (Adaptation)

    func testMediumModeNoAdaptationBelowThreshold() {
        let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1), adaptationThreshold: 5)
        let state = makeSimpleState(botCard: 7)

        // 4 sells (below threshold of 5)
        for _ in 0..<4 {
            bot.tracker.recordTrade(buyer: "Alice", seller: "You", playerOfInterest: "You")
        }

        let qBaseline = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1))
            .getQuote(state: state, requester: nil)
        let q = bot.getQuote(state: state, requester: "You")
        XCTAssertEqual(q.bid, qBaseline.bid)
    }

    func testMediumModeAdaptationKicksInAtThreshold() {
        let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1), adaptationThreshold: 5)
        let state = makeSimpleState(botCard: 7)

        // 6 consecutive sells → streak=6, direction=bearish
        for _ in 0..<6 {
            bot.tracker.recordTrade(buyer: "Alice", seller: "You", playerOfInterest: "You")
        }

        let qBaseline = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1))
            .getQuote(state: state, requester: nil)
        let qAdapted = bot.getQuote(state: state, requester: "You")

        // Bearish → quotes should shift DOWN
        XCTAssertLessThan(qAdapted.bid, qBaseline.bid,
            "After 6 sells, quotes should shift down (bearish adaptation)")
    }

    func testMediumModeAdaptationShiftsUpForBullish() {
        let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1), adaptationThreshold: 5)
        let state = makeSimpleState(botCard: 7)

        // 6 consecutive buys → bullish
        for _ in 0..<6 {
            bot.tracker.recordTrade(buyer: "You", seller: "Alice", playerOfInterest: "You")
        }

        let qBaseline = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1))
            .getQuote(state: state, requester: nil)
        let qAdapted = bot.getQuote(state: state, requester: "You")

        // Bullish → quotes should shift UP
        XCTAssertGreaterThan(qAdapted.bid, qBaseline.bid,
            "After 6 buys, quotes should shift up (bullish adaptation)")
    }

    func testMediumModeNoAdaptationWhenRequesterIsNil() {
        let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1), adaptationThreshold: 5)
        let state = makeSimpleState(botCard: 7)

        for _ in 0..<10 {
            bot.tracker.recordTrade(buyer: "Alice", seller: "You", playerOfInterest: "You")
        }

        let qNoRequester = bot.getQuote(state: state, requester: nil)
        let qBaseline = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1))
            .getQuote(state: state, requester: nil)
        XCTAssertEqual(qNoRequester.bid, qBaseline.bid)
    }

    func testMediumModeAdaptationSpreadStill2() {
        let bot = SimpleBot(playerID: "Alice", rng: GameRNG(seed: 1), adaptationThreshold: 5)
        let state = makeSimpleState(botCard: 7)

        for _ in 0..<20 {
            bot.tracker.recordTrade(buyer: "Alice", seller: "You", playerOfInterest: "You")
        }

        let q = bot.getQuote(state: state, requester: "You")
        XCTAssertEqual(q.ask - q.bid, 2)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: - StrategicBot: Quotes
    // ═══════════════════════════════════════════════════════════════════════

    func testStrategicBotSpreadAlways2() {
        let bot = StrategicBot(playerID: "Bob", rng: GameRNG(seed: 1))
        for card in [-10, 1, 7, 15, 20] {
            let state = makeStrategicState(botCard: card)
            let q = bot.getQuote(state: state, requester: nil)
            XCTAssertEqual(q.ask - q.bid, 2, "Spread != 2 for card=\(card)")
        }
    }

    func testStrategicBotSpreadAlways2WithRequester() {
        let bot = StrategicBot(playerID: "Bob", rng: GameRNG(seed: 1))
        for card in [-10, 1, 7, 15, 20] {
            let state = makeStrategicState(botCard: card)
            let q = bot.getQuote(state: state, requester: "You")
            XCTAssertEqual(q.ask - q.bid, 2)
        }
    }

    func testStrategicBotBluffsLowWhenHighCard() {
        // High card (20) → quote BELOW true EV
        let bot = StrategicBot(playerID: "Bob", rng: GameRNG(seed: 1))
        let state = makeStrategicState(botCard: 20, revealedCentral: [])
        let q = bot.getQuote(state: state, requester: nil)
        let trueEV = Deck.expectedTotal(knownCards: [20], unknownCount: 7)
        let bluffMid = Double(q.bid + q.ask) / 2.0
        XCTAssertLessThan(bluffMid, trueEV,
            "High-card bluff should quote below EV=\(String(format: "%.1f", trueEV)), got mid=\(bluffMid)")
    }

    func testStrategicBotBluffsHighWhenLowCard() {
        // Low card (-10) → quote ABOVE true EV
        let bot = StrategicBot(playerID: "Bob", rng: GameRNG(seed: 1))
        let state = makeStrategicState(botCard: -10, revealedCentral: [])
        let q = bot.getQuote(state: state, requester: nil)
        let trueEV = Deck.expectedTotal(knownCards: [-10], unknownCount: 7)
        let bluffMid = Double(q.bid + q.ask) / 2.0
        XCTAssertGreaterThan(bluffMid, trueEV,
            "Low-card bluff should quote above EV=\(String(format: "%.1f", trueEV)), got mid=\(bluffMid)")
    }

    func testStrategicBotBluffOffsetIsLargeEarly() {
        let bot = StrategicBot(playerID: "Bob", rng: GameRNG(seed: 1))
        let state = makeStrategicState(botCard: 20, revealedCentral: [])
        let q = bot.getQuote(state: state, requester: nil)
        let trueEV = Deck.expectedTotal(knownCards: [20], unknownCount: 7)
        let bluffMid = Double(q.bid + q.ask) / 2.0
        let offset = trueEV - bluffMid  // positive for high-card bluff
        // bluffOffsetEarly = 8, allow noise margin
        XCTAssertGreaterThanOrEqual(offset, 8.0 - 1.5,
            "Early bluff offset should be ~8, got \(String(format: "%.1f", offset))")
    }

    func testStrategicBotBluffConvergesAfterTwoReveals() {
        let bot = StrategicBot(playerID: "Bob", rng: GameRNG(seed: 1))
        let stateEarly = makeStrategicState(botCard: 20, revealedCentral: [])
        let stateLate = makeStrategicState(
            botCard: 20, centralCards: [5, 6, 9], revealedCentral: [5, 6])

        let qEarly = bot.getQuote(state: stateEarly, requester: nil)
        let qLate = bot.getQuote(state: stateLate, requester: nil)

        let midEarly = Double(qEarly.bid + qEarly.ask) / 2.0
        let midLate = Double(qLate.bid + qLate.ask) / 2.0

        let trueEVEarly = Deck.expectedTotal(knownCards: [20], unknownCount: 7)
        let trueEVLate = Deck.expectedTotal(knownCards: [20, 5, 6], unknownCount: 5)

        let offsetEarly = abs(midEarly - trueEVEarly)
        let offsetLate = abs(midLate - trueEVLate)

        XCTAssertGreaterThan(offsetEarly, offsetLate,
            "Bluff offset should shrink as more central cards are revealed")
    }

    func testStrategicBotLateBluffOffsetIsSmall() {
        let bot = StrategicBot(playerID: "Bob", rng: GameRNG(seed: 1))
        let state = makeStrategicState(
            botCard: 20, centralCards: [5, 6, 9], revealedCentral: [5, 6])
        let q = bot.getQuote(state: state, requester: nil)
        let trueEV = Deck.expectedTotal(knownCards: [20, 5, 6], unknownCount: 5)
        let mid = Double(q.bid + q.ask) / 2.0
        let offset = abs(mid - trueEV)
        // bluffOffsetLate = 1, allow noise margin
        XCTAssertLessThanOrEqual(offset, 1.0 + 1.5,
            "Late bluff offset should be ~1, got \(String(format: "%.1f", offset))")
    }

    // MARK: - StrategicBot: DecideOnQuote

    func testStrategicBotBuysWhenHighCardAndAskBelowEV() {
        let bot = StrategicBot(playerID: "Bob", rng: GameRNG(seed: 1))
        let state = makeStrategicState(botCard: 20)
        // EV ≈ 68, ask = 57 → buy edge ≈ 11
        let decision = bot.decideOnQuote(
            state: state, quoter: "You", quote: Quote(bid: 55, ask: 57))
        XCTAssertEqual(decision, "buy")
    }

    func testStrategicBotSellsWhenLowCardAndBidAboveEV() {
        let bot = StrategicBot(playerID: "Bob", rng: GameRNG(seed: 1))
        let state = makeStrategicState(botCard: -10)
        // EV ≈ 51, bid = 68 → sell edge ≈ 17
        let decision = bot.decideOnQuote(
            state: state, quoter: "You", quote: Quote(bid: 68, ask: 70))
        XCTAssertEqual(decision, "sell")
    }

    func testStrategicBotWalksWhenNoEdge() {
        let bot = StrategicBot(playerID: "Bob", rng: GameRNG(seed: 1))
        let state = makeStrategicState(botCard: 20)
        // EV ≈ 68, ask = 72 → buy edge = -4 (negative)
        let decision = bot.decideOnQuote(
            state: state, quoter: "You", quote: Quote(bid: 70, ask: 72))
        XCTAssertNil(decision)
    }

    func testStrategicBotOpportunisticSellEvenWithHighCard() {
        // High-card bot should still sell if bid is WAY above EV
        let bot = StrategicBot(playerID: "Bob", rng: GameRNG(seed: 1))
        let state = makeStrategicState(botCard: 20)
        // EV ≈ 68, bid = 80 → sell edge = 12, exceeds 3x threshold
        let decision = bot.decideOnQuote(
            state: state, quoter: "You", quote: Quote(bid: 80, ask: 82))
        XCTAssertEqual(decision, "sell")
    }

    // MARK: - StrategicBot: DecideAction

    func testStrategicBotReturnsValidTarget() {
        let bot = StrategicBot(playerID: "Bob", rng: GameRNG(seed: 42))
        let state = makeStrategicState(botCard: 20)
        let target = bot.decideAction(state: state)
        XCTAssertNotNil(target)
        XCTAssertTrue(state.playerIDs.contains(target!))
        XCTAssertNotEqual(target, "Bob")
    }

    func testStrategicBotReturnsNilDuringSettle() {
        let bot = StrategicBot(playerID: "Bob", rng: GameRNG(seed: 1))
        let state = makeStrategicState(botCard: 20, phase: .settle)
        let target = bot.decideAction(state: state)
        XCTAssertNil(target)
    }

    func testStrategicBotNeverReturnsSelf() {
        for seed in 1...100 {
            let bot = StrategicBot(playerID: "Bob", rng: GameRNG(seed: UInt64(seed)))
            let state = makeStrategicState(botCard: 20)
            let target = bot.decideAction(state: state)
            if let t = target {
                XCTAssertNotEqual(t, "Bob")
            }
        }
    }

    func testStrategicBotMaxTradesPerTurnRespected() {
        // Bot should stop acting after 3 actions in one phase
        let bot = StrategicBot(playerID: "Bob", rng: GameRNG(seed: 1))
        let state = makeStrategicState(botCard: 20)

        var actionsTaken = 0
        for _ in 0..<8 {
            let target = bot.decideAction(state: state)
            if target == nil { break }
            actionsTaken += 1
        }
        XCTAssertLessThanOrEqual(actionsTaken, 3)
    }

    func testStrategicBotPhaseChangeResetsTradeCount() {
        let bot = StrategicBot(playerID: "Bob", rng: GameRNG(seed: 1))
        let state = makeStrategicState(botCard: 20)

        // Exhaust trade allowance
        for _ in 0..<5 {
            _ = bot.decideAction(state: state)
        }

        let targetBeforeReset = bot.decideAction(state: state)
        XCTAssertNil(targetBeforeReset)

        // Reset via phase change
        state.phase = .reveal1
        bot.onPhaseChange(state: state)

        let targetAfterReset = bot.decideAction(state: state)
        XCTAssertNotNil(targetAfterReset)
    }

    // MARK: - StrategicBot: Adaptation

    func testStrategicBotNoAdaptationBelowThreshold() {
        let bot = StrategicBot(playerID: "Bob", rng: GameRNG(seed: 1))
        let state = makeStrategicState(botCard: 7)

        // 1 sell (below threshold of 2)
        bot.tracker.recordTrade(buyer: "Bob", seller: "You", playerOfInterest: "You")

        let qBaseline = StrategicBot(playerID: "Bob", rng: GameRNG(seed: 1))
            .getQuote(state: state, requester: nil)
        let q = bot.getQuote(state: state, requester: "You")
        XCTAssertEqual(q.bid, qBaseline.bid)
    }

    func testStrategicBotAdaptationKicksInAggressively() {
        let bot = StrategicBot(playerID: "Bob", rng: GameRNG(seed: 1))
        let state = makeStrategicState(botCard: 7)

        // 4 consecutive sells → bearish, streak=4
        for _ in 0..<4 {
            bot.tracker.recordTrade(buyer: "Bob", seller: "You", playerOfInterest: "You")
        }

        let qBaseline = StrategicBot(playerID: "Bob", rng: GameRNG(seed: 1))
            .getQuote(state: state, requester: nil)
        let qAdapted = bot.getQuote(state: state, requester: "You")

        // Bearish → quotes shift DOWN
        XCTAssertLessThan(qAdapted.bid, qBaseline.bid,
            "Strategic bot should adapt aggressively after sells")
    }

    func testStrategicBotAdaptationShiftsUpForBullish() {
        let bot = StrategicBot(playerID: "Bob", rng: GameRNG(seed: 1))
        let state = makeStrategicState(botCard: 7)

        for _ in 0..<4 {
            bot.tracker.recordTrade(buyer: "You", seller: "Bob", playerOfInterest: "You")
        }

        let qBaseline = StrategicBot(playerID: "Bob", rng: GameRNG(seed: 1))
            .getQuote(state: state, requester: nil)
        let qAdapted = bot.getQuote(state: state, requester: "You")

        // Bullish → quotes shift UP
        XCTAssertGreaterThan(qAdapted.bid, qBaseline.bid,
            "Strategic bot should shift quotes up for bullish requester")
    }

    func testStrategicBotAdaptationSpreadStill2() {
        let bot = StrategicBot(playerID: "Bob", rng: GameRNG(seed: 1))
        let state = makeStrategicState(botCard: 7)

        for _ in 0..<20 {
            bot.tracker.recordTrade(buyer: "Bob", seller: "You", playerOfInterest: "You")
        }

        let q = bot.getQuote(state: state, requester: "You")
        XCTAssertEqual(q.ask - q.bid, 2)
    }

    // MARK: - StrategicBot vs SimpleBot Comparison

    func testHighCardStrategicQuotesLowerThanSimple() {
        // Average over many seeds so bluff direction dominates noise
        var simpleMids: [Double] = []
        var strategicMids: [Double] = []

        for seed in 1...50 {
            let s = UInt64(seed)
            let simpleBot = SimpleBot(playerID: "Bob", rng: GameRNG(seed: s))
            let strategicBot = StrategicBot(playerID: "Bob", rng: GameRNG(seed: s))
            let state = makeStrategicState(botCard: 20, revealedCentral: [])

            let qSimple = simpleBot.getQuote(state: state, requester: nil)
            let qStrategic = strategicBot.getQuote(state: state, requester: nil)

            simpleMids.append(Double(qSimple.bid + qSimple.ask) / 2.0)
            strategicMids.append(Double(qStrategic.bid + qStrategic.ask) / 2.0)
        }

        let avgSimple = simpleMids.reduce(0, +) / Double(simpleMids.count)
        let avgStrategic = strategicMids.reduce(0, +) / Double(strategicMids.count)

        XCTAssertLessThan(avgStrategic, avgSimple,
            "High-card strategic avg mid \(String(format: "%.1f", avgStrategic)) should be "
            + "below simple avg mid \(String(format: "%.1f", avgSimple))")
    }

    func testLowCardStrategicQuotesHigherThanSimple() {
        var simpleMids: [Double] = []
        var strategicMids: [Double] = []

        for seed in 1...50 {
            let s = UInt64(seed)
            let simpleBot = SimpleBot(playerID: "Bob", rng: GameRNG(seed: s))
            let strategicBot = StrategicBot(playerID: "Bob", rng: GameRNG(seed: s))
            let state = makeStrategicState(botCard: -10, revealedCentral: [])

            let qSimple = simpleBot.getQuote(state: state, requester: nil)
            let qStrategic = strategicBot.getQuote(state: state, requester: nil)

            simpleMids.append(Double(qSimple.bid + qSimple.ask) / 2.0)
            strategicMids.append(Double(qStrategic.bid + qStrategic.ask) / 2.0)
        }

        let avgSimple = simpleMids.reduce(0, +) / Double(simpleMids.count)
        let avgStrategic = strategicMids.reduce(0, +) / Double(strategicMids.count)

        XCTAssertGreaterThan(avgStrategic, avgSimple,
            "Low-card strategic avg mid \(String(format: "%.1f", avgStrategic)) should be "
            + "above simple avg mid \(String(format: "%.1f", avgSimple))")
    }
}
