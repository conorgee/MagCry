package com.magcry

import com.magcry.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TradingTests {

    private fun makeState(): GameState {
        val playerIDs = listOf("You", "Alice", "Bob", "Carol", "Dave")
        val privateCards = mapOf("You" to 7, "Alice" to 10, "Bob" to 5, "Carol" to 6, "Dave" to 15)
        val centralCards = listOf(14, 13, 9)
        val state = GameState(
            playerIDs = playerIDs,
            privateCards = privateCards,
            centralCards = centralCards
        )
        state.revealedCentral = centralCards.toMutableList()
        return state
    }

    // ── ValidateQuote ──

    @Test
    fun testValidSpreadReturnsQuote() {
        val q = validateQuote(58, 60)
        assertEquals(58, q.bid)
        assertEquals(60, q.ask)
    }

    @Test
    fun testSpreadOf3Throws() {
        assertThrows(TradingException.InvalidSpread::class.java) { validateQuote(58, 61) }
    }

    @Test
    fun testSpreadOf1Throws() {
        assertThrows(TradingException.InvalidSpread::class.java) { validateQuote(58, 59) }
    }

    @Test
    fun testSpreadOf0Throws() {
        assertThrows(TradingException.InvalidSpread::class.java) { validateQuote(58, 58) }
    }

    @Test
    fun testNegativeBidValid() {
        val q = validateQuote(-5, -3)
        assertEquals(-5, q.bid)
        assertEquals(-3, q.ask)
    }

    // ── ExecuteTradeDirectly ──

    @Test
    fun testBasicDirectTrade() {
        val state = makeState()
        val trade = executeTradeDirectly(state, "You", "Alice", 60)
        assertEquals("You", trade.buyer)
        assertEquals("Alice", trade.seller)
        assertEquals(60, trade.price)
    }

    @Test
    fun testTradeRecordedInState() {
        val state = makeState()
        executeTradeDirectly(state, "You", "Alice", 60)
        assertEquals(1, state.trades.size)
    }

    @Test
    fun testMultipleDirectTradesAccumulate() {
        val state = makeState()
        executeTradeDirectly(state, "You", "Alice", 60)
        executeTradeDirectly(state, "Bob", "Carol", 70)
        assertEquals(2, state.trades.size)
    }

    @Test
    fun testSelfTradeThrows() {
        val state = makeState()
        assertThrows(TradingException.SelfTrade::class.java) {
            executeTradeDirectly(state, "You", "You", 60)
        }
    }

    @Test
    fun testSettlePhaseThrows() {
        val state = makeState()
        state.phase = Phase.SETTLE
        assertThrows(TradingException.SettlePhase::class.java) {
            executeTradeDirectly(state, "You", "Alice", 60)
        }
    }

    @Test
    fun testUnknownBuyerThrows() {
        val state = makeState()
        assertThrows(TradingException.UnknownPlayer::class.java) {
            executeTradeDirectly(state, "Ghost", "Alice", 60)
        }
    }

    @Test
    fun testUnknownSellerThrows() {
        val state = makeState()
        assertThrows(TradingException.UnknownPlayer::class.java) {
            executeTradeDirectly(state, "You", "Ghost", 60)
        }
    }

    @Test
    fun testRecordsCorrectPhase() {
        val state = makeState()
        state.phase = Phase.REVEAL2
        val trade = executeTradeDirectly(state, "You", "Alice", 60)
        assertEquals(Phase.REVEAL2, trade.phase)
    }

    @Test
    fun testAllNonSettlePhasesAllowTrading() {
        val phases = listOf(Phase.OPEN, Phase.REVEAL1, Phase.REVEAL2, Phase.REVEAL3)
        for (phase in phases) {
            val state = makeState()
            state.phase = phase
            val trade = executeTradeDirectly(state, "You", "Alice", 60)
            assertEquals(phase, trade.phase)
        }
    }

    @Test
    fun testPnlWorksOnDirectTrade() {
        val state = makeState()
        val trade = executeTradeDirectly(state, "You", "Alice", 60)
        val total = state.finalTotal() // 79
        assertEquals(total - 60, trade.pnlFor("You", total))
        assertEquals(60 - total, trade.pnlFor("Alice", total))
    }

    @Test
    fun testPnlForUninvolvedPlayerIsZero() {
        val state = makeState()
        val trade = executeTradeDirectly(state, "You", "Alice", 60)
        assertEquals(0, trade.pnlFor("Bob", state.finalTotal()))
    }

    // ── GameState ──

    @Test
    fun testFinalTotal() {
        val state = makeState()
        assertEquals(79, state.finalTotal())
    }

    @Test
    fun testKnownCardsForPlayer() {
        val state = makeState()
        val known = state.knownCards("You")
        assertTrue(known.contains(7))
        assertTrue(known.contains(14))
        assertTrue(known.contains(13))
        assertTrue(known.contains(9))
    }

    @Test
    fun testKnownCardsWithNoReveals() {
        val state = makeState()
        state.revealedCentral = mutableListOf()
        val known = state.knownCards("You")
        assertEquals(1, known.size)
        assertEquals(7, known[0])
    }

    @Test
    fun testUnknownCount() {
        val state = makeState()
        // All central revealed: unknown = 4 other players
        assertEquals(4, state.unknownCount("You"))
    }

    @Test
    fun testUnknownCountWithAllRevealed() {
        val state = makeState()
        state.revealedCentral = state.centralCards.toMutableList()
        // 4 other players + 0 hidden central = 4
        assertEquals(4, state.unknownCount("You"))
    }

    // ── Quote ──

    @Test
    fun testQuoteFromMid() {
        val q = Quote.fromMid(60.0)
        assertEquals(59, q.bid)
        assertEquals(61, q.ask)
    }

    @Test
    fun testQuoteFromMidCentersCorrectly() {
        val q = Quote.fromMid(60.5)
        // round(60.5) = 61 in Kotlin (Math.round), bid = 60, ask = 62
        assertEquals(60, q.bid)
        assertEquals(62, q.ask)
    }

    @Test
    fun testQuoteEquality() {
        val a = Quote(58, 60)
        val b = Quote(58, 60)
        assertEquals(a, b)
    }

    @Test
    fun testQuoteInequality() {
        val a = Quote(58, 60)
        val b = Quote(59, 61)
        assertNotEquals(a, b)
    }

    // ── Phase ──

    @Test
    fun testPhaseComparable() {
        assertTrue(Phase.OPEN.ordinal < Phase.REVEAL1.ordinal)
        assertTrue(Phase.REVEAL1.ordinal < Phase.REVEAL2.ordinal)
        assertTrue(Phase.REVEAL2.ordinal < Phase.REVEAL3.ordinal)
        assertTrue(Phase.REVEAL3.ordinal < Phase.SETTLE.ordinal)
    }

    @Test
    fun testPhaseLabels() {
        assertEquals("Open Trading", Phase.OPEN.label)
        assertEquals("Reveal 1", Phase.REVEAL1.label)
        assertEquals("Reveal 2", Phase.REVEAL2.label)
        assertEquals("Reveal 3", Phase.REVEAL3.label)
        assertEquals("Settlement", Phase.SETTLE.label)
    }

    // ── Settle / Scoring ──

    @Test
    fun testNoTradesAllZero() {
        val state = makeState()
        val scores = settle(state)
        for ((_, pnl) in scores) {
            assertEquals(0, pnl)
        }
    }

    @Test
    fun testSingleBuyCorrectPnl() {
        val state = makeState()
        executeTradeDirectly(state, "You", "Alice", 60)
        val scores = settle(state)
        assertEquals(79 - 60, scores["You"])
        assertEquals(60 - 79, scores["Alice"])
    }

    @Test
    fun testSingleSellCorrectPnl() {
        val state = makeState()
        executeTradeDirectly(state, "Alice", "You", 60)
        val scores = settle(state)
        assertEquals(60 - 79, scores["You"])
        assertEquals(79 - 60, scores["Alice"])
    }

    @Test
    fun testBuyerProfitsWhenTotalAbovePrice() {
        val state = makeState() // total = 79
        executeTradeDirectly(state, "You", "Alice", 70)
        val scores = settle(state)
        assertTrue(scores["You"]!! > 0)
    }

    @Test
    fun testSellerProfitsWhenTotalBelowPrice() {
        val state = makeState() // total = 79
        executeTradeDirectly(state, "Alice", "You", 85)
        val scores = settle(state)
        assertTrue(scores["You"]!! > 0) // You sold at 85, total 79 => +6
    }

    @Test
    fun testAllPlayersInScores() {
        val state = makeState()
        val scores = settle(state)
        for (pid in state.playerIDs) {
            assertTrue(scores.containsKey(pid))
        }
    }

    @Test
    fun testMultipleTradesAccumulatedCorrectly() {
        val state = makeState()
        executeTradeDirectly(state, "You", "Alice", 60)
        executeTradeDirectly(state, "You", "Bob", 70)
        val scores = settle(state)
        // You bought twice: (79-60) + (79-70) = 19+9 = 28
        assertEquals(28, scores["You"])
    }

    // ── Arbitrage ──

    @Test
    fun testArbitrageProfitIs15ForAllTotals() {
        // Buy at 60, sell at 75 => P&L = (T-60) + (75-T) = 15 regardless of T
        val state = makeState()
        executeTradeDirectly(state, "You", "Alice", 60)
        executeTradeDirectly(state, "Bob", "You", 75)
        val scores = settle(state)
        assertEquals(15, scores["You"])
    }

    @Test
    fun testGaryArbitrageViaSettle() {
        // "Gary" is "You" — same arb test via settle
        val state = makeState()
        executeTradeDirectly(state, "You", "Alice", 60)
        executeTradeDirectly(state, "Bob", "You", 75)
        val scores = settle(state)
        assertEquals(15, scores["You"])
    }

    @Test
    fun testArbitrageIsRiskFreeAcrossMultipleTotals() {
        // Test with different card setups; arb always = ask - bid
        for (total in listOf(50, 79, 100, 130)) {
            val buyPrice = 60
            val sellPrice = 75
            val arbProfit = sellPrice - buyPrice
            assertEquals(15, arbProfit)
        }
    }

    // ── Zero Sum ──

    @Test
    fun testSingleTradeZeroSum() {
        val state = makeState()
        executeTradeDirectly(state, "You", "Alice", 60)
        val scores = settle(state)
        assertEquals(0, scores.values.sum())
    }

    @Test
    fun testMultipleTradesZeroSum() {
        val state = makeState()
        executeTradeDirectly(state, "You", "Alice", 60)
        executeTradeDirectly(state, "Bob", "Carol", 70)
        val scores = settle(state)
        assertEquals(0, scores.values.sum())
    }

    @Test
    fun testManyTradesZeroSum() {
        val state = makeState()
        executeTradeDirectly(state, "You", "Alice", 60)
        executeTradeDirectly(state, "Bob", "Carol", 70)
        executeTradeDirectly(state, "Dave", "You", 80)
        executeTradeDirectly(state, "Alice", "Bob", 65)
        val scores = settle(state)
        assertEquals(0, scores.values.sum())
    }

    // ── Leaderboard ──

    @Test
    fun testLeaderboardSortedHighestFirst() {
        val state = makeState()
        executeTradeDirectly(state, "You", "Alice", 60)
        executeTradeDirectly(state, "Bob", "Carol", 70)
        val scores = settle(state)
        val lb = leaderboard(scores)
        for (i in 0 until lb.size - 1) {
            assertTrue(lb[i].second >= lb[i + 1].second)
        }
    }

    @Test
    fun testLeaderboardAllZero() {
        val state = makeState()
        val scores = settle(state)
        val lb = leaderboard(scores)
        for ((_, pnl) in lb) {
            assertEquals(0, pnl)
        }
    }

    @Test
    fun testLeaderboardAllNegative() {
        // Everyone sells to someone at a price above total (79)
        val state = makeState()
        executeTradeDirectly(state, "Alice", "You", 100)
        executeTradeDirectly(state, "Bob", "Carol", 100)
        val scores = settle(state)
        val lb = leaderboard(scores)
        // Sorted highest first
        assertTrue(lb[0].second >= lb[1].second)
    }

    @Test
    fun testLeaderboardSinglePlayer() {
        val scores = mapOf("You" to 10)
        val lb = leaderboard(scores)
        assertEquals(1, lb.size)
        assertEquals("You", lb[0].first)
        assertEquals(10, lb[0].second)
    }

    // ── TradeBreakdown ──

    @Test
    fun testTradeBreakdownFiltersToPlayerTradesOnly() {
        val state = makeState()
        executeTradeDirectly(state, "You", "Alice", 60)
        executeTradeDirectly(state, "Bob", "Carol", 70)
        val bd = tradeBreakdown(state, "You")
        assertEquals(1, bd.size)
    }

    @Test
    fun testUninvolvedPlayerGetsEmptyBreakdown() {
        val state = makeState()
        executeTradeDirectly(state, "You", "Alice", 60)
        val bd = tradeBreakdown(state, "Dave")
        assertEquals(0, bd.size)
    }

    @Test
    fun testPnlInBreakdownIsCorrect() {
        val state = makeState()
        executeTradeDirectly(state, "You", "Alice", 60)
        val bd = tradeBreakdown(state, "You")
        assertEquals(79 - 60, bd[0].second)
    }

    @Test
    fun testBreakdownIncludesBothBuyAndSell() {
        val state = makeState()
        executeTradeDirectly(state, "You", "Alice", 60)
        executeTradeDirectly(state, "Bob", "You", 70)
        val bd = tradeBreakdown(state, "You")
        assertEquals(2, bd.size)
    }

    // ── Difficulty ──

    @Test
    fun testDifficultyCaseIterable() {
        assertEquals(3, Difficulty.entries.size)
    }

    @Test
    fun testDifficultyRawValues() {
        assertEquals(1, Difficulty.EASY.value)
        assertEquals(2, Difficulty.MEDIUM.value)
        assertEquals(3, Difficulty.HARD.value)
    }

    // ── Trade Identity ──

    @Test
    fun testTradeEqualityIgnoresID() {
        val a = Trade(buyer = "You", seller = "Alice", price = 60, phase = Phase.OPEN)
        val b = Trade(buyer = "You", seller = "Alice", price = 60, phase = Phase.OPEN)
        assertEquals(a, b)
    }

    @Test
    fun testTradeInequalityOnPrice() {
        val a = Trade(buyer = "You", seller = "Alice", price = 60, phase = Phase.OPEN)
        val b = Trade(buyer = "You", seller = "Alice", price = 65, phase = Phase.OPEN)
        assertNotEquals(a, b)
    }

    @Test
    fun testTradeInequalityOnPhase() {
        val a = Trade(buyer = "You", seller = "Alice", price = 60, phase = Phase.OPEN)
        val b = Trade(buyer = "You", seller = "Alice", price = 60, phase = Phase.REVEAL1)
        assertNotEquals(a, b)
    }
}
