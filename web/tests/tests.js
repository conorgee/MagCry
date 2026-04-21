/**
 * tests.js — Browser test suite for the MagCry web app.
 *
 * Ports all game-logic tests from the Python CLI test suite.
 * Zero dependencies — custom assertion helpers, DOM-based output.
 */

import { DECK, DECK_SIZE, DECK_SUM, DECK_MEAN, N_PLAYERS, N_CENTRAL, N_IN_PLAY,
         deal, deckMeanExcluding, expectedTotal, SeededRandom } from '../js/deck.js';
import { Phase, Difficulty, PHASE_ORDER, Quote, Trade, GameState } from '../js/state.js';
import { TradingError, validateQuote, setMarket, executeTrade, getQuote,
         tradeSummary, executeTradeDirect } from '../js/trading.js';
import { settle, leaderboard, tradeBreakdown } from '../js/scoring.js';
import { Bot } from '../js/bot.js';
import { SimpleBot, EDGE_THRESHOLD } from '../js/simpleBot.js';
import { StrategicBot, BLUFF_OFFSET_EARLY, BLUFF_OFFSET_LATE,
         MAX_TRADES_PER_TURN, ADAPTATION_THRESHOLD } from '../js/strategicBot.js';

// ── Test framework ──────────────────────────────────────────────────────────

let _passed = 0, _failed = 0, _errors = [];
const _suites = [];
let _currentSuite = null;

function suite(name) {
  _currentSuite = { name, tests: [] };
  _suites.push(_currentSuite);
}

function test(name, fn) {
  try {
    fn();
    _currentSuite.tests.push({ name, passed: true });
    _passed++;
  } catch (e) {
    _currentSuite.tests.push({ name, passed: false, error: e.message || String(e) });
    _failed++;
  }
}

// ── Assertions ──────────────────────────────────────────────────────────────

function assertEqual(a, b, msg = '') {
  if (a !== b) throw new Error(`${msg ? msg + ': ' : ''}Expected ${JSON.stringify(b)}, got ${JSON.stringify(a)}`);
}

function assertNotEqual(a, b, msg = '') {
  if (a === b) throw new Error(`${msg ? msg + ': ' : ''}Expected values to differ, both are ${JSON.stringify(a)}`);
}

function assertTrue(v, msg = '') {
  if (!v) throw new Error(`${msg ? msg + ': ' : ''}Expected truthy, got ${JSON.stringify(v)}`);
}

function assertFalse(v, msg = '') {
  if (v) throw new Error(`${msg ? msg + ': ' : ''}Expected falsy, got ${JSON.stringify(v)}`);
}

function assertNull(v, msg = '') {
  if (v !== null && v !== undefined) throw new Error(`${msg ? msg + ': ' : ''}Expected null/undefined, got ${JSON.stringify(v)}`);
}

function assertNotNull(v, msg = '') {
  if (v === null || v === undefined) throw new Error(`${msg ? msg + ': ' : ''}Expected non-null, got ${v}`);
}

function assertIncludes(arr, val, msg = '') {
  if (!arr.includes(val)) throw new Error(`${msg ? msg + ': ' : ''}Expected array to include ${JSON.stringify(val)}`);
}

function assertAlmostEqual(a, b, delta = 0.01, msg = '') {
  if (Math.abs(a - b) > delta) throw new Error(`${msg ? msg + ': ' : ''}Expected ~${b}, got ${a} (delta ${delta})`);
}

function assertGreater(a, b, msg = '') {
  if (a <= b) throw new Error(`${msg ? msg + ': ' : ''}Expected ${a} > ${b}`);
}

function assertLess(a, b, msg = '') {
  if (a >= b) throw new Error(`${msg ? msg + ': ' : ''}Expected ${a} < ${b}`);
}

function assertGreaterEqual(a, b, msg = '') {
  if (a < b) throw new Error(`${msg ? msg + ': ' : ''}Expected ${a} >= ${b}`);
}

function assertLessEqual(a, b, msg = '') {
  if (a > b) throw new Error(`${msg ? msg + ': ' : ''}Expected ${a} <= ${b}`);
}

function assertThrows(fn, msg = '') {
  try { fn(); } catch (e) { return e; }
  throw new Error(`${msg ? msg + ': ' : ''}Expected function to throw`);
}

function assertInstanceOf(obj, cls, msg = '') {
  if (!(obj instanceof cls)) throw new Error(`${msg ? msg + ': ' : ''}Expected instance of ${cls.name}`);
}

// ── Test helpers ────────────────────────────────────────────────────────────

function makeState(opts = {}) {
  const playerIds = ['You', 'Alice', 'Bob', 'Carol', 'Dave'];
  const privateCards = opts.privateCards || { You: 7, Alice: 10, Bob: 5, Carol: 6, Dave: 15 };
  const centralCards = opts.centralCards || [14, 13, 9];
  const state = new GameState(playerIds, privateCards, centralCards);
  if (opts.trades) state.trades = opts.trades;
  if (opts.phase) state.phase = opts.phase;
  if (opts.revealedCentral) state.revealedCentral = opts.revealedCentral;
  return state;
}

function makeBotState(botCard, opts = {}) {
  const playerIds = opts.playerIds || ['Alice', 'You', 'Bob', 'Carol', 'Dave'];
  const otherCards = opts.otherCards || [7, 5, 6, 8];
  const privateCards = {};
  playerIds.forEach((pid, i) => { privateCards[pid] = i === 0 ? botCard : otherCards[i - 1]; });
  const centralCards = opts.centralCards || [9, 10, 11];
  const state = new GameState(playerIds, privateCards, centralCards);
  if (opts.phase) state.phase = opts.phase;
  if (opts.revealedCentral) state.revealedCentral = opts.revealedCentral;
  return state;
}

function makeStrategicBotState(botCard, opts = {}) {
  const playerIds = ['Bob', 'You', 'Alice', 'Carol', 'Dave'];
  const otherCards = opts.otherCards || [7, 5, 6, 8];
  const privateCards = {};
  playerIds.forEach((pid, i) => { privateCards[pid] = i === 0 ? botCard : otherCards[i - 1]; });
  const centralCards = opts.centralCards || [9, 10, 11];
  const state = new GameState(playerIds, privateCards, centralCards);
  if (opts.phase) state.phase = opts.phase;
  if (opts.revealedCentral) state.revealedCentral = opts.revealedCentral;
  return state;
}

function _t(buyer, seller, price) {
  return new Trade(buyer, seller, price, Phase.OPEN);
}

// ═══════════════════════════════════════════════════════════════════════════
// TEST SUITES
// ═══════════════════════════════════════════════════════════════════════════

// ── Deck Composition ────────────────────────────────────────────────────────

suite('Deck Composition');

test('deck has 17 cards', () => {
  assertEqual(DECK.length, 17);
  assertEqual(DECK_SIZE, 17);
});

test('deck contains -10', () => {
  assertIncludes(DECK, -10);
});

