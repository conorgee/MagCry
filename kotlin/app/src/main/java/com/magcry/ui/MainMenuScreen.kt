package com.magcry.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magcry.R
import com.magcry.model.Difficulty
import com.magcry.viewmodel.GameViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(vm: GameViewModel) {
    val context = LocalContext.current
    var showInstructions by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            // Logo — crow artwork, seamless on black, fills width minus 40dp padding
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = "MagCry Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
                    .aspectRatio(1536f / 650f)
                    .background(Color.Black),
            )

            // Title
            Text(
                text = "MagCry",
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 36.dp)
            )

            // Difficulty buttons — outlined, no fill
            Column(
                modifier = Modifier.padding(horizontal = 48.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DifficultyButton(
                    label = "Easy",
                    color = Color(red = 0.2f, green = 0.7f, blue = 0.3f),
                    onClick = { vm.startGame(Difficulty.EASY) }
                )
                DifficultyButton(
                    label = "Medium",
                    color = Color(red = 0.85f, green = 0.6f, blue = 0.15f),
                    onClick = { vm.startGame(Difficulty.MEDIUM) }
                )
                DifficultyButton(
                    label = "Hard",
                    color = Color(red = 0.8f, green = 0.25f, blue = 0.25f),
                    onClick = { vm.startGame(Difficulty.HARD) }
                )
            }

            // How to Play
            TextButton(
                onClick = { showInstructions = true },
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Text(
                    "How to Play",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            }

            // Tutorial
            TextButton(
                onClick = { vm.startTutorial() },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    "Tutorial",
                    color = Color.Cyan.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }

            // Stats
            TextButton(
                onClick = { showStats = true },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    "Stats",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.weight(1f))

            // Attribution
            Column(
                modifier = Modifier.padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Based on",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 10.sp,
                )
                Text(
                    text = "The Trading Game by Gary Stevenson",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 10.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.amazon.com/Trading-Game-Confession-Gary-Stevenson/dp/0593727215"))
                        )
                    },
                )
                Image(
                    painter = painterResource(R.drawable.bmc_button),
                    contentDescription = "Buy me a coffee",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .height(32.dp)
                        .clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/conorgee"))
                            )
                        },
                )
            }
        }
    }

    // Instructions bottom sheet
    if (showInstructions) {
        ModalBottomSheet(onDismissRequest = { showInstructions = false }) {
            InstructionsSheet(onDismiss = { showInstructions = false })
        }
    }

    // Stats bottom sheet
    if (showStats) {
        ModalBottomSheet(onDismissRequest = { showStats = false }) {
            vm.scoreStore?.let { store ->
                StatsScreen(scoreStore = store, onDismiss = { showStats = false })
            }
        }
    }
}

@Composable
private fun DifficultyButton(label: String, color: Color, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.6f)),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) {
        Text(
            label,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
