<p align="center">
  <img src="swift/MagCry/Resources/logo.png" alt="MagCry logo" width="400">
</p>

# MagCry

A card game based on [*The Trading Game*](https://www.amazon.com/Trading-Game-Confession-Gary-Stevenson/dp/0593727215) by Gary Stevenson. You play against 4 bots. Over 4 phases, central cards are revealed one at a time, and you trade contracts on what the sum of all 8 cards will be. The goal is to finish with the best P&L at settlement.

## Platforms

### Python CLI

The original version. Runs in the terminal.

- 3 difficulty levels: Easy (4 simple bots), Medium (2 simple + 2 strategic), Hard (4 strategic bots)
- "Ask for a price" quoting, wind-down phases, bot adaptation
- 7 test modules, 205 tests

```bash
python cli/main.py
```

### iOS (Swift / SwiftUI)

A native iOS app with a dark UI and the MagCry crow branding. Built with SwiftUI. The project file is generated from `swift/project.yml` using XcodeGen.

```bash
cd swift && xcodegen generate
```

### Android (Kotlin / Jetpack Compose)

A Kotlin port using Jetpack Compose. Dark Material 3 theme. Unit tests cover deck, trading, and bot logic.

```bash
cd kotlin && ./gradlew build
```

### Web (PWA)

Plain HTML, JS, and CSS. No framework, no build step. Works offline as a Progressive Web App.

- 20-step scripted tutorial
- 6-page instructions overlay
- Trade log
- 163 tests that run in the browser with zero dependencies

```bash
cd web && python -m http.server 8000
```

### Solver

An Excel spreadsheet (`model/trading_game_solver.xlsx`) and a Python script that generates it. Used for analysing card-sum probabilities and trading strategies.

## Project Structure

```
.
├── cli/                     # Python CLI
│   ├── main.py              # Entry point
│   ├── game/                # Deck, state, trading, scoring
│   ├── bots/                # Simple + strategic bot AI
│   └── tests/               # 7 modules, 205 tests
├── swift/
│   ├── project.yml          # XcodeGen spec
│   ├── MagCry/
│   │   ├── Models/          # Game engine in Swift
│   │   ├── ViewModels/
│   │   ├── Views/           # SwiftUI screens
│   │   └── Resources/       # Logo, images
│   └── MagCryTests/
├── kotlin/
│   ├── app/src/main/java/com/magcry/
│   │   ├── model/           # Game engine in Kotlin
│   │   ├── viewmodel/
│   │   └── ui/              # Compose screens
│   └── app/src/test/
├── web/
│   ├── index.html
│   ├── style.css
│   ├── js/                  # ES modules
│   ├── sw.js                # Service worker
│   └── tests/               # 163 browser tests
└── model/                   # Excel solver + generator script
```