test('deck contains 20', () => {
  assertIncludes(DECK, 20);
});

test('deck contains 1 through 15', () => {
  for (let i = 1; i <= 15; i++) assertIncludes(DECK, i);
});

test('deck has no duplicates', () => {
  assertEqual(DECK.length, new Set(DECK).size);
});

test('deck sum is 130', () => {
  assertEqual(DECK.reduce((a, b) => a + b, 0), 130);
  assertEqual(DECK_SUM, 130);
});

test('deck mean approx 7.65', () => {
  assertAlmostEqual(DECK_MEAN, 130 / 17, 0.001);
});

test('N_PLAYERS is 5', () => assertEqual(N_PLAYERS, 5));
test('N_CENTRAL is 3', () => assertEqual(N_CENTRAL, 3));
test('N_IN_PLAY is 8', () => assertEqual(N_IN_PLAY, 8));

// ── Deal ────────────────────────────────────────────────────────────────────

suite('Deal');

test('returns correct number of private cards', () => {
  const pids = ['You', 'Alice', 'Bob', 'Carol', 'Dave'];
  const { privateCards } = deal(pids, new SeededRandom(42));
  assertEqual(Object.keys(privateCards).length, 5);
});

test('returns correct number of central cards', () => {
  const pids = ['You', 'Alice', 'Bob', 'Carol', 'Dave'];
  const { centralCards } = deal(pids, new SeededRandom(42));
  assertEqual(centralCards.length, 3);
});

test('all dealt cards from deck', () => {
  const pids = ['You', 'Alice', 'Bob', 'Carol', 'Dave'];
  const { privateCards, centralCards } = deal(pids, new SeededRandom(42));
  const allDealt = [...Object.values(privateCards), ...centralCards];
  for (const card of allDealt) assertIncludes(DECK, card);
});

test('no duplicate cards in deal', () => {
  const pids = ['You', 'Alice', 'Bob', 'Carol', 'Dave'];
  const { privateCards, centralCards } = deal(pids, new SeededRandom(42));
  const allDealt = [...Object.values(privateCards), ...centralCards];
  assertEqual(allDealt.length, new Set(allDealt).size);
});

test('each player gets one card', () => {
  const pids = ['You', 'Alice', 'Bob', 'Carol', 'Dave'];
  const { privateCards } = deal(pids, new SeededRandom(42));
  for (const pid of pids) assertTrue(pid in privateCards);
});

test('deterministic with same seed', () => {
  const pids = ['You', 'Alice', 'Bob', 'Carol', 'Dave'];
  const r1 = deal(pids, new SeededRandom(99));
  const r2 = deal(pids, new SeededRandom(99));
  assertEqual(JSON.stringify(r1.privateCards), JSON.stringify(r2.privateCards));
  assertEqual(JSON.stringify(r1.centralCards), JSON.stringify(r2.centralCards));
});

test('different seeds give different deals', () => {
  const pids = ['You', 'Alice', 'Bob', 'Carol', 'Dave'];
  const r1 = deal(pids, new SeededRandom(1));
  const r2 = deal(pids, new SeededRandom(2));
  assertTrue(
    JSON.stringify(r1.privateCards) !== JSON.stringify(r2.privateCards) ||
    JSON.stringify(r1.centralCards) !== JSON.stringify(r2.centralCards)
  );
});

test('wrong player count raises', () => {
  assertThrows(() => deal(['A', 'B', 'C'], new SeededRandom(42)));
});

// ── Expected Total ──────────────────────────────────────────────────────────

suite('Expected Total');

test('expected total all unknown', () => {
  const ev = expectedTotal([], 8);
  assertAlmostEqual(ev, 8 * (130 / 17), 0.01);
});

test('expected total holding 20', () => {
  const ev = expectedTotal([20], 7);
  assertAlmostEqual(ev, 68.0, 0.6);
});

test('expected total holding -10', () => {
  const ev = expectedTotal([-10], 7);
  assertAlmostEqual(ev, 51.25, 0.6);
});

test('expected total all known', () => {
  const known = [-10, 1, 2, 3, 4, 5, 6, 7];
  assertEqual(expectedTotal(known, 0), known.reduce((a, b) => a + b, 0));
});

test('expected total increases with high known card', () => {
  assertGreater(expectedTotal([15], 7), expectedTotal([1], 7));
});

test('deck mean excluding removes card', () => {
  assertLess(deckMeanExcluding([20]), DECK_MEAN);
});

test('deck mean excluding multiple', () => {
  assertLess(deckMeanExcluding([15, 14, 13]), DECK_MEAN);
});

// ── Quote ───────────────────────────────────────────────────────────────────

suite('Quote');

test('valid quote', () => {
  const q = new Quote(60, 62);
  assertEqual(q.bid, 60);
  assertEqual(q.ask, 62);
});

test('invalid spread raises', () => {
  assertThrows(() => new Quote(60, 63));
});

test('invalid spread too small raises', () => {
  assertThrows(() => new Quote(60, 61));
});

test('spread of zero raises', () => {
  assertThrows(() => new Quote(60, 60));
});

test('from mid even', () => {
  const q = Quote.fromMid(62);
  assertEqual(q.bid, 61);
  assertEqual(q.ask, 63);
});

test('from mid odd', () => {
  const q = Quote.fromMid(61);
  assertEqual(q.bid, 60);
  assertEqual(q.ask, 62);
});

test('str representation', () => {
  const q = new Quote(58, 60);
  const s = q.toString();
  assertTrue(s.includes('58'));
  assertTrue(s.includes('60'));
});

test('negative bid valid', () => {
  const q = new Quote(-2, 0);
  assertEqual(q.bid, -2);
  assertEqual(q.ask, 0);
});

// ── Trade PnL ───────────────────────────────────────────────────────────────

suite('Trade PnL');

test('buyer pnl positive', () => {
  const trade = new Trade('Gary', 'Alice', 52, Phase.OPEN);
  assertEqual(trade.pnlFor('Gary', 67), 15);
});

test('buyer pnl negative', () => {
  const trade = new Trade('Gary', 'Alice', 70, Phase.OPEN);
  assertEqual(trade.pnlFor('Gary', 60), -10);
});

test('seller pnl positive', () => {
  const trade = new Trade('Alice', 'Gary', 67, Phase.OPEN);
  assertEqual(trade.pnlFor('Gary', 52), 15);
});

test('seller pnl negative', () => {
  const trade = new Trade('Alice', 'Gary', 50, Phase.OPEN);
  assertEqual(trade.pnlFor('Gary', 65), -15);
});

test('uninvolved player pnl is zero', () => {
  const trade = new Trade('Alice', 'Bob', 60, Phase.OPEN);
  assertEqual(trade.pnlFor('Carol', 80), 0);
  assertEqual(trade.pnlFor('Dave', 30), 0);
});

test('pnl at breakeven', () => {
  const trade = new Trade('Gary', 'Alice', 60, Phase.OPEN);
  assertEqual(trade.pnlFor('Gary', 60), 0);
});

