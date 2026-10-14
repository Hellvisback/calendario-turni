package com.turni.calendario.sync

import android.accounts.Account
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.turni.calendario.model.AssignedShift
import com.turni.calendario.repository.ShiftRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

data class DeviceCalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val ownerAccount: String,
    val isPrimary: Boolean = false
)

object GoogleCalendarSyncHelper {

    private const val APP_SYNC_TAG = "[CalendarioTurni]"

    fun hasCalendarPermissions(context: Context): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        return readGranted && writeGranted
    }

    /**
     * Interroga il ContentProvider di CalendarContract per elencare tutti i calendari del dispositivo,
     * dando precedenza agli account Google (@gmail.com / com.google).
     */
    fun getDeviceCalendars(context: Context): List<DeviceCalendarInfo> {
        if (!hasCalendarPermissions(context)) return emptyList()

        val list = mutableListOf<DeviceCalendarInfo>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.IS_PRIMARY
        )

        val uri: Uri = CalendarContract.Calendars.CONTENT_URI
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
            )

            cursor?.let {
                val idCol = it.getColumnIndex(CalendarContract.Calendars._ID)
                val nameCol = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountCol = it.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                val typeCol = it.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                val ownerCol = it.getColumnIndex(CalendarContract.Calendars.OWNER_ACCOUNT)
                val primaryCol = it.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)

                if (idCol >= 0) {
                    while (it.moveToNext()) {
                        val id = it.getLong(idCol)
                        val name = if (nameCol >= 0) it.getString(nameCol) ?: "Calendario" else "Calendario"
                        val account = if (accountCol >= 0) it.getString(accountCol) ?: "" else ""
                        val type = if (typeCol >= 0) it.getString(typeCol) ?: "" else ""
                        val owner = if (ownerCol >= 0) it.getString(ownerCol) ?: "" else ""
                        val isPrimary = if (primaryCol >= 0) it.getInt(primaryCol) == 1 else false

                        list.add(
                            DeviceCalendarInfo(
                                id = id,
                                displayName = name,
                                accountName = account,
                                accountType = type,
                                ownerAccount = owner,
                                isPrimary = isPrimary
                            )
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            try { cursor?.close() } catch (_: Throwable) {}
        }
        return list
    }

    /**
     * Sincronizza realmente i turni nel calendario selezionato su Android / Google Calendar.
     * È idempotente: rimuove preventivamente vecchi eventi [CalendarioTurni] nello stesso intervallo
     * per evitare duplicazioni se l'utente sincronizza più volte.
     */
    fun syncShiftsToCalendar(
        context: Context,
        targetCalendarId: Long?,
        shiftsToSync: List<Pair<LocalDate, AssignedShift>>,
        syncExactHours: Boolean = true,
        reminderMinutesBefore: Int? = 60,
        includeRestDays: Boolean = false
    ): Int {
        if (!hasCalendarPermissions(context)) return 0
        val cals = getDeviceCalendars(context)
        val cal = cals.find { it.id == targetCalendarId } ?: cals.firstOrNull() ?: return 0
        val calId = cal.id

        val contentResolver = context.contentResolver
        val timeZone = TimeZone.getDefault().id
        val zoneId = ZoneId.systemDefault()

        // 1. Pulizia preventiva degli eventi precedentemente sincronizzati per questo intervallo di date
        // per prevenire la moltiplicazione dei turni
        if (shiftsToSync.isNotEmpty()) {
            val minDate = shiftsToSync.minOf { it.first }
            val maxDate = shiftsToSync.maxOf { it.first }
            val startRangeMillis = minDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val endRangeMillis = maxDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

            removeAppEventsInRange(context, cal, startRangeMillis, endRangeMillis)
        }

        var insertedCount = 0

        for ((date, assigned) in shiftsToSync) {
            val def = ShiftRepository.getShiftDefinition(assigned.shiftDefId) ?: continue

            // Esclude riposi e smonti a meno che non siano esplicitamente richiesti
            val isRest = def.category.name == "RIPOSO" || def.shortCode.uppercase() in listOf("RIP", "SMO", "R", "S")
            if (isRest && !includeRestDays) {
                continue
            }

            try {
                val title = "${def.name} ${if (assigned.isOvertime) "(+Str)" else ""}"
                val description = buildString {
                    append(APP_SYNC_TAG).append(" ")
                    append("Turno: ").append(def.name).append(" (").append(def.shortCode).append(")\n")
                    if (def.isAllDay) {
                        append("Orario: Tutto il giorno\n")
                    } else {
                        append("Orario: ").append(def.startTime).append(" - ").append(def.endTime).append("\n")
                    }
                    if (assigned.notes.isNotBlank()) {
                        append("Note: ").append(assigned.notes).append("\n")
                    }
                    if (assigned.isTrasferta) {
                        append("Trasferta: Sì\n")
                    }
                    if (assigned.overtimeHours > 0f) {
                        append("Straordinario: ").append(assigned.overtimeHours).append(" ore\n")
                    }
                }

                val startMillis: Long
                val endMillis: Long
                val isAllDay: Boolean

                if (def.isAllDay || !syncExactHours) {
                    isAllDay = true
                    startMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    endMillis = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                } else {
                    isAllDay = false
                    val startLt = try { LocalTime.parse(def.startTime) } catch (_: Exception) { LocalTime.of(6, 0) }
                    val endLt = try { LocalTime.parse(def.endTime) } catch (_: Exception) { LocalTime.of(14, 0) }

                    val startZdt = ZonedDateTime.of(date, startLt, zoneId)
                    val endZdt = if (endLt.isBefore(startLt)) {
                        // Turno notturno a cavallo del giorno successivo
                        ZonedDateTime.of(date.plusDays(1), endLt, zoneId)
                    } else {
                        ZonedDateTime.of(date, endLt, zoneId)
                    }

                    startMillis = startZdt.toInstant().toEpochMilli()
                    endMillis = endZdt.toInstant().toEpochMilli()
                }

                val values = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, calId)
                    put(CalendarContract.Events.TITLE, title)
                    put(CalendarContract.Events.DESCRIPTION, description)
                    put(CalendarContract.Events.DTSTART, startMillis)
                    put(CalendarContract.Events.DTEND, endMillis)
                    put(CalendarContract.Events.ALL_DAY, if (isAllDay) 1 else 0)
                    put(CalendarContract.Events.EVENT_TIMEZONE, timeZone)
                    put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                    put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
                    put(CalendarContract.Events.HAS_ALARM, if (reminderMinutesBefore != null) 1 else 0)
                }

                val eventUri = try {
                    contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                } catch (t: Throwable) {
                    t.printStackTrace()
                    null
                }

                if (eventUri != null) {
                    insertedCount++
                    // Inserimento promemoria (protetto da fallimenti)
                    if (reminderMinutesBefore != null && reminderMinutesBefore > 0) {
                        try {
                            val eventId = ContentUris.parseId(eventUri)
                            val reminderValues = ContentValues().apply {
                                put(CalendarContract.Reminders.EVENT_ID, eventId)
                                put(CalendarContract.Reminders.MINUTES, reminderMinutesBefore)
                                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                            }
                            contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
                        } catch (t: Throwable) {
                            // Ignora se il provider locale non supporta o blocca i promemoria
                        }
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        // Forza sincronizzazione e notifica
        forceGoogleCalendarSync(context, cal)

        return insertedCount
    }

    /**
     * Rimuove gli eventi [CalendarioTurni] in un intervallo temporale specifico per evitare duplicati.
     */
    private fun removeAppEventsInRange(
        context: Context,
        cal: DeviceCalendarInfo,
        startMillis: Long,
        endMillis: Long
    ) {
        val contentResolver = context.contentResolver
        val where = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DESCRIPTION} LIKE ? AND ${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val args = arrayOf(cal.id.toString(), "%$APP_SYNC_TAG%", startMillis.toString(), endMillis.toString())

        val eventIds = mutableListOf<Long>()
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID),
                where,
                args,
                null
            )
            cursor?.let {
                val idCol = it.getColumnIndex(CalendarContract.Events._ID)
                if (idCol >= 0) {
                    while (it.moveToNext()) {
                        eventIds.add(it.getLong(idCol))
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            try { cursor?.close() } catch (_: Throwable) {}
        }

        for (id in eventIds) {
            try {
                val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
                contentResolver.delete(deleteUri, null, null)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Rimuove dal calendario tutti gli eventi creati dall'app (identificati dal tag di sync).
     */
    fun clearAppEventsFromCalendar(context: Context, targetCalendarId: Long?): Int {
        if (!hasCalendarPermissions(context)) return 0
        val cals = getDeviceCalendars(context)
        val cal = cals.find { it.id == targetCalendarId } ?: cals.firstOrNull() ?: return 0

        val contentResolver = context.contentResolver
        val where = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DESCRIPTION} LIKE ?"
        val args = arrayOf(cal.id.toString(), "%$APP_SYNC_TAG%")

        val eventIds = mutableListOf<Long>()
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID),
                where,
                args,
                null
            )
            cursor?.let {
                val idCol = it.getColumnIndex(CalendarContract.Events._ID)
                if (idCol >= 0) {
                    while (it.moveToNext()) {
                        eventIds.add(it.getLong(idCol))
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            try { cursor?.close() } catch (_: Throwable) {}
        }

        var deletedCount = 0
        for (id in eventIds) {
            try {
                val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
                val rows = contentResolver.delete(deleteUri, null, null)
                if (rows > 0) {
                    deletedCount += rows
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        // Notifica al ContentResolver che i calendari sono cambiati
        forceGoogleCalendarSync(context, cal)

        return deletedCount
    }

    /**
     * Notifica il ContentResolver per richiedere il refresh dei dati calendario.
     */
    fun forceGoogleCalendarSync(context: Context, cal: DeviceCalendarInfo?) {
        try {
            context.contentResolver.notifyChange(CalendarContract.Events.CONTENT_URI, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
