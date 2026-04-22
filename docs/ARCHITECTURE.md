# MagCry -- Architecture Overview

A single trading-card game implemented across five folders in one monorepo.
Each folder is a standalone build target; they share no code at the source level
but implement identical game logic, deck math, bot AI, and trading rules.

```
gary-game/
  cli/        Python 3 terminal game
  web/        Vanilla JS progressive web app
  swift/      SwiftUI iOS app
  kotlin/     Jetpack Compose Android app
  model/      Python spreadsheet generator (decision-support tool)
```

---

## 1. Shared Design

Every implementation follows the same structure:

| Layer | Responsibility |
|-------|---------------|
| **Deck / RNG** | 17-card deck definition, seeded PRNG, dealing logic, expected-value math |
| **Game State** | Mutable container: players, cards, trades, phase progression |
| **Trading** | Quote validation (spread = 2), trade execution, error handling |
| **Scoring** | Settlement P&L, leaderboard sorting, per-trade breakdown |
| **Bot AI** | `SimpleBot` (EV-based) and `StrategicBot` (bluffing), both with opponent tracking |
| **Game Loop** | Async/coroutine-driven phase loop that suspends at user-input points |
| **Tutorial** | Scripted 17-21 step walkthrough with fixed cards and hardcoded bot quotes |
| **Score Store** | Persistent per-difficulty stats (best P&L, win rate, streaks) |
| **UI** | Platform-native rendering of menu, gameplay, settlement, instructions, stats |

### Deck

17 unique cards: `[-10, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 20]`

- Sum = 130, mean ~ 7.647
- 8 cards in play per game: 5 private (one per player) + 3 central (face-down)
- Remaining 9 cards are discarded unseen

### PRNG

All platforms use a seeded **xorshift64*** generator (Python uses stdlib `random`).
`child()` derives independent sub-RNGs for per-bot randomness isolation.

### Phases

```
OPEN --> REVEAL_1 --> REVEAL_2 --> REVEAL_3 --> SETTLE
```

At each REVEAL phase one central card is flipped face-up before trading begins.

### Bot Difficulty

| Difficulty | Bots |
|------------|------|
| Easy | 4 SimpleBot, never adapt |
| Medium | 2 SimpleBot (adapt after 5 streaks) + 2 StrategicBot |
| Hard | 4 StrategicBot (adapt after 2 streaks, aggressive bluffing) |

---

## 2. `cli/` -- Python CLI

**Language:** Python 3.14, zero external dependencies (pytest for tests)

### File Map

```
cli/
  main.py              Entry point + game loop (623 lines)
  game/
    __init__.py
    state.py            GameState, Trade, Quote, Phase, Difficulty
    deck.py             Deck, deal(), expectedTotal()
    trading.py          validateQuote(), executeTradeDirectly()
    scoring.py          settle(), leaderboard(), tradeBreakdown()
    display.py          ANSI terminal output formatting (307 lines)
  bots/
    __init__.py
    base.py             Abstract Bot base class + opponent tracking
    simple.py           SimpleBot -- EV-based
    strategic.py        StrategicBot -- bluffing
  tests/                7 pytest test files
```

### How It Works

- `main.py` runs an interactive REPL loop
- Player types commands: `ask alice`, `buy alice`, `sell bob`, `status`, `next`
- Bot-asks-human flow: bot requests a quote, player must respond
- Wind-down rounds after "next" with bot-to-bot trading
- All output via ANSI escape codes for colors and formatting

### Running

```bash
cd cli
python main.py          # play the game
pytest                  # run tests
```

---

## 3. `web/` -- Vanilla JS PWA

**Language:** HTML5 + CSS3 + vanilla ES modules. No framework, no bundler, no npm.

### File Map

```
web/
  index.html            Single-page app shell (all screens as toggled divs)
  style.css             Dark-theme mobile-first stylesheet (1323 lines)
  manifest.json         PWA manifest (installable, standalone)
  sw.js                 Service worker (cache-first offline support)
  icons/
    icon-192.png        PWA icon
    icon-512.png        PWA icon
    logo.png            Menu logo (crow artwork)
    bmc-button.png      Buy Me a Coffee button
  js/
    app.js              Main controller + DOM binding (1189 lines)
    state.js            GameState, Trade, Quote, Phase, Difficulty
    deck.js             Deck, SeededRandom (mulberry32), deal()
    gameLoop.js         Async GameEngine (370 lines)
    trading.js          Quote validation, trade execution
    scoring.js          settle(), leaderboard()
    bot.js              Abstract Bot base class
    simpleBot.js        SimpleBot
    strategicBot.js     StrategicBot
    tutorial.js         20-step scripted tutorial
    scoreStore.js       localStorage-backed persistent stats
  tests/
    test.html           Browser test harness
    tests.js            Comprehensive test suite (~1400 lines)
```

