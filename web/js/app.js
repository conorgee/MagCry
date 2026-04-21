/**
 * app.js — Main application controller.
 *
 * Manages screen transitions, UI callbacks for GameEngine, DOM event
 * binding, tutorial flow, instructions overlay, and trade log panel.
 */

import { GameEngine, HUMAN_ID } from './gameLoop.js';
import { Phase, PHASE_SHORT_LABELS, Quote } from './state.js';
import { expectedTotal } from './deck.js';
import { executeTradeDirect } from './trading.js';
import { TutorialManager } from './tutorial.js';
import { ScoreStore } from './scoreStore.js';

// ── Instruction pages (6 pages, matching Swift InstructionsView) ────────────

const INSTRUCTION_PAGES = [
  {
    icon: '\u2660',
    title: 'The Game',
    text: '5 players each get 1 secret card. 3 more cards go face-down in the center. You\'re all betting on the sum of these 8 cards.',
    visual: 'deckFan',
  },
  {
    icon: '\uD83D\uDC41',
    title: 'Your Card',
    text: 'Cards range from \u221210 to 20. Only you can see yours. A higher card means a higher expected sum.',
    visual: 'mockCard',
  },
  {
    icon: '\uD83D\uDCAC',
    title: 'Get a Price',
    text: 'Tap a trader to ask for a two-way quote. The spread is always exactly 2. You see their bid and ask price.',
    visual: 'mockQuote',
  },
  {
    icon: '\u21C5',
    title: 'Buy or Sell',
    text: 'Buy if you think the sum will be ABOVE the ask. Sell if you think it will be BELOW the bid. Not sure? Just pass.',
    visual: 'pnlExamples',
  },
  {
    icon: '\uD83C\uDCCF',
    title: 'Card Reveals',
    text: 'Between rounds, central cards flip one at a time. New info means better estimates. Adjust your strategy as cards are revealed.',
    visual: 'miniCentral',
  },
  {
    icon: '\uD83E\uDDE0',
    title: 'Watch the Traders',
    text: 'Traders track your trading pattern. Keep buying and they\'ll raise prices on you. On Hard mode, traders bluff to mislead you.',
    visual: null,
  },
];

// ── DOM shorthand ───────────────────────────────────────────────────────────

const $ = id => document.getElementById(id);

// ── Format helpers ──────────────────────────────────────────────────────────

const fmtCard = v => (v >= 0 ? `+${v}` : `${v}`);
const fmtScore = v => (v > 0 ? `+${v.toFixed(1)}` : v.toFixed(1));

// ═════════════════════════════════════════════════════════════════════════════
// App
// ═════════════════════════════════════════════════════════════════════════════

class App {
  constructor() {
    this.engine = null;
    this.tutorial = new TutorialManager();
    this.scoreStore = new ScoreStore();
    this.isTutorial = false;
    this._tutorialCancelled = false;
    this._difficulty = null; // current game difficulty

    // Promise resolvers
    this._resolveAction = null;   // waitForPlayerAction
    this._resolveQuote = null;    // onBotAsksYou (slider)
    this._resolveCoach = null;    // tutorial coach tap
    this._tutNextResolve = null;  // tutorial Next button

    // Active quote during free-trading
    this._activeQuote = null;
    this._activeTarget = null;

    // Tutorial game state (only set during tutorial)
    this._tState = null;

    // Instructions page index
    this._iPage = 0;
  }

  // ── Initialisation ──────────────────────────────────────────────────────

  init() {
    this._bindMenu();
    this._bindGame();
    this._bindOverlays();
    this._buildInstructions();
  }

  _bindMenu() {
    for (const btn of document.querySelectorAll('.btn-difficulty')) {
      btn.addEventListener('click', () => this.startGame(btn.dataset.difficulty));
    }
    $('btn-how-to-play').addEventListener('click', () => this.showInstructions());
    $('btn-tutorial').addEventListener('click', () => this.startTutorial());
    $('btn-stats').addEventListener('click', () => this.showStats());
  }

  _bindGame() {
    // Ask-bot buttons
    for (const btn of document.querySelectorAll('.btn-bot')) {
      btn.addEventListener('click', () => {
        if (this._resolveAction) {
          const r = this._resolveAction;
          this._resolveAction = null;
          r({ action: 'ask', target: btn.dataset.bot });
        }
      });
    }

    // Buy
    $('btn-buy').addEventListener('click', () => {
      if (this._resolveAction && this._activeQuote) {
        const r = this._resolveAction;
        this._resolveAction = null;
        r({ action: 'buy', target: this._activeTarget, quote: this._activeQuote });
      }
    });

    // Sell
    $('btn-sell').addEventListener('click', () => {
      if (this._resolveAction && this._activeQuote) {
        const r = this._resolveAction;
        this._resolveAction = null;
        r({ action: 'sell', target: this._activeTarget, quote: this._activeQuote });
      }
    });

    // Pass
    $('btn-pass').addEventListener('click', () => {
      if (this._resolveAction) {
        this._activeQuote = null;
        this._activeTarget = null;
        this._showView('bot-buttons');
        const r = this._resolveAction;
        this._resolveAction = null;
        r({ action: 'pass' });
      }
    });

    // Slider input
    $('price-slider').addEventListener('input', () => this._syncSlider());

    // Submit quote (bot asks you via slider)
    $('btn-submit-quote').addEventListener('click', () => {
      if (this._resolveQuote) {
        const mid = parseInt($('price-slider').value, 10);
        const q = new Quote(mid - 1, mid + 1);
        const r = this._resolveQuote;
        this._resolveQuote = null;
        r(q);
      }
    });

    // Next
    $('btn-next').addEventListener('click', () => {
      // Tutorial: use dedicated resolver
      if (this.isTutorial) {
        if (this._tutNextResolve) {
          const r = this._tutNextResolve;
          this._tutNextResolve = null;
          r();
        }
        return;
      }
      // Normal game
      if (this._resolveAction) {
        const r = this._resolveAction;
        this._resolveAction = null;
        r('next');
      }
    });

    // Quit
    $('btn-quit').addEventListener('click', () => this._quit());

    // History
    $('btn-history').addEventListener('click', () => this.showTradeLog());

    // Coach tap
    $('btn-coach-tap').addEventListener('click', () => {
      if (this._resolveCoach) {
        const r = this._resolveCoach;
        this._resolveCoach = null;
        r();
      }
    });
  }

