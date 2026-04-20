"""
scoring.py — Settlement calculation and leaderboard formatting.
"""

from __future__ import annotations

from typing import Dict, List, Tuple

from game.state import GameState, Trade


def settle(state: GameState) -> Dict[str, int]:
    """
    Calculate final P&L for every player.

    For each trade:
        buyer  scores: final_total - price
        seller scores: price - final_total

    Returns {player_id: total_pnl}
    """
    T = state.final_total()
    scores: Dict[str, int] = {pid: 0 for pid in state.player_ids}
    for trade in state.trades:
        scores[trade.buyer] += T - trade.price
        scores[trade.seller] += trade.price - T
    return scores


def leaderboard(scores: Dict[str, int]) -> List[Tuple[str, int]]:
    """
    Return a sorted list of (player_id, score) from highest to lowest.
    """
    return sorted(scores.items(), key=lambda x: x[1], reverse=True)


def trade_breakdown(state: GameState, player_id: str) -> List[Tuple[Trade, int]]:
    """
    Return a list of (trade, pnl) for all trades involving player_id,
    evaluated at the final total.
    """
    T = state.final_total()
    result = []
    for trade in state.trades:
        if trade.buyer == player_id or trade.seller == player_id:
            pnl = trade.pnl_for(player_id, T)
            result.append((trade, pnl))
    return result
