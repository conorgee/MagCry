"""
test_state.py — Tests for game/state.py

Covers:
  - Quote validation and spread enforcement
  - Trade.pnl_for() for buyer, seller, uninvolved
  - GameState.advance_phase() ordering and central card reveals
  - GameState.final_total()
  - GameState.known_cards_for() and unknown_count_for()
  - GameState.scoreboard() across multiple trades
"""

import unittest

from game.state import (
    Difficulty,
    GameState,
    Phase,
    PHASE_ORDER,
    Quote,
    Trade,
)


def _make_state(private_cards=None, central_cards=None, trades=None, phase=Phase.OPEN):
    """Helper to build a minimal GameState for testing."""
    player_ids = ["You", "Alice", "Bob", "Carol", "Dave"]
    private_cards = private_cards or {
        "You": 7, "Alice": 10, "Bob": 5, "Carol": 6, "Dave": 15
    }
    central_cards = central_cards or [14, 13, 9]
    return GameState(
        player_ids=player_ids,
        private_cards=private_cards,
        central_cards=central_cards,
        trades=trades or [],
        markets={pid: None for pid in player_ids},
        phase=phase,
    )


class TestQuote(unittest.TestCase):

    def test_valid_quote(self):
        q = Quote(bid=60, ask=62)
        self.assertEqual(q.bid, 60)
        self.assertEqual(q.ask, 62)

    def test_invalid_spread_raises(self):
        with self.assertRaises(ValueError):
            Quote(bid=60, ask=63)

    def test_invalid_spread_too_small_raises(self):
        with self.assertRaises(ValueError):
            Quote(bid=60, ask=61)

    def test_spread_of_zero_raises(self):
        with self.assertRaises(ValueError):
            Quote(bid=60, ask=60)

    def test_from_mid_even(self):
        q = Quote.from_mid(62)
        self.assertEqual(q.bid, 61)
        self.assertEqual(q.ask, 63)

    def test_from_mid_odd(self):
        q = Quote.from_mid(61)
        self.assertEqual(q.bid, 60)
        self.assertEqual(q.ask, 62)

    def test_str_representation(self):
        q = Quote(bid=58, ask=60)
        self.assertIn("58", str(q))
        self.assertIn("60", str(q))

    def test_negative_bid_valid(self):
        # Edge case: very low market
        q = Quote(bid=-2, ask=0)
        self.assertEqual(q.bid, -2)
        self.assertEqual(q.ask, 0)


class TestTradePnl(unittest.TestCase):

    def _make_trade(self, buyer, seller, price):
        return Trade(buyer=buyer, seller=seller, price=price, phase=Phase.OPEN)

    def test_buyer_pnl_positive(self):
        # Buy at 52, final total 67 → profit 15
        trade = self._make_trade("Gary", "Alice", 52)
        self.assertEqual(trade.pnl_for("Gary", 67), 15)

    def test_buyer_pnl_negative(self):
        # Buy at 70, final total 60 → loss -10
        trade = self._make_trade("Gary", "Alice", 70)
        self.assertEqual(trade.pnl_for("Gary", 60), -10)

    def test_seller_pnl_positive(self):
        # Sell at 67, final total 52 → profit 15
        trade = self._make_trade("Alice", "Gary", 67)
        self.assertEqual(trade.pnl_for("Gary", 52), 15)

    def test_seller_pnl_negative(self):
        # Sell at 50, final total 65 → loss -15
        trade = self._make_trade("Alice", "Gary", 50)
        self.assertEqual(trade.pnl_for("Gary", 65), -15)

    def test_uninvolved_player_pnl_is_zero(self):
        trade = self._make_trade("Alice", "Bob", 60)
        self.assertEqual(trade.pnl_for("Carol", 80), 0)
        self.assertEqual(trade.pnl_for("Dave", 30), 0)

    def test_pnl_at_breakeven(self):
        # Buy at 60, total = 60 → 0
        trade = self._make_trade("Gary", "Alice", 60)
        self.assertEqual(trade.pnl_for("Gary", 60), 0)

    def test_buyer_and_seller_pnl_sum_to_zero(self):
        # Trades are zero-sum between two parties
        trade = self._make_trade("Gary", "Alice", 65)
        T = 72
        self.assertEqual(trade.pnl_for("Gary", T) + trade.pnl_for("Alice", T), 0)


