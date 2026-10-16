package com.turni.calendario.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.components.prismaticRefractionBrush
import com.turni.calendario.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

enum class ReportPeriodMode {
    MESE,
    ANNO,
    PERSONALIZZATO
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var periodMode by remember { mutableStateOf(ReportPeriodMode.MESE) }
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedYear by remember { mutableStateOf(LocalDate.now().year) }
    var customStartDate by remember { mutableStateOf(LocalDate.now().minusDays(30)) }
    var customEndDate by remember { mutableStateOf(LocalDate.now()) }

    val startDate = remember(periodMode, selectedMonth, selectedYear, customStartDate) {
        when (periodMode) {
            ReportPeriodMode.MESE -> selectedMonth.atDay(1)
            ReportPeriodMode.ANNO -> LocalDate.of(selectedYear, 1, 1)
            ReportPeriodMode.PERSONALIZZATO -> customStartDate
        }
    }
    val endDate = remember(periodMode, selectedMonth, selectedYear, customEndDate) {
        when (periodMode) {
            ReportPeriodMode.MESE -> selectedMonth.atEndOfMonth()
            ReportPeriodMode.ANNO -> LocalDate.of(selectedYear, 12, 31)
            ReportPeriodMode.PERSONALIZZATO -> customEndDate
        }
    }

    // Statistiche Complete Report Avanzate (con ore reali, straordinari, trasferte e regola CCNL ferie su riposo/smonto)
    val reportStats = remember(
        startDate,
        endDate,
        ShiftRepository.calendarShifts.size,
        ShiftRepository.dayExtraInfo.size,
        ShiftRepository.reperibilitaConfig
    ) {
        ShiftRepository.getComprehensiveReportStats(startDate, endDate)
    }

