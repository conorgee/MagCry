import SwiftUI

struct MainMenuView: View {
    var vm: GameViewModel
    @State private var showInstructions = false

    private let bookURL = URL(string: "https://www.amazon.com/Trading-Game-Confession-Gary-Stevenson/dp/0593727215")!

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer()

                // Logo — crow artwork, seamless on black
                if let img = UIImage(named: "logo") {
                    Image(uiImage: img)
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .padding(.horizontal, 40)
                }

                // Title
                Text("MagCry")
                    .font(.system(size: 38, weight: .bold, design: .default))
                    .foregroundStyle(.white)
                    .padding(.top, 8)
                    .padding(.bottom, 36)

                // Difficulty buttons — outlined, no fill
                VStack(spacing: 14) {
                    difficultyButton(.easy, color: Color(red: 0.2, green: 0.7, blue: 0.3))
                    difficultyButton(.medium, color: Color(red: 0.85, green: 0.6, blue: 0.15))
                    difficultyButton(.hard, color: Color(red: 0.8, green: 0.25, blue: 0.25))
                }
                .padding(.horizontal, 48)

                // How to Play
                Button {
                    showInstructions = true
                } label: {
                    Text("How to Play")
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.5))
                }
                .padding(.top, 24)

                Spacer()

                // Attribution
                VStack(spacing: 4) {
                    Text("Based on")
                        .font(.caption2)
                        .foregroundStyle(.white.opacity(0.3))
                    Link(destination: bookURL) {
                        Text("The Trading Game by Gary Stevenson")
                            .font(.caption2)
                            .foregroundStyle(.white.opacity(0.45))
                            .underline()
                    }
                    Link(destination: URL(string: "https://buymeacoffee.com/conorgee")!) {
                        Text("Buy Me a Coffee")
                            .font(.caption2)
                            .foregroundStyle(.white.opacity(0.45))
                            .underline()
                    }
                    .padding(.top, 12)
                }
                .padding(.bottom, 16)
            }
        }
        .sheet(isPresented: $showInstructions) {
            InstructionsView()
        }
    }

    private func difficultyButton(_ diff: Difficulty, color: Color) -> some View {
        Button {
            vm.startGame(difficulty: diff)
        } label: {
            Text(diff.label)
                .font(.headline)
                .foregroundStyle(color)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(color.opacity(0.6), lineWidth: 1.5)
                )
        }
    }
}

#Preview {
    MainMenuView(vm: GameViewModel())
}
