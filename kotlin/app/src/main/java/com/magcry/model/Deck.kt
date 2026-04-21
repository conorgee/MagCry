package com.magcry.model

/** Deck constants and dealing logic. */
object Deck {
    val cards: List<Int> = listOf(-10) + (1..15).toList() + listOf(20)
    const val SIZE = 17
    const val SUM = 130
    val MEAN: Double = SUM.toDouble() / SIZE.toDouble() // ~7.647
    const val N_PLAYERS = 5
    const val N_CENTRAL = 3
    const val N_IN_PLAY = 8 // 5 private + 3 central

    /** Mean of remaining deck after removing known cards. */
    fun meanExcluding(knownCards: List<Int>): Double {
        val remaining = cards.toMutableList()
        for (card in knownCards) {
            val idx = remaining.indexOf(card)
            if (idx >= 0) remaining.removeAt(idx)
        }
        if (remaining.isEmpty()) return 0.0
        return remaining.sum().toDouble() / remaining.size.toDouble()
    }

    /**
     * Expected total of all 8 in-play cards given what we know.
     * EV = sum(known) + unknownCount * meanExcluding(known)
     */
    fun expectedTotal(knownCards: List<Int>, unknownCount: Int): Double {
        val knownSum = knownCards.sum().toDouble()
        return knownSum + unknownCount.toDouble() * meanExcluding(knownCards)
    }

    /** Shuffle deck, deal 1 private card per player + 3 central cards. */
    fun deal(
        playerIDs: List<String>,
        rng: GameRNG
    ): Pair<Map<String, Int>, List<Int>> {
        require(playerIDs.size == N_PLAYERS) {
            "Expected $N_PLAYERS players, got ${playerIDs.size}"
        }

        val shuffled = rng.shuffled(cards)
        val privateCards = mutableMapOf<String, Int>()
        for ((i, pid) in playerIDs.withIndex()) {
            privateCards[pid] = shuffled[i]
        }
        val centralCards = shuffled.subList(N_PLAYERS, N_PLAYERS + N_CENTRAL).toList()
        return Pair(privateCards, centralCards)
    }
}
