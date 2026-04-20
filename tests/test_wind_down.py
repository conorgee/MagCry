"""
test_wind_down.py — Tests for the wind-down mechanic and simplified flow in main.py.

When the human types 'next', bots get 1-3 additional rounds of
trading before the card is actually revealed. If a bot asks the
human during wind-down, the human gets a mini trading window.

Covers:
  - run_wind_down returns "next" after completing all rounds
  - run_wind_down returns "quit" if human quits during wind-down
  - Number of wind-down rounds is between WIND_DOWN_RANGE
  - Bots trade with each other during wind-down
  - Bots may ask human for prices during wind-down (WIND_DOWN_ASK_RANGE)
  - If a bot asked during wind-down, human gets mini trading window
  - run_bots_ask_human returns (quit, n_asked) tuple
  - run_bots_ask_human respects custom ask_range param
  - run_phase is linear: Phase A -> Phase B -> wind-down (no turn loop)
  - "pass" command is removed — only "next" exits Phase B
  - run_wind_down_mini_turn: ask/buy/sell or enter to continue
  - Display functions produce output
"""

import random
import unittest
from unittest.mock import patch, MagicMock, call

from game.state import GameState, Phase, Quote, Difficulty
from game.trading import execute_trade_direct
from bots.simple import SimpleBot


# ── Helpers ──────────────────────────────────────────────────────────────────

def _make_state(phase=Phase.OPEN):
    """Create a minimal GameState for testing."""
    player_ids = ["You", "Alice", "Bob", "Carol", "Dave"]
    private_cards = {"You": 5, "Alice": 7, "Bob": 8, "Carol": 6, "Dave": 9}
    central_cards = [10, 11, 12]
    return GameState(
        player_ids=player_ids,
        private_cards=private_cards,
        central_cards=central_cards,
        trades=[],
        markets={pid: None for pid in player_ids},
        phase=phase,
    )


def _make_bots(rng=None):
    """Create 4 simple bots for testing."""
    rng = rng or random.Random(42)
    names = ["Alice", "Bob", "Carol", "Dave"]
    bots = [SimpleBot(n, rng=random.Random(rng.randint(0, 10000))) for n in names]
    return bots


def _make_bot_map(bots):
    return {b.player_id: b for b in bots}


# ── Tests for run_bots_ask_human return type ─────────────────────────────────

class TestBotsAskHumanReturnType(unittest.TestCase):
    """run_bots_ask_human now returns (quit_requested, n_asked) tuple."""

    @patch("main._get_human_quote")
    def test_returns_tuple(self, mock_quote):
        from main import run_bots_ask_human
        mock_quote.return_value = Quote(bid=60, ask=62)
        state = _make_state()
        bots = _make_bots()
        rng = random.Random(42)

        result = run_bots_ask_human(state, bots, rng)
        self.assertIsInstance(result, tuple)
        self.assertEqual(len(result), 2)

    @patch("main._get_human_quote")
    def test_no_askers_returns_false_zero(self, mock_quote):
        """With ask_range=(0, 0), returns (False, 0)."""
        from main import run_bots_ask_human
        state = _make_state()
        bots = _make_bots()
        rng = random.Random(42)

        quit_req, n_asked = run_bots_ask_human(state, bots, rng, ask_range=(0, 0))
        self.assertFalse(quit_req)
        self.assertEqual(n_asked, 0)
        mock_quote.assert_not_called()

    @patch("main._get_human_quote")
    def test_one_asker_returns_count(self, mock_quote):
        """With ask_range=(1, 1), returns n_asked=1."""
        from main import run_bots_ask_human
        mock_quote.return_value = Quote(bid=60, ask=62)
        state = _make_state()
        bots = _make_bots()
        rng = random.Random(42)

        quit_req, n_asked = run_bots_ask_human(state, bots, rng, ask_range=(1, 1))
        self.assertFalse(quit_req)
        self.assertEqual(n_asked, 1)

    @patch("main._get_human_quote")
    def test_quit_returns_true(self, mock_quote):
        """If human quits during quoting, returns (True, ...)."""
        from main import run_bots_ask_human
        mock_quote.return_value = None  # human quit
        state = _make_state()
        bots = _make_bots()
        rng = random.Random(42)

        quit_req, n_asked = run_bots_ask_human(state, bots, rng, ask_range=(1, 1))
        self.assertTrue(quit_req)


