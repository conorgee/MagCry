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

    // Log
    private(set) var log: [LogEntry] = []

    // Settlement
    private(set) var scores: [String: Int] = [:]
    private(set) var finalTotal: Int = 0

    // MARK: - Private

    private var rng: GameRNG!
    private var quoteContinuation: CheckedContinuation<Quote, Never>?
    private var nextContinuation: CheckedContinuation<Void, Never>?
    private var windDownContinuation: CheckedContinuation<Void, Never>?

    // Constants
    static let humanID = "You"
    static let botNames = ["Alice", "Bob", "Carol", "Dave"]

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: - Game Setup
    // ═══════════════════════════════════════════════════════════════════════

    func startGame(difficulty: Difficulty) {
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
        Task { await runGame() }
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
    // MARK: - Main Game Loop
    // ═══════════════════════════════════════════════════════════════════════

    private func runGame() async {
        guard let state = gameState else { return }

        let phases: [Phase] = [.open, .reveal1, .reveal2, .reveal3]

        for phase in phases {
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
                    }
                }
            }

            addLog(.phaseChange(label: phase.label))
            await runPhase(state: state)
        }

        // Settlement
        state.phase = .settle
        self.finalTotal = state.finalTotal()
        self.scores = settle(state: state)
        self.screen = .settlement
    }

    /// Run one complete trading phase: bots ask → human trades → wind-down.
    private func runPhase(state: GameState) async {
        // Phase A: Bots ask human for prices (0-2)
        _ = await runBotsAskHuman(state: state, askRange: 0...2)

        // Phase B: Human trades freely until they tap "Next"
        playingState = .playerTurn
        activeQuote = nil
        lastActionResult = nil
        await awaitPlayerNext()

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

            // Bot evaluates the human's quote
            let decision = bot.decideOnQuote(state: state, quoter: Self.humanID, quote: quote)

            if let decision = decision {
                if decision == "buy" {
                    // Bot buys at ask
                    try? executeTradeDirectly(
                        state: state, buyer: bot.playerID, seller: Self.humanID, price: quote.ask)
                    recordTradeForAllBots(buyer: bot.playerID, seller: Self.humanID)
                    let msg = "buys at \(quote.ask)"
                    addLog(.botBuys(botName: bot.playerID, price: quote.ask))
                    playingState = .botDecided(botName: bot.playerID, action: msg)
                } else {
                    // Bot sells at bid
                    try? executeTradeDirectly(
                        state: state, buyer: Self.humanID, seller: bot.playerID, price: quote.bid)
                    recordTradeForAllBots(buyer: Self.humanID, seller: bot.playerID)
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
    // MARK: - Player Actions (called by Views)
    // ═══════════════════════════════════════════════════════════════════════

    /// Player asks a bot for a price. Shows single quote — one at a time.
    func askBot(_ botName: String) {
        guard let state = gameState, let bot = botMap[botName] else { return }
        let quote = bot.getQuote(state: state, requester: Self.humanID)
        activeQuote = (botName: botName, quote: quote)
        lastActionResult = nil
        addLog(.yourQuote(botName: botName, bid: quote.bid, ask: quote.ask))
    }

    /// Player buys from the currently quoted bot at their ask price.
    func buyFromActive() {
        guard let state = gameState,
              let active = activeQuote,
              let _ = botMap[active.botName] else { return }
        do {
            try executeTradeDirectly(
                state: state, buyer: Self.humanID, seller: active.botName, price: active.quote.ask)
            recordTradeForAllBots(buyer: Self.humanID, seller: active.botName)
            addLog(.yourBuy(botName: active.botName, price: active.quote.ask))
            showActionResult("Bought from \(active.botName) at \(active.quote.ask)")
        } catch {
            addLog(.info(message: "Error: \(error.localizedDescription)", important: false))
        }
    }

    /// Player sells to the currently quoted bot at their bid price.
    func sellToActive() {
        guard let state = gameState,
              let active = activeQuote,
              let _ = botMap[active.botName] else { return }
        do {
            try executeTradeDirectly(
                state: state, buyer: active.botName, seller: Self.humanID, price: active.quote.bid)
            recordTradeForAllBots(buyer: active.botName, seller: Self.humanID)
            addLog(.yourSell(botName: active.botName, price: active.quote.bid))
            showActionResult("Sold to \(active.botName) at \(active.quote.bid)")
        } catch {
            addLog(.info(message: "Error: \(error.localizedDescription)", important: false))
        }
    }

    /// Player passes on the current quote — clears it with a brief message.
    func passOnQuote() {
        guard let active = activeQuote else { return }
        addLog(.yourPass(botName: active.botName))
        showActionResult("Passed")
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
        quoteContinuation?.resume(returning: quote)
        quoteContinuation = nil
    }

    /// Player taps "Next" to advance past the free trading phase.
    func playerTappedNext() {
        activeQuote = nil
        lastActionResult = nil
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
        return log.last(where: { $0.isImportant })?.displayText ?? "Your turn -- ask a bot for a price"
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
