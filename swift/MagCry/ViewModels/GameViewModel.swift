import Foundation
import Observation

/// Central game coordinator. Runs the entire game loop as a sequential async Task,
/// pausing at user-input points via CheckedContinuation. All state mutations happen
/// on @MainActor so SwiftUI can observe them safely.
@Observable
@MainActor
final class GameViewModel {

    // MARK: - Screen & Interaction State

    enum Screen {
        case mainMenu
        case playing
        case settlement
    }

    /// What the UI should currently show during gameplay.
    enum PlayingState: Equatable {
        case idle
        case playerTurn                                 // Free trading (ask/buy/sell/next)
        case botAsksYou(botName: String)                // Slider visible — bot wants a quote
        case botDecided(botName: String, action: String) // Brief result: "buys at 55" / "walks away"
        case windDownTurn                               // Mini trading window after bot asks in wind-down
        case botsTrading                                // Bots trading amongst themselves
    }

    // MARK: - Published State

    var screen: Screen = .mainMenu
    var playingState: PlayingState = .idle

    // Game data
    private(set) var gameState: GameState?
    private(set) var difficulty: Difficulty = .easy
    private(set) var bots: [any Bot] = []
    private(set) var botMap: [String: any Bot] = [:]

    // Player info
    private(set) var playerCard: Int = 0

    /// The single active quote the player is currently viewing (one bot at a time).
    var activeQuote: (botName: String, quote: Quote)?

    /// Brief result message after player acts on a quote (auto-clears after ~1s).
    var lastActionResult: String?

    /// Controls the History sheet presentation.
    var showHistory: Bool = false

    /// Show quit confirmation alert.
    var showQuitAlert: Bool = false

    // Log
    private(set) var log: [LogEntry] = []

    // Settlement
    private(set) var scores: [String: Int] = [:]
    private(set) var finalTotal: Int = 0

    // Tutorial
    private(set) var tutorialManager: TutorialManager?
    private(set) var isTutorial: Bool = false

    // Persistent stats
    var scoreStore: ScoreStore = ScoreStore()

    // MARK: - Private

    private var rng: GameRNG!
    private var quoteContinuation: CheckedContinuation<Quote, Never>?
    private var nextContinuation: CheckedContinuation<Void, Never>?
    private var windDownContinuation: CheckedContinuation<Void, Never>?
    private var gameTask: Task<Void, Never>?

    // Constants
    static let humanID = "You"
    static let botNames = ["Alice", "Bob", "Carol", "Dave"]

    // Tutorial constants
    private static let tutorialPrivateCards: [String: Int] = [
        "You": 12, "Alice": 5, "Bob": 8, "Carol": 7, "Dave": 6
    ]
    private static let tutorialCentralCards = [10, 11, 9]
    // Total = 12 + 5 + 8 + 7 + 6 + 10 + 11 + 9 = 68

    private static let tutorialAliceQuote = Quote(bid: 59, ask: 61)
    private static let tutorialBobQuote = Quote(bid: 70, ask: 72)

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: - Game Setup
    // ═══════════════════════════════════════════════════════════════════════

    func startGame(difficulty: Difficulty) {
        self.tutorialManager = nil
        self.isTutorial = false
        self.difficulty = difficulty
        self.rng = GameRNG()

        // Create bots
        self.bots = Self.makeBots(difficulty: difficulty, rng: rng)
        self.botMap = Dictionary(uniqueKeysWithValues: bots.map { ($0.playerID, $0) })

        // Deal cards
        let playerIDs = [Self.humanID] + Self.botNames
        let (privateCards, centralCards) = Deck.deal(playerIDs: playerIDs, rng: rng)

        self.gameState = GameState(
            playerIDs: playerIDs,
            privateCards: privateCards,
            centralCards: centralCards
        )

        self.playerCard = privateCards[Self.humanID]!
        self.activeQuote = nil
        self.lastActionResult = nil
        self.log = []
        self.scores = [:]
        self.finalTotal = 0

        self.screen = .playing

        // Launch the game loop
        gameTask = Task { await runGame() }
    }

