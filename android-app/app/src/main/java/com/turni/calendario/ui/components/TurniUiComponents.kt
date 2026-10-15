package com.turni.calendario.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.model.AssignedShift
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.model.ShiftCategory
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.theme.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pennello di Rifrazione Cromatica e Dispersione Prismatica
 * Simula la dispersione fisica della luce nel cristallo satinato:
 * ingresso ciano brillante, cresta speculare bianca, dispersione violetto e uscita magenta.
 */
fun prismaticRefractionBrush(
    highlightColor: Color = Color(0xFF38BDF8),
    dispersionExit: Color = Color(0xFFEC4899)
): Brush {
    return Brush.linearGradient(
        listOf(
            highlightColor.copy(alpha = 0.95f),
            Color(0xEEFFFFFF),
            Color(0x80818CF8),
            Color(0x25FFFFFF),
            dispersionExit.copy(alpha = 0.65f)
        ),
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(1000f, 500f)
    )
}

/**
 * Versione memorizzata del pennello di rifrazione per evitare riallocazioni
 */
@Composable
fun rememberPrismaticRefractionBrush(
    highlightColor: Color = Color(0xFF38BDF8),
    dispersionExit: Color = Color(0xFFEC4899)
): Brush {
    return remember(highlightColor, dispersionExit) {
        prismaticRefractionBrush(highlightColor, dispersionExit)
    }
}

/**
 * Modificatore di Feedback Tattile Elastico
 * Combina micro-compressione elastica (0.92f), vibrazione aptica istantanea
 * e rimbalzo morbido progressivo ad alte prestazioni.
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.92f else 1.0f,
        animationSpec = remember {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        }
    )

    this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = {
                try {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                } catch (_: Exception) {}
                onClick()
            }
        )
}

/**
 * Contenitore Fluid Glass con Rifrazione Ottica
 * Include:
 * - Bordo prismatico a rifrazione cromatica multi-strato
 * - Corpo in cristallo traslucido calibrato per OLED scuro
 * - Caustiche interne di diffusione e cresta speculare ultralucida
 * - Shader e gradienti memorizzati per azzerare il carico di ricomposizione
 */
@Composable
fun FluidGlassContainer(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(22.dp),
    shadowElevation: androidx.compose.ui.unit.Dp = 18.dp,
    highlightColor: Color = Color(0xFF38BDF8),
    content: @Composable BoxScope.() -> Unit
) {
    val refractionBrush = rememberPrismaticRefractionBrush(highlightColor)
    val bodyGradient = remember {
        Brush.verticalGradient(
            listOf(
                Color(0xF6111B2C), // Cristallo satinato opaco scuro
                Color(0xFA0A0E18)  // Fondo ossidiana profondo
            )
        )
    }
    val causticGradient = remember(highlightColor) {
        Brush.radialGradient(
            listOf(
                highlightColor.copy(alpha = 0.22f),
                Color.Transparent
            ),
            radius = 360f
        )
    }
    val specularGradient = remember(highlightColor) {
        Brush.horizontalGradient(
            listOf(
                Color.Transparent,
                highlightColor.copy(alpha = 0.90f),
                Color(0xF5FFFFFF),
                Color(0xFFEC4899).copy(alpha = 0.80f),
                Color.Transparent
            )
        )
    }

    Surface(
        modifier = modifier
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                spotColor = highlightColor.copy(alpha = 0.40f),
                ambientColor = Color(0xFFEC4899).copy(alpha = 0.18f)
            )
            .clip(shape)
            .border(
                width = 1.3.dp,
                brush = refractionBrush,
                shape = shape
            ),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bodyGradient)
        ) {
            // Caustica interna di diffusione luce
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(causticGradient)
            )

            // Velo di dispersione cromatica diagonale
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0x14FFFFFF),
                                Color.Transparent,
                                highlightColor.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(600f, 400f)
                        )
                    )
            )

            // Cresta speculare orizzontale in cima
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.3.dp)
                    .background(specularGradient)
            )

            content()
        }
    }
}

/**
 * Modello e HUD per Feedback Azioni Utente
 */
