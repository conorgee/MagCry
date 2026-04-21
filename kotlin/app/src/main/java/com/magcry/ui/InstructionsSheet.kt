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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val DarkBg = Color(0xFF141414)
private val Cyan = Color(0xFF00E5FF)
private val MutedGray = Color(0xFF555555)
private val CardBlue = Color(0xFF4A90D9)
private val CardCyan = Color(0xFF00BCD4)
private val Yellow = Color(0xFFFFEB3B)
private val Green = Color(0xFF4CAF50)

// ── Per-page data ──

private data class PageData(
    val icon: String,
    val title: String,
    val bullets: List<String>,
    val visual: (@Composable () -> Unit)? = null
)

private val pages = listOf(
    PageData(
        icon = "🃏",
        title = "The Game",
        bullets = listOf(
            "5 players each get 1 private card",
            "3 cards go to the centre",
            "That's 8 cards in play",
            "You bet on the SUM of all 8",
            "Buy if you think it's high, sell if low"
        ),
        visual = { GameVisual() }
    ),
    PageData(
        icon = "🔮",
        title = "Your Card",
        bullets = listOf(
            "Cards range from −10 to 20",
            "Only you can see your card",
            "Your card is a clue to the total"
        ),
        visual = { CardVisual() }
    ),
    PageData(
        icon = "💬",
        title = "Get a Price",
        bullets = listOf(
            "Tap a bot to request a 2-way quote",
            "The spread is always exactly 2",
            "You can Buy, Sell, or Pass"
        ),
        visual = { QuoteVisual() }
    ),
    PageData(
        icon = "📈",
        title = "Buy or Sell",
        bullets = listOf(
            "Buy if you think the sum > ask price",
            "Sell if you think the sum < bid price",
            "Your P&L = final total − price (if you bought)",
            "Your P&L = price − final total (if you sold)"
        ),
        visual = { PnlVisual() }
    ),
    PageData(
        icon = "🂠",
        title = "Card Reveals",
        bullets = listOf(
            "Central cards flip one at a time",
            "One reveal happens between each round",
            "New info means prices shift — watch out!"
        ),
        visual = { RevealVisual() }
    ),
    PageData(
        icon = "🤖",
        title = "Watch the Bots",
        bullets = listOf(
            "Bots track your trading patterns",
            "On Hard mode they bluff and adapt fast",
            "Try to out-think them!"
        )
    )
)

// ── Visuals ──

@Composable
private fun GameVisual() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 12.dp)
    ) {
        repeat(5) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CardBlue)
            )
        }
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CardCyan)
            )
        }
    }
}

@Composable
private fun CardVisual() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(top = 12.dp)
            .size(80.dp, 110.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E1E))
            .border(2.dp, Cyan, RoundedCornerShape(12.dp))
    ) {
        Text("+12", color = Yellow, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QuoteVisual() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 12.dp)
    ) {
        Text("Alice", color = Color.White, fontSize = 14.sp)
        Text("58 – 60", color = Cyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Buy" to Green, "Sell" to Color.Red, "Pass" to MutedGray).forEach { (label, color) ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(color)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PnlVisual() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 12.dp)
    ) {
        Text("Total = 82, bought at 60", color = Color.Gray, fontSize = 13.sp)
        Text("P&L = +22 ✓", color = Green, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Total = 55, bought at 60", color = Color.Gray, fontSize = 13.sp)
        Text("P&L = −5 ✗", color = Color.Red, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RevealVisual() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 12.dp)
    ) {
        // Face-up card
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp, 62.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E1E1E))
                .border(1.dp, Cyan, RoundedCornerShape(8.dp))
        ) {
            Text("14", color = Cyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        // Two face-down cards
        repeat(2) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp, 62.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardBlue)
            ) {
                Text("?", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Main Sheet ──

@Composable
fun InstructionsSheet(onDismiss: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 6 })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp)
    ) {
        // Done button – top right
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Text("Done", color = Cyan, fontSize = 16.sp)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp, bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val data = pages[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(data.icon, fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        data.title,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    data.bullets.forEach { bullet ->
                        Text(
                            "• $bullet",
                            color = Color(0xFFCCCCCC),
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            textAlign = TextAlign.Start
                        )
                    }
                    data.visual?.invoke()
                }
            }

            Spacer(Modifier.height(16.dp))

            // Page dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(6) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == pagerState.currentPage) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (index == pagerState.currentPage) Cyan else MutedGray)
                    )
                }
            }
        }

        // Bottom button
        val isLastPage = pagerState.currentPage == 5
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
                containerColor = if (isLastPage) Green else Cyan
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .height(50.dp)
        ) {
            Text(
                if (isLastPage) "Got it" else "Next",
                color = Color.Black,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
