package com.magcry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magcry.model.Difficulty
import com.magcry.model.DifficultyStats
import com.magcry.model.ScoreStore

@Composable
fun StatsScreen(scoreStore: ScoreStore, onDismiss: () -> Unit) {
    var selectedDifficulty by remember { mutableStateOf(Difficulty.EASY) }
    var showResetAlert by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0.08f, 0.08f, 0.08f))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                text = "Stats",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) {
                Text("Done", color = Color.Cyan)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Difficulty picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (diff in Difficulty.entries) {
                    FilterChip(
                        selected = selectedDifficulty == diff,
                        onClick = { selectedDifficulty = diff },
                        label = { Text(diff.label) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.Cyan.copy(alpha = 0.2f),
                            selectedLabelColor = Color.Cyan,
                        )
                    )
                }
            }

            val stats = scoreStore.statsFor(selectedDifficulty)

            if (stats.gamesPlayed == 0) {
                // No data
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp, start = 32.dp, end = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No games played yet",
                        color = Color.Gray,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Play a game on ${selectedDifficulty.label} to see your stats here.",
                        color = Color.Gray.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                StatsGrid(stats)
            }

            Spacer(Modifier.height(40.dp))

            // Reset button
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TextButton(onClick = { showResetAlert = true }) {
                    Text(
                        "Reset All Stats",
                        color = Color.Red.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // Reset confirmation
    if (showResetAlert) {
        AlertDialog(
            onDismissRequest = { showResetAlert = false },
            title = { Text("Reset all stats?") },
            text = { Text("This will permanently erase all your stats across all difficulties.") },
            confirmButton = {
                TextButton(onClick = {
                    scoreStore.resetAll()
                    showResetAlert = false
                }) {
                    Text("Reset", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAlert = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatsGrid(stats: DifficultyStats) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // Row 1: Games + Win Rate
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                title = "Games Played",
                value = "${stats.gamesPlayed}",
                accent = Color.White,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Win Rate",
                value = if (stats.gamesPlayed > 0) "${(stats.winRate * 100).toInt()}%" else "--",
                subtitle = "${stats.gamesWon} wins",
                accent = if (stats.winRate >= 0.5) Color.Green else Color(0xFFFFA500),
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Best P&L + Average P&L
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                title = "Best P&L",
                value = stats.bestPnL?.let { formatPnL(it) } ?: "--",
                accent = if ((stats.bestPnL ?: 0) > 0) Color.Green else Color.Red,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Avg P&L",
                value = if (stats.gamesPlayed > 0) formatPnL(stats.averagePnL.toInt()) else "--",
                accent = when {
                    stats.averagePnL > 0 -> Color.Green
                    stats.averagePnL < 0 -> Color.Red
                    else -> Color.Gray
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Row 3: Streaks
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                title = "Current Streak",
                value = "${stats.currentStreak}",
                subtitle = if (stats.currentStreak >= 3) "On fire" else null,
                accent = if (stats.currentStreak >= 3) Color.Yellow else Color.White,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Best Streak",
                value = "${stats.bestStreak}",
                accent = if (stats.bestStreak >= 3) Color.Yellow else Color.White,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 4: Total Trades
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                title = "Total Trades",
                value = "${stats.totalTrades}",
                accent = Color.Cyan,
                modifier = Modifier.weight(1f)
            )
            // Empty slot for balance
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String? = null,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
        Text(
            text = value,
            color = accent,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = Color.Gray,
                fontSize = 10.sp
            )
        }
    }
}

private fun formatPnL(value: Int): String = if (value >= 0) "+$value" else "$value"