data class ActionFeedback(
    val message: String,
    val isDestructive: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

private data class PerimeterParticle(
    val edge: Int,           // 0: Top, 1: Bottom, 2: Left, 3: Right, 4: Top-Left, 5: Top-Right, 6: Bottom-Left, 7: Bottom-Right
    val edgeRatio: Float,    // 0f..1f along the edge
    val burstDx: Float,      // outward delta X in dp
    val burstDy: Float,      // outward delta Y in dp
    val sizeDp: Float,       // size in dp
    val color: Color,
    val isDiamond: Boolean,  // variety: sparkling diamond or glowing circle
    val rotationSpeed: Float // degrees to rotate over progress
)

@Composable
fun ParticleBurstEffect(
    triggerKey: Long,
    isDestructive: Boolean,
    modifier: Modifier = Modifier
) {
    val animProgress = remember(triggerKey) { androidx.compose.animation.core.Animatable(0f) }

    val particles = remember(triggerKey) {
        val random = java.util.Random(triggerKey)
        val palette = if (isDestructive) {
            listOf(
                Color(0xFFEF4444), // Fiery red
                Color(0xFFF43F5E), // Rose red
                Color(0xFFFB923C), // Flame orange
                Color(0xFFF97316), // Vivid orange
                Color(0xFFFDE047), // Ember gold
                Color(0xFFFFFFFF)  // Spark white
            )
        } else {
            listOf(
                Color(0xFF10B981), // Emerald
                Color(0xFF34D399), // Mint green
                Color(0xFF38BDF8), // Cyan sky
                Color(0xFFFBBF24), // Gold amber
                Color(0xFFA855F7), // Neon purple
                Color(0xFFFFFFFF)  // Diamond white
            )
        }

        List(64) { index ->
            val edge = when {
                index < 22 -> 0  // Bordo superiore (top)
                index < 44 -> 1  // Bordo inferiore (bottom)
                index < 50 -> 2  // Bordo sinistro (left)
                index < 56 -> 3  // Bordo destro (right)
                else -> 4 + (index - 56) // 8 angoli / splay diagonali
            }
            val edgeRatio = random.nextFloat()
            val spreadX = (random.nextFloat() - 0.5f) * 36f
            val spreadY = (random.nextFloat() - 0.5f) * 36f

            // Vettori di velocità strettamente diretti all'ESTERNO della card (nessuna particella verso il centro)
            val (dx, dy) = when (edge) {
                0 -> Pair(spreadX, -(35f + random.nextFloat() * 65f))       // Top: eruzione verso l'alto
                1 -> Pair(spreadX, +(35f + random.nextFloat() * 65f))       // Bottom: eruzione verso il basso
                2 -> Pair(-(40f + random.nextFloat() * 70f), spreadY)       // Left: eruzione verso sinistra
                3 -> Pair(+(40f + random.nextFloat() * 70f), spreadY)       // Right: eruzione verso destra
                4, 5 -> Pair(-(35f + random.nextFloat() * 45f), -(35f + random.nextFloat() * 45f)) // Angolo Top-Left
                6, 7 -> Pair(+(35f + random.nextFloat() * 45f), -(35f + random.nextFloat() * 45f)) // Angolo Top-Right
                8, 9 -> Pair(-(35f + random.nextFloat() * 45f), +(35f + random.nextFloat() * 45f)) // Angolo Bottom-Left
                else -> Pair(+(35f + random.nextFloat() * 45f), +(35f + random.nextFloat() * 45f)) // Angolo Bottom-Right
            }

            val size = 2.6f + random.nextFloat() * 4.2f
            val color = palette[random.nextInt(palette.size)]
            val isDiamond = random.nextBoolean()
            val rotSpeed = (random.nextFloat() - 0.5f) * 620f

            PerimeterParticle(
                edge = edge,
                edgeRatio = edgeRatio,
                burstDx = dx,
                burstDy = dy,
                sizeDp = size,
                color = color,
                isDiamond = isDiamond,
                rotationSpeed = rotSpeed
            )
        }
    }

    LaunchedEffect(triggerKey) {
        animProgress.snapTo(0f)
        // Ritardo di assestamento: attende che il pop-up scenda dall'alto prima di far eruttare le particelle
        kotlinx.coroutines.delay(240L)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 950,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            )
        )
    }

    val progress = animProgress.value
    if (progress in 0.001f..0.999f) {
        androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val alpha = (1f - progress).coerceIn(0f, 1f)

            particles.forEach { p ->
                val startX = when (p.edge) {
                    0, 1 -> p.edgeRatio * w
                    2 -> 0f
                    3 -> w
                    4, 5, 8, 9 -> 0f
                    else -> w
                }
                val startY = when (p.edge) {
                    0 -> 0f
                    1 -> h
                    2, 3 -> p.edgeRatio * h
                    4, 5, 6, 7 -> 0f
                    else -> h
                }

                val currentX = startX + (p.burstDx * progress * density)
                val currentY = startY + (p.burstDy * progress * density)
                val particleRadius = (p.sizeDp * (1f - progress * 0.35f) * density).coerceAtLeast(1.2f)

                // Glow halo morbido attorno a ciascuna particella per brillantezza visiva
                drawCircle(
                    color = p.color.copy(alpha = alpha * 0.35f),
                    radius = particleRadius * 2.2f,
                    center = androidx.compose.ui.geometry.Offset(currentX, currentY)
                )

                if (p.isDiamond) {
                    // Sparkling diamond / stella rotante
                    val half = particleRadius
                    val diamondPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(currentX, currentY - half)
                        lineTo(currentX + half, currentY)
                        lineTo(currentX, currentY + half)
                        lineTo(currentX - half, currentY)
                        close()
                    }
                    drawPath(path = diamondPath, color = p.color.copy(alpha = alpha))
                } else {
                    // Favilla circolare splendente
                    drawCircle(
                        color = p.color.copy(alpha = alpha),
                        radius = particleRadius,
                        center = androidx.compose.ui.geometry.Offset(currentX, currentY)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MagicalActionFeedbackHUD(
    feedback: ActionFeedback?,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LaunchedEffect(feedback?.timestamp) {
        if (feedback != null) {
            kotlinx.coroutines.delay(2800)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = feedback != null,
        enter = slideInVertically(
            initialOffsetY = { -it - 80 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + scaleIn(
            initialScale = 0.85f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(animationSpec = tween(180)),
        exit = slideOutVertically(
            targetOffsetY = { -it - 80 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + scaleOut(
            targetScale = 0.85f,
            animationSpec = tween(220)
        ) + fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        if (feedback != null) {
            val isDestructive = feedback.isDestructive
            val primaryGlow = if (isDestructive) Color(0xFFEF4444) else Color(0xFF10B981)
            val secondaryGlow = if (isDestructive) Color(0xFFF43F5E) else Color(0xFF38BDF8)
            val containerBg = if (isDestructive) Color(0xF22A0A10) else Color(0xF2062418)

            val shakeAnim = remember(feedback.timestamp) { androidx.compose.animation.core.Animatable(0f) }

            LaunchedEffect(feedback.timestamp) {
                if (isDestructive) {
                    shakeAnim.snapTo(0f)
                    shakeAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = androidx.compose.animation.core.keyframes {
                            durationMillis = 350
                            0.0f at 0
                            -9.0f at 50
                            8.0f at 100
                            -5.0f at 160
                            4.0f at 220
                            -2.0f at 280
                            0.0f at 350
                        }
                    )
                }
            }

            val shakeOffset = if (isDestructive) shakeAnim.value else 0f

            Box(
                modifier = Modifier
                    .offset(x = shakeOffset.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Pillola compatta ed elegante: definisce la dimensione naturale stabile
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = containerBg,
                    border = androidx.compose.foundation.BorderStroke(
                        1.2.dp,
                        Brush.horizontalGradient(
                            listOf(primaryGlow, Color.White.copy(alpha = 0.85f), secondaryGlow)
                        )
                    ),
                    shadowElevation = 14.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(primaryGlow.copy(alpha = 0.28f))
                                .border(1.dp, primaryGlow.copy(alpha = 0.85f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isDestructive) Icons.Default.DeleteForever else Icons.Default.Check,
                                contentDescription = null,
                                tint = if (isDestructive) Color(0xFFFCA5A5) else Color(0xFFA7F3D0),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = feedback.message,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Coriandoli / Particelle scintillanti in overlay centrato: NON alterano mai la posizione né l'altezza
                ParticleBurstEffect(
                    triggerKey = feedback.timestamp,
                    isDestructive = isDestructive,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
    }
}

/**
 * Pulsante Trasferta con animazione di decollo dell'aereo confinata all'interno del controllo
 */
@Composable
fun ShiftPlaneFlightButton(
    isTrasferta: Boolean,
    onToggle: () -> Unit
) {
    val planeOffsetX = remember { Animatable(0f) }
    val planeOffsetY = remember { Animatable(0f) }
    val planeScale = remember { Animatable(1f) }
    val planeAlpha = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isTrasferta) Color(0xFF10B981).copy(alpha = 0.25f) else ObsidianSurfaceHigh,
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            if (isTrasferta) Color(0xFF10B981) else ObsidianBorderSubtle
        ),
        modifier = Modifier.bouncyClickable {
            coroutineScope.launch {
                launch {
                    planeOffsetX.animateTo(22f, tween(140, easing = FastOutLinearInEasing))
                    planeOffsetX.snapTo(-14f)
                    planeOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
                launch {
                    planeOffsetY.animateTo(-16f, tween(140, easing = FastOutLinearInEasing))
                    planeOffsetY.snapTo(10f)
                    planeOffsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
                launch {
                    planeScale.animateTo(1.2f, tween(70))
                    planeScale.animateTo(0.3f, tween(70))
                    planeScale.snapTo(0.6f)
                    planeScale.animateTo(1.0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
                launch {
                    planeAlpha.animateTo(0f, tween(130))
                    planeAlpha.snapTo(0f)
                    planeAlpha.animateTo(1f, tween(120))
                }
            }
            onToggle()
        }
    ) {
        Box(
            modifier = Modifier.size(34.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.FlightTakeoff,
                contentDescription = "Trasferta",
                tint = if (isTrasferta) Color(0xFF34D399) else TextMuted,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer {
                        translationX = planeOffsetX.value
                        translationY = planeOffsetY.value
                        scaleX = planeScale.value
                        scaleY = planeScale.value
                        alpha = planeAlpha.value
                    }
            )
        }
    }
}

/**
 * Scheda fluida e dinamica di ispezione del giorno selezionato (Breccia Ergonomics + Material 3 Expressive).
 * Supporta turni multipli con visualizzazione uniforme, trasferta indipendente per ciascun turno,
 * reperibilità giornaliera e accesso compatto a orari, riposo contrattuale e straordinari.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayInspectionCard(
    date: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onOpenEdit: () -> Unit,
    onDeleteShift: ((String) -> Unit)? = null,
    onAddShift: (() -> Unit)? = null,
    onAddSecondary: (() -> Unit)? = onAddShift,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shifts = ShiftRepository.getShiftsForDate(date)
    val primaryShift = shifts.firstOrNull()
    val primaryDef = primaryShift?.let { ShiftRepository.getShiftDefinition(it.shiftDefId) }

    val prevDate = remember(date) { date.minusDays(1) }
    val prevShifts = ShiftRepository.getShiftsForDate(prevDate)
    val prevShiftDef = prevShifts.firstOrNull()?.let { ShiftRepository.getShiftDefinition(it.shiftDefId) }

    val restInfo = remember(date, shifts, prevShifts) {
        calculateRestHours(prevDate, prevShiftDef, date, primaryDef)
    }

    val dayExtra = ShiftRepository.getDayExtraInfo(date)
    val isOnCall = ShiftRepository.isOnCall(date)

    val bellRotation = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    var showOvertimeDialog by remember { mutableStateOf(false) }
    var overtimeTargetShift by remember { mutableStateOf<AssignedShift?>(null) }
    var showNoteDialog by remember { mutableStateOf(false) }

    val updateOvertime: (Float) -> Unit = { newHours ->
        ShiftRepository.setDayOvertime(date, newHours)
        if (shifts.isNotEmpty()) {
            ShiftRepository.setShiftOvertime(date, shifts.first().id, newHours)
        }
    }

    val effectiveAddShift = onAddShift ?: onAddSecondary ?: onOpenEdit

    FluidGlassContainer(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {},
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 20.dp,
        highlightColor = primaryDef?.color ?: ObsidianPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp)
        ) {
            // RIGA 1: HEADER (Data + Navigazione a sinistra | Tasto X FISSO e INDIPENDENTE a destra)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Surface(
                        onClick = onPreviousDay,
                        shape = CircleShape,
                        color = ObsidianSurfaceHigh.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.ChevronLeft,
                                contentDescription = "Giorno precedente",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    val dayName = remember(date) { date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).uppercase() }
                    val isToday = remember(date) { date == LocalDate.now() }
                    Text(
                        text = "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).uppercase()} • $dayName",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    if (isToday) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ObsidianPrimary
                        ) {
                            Text(
                                text = "OGGI",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Black,
                                color = ObsidianBackground,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = onNextDay,
                        shape = CircleShape,
                        color = ObsidianSurfaceHigh.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Giorno successivo",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Tasto Chiudi Fisso a Destra (Mai spinto fuori o compresso)
                Surface(
                    onClick = onClose,
                    shape = CircleShape,
                    color = ObsidianSurfaceHigh.copy(alpha = 0.9f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Chiudi",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // BANNER DEDICATO RIPOSO CONTRATTUALE CCNL (se presente, occupa tutta la larghezza sotto l'header)
            if (restInfo != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = restInfo.containerColor,
                    border = androidx.compose.foundation.BorderStroke(1.dp, restInfo.borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = restInfo.icon,
                                contentDescription = null,
                                tint = restInfo.textColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Riposo contrattuale CCNL:",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = restInfo.textColor
                            )
                        }
                        Text(
                            text = restInfo.hoursFormatted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = restInfo.textColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // RIGA 2: CONTENUTO TURNI
            // Ciascun turno possiede il proprio indicatore di Straordinario (+/- e stacco effettivo), Trasferta (aereo) e Cestino!
            if (shifts.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    shifts.forEach { shift ->
                        val def = ShiftRepository.getShiftDefinition(shift.shiftDefId)
                        if (def != null) {
                            val badgeCode = when {
                                def.shortCode.isNotBlank() && def.shortCode.length <= 4 -> def.shortCode.uppercase()
                                else -> def.name.take(3).uppercase()
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(ObsidianSurfaceContainer.copy(alpha = 0.65f))
                                    .border(1.dp, ObsidianBorderSubtle, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(def.color),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = badgeCode,
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f, fill = false)) {
                                        Text(
                                            text = def.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        val timeText = if (def.isAllDay) "Tutto il giorno" else "${def.timeRangeFormatted} • ${def.durationFormatted}"
                                        Text(
                                            text = timeText,
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        if (shift.overtimeHours > 0f || shift.actualEndTime != null) {
                                            val otInfo = if (shift.actualEndTime != null) {
                                                "⏱️ Stacco ${shift.actualEndTime} (+${String.format(Locale.US, "%.2f", shift.overtimeHours).trimEnd('0').trimEnd('.')}h)"
                                            } else {
                                                "⏱️ Straord: +${String.format(Locale.US, "%.2f", shift.overtimeHours).trimEnd('0').trimEnd('.')}h"
                                            }
                                            Text(
                                                text = otInfo,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ObsidianPrimary
                                            )
                                        }
                                    }
                                }

                                // Lato Destro: Straordinario + Trasferta + Cestino per QUESTO specifico turno
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // 1. Tasto Straordinario / Stacco per questo turno
                                    val hasOt = shift.overtimeHours > 0f
                                    Surface(
                                        shape = RoundedCornerShape(9.dp),
                                        color = if (hasOt) ObsidianPrimary.copy(alpha = 0.22f) else ObsidianSurfaceHigh,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (hasOt) ObsidianPrimary else ObsidianBorderSubtle
                                        ),
                                        modifier = Modifier.bouncyClickable {
                                            overtimeTargetShift = shift
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Schedule,
                                                contentDescription = "Straordinario e Stacco",
                                                tint = if (hasOt) ObsidianPrimary else TextMuted,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = if (hasOt) "+${String.format(Locale.US, "%.2f", shift.overtimeHours).trimEnd('0').trimEnd('.')}h" else "+Str",
                                                fontSize = 11.sp,
                                                fontWeight = if (hasOt) FontWeight.ExtraBold else FontWeight.Medium,
                                                color = if (hasOt) ObsidianPrimary else TextSecondary
                                            )
                                        }
                                    }

                                    // 2. Tasto Trasferta per questo turno
                                    ShiftPlaneFlightButton(
                                        isTrasferta = shift.isTrasferta,
                                        onToggle = {
                                            ShiftRepository.toggleShiftTrasferta(date, shift.id)
                                        }
                                    )

                                    // 3. Cestino elimina per questo turno
                                    if (onDeleteShift != null) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFEF4444).copy(alpha = 0.18f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                                            modifier = Modifier.bouncyClickable { onDeleteShift(shift.id) }
                                        ) {
                                            Box(
                                                modifier = Modifier.size(32.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Rimuovi questo turno",
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianSurfaceContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Nessun turno inserito per oggi",
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            // Indicatore / Tasto Ex-Turno in grigio discreto (esiste SOLO se il turno originario di rotazione è stato sovrascritto)
            val origShiftId = primaryShift?.originalShiftDefId
            val hasExShift = primaryShift != null && primaryShift.isModifiedFromRotation && origShiftId != null && origShiftId != primaryShift.shiftDefId
            val exDef = if (hasExShift) ShiftRepository.getShiftDefinition(origShiftId!!) else null
            if (exDef != null && !dayExtra.hideExShift && ShiftRepository.appPreferences.showExShifts) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E2638),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.bouncyClickable {
                            ShiftRepository.setDayHideExShift(date, true)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ex turno: ${exDef.name}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Nascondi etichetta",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // RIGA 3: REPERIBILITÀ DEL GIORNO (Unica barra elegante senza sminchiamenti di layout)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isOnCall) Color(0xFF78350F).copy(alpha = 0.85f) else ObsidianSurfaceHigh,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isOnCall) Color(0xFFF59E0B) else ObsidianBorderSubtle
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .height(42.dp)
                    .bouncyClickable {
                        coroutineScope.launch {
                            bellRotation.snapTo(0f)
                            bellRotation.animateTo(-24f, tween(40, easing = LinearEasing))
                            bellRotation.animateTo(22f, tween(45, easing = LinearEasing))
                            bellRotation.animateTo(0f, tween(60, easing = FastOutSlowInEasing))
                        }
                        val currentManual = dayExtra.isOnCallManual
                        val newManual = if (currentManual == null) !isOnCall else !currentManual
                        ShiftRepository.setDayOnCallManual(date, newManual)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = if (isOnCall) Color(0xFFFBBF24) else TextMuted,
                            modifier = Modifier
                                .size(17.dp)
                                .graphicsLayer {
                                    rotationZ = bellRotation.value
                                    transformOrigin = TransformOrigin(0.5f, 0.1f)
                                }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reperibilità del Giorno",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isOnCall) Color(0xFFFBBF24) else TextPrimary
                        )
                    }
                    Text(
                        text = if (isOnCall) "ATTIVA" else "NON ATTIVA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isOnCall) Color(0xFFFBBF24) else TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // RIGA 4: SEZIONE NOTE & TAG DEL GIORNO (Formazione, Condotta, Visite...)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                shape = RoundedCornerShape(12.dp),
                color = ObsidianSurfaceContainer.copy(alpha = 0.55f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (dayExtra.notes.isNotBlank()) Color(0xFF38BDF8).copy(alpha = 0.45f) else ObsidianBorderSubtle
                )
            ) {
                if (dayExtra.notes.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 11.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = dayExtra.notes,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(ObsidianSurfaceHigh)
                                    .bouncyClickable { showNoteDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Modifica nota",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.18f))
                                    .bouncyClickable { ShiftRepository.setDayNotes(date, "") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Elimina nota",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bouncyClickable { showNoteDialog = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddComment,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8).copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "+ Note o tag del giorno (Formazione, Condotta...)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // RIGA 5: TASTI AZIONE PRINCIPALI: Cambia Turno / + Altro Turno
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (shifts.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianPrimary.copy(alpha = 0.22f),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, ObsidianPrimary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .bouncyClickable(onClick = onOpenEdit)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = ObsidianPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Aggiungi Turno",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ObsidianPrimary
                            )
                        }
                    }
                } else {
                    // Tasto Cambia Turno (Sostituisci)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianPrimary.copy(alpha = 0.22f),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, ObsidianPrimary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .bouncyClickable(onClick = onOpenEdit)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = ObsidianPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Cambia Turno",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = ObsidianPrimary,
                                maxLines = 1
                            )
                        }
                    }

                    // Tasto Aggiungi Altro Turno (Aggiunge un altro turno in parallelo allo stesso giorno)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianSurfaceHigh,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .bouncyClickable(onClick = effectiveAddShift)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircleOutline,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Altro Turno",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog Completo Straordinario & Orario di Stacco Effettivo per singolo turno
    if (overtimeTargetShift != null) {
        val targetShift = overtimeTargetShift!!
        val targetDef = ShiftRepository.getShiftDefinition(targetShift.shiftDefId)
        OvertimeAndEndTimeDialog(
            date = date,
            shiftDef = targetDef,
            currentActualEndTime = targetShift.actualEndTime ?: dayExtra.actualEndTime,
            currentOvertimeHours = if (targetShift.overtimeHours > 0f) targetShift.overtimeHours else dayExtra.overtimeHours,
            onDismiss = { overtimeTargetShift = null },
            onConfirm = { newEnd, newOvertime ->
                ShiftRepository.setShiftActualEndTime(date, targetShift.id, newEnd, newOvertime)
                overtimeTargetShift = null
            }
        )
    }

    // Dialog Straordinario per il giorno (fallback o giorno senza turni)
    if (showOvertimeDialog) {
        OvertimeAndEndTimeDialog(
            date = date,
            shiftDef = primaryDef,
            currentActualEndTime = dayExtra.actualEndTime,
            currentOvertimeHours = dayExtra.overtimeHours,
            onDismiss = { showOvertimeDialog = false },
            onConfirm = { newEnd, newOvertime ->
                ShiftRepository.setDayActualEndTime(date, newEnd, newOvertime)
                if (shifts.isNotEmpty()) {
                    ShiftRepository.setShiftActualEndTime(date, shifts.first().id, newEnd, newOvertime)
                }
                showOvertimeDialog = false
            }
        )
    }

    // Dialog Note del Giorno
    if (showNoteDialog) {
        DayNoteDialog(
            initialNote = dayExtra.notes,
            onDismiss = { showNoteDialog = false },
            onSave = { newNote ->
                ShiftRepository.setDayNotes(date, newNote)
                showNoteDialog = false
            }
        )
    }
}

/**
 * Dialog unificato per gestione Straordinario:
 * Tab 1: Orario di Stacco / Fine Effettivo (con calcolo automatico rispetto alla fine prevista del turno).
 * Tab 2: Ore Manuali e Preset rapidi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OvertimeAndEndTimeDialog(
    date: LocalDate,
    shiftDef: ShiftDefinition?,
    currentActualEndTime: String?,
    currentOvertimeHours: Float,
    onDismiss: () -> Unit,
    onConfirm: (actualEndTime: String?, overtimeHours: Float) -> Unit
) {
    val hasShiftTime = shiftDef != null && !shiftDef.isAllDay && shiftDef.endTime.isNotBlank()
    var selectedTab by remember { mutableStateOf(if (hasShiftTime) 0 else 1) }

    // State per Tab 1 (Orario di Stacco)
    val baseEndTime = if (hasShiftTime) shiftDef!!.endTime else "21:36"
    var inputEndTime by remember { mutableStateOf(currentActualEndTime ?: baseEndTime) }
    val calculatedOvertime = remember(inputEndTime, baseEndTime) {
        ShiftRepository.calculateOvertimeFromEndTime(baseEndTime, inputEndTime)
    }
    val diffMinutes = remember(calculatedOvertime) {
        (calculatedOvertime * 60).roundToInt()
    }

    // State per Tab 2 (Ore Manuali)
    var manualHoursText by remember {
        mutableStateOf(if (currentOvertimeHours > 0f) String.format(Locale.US, "%.2f", currentOvertimeHours).trimEnd('0').trimEnd('.') else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Straordinario & Stacco", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Color.White)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Selettore Tab (Orario Stacco vs Ore Manuali)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianSurfaceHigh)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .bouncyClickable { selectedTab = 0 },
                        shape = RoundedCornerShape(9.dp),
                        color = if (selectedTab == 0) ObsidianPrimary else Color.Transparent
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "⏱️ Orario Stacco",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 0) ObsidianBackground else TextSecondary
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .bouncyClickable { selectedTab = 1 },
                        shape = RoundedCornerShape(9.dp),
                        color = if (selectedTab == 1) ObsidianPrimary else Color.Transparent
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "✍️ Ore Manuali",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 1) ObsidianBackground else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedTab == 0) {
                    // TAB 1: Orario Stacco con calcolo automatico
                    if (shiftDef != null && !shiftDef.isAllDay) {
                        Text(
                            text = "Turno ${shiftDef.name}: fine programmata ore ${shiftDef.endTime}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            text = "Inserisci l'orario effettivo in cui hai staccato dal lavoro",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = inputEndTime,
                        onValueChange = { inputEndTime = it.filter { c -> c.isDigit() || c == ':' }.take(5) },
                        label = { Text("Orario di Stacco Effettivo (HH:mm)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("INCREMENTI RAPIDI DA FINE PREVISTA", color = TextMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(15, 30, 45, 60, 90, 120).forEach { mins ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ObsidianSurfaceHigh,
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, ObsidianBorderSubtle),
                                modifier = Modifier
                                    .weight(1f)
                                    .bouncyClickable {
                                        try {
                                            val base = LocalTime.parse(baseEndTime)
                                            val target = base.plusMinutes(mins.toLong())
                                            inputEndTime = String.format("%02d:%02d", target.hour, target.minute)
                                        } catch (_: Exception) {}
                                    }
                            ) {
                                Text(
                                    text = "+${mins}m",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianPrimary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (diffMinutes > 0) ObsidianPrimary.copy(alpha = 0.15f) else ObsidianSurfaceContainer,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (diffMinutes > 0) ObsidianPrimary.copy(alpha = 0.4f) else ObsidianBorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (diffMinutes > 0) "Straordinario Calcolato:" else "Nessuno straordinario",
                                fontSize = 11.5.sp,
                                color = if (diffMinutes > 0) TextPrimary else TextMuted
                            )
                            Text(
                                text = if (diffMinutes > 0) "+$diffMinutes min (+${String.format(Locale.US, "%.2f", calculatedOvertime).trimEnd('0').trimEnd('.')}h)" else "0 min",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = if (diffMinutes > 0) ObsidianPrimary else TextMuted
                            )
                        }
                    }
                } else {
                    // TAB 2: Inserimento Manuale Ore & Stepper
                    Text(
                        text = "Scrivi a mano le ore di straordinario o usa i preset rapidi",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = manualHoursText,
                        onValueChange = { input ->
                            val sanitized = input.replace(',', '.').filter { it.isDigit() || it == '.' }
                            if (sanitized.count { it == '.' } <= 1) {
                                manualHoursText = sanitized.take(5)
                            }
                        },
                        label = { Text("Ore Straordinario (es. 1.5)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("REGOLAZIONE RAPIDA", color = TextMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(-1.0f, -0.25f, 0.25f, 1.0f).forEach { step ->
                            val isNeg = step < 0f
                            val label = if (step > 0f) "+${step.toString().replace(".0", "")}h" else "${step.toString().replace(".0", "")}h"
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ObsidianSurfaceHigh,
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, ObsidianBorderSubtle),
                                modifier = Modifier
                                    .weight(1f)
                                    .bouncyClickable {
                                        val cur = manualHoursText.toFloatOrNull() ?: 0f
                                        val next = (cur + step).coerceAtLeast(0f)
                                        manualHoursText = if (next == 0f) "" else if (next % 1f == 0f) next.toInt().toString() else String.format(Locale.US, "%.2f", next).trimEnd('0').trimEnd('.')
                                    }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isNeg) Color(0xFFEF4444) else ObsidianPrimary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("PRESET RAPIDI", color = TextMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f, 3.0f).forEach { h ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ObsidianSurfaceHigh,
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, ObsidianBorderSubtle),
                                modifier = Modifier
                                    .weight(1f)
                                    .bouncyClickable {
                                        manualHoursText = if (h % 1f == 0f) h.toInt().toString() else String.format(Locale.US, "%.2f", h).trimEnd('0').trimEnd('.')
                                    }
                            ) {
                                Text(
                                    text = "+${if (h % 1f == 0f) h.toInt().toString() else String.format(Locale.US, "%.2f", h).trimEnd('0').trimEnd('.')}h",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianPrimary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedTab == 0) {
                        onConfirm(inputEndTime, calculatedOvertime)
                    } else {
                        val parsed = manualHoursText.toFloatOrNull() ?: 0f
                        onConfirm(null, parsed.coerceAtLeast(0f))
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary)
            ) {
                Text("Applica", color = ObsidianBackground, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onConfirm(null, 0f)
                }
            ) {
                Text("Azzera", color = TextSecondary)
            }
        },
        containerColor = ObsidianSurface,
        shape = RoundedCornerShape(20.dp)
    )
}

/**
 * Dialog per inserimento e modifica delle note del giorno
 */
@Composable
fun DayNoteDialog(
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var noteText by remember { mutableStateOf(initialNote) }
    val quickTags = remember {
        listOf(
            "🎓 Formazione",
            "🚆 Condotta / Guida",
            "🏢 Visita Medica",
            "💻 Smart Working",
            "⚠️ Ritardo / Anomalia",
            "🔄 Cambio Turno"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EditNote, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Nota & Tag del Giorno", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Color.White)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Aggiungi un appunto o tocca un tag rapido per inserirlo al volo:",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Riga di tag rapidi a 1 tocco
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickTags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ObsidianSurfaceHigh,
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                            modifier = Modifier.bouncyClickable {
                                noteText = if (noteText.isBlank()) {
                                    tag
                                } else if (!noteText.contains(tag)) {
                                    "$noteText • $tag"
                                } else {
                                    noteText
                                }
                            }
                        ) {
                            Text(
                                text = tag,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { input: String -> noteText = input.take(200) },
                    label = { Text("Testo della nota o tag") },
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(noteText.trim())
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
            ) {
                Text("Salva", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla", color = TextSecondary)
            }
        },
        containerColor = ObsidianSurface,
        shape = RoundedCornerShape(20.dp)
    )
}

/**
 * Modello informativo per la verifica dei riposi
 */
data class RestEvaluation(
    val label: String,
    val hoursFormatted: String,
    val isCompliant: Boolean,
    val containerColor: Color,
    val borderColor: Color,
    val textColor: Color,
    val icon: ImageVector
)

private fun calculateRestHours(
    prevDate: LocalDate,
    prev: ShiftDefinition?,
    curDate: LocalDate,
    current: ShiftDefinition?
): RestEvaluation? {
    if (prev == null || current == null) return null

    if (prev.isAllDay) {
        return RestEvaluation(
            label = "Stacco dopo riposo / assenza",
            hoursFormatted = "✓ Conforme",
            isCompliant = true,
            containerColor = Color(0xFF063326),
            borderColor = Color(0xFF10B981).copy(alpha = 0.4f),
            textColor = Color(0xFF34D399),
            icon = Icons.Default.CheckCircle
        )
    }
    if (current.isAllDay) return null

    return try {
        val prevStart = LocalTime.parse(prev.startTime)
        val prevEnd = LocalTime.parse(prev.endTime)
        val curStart = LocalTime.parse(current.startTime)

        val prevEndDateTime = if (prevEnd.isBefore(prevStart)) {
            prevDate.plusDays(1).atTime(prevEnd)
        } else {
            prevDate.atTime(prevEnd)
        }

        val curStartDateTime = curDate.atTime(curStart)
        val durationMinutes = Duration.between(prevEndDateTime, curStartDateTime).toMinutes()
        if (durationMinutes < 0) return null

        val hours = durationMinutes / 60
        val mins = durationMinutes % 60
        val formatted = "${hours}h ${if (mins > 0) "${mins}m" else ""}".trim()

        val minRequiredRest = ShiftRepository.activeWorkProfile.minRestHoursBetweenShifts.coerceAtLeast(8)

        if (hours >= minRequiredRest) {
            RestEvaluation(
                label = "Stacco Conforme ($formatted >= ${minRequiredRest}h)",
                hoursFormatted = "✓ $formatted",
                isCompliant = true,
                containerColor = Color(0xFF063326),
                borderColor = Color(0xFF10B981).copy(alpha = 0.4f),
                textColor = Color(0xFF34D399),
                icon = Icons.Default.CheckCircle
            )
        } else {
            RestEvaluation(
                label = "Attenzione: Riposo < ${minRequiredRest}h",
                hoursFormatted = "$formatted (min. ${minRequiredRest}h)",
                isCompliant = false,
                containerColor = Color(0xFF3B1219),
                borderColor = Color(0xFFEF4444).copy(alpha = 0.6f),
                textColor = Color(0xFFFCA5A5),
                icon = Icons.Default.Warning
            )
        }
    } catch (e: Exception) {
        null
    }
}
