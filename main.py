"""
main.py — The Trading Game CLI entry point (V2).

Book-accurate "ask for a price" mechanic with bot adaptation.
Trade freely, type 'next' to reveal the next central card.
After 'next', a wind-down period lets bots trade — and if a bot
asks you for a price, you get a chance to trade back.

Run with:  python main.py

Phase flow:
  1. Bots ask you for prices (you MUST quote)
  2. You trade freely: ask / buy / sell, type 'next' when ready
  3. Wind-down: bots trade, may ask you — if asked you can trade back
  4. Next central card revealed
"""

from __future__ import annotations

import random
import sys
from typing import Dict, List, Optional

from game.deck import deal
from game.state import GameState, Phase, PHASE_ORDER, Quote, Difficulty
from game.trading import (
    TradingError,
    execute_trade_direct,
    trade_summary,
    validate_quote,
)
from game.display import (
    print_phase_banner,
    print_player_status,
    print_help,
    print_trade,
    print_bot_trade,
    print_bot_asks_you,
    print_bot_decision,
    print_adaptation_warning,
    print_live_quote,
    print_difficulty_menu,
    print_difficulty_banner,
    print_settlement,
    print_error,
    print_info,
    print_reveal,
    print_wind_down_start,
    print_wind_down_round,
    print_wind_down_your_turn,
    BOLD, CYAN, YELLOW, DIM, RESET, _c,
)
from bots.base import Bot
from bots.simple import SimpleBot
from bots.strategic import StrategicBot


# ── Constants ──────────────────────────────────────────────────────────────────

HUMAN_ID    = "You"
BOT_NAMES   = ["Alice", "Bob", "Carol", "Dave"]

# How many bots ask you per turn (min, max)
BOTS_ASK_RANGE = (0, 2)

# Wind-down rounds after human types 'next' (min, max)
WIND_DOWN_RANGE = (1, 3)

# During wind-down, fewer bots pester you (min, max)
WIND_DOWN_ASK_RANGE = (0, 1)


# ── Difficulty → bot configuration ────────────────────────────────────────────

def make_bots(difficulty: Difficulty, rng: random.Random) -> List[Bot]:
    """Create bots based on difficulty level."""
    if difficulty == Difficulty.EASY:
        # 4 simple bots, no adaptation
        return [SimpleBot(n, rng=random.Random(rng.randint(0, 10000)))
                for n in BOT_NAMES]

    elif difficulty == Difficulty.MEDIUM:
        # 2 simple (adapt after 5) + 2 strategic
        return [
            SimpleBot("Alice", rng=random.Random(rng.randint(0, 10000)),
                      adaptation_threshold=5),
            SimpleBot("Bob",   rng=random.Random(rng.randint(0, 10000)),
                      adaptation_threshold=5),
            StrategicBot("Carol", rng=random.Random(rng.randint(0, 10000))),
            StrategicBot("Dave",  rng=random.Random(rng.randint(0, 10000))),
        ]

    else:  # HARD
        # 4 strategic bots with aggressive adaptation
        return [StrategicBot(n, rng=random.Random(rng.randint(0, 10000)))
                for n in BOT_NAMES]


def bots_by_id(bots: List[Bot]) -> Dict[str, Bot]:
    return {b.player_id: b for b in bots}


# ── Game setup ────────────────────────────────────────────────────────────────

def init_state(rng: random.Random) -> GameState:
    player_ids = [HUMAN_ID] + BOT_NAMES
    private_cards, central_cards = deal(player_ids, rng=rng)
    return GameState(
        player_ids=player_ids,
        private_cards=private_cards,
        central_cards=central_cards,
        markets={pid: None for pid in player_ids},
    )


# ── Trade recording ──────────────────────────────────────────────────────────

def record_trade_for_all_bots(bots: List[Bot], buyer: str, seller: str) -> None:
    """Notify all bots about a trade so they update opponent tracking."""
    for bot in bots:
        bot.record_trade(buyer, seller, buyer)
        bot.record_trade(buyer, seller, seller)