  _bindOverlays() {
    $('btn-close-log').addEventListener('click', () => this.hideTradeLog());
    $('trade-log-overlay').addEventListener('click', e => {
      if (e.target === $('trade-log-overlay')) this.hideTradeLog();
    });
    $('btn-close-instructions').addEventListener('click', () => this.hideInstructions());
    $('instructions-overlay').addEventListener('click', e => {
      if (e.target === $('instructions-overlay')) this.hideInstructions();
    });
    $('btn-instructions-next').addEventListener('click', () => this._nextIPage());
    $('btn-play-again').addEventListener('click', () => this.showScreen('menu'));
    $('btn-share').addEventListener('click', () => this._shareResult());

    // Stats overlay
    $('btn-close-stats').addEventListener('click', () => this.hideStats());
    $('stats-overlay').addEventListener('click', e => {
      if (e.target === $('stats-overlay')) this.hideStats();
    });
    for (const tab of document.querySelectorAll('.stats-tab')) {
      tab.addEventListener('click', () => {
        document.querySelectorAll('.stats-tab').forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        this._renderStats(tab.dataset.diff);
      });
    }
    $('btn-reset-stats').addEventListener('click', () => {
      if (confirm('Reset all stats?\n\nThis will permanently erase all your stats across all difficulties.')) {
        this.scoreStore.resetAll();
        const activeDiff = document.querySelector('.stats-tab.active').dataset.diff;
        this._renderStats(activeDiff);
      }
    });
  }

  // ── Screen management ───────────────────────────────────────────────────

  showScreen(name) {
    for (const s of document.querySelectorAll('.screen')) s.classList.remove('active');
    $(`screen-${name}`).classList.add('active');
  }

  // ── Start a normal game ─────────────────────────────────────────────────

  startGame(difficulty) {
    this.isTutorial = false;
    this._difficulty = difficulty;
    this._tState = null;
    this._resetUI();
    this.engine = new GameEngine(this._uiCallbacks());
    this.showScreen('game');
    this.engine.run(difficulty);
  }

  // ── Quit (works for both normal game and tutorial) ──────────────────────

  _quit() {
    if (this.isTutorial) {
      this._endTutorial();
      return;
    }
    if (this._resolveAction) { const r = this._resolveAction; this._resolveAction = null; r('quit'); }
    if (this._resolveQuote)  { const r = this._resolveQuote;  this._resolveQuote = null;  r(null); }
    if (this.engine) this.engine.cancel();
    this.showScreen('menu');
  }

  // ── UI callbacks object (passed to GameEngine) ──────────────────────────

  _uiCallbacks() {
    return {
      onPhaseStart:        (p, s) => this._onPhaseStart(p, s),
      onReveal:            (c, i, s) => this._onReveal(c, i, s),
      onPlayerStatus:      s => this._onPlayerStatus(s),
      onBotAsksYou:        n => this._onBotAsksYou(n),
      onBotDecision:       (n, d, q) => this._onBotDecision(n, d, q),
      onAdaptationWarning: (n, d) => this._onAdaptationWarning(n, d),
      onTrade:             (b, s, p, h) => this._onTrade(b, s, p, h),
      onBotTrade:          (b, s, p) => this._onBotTrade(b, s, p),
      waitForPlayerAction: (s, b, m) => this._waitForAction(s, b, m),
      onQuoteReceived:     (t, q) => this._onQuoteReceived(t, q),
      onBotsTrading:       () => this._onBotsTrading(),
      onWindDownStart:     n => this._onWindDownStart(n),
      onWindDownRound:     (r, t) => this._onWindDownRound(r, t),
      onSettlement:        (s, sc, b, bd) => this._onSettlement(s, sc, b, bd),
      onGameOver:          () => this._onGameOver(),
      onLogEntry:          e => this._onLogEntry(e),
      onError:             m => this._onError(m),
      delay:               ms => new Promise(r => setTimeout(r, ms)),
    };
  }

  // ── Reset game screen to initial state ──────────────────────────────────

  _resetUI() {
    $('phase-label').textContent = 'Open Trading';
    $('trade-count').textContent = '';
    $('your-card').textContent = '';
    $('your-ev').textContent = '';
    $('last-event').textContent = '';
    $('last-event').style.color = '';
    $('btn-next').disabled = true;
    $('btn-next').classList.remove('wind-down');
    for (let i = 0; i < 3; i++) {
      const s = $(`central-${i}`);
      s.classList.remove('flipped');
      s.querySelector('.card-front span').textContent = '?';
      s.querySelector('.card-back span').textContent = '';
    }
    this._showView('bot-buttons');
    this._enableBots(true);
    $('coach-overlay').classList.add('hidden');
    $('trade-log-body').innerHTML = '<p class="log-empty">No trades yet.</p>';
    this._activeQuote = null;
    this._activeTarget = null;
  }

