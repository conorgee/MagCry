package com.magcry.ui

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magcry.viewmodel.GameViewModel

private fun scoreString(score: Int): String = if (score >= 0) "+$score" else "$score"

private fun scoreColor(score: Int): Color = when {
    score > 0 -> Color.Green
    score < 0 -> Color.Red
    else -> Color.Gray
}

@Composable
fun SettlementScreen(vm: GameViewModel) {
    val context = LocalContext.current
    val (playerCards, centralCards) = vm.allDealtCards
    val totalEntries = playerCards.size + 1  // +1 for central row

    // Staggered reveal animation
    var visibleCardCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        visibleCardCount = totalEntries
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Spacer(Modifier.height(20.dp))
        Text(
            text = "SETTLEMENT",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Final Total: ${vm.finalTotal}",
            color = Color.Cyan,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "Sum of all 8 cards in play",
            color = Color.Gray,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(24.dp))

        // CARDS DEALT — staggered animation
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            SectionLabel("CARDS DEALT")
            Spacer(Modifier.height(6.dp))

            // Player cards
            playerCards.forEachIndexed { index, (name, card) ->
                val isHuman = name == GameViewModel.HUMAN_ID
                val animatedAlpha by animateFloatAsState(
                    targetValue = if (index < visibleCardCount) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = 300,
                        delayMillis = index * 150
                    ),
                    label = "card$index"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(animatedAlpha)
                        .padding(vertical = 4.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = name,
                        color = if (isHuman) Color.Yellow else Color.White,
                        fontWeight = if (isHuman) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                    Text(
                        text = cardString(card),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Central cards — single comma-joined row
            val centralIndex = playerCards.size
            val centralAlpha by animateFloatAsState(
                targetValue = if (centralIndex < visibleCardCount) 1f else 0f,
                animationSpec = tween(
                    durationMillis = 300,
                    delayMillis = centralIndex * 150
                ),
                label = "central"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(centralAlpha)
                    .padding(vertical = 4.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Central",
                    color = Color.Cyan,
                    fontSize = 14.sp
                )
                Text(
                    text = centralCards.joinToString(", ") { cardString(it) },
                    color = Color.Cyan,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // LEADERBOARD
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            SectionLabel("LEADERBOARD")
            Spacer(Modifier.height(4.dp))

            val sorted = vm.sortedScores
            sorted.forEachIndexed { index, (name, score) ->
                val isHuman = name == GameViewModel.HUMAN_ID
                val isNewBest = isHuman && (vm.scoreStore?.isNewBest == true) && !vm.isTutorial

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isHuman) Modifier.background(
                                Color.Yellow.copy(alpha = 0.08f),
                                RoundedCornerShape(8.dp)
                            ) else Modifier
                        )
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "#${index + 1}",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(30.dp)
                    )
                    Text(
                        text = name,
                        color = if (isHuman) Color.Yellow else Color.White,
                        fontWeight = if (isHuman) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                    if (isNewBest) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "New Best!",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color.Yellow, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = scoreString(score),
                        color = scoreColor(score),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Personal best line (non-tutorial only)
            if (!vm.isTutorial) {
                val best = vm.personalBest
                if (best != null) {
                    Text(
                        text = "Personal best: ${scoreString(best)}",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Streak callout
        if (!vm.isTutorial && vm.currentStreak >= 3) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${vm.currentStreak} wins in a row!",
                color = Color.Yellow,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(24.dp))

        // YOUR TRADES
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            val breakdown = vm.playerTradeBreakdown
            if (breakdown.isNotEmpty()) {
                SectionLabel("YOUR TRADES")
                Spacer(Modifier.height(4.dp))

                for ((trade, pnl) in breakdown) {
                    val isBuy = trade.buyer == GameViewModel.HUMAN_ID
                    val counterparty = if (isBuy) trade.seller else trade.buyer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBuy) "Bought from $counterparty" else "Sold to $counterparty",
                            color = if (isBuy) Color.Green else Color.Red,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "@ ${trade.price}",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = scoreString(pnl),
                            color = scoreColor(pnl),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(50.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = "You made no trades this round.",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Play again button
        Button(
            onClick = { vm.playAgain() },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Green.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Text(
                "Play Again",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Share button
        TextButton(
            onClick = {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, vm.shareText)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, null))
            },
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            Text(
                "Share Result",
                color = Color.Cyan,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        color = Color.Gray,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 2.sp
    )
}
