package com.magcry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val totalPages = 6

@Composable
fun InstructionsSheet(onDismiss: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { totalPages })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0.08f, 0.08f, 0.08f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Done button
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, end = 20.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        "Done",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Pages
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> InstructionPage(
                        title = "The Game",
                        lines = listOf(
                            "5 players each get 1 secret card.",
                            "3 more cards go face-down in the center.",
                            "You're all betting on the sum of these 8 cards."
                        )
                    ) { DeckFanVisual() }
                    1 -> InstructionPage(
                        title = "Your Card",
                        lines = listOf(
                            "Cards range from -10 to 20.",
                            "Only you can see yours.",
                            "A higher card means a higher expected sum."
                        )
                    ) { MockCardVisual("+12") }
                    2 -> InstructionPage(
                        title = "Get a Price",
                        lines = listOf(
                            "Tap a trader to ask for a two-way quote.",
                            "The spread is always exactly 2.",
                            "You see their bid and ask price."
                        )
                    ) { MockQuoteVisual() }
                    3 -> InstructionPage(
                        title = "Buy or Sell",
                        lines = listOf(
                            "Buy if you think the sum will be ABOVE the ask.",
                            "Sell if you think it will be BELOW the bid.",
                            "Not sure? Just pass."
                        )
                    ) { PnlExamplesVisual() }
                    4 -> InstructionPage(
                        title = "Card Reveals",
                        lines = listOf(
                            "Between rounds, central cards flip one at a time.",
                            "New info means better estimates.",
                            "Adjust your strategy as cards are revealed."
                        )
                    ) { MiniCentralCardsVisual() }
                    5 -> InstructionPage(
                        title = "Watch the Open Cry Traders",
                        lines = listOf(
                            "Traders track your trading pattern.",
                            "Keep buying and they'll raise prices on you.",
                            "On Hard mode, traders bluff to mislead you."
                        )
                    ) { }
                }
            }

            // Page dots + button
            Column(
                modifier = Modifier.padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Page dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.weight(1f))
                    for (i in 0 until totalPages) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == pagerState.currentPage) Color.Cyan
                                    else Color.White.copy(alpha = 0.25f)
                                )
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }

                // Next / Got it button
                val isLastPage = pagerState.currentPage == totalPages - 1
                Button(
                    onClick = {
                        if (isLastPage) {
                            onDismiss()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLastPage) Color.Green.copy(alpha = 0.6f)
                        else Color.Cyan.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp)
                ) {
                    Text(
                        if (isLastPage) "Got it" else "Next",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun InstructionPage(
    title: String,
    lines: List<String>,
    visual: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.weight(1f))

        Text(
            text = title,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))

        for (line in lines) {
            Text(
                text = line,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 3.dp)
            )
        }

        Spacer(Modifier.height(8.dp))
        visual()

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.weight(1f))
    }
}

// -- Visual mockups --

@Composable
private fun DeckFanVisual() {
    Row(
        horizontalArrangement = Arrangement.spacedBy((-8).dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        for (i in 0 until 8) {
            val color = if (i < 5) Color.Blue.copy(alpha = 0.4f) else Color.Cyan.copy(alpha = 0.4f)
            val borderColor = if (i < 5) Color.Blue.copy(alpha = 0.6f) else Color.Cyan.copy(alpha = 0.6f)
            Box(
                modifier = Modifier
                    .size(28.dp, 40.dp)
                    .rotate((i - 4) * 5f)
                    .background(color, RoundedCornerShape(4.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun MockCardVisual(value: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(top = 8.dp)
            .size(70.dp, 100.dp)
            .background(Color.Yellow.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .border(1.5.dp, Color.Yellow.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
    ) {
        Text(
            text = value,
            color = Color.Yellow,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun MockQuoteVisual() {
    Column(
        modifier = Modifier
            .padding(top = 8.dp, start = 20.dp, end = 20.dp)
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Alice", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("58", color = Color.Red, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("--", color = Color.Gray)
                Text("60", color = Color.Green, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MockActionButton("Buy 60", Color.Green, Modifier.weight(1f))
            MockActionButton("Sell 58", Color.Red, Modifier.weight(1f))
            MockActionButton("Pass", Color.Gray, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MockActionButton(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (color == Color.Gray) Color.Gray else color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PnlExamplesVisual() {
    Column(
        modifier = Modifier
            .padding(top = 8.dp, start = 20.dp, end = 20.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PnlExampleRow("Buy at 60", "Sum = 65", "+5", Color.Green)
        PnlExampleRow("Sell at 58", "Sum = 65", "-7", Color.Red)
    }
}

@Composable
private fun PnlExampleRow(action: String, outcome: String, pnl: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(action, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(" \u2192 ", color = Color.Gray, fontSize = 12.sp)
        Text(outcome, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(pnl, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun MiniCentralCardsVisual() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        // Face-up card
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp, 60.dp)
                .background(Color.Cyan.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                .border(1.dp, Color.Cyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
        ) {
            Text("9", color = Color.Cyan, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        // Face-down cards
        for (i in 0 until 2) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp, 60.dp)
                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            ) {
                Text("?", color = Color.Gray, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
