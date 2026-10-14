package com.turni.calendario.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.util.Log
import com.turni.calendario.MainActivity
import com.turni.calendario.model.ShiftAlarmRule
import com.turni.calendario.repository.ShiftRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class UpcomingAlarmInfo(
    val triggerDateTime: LocalDateTime,
    val shiftName: String,
    val shiftColorHex: Long,
    val isReperibilita: Boolean,
    val message: String
)

object ShiftAlarmScheduler {
    private const val TAG = "ShiftAlarmScheduler"
    private const val ALARM_REQUEST_CODE = 4040

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
        }
        val def = ShiftRepository.getShiftDefinition(rule.shiftDefId) ?: return null
        return try {
            val startTime = LocalTime.parse(def.startTime)
            startTime.minusMinutes(rule.minutesBefore.toLong())
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Calcola la prima sveglia futura utile nei prossimi 30 giorni
     */
    fun getNextUpcomingAlarm(context: Context): UpcomingAlarmInfo? {
        val config = ShiftRepository.smartAlarmsConfig
        if (!config.isGlobalEnabled) return null

        val now = LocalDateTime.now()
        val startDate = LocalDate.now()
        val endDate = startDate.plusDays(30)

        val repRule = config.rules.firstOrNull { it.shiftDefId == "reperibilita" }
        val candidates = mutableListOf<UpcomingAlarmInfo>()

        var curr = startDate
        while (!curr.isAfter(endDate)) {
            // 1. REPERIBILITÀ: Avviso la sera del giorno precedente
            val nextDay = curr.plusDays(1)
            val isNextDayOnCall = ShiftRepository.isOnCall(nextDay)
            if (isNextDayOnCall && repRule != null && repRule.isEnabled) {
                val repTime = getResolvedAlarmTime(repRule) ?: LocalTime.of(20, 0)
                val alarmDt = LocalDateTime.of(curr, repTime)
                if (alarmDt.isAfter(now)) {
                    candidates.add(
                        UpcomingAlarmInfo(
                            triggerDateTime = alarmDt,
                            shiftName = "Reperibilità",
                            shiftColorHex = 0xFFF59E0B,
                            isReperibilita = true,
                            message = "Promemoria: domani sei di Reperibilità!"
                        )
                    )
                }
            }

            // 2. TURNI DELLA GIORNATA
            val dayShifts = ShiftRepository.getShiftsForDate(curr)
            val planned = ShiftRepository.getPlannedRotationShift(curr)
            val targetDefIds = (dayShifts.map { it.shiftDefId } + listOfNotNull(planned?.id)).distinct()

            for (rule in config.rules) {
                if (rule.shiftDefId == "reperibilita") continue
                if (rule.isEnabled && targetDefIds.contains(rule.shiftDefId)) {
                    val shiftDef = ShiftRepository.getShiftDefinition(rule.shiftDefId)
                    val alarmTime = getResolvedAlarmTime(rule)
                    if (shiftDef != null && alarmTime != null) {
                        val alarmDt = LocalDateTime.of(curr, alarmTime)
                        if (alarmDt.isAfter(now)) {
                            candidates.add(
                                UpcomingAlarmInfo(
                                    triggerDateTime = alarmDt,
                                    shiftName = shiftDef.name,
                                    shiftColorHex = shiftDef.colorHex,
                                    isReperibilita = false,
                                    message = "Sveglia Turno: ${shiftDef.name} (${shiftDef.startTime} - ${shiftDef.endTime})"
                                )
                            )
                        }
                    }
                }
            }

            curr = curr.plusDays(1)
        }

        return candidates.minByOrNull { it.triggerDateTime }
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

        val epochMillis = next.triggerDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

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
            Log.e(TAG, "Errore permessi allarme esatto: ${e.message}")
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
}
