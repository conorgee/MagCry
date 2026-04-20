import SwiftUI

/// Full trade history, presented as a sheet from GameView.
/// Entries are grouped by phase with card-based styling.
struct TradeLogView: View {
    let entries: [LogEntry]
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        ForEach(entries) { entry in
                            entryRow(entry)
                                .id(entry.id)
                        }
                    }
                    .padding(.vertical, 8)
                }
                .onChange(of: entries.count) {
                    if let last = entries.last {
                        withAnimation(.easeOut(duration: 0.2)) {
                            proxy.scrollTo(last.id, anchor: .bottom)
                        }
                    }
                }
            }
            .background(Color(white: 0.08))
            .navigationTitle("Trade History")
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
        .presentationDetents([.medium, .large])
    }

    // MARK: - Entry Row

    @ViewBuilder
    private func entryRow(_ entry: LogEntry) -> some View {
        switch entry.kind {
        // Phase headers — full-width dark bar
        case .phaseChange(let label):
            sectionHeader(label)

        case .cardReveal(let index, let card):
            sectionHeader("Reveal \(index) -- Card: \(card)")

        // Bot asks you for a price
        case .botAsksYou(let botName):
            eventRow(
                label: botName,
                detail: "asks for your price",
                labelColor: .orange,
                detailColor: .white.opacity(0.7),
                weight: .semibold
            )

        // Your quote
        case .yourQuote(let botName, let bid, let ask):
            eventRow(
                label: "You",
                detail: "quoted \(botName): \(bid) - \(ask)",
                labelColor: .cyan,
                detailColor: .white.opacity(0.6),
                weight: .regular
            )

        // Your trades — highlighted
        case .yourBuy(let botName, let price):
            tradeRow(
                action: "BUY",
                counterparty: botName,
                price: price,
                color: .green,
                isYours: true
            )

        case .yourSell(let botName, let price):
            tradeRow(
                action: "SELL",
                counterparty: botName,
                price: price,
                color: .red,
                isYours: true
            )

        case .yourPass(let botName):
            eventRow(
                label: "You",
                detail: "passed on \(botName)",
                labelColor: .white.opacity(0.5),
                detailColor: .white.opacity(0.4),
                weight: .regular
            )

        // Bot acts on your quote
        case .botBuys(let botName, let price):
            tradeRow(
                action: "BUYS",
                counterparty: botName,
                price: price,
                color: .green,
                isYours: true
            )

        case .botSells(let botName, let price):
            tradeRow(
                action: "SELLS",
                counterparty: botName,
                price: price,
                color: .red,
                isYours: true
            )

        case .botWalks(let botName):
            eventRow(
                label: botName,
                detail: "walks away",
                labelColor: .white.opacity(0.5),
                detailColor: .white.opacity(0.4),
                weight: .regular
            )

        // Bot-to-bot trades — muted
        case .botTrade(let buyer, let seller, let price):
            HStack(spacing: 6) {
                Text(buyer)
                    .foregroundStyle(.white.opacity(0.35))
                Text("buys from")
                    .foregroundStyle(.white.opacity(0.25))
                Text(seller)
                    .foregroundStyle(.white.opacity(0.35))
                Text("at \(price)")
                    .foregroundStyle(.white.opacity(0.3))
            }
            .font(.caption)
            .padding(.horizontal, 16)
            .padding(.vertical, 4)

        // Info — small secondary text
        case .info(let message, let important):
            Text(message)
                .font(.caption)
                .italic()
                .foregroundStyle(important ? .yellow.opacity(0.8) : .white.opacity(0.35))
                .padding(.horizontal, 16)
                .padding(.vertical, 3)
        }
    }

    // MARK: - Components

    private func sectionHeader(_ text: String) -> some View {
        Text(text.uppercased())
            .font(.caption.weight(.bold))
            .foregroundStyle(.white.opacity(0.5))
            .tracking(1.2)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(Color.white.opacity(0.04))
            .padding(.top, 8)
    }

    private func eventRow(
        label: String,
        detail: String,
        labelColor: Color,
        detailColor: Color,
        weight: Font.Weight
    ) -> some View {
        HStack(spacing: 6) {
            Text(label)
                .fontWeight(weight)
                .foregroundStyle(labelColor)
            Text(detail)
                .foregroundStyle(detailColor)
        }
        .font(.subheadline)
        .padding(.horizontal, 16)
        .padding(.vertical, 5)
    }

    private func tradeRow(
        action: String,
        counterparty: String,
        price: Int,
        color: Color,
        isYours: Bool
    ) -> some View {
        HStack(spacing: 8) {
            // Action badge
            Text(action)
                .font(.caption.weight(.bold))
                .foregroundStyle(color)
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .overlay(
                    RoundedRectangle(cornerRadius: 4)
                        .stroke(color.opacity(0.5), lineWidth: 1)
                )

            if isYours {
                Text(counterparty)
                    .foregroundStyle(.white.opacity(0.8))
            } else {
                Text(counterparty)
                    .foregroundStyle(.white.opacity(0.4))
            }

            Spacer()

            Text("@ \(price)")
                .fontWeight(.medium)
                .foregroundStyle(isYours ? .white : .white.opacity(0.4))
        }
        .font(.subheadline)
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
        .background(isYours ? color.opacity(0.08) : Color.clear)
    }
}
