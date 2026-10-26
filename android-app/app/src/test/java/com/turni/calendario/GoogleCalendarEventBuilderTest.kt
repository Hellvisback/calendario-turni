package com.turni.calendario

import com.turni.calendario.model.AssignedShift
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.sync.GoogleCalendarEventBuilder as Builder
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

class GoogleCalendarEventBuilderTest {
    private val date = LocalDate.of(2026, 10, 24)
    private val zone = ZoneId.of("Europe/Rome")
    private val definition = ShiftDefinition("night", "Notte", "NOT", "22:00", "06:00")
    private val assignment = AssignedShift("assigned-night", date, "night")

    @Test fun overnightPreservesDatesAndDstOffset() {
        val event = Builder.build(date, assignment, definition, zone = zone)
        val start = OffsetDateTime.parse(event.getJSONObject("start").getString("dateTime"))
        val end = OffsetDateTime.parse(event.getJSONObject("end").getString("dateTime"))
        assertEquals("2026-10-24T22:00+02:00", start.toString())
        assertEquals("2026-10-25T06:00+01:00", end.toString())
        assertEquals(9L, Duration.between(start.toInstant(), end.toInstant()).toHours())
    }

    @Test fun actualEndOverridesOvertimeAndNeverDoublesIt() {
        val event = Builder.build(date, assignment.copy(actualEndTime = "06:30", overtimeHours = .5f), definition, zone = zone)
        assertEquals("2026-10-25T06:30:00+01:00", event.getJSONObject("end").getString("dateTime"))
    }

    @Test fun customTimesAndOvertimeAreUsedInsteadOfDefinitionDefaults() {
        val event = Builder.build(date, assignment.copy(customStartTime = "14:00", customEndTime = "21:36", overtimeHours = .5f), definition, zone = zone)
        assertEquals("2026-10-24T14:00:00+02:00", event.getJSONObject("start").getString("dateTime"))
        assertEquals("2026-10-24T22:06:00+02:00", event.getJSONObject("end").getString("dateTime"))
    }

    @Test fun overtimeMayCrossMidnight() {
        val event = Builder.build(date, assignment.copy(actualEndTime = "00:15", customStartTime = "16:00", customEndTime = "23:30", overtimeHours = .75f), definition, zone = zone)
        assertEquals("2026-10-25T00:15:00+02:00", event.getJSONObject("end").getString("dateTime"))
    }

    @Test fun allDayUsesDateAndExclusiveEndWithoutTimeZone() {
        val event = Builder.build(date, assignment, definition.copy(isAllDay = true), zone = zone)
        assertEquals("2026-10-24", event.getJSONObject("start").getString("date"))
        assertEquals("2026-10-25", event.getJSONObject("end").getString("date"))
        assertFalse(event.getJSONObject("start").has("dateTime"))
        assertFalse(event.getJSONObject("start").has("timeZone"))
    }

    @Test fun stableIdIsGoogleBase32HexAndDoesNotDependOnModelEdits() {
        val id = Builder.stableId(date, assignment.id)
        assertTrue(id.matches(Regex("[a-v0-9]{50}")))
        assertEquals(id, Builder.build(date, assignment, definition.copy(name = "Nome nuovo"), zone = zone).getString("id"))
        assertNotEquals(id, Builder.stableId(date.plusDays(1), assignment.id))
        assertNotEquals(id, Builder.stableId(date, "another-assignment"))
    }

    @Test fun ownershipAndOriginAreExplicitAndPersonalEventsCannotMatch() {
        val event = Builder.build(date, assignment, definition, zone = zone)
        assertTrue(Builder.isOwned(event))
        assertEquals(date, Builder.localDate(event))
        val properties = event.getJSONObject("extendedProperties").getJSONObject("private")
        assertEquals(assignment.id, properties.getString("assignmentId"))
        assertEquals("principale", properties.getString("workProfileId"))
        assertFalse(Builder.isOwned(JSONObject().put("summary", "TurniSync Notte")))
        assertFalse(Builder.isOwned(JSONObject().put("description", "[CalendarioTurni]")))
    }

    @Test fun disabledReminderNeverInheritsCalendarDefault() {
        val event = Builder.build(date, assignment, definition, reminderMinutes = null, zone = zone)
        assertFalse(event.getJSONObject("reminders").getBoolean("useDefault"))
        assertEquals(0, event.getJSONObject("reminders").getJSONArray("overrides").length())
        val enabled = Builder.build(date, assignment, definition, reminderMinutes = 15, zone = zone)
        assertEquals(15, enabled.getJSONObject("reminders").getJSONArray("overrides").getJSONObject(0).getInt("minutes"))
    }

    @Test(expected = java.time.format.DateTimeParseException::class)
    fun invalidShiftTimeIsRejectedInsteadOfExportingFakeTime() {
        Builder.build(date, assignment.copy(customStartTime = "invalid"), definition, zone = zone)
    }
}
