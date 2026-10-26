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
import com.turni.calendario.widget.WidgetUpdateManager
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        // Sincronizza lo sfondo delle barre di sistema (status bar e navigation bar) con ObsidianBackground (#080B11)
        // per rimuovere qualsiasi bordino o contrasto ai bordi superiore e inferiore dello schermo
        val obsidianDarkColor = android.graphics.Color.parseColor("#080B11")
        window.statusBarColor = obsidianDarkColor
        window.navigationBarColor = obsidianDarkColor
        window.decorView.setBackgroundColor(obsidianDarkColor)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
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
                MainScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
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
