# MagCry -- Game Rules

Based on *The Trading Game* by Gary Stevenson.

---

## Overview

MagCry is a card-trading game where players bet on the **sum of 8 hidden
cards** by buying and selling contracts from each other. Each trade is a bet:
the buyer profits if the true total is higher than the trade price, and the
seller profits if it's lower. All profit and loss is zero-sum.

---

## Setup

### The Deck

17 unique cards:

```
-10, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 20
```

- Total sum of all 17 cards: **130**
- Mean value per card: **~7.65**

### Dealing

The deck is shuffled. From the top:

1. **5 private cards** -- one dealt face-down to each of the 5 players
   (1 human + 4 bots: Alice, Bob, Carol, Dave). Each player sees only
   their own card.
2. **3 central cards** -- placed face-down in the middle of the table.
   Nobody sees these yet.
3. **9 remaining cards** -- discarded unseen. They play no further role.

The **settlement value** (called the "total") is the sum of all 8 cards
in play (5 private + 3 central). This is what players are trading.

---

## How Trading Works

### Two-Way Quotes

When asked for a price, a player must give a **two-way quote**: a **bid**
(the price they'll buy at) and an **ask** (the price they'll sell at).
The ask must always be exactly 2 higher than the bid:

```
Ask = Bid + 2
```

This fixed spread of 2 is mandatory. There is no negotiation on the spread.

Example: if you quote 60/62, you're saying "I'll buy at 60 or sell at 62."

### Making a Trade

When you see someone's quote, you can:

- **Buy** at their ask price (you think the total will be higher)
- **Sell** at their bid price (you think the total will be lower)
- **Pass** (walk away, no trade)

Each trade is a binding contract. There's no limit on how many trades you
can make, and you can trade with the same person multiple times.

### What a Trade Means

Every trade is a bet on the final total:

- **Buyer** profits by `(Total - Price)` per trade
- **Seller** profits by `(Price - Total)` per trade

If the total is 68 and you bought at 62:
- You (buyer) make +6
- The seller makes -6

If the total is 68 and you sold at 70:
- You (seller) make +2
- The buyer makes -2

All P&L across all players always sums to zero.

---

## Game Phases

The game has **4 trading phases**, followed by settlement:

### Phase 1: Open

All 3 central cards are still face-down. Players trade based only on
their private card and their estimate of the total.

**Expected value with no information:**
With 8 cards in play from a deck averaging ~7.65 per card, the baseline
expected total is roughly **61** (8 x 7.65). Knowing your own card shifts
this estimate up or down.

### Phase 2: Reveal 1

One central card is flipped face-up. Everyone sees it. Trading continues
with this new information.

### Phase 3: Reveal 2

A second central card is flipped. Two of three central cards are now
visible. Estimates become more precise.

### Phase 4: Reveal 3

The third and final central card is flipped. All central cards are now
known. The only hidden information is each player's private card.

### Settlement

All private cards are revealed. The **total** (sum of all 8 cards) is
calculated. Every trade is settled:

```
Buyer P&L  = Total - Trade Price
Seller P&L = Trade Price - Total
```

Total P&L is summed across all trades for each player. The player with
the highest total P&L wins.

---

## Phase Structure (Each Phase)

Within each phase, three things happen in order:

### A. Bots Ask You (0-2 times)

A random bot approaches and asks: "What's your price?" You must provide
a two-way quote via the slider (bid/ask with spread of 2). The bot then
evaluates your quote and decides to:

- **Buy** at your ask (they think the total is higher)
- **Sell** at your bid (they think the total is lower)
- **Walk away** (your price isn't attractive to them)

### B. Your Turn (Free Trading)

You can approach any bot and ask for their quote. When they show you
their bid/ask, you can buy, sell, or pass. You can ask as many bots as
you want, in any order. When you're done, tap **Next**.

### C. Wind-Down (1-3 Rounds)

After you tap Next, trading doesn't stop immediately. There are 1-3
additional rounds where:

- Bots may ask you for one more quote (0-1 per round)
- Bots trade with each other
- You can observe (and participate if asked)

This simulates the ongoing market activity you can't fully control.

---

## The Bots

Four AI opponents with distinct personalities based on difficulty:

### SimpleBot (Used in Easy and Medium)

- Quotes based on **expected value (EV)** -- an honest estimate of the
  total, calculated from their private card and any revealed central cards
- Adds small random noise (+/-1) to avoid being perfectly predictable
- Trades when the price offers an edge > 1 point vs. their EV
- On Medium: adapts after detecting 5+ same-direction trades from you
  (if you keep buying, they raise their prices)

### StrategicBot (Used in Medium and Hard)

- **Bluffs**: quotes in the opposite direction of their hand
  - Holding a high card? Quotes LOW to suppress the market and buy cheap
  - Holding a low card? Quotes HIGH to inflate the market and sell expensive
- Bluff magnitude decreases as more cards are revealed:
  - Open: offset of 8 points
  - After 1 reveal: offset of 4
  - After 2+ reveals: offset of 1
- Decides on trades using their TRUE EV (not their bluff), so they won't
  actually lose money on bad trades
- Adapts aggressively after just 2 same-direction trades from you
- Limits to 3 trades per phase to avoid overexposure
- Targets opponents trading in the opposite direction

### Difficulty Levels

| Difficulty | Bot Composition | Adaptation |
|------------|----------------|------------|
| **Easy** | 4 SimpleBots | Never adapts -- honest prices throughout |
| **Medium** | 2 SimpleBots + 2 StrategicBots | Simple adapts after 5 streaks; Strategic adapts after 2 |
| **Hard** | 4 StrategicBots | All bluff; all adapt after 2 streaks |

### Bot Adaptation (BotTracker)

Bots remember how you've traded with them:

- **Direction tracking**: If you've been mostly buying, they mark you as
  "bullish" and may raise their prices
- **Direct shift**: Each trade with a bot shifts their future quotes for
  you by 2-6 points in the direction that's worse for you
- **Decay**: These shifts reduce by 2 points when a new phase starts
- **Pass with market read**: Even passing (after seeing their quote) can
  cause a small 1-2 point shift based on your trades with other bots

---

## Strategy Tips

### Basic Math

- Expected total with no info: ~61 (8 cards x mean of ~7.65)
- Your EV = (Your card) + (Revealed centrals) + (Unknown count x Pool mean)
- Pool mean = (130 - sum of all cards you've seen) / (17 - count of cards you've seen)

### Key Insights

1. **Your card shifts your estimate.** Holding the 20? Your EV is ~12 points
   higher than someone holding the 1.

2. **Card reveals reduce uncertainty.** Early trades are riskier. Later trades
   are more informed but offer less edge.

3. **Buy low, sell high relative to your EV.** If your EV is 65 and someone
   offers 60/62, buying at 62 gives you +3 expected profit.

4. **Watch for bluffers.** On Hard, bots quote opposite to their hand. If
   a bot quotes very low, they might hold a high card and want to buy cheap.

5. **Don't trade too much in one direction.** Bots adapt. If you keep buying
   from the same bot, they'll raise prices on you.

6. **The spread is your cost.** Every trade crosses a 2-point spread. You need
   at least 2 points of edge to break even.

### Gary's Arbitrage Proof

From the book: if you can buy at 52 from one player and sell at 67 to another,
you lock in +15 profit regardless of what the total turns out to be:

```
If total = 100: Buy P&L = +48, Sell P&L = -33, Net = +15
If total = 0:   Buy P&L = -52, Sell P&L = +67, Net = +15
```

This is always true. Arbitrage (buying low / selling high) is risk-free profit.

---

## Scoring and Stats

### Per-Game Scoring

Your total P&L is the sum of P&L from all your individual trades.
The leaderboard ranks all 5 players by total P&L (highest wins).

### Persistent Stats (Mobile and Web)

The game tracks per-difficulty statistics across sessions:

- **Games played / won**
- **Win rate** (% of games where you finished #1)
- **Best P&L** (single-game high score)
- **Average P&L** (mean across all games)
- **Current win streak / Best win streak**
- **Total trades made**

Stats can be reset from the Stats screen.

---

## Tutorial

A guided walkthrough with pre-set cards (total = 68) and scripted bot
behavior. It teaches:

1. Seeing your private card
2. Understanding expected value
3. Submitting a two-way quote when a bot asks
4. Buying, selling, and passing on bot quotes
5. Card reveals and how they change your estimate
6. Wind-down trading
7. Settlement and P&L calculation

The tutorial uses fixed RNG seeds and hardcoded bot quotes to ensure a
consistent learning experience.