# ── Bots ask human for prices ────────────────────────────────────────────────

def run_bots_ask_human(
    state: GameState, bots: List[Bot], rng: random.Random,
    ask_range: tuple = None,
) -> tuple:
    """
    0-2 bots ask the human for a price.
    Human MUST quote (spread of 2). Bot decides to trade or walk.

    Returns:
        (quit_requested: bool, n_asked: int)
        quit_requested — True if human wants to quit
        n_asked        — how many bots actually asked
    """
    if ask_range is None:
        ask_range = BOTS_ASK_RANGE
    n_askers = rng.randint(*ask_range)
    if n_askers == 0:
        return (False, 0)

    askers = rng.sample(bots, min(n_askers, len(bots)))

    for idx, bot in enumerate(askers):
        print_bot_asks_you(bot.player_id)

        # Check if this bot has adapted against the human
        streak = bot.opponent_same_direction_streak(HUMAN_ID)
        direction = bot.opponent_direction(HUMAN_ID)
        if streak >= 3 and direction != "neutral":
            print_adaptation_warning(bot.player_id, direction)

        # Human must provide a quote
        quote = _get_human_quote()
        if quote is None:
            return (True, idx)  # quit — idx bots asked so far

        # Bot evaluates the human's quote
        decision = bot.decide_on_quote(state, HUMAN_ID, quote)
        print_bot_decision(bot.player_id, decision, quote)

        if decision == "buy":
            trade = execute_trade_direct(
                state, bot.player_id, HUMAN_ID, quote.ask
            )
            record_trade_for_all_bots(bots, bot.player_id, HUMAN_ID)
        elif decision == "sell":
            trade = execute_trade_direct(
                state, HUMAN_ID, bot.player_id, quote.bid
            )
            record_trade_for_all_bots(bots, HUMAN_ID, bot.player_id)

    return (False, len(askers))


def _get_human_quote() -> Optional[Quote]:
    """Prompt the human for a two-way quote. Returns None on quit."""
    while True:
        try:
            raw = input(f"\n  > ").strip()
        except (EOFError, KeyboardInterrupt):
            return None

        if raw.lower() in ("quit", "exit", "q"):
            return None

        parts = raw.split()
        if len(parts) == 2:
            try:
                bid, ask = int(parts[0]), int(parts[1])
                return validate_quote(bid, ask)
            except (ValueError, TradingError) as e:
                print_error(str(e))
                continue

        print_error("Enter two numbers: <bid> <ask>  (spread of 2)")


# ── Human trading ────────────────────────────────────────────────────────────

def run_human_turn(
    state: GameState, bots: List[Bot], bot_map: Dict[str, Bot]
) -> str:
    """
    Human's interactive turn: ask bots for prices, then trade.

    Returns:
        "next"  — advance to next phase (reveal next card)
        "quit"  — exit game
    """
    print_player_status(state, HUMAN_ID)
    print()
    print(f"{BOLD}{CYAN}  Commands: ask / buy / sell / status / help / next{RESET}")

    # Track quotes received this turn (bot_name -> Quote)
    live_quotes: Dict[str, Quote] = {}

    while True:
        try:
            raw = input(f"\n  > ").strip()
        except (EOFError, KeyboardInterrupt):
            return "quit"

        result = _parse_human_command(raw, state, bots, bot_map, live_quotes)
        if result in ("next", "quit"):
            return result


