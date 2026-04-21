/**
 * gameLoop.js — Async game loop with Promise-based user-input pauses.
 *
 * Same flow as the Python CLI / Swift iOS versions:
 *   OPEN → REVEAL_1 → REVEAL_2 → REVEAL_3 → SETTLE
 *
 * Each phase: bots ask human → free trading → wind-down → reveal
 */

import { Phase, PHASE_ORDER, GameState, Difficulty, Quote, Trade } from './state.js';
import { deal, expectedTotal, SeededRandom, DECK_MEAN } from './deck.js';
import { executeTradeDirect, TradingError } from './trading.js';
import { settle, leaderboard, tradeBreakdown } from './scoring.js';
import { SimpleBot } from './simpleBot.js';
import { StrategicBot } from './strategicBot.js';

// ── Constants (matching Python CLI) ─────────────────────────────────────────

export const HUMAN_ID = 'You';
export const BOT_NAMES = ['Alice', 'Bob', 'Carol', 'Dave'];
export const BOTS_ASK_RANGE = [0, 2];
export const WIND_DOWN_RANGE = [1, 3];
export const WIND_DOWN_ASK_RANGE = [0, 1];
const BOT_DELAY = 600;

// ── Bot configuration per difficulty ────────────────────────────────────────

export function makeBots(difficulty, rng) {
  if (difficulty === Difficulty.EASY) {
    return BOT_NAMES.map(n => new SimpleBot(n, new SeededRandom(rng.randint(0, 10000)), null));
  }
  if (difficulty === Difficulty.MEDIUM) {
    return [
      new SimpleBot('Alice', new SeededRandom(rng.randint(0, 10000)), 5),
      new SimpleBot('Bob', new SeededRandom(rng.randint(0, 10000)), 5),
      new StrategicBot('Carol', new SeededRandom(rng.randint(0, 10000))),
      new StrategicBot('Dave', new SeededRandom(rng.randint(0, 10000))),
    ];
  }
  // HARD
  return BOT_NAMES.map(n => new StrategicBot(n, new SeededRandom(rng.randint(0, 10000))));
}

export function botsById(bots) {
  const map = {};
  for (const b of bots) map[b.playerId] = b;
  return map;
}

// ── Game state init ─────────────────────────────────────────────────────────

export function initState(rng) {
  const playerIds = [HUMAN_ID, ...BOT_NAMES];
  const { privateCards, centralCards } = deal(playerIds, rng);
  return new GameState(playerIds, privateCards, centralCards);
}

// ── Trade recording (notify all bots) ───────────────────────────────────────

export function recordTradeForAllBots(bots, buyer, seller) {
  for (const bot of bots) {
    bot.recordTrade(buyer, seller, buyer);
    bot.recordTrade(buyer, seller, seller);
  }
}

// ── UI callback interface ───────────────────────────────────────────────────

/**
 * The game loop communicates with the UI through a callback object (ui).
 * This decouples game logic from DOM rendering.
 *
 * Required callbacks:
 *   ui.onPhaseStart(phase, state)
 *   ui.onReveal(card, index, state)
 *   ui.onPlayerStatus(state)
 *   ui.onBotAsksYou(botName) → Promise<Quote|null>  (null = quit)
 *   ui.onBotDecision(botName, decision, quote)
 *   ui.onAdaptationWarning(botName, direction)
 *   ui.onTrade(buyer, seller, price, isHumanInvolved)
 *   ui.onBotTrade(buyer, seller, price)
 *   ui.waitForPlayerAction(state, bots, botMap) → Promise<{action, target, quote}|'next'|'quit'>
 *   ui.onBotsTrading()
 *   ui.onWindDownStart(nRounds)
 *   ui.onWindDownRound(round, total)
 *   ui.onWindDownYourTurn() → Promise<'continue'|'quit'|{action, target, quote}>
 *   ui.onSettlement(state, scores, board, breakdown)
 *   ui.onGameOver()
 *   ui.delay(ms) → Promise
 */

// ── Async game engine ───────────────────────────────────────────────────────

export class GameEngine {
  constructor(ui) {
    this.ui = ui;
    this.state = null;
    this.bots = [];
    this.botMap = {};
    this.rng = null;
    this.cancelled = false;
    this.log = [];  // { type, data } entries for trade log
  }

  cancel() {
    this.cancelled = true;
  }

  _delay(ms) {
    if (this.cancelled) return Promise.resolve();
    return this.ui.delay ? this.ui.delay(ms) : new Promise(r => setTimeout(r, ms));
  }

  _log(type, data) {
    const entry = { type, ...data };
    this.log.push(entry);
    if (this.ui.onLogEntry) this.ui.onLogEntry(entry);
  }

  // ── Main game flow ──────────────────────────────────────────────────────