test('buyer and seller pnl sum to zero', () => {
  const trade = new Trade('Gary', 'Alice', 65, Phase.OPEN);
  assertEqual(trade.pnlFor('Gary', 72) + trade.pnlFor('Alice', 72), 0);
});

// ── Advance Phase ───────────────────────────────────────────────────────────

suite('Advance Phase');

test('phase order open to reveal1', () => {
  const state = makeState({ phase: Phase.OPEN });
  state.advancePhase();
  assertEqual(state.phase, Phase.REVEAL_1);
});

test('phase order full sequence', () => {
  const state = makeState({ phase: Phase.OPEN });
  const expected = [Phase.REVEAL_1, Phase.REVEAL_2, Phase.REVEAL_3, Phase.SETTLE];
  for (const exp of expected) {
    state.advancePhase();
    assertEqual(state.phase, exp);
  }
});

test('reveal 1 exposes first central card', () => {
  const state = makeState({ centralCards: [14, 13, 9], phase: Phase.OPEN });
  state.advancePhase();
  assertEqual(JSON.stringify(state.revealedCentral), JSON.stringify([14]));
});

test('reveal 2 exposes second central card', () => {
  const state = makeState({ centralCards: [14, 13, 9], phase: Phase.OPEN });
  state.advancePhase();
  state.advancePhase();
  assertEqual(JSON.stringify(state.revealedCentral), JSON.stringify([14, 13]));
});

test('reveal 3 exposes all central cards', () => {
  const state = makeState({ centralCards: [14, 13, 9], phase: Phase.OPEN });
  for (let i = 0; i < 3; i++) state.advancePhase();
  assertEqual(JSON.stringify(state.revealedCentral), JSON.stringify([14, 13, 9]));
});

test('advance resets turn counter', () => {
  const state = makeState();
  state.turn = 5;
  state.advancePhase();
  assertEqual(state.turn, 0);
});

test('phase order constant is correct', () => {
  assertEqual(PHASE_ORDER[0], Phase.OPEN);
  assertEqual(PHASE_ORDER[PHASE_ORDER.length - 1], Phase.SETTLE);
  assertEqual(PHASE_ORDER.length, 5);
});

// ── Final Total ─────────────────────────────────────────────────────────────

suite('Final Total');

test('final total sums all 8 cards', () => {
  const state = makeState({
    privateCards: { You: 7, Alice: 10, Bob: 5, Carol: 6, Dave: 15 },
    centralCards: [14, 13, 9],
  });
  assertEqual(state.finalTotal(), 79);
});

test('final total with minus 10', () => {
  const state = makeState({
    privateCards: { You: -10, Alice: 10, Bob: 5, Carol: 6, Dave: 15 },
    centralCards: [14, 13, 9],
  });
  assertEqual(state.finalTotal(), 62);
});

// ── Known / Unknown Cards ───────────────────────────────────────────────────

suite('Known / Unknown Cards');

test('known cards no reveals', () => {
  const state = makeState();
  const known = state.knownCardsFor('You');
  assertEqual(JSON.stringify(known), JSON.stringify([7]));
});

test('known cards after reveal', () => {
  const state = makeState({ centralCards: [14, 13, 9], revealedCentral: [14] });
  const known = state.knownCardsFor('You');
  assertIncludes(known, 7);
  assertIncludes(known, 14);
  assertEqual(known.length, 2);
});

test('unknown count at start', () => {
  const state = makeState();
  assertEqual(state.unknownCountFor('You'), 7);
});

test('unknown count after one reveal', () => {
  const state = makeState({ revealedCentral: [14] });
  assertEqual(state.unknownCountFor('You'), 6);
});

test('unknown count fully revealed', () => {
  const state = makeState({ centralCards: [14, 13, 9], revealedCentral: [14, 13, 9] });
  assertEqual(state.unknownCountFor('You'), 4);
});

// ── Scoreboard ──────────────────────────────────────────────────────────────

suite('Scoreboard');

test('scoreboard no trades', () => {
  const state = makeState();
  const scores = state.scoreboard();
  for (const pid of state.playerIds) assertEqual(scores[pid], 0);
});

test('scoreboard single buy', () => {
  const trade = new Trade('You', 'Alice', 60, Phase.OPEN);
  const state = makeState({ trades: [trade] });
  const scores = state.scoreboard();
  assertEqual(scores['You'], 79 - 60);
  assertEqual(scores['Alice'], 60 - 79);
});

test('scoreboard multiple trades', () => {
  const trades = [
    new Trade('You', 'Alice', 60, Phase.OPEN),
    new Trade('Bob', 'You', 70, Phase.OPEN),
  ];
  const state = makeState({ trades });
  const scores = state.scoreboard();
  const T = state.finalTotal(); // 79
  assertEqual(scores['You'], (T - 60) + (70 - T));
});

test('scoreboard is zero sum', () => {
  const trades = [
    new Trade('You', 'Alice', 58, Phase.OPEN),
    new Trade('Bob', 'Carol', 65, Phase.OPEN),
    new Trade('Dave', 'You', 72, Phase.OPEN),
  ];
  const state = makeState({ trades });
  const scores = state.scoreboard();
  assertEqual(Object.values(scores).reduce((a, b) => a + b, 0), 0);
});

// ── Difficulty ──────────────────────────────────────────────────────────────

suite('Difficulty');

test('difficulty values exist', () => {
  assertNotNull(Difficulty.EASY);
  assertNotNull(Difficulty.MEDIUM);
  assertNotNull(Difficulty.HARD);
});

test('difficulty members count', () => {
  assertEqual(Object.keys(Difficulty).length, 3);
});

// ── Validate Quote ──────────────────────────────────────────────────────────

suite('Validate Quote');

test('valid spread returns quote', () => {
  const q = validateQuote(60, 62);
  assertInstanceOf(q, Quote);
  assertEqual(q.bid, 60);
  assertEqual(q.ask, 62);
});

test('spread of 3 raises', () => {
  assertThrows(() => validateQuote(60, 63));
});

test('spread of 1 raises', () => {
  assertThrows(() => validateQuote(60, 61));
});

test('spread of 0 raises', () => {
  assertThrows(() => validateQuote(60, 60));
});

test('negative bid valid', () => {
  const q = validateQuote(-4, -2);
  assertEqual(q.bid, -4);
});

// ── Set Market ──────────────────────────────────────────────────────────────

suite('Set Market');

test('set market stores quote', () => {
  const state = makeState();
  const q = setMarket(state, 'You', 58, 60);
  assertEqual(state.markets['You'], q);
  assertEqual(q.bid, 58);
  assertEqual(q.ask, 60);
});

test('set market overwrites previous', () => {
  const state = makeState();
  setMarket(state, 'You', 58, 60);
  setMarket(state, 'You', 62, 64);
  assertEqual(state.markets['You'].bid, 62);
});