def _parse_human_command(
    raw: str,
    state: GameState,
    bots: List[Bot],
    bot_map: Dict[str, Bot],
    live_quotes: Dict[str, Quote],
) -> Optional[str]:
    """
    Parse and execute one human command during Phase B.

    Returns:
        "next"  — advance to next phase (reveal next card)
        "quit"  — exit game
        None    — command processed, keep looping
    """
    parts = raw.strip().lower().split()
    if not parts:
        return None

    cmd = parts[0]

    # ── ask <player> ───────────────────────────────────────────────────────
    if cmd == "ask":
        if len(parts) != 2:
            print_error("Usage: ask <player>   e.g. ask Alice")
            return None
        target = _resolve_bot(parts[1], state, bot_map)
        if target is None:
            return None

        bot = bot_map[target]
        quote = bot.get_quote(state, requester=HUMAN_ID)
        live_quotes[target] = quote
        print_live_quote(target, quote)
        return None

    # ── buy <player> ───────────────────────────────────────────────────────
    elif cmd == "buy":
        if len(parts) != 2:
            print_error("Usage: buy <player>   e.g. buy Alice")
            return None
        target = _resolve_bot(parts[1], state, bot_map)
        if target is None:
            return None
        if target not in live_quotes:
            print_error(f"Ask {target} for a price first!  (ask {target.lower()})")
            return None

        quote = live_quotes[target]
        try:
            trade = execute_trade_direct(state, HUMAN_ID, target, quote.ask)
            print_trade(trade.buyer, trade.seller, trade.price, HUMAN_ID)
            record_trade_for_all_bots(bots, HUMAN_ID, target)
            del live_quotes[target]  # quote consumed
        except TradingError as e:
            print_error(str(e))
        return None

    # ── sell <player> ──────────────────────────────────────────────────────
    elif cmd == "sell":
        if len(parts) != 2:
            print_error("Usage: sell <player>   e.g. sell Bob")
            return None
        target = _resolve_bot(parts[1], state, bot_map)
        if target is None:
            return None
        if target not in live_quotes:
            print_error(f"Ask {target} for a price first!  (ask {target.lower()})")
            return None

        quote = live_quotes[target]
        try:
            trade = execute_trade_direct(state, target, HUMAN_ID, quote.bid)
            print_trade(trade.buyer, trade.seller, trade.price, HUMAN_ID)
            record_trade_for_all_bots(bots, target, HUMAN_ID)
            del live_quotes[target]  # quote consumed
        except TradingError as e:
            print_error(str(e))
        return None

    # ── status ─────────────────────────────────────────────────────────────
    elif cmd == "status":
        print_player_status(state, HUMAN_ID)
        return None

    # ── help ───────────────────────────────────────────────────────────────
    elif cmd == "help":
        print_help(state.player_ids, HUMAN_ID)
        return None

    # ── next (advance to next phase / reveal card) ─────────────────────────
    elif cmd in ("next", "done"):
        return "next"

    # ── quit ───────────────────────────────────────────────────────────────
    elif cmd in ("quit", "exit", "q"):
        return "quit"

    else:
        print_error(f"Unknown command '{cmd}'. Type 'help' for options.")
        return None


def _resolve_bot(
    name_input: str, state: GameState, bot_map: Dict[str, Bot]
) -> Optional[str]:
    """Case-insensitive bot name resolution. Rejects self-trade."""
    needle = name_input.lower()
    for pid in state.player_ids:
        if pid.lower() == needle:
            if pid == HUMAN_ID:
                print_error("You can't trade with yourself!")
                return None
            if pid not in bot_map:
                print_error(f"{pid} is not a bot you can ask.")
                return None
            return pid
    print_error(
        f"Unknown player '{name_input}'. "
        f"Players: {', '.join(p for p in state.player_ids if p != HUMAN_ID)}"
    )
    return None


# ── Phase C: Bot-to-bot trading ──────────────────────────────────────────────

