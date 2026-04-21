package com.magcry.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.weight(1f))

        Image(
            painter = painterResource(R.drawable.logo),
            contentDescription = "MagCry Logo",
            modifier = Modifier.size(120.dp),
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "MagCry",
            color = Color.White,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(32.dp))

        DifficultyButton("Easy", Color(0xFF4CAF50)) { vm.startGame(Difficulty.EASY) }
        Spacer(Modifier.height(12.dp))
        DifficultyButton("Medium", Color(0xFFFF9800)) { vm.startGame(Difficulty.MEDIUM) }
        Spacer(Modifier.height(12.dp))
        DifficultyButton("Hard", Color(0xFFF44336)) { vm.startGame(Difficulty.HARD) }

        Spacer(Modifier.height(24.dp))

        TextButton(onClick = { showInstructions = true }) {
            Text("How to Play", color = Color.White)
        }

        TextButton(onClick = { vm.startTutorial() }) {
            Text("Tutorial", color = Color(0xFF00BCD4))
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = "Based on",
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "The Trading Game by Gary Stevenson",
            color = Color(0xFF00BCD4),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.thetradinggame.co.uk")))
            },
        )

        Spacer(Modifier.height(12.dp))

        Image(
            painter = painterResource(R.drawable.bmc_button),
            contentDescription = "Buy me a coffee",
            modifier = Modifier
                .height(40.dp)
                .clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/conorgee")))
                },
        )

        Spacer(Modifier.height(16.dp))
    }

    if (showInstructions) {
        ModalBottomSheet(onDismissRequest = { showInstructions = false }) {
            InstructionsSheet(onDismiss = { showInstructions = false })
        }
    }
}

@Composable
private fun DifficultyButton(label: String, color: Color, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.width(200.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        border = BorderStroke(1.dp, color),
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}
