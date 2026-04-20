"""
test_deck.py — Tests for game/deck.py

Covers:
  - Deck composition, sum, and mean
  - deal() correctness, uniqueness, determinism
  - expected_total() against book-stated values
"""

import random
import unittest

from game.deck import (
    DECK,
    DECK_MEAN,
    DECK_SIZE,
    DECK_SUM,
    N_CENTRAL,
    N_IN_PLAY,
    N_PLAYERS,
    deal,
    deck_mean_excluding,
    expected_total,
)


class TestDeckComposition(unittest.TestCase):

    def test_deck_has_17_cards(self):
        self.assertEqual(len(DECK), 17)
        self.assertEqual(DECK_SIZE, 17)

    def test_deck_contains_minus_ten(self):
        self.assertIn(-10, DECK)

    def test_deck_contains_twenty(self):
        self.assertIn(20, DECK)

    def test_deck_contains_1_through_15(self):
        for i in range(1, 16):
            self.assertIn(i, DECK)

    def test_deck_has_no_duplicates(self):
        self.assertEqual(len(DECK), len(set(DECK)))

    def test_deck_sum_is_130(self):
        self.assertEqual(sum(DECK), 130)
        self.assertEqual(DECK_SUM, 130)

    def test_deck_mean_approx_7_65(self):
        self.assertAlmostEqual(DECK_MEAN, 130 / 17, places=5)

    def test_n_players_is_5(self):
        self.assertEqual(N_PLAYERS, 5)

    def test_n_central_is_3(self):
        self.assertEqual(N_CENTRAL, 3)

    def test_n_in_play_is_8(self):
        self.assertEqual(N_IN_PLAY, 8)


class TestDeal(unittest.TestCase):

    def setUp(self):
        self.player_ids = ["You", "Alice", "Bob", "Carol", "Dave"]
        self.rng = random.Random(42)

    def test_returns_correct_number_of_private_cards(self):
        private, central = deal(self.player_ids, rng=self.rng)
        self.assertEqual(len(private), 5)

    def test_returns_correct_number_of_central_cards(self):
        private, central = deal(self.player_ids, rng=self.rng)
        self.assertEqual(len(central), 3)

    def test_all_dealt_cards_from_deck(self):
        private, central = deal(self.player_ids, rng=self.rng)
        all_dealt = list(private.values()) + central
        for card in all_dealt:
            self.assertIn(card, DECK)

    def test_no_duplicate_cards_in_deal(self):
        private, central = deal(self.player_ids, rng=self.rng)
        all_dealt = list(private.values()) + central
        self.assertEqual(len(all_dealt), len(set(all_dealt)))

    def test_each_player_gets_one_card(self):
        private, central = deal(self.player_ids, rng=self.rng)
        for pid in self.player_ids:
            self.assertIn(pid, private)

    def test_deterministic_with_same_seed(self):
        rng1 = random.Random(99)
        rng2 = random.Random(99)
        p1, c1 = deal(self.player_ids, rng=rng1)
        p2, c2 = deal(self.player_ids, rng=rng2)
        self.assertEqual(p1, p2)
        self.assertEqual(c1, c2)

    def test_different_seeds_give_different_deals(self):
        rng1 = random.Random(1)
        rng2 = random.Random(2)
        p1, c1 = deal(self.player_ids, rng=rng1)
        p2, c2 = deal(self.player_ids, rng=rng2)
        # Overwhelmingly likely to differ
        self.assertTrue(p1 != p2 or c1 != c2)

    def test_wrong_player_count_raises(self):
        with self.assertRaises(AssertionError):
            deal(["Only", "Three", "Players"], rng=self.rng)


class TestExpectedTotal(unittest.TestCase):
    """
    Verify against numbers stated explicitly in the book.
    """

    def test_expected_total_all_unknown(self):
        # 8 unknown cards from a full deck: 8 * (130/17) ≈ 61.18
        ev = expected_total([], 8)
        self.assertAlmostEqual(ev, 8 * (130 / 17), places=2)

    def test_expected_total_holding_20(self):
        # Book states: holding 20 → expected total ≈ 68
        # known=[20], 7 unknown remaining cards
        ev = expected_total([20], 7)
        self.assertAlmostEqual(ev, 68.0, delta=0.6)

    def test_expected_total_holding_minus_10(self):
        # Book states: holding -10 → expected total ≈ 51.2
        ev = expected_total([-10], 7)
        self.assertAlmostEqual(ev, 51.2, delta=0.6)

    def test_expected_total_all_known(self):
        # If all cards are known, EV = their sum exactly
        known = [-10, 1, 2, 3, 4, 5, 6, 7]
        ev = expected_total(known, 0)
        self.assertEqual(ev, sum(known))

    def test_expected_total_increases_with_high_known_card(self):
        ev_low  = expected_total([1],  7)
        ev_high = expected_total([15], 7)
        self.assertGreater(ev_high, ev_low)

    def test_deck_mean_excluding_removes_card(self):
        # After removing 20 from deck, mean of remaining should be lower
        mean_full = DECK_MEAN
        mean_excl = deck_mean_excluding([20])
        self.assertLess(mean_excl, mean_full)

    def test_deck_mean_excluding_multiple(self):
        # Removing several high cards should lower the mean
        mean_excl = deck_mean_excluding([15, 14, 13])
        self.assertLess(mean_excl, DECK_MEAN)


if __name__ == "__main__":
    unittest.main()
