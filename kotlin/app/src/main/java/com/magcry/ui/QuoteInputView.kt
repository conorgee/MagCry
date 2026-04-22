package com.magcry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magcry.model.Difficulty
import com.magcry.model.Quote
import com.magcry.viewmodel.GameViewModel
import kotlin.math.roundToInt

/**
 * Slider-based quote input shown when a trader asks "What's your price?"
 * User sets the bid with a slider; ask = bid + 2 is shown live.
 * When lockedBid is set (tutorial mode), the slider is disabled at that value.
 */
@Composable
fun QuoteInputView(
    vm: GameViewModel,
    botName: String,
    lockedBid: Int? = null
) {
    val isLocked = lockedBid != null
    val intRange = vm.suggestedBidRange
    val floatRange = intRange.first.toFloat()..intRange.last.toFloat()

    var bidValue by remember {
        mutableFloatStateOf(
            if (lockedBid != null) lockedBid.toFloat()
            else (vm.playerEV.roundToInt() - 1).toFloat().coerceIn(floatRange)
        )
    }

    val displayBid = lockedBid ?: bidValue.roundToInt()
    val displayAsk = displayBid + 2

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Trader asking
        Text(
            text = "$botName asks:",
            color = Color.Gray,
            fontSize = 14.sp
        )
        Text(
            text = "\"What's your price?\"",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )

        // Live bid/ask display with BID/ASK column labels
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "BID",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    text = "$displayBid",
                    color = Color.Red,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "ASK",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    text = "$displayAsk",
                    color = Color.Green,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Slider (disabled when locked in tutorial)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val steps = (intRange.last - intRange.first).coerceAtLeast(1) - 1
            Slider(
                value = if (isLocked) lockedBid!!.toFloat() else bidValue,
                onValueChange = { if (!isLocked) bidValue = it },
                valueRange = floatRange,
                steps = steps,
                enabled = !isLocked,
                colors = SliderDefaults.colors(
                    thumbColor = if (isLocked) Color.Gray else Color.Cyan,
                    activeTrackColor = if (isLocked) Color.Gray else Color.Cyan,
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${intRange.first}",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
                if (vm.difficulty == Difficulty.EASY || isLocked) {
                    Text(
                        text = "EV: ${"%.0f".format(vm.playerEV)}",
                        color = Color.Cyan,
                        fontSize = 10.sp
                    )
                }
                Text(
                    text = "${intRange.last}",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
        }

        // Submit button — cyan
        Button(
            onClick = {
                val bid = displayBid
                vm.submitQuote(Quote(bid = bid, ask = bid + 2))
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Cyan.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Submit Price",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
