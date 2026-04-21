/**
 * tutorial.js — 20-step scripted tutorial with fixed cards and coach overlay.
 */

import { Phase, Quote, GameState, Difficulty } from './state.js';
import { SeededRandom } from './deck.js';
import { SimpleBot } from './simpleBot.js';

// Fixed tutorial deal: total = 68
const TUTORIAL_PRIVATE = { You: 12, Alice: 8, Bob: 10, Carol: 5, Dave: 6 };
const TUTORIAL_CENTRAL = [10, 11, 9];
const TUTORIAL_TOTAL = 71; // 12+8+10+5+6 + 10+11+9 = 71 — wait let me recalculate
// 12+8+10+5+6 = 41, 10+11+9 = 30, total = 71. Let me match the Swift tutorial which uses total=68.
// Actually, the Swift tutorial uses specific cards. Let me use cards that sum to 68:
// Private: You=12, Alice=5, Bob=3, Carol=7, Dave=4 = 31
// Central: 10, 11, 9 = 30, but 31+30=61. Need 68.
// Let me just use: You=12, Alice=8, Bob=10, Carol=6, Dave=5 = 41, Central=10, 11, 6 = 27, total=68
// Actually let me just pick: You=12, Alice=9, Bob=8, Carol=7, Dave=6 = 42, Central=10, 11, 5 = 26, total=68

const TUTO_PRIVATE = { You: 12, Alice: 9, Bob: 8, Carol: 7, Dave: 6 };
const TUTO_CENTRAL = [10, 11, 5];
// Sum: 12+9+8+7+6 + 10+11+5 = 68

export const TutorialStep = {
  WELCOME: 'welcome',
  SEE_CARD: 'seeCard',
  EXPLAIN_EV: 'explainEv',
  ASK_ALICE: 'askAlice',
  SEE_ALICE_QUOTE: 'seeAliceQuote',
  BUY_FROM_ALICE: 'buyFromAlice',
  BOUGHT_RESULT: 'boughtResult',
  TAP_NEXT_1: 'tapNext1',
  REVEAL_1: 'reveal1',
  ASK_BOB: 'askBob',
  SEE_BOB_QUOTE: 'seeBobQuote',
  SELL_TO_BOB: 'sellToBob',
  SOLD_RESULT: 'soldResult',
  HISTORY_TIP: 'historyTip',
  TAP_NEXT_2: 'tapNext2',
  REVEAL_2: 'reveal2',
  CAROL_ASKS: 'carolAsks',
  CAROL_WALKS: 'carolWalks',
  TAP_NEXT_3: 'tapNext3',
  REVEAL_3_SETTLE: 'reveal3Settle',
};

