"""
test_bot_simple.py — Tests for bots/simple.py (SimpleBot) V2

Covers:
  - Quote spread is always exactly 2
  - Quote centres near true EV for various card values
  - Quote higher for high card, lower for low card
  - decide_on_quote: buys when ask below EV, sells when bid above EV, walks otherwise
  - decide_action: returns a valid target or None
  - Easy mode: no adaptation (adaptation_threshold=None)
  - Medium mode: adaptation kicks in after threshold, shifts quotes against direction
"""

import random
import unittest

from bots.simple import SimpleBot, EDGE_THRESHOLD
from game.deck import expected_total
from game.state import GameState, Phase, Quote


def _make_state(bot_card: int, other_cards=None, central_cards=None,
                revealed_central=None, phase=Phase.OPEN):
    player_ids = ["Alice", "You", "Bob", "Carol", "Dave"]
    other_cards = other_cards or [7, 5, 6, 8]
    private_cards = dict(zip(player_ids, [bot_card] + other_cards))
    central_cards = central_cards or [9, 10, 11]
    state = GameState(
        player_ids=player_ids,
        private_cards=private_cards,
        central_cards=central_cards,
        trades=[],
        markets={pid: None for pid in player_ids},
        phase=phase,
    )
    state.revealed_central = list(revealed_central or [])
    return state


class TestSimpleBotQuote(unittest.TestCase):

    def test_spread_always_2(self):
        bot = SimpleBot("Alice", rng=random.Random(0))
        for card in [-10, 1, 7, 15, 20]:
            state = _make_state(bot_card=card)
            q = bot.get_quote(state)
            self.assertEqual(q.ask - q.bid, 2,
                msg=f"Spread != 2 for card={card}")

    def test_spread_always_2_with_requester(self):
        bot = SimpleBot("Alice", rng=random.Random(0))
        for card in [-10, 1, 7, 15, 20]:
            state = _make_state(bot_card=card)
            q = bot.get_quote(state, requester="You")
            self.assertEqual(q.ask - q.bid, 2,
                msg=f"Spread != 2 with requester for card={card}")

    def test_quote_near_ev_for_average_card(self):
        bot = SimpleBot("Alice", rng=random.Random(1))
        state = _make_state(bot_card=7)
        q = bot.get_quote(state)
        mid = (q.bid + q.ask) / 2
        ev = expected_total([7], 7)
        self.assertAlmostEqual(mid, ev, delta=2.0)

    def test_quote_higher_for_high_card(self):
        bot = SimpleBot("Alice", rng=random.Random(2))
        state_high = _make_state(bot_card=20)
        state_low  = _make_state(bot_card=1)
        q_high = bot.get_quote(state_high)
        q_low  = bot.get_quote(state_low)
        self.assertGreater(q_high.bid, q_low.bid)

    def test_quote_lower_for_low_card(self):
        bot = SimpleBot("Alice", rng=random.Random(3))
        state_neg = _make_state(bot_card=-10)
        state_pos = _make_state(bot_card=15)
        q_neg = bot.get_quote(state_neg)
        q_pos = bot.get_quote(state_pos)
        self.assertLess(q_neg.bid, q_pos.bid)

    def test_quote_near_book_value_for_20(self):
        bot = SimpleBot("Alice", rng=random.Random(0))
        state = _make_state(bot_card=20)
        q = bot.get_quote(state)
        mid = (q.bid + q.ask) / 2
        self.assertAlmostEqual(mid, 68.0, delta=3.0)

    def test_quote_near_book_value_for_minus10(self):
        bot = SimpleBot("Alice", rng=random.Random(0))
        state = _make_state(bot_card=-10)
        q = bot.get_quote(state)
        mid = (q.bid + q.ask) / 2
        self.assertAlmostEqual(mid, 51.2, delta=3.0)

    def test_quote_updates_after_central_reveal(self):
        bot = SimpleBot("Alice", rng=random.Random(0))
        state_before = _make_state(bot_card=7, revealed_central=[])
        state_after  = _make_state(bot_card=7, central_cards=[15, 9, 8],
                                   revealed_central=[15])
        q_before = bot.get_quote(state_before)
        q_after  = bot.get_quote(state_after)
        self.assertGreater(q_after.bid, q_before.bid)


class TestSimpleBotDecideOnQuote(unittest.TestCase):

    def test_buys_when_ask_well_below_ev(self):
        # Bot has card=20, EV≈68. Quote ask=55 — huge buy edge.
        bot = SimpleBot("Alice", rng=random.Random(0))
        state = _make_state(bot_card=20)
        decision = bot.decide_on_quote(state, "You", Quote(bid=53, ask=55))
        self.assertEqual(decision, "buy")

    def test_sells_when_bid_well_above_ev(self):
        # Bot has card=-10, EV≈51. Quote bid=70 — huge sell edge.
        bot = SimpleBot("Alice", rng=random.Random(0))
        state = _make_state(bot_card=-10)
        decision = bot.decide_on_quote(state, "You", Quote(bid=70, ask=72))
        self.assertEqual(decision, "sell")

    def test_walks_when_no_edge(self):
        # Bot has card=7, EV≈61. Quote 60-62 — right at EV, no edge.
        bot = SimpleBot("Alice", rng=random.Random(0))
        state = _make_state(bot_card=7)
        decision = bot.decide_on_quote(state, "You", Quote(bid=60, ask=62))
        self.assertIsNone(decision)

    def test_prefers_buy_when_both_edges_exist(self):
        # Quote bid=80, ask=50 (unrealistic but tests preference)
        # EV≈61 → buy_edge = 61-50=11, sell_edge = 80-61=19
        # sell_edge > buy_edge → should sell
        bot = SimpleBot("Alice", rng=random.Random(0))
        state = _make_state(bot_card=7)
        decision = bot.decide_on_quote(state, "You", Quote(bid=80, ask=82))
        self.assertEqual(decision, "sell")