    val shiftCounts = reportStats.shiftCounts
    val totalCount = shiftCounts.values.sum()
    val workedDays = reportStats.workedDays
    val totalHours = reportStats.totalHours
    val totalOvertimeHours = reportStats.totalOvertimeHours
    val countNotte = reportStats.countNotte
    val countRiposo = reportStats.countRiposo
    val countSmonto = reportStats.countSmonto
    val countFerie = reportStats.countFerie
    val countTrasferte = reportStats.countTrasferte
    val countReperibilita = reportStats.countReperibilita

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // 1. Header Superiore: Titolo + Pill Periodo (Mese vs Anno vs Personalizzato)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Report & Ore",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    text = "Analisi turni, straordinari e presenze",
                    fontSize = 11.5.sp,
                    color = TextSecondary
                )
            }

            // Segmented pill: [ Mese ] [ Anno ] [ Personalizzato ]
            Surface(
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.2.dp,
                    prismaticRefractionBrush(ObsidianPrimary)
                ),
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xDE182236), Color(0xF50D131F))
                            )
                        )
                        .padding(2.5.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (periodMode == ReportPeriodMode.MESE) ObsidianPrimary else Color.Transparent,
                            modifier = Modifier.bouncyClickable { periodMode = ReportPeriodMode.MESE }
                        ) {
                            Text(
                                text = "Mese",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (periodMode == ReportPeriodMode.MESE) ObsidianBackground else TextSecondary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.5.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (periodMode == ReportPeriodMode.ANNO) ObsidianPrimary else Color.Transparent,
                            modifier = Modifier.bouncyClickable { periodMode = ReportPeriodMode.ANNO }
                        ) {
                            Text(
                                text = "Anno",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (periodMode == ReportPeriodMode.ANNO) ObsidianBackground else TextSecondary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.5.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (periodMode == ReportPeriodMode.PERSONALIZZATO) ObsidianPrimary else Color.Transparent,
                            modifier = Modifier.bouncyClickable { periodMode = ReportPeriodMode.PERSONALIZZATO }
                        ) {
                            Text(
                                text = "Pers.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (periodMode == ReportPeriodMode.PERSONALIZZATO) ObsidianBackground else TextSecondary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.5.dp)
                            )
                        }
                    }
                }
            }
        }

        // Selettore Data / Intervallo
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.2.dp,
                prismaticRefractionBrush(ObsidianPrimary)
            ),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xD0172033), Color(0xEE0B0E14))
                        )
                    )
            ) {
                if (periodMode == ReportPeriodMode.PERSONALIZZATO) {
                    // Controlli per Intervallo Date Personalizzato (Da Data A Data)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Data Inizio
                        Surface(
                            onClick = {
                                val dp = android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val newStart = LocalDate.of(year, month + 1, dayOfMonth)
                                        customStartDate = newStart
                                        if (customEndDate.isBefore(newStart)) {
                                            customEndDate = newStart
                                        }
                                    },
                                    customStartDate.year,
                                    customStartDate.monthValue - 1,
                                    customStartDate.dayOfMonth
                                )
                                dp.show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = ObsidianSurfaceHigh,
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Text("DA:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                                Text(
                                    text = "${customStartDate.dayOfMonth} ${customStartDate.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).uppercase()} ${customStartDate.year}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianPrimary
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .size(16.dp)
                        )

                        // Data Fine
                        Surface(
                            onClick = {
                                val dp = android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val newEnd = LocalDate.of(year, month + 1, dayOfMonth)
                                        customEndDate = newEnd
                                        if (customStartDate.isAfter(newEnd)) {
                                            customStartDate = newEnd
                                        }
                                    },
                                    customEndDate.year,
                                    customEndDate.monthValue - 1,
                                    customEndDate.dayOfMonth
                                )
                                dp.show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = ObsidianSurfaceHigh,
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Text("A:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                                Text(
                                    text = "${customEndDate.dayOfMonth} ${customEndDate.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).uppercase()} ${customEndDate.year}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianPrimary
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (periodMode == ReportPeriodMode.ANNO) selectedYear--
                                else selectedMonth = selectedMonth.minusMonths(1)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Precedente", tint = TextPrimary)
                        }

                        Text(
                            text = if (periodMode == ReportPeriodMode.ANNO) "$selectedYear"
                            else "${selectedMonth.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.titlecase(Locale.ITALIAN) }} ${selectedMonth.year}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )

                        IconButton(
                            onClick = {
                                if (periodMode == ReportPeriodMode.ANNO) selectedYear++
                                else selectedMonth = selectedMonth.plusMonths(1)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Successivo", tint = TextPrimary)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Hero Card: Ore Totali Lavorate + Proportional Segmented Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(22.dp), spotColor = ObsidianPrimary.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(22.dp),
            color = ObsidianSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "MONTE ORE LAVORATE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ObsidianPrimary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = String.format("%.1f h", totalHours),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            if (totalOvertimeHours > 0f) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = ObsidianPrimary.copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(0.8.dp, ObsidianPrimary)
                                ) {
                                    Text(
                                        text = "+${String.format("%.1f", totalOvertimeHours)}h straord.",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ObsidianPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "$workedDays turni di servizio su $totalCount giornate registrate",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianPrimary.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianPrimary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.WorkOutline, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = ShiftRepository.activeWorkProfile.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ObsidianPrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Barra di Ripartizione Proporzionale Multi-Colore
                Text(
                    text = "Distribuzione Visiva del Tempo:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (totalCount > 0) {
                    val shiftSegments = remember(shiftCounts) {
                        ShiftRepository.shiftDefinitions.mapNotNull { def ->
                            val c = shiftCounts[def.id] ?: 0
                            if (c > 0) Pair(def, c) else null
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(ObsidianSurfaceHigh)
                    ) {
                        shiftSegments.forEach { (def, count) ->
                            Box(
                                modifier = Modifier
                                    .weight(count.toFloat())
                                    .fillMaxHeight()
                                    .background(def.color)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Legenda rapida dei colori dinamica
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        shiftSegments.take(5).forEach { (def, _) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(def.color)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(def.name, fontSize = 10.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Griglia 2x2 Quick Metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card Giornate di Lavoro
            MetricMiniCard(
                modifier = Modifier.weight(1f),
                title = "Servizio Attivo",
                value = "$workedDays gg",
                subtitle = "Turni Lavorati",
                icon = Icons.Default.WorkHistory,
                tint = ObsidianPrimary
            )

            // Card Notti con indennità
            MetricMiniCard(
                modifier = Modifier.weight(1f),
                title = "Turni Notturni",
                value = "$countNotte notti",
                subtitle = "+35% Maggiorazione",
                icon = Icons.Default.Bedtime,
                tint = ColorNotte
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card Riposi
            MetricMiniCard(
                modifier = Modifier.weight(1f),
                title = "Riposi Goduti",
                value = "$countRiposo gg",
                subtitle = "Recupero Fisico",
                icon = Icons.Default.Weekend,
                tint = ColorRiposo
            )

            // Card Ferie
            MetricMiniCard(
                modifier = Modifier.weight(1f),
                title = "Ferie Fruite",
                value = "$countFerie gg",
                subtitle = "Assenza Giustificata",
                icon = Icons.Default.BeachAccess,
                tint = ColorFerie
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card Smonti Notte
            MetricMiniCard(
                modifier = Modifier.weight(1f),
                title = "Smonti Notte",
                value = "$countSmonto smonti",
                subtitle = "Recupero Post-Notte",
                icon = Icons.Default.NightsStay,
                tint = Color(0xFF60A5FA)
            )

            // Card Indennità Speciali (Reperibilità e Trasferte)
            MetricMiniCard(
                modifier = Modifier.weight(1f),
                title = "Rep. & Trasferte",
                value = "$countReperibilita rep • $countTrasferte trasf",
                subtitle = "Indennità Speciali",
                icon = Icons.Default.PhoneCallback,
                tint = Color(0xFFF59E0B)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Sezione Dettaglio Conteggio per Turno
        Text(
            text = "DETTAGLIO ANALITICO DEI TURNI",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = ObsidianSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                val activeDefs = ShiftRepository.shiftDefinitions.filter {
                    (shiftCounts[it.id] ?: 0) > 0 || it.id in listOf("pomeriggio", "mattina", "notte", "mat_dom", "giornaliero", "uffici", "donazione")
                }

                activeDefs.forEachIndexed { index, def ->
                    val count = shiftCounts[def.id] ?: 0
                    val pct = if (totalCount > 0) (count.toFloat() / totalCount.toFloat()) else 0f

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Badge con compact code
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(def.color),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = getCompactShiftCode(def.id, def.shortCode),
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Nome, Orario e Mini Progress Bar
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = def.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "$count",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Barra percentuale
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(ObsidianSurfaceHigh)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(pct)
                                            .clip(CircleShape)
                                            .background(def.color)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = def.timeRangeFormatted,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    if (index < activeDefs.size - 1) {
                        Divider(color = ObsidianBorder, thickness = 0.6.dp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }

                Divider(color = ObsidianBorder, thickness = 1.dp, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp))

                // Totale Complessivo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Totale Giornate Registrate",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ObsidianPrimary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "$totalCount giornate",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ObsidianPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun MetricMiniCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = ObsidianSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tint.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
        }
    }
}

data class ReportPeriodStats(
    val workedDays: Int,
    val totalHours: Float,
    val countNotte: Int,
    val countRiposo: Int,
    val countFerie: Int
)
