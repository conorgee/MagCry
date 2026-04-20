import SwiftUI

/// Displays the 3 central card slots: revealed cards shown face-up, hidden as "?".
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
                    cardView(index: index)
                }
            }
        }
    }

    @ViewBuilder
    private func cardView(index: Int) -> some View {
        if index < revealed.count {
            // Revealed card
            ZStack {
                RoundedRectangle(cornerRadius: 10)
                    .fill(Color.white)
                    .frame(width: 60, height: 80)
                    .shadow(color: .cyan.opacity(0.3), radius: 4)

                Text(cardString(revealed[index]))
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundStyle(.black)
            }
        } else {
            // Hidden card
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
    }

    private func cardString(_ value: Int) -> String {
        value >= 0 ? "+\(value)" : "\(value)"
    }
}
