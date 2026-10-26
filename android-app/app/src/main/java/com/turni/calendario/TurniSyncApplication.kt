package com.turni.calendario

import android.app.Application
import com.turni.calendario.repository.LocalDatabaseStore

class TurniSyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LocalDatabaseStore.start(this)
        com.turni.calendario.ui.screens.GoogleAccountSession.initialize(this)
        com.turni.calendario.sync.DriveAutomaticBackup.ensureScheduled(this)
    }
}