def run_bot_to_bot(state: GameState, bots: List[Bot], bot_map: Dict[str, Bot]) -> None:
    """
    Each bot may ask another bot for a price and trade.
    Shown as dim log output.
    """
    for bot in bots:
        target_id = bot.decide_action(state)
        if target_id is None:
            continue
        if target_id == HUMAN_ID:
            continue  # Bots asking human is handled in Phase A
        if target_id == bot.player_id:
            continue  # Can't self-trade
        if target_id not in bot_map:
            continue

        target_bot = bot_map[target_id]
        quote = target_bot.get_quote(state, requester=bot.player_id)

        decision = bot.decide_on_quote(state, target_id, quote)
        if decision == "buy":
            try:
                trade = execute_trade_direct(
                    state, bot.player_id, target_id, quote.ask
                )
                print_bot_trade(trade.buyer, trade.seller, trade.price)
                record_trade_for_all_bots(bots, bot.player_id, target_id)
            except TradingError:
                pass
        elif decision == "sell":
            try:
                trade = execute_trade_direct(
                    state, target_id, bot.player_id, quote.bid
                )
                print_bot_trade(trade.buyer, trade.seller, trade.price)
                record_trade_for_all_bots(bots, target_id, bot.player_id)
            except TradingError:
                pass


# ── Wind-down: bot trading after human types 'next' ──────────────────────────

def run_wind_down(
    state: GameState,
    bots: List[Bot],
    bot_map: Dict[str, Bot],
    rng: random.Random,
) -> str:
    """
    After the human types 'next', run 1-3 additional rounds where bots
    trade with each other and may still ask the human for prices.
    If a bot asks you during wind-down, you get a mini trading window
    to ask for prices back before the wind-down continues.

    Returns:
        "next"  — wind-down complete, proceed to reveal
        "quit"  — human quit during wind-down
    """
    n_rounds = rng.randint(*WIND_DOWN_RANGE)
    print_wind_down_start(n_rounds)

    for i in range(n_rounds):
        print_wind_down_round(i + 1, n_rounds)

        # Some bots may still ask the human for a price (but fewer)
        quit_requested, n_asked = run_bots_ask_human(
            state, bots, rng, ask_range=WIND_DOWN_ASK_RANGE
        )
        if quit_requested:
            return "quit"

        # If a bot asked you, you get a chance to trade back
        if n_asked > 0:
            result = run_wind_down_mini_turn(state, bots, bot_map)
            if result == "quit":
                return "quit"

        # Bot-to-bot trading
        run_bot_to_bot(state, bots, bot_map)

    return "next"


def run_wind_down_mini_turn(
    state: GameState, bots: List[Bot], bot_map: Dict[str, Bot]
) -> str:
    """
    Quick trading window during wind-down after a bot asks you.
    You can ask for prices and trade, or press enter / type 'next' to
    let the wind-down continue.

    Returns:
        "continue"  — done, continue wind-down
        "quit"      — human wants to exit
    """
    print_wind_down_your_turn()

    live_quotes: Dict[str, Quote] = {}

    while True:
        try:
            raw = input(f"\n  > ").strip()
        except (EOFError, KeyboardInterrupt):
            return "quit"

        if not raw or raw.lower() in ("next", "done"):
            return "continue"

        if raw.lower() in ("quit", "exit", "q"):
            return "quit"

        # Reuse the standard command parser
        result = _parse_human_command(raw, state, bots, bot_map, live_quotes)
        if result == "quit":
            return "quit"
        if result == "next":
            return "continue"
        # None → command processed, keep looping


# ── Phase loop ────────────────────────────────────────────────────────────────

def run_phase(
    state: GameState,
    bots: List[Bot],
    bot_map: Dict[str, Bot],
    rng: random.Random,
) -> str:
    """
    Run a complete trading phase.
    The player trades as long as they want, then types 'next'.
    A wind-down period follows before the phase actually advances.

    Returns:
        "next"  — player is ready to advance
        "quit"  — player wants to exit
    """
    # Bots ask human for prices
    quit_requested, _ = run_bots_ask_human(state, bots, rng)
    if quit_requested:
        return "quit"

    # Human trades freely until 'next'
    result = run_human_turn(state, bots, bot_map)
    if result == "quit":
        return "quit"

    # Wind-down: bots get more trading before the card is revealed
    return run_wind_down(state, bots, bot_map, rng)


# ── Difficulty selection ──────────────────────────────────────────────────────

