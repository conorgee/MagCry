"""
deck.py — Card deck definition, shuffling, and dealing logic.

Deck: [-10, 1..15, 20]  (17 cards total, average = 130/17 ≈ 7.65)
Each round: 5 private cards (one per player) + 3 central cards = 8 in play.
"""

import random
from typing import Dict, List, Tuple

DECK: List[int] = [-10] + list(range(1, 16)) + [20]

DECK_SIZE = len(DECK)          # 17
DECK_SUM = sum(DECK)           # 130
DECK_MEAN = DECK_SUM / DECK_SIZE  # ≈ 7.647

N_PLAYERS = 5
N_CENTRAL = 3
N_IN_PLAY = N_PLAYERS + N_CENTRAL  # 8


def deck_mean_excluding(known_cards: List[int]) -> float:
    """
    Expected value of a single unknown card, given we already know
    some cards have been removed from the deck.
    """
    remaining = list(DECK)
    for c in known_cards:
        remaining.remove(c)
    if not remaining:
        return 0.0
    return sum(remaining) / len(remaining)


def expected_total(known_cards: List[int], unknown_count: int) -> float:
    """
    Expected final total given the cards we know and the number
    of cards still hidden.

    known_cards  : list of card values already visible to us
    unknown_count: how many cards are still hidden
    """
    known_sum = sum(known_cards)
    ev_per_unknown = deck_mean_excluding(known_cards)
    return known_sum + unknown_count * ev_per_unknown


def deal(
    player_ids: List[str],
    rng: random.Random | None = None,
) -> Tuple[Dict[str, int], List[int]]:
    """
    Shuffle the deck and deal one private card to each player plus
    three central cards face-down.

    Returns:
        private_cards : {player_id: card_value}
        central_cards : list of 3 ints (face-down order preserved)
    """
    if rng is None:
        rng = random.Random()

    deck = DECK.copy()
    rng.shuffle(deck)

    n = len(player_ids)
    assert n == N_PLAYERS, f"Expected {N_PLAYERS} players, got {n}"

    private_cards: Dict[str, int] = {}
    for i, pid in enumerate(player_ids):
        private_cards[pid] = deck[i]

    central_cards: List[int] = deck[N_PLAYERS: N_PLAYERS + N_CENTRAL]

    return private_cards, central_cards
