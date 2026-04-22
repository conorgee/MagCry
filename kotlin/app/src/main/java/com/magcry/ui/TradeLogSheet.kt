package com.magcry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magcry.viewmodel.LogEntry
import com.magcry.viewmodel.LogKind
import kotlinx.coroutines.launch

@Composable
fun TradeLogSheet(entries: List<LogEntry>, onDismiss: () -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll to bottom when new entries are added
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(entries.lastIndex)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0.08f, 0.08f, 0.08f))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0.12f, 0.12f, 0.12f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Trade History",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onDismiss) {
                Text("Done", color = Color.White)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                LogEntryRow(entry)
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry) {
    when (val kind = entry.kind) {
        is LogKind.PhaseChange -> {
            SectionHeader(kind.label)
        }
        is LogKind.CardReveal -> {
            SectionHeader("Reveal ${kind.index} -- Card: ${kind.card}")
        }
        is LogKind.BotAsksYou -> {
            EventRow(
                label = kind.botName,
                detail = "asks for your price",
                labelColor = Color(0xFFFFA500),  // orange
                detailColor = Color.White.copy(alpha = 0.7f),
                bold = true
            )
        }
        is LogKind.YourQuote -> {
            EventRow(
                label = "You",
                detail = "quoted ${kind.botName}: ${kind.bid} - ${kind.ask}",
                labelColor = Color.Cyan,
                detailColor = Color.White.copy(alpha = 0.6f),
                bold = false
            )
        }
        is LogKind.YourBuy -> {
            TradeRow(
                action = "BUY",
                counterparty = kind.botName,
                price = kind.price,
                color = Color.Green,
                isYours = true
            )
        }
        is LogKind.YourSell -> {
            TradeRow(
                action = "SELL",
                counterparty = kind.botName,
                price = kind.price,
                color = Color.Red,
                isYours = true
            )
        }
        is LogKind.YourPass -> {
            EventRow(
                label = "You",
                detail = "passed on ${kind.botName}",
                labelColor = Color.White.copy(alpha = 0.5f),
                detailColor = Color.White.copy(alpha = 0.4f),
                bold = false
            )
        }
        is LogKind.BotBuys -> {
            TradeRow(
                action = "BUYS",
                counterparty = kind.botName,
                price = kind.price,
                color = Color.Green,
                isYours = true
            )
        }
        is LogKind.BotSells -> {
            TradeRow(
                action = "SELLS",
                counterparty = kind.botName,
                price = kind.price,
                color = Color.Red,
                isYours = true
            )
        }
        is LogKind.BotWalks -> {
            EventRow(
                label = kind.botName,
                detail = "walks away",
                labelColor = Color.White.copy(alpha = 0.5f),
                detailColor = Color.White.copy(alpha = 0.4f),
                bold = false
            )
        }
        is LogKind.BotTrade -> {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(kind.buyer, color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp)
                Text("buys from", color = Color.White.copy(alpha = 0.25f), fontSize = 12.sp)
                Text(kind.seller, color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp)
                Text("at ${kind.price}", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp)
            }
        }
        is LogKind.Info -> {
            Text(
                text = kind.message,
                color = if (kind.important) Color.Yellow.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.35f),
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun EventRow(
    label: String,
    detail: String,
    labelColor: Color,
    detailColor: Color,
    bold: Boolean
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 14.sp,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal
        )
        Text(
            text = detail,
            color = detailColor,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun TradeRow(
    action: String,
    counterparty: String,
    price: Int,
    color: Color,
    isYours: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isYours) Modifier.background(color.copy(alpha = 0.08f)) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Action badge — outlined text
        Text(
            text = action,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Text(
            text = counterparty,
            color = if (isYours) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.4f),
            fontSize = 14.sp
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "@ $price",
            color = if (isYours) Color.White else Color.White.copy(alpha = 0.4f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
