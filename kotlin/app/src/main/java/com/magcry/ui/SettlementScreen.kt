package com.magcry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magcry.viewmodel.GameViewModel

private fun scoreString(score: Int): String = if (score >= 0) "+$score" else "$score"

private fun scoreColor(score: Int): Color = when {
    score > 0 -> Color(0xFF44DD44)
    score < 0 -> Color(0xFFFF4444)
    else -> Color.Gray
}


@Composable
fun SettlementScreen(vm: GameViewModel) {
    val (playerCards, centralCards) = vm.allDealtCards

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Spacer(Modifier.height(24.dp))
        Text(
            text = "S E T T L E M E N T",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Final Total: ${vm.finalTotal}",
            color = Color.Cyan,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Sum of all 8 cards",
            color = Color.Gray,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(24.dp))

        // CARDS DEALT
        SectionHeader("CARDS DEALT")
        Spacer(Modifier.height(8.dp))

        for ((name, card) in playerCards) {
            val isHuman = name == GameViewModel.HUMAN_ID
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isHuman) "You" else name,
                    color = if (isHuman) Color.Yellow else Color.White,
                    fontWeight = if (isHuman) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
                )
                Text(
                    text = cardString(card),
                    color = if (isHuman) Color.Yellow else Color.White,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        for (card in centralCards) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Central", color = Color.Cyan, fontSize = 14.sp)
                Text(cardString(card), color = Color.Cyan, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(20.dp))

        // LEADERBOARD
        SectionHeader("LEADERBOARD")
        Spacer(Modifier.height(8.dp))

        val sorted = vm.sortedScores
        sorted.forEachIndexed { index, (name, score) ->
            val isHuman = name == GameViewModel.HUMAN_ID
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isHuman) Modifier.background(
                            Color.Yellow.copy(alpha = 0.1f),
                            RoundedCornerShape(4.dp)
                        ) else Modifier
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "#${index + 1}  ${if (isHuman) "You" else name}",
                    color = if (isHuman) Color.Yellow else Color.White,
                    fontWeight = if (isHuman) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
                )
                Text(
                    text = scoreString(score),
                    color = scoreColor(score),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // YOUR TRADES
        SectionHeader("YOUR TRADES")
        Spacer(Modifier.height(8.dp))

        val breakdown = vm.playerTradeBreakdown
        if (breakdown.isEmpty()) {
            Text(
                text = "No trades",
                color = Color(0xFF666666),
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            for ((trade, pnl) in breakdown) {
                val isBuy = trade.buyer == GameViewModel.HUMAN_ID
                val counterparty = if (isBuy) trade.seller else trade.buyer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isBuy) "Bought from $counterparty" else "Sold to $counterparty",
                        color = if (isBuy) Color(0xFF44DD44) else Color(0xFFFF4444),
                        fontSize = 13.sp
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text("@ ${trade.price}", color = Color.White, fontSize = 13.sp)
                        Text(
                            text = "P&L: ${scoreString(pnl)}",
                            color = scoreColor(pnl),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // Play Again button
        Button(
            onClick = { vm.playAgain() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22AA44)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Play Again", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color(0xFF888888),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp
    )
    HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(top = 4.dp))
}