test('set market invalid spread raises', () => {
  const state = makeState();
  assertThrows(() => setMarket(state, 'You', 58, 61));
});

test('set market unknown player raises', () => {
  const state = makeState();
  assertThrows(() => setMarket(state, 'Ghost', 58, 60));
});

test('set market during settle raises', () => {
  const state = makeState({ phase: Phase.SETTLE });
  assertThrows(() => setMarket(state, 'You', 58, 60));
});

test('set market all phases except settle', () => {
  for (const phase of [Phase.OPEN, Phase.REVEAL_1, Phase.REVEAL_2, Phase.REVEAL_3]) {
    const state = makeState({ phase });
    const q = setMarket(state, 'You', 60, 62);
    assertEqual(q.bid, 60);
  }
});

// ── Execute Trade ───────────────────────────────────────────────────────────

suite('Execute Trade');

test('buy hits ask', () => {
  const state = makeState();
  state.markets['Alice'] = new Quote(60, 62);
  const trade = executeTrade(state, 'You', 'Alice', 'buy');
  assertEqual(trade.price, 62);
  assertEqual(trade.buyer, 'You');
  assertEqual(trade.seller, 'Alice');
});

test('buy recorded in state', () => {
  const state = makeState();
  state.markets['Alice'] = new Quote(60, 62);
  executeTrade(state, 'You', 'Alice', 'buy');
  assertEqual(state.trades.length, 1);
});

test('sell hits bid', () => {
  const state = makeState();
  state.markets['Alice'] = new Quote(60, 62);
  const trade = executeTrade(state, 'You', 'Alice', 'sell');
  assertEqual(trade.price, 60);
  assertEqual(trade.buyer, 'Alice');
  assertEqual(trade.seller, 'You');
});

test('sell recorded in state', () => {
  const state = makeState();
  state.markets['Alice'] = new Quote(60, 62);
  executeTrade(state, 'You', 'Alice', 'sell');
  assertEqual(state.trades.length, 1);
});

test('self trade raises', () => {
  const state = makeState();
  state.markets['You'] = new Quote(60, 62);
  assertThrows(() => executeTrade(state, 'You', 'You', 'buy'));
});

test('no quote raises', () => {
  const state = makeState();
  assertThrows(() => executeTrade(state, 'You', 'Alice', 'buy'));
});

test('invalid direction raises', () => {
  const state = makeState();
  state.markets['Alice'] = new Quote(60, 62);
  assertThrows(() => executeTrade(state, 'You', 'Alice', 'hold'));
});

test('trade during settle raises', () => {
  const state = makeState({ phase: Phase.SETTLE });
  state.markets['Alice'] = new Quote(60, 62);
  assertThrows(() => executeTrade(state, 'You', 'Alice', 'buy'));
});

test('trade records current phase', () => {
  const state = makeState({ phase: Phase.REVEAL_2 });
  state.markets['Alice'] = new Quote(60, 62);
  const trade = executeTrade(state, 'You', 'Alice', 'buy');
  assertEqual(trade.phase, Phase.REVEAL_2);
});

test('multiple trades accumulate', () => {
  const state = makeState();
  state.markets['Alice'] = new Quote(60, 62);
  state.markets['Bob'] = new Quote(58, 60);
  executeTrade(state, 'You', 'Alice', 'buy');
  executeTrade(state, 'You', 'Bob', 'sell');
  assertEqual(state.trades.length, 2);
});

test('bot can trade against human quote', () => {
  const state = makeState();
  state.markets['You'] = new Quote(60, 62);
  const trade = executeTrade(state, 'Alice', 'You', 'buy');
  assertEqual(trade.buyer, 'Alice');
  assertEqual(trade.seller, 'You');
  assertEqual(trade.price, 62);
});

// ── Get Quote ───────────────────────────────────────────────────────────────

suite('Get Quote');

test('get quote returns null before posting', () => {
  const state = makeState();
  assertNull(getQuote(state, 'You'));
});

test('get quote returns quote after posting', () => {
  const state = makeState();
  setMarket(state, 'You', 60, 62);
  const q = getQuote(state, 'You');
  assertNotNull(q);
  assertEqual(q.bid, 60);
});

// ── Trade Summary ───────────────────────────────────────────────────────────

suite('Trade Summary');

test('no trades', () => {
  const state = makeState();
  const s = tradeSummary(state, 'You');
  assertEqual(s.nBuys, 0);
  assertEqual(s.nSells, 0);
  assertEqual(s.avgBuy, 0.0);
  assertEqual(s.avgSell, 0.0);
});

test('one buy', () => {
  const state = makeState();
  state.markets['Alice'] = new Quote(60, 62);
  executeTrade(state, 'You', 'Alice', 'buy');
  const s = tradeSummary(state, 'You');
  assertEqual(s.nBuys, 1);
  assertEqual(s.avgBuy, 62.0);
});

test('one sell', () => {
  const state = makeState();
  state.markets['Alice'] = new Quote(60, 62);
  executeTrade(state, 'You', 'Alice', 'sell');
  const s = tradeSummary(state, 'You');
  assertEqual(s.nSells, 1);
  assertEqual(s.avgSell, 60.0);
});

test('average buy price', () => {
  const state = makeState();
  state.markets['Alice'] = new Quote(60, 62);
  state.markets['Bob'] = new Quote(64, 66);
  executeTrade(state, 'You', 'Alice', 'buy');
  executeTrade(state, 'You', 'Bob', 'buy');
  const s = tradeSummary(state, 'You');
  assertEqual(s.nBuys, 2);
  assertAlmostEqual(s.avgBuy, 64.0, 0.01);
});

test('mixed buys and sells', () => {
  const state = makeState();
  state.markets['Alice'] = new Quote(60, 62);
  state.markets['Bob'] = new Quote(68, 70);
  executeTrade(state, 'You', 'Alice', 'buy');
  executeTrade(state, 'You', 'Bob', 'sell');
  const s = tradeSummary(state, 'You');
  assertEqual(s.nBuys, 1);
  assertEqual(s.nSells, 1);
  assertEqual(s.avgBuy, 62.0);
  assertEqual(s.avgSell, 68.0);
});

// ── Execute Trade Direct ────────────────────────────────────────────────────

suite('Execute Trade Direct');

test('basic trade', () => {
  const state = makeState();
  const trade = executeTradeDirect(state, 'You', 'Alice', 60);
  assertEqual(trade.buyer, 'You');
  assertEqual(trade.seller, 'Alice');
  assertEqual(trade.price, 60);
  assertEqual(trade.phase, Phase.OPEN);
});

test('trade recorded in state', () => {
  const state = makeState();
  executeTradeDirect(state, 'You', 'Alice', 60);
  assertEqual(state.trades.length, 1);
});

test('multiple trades accumulate (direct)', () => {
  const state = makeState();
  executeTradeDirect(state, 'You', 'Alice', 60);
  executeTradeDirect(state, 'Bob', 'Carol', 65);
  assertEqual(state.trades.length, 2);
});

