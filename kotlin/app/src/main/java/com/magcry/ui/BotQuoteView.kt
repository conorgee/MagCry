package com.magcry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magcry.model.Quote

@Composable
fun BotQuoteView(
    botName: String,
    quote: Quote,
    onBuy: () -> Unit,
    onSell: () -> Unit,
    onPass: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = botName,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Bid: ${quote.bid}", color = Color(0xFFF44336), fontSize = 16.sp)
            Text(text = "Ask: ${quote.ask}", color = Color(0xFF4CAF50), fontSize = 16.sp)
        }

        Spacer(Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onBuy,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                modifier = Modifier.weight(1f),
            ) {
                Text("Buy ${quote.ask}", color = Color.White)
            }

            Button(
                onClick = onSell,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                modifier = Modifier.weight(1f),
            ) {
                Text("Sell ${quote.bid}", color = Color.White)
            }

            Button(
                onClick = onPass,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                modifier = Modifier.weight(1f),
            ) {
                Text("Pass", color = Color.White)
            }
        }
    }
}
