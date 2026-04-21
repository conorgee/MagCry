import SwiftUI

struct StatsView: View {
    var scoreStore: ScoreStore
    @State private var selectedDifficulty: Difficulty = .easy
    @State private var showResetAlert = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ZStack {
                Color(white: 0.08).ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 24) {
                        // Difficulty picker
                        Picker("Difficulty", selection: $selectedDifficulty) {
                            ForEach(Difficulty.allCases, id: \.rawValue) { diff in
                                Text(diff.label).tag(diff)
                            }
                        }
                        .pickerStyle(.segmented)
                        .padding(.horizontal, 24)
                        .padding(.top, 16)

                        let stats = scoreStore.statsFor(selectedDifficulty)

                        if stats.gamesPlayed == 0 {
                            noDataView
                        } else {
                            statsGrid(stats)
                        }

                        Spacer(minLength: 20)

                        // Reset button
                        Button(role: .destructive) {
                            showResetAlert = true
                        } label: {
                            Text("Reset All Stats")
                                .font(.subheadline)
                                .foregroundStyle(.red.opacity(0.7))
                        }
                        .padding(.bottom, 24)
                    }
                }
            }
            .navigationTitle("Stats")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                        .foregroundStyle(.cyan)
                }
            }
            .alert("Reset all stats?", isPresented: $showResetAlert) {
                Button("Cancel", role: .cancel) { }
                Button("Reset", role: .destructive) {
                    scoreStore.resetAll()
                }
            } message: {
                Text("This will permanently erase all your stats across all difficulties.")
            }
        }
    }

    // MARK: - No Data

    private var noDataView: some View {
        VStack(spacing: 8) {
            Text("No games played yet")
                .font(.headline)
                .foregroundStyle(.secondary)
            Text("Play a game on \(selectedDifficulty.label) to see your stats here.")
                .font(.subheadline)
                .foregroundStyle(.secondary.opacity(0.7))
                .multilineTextAlignment(.center)
        }
        .padding(.top, 40)
        .padding(.horizontal, 32)
    }

    // MARK: - Stats Grid

    private func statsGrid(_ stats: DifficultyStats) -> some View {
        VStack(spacing: 16) {
            // Row 1: Games + Win Rate
            HStack(spacing: 12) {
                statCard(
                    title: "Games Played",
                    value: "\(stats.gamesPlayed)",
                    accent: .white
                )
                statCard(
                    title: "Win Rate",
                    value: stats.gamesPlayed > 0
                        ? "\(Int(stats.winRate * 100))%"
                        : "--",
                    subtitle: "\(stats.gamesWon) wins",
                    accent: stats.winRate >= 0.5 ? .green : .orange
                )
            }

            // Row 2: Best P&L + Average P&L
            HStack(spacing: 12) {
                statCard(
                    title: "Best P&L",
                    value: stats.bestPnL.map { formatPnL($0) } ?? "--",
                    accent: (stats.bestPnL ?? 0) > 0 ? .green : .red
                )
                statCard(
                    title: "Avg P&L",
                    value: stats.gamesPlayed > 0
                        ? formatPnL(Int(stats.averagePnL.rounded()))
                        : "--",
                    accent: stats.averagePnL > 0 ? .green
                        : stats.averagePnL < 0 ? .red : .secondary
                )
            }

            // Row 3: Streaks
            HStack(spacing: 12) {
                statCard(
                    title: "Current Streak",
                    value: "\(stats.currentStreak)",
                    subtitle: stats.currentStreak >= 3 ? "On fire" : nil,
                    accent: stats.currentStreak >= 3 ? .yellow : .white
                )
                statCard(
                    title: "Best Streak",
                    value: "\(stats.bestStreak)",
                    accent: stats.bestStreak >= 3 ? .yellow : .white
                )
            }

            // Row 4: Total Trades
            HStack(spacing: 12) {
                statCard(
                    title: "Total Trades",
                    value: "\(stats.totalTrades)",
                    accent: .cyan
                )
                // Empty slot for balance
                Color.clear.frame(height: 1)
            }
        }
        .padding(.horizontal, 24)
    }

    // MARK: - Stat Card

    private func statCard(
        title: String,
        value: String,
        subtitle: String? = nil,
        accent: Color
    ) -> some View {
        VStack(spacing: 6) {
            Text(title)
                .font(.caption)
                .fontWeight(.semibold)
                .foregroundStyle(.secondary)
                .tracking(1)

            Text(value)
                .font(.title.monospacedDigit())
                .fontWeight(.bold)
                .foregroundStyle(accent)

            if let subtitle = subtitle {
                Text(subtitle)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .background(Color.white.opacity(0.05))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    // MARK: - Helpers

    private func formatPnL(_ value: Int) -> String {
        value >= 0 ? "+\(value)" : "\(value)"
    }
}
