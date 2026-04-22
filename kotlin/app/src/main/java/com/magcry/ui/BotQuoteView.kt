package com.magcry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magcry.model.Quote

/**
 * Shows a single bot's live quote with Buy / Sell / Pass action buttons.
 * Layout: inline HStack with name left, bid-ask right with em-dash.
 * Transparent button backgrounds matching Swift.
 */
@Composable
fun BotQuoteView(
    botName: String,
    quote: Quote,
    onBuy: () -> Unit,
    onSell: () -> Unit,
    onPass: () -> Unit,
    buyEnabled: Boolean = true,
    sellEnabled: Boolean = true,
    passEnabled: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Bot name + quote — inline HStack
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = botName,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${quote.bid}",
                    color = Color.Red,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "\u2014",  // em-dash
                    color = Color.Gray,
                    fontSize = 18.sp
                )
                Text(
                    text = "${quote.ask}",
                    color = Color.Green,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Action buttons: Buy / Sell / Pass — transparent backgrounds
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Buy button
            Button(
                onClick = onBuy,
                enabled = buyEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (buyEnabled) Color.Green.copy(alpha = 0.25f) else Color.Gray.copy(alpha = 0.1f),
                    contentColor = if (buyEnabled) Color.Green else Color.Gray,
                    disabledContainerColor = Color.Gray.copy(alpha = 0.1f),
                    disabledContentColor = Color.Gray,
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "Buy ${quote.ask}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Sell button
            Button(
                onClick = onSell,
                enabled = sellEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (sellEnabled) Color.Red.copy(alpha = 0.25f) else Color.Gray.copy(alpha = 0.1f),
                    contentColor = if (sellEnabled) Color.Red else Color.Gray,
                    disabledContainerColor = Color.Gray.copy(alpha = 0.1f),
                    disabledContentColor = Color.Gray,
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "Sell ${quote.bid}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Pass button
            Button(
                onClick = onPass,
                enabled = passEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (passEnabled) Color.White.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.05f),
                    contentColor = if (passEnabled) Color.Gray else Color.Gray.copy(alpha = 0.5f),
                    disabledContainerColor = Color.Gray.copy(alpha = 0.05f),
                    disabledContentColor = Color.Gray.copy(alpha = 0.5f),
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "Pass",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
