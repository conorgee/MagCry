"""
display.py — Terminal output formatting for MagCry.
Uses only stdlib (no external deps).

V2 additions: difficulty banner, bot-asks-you prompts, adaptation warnings,
              live quotes display (per-interaction).
"""

from __future__ import annotations

from typing import Dict, List, Optional, Tuple

from game.state import GameState, Phase, PHASE_LABELS, Quote, Difficulty
from game.scoring import leaderboard


# ── Colour helpers (ANSI, gracefully disabled on non-TTY) ──────────────────────

RESET  = "\033[0m"
BOLD   = "\033[1m"
DIM    = "\033[2m"
RED    = "\033[91m"
GREEN  = "\033[92m"
YELLOW = "\033[93m"
CYAN   = "\033[96m"
WHITE  = "\033[97m"
MAGENTA = "\033[95m"


def _c(text: str, *codes: str) -> str:
    return "".join(codes) + str(text) + RESET


def _hr(char: str = "─", width: int = 62) -> str:
    return char * width


# ── Difficulty selection ──────────────────────────────────────────────────────

DIFFICULTY_INFO = {
    Difficulty.EASY: (
        "Easy — LSE Round 1",
        "4 simple bots, never adapt. Exploit freely.",
    ),
    Difficulty.MEDIUM: (
        "Medium",
        "2 simple + 2 strategic. Bots adapt after ~5 same-direction trades.",
    ),
    Difficulty.HARD: (
        "Hard — Citi Final",
        "4 strategic bots. Bluff, infer your card, adapt after 2-3 trades.",
    ),
}


def print_difficulty_menu() -> None:
    print()
    print(_c("  Select difficulty", BOLD, WHITE))
    print(_c("  " + _hr("─", 50), DIM))
    for i, diff in enumerate([Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD], 1):
        name, desc = DIFFICULTY_INFO[diff]
        print(f"  {_c(str(i), BOLD, CYAN)}  {_c(name, BOLD, WHITE)}")
        print(f"     {_c(desc, DIM)}")
    print(_c("  " + _hr("─", 50), DIM))


def print_difficulty_banner(difficulty: Difficulty) -> None:
    name, desc = DIFFICULTY_INFO[difficulty]
    print()
    print(_c(f"  Difficulty: {name}", BOLD, MAGENTA))
    print(_c(f"  {desc}", DIM))


# ── Phase banner ──────────────────────────────────────────────────────────────

def print_phase_banner(phase: Phase) -> None:
    label = PHASE_LABELS[phase]
    print()
    print(_c(_hr("═"), BOLD, CYAN))
    print(_c(f"  {label}", BOLD, CYAN))
    print(_c(_hr("═"), BOLD, CYAN))


# ── Turn header ───────────────────────────────────────────────────────────────

def print_turn_header(turn_n: int) -> None:
    print()
    print(f"{BOLD}  ── Turn {turn_n} ──{RESET}")


# ── Bot asks you ──────────────────────────────────────────────────────────────

def print_bot_asks_you(bot_name: str) -> None:
    """Display when a bot asks the human for a price."""
    print()
    print(_c(f"  [bot] {bot_name} asks: ", BOLD, YELLOW) +
          _c("\"What's your price?\"", YELLOW))
    print(_c("  You MUST quote. Enter: <bid> <ask>  (spread of 2)", DIM))


def print_bot_decision(bot_name: str, decision: Optional[str], quote: Quote) -> None:
    """Display what the bot decided after seeing your quote."""
    if decision == "buy":
        print(_c(f"  [bot] {bot_name} buys at {quote.ask}", GREEN))
    elif decision == "sell":
        print(_c(f"  [bot] {bot_name} sells at {quote.bid}", RED))
    else:
        print(_c(f"  [bot] {bot_name} walks away", DIM))


# ── Adaptation warning ────────────────────────────────────────────────────────