class TestAdvancePhase(unittest.TestCase):

    def test_phase_order_open_to_reveal1(self):
        state = _make_state(phase=Phase.OPEN)
        state.advance_phase()
        self.assertEqual(state.phase, Phase.REVEAL_1)

    def test_phase_order_full_sequence(self):
        state = _make_state(phase=Phase.OPEN)
        expected = [Phase.REVEAL_1, Phase.REVEAL_2, Phase.REVEAL_3, Phase.SETTLE]
        for expected_phase in expected:
            state.advance_phase()
            self.assertEqual(state.phase, expected_phase)

    def test_reveal_1_exposes_first_central_card(self):
        state = _make_state(central_cards=[14, 13, 9], phase=Phase.OPEN)
        state.advance_phase()  # → REVEAL_1
        self.assertEqual(state.revealed_central, [14])

    def test_reveal_2_exposes_second_central_card(self):
        state = _make_state(central_cards=[14, 13, 9], phase=Phase.OPEN)
        state.advance_phase()  # → REVEAL_1
        state.advance_phase()  # → REVEAL_2
        self.assertEqual(state.revealed_central, [14, 13])

    def test_reveal_3_exposes_all_central_cards(self):
        state = _make_state(central_cards=[14, 13, 9], phase=Phase.OPEN)
        for _ in range(3):
            state.advance_phase()
        self.assertEqual(state.revealed_central, [14, 13, 9])

    def test_advance_resets_turn_counter(self):
        state = _make_state()
        state.turn = 5
        state.advance_phase()
        self.assertEqual(state.turn, 0)

    def test_phase_order_constant_is_correct(self):
        self.assertEqual(PHASE_ORDER[0], Phase.OPEN)
        self.assertEqual(PHASE_ORDER[-1], Phase.SETTLE)
        self.assertEqual(len(PHASE_ORDER), 5)


class TestFinalTotal(unittest.TestCase):

    def test_final_total_sums_all_8_cards(self):
        state = _make_state(
            private_cards={"You": 7, "Alice": 10, "Bob": 5, "Carol": 6, "Dave": 15},
            central_cards=[14, 13, 9],
        )
        # 7+10+5+6+15 + 14+13+9 = 79
        self.assertEqual(state.final_total(), 79)

    def test_final_total_with_minus_10(self):
        state = _make_state(
            private_cards={"You": -10, "Alice": 10, "Bob": 5, "Carol": 6, "Dave": 15},
            central_cards=[14, 13, 9],
        )
        # -10+10+5+6+15 + 14+13+9 = 62
        self.assertEqual(state.final_total(), 62)


class TestKnownUnknownCards(unittest.TestCase):

    def test_known_cards_no_reveals(self):
        state = _make_state()
        # Only own card known before any reveal
        known = state.known_cards_for("You")
        self.assertEqual(known, [7])

    def test_known_cards_after_reveal(self):
        state = _make_state(central_cards=[14, 13, 9])
        state.revealed_central = [14]
        known = state.known_cards_for("You")
        self.assertIn(7, known)
        self.assertIn(14, known)
        self.assertEqual(len(known), 2)

    def test_unknown_count_at_start(self):
        # 4 other private cards + 3 central = 7 unknown
        state = _make_state()
        self.assertEqual(state.unknown_count_for("You"), 7)

    def test_unknown_count_after_one_reveal(self):
        state = _make_state()
        state.revealed_central = [14]
        self.assertEqual(state.unknown_count_for("You"), 6)

    def test_unknown_count_fully_revealed(self):
        state = _make_state(central_cards=[14, 13, 9])
        state.revealed_central = [14, 13, 9]
        # 4 other private cards still hidden
        self.assertEqual(state.unknown_count_for("You"), 4)


class TestScoreboard(unittest.TestCase):

    def test_scoreboard_no_trades(self):
        state = _make_state()
        scores = state.scoreboard()
        for pid in state.player_ids:
            self.assertEqual(scores[pid], 0)

    def test_scoreboard_single_buy(self):
        # You buy at 60, T=79 → +19
        trade = Trade(buyer="You", seller="Alice", price=60, phase=Phase.OPEN)
        state = _make_state(trades=[trade])
        scores = state.scoreboard()
        self.assertEqual(scores["You"], 79 - 60)
        self.assertEqual(scores["Alice"], 60 - 79)

    def test_scoreboard_multiple_trades(self):
        trades = [
            Trade(buyer="You",   seller="Alice", price=60, phase=Phase.OPEN),
            Trade(buyer="Bob",   seller="You",   price=70, phase=Phase.OPEN),
        ]
        state = _make_state(trades=trades)
        scores = state.scoreboard()
        T = state.final_total()  # 79
        # You: bought at 60 (+19) and sold at 70 (-9) → +10
        self.assertEqual(scores["You"], (T - 60) + (70 - T))

    def test_scoreboard_is_zero_sum(self):
        trades = [
            Trade(buyer="You",   seller="Alice", price=58, phase=Phase.OPEN),
            Trade(buyer="Bob",   seller="Carol", price=65, phase=Phase.OPEN),
            Trade(buyer="Dave",  seller="You",   price=72, phase=Phase.OPEN),
        ]
        state = _make_state(trades=trades)
        scores = state.scoreboard()
        self.assertEqual(sum(scores.values()), 0)


class TestDifficulty(unittest.TestCase):
    """V2: Difficulty enum is available and has correct values."""

    def test_difficulty_values_exist(self):
        self.assertIsNotNone(Difficulty.EASY)
        self.assertIsNotNone(Difficulty.MEDIUM)
        self.assertIsNotNone(Difficulty.HARD)

    def test_difficulty_members_count(self):
        self.assertEqual(len(Difficulty), 3)

    def test_difficulty_ordering(self):
        # EASY < MEDIUM < HARD by auto() value
        self.assertLess(Difficulty.EASY.value, Difficulty.MEDIUM.value)
        self.assertLess(Difficulty.MEDIUM.value, Difficulty.HARD.value)


if __name__ == "__main__":
    unittest.main()