# ── Tests for run_bots_ask_human with custom ask_range ───────────────────────

class TestBotsAskHumanRange(unittest.TestCase):
    """Tests that run_bots_ask_human respects the ask_range parameter."""

    @patch("main._get_human_quote")
    def test_default_range_uses_bots_ask_range(self, mock_quote):
        """Without ask_range param, uses BOTS_ASK_RANGE."""
        from main import run_bots_ask_human, BOTS_ASK_RANGE
        state = _make_state()
        bots = _make_bots()
        mock_quote.return_value = Quote(bid=60, ask=62)

        n_askers_seen = set()
        for seed in range(200):
            mock_quote.reset_mock()
            rng = random.Random(seed)
            _, n_asked = run_bots_ask_human(state, bots, rng)
            n_askers_seen.add(n_asked)

        for n in n_askers_seen:
            self.assertGreaterEqual(n, BOTS_ASK_RANGE[0])
            self.assertLessEqual(n, BOTS_ASK_RANGE[1])

    @patch("main._get_human_quote")
    def test_custom_range_zero_to_one(self, mock_quote):
        """With ask_range=(0, 1), at most 1 bot asks."""
        from main import run_bots_ask_human
        state = _make_state()
        bots = _make_bots()
        mock_quote.return_value = Quote(bid=60, ask=62)

        for seed in range(100):
            mock_quote.reset_mock()
            rng = random.Random(seed)
            _, n_asked = run_bots_ask_human(state, bots, rng, ask_range=(0, 1))
            self.assertLessEqual(n_asked, 1)

    @patch("main._get_human_quote")
    def test_custom_range_zero_zero(self, mock_quote):
        """With ask_range=(0, 0), no bots ask."""
        from main import run_bots_ask_human
        state = _make_state()
        bots = _make_bots()
        mock_quote.return_value = Quote(bid=60, ask=62)

        for seed in range(20):
            mock_quote.reset_mock()
            rng = random.Random(seed)
            _, n_asked = run_bots_ask_human(state, bots, rng, ask_range=(0, 0))
            self.assertEqual(n_asked, 0)


# ── Tests for run_wind_down ──────────────────────────────────────────────────

