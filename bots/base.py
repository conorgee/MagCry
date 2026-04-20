"""
base.py — Abstract base class for all bot players.

Bots track opponent trading history and adapt their behaviour based on
difficulty level. This is the primary constraint in the game — smart
opponents ARE the position limit (book-accurate).

V2 interface:
  - get_quote(state, requester)     → Quote when asked "what's your price?"
  - decide_on_quote(state, quoter, quote) → what to do when we receive a quote
  - decide_action(state)            → who to ask for a price (or None)
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from collections import defaultdict
from typing import Dict, List, Optional, Tuple

from game.state import GameState, Quote


class Bot(ABC):
    """
    All bots must implement:
      - get_quote(state, requester)     → Quote to post when asked for a price
      - decide_on_quote(state, quoter, quote) → reaction to a received quote
      - decide_action(state)            → target player to ask, or None

    Bots track opponent trading patterns to adapt behaviour.
    """

    def __init__(self, player_id: str) -> None:
        self.player_id = player_id
        # Track every player's directional trades: {player_id: ["buy","sell",...]}
        self._opponent_history: Dict[str, List[str]] = defaultdict(list)

    # ── Trade tracking ─────────────────────────────────────────────────────

    def record_trade(self, trade_buyer: str, trade_seller: str, player_of_interest: str) -> None:
        """
        Record that a player bought or sold. Call after every trade.
        We track the direction from the perspective of `player_of_interest`.
        """
        if player_of_interest == trade_buyer:
            self._opponent_history[player_of_interest].append("buy")
        elif player_of_interest == trade_seller:
            self._opponent_history[player_of_interest].append("sell")

    def opponent_direction(self, player_id: str) -> str:
        """
        Infer a player's directional bias from their trade history.
        Returns "bullish", "bearish", or "neutral".
        """
        history = self._opponent_history.get(player_id, [])
        if len(history) < 2:
            return "neutral"
        buys = history.count("buy")
        sells = history.count("sell")
        if sells >= buys + 2:
            return "bearish"
        if buys >= sells + 2:
            return "bullish"
        return "neutral"

    def opponent_trade_count(self, player_id: str) -> int:
        """Total number of trades recorded for a player."""
        return len(self._opponent_history.get(player_id, []))

    def opponent_same_direction_streak(self, player_id: str) -> int:
        """
        How many consecutive same-direction trades has this player made?
        Returns 0 if no history.
        """
        history = self._opponent_history.get(player_id, [])
        if not history:
            return 0
        last = history[-1]
        streak = 0
        for d in reversed(history):
            if d == last:
                streak += 1
            else:
                break
        return streak

    # ── Bot interface (must implement) ─────────────────────────────────────

    @abstractmethod
    def get_quote(self, state: GameState, requester: str | None = None) -> Quote:
        """
        Return the two-way price this bot quotes when asked.
        Spread is always exactly 2 (book rule).

        If `requester` is provided, the bot may adapt the quote based on
        that player's trading history (the key V2 mechanic).
        """

    @abstractmethod
    def decide_on_quote(
        self, state: GameState, quoter: str, quote: Quote
    ) -> Optional[str]:
        """
        When we asked another player for a price and they gave us `quote`,
        decide what to do.

        Args:
            quoter: the player who provided the quote
            quote:  the two-way price they offered

        Returns:
            "buy"  — hit their ask (buy from them)
            "sell" — hit their bid (sell to them)
            None   — walk away
        """

    @abstractmethod
    def decide_action(self, state: GameState) -> Optional[str]:
        """
        Proactively decide whether to ask another player for a price.

        Returns:
            target_player_id  — ask this player "what's your price?"
            None              — do nothing this turn
        """

    def on_phase_change(self, state: GameState) -> None:
        """
        Hook called when the phase advances (e.g. a central card is revealed).
        Default: no-op. Subclasses may override to update internal state.
        """