test('self trade raises (direct)', () => {
  const state = makeState();
  assertThrows(() => executeTradeDirect(state, 'You', 'You', 60));
});

test('settle phase raises (direct)', () => {
  const state = makeState({ phase: Phase.SETTLE });
  assertThrows(() => executeTradeDirect(state, 'You', 'Alice', 60));
});

test('unknown buyer raises', () => {
  const state = makeState();
  assertThrows(() => executeTradeDirect(state, 'Ghost', 'Alice', 60));
});

test('unknown seller raises', () => {
  const state = makeState();
  assertThrows(() => executeTradeDirect(state, 'You', 'Ghost', 60));
});

test('records correct phase (direct)', () => {
  const state = makeState({ phase: Phase.REVEAL_2 });
  const trade = executeTradeDirect(state, 'You', 'Alice', 58);
  assertEqual(trade.phase, Phase.REVEAL_2);
});

test('pnl works on direct trade', () => {
  const state = makeState(); // finalTotal = 79
  const trade = executeTradeDirect(state, 'You', 'Alice', 60);
  const T = state.finalTotal();
  assertEqual(trade.pnlFor('You', T), T - 60);
  assertEqual(trade.pnlFor('Alice', T), 60 - T);
});

// ── Settle ──────────────────────────────────────────────────────────────────

suite('Settle');

test('no trades all zero', () => {
  const state = makeState({ phase: Phase.SETTLE });
  const scores = settle(state);
  for (const pid of state.playerIds) assertEqual(scores[pid], 0);
});

test('single buy correct pnl', () => {
  const state = makeState({ trades: [_t('You', 'Alice', 60)], phase: Phase.SETTLE });
  const scores = settle(state);
  const T = state.finalTotal(); // 79
  assertEqual(scores['You'], T - 60);
  assertEqual(scores['Alice'], 60 - T);
});

test('single sell correct pnl', () => {
  const state = makeState({ trades: [_t('Alice', 'You', 70)], phase: Phase.SETTLE });
  const scores = settle(state);
  const T = state.finalTotal(); // 79
  assertEqual(scores['You'], 70 - T);
  assertEqual(scores['Alice'], T - 70);
});

test('buyer profits when total above price', () => {
  const state = makeState({ trades: [_t('You', 'Alice', 50)] });
  const scores = settle(state);
  assertGreater(scores['You'], 0);
});

test('seller profits when total below price', () => {
  const state = makeState({ trades: [_t('Alice', 'You', 100)] });
  const scores = settle(state);
  assertGreater(scores['You'], 0);
});

test('all players in scores', () => {
  const state = makeState();
  const scores = settle(state);
  for (const pid of state.playerIds) assertTrue(pid in scores);
});

test('multiple trades accumulated correctly', () => {
  const trades = [_t('You', 'Alice', 60), _t('Bob', 'You', 70)];
  const state = makeState({ trades });
  const T = state.finalTotal(); // 79
  const scores = settle(state);
  assertEqual(scores['You'], (T - 60) + (70 - T));
});

// ── Gary Arbitrage ──────────────────────────────────────────────────────────

suite('Gary Arbitrage');

test('arbitrage profit is 15 for all totals', () => {
  for (let T = -10; T <= 130; T++) {
    assertEqual(((T - 52) + (67 - T)), 15, `Arbitrage profit should be 15 for T=${T}`);
  }
});

test('gary arbitrage via settle', () => {
  const privateCards = { You: -10, Alice: 20, Bob: 8, Carol: 7, Dave: 6 };
  const centralCards = [5, 4, 3];
  const trades = [_t('You', 'Alice', 52), _t('Bob', 'You', 67)];
  const state = makeState({ trades, privateCards, centralCards });
  const scores = settle(state);
  assertEqual(scores['You'], 15);
});

test('arbitrage is risk free across multiple totals', () => {
  const scenarios = [[1, 2, 3], [10, 11, 12], [13, 14, 15], [20, 9, 8]];
  for (const central of scenarios) {
    const privateCards = { You: -10, Alice: 5, Bob: 6, Carol: 7, Dave: 8 };
    const trades = [_t('You', 'Alice', 52), _t('Bob', 'You', 67)];
    const state = makeState({ trades, privateCards, centralCards: central });
    const scores = settle(state);
    assertEqual(scores['You'], 15, `Arbitrage failed for central=${central}`);
  }
});

// ── Zero Sum ────────────────────────────────────────────────────────────────

suite('Zero Sum');

test('single trade zero sum', () => {
  const state = makeState({ trades: [_t('You', 'Alice', 65)] });
  const scores = settle(state);
  assertEqual(Object.values(scores).reduce((a, b) => a + b, 0), 0);
});

test('multiple trades zero sum', () => {
  const trades = [_t('You', 'Alice', 58), _t('Bob', 'Carol', 65), _t('Dave', 'You', 72), _t('Alice', 'Bob', 61)];
  const state = makeState({ trades });
  const scores = settle(state);
  assertEqual(Object.values(scores).reduce((a, b) => a + b, 0), 0);
});

test('many trades zero sum', () => {
  const pairs = [
    ['You','Alice',60],['Bob','Carol',65],['Dave','You',70],
    ['Alice','Bob',55],['Carol','Dave',68],['You','Bob',62],
    ['Alice','Dave',71],['Carol','You',59],['Bob','Dave',66],
    ['Dave','Alice',63],
  ];
  const trades = pairs.map(([b, s, p]) => _t(b, s, p));
  const state = makeState({ trades });
  const scores = settle(state);
  assertEqual(Object.values(scores).reduce((a, b) => a + b, 0), 0);
});

// ── Leaderboard ─────────────────────────────────────────────────────────────

suite('Leaderboard');

test('sorted highest first', () => {
  const scores = { You: 10, Alice: -5, Bob: 25, Carol: 0, Dave: -15 };
  const board = leaderboard(scores);
  assertEqual(board[0][0], 'Bob');
  assertEqual(board[0][1], 25);
  assertEqual(board[board.length - 1][0], 'Dave');
});

test('all zero', () => {
  const scores = { You: 0, Alice: 0, Bob: 0 };
  const board = leaderboard(scores);
  assertEqual(board.length, 3);
  assertTrue(board.every(([_, s]) => s === 0));
});

test('all negative', () => {
  const scores = { You: -10, Alice: -5, Bob: -20 };
  const board = leaderboard(scores);
  assertEqual(board[0][0], 'Alice');
});

test('single player', () => {
  const scores = { You: 42 };
  const board = leaderboard(scores);
  assertEqual(board[0][0], 'You');
  assertEqual(board[0][1], 42);
});

// ── Trade Breakdown ─────────────────────────────────────────────────────────

suite('Trade Breakdown');

test('filters to player trades only', () => {
  const trades = [_t('You', 'Alice', 60), _t('Bob', 'Carol', 65), _t('Dave', 'You', 70)];
  const state = makeState({ trades });
  const bd = tradeBreakdown(state, 'You');
  assertEqual(bd.length, 2);
});

