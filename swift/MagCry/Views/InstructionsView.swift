import SwiftUI

struct InstructionsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var currentPage = 0

    private let totalPages = 6

    var body: some View {
        ZStack {
            Color(white: 0.08).ignoresSafeArea()

            VStack(spacing: 0) {
                // Dismiss
                HStack {
                    Spacer()
                    Button("Done") { dismiss() }
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.white.opacity(0.6))
                        .padding(.trailing, 20)
                        .padding(.top, 16)
                }

                // Pages
                TabView(selection: $currentPage) {
                    pageTheGame.tag(0)
                    pageYourCard.tag(1)
                    pageGetAPrice.tag(2)
                    pageBuyOrSell.tag(3)
                    pageCardReveals.tag(4)
                    pageWatchTheBots.tag(5)
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .animation(.easeInOut(duration: 0.3), value: currentPage)

                // Page dots + button
                VStack(spacing: 16) {
                    HStack(spacing: 8) {
                        ForEach(0..<totalPages, id: \.self) { i in
                            Circle()
                                .fill(i == currentPage ? Color.cyan : Color.white.opacity(0.25))
                                .frame(width: 8, height: 8)
                        }
                    }

                    if currentPage < totalPages - 1 {
                        Button {
                            withAnimation { currentPage += 1 }
                        } label: {
                            Text("Next")
                                .font(.headline)
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(Color.cyan.opacity(0.5))
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                        }
                        .padding(.horizontal, 48)
                    } else {
                        Button {
                            dismiss()
                        } label: {
                            Text("Got it")
                                .font(.headline)
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(Color.green.opacity(0.6))
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                        }
                        .padding(.horizontal, 48)
                    }
                }
                .padding(.bottom, 32)
            }
        }
        .presentationDetents([.large])
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: - Page 1: The Game
    // ═══════════════════════════════════════════════════════════════════════

    private var pageTheGame: some View {
        instructionPage(
            icon: "suit.spade.fill",
            title: "The Game",
            lines: [
                "5 players each get 1 secret card.",
                "3 more cards go face-down in the center.",
                "You're all betting on the sum of these 8 cards."
            ]
        ) {
            deckFanVisual
        }
    }

    private var deckFanVisual: some View {
        HStack(spacing: -8) {
            ForEach(0..<8, id: \.self) { i in
                RoundedRectangle(cornerRadius: 4)
                    .fill(i < 5
                        ? Color.blue.opacity(0.4)
                        : Color.cyan.opacity(0.4))
                    .frame(width: 28, height: 40)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4)
                            .stroke(i < 5 ? Color.blue.opacity(0.6) : Color.cyan.opacity(0.6),
                                    lineWidth: 1)
                    )
                    .rotationEffect(.degrees(Double(i - 4) * 5))
            }
        }
        .padding(.top, 8)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: - Page 2: Your Card
    // ═══════════════════════════════════════════════════════════════════════

    private var pageYourCard: some View {
        instructionPage(
            icon: "eye.fill",
            title: "Your Card",
            lines: [
                "Cards range from -10 to 20.",
                "Only you can see yours.",
                "A higher card means a higher expected sum."
            ]
        ) {
            mockCardVisual("+12")
        }
    }

    private func mockCardVisual(_ value: String) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 10)
                .fill(Color.yellow.opacity(0.12))
                .frame(width: 70, height: 100)
            RoundedRectangle(cornerRadius: 10)
                .stroke(Color.yellow.opacity(0.5), lineWidth: 1.5)
                .frame(width: 70, height: 100)
            Text(value)
                .font(.title.monospacedDigit().bold())
                .foregroundStyle(.yellow)
        }
        .padding(.top, 8)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: - Page 3: Get a Price
    // ═══════════════════════════════════════════════════════════════════════

    private var pageGetAPrice: some View {
        instructionPage(
            icon: "bubble.left.and.bubble.right.fill",
            title: "Get a Price",
            lines: [
                "Tap a trader to ask for a two-way quote.",
                "The spread is always exactly 2.",
                "You see their bid and ask price."
            ]
        ) {
            mockQuoteVisual
        }
    }

    private var mockQuoteVisual: some View {
        VStack(spacing: 8) {
            HStack {
                Text("Alice")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white)
                Spacer()
                HStack(spacing: 6) {
                    Text("58")
                        .foregroundStyle(.red)
                        .fontWeight(.bold)
                    Text("--")
                        .foregroundStyle(.secondary)
                    Text("60")
                        .foregroundStyle(.green)
                        .fontWeight(.bold)
                }
                .font(.callout.monospacedDigit())
            }

            HStack(spacing: 6) {
                mockActionButton("Buy 60", color: .green)
                mockActionButton("Sell 58", color: .red)
                mockActionButton("Pass", color: .gray)
            }
        }
        .padding(12)
        .background(Color.white.opacity(0.06))
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .padding(.horizontal, 20)
        .padding(.top, 8)
    }

    private func mockActionButton(_ text: String, color: Color) -> some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .background(color.opacity(0.2))
            .foregroundStyle(color == .gray ? .secondary : color)
            .clipShape(RoundedRectangle(cornerRadius: 6))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: - Page 4: Buy or Sell
    // ═══════════════════════════════════════════════════════════════════════

    private var pageBuyOrSell: some View {
        instructionPage(
            icon: "arrow.up.arrow.down",
            title: "Buy or Sell",
            lines: [
                "Buy if you think the sum will be ABOVE the ask.",
                "Sell if you think it will be BELOW the bid.",
                "Not sure? Just pass."
            ]
        ) {
            pnlExamplesVisual
        }
    }

    private var pnlExamplesVisual: some View {
        VStack(spacing: 8) {
            pnlExampleRow(
                action: "Buy at 60",
                outcome: "Sum = 65",
                pnl: "+5",
                color: .green
            )
            pnlExampleRow(
                action: "Sell at 58",
                outcome: "Sum = 65",
                pnl: "-7",
                color: .red
            )
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
    }

    private func pnlExampleRow(
        action: String, outcome: String, pnl: String, color: Color
    ) -> some View {
        HStack {
            Text(action)
                .font(.caption.weight(.medium))
                .foregroundStyle(.white.opacity(0.7))
            Image(systemName: "arrow.right")
                .font(.caption2)
                .foregroundStyle(.secondary)
            Text(outcome)
                .font(.caption.weight(.medium))
                .foregroundStyle(.white.opacity(0.7))
            Spacer()
            Text(pnl)
                .font(.callout.monospacedDigit().bold())
                .foregroundStyle(color)
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 12)
        .background(color.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: - Page 5: Card Reveals
    // ═══════════════════════════════════════════════════════════════════════

    private var pageCardReveals: some View {
        instructionPage(
            icon: "rectangle.stack.fill",
            title: "Card Reveals",
            lines: [
                "Between rounds, central cards flip one at a time.",
                "New info means better estimates.",
                "Adjust your strategy as cards are revealed."
            ]
        ) {
            miniCentralCardsVisual
        }
    }

    private var miniCentralCardsVisual: some View {
        HStack(spacing: 12) {
            // Face-up card
            ZStack {
                RoundedRectangle(cornerRadius: 6)
                    .fill(Color.cyan.opacity(0.15))
                    .frame(width: 44, height: 60)
                RoundedRectangle(cornerRadius: 6)
                    .stroke(Color.cyan.opacity(0.5), lineWidth: 1)
                    .frame(width: 44, height: 60)
                Text("9")
                    .font(.title3.monospacedDigit().bold())
                    .foregroundStyle(.cyan)
            }

            // Face-down cards
            ForEach(0..<2, id: \.self) { _ in
                ZStack {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(Color.white.opacity(0.06))
                        .frame(width: 44, height: 60)
                    RoundedRectangle(cornerRadius: 6)
                        .stroke(Color.white.opacity(0.15), lineWidth: 1)
                        .frame(width: 44, height: 60)
                    Text("?")
                        .font(.title3.weight(.medium))
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(.top, 8)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: - Page 6: Watch the Bots
    // ═══════════════════════════════════════════════════════════════════════

    private var pageWatchTheBots: some View {
        instructionPage(
            icon: "brain.head.profile",
            title: "Watch the Open Cry Traders",
            lines: [
                "Traders track your trading pattern.",
                "Keep buying and they'll raise prices on you.",
                "On Hard mode, traders bluff to mislead you."
            ]
        ) {
            EmptyView()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: - Page Template
    // ═══════════════════════════════════════════════════════════════════════

    private func instructionPage<V: View>(
        icon: String,
        title: String,
        lines: [String],
        @ViewBuilder visual: () -> V
    ) -> some View {
        VStack(spacing: 16) {
            Spacer()

            Image(systemName: icon)
                .font(.system(size: 48))
                .foregroundStyle(.cyan)
                .padding(.bottom, 4)

            Text(title)
                .font(.system(size: 24, weight: .bold))
                .foregroundStyle(.white)

            VStack(spacing: 6) {
                ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                    Text(line)
                        .font(.body)
                        .foregroundStyle(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                }
            }
            .padding(.horizontal, 32)

            visual()

            Spacer()
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

#Preview {
    InstructionsView()
}
