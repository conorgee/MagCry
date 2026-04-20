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
- **Full test suite** with 7 test modules

```bash
python main.py
```

### iOS (Swift / SwiftUI)

A native iOS port with the same game engine and a dark-themed UI.

- SwiftUI views with the MagCry crow branding
- Same bot AI and trading logic ported to Swift
- XcodeGen project (`swift/project.yml`)

Open `swift/MagCry.xcodeproj` in Xcode or regenerate with:

```bash
cd swift && xcodegen generate
```

### Solver

An Excel-based solver (`model/trading_game_solver.xlsx`) with a Python generator script for analysing optimal strategies.

## Project Structure

```
.
├── main.py                  # CLI entry point
├── game/                    # Core engine (deck, state, trading, scoring)
├── bots/                    # Bot AI (simple + strategic)
├── tests/                   # Python test suite
├── model/                   # Excel solver + generator
└── swift/
    ├── MagCry/              # iOS app (SwiftUI)
    │   ├── Models/          # Game engine (Swift port)
    │   ├── ViewModels/      # GameViewModel
    │   ├── Views/           # SwiftUI screens
    │   └── Resources/       # Logo asset
    └── MagCryTests/         # Swift test suite
```
