import SwiftUI

struct SettlementView: View {
    var vm: GameViewModel

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                // Header
                VStack(spacing: 8) {
                    Text("SETTLEMENT")
                        .font(.system(size: 24, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                        .tracking(2)

                    Text("Final Total: \(vm.finalTotal)")
                        .font(.title2.monospacedDigit())
                        .fontWeight(.semibold)
                        .foregroundStyle(.cyan)

                    Text("Sum of all 8 cards in play")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(.top, 20)

                // Cards dealt reveal
                VStack(spacing: 6) {
                    Text("CARDS DEALT")
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundStyle(.secondary)
                        .tracking(2)

                    let dealt = vm.allDealtCards

                    // Player cards
                    ForEach(dealt.players, id: \.name) { entry in
                        HStack {
                            Text(entry.name)
                                .font(.subheadline)
                                .fontWeight(entry.name == GameViewModel.humanID ? .bold : .regular)
                                .foregroundStyle(entry.name == GameViewModel.humanID ? .yellow : .white)
                            Spacer()
                            Text(cardString(entry.card))
                                .font(.subheadline.monospacedDigit())
                                .fontWeight(.semibold)
                                .foregroundStyle(.white)
                        }
                        .padding(.vertical, 4)
                        .padding(.horizontal, 12)
                    }

                    // Central cards
                    HStack {
                        Text("Central")
                            .font(.subheadline)
                            .foregroundStyle(.cyan)
                        Spacer()
                        Text(dealt.central.map { cardString($0) }.joined(separator: ", "))
                            .font(.subheadline.monospacedDigit())
                            .fontWeight(.semibold)
                            .foregroundStyle(.cyan)
                    }
                    .padding(.vertical, 4)
                    .padding(.horizontal, 12)
                }
                .padding(.horizontal, 24)

                // Leaderboard
                VStack(spacing: 4) {
                    Text("LEADERBOARD")
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundStyle(.secondary)
                        .tracking(2)

                    ForEach(Array(vm.sortedScores.enumerated()), id: \.offset) { index, entry in
                        leaderboardRow(
                            rank: index + 1,
                            name: entry.name,
                            score: entry.score,
                            isHuman: entry.name == GameViewModel.humanID
                        )
                    }
                }
                .padding(.horizontal, 24)

                // Your trades breakdown
                if !vm.playerTradeBreakdown.isEmpty {
                    VStack(spacing: 4) {
                        Text("YOUR TRADES")
                            .font(.caption)
                            .fontWeight(.semibold)
                            .foregroundStyle(.secondary)
                            .tracking(2)

                        ForEach(Array(vm.playerTradeBreakdown.enumerated()), id: \.offset) {
                            _, item in
                            tradeRow(item.trade, pnl: item.pnl)
                        }
                    }
                    .padding(.horizontal, 24)
                } else {
                    Text("You made no trades this round.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                // Play again
                Button("Play Again") {
                    vm.playAgain()
                }
                .buttonStyle(PrimaryButtonStyle(color: .green))
                .padding(.horizontal, 32)
                .padding(.bottom, 20)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.clear)
    }

    // MARK: - Subviews

    private func leaderboardRow(rank: Int, name: String, score: Int, isHuman: Bool) -> some View {
        HStack {
            Text("#\(rank)")
                .font(.subheadline.monospacedDigit())
                .foregroundStyle(.secondary)
                .frame(width: 30, alignment: .leading)

            Text(name)
                .font(.subheadline)
                .fontWeight(isHuman ? .bold : .regular)
                .foregroundStyle(isHuman ? .yellow : .white)

            Spacer()

            Text(scoreString(score))
                .font(.subheadline.monospacedDigit())
                .fontWeight(.semibold)
                .foregroundStyle(scoreColor(score))
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 12)
        .background(isHuman ? Color.yellow.opacity(0.08) : Color.clear)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private func tradeRow(_ trade: Trade, pnl: Int) -> some View {
        HStack {
            if trade.buyer == GameViewModel.humanID {
                Text("Bought from \(trade.seller)")
                    .font(.caption)
                    .foregroundStyle(.green)
            } else {
                Text("Sold to \(trade.buyer)")
                    .font(.caption)
                    .foregroundStyle(.red)
            }

            Spacer()

            Text("@ \(trade.price)")
                .font(.caption.monospacedDigit())
                .foregroundStyle(.secondary)

            Text(scoreString(pnl))
                .font(.caption.monospacedDigit())
                .fontWeight(.semibold)
                .foregroundStyle(scoreColor(pnl))
                .frame(width: 50, alignment: .trailing)
        }
        .padding(.vertical, 4)
    }

    private func scoreString(_ score: Int) -> String {
        score >= 0 ? "+\(score)" : "\(score)"
    }

    private func scoreColor(_ score: Int) -> Color {
        if score > 0 { return .green }
        if score < 0 { return .red }
        return .secondary
    }

    private func cardString(_ value: Int) -> String {
        value >= 0 ? "+\(value)" : "\(value)"
    }
}
