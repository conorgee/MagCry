package com.magcry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magcry.viewmodel.GameViewModel
import com.magcry.viewmodel.GameViewModel.PlayingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(vm: GameViewModel) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = vm.currentPhase.label,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Trades: ${vm.tradeCount}",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("♠", color = Color.Yellow, fontSize = 18.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${vm.playerCard}",
                        color = Color.Yellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "EV ${vm.playerEV.toInt()}",
                        color = Color.Cyan,
                        fontSize = 14.sp
                    )
                }
            }

            // Central cards
            CentralCardsView(
                centralCards = vm.gameState?.centralCards ?: emptyList(),
                revealedCount = vm.revealedCentralCards.size
            )

            HorizontalDivider(color = Color(0xFF333333))

            // Last event banner
            Text(
                text = vm.lastEvent,
                color = Color(0xFFFFAA00),
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            HorizontalDivider(color = Color(0xFF333333))

            // Interaction area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (val state = vm.playingState) {
                    is PlayingState.BotAsksYou -> {
                        QuoteInputView(vm = vm, botName = state.botName)
                    }
                    is PlayingState.BotDecided -> {
                        Text(
                            text = "${state.botName} ${state.action}",
                            color = Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    is PlayingState.PlayerTurn, is PlayingState.WindDownTurn -> {
                        PlayerTradingArea(vm = vm)
                    }
                    is PlayingState.BotsTrading -> {
                        CircularProgressIndicator(color = Color.Cyan)
                    }
                    is PlayingState.Idle -> { /* nothing */ }
                }
            }

            // Bottom bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    vm.playingState is PlayingState.WindDownTurn -> {
                        Button(
                            onClick = { vm.windDownContinue() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDD8800)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Continue", fontSize = 16.sp)
                        }
                    }
                    vm.playingState is PlayingState.PlayerTurn -> {
                        Button(
                            onClick = { vm.playerTappedNext() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22AA44)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Next", fontSize = 16.sp)
                        }
                    }
                    else -> {
                        Spacer(Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.width(8.dp))

                IconButton(onClick = { vm.showHistory = !vm.showHistory }) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "Trade History",
                        tint = Color.White
                    )
                }
            }
        }

        // Trade history bottom sheet
        if (vm.showHistory) {
            ModalBottomSheet(
                onDismissRequest = { vm.showHistory = false },
                containerColor = Color(0xFF1A1A1A)
            ) {
                TradeLogSheet(
                    entries = vm.log,
                    onDismiss = { vm.showHistory = false }
                )
            }
        }

        // Tutorial coach overlay
        val manager = vm.tutorialManager
        if (manager?.currentStep != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                TutorialCoachView(manager = manager)
            }
        }
    }
}

@Composable
private fun PlayerTradingArea(vm: GameViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val activeQuote = vm.activeQuote
        val lastResult = vm.lastActionResult

        when {
            activeQuote != null -> {
                val (botName, quote) = activeQuote!!
                BotQuoteView(
                    botName = botName,
                    quote = quote,
                    onBuy = { vm.buyFromActive() },
                    onSell = { vm.sellToActive() },
                    onPass = { vm.passOnQuote() }
                )
            }
            lastResult != null -> {
                Text(
                    text = lastResult,
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }
            else -> {
                // 2x2 grid of "Ask" buttons
                val bots = GameViewModel.BOT_NAMES
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in bots.chunked(2)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (name in row) {
                                Button(
                                    onClick = { vm.askBot(name) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2A2A2A)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Ask $name", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
