package com.magcry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.magcry.model.ScoreStore
import com.magcry.ui.theme.MagCryTheme
import com.magcry.viewmodel.GameViewModel
import com.magcry.viewmodel.GameViewModel.Screen

@Composable
fun MagCryApp(vm: GameViewModel) {
    val context = LocalContext.current

    // Initialize ScoreStore if not yet set
    if (vm.scoreStore == null) {
        vm.scoreStore = ScoreStore.create(context)
    }

    MagCryTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF141414))
        ) {
            when (vm.screen) {
                Screen.MainMenu -> MainMenuScreen(vm)
                Screen.Playing -> GameScreen(vm)
                Screen.Settlement -> SettlementScreen(vm)
                Screen.Stats -> {
                    vm.scoreStore?.let { store ->
                        StatsScreen(scoreStore = store, onDismiss = { vm.backToMenu() })
                    }
                }
            }
        }
    }
}
