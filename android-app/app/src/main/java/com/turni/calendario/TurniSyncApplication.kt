package com.turni.calendario

import android.app.Application
import com.turni.calendario.repository.LocalDatabaseStore

class TurniSyncApplication : Application() {
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        com.turni.calendario.ui.theme.TurniThemeState.refreshSystem(this)
        if (com.turni.calendario.ui.theme.TurniThemeState.storedMode(this) == com.turni.calendario.ui.theme.ThemeMode.AUTOMATIC)
            com.turni.calendario.widget.WidgetUpdateManager.notifyWidgetsChanged(this)
    }

    override fun onCreate() {
        super.onCreate()
        com.turni.calendario.ui.theme.TurniThemeState.initialize(this)
        LocalDatabaseStore.start(this)
        com.turni.calendario.ui.screens.GoogleAccountSession.initialize(this)
        com.turni.calendario.sync.DriveAutomaticBackup.ensureScheduled(this)
    }
}
