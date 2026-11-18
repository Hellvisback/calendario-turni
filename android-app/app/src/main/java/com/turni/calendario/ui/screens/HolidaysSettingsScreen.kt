package com.turni.calendario.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.turni.calendario.model.HolidayItem
import com.turni.calendario.model.HolidaysCatalog
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val fullDate = DateTimeFormatter.ofPattern("EEEE d MMMM uuuu", Locale.ITALIAN)

@Composable
fun HolidaysSettingsScreen(onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val year = LocalDate.now().year
    val enabledIds = ShiftRepository.enabledHolidayIds
    val sections = remember(year) {
        val catalog = HolidaysCatalog.allHolidays.sortedBy { it.getDateForYear(year) }
        listOf(
            "Festività nazionali e civili" to catalog.filter { !it.isCcnlExHoliday && !it.isPatronSaint },
            "Santi patroni" to catalog.filter { !it.isCcnlExHoliday && it.isPatronSaint },
            "Ex festività CCNL e giorni speciali" to catalog.filter { it.isCcnlExHoliday }
        )
    }
    var sectionIndex by rememberSaveable { mutableIntStateOf(0) }
    val activeCount = HolidaysCatalog.allHolidays.count { it.id in enabledIds }
    val defaultIds = remember { HolidaysCatalog.allHolidays.filter { it.isDefaultEnabled }.map { it.id }.toSet() }
    val scheme = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().background(ObsidianBackground).statusBarsPadding().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(onClick = onDismiss, shape = CircleShape, color = ObsidianSurfaceContainer, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.ArrowBack, "Indietro", tint = TextPrimary)
                }
            }
            Text("Festività", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(shape = RoundedCornerShape(20.dp), color = scheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Calendario $year", style = MaterialTheme.typography.labelLarge, color = scheme.onPrimaryContainer)
                    Text(
                        if (activeCount == 1) "1 festività selezionata" else "$activeCount festività selezionate", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = scheme.onPrimaryContainer
                    )
                    Text("Scegli le ricorrenze da evidenziare sul calendario.", style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer)
                    TextButton(
                        onClick = ShiftRepository::resetHolidaysToDefault,
                        enabled = enabledIds != defaultIds,
                        colors = ButtonDefaults.textButtonColors(contentColor = scheme.onPrimaryContainer),
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ripristina predefinite")
                    }
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Nazionali", "Patroni", "CCNL").forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = sectionIndex == index, onClick = { sectionIndex = index },
                        shape = SegmentedButtonDefaults.itemShape(index, sections.size),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = scheme.primary, activeContentColor = scheme.onPrimary),
                        icon = {}
                    ) { Text(label) }
                }
            }
            val (title, holidays) = sections[sectionIndex]
            HolidaySection(title, holidays, enabledIds, year)
            Spacer(Modifier.height(12.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun HolidaySection(title: String, holidays: List<HolidayItem>, enabledIds: Set<String>, year: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title, Modifier.padding(start = 4.dp).semantics { heading() },
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary
        )
        Surface(shape = RoundedCornerShape(18.dp), color = ObsidianSurface, border = BorderStroke(1.dp, ObsidianBorder)) {
            Column {
                holidays.forEachIndexed { index, holiday ->
                    val selected = holiday.id in enabledIds
                    val date = remember(holiday, year) { holiday.getDateForYear(year) }
                    val scheme = MaterialTheme.colorScheme
                    val fill by animateColorAsState(
                        if (selected) scheme.primaryContainer else ObsidianSurfaceContainer, tween(180), label = "dateFill"
                    )
                    val ink by animateColorAsState(
                        if (selected) scheme.onPrimaryContainer else TextSecondary, tween(180), label = "dateInk"
                    )
                    Row(
                        Modifier.fillMaxWidth().toggleable(value = selected, role = Role.Switch) { ShiftRepository.toggleHoliday(holiday.id) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp), color = fill,
                            modifier = Modifier.widthIn(min = 48.dp).clearAndSetSemantics { contentDescription = date.format(fullDate) }
                        ) {
                            Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ink)
                                Text(date.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN), style = MaterialTheme.typography.labelSmall, color = ink)
                            }
                        }
                        Text(
                            holiday.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold, color = TextPrimary
                        )
                        Switch(checked = selected, onCheckedChange = null)
                    }
                    if (index < holidays.lastIndex) {
                        HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = ObsidianBorderSubtle)
                    }
                }
            }
        }
    }
}