class TestSimpleBotDecideAction(unittest.TestCase):

    def test_returns_valid_player_id(self):
        bot = SimpleBot("Alice", rng=random.Random(42))
        state = _make_state(bot_card=7)
        target = bot.decide_action(state)
        self.assertIsNotNone(target)
        self.assertIn(target, state.player_ids)
        self.assertNotEqual(target, "Alice")  # never self

    def test_returns_none_during_settle(self):
        bot = SimpleBot("Alice", rng=random.Random(42))
        state = _make_state(bot_card=7, phase=Phase.SETTLE)
        target = bot.decide_action(state)
        self.assertIsNone(target)

    def test_never_returns_self(self):
        for seed in range(100):
            bot = SimpleBot("Alice", rng=random.Random(seed))
            state = _make_state(bot_card=7)
            target = bot.decide_action(state)
            if target is not None:
                self.assertNotEqual(target, "Alice")


class TestSimpleBotEasyMode(unittest.TestCase):
    """Easy mode: adaptation_threshold=None → quotes never shift."""

    def test_no_adaptation_even_after_many_sells(self):
        bot = SimpleBot("Alice", rng=random.Random(0), adaptation_threshold=None)
        state = _make_state(bot_card=7)

        # Record 10 sells by "You"
        for _ in range(10):
            bot.record_trade("Alice", "You", "You")  # You is seller

        # Baseline: fresh bot with same seed, same player_id, no history
        q_before = SimpleBot("Alice", rng=random.Random(0)).get_quote(state)
        q_after = bot.get_quote(state, requester="You")

        # Quotes should be identical (no adaptation)
        self.assertEqual(q_before.bid, q_after.bid)

    def test_no_adaptation_after_many_buys(self):
        bot = SimpleBot("Alice", rng=random.Random(0), adaptation_threshold=None)
        state = _make_state(bot_card=7)

        for _ in range(10):
            bot.record_trade("You", "Alice", "You")  # You is buyer

        q_before = SimpleBot("Alice", rng=random.Random(0)).get_quote(state)
        q_after = bot.get_quote(state, requester="You")
        self.assertEqual(q_before.bid, q_after.bid)


class TestSimpleBotMediumMode(unittest.TestCase):
    """Medium mode: adaptation_threshold=5 → shifts quotes after 5+ same-direction."""

    def test_no_adaptation_below_threshold(self):
        bot = SimpleBot("Alice", rng=random.Random(0), adaptation_threshold=5)
        state = _make_state(bot_card=7)

        # 4 sells (below threshold of 5)
        for _ in range(4):
            bot.record_trade("Alice", "You", "You")

        q_no_adapt = SimpleBot("Alice", rng=random.Random(0)).get_quote(state)
        q = bot.get_quote(state, requester="You")
        self.assertEqual(q.bid, q_no_adapt.bid)

    def test_adaptation_kicks_in_at_threshold(self):
        bot = SimpleBot("Alice", rng=random.Random(0), adaptation_threshold=5)
        state = _make_state(bot_card=7)

        # Record 6 consecutive sells by "You" → streak = 6, direction = bearish
        for _ in range(6):
            bot.record_trade("Alice", "You", "You")

        q_baseline = SimpleBot("Alice", rng=random.Random(0)).get_quote(state)
        q_adapted = bot.get_quote(state, requester="You")

        # Bearish → quotes should shift DOWN
        self.assertLess(q_adapted.bid, q_baseline.bid,
            msg="After 6 sells, quotes should shift down (bearish adaptation)")

    def test_adaptation_shifts_up_for_bullish(self):
        bot = SimpleBot("Alice", rng=random.Random(0), adaptation_threshold=5)
        state = _make_state(bot_card=7)

        # Record 6 consecutive buys by "You" → bullish
        for _ in range(6):
            bot.record_trade("You", "Alice", "You")

        q_baseline = SimpleBot("Alice", rng=random.Random(0)).get_quote(state)
        q_adapted = bot.get_quote(state, requester="You")

        # Bullish → quotes should shift UP
        self.assertGreater(q_adapted.bid, q_baseline.bid,
            msg="After 6 buys, quotes should shift up (bullish adaptation)")

    def test_no_adaptation_when_requester_is_none(self):
        """Adaptation only applies when we know who's asking."""
        bot = SimpleBot("Alice", rng=random.Random(0), adaptation_threshold=5)
        state = _make_state(bot_card=7)

        for _ in range(10):
            bot.record_trade("Alice", "You", "You")

        q_no_requester = bot.get_quote(state, requester=None)
        q_baseline = SimpleBot("Alice", rng=random.Random(0)).get_quote(state)
        self.assertEqual(q_no_requester.bid, q_baseline.bid)

    def test_adaptation_spread_still_2(self):
        """Even after heavy adaptation, spread must be exactly 2."""
        bot = SimpleBot("Alice", rng=random.Random(0), adaptation_threshold=5)
        state = _make_state(bot_card=7)

        for _ in range(20):
            bot.record_trade("Alice", "You", "You")

        q = bot.get_quote(state, requester="You")
        self.assertEqual(q.ask - q.bid, 2)


if __name__ == "__main__":
    unittest.main()
