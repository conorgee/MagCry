<p align="center">
  <img src="swift/MagCry/Resources/logo.png" alt="MagCry logo" width="400">
</p>

# MagCry — The Trading Game

A card-trading game inspired by [*The Trading Game*](https://www.amazon.com/Trading-Game-Confession-Gary-Stevenson/dp/0593727215) by Gary Stevenson. Players trade against AI bots across four phases as central cards are gradually revealed, trying to maximise P&L at settlement.

## Platforms

### Python CLI

The original implementation — a fully interactive terminal game.

- **3 difficulty levels**: Easy (4 simple bots), Medium (2 simple + 2 strategic), Hard (4 strategic bots with aggressive adaptation)
- **Book-accurate mechanics**: "ask for a price" quoting, wind-down trading phases, bot adaptation
- **Full test suite** with 7 test modules (205 tests)

```bash
python cli/main.py
```

### iOS (Swift / SwiftUI)

A native iOS port with the same game engine and a dark-themed UI.

- SwiftUI views with the MagCry crow branding
- Same bot AI and trading logic ported to Swift
- XcodeGen project (`swift/project.yml`)

```bash
cd swift && xcodegen generate
```

### Android (Kotlin / Jetpack Compose)

An Android port using Kotlin and Jetpack Compose.

- Material 3 dark theme matching the iOS app
- Same game engine (deck, trading, scoring, bots) ported to Kotlin
- Unit tests for deck, trading, and bot logic

```bash
cd kotlin && ./gradlew build
```

### Web (PWA)

A browser-based port — vanilla HTML/JS/CSS, no framework, no build step. Installable as a Progressive Web App.

- Dark theme matching the native apps
- 20-step scripted tutorial, 6-page instructions overlay, trade log
- Offline support via service worker
- 163 browser-based tests (zero dependencies)

Serve locally or deploy to any static host (e.g. GitHub Pages):

```bash
cd web && python -m http.server 8000
```

### Solver

An Excel-based solver (`model/trading_game_solver.xlsx`) with a Python generator script for analysing optimal strategies.

## Project Structure

```
.
├── cli/                     # Python CLI
│   ├── main.py              # Entry point
│   ├── game/                # Core engine (deck, state, trading, scoring)
│   ├── bots/                # Bot AI (simple + strategic)
│   └── tests/               # Python test suite (7 modules, 205 tests)
├── swift/
│   ├── project.yml          # XcodeGen spec
│   ├── MagCry/              # iOS app (SwiftUI)
│   │   ├── Models/          # Game engine (Swift port)
│   │   ├── ViewModels/      # GameViewModel
│   │   ├── Views/           # SwiftUI screens
│   │   └── Resources/       # Logo, assets
│   └── MagCryTests/         # Swift test suite
├── kotlin/
│   ├── app/src/main/java/com/magcry/
│   │   ├── model/           # Game engine (Kotlin port)
│   │   ├── viewmodel/       # GameViewModel
│   │   └── ui/              # Jetpack Compose screens
│   └── app/src/test/        # Kotlin unit tests
├── web/
│   ├── index.html           # Single-page app
│   ├── style.css            # Dark theme styles
│   ├── js/                  # ES modules (game engine + app controller)
│   ├── sw.js                # Service worker (offline PWA)
│   └── tests/               # Browser test suite (163 tests)
└── model/                   # Excel solver + Python generator
```