def print_adaptation_warning(bot_name: str, direction: str) -> None:
    """Warn the player that a bot has caught onto their strategy."""
    if direction == "bearish":
        msg = f"{bot_name} noticed you keep selling. Quotes are shifting down..."
    elif direction == "bullish":
        msg = f"{bot_name} noticed you keep buying. Quotes are shifting up..."
    else:
        return
    print(_c(f"  ! {msg}", BOLD, MAGENTA))


# ── Current quotes (V2: per-interaction, not standing markets) ────────────────

def print_live_quote(player_id: str, quote: Quote) -> None:
    """Show a single quote just received from a bot."""
    print(_c(f"  {player_id}'s price: ", BOLD, WHITE) +
          _c(f"{quote.bid} – {quote.ask}", GREEN))


def print_markets(state: GameState, human_id: str) -> None:
    """Legacy V1 market display — still works if state.markets is populated."""
    print()
    print(_c("  Current Markets", BOLD, WHITE))
    print(_c("  " + _hr("─", 50), DIM))
    print(f"  {'Player':<18} {'Market':>12}  {'Type'}")
    print(_c("  " + _hr("─", 50), DIM))
    for pid in state.player_ids:
        quote: Optional[Quote] = state.markets.get(pid)
        tag = _c("YOU", BOLD, YELLOW) if pid == human_id else _c("bot", DIM)
        if quote:
            mkt = _c(f"{quote.bid:>4} – {quote.ask:<4}", GREEN)
        else:
            mkt = _c("  no quote  ", DIM)
        print(f"  {pid:<18} {mkt}  {tag}")
    print(_c("  " + _hr("─", 50), DIM))


# ── Player status line ────────────────────────────────────────────────────────

def print_player_status(state: GameState, human_id: str) -> None:
    card = state.private_cards[human_id]
    revealed = state.revealed_central
    hidden = len(state.central_cards) - len(revealed)

    card_str = _c(f"{card:+d}", BOLD, YELLOW)
    revealed_str = (
        _c(str(revealed), GREEN) if revealed else _c("none yet", DIM)
    )

    buys  = [t for t in state.trades if t.buyer  == human_id]
    sells = [t for t in state.trades if t.seller == human_id]

    buy_str  = _c(f"{len(buys)} buy{'s' if len(buys)!=1 else ''}", GREEN if buys else DIM)
    sell_str = _c(f"{len(sells)} sell{'s' if len(sells)!=1 else ''}", RED if sells else DIM)

    net = len(buys) - len(sells)
    net_str = _c(f"net {net:+d}", BOLD, YELLOW if net != 0 else DIM)

    print()
    print(_c("  Your status", BOLD, WHITE))
    print(f"  Card: {card_str}   |   Revealed central: {revealed_str}   |   Hidden central: {hidden}")
    print(f"  Trades: {buy_str}  {sell_str}  ({net_str})")


# ── Help / command reference (V2) ────────────────────────────────────────────

def print_help(player_ids: List[str], human_id: str) -> None:
    others = [p for p in player_ids if p != human_id]
    names = ", ".join(others)
    print()
    print(_c("  Commands", BOLD, WHITE))
    print(_c("  " + _hr("─", 50), DIM))
    print(f"  {'ask <player>':<28}  Ask a player for their price")
    print(f"  {'buy <player>':<28}  Hit their ask (buy from them)")
    print(f"  {'sell <player>':<28}  Hit their bid (sell to them)")
    print(f"  {'status':<28}  Show your card & trade summary")
    print(f"  {'next':<28}  Reveal next central card / advance phase")
    print(f"  {'help':<28}  Show this help")
    print(_c("  " + _hr("─", 50), DIM))
    print()
    print(f"  Players: {names}")
    print(f"  When a bot asks you: enter {_c('<bid> <ask>', BOLD, WHITE)} (spread of 2)")


# ── Trade confirmation ────────────────────────────────────────────────────────

def print_trade(buyer: str, seller: str, price: int, human_id: str) -> None:
    you_bought = buyer == human_id
    you_sold   = seller == human_id
    if you_bought:
        msg = _c(f"  Bought at {price} from {seller}", GREEN)
    elif you_sold:
        msg = _c(f"  Sold at {price} to {buyer}", RED)
    else:
        msg = _c(f"  {buyer} bought from {seller} at {price}", DIM)
    print(msg)


