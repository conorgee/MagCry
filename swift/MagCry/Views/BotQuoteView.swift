import SwiftUI

/// Shows a single bot's live quote with Buy / Sell / Pass action buttons.
struct BotQuoteView: View {
    let botName: String
    let quote: Quote
    let onBuy: () -> Void
    let onSell: () -> Void
    let onPass: () -> Void

    var body: some View {
        VStack(spacing: 10) {
            // Bot name + quote
            HStack {
                Text(botName)
                    .font(.headline)
                    .foregroundStyle(.white)

                Spacer()

                HStack(spacing: 6) {
                    Text("\(quote.bid)")
                        .foregroundStyle(.red)
                        .fontWeight(.bold)
                    Text("—")
                        .foregroundStyle(.secondary)
                    Text("\(quote.ask)")
                        .foregroundStyle(.green)
                        .fontWeight(.bold)
                }
                .font(.title3.monospacedDigit())
            }

            // Action buttons: Buy / Sell / Pass
            HStack(spacing: 8) {
                Button {
                    onBuy()
                } label: {
                    Text("Buy \(quote.ask)")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(Color.green.opacity(0.25))
                        .foregroundStyle(.green)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }

                Button {
                    onSell()
                } label: {
                    Text("Sell \(quote.bid)")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(Color.red.opacity(0.25))
                        .foregroundStyle(.red)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }

                Button {
                    onPass()
                } label: {
                    Text("Pass")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(Color.white.opacity(0.1))
                        .foregroundStyle(.secondary)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
            }
        }
        .padding(12)
        .background(Color.white.opacity(0.05))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
