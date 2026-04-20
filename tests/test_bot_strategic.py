"""
test_bot_strategic.py — Tests for bots/strategic.py (StrategicBot) V2

Covers:
  - Quote spread is always exactly 2
  - Bluffs LOW when holding a HIGH card (to drag market down, then buy)
  - Bluffs HIGH when holding a LOW card (to push market up, then sell)
  - Bluff offset is large early (OPEN), converges to near-zero after 2 reveals
  - decide_on_quote: buys/sells when edge exists, walks otherwise
  - decide_action: returns a valid target, respects MAX_TRADES_PER_TURN
  - Aggressive adaptation after 2-3 same-direction trades
  - on_phase_change() resets trade counter
  - Never self-trades
"""

import random
import unittest

from bots.strategic import (
    StrategicBot,
    BLUFF_OFFSET_EARLY,
    BLUFF_OFFSET_LATE,
    MAX_TRADES_PER_TURN,
    ADAPTATION_THRESHOLD,
)
from game.deck import expected_total, DECK_MEAN
from game.state import GameState, Phase, Quote


def _make_state(bot_card: int, other_cards=None, central_cards=None,
                revealed_central=None, phase=Phase.OPEN):
    player_ids = ["Bob", "You", "Alice", "Carol", "Dave"]
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


class TestStrategicBotQuote(unittest.TestCase):

    def test_spread_always_2(self):
        bot = StrategicBot("Bob", rng=random.Random(0))
        for card in [-10, 1, 7, 15, 20]:
            state = _make_state(bot_card=card)
            q = bot.get_quote(state)
            self.assertEqual(q.ask - q.bid, 2,
                msg=f"Spread != 2 for card={card}")

    def test_spread_always_2_with_requester(self):
        bot = StrategicBot("Bob", rng=random.Random(0))
        for card in [-10, 1, 7, 15, 20]:
            state = _make_state(bot_card=card)
            q = bot.get_quote(state, requester="You")
            self.assertEqual(q.ask - q.bid, 2)

    def test_bluffs_low_when_high_card(self):
        """High card (20) → quote BELOW true EV."""
        bot = StrategicBot("Bob", rng=random.Random(0))
        state = _make_state(bot_card=20, revealed_central=[])
        q = bot.get_quote(state)
        true_ev = expected_total([20], 7)
        bluff_mid = (q.bid + q.ask) / 2
        self.assertLess(bluff_mid, true_ev,
            msg=f"High-card bluff should quote below EV={true_ev:.1f}, got mid={bluff_mid}")

    def test_bluffs_high_when_low_card(self):
        """Low card (-10) → quote ABOVE true EV."""
        bot = StrategicBot("Bob", rng=random.Random(0))
        state = _make_state(bot_card=-10, revealed_central=[])
        q = bot.get_quote(state)
        true_ev = expected_total([-10], 7)
        bluff_mid = (q.bid + q.ask) / 2
        self.assertGreater(bluff_mid, true_ev,
            msg=f"Low-card bluff should quote above EV={true_ev:.1f}, got mid={bluff_mid}")

    def test_bluff_offset_is_large_early(self):
        bot = StrategicBot("Bob", rng=random.Random(0))
        state = _make_state(bot_card=20, revealed_central=[])
        q = bot.get_quote(state)
        true_ev = expected_total([20], 7)
        bluff_mid = (q.bid + q.ask) / 2
        offset = true_ev - bluff_mid
        self.assertGreaterEqual(offset, BLUFF_OFFSET_EARLY - 1.5,
            msg=f"Early bluff offset should be ~{BLUFF_OFFSET_EARLY}, got {offset:.1f}")

    def test_bluff_converges_after_two_reveals(self):
        bot = StrategicBot("Bob", rng=random.Random(0))
        state_early = _make_state(bot_card=20, revealed_central=[])
        state_late  = _make_state(bot_card=20, central_cards=[5, 6, 9],
                                  revealed_central=[5, 6])

        q_early = bot.get_quote(state_early)
        q_late  = bot.get_quote(state_late)

        true_ev_late = expected_total([20, 5, 6], 5)
        mid_early = (q_early.bid + q_early.ask) / 2
        mid_late  = (q_late.bid  + q_late.ask)  / 2

        offset_early = abs(mid_early - expected_total([20], 7))
        offset_late  = abs(mid_late  - true_ev_late)

        self.assertGreater(offset_early, offset_late,
            msg="Bluff offset should shrink as more central cards are revealed")

    def test_late_bluff_offset_is_small(self):
        bot = StrategicBot("Bob", rng=random.Random(0))
        state = _make_state(bot_card=20, central_cards=[5, 6, 9],
                            revealed_central=[5, 6])
        q = bot.get_quote(state)
        true_ev = expected_total([20, 5, 6], 5)
        mid = (q.bid + q.ask) / 2
        offset = abs(mid - true_ev)
        self.assertLessEqual(offset, BLUFF_OFFSET_LATE + 1.5,
            msg=f"Late bluff offset should be ~{BLUFF_OFFSET_LATE}, got {offset:.1f}")


