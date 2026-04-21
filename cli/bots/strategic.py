"""
strategic.py — Strategic bluffing bot with aggressive adaptation.

Difficulty: Hard (Citi Final level)

Behaviour:
  - Calculates true EV but BLUFFS when quoting:
      High card → quotes LOW  (suppress market, then buy cheap)
      Low card  → quotes HIGH (inflate market, then sell expensive)
  - Bluff offset is large early, converges to near-zero after 2+ reveals
  - Infers opponent's card from their trading pattern (bearish → low card)
  - Aggressively adapts quotes after 2-3 same-direction trades by an opponent
  - After adaptation, the human's edge disappears fast (~3-4 trades)
"""

from __future__ import annotations

import random
from typing import Optional

from bots.base import Bot
from game.deck import expected_total, DECK_MEAN
from game.state import GameState, Quote, Phase


# Bluff offsets (how far to shift quoted mid from true EV)
BLUFF_OFFSET_EARLY = 8   # Phase.OPEN — maximum deception
BLUFF_OFFSET_MID   = 4   # After first reveal
BLUFF_OFFSET_LATE  = 1   # After second+ reveal — almost honest

EDGE_THRESHOLD = 1        # Min edge to pull the trigger on a trade
MAX_TRADES_PER_TURN = 3   # Don't over-expose on a single turn

# Adaptation: shift 3 pts per trade beyond threshold, cap at 15
ADAPTATION_THRESHOLD = 2
ADAPT_SHIFT_PER_TRADE = 3
MAX_ADAPT_SHIFT = 15


class StrategicBot(Bot):
    """
    Bluffing, market-manipulating bot with aggressive adaptation.
    Difficulty: Hard (Citi Final).
    """

    def __init__(self, player_id: str, rng: random.Random | None = None) -> None:
        super().__init__(player_id)
        self.rng = rng or random.Random()
        # Track how many times we've traded this phase (reset on phase change)
        self._phase_trade_count: int = 0
        self._last_phase: Optional[Phase] = None

    # ── Internal helpers ───────────────────────────────────────────────────

    def _true_ev(self, state: GameState) -> float:
        """True expected total — never shown directly when bluffing."""
        known = state.known_cards_for(self.player_id)
        unknown = state.unknown_count_for(self.player_id)
        return expected_total(known, unknown)

    def _my_card(self, state: GameState) -> int:
        return state.private_cards[self.player_id]

    def _is_high_card(self, state: GameState) -> bool:
        """True if our card is above deck mean — we want to buy."""
        return self._my_card(state) > DECK_MEAN

    def _bluff_offset(self, state: GameState) -> int:
        """Return current bluff offset magnitude based on phase."""
        n_revealed = len(state.revealed_central)
        if n_revealed == 0:
            return BLUFF_OFFSET_EARLY
        elif n_revealed == 1:
            return BLUFF_OFFSET_MID
        else:
            return BLUFF_OFFSET_LATE

    def _bluff_mid(self, state: GameState) -> float:
        """
        The mid price we will QUOTE (not necessarily our true belief).
        High card → quote low (to suppress market, then buy).
        Low card  → quote high (to inflate market, then sell).
        """
        true_ev = self._true_ev(state)
        offset = self._bluff_offset(state)
        if self._is_high_card(state):
            return true_ev - offset   # quote low
        else:
            return true_ev + offset   # quote high

    def _adaptation_shift(self, requester: str | None) -> int:
        """
        Aggressive adaptation: shift quotes against opponent's direction.
        Kicks in after just 2-3 same-direction trades (much faster than SimpleBot).
        """
        if requester is None:
            return 0

        streak = self.opponent_same_direction_streak(requester)
        if streak < ADAPTATION_THRESHOLD:
            return 0

        direction = self.opponent_direction(requester)
        excess = streak - ADAPTATION_THRESHOLD + 1
        shift_amount = min(excess * ADAPT_SHIFT_PER_TRADE, MAX_ADAPT_SHIFT)

        if direction == "bearish":
            return -shift_amount   # lower quotes → worse sell fills
        elif direction == "bullish":
            return shift_amount    # raise quotes → worse buy fills
        return 0

    def _infer_card_from_direction(self, player_id: str) -> float:
        """
        Infer what card an opponent likely holds from their trade direction.
        Bullish (buying a lot) → they probably have a high card.
        Bearish (selling a lot) → they probably have a low card.

        Returns an estimated card value used for improved EV calculation.
        """
        direction = self.opponent_direction(player_id)
        if direction == "bullish":
            return 14.0   # likely 12-20
        elif direction == "bearish":
            return -2.0   # likely -10 to 4
        return DECK_MEAN  # neutral — use deck average

    # ── Bot interface ──────────────────────────────────────────────────────

    def get_quote(self, state: GameState, requester: str | None = None) -> Quote:
        mid = self._bluff_mid(state)
        # Add tiny noise so the bluff doesn't look mechanical
        noise = self.rng.uniform(-0.5, 0.5)
        shift = self._adaptation_shift(requester)
        bid = round(mid + noise) + shift - 1
        return Quote(bid=bid, ask=bid + 2)

    def decide_on_quote(
        self, state: GameState, quoter: str, quote: Quote
    ) -> Optional[str]:
        """
        Evaluate a received quote using our TRUE EV (not bluff EV).
        High card holders prefer buying; low card holders prefer selling.
        """
        true_ev = self._true_ev(state)

        if self._is_high_card(state):
            # We want to buy — is their ask cheap enough?
            buy_edge = true_ev - quote.ask
            if buy_edge > EDGE_THRESHOLD:
                return "buy"
        else:
            # We want to sell — is their bid expensive enough?
            sell_edge = quote.bid - true_ev
            if sell_edge > EDGE_THRESHOLD:
                return "sell"

        # Also opportunistically take big edges either direction
        buy_edge = true_ev - quote.ask
        sell_edge = quote.bid - true_ev
        if buy_edge > EDGE_THRESHOLD * 3:
            return "buy"
        if sell_edge > EDGE_THRESHOLD * 3:
            return "sell"

        return None

    def decide_action(self, state: GameState) -> Optional[str]:
        """
        Pick a target to ask for a price. Strategic about target selection:
        prefer players whose trading direction suggests they'll offer us edge.
        """
        if state.phase == Phase.SETTLE:
            return None

        # Reset trade count tracker on new phase
        if self._last_phase != state.phase:
            self._phase_trade_count = 0
            self._last_phase = state.phase

        if self._phase_trade_count >= MAX_TRADES_PER_TURN:
            return None

        others = [p for p in state.player_ids if p != self.player_id]
        if not others:
            return None

        # Prefer targets with opposite direction (more likely to give us edge)
        high_card = self._is_high_card(state)
        preferred = []
        neutral = []
        for pid in others:
            d = self.opponent_direction(pid)
            if high_card and d == "bearish":
                preferred.append(pid)     # bearish player might quote low → good buy
            elif not high_card and d == "bullish":
                preferred.append(pid)     # bullish player might quote high → good sell
            elif d == "neutral":
                neutral.append(pid)
            # opposite cases are less interesting — skip

        pool = preferred or neutral or others
        target = self.rng.choice(pool)

        self._phase_trade_count += 1
        return target

    def on_phase_change(self, state: GameState) -> None:
        self._phase_trade_count = 0
        self._last_phase = state.phase
