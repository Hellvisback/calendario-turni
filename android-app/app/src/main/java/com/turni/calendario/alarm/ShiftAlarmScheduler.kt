package com.turni.calendario.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.turni.calendario.MainActivity
import com.turni.calendario.model.ShiftAlarmRule
import com.turni.calendario.repository.ShiftRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class UpcomingAlarmInfo(
    val id: String,
    val date: LocalDate,
    val triggerDateTime: LocalDateTime,
    val shiftName: String,
    val shiftColorHex: Long,
    val isReperibilita: Boolean,
    val message: String,
    val isEnabled: Boolean = true
)

object ShiftAlarmScheduler {
    private const val TAG = "ShiftAlarmScheduler"
    private const val ALARM_REQUEST_CODE = 4040
    private const val TEST_REQUEST_CODE = 4042

    /**
     * Risolve l'orario effettivo di sveglia per una specifica regola
     */
    fun getResolvedAlarmTime(rule: ShiftAlarmRule): LocalTime? {
        if (!rule.customTime.isNullOrBlank()) {
            val parts = rule.customTime.split(":")
            if (parts.size == 2) {
                val h = parts[0].trim().toIntOrNull()
                val m = parts[1].trim().toIntOrNull()
                if (h != null && m != null && h in 0..23 && m in 0..59) {
                    return LocalTime.of(h, m)
                }
            }
            return null
        }
        val def = ShiftRepository.getShiftDefinition(rule.shiftDefId) ?: return null
        return try {
            val startTime = LocalTime.parse(def.startTime)
            startTime.minusMinutes(rule.minutesBefore.coerceIn(0, 10080).toLong())
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Restituisce tutte le sveglie calcolate per i turni e la reperibilità nei prossimi N giorni
     */
    fun getAllUpcomingAlarms(context: Context, daysAhead: Int = 30): List<UpcomingAlarmInfo> =
        calculateUpcomingAlarms(LocalDateTime.now(), daysAhead)

    /** Deterministic calendar calculation, independent of Android services. */
    fun calculateUpcomingAlarms(now: LocalDateTime, daysAhead: Int = 30): List<UpcomingAlarmInfo> {
        if (daysAhead < 0) return emptyList()
        val config = ShiftRepository.smartAlarmsConfig
        val startDate = now.toLocalDate()
        // A shift shortly after midnight may need its alarm on the previous day.
        // Include tomorrow's assignments, then constrain the result by trigger date.
        val lastTriggerDate = startDate.plusDays(daysAhead.toLong())
        val leadDays = (config.rules.maxOfOrNull { it.minutesBefore.coerceIn(0, 10080) } ?: 0) / 1440 + 1
        val endDate = lastTriggerDate.plusDays(leadDays.toLong())

        val repRule = config.rules.firstOrNull { it.shiftDefId == "reperibilita" }
        val candidates = mutableListOf<UpcomingAlarmInfo>()

        var curr = startDate
        while (!curr.isAfter(endDate)) {
            // 1. REPERIBILITÀ: Avviso la sera del giorno precedente
            val nextDay = curr.plusDays(1)
            val isNextDayOnCall = ShiftRepository.isOnCall(nextDay)
            if (isNextDayOnCall && repRule != null) {
                val repTime = getResolvedAlarmTime(repRule)
                    ?: if (repRule.customTime.isNullOrBlank()) LocalTime.of(20, 0) else null
                val alarmDt = repTime?.let { LocalDateTime.of(curr, it) }
                if (alarmDt != null && alarmDt.isAfter(now)) {
                    val alarmId = "${curr}_rep"
                    val isAlarmDisabled = !config.isGlobalEnabled || !repRule.isEnabled || config.disabledAlarmIds.contains(alarmId)
                    candidates.add(
                        UpcomingAlarmInfo(
                            id = alarmId,
                            date = nextDay,
                            triggerDateTime = alarmDt,
                            shiftName = "Reperibilità",
                            shiftColorHex = 0xFFF59E0B,
                            isReperibilita = true,
                            message = "Promemoria: domani sei di Reperibilità!",
                            isEnabled = !isAlarmDisabled
                        )
                    )
                }
            }

            // 2. TURNI DELLA GIORNATA
            val dayShifts = ShiftRepository.getShiftsForDate(curr)

            for (rule in config.rules) {
                if (rule.shiftDefId == "reperibilita") continue
                val assigned = dayShifts.filter { it.shiftDefId == rule.shiftDefId }
                if (assigned.isNotEmpty()) {
                    val shiftDef = ShiftRepository.getShiftDefinition(rule.shiftDefId)
                    if (shiftDef != null) {
                        // Keep the existing model/date alarm ID and select the next matching
                        // assignment when the same model occurs more than once on a date.
                        val alarmDt = assigned.mapNotNull { shift ->
                            try {
                                if (!rule.customTime.isNullOrBlank()) {
                                    LocalDateTime.of(curr, LocalTime.parse(rule.customTime.trim()))
                                } else {
                                    LocalDateTime.of(curr, LocalTime.parse(shift.customStartTime ?: shiftDef.startTime))
                                        .minusMinutes(rule.minutesBefore.coerceIn(0, 10080).toLong())
                                }
                            } catch (_: Exception) { null }
                        }.filter { it.isAfter(now) }.minOrNull()
                        if (alarmDt != null) {
                            val alarmId = "${curr}_${rule.shiftDefId}"
                            val isAlarmDisabled = !config.isGlobalEnabled || !rule.isEnabled || config.disabledAlarmIds.contains(alarmId)
                            candidates.add(
                                UpcomingAlarmInfo(
                                    id = alarmId,
                                    date = curr,
                                    triggerDateTime = alarmDt,
                                    shiftName = shiftDef.name,
                                    shiftColorHex = shiftDef.colorHex,
                                    isReperibilita = false,
                                    message = "Sveglia Turno: ${shiftDef.name} (${shiftDef.startTime} - ${shiftDef.endTime})",
                                    isEnabled = !isAlarmDisabled
                                )
                            )
                        }
                    }
                }
            }

            curr = curr.plusDays(1)
        }

        return candidates.filter { !it.triggerDateTime.toLocalDate().isAfter(lastTriggerDate) }.sortedBy { it.triggerDateTime }
    }

    /**
     * Finds the next enabled alarm in the next twelve months.
     */
    fun getNextUpcomingAlarm(context: Context): UpcomingAlarmInfo? {
        val config = ShiftRepository.smartAlarmsConfig
        if (!config.isGlobalEnabled) return null
        val all = getAllUpcomingAlarms(context, 365)
        return all.firstOrNull { it.isEnabled }
    }

    /**
     * Programma la prossima sveglia precisa con AlarmManager.setAlarmClock()
     */
    fun scheduleNextAlarm(context: Context): UpcomingAlarmInfo? {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return null
        val next = getNextUpcomingAlarm(context)

        val intent = Intent(context, ShiftAlarmReceiver::class.java).apply {
            action = ShiftAlarmReceiver.ACTION_TRIGGER_SHIFT_ALARM
            if (next != null) {
                putExtra("shift_name", next.shiftName)
                putExtra("message", next.message)
                putExtra("color_hex", next.shiftColorHex)
                putExtra("is_reperibilita", next.isReperibilita)
            }
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (next == null) {
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Nessuna sveglia futura da schedulare, cancellato eventuale allarme pendente.")
            return null
        }

        // Never report success when Android cannot deliver a visible, exact alarm.
        if (!canScheduleExactAlarms(context) || !hasNotificationPermission(context)) {
            alarmManager.cancel(pendingIntent)
            return null
        }

        val epochMillis = resolveEpochMillis(next.triggerDateTime, ZoneId.systemDefault())

        val showIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            ALARM_REQUEST_CODE + 1,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val clockInfo = AlarmManager.AlarmClockInfo(epochMillis, showPendingIntent)
            alarmManager.setAlarmClock(clockInfo, pendingIntent)
            Log.d(TAG, "Sveglia programmata con successo per ${next.triggerDateTime}: ${next.shiftName}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Exact alarm permission denied", e)
            return null
        } catch (e: RuntimeException) {
            Log.e(TAG, "Exact alarm registration failed", e)
            return null
        }

        return next
    }

    /**
     * Cancella la sveglia attualmente programmata
     */
    fun cancelScheduledAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ShiftAlarmReceiver::class.java).apply {
            action = ShiftAlarmReceiver.ACTION_TRIGGER_SHIFT_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /**
     * Attiva o disattiva un singolo allarme per ID specifico
     */
    fun toggleAlarmId(context: Context, alarmId: String, enable: Boolean): UpcomingAlarmInfo? {
        val config = ShiftRepository.smartAlarmsConfig
        val updatedSet = if (enable) {
            config.disabledAlarmIds - alarmId
        } else {
            config.disabledAlarmIds + alarmId
        }
        ShiftRepository.smartAlarmsConfig = config.copy(disabledAlarmIds = updatedSet)
        return scheduleNextAlarm(context)
    }

    /**
     * Attiva o disattiva in blocco tutte le sveglie future
     */
    fun toggleAllAlarms(context: Context, enableAll: Boolean): UpcomingAlarmInfo? {
        val config = ShiftRepository.smartAlarmsConfig
        if (enableAll) {
            ShiftRepository.smartAlarmsConfig = config.copy(
                isGlobalEnabled = true,
                disabledAlarmIds = emptySet()
            )
            return scheduleNextAlarm(context)
        } else {
            // A global off switch must remain off beyond the current thirty-day view.
            ShiftRepository.smartAlarmsConfig = config.copy(isGlobalEnabled = false)
            cancelScheduledAlarm(context)
            return null
        }
    }

    /**
     * Verifica autorizzazione per programmare allarmi esatti (Android 12+)
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            am?.canScheduleExactAlarms() ?: false
        } else {
            true
        }
    }

    /**
     * Verifica autorizzazione per mostrare notifiche (Android 13+)
     */
    fun hasNotificationPermission(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return false
        if (!manager.areNotificationsEnabled()) return false
        if (manager.getNotificationChannel(ShiftAlarmReceiver.CHANNEL_ID)?.importance == android.app.NotificationManager.IMPORTANCE_NONE) return false
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context,
                "android.permission.POST_NOTIFICATIONS"
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /** Uses the same receiver/service as production alarms without changing any shift rule. */
    fun scheduleTestAlarm(context: Context, delaySeconds: Int = 10): Boolean {
        ShiftAlarmService.ensureChannel(context)
        if (!canScheduleExactAlarms(context) || !hasNotificationPermission(context)) return false
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        val intent = Intent(context, ShiftAlarmReceiver::class.java).apply {
            action = ShiftAlarmReceiver.ACTION_TRIGGER_SHIFT_ALARM
            putExtra("shift_name", "Sveglia di prova")
            putExtra("message", "Prova reale di suono, vibrazione e notifica TurniSync")
            putExtra("is_test", true)
        }
        val operation = PendingIntent.getBroadcast(context, TEST_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val show = PendingIntent.getActivity(context, TEST_REQUEST_CODE, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return try {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(System.currentTimeMillis() + delaySeconds.coerceIn(5, 60) * 1000L, show), operation)
            true
        } catch (error: RuntimeException) {
            Log.e(TAG, "Test alarm registration failed", error)
            false
        }
    }

    fun cancelTestAlarm(context: Context) {
        val intent = Intent(context, ShiftAlarmReceiver::class.java).setAction(ShiftAlarmReceiver.ACTION_TRIGGER_SHIFT_ALARM)
        val operation = PendingIntent.getBroadcast(context, TEST_REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
        (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(operation)
        operation.cancel()
        context.stopService(Intent(context, ShiftAlarmService::class.java))
    }

    /** Resolve DST gaps/overlaps using the user's current time zone at scheduling. */
    fun resolveEpochMillis(dateTime: LocalDateTime, zone: ZoneId): Long = dateTime.atZone(zone).toInstant().toEpochMilli()
}
