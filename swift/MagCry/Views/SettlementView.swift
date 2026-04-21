import SwiftUI

struct SettlementView: View {
    var vm: GameViewModel
    @State private var visibleCardCount = 0

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

                // Cards dealt reveal (staggered animation)
                cardsDealtSection

                // Leaderboard
                leaderboardSection

                // Streak callout
                if !vm.isTutorial && vm.currentStreak >= 3 {
                    Text("\(vm.currentStreak) wins in a row!")
                        .font(.headline)
                        .foregroundStyle(.yellow)
                        .padding(.vertical, 4)
                }

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

                // Share button
                ShareLink(item: vm.shareText) {
                    Label("Share Result", systemImage: "square.and.arrow.up")
                        .font(.subheadline)
                        .foregroundStyle(.cyan)
                }
                .padding(.bottom, 20)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.clear)
        .onAppear {
            animateCards()
        }
    }

    // MARK: - Cards Dealt (staggered animation)

    private var cardsDealtSection: some View {
        VStack(spacing: 6) {
            Text("CARDS DEALT")
                .font(.caption)
                .fontWeight(.semibold)
                .foregroundStyle(.secondary)
                .tracking(2)

            let dealt = vm.allDealtCards
            let allEntries = dealt.players.count + 1 // +1 for central

            // Player cards
            ForEach(Array(dealt.players.enumerated()), id: \.element.name) { index, entry in
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
                .opacity(index < visibleCardCount ? 1 : 0)
                .offset(y: index < visibleCardCount ? 0 : 10)
                .animation(
                    .easeOut(duration: 0.3).delay(Double(index) * 0.15),
                    value: visibleCardCount
                )
            }

            // Central cards (last entry)
            let centralIndex = dealt.players.count
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
            .opacity(centralIndex < visibleCardCount ? 1 : 0)
            .offset(y: centralIndex < visibleCardCount ? 0 : 10)
            .animation(
                .easeOut(duration: 0.3).delay(Double(centralIndex) * 0.15),
                value: visibleCardCount
            )

            // Ensure allEntries is used to suppress warning
            let _ = allEntries
        }
        .padding(.horizontal, 24)
    }

    // MARK: - Leaderboard

    private var leaderboardSection: some View {
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
                    isHuman: entry.name == GameViewModel.humanID,
                    isNewBest: entry.name == GameViewModel.humanID && vm.scoreStore.isNewBest && !vm.isTutorial
                )
            }

            // Personal best line (non-tutorial only)
            if !vm.isTutorial, let best = vm.personalBest {
                Text("Personal best: \(scoreString(best))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .padding(.top, 4)
            }
        }
        .padding(.horizontal, 24)
    }

    // MARK: - Stagger Animation

    private func animateCards() {
        let dealt = vm.allDealtCards
        let totalEntries = dealt.players.count + 1
        // Trigger all at once; individual delays handled by .animation modifier
        withAnimation {
            visibleCardCount = totalEntries
        }
    }

    // MARK: - Subviews

    private func leaderboardRow(rank: Int, name: String, score: Int, isHuman: Bool, isNewBest: Bool = false) -> some View {
        HStack {
            Text("#\(rank)")
                .font(.subheadline.monospacedDigit())
                .foregroundStyle(.secondary)
                .frame(width: 30, alignment: .leading)

            Text(name)
                .font(.subheadline)
                .fontWeight(isHuman ? .bold : .regular)
                .foregroundStyle(isHuman ? .yellow : .white)

            if isNewBest {
                Text("New Best!")
                    .font(.caption2)
                    .fontWeight(.bold)
                    .foregroundStyle(.black)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.yellow)
                    .clipShape(RoundedRectangle(cornerRadius: 4))
            }

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
