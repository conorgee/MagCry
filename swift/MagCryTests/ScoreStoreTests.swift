import XCTest
@testable import MagCry

final class ScoreStoreTests: XCTestCase {

    /// Create a ScoreStore backed by a fresh, isolated UserDefaults suite.
    private func makeStore() -> ScoreStore {
        let suite = UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!
        return ScoreStore(defaults: defaults)
    }

    // MARK: - Basic Recording

    func testFirstGameRecordsStat() {
        let store = makeStore()
        store.record(difficulty: .easy, pnl: 10, rank: 1, tradeCount: 3)

        let stats = store.statsFor(.easy)
        XCTAssertEqual(stats.gamesPlayed, 1)
        XCTAssertEqual(stats.gamesWon, 1)
        XCTAssertEqual(stats.totalPnL, 10)
        XCTAssertEqual(stats.totalTrades, 3)
        XCTAssertEqual(stats.bestPnL, 10)
    }

    func testMultipleGamesAccumulate() {
        let store = makeStore()
        store.record(difficulty: .easy, pnl: 10, rank: 1, tradeCount: 3)
        store.record(difficulty: .easy, pnl: -5, rank: 3, tradeCount: 2)
        store.record(difficulty: .easy, pnl: 20, rank: 1, tradeCount: 4)

        let stats = store.statsFor(.easy)
        XCTAssertEqual(stats.gamesPlayed, 3)
        XCTAssertEqual(stats.gamesWon, 2)
        XCTAssertEqual(stats.totalPnL, 25) // 10 + (-5) + 20
        XCTAssertEqual(stats.totalTrades, 9) // 3 + 2 + 4
    }

    // MARK: - Best P&L

    func testBestPnLTracksHighest() {
        let store = makeStore()
        store.record(difficulty: .medium, pnl: 5, rank: 2, tradeCount: 1)
        XCTAssertEqual(store.statsFor(.medium).bestPnL, 5)

        store.record(difficulty: .medium, pnl: 15, rank: 1, tradeCount: 2)
        XCTAssertEqual(store.statsFor(.medium).bestPnL, 15)

        // Lower score doesn't replace best
        store.record(difficulty: .medium, pnl: 8, rank: 2, tradeCount: 1)
        XCTAssertEqual(store.statsFor(.medium).bestPnL, 15)
    }

    func testIsNewBestFlag() {
        let store = makeStore()
        store.record(difficulty: .easy, pnl: 10, rank: 1, tradeCount: 1)
        XCTAssertTrue(store.isNewBest) // First game is always new best

        store.record(difficulty: .easy, pnl: 5, rank: 2, tradeCount: 1)
        XCTAssertFalse(store.isNewBest) // Lower score

        store.record(difficulty: .easy, pnl: 20, rank: 1, tradeCount: 1)
        XCTAssertTrue(store.isNewBest) // New best!
    }

    func testNegativeBestPnL() {
        let store = makeStore()
        store.record(difficulty: .hard, pnl: -10, rank: 5, tradeCount: 1)
        XCTAssertEqual(store.statsFor(.hard).bestPnL, -10)

        store.record(difficulty: .hard, pnl: -3, rank: 4, tradeCount: 1)
        XCTAssertEqual(store.statsFor(.hard).bestPnL, -3) // -3 > -10
    }

    // MARK: - Win Rate

    func testWinRate() {
        let store = makeStore()
        store.record(difficulty: .easy, pnl: 10, rank: 1, tradeCount: 1)
        store.record(difficulty: .easy, pnl: -5, rank: 3, tradeCount: 1)
        store.record(difficulty: .easy, pnl: 8, rank: 1, tradeCount: 1)
        store.record(difficulty: .easy, pnl: -2, rank: 4, tradeCount: 1)

        let stats = store.statsFor(.easy)
        XCTAssertEqual(stats.winRate, 0.5, accuracy: 0.001)
    }

    func testWinRateZeroGames() {
        let store = makeStore()
        XCTAssertEqual(store.statsFor(.easy).winRate, 0)
    }

    // MARK: - Average P&L

    func testAveragePnL() {
        let store = makeStore()
        store.record(difficulty: .easy, pnl: 10, rank: 1, tradeCount: 1)
        store.record(difficulty: .easy, pnl: -6, rank: 3, tradeCount: 1)
        store.record(difficulty: .easy, pnl: 8, rank: 2, tradeCount: 1)

        let stats = store.statsFor(.easy)
        XCTAssertEqual(stats.averagePnL, 4.0, accuracy: 0.001) // (10 + -6 + 8) / 3 = 4.0
    }

    func testAveragePnLZeroGames() {
        let store = makeStore()
        XCTAssertEqual(store.statsFor(.easy).averagePnL, 0)
    }

    // MARK: - Streaks

    func testWinStreakBuilds() {
        let store = makeStore()
        store.record(difficulty: .easy, pnl: 10, rank: 1, tradeCount: 1)
        XCTAssertEqual(store.statsFor(.easy).currentStreak, 1)
        XCTAssertEqual(store.statsFor(.easy).bestStreak, 1)

        store.record(difficulty: .easy, pnl: 15, rank: 1, tradeCount: 1)
        XCTAssertEqual(store.statsFor(.easy).currentStreak, 2)
        XCTAssertEqual(store.statsFor(.easy).bestStreak, 2)

        store.record(difficulty: .easy, pnl: 20, rank: 1, tradeCount: 1)
        XCTAssertEqual(store.statsFor(.easy).currentStreak, 3)
        XCTAssertEqual(store.statsFor(.easy).bestStreak, 3)
    }

