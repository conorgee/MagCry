package com.magcry.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import com.magcry.model.TutorialManager
import com.magcry.model.TutorialTrigger

@Composable
fun TutorialCoachView(manager: TutorialManager) {
    val step = manager.currentStep ?: return

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF242424).copy(alpha = 0.95f)
        ),
        border = BorderStroke(1.dp, Color.Cyan),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .then(
                if (step.requiresTap) {
                    Modifier.clickable { manager.advance(TutorialTrigger.USER_TAPPED) }
                } else {
                    Modifier
                }
            )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = step.message,
                color = Color.White,
                fontSize = 15.sp,
                lineHeight = 21.sp
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { manager.skip() }) {
                    Text("Skip", color = Color(0xFF666666), fontSize = 13.sp)
                }

                if (step.requiresTap) {
                    Text(
                        text = "Tap to continue",
                        color = Color.Cyan.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
