"""
test_trading.py — Tests for game/trading.py

Covers:
  - set_market(): valid quotes, bad spreads, settle-phase block
  - execute_trade(): buy/sell mechanics, self-trade, no-quote, settle-phase
  - trade_summary(): counts and averages
  - Multiple trade accumulation
"""

import unittest

from game.state import GameState, Phase, Quote, Trade
from game.trading import (
    TradingError,
    execute_trade,
    execute_trade_direct,
    get_quote,
    set_market,
    trade_summary,
    validate_quote,
)


def _make_state(phase=Phase.OPEN, extra_trades=None):
    player_ids = ["You", "Alice", "Bob", "Carol", "Dave"]
    private_cards = {"You": 7, "Alice": 10, "Bob": 5, "Carol": 6, "Dave": 15}
    central_cards = [14, 13, 9]
    state = GameState(
        player_ids=player_ids,
        private_cards=private_cards,
        central_cards=central_cards,
        trades=list(extra_trades or []),
        markets={pid: None for pid in player_ids},
        phase=phase,
    )
    return state


class TestValidateQuote(unittest.TestCase):

    def test_valid_spread_returns_quote(self):
        q = validate_quote(60, 62)
        self.assertIsInstance(q, Quote)
        self.assertEqual(q.bid, 60)
        self.assertEqual(q.ask, 62)

    def test_spread_of_3_raises(self):
        with self.assertRaises(TradingError):
            validate_quote(60, 63)

    def test_spread_of_1_raises(self):
        with self.assertRaises(TradingError):
            validate_quote(60, 61)

    def test_spread_of_0_raises(self):
        with self.assertRaises(TradingError):
            validate_quote(60, 60)

    def test_negative_bid_valid(self):
        q = validate_quote(-4, -2)
        self.assertEqual(q.bid, -4)


class TestSetMarket(unittest.TestCase):

    def test_set_market_stores_quote(self):
        state = _make_state()
        q = set_market(state, "You", 58, 60)
        self.assertEqual(state.markets["You"], q)
        self.assertEqual(q.bid, 58)
        self.assertEqual(q.ask, 60)

    def test_set_market_overwrites_previous(self):
        state = _make_state()
        set_market(state, "You", 58, 60)
        set_market(state, "You", 62, 64)
        self.assertEqual(state.markets["You"].bid, 62)

    def test_set_market_invalid_spread_raises(self):
        state = _make_state()
        with self.assertRaises(TradingError):
            set_market(state, "You", 58, 61)

    def test_set_market_unknown_player_raises(self):
        state = _make_state()
        with self.assertRaises(TradingError):
            set_market(state, "Ghost", 58, 60)

    def test_set_market_during_settle_raises(self):
        state = _make_state(phase=Phase.SETTLE)
        with self.assertRaises(TradingError):
            set_market(state, "You", 58, 60)

    def test_set_market_all_phases_except_settle(self):
        for phase in [Phase.OPEN, Phase.REVEAL_1, Phase.REVEAL_2, Phase.REVEAL_3]:
            state = _make_state(phase=phase)
            q = set_market(state, "You", 60, 62)
            self.assertEqual(q.bid, 60)


class TestExecuteTrade(unittest.TestCase):

    def _state_with_alice_quote(self, bid=60, ask=62, phase=Phase.OPEN):
        state = _make_state(phase=phase)
        state.markets["Alice"] = Quote(bid=bid, ask=ask)
        return state

    # ── Buy ───────────────────────────────────────────────────────────────

    def test_buy_hits_ask(self):
        state = self._state_with_alice_quote(bid=60, ask=62)
        trade = execute_trade(state, "You", "Alice", "buy")
        self.assertEqual(trade.price, 62)
        self.assertEqual(trade.buyer, "You")
        self.assertEqual(trade.seller, "Alice")

    def test_buy_recorded_in_state(self):
        state = self._state_with_alice_quote()
        execute_trade(state, "You", "Alice", "buy")
        self.assertEqual(len(state.trades), 1)

    # ── Sell ──────────────────────────────────────────────────────────────

    def test_sell_hits_bid(self):
        state = self._state_with_alice_quote(bid=60, ask=62)
        trade = execute_trade(state, "You", "Alice", "sell")
        self.assertEqual(trade.price, 60)
        self.assertEqual(trade.buyer, "Alice")
        self.assertEqual(trade.seller, "You")

    def test_sell_recorded_in_state(self):
        state = self._state_with_alice_quote()
        execute_trade(state, "You", "Alice", "sell")
        self.assertEqual(len(state.trades), 1)

    # ── Error cases ───────────────────────────────────────────────────────

    def test_self_trade_raises(self):
        state = _make_state()
        state.markets["You"] = Quote(bid=60, ask=62)
        with self.assertRaises(TradingError):
            execute_trade(state, "You", "You", "buy")

    def test_no_quote_raises(self):
        state = _make_state()
        # Alice has no quote
        with self.assertRaises(TradingError):
            execute_trade(state, "You", "Alice", "buy")

    def test_invalid_direction_raises(self):
        state = self._state_with_alice_quote()
        with self.assertRaises(TradingError):
            execute_trade(state, "You", "Alice", "hold")

    def test_trade_during_settle_raises(self):
        state = self._state_with_alice_quote(phase=Phase.SETTLE)
        with self.assertRaises(TradingError):
            execute_trade(state, "You", "Alice", "buy")

    # ── Phase recorded correctly ──────────────────────────────────────────

    def test_trade_records_current_phase(self):
        state = self._state_with_alice_quote(phase=Phase.REVEAL_2)
        trade = execute_trade(state, "You", "Alice", "buy")
        self.assertEqual(trade.phase, Phase.REVEAL_2)

    # ── Multiple trades ───────────────────────────────────────────────────

    def test_multiple_trades_accumulate(self):
        state = _make_state()
        state.markets["Alice"] = Quote(bid=60, ask=62)
        state.markets["Bob"]   = Quote(bid=58, ask=60)
        execute_trade(state, "You", "Alice", "buy")
        execute_trade(state, "You", "Bob",   "sell")
        self.assertEqual(len(state.trades), 2)

    def test_bot_can_trade_against_human_quote(self):
        state = _make_state()
        state.markets["You"] = Quote(bid=60, ask=62)
        trade = execute_trade(state, "Alice", "You", "buy")
        self.assertEqual(trade.buyer, "Alice")
        self.assertEqual(trade.seller, "You")
        self.assertEqual(trade.price, 62)


