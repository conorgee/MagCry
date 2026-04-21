package com.magcry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magcry.viewmodel.LogEntry
import com.magcry.viewmodel.LogKind

@Composable
fun TradeLogSheet(entries: List<LogEntry>, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Trade History",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onDismiss) {
                Text("Done", color = Color.Cyan)
            }
        }

        HorizontalDivider(color = Color(0xFF333333))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            items(entries) { entry ->
                LogEntryRow(entry)
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry) {
    when (val kind = entry.kind) {
        is LogKind.PhaseChange -> {
            SectionLabel(kind.label.uppercase())
        }
        is LogKind.CardReveal -> {
            SectionLabel("CENTRAL CARD ${kind.index} REVEALED: ${kind.card}")
        }
        is LogKind.BotAsksYou -> {
            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(kind.botName, color = Color(0xFFFFAA00), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Text("asks for your price", color = Color.White, fontSize = 13.sp)
            }
        }
        is LogKind.YourQuote -> {
            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("You", color = Color.Cyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Text("quoted ${kind.botName}: ${kind.bid} - ${kind.ask}", color = Color.White, fontSize = 13.sp)
            }
        }
        is LogKind.YourBuy -> {
            TradeRow(
                badge = "BUY",
                badgeColor = Color(0xFF22AA44),
                bgColor = Color(0xFF0A2A0A),
                name = "from ${kind.botName}",
                detail = "@ ${kind.price}",
                detailColor = Color(0xFF88DD88)
            )
        }
        is LogKind.YourSell -> {
            TradeRow(
                badge = "SELL",
                badgeColor = Color(0xFFDD4444),
                bgColor = Color(0xFF2A0A0A),
                name = "to ${kind.botName}",
                detail = "@ ${kind.price}",
                detailColor = Color(0xFFFF8888)
            )
        }
        is LogKind.YourPass -> {
            Text(
                text = "Passed on ${kind.botName}'s quote",
                color = Color(0xFF666666),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        is LogKind.BotBuys -> {
            TradeRow(
                badge = "BUY",
                badgeColor = Color(0xFF22AA44),
                bgColor = Color.Transparent,
                name = kind.botName,
                detail = "buys at ${kind.price}",
                detailColor = Color(0xFF88DD88)
            )
        }
        is LogKind.BotSells -> {
            TradeRow(
                badge = "SELL",
                badgeColor = Color(0xFFDD4444),
                bgColor = Color.Transparent,
                name = kind.botName,
                detail = "sells at ${kind.price}",
                detailColor = Color(0xFFFF8888)
            )
        }
        is LogKind.BotWalks -> {
            Text(
                text = "${kind.botName} walks away",
                color = Color(0xFF666666),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        is LogKind.BotTrade -> {
            Text(
                text = "${kind.buyer} buys from ${kind.seller} at ${kind.price}",
                color = Color(0xFF444444),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        is LogKind.Info -> {
            Text(
                text = kind.message,
                color = if (kind.important) Color(0xFFDDCC44) else Color(0xFF555555),
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(Color(0xFF2A2A2A), RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = Color(0xFF888888),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun TradeRow(
    badge: String,
    badgeColor: Color,
    bgColor: Color,
    name: String,
    detail: String,
    detailColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Badge(containerColor = badgeColor) {
            Text(badge, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(6.dp))
        Text(name, color = Color.White, fontSize = 13.sp)
        Spacer(Modifier.width(4.dp))
        Text(detail, color = detailColor, fontSize = 13.sp)
    }
}
