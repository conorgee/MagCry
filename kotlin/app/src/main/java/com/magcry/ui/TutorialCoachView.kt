package com.magcry.ui

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magcry.model.TutorialManager
import com.magcry.model.TutorialTrigger

/**
 * Floating coach bubble displayed during the interactive tutorial.
 * Sits at the bottom of the game screen and shows contextual guidance.
 * Non-blocking — game UI beneath remains fully interactive.
 */
@Composable
fun TutorialCoachView(manager: TutorialManager) {
    val step = manager.currentStep ?: return

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0.14f, 0.14f, 0.14f, 0.95f),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.SolidColor(Color.Cyan.copy(alpha = 0.25f))
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black.copy(alpha = 0.3f))
                .then(
                    if (step.requiresTap) {
                        Modifier.clickable {
                            manager.advance(TutorialTrigger.UserTapped)
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Message
                    Text(
                        text = step.message,
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.weight(1f)
                    )

                    // Skip button
                    TextButton(
                        onClick = { manager.skip() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Skip",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Tap hint for tap-to-advance steps
                if (step.requiresTap) {
                    Text(
                        text = "Tap to continue",
                        color = Color.Cyan.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}