  async run(difficulty, seed) {
    this.cancelled = false;
    this.log = [];
    this.rng = new SeededRandom(seed || Date.now());
    this.bots = makeBots(difficulty, this.rng);
    this.botMap = botsById(this.bots);
    this.state = initState(this.rng);

    const phases = [Phase.OPEN, Phase.REVEAL_1, Phase.REVEAL_2, Phase.REVEAL_3];

    for (const phase of phases) {
      if (this.cancelled) return;

      this.state.phase = phase;

      // Reveal central card for REVEAL phases
      if ([Phase.REVEAL_1, Phase.REVEAL_2, Phase.REVEAL_3].includes(phase)) {
        const revealIdx = [Phase.REVEAL_1, Phase.REVEAL_2, Phase.REVEAL_3].indexOf(phase);
        if (revealIdx === this.state.revealedCentral.length) {
          const card = this.state.centralCards[revealIdx];
          this.state.revealedCentral.push(card);
          this._log('reveal', { card, index: revealIdx + 1 });
          await this.ui.onReveal(card, revealIdx + 1, this.state);
          for (const bot of this.bots) bot.onPhaseChange(this.state);
        }
      }

      this._log('phase', { phase });
      await this.ui.onPhaseStart(phase, this.state);

      const result = await this._runPhase();
      if (result === 'quit') {
        await this.ui.onGameOver();
        return;
      }
    }

    // ── Settlement ──────────────────────────────────────────────────────
    if (this.cancelled) return;
    this.state.phase = Phase.SETTLE;
    this._log('phase', { phase: Phase.SETTLE });

    const scores = settle(this.state);
    const board = leaderboard(scores);
    const breakdown = tradeBreakdown(this.state, HUMAN_ID);
    await this.ui.onSettlement(this.state, scores, board, breakdown);
  }

  // ── Phase execution ───────────────────────────────────────────────────

  async _runPhase() {
    // Step 1: Bots ask human
    const askResult = await this._runBotsAskHuman(BOTS_ASK_RANGE);
    if (askResult === 'quit') return 'quit';

    // Step 2: Human free trading
    const humanResult = await this._runHumanTurn();
    if (humanResult === 'quit') return 'quit';

    // Step 3: Wind-down
    return this._runWindDown();
  }

  // ── Bots ask human ────────────────────────────────────────────────────

  async _runBotsAskHuman(askRange) {
    if (this.cancelled) return 'quit';

    const nAskers = this.rng.randint(askRange[0], askRange[1]);
    if (nAskers === 0) return 'continue';

    const askers = this.rng.sample(this.bots, Math.min(nAskers, this.bots.length));

    for (const bot of askers) {
      if (this.cancelled) return 'quit';

      // Check adaptation warning
      const streak = bot.opponentSameDirectionStreak(HUMAN_ID);
      const direction = bot.opponentDirection(HUMAN_ID);
      if (streak >= 3 && direction !== 'neutral') {
        this._log('adaptation', { botName: bot.playerId, direction });
        await this.ui.onAdaptationWarning(bot.playerId, direction);
      }

      // Ask human for a quote
      this._log('botAsks', { botName: bot.playerId });
      const quote = await this.ui.onBotAsksYou(bot.playerId);
      if (quote === null) return 'quit';

      this._log('humanQuote', { quote: quote.toString() });

      // Bot evaluates
      const decision = bot.decideOnQuote(this.state, HUMAN_ID, quote);

      if (decision === 'buy') {
        const trade = executeTradeDirect(this.state, bot.playerId, HUMAN_ID, quote.ask);
        recordTradeForAllBots(this.bots, bot.playerId, HUMAN_ID);
        this._log('trade', { buyer: bot.playerId, seller: HUMAN_ID, price: quote.ask, human: true });
        await this.ui.onBotDecision(bot.playerId, 'buy', quote);
        await this.ui.onTrade(bot.playerId, HUMAN_ID, quote.ask, true);
      } else if (decision === 'sell') {
        const trade = executeTradeDirect(this.state, HUMAN_ID, bot.playerId, quote.bid);
        recordTradeForAllBots(this.bots, HUMAN_ID, bot.playerId);
        this._log('trade', { buyer: HUMAN_ID, seller: bot.playerId, price: quote.bid, human: true });
        await this.ui.onBotDecision(bot.playerId, 'sell', quote);
        await this.ui.onTrade(HUMAN_ID, bot.playerId, quote.bid, true);
      } else {
        this._log('botWalks', { botName: bot.playerId });
        await this.ui.onBotDecision(bot.playerId, null, quote);
      }

      await this._delay(BOT_DELAY);
    }

    return 'continue';
  }

  // ── Human free trading ────────────────────────────────────────────────