test('uninvolved player gets empty breakdown', () => {
  const state = makeState({ trades: [_t('Alice', 'Bob', 60)] });
  assertEqual(tradeBreakdown(state, 'Carol').length, 0);
});

test('pnl in breakdown is correct', () => {
  const state = makeState({ trades: [_t('You', 'Alice', 60)] });
  const bd = tradeBreakdown(state, 'You');
  assertEqual(bd.length, 1);
  assertEqual(bd[0].pnl, state.finalTotal() - 60);
});

test('breakdown includes both buy and sell', () => {
  const trades = [_t('You', 'Alice', 60), _t('Bob', 'You', 70)];
  const state = makeState({ trades });
  assertEqual(tradeBreakdown(state, 'You').length, 2);
});

// ── SimpleBot Quote ─────────────────────────────────────────────────────────

suite('SimpleBot Quote');

test('spread always 2', () => {
  const bot = new SimpleBot('Alice', new SeededRandom(0));
  for (const card of [-10, 1, 7, 15, 20]) {
    const state = makeBotState(card);
    const q = bot.getQuote(state);
    assertEqual(q.ask - q.bid, 2, `Spread != 2 for card=${card}`);
  }
});

test('spread always 2 with requester', () => {
  const bot = new SimpleBot('Alice', new SeededRandom(0));
  for (const card of [-10, 1, 7, 15, 20]) {
    const state = makeBotState(card);
    const q = bot.getQuote(state, 'You');
    assertEqual(q.ask - q.bid, 2, `Spread != 2 with requester for card=${card}`);
  }
});

test('quote near ev for average card', () => {
  const bot = new SimpleBot('Alice', new SeededRandom(1));
  const state = makeBotState(7);
  const q = bot.getQuote(state);
  const mid = (q.bid + q.ask) / 2;
  const ev = expectedTotal([7], 7);
  assertAlmostEqual(mid, ev, 2.0);
});

test('quote higher for high card', () => {
  const bot = new SimpleBot('Alice', new SeededRandom(2));
  const qHigh = bot.getQuote(makeBotState(20));
  const qLow = bot.getQuote(makeBotState(1));
  assertGreater(qHigh.bid, qLow.bid);
});

test('quote lower for low card', () => {
  const bot = new SimpleBot('Alice', new SeededRandom(3));
  const qNeg = bot.getQuote(makeBotState(-10));
  const qPos = bot.getQuote(makeBotState(15));
  assertLess(qNeg.bid, qPos.bid);
});

test('quote near book value for 20', () => {
  const bot = new SimpleBot('Alice', new SeededRandom(0));
  const q = bot.getQuote(makeBotState(20));
  assertAlmostEqual((q.bid + q.ask) / 2, 68.0, 3.0);
});

test('quote near book value for -10', () => {
  const bot = new SimpleBot('Alice', new SeededRandom(0));
  const q = bot.getQuote(makeBotState(-10));
  assertAlmostEqual((q.bid + q.ask) / 2, 51.2, 3.0);
});

test('quote updates after central reveal', () => {
  const bot = new SimpleBot('Alice', new SeededRandom(0));
  const qBefore = bot.getQuote(makeBotState(7, { revealedCentral: [] }));
  const qAfter = bot.getQuote(makeBotState(7, { centralCards: [15, 9, 8], revealedCentral: [15] }));
  assertGreater(qAfter.bid, qBefore.bid);
});

// ── SimpleBot Decide On Quote ───────────────────────────────────────────────

suite('SimpleBot Decide On Quote');

test('buys when ask well below ev', () => {
  const bot = new SimpleBot('Alice', new SeededRandom(0));
  const state = makeBotState(20);
  assertEqual(bot.decideOnQuote(state, 'You', new Quote(53, 55)), 'buy');
});

test('sells when bid well above ev', () => {
  const bot = new SimpleBot('Alice', new SeededRandom(0));
  const state = makeBotState(-10);
  assertEqual(bot.decideOnQuote(state, 'You', new Quote(70, 72)), 'sell');
});

test('walks when no edge', () => {
  const bot = new SimpleBot('Alice', new SeededRandom(0));
  const state = makeBotState(7);
  assertNull(bot.decideOnQuote(state, 'You', new Quote(60, 62)));
});

test('prefers sell when sell edge > buy edge', () => {
  const bot = new SimpleBot('Alice', new SeededRandom(0));
  const state = makeBotState(7);
  // bid=80, ask=82 → sell_edge = 80 - ~61 = ~19
  assertEqual(bot.decideOnQuote(state, 'You', new Quote(80, 82)), 'sell');
});

// ── SimpleBot Decide Action ─────────────────────────────────────────────────

suite('SimpleBot Decide Action');

test('returns valid player id', () => {
  const bot = new SimpleBot('Alice', new SeededRandom(42));
  const state = makeBotState(7);
  const target = bot.decideAction(state);
  assertNotNull(target);
  assertIncludes(state.playerIds, target);
  assertNotEqual(target, 'Alice');
});

test('returns null during settle', () => {
  const bot = new SimpleBot('Alice', new SeededRandom(42));
  const state = makeBotState(7, { phase: Phase.SETTLE });
  assertNull(bot.decideAction(state));
});

test('never returns self', () => {
  for (let seed = 0; seed < 100; seed++) {
    const bot = new SimpleBot('Alice', new SeededRandom(seed));
    const state = makeBotState(7);
    const target = bot.decideAction(state);
    if (target !== null) assertNotEqual(target, 'Alice');
  }
});

// ── SimpleBot Easy Mode ─────────────────────────────────────────────────────

suite('SimpleBot Easy Mode');

test('no adaptation even after many sells', () => {
  const bot = new SimpleBot('Alice', new SeededRandom(0), null);
  const state = makeBotState(7);
  for (let i = 0; i < 10; i++) bot.recordTrade('Alice', 'You', 'You');
  const qBefore = new SimpleBot('Alice', new SeededRandom(0)).getQuote(state);
  const qAfter = bot.getQuote(state, 'You');
  assertEqual(qBefore.bid, qAfter.bid);
});

test('no adaptation after many buys', () => {
  const bot = new SimpleBot('Alice', new SeededRandom(0), null);
  const state = makeBotState(7);
  for (let i = 0; i < 10; i++) bot.recordTrade('You', 'Alice', 'You');
  const qBefore = new SimpleBot('Alice', new SeededRandom(0)).getQuote(state);
  const qAfter = bot.getQuote(state, 'You');
  assertEqual(qBefore.bid, qAfter.bid);
});

// ── SimpleBot Medium Mode ───────────────────────────────────────────────────

suite('SimpleBot Medium Mode');

test('no adaptation below threshold', () => {
  const bot = new SimpleBot('Alice', new SeededRandom(0), 5);
  const state = makeBotState(7);
  for (let i = 0; i < 4; i++) bot.recordTrade('Alice', 'You', 'You');
  const qNoAdapt = new SimpleBot('Alice', new SeededRandom(0)).getQuote(state);
  const q = bot.getQuote(state, 'You');
  assertEqual(q.bid, qNoAdapt.bid);
});

