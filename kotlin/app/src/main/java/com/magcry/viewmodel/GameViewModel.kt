package com.magcry.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magcry.model.*
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Central game coordinator. Runs the entire game loop as a sequential coroutine,
 * pausing at user-input points via suspendCancellableCoroutine. All state mutations
 * happen on the main thread so Compose can observe them safely.
 */
class GameViewModel : ViewModel() {

    // -- Screen & Interaction State --

    sealed class Screen {
        data object MainMenu : Screen()
        data object Playing : Screen()
        data object Settlement : Screen()
    }

    sealed class PlayingState {
        data object Idle : PlayingState()
        data object PlayerTurn : PlayingState()
        data class BotAsksYou(val botName: String) : PlayingState()
        data class BotDecided(val botName: String, val action: String) : PlayingState()
        data object WindDownTurn : PlayingState()
        data object BotsTrading : PlayingState()
    }

    // -- Published State --

    var screen: Screen by mutableStateOf(Screen.MainMenu)
        private set
    var playingState: PlayingState by mutableStateOf<PlayingState>(PlayingState.Idle)
        private set

    // Game data
    var gameState: GameState? by mutableStateOf(null)
        private set
    var difficulty: Difficulty by mutableStateOf(Difficulty.EASY)
        private set
    var bots: List<Bot> by mutableStateOf(emptyList())
        private set
    var botMap: Map<String, Bot> by mutableStateOf(emptyMap())
        private set

    // Player info
    var playerCard: Int by mutableIntStateOf(0)
        private set

    /** The single active quote the player is currently viewing. */
    var activeQuote: Pair<String, Quote>? by mutableStateOf(null)

    /** Brief result message after player acts on a quote. */
    var lastActionResult: String? by mutableStateOf(null)

    /** Controls the History sheet presentation. */
    var showHistory: Boolean by mutableStateOf(false)

    // Log
    val log = mutableStateListOf<LogEntry>()

    // Settlement
    var scores: Map<String, Int> by mutableStateOf(emptyMap())
        private set
    var finalTotal: Int by mutableIntStateOf(0)
        private set

    // Tutorial
    var tutorialManager: TutorialManager? by mutableStateOf(null)
        private set

    // -- Private --

    private var rng: GameRNG? = null
    private var quoteContinuation: CancellableContinuation<Quote>? = null
    private var nextContinuation: CancellableContinuation<Unit>? = null
    private var windDownContinuation: CancellableContinuation<Unit>? = null

