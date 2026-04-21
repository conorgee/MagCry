"""
test_scoring.py — Tests for game/scoring.py

Covers:
  - settle(): zero trades, single buy, single sell, multiple trades
  - THE GARY ARBITRAGE: buy at 52 + sell at 67 = +15 regardless of T
  - Zero-sum property across all players
  - leaderboard(): sorted order, negative scores
  - trade_breakdown(): filters correctly per player
"""

import unittest

from game.state import GameState, Phase, Quote, Trade
from game.scoring import leaderboard, settle, trade_breakdown


def _make_state(trades=None, private_cards=None, central_cards=None):
    player_ids = ["You", "Alice", "Bob", "Carol", "Dave"]
    private_cards = private_cards or {
        "You": 7, "Alice": 10, "Bob": 5, "Carol": 6, "Dave": 15
    }
    central_cards = central_cards or [14, 13, 9]
    state = GameState(
        player_ids=player_ids,
        private_cards=private_cards,
        central_cards=central_cards,
        trades=list(trades or []),
        markets={pid: None for pid in player_ids},
        phase=Phase.SETTLE,
    )
    state.revealed_central = list(central_cards)
    return state


def _t(buyer, seller, price):
    return Trade(buyer=buyer, seller=seller, price=price, phase=Phase.OPEN)


class TestSettle(unittest.TestCase):

    def test_no_trades_all_zero(self):
        state = _make_state()
        scores = settle(state)
        for pid in state.player_ids:
            self.assertEqual(scores[pid], 0)

    def test_single_buy_correct_pnl(self):
        # You buy at 60, T = 79 (7+10+5+6+15+14+13+9)
        state = _make_state(trades=[_t("You", "Alice", 60)])
        scores = settle(state)
        T = state.final_total()   # 79
        self.assertEqual(scores["You"],   T - 60)
        self.assertEqual(scores["Alice"], 60 - T)

    def test_single_sell_correct_pnl(self):
        # You sell at 70 → Alice buys at 70, T = 79
        state = _make_state(trades=[_t("Alice", "You", 70)])
        scores = settle(state)
        T = state.final_total()   # 79
        self.assertEqual(scores["You"],   70 - T)   # seller
        self.assertEqual(scores["Alice"], T - 70)   # buyer

    def test_buyer_profits_when_total_above_price(self):
        state = _make_state(trades=[_t("You", "Alice", 50)])
        scores = settle(state)
        self.assertGreater(scores["You"], 0)

    def test_seller_profits_when_total_below_price(self):
        # You sell at 100 (well above T=79)
        state = _make_state(trades=[_t("Alice", "You", 100)])
        scores = settle(state)
        self.assertGreater(scores["You"], 0)

    def test_all_players_in_scores(self):
        state = _make_state()
        scores = settle(state)
        for pid in state.player_ids:
            self.assertIn(pid, scores)

    def test_multiple_trades_accumulated_correctly(self):
        trades = [
            _t("You", "Alice", 60),   # You buy at 60
            _t("Bob",  "You",  70),   # You sell at 70
        ]
        state = _make_state(trades=trades)
        T = state.final_total()  # 79
        scores = settle(state)
        expected_you = (T - 60) + (70 - T)  # +19 - 9 = +10
        self.assertEqual(scores["You"], expected_you)


class TestGaryArbitrage(unittest.TestCase):
    """
    The centrepiece regression test.

    From the book:
      Player A quotes 50-52  (holds -10, EV ≈ 51)
      Player B quotes 67-69  (holds 20,  EV ≈ 68)

    Gary:
      Buys  at 52 from player A
      Sells at 67 to   player B

    Profit = (T - 52) + (67 - T) = 15  regardless of T.
    This is a risk-free arbitrage.
    """

    def test_arbitrage_profit_is_15_for_all_totals(self):
        for T in range(-10, 131):
            buy_pnl  = T - 52
            sell_pnl = 67 - T
            self.assertEqual(buy_pnl + sell_pnl, 15,
                msg=f"Arbitrage profit should be 15 for T={T}")

    def test_gary_arbitrage_via_settle(self):
        # Build a state where Gary (You) buys at 52 from Alice and sells at 67 to Bob
        # Use private + central cards that sum to a known total
        private = {"You": -10, "Alice": 20, "Bob": 8, "Carol": 7, "Dave": 6}
        central = [5, 4, 3]
        # T = -10+20+8+7+6 + 5+4+3 = 43
        trades = [
            _t("You",  "Alice", 52),   # You buy at 52
            _t("Bob",  "You",   67),   # You sell at 67
        ]
        state = _make_state(trades=trades, private_cards=private, central_cards=central)
        T = state.final_total()
        scores = settle(state)
        self.assertEqual(scores["You"], 15,
            msg=f"Gary's arbitrage should profit exactly 15 (T={T})")

    def test_arbitrage_is_risk_free_across_multiple_totals(self):
        """
        Vary the central cards so T changes; Gary's net should always be 15.
        """
        central_scenarios = [
            [1, 2, 3],    # low total
            [10, 11, 12], # mid total
            [13, 14, 15], # high total
            [20, 9, 8],   # with the 20
        ]
        for central in central_scenarios:
            private = {"You": -10, "Alice": 5, "Bob": 6, "Carol": 7, "Dave": 8}
            trades = [
                _t("You", "Alice", 52),
                _t("Bob", "You",   67),
            ]
            state = _make_state(trades=trades, private_cards=private, central_cards=central)
            scores = settle(state)
            self.assertEqual(scores["You"], 15,
                msg=f"Arbitrage failed for central={central}, T={state.final_total()}")


