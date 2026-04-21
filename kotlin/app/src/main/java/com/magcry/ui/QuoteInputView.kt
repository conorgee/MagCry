package com.magcry.ui

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
import com.magcry.model.Quote
import com.magcry.viewmodel.GameViewModel
import kotlin.math.roundToInt

@Composable
fun QuoteInputView(vm: GameViewModel, botName: String) {
    val intRange = vm.suggestedBidRange
    val floatRange = intRange.first.toFloat()..intRange.last.toFloat()
    var sliderValue by remember {
        mutableFloatStateOf((vm.playerEV.roundToInt() - 1).toFloat().coerceIn(floatRange))
    }
    val bid = sliderValue.roundToInt()
    val ask = bid + 2

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$botName asks:",
                color = Color(0xFFFFAA00),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "What's your price?",
                color = Color.White,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(16.dp))

            // Bid / Ask display
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "$bid",
                    color = Color(0xFFFF4444),
                    fontSize = 36.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(24.dp))
                Text(
                    text = "/",
                    color = Color.Gray,
                    fontSize = 28.sp
                )
                Spacer(Modifier.width(24.dp))
                Text(
                    text = "$ask",
                    color = Color(0xFF44DD44),
                    fontSize = 36.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))

            val steps = (intRange.last - intRange.first).coerceAtLeast(1) - 1
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                valueRange = floatRange,
                steps = steps,
                modifier = Modifier.fillMaxWidth()
            )

            // Range labels + EV hint
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${intRange.first}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    text = "EV ≈ ${vm.playerEV.roundToInt()}",
                    color = Color.Cyan.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                Text(
                    text = "${intRange.last}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { vm.submitQuote(Quote(bid, bid + 2)) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2266CC)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Submit Price", fontSize = 16.sp)
            }
        }
    }
}
