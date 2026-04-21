import SwiftUI

/// Floating coach bubble displayed during the interactive tutorial.
/// Sits at the top of the game screen and shows contextual guidance.
/// Non-blocking — game UI beneath remains fully interactive.
struct TutorialCoachView: View {
    var manager: TutorialManager

    var body: some View {
        if let step = manager.currentStep {
            VStack(spacing: 0) {
                HStack(alignment: .top, spacing: 12) {
                    // Message
                    Text(step.message)
                        .font(.callout)
                        .foregroundStyle(.white)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    // Skip button
                    Button {
                        withAnimation(.easeOut(duration: 0.2)) {
                            manager.skip()
                        }
                    } label: {
                        Text("Skip")
                            .font(.caption.weight(.medium))
                            .foregroundStyle(.white.opacity(0.5))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)

                // Tap hint for tap-to-advance steps
                if step.requiresTap {
                    Text("Tap to continue")
                        .font(.caption2)
                        .foregroundStyle(.cyan.opacity(0.6))
                        .padding(.bottom, 8)
                }
            }
            .background(
                RoundedRectangle(cornerRadius: 14)
                    .fill(Color(white: 0.14).opacity(0.95))
                    .shadow(color: .black.opacity(0.3), radius: 8, y: 4)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(Color.cyan.opacity(0.25), lineWidth: 1)
            )
            .padding(.horizontal, 16)
            .contentShape(Rectangle())
            .onTapGesture {
                if step.requiresTap {
                    withAnimation(.easeInOut(duration: 0.25)) {
                        manager.advance(trigger: .userTapped)
                    }
                }
            }
            .transition(.asymmetric(
                insertion: .move(edge: .top).combined(with: .opacity),
                removal: .opacity
            ))
            .animation(.easeInOut(duration: 0.3), value: step)
            .id(step)  // Force re-render on step change for transition
        }
    }
}