### How It Works

- Single HTML page with screen `<div>`s toggled via CSS `.active`/`.hidden`
- `GameEngine` runs an async loop, communicating with UI through a callback object
- User input resolves Promises (coroutine-style flow, no callback spaghetti)
- CSS 3D transforms for card flip animations
- Service worker caches all assets for offline play

### Running

```bash
cd web
python -m http.server 8000    # or any static server
# open http://localhost:8000
# tests: open tests/test.html in browser
```

---

## 4. `swift/` -- SwiftUI iOS App

**Language:** Swift 5.9, SwiftUI + Observation framework
**Target:** iOS 17.0+, portrait iPhone only
**Project:** XcodeGen (`project.yml` generates `.xcodeproj`)
**Dependencies:** None (pure Apple frameworks)

### File Map

```
swift/MagCry/
  MagCryApp.swift                   @main entry, screen routing
  Models/
    GameState.swift                 GameState, Trade, Quote, Phase, Difficulty
    Deck.swift                      Deck, GameRNG (xorshift64*), deal()
    Bot.swift                       Bot protocol, BotTracker
    SimpleBot.swift                 EV-based bot
    StrategicBot.swift              Bluffing bot
    Trading.swift                   Quote validation, trade execution
    Scoring.swift                   Settlement, leaderboard
    ScoreStore.swift                UserDefaults-backed persistent stats
    TutorialManager.swift           21-step scripted tutorial state machine
  ViewModels/
    GameViewModel.swift             Central coordinator (870 lines)
  Views/
    MainMenuView.swift              Logo, difficulty buttons, links
    GameView.swift                  Gameplay screen (header, cards, interaction area)
    SettlementView.swift            End-of-round results + animations
    CentralCardsView.swift          3D card flip animation
    BotQuoteView.swift              Bot bid/ask + Buy/Sell/Pass buttons
    QuoteInputView.swift            Slider-based quote input
    InstructionsView.swift          6-page How to Play
    TutorialCoachView.swift         Floating coach bubble overlay
    TradeLogView.swift              Trade history sheet
    StatsView.swift                 Per-difficulty stats grid
  Resources/
    logo.png                        Crow artwork (1536x650)
    bmc-button.png                  Buy Me a Coffee
swift/MagCryTests/
    DeckTests.swift                 25 tests
    TradingTests.swift              42 tests
    BotTests.swift                  48 tests
    ScoreStoreTests.swift           17 tests
```

### Key Pattern: CheckedContinuation

The ViewModel runs the game as a single `async` function. At each user-input
point (quote submission, "Next" button, wind-down continue), it suspends via
`CheckedContinuation`. The UI resumes the continuation when the user acts.
This gives a clean sequential game loop without callback nesting.

```swift
// Simplified example
let quote = await withCheckedContinuation { cont in
    self.quoteContinuation = cont
}
// ...UI calls: vm.quoteContinuation?.resume(returning: quote)
```

### Building

```bash
cd swift
xcodegen generate          # generates MagCry.xcodeproj
open MagCry.xcodeproj      # build + run in Xcode
# or: xcodebuild -scheme MagCry -destination 'platform=iOS Simulator,...'
```

---

## 5. `kotlin/` -- Jetpack Compose Android App

**Language:** Kotlin 2.1, Jetpack Compose + Material 3
**Target:** minSdk 24 (Android 7), targetSdk 35
**Build:** Gradle 8.9, AGP 8.7.3, Compose BOM 2024.12.01
**Tests:** JUnit 5 (Jupiter)

### File Map

