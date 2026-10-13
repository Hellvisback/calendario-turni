package com.turni.calendario.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.model.ShiftCategory
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.model.ShiftRotation
import com.turni.calendario.model.WorkProfile
import com.turni.calendario.model.CcnlDefinition
import com.turni.calendario.model.CcnlCatalog
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.ActionFeedback
import com.turni.calendario.ui.components.MagicalActionFeedbackHUD
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.components.prismaticRefractionBrush
import com.turni.calendario.ui.theme.*
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun TurniModelsScreen(
    onEditorActiveChanged: ((Boolean) -> Unit)? = null
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Turni, 1: Lavori, 2: Rotazioni
    var editingShift by remember { mutableStateOf<ShiftDefinition?>(null) }
    var editingRotation by remember { mutableStateOf<ShiftRotation?>(null) }
    var showRotationApplyDialog by remember { mutableStateOf<ShiftRotation?>(null) }
    var viewingCcnl by remember { mutableStateOf<CcnlDefinition?>(null) }
    var editingWorkProfile by remember { mutableStateOf<Pair<WorkProfile, Boolean>?>(null) }
    var actionFeedback by remember { mutableStateOf<ActionFeedback?>(null) }

    LaunchedEffect(actionFeedback) {
        if (actionFeedback != null) {
            kotlinx.coroutines.delay(2600)
            actionFeedback = null
        }
    }

    val isEditing = editingShift != null || editingRotation != null || editingWorkProfile != null
    LaunchedEffect(isEditing) {
        onEditorActiveChanged?.invoke(isEditing)
    }

    // Gestione editor turno a schermo intero
    if (editingShift != null) {
        ShiftEditorScreen(
            shift = editingShift!!,
            onDismiss = {
                editingShift = null
                onEditorActiveChanged?.invoke(false)
            },
            onSave = { saved ->
                ShiftRepository.saveShiftDefinition(saved)
                editingShift = null
                onEditorActiveChanged?.invoke(false)
                actionFeedback = ActionFeedback("Turno '${saved.name}' salvato con successo!", isDestructive = false)
            },
            onDelete = { id ->
                val def = ShiftRepository.getShiftDefinition(id)
                val shiftName = def?.name ?: "Turno"
                ShiftRepository.deleteShiftDefinition(id)
                editingShift = null
                onEditorActiveChanged?.invoke(false)
                actionFeedback = ActionFeedback("Turno '$shiftName' eliminato", isDestructive = true)
            }
        )
        return
    }

    // Gestione editor rotazione a schermo intero
    if (editingRotation != null) {
        RotationMatrixEditorScreen(
            rotation = editingRotation!!,
            onDismiss = {
                editingRotation = null
                onEditorActiveChanged?.invoke(false)
            },
            onSave = { saved ->
                ShiftRepository.saveRotation(saved)
                editingRotation = null
                onEditorActiveChanged?.invoke(false)
                actionFeedback = ActionFeedback("Rotazione '${saved.name}' salvata!", isDestructive = false)
            },
            onApply = { rot ->
                ShiftRepository.saveRotation(rot)
                ShiftRepository.applyRotation(rot.id, LocalDate.now(), 90)
                editingRotation = null
                onEditorActiveChanged?.invoke(false)
                actionFeedback = ActionFeedback("Rotazione '${rot.name}' applicata per 90 giorni!", isDestructive = false)
            },
            onDelete = { id ->
                val rot = ShiftRepository.rotations.find { it.id == id }
                val rotName = rot?.name ?: "Rotazione"
                ShiftRepository.deleteRotation(id)
                editingRotation = null
                onEditorActiveChanged?.invoke(false)
                actionFeedback = ActionFeedback("Rotazione '$rotName' eliminata", isDestructive = true)
            }
        )
        return
    }

    // Gestione editor profilo lavoro a schermo intero (spazioso, dinamico e reattivo per tutti i display)
    if (editingWorkProfile != null) {
        val (profileToEdit, isNewProfile) = editingWorkProfile!!
        WorkProfileEditorScreen(
            profile = profileToEdit,
            isNew = isNewProfile,
            onDismiss = {
                editingWorkProfile = null
                onEditorActiveChanged?.invoke(false)
            },
            onSave = { saved ->
                if (isNewProfile) {
                    ShiftRepository.addWorkProfile(saved)
                    actionFeedback = ActionFeedback("Lavoro '${saved.name}' aggiunto con successo!", isDestructive = false)
                } else {
                    ShiftRepository.updateWorkProfile(saved)
                    actionFeedback = ActionFeedback("Lavoro '${saved.name}' salvato con successo!", isDestructive = false)
                }
                editingWorkProfile = null
                onEditorActiveChanged?.invoke(false)
            },
            onDelete = { profileId ->
                val name = ShiftRepository.workProfiles.find { it.id == profileId }?.name ?: "Lavoro"
                if (ShiftRepository.deleteWorkProfile(profileId)) {
                    actionFeedback = ActionFeedback("Lavoro '$name' eliminato", isDestructive = true)
                }
                editingWorkProfile = null
                onEditorActiveChanged?.invoke(false)
            },
            onConsultCcnl = { ccnl ->
                viewingCcnl = ccnl
            }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
        // Top Header: "Modelli & Turni"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Modelli & Turni",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    text = "Configura orari, lavoro e rotazioni cicliche",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianSurfaceContainer)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Configura",
                    tint = ObsidianPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Pill Tabs con Lente Scorrevole Fluida: Turni | Lavori | Rotazioni
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(25.dp))
                .border(
                    width = 1.2.dp,
                    brush = prismaticRefractionBrush(ObsidianPrimary),
                    shape = RoundedCornerShape(25.dp)
                ),
            color = Color.Transparent
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xF6111B2C),
                                Color(0xFA0A0E18)
                            )
                        )
                    )
                    .padding(4.dp)
            ) {
                val tabs = listOf("Turni", "Lavori", "Rotazioni")
                val tabCount = tabs.size
                val tabWidth = maxWidth / tabCount
                val targetOffsetX = tabWidth * selectedTab

                val animatedOffsetX by androidx.compose.animation.core.animateDpAsState(
                    targetValue = targetOffsetX,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )

                // Lente di scorrimento fluida
                Box(
                    modifier = Modifier
                        .offset(x = animatedOffsetX)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White,
                                    Color(0xFFE2E8F0)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    Color.White,
                                    Color(0x8038BDF8)
                                )
                            ),
                            RoundedCornerShape(21.dp)
                        )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(21.dp))
                                .bouncyClickable { selectedTab = index }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                                color = if (isSelected) Color(0xFF0F172A) else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            transitionSpec = {
                val isForward = targetState > initialState
                if (isForward) {
                    (slideInHorizontally(
                        initialOffsetX = { fullWidth -> (fullWidth * 0.40f).toInt() },
                        animationSpec = spring(
                            dampingRatio = 0.85f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(animationSpec = tween(200))).with(
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> (-fullWidth * 0.40f).toInt() },
                            animationSpec = spring(
                                dampingRatio = 0.85f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) + fadeOut(animationSpec = tween(160))
                    )
                } else {
                    (slideInHorizontally(
                        initialOffsetX = { fullWidth -> (-fullWidth * 0.40f).toInt() },
                        animationSpec = spring(
                            dampingRatio = 0.85f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(animationSpec = tween(200))).with(
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> (fullWidth * 0.40f).toInt() },
                            animationSpec = spring(
                                dampingRatio = 0.85f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) + fadeOut(animationSpec = tween(160))
                    )
                }
            }
        ) { currentTab ->
            when (currentTab) {
                0 -> {
                // TAB TURNI: Raggruppamento organizzato
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "I TUOI TURNI ATTIVI",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.2.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianSurfaceContainer,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
                    ) {
                        Text(
                            text = "${ShiftRepository.shiftDefinitions.size} definiti",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ObsidianPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp)
                    ) {
                        items(
                            items = ShiftRepository.shiftDefinitions,
                            key = { it.id }
                        ) { shiftDef ->
                            ShiftItemRow(
                                shiftDef = shiftDef,
                                onClick = { editingShift = shiftDef }
                            )
                            Divider(color = ObsidianBorderSubtle, thickness = 1.dp)
                        }

                        item {
                            // Tasto "+ Aggiungi turno" in stile Hero card
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        editingShift = ShiftDefinition(
                                            id = UUID.randomUUID().toString(),
                                            name = "Nuovo Turno",
                                            shortCode = "NT",
                                            startTime = "08:00",
                                            endTime = "16:00",
                                            colorHex = 0xFF38BDF8
                                        )
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(ObsidianPrimary.copy(alpha = 0.15f))
                                        .border(1.dp, ObsidianPrimary.copy(alpha = 0.3f), RoundedCornerShape(13.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Aggiungi",
                                        tint = ObsidianPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "Aggiungi Nuovo Turno",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Configura codice, colore, orario e indennità",
                                        fontSize = 11.5.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // TAB LAVORI: Gestione dinamica dei profili di lavoro e contratti CCNL
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "I TUOI PROFILI DI LAVORO & CCNL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.2.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ObsidianSurfaceContainer,
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
                        ) {
                            Text(
                                text = "${ShiftRepository.workProfiles.size} ${if (ShiftRepository.workProfiles.size == 1) "profilo" else "profili"}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ObsidianPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    ShiftRepository.workProfiles.forEach { profile ->
                        val isActive = profile.id == ShiftRepository.activeWorkProfileId || profile.isDefault
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                            border = androidx.compose.foundation.BorderStroke(
                                1.2.dp,
                                if (isActive) Color(0xFF10B981).copy(alpha = 0.5f) else ObsidianBorder
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(15.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(
                                                        Color(profile.colorHex),
                                                        Color(profile.colorHex).copy(alpha = 0.7f)
                                                    )
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = getWorkProfileIcon(profile.iconKey),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = profile.name.ifBlank { "Lavoro Senza Nome" },
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 20.sp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            if (isActive) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFF10B981).copy(alpha = 0.2f),
                                                    border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                                                ) {
                                                    Text(
                                                        "ATTIVO",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color(0xFF34D399),
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = if (profile.companyOrDepartment.isNotBlank())
                                                "${profile.companyOrDepartment} • ${profile.linkedCcnl.shortLabel}"
                                            else profile.linkedCcnl.shortLabel,
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }

                                    // Tasto Imposta come Attivo se non lo è
                                    if (!isActive) {
                                        OutlinedButton(
                                            onClick = {
                                                ShiftRepository.setActiveWorkProfile(profile.id)
                                                actionFeedback = ActionFeedback("Lavoro '${profile.name}' impostato come attivo!", isDestructive = false)
                                            },
                                            modifier = Modifier.height(34.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianPrimary.copy(alpha = 0.5f))
                                        ) {
                                            Text("Attiva", color = ObsidianPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Banner Scheda CCNL (Apri e clicchi!)
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { viewingCcnl = profile.linkedCcnl },
                                    shape = RoundedCornerShape(12.dp),
                                    color = ObsidianPrimary.copy(alpha = 0.10f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianPrimary.copy(alpha = 0.25f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Description,
                                                contentDescription = null,
                                                tint = ObsidianPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = profile.ccnlName,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    text = "Tocca per aprire al volo la scheda CCNL",
                                                    fontSize = 10.sp,
                                                    color = ObsidianPrimary
                                                )
                                            }
                                        }
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = ObsidianPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Divider(color = ObsidianBorderSubtle, thickness = 1.dp)
                                Spacer(modifier = Modifier.height(12.dp))

                                // Metriche sintetiche estrapolate dal CCNL / Profilo (Griglia 2x2 + Riposo)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        color = ObsidianSurfaceContainer,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                            Text("Orario Base", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("${profile.weeklyHours}h / sett.", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        color = ObsidianSurfaceContainer,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                            Text("Riposo Min. Stacco", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("${profile.minRestHoursBetweenShifts}h minimo", color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        color = ObsidianSurfaceContainer,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                            Text("Notturno (22-06)", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("+${profile.nightSurchargePercent}% CCNL", color = ObsidianPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        color = ObsidianSurfaceContainer,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                            Text("Festivo / Notte+Fest", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("+${profile.holidaySurchargePercent}% / +${profile.nightHolidaySurchargePercent}%", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                Divider(color = ObsidianBorderSubtle, thickness = 1.dp)
                                Spacer(modifier = Modifier.height(12.dp))

                                // Azioni Lavoro: "Scheda CCNL", "Modifica", "Elimina"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { viewingCcnl = profile.linkedCcnl },
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .height(42.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimaryContainer)
                                    ) {
                                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Scheda CCNL", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { editingWorkProfile = profile to false },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Modifica", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    if (ShiftRepository.workProfiles.size > 1) {
                                        IconButton(
                                            onClick = {
                                                val name = profile.name
                                                if (ShiftRepository.deleteWorkProfile(profile.id)) {
                                                    actionFeedback = ActionFeedback("Lavoro '$name' eliminato", isDestructive = true)
                                                }
                                            },
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                                                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Card Tasto "+ Aggiungi Secondo Lavoro" (INTERATTIVA E COLLEGATA!)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .clickable {
                                val newProfile = WorkProfile(
                                    id = "job_${System.currentTimeMillis()}",
                                    name = "",
                                    companyOrDepartment = "",
                                    ccnlCode = "CCNL_SANITA",
                                    ccnlName = "CCNL Sanità Pubblica & Privata",
                                    weeklyHours = 36,
                                    minRestHoursBetweenShifts = 11,
                                    nightSurchargePercent = 25,
                                    holidaySurchargePercent = 30,
                                    nightHolidaySurchargePercent = 50,
                                    colorHex = 0xFF10B981,
                                    iconKey = "hospital",
                                    isDefault = false
                                )
                                editingWorkProfile = newProfile to true
                            },
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceContainer),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(ObsidianPrimary.copy(alpha = 0.15f))
                                    .border(1.dp, ObsidianPrimary.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AddBusiness, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Aggiungi Secondo Lavoro", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                                Text("Collega un CCNL con parametri orari e riposo minimo", color = TextSecondary, fontSize = 11.5.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(ObsidianPrimaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }

            2 -> {
                // TAB ROTAZIONI: Visualizzazione del ciclo 28gg in matrice 4x7
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ROTAZIONI CICLICHE PERIODICHE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.2.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianSurfaceContainer,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
                    ) {
                        Text(
                            text = "${ShiftRepository.rotations.size} schemi",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ObsidianPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // ELENCO ROTAZIONI: Ogni rotazione in un proprio riquadro (Card) autonomo ed elegante
                ShiftRepository.getRotationsSortedByDefault().forEach { rot ->
                    val isDefault = rot.id == ShiftRepository.defaultRotationId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isDefault) 1.5.dp else 1.dp,
                            color = if (isDefault) Color(0xFFF59E0B) else ObsidianBorder
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Header della Rotazione: Icona fissa rotazione a sx, Nome/Badge al centro, Stellina animata e Matita a dx
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Icona Rotazione SEMPRE coerente a sinistra (non cambia mai in stella)
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(ObsidianPrimary.copy(alpha = 0.15f))
                                        .border(1.dp, ObsidianPrimary.copy(alpha = 0.35f), RoundedCornerShape(13.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Repeat,
                                        contentDescription = null,
                                        tint = ObsidianPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                val totalWeeks = ((rot.daySequence.size + 6) / 7).coerceAtLeast(1)
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(rot.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
                                        if (isDefault) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                                border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
                                            ) {
                                                Text(
                                                    text = "⭐ PREDEFINITO",
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color(0xFFFBBF24),
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text("${rot.daySequence.size} giorni • Ciclo $totalWeeks sett.", color = TextSecondary, fontSize = 11.5.sp)
                                }

                                // Azioni in alto a destra: Stellina preferito con animazione elastica + Matita modifica
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    var starTrigger by remember { mutableStateOf(false) }
                                    val starScale by animateFloatAsState(
                                        targetValue = if (starTrigger) 1.30f else 1.0f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                                        finishedListener = { starTrigger = false }
                                    )

                                    Surface(
                                        onClick = {
                                            starTrigger = true
                                            val isNowDefault = ShiftRepository.toggleDefaultRotation(rot.id)
                                            actionFeedback = if (isNowDefault) {
                                                ActionFeedback("Rotazione '${rot.name}' impostata come predefinita!", isDestructive = false)
                                            } else {
                                                ActionFeedback("Rotazione predefinita revocata per '${rot.name}'", isDestructive = false)
                                            }
                                        },
                                        shape = CircleShape,
                                        color = if (isDefault) Color(0xFFF59E0B).copy(alpha = 0.22f) else ObsidianSurfaceContainer,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDefault) Color(0xFFF59E0B) else ObsidianBorderSubtle),
                                        modifier = Modifier
                                            .size(36.dp)
                                            .scale(starScale)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isDefault) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = "Imposta come predefinito",
                                                tint = if (isDefault) Color(0xFFFBBF24) else TextSecondary,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        onClick = { editingRotation = rot },
                                        shape = CircleShape,
                                        color = ObsidianSurfaceContainer,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Modifica rotazione",
                                                tint = TextPrimary,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // MATRICE SETTIMANALE DINAMICA (Panoramica rapida dei turni)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(ObsidianSurfaceContainer)
                                    .border(1.dp, ObsidianBorderSubtle, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                // Intestazione giorni L M M G V S D
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("", modifier = Modifier.width(22.dp))
                                    listOf("L", "M", "M", "G", "V", "S", "D").forEach { d ->
                                        Text(
                                            text = d,
                                            color = TextMuted,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.weight(1f),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }

                                // Righe di Settimana dinamiche
                                val totalWeeks = Math.max(1, (rot.daySequence.size + 6) / 7)
                                for (weekIdx in 0 until totalWeeks) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "S${weekIdx + 1}",
                                            color = TextMuted,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(22.dp)
                                        )
                                        for (dayIdx in 0 until 7) {
                                            val totalIdx = weekIdx * 7 + dayIdx
                                            val isWithinBounds = totalIdx < rot.daySequence.size
                                            val shiftId = rot.daySequence.getOrNull(totalIdx)
                                            val shiftDef = shiftId?.let { ShiftRepository.getShiftDefinition(it) }
                                            val code = if (isWithinBounds) {
                                                shiftDef?.let { getCompactShiftCode(it.id, it.shortCode) } ?: "--"
                                            } else "•"
                                            val col = if (isWithinBounds) {
                                                shiftDef?.color ?: Color(0xFF1E2638)
                                            } else Color.Transparent

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(26.dp)
                                                    .padding(horizontal = 2.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(col)
                                                    .then(
                                                        if (!isWithinBounds) Modifier.border(0.6.dp, ObsidianBorderSubtle, RoundedCornerShape(6.dp))
                                                        else Modifier
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = code,
                                                    color = if (isWithinBounds) Color.White else TextMuted,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                            }
                        }
                    }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // RIQUADRO A SE' STANTE PER AGGIUNGI NUOVA ROTAZIONE (in basso a destra / card dedicata)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable {
                            editingRotation = ShiftRotation(
                                id = "rot_${UUID.randomUUID().toString().take(8)}",
                                name = "Nuova Rotazione",
                                totalDays = 21,
                                daySequence = List(21) { idx ->
                                    when (idx % 7) {
                                        0, 1 -> "mattina"
                                        2, 3 -> "pomeriggio"
                                        4 -> "notte"
                                        5 -> "smonto_notte"
                                        else -> "riposo"
                                    }
                                },
                                description = "Rotazione libera personalizzata"
                            )
                        },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceContainer),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, ObsidianPrimary.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(ObsidianPrimary.copy(alpha = 0.2f))
                                .border(1.dp, ObsidianPrimary.copy(alpha = 0.5f), RoundedCornerShape(13.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Aggiungi",
                                tint = ObsidianPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Aggiungi Nuova Rotazione",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Crea un ciclo a settimane libere (S1, S2, S3...) con fine personalizzata",
                                fontSize = 11.5.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(90.dp))
            }
        }
    }
}
}

        // HUD Magico per Feedback Creazione/Salvataggio (smeraldo) ed Eliminazione (rubino)
        MagicalActionFeedbackHUD(
            feedback = actionFeedback,
            onDismiss = { actionFeedback = null },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 10.dp)
        )
    }

    // Dialog Scheda CCNL ("Apri e clicchi!")
    if (viewingCcnl != null) {
        CcnlDetailsDialog(
            ccnl = viewingCcnl!!,
            onDismiss = { viewingCcnl = null }
        )
    }

    // Dialog Applica Rotazione
    if (showRotationApplyDialog != null) {
        val rot = showRotationApplyDialog!!
        val today = LocalDate.now()
        AlertDialog(
            onDismissRequest = { showRotationApplyDialog = null },
            title = { Text("Applica ${rot.name}", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Scegli per quanto tempo stendere il ciclo da ${rot.daySequence.size} giorni sul calendario:", color = TextSecondary, fontSize = 13.sp)
                    listOf(
                        "1 Mese" to 30,
                        "3 Mesi (Consigliato)" to 90,
                        "6 Mesi" to 180,
                        "1 Anno Intero" to 365
                    ).forEach { (label, days) ->
                        OutlinedButton(
                            onClick = {
                                ShiftRepository.applyRotation(rot.id, today, days)
                                showRotationApplyDialog = null
                                actionFeedback = ActionFeedback("Rotazione '${rot.name}' applicata per $days giorni!", isDestructive = false)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .bouncyClickable {
                                    ShiftRepository.applyRotation(rot.id, today, days)
                                    showRotationApplyDialog = null
                                    actionFeedback = ActionFeedback("Rotazione '${rot.name}' applicata per $days giorni!", isDestructive = false)
                                },
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                        ) {
                            Text(label, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRotationApplyDialog = null }) {
                    Text("Annulla", color = TextSecondary)
                }
            },
            containerColor = ObsidianSurface,
            shape = RoundedCornerShape(22.dp)
        )
    }
}

val extendedPaletteColors = listOf(
    0xFF0EA5E9, // Sky Blue
    0xFF0284C7, // Ocean Blue
    0xFF2563EB, // Royal Blue
    0xFF6366F1, // Indigo
    0xFF8B5CF6, // Purple
    0xFFA855F7, // Bright Violet
    0xFFD946EF, // Fuchsia
    0xFFEC4899, // Pink
    0xFFF43F5E, // Rose
    0xFFEF4444, // Red
    0xFFF97316, // Orange
    0xFFF59E0B, // Amber
    0xFFEAB308, // Gold
    0xFF84CC16, // Lime
    0xFF10B981, // Emerald
    0xFF14B8A6, // Teal
    0xFF06B6D4, // Cyan
    0xFF64748B  // Slate
)

val rainbowSwatches = listOf(
    listOf(0xFFFF8A80, 0xFFFF5252, 0xFFFF1744, 0xFFD50000, 0xFFB71C1C),
    listOf(0xFFFFD180, 0xFFFFAB40, 0xFFFF9100, 0xFFFF6D00, 0xFFE65100),
    listOf(0xFFFFFF8D, 0xFFFFE57F, 0xFFFFD740, 0xFFFFC400, 0xFFFFAB00),
    listOf(0xFFCCFF90, 0xFFB2FF59, 0xFF76FF03, 0xFF64DD17, 0xFF33691E),
    listOf(0xFFA7F3D0, 0xFF34D399, 0xFF10B981, 0xFF059669, 0xFF064E3B),
    listOf(0xFFA5F3FC, 0xFF22D3EE, 0xFF06B6D4, 0xFF0891B2, 0xFF164E63),
    listOf(0xFFBAE6FD, 0xFF38BDF8, 0xFF0EA5E9, 0xFF0284C7, 0xFF0369A1),
    listOf(0xFFC7D2FE, 0xFF818CF8, 0xFF6366F1, 0xFF4F46E5, 0xFF3730A3),
    listOf(0xFFDDD6FE, 0xFFA78BFA, 0xFF8B5CF6, 0xFF7C3AED, 0xFF5B21B6),
    listOf(0xFFFBCFE8, 0xFFF472B6, 0xFFEC4899, 0xFFDB2777, 0xFF9D174D),
    listOf(0xFFCBD5E1, 0xFF94A3B8, 0xFF64748B, 0xFF475569, 0xFF1E293B)
)

fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val h = (hue % 360f + 360f) % 360f
    val c = value * saturation
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = value - c
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = 1f
    )
}

fun colorToLong(color: Color): Long {
    val a = (color.alpha * 255).toLong().coerceIn(0, 255)
    val r = (color.red * 255).toLong().coerceIn(0, 255)
    val g = (color.green * 255).toLong().coerceIn(0, 255)
    val b = (color.blue * 255).toLong().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

fun colorToHex(color: Color): String {
    val r = (color.red * 255).toInt().coerceIn(0, 255)
    val g = (color.green * 255).toInt().coerceIn(0, 255)
    val b = (color.blue * 255).toInt().coerceIn(0, 255)
    return String.format("#%02X%02X%02X", r, g, b)
}

fun parseHexToColor(hex: String): Color? {
    val clean = hex.removePrefix("#").trim()
    return try {
        if (clean.length == 6) {
            val r = clean.substring(0, 2).toInt(16)
            val g = clean.substring(2, 4).toInt(16)
            val b = clean.substring(4, 6).toInt(16)
            Color(r, g, b)
        } else if (clean.length == 8) {
            val a = clean.substring(0, 2).toInt(16)
            val r = clean.substring(2, 4).toInt(16)
            val g = clean.substring(4, 6).toInt(16)
            val b = clean.substring(6, 8).toInt(16)
            Color(r, g, b, a)
        } else null
    } catch (e: Exception) {
        null
    }
}

@Composable
fun CustomColorPickerDialog(
    initialColorHex: Long,
    onDismiss: () -> Unit,
    onColorSelected: (Long) -> Unit
) {
    var selectedColor by remember { mutableStateOf(Color(initialColorHex)) }
    var hue by remember { mutableStateOf(195f) }
    var saturation by remember { mutableStateOf(0.85f) }
    var value by remember { mutableStateOf(0.95f) }
    var hexText by remember { mutableStateOf(colorToHex(Color(initialColorHex)).removePrefix("#")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(selectedColor)
                        .border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(11.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Colore Personalizzato",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = colorToHex(selectedColor),
                        fontSize = 12.sp,
                        color = ObsidianPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "TAVOLOZZA COMPLETA",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianSurfaceContainer)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    rainbowSwatches.forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            rowColors.forEach { colVal ->
                                val swatchColor = Color(colVal)
                                val isSel = colorToLong(selectedColor) == colVal
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(swatchColor)
                                        .border(
                                            width = if (isSel) 2.5.dp else 0.dp,
                                            color = if (isSel) Color.White else Color.Transparent,
                                            shape = RoundedCornerShape(7.dp)
                                        )
                                        .bouncyClickable {
                                            selectedColor = swatchColor
                                            hexText = colorToHex(swatchColor).removePrefix("#")
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSel) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Spettro continuo Hue
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Spettro Tonalità (Hue)", fontSize = 11.5.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                        Text("${hue.toInt()}°", fontSize = 11.5.sp, color = ObsidianPrimary, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Red,
                                        Color.Yellow,
                                        Color.Green,
                                        Color.Cyan,
                                        Color.Blue,
                                        Color.Magenta,
                                        Color.Red
                                    )
                                )
                            )
                    )

                    Slider(
                        value = hue,
                        onValueChange = {
                            hue = it
                            selectedColor = hsvToColor(hue, saturation, value)
                            hexText = colorToHex(selectedColor).removePrefix("#")
                        },
                        valueRange = 0f..360f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth().height(24.dp)
                    )
                }

                // Luminosità
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Luminosità", fontSize = 11.5.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                        Text("${(value * 100).toInt()}%", fontSize = 11.5.sp, color = ObsidianPrimary, fontWeight = FontWeight.Bold)
                    }

                    Slider(
                        value = value,
                        onValueChange = {
                            value = it
                            selectedColor = hsvToColor(hue, saturation, value)
                            hexText = colorToHex(selectedColor).removePrefix("#")
                        },
                        valueRange = 0.2f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = ObsidianPrimary,
                            inactiveTrackColor = ObsidianSurfaceContainer
                        ),
                        modifier = Modifier.fillMaxWidth().height(24.dp)
                    )
                }

                // Codice HEX
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Codice HEX:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { input: String ->
                            val clean = input.filter { it.isLetterOrDigit() }.take(6).uppercase()
                            hexText = clean
                            val parsed = parseHexToColor(clean)
                            if (parsed != null) {
                                selectedColor = parsed
                            }
                        },
                        leadingIcon = { Text("#", color = TextMuted, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onColorSelected(colorToLong(selectedColor)) },
                colors = ButtonDefaults.buttonColors(containerColor = selectedColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Applica Colore", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla", color = TextSecondary)
            }
        },
        containerColor = ObsidianSurface,
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
fun UniversalColorPickerRow(
    selectedColorHex: Long,
    onColorSelected: (Long) -> Unit
) {
    var showCustomPicker by remember { mutableStateOf(false) }
    val isCustom = extendedPaletteColors.none { it == selectedColorHex }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(4.dp))
            extendedPaletteColors.forEach { colHex ->
                val isSelected = selectedColorHex == colHex
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(colHex))
                        .border(
                            width = if (isSelected) 3.dp else 0.dp,
                            color = if (isSelected) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .bouncyClickable { onColorSelected(colHex) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selezionato",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Ultimo colore a destra: badge personalizzato arcobaleno!
            val rainbowBrush = Brush.sweepGradient(
                listOf(
                    Color(0xFFFF1744),
                    Color(0xFFFF9100),
                    Color(0xFFFFD740),
                    Color(0xFF00E676),
                    Color(0xFF00E5FF),
                    Color(0xFF2979FF),
                    Color(0xFFD500F9),
                    Color(0xFFFF1744)
                )
            )

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isCustom) Color(selectedColorHex) else ObsidianSurfaceContainer)
                    .border(
                        width = if (isCustom) 3.dp else 1.8.dp,
                        brush = if (isCustom) Brush.linearGradient(listOf(Color.White, Color.White)) else rainbowBrush,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .bouncyClickable { showCustomPicker = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = "Personalizzato",
                    tint = if (isCustom) Color.White else Color.White.copy(alpha = 0.95f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
        }

        if (showCustomPicker) {
            CustomColorPickerDialog(
                initialColorHex = selectedColorHex,
                onDismiss = { showCustomPicker = false },
                onColorSelected = {
                    onColorSelected(it)
                    showCustomPicker = false
                }
            )
        }
    }
}

/**
 * Singola riga elenco turno:
 * Badge compatto e proporzionato verticalmente, nome del turno e orario.
 * Icona a destra rimossa su richiesta dell'utente.
 */
@Composable
fun ShiftItemRow(
    shiftDef: ShiftDefinition,
    onClick: () -> Unit
) {
    val compactCode = getCompactShiftCode(shiftDef.id, shiftDef.shortCode)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Badge quadrato compatto e perfettamente proporzionato
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(shiftDef.color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = compactCode,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.5.sp,
                letterSpacing = 0.2.sp
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = shiftDef.name,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            val timeText = if (shiftDef.isAllDay) {
                "Tutto il giorno"
            } else {
                val breakLabel = if (shiftDef.breakMinutes > 0) {
                    val startLabel = shiftDef.breakStartTime?.let { " da $it" } ?: ""
                    " (☕ ${shiftDef.breakMinutes}m$startLabel)"
                } else ""
                "${shiftDef.startTime} - ${shiftDef.endTime} • ${shiftDef.durationFormatted}$breakLabel"
            }
            Text(
                text = timeText,
                color = TextSecondary,
                fontSize = 11.5.sp
            )
        }
    }
}

/**
 * Schermata di Modifica e Creazione Turno completa:
 * - BackHandler per tornare indietro con il tasto fisico senza uscire
 * - Hero Showcase Card fluttuante con gradiente del colore attivo e anteprima 3D
 * - Chip Categorie rapide (Lavoro, Riposo, Ferie, Reperibilità)
 * - Selettore Orari a Step (+/- 15 min) e Presets rapidi turnisti (Mattina, Pomeriggio, Notte, MD)
 * - Palette colori squircle a 10 tinte vivaci
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftEditorScreen(
    shift: ShiftDefinition,
    onDismiss: () -> Unit,
    onSave: (ShiftDefinition) -> Unit,
    onDelete: (String) -> Unit
) {
    BackHandler { onDismiss() }

    fun adjustTime(timeStr: String, deltaMinutes: Int): String {
        return try {
            val parts = timeStr.split(":")
            val totalMins = (parts[0].toInt() * 60 + parts[1].toInt() + deltaMinutes + 1440) % 1440
            String.format("%02d:%02d", totalMins / 60, totalMins % 60)
        } catch (e: Exception) {
            timeStr
        }
    }

    var name by remember { mutableStateOf(shift.name) }
    var shortCode by remember { mutableStateOf(shift.shortCode) }
    var startTime by remember { mutableStateOf(shift.startTime) }
    var endTime by remember { mutableStateOf(shift.endTime) }
    var selectedColorHex by remember { mutableStateOf(shift.colorHex) }
    var isAllDay by remember { mutableStateOf(shift.isAllDay) }
    var hasBreak by remember { mutableStateOf(shift.breakMinutes > 0) }
    var breakMinutes by remember { mutableStateOf(if (shift.breakMinutes > 0) shift.breakMinutes else 30) }
    var breakStartTime by remember { mutableStateOf<String>(shift.breakStartTime ?: "13:00") }
    var showManualBreakDialog by remember { mutableStateOf(false) }
    var manualMinutesText by remember { mutableStateOf(if (shift.breakMinutes > 0) shift.breakMinutes.toString() else "30") }

    val currentColor = Color(selectedColorHex)

    val onPerformSave = {
        val updated = shift.copy(
            name = name.trim().ifEmpty { "Turno" },
            shortCode = shortCode.trim().ifEmpty { name.take(2).uppercase() },
            startTime = startTime,
            endTime = endTime,
            isAllDay = isAllDay,
            colorHex = selectedColorHex,
            breakMinutes = if (!isAllDay && hasBreak) breakMinutes else 0,
            breakStartTime = if (!isAllDay && hasBreak) breakStartTime else null
        )
        onSave(updated)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Barra superiore con Indietro a pillola e Titolo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Indietro", tint = TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Turni", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Text(
                    text = if (shift.name.isBlank()) "Nuovo Turno" else name,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )

                // Spacer bilanciato
                Spacer(modifier = Modifier.size(40.dp))
            }

            Spacer(modifier = Modifier.height(14.dp))

        // HERO SHOWCASE CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, currentColor.copy(alpha = 0.5f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                currentColor.copy(alpha = 0.15f),
                                ObsidianSurface
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Grande icona circolare con glow
                    val heroCode = shortCode.ifEmpty { getCompactShiftCode(shift.id, name.take(2).uppercase()) }
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(currentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = heroCode,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = name.ifEmpty { "Nome Turno" },
                        color = TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Orario con freccia
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isAllDay) {
                            Text(
                                text = "Tutto il giorno",
                                color = TextSecondary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = startTime,
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = currentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = endTime,
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Chip durata netta e chip pausa
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianSurfaceContainer,
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
                        ) {
                            val effBreak = if (!isAllDay && hasBreak) breakMinutes else 0
                            val previewShift = shift.copy(startTime = startTime, endTime = endTime, isAllDay = isAllDay, breakMinutes = effBreak)
                            Text(
                                text = if (isAllDay) "Riposo / Assenza" else "⏱ ${previewShift.durationFormatted}",
                                color = ObsidianPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        if (!isAllDay) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (hasBreak) Color(0xFFF59E0B).copy(alpha = 0.2f) else ObsidianSurfaceContainer,
                                border = androidx.compose.foundation.BorderStroke(
                                    0.8.dp,
                                    if (hasBreak) Color(0xFFF59E0B).copy(alpha = 0.5f) else ObsidianBorderSubtle
                                )
                            ) {
                                Text(
                                    text = if (hasBreak) "☕ Pausa: ${breakMinutes}m" else "☕ Nessuna pausa",
                                    color = if (hasBreak) Color(0xFFF59E0B) else TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // NOME E SIGLA
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome Turno (es. Pomeriggio, Mattina, Notte)", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = shortCode,
                    onValueChange = { shortCode = it.take(4).uppercase() },
                    label = { Text("Codice Breve (es. POM, MAT, NOT)", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SELETTORE ORARIO CON PRESETS RAPIDI E STEPPER +/- 15 MIN
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
        ) {
            Column(modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Tutto il Giorno", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Per riposi, ferie e permessi", color = TextSecondary, fontSize = 11.5.sp)
                        }
                        Switch(
                            checked = isAllDay,
                            onCheckedChange = { isAllDay = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = currentColor
                            )
                        )
                    }

                    if (!isAllDay) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = ObsidianBorderSubtle, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            "ORARIO DI SERVIZIO",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Stepper Ora Inizio e Ora Fine
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Inizio
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(ObsidianSurfaceContainer)
                                    .border(1.dp, ObsidianBorderSubtle, RoundedCornerShape(14.dp))
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Inizio", color = TextSecondary, fontSize = 11.sp)
                                Text(startTime, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        shape = CircleShape,
                                        color = ObsidianSurfaceHigh,
                                        modifier = Modifier.bouncyClickable { startTime = adjustTime(startTime, -15) }
                                    ) {
                                        Text("-15", color = TextPrimary, fontSize = 10.sp, modifier = Modifier.padding(6.dp))
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = ObsidianSurfaceHigh,
                                        modifier = Modifier.bouncyClickable { startTime = adjustTime(startTime, 15) }
                                    ) {
                                        Text("+15", color = TextPrimary, fontSize = 10.sp, modifier = Modifier.padding(6.dp))
                                    }
                                }
                            }

                            // Fine
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(ObsidianSurfaceContainer)
                                    .border(1.dp, ObsidianBorderSubtle, RoundedCornerShape(14.dp))
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Fine", color = TextSecondary, fontSize = 11.sp)
                                Text(endTime, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        shape = CircleShape,
                                        color = ObsidianSurfaceHigh,
                                        modifier = Modifier.bouncyClickable { endTime = adjustTime(endTime, -15) }
                                    ) {
                                        Text("-15", color = TextPrimary, fontSize = 10.sp, modifier = Modifier.padding(6.dp))
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = ObsidianSurfaceHigh,
                                        modifier = Modifier.bouncyClickable { endTime = adjustTime(endTime, 15) }
                                    ) {
                                        Text("+15", color = TextPrimary, fontSize = 10.sp, modifier = Modifier.padding(6.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Preset Rapidi Turnisti Italiani (Mattina, Pomeriggio, Notte, MD)
                        Text("PRESET RAPIDI", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (!isAllDay) {
                    Spacer(modifier = Modifier.height(8.dp))
                    // Riga a tutta larghezza senza clipping prematuro: scorre fino ai bordi della card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Spacer(modifier = Modifier.width(16.dp))
                        listOf(
                            Triple("Mattina", "06:00", "13:36"),
                            Triple("Pomeriggio", "14:00", "21:36"),
                            Triple("MD", "13:00", "21:00"),
                            Triple("Notte", "21:36", "06:00"),
                            Triple("Giornaliero", "08:00", "16:30")
                        ).forEach { (pName, s, e) ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ObsidianSurfaceContainer,
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                                modifier = Modifier.bouncyClickable {
                                    startTime = s
                                    endTime = e
                                }
                            ) {
                                Text(
                                    text = "$pName ($s-$e)",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                }
            }
        }

        if (!isAllDay) {
            Spacer(modifier = Modifier.height(16.dp))

            // SEZIONE PAUSA DI LAVORO
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (hasBreak) Color(0xFFF59E0B).copy(alpha = 0.2f) else ObsidianSurfaceContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("☕", fontSize = 18.sp)
                            }
                            Column {
                                Text(
                                    text = "Pausa di Lavoro",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (hasBreak) "Detratta dalla durata netta (${breakMinutes}m)" else "Nessuna pausa configurata",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }

                        Switch(
                            checked = hasBreak,
                            onCheckedChange = { 
                                hasBreak = it
                                if (it && breakMinutes <= 0) breakMinutes = 30
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFF59E0B)
                            )
                        )
                    }

                    if (hasBreak) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = ObsidianBorderSubtle, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(14.dp))

                        // ORARIO INIZIO PAUSA
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "INIZIO PAUSA",
                                color = TextMuted,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = breakStartTime,
                                onValueChange = { breakStartTime = it },
                                placeholder = { Text("13:00", color = TextMuted, fontSize = 14.sp) },
                                label = { Text("Orario (es. 12:00, 12:21, 13:00)", color = TextSecondary, fontSize = 11.5.sp) },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = Color(0xFFF59E0B),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    textColor = Color(0xFFF59E0B),
                                    focusedBorderColor = Color(0xFFF59E0B),
                                    unfocusedBorderColor = ObsidianBorderSubtle,
                                    cursorColor = Color(0xFFF59E0B)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = ObsidianBorderSubtle, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(14.dp))

                        // DURATA PAUSA (Valore + Stepper -15/-5/-1/+1/+5/+15)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("DURATA PAUSA", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Text(
                                    text = "${breakMinutes} min",
                                    color = Color(0xFFF59E0B),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = ObsidianSurfaceHigh,
                                    modifier = Modifier.bouncyClickable {
                                        breakMinutes = (breakMinutes - 30).coerceAtLeast(1)
                                        manualMinutesText = breakMinutes.toString()
                                    }
                                ) {
                                    Text("-30", color = TextPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp))
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = ObsidianSurfaceHigh,
                                    modifier = Modifier.bouncyClickable {
                                        breakMinutes = (breakMinutes - 15).coerceAtLeast(1)
                                        manualMinutesText = breakMinutes.toString()
                                    }
                                ) {
                                    Text("-15", color = TextPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp))
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = ObsidianSurfaceHigh,
                                    modifier = Modifier.bouncyClickable {
                                        breakMinutes = (breakMinutes + 15).coerceAtMost(240)
                                        manualMinutesText = breakMinutes.toString()
                                    }
                                ) {
                                    Text("+15", color = TextPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp))
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = ObsidianSurfaceHigh,
                                    modifier = Modifier.bouncyClickable {
                                        breakMinutes = (breakMinutes + 30).coerceAtMost(240)
                                        manualMinutesText = breakMinutes.toString()
                                    }
                                ) {
                                    Text("+30", color = TextPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Presets rapidi di pausa (15m, 30m, 45m, 60m) + Tasto Inserimento Manuale (es. 36m)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(15, 30, 45, 60).forEach { mins ->
                                val isSel = breakMinutes == mins
                                Surface(
                                    onClick = {
                                        breakMinutes = mins
                                        manualMinutesText = mins.toString()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSel) Color(0xFFF59E0B).copy(alpha = 0.25f) else ObsidianSurfaceContainer,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSel) Color(0xFFF59E0B) else ObsidianBorderSubtle
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "${mins}m",
                                        color = if (isSel) Color(0xFFF59E0B) else TextSecondary,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }

                            // Chip per inserimento manuale esatto (es. 36m)
                            val isCustomBreak = listOf(15, 30, 45, 60).none { it == breakMinutes }
                            Surface(
                                onClick = {
                                    manualMinutesText = breakMinutes.toString()
                                    showManualBreakDialog = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCustomBreak) Color(0xFFF59E0B).copy(alpha = 0.35f) else ObsidianSurfaceContainer,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.2.dp,
                                    if (isCustomBreak) Color(0xFFF59E0B) else ObsidianBorderSubtle
                                ),
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = if (isCustomBreak) Color(0xFFF59E0B) else TextSecondary, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (isCustomBreak) "${breakMinutes}m" else "Altro",
                                        color = if (isCustomBreak) Color(0xFFF59E0B) else TextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showManualBreakDialog) {
            AlertDialog(
                onDismissRequest = { showManualBreakDialog = false },
                title = { Text("Inserisci Durata Pausa", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Specifica i minuti esatti (es. 36 minuti):", color = TextSecondary, fontSize = 13.sp)
                        OutlinedTextField(
                            value = manualMinutesText,
                            onValueChange = { input: String -> manualMinutesText = input.filter { ch -> ch.isDigit() }.take(4) },
                            trailingIcon = { Text("min", color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val parsed = manualMinutesText.toIntOrNull()?.coerceIn(1, 480) ?: breakMinutes
                            breakMinutes = parsed
                            showManualBreakDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Conferma", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManualBreakDialog = false }) {
                        Text("Annulla", color = TextSecondary)
                    }
                },
                containerColor = ObsidianSurface,
                shape = RoundedCornerShape(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // PALETTE COLORI CON SELETTORE UNIVERSALE & PERSONALIZZATO
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
        ) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                Text(
                    text = "COLORE IDENTIFICATIVO",
                    color = TextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                UniversalColorPickerRow(
                    selectedColorHex = selectedColorHex,
                    onColorSelected = { selectedColorHex = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // AZIONE AGGIUNTIVA: DUPLICA
        OutlinedButton(
            onClick = {
                val copy = shift.copy(
                    id = UUID.randomUUID().toString(),
                    name = "$name (Copia)",
                    shortCode = "${shortCode}2",
                    startTime = startTime,
                    endTime = endTime,
                    isAllDay = isAllDay,
                    colorHex = selectedColorHex,
                    breakMinutes = if (!isAllDay && hasBreak) breakMinutes else 0
                )
                onSave(copy)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Duplica questo Turno", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        // Spaziatura per non sovrapporsi alla dock inferiore fissa
        Spacer(modifier = Modifier.height(110.dp))
    }

    // STICKY BOTTOM DOCK (Riccardo Breccia / Liquid Glass: Elimina a sinistra, Salva a destra)
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth(),
        color = ObsidianSurface.copy(alpha = 0.96f),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Elimina a sinistra in rosso
            OutlinedButton(
                onClick = { onDelete(shift.id) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFFEF4444).copy(alpha = 0.12f),
                    contentColor = Color(0xFFEF4444)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.45f))
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Elimina", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            // Salva a destra nel colore del turno
            Button(
                onClick = onPerformSave,
                modifier = Modifier
                    .weight(1.3f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = currentColor)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Salva Turno", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        }
    }
}
}

/**
 * Editor Visivo della Rotazione con Matrice Settimanale Dinamica e Palette Turni (Screenshot 15).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotationMatrixEditorScreen(
    rotation: ShiftRotation,
    onDismiss: () -> Unit,
    onSave: (ShiftRotation) -> Unit,
    onApply: (ShiftRotation) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    BackHandler { onDismiss() }

    var title by remember { mutableStateOf(rotation.name) }
    val currentSequence = remember { mutableStateListOf<String>().apply { addAll(rotation.daySequence) } }
    var selectedPaintShiftId by remember { mutableStateOf("pomeriggio") }
    var visibleWeeks by remember { 
        mutableStateOf(((currentSequence.size + 6) / 7).coerceIn(2, 10).coerceAtLeast(3)) 
    }

    val dayNamesLong = listOf("Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica")

    fun setDuration(targetDays: Int) {
        val clamped = targetDays.coerceIn(1, 70)
        while (currentSequence.size < clamped) {
            currentSequence.add(selectedPaintShiftId)
        }
        while (currentSequence.size > clamped) {
            currentSequence.removeAt(currentSequence.size - 1)
        }
        val neededWeeks = ((clamped + 6) / 7).coerceIn(1, 10)
        if (neededWeeks > visibleWeeks) {
            visibleWeeks = neededWeeks
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .padding(16.dp)
    ) {
        // Top Header: <  Turno A  (N giorni)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianSurfaceContainer)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Indietro", tint = TextPrimary)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title.ifEmpty { "Nuova Rotazione" }, color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                val lastIdx = (currentSequence.size - 1).coerceAtLeast(0)
                val lastWeek = (lastIdx / 7) + 1
                val lastDayName = dayNamesLong[lastIdx % 7]
                Text(
                    text = "${currentSequence.size} giorni • Termina di $lastDayName (S$lastWeek)",
                    color = ObsidianPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Campo Titolo
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Nome Rotazione", color = TextSecondary, fontSize = 13.5.sp)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("es. Turno A, B, C...") },
                    modifier = Modifier.width(200.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // BARRA STATO FINE CICLO LIBERA & STEPPER DINAMICO
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LUNGHEZZA CICLO LIBERO",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                    val lastIdx = (currentSequence.size - 1).coerceAtLeast(0)
                    val lastDayName = dayNamesLong[lastIdx % 7]
                    val lastWeek = (lastIdx / 7) + 1
                    Text(
                        text = "${currentSequence.size} gg (Fine: S$lastWeek $lastDayName)",
                        color = TextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ObsidianSurfaceHigh,
                        modifier = Modifier
                            .size(30.dp)
                            .bouncyClickable { setDuration(currentSequence.size - 1) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("-1", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ObsidianPrimary.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianPrimary.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "${currentSequence.size} gg",
                            color = ObsidianPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = ObsidianSurfaceHigh,
                        modifier = Modifier
                            .size(30.dp)
                            .bouncyClickable { setDuration(currentSequence.size + 1) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("+1", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Matrice Settimanale Dinamica (Calcola automaticamente le settimane necessarie)
        val totalWeeks = ((currentSequence.size + 6) / 7).coerceAtLeast(1)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Intestazione giorni lun-dom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("", modifier = Modifier.width(22.dp))
                    listOf("L", "M", "M", "G", "V", "S", "D").forEach { d ->
                        Text(
                            text = d,
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                for (rowIndex in 0 until visibleWeeks) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "S${rowIndex + 1}",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(26.dp)
                        )

                        for (colIndex in 0 until 7) {
                            val dayIndex = rowIndex * 7 + colIndex
                            val isWithinCycle = dayIndex < currentSequence.size
                            val isLastDayOfCycle = dayIndex == currentSequence.size - 1
                            val shiftId = if (isWithinCycle) currentSequence[dayIndex] else null
                            val shiftDef = shiftId?.let { ShiftRepository.getShiftDefinition(it) }
                            val code = if (isWithinCycle) {
                                shiftDef?.let { getCompactShiftCode(it.id, it.shortCode) } ?: "--"
                            } else "+"

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isWithinCycle) (shiftDef?.color ?: ObsidianSurfaceContainer)
                                        else ObsidianSurfaceContainer.copy(alpha = 0.45f)
                                    )
                                    .border(
                                        width = if (isLastDayOfCycle) 2.dp else if (isWithinCycle) 0.dp else 0.8.dp,
                                        color = if (isLastDayOfCycle) Color.White else if (isWithinCycle) ObsidianBorder else ObsidianBorderSubtle,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        if (isWithinCycle) {
                                            currentSequence[dayIndex] = selectedPaintShiftId
                                        } else {
                                            setDuration(dayIndex + 1)
                                            currentSequence[dayIndex] = selectedPaintShiftId
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = code,
                                    color = if (isWithinCycle) Color.White else TextMuted,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = if (isWithinCycle) 10.sp else 12.sp
                                )
                            }
                        }
                    }
                }

                // Tasto Aggiungi Settimana (fino a S10!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (visibleWeeks < 10) {
                        Surface(
                            onClick = { visibleWeeks = (visibleWeeks + 1).coerceAtMost(10) },
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianPrimary.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianPrimary.copy(alpha = 0.35f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Aggiungi Settimana (S${visibleWeeks + 1})",
                                    color = ObsidianPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (visibleWeeks > ((currentSequence.size + 6) / 7).coerceAtLeast(1) && visibleWeeks > 1) {
                        Surface(
                            onClick = { visibleWeeks = (visibleWeeks - 1).coerceAtLeast(1) },
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianSurfaceContainer,
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                            modifier = Modifier.weight(0.7f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "- Rimuovi S$visibleWeeks",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Palette dei turni per toccare e assegnare ai cerchietti: a tutta larghezza con scorrimento pulito
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Spacer(modifier = Modifier.width(14.dp))
                ShiftRepository.shiftDefinitions.forEach { def ->
                    val isSel = selectedPaintShiftId == def.id
                    val code = getCompactShiftCode(def.id, def.shortCode)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .bouncyClickable { selectedPaintShiftId = def.id }
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(def.color)
                                .border(
                                    width = if (isSel) 3.dp else 0.dp,
                                    color = if (isSel) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(11.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(code, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = def.name,
                            fontSize = 10.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSel) TextPrimary else TextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bottoni inferiori
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    val updated = rotation.copy(
                        name = title,
                        totalDays = currentSequence.size,
                        daySequence = currentSequence.toList()
                    )
                    onSave(updated)
                    onApply(updated)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimaryContainer),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Applica", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.5.sp, maxLines = 1, softWrap = false)
            }

            if (onDelete != null && ShiftRepository.rotations.size > 1) {
                OutlinedButton(
                    onClick = { onDelete(rotation.id) },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Elimina", modifier = Modifier.size(18.dp))
                }
            }

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(0.7f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
            ) {
                Text("Indietro", color = TextPrimary, fontSize = 13.5.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * Risolve l'icona appropriata in base al settore lavorativo / profilo.
 */
fun getWorkProfileIcon(iconKey: String): ImageVector {
    return when (iconKey) {
        "transit" -> Icons.Default.DirectionsTransit
        "hospital" -> Icons.Default.LocalHospital
        "factory" -> Icons.Default.PrecisionManufacturing
        "security" -> Icons.Default.Security
        "shipping" -> Icons.Default.LocalShipping
        "store" -> Icons.Default.Storefront
        else -> Icons.Default.Business
    }
}

/**
 * Scheda CCNL dettagliata: visualizzabile al volo con 1 tap ("apri e clicchi").
 * Contiene settore, parametri orari, percentuali maggiorazioni e regole riposo minimo obbligatorio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CcnlDetailsDialog(
    ccnl: CcnlDefinition,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianPrimary.copy(alpha = 0.2f))
                        .border(1.dp, ObsidianPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = ObsidianPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "SCHEDA CONTRATTUALE CCNL",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = ObsidianPrimary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = ccnl.shortLabel,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = ccnl.name,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 13.5.sp
                )
                Text(
                    text = ccnl.contractDescription,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                // Settore applicativo
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ObsidianSurfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Settore / Ambito", fontSize = 10.sp, color = TextMuted)
                            Text(ccnl.sector, fontSize = 11.5.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Regola Riposo Continuativo Minimo (Evidenziata in verde smeraldo)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Riposo Minimo Obbligatorio: ${ccnl.minRestHoursBetweenShifts} ore",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34D399)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = ccnl.restingRulesSummary,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Tabella Parametri Orari e Maggiorazioni
                Text("PARAMETRI E MAGGIORAZIONI CONTRATTUALI", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceContainer),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Orario medio settimanale:", color = TextMuted, fontSize = 12.sp)
                            Text("${ccnl.standardWeeklyHours} ore", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Divider(color = ObsidianBorderSubtle, thickness = 0.8.dp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Maggiorazione Notte (22-06):", color = TextMuted, fontSize = 12.sp)
                            Text("+${ccnl.nightSurchargePercent}%", color = ObsidianPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Divider(color = ObsidianBorderSubtle, thickness = 0.8.dp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Maggiorazione Festivo:", color = TextMuted, fontSize = 12.sp)
                            Text("+${ccnl.holidaySurchargePercent}%", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Divider(color = ObsidianBorderSubtle, thickness = 0.8.dp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Notte + Festivo:", color = TextMuted, fontSize = 12.sp)
                            Text("+${ccnl.nightHolidaySurchargePercent}%", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Divider(color = ObsidianBorderSubtle, thickness = 0.8.dp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Straordinario Diurno:", color = TextMuted, fontSize = 12.sp)
                            Text("+${ccnl.overtimeDaySurchargePercent}%", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Divider(color = ObsidianBorderSubtle, thickness = 0.8.dp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Straordinario Notturno:", color = TextMuted, fontSize = 12.sp)
                            Text("+${ccnl.overtimeNightSurchargePercent}%", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // Note Reperibilità / Pronta disponibilità
                if (ccnl.onCallNotes.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianSurfaceContainer,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Disciplina Reperibilità", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(ccnl.onCallNotes, fontSize = 11.5.sp, color = TextSecondary, lineHeight = 15.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Chiudi", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = ObsidianSurface,
        shape = RoundedCornerShape(22.dp)
    )
}

/**
 * Riquadro stepper per incrementare / decrementare valori numerici contrattuali.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberStepperRow(
    label: String,
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    note: String? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = ObsidianSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    fontSize = 12.5.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Surface(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .bouncyClickable { onMinus() },
                        color = ObsidianSurfaceHigh,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }

                    Text(
                        text = valueText,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        maxLines = 1,
                        modifier = Modifier.widthIn(min = 52.dp).padding(horizontal = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Surface(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .bouncyClickable { onPlus() },
                        color = ObsidianPrimaryContainer,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianPrimary)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
            if (note != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(note, fontSize = 10.sp, color = TextMuted, lineHeight = 13.sp)
            }
        }
    }
}

/**
 * Editor Completo a Schermo Intero per creare o modificare un Profilo Lavoro con CCNL legato.
 * Ampio, reattivo, ottimizzato per tutti i display (compresi 4K e schermi grandi).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkProfileEditorScreen(
    profile: WorkProfile,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (WorkProfile) -> Unit,
    onDelete: ((String) -> Unit)? = null,
    onConsultCcnl: (CcnlDefinition) -> Unit
) {
    BackHandler { onDismiss() }

    var name by remember { mutableStateOf(profile.name) }
    var department by remember { mutableStateOf(profile.companyOrDepartment) }
    var selectedCcnlCode by remember { mutableStateOf(profile.ccnlCode) }
    var weeklyHours by remember { mutableStateOf(profile.weeklyHours) }
    var minRestHours by remember { mutableStateOf(profile.minRestHoursBetweenShifts) }
    var nightSurcharge by remember { mutableStateOf(profile.nightSurchargePercent) }
    var holidaySurcharge by remember { mutableStateOf(profile.holidaySurchargePercent) }
    var nightHolidaySurcharge by remember { mutableStateOf(profile.nightHolidaySurchargePercent) }
    var overtimeDaySurcharge by remember { mutableStateOf(profile.overtimeDaySurchargePercent) }
    var overtimeNightSurcharge by remember { mutableStateOf(profile.overtimeNightSurchargePercent) }
    var isDefault by remember { mutableStateOf(profile.isDefault) }
    var selectedIconKey by remember { mutableStateOf(profile.iconKey) }
    var selectedColorHex by remember { mutableStateOf(profile.colorHex) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val currentCcnl = remember(selectedCcnlCode) { CcnlCatalog.findByCode(selectedCcnlCode) }
    val currentColor = Color(selectedColorHex)

    val iconsList = listOf(
        "transit" to "Ferrovia",
        "hospital" to "Sanità",
        "factory" to "Industria",
        "security" to "Sicurezza",
        "shipping" to "Logistica",
        "store" to "Commercio",
        "business" to "Ufficio"
    )

    fun doSave() {
        val finalName = if (name.isBlank()) currentCcnl.shortLabel else name.trim()
        val updated = profile.copy(
            name = finalName,
            companyOrDepartment = department.trim(),
            ccnlCode = selectedCcnlCode,
            ccnlName = currentCcnl.name,
            weeklyHours = weeklyHours,
            minRestHoursBetweenShifts = minRestHours,
            nightSurchargePercent = nightSurcharge,
            holidaySurchargePercent = holidaySurcharge,
            nightHolidaySurchargePercent = nightHolidaySurcharge,
            overtimeDaySurchargePercent = overtimeDaySurcharge,
            overtimeNightSurchargePercent = overtimeNightSurcharge,
            colorHex = selectedColorHex,
            iconKey = selectedIconKey,
            isDefault = isDefault
        )
        onSave(updated)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top App Bar a Schermo Intero
            Surface(
                color = ObsidianSurface.copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ObsidianSurfaceContainer)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Indietro", tint = TextPrimary)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isNew) "Aggiungi Secondo Lavoro" else "Modifica Profilo Lavoro",
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            fontSize = 17.5.sp
                        )
                        Text(
                            text = "Regole contrattuali, CCNL e riposo minimo",
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }

                    Button(
                        onClick = { doSave() },
                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimaryContainer),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isNew) "Crea" else "Salva", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }
                }
            }

            // Corpo Scrollabile e Centrato Reattivamente
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 740.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // HERO PREVIEW CARD
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = ObsidianSurfaceContainer
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, currentColor.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            currentColor.copy(alpha = 0.22f),
                                            ObsidianSurfaceContainer.copy(alpha = 0.95f)
                                        )
                                    )
                                )
                                .padding(18.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(currentColor.copy(alpha = 0.25f))
                                            .border(1.5.dp, currentColor.copy(alpha = 0.7f), RoundedCornerShape(16.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = getWorkProfileIcon(selectedIconKey),
                                            contentDescription = null,
                                            tint = currentColor,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = name.ifBlank { "Nome Lavoro / Azienda" },
                                            fontWeight = FontWeight.Black,
                                            color = TextPrimary,
                                            fontSize = 20.sp
                                        )
                                        Text(
                                            text = department.ifBlank { "Reparto / Settore aziendale" },
                                            color = TextSecondary,
                                            fontSize = 13.sp
                                        )
                                    }

                                    if (isDefault) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFF10B981).copy(alpha = 0.18f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
                                        ) {
                                            Text(
                                                text = "ATTIVO",
                                                color = Color(0xFF10B981),
                                                fontWeight = FontWeight.Black,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                // Badges riassuntivi a scorrimento orizzontale (nessun restringimento o testo verticale)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = ObsidianSurface,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
                                    ) {
                                        Text(
                                            text = currentCcnl.shortLabel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ObsidianPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = ObsidianSurface,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
                                    ) {
                                        Text(
                                            text = "$weeklyHours h/settimana",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextSecondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = ObsidianSurface,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
                                    ) {
                                        Text(
                                            text = "Stacco: $minRestHours h",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextSecondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // CARD 1: DATI AZIENDA / REPARTO
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceContainer),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "DATI GENERALI",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                letterSpacing = 1.sp
                            )

                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Nome Lavoro / Azienda (es. RFI, Ospedale, Poste)", color = TextSecondary) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = department,
                                onValueChange = { department = it },
                                label = { Text("Settore / Reparto (es. Circolazione, Pronto Soccorso)", color = TextSecondary) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true
                            )
                        }
                    }

                    // CARD 2: CCNL DI RIFERIMENTO
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceContainer),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "CCNL DI RIFERIMENTO (PRESET UFFICIALI)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                letterSpacing = 1.sp
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CcnlCatalog.presets.forEach { ccnl ->
                                    val isSelected = ccnl.code == selectedCcnlCode
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .bouncyClickable {
                                                selectedCcnlCode = ccnl.code
                                                weeklyHours = ccnl.standardWeeklyHours
                                                minRestHours = ccnl.minRestHoursBetweenShifts
                                                nightSurcharge = ccnl.nightSurchargePercent
                                                holidaySurcharge = ccnl.holidaySurchargePercent
                                                nightHolidaySurcharge = ccnl.nightHolidaySurchargePercent
                                                overtimeDaySurcharge = ccnl.overtimeDaySurchargePercent
                                                overtimeNightSurcharge = ccnl.overtimeNightSurchargePercent
                                                if (name.isBlank() && isNew) {
                                                    name = ccnl.shortLabel
                                                }
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) ObsidianPrimary.copy(alpha = 0.22f) else ObsidianSurface,
                                        border = androidx.compose.foundation.BorderStroke(
                                            if (isSelected) 1.5.dp else 1.dp,
                                            if (isSelected) ObsidianPrimary else ObsidianBorderSubtle
                                        )
                                    ) {
                                        Text(
                                            text = ccnl.shortLabel,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                            color = if (isSelected) ObsidianPrimary else TextSecondary,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                                        )
                                    }
                                }
                            }

                            // Tasto Scheda CCNL
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .bouncyClickable { onConsultCcnl(currentCcnl) },
                                shape = RoundedCornerShape(14.dp),
                                color = ObsidianPrimary.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianPrimary.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(
                                            Icons.Default.MenuBook,
                                            contentDescription = null,
                                            tint = ObsidianPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                "Scheda: ${currentCcnl.name}",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Text(
                                                "Tocca per consultare tutti i dettagli normativi e regole",
                                                fontSize = 10.5.sp,
                                                color = ObsidianPrimary
                                            )
                                        }
                                    }
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = ObsidianPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // CARD 3: ORARIO SETTIMANALE & RIPOSO MINIMO
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceContainer),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "ORARIO SETTIMANALE & RIPOSO MINIMO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                letterSpacing = 1.sp
                            )

                            NumberStepperRow(
                                label = "Orario Medio Settimanale",
                                valueText = "$weeklyHours ore",
                                onMinus = { if (weeklyHours > 20) weeklyHours -= 1 },
                                onPlus = { if (weeklyHours < 48) weeklyHours += 1 },
                                note = "Standard contrattuale CCNL: ${currentCcnl.standardWeeklyHours}h"
                            )

                            NumberStepperRow(
                                label = "Riposo Minimo Obbligatorio tra Turni",
                                valueText = "$minRestHours ore",
                                onMinus = { if (minRestHours > 8) minRestHours -= 1 },
                                onPlus = { if (minRestHours < 24) minRestHours += 1 },
                                note = "Raccomandato dal CCNL: ${currentCcnl.minRestHoursBetweenShifts}h. Verifica la conformità dello stacco sul calendario."
                            )
                        }
                    }

                    // CARD 4: MAGGIORAZIONI CONTRATTUALI (%)
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceContainer),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "MAGGIORAZIONI CONTRATTUALI (%)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                letterSpacing = 1.sp
                            )

                            NumberStepperRow(
                                label = "Maggiorazione Notturna (22-06)",
                                valueText = "+$nightSurcharge%",
                                onMinus = { if (nightSurcharge > 0) nightSurcharge -= 5 },
                                onPlus = { if (nightSurcharge < 100) nightSurcharge += 5 }
                            )

                            NumberStepperRow(
                                label = "Maggiorazione Festivo",
                                valueText = "+$holidaySurcharge%",
                                onMinus = { if (holidaySurcharge > 0) holidaySurcharge -= 5 },
                                onPlus = { if (holidaySurcharge < 120) holidaySurcharge += 5 }
                            )

                            NumberStepperRow(
                                label = "Notte + Festivo",
                                valueText = "+$nightHolidaySurcharge%",
                                onMinus = { if (nightHolidaySurcharge > 0) nightHolidaySurcharge -= 5 },
                                onPlus = { if (nightHolidaySurcharge < 150) nightHolidaySurcharge += 5 }
                            )

                            NumberStepperRow(
                                label = "Straordinario Diurno",
                                valueText = "+$overtimeDaySurcharge%",
                                onMinus = { if (overtimeDaySurcharge > 0) overtimeDaySurcharge -= 5 },
                                onPlus = { if (overtimeDaySurcharge < 100) overtimeDaySurcharge += 5 }
                            )

                            NumberStepperRow(
                                label = "Straordinario Notturno",
                                valueText = "+$overtimeNightSurcharge%",
                                onMinus = { if (overtimeNightSurcharge > 0) overtimeNightSurcharge -= 5 },
                                onPlus = { if (overtimeNightSurcharge < 120) overtimeNightSurcharge += 5 }
                            )
                        }
                    }

                    // CARD 5: ICONA & COLORE DISTINTIVO (CON COLOR PICKER PERSONALIZZATO!)
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceContainer),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                "ICONA DEL PROFILO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                letterSpacing = 1.sp
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                iconsList.forEach { (key, label) ->
                                    val isSel = selectedIconKey == key
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .bouncyClickable { selectedIconKey = key },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSel) ObsidianPrimaryContainer else ObsidianSurface,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSel) ObsidianPrimary else ObsidianBorderSubtle
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = getWorkProfileIcon(key),
                                                contentDescription = label,
                                                tint = if (isSel) Color.White else TextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                label,
                                                fontSize = 11.5.sp,
                                                color = if (isSel) Color.White else TextSecondary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                "COLORE IDENTIFICATIVO (TAVOLOZZA & PERSONALIZZATO)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                letterSpacing = 1.sp
                            )

                            // Universal Color Picker Row con l'ultimo badge a destra Personalizzato!
                            UniversalColorPickerRow(
                                selectedColorHex = selectedColorHex,
                                onColorSelected = { selectedColorHex = it }
                            )
                        }
                    }

                    // CARD 6: STATO LAVORO ATTIVO
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = ObsidianSurfaceContainer,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Imposta come Lavoro Attivo",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Usa le regole CCNL e il riposo minimo di questo lavoro sul calendario",
                                    fontSize = 11.5.sp,
                                    color = TextMuted
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = isDefault,
                                onCheckedChange = { isDefault = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF10B981)
                                )
                            )
                        }
                    }

                    // PULSANTI INFERIORI
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { doSave() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimaryContainer),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = if (isNew) "Aggiungi Profilo Lavoro" else "Salva Modifiche",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        if (!isNew && onDelete != null && ShiftRepository.workProfiles.size > 1) {
                            OutlinedButton(
                                onClick = { showDeleteConfirmDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFEF4444)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Elimina questo Profilo Lavoro", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }
    }

    if (showDeleteConfirmDialog && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Eliminare questo lavoro?", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text("Se elimini '${profile.name}', i turni assegnati non saranno cancellati ma non saranno più associati a questo profilo.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete(profile.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Elimina", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Annulla", color = TextSecondary)
                }
            },
            containerColor = ObsidianSurface,
            shape = RoundedCornerShape(22.dp)
        )
    }
}


