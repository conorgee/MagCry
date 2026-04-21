import Foundation
import Observation

// MARK: - Per-Difficulty Stats

struct DifficultyStats: Codable, Equatable {
    var bestPnL: Int?
    var gamesPlayed: Int = 0
    var gamesWon: Int = 0      // 1st place = win
    var totalPnL: Int = 0      // for computing average
    var totalTrades: Int = 0
    var currentStreak: Int = 0
    var bestStreak: Int = 0

    var winRate: Double {
        guard gamesPlayed > 0 else { return 0 }
        return Double(gamesWon) / Double(gamesPlayed)
    }

    var averagePnL: Double {
        guard gamesPlayed > 0 else { return 0 }
        return Double(totalPnL) / Double(gamesPlayed)
    }
}

// MARK: - ScoreStore

/// Persistent stats storage backed by UserDefaults.
/// Tracks best P&L, games played/won, streaks, and totals per difficulty.
@Observable
final class ScoreStore {

    private static let defaultsKey = "magcry_stats_v1"

    /// Stats keyed by Difficulty rawValue (1, 2, 3).
    private(set) var stats: [Int: DifficultyStats] = [:]

    /// Set to true briefly after a new best is recorded (read by settlement view).
    var isNewBest: Bool = false

    private var defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.stats = Self.loadStats(from: defaults)
    }

    // MARK: - Public API

    /// Record the result of a completed game.
    /// - Parameters:
    ///   - difficulty: The difficulty level played.
    ///   - pnl: The player's final P&L.
    ///   - rank: The player's leaderboard rank (1 = best).
    ///   - tradeCount: Number of trades the player made.
    func record(difficulty: Difficulty, pnl: Int, rank: Int, tradeCount: Int) {
        var s = stats[difficulty.rawValue] ?? DifficultyStats()

        s.gamesPlayed += 1
        s.totalPnL += pnl
        s.totalTrades += tradeCount

        // Win tracking (1st place = win)
        let won = rank == 1
        if won {
            s.gamesWon += 1
            s.currentStreak += 1
            s.bestStreak = max(s.bestStreak, s.currentStreak)
        } else {
            s.currentStreak = 0
        }

        // Best P&L tracking
        if let best = s.bestPnL {
            if pnl > best {
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

        stats[difficulty.rawValue] = s
        save()
    }

    /// Get stats for a specific difficulty.
    func statsFor(_ difficulty: Difficulty) -> DifficultyStats {
        stats[difficulty.rawValue] ?? DifficultyStats()
    }

    /// Reset all stats (with no undo).
    func resetAll() {
        stats = [:]
        isNewBest = false
        defaults.removeObject(forKey: Self.defaultsKey)
    }

    // MARK: - Persistence

    private static func loadStats(from defaults: UserDefaults) -> [Int: DifficultyStats] {
        guard let data = defaults.data(forKey: defaultsKey),
              let decoded = try? JSONDecoder().decode([Int: DifficultyStats].self, from: data)
        else { return [:] }
        return decoded
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(stats) else { return }
        defaults.set(data, forKey: Self.defaultsKey)
    }
}