    /// Start a scripted tutorial — fixed cards, fixed quotes, no randomness.
    func startTutorial() {
        self.isTutorial = true
        self.difficulty = .easy
        self.rng = GameRNG()

        // Create simple bots (they won't actually be used for quotes, but needed for structure)
        self.bots = Self.makeBots(difficulty: .easy, rng: rng)
        self.botMap = Dictionary(uniqueKeysWithValues: bots.map { ($0.playerID, $0) })

        // Fixed predetermined cards
        let playerIDs = [Self.humanID] + Self.botNames
        self.gameState = GameState(
            playerIDs: playerIDs,
            privateCards: Self.tutorialPrivateCards,
            centralCards: Self.tutorialCentralCards
        )

        self.playerCard = Self.tutorialPrivateCards[Self.humanID]!
        self.activeQuote = nil
        self.lastActionResult = nil
        self.log = []
        self.scores = [:]
        self.finalTotal = 0
        self.tutorialManager = TutorialManager()

        self.screen = .playing

        // Launch the scripted tutorial loop
        gameTask = Task { await runTutorial() }
    }

    private static func makeBots(difficulty: Difficulty, rng: GameRNG) -> [any Bot] {
        switch difficulty {
        case .easy:
            return botNames.map { SimpleBot(playerID: $0, rng: rng.child()) }
        case .medium:
            return [
                SimpleBot(playerID: "Alice", rng: rng.child(), adaptationThreshold: 5),
                SimpleBot(playerID: "Bob", rng: rng.child(), adaptationThreshold: 5),
                StrategicBot(playerID: "Carol", rng: rng.child()),
                StrategicBot(playerID: "Dave", rng: rng.child()),
            ]
        case .hard:
            return botNames.map { StrategicBot(playerID: $0, rng: rng.child()) }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: - Quit Game
    // ═══════════════════════════════════════════════════════════════════════

    /// Cancel the running game/tutorial and return to menu.
    func quitGame() {
        // Cancel the game task
        gameTask?.cancel()
        gameTask = nil

        // Resume any stuck continuations so they don't leak
        quoteContinuation?.resume(returning: Quote(bid: 0, ask: 2))
        quoteContinuation = nil
        nextContinuation?.resume()
        nextContinuation = nil
        windDownContinuation?.resume()
        windDownContinuation = nil

        // Reset state
        tutorialManager = nil
        isTutorial = false
        gameState = nil
        activeQuote = nil
        lastActionResult = nil
        playingState = .idle

        screen = .mainMenu
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: - Main Game Loop
    // ═══════════════════════════════════════════════════════════════════════

    private func runGame() async {
        guard let state = gameState else { return }

        let phases: [Phase] = [.open, .reveal1, .reveal2, .reveal3]

        for phase in phases {
            // Check for cancellation at each phase boundary
            if Task.isCancelled { return }

            state.phase = phase

            // Reveal central card for reveal phases
            if phase != .open {
                let revealIdx = [Phase.reveal1, .reveal2, .reveal3].firstIndex(of: phase)!
                if revealIdx == state.revealedCentral.count {
                    let card = state.centralCards[revealIdx]
                    state.revealedCentral.append(card)
                    addLog(.cardReveal(index: revealIdx + 1, card: card))

                    // Notify bots of phase change
                    for bot in bots {
                        bot.onPhaseChange(state: state)
                        bot.tracker.decayDirectShifts()
                    }
                }
            }

            addLog(.phaseChange(label: phase.label))
            await runPhase(state: state)
        }

        if Task.isCancelled { return }

        // Settlement
        state.phase = .settle
        self.finalTotal = state.finalTotal()
        self.scores = settle(state: state)

        // Record stats (skip tutorials)
        if !isTutorial {
            let sorted = leaderboard(scores: scores)
            let playerPnL = scores[Self.humanID] ?? 0
            let rank = (sorted.firstIndex(where: { $0.0 == Self.humanID }) ?? sorted.count) + 1
            let playerTrades = state.trades.filter {
                $0.buyer == Self.humanID || $0.seller == Self.humanID
            }.count
            scoreStore.record(difficulty: difficulty, pnl: playerPnL, rank: rank, tradeCount: playerTrades)
        }

        self.screen = .settlement
    }

    /// Run one complete trading phase: bots ask → human trades → wind-down.
    private func runPhase(state: GameState) async {
        if Task.isCancelled { return }

        // Phase A: Bots ask human for prices (0-2)
        _ = await runBotsAskHuman(state: state, askRange: 0...2)

        if Task.isCancelled { return }

        // Phase B: Human trades freely until they tap "Next"
        playingState = .playerTurn
        activeQuote = nil
        lastActionResult = nil
        await awaitPlayerNext()

        if Task.isCancelled { return }

        // Phase C: Wind-down
        await runWindDown(state: state)
    }

    // MARK: - Phase A: Bots Ask Human

    /// Some bots ask the human for a two-way price. Returns how many actually asked.
    @discardableResult
    private func runBotsAskHuman(state: GameState, askRange: ClosedRange<Int>) async -> Int {
        let nAskers = rng.nextInt(in: askRange)
        guard nAskers > 0 else { return 0 }

        let askers = rng.sample(bots, count: min(nAskers, bots.count))

        for bot in askers {
            if Task.isCancelled { return 0 }

            addLog(.botAsksYou(botName: bot.playerID))

            // Adaptation warning
            let streak = bot.tracker.opponentSameDirectionStreak(Self.humanID)
            let direction = bot.tracker.opponentDirection(Self.humanID)
            if streak >= 3 && direction != "neutral" {
                addLog(.info(message: "\(bot.playerID) seems to have read your \(direction) pattern.", important: false))
            }

            // Show slider — wait for user to submit a quote
            playingState = .botAsksYou(botName: bot.playerID)
            let quote = await awaitUserQuote()

            if Task.isCancelled { return 0 }

            // Bot evaluates the human's quote
            let decision = bot.decideOnQuote(state: state, quoter: Self.humanID, quote: quote)

            if let decision = decision {
                if decision == "buy" {
                    // Bot buys at ask
                    try? executeTradeDirectly(
                        state: state, buyer: bot.playerID, seller: Self.humanID, price: quote.ask)
                    recordTradeForAllBots(buyer: bot.playerID, seller: Self.humanID)
                    // Bot bought from human → human sold → opponentBought=false
                    bot.tracker.recordDirectTrade(opponent: Self.humanID, opponentBought: false)
                    let msg = "buys at \(quote.ask)"
                    addLog(.botBuys(botName: bot.playerID, price: quote.ask))
                    playingState = .botDecided(botName: bot.playerID, action: msg)
                } else {
                    // Bot sells at bid
                    try? executeTradeDirectly(
                        state: state, buyer: Self.humanID, seller: bot.playerID, price: quote.bid)
                    recordTradeForAllBots(buyer: Self.humanID, seller: bot.playerID)
                    // Bot sold to human → human bought → opponentBought=true
                    bot.tracker.recordDirectTrade(opponent: Self.humanID, opponentBought: true)
                    let msg = "sells at \(quote.bid)"
                    addLog(.botSells(botName: bot.playerID, price: quote.bid))
                    playingState = .botDecided(botName: bot.playerID, action: msg)
                }
            } else {
                addLog(.botWalks(botName: bot.playerID))
                playingState = .botDecided(botName: bot.playerID, action: "walks away")
            }

            await shortDelay()
        }

        return askers.count
    }

    // MARK: - Wind-Down

    /// After human taps "Next", 1-3 rounds of bot trading. Bots may still ask human.
    private func runWindDown(state: GameState) async {
        let nRounds = rng.nextInt(in: 1...3)
        addLog(.info(message: "Wind-down: \(nRounds) round(s)...", important: false))

        for round in 1...nRounds {
            if Task.isCancelled { return }

            addLog(.info(message: "Round \(round)/\(nRounds)", important: false))

            playingState = .botsTrading
            await shortDelay()

            // Bot may ask human during wind-down (0-1)
            let nAsked = await runBotsAskHuman(state: state, askRange: 0...1)

            // If a bot asked, give human a mini trading window
            if nAsked > 0 {
                playingState = .windDownTurn
                activeQuote = nil
                lastActionResult = nil
                addLog(.info(message: "Quick -- you can trade back.", important: true))
                await awaitWindDownContinue()
            }

            if Task.isCancelled { return }

            // Bot-to-bot trading
            playingState = .botsTrading
            await runBotToBot(state: state)
            await shortDelay()
        }
    }

    // MARK: - Bot-to-Bot Trading

    private func runBotToBot(state: GameState) async {
        for bot in bots {
            guard let targetID = bot.decideAction(state: state) else { continue }
            guard targetID != Self.humanID else { continue }
            guard targetID != bot.playerID else { continue }
            guard let targetBot = botMap[targetID] else { continue }

            let quote = targetBot.getQuote(state: state, requester: bot.playerID)
            let decision = bot.decideOnQuote(state: state, quoter: targetID, quote: quote)

            if decision == "buy" {
                if (try? executeTradeDirectly(
                    state: state, buyer: bot.playerID, seller: targetID, price: quote.ask)) != nil {
                    recordTradeForAllBots(buyer: bot.playerID, seller: targetID)
                    addLog(.botTrade(buyer: bot.playerID, seller: targetID, price: quote.ask))
                }
            } else if decision == "sell" {
                if (try? executeTradeDirectly(
                    state: state, buyer: targetID, seller: bot.playerID, price: quote.bid)) != nil {
                    recordTradeForAllBots(buyer: targetID, seller: bot.playerID)
                    addLog(.botTrade(buyer: targetID, seller: bot.playerID, price: quote.bid))
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: - Scripted Tutorial Loop
    // ═══════════════════════════════════════════════════════════════════════

    /// Runs the fully scripted tutorial across all 4 phases.
    /// No randomness, no wind-down, no bot-to-bot trading.
    /// Phase flow: Open → Reveal1 → Reveal2 (Carol asks you) → Reveal3 → Settlement.
    private func runTutorial() async {
        guard let state = gameState else { return }

        // ── Phase: Open Trading ──
        state.phase = .open
        addLog(.phaseChange(label: Phase.open.label))
        playingState = .playerTurn
        activeQuote = nil
        lastActionResult = nil
        // Coach steps: welcome → seeCard → askAlice → aliceQuote →
        //              boughtResult → canKeepTrading → tapNext1 → [Next tapped]
        await awaitPlayerNext()

        if Task.isCancelled { return }

        // ── Phase: Reveal 1 (card 10) ──
        state.phase = .reveal1
        state.revealedCentral.append(state.centralCards[0])
        addLog(.cardReveal(index: 1, card: state.centralCards[0]))
        addLog(.phaseChange(label: Phase.reveal1.label))
        playingState = .playerTurn
        activeQuote = nil
        lastActionResult = nil
        // Coach steps: cardRevealed1 → askBob → bobQuote →
        //              soldResult → historyTip → tapNext2 → [Next tapped]
        await awaitPlayerNext()

        if Task.isCancelled { return }

        // ── Phase: Reveal 2 (card 11) — Carol asks you ──
        state.phase = .reveal2
        state.revealedCentral.append(state.centralCards[1])
        addLog(.cardReveal(index: 2, card: state.centralCards[1]))
        addLog(.phaseChange(label: Phase.reveal2.label))

        // Carol asks for a price — show slider immediately
        addLog(.botAsksYou(botName: "Carol"))
        playingState = .botAsksYou(botName: "Carol")
        // Coach step: botAsksYou ("Card 11 revealed! Carol wants YOUR price...")
        _ = await awaitUserQuote()

        if Task.isCancelled { return }

        // Carol walks away (always, for simplicity)
        addLog(.botWalks(botName: "Carol"))
        playingState = .botDecided(botName: "Carol", action: "walks away")
        try? await Task.sleep(for: .milliseconds(1500))

        if Task.isCancelled { return }

        // Player turn for remaining coach steps
        playingState = .playerTurn
        activeQuote = nil
        lastActionResult = nil
        // Coach steps: botAsksResult → tapNext3 → [Next tapped]
        await awaitPlayerNext()

        if Task.isCancelled { return }

        // ── Phase: Reveal 3 (card 9) ──
        state.phase = .reveal3
        state.revealedCentral.append(state.centralCards[2])
        addLog(.cardReveal(index: 3, card: state.centralCards[2]))
        addLog(.phaseChange(label: Phase.reveal3.label))
        playingState = .playerTurn
        activeQuote = nil
        lastActionResult = nil
        // Coach steps: cardRevealed3 → tapNext4 → [Next tapped]
        await awaitPlayerNext()

        if Task.isCancelled { return }

        // ── Settlement ──
        state.phase = .settle
        self.finalTotal = state.finalTotal()
        self.scores = settle(state: state)
        self.tutorialManager?.skip()
        self.screen = .settlement
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: - Player Actions (called by Views)
    // ═══════════════════════════════════════════════════════════════════════

    /// Player asks a bot for a price. Shows single quote — one at a time.
    func askBot(_ botName: String) {
        guard let state = gameState else { return }

        // In tutorial, use hardcoded quotes
        let quote: Quote
        if isTutorial {
            switch botName {
            case "Alice": quote = Self.tutorialAliceQuote
            case "Bob":   quote = Self.tutorialBobQuote
            default:      return  // Only Alice and Bob are allowed in tutorial
            }
        } else {
            guard let bot = botMap[botName] else { return }
            quote = bot.getQuote(state: state, requester: Self.humanID)
        }

        activeQuote = (botName: botName, quote: quote)
        lastActionResult = nil
        addLog(.yourQuote(botName: botName, bid: quote.bid, ask: quote.ask))
        tutorialManager?.advance(trigger: .botAsked(botName))
    }

    /// Player buys from the currently quoted bot at their ask price.
    func buyFromActive() {
        guard let state = gameState,
              let active = activeQuote else { return }
        do {
            try executeTradeDirectly(
                state: state, buyer: Self.humanID, seller: active.botName, price: active.quote.ask)
            if !isTutorial {
                recordTradeForAllBots(buyer: Self.humanID, seller: active.botName)
                if let bot = botMap[active.botName] {
                    bot.tracker.recordDirectTrade(opponent: Self.humanID, opponentBought: true)
                }
            }
            addLog(.yourBuy(botName: active.botName, price: active.quote.ask))
            showActionResult("Bought from \(active.botName) at \(active.quote.ask)")
            tutorialManager?.advance(trigger: .bought)
        } catch {
            addLog(.info(message: "Error: \(error.localizedDescription)", important: false))
        }
    }

    /// Player sells to the currently quoted bot at their bid price.
    func sellToActive() {
        guard let state = gameState,
              let active = activeQuote else { return }
        do {
            try executeTradeDirectly(
                state: state, buyer: active.botName, seller: Self.humanID, price: active.quote.bid)
            if !isTutorial {
                recordTradeForAllBots(buyer: active.botName, seller: Self.humanID)
                if let bot = botMap[active.botName] {
                    bot.tracker.recordDirectTrade(opponent: Self.humanID, opponentBought: false)
                }
            }
            addLog(.yourSell(botName: active.botName, price: active.quote.bid))
            showActionResult("Sold to \(active.botName) at \(active.quote.bid)")
            tutorialManager?.advance(trigger: .sold)
        } catch {
            addLog(.info(message: "Error: \(error.localizedDescription)", important: false))
        }
    }

    /// Player passes on the current quote — clears it with a brief message.
    func passOnQuote() {
        guard let active = activeQuote else { return }
        if !isTutorial {
            // Bot reads the player's recent trades with other bots (soft adjustment)
            if let bot = botMap[active.botName] {
                bot.tracker.recordPassWithMarketRead(opponent: Self.humanID)
            }
        }
        addLog(.yourPass(botName: active.botName))
        showActionResult("Passed")
        tutorialManager?.advance(trigger: .passed)
    }

    /// Show a brief result message, then auto-clear after a delay.
    private func showActionResult(_ message: String) {
        activeQuote = nil
        lastActionResult = message
        Task {
            try? await Task.sleep(for: .milliseconds(1000))
            if self.lastActionResult == message {
                self.lastActionResult = nil
            }
        }
    }

    /// Player submits a quote when a bot asks (from slider).
    func submitQuote(_ quote: Quote) {
        tutorialManager?.advance(trigger: .quotedBot)
        quoteContinuation?.resume(returning: quote)
        quoteContinuation = nil
    }

    /// Player taps "Next" to advance past the free trading phase.
    func playerTappedNext() {
        activeQuote = nil
        lastActionResult = nil
        tutorialManager?.advance(trigger: .nextTapped)
        nextContinuation?.resume()
        nextContinuation = nil
    }

    /// Player taps "Continue" during wind-down mini trading window.
    func windDownContinue() {
        activeQuote = nil
        lastActionResult = nil
        windDownContinuation?.resume()
        windDownContinuation = nil
    }

    /// Return to main menu from settlement screen.
    func playAgain() {
        tutorialManager = nil
        isTutorial = false
        screen = .mainMenu
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: - Continuations
    // ═══════════════════════════════════════════════════════════════════════

    private func awaitUserQuote() async -> Quote {
        await withCheckedContinuation { continuation in
            self.quoteContinuation = continuation
        }
    }

    private func awaitPlayerNext() async {
        await withCheckedContinuation { continuation in
            self.nextContinuation = continuation
        }
    }

    private func awaitWindDownContinue() async {
        await withCheckedContinuation { continuation in
            self.windDownContinuation = continuation
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: - Helpers
    // ═══════════════════════════════════════════════════════════════════════

    /// Notify all bots about a trade so they update opponent tracking.
    private func recordTradeForAllBots(buyer: String, seller: String) {
        for bot in bots {
            bot.tracker.recordTrade(buyer: buyer, seller: seller, playerOfInterest: buyer)
            bot.tracker.recordTrade(buyer: buyer, seller: seller, playerOfInterest: seller)
        }
    }

    private func addLog(_ kind: LogKind) {
        log.append(LogEntry(kind: kind))
    }

    private func shortDelay() async {
        try? await Task.sleep(for: .milliseconds(600))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: - Computed Properties (for Views)
    // ═══════════════════════════════════════════════════════════════════════

    var currentPhase: Phase {
        gameState?.phase ?? .open
    }

    var revealedCentralCards: [Int] {
        gameState?.revealedCentral ?? []
    }

    var tradeCount: Int {
        gameState?.trades.count ?? 0
    }

    /// Player's expected value based on known cards.
    var playerEV: Double {
        guard let state = gameState else { return 0 }
        let known = state.knownCards(for: Self.humanID)
        let unknown = state.unknownCount(for: Self.humanID)
        return Deck.expectedTotal(knownCards: known, unknownCount: unknown)
    }

    /// Suggested slider range: EV +/- 25, clamped to reasonable deck bounds.
    var suggestedBidRange: ClosedRange<Int> {
        let ev = Int(playerEV.rounded())
        return max(ev - 25, -12)...min(ev + 25, 132)
    }

    var sortedScores: [(name: String, score: Int)] {
        leaderboard(scores: scores).map { (name: $0.0, score: $0.1) }
    }

    /// All players' private cards + central cards, for the settlement reveal.
    var allDealtCards: (players: [(name: String, card: Int)], central: [Int]) {
        guard let state = gameState else { return ([], []) }
        let playerCards = state.playerIDs.map { id in
            (name: id, card: state.privateCards[id] ?? 0)
        }
        return (players: playerCards, central: state.centralCards)
    }

    var playerTradeBreakdown: [(trade: Trade, pnl: Int)] {
        guard let state = gameState else { return [] }
        return tradeBreakdown(state: state, playerID: Self.humanID)
    }

    var botDescriptions: [(name: String, type: String)] {
        bots.map { bot in
            let type = bot is StrategicBot ? "strategic" : "simple"
            return (name: bot.playerID, type: type)
        }
    }

    /// Most recent important log entry — shown as a status banner.
    var lastEvent: String {
        if let result = lastActionResult {
            return result
        }
        return log.last(where: { $0.isImportant })?.displayText ?? "Your turn -- ask a trader for a price"
    }

    /// True when the player can freely ask/buy/sell (main turn or wind-down mini turn).
    var isPlayerTurn: Bool {
        if case .playerTurn = playingState { return true }
        if case .windDownTurn = playingState { return true }
        return false
    }

    /// True when the player is in a wind-down mini turn (shows "Continue" instead of "Next").
    var isWindDownTurn: Bool {
        if case .windDownTurn = playingState { return true }
        return false
    }

    /// Player's rank in the current game (1 = best). Available after settlement.
    var playerRank: Int {
        let sorted = sortedScores
        guard let idx = sorted.firstIndex(where: { $0.name == Self.humanID }) else { return 0 }
        return idx + 1
    }

    /// Player's P&L in the current game. Available after settlement.
    var playerPnL: Int {
        scores[Self.humanID] ?? 0
    }

    /// Number of trades the player made in this game.
    var playerTradeCount: Int {
        gameState?.trades.filter {
            $0.buyer == Self.humanID || $0.seller == Self.humanID
        }.count ?? 0
    }

    /// Text summary for sharing game results.
    var shareText: String {
        let pnlStr = playerPnL >= 0 ? "+\(playerPnL)" : "\(playerPnL)"
        let rankSuffix: String
        switch playerRank {
        case 1: rankSuffix = "1st"
        case 2: rankSuffix = "2nd"
        case 3: rankSuffix = "3rd"
        default: rankSuffix = "\(playerRank)th"
        }
        return """
        MagCry [\(difficulty.label)]
        P&L: \(pnlStr) (\(rankSuffix) place)
        Final total: \(finalTotal) | \(playerTradeCount) trades
        """
    }

    /// Personal best P&L for the current difficulty.
    var personalBest: Int? {
        scoreStore.statsFor(difficulty).bestPnL
    }

    /// Current win streak for the current difficulty.
    var currentStreak: Int {
        scoreStore.statsFor(difficulty).currentStreak
    }
}

// MARK: - LogEntry

enum LogKind {
    // Section headers
    case phaseChange(label: String)
    case cardReveal(index: Int, card: Int)

    // Bot asks you for a price
    case botAsksYou(botName: String)

    // You asked a bot — your quote
    case yourQuote(botName: String, bid: Int, ask: Int)

    // Your trades
    case yourBuy(botName: String, price: Int)
    case yourSell(botName: String, price: Int)
    case yourPass(botName: String)

    // Bot acts on your quote (when they asked you)
    case botBuys(botName: String, price: Int)
    case botSells(botName: String, price: Int)
    case botWalks(botName: String)

    // Bot-to-bot trades (muted)
    case botTrade(buyer: String, seller: String, price: Int)

    // Informational (adaptation warnings, wind-down info, errors)
    case info(message: String, important: Bool)
}

struct LogEntry: Identifiable {
    let id = UUID()
    let kind: LogKind

    var displayText: String {
        switch kind {
        case .phaseChange(let label):
            return label
        case .cardReveal(let index, let card):
            return "Central card \(index) revealed: \(card)"
        case .botAsksYou(let botName):
            return "\(botName) asks for your price"
        case .yourQuote(let botName, let bid, let ask):
            return "You quoted \(botName): \(bid) - \(ask)"
        case .yourBuy(let botName, let price):
            return "You buy from \(botName) at \(price)"
        case .yourSell(let botName, let price):
            return "You sell to \(botName) at \(price)"
        case .yourPass(let botName):
            return "Passed on \(botName)'s quote"
        case .botBuys(let botName, let price):
            return "\(botName) buys at \(price)"
        case .botSells(let botName, let price):
            return "\(botName) sells at \(price)"
        case .botWalks(let botName):
            return "\(botName) walks away"
        case .botTrade(let buyer, let seller, let price):
            return "\(buyer) buys from \(seller) at \(price)"
        case .info(let message, _):
            return message
        }
    }

    var isImportant: Bool {
        switch kind {
        case .phaseChange, .cardReveal, .botAsksYou,
             .yourBuy, .yourSell, .botBuys, .botSells:
            return true
        case .info(_, let important):
            return important
        case .yourQuote, .yourPass, .botWalks, .botTrade:
            return false
        }
    }

    /// True for phase headers and card reveals — used for section grouping.
    var isSectionHeader: Bool {
        switch kind {
        case .phaseChange, .cardReveal: return true
        default: return false
        }
    }
}