class TestRunWindDown(unittest.TestCase):
    """Tests for the run_wind_down function."""

    @patch("main.run_bot_to_bot")
    @patch("main.run_bots_ask_human", return_value=(False, 0))
    @patch("main.print_wind_down_round")
    @patch("main.print_wind_down_start")
    def test_returns_next_on_completion(self, mock_start, mock_round,
                                        mock_ask, mock_bot2bot):
        """Wind-down returns 'next' when all rounds complete normally."""
        from main import run_wind_down
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)
        rng = random.Random(42)

        result = run_wind_down(state, bots, bot_map, rng)
        self.assertEqual(result, "next")

    @patch("main.run_bot_to_bot")
    @patch("main.run_bots_ask_human", return_value=(False, 0))
    @patch("main.print_wind_down_round")
    @patch("main.print_wind_down_start")
    def test_runs_correct_number_of_rounds(self, mock_start, mock_round,
                                            mock_ask, mock_bot2bot):
        """Wind-down runs 1-3 rounds (per WIND_DOWN_RANGE)."""
        from main import run_wind_down, WIND_DOWN_RANGE
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)

        round_counts = set()
        for seed in range(100):
            mock_round.reset_mock()
            mock_ask.reset_mock()
            mock_bot2bot.reset_mock()
            rng = random.Random(seed)
            run_wind_down(state, bots, bot_map, rng)
            n_rounds = mock_round.call_count
            round_counts.add(n_rounds)
            self.assertGreaterEqual(n_rounds, WIND_DOWN_RANGE[0])
            self.assertLessEqual(n_rounds, WIND_DOWN_RANGE[1])

        self.assertGreater(len(round_counts), 1,
                           "Expected wind-down to vary in round count")

    @patch("main.run_bot_to_bot")
    @patch("main.run_bots_ask_human")
    @patch("main.print_wind_down_round")
    @patch("main.print_wind_down_start")
    def test_returns_quit_if_human_quits(self, mock_start, mock_round,
                                          mock_ask, mock_bot2bot):
        """If human quits during a wind-down quote, returns 'quit'."""
        from main import run_wind_down
        mock_ask.return_value = (True, 0)
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)
        rng = random.Random(42)

        result = run_wind_down(state, bots, bot_map, rng)
        self.assertEqual(result, "quit")

    @patch("main.run_bot_to_bot")
    @patch("main.run_bots_ask_human")
    @patch("main.print_wind_down_round")
    @patch("main.print_wind_down_start")
    def test_quit_stops_remaining_rounds(self, mock_start, mock_round,
                                          mock_ask, mock_bot2bot):
        """If human quits on the first round, remaining rounds are skipped."""
        from main import run_wind_down
        mock_ask.return_value = (True, 0)
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)
        rng = random.Random(0)

        run_wind_down(state, bots, bot_map, rng)
        self.assertEqual(mock_round.call_count, 1)
        mock_bot2bot.assert_not_called()

    @patch("main.run_bot_to_bot")
    @patch("main.run_bots_ask_human", return_value=(False, 0))
    @patch("main.print_wind_down_round")
    @patch("main.print_wind_down_start")
    def test_calls_bot_to_bot_each_round(self, mock_start, mock_round,
                                          mock_ask, mock_bot2bot):
        """Bot-to-bot trading runs once per wind-down round."""
        from main import run_wind_down
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)
        rng = random.Random(42)

        run_wind_down(state, bots, bot_map, rng)
        n_rounds = mock_round.call_count
        self.assertEqual(mock_bot2bot.call_count, n_rounds)

    @patch("main.run_bot_to_bot")
    @patch("main.run_bots_ask_human", return_value=(False, 0))
    @patch("main.print_wind_down_round")
    @patch("main.print_wind_down_start")
    def test_uses_wind_down_ask_range(self, mock_start, mock_round,
                                       mock_ask, mock_bot2bot):
        """run_bots_ask_human is called with WIND_DOWN_ASK_RANGE."""
        from main import run_wind_down, WIND_DOWN_ASK_RANGE
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)
        rng = random.Random(42)

        run_wind_down(state, bots, bot_map, rng)

        for c in mock_ask.call_args_list:
            self.assertEqual(c.kwargs.get("ask_range"), WIND_DOWN_ASK_RANGE)

    @patch("main.run_wind_down_mini_turn", return_value="continue")
    @patch("main.run_bot_to_bot")
    @patch("main.run_bots_ask_human", return_value=(False, 1))
    @patch("main.print_wind_down_round")
    @patch("main.print_wind_down_start")
    def test_mini_turn_triggered_when_bot_asked(self, mock_start, mock_round,
                                                 mock_ask, mock_bot2bot,
                                                 mock_mini):
        """If a bot asked during wind-down, human gets mini trading window."""
        from main import run_wind_down
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)
        rng = random.Random(42)

        run_wind_down(state, bots, bot_map, rng)
        # mini_turn called at least once (for every round where n_asked > 0)
        self.assertGreaterEqual(mock_mini.call_count, 1)

    @patch("main.run_wind_down_mini_turn", return_value="continue")
    @patch("main.run_bot_to_bot")
    @patch("main.run_bots_ask_human", return_value=(False, 0))
    @patch("main.print_wind_down_round")
    @patch("main.print_wind_down_start")
    def test_no_mini_turn_when_no_bot_asked(self, mock_start, mock_round,
                                              mock_ask, mock_bot2bot,
                                              mock_mini):
        """If no bot asked during wind-down, no mini trading window."""
        from main import run_wind_down
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)
        rng = random.Random(42)

        run_wind_down(state, bots, bot_map, rng)
        mock_mini.assert_not_called()

    @patch("main.run_wind_down_mini_turn", return_value="quit")
    @patch("main.run_bot_to_bot")
    @patch("main.run_bots_ask_human", return_value=(False, 1))
    @patch("main.print_wind_down_round")
    @patch("main.print_wind_down_start")
    def test_mini_turn_quit_propagates(self, mock_start, mock_round,
                                        mock_ask, mock_bot2bot, mock_mini):
        """If human quits during mini turn, wind-down returns 'quit'."""
        from main import run_wind_down
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)
        rng = random.Random(42)

        result = run_wind_down(state, bots, bot_map, rng)
        self.assertEqual(result, "quit")

    @patch("main.print_wind_down_start")
    @patch("main.print_wind_down_round")
    @patch("main.run_bots_ask_human", return_value=(False, 0))
    @patch("main.run_bot_to_bot")
    def test_display_functions_called(self, mock_bot2bot, mock_ask,
                                       mock_round, mock_start):
        """Wind-down prints start banner and round headers."""
        from main import run_wind_down
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)
        rng = random.Random(42)

        run_wind_down(state, bots, bot_map, rng)
        mock_start.assert_called_once()
        self.assertGreaterEqual(mock_round.call_count, 1)


