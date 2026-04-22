package com.magcry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magcry.model.Difficulty
import com.magcry.viewmodel.GameViewModel
import com.magcry.viewmodel.GameViewModel.PlayingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(vm: GameViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header — two rows matching Swift
            GameHeader(vm)

            // Central cards
            CentralCardsView(
                centralCards = vm.gameState?.centralCards ?: emptyList(),
                revealed = vm.revealedCentralCards
            )
            Spacer(Modifier.height(12.dp))

            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

            // Last event banner — white 0.9 opacity, not orange
            Text(
                text = vm.lastEvent,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

            // Main interaction area (expands to fill)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                InteractionArea(vm)
            }

            // Bottom bar — pinned at bottom
            BottomBar(vm)
        }

        // Tutorial coach overlay — pinned to bottom
        val manager = vm.tutorialManager
        if (manager?.isActive == true) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 60.dp)
            ) {
                TutorialCoachView(manager = manager)
            }
        }
    }

    // Trade history bottom sheet
    if (vm.showHistory) {
        ModalBottomSheet(
            onDismissRequest = { vm.showHistory = false },
            containerColor = Color(0.08f, 0.08f, 0.08f)
        ) {
            TradeLogSheet(
                entries = vm.log,
                onDismiss = { vm.showHistory = false }
            )
        }
    }

    // Quit confirmation alert
    if (vm.showQuitAlert) {
        AlertDialog(
            onDismissRequest = { vm.showQuitAlert = false },
            title = { Text("Quit this game?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.showQuitAlert = false
                    vm.quitGame()
                }) {
                    Text("Quit", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.showQuitAlert = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun GameHeader(vm: GameViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Row 1: Phase label + trade count + quit button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = vm.currentPhase.label,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${vm.tradeCount} trades",
                color = Color.Gray,
                fontSize = 12.sp
            )
            // Quit button
            Text(
                text = "\u2715",  // X mark
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 20.sp,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(CircleShape)
                    .clickable { vm.showQuitAlert = true }
                    .padding(4.dp)
            )
        }

        // Row 2: Player card + EV
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val cardStr = if (vm.playerCard >= 0) "+${vm.playerCard}" else "${vm.playerCard}"
            Text(
                text = "\u2660 Your card: $cardStr",  // spade symbol
                color = Color.Yellow,
                fontSize = 14.sp
            )
            Spacer(Modifier.weight(1f))
            if (vm.difficulty == Difficulty.EASY || vm.isTutorial) {
                Text(
                    text = "EV: ${"%.1f".format(vm.playerEV)}",
                    color = Color.Cyan,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun InteractionArea(vm: GameViewModel) {
    when (val state = vm.playingState) {
        is PlayingState.BotAsksYou -> {
            QuoteInputView(
                vm = vm,
                botName = state.botName,
                lockedBid = if (vm.isTutorial) 66 else null
            )
        }
        is PlayingState.BotDecided -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = state.botName,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = state.action,
                    color = if (state.action.contains("buys") || state.action.contains("sells"))
                        Color.Yellow else Color.Gray,
                    fontSize = 18.sp
                )
            }
        }
        is PlayingState.PlayerTurn, is PlayingState.WindDownTurn -> {
            PlayerTradingArea(vm = vm)
        }
        is PlayingState.BotsTrading -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(color = Color.White)
                Text(
                    text = "Traders are trading...",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }
        is PlayingState.Idle -> { /* nothing */ }
    }
}

@Composable
private fun PlayerTradingArea(vm: GameViewModel) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val activeQuote = vm.activeQuote
        val lastResult = vm.lastActionResult

        when {
            activeQuote != null -> {
                val (botName, quote) = activeQuote
                BotQuoteView(
                    botName = botName,
                    quote = quote,
                    onBuy = { vm.buyFromActive() },
                    onSell = { vm.sellToActive() },
                    onPass = { vm.passOnQuote() },
                    buyEnabled = vm.tutorialManager?.currentStep?.canBuy ?: true,
                    sellEnabled = vm.tutorialManager?.currentStep?.canSell ?: true,
                    passEnabled = vm.tutorialManager?.currentStep?.canPass ?: true
                )
            }
            lastResult != null -> {
                Text(
                    text = lastResult,
                    color = Color.Yellow,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            else -> {
                BotAskButtons(vm)
            }
        }
    }
}

@Composable
private fun BotAskButtons(vm: GameViewModel) {
    val allowedBots = vm.tutorialManager?.currentStep?.allowedBotNames

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 2x2 grid
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (name in GameViewModel.BOT_NAMES.take(2)) {
                val enabled = allowedBots == null || allowedBots.contains(name)
                AskButton(
                    name = name,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.askBot(name) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (name in GameViewModel.BOT_NAMES.drop(2)) {
                val enabled = allowedBots == null || allowedBots.contains(name)
                AskButton(
                    name = name,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.askBot(name) }
                )
            }
        }
    }
}

@Composable
private fun AskButton(
    name: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled) Color.Blue.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.15f),
            contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
            disabledContainerColor = Color.Gray.copy(alpha = 0.15f),
            disabledContentColor = Color.White.copy(alpha = 0.3f),
        ),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            "Ask $name",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BottomBar(vm: GameViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            vm.isWindDownTurn -> {
                PrimaryGameButton(
                    text = "Continue",
                    color = Color(0xFFFF9800),  // orange
                    onClick = { vm.windDownContinue() },
                    modifier = Modifier.weight(1f)
                )
            }
            vm.playingState is PlayingState.PlayerTurn -> {
                val nextEnabled = vm.tutorialManager?.currentStep?.canNext ?: true
                PrimaryGameButton(
                    text = "Next",
                    color = if (nextEnabled) Color.Green else Color.Gray,
                    onClick = { vm.playerTappedNext() },
                    enabled = nextEnabled,
                    modifier = Modifier.weight(1f)
                )
            }
            else -> {
                Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.width(8.dp))

        // History button
        IconButton(
            onClick = { vm.showHistory = true },
            modifier = Modifier
                .size(44.dp)
                .background(Color.White.copy(alpha = 0.08f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = "Trade History",
                tint = Color.Gray
            )
        }
    }
}

@Composable
private fun PrimaryGameButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.7f),
            disabledContainerColor = color.copy(alpha = 0.3f),
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        modifier = modifier
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