  // ── Interaction area view switching ─────────────────────────────────────

  _showView(name) {
    for (const id of ['bot-buttons', 'quote-view', 'slider-view', 'decision-result', 'bots-trading']) {
      $(id).classList.toggle('hidden', id !== name);
    }
  }

  // ── Button enable/disable helpers ───────────────────────────────────────

  _enableBots(on) {
    for (const b of document.querySelectorAll('.btn-bot')) b.disabled = !on;
  }

  _enableBot(name, on) {
    const b = document.querySelector(`.btn-bot[data-bot="${name}"]`);
    if (b) b.disabled = !on;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // UI callback implementations (called by GameEngine)
  // ═══════════════════════════════════════════════════════════════════════════

  async _onPhaseStart(phase, state) {
    $('phase-label').textContent = PHASE_SHORT_LABELS[phase] || phase;
    this._refreshCard(state);
    this._refreshEv(state);
    this._refreshCount(state);
    this._showView('bot-buttons');
    $('btn-next').disabled = true;
    $('btn-next').classList.remove('wind-down');
  }

  async _onReveal(card, idx, state) {
    const slot = $(`central-${idx - 1}`);
    slot.querySelector('.card-back span').textContent = fmtCard(card);
    slot.classList.add('flipped');
    this._refreshEv(state);
    $('last-event').textContent = `Central card ${idx} revealed: ${fmtCard(card)}`;
    $('last-event').style.color = '';
  }

  async _onPlayerStatus(state) {
    this._refreshCard(state);
    this._refreshEv(state);
    this._showView('bot-buttons');
    this._enableBots(true);
    $('btn-next').disabled = false;
  }

  _onBotAsksYou(botName) {
    return new Promise(resolve => {
      this._resolveQuote = resolve;
      $('slider-bot-name').textContent = `${botName} asks you`;
      const ev = this.engine.getEv();
      $('price-slider').value = Math.round(ev);
      this._syncSlider();
      this._showView('slider-view');
      $('btn-next').disabled = true;
    });
  }

  async _onBotDecision(botName, decision, quote) {
    let text;
    if (decision === 'buy') {
      text = `${botName} buys at ${quote.ask}`;
      $('decision-text').style.color = 'var(--buy)';
    } else if (decision === 'sell') {
      text = `${botName} sells at ${quote.bid}`;
      $('decision-text').style.color = 'var(--sell)';
    } else {
      text = `${botName} walks away`;
      $('decision-text').style.color = 'var(--text-secondary)';
    }
    $('decision-text').textContent = text;
    $('last-event').textContent = text;
    $('last-event').style.color = '';
    this._showView('decision-result');
  }

  async _onAdaptationWarning(botName, dir) {
    const verb = dir === 'buying' ? 'buying' : 'selling';
    $('last-event').textContent = `\u26A0 ${botName} notices you keep ${verb}...`;
    $('last-event').style.color = '#e67e22';
    await new Promise(r => setTimeout(r, 900));
    $('last-event').style.color = '';
  }

  async _onTrade(buyer, seller, price) {
    this._refreshCount(this.engine.state);
    this._refreshEv(this.engine.state);
    if (buyer === HUMAN_ID) {
      $('last-event').textContent = `You bought from ${seller} at ${price}`;
      $('last-event').style.color = 'var(--buy)';
    } else {
      $('last-event').textContent = `You sold to ${buyer} at ${price}`;
      $('last-event').style.color = 'var(--sell)';
    }
    // Clear active quote after trade
    this._activeQuote = null;
    this._activeTarget = null;
    await new Promise(r => setTimeout(r, 400));
    $('last-event').style.color = '';
  }

  async _onBotTrade(buyer, seller, price) {
    $('last-event').textContent = `${buyer} bought from ${seller} at ${price}`;
    $('last-event').style.color = 'var(--text-secondary)';
    await new Promise(r => setTimeout(r, 300));
    $('last-event').style.color = '';
  }

  _waitForAction() {
    return new Promise(resolve => {
      this._resolveAction = resolve;
      if (this._activeQuote) {
        this._showView('quote-view');
      } else {
        this._showView('bot-buttons');
        this._enableBots(true);
      }
      $('btn-next').disabled = false;
    });
  }

  async _onQuoteReceived(target, quote) {
    this._activeQuote = quote;
    this._activeTarget = target;
    $('quote-bot-name').textContent = target;
    $('quote-bid').textContent = quote.bid;
    $('quote-ask').textContent = quote.ask;
    $('buy-price').textContent = quote.ask;
    $('sell-price').textContent = quote.bid;
    this._showView('quote-view');
  }

  async _onBotsTrading() {
    this._showView('bots-trading');
    $('btn-next').disabled = true;
  }

  async _onWindDownStart(n) {
    $('btn-next').classList.add('wind-down');
    $('last-event').textContent = `Wind-down: ${n} round${n !== 1 ? 's' : ''} of bot trading`;
    $('last-event').style.color = '';
  }

  async _onWindDownRound(r, t) {
    $('last-event').textContent = `Wind-down ${r}/${t}`;
    $('last-event').style.color = '';
  }

  async _onSettlement(state, scores, board, breakdown) {
    // Record stats (non-tutorial only)
    if (!this.isTutorial && this._difficulty) {
      const humanPnl = scores[HUMAN_ID] || 0;
      const rank = board.findIndex(([pid]) => pid === HUMAN_ID) + 1;
      const tradeCount = breakdown.length;
      this.scoreStore.record(this._difficulty, humanPnl, rank, tradeCount);
    }
    this._renderSettlement(state, scores, board, breakdown);
    this.showScreen('settlement');
  }

  async _onGameOver() {
    this.showScreen('menu');
  }

  _onLogEntry(entry) { this._appendLog(entry); }

  _onError(msg) {
    $('last-event').textContent = msg;
    $('last-event').style.color = 'var(--sell)';
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Data → DOM helpers
  // ═══════════════════════════════════════════════════════════════════════════

  _refreshCard(state) {
    $('your-card').textContent = `Your card: ${fmtCard(state.privateCards[HUMAN_ID])}`;
  }

  _refreshEv(state) {
    const known = state.knownCardsFor(HUMAN_ID);
    const unk = state.unknownCountFor(HUMAN_ID);
    $('your-ev').textContent = `EV: ${expectedTotal(known, unk).toFixed(1)}`;
  }

  _refreshCount(state) {
    const b = state.trades.filter(t => t.buyer === HUMAN_ID).length;
    const s = state.trades.filter(t => t.seller === HUMAN_ID).length;
    $('trade-count').textContent = b + s > 0 ? `${b}B ${s}S` : '';
  }

  _syncSlider() {
    const mid = parseInt($('price-slider').value, 10);
    $('slider-bid').textContent = mid - 1;
    $('slider-ask').textContent = mid + 1;
    $('slider-min').textContent = $('price-slider').min;
    $('slider-max').textContent = $('price-slider').max;
    let ev = 0;
    if (this.engine) {
      ev = this.engine.getEv();
    } else if (this._tState) {
      ev = expectedTotal(
        this._tState.knownCardsFor(HUMAN_ID),
        this._tState.unknownCountFor(HUMAN_ID),
      );
    }
    $('slider-ev').textContent = `EV: ${ev.toFixed(0)}`;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Settlement screen rendering
  // ═══════════════════════════════════════════════════════════════════════════

  _renderSettlement(state, scores, board, breakdown) {
    const T = state.finalTotal();
    $('final-total').textContent = T;

    // Save for share
    this._lastSettlement = { board, breakdown, finalTotal: T };

    // Cards dealt (with staggered animation)
    const cd = $('cards-dealt');
    cd.innerHTML = '';
    let delayIdx = 0;
    for (const pid of state.playerIds) {
      const div = document.createElement('div');
      div.className = `card-row${pid === HUMAN_ID ? ' you' : ''}`;
      div.style.animationDelay = `${delayIdx * 0.15}s`;
      div.innerHTML = `<span class="card-label">${pid}</span>` +
        `<span class="card-value">${fmtCard(state.privateCards[pid])}</span>`;
      cd.appendChild(div);
      delayIdx++;
    }
    // Central cards as one row
    const centralDiv = document.createElement('div');
    centralDiv.className = 'card-row central';
    centralDiv.style.animationDelay = `${delayIdx * 0.15}s`;
    centralDiv.innerHTML = `<span class="card-label">Central</span>` +
      `<span class="card-value">${state.centralCards.map(fmtCard).join(', ')}</span>`;
    cd.appendChild(centralDiv);

    // Leaderboard
    const lb = $('leaderboard');
    lb.innerHTML = '';
    const isNewBest = this.scoreStore.isNewBest && !this.isTutorial;
    board.forEach(([pid, sc], i) => {
      const cls = sc > 0 ? 'positive' : sc < 0 ? 'negative' : 'zero';
      const div = document.createElement('div');
      div.className = `lb-row${pid === HUMAN_ID ? ' you' : ''}`;
      let html =
        `<span class="lb-rank">#${i + 1}</span>` +
        `<span class="lb-name">${pid}</span>`;
      if (pid === HUMAN_ID && isNewBest) {
        html += `<span class="lb-new-best">New Best!</span>`;
      }
      html += `<span class="lb-score ${cls}">${fmtScore(sc)}</span>` +
        (i === 0 ? '<span class="lb-winner">&#9733;</span>' : '');
      div.innerHTML = html;
      lb.appendChild(div);
    });

    // Personal best (non-tutorial only)
    const pbEl = $('personal-best');
    if (!this.isTutorial && this._difficulty) {
      const best = this.scoreStore.statsFor(this._difficulty).bestPnL;
      if (best !== null) {
        pbEl.textContent = `Personal best: ${best >= 0 ? '+' + best : best}`;
        pbEl.classList.remove('hidden');
      } else {
        pbEl.classList.add('hidden');
      }
    } else {
      pbEl.classList.add('hidden');
    }

    // Streak callout (non-tutorial, >= 3 wins)
    const streakEl = $('streak-callout');
    if (!this.isTutorial && this._difficulty) {
      const streak = this.scoreStore.statsFor(this._difficulty).currentStreak;
      if (streak >= 3) {
        streakEl.textContent = `${streak} wins in a row!`;
        streakEl.classList.remove('hidden');
      } else {
        streakEl.classList.add('hidden');
      }
    } else {
      streakEl.classList.add('hidden');
    }

    // Your trades
    const yt = $('your-trades');
    yt.innerHTML = '';
    if (breakdown.length === 0) {
      yt.innerHTML = '<p style="color:var(--text-secondary);text-align:center">No trades made.</p>';
    } else {
      for (const { trade, pnl } of breakdown) {
        const isBuy = trade.buyer === HUMAN_ID;
        const cp = isBuy ? trade.seller : trade.buyer;
        const pCls = pnl > 0 ? 'positive' : pnl < 0 ? 'negative' : '';
        const div = document.createElement('div');
        div.className = `trade-row ${isBuy ? 'buy-trade' : 'sell-trade'}`;
        div.innerHTML =
          `<span class="trade-desc ${isBuy ? 'buy' : 'sell'}">${isBuy ? 'Bought from' : 'Sold to'} ${cp} @ ${trade.price}</span>` +
          `<span class="trade-pnl ${pCls}">${fmtScore(pnl)}</span>`;
        yt.appendChild(div);
      }
    }

    // Share button visibility (hide during tutorial)
    $('btn-share').classList.toggle('hidden', this.isTutorial);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Trade log panel
  // ═══════════════════════════════════════════════════════════════════════════

  _appendLog(entry) {
    const body = $('trade-log-body');
    const empty = body.querySelector('.log-empty');
    if (empty) empty.remove();

    const div = document.createElement('div');
    div.className = 'log-entry';

    switch (entry.type) {
      case 'phase':
        div.className += ' phase-header';
        div.textContent = PHASE_SHORT_LABELS[entry.phase] || entry.phase;
        break;
      case 'reveal':
        div.className += ' info';
        div.textContent = `Central card ${entry.index}: ${fmtCard(entry.card)}`;
        break;
      case 'trade':
        if (entry.human) {
          const isBuy = entry.buyer === HUMAN_ID;
          div.className += ` human-trade ${isBuy ? 'buy' : 'sell'}`;
          div.textContent = isBuy
            ? `You bought from ${entry.seller} @ ${entry.price}`
            : `You sold to ${entry.buyer} @ ${entry.price}`;
        } else {
          return; // non-human trades logged under botTrade
        }
        break;
      case 'botTrade':
        div.className += ' bot-trade';
        div.textContent = `${entry.buyer} bought from ${entry.seller} @ ${entry.price}`;
        break;
      case 'botAsks':
        div.className += ' bot-asks';
        div.textContent = `${entry.botName} asks you for a price`;
        break;
      case 'adaptation':
        div.className += ' info';
        div.textContent = `${entry.botName} adapting to your ${entry.direction} pattern`;
        break;
      default:
        return;
    }

    body.appendChild(div);
    body.scrollTop = body.scrollHeight;
  }

  showTradeLog() { $('trade-log-overlay').classList.remove('hidden'); }
  hideTradeLog() { $('trade-log-overlay').classList.add('hidden'); }

  // ── Share ───────────────────────────────────────────────────────────────

  async _shareResult() {
    if (!this.engine && !this._lastSettlement) return;
    const { board, breakdown, finalTotal } = this._lastSettlement || {};
    if (!board) return;

    const humanScore = board.find(([pid]) => pid === HUMAN_ID);
    const rank = board.findIndex(([pid]) => pid === HUMAN_ID) + 1;
    const pnl = humanScore ? humanScore[1] : 0;
    const pnlStr = pnl >= 0 ? `+${Math.round(pnl)}` : `${Math.round(pnl)}`;
    const suffix = rank === 1 ? '1st' : rank === 2 ? '2nd' : rank === 3 ? '3rd' : `${rank}th`;
    const diffLabel = (this._difficulty || 'easy').charAt(0).toUpperCase() + (this._difficulty || 'easy').slice(1);

    const text = `MagCry [${diffLabel}]\nP&L: ${pnlStr} (${suffix} place)\nFinal total: ${finalTotal} | ${breakdown.length} trades`;

    if (navigator.share) {
      try {
        await navigator.share({ text });
        return;
      } catch (_) { /* user cancelled or not supported */ }
    }
    // Clipboard fallback
    try {
      await navigator.clipboard.writeText(text);
      const btn = $('btn-share');
      const orig = btn.textContent;
      btn.textContent = 'Copied!';
      setTimeout(() => { btn.innerHTML = `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 12v8a2 2 0 002 2h12a2 2 0 002-2v-8"/><polyline points="16 6 12 2 8 6"/><line x1="12" y1="2" x2="12" y2="15"/></svg> Share Result`; }, 1500);
    } catch (_) { /* ignore */ }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Stats screen
  // ═══════════════════════════════════════════════════════════════════════════

  showStats() {
    // Reset to Easy tab
    document.querySelectorAll('.stats-tab').forEach(t =>
      t.classList.toggle('active', t.dataset.diff === 'easy'));
    this._renderStats('easy');
    $('stats-overlay').classList.remove('hidden');
  }

  hideStats() {
    $('stats-overlay').classList.add('hidden');
  }

  _renderStats(difficulty) {
    const s = this.scoreStore.statsFor(difficulty);
    const container = $('stats-content');

    if (s.gamesPlayed === 0) {
      const label = difficulty.charAt(0).toUpperCase() + difficulty.slice(1);
      container.innerHTML =
        `<div class="stats-empty">` +
        `<h3>No games played yet</h3>` +
        `<p>Play a game on ${label} to see your stats here.</p>` +
        `</div>`;
      return;
    }

    const fmtPnl = v => (v >= 0 ? `+${v}` : `${v}`);
    const winPct = s.gamesPlayed > 0 ? Math.round(s.winRate * 100) + '%' : '--';
    const avgPnl = s.gamesPlayed > 0 ? fmtPnl(Math.round(s.averagePnL)) : '--';
    const bestPnl = s.bestPnL !== null ? fmtPnl(s.bestPnL) : '--';

    // Color helpers
    const winColor = s.winRate >= 0.5 ? 'var(--buy)' : '#e67e22';
    const bestColor = (s.bestPnL || 0) > 0 ? 'var(--buy)' : 'var(--sell)';
    const avgColor = s.averagePnL > 0 ? 'var(--buy)' : s.averagePnL < 0 ? 'var(--sell)' : 'var(--text-secondary)';
    const curStreakColor = s.currentStreak >= 3 ? 'var(--player)' : '#fff';
    const bestStreakColor = s.bestStreak >= 3 ? 'var(--player)' : '#fff';

    container.innerHTML =
      `<div class="stats-grid">` +
        this._statCard('Games Played', `${s.gamesPlayed}`, '#fff') +
        this._statCard('Win Rate', winPct, winColor, `${s.gamesWon} wins`) +
        this._statCard('Best P&L', bestPnl, bestColor) +
        this._statCard('Avg P&L', avgPnl, avgColor) +
        this._statCard('Current Streak', `${s.currentStreak}`, curStreakColor, s.currentStreak >= 3 ? 'On fire' : null) +
        this._statCard('Best Streak', `${s.bestStreak}`, bestStreakColor) +
        this._statCard('Total Trades', `${s.totalTrades}`, 'var(--accent)') +
        `<div></div>` + // empty slot for balance
      `</div>`;
  }

  _statCard(title, value, color, subtitle) {
    return `<div class="stat-card">` +
      `<span class="stat-card-title">${title}</span>` +
      `<span class="stat-card-value" style="color:${color}">${value}</span>` +
      (subtitle ? `<span class="stat-card-subtitle">${subtitle}</span>` : '') +
      `</div>`;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Instructions overlay (6-page)
  // ═══════════════════════════════════════════════════════════════════════════

  _buildInstructions() {
    const container = $('instructions-pages');
    const dots = $('page-dots');

    INSTRUCTION_PAGES.forEach((page, i) => {
      const div = document.createElement('div');
      div.className = `instruction-page${i === 0 ? ' active' : ''}`;
      div.dataset.page = i;
      div.innerHTML =
        `<div class="instruction-icon">${page.icon}</div>` +
        `<h3 class="instruction-title">${page.title}</h3>` +
        `<p class="instruction-text">${page.text.replace(/\n/g, '<br>')}</p>` +
        this._instructionVisual(page.visual);
      container.appendChild(div);

      const dot = document.createElement('div');
      dot.className = `page-dot${i === 0 ? ' active' : ''}`;
      dots.appendChild(dot);
    });

    this._updateIBtn();
  }

  /** Build visual HTML for instruction pages matching Swift. */
  _instructionVisual(type) {
    if (!type) return '';
    switch (type) {
      case 'deckFan': {
        // 8 mini cards fanned out: 5 blue (player) + 3 cyan (central)
        let cards = '';
        for (let i = 0; i < 8; i++) {
          const color = i < 5 ? 'blue' : 'cyan';
          const rot = (i - 4) * 5;
          cards += `<div class="fan-card fan-${color}" style="transform:rotate(${rot}deg)"></div>`;
        }
        return `<div class="visual-fan">${cards}</div>`;
      }
      case 'mockCard':
        // Yellow-bordered card showing "+12"
        return `<div class="visual-mock-card"><span>+12</span></div>`;
      case 'mockQuote':
        // Mock quote card with bid-ask and buttons
        return `<div class="visual-mock-quote">` +
          `<div class="mqh"><span class="mq-name">Alice</span>` +
          `<span class="mq-prices"><span class="mq-bid">58</span> -- <span class="mq-ask">60</span></span></div>` +
          `<div class="mq-btns">` +
          `<span class="mq-btn mq-btn-buy">Buy 60</span>` +
          `<span class="mq-btn mq-btn-sell">Sell 58</span>` +
          `<span class="mq-btn mq-btn-pass">Pass</span>` +
          `</div></div>`;
      case 'pnlExamples':
        return `<div class="visual-pnl">` +
          `<div class="pnl-row pnl-green">` +
          `<span>Buy at 60</span><span class="pnl-arrow">\u2192</span><span>Sum = 65</span>` +
          `<span class="pnl-val pnl-pos">+5</span></div>` +
          `<div class="pnl-row pnl-red">` +
          `<span>Sell at 58</span><span class="pnl-arrow">\u2192</span><span>Sum = 65</span>` +
          `<span class="pnl-val pnl-neg">\u22127</span></div>` +
          `</div>`;
      case 'miniCentral':
        // 1 face-up + 2 face-down mini cards
        return `<div class="visual-mini-central">` +
          `<div class="mini-card mini-up"><span>9</span></div>` +
          `<div class="mini-card mini-down"><span>?</span></div>` +
          `<div class="mini-card mini-down"><span>?</span></div>` +
          `</div>`;
      default:
        return '';
    }
  }

  _nextIPage() {
    if (this._iPage >= INSTRUCTION_PAGES.length - 1) {
      this.hideInstructions();
      return;
    }
    const pages = document.querySelectorAll('.instruction-page');
    const dots = document.querySelectorAll('.page-dot');

    pages[this._iPage].classList.remove('active');
    pages[this._iPage].classList.add('prev');
    this._iPage++;
    pages[this._iPage].classList.remove('prev');
    pages[this._iPage].classList.add('active');
    dots.forEach((d, i) => d.classList.toggle('active', i === this._iPage));
    this._updateIBtn();
  }

  _updateIBtn() {
    const btn = $('btn-instructions-next');
    const last = this._iPage >= INSTRUCTION_PAGES.length - 1;
    btn.textContent = last ? 'Got it' : 'Next';
    btn.classList.toggle('final', last);
  }

  showInstructions() {
    this._iPage = 0;
    const pages = document.querySelectorAll('.instruction-page');
    const dots = document.querySelectorAll('.page-dot');
    pages.forEach((p, i) => {
      p.classList.toggle('active', i === 0);
      p.classList.remove('prev');
    });
    dots.forEach((d, i) => d.classList.toggle('active', i === 0));
    this._updateIBtn();
    $('instructions-overlay').classList.remove('hidden');
  }

  hideInstructions() {
    $('instructions-overlay').classList.add('hidden');
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Tutorial (20-step scripted flow)
  // ═══════════════════════════════════════════════════════════════════════════

  async startTutorial() {
    this.isTutorial = true;
    this._tutorialCancelled = false;
    this.tutorial.start();

    this._tState = this.tutorial.createTutorialState();
    this.engine = null; // no GameEngine for tutorial

    this._resetUI();
    this.showScreen('game');
    this._refreshCard(this._tState);
    this._refreshEv(this._tState);
    $('phase-label').textContent = 'Tutorial';
    this._enableBots(false);
    $('btn-next').disabled = true;

    await this._runTutorial();
  }

  /** Linear scripted tutorial — each step is explicit for clarity. */
  async _runTutorial() {
    const tm = this.tutorial;
    const st = this._tState;
    const _c = () => this._tutorialCancelled; // cancellation check

    // Step 0: WELCOME
    await this._coachTap(tm.currentStep.message); if (_c()) return;
    tm.advance('tap');

    // Step 1: SEE_CARD
    await this._coachTap(tm.currentStep.message); if (_c()) return;
    tm.advance('tap');

    // Step 2: EXPLAIN_EV
    await this._coachTap(tm.currentStep.message); if (_c()) return;
    tm.advance('tap');

    // Step 3: ASK_ALICE — enable only Alice button
    this._coachMsg(tm.currentStep.message);
    this._showView('bot-buttons');
    this._enableBots(false);
    this._enableBot('Alice', true);
    await this._waitClick(document.querySelector('.btn-bot[data-bot="Alice"]'));
    if (_c()) return;
    tm.advance('askedBot');

    // Step 4: SEE_ALICE_QUOTE — show Alice's fixed quote
    const aq = tm.getAliceQuote();
    this._showQuote('Alice', aq);
    $('btn-buy').disabled = true;
    $('btn-sell').disabled = true;
    $('btn-pass').disabled = true;
    await this._coachTap(tm.currentStep.message); if (_c()) return;
    tm.advance('tap');

    // Step 5: BUY_FROM_ALICE — enable only Buy
    this._coachMsg(tm.currentStep.message);
    $('btn-buy').disabled = false;
    await this._waitClick($('btn-buy'));
    if (_c()) return;
    executeTradeDirect(st, HUMAN_ID, 'Alice', aq.ask);
    this._refreshCount(st);
    this._refreshEv(st);
    tm.advance('bought');

    // Step 6: BOUGHT_RESULT
    $('last-event').textContent = `You bought from Alice at ${aq.ask}`;
    $('last-event').style.color = 'var(--buy)';
    this._showView('bot-buttons');
    this._enableBots(false);
    await this._coachTap(tm.currentStep.message); if (_c()) return;
    $('last-event').style.color = '';
    tm.advance('tap');

    // Step 7: TAP_NEXT_1 — enable Next
    this._coachMsg(tm.currentStep.message);
    $('btn-next').disabled = false;
    await this._waitNext(); if (_c()) return;
    $('btn-next').disabled = true;
    tm.advance('nextTapped');

    // Step 8: REVEAL_1 — reveal central card 0
    st.revealedCentral.push(st.centralCards[0]);
    this._revealSlot(0, st.centralCards[0]);
    this._refreshEv(st);
    $('phase-label').textContent = 'Tutorial \u2014 Reveal 1';
    await this._coachTap(tm.currentStep.message); if (_c()) return;
    tm.advance('tap');

    // Step 9: ASK_BOB
    this._coachMsg(tm.currentStep.message);
    this._showView('bot-buttons');
    this._enableBots(false);
    this._enableBot('Bob', true);
    await this._waitClick(document.querySelector('.btn-bot[data-bot="Bob"]'));
    if (_c()) return;
    tm.advance('askedBot');

    // Step 10: SEE_BOB_QUOTE
    const bq = tm.getBobQuote();
    this._showQuote('Bob', bq);
    $('btn-buy').disabled = true;
    $('btn-sell').disabled = true;
    $('btn-pass').disabled = true;
    await this._coachTap(tm.currentStep.message); if (_c()) return;
    tm.advance('tap');

    // Step 11: SELL_TO_BOB — enable only Sell
    this._coachMsg(tm.currentStep.message);
    $('btn-sell').disabled = false;
    await this._waitClick($('btn-sell'));
    if (_c()) return;
    executeTradeDirect(st, 'Bob', HUMAN_ID, bq.bid);
    this._refreshCount(st);
    this._refreshEv(st);
    tm.advance('sold');

    // Step 12: SOLD_RESULT
    $('last-event').textContent = `You sold to Bob at ${bq.bid}`;
    $('last-event').style.color = 'var(--sell)';
    this._showView('bot-buttons');
    this._enableBots(false);
    await this._coachTap(tm.currentStep.message); if (_c()) return;
    $('last-event').style.color = '';
    tm.advance('tap');

    // Step 13: HISTORY_TIP
    await this._coachTap(tm.currentStep.message); if (_c()) return;
    tm.advance('tap');

    // Step 14: TAP_NEXT_2
    this._coachMsg(tm.currentStep.message);
    $('btn-next').disabled = false;
    await this._waitNext(); if (_c()) return;
    $('btn-next').disabled = true;
    tm.advance('nextTapped');

    // Step 15: REVEAL_2
    st.revealedCentral.push(st.centralCards[1]);
    this._revealSlot(1, st.centralCards[1]);
    this._refreshEv(st);
    $('phase-label').textContent = 'Tutorial \u2014 Reveal 2';
    await this._coachTap(tm.currentStep.message); if (_c()) return;
    tm.advance('tap');

    // Step 16: CAROL_ASKS — show slider, Carol will walk
    this._coachMsg(tm.currentStep.message);
    this._showView('slider-view');
    $('slider-bot-name').textContent = 'Carol asks you';
    const ev = expectedTotal(st.knownCardsFor(HUMAN_ID), st.unknownCountFor(HUMAN_ID));
    $('price-slider').value = Math.round(ev);
    this._syncSlider();
    await this._waitClick($('btn-submit-quote'));
    if (_c()) return;
    tm.advance('quotedBot');

    // Step 17: CAROL_WALKS
    this._showView('bot-buttons');
    this._enableBots(false);
    $('last-event').textContent = 'Carol walks away';
    $('last-event').style.color = 'var(--text-secondary)';
    await this._coachTap(tm.currentStep.message); if (_c()) return;
    $('last-event').style.color = '';
    tm.advance('tap');

    // Step 18: TAP_NEXT_3
    this._coachMsg(tm.currentStep.message);
    $('btn-next').disabled = false;
    await this._waitNext(); if (_c()) return;
    $('btn-next').disabled = true;
    tm.advance('nextTapped');

    // Step 19: REVEAL_3_SETTLE — reveal last card
    st.revealedCentral.push(st.centralCards[2]);
    this._revealSlot(2, st.centralCards[2]);
    this._refreshEv(st);
    $('phase-label').textContent = 'Tutorial \u2014 Settlement';
    await this._coachTap(tm.currentStep.message); if (_c()) return;
    tm.advance('tap');

    // Show settlement
    this._hideCoach();
    const T = st.finalTotal();
    const scores = {};
    for (const pid of st.playerIds) scores[pid] = 0;
    for (const trade of st.trades) {
      scores[trade.buyer] += T - trade.price;
      scores[trade.seller] += trade.price - T;
    }
    const board = Object.entries(scores).sort((a, b) => b[1] - a[1]);
    const breakdown = [];
    for (const trade of st.trades) {
      if (trade.buyer === HUMAN_ID || trade.seller === HUMAN_ID) {
        const pnl = trade.buyer === HUMAN_ID ? T - trade.price : trade.price - T;
        breakdown.push({ trade, pnl });
      }
    }
    this._renderSettlement(st, scores, board, breakdown);
    this.showScreen('settlement');
    this.isTutorial = false;
    this._tState = null;
  }

  // ── Tutorial helpers ────────────────────────────────────────────────────

  /** Show coach bubble with message + tap button, wait for tap. */
  _coachTap(msg) {
    $('coach-message').textContent = msg;
    $('coach-overlay').classList.remove('hidden');
    $('btn-coach-tap').classList.remove('hidden');
    return new Promise(resolve => { this._resolveCoach = resolve; })
      .then(() => { $('coach-overlay').classList.add('hidden'); });
  }

  /** Show coach bubble with message (no tap button). */
  _coachMsg(msg) {
    $('coach-message').textContent = msg;
    $('coach-overlay').classList.remove('hidden');
    $('btn-coach-tap').classList.add('hidden');
  }

  _hideCoach() {
    $('coach-overlay').classList.add('hidden');
  }

  /** Show the quote-view with given bot name and quote. */
  _showQuote(name, quote) {
    $('quote-bot-name').textContent = name;
    $('quote-bid').textContent = quote.bid;
    $('quote-ask').textContent = quote.ask;
    $('buy-price').textContent = quote.ask;
    $('sell-price').textContent = quote.bid;
    this._showView('quote-view');
  }

  /** Reveal a central card slot with 3D flip. */
  _revealSlot(idx, card) {
    const s = $(`central-${idx}`);
    s.querySelector('.card-back span').textContent = fmtCard(card);
    s.classList.add('flipped');
  }

  /** Wait for a single click on an element (one-shot listener). */
  _waitClick(el) {
    return new Promise(resolve => {
      const handler = () => {
        el.removeEventListener('click', handler);
        resolve();
      };
      el.addEventListener('click', handler);
    });
  }

  /** Wait for Next button during tutorial. */
  _waitNext() {
    return new Promise(resolve => {
      this._tutNextResolve = resolve;
    });
  }

  /** Cancel tutorial and return to menu. */
  _endTutorial() {
    this.isTutorial = false;
    this._tutorialCancelled = true;
    this._tState = null;
    this.tutorial.stop();
    // Unblock any pending waits
    if (this._resolveCoach) { const r = this._resolveCoach; this._resolveCoach = null; r(); }
    if (this._tutNextResolve) { const r = this._tutNextResolve; this._tutNextResolve = null; r(); }
    this.showScreen('menu');
  }
}

// ═════════════════════════════════════════════════════════════════════════════
// Bootstrap
// ═════════════════════════════════════════════════════════════════════════════

const app = new App();
app.init();

// Service worker registration (for PWA)
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('sw.js').catch(() => {});
}
