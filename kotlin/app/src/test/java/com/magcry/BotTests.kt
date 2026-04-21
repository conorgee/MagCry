package com.magcry

import com.magcry.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BotTests {

    // -- Helpers --

    private fun makeSimpleState(
        botCard: Int,
        otherCards: List<Int> = listOf(7, 5, 6, 8),
        centralCards: List<Int> = listOf(9, 10, 11),
        revealedCentral: List<Int> = emptyList(),
        phase: Phase = Phase.OPEN
    ): GameState {
        val playerIDs = listOf("Alice", "You", "Bob", "Carol", "Dave")
        val allCards = listOf(botCard) + otherCards
        val privateCards = playerIDs.zip(allCards).toMap()
        val state = GameState(playerIDs, privateCards, centralCards, phase)
        state.revealedCentral = revealedCentral.toMutableList()
        return state
    }

    private fun makeStrategicState(
        botCard: Int,
        otherCards: List<Int> = listOf(7, 5, 6, 8),
        centralCards: List<Int> = listOf(9, 10, 11),
        revealedCentral: List<Int> = emptyList(),
        phase: Phase = Phase.OPEN
    ): GameState {
        val playerIDs = listOf("Bob", "You", "Alice", "Carol", "Dave")
        val allCards = listOf(botCard) + otherCards
        val privateCards = playerIDs.zip(allCards).toMap()
        val state = GameState(playerIDs, privateCards, centralCards, phase)
        state.revealedCentral = revealedCentral.toMutableList()
        return state
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BotTracker
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun testTrackerRecordsBuyDirection() {
        val tracker = BotTracker()
        tracker.recordTrade("You", "Alice", "You")
        assertEquals(1, tracker.opponentTradeCount("You"))
    }

    @Test fun testTrackerRecordsSellDirection() {
        val tracker = BotTracker()
        tracker.recordTrade("Alice", "You", "You")
        assertEquals(1, tracker.opponentTradeCount("You"))
    }

    @Test fun testTrackerNeutralWithFewTrades() {
        val tracker = BotTracker()
        tracker.recordTrade("You", "Alice", "You")
        assertEquals("neutral", tracker.opponentDirection("You"))
    }

    @Test fun testTrackerBullishAfterManyBuys() {
        val tracker = BotTracker()
        repeat(4) { tracker.recordTrade("You", "Alice", "You") }
        assertEquals("bullish", tracker.opponentDirection("You"))
    }

    @Test fun testTrackerBearishAfterManySells() {
        val tracker = BotTracker()
        repeat(4) { tracker.recordTrade("Alice", "You", "You") }
        assertEquals("bearish", tracker.opponentDirection("You"))
    }

    @Test fun testTrackerStreakCounting() {
        val tracker = BotTracker()
        repeat(3) { tracker.recordTrade("Alice", "You", "You") }
        assertEquals(3, tracker.opponentSameDirectionStreak("You"))
    }

    @Test fun testTrackerStreakResetsByDirectionChange() {
        val tracker = BotTracker()
        tracker.recordTrade("Alice", "You", "You")  // sell
        tracker.recordTrade("Alice", "You", "You")  // sell
        tracker.recordTrade("You", "Alice", "You")  // buy (breaks streak)
        assertEquals(1, tracker.opponentSameDirectionStreak("You"))
    }

    @Test fun testTrackerUnknownPlayerIsNeutral() {
        val tracker = BotTracker()
        assertEquals("neutral", tracker.opponentDirection("Ghost"))
        assertEquals(0, tracker.opponentTradeCount("Ghost"))
        assertEquals(0, tracker.opponentSameDirectionStreak("Ghost"))
    }

    // -- BotTracker: Direct Shifts --

    @Test fun testDirectShiftZeroByDefault() {
        val tracker = BotTracker(GameRNG(1uL))
        assertEquals(0, tracker.directShiftFor("You"))
    }

    @Test fun testDirectShiftIncreasesWhenOpponentBuys() {
        val tracker = BotTracker(GameRNG(1uL))
        tracker.recordDirectTrade("You", opponentBought = true)
        val shift = tracker.directShiftFor("You")
        assertTrue(shift in 2..6)
    }

    @Test fun testDirectShiftDecreasesWhenOpponentSells() {
        val tracker = BotTracker(GameRNG(1uL))
        tracker.recordDirectTrade("You", opponentBought = false)
        val shift = tracker.directShiftFor("You")
        assertTrue(shift in -6..-2)
    }

    @Test fun testDirectShiftAccumulatesFromMultipleBuys() {
        val tracker = BotTracker(GameRNG(42uL))
        repeat(3) { tracker.recordDirectTrade("You", opponentBought = true) }
        val shift = tracker.directShiftFor("You")
        assertTrue(shift in 6..18)
    }

    @Test fun testDirectShiftAccumulatesFromMultipleSells() {
        val tracker = BotTracker(GameRNG(42uL))
        repeat(3) { tracker.recordDirectTrade("You", opponentBought = false) }
        val shift = tracker.directShiftFor("You")
        assertTrue(shift in -18..-6)
    }

    @Test fun testDirectShiftPerPlayerIndependent() {
        val tracker = BotTracker(GameRNG(1uL))
        tracker.recordDirectTrade("You", opponentBought = true)
        tracker.recordDirectTrade("Bob", opponentBought = false)
        assertTrue(tracker.directShiftFor("You") > 0)
        assertTrue(tracker.directShiftFor("Bob") < 0)
    }

    @Test fun testDecayDirectShiftsReducesByTwo() {
        val tracker = BotTracker(GameRNG(1uL))
        tracker.recordDirectTrade("You", opponentBought = true)
        val shiftBefore = tracker.directShiftFor("You")
        assertTrue(shiftBefore >= 2)
        tracker.decayDirectShifts()
        val shiftAfter = tracker.directShiftFor("You")
        assertEquals(maxOf(0, shiftBefore - 2), shiftAfter)
    }

    @Test fun testDecayDirectShiftsNeverCrossesZero() {
        val tracker = BotTracker(GameRNG(1uL))
        tracker.recordDirectTrade("You", opponentBought = true)
        repeat(10) { tracker.decayDirectShifts() }
        assertTrue(tracker.directShiftFor("You") >= 0)
    }

    @Test fun testDecayDirectShiftsNegativeNeverCrossesZero() {
        val tracker = BotTracker(GameRNG(1uL))
        tracker.recordDirectTrade("You", opponentBought = false)
        repeat(10) { tracker.decayDirectShifts() }
        assertTrue(tracker.directShiftFor("You") <= 0)
    }

    @Test fun testPassWithMarketReadBullishShiftsUp() {
        val tracker = BotTracker(GameRNG(1uL))
        repeat(4) { tracker.recordTrade("You", "Alice", "You") }
        assertEquals("bullish", tracker.opponentDirection("You"))
        tracker.recordPassWithMarketRead("You")
        val shift = tracker.directShiftFor("You")
        assertTrue(shift in 1..2)
    }

    @Test fun testPassWithMarketReadBearishShiftsDown() {
        val tracker = BotTracker(GameRNG(1uL))
        repeat(4) { tracker.recordTrade("Alice", "You", "You") }
        assertEquals("bearish", tracker.opponentDirection("You"))
        tracker.recordPassWithMarketRead("You")
        val shift = tracker.directShiftFor("You")
        assertTrue(shift in -2..-1)
    }

    @Test fun testPassWithMarketReadNeutralNoShift() {
        val tracker = BotTracker(GameRNG(1uL))
        tracker.recordPassWithMarketRead("You")
        assertEquals(0, tracker.directShiftFor("You"))
    }

    @Test fun testDirectShiftAffectsSimpleBotQuote() {
        val bot = SimpleBot("Alice", GameRNG(1uL))
        val state = makeSimpleState(botCard = 7)
        val qBefore = bot.getQuote(state, "You")
        repeat(3) { bot.tracker.recordDirectTrade("You", opponentBought = true) }
        val qAfter = bot.getQuote(state, "You")
        assertTrue(qAfter.bid > qBefore.bid)
    }

    @Test fun testDirectShiftAffectsStrategicBotQuote() {
        val bot = StrategicBot("Bob", GameRNG(1uL))
        val state = makeStrategicState(botCard = 7)
        val qBefore = bot.getQuote(state, "You")
        repeat(3) { bot.tracker.recordDirectTrade("You", opponentBought = false) }
        val qAfter = bot.getQuote(state, "You")
        assertTrue(qAfter.bid < qBefore.bid)
    }

    @Test fun testDirectShiftDoesNotAffectOtherRequesters() {
        val bot = SimpleBot("Alice", GameRNG(1uL))
        val state = makeSimpleState(botCard = 7)
        repeat(3) { bot.tracker.recordDirectTrade("You", opponentBought = true) }
        val qForYou = bot.getQuote(state, "You")
        val qForBob = bot.getQuote(state, "Bob")
        assertTrue(qForYou.bid > qForBob.bid)
    }

    @Test fun testDirectShiftSpreadAlways2() {
        val bot = SimpleBot("Alice", GameRNG(1uL))
        val state = makeSimpleState(botCard = 7)
        repeat(5) { bot.tracker.recordDirectTrade("You", opponentBought = true) }
        val q = bot.getQuote(state, "You")
        assertEquals(2, q.ask - q.bid)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SimpleBot: Quotes
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun testSimpleBotSpreadAlways2() {
        val bot = SimpleBot("Alice", GameRNG(1uL))
        for (card in listOf(-10, 1, 7, 15, 20)) {
            val state = makeSimpleState(botCard = card)
            val q = bot.getQuote(state, null)
            assertEquals(2, q.ask - q.bid, "Spread != 2 for card=$card")
        }
    }

    @Test fun testSimpleBotSpreadAlways2WithRequester() {
        val bot = SimpleBot("Alice", GameRNG(1uL))
        for (card in listOf(-10, 1, 7, 15, 20)) {
            val state = makeSimpleState(botCard = card)
            val q = bot.getQuote(state, "You")
            assertEquals(2, q.ask - q.bid)
        }
    }

    @Test fun testSimpleBotQuoteNearEVForAverageCard() {
        val bot = SimpleBot("Alice", GameRNG(1uL))
        val state = makeSimpleState(botCard = 7)
        val q = bot.getQuote(state, null)
        val mid = (q.bid + q.ask) / 2.0
        val ev = Deck.expectedTotal(listOf(7), 7)
        assertEquals(ev, mid, 2.0)
    }

    @Test fun testSimpleBotQuoteHigherForHighCard() {
        val bot = SimpleBot("Alice", GameRNG(2uL))
        val stateHigh = makeSimpleState(botCard = 20)
        val stateLow = makeSimpleState(botCard = 1)
        val qHigh = bot.getQuote(stateHigh, null)
        val qLow = bot.getQuote(stateLow, null)
        assertTrue(qHigh.bid > qLow.bid)
    }

    @Test fun testSimpleBotQuoteLowerForLowCard() {
        val bot = SimpleBot("Alice", GameRNG(3uL))
        val stateNeg = makeSimpleState(botCard = -10)
        val statePos = makeSimpleState(botCard = 15)
        val qNeg = bot.getQuote(stateNeg, null)
        val qPos = bot.getQuote(statePos, null)
        assertTrue(qNeg.bid < qPos.bid)
    }

    @Test fun testSimpleBotQuoteNearBookValueFor20() {
        val bot = SimpleBot("Alice", GameRNG(1uL))
        val state = makeSimpleState(botCard = 20)
        val q = bot.getQuote(state, null)
        val mid = (q.bid + q.ask) / 2.0
        assertEquals(68.0, mid, 3.0)
    }

    @Test fun testSimpleBotQuoteNearBookValueForMinus10() {
        val bot = SimpleBot("Alice", GameRNG(1uL))
        val state = makeSimpleState(botCard = -10)
        val q = bot.getQuote(state, null)
        val mid = (q.bid + q.ask) / 2.0
        assertEquals(51.2, mid, 3.0)
    }

    @Test fun testSimpleBotQuoteUpdatesAfterCentralReveal() {
        val bot = SimpleBot("Alice", GameRNG(1uL))
        val stateBefore = makeSimpleState(botCard = 7, revealedCentral = emptyList())
        val stateAfter = makeSimpleState(
            botCard = 7, centralCards = listOf(15, 9, 8), revealedCentral = listOf(15)
        )
        val qBefore = bot.getQuote(stateBefore, null)
        val qAfter = bot.getQuote(stateAfter, null)
        assertTrue(qAfter.bid > qBefore.bid)
    }

    // SimpleBot: DecideOnQuote

    @Test fun testSimpleBotBuysWhenAskWellBelowEV() {
        val bot = SimpleBot("Alice", GameRNG(1uL))
        val state = makeSimpleState(botCard = 20)
        val decision = bot.decideOnQuote(state, "You", Quote(53, 55))
        assertEquals("buy", decision)
    }

    @Test fun testSimpleBotSellsWhenBidWellAboveEV() {
        val bot = SimpleBot("Alice", GameRNG(1uL))
        val state = makeSimpleState(botCard = -10)
        val decision = bot.decideOnQuote(state, "You", Quote(70, 72))
        assertEquals("sell", decision)
    }

    @Test fun testSimpleBotWalksWhenNoEdge() {
        val bot = SimpleBot("Alice", GameRNG(1uL))
        val state = makeSimpleState(botCard = 7)
        val decision = bot.decideOnQuote(state, "You", Quote(60, 62))
        assertNull(decision)
    }

    @Test fun testSimpleBotPrefersBiggerEdge() {
        val bot = SimpleBot("Alice", GameRNG(1uL))
        val state = makeSimpleState(botCard = 7)
        val decision = bot.decideOnQuote(state, "You", Quote(80, 82))
        assertEquals("sell", decision)
    }

    // SimpleBot: DecideAction

    @Test fun testSimpleBotReturnsValidTarget() {
        val bot = SimpleBot("Alice", GameRNG(42uL))
        val state = makeSimpleState(botCard = 7)
        val target = bot.decideAction(state)
        assertNotNull(target)
        assertTrue(target!! in state.playerIDs)
        assertNotEquals("Alice", target)
    }

    @Test fun testSimpleBotReturnsNilDuringSettle() {
        val bot = SimpleBot("Alice", GameRNG(42uL))
        val state = makeSimpleState(botCard = 7, phase = Phase.SETTLE)
        assertNull(bot.decideAction(state))
    }

    @Test fun testSimpleBotNeverReturnsSelf() {
        for (seed in 1..100) {
            val bot = SimpleBot("Alice", GameRNG(seed.toULong()))
            val state = makeSimpleState(botCard = 7)
            val target = bot.decideAction(state)
            if (target != null) assertNotEquals("Alice", target)
        }
    }

    // SimpleBot: Easy Mode (No Adaptation)

    @Test fun testEasyModeNoAdaptationAfterManySells() {
        val bot = SimpleBot("Alice", GameRNG(1uL), adaptationThreshold = null)
        val state = makeSimpleState(botCard = 7)
        repeat(10) { bot.tracker.recordTrade("Alice", "You", "You") }
        val qBaseline = SimpleBot("Alice", GameRNG(1uL)).getQuote(state, null)
        val qAdapted = bot.getQuote(state, "You")
        assertEquals(qBaseline.bid, qAdapted.bid)
    }

    @Test fun testEasyModeNoAdaptationAfterManyBuys() {
        val bot = SimpleBot("Alice", GameRNG(1uL), adaptationThreshold = null)
        val state = makeSimpleState(botCard = 7)
        repeat(10) { bot.tracker.recordTrade("You", "Alice", "You") }
        val qBaseline = SimpleBot("Alice", GameRNG(1uL)).getQuote(state, null)
        val qAdapted = bot.getQuote(state, "You")
        assertEquals(qBaseline.bid, qAdapted.bid)
    }

    // SimpleBot: Medium Mode (Adaptation)

    @Test fun testMediumModeNoAdaptationBelowThreshold() {
        val bot = SimpleBot("Alice", GameRNG(1uL), adaptationThreshold = 5)
        val state = makeSimpleState(botCard = 7)
        repeat(4) { bot.tracker.recordTrade("Alice", "You", "You") }
        val qBaseline = SimpleBot("Alice", GameRNG(1uL)).getQuote(state, null)
        val q = bot.getQuote(state, "You")
        assertEquals(qBaseline.bid, q.bid)
    }

    @Test fun testMediumModeAdaptationKicksInAtThreshold() {
        val bot = SimpleBot("Alice", GameRNG(1uL), adaptationThreshold = 5)
        val state = makeSimpleState(botCard = 7)
        repeat(6) { bot.tracker.recordTrade("Alice", "You", "You") }
        val qBaseline = SimpleBot("Alice", GameRNG(1uL)).getQuote(state, null)
        val qAdapted = bot.getQuote(state, "You")
        assertTrue(qAdapted.bid < qBaseline.bid)
    }

    @Test fun testMediumModeAdaptationShiftsUpForBullish() {
        val bot = SimpleBot("Alice", GameRNG(1uL), adaptationThreshold = 5)
        val state = makeSimpleState(botCard = 7)
        repeat(6) { bot.tracker.recordTrade("You", "Alice", "You") }
        val qBaseline = SimpleBot("Alice", GameRNG(1uL)).getQuote(state, null)
        val qAdapted = bot.getQuote(state, "You")
        assertTrue(qAdapted.bid > qBaseline.bid)
    }

    @Test fun testMediumModeNoAdaptationWhenRequesterIsNil() {
        val bot = SimpleBot("Alice", GameRNG(1uL), adaptationThreshold = 5)
        val state = makeSimpleState(botCard = 7)
        repeat(10) { bot.tracker.recordTrade("Alice", "You", "You") }
        val qNoRequester = bot.getQuote(state, null)
        val qBaseline = SimpleBot("Alice", GameRNG(1uL)).getQuote(state, null)
        assertEquals(qBaseline.bid, qNoRequester.bid)
    }

    @Test fun testMediumModeAdaptationSpreadStill2() {
        val bot = SimpleBot("Alice", GameRNG(1uL), adaptationThreshold = 5)
        val state = makeSimpleState(botCard = 7)
        repeat(20) { bot.tracker.recordTrade("Alice", "You", "You") }
        val q = bot.getQuote(state, "You")
        assertEquals(2, q.ask - q.bid)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // StrategicBot: Quotes
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun testStrategicBotSpreadAlways2() {
        val bot = StrategicBot("Bob", GameRNG(1uL))
        for (card in listOf(-10, 1, 7, 15, 20)) {
            val state = makeStrategicState(botCard = card)
            val q = bot.getQuote(state, null)
            assertEquals(2, q.ask - q.bid, "Spread != 2 for card=$card")
        }
    }

    @Test fun testStrategicBotSpreadAlways2WithRequester() {
        val bot = StrategicBot("Bob", GameRNG(1uL))
        for (card in listOf(-10, 1, 7, 15, 20)) {
            val state = makeStrategicState(botCard = card)
            val q = bot.getQuote(state, "You")
            assertEquals(2, q.ask - q.bid)
        }
    }

    @Test fun testStrategicBotBluffsLowWhenHighCard() {
        val bot = StrategicBot("Bob", GameRNG(1uL))
        val state = makeStrategicState(botCard = 20, revealedCentral = emptyList())
        val q = bot.getQuote(state, null)
        val trueEV = Deck.expectedTotal(listOf(20), 7)
        val bluffMid = (q.bid + q.ask) / 2.0
        assertTrue(bluffMid < trueEV)
    }

    @Test fun testStrategicBotBluffsHighWhenLowCard() {
        val bot = StrategicBot("Bob", GameRNG(1uL))
        val state = makeStrategicState(botCard = -10, revealedCentral = emptyList())
        val q = bot.getQuote(state, null)
        val trueEV = Deck.expectedTotal(listOf(-10), 7)
        val bluffMid = (q.bid + q.ask) / 2.0
        assertTrue(bluffMid > trueEV)
    }

    @Test fun testStrategicBotBluffOffsetIsLargeEarly() {
        val bot = StrategicBot("Bob", GameRNG(1uL))
        val state = makeStrategicState(botCard = 20, revealedCentral = emptyList())
        val q = bot.getQuote(state, null)
        val trueEV = Deck.expectedTotal(listOf(20), 7)
        val bluffMid = (q.bid + q.ask) / 2.0
        val offset = trueEV - bluffMid
        assertTrue(offset >= 8.0 - 1.5)
    }

    @Test fun testStrategicBotBluffConvergesAfterTwoReveals() {
        val bot = StrategicBot("Bob", GameRNG(1uL))
        val stateEarly = makeStrategicState(botCard = 20, revealedCentral = emptyList())
        val stateLate = makeStrategicState(
            botCard = 20, centralCards = listOf(5, 6, 9), revealedCentral = listOf(5, 6)
        )
        val qEarly = bot.getQuote(stateEarly, null)
        val qLate = bot.getQuote(stateLate, null)
        val midEarly = (qEarly.bid + qEarly.ask) / 2.0
        val midLate = (qLate.bid + qLate.ask) / 2.0
        val trueEVEarly = Deck.expectedTotal(listOf(20), 7)
        val trueEVLate = Deck.expectedTotal(listOf(20, 5, 6), 5)
        val offsetEarly = kotlin.math.abs(midEarly - trueEVEarly)
        val offsetLate = kotlin.math.abs(midLate - trueEVLate)
        assertTrue(offsetEarly > offsetLate)
    }

    @Test fun testStrategicBotLateBluffOffsetIsSmall() {
        val bot = StrategicBot("Bob", GameRNG(1uL))
        val state = makeStrategicState(
            botCard = 20, centralCards = listOf(5, 6, 9), revealedCentral = listOf(5, 6)
        )
        val q = bot.getQuote(state, null)
        val trueEV = Deck.expectedTotal(listOf(20, 5, 6), 5)
        val mid = (q.bid + q.ask) / 2.0
        val offset = kotlin.math.abs(mid - trueEV)
        assertTrue(offset <= 1.0 + 1.5)
    }

    // StrategicBot: DecideOnQuote

    @Test fun testStrategicBotBuysWhenHighCardAndAskBelowEV() {
        val bot = StrategicBot("Bob", GameRNG(1uL))
        val state = makeStrategicState(botCard = 20)
        val decision = bot.decideOnQuote(state, "You", Quote(55, 57))
        assertEquals("buy", decision)
    }

    @Test fun testStrategicBotSellsWhenLowCardAndBidAboveEV() {
        val bot = StrategicBot("Bob", GameRNG(1uL))
        val state = makeStrategicState(botCard = -10)
        val decision = bot.decideOnQuote(state, "You", Quote(68, 70))
        assertEquals("sell", decision)
    }

    @Test fun testStrategicBotWalksWhenNoEdge() {
        val bot = StrategicBot("Bob", GameRNG(1uL))
        val state = makeStrategicState(botCard = 20)
        val decision = bot.decideOnQuote(state, "You", Quote(70, 72))
        assertNull(decision)
    }

    @Test fun testStrategicBotOpportunisticSellEvenWithHighCard() {
        val bot = StrategicBot("Bob", GameRNG(1uL))
        val state = makeStrategicState(botCard = 20)
        val decision = bot.decideOnQuote(state, "You", Quote(80, 82))
        assertEquals("sell", decision)
    }

    // StrategicBot: DecideAction

    @Test fun testStrategicBotReturnsValidTarget() {
        val bot = StrategicBot("Bob", GameRNG(42uL))
        val state = makeStrategicState(botCard = 20)
        val target = bot.decideAction(state)
        assertNotNull(target)
        assertTrue(target!! in state.playerIDs)
        assertNotEquals("Bob", target)
    }

    @Test fun testStrategicBotReturnsNilDuringSettle() {
        val bot = StrategicBot("Bob", GameRNG(1uL))
        val state = makeStrategicState(botCard = 20, phase = Phase.SETTLE)
        assertNull(bot.decideAction(state))
    }

    @Test fun testStrategicBotNeverReturnsSelf() {
        for (seed in 1..100) {
            val bot = StrategicBot("Bob", GameRNG(seed.toULong()))
            val state = makeStrategicState(botCard = 20)
            val target = bot.decideAction(state)
            if (target != null) assertNotEquals("Bob", target)
        }
    }

    @Test fun testStrategicBotMaxTradesPerTurnRespected() {
        val bot = StrategicBot("Bob", GameRNG(1uL))
        val state = makeStrategicState(botCard = 20)
        var actionsTaken = 0
        for (i in 0 until 8) {
            val target = bot.decideAction(state) ?: break
            actionsTaken++
        }
        assertTrue(actionsTaken <= 3)
    }

    @Test fun testStrategicBotPhaseChangeResetsTradeCount() {
        val bot = StrategicBot("Bob", GameRNG(1uL))
        val state = makeStrategicState(botCard = 20)
        repeat(5) { bot.decideAction(state) }
        assertNull(bot.decideAction(state))
        state.phase = Phase.REVEAL1
        bot.onPhaseChange(state)
        assertNotNull(bot.decideAction(state))
    }

    // StrategicBot: Adaptation

    @Test fun testStrategicBotNoAdaptationBelowThreshold() {
        val bot = StrategicBot("Bob", GameRNG(1uL))
        val state = makeStrategicState(botCard = 7)
        bot.tracker.recordTrade("Bob", "You", "You")
        val qBaseline = StrategicBot("Bob", GameRNG(1uL)).getQuote(state, null)
        val q = bot.getQuote(state, "You")
        assertEquals(qBaseline.bid, q.bid)
    }

    @Test fun testStrategicBotAdaptationKicksInAggressively() {
        val bot = StrategicBot("Bob", GameRNG(1uL))
        val state = makeStrategicState(botCard = 7)
        repeat(4) { bot.tracker.recordTrade("Bob", "You", "You") }
        val qBaseline = StrategicBot("Bob", GameRNG(1uL)).getQuote(state, null)
        val qAdapted = bot.getQuote(state, "You")
        assertTrue(qAdapted.bid < qBaseline.bid)
    }

    @Test fun testStrategicBotAdaptationShiftsUpForBullish() {
        val bot = StrategicBot("Bob", GameRNG(1uL))
        val state = makeStrategicState(botCard = 7)
        repeat(4) { bot.tracker.recordTrade("You", "Bob", "You") }
        val qBaseline = StrategicBot("Bob", GameRNG(1uL)).getQuote(state, null)
        val qAdapted = bot.getQuote(state, "You")
        assertTrue(qAdapted.bid > qBaseline.bid)
    }

    @Test fun testStrategicBotAdaptationSpreadStill2() {
        val bot = StrategicBot("Bob", GameRNG(1uL))
        val state = makeStrategicState(botCard = 7)
        repeat(20) { bot.tracker.recordTrade("Bob", "You", "You") }
        val q = bot.getQuote(state, "You")
        assertEquals(2, q.ask - q.bid)
    }

    // StrategicBot vs SimpleBot Comparison

    @Test fun testHighCardStrategicQuotesLowerThanSimple() {
        var simpleMids = 0.0
        var strategicMids = 0.0
        for (seed in 1..50) {
            val s = seed.toULong()
            val simpleBot = SimpleBot("Bob", GameRNG(s))
            val strategicBot = StrategicBot("Bob", GameRNG(s))
            val state = makeStrategicState(botCard = 20, revealedCentral = emptyList())
            val qSimple = simpleBot.getQuote(state, null)
            val qStrategic = strategicBot.getQuote(state, null)
            simpleMids += (qSimple.bid + qSimple.ask) / 2.0
            strategicMids += (qStrategic.bid + qStrategic.ask) / 2.0
        }
        assertTrue(strategicMids / 50 < simpleMids / 50)
    }

    @Test fun testLowCardStrategicQuotesHigherThanSimple() {
        var simpleMids = 0.0
        var strategicMids = 0.0
        for (seed in 1..50) {
            val s = seed.toULong()
            val simpleBot = SimpleBot("Bob", GameRNG(s))
            val strategicBot = StrategicBot("Bob", GameRNG(s))
            val state = makeStrategicState(botCard = -10, revealedCentral = emptyList())
            val qSimple = simpleBot.getQuote(state, null)
            val qStrategic = strategicBot.getQuote(state, null)
            simpleMids += (qSimple.bid + qSimple.ask) / 2.0
            strategicMids += (qStrategic.bid + qStrategic.ask) / 2.0
        }
        assertTrue(strategicMids / 50 > simpleMids / 50)
    }
}
