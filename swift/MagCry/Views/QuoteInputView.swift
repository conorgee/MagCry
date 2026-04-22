import SwiftUI

/// Slider-based quote input shown when a trader asks "What's your price?"
/// User sets the bid with a slider; ask = bid + 2 is shown live.
/// When `lockedBid` is set (tutorial mode), the slider is disabled at that value.
struct QuoteInputView: View {
    var vm: GameViewModel
    let botName: String
    var lockedBid: Int? = nil

    @State private var bidValue: Double = 60

    private var isLocked: Bool { lockedBid != nil }
    private var displayBid: Int { lockedBid ?? Int(bidValue) }
    private var displayAsk: Int { displayBid + 2 }

    var body: some View {
        VStack(spacing: 16) {
            // Trader asking
            Text("\(botName) asks:")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Text("\"What's your price?\"")
                .font(.title3)
                .fontWeight(.semibold)
                .foregroundStyle(.white)

            // Live bid/ask display
            HStack(spacing: 24) {
                VStack {
                    Text("BID")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text("\(displayBid)")
                        .font(.title.monospacedDigit())
                        .fontWeight(.bold)
                        .foregroundStyle(.red)
                }
                VStack {
                    Text("ASK")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text("\(displayAsk)")
                        .font(.title.monospacedDigit())
                        .fontWeight(.bold)
                        .foregroundStyle(.green)
                }
            }

            // Slider (disabled when locked in tutorial)
            VStack(spacing: 4) {
                let sliderRange = Double(vm.suggestedBidRange.lowerBound)...Double(vm.suggestedBidRange.upperBound)
                Slider(value: isLocked ? .constant(Double(lockedBid!)) : $bidValue,
                       in: sliderRange, step: 1)
                    .tint(isLocked ? .gray : .cyan)
                    .disabled(isLocked)

                HStack {
                    Text("\(vm.suggestedBidRange.lowerBound)")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                    Spacer()
                    if vm.difficulty == .easy || isLocked {
                        Text("EV: \(vm.playerEV, specifier: "%.0f")")
                            .font(.caption2)
                            .foregroundStyle(.cyan)
                        Spacer()
                    }
                    Text("\(vm.suggestedBidRange.upperBound)")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }

            // Submit button
            Button("Submit Price") {
                let bid = displayBid
                let quote = Quote(bid: bid, ask: bid + 2)
                vm.submitQuote(quote)
            }
            .buttonStyle(PrimaryButtonStyle(color: .cyan))
        }
        .padding()
        .background(Color.white.opacity(0.05))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .onAppear {
            if lockedBid == nil {
                bidValue = (vm.playerEV.rounded()) - 1
            }
        }
    }
}
