package com.magcry.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// -- Tutorial Step --

/**
 * Fully scripted tutorial steps. No randomness — every action is predetermined.
 * Covers all 4 trading phases with all 3 central card reveals.
 */
enum class TutorialStep(
    val message: String,
    val requiresTap: Boolean,
    val allowedBotNames: Set<String> = emptySet(),
    val canBuy: Boolean = false,
    val canSell: Boolean = false,
    val canPass: Boolean = false,
    val canNext: Boolean = false
) {
    // Phase: Open
    WELCOME(
        message = "Welcome! Let's walk through your first trade.",
        requiresTap = true
    ),
    SEE_CARD(
        message = "Your card is +12. Your EV (best guess of the total) is 63.6.",
        requiresTap = true
    ),
    ASK_ALICE(
        message = "Tap Ask Alice to get her price.",
        requiresTap = false,
        allowedBotNames = setOf("Alice")
    ),
    ALICE_QUOTE(
        message = "Her ask is 61 -- below your EV. Buy!",
        requiresTap = false,
        canBuy = true
    ),
    BOUGHT_RESULT(
        message = "You bought at 61. You profit if the final total exceeds 61.",
        requiresTap = true
    ),
    CAN_KEEP_TRADING(
        message = "You can keep asking traders for more prices. When you're done, tap Next.",
        requiresTap = true
    ),
    TAP_NEXT1(
        message = "Tap Next now to reveal a central card.",
        requiresTap = false,
        canNext = true
    ),

    // Phase: Reveal 1
    CARD_REVEALED1(
        message = "Card 10 revealed! EV is now 65.2.",
        requiresTap = true
    ),
    ASK_BOB(
        message = "Tap Ask Bob for his price.",
        requiresTap = false,
        allowedBotNames = setOf("Bob")
    ),
    BOB_QUOTE(
        message = "Bob bids 70 -- above your buy at 61. Sell to lock in profit!",
        requiresTap = false,
        canSell = true
    ),
    SOLD_RESULT(
        message = "Sold at 70, bought at 61 -- that's +9 locked in!",
        requiresTap = true
    ),
    HISTORY_TIP(
        message = "Tip: tap the clock icon anytime to see your full trade history.",
        requiresTap = true
    ),
    TAP_NEXT2(
        message = "Tap Next to reveal another card.",
        requiresTap = false,
        canNext = true
    ),

    // Phase: Reveal 2 (Carol asks you for a price)
    BOT_ASKS_YOU(
        message = "Card 11 revealed! Carol wants YOUR price -- your EV is 67.6, so quote 66-68. Tap Submit.",
        requiresTap = false
    ),
    BOT_ASKS_RESULT(
        message = "Carol walked away. Traders decide based on your quote vs their own estimate.",
        requiresTap = true
    ),
    TAP_NEXT3(
        message = "Tap Next to reveal the final card.",
        requiresTap = false,
        canNext = true
    ),

    // Phase: Reveal 3
    CARD_REVEALED3(
        message = "Final card: 9. All cards are now revealed -- EV is 69.1.",
        requiresTap = true
    ),
    TAP_NEXT4(
        message = "Tap Next to see the final settlement.",
        requiresTap = false,
        canNext = true
    ),

    DONE(
        message = "You're ready! Good luck out there.",
        requiresTap = true
    );

    /** The next step, or null if this is the last one. */
    val next: TutorialStep?
        get() {
            val all = entries
            val idx = all.indexOf(this)
            return if (idx + 1 < all.size) all[idx + 1] else null
        }
}

// -- Tutorial Trigger --

/** Events from the game that can advance the tutorial. */
sealed class TutorialTrigger {
    data object UserTapped : TutorialTrigger()
    data class BotAsked(val botName: String) : TutorialTrigger()
    data object Bought : TutorialTrigger()
    data object Sold : TutorialTrigger()
    data object Passed : TutorialTrigger()
    data object NextTapped : TutorialTrigger()
    data object QuotedBot : TutorialTrigger()
}

// -- Tutorial Manager --

/**
 * Manages the scripted tutorial state machine.
 * Each step only advances when the correct trigger is received.
 */
class TutorialManager {
    var currentStep: TutorialStep? by mutableStateOf(TutorialStep.WELCOME)
        private set

    val isActive: Boolean get() = currentStep != null

    /** Advance the tutorial based on a game event or user tap. */
    fun advance(trigger: TutorialTrigger) {
        val step = currentStep ?: return

        when {
            // Tap-to-advance steps
            trigger is TutorialTrigger.UserTapped && step.requiresTap && step != TutorialStep.DONE -> {
                moveToNext()
            }
            // Done — dismiss tutorial
            step == TutorialStep.DONE && trigger is TutorialTrigger.UserTapped -> {
                currentStep = null
            }
            // Ask Alice
            step == TutorialStep.ASK_ALICE && trigger is TutorialTrigger.BotAsked && trigger.botName == "Alice" -> {
                moveToNext()
            }
            // Buy from Alice
            step == TutorialStep.ALICE_QUOTE && trigger is TutorialTrigger.Bought -> {
                moveToNext()
            }
            // Tap Next to reveal card 1
            step == TutorialStep.TAP_NEXT1 && trigger is TutorialTrigger.NextTapped -> {
                moveToNext()
            }
            // Ask Bob
            step == TutorialStep.ASK_BOB && trigger is TutorialTrigger.BotAsked && trigger.botName == "Bob" -> {
                moveToNext()
            }
            // Sell to Bob
            step == TutorialStep.BOB_QUOTE && trigger is TutorialTrigger.Sold -> {
                moveToNext()
            }
            // Tap Next to reveal card 2
            step == TutorialStep.TAP_NEXT2 && trigger is TutorialTrigger.NextTapped -> {
                moveToNext()
            }
            // User submitted quote to Carol via slider
            step == TutorialStep.BOT_ASKS_YOU && trigger is TutorialTrigger.QuotedBot -> {
                moveToNext()
            }
            // Tap Next to reveal card 3
            step == TutorialStep.TAP_NEXT3 && trigger is TutorialTrigger.NextTapped -> {
                moveToNext()
            }
            // Tap Next to settle
            step == TutorialStep.TAP_NEXT4 && trigger is TutorialTrigger.NextTapped -> {
                moveToNext()
            }
        }
    }

    /** Skip the tutorial entirely. */
    fun skip() {
        currentStep = null
    }

    private fun moveToNext() {
        currentStep = currentStep?.next
    }
}
