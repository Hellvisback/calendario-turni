package com.turni.calendario.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.model.HolidayItem
import com.turni.calendario.model.HolidaysCatalog
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolidaysSettingsScreen(
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val enabledIds = ShiftRepository.enabledHolidayIds

    val nationalHolidays = remember {
        HolidaysCatalog.allHolidays.filter { !it.isPatronSaint && !it.isCcnlExHoliday }
    }
    val patronHolidays = remember {
        HolidaysCatalog.allHolidays.filter { it.isPatronSaint && !it.isCcnlExHoliday }
    }
    val ccnlHolidays = remember {
        HolidaysCatalog.allHolidays.filter { it.isCcnlExHoliday }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top App Bar Ergonomica Breccia (Unico tasto indietro in alto)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onDismiss,
                    shape = CircleShape,
                    color = ObsidianSurfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Indietro",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Festività & CCNL",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Configura giorni festivi e patroni",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            // Pillola contatore festività attive
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFEF4444).copy(alpha = 0.18f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f))
            ) {
                Text(
                    text = "${enabledIds.size} Attive",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFF87171),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Scheda Azioni Rapide (Reset ai default nazionali)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = ObsidianSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Festività Nazionali Standard",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Ripristina le festività della legge italiana",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Button(
                    onClick = { ShiftRepository.resetHolidaysToDefault() },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceContainer),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, ObsidianBorderSubtle),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ripristina", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Lista Raggruppata Scrollabile
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HolidaySectionView(
                title = "FESTIVITÀ NAZIONALI & CIVILI",
                holidays = nationalHolidays,
                enabledIds = enabledIds,
                onToggle = { ShiftRepository.toggleHoliday(it) }
            )

            HolidaySectionView(
                title = "SANTI PATRONI",
                holidays = patronHolidays,
                enabledIds = enabledIds,
                onToggle = { ShiftRepository.toggleHoliday(it) }
            )

            HolidaySectionView(
                title = "EX-FESTIVITÀ CCNL E GIORNI SPECIALI",
                holidays = ccnlHolidays,
                enabledIds = enabledIds,
                onToggle = { ShiftRepository.toggleHoliday(it) }
            )

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun HolidaySectionView(
    title: String,
    holidays: List<HolidayItem>,
    enabledIds: Set<String>,
    onToggle: (String) -> Unit
) {
    Column {
        Text(
            text = title,
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = ObsidianSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
        ) {
            Column {
                holidays.forEachIndexed { index, item ->
                    val isChecked = enabledIds.contains(item.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bouncyClickable { onToggle(item.id) }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            // Badge data compatta
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isChecked) Color(0xFFEF4444).copy(alpha = 0.22f) else ObsidianSurfaceContainer,
                                border = androidx.compose.foundation.BorderStroke(
                                    0.8.dp,
                                    if (isChecked) Color(0xFFEF4444).copy(alpha = 0.6f) else ObsidianBorderSubtle
                                ),
                                modifier = Modifier.size(width = 44.dp, height = 34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${item.day}/${item.month}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isChecked) Color(0xFFFCA5A5) else TextSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = item.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = if (isChecked) TextPrimary else TextSecondary
                                )
                                Text(
                                    text = item.dateExample,
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        Switch(
                            checked = isChecked,
                            onCheckedChange = { onToggle(item.id) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFEF4444),
                                uncheckedTrackColor = ObsidianSurfaceContainer,
                                uncheckedThumbColor = TextMuted
                            )
                        )
                    }

                    if (index < holidays.size - 1) {
                        Divider(color = ObsidianBorderSubtle, thickness = 0.6.dp, modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
        }
    }
}
