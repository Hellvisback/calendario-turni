@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.turni.calendario.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.turni.calendario.export.PdfExportPlan
import com.turni.calendario.export.PdfGenerator
import com.turni.calendario.model.PdfExportMode
import com.turni.calendario.model.PdfExportOptions
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.ActionFeedback
import com.turni.calendario.ui.components.SiteIcon
import com.turni.calendario.ui.components.SiteIconMotion
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PdfExportScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var options by remember { mutableStateOf(ShiftRepository.pdfExportOptions) }
    var dateDialog by remember { mutableStateOf(false) }
    var filterDialog by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<ActionFeedback?>(null) }
    var preview by remember { mutableStateOf<PdfPreview?>(null) }
    var previewLoading by remember { mutableStateOf(true) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableStateOf(false) }
    val calendar by remember { derivedStateOf { ShiftRepository.calendarShifts.mapValues { it.value.toList() } } }
    val extras by remember { derivedStateOf { ShiftRepository.dayExtraInfo.toMap() } }
    val definitions by remember { derivedStateOf { ShiftRepository.shiftDefinitions.toList() } }
    LaunchedEffect(options, ShiftRepository.activeWorkProfileId, calendar, extras, definitions) {
        previewLoading = true
        previewError = null
        try {
            delay(280)
            val snapshot = PdfGenerator.capture(options)
            preview = withContext(Dispatchers.IO) {
                val file = PdfGenerator.writePdf(context, snapshot)
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        check(renderer.pageCount == 1) { "Il documento deve contenere una sola pagina" }
                        renderer.openPage(0).use { page ->
                            val width = 1800
                            val bitmap = Bitmap.createBitmap(
                                width,
                                (width.toFloat() * page.height / page.width).toInt(),
                                Bitmap.Config.ARGB_8888,
                            )
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            PdfPreview(file, bitmap, page.width, page.height)
                        }
                    }
                }
            }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            previewError = if (error is IllegalArgumentException) {
                error.message
            } else {
                "Impossibile preparare l’anteprima. Riprova."
            }
        } finally {
            previewLoading = false
        }
    }
    val update: (PdfExportOptions) -> Unit = {
        if (options != it) {
            previewLoading = true
            zoom = false
        }
        options = it
        ShiftRepository.pdfExportOptions = it
        ShiftRepository.notifyDataChanged()
    }
    val sharePreview: () -> Unit = {
        preview?.let { ready ->
            busy = true
            try {
                PdfGenerator.sharePdf(context, ready.file)
            } catch (_: Exception) {
                feedback = ActionFeedback("Impossibile condividere il PDF. Riprova.", true)
            } finally {
                busy = false
            }
        }
    }
    val range = remember(options.mode, options.startDate, options.endDate, options.selectedYear) {
        PdfExportPlan.range(options)
    }
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN) }
    val modes = remember { PdfExportMode.values().toList() }
    val modeItems = remember(modes) {
        modes.map { mode ->
            mode.label to when (mode) {
                PdfExportMode.ELENCO -> "file-text"
                PdfExportMode.MESE -> "calendar"
                PdfExportMode.ANNO -> "clock"
                PdfExportMode.PERSONALIZZATO -> "swap"
            }
        }
    }
    val palette = currentTurniPalette
    val formatIconMotion = remember { Animatable(0f) }
    LaunchedEffect(options.mode) {
        formatIconMotion.snapTo(0f)
        formatIconMotion.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        formatIconMotion.snapTo(0f)
    }
    val previewIconMotion = remember { Animatable(0f) }
    val previewScope = rememberCoroutineScope()
    var previewIconJob by remember { mutableStateOf<Job?>(null) }

    CloudFeatureScaffold(
        title = "Esporta e stampa PDF",
        subtitle = ShiftRepository.activeWorkProfile.name,
        onDismiss = onDismiss,
        feedback = feedback,
        onDismissFeedback = { feedback = null },
        bottomBar = {
            CloudButton(
                label = when {
                    busy -> "Condivisione…"
                    previewLoading -> "Preparazione anteprima…"
                    else -> "Condividi PDF · 1 pagina"
                },
                icon = "download",
                onClick = sharePreview,
                enabled = !busy && !previewLoading && previewError == null && preview != null,
            )
        },
    ) {
        CloudCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NeutralIconBadge(
                    icon = "file-text",
                    size = 42.dp,
                    cornerRadius = 13.dp,
                    iconSize = 20.dp,
                    progress = formatIconMotion.value,
                )
                Text(
                    text = "Formato",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            SlidingSegmentBar(
                items = modeItems,
                selectedIndex = modes.indexOf(options.mode).coerceAtLeast(0),
                onSelect = { index -> update(options.copy(mode = modes[index])) },
            )
        }

        CloudCard(
            title = "Anteprima",
        ) {
            val dark = TurniThemeState.dark
            val stageShape = RoundedCornerShape(18.dp)
            when {
                previewLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(stageShape)
                        .background(ObsidianSurfaceContainer)
                        .border(1.dp, ObsidianBorder, stageShape),
                    contentAlignment = Alignment.Center,
                ) {
                    preview?.let {
                        Image(
                            bitmap = it.bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 44.dp, bottom = 36.dp, start = 20.dp, end = 20.dp),
                            contentScale = ContentScale.Fit,
                            alpha = 0.22f,
                        )
                    }
                    CircularProgressIndicator(Modifier.size(26.dp), color = BrandInk, strokeWidth = 2.dp)
                }
                previewError != null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(stageShape)
                        .background(ObsidianSurfaceContainer)
                        .border(1.dp, ObsidianBorder, stageShape)
                        .padding(16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        NeutralIconBadge(
                            icon = "alert-triangle",
                            size = 38.dp,
                            cornerRadius = 12.dp,
                            iconSize = 18.dp,
                            fill = Color(palette.danger.fill),
                            tint = Color(palette.danger.ink),
                        )
                        Text(previewError.orEmpty(), color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
                preview != null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(stageShape)
                        .background(ObsidianSurfaceContainer)
                        .border(1.dp, ObsidianBorder, stageShape)
                        .bouncyClickable {
                            previewIconJob?.cancel()
                            previewIconJob = previewScope.launch {
                                previewIconMotion.snapTo(0f)
                                previewIconMotion.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
                                previewIconMotion.snapTo(0f)
                            }
                            zoom = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 44.dp, bottom = 40.dp, start = 20.dp, end = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val pageRatio = (preview!!.width.toFloat() / preview!!.height.toFloat()).coerceIn(0.4f, 2.5f)
                        val containerRatio = if (maxHeight > 0.dp) maxWidth / maxHeight else 1f
                        val sheetModifier = if (pageRatio >= containerRatio) {
                            Modifier.fillMaxWidth().aspectRatio(pageRatio)
                        } else {
                            Modifier.fillMaxHeight().aspectRatio(pageRatio)
                        }
                        Surface(
                            modifier = sheetModifier,
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            shadowElevation = if (dark) 8.dp else 4.dp,
                            border = BorderStroke(0.75.dp, Color(0xFFD5DCE6)),
                        ) {
                            Image(
                                bitmap = preview!!.bitmap.asImageBitmap(),
                                contentDescription = "Anteprima PDF, tocca per ingrandire",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                    PdfStatusPill(
                        text = preview!!.pageBadgeLabel,
                        fill = ObsidianPrimaryContainer,
                        ink = BrandInk,
                        icon = "file-text",
                        progress = previewIconMotion.value,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                    )
                    PdfStatusPill(
                        text = preview!!.orientationWord,
                        fill = ObsidianSurface.copy(alpha = 0.94f),
                        ink = TextSecondary,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .border(0.75.dp, ObsidianBorder, RoundedCornerShape(10.dp)),
                    )
                }
                else -> Text("Nessuna anteprima disponibile", color = TextSecondary, fontSize = 12.sp)
            }
            AnimatedVisibility(
                visible = preview != null && !previewLoading && previewError == null,
                enter = fadeIn(tween(180)) + expandVertically(tween(200)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
            ) {
                CloudButton(
                    label = "Ingrandisci anteprima",
                    icon = "search",
                    onClick = { zoom = true },
                    secondary = true,
                )
            }
        }

        val filteredModel = options.filterShiftId?.let { ShiftRepository.getShiftDefinition(it) }
        CloudCard("Periodo e turni") {
            PdfAction(
                title = "${range.first.format(formatter)} – ${range.second.format(formatter)}",
                subtitle = "Cambia periodo",
                onClick = { dateDialog = true },
                badge = { progress ->
                    NeutralIconBadge(
                        icon = "calendar",
                        size = 38.dp,
                        cornerRadius = 12.dp,
                        iconSize = 18.dp,
                        fill = ObsidianSurface,
                        tint = BrandInk,
                        progress = progress,
                    )
                },
            )
            PdfAction(
                title = filteredModel?.name ?: "Tutti i turni",
                subtitle = "Filtra modelli",
                onClick = { filterDialog = true },
                badge = { progress ->
                    if (filteredModel == null) {
                        NeutralIconBadge(
                            icon = "layers",
                            size = 38.dp,
                            cornerRadius = 12.dp,
                            iconSize = 18.dp,
                            fill = ObsidianSurface,
                            tint = BrandInk,
                            progress = progress,
                        )
                    } else {
                        PastelBadge(
                            color = Color(filteredModel.colorHex),
                            size = 38.dp,
                            cornerRadius = 12.dp,
                            definitionId = filteredModel.id,
                        ) { ink ->
                            Text(
                                text = filteredModel.shortCode.take(4),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ink,
                            )
                        }
                    }
                },
            )
        }

        CloudCard("Contenuti") {
            PdfToggle("Nome del turno", options.includeTitle) { update(options.copy(includeTitle = it)) }
            SettingsDivider()
            PdfToggle("Orario di inizio e fine", options.includeTime) { update(options.copy(includeTime = it)) }
            SettingsDivider()
            PdfToggle("Durata prevista", options.includeDuration) { update(options.copy(includeDuration = it)) }
            SettingsDivider()
            PdfToggle("Pausa", options.includeBreak) { update(options.copy(includeBreak = it)) }
            SettingsDivider()
            PdfToggle("Trasferte", options.includeLocation) { update(options.copy(includeLocation = it)) }
            SettingsDivider()
            PdfToggle("Note e tag", options.includeNotes) { update(options.copy(includeNotes = it)) }
            SettingsDivider()
            PdfToggle("Festività", options.includeEvents) { update(options.copy(includeEvents = it)) }
            SettingsDivider()
            PdfToggle("Colori dei turni", options.includeIcon) { update(options.copy(includeIcon = it)) }
            AnimatedVisibility(
                visible = options.mode == PdfExportMode.ELENCO,
                enter = fadeIn(tween(180)) + expandVertically(tween(200)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(160)),
            ) {
                Column {
                    SettingsDivider()
                    PdfToggle("Giorni senza turni", options.includeEmptyRows) {
                        update(options.copy(includeEmptyRows = it))
                    }
                }
            }
            SettingsDivider()
            PdfToggle("Marchio TurniSync", options.includeLogo) { update(options.copy(includeLogo = it)) }
        }

        CloudCard("Nota del documento") {
            OutlinedTextField(
                value = options.headerNotes,
                onValueChange = { update(options.copy(headerNotes = it.take(4000))) },
                placeholder = { Text("Nota opzionale") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = ObsidianSurfaceContainer,
                    unfocusedContainerColor = ObsidianSurfaceContainer,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    errorBorderColor = ColorNegative,
                    focusedPlaceholderColor = TextSecondary,
                    unfocusedPlaceholderColor = TextSecondary,
                    cursorColor = ObsidianPrimary,
                ),
                shape = RoundedCornerShape(15.dp),
            )
        }
    }

    if (zoom && preview != null) {
        PdfPreviewDialog(preview!!, busy = busy, onShare = sharePreview) { zoom = false }
    }
    if (dateDialog) {
        PdfPeriodDialog(options, { dateDialog = false }) {
            update(it)
            dateDialog = false
        }
    }
    if (filterDialog) {
        val dialogPalette = currentTurniPalette
        TurniSafeBottomDialog(onClose = { filterDialog = false }, scrollContent = false) { finish ->
            val dark = TurniThemeState.dark
            val filterBadgeMotion = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                filterBadgeMotion.snapTo(0f)
                filterBadgeMotion.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
                filterBadgeMotion.snapTo(0f)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NeutralIconBadge(
                    icon = "layers",
                    size = 38.dp,
                    cornerRadius = 12.dp,
                    iconSize = 18.dp,
                    fill = ObsidianPrimaryContainer,
                    tint = BrandInk,
                    progress = filterBadgeMotion.value,
                )
                Text(
                    text = "Turni da includere",
                    modifier = Modifier.weight(1f),
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
                TonalIconButton(
                    icon = "x",
                    contentDescription = "Chiudi",
                    onClick = { finish { filterDialog = false } },
                    size = 36.dp,
                    cornerRadius = 12.dp,
                    fill = Color(dialogPalette.danger.fill),
                    tint = Color(dialogPalette.danger.ink),
                )
            }
            val models = ShiftRepository.shiftDefinitions.filter { it.workProfileId == ShiftRepository.activeWorkProfileId }
            val entries = listOf(null to "Tutti i turni") + models.map { it.id to it.name }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                entries.forEach { (id, label) ->
                    val selected = options.filterShiftId == id
                    val shape = RoundedCornerShape(15.dp)
                    val selectedBg = lerp(ObsidianSurfaceContainer, ObsidianPrimary, if (dark) 0.22f else 0.12f)
                    val rowBg by animateColorAsState(
                        targetValue = if (selected) selectedBg else ObsidianSurfaceContainer,
                        animationSpec = tween(180),
                        label = "pdfFilterBg",
                    )
                    val rowRing by animateColorAsState(
                        targetValue = if (selected) ObsidianPrimary else Color.Transparent,
                        animationSpec = tween(180),
                        label = "pdfFilterRing",
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(rowBg)
                            .then(if (selected) Modifier.border(2.dp, rowRing, shape) else Modifier)
                            .clickable(
                                interactionSource = remember(id) { MutableInteractionSource() },
                                indication = null,
                            ) {
                                finish {
                                    update(options.copy(filterShiftId = id))
                                    filterDialog = false
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val model = models.firstOrNull { it.id == id }
                        if (model == null) {
                            NeutralIconBadge(
                                icon = "layers",
                                size = 38.dp,
                                cornerRadius = 11.dp,
                                iconSize = 18.dp,
                                fill = ObsidianSurface,
                                tint = BrandInk,
                            )
                        } else {
                            PastelBadge(
                                color = Color(model.colorHex),
                                size = 38.dp,
                                cornerRadius = 11.dp,
                                definitionId = model.id,
                            ) { ink ->
                                Text(
                                    text = model.shortCode.take(4),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ink,
                                )
                            }
                        }
                        Text(label, Modifier.weight(1f), color = TextPrimary, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium)
                        if (selected) SiteIcon("check", null, Modifier.size(20.dp), BrandInk)
                    }
                }
            }
            CloudButton(
                label = "Chiudi",
                icon = "x",
                onClick = { finish { filterDialog = false } },
                secondary = true,
            )
        }
    }
}

@Composable
private fun PdfStatusPill(
    text: String,
    fill: Color,
    ink: Color,
    icon: String? = null,
    progress: Float = 0f,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            SiteIconMotion(
                name = icon,
                description = null,
                modifier = Modifier.size(12.dp),
                tint = ink,
            ) { progress }
        }
        Text(
            text = text,
            color = ink,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PdfAction(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    badge: @Composable (Float) -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(15.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(shape)
            .background(ObsidianSurfaceContainer)
            .bouncyClickable {
                scope.launch {
                    progress.snapTo(0f)
                    progress.animateTo(1f, tween(560))
                    progress.snapTo(0f)
                }
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        badge(progress.value)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SiteIconMotion("chevron-right", null, Modifier.size(18.dp), TextSecondary) {
            progress.value
        }
    }
}

@Composable
private fun PdfToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp), color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
        TurniSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PdfPeriodDialog(options: PdfExportOptions, dismiss: () -> Unit, save: (PdfExportOptions) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(options.startDate)) }
    var year by remember { mutableStateOf(options.selectedYear) }
    var yearPage by remember { mutableStateOf((options.selectedYear / 12) * 12) }
    var from by remember { mutableStateOf(options.startDate) }
    var to by remember { mutableStateOf(options.endDate) }
    var selectingEnd by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val dark = TurniThemeState.dark
    val palette = currentTurniPalette
    val title = when (options.mode) {
        PdfExportMode.MESE -> "Scegli il mese"
        PdfExportMode.ANNO -> "Scegli l’anno"
        else -> "Periodo da esportare"
    }
    TurniSafeBottomDialog(onClose = dismiss, scrollContent = false) { finish ->
        val calendarBadgeMotion = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            calendarBadgeMotion.snapTo(0f)
            calendarBadgeMotion.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            calendarBadgeMotion.snapTo(0f)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NeutralIconBadge(
                icon = "calendar",
                size = 38.dp,
                cornerRadius = 12.dp,
                iconSize = 18.dp,
                fill = ObsidianPrimaryContainer,
                tint = BrandInk,
                progress = calendarBadgeMotion.value,
            )
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
            TonalIconButton(
                icon = "x",
                contentDescription = "Chiudi",
                onClick = { finish(dismiss) },
                size = 36.dp,
                cornerRadius = 12.dp,
                fill = Color(palette.danger.fill),
                tint = Color(palette.danger.ink),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (options.mode) {
                PdfExportMode.MESE, PdfExportMode.ANNO -> {
                    val monthly = options.mode == PdfExportMode.MESE
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TonalIconButton(
                            icon = "chevron-left",
                            contentDescription = "Periodo precedente",
                            onClick = {
                                if (monthly && month.year > 1900) {
                                    month = month.minusYears(1)
                                } else if (!monthly) {
                                    yearPage = (yearPage - 12).coerceAtLeast(1900)
                                }
                            },
                            size = 38.dp,
                            cornerRadius = 12.dp,
                        )
                        Text(
                            text = if (monthly) month.year.toString() else "$yearPage – ${minOf(2200, yearPage + 11)}",
                            modifier = Modifier.weight(1f),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        TonalIconButton(
                            icon = "chevron-right",
                            contentDescription = "Periodo successivo",
                            onClick = {
                                if (monthly && month.year < 2200) {
                                    month = month.plusYears(1)
                                } else if (!monthly) {
                                    yearPage = (yearPage + 12).coerceAtMost(2196)
                                }
                            },
                            size = 38.dp,
                            cornerRadius = 12.dp,
                        )
                    }
                    (0 until 12).chunked(3).forEach { group ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            group.forEach { index ->
                                val value = if (monthly) index + 1 else yearPage + index
                                val chosen = if (monthly) month.monthValue == value else year == value
                                val cellShape = RoundedCornerShape(13.dp)
                                val tone by animateColorAsState(
                                    targetValue = if (chosen) ObsidianPrimary else ObsidianSurfaceContainer,
                                    animationSpec = tween(180),
                                    label = "pdfPeriodCellBg",
                                )
                                val inkTone by animateColorAsState(
                                    targetValue = if (chosen) OnBrand else TextPrimary,
                                    animationSpec = tween(180),
                                    label = "pdfPeriodCellInk",
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(cellShape)
                                        .background(tone)
                                        .clickable(
                                            interactionSource = remember(value) { MutableInteractionSource() },
                                            indication = null,
                                            enabled = value in (if (monthly) 1..12 else 1900..2200),
                                        ) {
                                            if (monthly) month = YearMonth.of(month.year, value) else year = value
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if (monthly) {
                                            java.time.Month.of(value)
                                                .getDisplayName(java.time.format.TextStyle.SHORT, Locale.ITALIAN)
                                                .replaceFirstChar { it.titlecase(Locale.ITALIAN) }
                                        } else {
                                            value.toString()
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = inkTone,
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(false, true).forEach { end ->
                            val selected = selectingEnd == end
                            val chipShape = RoundedCornerShape(14.dp)
                            val activeBg = lerp(ObsidianSurfaceContainer, ObsidianPrimary, if (dark) 0.22f else 0.12f)
                            val chipBg by animateColorAsState(
                                targetValue = if (selected) activeBg else ObsidianSurfaceContainer,
                                animationSpec = tween(180),
                                label = "pdfRangeChipBg",
                            )
                            val chipRing by animateColorAsState(
                                targetValue = if (selected) ObsidianPrimary else Color.Transparent,
                                animationSpec = tween(180),
                                label = "pdfRangeChipRing",
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(chipShape)
                                    .background(chipBg)
                                    .then(if (selected) Modifier.border(2.dp, chipRing, chipShape) else Modifier)
                                    .clickable(
                                        interactionSource = remember(end) { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        selectingEnd = end
                                        month = YearMonth.from(if (end) to else from)
                                    }
                                    .padding(12.dp),
                            ) {
                                Column {
                                    Text(if (end) "Al" else "Dal", fontSize = 11.sp, color = TextSecondary)
                                    Text(
                                        text = (if (end) to else from).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                                        fontSize = 14.sp,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                    AppDateGrid(month, if (selectingEnd) to else from, { month = it }, rangeStart = from, rangeEnd = to) { date ->
                        error = null
                        if (selectingEnd) {
                            to = date
                            if (from > to) from = to
                        } else {
                            from = date
                            if (to < from) to = from
                            selectingEnd = true
                        }
                    }
                }
            }
            error?.let { Text(it, color = ColorNegative, fontSize = 12.sp, lineHeight = 17.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CloudButton("Annulla", "x", { finish(dismiss) }, modifier = Modifier.weight(1f), secondary = true)
            CloudButton(
                label = "Conferma",
                icon = "check",
                onClick = {
                    try {
                        val changed = when (options.mode) {
                            PdfExportMode.MESE -> options.copy(startDate = month.atDay(1), endDate = month.atEndOfMonth())
                            PdfExportMode.ANNO -> options.copy(selectedYear = year)
                            else -> options.copy(startDate = from, endDate = to)
                        }
                        PdfExportPlan.dates(changed)
                        finish { save(changed) }
                    } catch (exception: IllegalArgumentException) {
                        error = exception.message
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private data class PdfPreview(val file: File, val bitmap: Bitmap, val width: Int, val height: Int) {
    val paperSize: String
        get() = when (minOf(width, height)) { 595 -> "A4"; 842 -> "A3"; 1191 -> "A2"; 1684 -> "A1"; else -> "Foglio" }
    val orientationWord: String
        get() = if (height >= width) "Verticale" else "Orizzontale"
    val pageBadgeLabel: String
        get() = "$paperSize · 1 pagina"
}

@Composable
private fun PdfPreviewDialog(
    preview: PdfPreview,
    busy: Boolean = false,
    onShare: () -> Unit = {},
    onClose: () -> Unit,
) {
    val palette = currentTurniPalette
    var scale by remember(preview) { mutableStateOf(1f) }
    var offset by remember(preview) { mutableStateOf(Offset.Zero) }
    val gestures = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 4f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        val dark = TurniThemeState.dark
        val dimAmount = if (dark) 0.56f else 0.36f
        DisposableEffect(view, dark) {
            val applyChrome = Runnable {
                val window = (view as? DialogWindowProvider)?.window
                    ?: (view.parent as? DialogWindowProvider)?.window
                window?.let {
                    it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    it.setDimAmount(dimAmount)
                    @Suppress("DEPRECATION")
                    it.addFlags(
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                    )
                    @Suppress("DEPRECATION")
                    it.clearFlags(
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
                    )
                    WindowCompat.setDecorFitsSystemWindows(it, false)
                    @Suppress("DEPRECATION")
                    it.statusBarColor = Color.Transparent.toArgb()
                    @Suppress("DEPRECATION")
                    it.navigationBarColor = Color.Transparent.toArgb()
                    WindowCompat.getInsetsController(it, it.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
            }
            applyChrome.run()
            view.post(applyChrome)
            onDispose { view.removeCallbacks(applyChrome) }
        }

        val navBottom = WindowInsets.navigationBars.asPaddingValues()
            .calculateBottomPadding()
            .coerceAtLeast(24.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .padding(bottom = navBottom),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f),
                shape = RoundedCornerShape(26.dp),
                color = ObsidianSurface,
                tonalElevation = 0.dp,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PdfStatusPill(
                            text = preview.pageBadgeLabel,
                            fill = ObsidianPrimaryContainer,
                            ink = BrandInk,
                            icon = "file-text",
                        )
                        Text(
                            text = preview.orientationWord,
                            modifier = Modifier.weight(1f),
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                        )
                        TonalIconButton(
                            icon = "x",
                            contentDescription = "Chiudi anteprima",
                            onClick = onClose,
                            size = 36.dp,
                            cornerRadius = 12.dp,
                            fill = Color(palette.danger.fill),
                            tint = Color(palette.danger.ink),
                        )
                    }
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(ObsidianSurfaceContainer)
                            .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp))
                            .transformable(gestures)
                            .padding(14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val pageRatio = (preview.width.toFloat() / preview.height.toFloat()).coerceIn(0.4f, 2.5f)
                        val containerRatio = if (maxHeight > 0.dp) maxWidth / maxHeight else 1f
                        val sheetModifier = if (pageRatio >= containerRatio) {
                            Modifier.fillMaxWidth().aspectRatio(pageRatio)
                        } else {
                            Modifier.fillMaxHeight().aspectRatio(pageRatio)
                        }
                        Surface(
                            modifier = sheetModifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            shadowElevation = if (dark) 8.dp else 4.dp,
                            border = BorderStroke(0.75.dp, Color(0xFFD5DCE6)),
                        ) {
                            Image(
                                bitmap = preview.bitmap.asImageBitmap(),
                                contentDescription = "Documento PDF",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                    Text("Pizzica per ingrandire", fontSize = 11.sp, lineHeight = 16.sp, color = TextSecondary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CloudButton("Chiudi", "x", onClose, modifier = Modifier.weight(1f), secondary = true)
                        CloudButton(
                            label = if (busy) "Condivisione…" else "Condividi PDF",
                            icon = "download",
                            onClick = onShare,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