```
kotlin/app/src/main/java/com/magcry/
  MainActivity.kt                    Single Activity entry point
  model/
    GameState.kt                     GameState, Trade, Quote, Phase, Difficulty
    Deck.kt                          Deck constants, deal()
    GameRNG.kt                       Seeded xorshift64* PRNG
    Bot.kt                           Bot interface, BotTracker
    SimpleBot.kt                     EV-based bot
    StrategicBot.kt                  Bluffing bot
    Trading.kt                       Quote validation, trade execution
    Scoring.kt                       Settlement, leaderboard
    ScoreStore.kt                    SharedPreferences-backed persistent stats
    TutorialManager.kt              17-step scripted tutorial state machine
  viewmodel/
    GameViewModel.kt                 Central coordinator (817 lines) + LogEntry/LogKind
  ui/
    MagCryApp.kt                     Root composable, screen routing
    MainMenuScreen.kt                Logo, difficulty buttons, links
    GameScreen.kt                    Gameplay screen
    SettlementScreen.kt              End-of-round results + animations
    StatsScreen.kt                   Per-difficulty stats grid
    CentralCardsView.kt             3D card flip (graphicsLayer { rotationY })
    BotQuoteView.kt                  Bot bid/ask + Buy/Sell/Pass buttons
    QuoteInputView.kt               Slider-based quote input
    TradeLogSheet.kt                 Trade history bottom sheet
    InstructionsSheet.kt            6-page How to Play (HorizontalPager)
    TutorialCoachView.kt            Floating coach bubble overlay
    theme/
      Theme.kt                       Dark Material3 theme
kotlin/app/src/main/res/
  drawable-nodpi/                    logo.png, bmc_button.png
  mipmap-*/                          Adaptive launcher icons (foreground + background)
  values/                            colors.xml, themes.xml
kotlin/app/src/test/java/com/magcry/
  DeckTests.kt                      23 tests
  TradingTests.kt                   38 tests
  BotTests.kt                       42 tests
```

### Key Pattern: suspendCancellableCoroutine

Kotlin equivalent of Swift's `CheckedContinuation`. The ViewModel launches the
game loop in `viewModelScope` and suspends at each user-input point:

```kotlin
// Simplified example
val quote = suspendCancellableCoroutine<Quote> { cont ->
    quoteContinuation = cont
}
// ...UI calls: vm.quoteContinuation?.resume(quote)
```

### Building

```bash
cd kotlin
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug    # build APK
./gradlew test             # run 103 unit tests
```

---

## 6. `model/` -- Spreadsheet Generator

**Language:** Python 3 + openpyxl

### File Map

```
model/
  generate_sheet.py           Generates the spreadsheet (439 lines)
  trading_game_solver.xlsx    Formula-only Excel solver
```

### What It Does

Generates a decision-support spreadsheet for playing the trading game live:

| Tab | Purpose |
|-----|---------|
| **GAME** | Input your card + central cards + opponent quotes. Outputs: fair EV, recommended bid/ask, BUY/SELL/WALK recommendation |
| **INFERENCE** | Bayesian opponent card inference. Gaussian likelihood model with truthful vs. bluff hypothesis mixing |
| **REFERENCE** | Deck data, tunable parameters (sigma, bluff offset, bluff prior), usage instructions |

Core formula: `Fair Total = KnownSum + Unknowns x RemainingPoolMean`

---

## Cross-Platform Mapping

How the same concept maps across platforms:

| Concept | CLI (Python) | Web (JS) | Swift | Kotlin |
|---------|-------------|----------|-------|--------|
| Entry point | `main.py` | `app.js` | `MagCryApp.swift` | `MainActivity.kt` |
| Game state | `GameState` class | `GameState` class | `GameState` class | `GameState` class |
| PRNG | `random.Random` | `SeededRandom` (mulberry32) | `GameRNG` (xorshift64*) | `GameRNG` (xorshift64*) |
| Async suspension | N/A (blocking input) | `Promise` resolve | `CheckedContinuation` | `suspendCancellableCoroutine` |
| Persistent stats | N/A | `localStorage` | `UserDefaults` | `SharedPreferences` |
| Screen routing | N/A (sequential) | CSS class toggle | `vm.screen` enum | `vm.screen` sealed class |
| Card flip animation | N/A | CSS 3D transform | `rotation3DEffect` | `graphicsLayer { rotationY }` |
| Tutorial steps | N/A | 20 steps | 21 steps | 17 steps |
| Test framework | pytest | Custom browser suite | XCTest | JUnit 5 |
| Test count | ~50 | ~60 | 132 | 103 |

---

## Test Coverage Summary

| Platform | Deck | Trading | Bots | ScoreStore | Total |
|----------|------|---------|------|------------|-------|
| CLI | yes | yes | yes | N/A | ~50 |
| Web | yes | yes | yes | N/A | ~60 |
| Swift | 25 | 42 | 48 | 17 | 132 |
| Kotlin | 23 | 38 | 42 | -- | 103 |

All tests are pure logic tests (no UI/integration tests). The game math is
verified to be zero-sum across all players in every test suite.
