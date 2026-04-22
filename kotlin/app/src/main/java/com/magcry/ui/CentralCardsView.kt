package com.magcry.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun cardString(value: Int): String = if (value >= 0) "+$value" else "$value"

/**
 * Displays the 3 central card slots: revealed cards shown face-up with flip animation, hidden as "?".
 * Card back: blue opacity 0.3 + stroke border blue 0.5.
 * 3D flip animation using graphicsLayer { rotationY }.
 */
@Composable
fun CentralCardsView(centralCards: List<Int>, revealed: List<Int>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "CENTRAL CARDS",
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (index in 0 until 3) {
                val isRevealed = index < revealed.size
                val value = if (isRevealed) revealed[index] else null
                FlipCardView(isRevealed = isRevealed, value = value)
            }
        }
    }
}

@Composable
private fun FlipCardView(isRevealed: Boolean, value: Int?) {
    // Animate from 0 to 180 degrees on Y axis when revealed
    val flipDegrees by animateFloatAsState(
        targetValue = if (isRevealed) 180f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "flip"
    )

    Box(
        modifier = Modifier
            .width(60.dp)
            .height(80.dp)
            .graphicsLayer {
                rotationY = flipDegrees
                cameraDistance = 12f * density
            },
        contentAlignment = Alignment.Center
    ) {
        if (flipDegrees < 90f) {
            // Face-down (showing back) — blue opacity 0.3 + stroke border blue 0.5
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Blue.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .border(2.dp, Color.Blue.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "?",
                    color = Color.Blue.copy(alpha = 0.6f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Face-up — counter-rotated so text isn't mirrored
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
                    .shadow(4.dp, RoundedCornerShape(10.dp), ambientColor = Color.Cyan.copy(alpha = 0.3f))
                    .background(Color.White, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (value != null) {
                    Text(
                        text = cardString(value),
                        color = Color.Black,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
