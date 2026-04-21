import SwiftUI

/// Displays the 3 central card slots: revealed cards shown face-up with flip animation, hidden as "?".
struct CentralCardsView: View {
    let centralCards: [Int]   // All 3 central cards (for count)
    let revealed: [Int]       // Cards revealed so far

    var body: some View {
        VStack(spacing: 6) {
            Text("CENTRAL CARDS")
                .font(.caption)
                .fontWeight(.semibold)
                .foregroundStyle(.secondary)
                .tracking(2)

            HStack(spacing: 12) {
                ForEach(0..<3, id: \.self) { index in
                    FlipCardView(
                        isRevealed: index < revealed.count,
                        value: index < revealed.count ? revealed[index] : nil
                    )
                }
            }
        }
    }
}

/// A single card that flips from face-down to face-up with a 3D rotation.
private struct FlipCardView: View {
    let isRevealed: Bool
    let value: Int?

    @State private var flipDegrees: Double = 0

    var body: some View {
        ZStack {
            if flipDegrees < 90 && !isRevealed {
                // Face-down (showing back)
                cardBack
            } else if flipDegrees < 90 && isRevealed && !hasFlipped {
                // Already revealed on appear (no animation needed)
                cardFace
            } else if flipDegrees >= 90 {
                // Mid-flip: show face (counter-rotated so text isn't mirrored)
                cardFace
                    .rotation3DEffect(.degrees(180), axis: (x: 0, y: 1, z: 0))
            } else {
                cardFace
            }
        }
        .rotation3DEffect(.degrees(flipDegrees), axis: (x: 0, y: 1, z: 0))
        .onChange(of: isRevealed) { _, newValue in
            if newValue {
                withAnimation(.easeInOut(duration: 0.5)) {
                    flipDegrees = 180
                }
            }
        }
    }

    private var hasFlipped: Bool { flipDegrees > 0 }

    private var cardFace: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 10)
                .fill(Color.white)
                .frame(width: 60, height: 80)
                .shadow(color: .cyan.opacity(0.3), radius: 4)

            if let value = value {
                Text(cardString(value))
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundStyle(.black)
            }
        }
    }

    private var cardBack: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 10)
                .fill(Color.blue.opacity(0.3))
                .frame(width: 60, height: 80)

            RoundedRectangle(cornerRadius: 10)
                .strokeBorder(Color.blue.opacity(0.5), lineWidth: 2)
                .frame(width: 60, height: 80)

            Text("?")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundStyle(.blue.opacity(0.6))
        }
    }

    private func cardString(_ value: Int) -> String {
        value >= 0 ? "+\(value)" : "\(value)"
    }
}