test('adaptation kicks in at threshold', () => {
  const bot = new SimpleBot('Alice', new SeededRandom(0), 5);
  const state = makeBotState(7);
  for (let i = 0; i < 6; i++) bot.recordTrade('Alice', 'You', 'You');
  const qBaseline = new SimpleBot('Alice', new SeededRandom(0)).getQuote(state);
  const qAdapted = bot.getQuote(state, 'You');
  assertLess(qAdapted.bid, qBaseline.bid, 'After 6 sells, quotes should shift down');
});

test('adaptation shifts up for bullish', () => {
  const bot = new SimpleBot('Alice', new SeededRandom(0), 5);
  const state = makeBotState(7);
  for (let i = 0; i < 6; i++) bot.recordTrade('You', 'Alice', 'You');
  const qBaseline = new SimpleBot('Alice', new SeededRandom(0)).getQuote(state);
  const qAdapted = bot.getQuote(state, 'You');
  assertGreater(qAdapted.bid, qBaseline.bid, 'After 6 buys, quotes should shift up');
});

test('no adaptation when requester is null', () => {
  const bot = new SimpleBot('Alice', new SeededRandom(0), 5);
  const state = makeBotState(7);
  for (let i = 0; i < 10; i++) bot.recordTrade('Alice', 'You', 'You');
  const qNoReq = bot.getQuote(state, null);
  const qBaseline = new SimpleBot('Alice', new SeededRandom(0)).getQuote(state);
  assertEqual(qNoReq.bid, qBaseline.bid);
});

test('adaptation spread still 2', () => {
  const bot = new SimpleBot('Alice', new SeededRandom(0), 5);
  const state = makeBotState(7);
  for (let i = 0; i < 20; i++) bot.recordTrade('Alice', 'You', 'You');
  const q = bot.getQuote(state, 'You');
  assertEqual(q.ask - q.bid, 2);
});

// ── StrategicBot Quote ──────────────────────────────────────────────────────

suite('StrategicBot Quote');

test('spread always 2 (strategic)', () => {
  const bot = new StrategicBot('Bob', new SeededRandom(0));
  for (const card of [-10, 1, 7, 15, 20]) {
    const state = makeStrategicBotState(card);
    const q = bot.getQuote(state);
    assertEqual(q.ask - q.bid, 2, `Spread != 2 for card=${card}`);
  }
});

test('spread always 2 with requester (strategic)', () => {
  const bot = new StrategicBot('Bob', new SeededRandom(0));
  for (const card of [-10, 1, 7, 15, 20]) {
    const state = makeStrategicBotState(card);
    const q = bot.getQuote(state, 'You');
    assertEqual(q.ask - q.bid, 2);
  }
});

test('bluffs low when high card', () => {
  const bot = new StrategicBot('Bob', new SeededRandom(0));
  const state = makeStrategicBotState(20, { revealedCentral: [] });
  const q = bot.getQuote(state);
  const trueEv = expectedTotal([20], 7);
  const bluffMid = (q.bid + q.ask) / 2;
  assertLess(bluffMid, trueEv, `High-card bluff should quote below EV=${trueEv.toFixed(1)}`);
});

test('bluffs high when low card', () => {
  const bot = new StrategicBot('Bob', new SeededRandom(0));
  const state = makeStrategicBotState(-10, { revealedCentral: [] });
  const q = bot.getQuote(state);
  const trueEv = expectedTotal([-10], 7);
  const bluffMid = (q.bid + q.ask) / 2;
  assertGreater(bluffMid, trueEv, `Low-card bluff should quote above EV=${trueEv.toFixed(1)}`);
});

test('bluff offset is large early', () => {
  const bot = new StrategicBot('Bob', new SeededRandom(0));
  const state = makeStrategicBotState(20, { revealedCentral: [] });
  const q = bot.getQuote(state);
  const trueEv = expectedTotal([20], 7);
  const offset = trueEv - (q.bid + q.ask) / 2;
  assertGreaterEqual(offset, BLUFF_OFFSET_EARLY - 1.5, `Early bluff offset should be ~${BLUFF_OFFSET_EARLY}`);
});

test('bluff converges after two reveals', () => {
  const bot = new StrategicBot('Bob', new SeededRandom(0));
  const stateEarly = makeStrategicBotState(20, { revealedCentral: [] });
  const stateLate = makeStrategicBotState(20, { centralCards: [5, 6, 9], revealedCentral: [5, 6] });
  const qEarly = bot.getQuote(stateEarly);
  const qLate = bot.getQuote(stateLate);
  const offsetEarly = Math.abs((qEarly.bid + qEarly.ask) / 2 - expectedTotal([20], 7));
  const offsetLate = Math.abs((qLate.bid + qLate.ask) / 2 - expectedTotal([20, 5, 6], 5));
  assertGreater(offsetEarly, offsetLate, 'Bluff offset should shrink as cards are revealed');
});

test('late bluff offset is small', () => {
  const bot = new StrategicBot('Bob', new SeededRandom(0));
  const state = makeStrategicBotState(20, { centralCards: [5, 6, 9], revealedCentral: [5, 6] });
  const q = bot.getQuote(state);
  const trueEv = expectedTotal([20, 5, 6], 5);
  const offset = Math.abs((q.bid + q.ask) / 2 - trueEv);
  assertLessEqual(offset, BLUFF_OFFSET_LATE + 1.5, `Late bluff offset should be ~${BLUFF_OFFSET_LATE}`);
});

// ── StrategicBot Decide On Quote ────────────────────────────────────────────

suite('StrategicBot Decide On Quote');

test('buys when high card and ask below ev', () => {
  const bot = new StrategicBot('Bob', new SeededRandom(0));
  const state = makeStrategicBotState(20);
  assertEqual(bot.decideOnQuote(state, 'You', new Quote(55, 57)), 'buy');
});

test('sells when low card and bid above ev', () => {
  const bot = new StrategicBot('Bob', new SeededRandom(0));
  const state = makeStrategicBotState(-10);
  assertEqual(bot.decideOnQuote(state, 'You', new Quote(68, 70)), 'sell');
});

test('walks when no edge (strategic)', () => {
  const bot = new StrategicBot('Bob', new SeededRandom(0));
  const state = makeStrategicBotState(20);
  assertNull(bot.decideOnQuote(state, 'You', new Quote(70, 72)));
});

test('opportunistic sell even with high card', () => {
  const bot = new StrategicBot('Bob', new SeededRandom(0));
  const state = makeStrategicBotState(20);
  assertEqual(bot.decideOnQuote(state, 'You', new Quote(80, 82)), 'sell');
});

// ── StrategicBot Decide Action ──────────────────────────────────────────────

suite('StrategicBot Decide Action');

