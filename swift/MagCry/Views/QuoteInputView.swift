import SwiftUI

/// Slider-based quote input shown when a bot asks "What's your price?"
/// User sets the bid with a slider; ask = bid + 2 is shown live.
struct QuoteInputView: View {
    var vm: GameViewModel
    let botName: String

    @State private var bidValue: Double = 60

    var body: some View {
        VStack(spacing: 16) {
            // Bot asking
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
                    Text("\(Int(bidValue))")
                        .font(.title.monospacedDigit())
                        .fontWeight(.bold)
                        .foregroundStyle(.red)
                }
                VStack {
                    Text("ASK")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text("\(Int(bidValue) + 2)")
                        .font(.title.monospacedDigit())
                        .fontWeight(.bold)
                        .foregroundStyle(.green)
                }
            }

            // Slider
            VStack(spacing: 4) {
                let sliderRange = Double(vm.suggestedBidRange.lowerBound)...Double(vm.suggestedBidRange.upperBound)
                Slider(value: $bidValue, in: sliderRange, step: 1)
                    .tint(.cyan)

                HStack {
                    Text("\(vm.suggestedBidRange.lowerBound)")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Text("EV: \(vm.playerEV, specifier: "%.0f")")
                        .font(.caption2)
                        .foregroundStyle(.cyan)
                    Spacer()
                    Text("\(vm.suggestedBidRange.upperBound)")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }

            // Submit button
            Button("Submit Price") {
                let bid = Int(bidValue)
                let quote = Quote(bid: bid, ask: bid + 2)
                vm.submitQuote(quote)
            }
            .buttonStyle(PrimaryButtonStyle(color: .cyan))
        }
        .padding()
        .background(Color.white.opacity(0.05))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .onAppear {
            bidValue = (vm.playerEV.rounded()) - 1
        }
    }
}