class TestStrategicBotDecideOnQuote(unittest.TestCase):

    def test_buys_when_high_card_and_ask_below_ev(self):
        bot = StrategicBot("Bob", rng=random.Random(0))
        state = _make_state(bot_card=20)
        # EV ≈ 68, ask = 57 → buy edge ≈ 11
        decision = bot.decide_on_quote(state, "You", Quote(bid=55, ask=57))
        self.assertEqual(decision, "buy")

    def test_sells_when_low_card_and_bid_above_ev(self):
        bot = StrategicBot("Bob", rng=random.Random(0))
        state = _make_state(bot_card=-10)
        # EV ≈ 51, bid = 68 → sell edge ≈ 17
        decision = bot.decide_on_quote(state, "You", Quote(bid=68, ask=70))
        self.assertEqual(decision, "sell")

    def test_walks_when_no_edge(self):
        bot = StrategicBot("Bob", rng=random.Random(0))
        state = _make_state(bot_card=20)
        # EV ≈ 68, ask = 72 → buy edge = -4 (negative)
        decision = bot.decide_on_quote(state, "You", Quote(bid=70, ask=72))
        self.assertIsNone(decision)

    def test_opportunistic_sell_even_with_high_card(self):
        """High-card bot should still sell if bid is WAY above EV."""
        bot = StrategicBot("Bob", rng=random.Random(0))
        state = _make_state(bot_card=20)
        # EV ≈ 68, bid = 80 → sell edge = 12. Must exceed 3x threshold.
        decision = bot.decide_on_quote(state, "You", Quote(bid=80, ask=82))
        self.assertEqual(decision, "sell")


class TestStrategicBotDecideAction(unittest.TestCase):

    def test_returns_valid_target(self):
        bot = StrategicBot("Bob", rng=random.Random(42))
        state = _make_state(bot_card=20)
        target = bot.decide_action(state)
        self.assertIsNotNone(target)
        self.assertIn(target, state.player_ids)
        self.assertNotEqual(target, "Bob")

    def test_returns_none_during_settle(self):
        bot = StrategicBot("Bob", rng=random.Random(0))
        state = _make_state(bot_card=20, phase=Phase.SETTLE)
        target = bot.decide_action(state)
        self.assertIsNone(target)

    def test_never_returns_self(self):
        for seed in range(100):
            bot = StrategicBot("Bob", rng=random.Random(seed))
            state = _make_state(bot_card=20)
            target = bot.decide_action(state)
            if target is not None:
                self.assertNotEqual(target, "Bob")

    def test_max_trades_per_turn_respected(self):
        """Bot should stop acting after MAX_TRADES_PER_TURN actions in one phase."""
        bot = StrategicBot("Bob", rng=random.Random(0))
        state = _make_state(bot_card=20)

        actions_taken = 0
        for _ in range(MAX_TRADES_PER_TURN + 5):
            target = bot.decide_action(state)
            if target is None:
                break
            actions_taken += 1

        self.assertLessEqual(actions_taken, MAX_TRADES_PER_TURN)

    def test_phase_change_resets_trade_count(self):
        bot = StrategicBot("Bob", rng=random.Random(0))
        state = _make_state(bot_card=20)

        # Exhaust trade allowance
        for _ in range(MAX_TRADES_PER_TURN + 2):
            bot.decide_action(state)

        target_before_reset = bot.decide_action(state)
        self.assertIsNone(target_before_reset)

        # Reset via phase change
        state.phase = Phase.REVEAL_1
        bot.on_phase_change(state)

        target_after_reset = bot.decide_action(state)
        self.assertIsNotNone(target_after_reset)