class TestZeroSum(unittest.TestCase):

    def test_single_trade_zero_sum(self):
        state = _make_state(trades=[_t("You", "Alice", 65)])
        scores = settle(state)
        self.assertEqual(sum(scores.values()), 0)

    def test_multiple_trades_zero_sum(self):
        trades = [
            _t("You",   "Alice", 58),
            _t("Bob",   "Carol", 65),
            _t("Dave",  "You",   72),
            _t("Alice", "Bob",   61),
        ]
        state = _make_state(trades=trades)
        scores = settle(state)
        self.assertEqual(sum(scores.values()), 0)

    def test_many_trades_zero_sum(self):
        # 10 random-ish trades
        players = ["You", "Alice", "Bob", "Carol", "Dave"]
        pairs = [
            ("You", "Alice", 60), ("Bob", "Carol", 65), ("Dave", "You", 70),
            ("Alice", "Bob", 55), ("Carol", "Dave", 68), ("You", "Bob", 62),
            ("Alice", "Dave", 71), ("Carol", "You", 59), ("Bob", "Dave", 66),
            ("Dave", "Alice", 63),
        ]
        trades = [_t(b, s, p) for b, s, p in pairs]
        state = _make_state(trades=trades)
        scores = settle(state)
        self.assertEqual(sum(scores.values()), 0)


class TestLeaderboard(unittest.TestCase):

    def test_sorted_highest_first(self):
        scores = {"You": 10, "Alice": -5, "Bob": 25, "Carol": 0, "Dave": -15}
        board = leaderboard(scores)
        self.assertEqual(board[0][0], "Bob")
        self.assertEqual(board[0][1], 25)
        self.assertEqual(board[-1][0], "Dave")

    def test_all_zero(self):
        scores = {"You": 0, "Alice": 0, "Bob": 0}
        board = leaderboard(scores)
        self.assertEqual(len(board), 3)
        self.assertTrue(all(s == 0 for _, s in board))

    def test_all_negative(self):
        scores = {"You": -10, "Alice": -5, "Bob": -20}
        board = leaderboard(scores)
        self.assertEqual(board[0][0], "Alice")  # least negative = winner

    def test_single_player(self):
        scores = {"You": 42}
        board = leaderboard(scores)
        self.assertEqual(board[0], ("You", 42))


class TestTradeBreakdown(unittest.TestCase):

    def test_filters_to_player_trades_only(self):
        trades = [
            _t("You",   "Alice", 60),
            _t("Bob",   "Carol", 65),   # doesn't involve You
            _t("Dave",  "You",   70),
        ]
        state = _make_state(trades=trades)
        breakdown = trade_breakdown(state, "You")
        self.assertEqual(len(breakdown), 2)

    def test_uninvolved_player_gets_empty_breakdown(self):
        trades = [_t("Alice", "Bob", 60)]
        state = _make_state(trades=trades)
        breakdown = trade_breakdown(state, "Carol")
        self.assertEqual(len(breakdown), 0)

    def test_pnl_in_breakdown_is_correct(self):
        # You buy at 60, T = 79 → pnl = +19
        trades = [_t("You", "Alice", 60)]
        state = _make_state(trades=trades)
        breakdown = trade_breakdown(state, "You")
        self.assertEqual(len(breakdown), 1)
        trade, pnl = breakdown[0]
        self.assertEqual(pnl, state.final_total() - 60)

    def test_breakdown_includes_both_buy_and_sell(self):
        trades = [
            _t("You",   "Alice", 60),  # You buy
            _t("Bob",   "You",   70),  # You sell
        ]
        state = _make_state(trades=trades)
        breakdown = trade_breakdown(state, "You")
        self.assertEqual(len(breakdown), 2)


if __name__ == "__main__":
    unittest.main()
