package com.magcry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun cardString(value: Int): String = if (value >= 0) "+$value" else "$value"

@Composable
fun CentralCardsView(centralCards: List<Int>, revealedCount: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "CENTRAL CARDS",
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            centralCards.forEachIndexed { index, value ->
                val isRevealed = index < revealedCount
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(80.dp)
                        .then(
                            if (isRevealed) Modifier.shadow(4.dp, RoundedCornerShape(10.dp), ambientColor = Color(0xFF00BCD4))
                            else Modifier
                        )
                        .background(
                            if (isRevealed) Color.White else Color(0xFF1565C0),
                            RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isRevealed) cardString(value) else "?",
                        color = if (isRevealed) Color.Black else Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
