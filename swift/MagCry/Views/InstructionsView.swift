import SwiftUI

struct InstructionsView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    section("The Setup") {
                        bullet("A deck of 17 cards: -10, 1 through 15, and 20.")
                        bullet("Total sum of all cards: 130.")
                        bullet("Each of 5 players is dealt 1 private card.")
                        bullet("3 cards go face-down in the centre.")
                    }

                    section("What You're Trading") {
                        bullet("You're trading the final sum of 8 cards in play (5 private + 3 central).")
                        bullet("You know your own card and nothing else at first.")
                        bullet("Central cards are revealed one at a time between trading phases.")
                    }

                    section("How Trading Works") {
                        bullet("Tap a bot's name to ask them for a two-way price.")
                        bullet("Every price has a spread of exactly 2 (e.g. 54 - 56).")
                        bullet("You can Buy at their ask (high), Sell at their bid (low), or Pass.")
                        bullet("You can ask multiple bots per turn, one at a time.")
                        bullet("Sometimes a bot will ask you for a price instead -- use the slider to quote.")
                    }

                    section("Phases") {
                        bullet("Open Trading -- trade with no central cards revealed.")
                        bullet("Reveal 1, 2, 3 -- a central card is flipped before each phase.")
                        bullet("After you tap Next, a short wind-down occurs (bots trade among themselves and may ask you one more time).")
                    }

                    section("Settlement") {
                        bullet("After all 3 central cards are revealed and final trading ends, the true sum is calculated.")
                        bullet("Every trade settles against the final sum.")
                        bullet("If you bought at 54 and the sum is 60, you profit 6.")
                        bullet("If you sold at 54 and the sum is 60, you lose 6.")
                        bullet("Highest total P&L wins.")
                    }

                    section("Tips") {
                        bullet("Your expected value (EV) updates as cards are revealed -- use it to guide your prices.")
                        bullet("Watch for bots adapting to your trading pattern. If you keep buying, they'll raise prices.")
                        bullet("On harder difficulties, bots bluff and adjust aggressively.")
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
            }
            .background(Color(white: 0.08))
            .navigationTitle("How to Play")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                        .foregroundStyle(.white)
                }
            }
            .toolbarBackground(Color(white: 0.12), for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
        }
        .presentationDetents([.large])
    }

    // MARK: - Helpers

    private func section(_ title: String, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.headline)
                .foregroundStyle(.white)
            content()
        }
    }

    private func bullet(_ text: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Text("--")
                .foregroundStyle(.secondary)
                .font(.subheadline.monospaced())
            Text(text)
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.85))
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}
