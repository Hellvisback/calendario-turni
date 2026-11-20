package com.turni.calendario.repository

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.turni.calendario.model.*
import java.io.File

enum class FirstRunDecision { START, RESUME, READY }

/** Decide before autosave creates a database, including an interrupted first launch. */
fun firstRunDecision(recorded: String?, hasLocalDatabase: Boolean): FirstRunDecision = when(recorded) {
    // Completion belongs to the installation, not to the current database-file visibility.
    // Missing/unreadable data is handled by storage and restore, never by restarting setup.
    "complete" -> FirstRunDecision.READY
    "pending" -> FirstRunDecision.RESUME
    else -> if(hasLocalDatabase) FirstRunDecision.READY else FirstRunDecision.START
}

/** Restored completion alone must not authorize uploading a newly created local snapshot. */
internal fun firstRunRecoveryPreferences(recorded: String?, hasLocalDatabase: Boolean, preferences: AppPreferences): AppPreferences =
    if (recorded == "complete" && !hasLocalDatabase) preferences.copy(autoBackupDaily = false) else preferences

/** Clamp persisted or requested onboarding steps within the current 0..LAST_STEP range. */
internal fun normalizeFirstRunStep(step: Int): Int = step.coerceIn(0, FirstRunStore.LAST_STEP)

object FirstRunStore {
    const val LAST_STEP = 4
    var required by mutableStateOf(false); private set
    var step by mutableStateOf(0); private set
    private const val PREFS="turnisync_first_run_v1"
    fun initialize(context:Context) {
        val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val file=File(context.filesDir,"turnisync-database-v3.json")
        val existing=file.exists()||File(file.path+".bak").exists()||
            File(context.filesDir,"cloud_backups/turni_cloud_backup_latest.json").exists()
        val recorded=prefs.getString("state",null)
        val decision=firstRunDecision(recorded,existing)
        ShiftRepository.appPreferences=firstRunRecoveryPreferences(recorded,existing,ShiftRepository.appPreferences)
        required=decision!=FirstRunDecision.READY
        step=normalizeFirstRunStep(prefs.getInt("step",0))
        if(decision==FirstRunDecision.START || (decision==FirstRunDecision.RESUME&&!existing)) {
            step=0
            // No personal examples, automatic on-call days or enabled alarms on a new installation.
            val base=DatabaseSnapshot.capture()
            com.turni.calendario.sync.DatabaseRestore.apply(base.copy(
                definitions=emptyList(),rotations=emptyList(),shifts=emptyMap(),extras=emptyMap(),
                profiles=listOf(WorkProfile("principale","Il mio lavoro",ccnlCode="CCNL_PERSONALIZZATO",ccnlName="Personalizzato",weeklyHours=40)),
                activeWorkProfileId="principale",activeRotationId="",defaultRotationId="",
                onCall=ReperibilitaConfig(mode=ReperibilitaMode.MANUALE,shiftSpecificIds=emptyList()),
                alarms=SmartAlarmsConfig(isGlobalEnabled=false,rules=emptyList()),
                preferences=AppPreferences(autoBackupDaily=false),statisticsRules=emptyList(),dayTags=emptyList()))
        }
        prefs.edit().putString("state",if(required)"pending" else "complete").putInt("step",step).commit()
    }
    fun goTo(context:Context,value:Int) {
        step=normalizeFirstRunStep(value)
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putInt("step",step).apply()
    }
    fun complete(context:Context) {
        check(context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("state","complete").putInt("step",0).commit())
        required=false
    }
}

/** Explicitly chosen starting examples. The calendar remains empty. */
fun starterShiftDefinitions(workId:String)=listOf(
    ShiftDefinition("starter-morning","Mattina","MAT","06:00","14:00",colorHex=0xFFF2C46D,workProfileId=workId),
    ShiftDefinition("starter-afternoon","Pomeriggio","POM","14:00","22:00",colorHex=0xFFEE9C9C,workProfileId=workId),
    ShiftDefinition("starter-night","Notte","NOT","22:00","06:00",colorHex=0xFFBAAEF6,workProfileId=workId),
    ShiftDefinition("starter-rest","Riposo","RIP",isAllDay=true,colorHex=0xFFAAB9CE,category=ShiftCategory.RIPOSO,workProfileId=workId),
    ShiftDefinition("starter-leave","Ferie","FER",isAllDay=true,colorHex=0xFFB4D995,category=ShiftCategory.ASSENZA,workProfileId=workId)
)

/** Return missing starter templates only when the target work profile has no models yet. */
internal fun missingStarterShiftDefinitions(
    workId: String,
    existing: List<ShiftDefinition>
): List<ShiftDefinition> {
    if (existing.any { it.workProfileId == workId }) return emptyList()
    val existingIds = existing.mapTo(HashSet()) { it.id }
    return starterShiftDefinitions(workId).filterNot { it.id in existingIds }
}
