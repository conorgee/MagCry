package com.magcry

import com.magcry.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class DeckTests {

    // -- Deck composition --

    @Test
    fun testDeckHas17Cards() {
        assertEquals(17, Deck.cards.size)
    }

    @Test
    fun testDeckContainsMinusTen() {
        assertTrue(Deck.cards.contains(-10))
    }

    @Test
    fun testDeckContainsTwenty() {
        assertTrue(Deck.cards.contains(20))
    }

    @Test
    fun testDeckContains1Through15() {
        for (i in 1..15) {
            assertTrue(Deck.cards.contains(i), "Deck should contain $i")
        }
    }

    @Test
    fun testDeckHasNoDuplicates() {
        assertEquals(Deck.cards.size, Deck.cards.toSet().size)
    }

    @Test
    fun testDeckSumIs130() {
        assertEquals(130, Deck.cards.sum())
    }

    @Test
    fun testDeckMeanApprox7_65() {
        val mean = Deck.cards.sum().toDouble() / Deck.cards.size
        assertTrue(abs(mean - 7.647) < 0.01)
    }

    // -- Constants --

    @Test
    fun testNPlayersIs5() {
        assertEquals(5, Deck.N_PLAYERS)
    }

    @Test
    fun testNCentralIs3() {
        assertEquals(3, Deck.N_CENTRAL)
    }

    @Test
    fun testNInPlayIs8() {
        assertEquals(8, Deck.N_IN_PLAY)
    }

    // -- Deal --

    @Test
    fun testDealReturnsCorrectNumberOfPrivateCards() {
        val rng = GameRNG(42uL)
        val (private, _) = Deck.deal(listOf("A", "B", "C", "D", "E"), rng)
        assertEquals(5, private.size)
    }

    @Test
    fun testDealReturnsCorrectNumberOfCentralCards() {
        val rng = GameRNG(42uL)
        val (_, central) = Deck.deal(listOf("A", "B", "C", "D", "E"), rng)
        assertEquals(3, central.size)
    }

    @Test
    fun testAllDealtCardsFromDeck() {
        val rng = GameRNG(42uL)
        val (private, central) = Deck.deal(listOf("A", "B", "C", "D", "E"), rng)
        val allDealt = private.values + central
        for (card in allDealt) {
            assertTrue(Deck.cards.contains(card), "Dealt card $card not in deck")
        }
    }

    @Test
    fun testNoDuplicateCardsInDeal() {
        val rng = GameRNG(42uL)
        val (private, central) = Deck.deal(listOf("A", "B", "C", "D", "E"), rng)
        val allDealt = private.values + central
        assertEquals(allDealt.toList().size, allDealt.toSet().size)
    }

    @Test
    fun testEachPlayerGetsOneCard() {
        val rng = GameRNG(42uL)
        val players = listOf("A", "B", "C", "D", "E")
        val (private, _) = Deck.deal(players, rng)
        for (p in players) {
            assertNotNull(private[p], "Player $p should have a card")
        }
    }

    @Test
    fun testDeterministicWithSameSeed() {
        val (p1, c1) = Deck.deal(listOf("A", "B", "C", "D", "E"), GameRNG(42uL))
        val (p2, c2) = Deck.deal(listOf("A", "B", "C", "D", "E"), GameRNG(42uL))
        assertEquals(p1, p2)
        assertEquals(c1, c2)
    }

    @Test
    fun testDifferentSeedsGiveDifferentDeals() {
        val (p1, c1) = Deck.deal(listOf("A", "B", "C", "D", "E"), GameRNG(42uL))
        val (p2, c2) = Deck.deal(listOf("A", "B", "C", "D", "E"), GameRNG(99uL))
        val same = p1 == p2 && c1 == c2
        assertFalse(same, "Different seeds should (almost certainly) give different deals")
    }

    // -- Expected total --

    @Test
    fun testExpectedTotalAllUnknown() {
        val ev = Deck.expectedTotal(emptyList(), 8)
        assertTrue(abs(ev - 8 * Deck.MEAN) < 0.01)
    }

    @Test
    fun testExpectedTotalHolding20() {
        val ev = Deck.expectedTotal(listOf(20), 7)
        val expected = 20.0 + 7.0 * Deck.meanExcluding(listOf(20))
        assertTrue(abs(ev - expected) < 0.01)
    }

    @Test
    fun testExpectedTotalHoldingMinus10() {
        val ev = Deck.expectedTotal(listOf(-10), 7)
        val expected = -10.0 + 7.0 * Deck.meanExcluding(listOf(-10))
        assertTrue(abs(ev - expected) < 0.01)
    }

    @Test
    fun testExpectedTotalAllKnown() {
        val known = listOf(1, 2, 3, 4, 5, 6, 7, 8)
        val ev = Deck.expectedTotal(known, 0)
        assertEquals(36.0, ev, 0.001)
    }

    @Test
    fun testExpectedTotalIncreasesWithHighKnownCard() {
        val evLow = Deck.expectedTotal(listOf(1), 7)
        val evHigh = Deck.expectedTotal(listOf(20), 7)
        assertTrue(evHigh > evLow)
    }

    // -- Mean excluding --

    @Test
    fun testMeanExcludingRemovesCard() {
        val mean = Deck.meanExcluding(listOf(20))
        val remaining = Deck.cards.filter { it != 20 }
        val expected = remaining.sum().toDouble() / remaining.size
        assertTrue(abs(mean - expected) < 0.001)
    }

    @Test
    fun testMeanExcludingMultiple() {
        val mean = Deck.meanExcluding(listOf(-10, 20))
        val remaining = Deck.cards.toMutableList()
        remaining.remove(-10)
        remaining.remove(20)
        val expected = remaining.sum().toDouble() / remaining.size
        assertTrue(abs(mean - expected) < 0.001)
    }

    // -- GameRNG --

    @Test
    fun testRNGDeterministic() {
        val a = GameRNG(42uL)
        val b = GameRNG(42uL)
        repeat(100) {
            assertEquals(a.next(), b.next())
        }
    }

    @Test
    fun testRNGDifferentSeeds() {
        val a = GameRNG(42uL)
        val b = GameRNG(99uL)
        val same = (0 until 10).all { a.next() == b.next() }
        assertFalse(same)
    }

    @Test
    fun testRNGNextIntInRange() {
        val rng = GameRNG(42uL)
        repeat(1000) {
            val v = rng.nextInt(0..10)
            assertTrue(v in 0..10, "nextInt out of range: $v")
        }
    }

    @Test
    fun testRNGNextDoubleInRange() {
        val rng = GameRNG(42uL)
        repeat(1000) {
            val v = rng.nextDouble()
            assertTrue(v in 0.0..1.0, "nextDouble out of range: $v")
        }
    }

    @Test
    fun testRNGShuffledPreservesElements() {
        val rng = GameRNG(42uL)
        val original = listOf(1, 2, 3, 4, 5)
        val shuffled = rng.shuffled(original)
        assertEquals(original.sorted(), shuffled.sorted())
    }

    @Test
    fun testRNGSampleReturnsRequestedCount() {
        val rng = GameRNG(42uL)
        val sample = rng.sample(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), 3)
        assertEquals(3, sample.size)
    }

    @Test
    fun testRNGSampleNoDuplicates() {
        val rng = GameRNG(42uL)
        val sample = rng.sample(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), 5)
        assertEquals(sample.size, sample.toSet().size)
    }

    @Test
    fun testRNGChildIsDeterministic() {
        val a = GameRNG(42uL)
        val b = GameRNG(42uL)
        val childA = a.child()
        val childB = b.child()
        repeat(50) {
            assertEquals(childA.next(), childB.next())
        }
    }

    @Test
    fun testRNGZeroSeedBecomesNonZero() {
        val rng = GameRNG(0uL)
        val v = rng.next()
        assertTrue(v != 0uL)
    }
}