const STEPS = [
  {
    id: TutorialStep.WELCOME,
    message: "Welcome to MagCry! You'll trade contracts on the sum of 8 cards. Let's learn how it works.",
    requiresTap: true,
    allowedBots: [],
    canBuy: false, canSell: false, canPass: false, canNext: false,
  },
  {
    id: TutorialStep.SEE_CARD,
    message: "Your card is +12. Only you can see it. The deck average is ~7.6, so 12 is a good card!",
    requiresTap: true,
    allowedBots: [],
    canBuy: false, canSell: false, canPass: false, canNext: false,
  },
  {
    id: TutorialStep.EXPLAIN_EV,
    message: "Your EV (expected value) is 63.6. That's your best guess at the final sum, based on what you know.",
    requiresTap: true,
    allowedBots: [],
    canBuy: false, canSell: false, canPass: false, canNext: false,
  },
  {
    id: TutorialStep.ASK_ALICE,
    message: "Try asking Alice for a price. Tap 'Ask Alice'.",
    requiresTap: false,
    allowedBots: ['Alice'],
    canBuy: false, canSell: false, canPass: false, canNext: false,
    trigger: 'askedBot',
  },
  {
    id: TutorialStep.SEE_ALICE_QUOTE,
    message: "Alice quotes a bid and ask. The ask (buy price) is below your EV — that's a good deal!",
    requiresTap: true,
    allowedBots: [],
    canBuy: false, canSell: false, canPass: false, canNext: false,
  },
  {
    id: TutorialStep.BUY_FROM_ALICE,
    message: "Buy from Alice at her ask price. If the final total is above this price, you profit!",
    requiresTap: false,
    allowedBots: [],
    canBuy: true, canSell: false, canPass: false, canNext: false,
    trigger: 'bought',
  },
  {
    id: TutorialStep.BOUGHT_RESULT,
    message: "Nice trade! You bought a contract. If T > price, you make (T - price). If T < price, you lose.",
    requiresTap: true,
    allowedBots: [],
    canBuy: false, canSell: false, canPass: false, canNext: false,
  },
  {
    id: TutorialStep.TAP_NEXT_1,
    message: "Now tap 'Next' to reveal the first central card and move to the next phase.",
    requiresTap: false,
    allowedBots: [],
    canBuy: false, canSell: false, canPass: false, canNext: true,
    trigger: 'nextTapped',
  },
  {
    id: TutorialStep.REVEAL_1,
    message: "Central card revealed: +10! Your EV updated. Now you have more information to trade with.",
    requiresTap: true,
    allowedBots: [],
    canBuy: false, canSell: false, canPass: false, canNext: false,
  },
  {
    id: TutorialStep.ASK_BOB,
    message: "Ask Bob for a price. Tap 'Ask Bob'.",
    requiresTap: false,
    allowedBots: ['Bob'],
    canBuy: false, canSell: false, canPass: false, canNext: false,
    trigger: 'askedBot',
  },
  {
    id: TutorialStep.SEE_BOB_QUOTE,
    message: "Bob's bid is above your EV. That means selling is profitable — you'd lock in (bid - T) if T < bid.",
    requiresTap: true,
    allowedBots: [],
    canBuy: false, canSell: false, canPass: false, canNext: false,
  },
  {
    id: TutorialStep.SELL_TO_BOB,
    message: "Sell to Bob at his bid price. Tap 'Sell'.",
    requiresTap: false,
    allowedBots: [],
    canBuy: false, canSell: true, canPass: false, canNext: false,
    trigger: 'sold',
  },
  {
    id: TutorialStep.SOLD_RESULT,
    message: "You sold! With a buy at ~61 and sell at ~70, you've locked in ~9 points of profit regardless of T.",
    requiresTap: true,
    allowedBots: [],
    canBuy: false, canSell: false, canPass: false, canNext: false,
  },
  {
    id: TutorialStep.HISTORY_TIP,
    message: "Tip: Tap the history icon to see all trades. The bots are watching YOUR trading patterns too...",
    requiresTap: true,
    allowedBots: [],
    canBuy: false, canSell: false, canPass: false, canNext: false,
  },
  {
    id: TutorialStep.TAP_NEXT_2,
    message: "Tap 'Next' to reveal another card.",
    requiresTap: false,
    allowedBots: [],
    canBuy: false, canSell: false, canPass: false, canNext: true,
    trigger: 'nextTapped',
  },
  {
    id: TutorialStep.REVEAL_2,
    message: "Central card +11 revealed! Almost all information is known now.",
    requiresTap: true,
    allowedBots: [],
    canBuy: false, canSell: false, canPass: false, canNext: false,
  },
  {
    id: TutorialStep.CAROL_ASKS,
    message: "Sometimes bots ask YOU for a price. Use the slider to set your bid/ask, then submit.",
    requiresTap: false,
    allowedBots: [],
    canBuy: false, canSell: false, canPass: false, canNext: false,
    trigger: 'quotedBot',
    botAsks: 'Carol',
  },
  {
    id: TutorialStep.CAROL_WALKS,
    message: "Carol walked away — she didn't like your price. That happens! Not every quote leads to a trade.",
    requiresTap: true,
    allowedBots: [],
    canBuy: false, canSell: false, canPass: false, canNext: false,
  },
  {
    id: TutorialStep.TAP_NEXT_3,
    message: "Tap 'Next' to reveal the final card and see how you did!",
    requiresTap: false,
    allowedBots: [],
    canBuy: false, canSell: false, canPass: false, canNext: true,
    trigger: 'nextTapped',
  },
  {
    id: TutorialStep.REVEAL_3_SETTLE,
    message: "All cards revealed! The final total is 68. Let's see the settlement...",
    requiresTap: true,
    allowedBots: [],
    canBuy: false, canSell: false, canPass: false, canNext: false,
  },
];

export class TutorialManager {
  constructor() {
    this.active = false;
    this.stepIndex = 0;
    this._onAdvance = null;
  }

  get currentStep() {
    if (!this.active || this.stepIndex >= STEPS.length) return null;
    return STEPS[this.stepIndex];
  }

  get isComplete() {
    return this.stepIndex >= STEPS.length;
  }

  start() {
    this.active = true;
    this.stepIndex = 0;
  }

  stop() {
    this.active = false;
  }

  /** Create the fixed tutorial game state */
  createTutorialState() {
    const playerIds = ['You', 'Alice', 'Bob', 'Carol', 'Dave'];
    return new GameState(playerIds, { ...TUTO_PRIVATE }, [...TUTO_CENTRAL]);
  }

  /** Create tutorial bots (all simple, easy mode) */
  createTutorialBots() {
    const names = ['Alice', 'Bob', 'Carol', 'Dave'];
    return names.map(n => new SimpleBot(n, new SeededRandom(42), null));
  }

  /**
   * Advance to next step. Call with the trigger that caused the advance.
   * Returns the new step, or null if tutorial is complete.
   */
  advance(trigger = 'tap') {
    const step = this.currentStep;
    if (!step) return null;

    if (step.requiresTap && trigger === 'tap') {
      this.stepIndex++;
      return this.currentStep;
    }

    if (!step.requiresTap && step.trigger === trigger) {
      this.stepIndex++;
      return this.currentStep;
    }

    return step; // not the right trigger, stay on current step
  }

  /** Force advance (for programmatic use) */
  forceAdvance() {
    this.stepIndex++;
    return this.currentStep;
  }

  /** Get the fixed alice quote for tutorial */
  getAliceQuote() {
    return new Quote(59, 61); // EV ~63.6, ask=61 is below EV → good buy
  }

  /** Get the fixed bob quote for tutorial */
  getBobQuote() {
    return new Quote(69, 71); // bid=69 is above EV → good sell
  }
}
