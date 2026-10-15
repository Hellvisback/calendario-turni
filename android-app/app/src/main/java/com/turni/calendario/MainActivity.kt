package com.turni.calendario

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.screens.MainScreen
import com.turni.calendario.ui.theme.CalendarioTurniTheme
import com.turni.calendario.widget.WidgetUpdateManager
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Aggancia l'ascoltatore per aggiornamento in tempo reale dei Widget
        ShiftRepository.onDataChangedListener = {
            WidgetUpdateManager.notifyWidgetsChanged(applicationContext)
        }

        // Gestione eventuale apertura da click su giorno del widget
        handleWidgetDeepLink(intent)

        if (ShiftRepository.appPreferences.autoBackupDaily) {
            com.turni.calendario.sync.GoogleDriveBackupHelper.saveCloudBackupDirect(applicationContext)
        }

        // Aggiorna tutti i widget all'avvio dell'app per sincronizzare orari e date odierne
        WidgetUpdateManager.updateAllWidgets(applicationContext)

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