class TestGetQuote(unittest.TestCase):

    def test_get_quote_returns_none_before_posting(self):
        state = _make_state()
        self.assertIsNone(get_quote(state, "You"))

    def test_get_quote_returns_quote_after_posting(self):
        state = _make_state()
        set_market(state, "You", 60, 62)
        q = get_quote(state, "You")
        self.assertIsNotNone(q)
        self.assertEqual(q.bid, 60)


class TestTradeSummary(unittest.TestCase):

    def test_no_trades(self):
        state = _make_state()
        n_buys, n_sells, avg_buy, avg_sell = trade_summary(state, "You")
        self.assertEqual(n_buys, 0)
        self.assertEqual(n_sells, 0)
        self.assertEqual(avg_buy, 0.0)
        self.assertEqual(avg_sell, 0.0)

    def test_one_buy(self):
        state = _make_state()
        state.markets["Alice"] = Quote(bid=60, ask=62)
        execute_trade(state, "You", "Alice", "buy")
        n_buys, n_sells, avg_buy, avg_sell = trade_summary(state, "You")
        self.assertEqual(n_buys, 1)
        self.assertEqual(avg_buy, 62.0)

    def test_one_sell(self):
        state = _make_state()
        state.markets["Alice"] = Quote(bid=60, ask=62)
        execute_trade(state, "You", "Alice", "sell")
        n_buys, n_sells, avg_buy, avg_sell = trade_summary(state, "You")
        self.assertEqual(n_sells, 1)
        self.assertEqual(avg_sell, 60.0)

    def test_average_buy_price(self):
        state = _make_state()
        state.markets["Alice"] = Quote(bid=60, ask=62)
        state.markets["Bob"]   = Quote(bid=64, ask=66)
        execute_trade(state, "You", "Alice", "buy")  # price 62
        execute_trade(state, "You", "Bob",   "buy")  # price 66
        n_buys, _, avg_buy, _ = trade_summary(state, "You")
        self.assertEqual(n_buys, 2)
        self.assertAlmostEqual(avg_buy, 64.0)

    def test_mixed_buys_and_sells(self):
        state = _make_state()
        state.markets["Alice"] = Quote(bid=60, ask=62)
        state.markets["Bob"]   = Quote(bid=68, ask=70)
        execute_trade(state, "You", "Alice", "buy")   # buy at 62
        execute_trade(state, "You", "Bob",   "sell")  # sell at 68
        n_buys, n_sells, avg_buy, avg_sell = trade_summary(state, "You")
        self.assertEqual(n_buys, 1)
        self.assertEqual(n_sells, 1)
        self.assertEqual(avg_buy, 62.0)
        self.assertEqual(avg_sell, 68.0)


# ── V2: execute_trade_direct ──────────────────────────────────────────────────

class TestExecuteTradeDirect(unittest.TestCase):
    """Tests for the V2 direct trade execution (no standing markets needed)."""

    def test_basic_trade(self):
        state = _make_state()
        trade = execute_trade_direct(state, "You", "Alice", 60)
        self.assertEqual(trade.buyer, "You")
        self.assertEqual(trade.seller, "Alice")
        self.assertEqual(trade.price, 60)
        self.assertEqual(trade.phase, Phase.OPEN)

    def test_trade_recorded_in_state(self):
        state = _make_state()
        execute_trade_direct(state, "You", "Alice", 60)
        self.assertEqual(len(state.trades), 1)

    def test_multiple_trades_accumulate(self):
        state = _make_state()
        execute_trade_direct(state, "You", "Alice", 60)
        execute_trade_direct(state, "Bob", "Carol", 65)
        self.assertEqual(len(state.trades), 2)

    def test_self_trade_raises(self):
        state = _make_state()
        with self.assertRaises(TradingError):
            execute_trade_direct(state, "You", "You", 60)

    def test_settle_phase_raises(self):
        state = _make_state(phase=Phase.SETTLE)
        with self.assertRaises(TradingError):
            execute_trade_direct(state, "You", "Alice", 60)

    def test_unknown_buyer_raises(self):
        state = _make_state()
        with self.assertRaises(TradingError):
            execute_trade_direct(state, "Ghost", "Alice", 60)

    def test_unknown_seller_raises(self):
        state = _make_state()
        with self.assertRaises(TradingError):
            execute_trade_direct(state, "You", "Ghost", 60)

    def test_records_correct_phase(self):
        state = _make_state(phase=Phase.REVEAL_2)
        trade = execute_trade_direct(state, "You", "Alice", 58)
        self.assertEqual(trade.phase, Phase.REVEAL_2)

    def test_pnl_works_on_direct_trade(self):
        state = _make_state()  # final_total = 79
        trade = execute_trade_direct(state, "You", "Alice", 60)
        T = state.final_total()  # 79
        self.assertEqual(trade.pnl_for("You", T), T - 60)
        self.assertEqual(trade.pnl_for("Alice", T), 60 - T)


if __name__ == "__main__":
    unittest.main()
