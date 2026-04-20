"""
simple.py — EV-based bot with optional adaptation.

Difficulty modes:
  Easy   (adaptation_threshold=None)  — pure EV calculator, never adjusts.
          The human can exploit indefinitely. LSE Round 1 level.
  Medium (adaptation_threshold=5)     — tracks human trading direction and
          shifts quotes against them after 5+ same-direction trades.
          ~10 sells before the edge disappears.

Behaviour:
  - Calculates expected total from own card + revealed central cards
  - Quotes centred on EV with spread of 2, plus small random noise
  - Never bluffs — quotes reflect true belief (with adaptation shift)
  - Evaluates received quotes and trades when edge > threshold
"""

from __future__ import annotations

import random
from typing import Optional

from bots.base import Bot
from game.deck import expected_total
from game.state import GameState, Quote, Phase


EDGE_THRESHOLD = 1   # Min free edge (pts) required to trade
NOISE_RANGE    = 1   # Max random noise added to mid price (+/-)
ADAPT_SHIFT_PER_TRADE = 2   # Pts shift per trade beyond adaptation threshold
MAX_ADAPT_SHIFT = 10         # Cap total shift


class SimpleBot(Bot):
    """
    EV calculator bot. Quotes expected value, no bluffing.

    Args:
        player_id: bot's name
        rng: random source for noise
        adaptation_threshold: None = never adapt (Easy),
                              int  = adapt after N same-direction trades (Medium)
    """

    def __init__(
        self,
        player_id: str,
        rng: random.Random | None = None,
        adaptation_threshold: int | None = None,
    ) -> None:
        super().__init__(player_id)
        self.rng = rng or random.Random()
        self.adaptation_threshold = adaptation_threshold

    # ── Internal helpers ───────────────────────────────────────────────────

    def _expected_mid(self, state: GameState) -> float:
        """Compute the EV of the final total from this bot's perspective."""
        known = state.known_cards_for(self.player_id)
        unknown = state.unknown_count_for(self.player_id)
        return expected_total(known, unknown)

    def _adaptation_shift(self, requester: str | None) -> int:
        """
        How much to shift the quote against requester's trading direction.

        If requester has been selling (bearish), we LOWER our quotes so their
        sells get worse fills. If buying (bullish), we RAISE quotes so their
        buys cost more.

        Returns 0 if no adaptation (Easy mode or below threshold).
        """
        if self.adaptation_threshold is None or requester is None:
            return 0

        streak = self.opponent_same_direction_streak(requester)
        if streak < self.adaptation_threshold:
            return 0

        direction = self.opponent_direction(requester)
        excess = streak - self.adaptation_threshold + 1
        shift_amount = min(excess * ADAPT_SHIFT_PER_TRADE, MAX_ADAPT_SHIFT)

        if direction == "bearish":
            return -shift_amount   # lower quotes → worse sell fills
        elif direction == "bullish":
            return shift_amount    # raise quotes → worse buy fills
        return 0

    # ── Bot interface ──────────────────────────────────────────────────────

    def get_quote(self, state: GameState, requester: str | None = None) -> Quote:
        mid = self._expected_mid(state)
        noise = self.rng.randint(-NOISE_RANGE, NOISE_RANGE)
        shift = self._adaptation_shift(requester)
        bid = round(mid) + noise + shift - 1  # spread of 2: ask = bid + 2
        return Quote(bid=bid, ask=bid + 2)

    def decide_on_quote(
        self, state: GameState, quoter: str, quote: Quote
    ) -> Optional[str]:
        """
        Evaluate a received quote against our EV.
        Buy if ask is well below EV, sell if bid is well above EV.
        """
        ev = self._expected_mid(state)

        buy_edge = ev - quote.ask    # positive if ask is cheap
        sell_edge = quote.bid - ev   # positive if bid is expensive

        if buy_edge > EDGE_THRESHOLD and buy_edge >= sell_edge:
            return "buy"
        if sell_edge > EDGE_THRESHOLD:
            return "sell"
        return None

    def decide_action(self, state: GameState) -> Optional[str]:
        """Pick a random other player to ask for a price."""
        if state.phase == Phase.SETTLE:
            return None
        others = [p for p in state.player_ids if p != self.player_id]
        if not others:
            return None
        return self.rng.choice(others)
