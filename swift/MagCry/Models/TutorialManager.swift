import Foundation
import Observation

// MARK: - Tutorial Step

/// Fully scripted tutorial steps. No randomness — every action is predetermined.
/// Covers all 4 trading phases with all 3 central card reveals.
enum TutorialStep: Int, CaseIterable {
    // Phase: Open
    case welcome
    case seeCard
    case askAlice
    case aliceQuote
    case boughtResult
    case canKeepTrading
    case tapNext1

    // Phase: Reveal 1
    case cardRevealed1
    case askBob
    case bobQuote
    case soldResult
    case historyTip
    case tapNext2

    // Phase: Reveal 2 (Carol asks you for a price)
    case botAsksYou
    case botAsksResult
    case tapNext3

    // Phase: Reveal 3
    case cardRevealed3
    case tapNext4

    case done

    /// Coach bubble message for this step.
    var message: String {
        switch self {
        case .welcome:
            return "Welcome! Let's walk through your first trade."
        case .seeCard:
            return "Your card is +12. Your EV (best guess of the total) is 63.6."
        case .askAlice:
            return "Tap Ask Alice to get her price."
        case .aliceQuote:
            return "Her ask is 61 -- below your EV. Buy!"
        case .boughtResult:
            return "You bought at 61. You profit if the final total exceeds 61."
        case .canKeepTrading:
            return "You can keep asking traders for more prices. When you're done, tap Next."
        case .tapNext1:
            return "Tap Next now to reveal a central card."
        case .cardRevealed1:
            return "Card 10 revealed! EV is now 65.2."
        case .askBob:
            return "Tap Ask Bob for his price."
        case .bobQuote:
            return "Bob bids 70 -- above your buy at 61. Sell to lock in profit!"
        case .soldResult:
            return "Sold at 70, bought at 61 -- that's +9 locked in!"
        case .historyTip:
            return "Tip: tap the clock icon anytime to see your full trade history."
        case .tapNext2:
            return "Tap Next to reveal another card."
        case .botAsksYou:
            return "Card 11 revealed! Carol wants YOUR price -- your EV is 67.6, so quote 66-68. Tap Submit."
        case .botAsksResult:
            return "Carol walked away. Traders decide based on your quote vs their own estimate."
        case .tapNext3:
            return "Tap Next to reveal the final card."
        case .cardRevealed3:
            return "Final card: 9. All cards are now revealed -- EV is 69.1."
        case .tapNext4:
            return "Tap Next to see the final settlement."
        case .done:
            return "You're ready! Good luck out there."
        }
    }

    /// Whether this step advances by tapping the coach bubble.
    var requiresTap: Bool {
        switch self {
        case .welcome, .seeCard, .boughtResult, .canKeepTrading,
             .cardRevealed1, .soldResult, .historyTip,
             .botAsksResult, .cardRevealed3, .done:
            return true
        case .askAlice, .aliceQuote, .tapNext1,
             .askBob, .bobQuote, .tapNext2,
             .botAsksYou, .tapNext3, .tapNext4:
            return false
        }
    }

    /// Which bot buttons are enabled during this step (empty = none).
    var allowedBotNames: Set<String> {
        switch self {
        case .askAlice: return ["Alice"]
        case .askBob:   return ["Bob"]
        default:        return []
        }
    }

    /// Whether the Buy button is enabled during this step.
    var canBuy: Bool {
        self == .aliceQuote
    }

    /// Whether the Sell button is enabled during this step.
    var canSell: Bool {
        self == .bobQuote
    }

    /// Whether the Pass button is enabled during this step.
    var canPass: Bool {
        false  // Never pass in tutorial
    }

    /// Whether the Next button is enabled during this step.
    var canNext: Bool {
        switch self {
        case .tapNext1, .tapNext2, .tapNext3, .tapNext4:
            return true
        default:
            return false
        }
    }

    /// The next step, or nil if this is the last one.
    var next: TutorialStep? {
        let all = TutorialStep.allCases
        guard let idx = all.firstIndex(of: self), idx + 1 < all.count else { return nil }
        return all[idx + 1]
    }
}

// MARK: - Tutorial Trigger

/// Events from the game that can advance the tutorial.
enum TutorialTrigger {
    case userTapped
    case botAsked(String)   // which bot was asked
    case bought
    case sold
    case passed
    case nextTapped
    case quotedBot          // user submitted a quote via slider
}

// MARK: - Tutorial Manager

/// Manages the scripted tutorial state machine.
/// Each step only advances when the correct trigger is received.
@Observable
@MainActor
final class TutorialManager {

    /// The current tutorial step, or nil if tutorial is finished.
    private(set) var currentStep: TutorialStep?

    /// Whether the tutorial is currently active.
    var isActive: Bool { currentStep != nil }

    init() {
        self.currentStep = .welcome
    }

    /// Advance the tutorial based on a game event or user tap.
    /// Only advances if the trigger matches the expected action for the current step.
    func advance(trigger: TutorialTrigger) {
        guard let step = currentStep else { return }

        switch (step, trigger) {
        // Tap-to-advance steps
        case (.welcome, .userTapped),
             (.seeCard, .userTapped),
             (.boughtResult, .userTapped),
             (.canKeepTrading, .userTapped),
             (.cardRevealed1, .userTapped),
             (.soldResult, .userTapped),
             (.historyTip, .userTapped),
             (.botAsksResult, .userTapped),
             (.cardRevealed3, .userTapped):
            moveToNext()

        // Done — dismiss tutorial
        case (.done, .userTapped):
            currentStep = nil

        // Ask Alice
        case (.askAlice, .botAsked("Alice")):
            moveToNext()

        // Buy from Alice
        case (.aliceQuote, .bought):
            moveToNext()

        // Tap Next to reveal card 1
        case (.tapNext1, .nextTapped):
            moveToNext()

        // Ask Bob
        case (.askBob, .botAsked("Bob")):
            moveToNext()

        // Sell to Bob
        case (.bobQuote, .sold):
            moveToNext()

        // Tap Next to reveal card 2
        case (.tapNext2, .nextTapped):
            moveToNext()

        // User submitted quote to Carol via slider
        case (.botAsksYou, .quotedBot):
            moveToNext()

        // Tap Next to reveal card 3
        case (.tapNext3, .nextTapped):
            moveToNext()

        // Tap Next to settle
        case (.tapNext4, .nextTapped):
            moveToNext()

        default:
            break  // Ignore triggers that don't match the current step
        }
    }

    /// Skip the tutorial entirely.
    func skip() {
        currentStep = nil
    }

    // MARK: - Private

    private func moveToNext() {
        currentStep = currentStep?.next
    }
}
