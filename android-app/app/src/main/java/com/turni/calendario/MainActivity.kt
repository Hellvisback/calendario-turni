package com.turni.calendario

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.screens.MainScreen
import com.turni.calendario.ui.theme.CalendarioTurniTheme
import com.turni.calendario.ui.theme.TurniThemeState
import com.turni.calendario.ui.theme.ObsidianBackground
import androidx.compose.ui.graphics.toArgb
import com.turni.calendario.widget.WidgetUpdateManager
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        // Match the stored theme before Compose draws its first frame.
        val backgroundColor = ObsidianBackground.toArgb()
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        window.decorView.setBackgroundColor(backgroundColor)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !TurniThemeState.dark
            isAppearanceLightNavigationBars = !TurniThemeState.dark
        }

        // LocalDatabaseStore coalesces durable saves, widget refreshes and alarm scheduling off main.

        // Gestione eventuale apertura da click su giorno del widget
        handleWidgetDeepLink(intent)

        if (ShiftRepository.appPreferences.autoBackupDaily) {
            lifecycleScope.launch(Dispatchers.IO) {
                val snapshot=androidx.compose.runtime.snapshots.Snapshot.takeSnapshot()
                try { snapshot.enter { com.turni.calendario.sync.GoogleDriveBackupHelper.saveLocalBackupDirect(applicationContext) } }
                finally { snapshot.dispose() }
            }
        }

        setContent {
            CalendarioTurniTheme {
                androidx.compose.animation.Crossfade(com.turni.calendario.repository.FirstRunStore.required,
                    animationSpec=androidx.compose.animation.core.tween(300)) { setup ->
                    if(setup)com.turni.calendario.ui.screens.FirstRunScreen()
                    else MainScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetDeepLink(intent)
    }

    private fun handleWidgetDeepLink(intent: Intent?) {
        val dateStr = intent?.getStringExtra(WidgetUpdateManager.EXTRA_SELECTED_DATE)
        if (!dateStr.isNullOrBlank()) {
            try {
                val parsed = LocalDate.parse(dateStr)
                ShiftRepository.selectedDateFromWidget = parsed
            } catch (_: Exception) {}
        }
    }
}
