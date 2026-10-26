package com.turni.calendario.ui.screens

internal data class ReportRingBoundary(val startDistance: Float, val endDistance: Float)

/** The visible arc represents 100%; hidden continuations belong only to its end colors. */
internal fun reportRingBoundaries(
    counts: List<Int>, leadingLength: Float, visibleLength: Float, trailingLength: Float
): List<ReportRingBoundary> {
    require(counts.all { it > 0 })
    require(listOf(leadingLength, visibleLength, trailingLength).all { it.isFinite() && it >= 0f })
    if (counts.isEmpty()) return emptyList()
    val total = counts.sumOf { it.toLong() }.toDouble()
    val fullLength = leadingLength + visibleLength + trailingLength
    var accumulated = 0L
    return counts.mapIndexed { index, count ->
        val start = if (index == 0) 0f else leadingLength + visibleLength * (accumulated / total).toFloat()
        accumulated += count
        val end = if (index == counts.lastIndex) fullLength else leadingLength + visibleLength * (accumulated / total).toFloat()
        ReportRingBoundary(start, end)
    }
}