    func testStreakResetsOnLoss() {
        let store = makeStore()
        store.record(difficulty: .easy, pnl: 10, rank: 1, tradeCount: 1)
        store.record(difficulty: .easy, pnl: 15, rank: 1, tradeCount: 1)
        store.record(difficulty: .easy, pnl: -5, rank: 3, tradeCount: 1) // Loss

        XCTAssertEqual(store.statsFor(.easy).currentStreak, 0)
        XCTAssertEqual(store.statsFor(.easy).bestStreak, 2) // Best preserved
    }

    func testBestStreakPreservedAcrossMultipleStreaks() {
        let store = makeStore()
        // Streak of 3
        store.record(difficulty: .easy, pnl: 10, rank: 1, tradeCount: 1)
        store.record(difficulty: .easy, pnl: 15, rank: 1, tradeCount: 1)
        store.record(difficulty: .easy, pnl: 20, rank: 1, tradeCount: 1)
        XCTAssertEqual(store.statsFor(.easy).bestStreak, 3)

        // Loss
        store.record(difficulty: .easy, pnl: -5, rank: 3, tradeCount: 1)

        // Streak of 2 (doesn't beat 3)
        store.record(difficulty: .easy, pnl: 10, rank: 1, tradeCount: 1)
        store.record(difficulty: .easy, pnl: 12, rank: 1, tradeCount: 1)
        XCTAssertEqual(store.statsFor(.easy).currentStreak, 2)
        XCTAssertEqual(store.statsFor(.easy).bestStreak, 3) // Still 3
    }

    // MARK: - Per-Difficulty Isolation

    func testDifficultiesAreIsolated() {
        let store = makeStore()
        store.record(difficulty: .easy, pnl: 10, rank: 1, tradeCount: 3)
        store.record(difficulty: .hard, pnl: -5, rank: 4, tradeCount: 1)

        XCTAssertEqual(store.statsFor(.easy).gamesPlayed, 1)
        XCTAssertEqual(store.statsFor(.easy).gamesWon, 1)
        XCTAssertEqual(store.statsFor(.easy).bestPnL, 10)

        XCTAssertEqual(store.statsFor(.hard).gamesPlayed, 1)
        XCTAssertEqual(store.statsFor(.hard).gamesWon, 0)
        XCTAssertEqual(store.statsFor(.hard).bestPnL, -5)

        XCTAssertEqual(store.statsFor(.medium).gamesPlayed, 0) // Untouched
    }

    // MARK: - Persistence

    func testPersistenceRoundTrip() {
        let suite = UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!

        // Write
        let store1 = ScoreStore(defaults: defaults)
        store1.record(difficulty: .easy, pnl: 10, rank: 1, tradeCount: 3)
        store1.record(difficulty: .hard, pnl: -5, rank: 4, tradeCount: 1)

        // Read in a new instance with same defaults
        let store2 = ScoreStore(defaults: defaults)
        XCTAssertEqual(store2.statsFor(.easy).gamesPlayed, 1)
        XCTAssertEqual(store2.statsFor(.easy).bestPnL, 10)
        XCTAssertEqual(store2.statsFor(.hard).gamesPlayed, 1)
        XCTAssertEqual(store2.statsFor(.hard).bestPnL, -5)

        // Clean up
        defaults.removePersistentDomain(forName: suite)
    }

    // MARK: - Reset

    func testResetClearsAllStats() {
        let store = makeStore()
        store.record(difficulty: .easy, pnl: 10, rank: 1, tradeCount: 3)
        store.record(difficulty: .hard, pnl: 20, rank: 1, tradeCount: 5)

        store.resetAll()

        XCTAssertEqual(store.statsFor(.easy).gamesPlayed, 0)
        XCTAssertEqual(store.statsFor(.easy).bestPnL, nil)
        XCTAssertEqual(store.statsFor(.hard).gamesPlayed, 0)
        XCTAssertFalse(store.isNewBest)
    }

    // MARK: - Edge Cases

    func testZeroPnLGame() {
        let store = makeStore()
        store.record(difficulty: .easy, pnl: 0, rank: 3, tradeCount: 0)

        let stats = store.statsFor(.easy)
        XCTAssertEqual(stats.gamesPlayed, 1)
        XCTAssertEqual(stats.gamesWon, 0)
        XCTAssertEqual(stats.bestPnL, 0)
        XCTAssertEqual(stats.totalTrades, 0)
    }

    func testRank1IsWinOtherRanksAreLosses() {
        let store = makeStore()
        store.record(difficulty: .easy, pnl: 10, rank: 1, tradeCount: 1)
        store.record(difficulty: .easy, pnl: 10, rank: 2, tradeCount: 1)
        store.record(difficulty: .easy, pnl: 10, rank: 3, tradeCount: 1)
        store.record(difficulty: .easy, pnl: 10, rank: 4, tradeCount: 1)
        store.record(difficulty: .easy, pnl: 10, rank: 5, tradeCount: 1)

        let stats = store.statsFor(.easy)
        XCTAssertEqual(stats.gamesPlayed, 5)
        XCTAssertEqual(stats.gamesWon, 1) // Only rank 1 counts
    }

    func testDifficultyStatsEquatable() {
        let a = DifficultyStats(bestPnL: 10, gamesPlayed: 5, gamesWon: 3, totalPnL: 25, totalTrades: 12, currentStreak: 2, bestStreak: 4)
        let b = DifficultyStats(bestPnL: 10, gamesPlayed: 5, gamesWon: 3, totalPnL: 25, totalTrades: 12, currentStreak: 2, bestStreak: 4)
        XCTAssertEqual(a, b)
    }
}