# ── Tests for run_wind_down_mini_turn ────────────────────────────────────────

class TestWindDownMiniTurn(unittest.TestCase):
    """Tests for the mini trading window during wind-down."""

    @patch("main.print_wind_down_your_turn")
    @patch("builtins.input", return_value="")
    def test_enter_continues(self, mock_input, mock_prompt):
        """Pressing enter (empty input) returns 'continue'."""
        from main import run_wind_down_mini_turn
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)

        result = run_wind_down_mini_turn(state, bots, bot_map)
        self.assertEqual(result, "continue")

    @patch("main.print_wind_down_your_turn")
    @patch("builtins.input", return_value="next")
    def test_next_continues(self, mock_input, mock_prompt):
        """Typing 'next' returns 'continue'."""
        from main import run_wind_down_mini_turn
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)

        result = run_wind_down_mini_turn(state, bots, bot_map)
        self.assertEqual(result, "continue")

    @patch("main.print_wind_down_your_turn")
    @patch("builtins.input", return_value="done")
    def test_done_continues(self, mock_input, mock_prompt):
        """Typing 'done' returns 'continue'."""
        from main import run_wind_down_mini_turn
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)

        result = run_wind_down_mini_turn(state, bots, bot_map)
        self.assertEqual(result, "continue")

    @patch("main.print_wind_down_your_turn")
    @patch("builtins.input", return_value="quit")
    def test_quit_returns_quit(self, mock_input, mock_prompt):
        """Typing 'quit' returns 'quit'."""
        from main import run_wind_down_mini_turn
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)

        result = run_wind_down_mini_turn(state, bots, bot_map)
        self.assertEqual(result, "quit")

    @patch("main.print_wind_down_your_turn")
    @patch("builtins.input", side_effect=EOFError)
    def test_eof_returns_quit(self, mock_input, mock_prompt):
        """EOFError (ctrl-D) returns 'quit'."""
        from main import run_wind_down_mini_turn
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)

        result = run_wind_down_mini_turn(state, bots, bot_map)
        self.assertEqual(result, "quit")

    @patch("main.print_wind_down_your_turn")
    @patch("builtins.input", side_effect=["ask alice", ""])
    def test_can_ask_then_continue(self, mock_input, mock_prompt):
        """User can ask a bot for a price, then press enter to continue."""
        from main import run_wind_down_mini_turn
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)

        result = run_wind_down_mini_turn(state, bots, bot_map)
        self.assertEqual(result, "continue")

    @patch("main.print_wind_down_your_turn")
    def test_prompt_is_shown(self, mock_prompt):
        """print_wind_down_your_turn is called."""
        from main import run_wind_down_mini_turn
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)

        with patch("builtins.input", return_value=""):
            run_wind_down_mini_turn(state, bots, bot_map)
        mock_prompt.assert_called_once()