test('returns valid target (strategic)', () => {
  const bot = new StrategicBot('Bob', new SeededRandom(42));
  const state = makeStrategicBotState(20);
  const target = bot.decideAction(state);
  assertNotNull(target);
  assertIncludes(state.playerIds, target);
  assertNotEqual(target, 'Bob');
});

test('returns null during settle (strategic)', () => {
  const bot = new StrategicBot('Bob', new SeededRandom(0));
  const state = makeStrategicBotState(20, { phase: Phase.SETTLE });
  assertNull(bot.decideAction(state));
});

test('never returns self (strategic)', () => {
  for (let seed = 0; seed < 100; seed++) {
    const bot = new StrategicBot('Bob', new SeededRandom(seed));
    const state = makeStrategicBotState(20);
    const target = bot.decideAction(state);
    if (target !== null) assertNotEqual(target, 'Bob');
  }
});

test('max trades per turn respected', () => {
  const bot = new StrategicBot('Bob', new SeededRandom(0));
  const state = makeStrategicBotState(20);
  let actionsTaken = 0;
  for (let i = 0; i < MAX_TRADES_PER_TURN + 5; i++) {
    if (bot.decideAction(state) === null) break;
    actionsTaken++;
  }
  assertLessEqual(actionsTaken, MAX_TRADES_PER_TURN);
});

test('phase change resets trade count', () => {
  const bot = new StrategicBot('Bob', new SeededRandom(0));
  const state = makeStrategicBotState(20);
  for (let i = 0; i < MAX_TRADES_PER_TURN + 2; i++) bot.decideAction(state);
  assertNull(bot.decideAction(state));
  state.phase = Phase.REVEAL_1;
  bot.onPhaseChange(state);
  assertNotNull(bot.decideAction(state));
});

// ── StrategicBot Adaptation ─────────────────────────────────────────────────

suite('StrategicBot Adaptation');

test('no adaptation below threshold (strategic)', () => {
  const bot = new StrategicBot('Bob', new SeededRandom(0));
  const state = makeStrategicBotState(7);
  bot.recordTrade('Bob', 'You', 'You');
  const qBaseline = new StrategicBot('Bob', new SeededRandom(0)).getQuote(state);
  const q = bot.getQuote(state, 'You');
  assertEqual(q.bid, qBaseline.bid);
});

test('adaptation kicks in aggressively', () => {
  const bot = new StrategicBot('Bob', new SeededRandom(0));
  const state = makeStrategicBotState(7);
  for (let i = 0; i < 4; i++) bot.recordTrade('Bob', 'You', 'You');
  const qBaseline = new StrategicBot('Bob', new SeededRandom(0)).getQuote(state);
  const qAdapted = bot.getQuote(state, 'You');
  assertLess(qAdapted.bid, qBaseline.bid, 'Strategic bot should adapt aggressively after sells');
});

test('adaptation shifts up for bullish (strategic)', () => {
  const bot = new StrategicBot('Bob', new SeededRandom(0));
  const state = makeStrategicBotState(7);
  for (let i = 0; i < 4; i++) bot.recordTrade('You', 'Bob', 'You');
  const qBaseline = new StrategicBot('Bob', new SeededRandom(0)).getQuote(state);
  const qAdapted = bot.getQuote(state, 'You');
  assertGreater(qAdapted.bid, qBaseline.bid, 'Should shift up for bullish requester');
});

test('adaptation spread still 2 (strategic)', () => {
  const bot = new StrategicBot('Bob', new SeededRandom(0));
  const state = makeStrategicBotState(7);
  for (let i = 0; i < 20; i++) bot.recordTrade('Bob', 'You', 'You');
  const q = bot.getQuote(state, 'You');
  assertEqual(q.ask - q.bid, 2);
});

// ── StrategicBot vs SimpleBot ───────────────────────────────────────────────

suite('StrategicBot vs SimpleBot');

function avgMid(BotClass, card, n = 50, opts = {}) {
  let sum = 0;
  for (let seed = 0; seed < n; seed++) {
    const bot = new BotClass(
      'Bob',
      new SeededRandom(seed),
      ...(BotClass === SimpleBot ? [opts.adaptationThreshold || null] : [])
    );
    const state = makeStrategicBotState(card, { revealedCentral: [] });
    const q = bot.getQuote(state);
    sum += (q.bid + q.ask) / 2;
  }
  return sum / n;
}

test('high card strategic quotes lower than simple', () => {
  const avgSimple = avgMid(SimpleBot, 20);
  const avgStrategic = avgMid(StrategicBot, 20);
  assertLess(avgStrategic, avgSimple,
    `High-card strategic avg ${avgStrategic.toFixed(1)} should be below simple avg ${avgSimple.toFixed(1)}`);
});

test('low card strategic quotes higher than simple', () => {
  const avgSimple = avgMid(SimpleBot, -10);
  const avgStrategic = avgMid(StrategicBot, -10);
  assertGreater(avgStrategic, avgSimple,
    `Low-card strategic avg ${avgStrategic.toFixed(1)} should be above simple avg ${avgSimple.toFixed(1)}`);
});

// ═══════════════════════════════════════════════════════════════════════════
// RENDER RESULTS
// ═══════════════════════════════════════════════════════════════════════════

const summaryEl = document.getElementById('summary');
const resultsEl = document.getElementById('results');

summaryEl.textContent = `${_passed + _failed} tests: ${_passed} passed, ${_failed} failed`;
summaryEl.className = _failed === 0 ? 'pass-summary' : 'fail-summary';

for (const s of _suites) {
  const div = document.createElement('div');
  div.className = 'suite';

  const allPass = s.tests.every(t => t.passed);
  const header = document.createElement('div');
  header.className = 'suite-name';
  header.textContent = `${allPass ? '' : ''} ${s.name} (${s.tests.filter(t => t.passed).length}/${s.tests.length})`;
  div.appendChild(header);

  const testsDiv = document.createElement('div');
  testsDiv.style.display = allPass ? 'none' : 'block';
  header.addEventListener('click', () => {
    testsDiv.style.display = testsDiv.style.display === 'none' ? 'block' : 'none';
  });

  for (const t of s.tests) {
    const testEl = document.createElement('div');
    testEl.className = `test ${t.passed ? 'pass' : 'fail'}`;
    testEl.textContent = t.name;
    testsDiv.appendChild(testEl);
    if (!t.passed && t.error) {
      const errEl = document.createElement('div');
      errEl.className = 'error-detail';
      errEl.textContent = t.error;
      testsDiv.appendChild(errEl);
    }
  }

  div.appendChild(testsDiv);
  resultsEl.appendChild(div);
}

console.log(`\n${_passed + _failed} tests: ${_passed} passed, ${_failed} failed\n`);
if (_failed > 0) {
  console.error('FAILED TESTS:');
  for (const s of _suites) {
    for (const t of s.tests) {
      if (!t.passed) console.error(`  ${s.name} > ${t.name}: ${t.error}`);
    }
  }
}