    companion object {
        const val HUMAN_ID = "You"
        val BOT_NAMES = listOf("Alice", "Bob", "Carol", "Dave")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: Game Setup
    // ═══════════════════════════════════════════════════════════════════════

    fun startGame(diff: Difficulty) {
        tutorialManager = null
        difficulty = diff
        rng = GameRNG()

        // Create bots
        bots = makeBots(diff, rng!!)
        botMap = bots.associateBy { it.playerID }

        // Deal cards
        val playerIDs = listOf(HUMAN_ID) + BOT_NAMES
        val (privateCards, centralCards) = Deck.deal(playerIDs, rng!!)

        gameState = GameState(
            playerIDs = playerIDs,
            privateCards = privateCards,
            centralCards = centralCards
        )

        playerCard = privateCards[HUMAN_ID]!!
        activeQuote = null
        lastActionResult = null
        log.clear()
        scores = emptyMap()
        finalTotal = 0

        screen = Screen.Playing

        viewModelScope.launch { runGame() }
    }

    fun startTutorial() {
        startGame(Difficulty.EASY)
        tutorialManager = TutorialManager()
    }

    private fun makeBots(diff: Difficulty, rng: GameRNG): List<Bot> = when (diff) {
        Difficulty.EASY -> BOT_NAMES.map { SimpleBot(it, rng.child()) }
        Difficulty.MEDIUM -> listOf(
            SimpleBot("Alice", rng.child(), adaptationThreshold = 5),
            SimpleBot("Bob", rng.child(), adaptationThreshold = 5),
            StrategicBot("Carol", rng.child()),
            StrategicBot("Dave", rng.child()),
        )
        Difficulty.HARD -> BOT_NAMES.map { StrategicBot(it, rng.child()) }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: Main Game Loop
    // ═══════════════════════════════════════════════════════════════════════

    private suspend fun runGame() {
        val state = gameState ?: return

        val phases = listOf(Phase.OPEN, Phase.REVEAL1, Phase.REVEAL2, Phase.REVEAL3)

        for (phase in phases) {
            state.phase = phase

            // Reveal central card for reveal phases
            if (phase != Phase.OPEN) {
                val revealIdx = listOf(Phase.REVEAL1, Phase.REVEAL2, Phase.REVEAL3).indexOf(phase)
                if (revealIdx == state.revealedCentral.size) {
                    val card = state.centralCards[revealIdx]
                    state.revealedCentral.add(card)
                    addLog(LogKind.CardReveal(revealIdx + 1, card))

                    for (bot in bots) {
                        bot.onPhaseChange(state)
                        bot.tracker.decayDirectShifts()
                    }

                    tutorialManager?.advance(TutorialTrigger.PHASE_CHANGED)
                }
            }

            addLog(LogKind.PhaseChange(phase.label))
            runPhase(state)
        }

        // Settlement
        state.phase = Phase.SETTLE
        finalTotal = state.finalTotal()
        scores = settle(state)
        screen = Screen.Settlement
    }

    private suspend fun runPhase(state: GameState) {
        // Phase A: Bots ask human for prices (0-2)
        runBotsAskHuman(state, 0..2)

        // Phase B: Human trades freely until they tap "Next"
        playingState = PlayingState.PlayerTurn
        activeQuote = null
        lastActionResult = null
        awaitPlayerNext()

        // Phase C: Wind-down
        runWindDown(state)
    }

    // MARK: Phase A: Bots Ask Human

    private suspend fun runBotsAskHuman(state: GameState, askRange: IntRange): Int {
        val nAskers = rng!!.nextInt(askRange)
        if (nAskers <= 0) return 0

        val askers = rng!!.sample(bots, minOf(nAskers, bots.size))

        for (bot in askers) {
            addLog(LogKind.BotAsksYou(bot.playerID))

            val streak = bot.tracker.opponentSameDirectionStreak(HUMAN_ID)
            val direction = bot.tracker.opponentDirection(HUMAN_ID)
            if (streak >= 3 && direction != "neutral") {
                addLog(LogKind.Info("${bot.playerID} seems to have read your $direction pattern.", false))
            }

            playingState = PlayingState.BotAsksYou(bot.playerID)
            val quote = awaitUserQuote()

            val decision = bot.decideOnQuote(state, HUMAN_ID, quote)

            if (decision != null) {
                if (decision == "buy") {
                    try {
                        executeTradeDirectly(state, bot.playerID, HUMAN_ID, quote.ask)
                        recordTradeForAllBots(bot.playerID, HUMAN_ID)
                        bot.tracker.recordDirectTrade(HUMAN_ID, opponentBought = false)
                        val msg = "buys at ${quote.ask}"
                        addLog(LogKind.BotBuys(bot.playerID, quote.ask))
                        playingState = PlayingState.BotDecided(bot.playerID, msg)
                    } catch (_: Exception) { }
                } else {
                    try {
                        executeTradeDirectly(state, HUMAN_ID, bot.playerID, quote.bid)
                        recordTradeForAllBots(HUMAN_ID, bot.playerID)
                        bot.tracker.recordDirectTrade(HUMAN_ID, opponentBought = true)
                        val msg = "sells at ${quote.bid}"
                        addLog(LogKind.BotSells(bot.playerID, quote.bid))
                        playingState = PlayingState.BotDecided(bot.playerID, msg)
                    } catch (_: Exception) { }
                }
            } else {
                addLog(LogKind.BotWalks(bot.playerID))
                playingState = PlayingState.BotDecided(bot.playerID, "walks away")
            }

            shortDelay()
        }

        return askers.size
    }

    // MARK: Wind-Down

    private suspend fun runWindDown(state: GameState) {
        val nRounds = rng!!.nextInt(1..3)
        addLog(LogKind.Info("Wind-down: $nRounds round(s)...", false))

        for (round in 1..nRounds) {
            addLog(LogKind.Info("Round $round/$nRounds", false))

            playingState = PlayingState.BotsTrading
            shortDelay()

            val nAsked = runBotsAskHuman(state, 0..1)

            if (nAsked > 0) {
                playingState = PlayingState.WindDownTurn
                activeQuote = null
                lastActionResult = null
                addLog(LogKind.Info("Quick -- you can trade back.", true))
                awaitWindDownContinue()
            }

            playingState = PlayingState.BotsTrading
            runBotToBot(state)
            shortDelay()
        }
    }

    // MARK: Bot-to-Bot Trading

    private fun runBotToBot(state: GameState) {
        for (bot in bots) {
            val targetID = bot.decideAction(state) ?: continue
            if (targetID == HUMAN_ID || targetID == bot.playerID) continue
            val targetBot = botMap[targetID] ?: continue

            val quote = targetBot.getQuote(state, bot.playerID)
            val decision = bot.decideOnQuote(state, targetID, quote)

            if (decision == "buy") {
                try {
                    executeTradeDirectly(state, bot.playerID, targetID, quote.ask)
                    recordTradeForAllBots(bot.playerID, targetID)
                    addLog(LogKind.BotTrade(bot.playerID, targetID, quote.ask))
                } catch (_: Exception) { }
            } else if (decision == "sell") {
                try {
                    executeTradeDirectly(state, targetID, bot.playerID, quote.bid)
                    recordTradeForAllBots(targetID, bot.playerID)
                    addLog(LogKind.BotTrade(targetID, bot.playerID, quote.bid))
                } catch (_: Exception) { }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: Player Actions (called by Views)
    // ═══════════════════════════════════════════════════════════════════════

    fun askBot(botName: String) {
        val state = gameState ?: return
        val bot = botMap[botName] ?: return
        val quote = bot.getQuote(state, HUMAN_ID)
        activeQuote = Pair(botName, quote)
        lastActionResult = null
        addLog(LogKind.YourQuote(botName, quote.bid, quote.ask))
        tutorialManager?.advance(TutorialTrigger.BOT_ASKED)
    }

    fun buyFromActive() {
        val state = gameState ?: return
        val (botName, quote) = activeQuote ?: return
        val bot = botMap[botName] ?: return
        try {
            executeTradeDirectly(state, HUMAN_ID, botName, quote.ask)
            recordTradeForAllBots(HUMAN_ID, botName)
            bot.tracker.recordDirectTrade(HUMAN_ID, opponentBought = true)
            addLog(LogKind.YourBuy(botName, quote.ask))
            showActionResult("Bought from $botName at ${quote.ask}")
            tutorialManager?.advance(TutorialTrigger.QUOTE_ACTED_ON)
        } catch (e: Exception) {
            addLog(LogKind.Info("Error: ${e.message}", false))
        }
    }

    fun sellToActive() {
        val state = gameState ?: return
        val (botName, quote) = activeQuote ?: return
        val bot = botMap[botName] ?: return
        try {
            executeTradeDirectly(state, botName, HUMAN_ID, quote.bid)
            recordTradeForAllBots(botName, HUMAN_ID)
            bot.tracker.recordDirectTrade(HUMAN_ID, opponentBought = false)
            addLog(LogKind.YourSell(botName, quote.bid))
            showActionResult("Sold to $botName at ${quote.bid}")
            tutorialManager?.advance(TutorialTrigger.QUOTE_ACTED_ON)
        } catch (e: Exception) {
            addLog(LogKind.Info("Error: ${e.message}", false))
        }
    }

    fun passOnQuote() {
        val (botName, _) = activeQuote ?: return
        botMap[botName]?.tracker?.recordPassWithMarketRead(HUMAN_ID)
        addLog(LogKind.YourPass(botName))
        showActionResult("Passed")
        tutorialManager?.advance(TutorialTrigger.QUOTE_ACTED_ON)
    }

    private fun showActionResult(message: String) {
        activeQuote = null
        lastActionResult = message
        viewModelScope.launch {
            delay(1000)
            if (lastActionResult == message) {
                lastActionResult = null
            }
        }
    }

    fun submitQuote(quote: Quote) {
        quoteContinuation?.resume(quote)
        quoteContinuation = null
    }

    fun playerTappedNext() {
        activeQuote = null
        lastActionResult = null
        tutorialManager?.advance(TutorialTrigger.NEXT_TAPPED)
        nextContinuation?.resume(Unit)
        nextContinuation = null
    }

    fun windDownContinue() {
        activeQuote = null
        lastActionResult = null
        windDownContinuation?.resume(Unit)
        windDownContinuation = null
    }

    fun playAgain() {
        tutorialManager = null
        screen = Screen.MainMenu
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: Continuations
    // ═══════════════════════════════════════════════════════════════════════

    private suspend fun awaitUserQuote(): Quote = suspendCancellableCoroutine { cont ->
        quoteContinuation = cont
    }

    private suspend fun awaitPlayerNext() = suspendCancellableCoroutine { cont ->
        nextContinuation = cont
    }

    private suspend fun awaitWindDownContinue() = suspendCancellableCoroutine { cont ->
        windDownContinuation = cont
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private fun recordTradeForAllBots(buyer: String, seller: String) {
        for (bot in bots) {
            bot.tracker.recordTrade(buyer, seller, buyer)
            bot.tracker.recordTrade(buyer, seller, seller)
        }
    }

    private fun addLog(kind: LogKind) {
        log.add(LogEntry(kind = kind))
    }

    private suspend fun shortDelay() {
        delay(600)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARK: Computed Properties (for Views)
    // ═══════════════════════════════════════════════════════════════════════

    val currentPhase: Phase get() = gameState?.phase ?: Phase.OPEN

    val revealedCentralCards: List<Int> get() = gameState?.revealedCentral ?: emptyList()

    val tradeCount: Int get() = gameState?.trades?.size ?: 0

    val playerEV: Double
        get() {
            val state = gameState ?: return 0.0
            val known = state.knownCards(HUMAN_ID)
            val unknown = state.unknownCount(HUMAN_ID)
            return Deck.expectedTotal(known, unknown)
        }

    val suggestedBidRange: IntRange
        get() {
            val ev = Math.round(playerEV).toInt()
            return maxOf(ev - 25, -12)..minOf(ev + 25, 132)
        }

    val sortedScores: List<Pair<String, Int>>
        get() = leaderboard(scores)

    val allDealtCards: Pair<List<Pair<String, Int>>, List<Int>>
        get() {
            val state = gameState ?: return Pair(emptyList(), emptyList())
            val playerCards = state.playerIDs.map { id ->
                Pair(id, state.privateCards[id] ?: 0)
            }
            return Pair(playerCards, state.centralCards)
        }

    val playerTradeBreakdown: List<Pair<Trade, Int>>
        get() {
            val state = gameState ?: return emptyList()
            return tradeBreakdown(state, HUMAN_ID)
        }

    val botDescriptions: List<Pair<String, String>>
        get() = bots.map { bot ->
            val type = if (bot is StrategicBot) "strategic" else "simple"
            Pair(bot.playerID, type)
        }

    val lastEvent: String
        get() {
            lastActionResult?.let { return it }
            return log.lastOrNull { it.isImportant }?.displayText
                ?: "Your turn -- ask a bot for a price"
        }

    val isPlayerTurn: Boolean
        get() = playingState is PlayingState.PlayerTurn || playingState is PlayingState.WindDownTurn

    val isWindDownTurn: Boolean
        get() = playingState is PlayingState.WindDownTurn
}

// ═══════════════════════════════════════════════════════════════════════
// MARK: LogEntry
// ═══════════════════════════════════════════════════════════════════════

sealed class LogKind {
    data class PhaseChange(val label: String) : LogKind()
    data class CardReveal(val index: Int, val card: Int) : LogKind()
    data class BotAsksYou(val botName: String) : LogKind()
    data class YourQuote(val botName: String, val bid: Int, val ask: Int) : LogKind()
    data class YourBuy(val botName: String, val price: Int) : LogKind()
    data class YourSell(val botName: String, val price: Int) : LogKind()
    data class YourPass(val botName: String) : LogKind()
    data class BotBuys(val botName: String, val price: Int) : LogKind()
    data class BotSells(val botName: String, val price: Int) : LogKind()
    data class BotWalks(val botName: String) : LogKind()
    data class BotTrade(val buyer: String, val seller: String, val price: Int) : LogKind()
    data class Info(val message: String, val important: Boolean) : LogKind()
}

data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val kind: LogKind
) {
    val displayText: String
        get() = when (kind) {
            is LogKind.PhaseChange -> kind.label
            is LogKind.CardReveal -> "Central card ${kind.index} revealed: ${kind.card}"
            is LogKind.BotAsksYou -> "${kind.botName} asks for your price"
            is LogKind.YourQuote -> "You quoted ${kind.botName}: ${kind.bid} - ${kind.ask}"
            is LogKind.YourBuy -> "You buy from ${kind.botName} at ${kind.price}"
            is LogKind.YourSell -> "You sell to ${kind.botName} at ${kind.price}"
            is LogKind.YourPass -> "Passed on ${kind.botName}'s quote"
            is LogKind.BotBuys -> "${kind.botName} buys at ${kind.price}"
            is LogKind.BotSells -> "${kind.botName} sells at ${kind.price}"
            is LogKind.BotWalks -> "${kind.botName} walks away"
            is LogKind.BotTrade -> "${kind.buyer} buys from ${kind.seller} at ${kind.price}"
            is LogKind.Info -> kind.message
        }

    val isImportant: Boolean
        get() = when (kind) {
            is LogKind.PhaseChange, is LogKind.CardReveal, is LogKind.BotAsksYou,
            is LogKind.YourBuy, is LogKind.YourSell, is LogKind.BotBuys, is LogKind.BotSells -> true
            is LogKind.Info -> kind.important
            else -> false
        }

    val isSectionHeader: Boolean
        get() = kind is LogKind.PhaseChange || kind is LogKind.CardReveal
}
