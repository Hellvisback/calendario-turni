package com.turni.calendario.model

import java.time.LocalDate
import java.util.Locale

data class DayTag(
    val id: String,
    val name: String,
    val iconKey: String = "tag",
    val colorHex: Long = 0xFF94C4EF,
    val archived: Boolean = false
)

val dayTagIcons = listOf("tag", "graduation-cap", "heart-pulse", "book-open", "smile", "flag")
fun normalizedTagName(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

fun validateDayTags(tags: List<DayTag>, extras: Map<LocalDate, DayExtraInfo>) {
    val ids = tags.map { it.id }.toSet()
    require(tags.size <= 500 && ids.size == tags.size && ids.none { it.isBlank() }) { "Etichette duplicate o non valide" }
    require(tags.all { it.name.isNotBlank() && it.name.length <= 40 && it.iconKey in dayTagIcons && it.colorHex in 0L..0xFFFFFFFFL }) { "Nome o icona etichetta non validi" }
    require(tags.map { normalizedTagName(it.name) }.distinct().size == tags.size) { "Esiste già un’etichetta con questo nome" }
    require(extras.values.all { extra -> extra.tagIds.distinct().size == extra.tagIds.size && extra.tagIds.all { it in ids } }) { "Etichetta del giorno non trovata" }
}

data class DayTagMetric(val tag: DayTag, val dates: List<LocalDate>)

/** Each label counts unique dates, independent of the number or names of shifts. */
fun dayTagMetrics(tags: List<DayTag>, extras: Map<LocalDate, DayExtraInfo>, start: LocalDate, end: LocalDate): List<DayTagMetric> {
    if (end.isBefore(start)) return emptyList()
    val datesByTag = mutableMapOf<String, MutableList<LocalDate>>()
    extras.forEach { (date, extra) ->
        if (date in start..end) extra.tagIds.distinct().forEach { id -> datesByTag.getOrPut(id) { mutableListOf() }.add(date) }
    }
    return tags.mapNotNull { tag -> datesByTag[tag.id]?.let { DayTagMetric(tag, it.sortedDescending()) } }
        .sortedWith(compareByDescending<DayTagMetric> { it.dates.size }.thenBy { normalizedTagName(it.tag.name) })
}
