package com.magcry.model

/**
 * Class-based RNG so bots can hold a reference. Uses xorshift64* algorithm.
 * Deterministic given the same seed — used for reproducible gameplay and testing.
 */
class GameRNG(seed: ULong = (1uL..ULong.MAX_VALUE).random()) {
    private var state: ULong = if (seed == 0uL) 1uL else seed

    fun next(): ULong {
        state = state xor (state shr 12)
        state = state xor (state shl 25)
        state = state xor (state shr 27)
        return state * 0x2545F4914F6CDD1DuL
    }

    // -- Convenience --

    fun nextInt(range: IntRange): Int {
        val width = (range.last - range.first + 1).toULong()
        return range.first + (next() % width).toInt()
    }

    fun nextDouble(): Double {
        return next().toDouble() / ULong.MAX_VALUE.toDouble()
    }

    fun <T> shuffled(array: List<T>): List<T> {
        val result = array.toMutableList()
        for (i in result.size - 1 downTo 1) {
            val j = (next() % (i.toULong() + 1uL)).toInt()
            val tmp = result[i]
            result[i] = result[j]
            result[j] = tmp
        }
        return result
    }

    fun <T> sample(array: List<T>, count: Int): List<T> {
        val pool = array.toMutableList()
        val result = mutableListOf<T>()
        for (i in 0 until minOf(count, pool.size)) {
            val idx = (next() % pool.size.toULong()).toInt()
            result.add(pool.removeAt(idx))
        }
        return result
    }

    fun <T> randomElement(array: List<T>): T? {
        if (array.isEmpty()) return null
        return array[(next() % array.size.toULong()).toInt()]
    }

    /** Create a child RNG with a derived seed (for giving each bot its own RNG). */
    fun child(): GameRNG = GameRNG(next())
}