def print_bot_trade(buyer: str, seller: str, price: int) -> None:
    print(_c(f"  [bot] {buyer} bought from {seller} at {price}", DIM))


def print_bot_quote(player_id: str, quote: Quote) -> None:
    print(_c(f"  [bot] {player_id} quotes {quote}", DIM))


# ── Settlement screen ─────────────────────────────────────────────────────────

def print_settlement(state: GameState, human_id: str) -> None:
    T = state.final_total()
    scores = state.scoreboard()
    board = leaderboard(scores)

    print()
    print(_c(_hr("═"), BOLD, CYAN))
    print(_c("  SETTLEMENT", BOLD, CYAN))
    print(_c(_hr("═"), BOLD, CYAN))

    # Reveal all cards
    print()
    print(_c("  All cards revealed:", BOLD, WHITE))
    for pid in state.player_ids:
        card = state.private_cards[pid]
        tag = " (YOU)" if pid == human_id else ""
        print(f"    {pid:<18} {card:>+4}{tag}")
    print(f"    {'Central cards':<18} {state.central_cards}")
    print()
    print(_c(f"  Final total T = {T}", BOLD, YELLOW))

    # Leaderboard
    print()
    print(_c("  Leaderboard", BOLD, WHITE))
    print(_c("  " + _hr("─", 40), DIM))
    for rank, (pid, score) in enumerate(board, 1):
        you = " <- YOU" if pid == human_id else ""
        score_str = _c(f"{score:>+6}", GREEN if score >= 0 else RED)
        winner_star = _c(" * WINNER", BOLD, YELLOW) if rank == 1 else ""
        print(f"  {rank}. {pid:<18} {score_str}{winner_star}{you}")
    print(_c("  " + _hr("─", 40), DIM))

    # Human trade breakdown
    print()
    print(_c(f"  Your trades ({human_id})", BOLD, WHITE))
    print(_c("  " + _hr("─", 50), DIM))
    my_trades = [(t, t.pnl_for(human_id, T)) for t in state.trades
                 if t.buyer == human_id or t.seller == human_id]
    if not my_trades:
        print(_c("  No trades.", DIM))
    else:
        print(f"  {'Direction':<10} {'Counterparty':<18} {'Price':>6}  {'P&L':>6}")
        for trade, pnl in my_trades:
            if trade.buyer == human_id:
                direction = _c("BUY ", GREEN)
                counterparty = trade.seller
            else:
                direction = _c("SELL", RED)
                counterparty = trade.buyer
            pnl_str = _c(f"{pnl:>+6}", GREEN if pnl >= 0 else RED)
            print(f"  {direction}       {counterparty:<18} {trade.price:>6}  {pnl_str}")
    print(_c("  " + _hr("─", 50), DIM))


# ── Error / info messages ─────────────────────────────────────────────────────

def print_error(msg: str) -> None:
    print(_c(f"  Error: {msg}", RED))


def print_info(msg: str) -> None:
    print(_c(f"  {msg}", CYAN))


def print_reveal(card: int, n: int) -> None:
    print()
    print(_c(f"  *** Central card {n} revealed: {card:+d} ***", BOLD, YELLOW))


# ── Wind-down display ─────────────────────────────────────────────────────────

def print_wind_down_start(n_rounds: int) -> None:
    """Announce the wind-down period before the next card is revealed."""
    print()
    print(_c("  You signal you're ready...", BOLD, WHITE))
    print(_c(f"  But trading continues — {n_rounds} more round{'s' if n_rounds != 1 else ''} "
             "before the card is revealed.", DIM))

def print_wind_down_round(round_n: int, total: int) -> None:
    """Header for a single wind-down round."""
    print()
    print(_c(f"  ── Wind-down {round_n}/{total} ──", BOLD, MAGENTA))


def print_wind_down_your_turn() -> None:
    """Prompt the human to trade back during wind-down after being asked."""
    print(_c("  You can ask for prices too  (press enter to continue)", DIM))
