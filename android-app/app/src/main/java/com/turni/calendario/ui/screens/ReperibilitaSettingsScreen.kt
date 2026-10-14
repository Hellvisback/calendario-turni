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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.model.ReperibilitaMode
import com.turni.calendario.model.SeasonalReperibilitaConfig
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.AnimatedStatusPopup
import com.turni.calendario.ui.components.PopupType
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.theme.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReperibilitaSettingsScreen(
    onDismiss: () -> Unit
) {
    var config by remember { mutableStateOf(ShiftRepository.reperibilitaConfig) }
    var saveSuccessMessage by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onDismiss,
                shape = CircleShape,
                color = ObsidianSurfaceContainer,
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
                    text = "Gestione Reperibilità",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    text = "Configura la modalità di reperibilità software",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))



        // Selettore Modalità Reperibilità
        Text(
            text = "MODALITÀ REPERIBILITÀ",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        ReperibilitaMode.values().forEach { mode ->
            val isSelected = config.mode == mode
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .bouncyClickable {
                        config = config.copy(mode = mode)
                        ShiftRepository.reperibilitaConfig = config
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) ObsidianSurfaceHighest else ObsidianSurface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) Color(0xFFF59E0B) else ObsidianBorder
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = {
                            config = config.copy(mode = mode)
                            ShiftRepository.reperibilitaConfig = config
                        },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Color(0xFFF59E0B),
                            unselectedColor = TextMuted
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mode.label,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(0xFFFBBF24) else TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = mode.description,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dettaglio Configurazione in base alla modalità selezionata
        when (config.mode) {
            ReperibilitaMode.MANUALE -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color(0xFFF59E0B))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reperibilità Singola / Manuale", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Puoi assegnare o rimuovere la reperibilità in qualsiasi momento toccando il giorno nel calendario e premendo sul chip 'Reperibilità'.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ObsidianSurfaceContainer
                        ) {
                            Text(
                                text = "Date contrassegnate manualmente: ${config.manualDates.size}",
                                color = ObsidianPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            ReperibilitaMode.TURNO_SPECIFICO -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFFF59E0B))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Seleziona i Turni di Reperibilità", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Tutti i giorni in cui lavori uno dei turni selezionati sotto avranno automaticamente la reperibilità attiva nel calendario.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val availableShifts = ShiftRepository.shiftDefinitions.toList()
                        if (availableShifts.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ObsidianSurfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Nessun turno configurato. Crea prima i tuoi turni personalizzati nella sezione Turni.", color = TextMuted, fontSize = 12.sp)
                                }
                            }
                        } else {
                            // SimpleFlowRow elastico che racchiude TUTTI i turni dell'utente senza tagli
                            SimpleFlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalSpacing = 8.dp,
                                verticalSpacing = 8.dp
                            ) {
                                availableShifts.forEach { shiftDef ->
                                    val isChosen = shiftDef.id in config.shiftSpecificIds
                                    Surface(
                                        onClick = {
                                            val newIds = if (isChosen) config.shiftSpecificIds - shiftDef.id else config.shiftSpecificIds + shiftDef.id
                                            config = config.copy(shiftSpecificIds = newIds)
                                            ShiftRepository.reperibilitaConfig = config
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isChosen) shiftDef.color.copy(alpha = 0.25f) else ObsidianSurfaceHigh,
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = 1.dp,
                                            color = if (isChosen) shiftDef.color else ObsidianBorderSubtle
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(shiftDef.color)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "${shiftDef.shortCode} • ${shiftDef.name}",
                                                fontSize = 12.sp,
                                                fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isChosen) Color.White else TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ReperibilitaMode.ALTERNATA_STAGIONALE -> {
                val seasonal = config.seasonal
                val monthNames = mapOf(
                    1 to "Gen", 2 to "Feb", 3 to "Mar", 4 to "Apr",
                    5 to "Mag", 6 to "Giu", 7 to "Lug", 8 to "Ago",
                    9 to "Set", 10 to "Ott", 11 to "Nov", 12 to "Dic"
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Card Periodo Invernale
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AcUnit, contentDescription = null, tint = ObsidianPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Inverno: Reperibilità su Turni (Data A ➜ Data B)", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Nel periodo invernale (ripetuto in loop ogni anno), la reperibilità è attiva nei giorni con i turni selezionati sotto.",
                                color = TextSecondary,
                                fontSize = 11.5.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Selettore Data A (Inizio) e Data B (Fine)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Data A Inizio
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = ObsidianSurfaceContainer,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("DATA A (INIZIO)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ObsidianPrimary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "${seasonal.winterStartDay} ${monthNames[seasonal.winterStartMonth] ?: "Ott"}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = ObsidianSurfaceHigh,
                                                modifier = Modifier.size(26.dp).bouncyClickable {
                                                    val newM = if (seasonal.winterStartMonth == 1) 12 else seasonal.winterStartMonth - 1
                                                    config = config.copy(seasonal = seasonal.copy(winterStartMonth = newM))
                                                    ShiftRepository.reperibilitaConfig = config
                                                }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) { Text("M-", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary) }
                                            }
                                            Surface(
                                                shape = CircleShape,
                                                color = ObsidianSurfaceHigh,
                                                modifier = Modifier.size(26.dp).bouncyClickable {
                                                    val newM = if (seasonal.winterStartMonth == 12) 1 else seasonal.winterStartMonth + 1
                                                    config = config.copy(seasonal = seasonal.copy(winterStartMonth = newM))
                                                    ShiftRepository.reperibilitaConfig = config
                                                }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) { Text("M+", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary) }
                                            }
                                        }
                                    }
                                }

                                // Data B Fine
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = ObsidianSurfaceContainer,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("DATA B (FINE)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ObsidianPrimary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "${seasonal.winterEndDay} ${monthNames[seasonal.winterEndMonth] ?: "Mag"}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = ObsidianSurfaceHigh,
                                                modifier = Modifier.size(26.dp).bouncyClickable {
                                                    val newM = if (seasonal.winterEndMonth == 1) 12 else seasonal.winterEndMonth - 1
                                                    config = config.copy(seasonal = seasonal.copy(winterEndMonth = newM))
                                                    ShiftRepository.reperibilitaConfig = config
                                                }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) { Text("M-", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary) }
                                            }
                                            Surface(
                                                shape = CircleShape,
                                                color = ObsidianSurfaceHigh,
                                                modifier = Modifier.size(26.dp).bouncyClickable {
                                                    val newM = if (seasonal.winterEndMonth == 12) 1 else seasonal.winterEndMonth + 1
                                                    config = config.copy(seasonal = seasonal.copy(winterEndMonth = newM))
                                                    ShiftRepository.reperibilitaConfig = config
                                                }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) { Text("M+", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary) }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Turni in reperibilità d'inverno:", fontSize = 11.5.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))

                            // Chip turni per inverno con SimpleFlowRow elastico
                            SimpleFlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalSpacing = 6.dp,
                                verticalSpacing = 6.dp
                            ) {
                                val availableShifts = ShiftRepository.shiftDefinitions.toList()
                                availableShifts.forEach { sDef ->
                                    val isWinterActive = sDef.id in seasonal.winterShiftIds
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isWinterActive) sDef.color.copy(alpha = 0.25f) else ObsidianSurfaceHigh,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isWinterActive) sDef.color else ObsidianBorderSubtle),
                                        modifier = Modifier.bouncyClickable {
                                            val newShiftIds = if (isWinterActive) seasonal.winterShiftIds - sDef.id else seasonal.winterShiftIds + sDef.id
                                            config = config.copy(seasonal = seasonal.copy(winterShiftIds = newShiftIds))
                                            ShiftRepository.reperibilitaConfig = config
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(sDef.color)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = "${sDef.shortCode} • ${sDef.name}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isWinterActive) Color.White else TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Card Periodo Estivo a Settimane
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WbSunny, contentDescription = null, tint = Color(0xFFF59E0B))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Estate: Rotazione a Settimane (Data B ➜ Data A)", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Dalla data B alla data A, la reperibilità ruota a blocchi settimanali a partire dal giorno di inizio configurato.",
                                color = TextSecondary,
                                fontSize = 11.5.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Data inizio ciclo estivo (Ancora)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = ObsidianSurfaceContainer,
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("INIZIO 1ª SETTIMANA REPERIBILITÀ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                                        val refDate = seasonal.summerCycleReferenceDate
                                        Text(
                                            "${refDate.dayOfMonth} ${monthNames[refDate.monthValue] ?: ""} ${refDate.year}",
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = TextPrimary
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Surface(
                                            shape = CircleShape,
                                            color = ObsidianSurfaceHigh,
                                            modifier = Modifier.size(28.dp).bouncyClickable {
                                                val prev = seasonal.summerCycleReferenceDate.minusWeeks(1)
                                                config = config.copy(seasonal = seasonal.copy(summerCycleReferenceDate = prev))
                                                ShiftRepository.reperibilitaConfig = config
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) { Text("-7g", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary) }
                                        }
                                        Surface(
                                            shape = CircleShape,
                                            color = ObsidianSurfaceHigh,
                                            modifier = Modifier.size(28.dp).bouncyClickable {
                                                val next = seasonal.summerCycleReferenceDate.plusWeeks(1)
                                                config = config.copy(seasonal = seasonal.copy(summerCycleReferenceDate = next))
                                                ShiftRepository.reperibilitaConfig = config
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) { Text("+7g", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary) }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Controlli Settimane di lavoro e reperibilità
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Settimane Turno Ordinario", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("Senza reperibilità", fontSize = 10.5.sp, color = TextMuted)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        shape = CircleShape,
                                        color = ObsidianSurfaceHigh,
                                        modifier = Modifier.size(28.dp).bouncyClickable {
                                            val next = (seasonal.summerCycleWorkWeeks - 1).coerceAtLeast(1)
                                            config = config.copy(seasonal = seasonal.copy(summerCycleWorkWeeks = next))
                                            ShiftRepository.reperibilitaConfig = config
                                        }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) { Text("-", color = TextPrimary, fontWeight = FontWeight.Bold) }
                                    }
                                    Text("${seasonal.summerCycleWorkWeeks} sett.", color = ObsidianPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Surface(
                                        shape = CircleShape,
                                        color = ObsidianSurfaceHigh,
                                        modifier = Modifier.size(28.dp).bouncyClickable {
                                            val next = seasonal.summerCycleWorkWeeks + 1
                                            config = config.copy(seasonal = seasonal.copy(summerCycleWorkWeeks = next))
                                            ShiftRepository.reperibilitaConfig = config
                                        }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) { Text("+", color = TextPrimary, fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Settimane Reperibilità", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("Reperibile tutta la settimana", fontSize = 10.5.sp, color = TextMuted)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        shape = CircleShape,
                                        color = ObsidianSurfaceHigh,
                                        modifier = Modifier.size(28.dp).bouncyClickable {
                                            val next = (seasonal.summerCycleOnCallWeeks - 1).coerceAtLeast(1)
                                            config = config.copy(seasonal = seasonal.copy(summerCycleOnCallWeeks = next))
                                            ShiftRepository.reperibilitaConfig = config
                                        }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) { Text("-", color = TextPrimary, fontWeight = FontWeight.Bold) }
                                    }
                                    Text("${seasonal.summerCycleOnCallWeeks} sett.", color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Surface(
                                        shape = CircleShape,
                                        color = ObsidianSurfaceHigh,
                                        modifier = Modifier.size(28.dp).bouncyClickable {
                                            val next = seasonal.summerCycleOnCallWeeks + 1
                                            config = config.copy(seasonal = seasonal.copy(summerCycleOnCallWeeks = next))
                                            ShiftRepository.reperibilitaConfig = config
                                        }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) { Text("+", color = TextPrimary, fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tasto Salva Configurazione
        Button(
            onClick = {
                ShiftRepository.reperibilitaConfig = config
                saveSuccessMessage = "Configurazione Reperibilità salvata con successo!"
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Salva Regole Reperibilità", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    AnimatedStatusPopup(
        message = saveSuccessMessage,
        type = PopupType.SUCCESS,
        onDismiss = { saveSuccessMessage = null }
    )
    }
}

@Composable
fun SimpleFlowRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    Layout(
        modifier = modifier,
        content = content
    ) { measurables, constraints ->
        val hSpacingPx = horizontalSpacing.roundToPx()
        val vSpacingPx = verticalSpacing.roundToPx()

        val rows = mutableListOf<List<Placeable>>()
        val rowHeights = mutableListOf<Int>()
        var currentRow = mutableListOf<Placeable>()
        var currentLineWidth = 0
        var currentLineHeight = 0

        for (measurable in measurables) {
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            if (currentLineWidth + placeable.width + (if (currentRow.isNotEmpty()) hSpacingPx else 0) > constraints.maxWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                rowHeights.add(currentLineHeight)
                currentRow = mutableListOf(placeable)
                currentLineWidth = placeable.width
                currentLineHeight = placeable.height
            } else {
                if (currentRow.isNotEmpty()) currentLineWidth += hSpacingPx
                currentRow.add(placeable)
                currentLineWidth += placeable.width
                currentLineHeight = maxOf(currentLineHeight, placeable.height)
            }
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
            rowHeights.add(currentLineHeight)
        }

        val totalHeight = rowHeights.sum() + (if (rows.size > 1) (rows.size - 1) * vSpacingPx else 0)

        layout(constraints.maxWidth, totalHeight.coerceAtLeast(0)) {
            var currentY = 0
            for (i in rows.indices) {
                val row = rows[i]
                var currentX = 0
                for (placeable in row) {
                    placeable.placeRelative(currentX, currentY)
                    currentX += placeable.width + hSpacingPx
                }
                currentY += rowHeights[i] + vSpacingPx
            }
        }
    }
}