def select_difficulty() -> Difficulty:
    """Prompt the user to select a difficulty level."""
    print_difficulty_menu()
    while True:
        try:
            raw = input("\n  Select [1/2/3] > ").strip()
        except (EOFError, KeyboardInterrupt):
            sys.exit(0)

        if raw == "1":
            return Difficulty.EASY
        elif raw == "2":
            return Difficulty.MEDIUM
        elif raw == "3":
            return Difficulty.HARD
        else:
            print_error("Enter 1, 2, or 3")


# ── Main game loop ────────────────────────────────────────────────────────────

def print_welcome() -> None:
    print()
    print(f"{BOLD}{CYAN}{'=' * 62}{RESET}")
    print(f"{BOLD}{CYAN}  THE TRADING GAME{RESET}")
    print(f"{BOLD}{CYAN}  Based on the Citibank internship competition{RESET}")
    print(f"{BOLD}{CYAN}  from Gary Stevenson's book{RESET}")
    print(f"{BOLD}{CYAN}{'=' * 62}{RESET}")
    print()
    print("  Deck: -10, 1-15, 20  (17 cards, avg ~ 7.65)")
    print("  You vs 4 bots. Trade the final sum of all 8 cards.")
    print()
    print("  \"What's your price?\" — you must always answer.")
    print("  Ask bots for prices, then decide to buy, sell, or walk.")
    print("  Type 'next' when ready to reveal the next central card.")
    print()
    print("  Type 'help' during the game for all commands.")


def main() -> None:
    rng = random.Random()

    print_welcome()

    while True:
        # ── Difficulty ─────────────────────────────────────────────────────
        difficulty = select_difficulty()
        print_difficulty_banner(difficulty)

        # ── New game ───────────────────────────────────────────────────────
        bots = make_bots(difficulty, rng)
        bot_map = bots_by_id(bots)
        state = init_state(rng)

        your_card = state.private_cards[HUMAN_ID]
        print()
        print(f"{BOLD}{YELLOW}  Your private card: {your_card:+d}{RESET}")

        bot_desc = ", ".join(
            f"{b.player_id} ({'strategic' if isinstance(b, StrategicBot) else 'simple'})"
            for b in bots
        )
        print(f"{DIM}  Bots: {bot_desc}{RESET}")

        quit_game = False

        # Run through phases OPEN -> REVEAL_1 -> REVEAL_2 -> REVEAL_3
        for phase in [Phase.OPEN, Phase.REVEAL_1, Phase.REVEAL_2, Phase.REVEAL_3]:
            state.phase = phase

            # Reveal central card for REVEAL phases
            n_revealed = len(state.revealed_central)
            if phase in (Phase.REVEAL_1, Phase.REVEAL_2, Phase.REVEAL_3):
                reveal_idx = [Phase.REVEAL_1, Phase.REVEAL_2, Phase.REVEAL_3].index(phase)
                if reveal_idx == n_revealed:
                    card = state.central_cards[reveal_idx]
                    state.revealed_central.append(card)
                    print_reveal(card, reveal_idx + 1)
                # Notify bots of phase change
                for bot in bots:
                    bot.on_phase_change(state)

            print_phase_banner(phase)

            result = run_phase(state, bots, bot_map, rng)
            if result == "quit":
                quit_game = True
                break

        if quit_game:
            print_info("Thanks for playing.")
            sys.exit(0)

        # ── Settlement ─────────────────────────────────────────────────────
        state.phase = Phase.SETTLE
        print_phase_banner(Phase.SETTLE)
        print_settlement(state, HUMAN_ID)

        # ── Play again? ────────────────────────────────────────────────────
        print()
        try:
            again = input("  Play again? [y/n] > ").strip().lower()
        except (EOFError, KeyboardInterrupt):
            again = "n"

        if again not in ("y", "yes"):
            print_info("Thanks for playing. See you on the desk.")
            break


if __name__ == "__main__":
    main()