class TestStrategicBotAdaptation(unittest.TestCase):

    def test_no_adaptation_below_threshold(self):
        bot = StrategicBot("Bob", rng=random.Random(0))
        state = _make_state(bot_card=7)

        # 1 sell (below threshold of 2)
        bot.record_trade("Bob", "You", "You")

        q_baseline = StrategicBot("Bob", rng=random.Random(0)).get_quote(state)
        q = bot.get_quote(state, requester="You")
        self.assertEqual(q.bid, q_baseline.bid)

    def test_adaptation_kicks_in_aggressively(self):
        bot = StrategicBot("Bob", rng=random.Random(0))
        state = _make_state(bot_card=7)

        # Record 4 consecutive sells by "You" → bearish, streak = 4
        for _ in range(4):
            bot.record_trade("Bob", "You", "You")

        q_baseline = StrategicBot("Bob", rng=random.Random(0)).get_quote(state)
        q_adapted = bot.get_quote(state, requester="You")

        # Bearish → quotes shift DOWN
        self.assertLess(q_adapted.bid, q_baseline.bid,
            msg="Strategic bot should adapt aggressively after sells")

    def test_adaptation_shifts_up_for_bullish(self):
        bot = StrategicBot("Bob", rng=random.Random(0))
        state = _make_state(bot_card=7)

        for _ in range(4):
            bot.record_trade("You", "Bob", "You")

        q_baseline = StrategicBot("Bob", rng=random.Random(0)).get_quote(state)
        q_adapted = bot.get_quote(state, requester="You")

        # Bullish → quotes shift UP
        self.assertGreater(q_adapted.bid, q_baseline.bid,
            msg="Strategic bot should shift quotes up for bullish requester")

    def test_adaptation_spread_still_2(self):
        bot = StrategicBot("Bob", rng=random.Random(0))
        state = _make_state(bot_card=7)

        for _ in range(20):
            bot.record_trade("Bob", "You", "You")

        q = bot.get_quote(state, requester="You")
        self.assertEqual(q.ask - q.bid, 2)


class TestStrategicBotVsSimple(unittest.TestCase):
    """Average over many seeds so bluff direction dominates noise."""

    def _avg_mid(self, bot_cls, card, n=50, **kwargs):
        mids = []
        for seed in range(n):
            bot = bot_cls("Bob", rng=random.Random(seed), **kwargs)
            state = _make_state(bot_card=card, revealed_central=[])
            q = bot.get_quote(state)
            mids.append((q.bid + q.ask) / 2)
        return sum(mids) / len(mids)

    def test_high_card_strategic_quotes_lower_than_simple(self):
        from bots.simple import SimpleBot
        avg_simple    = self._avg_mid(SimpleBot,    card=20)
        avg_strategic = self._avg_mid(StrategicBot, card=20)
        self.assertLess(avg_strategic, avg_simple,
            msg=f"High-card strategic avg mid {avg_strategic:.1f} should be "
                f"below simple avg mid {avg_simple:.1f}")

    def test_low_card_strategic_quotes_higher_than_simple(self):
        from bots.simple import SimpleBot
        avg_simple    = self._avg_mid(SimpleBot,    card=-10)
        avg_strategic = self._avg_mid(StrategicBot, card=-10)
        self.assertGreater(avg_strategic, avg_simple,
            msg=f"Low-card strategic avg mid {avg_strategic:.1f} should be "
                f"above simple avg mid {avg_simple:.1f}")


if __name__ == "__main__":
    unittest.main()
