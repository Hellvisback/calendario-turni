package com.turni.calendario.repository

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.turni.calendario.sync.DatabaseRestore
import com.turni.calendario.sync.GoogleDriveBackupHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

/** Atomic local storage independent of optional exported/cloud backups. */
object LocalDatabaseStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false
    private var persistenceBlocked = false
    private var lastSaved: DatabaseSnapshot? = null
    var loadError by mutableStateOf<String?>(null)
        private set

    @OptIn(FlowPreview::class)
    fun start(context: Context) {
        if (started) return
        started = true
        val appContext = context.applicationContext
        val atomic = AtomicFile(File(appContext.filesDir, "turnisync-database-v3.json"))
        val legacyBackup = File(appContext.filesDir, "cloud_backups/turni_cloud_backup_latest.json")
        val designPreferences=appContext.getSharedPreferences("turnisync_theme",Context.MODE_PRIVATE)
        // Startup is intentionally synchronous before any screen/widget can read defaults.
        // Subsequent writes and encoding run exclusively on the IO dispatcher.
        try {
            val text = when {
                atomic.baseFile.exists() || File(atomic.baseFile.path + ".bak").exists() -> atomic.openRead().bufferedReader().use { it.readText() }
                legacyBackup.isFile -> legacyBackup.readText(Charsets.UTF_8)
                else -> null
            }
            if (text != null) DatabaseRestore.apply(DatabaseRestore.parse(text))
            if(!designPreferences.getBoolean("approved_palette_adopted",false)) {
                DatabaseRestore.apply(adoptApprovedPalette(DatabaseSnapshot.capture()))
            }
        } catch (error: Exception) {
            // Preserve damaged input for recovery instead of replacing it with seeded defaults.
            persistenceBlocked = true
            loadError = "Dati locali non leggibili. Il file originale è stato conservato: ripristina un backup valido prima di salvare nuove modifiche."
            Log.e("TurniSyncStorage", "Local database could not be loaded; original file preserved", error)
        }
        scope.launch {
            snapshotFlow { DatabaseSnapshot.capture() }
                .debounce(180)
                .collect { snapshot ->
                    if (!persistenceBlocked && snapshot != lastSaved) {
                        withContext(Dispatchers.IO) {
                            var stream: java.io.FileOutputStream? = null
                            try {
                                val json = GoogleDriveBackupHelper.exportDatabaseToJson(snapshot)
                                stream = atomic.startWrite()
                                stream!!.write(json.toByteArray(Charsets.UTF_8))
                                atomic.finishWrite(stream)
                                stream=null
                                if(!designPreferences.getBoolean("approved_palette_adopted",false))
                                    designPreferences.edit().putBoolean("approved_palette_adopted",true).apply()
                                lastSaved = snapshot
                                val readSnapshot=androidx.compose.runtime.snapshots.Snapshot.takeSnapshot()
                                try {
                                    readSnapshot.enter {
                                        com.turni.calendario.widget.WidgetUpdateManager.notifyWidgetsChanged(appContext)
                                        com.turni.calendario.alarm.ShiftAlarmScheduler.scheduleNextAlarm(appContext)
                                    }
                                } finally { readSnapshot.dispose() }
                            } catch (error: Exception) {
                                if (stream != null) atomic.failWrite(stream)
                                Log.e("TurniSyncStorage", "Atomic save failed; last valid database preserved", error)
                            }
                        }
                    }
                }
        }
    }

    /** Explicit, validated user restore may replace an unreadable local file. */
    fun allowPersistenceAfterRestore() { persistenceBlocked = false; loadError = null }
}