  async _runHumanTurn() {
    if (this.cancelled) return 'quit';

    await this.ui.onPlayerStatus(this.state);

    while (!this.cancelled) {
      const result = await this.ui.waitForPlayerAction(this.state, this.bots, this.botMap);

      if (result === 'quit') return 'quit';
      if (result === 'next') return 'next';

      // Handle ask/buy/sell
      if (result.action === 'ask') {
        const bot = this.botMap[result.target];
        const quote = bot.getQuote(this.state, HUMAN_ID);
        this._log('playerAsks', { target: result.target, quote: quote.toString() });
        await this.ui.onQuoteReceived(result.target, quote);
      } else if (result.action === 'buy') {
        try {
          const trade = executeTradeDirect(this.state, HUMAN_ID, result.target, result.quote.ask);
          recordTradeForAllBots(this.bots, HUMAN_ID, result.target);
          this._log('trade', { buyer: HUMAN_ID, seller: result.target, price: result.quote.ask, human: true });
          await this.ui.onTrade(HUMAN_ID, result.target, result.quote.ask, true);
        } catch (e) {
          if (this.ui.onError) await this.ui.onError(e.message);
        }
      } else if (result.action === 'sell') {
        try {
          const trade = executeTradeDirect(this.state, result.target, HUMAN_ID, result.quote.bid);
          recordTradeForAllBots(this.bots, result.target, HUMAN_ID);
          this._log('trade', { buyer: result.target, seller: HUMAN_ID, price: result.quote.bid, human: true });
          await this.ui.onTrade(result.target, HUMAN_ID, result.quote.bid, true);
        } catch (e) {
          if (this.ui.onError) await this.ui.onError(e.message);
        }
      }
    }
    return 'quit';
  }

  // ── Bot-to-bot trading ────────────────────────────────────────────────

  async _runBotToBot() {
    if (this.cancelled) return;

    await this.ui.onBotsTrading();

    for (const bot of this.bots) {
      if (this.cancelled) return;

      const targetId = bot.decideAction(this.state);
      if (!targetId || targetId === HUMAN_ID || targetId === bot.playerId || !this.botMap[targetId]) continue;

      const targetBot = this.botMap[targetId];
      const quote = targetBot.getQuote(this.state, bot.playerId);
      const decision = bot.decideOnQuote(this.state, targetId, quote);

      if (decision === 'buy') {
        try {
          executeTradeDirect(this.state, bot.playerId, targetId, quote.ask);
          recordTradeForAllBots(this.bots, bot.playerId, targetId);
          this._log('botTrade', { buyer: bot.playerId, seller: targetId, price: quote.ask });
          if (this.ui.onBotTrade) await this.ui.onBotTrade(bot.playerId, targetId, quote.ask);
        } catch (e) { /* silent */ }
      } else if (decision === 'sell') {
        try {
          executeTradeDirect(this.state, targetId, bot.playerId, quote.bid);
          recordTradeForAllBots(this.bots, targetId, bot.playerId);
          this._log('botTrade', { buyer: targetId, seller: bot.playerId, price: quote.bid });
          if (this.ui.onBotTrade) await this.ui.onBotTrade(targetId, bot.playerId, quote.bid);
        } catch (e) { /* silent */ }
      }
    }
  }

  // ── Wind-down ─────────────────────────────────────────────────────────

  async _runWindDown() {
    if (this.cancelled) return 'quit';

    const nRounds = this.rng.randint(WIND_DOWN_RANGE[0], WIND_DOWN_RANGE[1]);
    this._log('windDownStart', { nRounds });
    await this.ui.onWindDownStart(nRounds);

    for (let i = 0; i < nRounds; i++) {
      if (this.cancelled) return 'quit';

      this._log('windDownRound', { round: i + 1, total: nRounds });
      await this.ui.onWindDownRound(i + 1, nRounds);

      // Some bots may ask human (fewer during wind-down)
      const askResult = await this._runBotsAskHuman(WIND_DOWN_ASK_RANGE);
      if (askResult === 'quit') return 'quit';

      // If bot asked, give human a mini trading window
      if (askResult === 'continue') {
        // Check if any bot actually asked (we just ran the ask flow)
        // The mini-turn is handled by a simpler UI flow
      }

      // Bot-to-bot trading
      await this._runBotToBot();
      await this._delay(BOT_DELAY);
    }

    return 'next';
  }

  // ── Helpers ───────────────────────────────────────────────────────────

  getEv() {
    if (!this.state) return 0;
    const known = this.state.knownCardsFor(HUMAN_ID);
    const unknown = this.state.unknownCountFor(HUMAN_ID);
    return expectedTotal(known, unknown);
  }

  getTradeCount() {
    if (!this.state) return { buys: 0, sells: 0 };
    const buys = this.state.trades.filter(t => t.buyer === HUMAN_ID).length;
    const sells = this.state.trades.filter(t => t.seller === HUMAN_ID).length;
    return { buys, sells };
  }

  getBotType(botName) {
    const bot = this.botMap[botName];
    if (!bot) return '';
    return bot instanceof StrategicBot ? 'strategic' : 'simple';
  }
}
