"""
state.py — Core game state dataclasses and enums.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum, auto
from typing import Dict, List, Optional, Tuple


class Difficulty(Enum):
    EASY = auto()      # 4 simple bots, never adapt — LSE Round 1
    MEDIUM = auto()    # 2 simple + 2 strategic, adapt after 5 trades
    HARD = auto()      # 4 strategic bots, aggressive adaptation — Citi Final


class Phase(Enum):
    OPEN = auto()      # Initial trading — no central cards revealed
    REVEAL_1 = auto()  # First central card revealed
    REVEAL_2 = auto()  # Second central card revealed
    REVEAL_3 = auto()  # Third central card revealed
    SETTLE = auto()    # All revealed, settlement


PHASE_ORDER = [Phase.OPEN, Phase.REVEAL_1, Phase.REVEAL_2, Phase.REVEAL_3, Phase.SETTLE]

PHASE_LABELS = {
    Phase.OPEN:     "Phase 1 — Initial Trading (no central cards revealed)",
    Phase.REVEAL_1: "Phase 2 — First central card revealed",
    Phase.REVEAL_2: "Phase 3 — Second central card revealed",
    Phase.REVEAL_3: "Phase 4 — Third central card revealed",
    Phase.SETTLE:   "Phase 5 — Settlement",
}


@dataclass
class Trade:
    """A single executed transaction."""
    buyer: str        # player_id who bought
    seller: str       # player_id who sold
    price: int        # agreed price
    phase: Phase      # phase in which it occurred

    def pnl_for(self, player_id: str, final_total: int) -> int:
        """Return the P&L this trade contributes for a given player."""
        if player_id == self.buyer:
            return final_total - self.price
        if player_id == self.seller:
            return self.price - final_total
        return 0


@dataclass
class Quote:
    """A two-way market: bid (buy price) and ask (sell price). Spread is always 2."""
    bid: int
    ask: int

    def __post_init__(self) -> None:
        if self.ask != self.bid + 2:
            raise ValueError(f"Spread must be exactly 2 (got bid={self.bid} ask={self.ask})")

    def __str__(self) -> str:
        return f"{self.bid} – {self.ask}"

    @staticmethod
    def from_mid(mid: float) -> "Quote":
        """Create a quote centred on mid, rounding bid down."""
        bid = int(mid) - 1
        return Quote(bid=bid, ask=bid + 2)


@dataclass
class GameState:
    """Complete mutable state for one round of MagCry."""

    # Players (ordered list; index 0 is always the human)
    player_ids: List[str]

    # Cards
    private_cards: Dict[str, int]   # player_id → hidden card value
    central_cards: List[int]        # all 3 central cards (hidden until revealed)
    revealed_central: List[int] = field(default_factory=list)  # revealed so far

    # Trading
    trades: List[Trade] = field(default_factory=list)
    markets: Dict[str, Optional[Quote]] = field(default_factory=dict)  # current quote per player

    # Phase
    phase: Phase = Phase.OPEN

    # Round counter within phase (used to limit bot/human turns)
    turn: int = 0

    def advance_phase(self) -> None:
        """Move to the next phase, revealing the next central card if applicable."""
        idx = PHASE_ORDER.index(self.phase)
        if idx + 1 < len(PHASE_ORDER):
            self.phase = PHASE_ORDER[idx + 1]

        # Reveal next central card when entering REVEAL phases
        n_revealed = len(self.revealed_central)
        if self.phase in (Phase.REVEAL_1, Phase.REVEAL_2, Phase.REVEAL_3):
            reveal_idx = PHASE_ORDER.index(self.phase) - 1  # 0, 1, 2
            if reveal_idx < len(self.central_cards) and reveal_idx == n_revealed:
                self.revealed_central.append(self.central_cards[reveal_idx])

        self.turn = 0

    def final_total(self) -> int:
        """Sum of all 8 cards — only valid at SETTLE phase."""
        return sum(self.private_cards.values()) + sum(self.central_cards)

    def known_cards_for(self, player_id: str) -> List[int]:
        """Cards visible to a specific player: own card + revealed central cards."""
        return [self.private_cards[player_id]] + list(self.revealed_central)

    def unknown_count_for(self, player_id: str) -> int:
        """How many cards are still hidden from this player's perspective."""
        return (len(self.player_ids) - 1) + (len(self.central_cards) - len(self.revealed_central))

    def scoreboard(self) -> Dict[str, int]:
        """
        Return P&L per player. Requires phase == SETTLE.
        """
        T = self.final_total()
        scores: Dict[str, int] = {pid: 0 for pid in self.player_ids}
        for trade in self.trades:
            if trade.buyer in scores:
                scores[trade.buyer] += T - trade.price
            if trade.seller in scores:
                scores[trade.seller] += trade.price - T
        return scores
