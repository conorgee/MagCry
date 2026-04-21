package com.magcry.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// -- Tutorial Step --

/** Fully scripted tutorial steps. No randomness — every action is predetermined. */
enum class TutorialStep(
    val message: String,
    val requiresTap: Boolean,
    val allowedBotNames: Set<String> = emptySet(),
    val canBuy: Boolean = false,
    val canSell: Boolean = false,
    val canPass: Boolean = false,
    val canNext: Boolean = false
) {
    WELCOME(
        message = "Welcome! Let's walk through your first trade.",
        requiresTap = true
    ),
    SEE_CARD(
        message = "Your card is +12. Your EV (best guess of the total) is about 66.",
        requiresTap = true
    ),
    ASK_ALICE(
        message = "Tap Ask Alice to get her price.",
        requiresTap = false,
        allowedBotNames = setOf("Alice")
    ),
    ALICE_QUOTE(
        message = "Her ask is 61 -- below your EV of 66. Buy!",
        requiresTap = false,
        canBuy = true
    ),
    BOUGHT_RESULT(
        message = "You bought at 61. You profit if the final total exceeds 61.",
        requiresTap = true
    ),
    TAP_NEXT1(
        message = "Tap Next to reveal a central card.",
        requiresTap = false,
        canNext = true
    ),
    CARD_REVEALED(
        message = "Card 10 revealed! EV is now about 68. Your buy at 61 looks great.",
        requiresTap = true
    ),
    ASK_BOB(
        message = "Now tap Ask Bob for his price.",
        requiresTap = false,
        allowedBotNames = setOf("Bob")
    ),
    BOB_QUOTE(
        message = "Bob bids 70 -- above your buy at 61. Sell to lock in profit!",
        requiresTap = false,
        canSell = true
    ),
    SOLD_RESULT(
        message = "Sold at 70, bought at 61. That's +9 locked in!",
        requiresTap = true
    ),
    TAP_NEXT2(
        message = "Tap Next to see the final results.",
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
enum class TutorialTrigger {
    USER_TAPPED,
    BOT_ASKED,
    QUOTE_ACTED_ON,
    NEXT_TAPPED,
    PHASE_CHANGED
}

// -- Tutorial Manager --

/** Manages the scripted tutorial state machine. */
class TutorialManager {
    var currentStep: TutorialStep? by mutableStateOf(TutorialStep.WELCOME)
        private set

    val isActive: Boolean get() = currentStep != null

    /** Advance the tutorial based on a game event or user tap. */
    fun advance(trigger: TutorialTrigger) {
        val step = currentStep ?: return

        when {
            // Tap-to-advance steps
            trigger == TutorialTrigger.USER_TAPPED && step.requiresTap && step != TutorialStep.DONE -> {
                moveToNext()
            }
            // Done — dismiss tutorial
            step == TutorialStep.DONE && trigger == TutorialTrigger.USER_TAPPED -> {
                currentStep = null
            }
            // Ask Alice
            step == TutorialStep.ASK_ALICE && trigger == TutorialTrigger.BOT_ASKED -> {
                moveToNext()
            }
            // Buy from Alice
            step == TutorialStep.ALICE_QUOTE && trigger == TutorialTrigger.QUOTE_ACTED_ON -> {
                moveToNext()
            }
            // Tap Next to reveal card
            step == TutorialStep.TAP_NEXT1 && trigger == TutorialTrigger.NEXT_TAPPED -> {
                moveToNext()
            }
            // Ask Bob
            step == TutorialStep.ASK_BOB && trigger == TutorialTrigger.BOT_ASKED -> {
                moveToNext()
            }
            // Sell to Bob
            step == TutorialStep.BOB_QUOTE && trigger == TutorialTrigger.QUOTE_ACTED_ON -> {
                moveToNext()
            }
            // Tap Next to settle
            step == TutorialStep.TAP_NEXT2 && trigger == TutorialTrigger.NEXT_TAPPED -> {
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
