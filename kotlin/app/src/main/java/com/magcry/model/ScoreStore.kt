package com.magcry.model

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

// -- Per-Difficulty Stats --

data class DifficultyStats(
    var bestPnL: Int? = null,
    var gamesPlayed: Int = 0,
    var gamesWon: Int = 0,       // 1st place = win
    var totalPnL: Int = 0,       // for computing average
    var totalTrades: Int = 0,
    var currentStreak: Int = 0,
    var bestStreak: Int = 0
) {
    val winRate: Double
        get() = if (gamesPlayed > 0) gamesWon.toDouble() / gamesPlayed else 0.0

    val averagePnL: Double
        get() = if (gamesPlayed > 0) totalPnL.toDouble() / gamesPlayed else 0.0

    fun toJson(): JSONObject = JSONObject().apply {
        if (bestPnL != null) put("bestPnL", bestPnL)
        put("gamesPlayed", gamesPlayed)
        put("gamesWon", gamesWon)
        put("totalPnL", totalPnL)
        put("totalTrades", totalTrades)
        put("currentStreak", currentStreak)
        put("bestStreak", bestStreak)
    }

    companion object {
        fun fromJson(json: JSONObject): DifficultyStats = DifficultyStats(
            bestPnL = if (json.has("bestPnL")) json.getInt("bestPnL") else null,
            gamesPlayed = json.optInt("gamesPlayed", 0),
            gamesWon = json.optInt("gamesWon", 0),
            totalPnL = json.optInt("totalPnL", 0),
            totalTrades = json.optInt("totalTrades", 0),
            currentStreak = json.optInt("currentStreak", 0),
            bestStreak = json.optInt("bestStreak", 0)
        )
    }
}

// -- ScoreStore --

/**
 * Persistent stats storage backed by SharedPreferences.
 * Tracks best P&L, games played/won, streaks, and totals per difficulty.
 */
class ScoreStore(private val prefs: SharedPreferences) {

    companion object {
        private const val PREFS_KEY = "magcry_stats_v1"

        fun create(context: Context): ScoreStore {
            val prefs = context.getSharedPreferences("magcry_prefs", Context.MODE_PRIVATE)
            return ScoreStore(prefs)
        }
    }

    /** Stats keyed by Difficulty rawValue (1, 2, 3). */
    var stats: Map<Int, DifficultyStats> by mutableStateOf(loadStats())
        private set

    /** Set to true briefly after a new best is recorded (read by settlement view). */
    var isNewBest: Boolean by mutableStateOf(false)

    // -- Public API --

    /**
     * Record the result of a completed game.
     */
    fun record(difficulty: Difficulty, pnl: Int, rank: Int, tradeCount: Int) {
        val s = (stats[difficulty.value] ?: DifficultyStats()).copy()

        s.gamesPlayed += 1
        s.totalPnL += pnl
        s.totalTrades += tradeCount

        // Win tracking (1st place = win)
        val won = rank == 1
        if (won) {
            s.gamesWon += 1
            s.currentStreak += 1
            s.bestStreak = maxOf(s.bestStreak, s.currentStreak)
        } else {
            s.currentStreak = 0
        }

        // Best P&L tracking
        val best = s.bestPnL
        if (best != null) {
            if (pnl > best) {
                s.bestPnL = pnl
                isNewBest = true
            } else {
                isNewBest = false
            }
        } else {
            // First game at this difficulty
            s.bestPnL = pnl
            isNewBest = true
        }

        stats = stats + (difficulty.value to s)
        save()
    }

    /** Get stats for a specific difficulty. */
    fun statsFor(difficulty: Difficulty): DifficultyStats {
        return stats[difficulty.value] ?: DifficultyStats()
    }

    /** Reset all stats (with no undo). */
    fun resetAll() {
        stats = emptyMap()
        isNewBest = false
        prefs.edit().remove(PREFS_KEY).apply()
    }

    // -- Persistence --

    private fun loadStats(): Map<Int, DifficultyStats> {
        val jsonStr = prefs.getString(PREFS_KEY, null) ?: return emptyMap()
        return try {
            val json = JSONObject(jsonStr)
            val result = mutableMapOf<Int, DifficultyStats>()
            for (key in json.keys()) {
                result[key.toInt()] = DifficultyStats.fromJson(json.getJSONObject(key))
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun save() {
        val json = JSONObject()
        for ((key, value) in stats) {
            json.put(key.toString(), value.toJson())
        }
        prefs.edit().putString(PREFS_KEY, json.toString()).apply()
    }
}