# ── Tests for run_phase (simplified, no turn loop) ───────────────────────────

class TestRunPhaseSimplified(unittest.TestCase):
    """run_phase is now linear: Phase A -> Phase B -> wind-down."""

    @patch("main.run_wind_down", return_value="next")
    @patch("main.run_human_turn", return_value="next")
    @patch("main.run_bots_ask_human", return_value=(False, 1))
    def test_next_triggers_wind_down(self, mock_ask, mock_human, mock_wind_down):
        """When human types 'next', run_phase calls run_wind_down."""
        from main import run_phase
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)
        rng = random.Random(42)

        result = run_phase(state, bots, bot_map, rng)
        self.assertEqual(result, "next")
        mock_wind_down.assert_called_once()

    @patch("main.run_wind_down", return_value="quit")
    @patch("main.run_human_turn", return_value="next")
    @patch("main.run_bots_ask_human", return_value=(False, 0))
    def test_wind_down_quit_propagates(self, mock_ask, mock_human, mock_wind_down):
        """If human quits during wind-down, run_phase returns 'quit'."""
        from main import run_phase
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)
        rng = random.Random(42)

        result = run_phase(state, bots, bot_map, rng)
        self.assertEqual(result, "quit")

    @patch("main.run_wind_down")
    @patch("main.run_human_turn", return_value="quit")
    @patch("main.run_bots_ask_human", return_value=(False, 0))
    def test_quit_from_phase_b_skips_wind_down(self, mock_ask, mock_human,
                                                 mock_wind_down):
        """If human quits during Phase B, wind-down is not called."""
        from main import run_phase
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)
        rng = random.Random(42)

        result = run_phase(state, bots, bot_map, rng)
        self.assertEqual(result, "quit")
        mock_wind_down.assert_not_called()

    @patch("main.run_wind_down")
    @patch("main.run_human_turn")
    @patch("main.run_bots_ask_human", return_value=(True, 0))
    def test_quit_from_phase_a_skips_everything(self, mock_ask, mock_human,
                                                  mock_wind_down):
        """If human quits during Phase A, Phase B and wind-down are skipped."""
        from main import run_phase
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)
        rng = random.Random(42)

        result = run_phase(state, bots, bot_map, rng)
        self.assertEqual(result, "quit")
        mock_human.assert_not_called()
        mock_wind_down.assert_not_called()

    @patch("main.run_wind_down", return_value="next")
    @patch("main.run_human_turn", return_value="next")
    @patch("main.run_bots_ask_human", return_value=(False, 0))
    def test_no_turn_loop(self, mock_ask, mock_human, mock_wind_down):
        """run_phase calls Phase A and Phase B exactly once each."""
        from main import run_phase
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)
        rng = random.Random(42)

        run_phase(state, bots, bot_map, rng)
        self.assertEqual(mock_ask.call_count, 1)
        self.assertEqual(mock_human.call_count, 1)


# ── Tests for pass removal ───────────────────────────────────────────────────

class TestPassRemoved(unittest.TestCase):
    """Verify 'pass' command is no longer recognized."""

    def test_parse_pass_is_unknown(self):
        """'pass' should not return 'pass' — it's an unknown command."""
        from main import _parse_human_command
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)
        live_quotes = {}

        result = _parse_human_command("pass", state, bots, bot_map, live_quotes)
        # "pass" is no longer a valid command, should return None (unknown)
        self.assertIsNone(result)

    def test_parse_done_returns_next(self):
        """'done' is now an alias for 'next'."""
        from main import _parse_human_command
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)
        live_quotes = {}

        result = _parse_human_command("done", state, bots, bot_map, live_quotes)
        self.assertEqual(result, "next")

    def test_parse_next_returns_next(self):
        """'next' still works."""
        from main import _parse_human_command
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)
        live_quotes = {}

        result = _parse_human_command("next", state, bots, bot_map, live_quotes)
        self.assertEqual(result, "next")

    def test_parse_empty_returns_none(self):
        """Empty input returns None (do nothing), not 'pass'."""
        from main import _parse_human_command
        state = _make_state()
        bots = _make_bots()
        bot_map = _make_bot_map(bots)
        live_quotes = {}

        result = _parse_human_command("", state, bots, bot_map, live_quotes)
        self.assertIsNone(result)


# ── Tests for display functions ──────────────────────────────────────────────

class TestWindDownDisplay(unittest.TestCase):
    """Tests for the wind-down display functions in display.py."""

    @patch("builtins.print")
    def test_print_wind_down_start_singular(self, mock_print):
        from game.display import print_wind_down_start
        print_wind_down_start(1)
        output = " ".join(str(c) for c in mock_print.call_args_list)
        self.assertIn("1 more round", output)
        self.assertNotIn("rounds", output.split("1 more ")[1].split(" ")[0])

    @patch("builtins.print")
    def test_print_wind_down_start_plural(self, mock_print):
        from game.display import print_wind_down_start
        print_wind_down_start(3)
        output = " ".join(str(c) for c in mock_print.call_args_list)
        self.assertIn("3 more rounds", output)

    @patch("builtins.print")
    def test_print_wind_down_round(self, mock_print):
        from game.display import print_wind_down_round
        print_wind_down_round(2, 3)
        output = " ".join(str(c) for c in mock_print.call_args_list)
        self.assertIn("2/3", output)

    @patch("builtins.print")
    def test_print_wind_down_your_turn(self, mock_print):
        from game.display import print_wind_down_your_turn
        print_wind_down_your_turn()
        mock_print.assert_called_once()
        output = str(mock_print.call_args)
        self.assertIn("ask for prices", output)

    @patch("builtins.print")
    def test_help_does_not_mention_pass(self, mock_print):
        """Help text should not contain 'pass' as a command."""
        from game.display import print_help
        player_ids = ["You", "Alice", "Bob", "Carol", "Dave"]
        print_help(player_ids, "You")
        output = " ".join(str(c) for c in mock_print.call_args_list)
        # "pass" should not appear as a command (it may appear in other contexts)
        self.assertNotIn("'pass'", output)
        # But 'next' should be there
        self.assertIn("next", output)


# ── Tests for constants ──────────────────────────────────────────────────────

class TestWindDownConstants(unittest.TestCase):
    """Verify wind-down tuning constants are reasonable."""

    def test_wind_down_range(self):
        from main import WIND_DOWN_RANGE
        self.assertEqual(len(WIND_DOWN_RANGE), 2)
        self.assertGreaterEqual(WIND_DOWN_RANGE[0], 1)
        self.assertLessEqual(WIND_DOWN_RANGE[1], 5)

    def test_wind_down_ask_range(self):
        from main import WIND_DOWN_ASK_RANGE
        self.assertEqual(len(WIND_DOWN_ASK_RANGE), 2)
        self.assertGreaterEqual(WIND_DOWN_ASK_RANGE[0], 0)
        self.assertLessEqual(WIND_DOWN_ASK_RANGE[1], 2)

    def test_wind_down_ask_range_leq_normal(self):
        """Wind-down ask range max should be <= normal ask range max."""
        from main import BOTS_ASK_RANGE, WIND_DOWN_ASK_RANGE
        self.assertLessEqual(WIND_DOWN_ASK_RANGE[1], BOTS_ASK_RANGE[1])


if __name__ == "__main__":
    unittest.main()
