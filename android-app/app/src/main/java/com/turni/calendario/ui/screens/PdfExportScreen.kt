@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.turni.calendario.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.turni.calendario.export.PdfExportPlan
import com.turni.calendario.export.PdfGenerator
import com.turni.calendario.export.PdfPresentation
import com.turni.calendario.model.PdfExportMode
import com.turni.calendario.model.PdfExportOptions
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.ActionFeedback
import com.turni.calendario.ui.components.SiteIcon
import com.turni.calendario.ui.components.SiteIconMotion
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.Dispatchers
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
        CloudCard("Formato", "Scegli come organizzare il documento") {
            SlidingSegmentBar(
                items = modeItems,
                selectedIndex = modes.indexOf(options.mode).coerceAtLeast(0),
                onSelect = { index -> update(options.copy(mode = modes[index])) },
            )
            AnimatedContent(
                targetState = options.mode,
                transitionSpec = {
                    val forward = targetState.ordinal >= initialState.ordinal
                    (slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) {
                        if (forward) it / 6 else -it / 6
                    } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) {
                            if (forward) -it / 8 else it / 8
                        } + fadeOut(tween(150)))
                },
                label = "pdfModeDescription",
            ) { mode ->
                val modeDescription = when (PdfExportPlan.presentation(options.copy(mode = mode))) {
                    PdfPresentation.LIST -> "Elenco compatto su una pagina, con i dettagli selezionati."
                    PdfPresentation.MONTHS -> "Calendario mensile su una pagina."
                    else -> "Mesi affiancati e giorni allineati su un foglio orizzontale."
                }
                Text(modeDescription, color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }

        CloudCard(
            title = "Anteprima",
            subtitle = preview?.paperLabel?.takeUnless { previewLoading || previewError != null }
                ?: "Lo stesso foglio che verrà condiviso",
        ) {
            when {
                previewLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianSurfaceContainer)
                        .border(1.dp, ObsidianBorderSubtle, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    preview?.let {
                        Image(
                            bitmap = it.bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(10.dp),
                            contentScale = ContentScale.Fit,
                            alpha = 0.25f,
                        )
                    }
                    CircularProgressIndicator(Modifier.size(26.dp), color = BrandInk, strokeWidth = 2.dp)
                }
                previewError != null -> Text(previewError.orEmpty(), color = TextSecondary, fontSize = 12.sp)
                preview != null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianSurfaceContainer)
                        .border(1.dp, ObsidianBorderSubtle, RoundedCornerShape(14.dp))
                        .bouncyClickable { zoom = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = preview!!.bitmap.asImageBitmap(),
                        contentDescription = "Anteprima PDF, tocca per ingrandire",
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                else -> Text("Nessuna anteprima disponibile", color = TextSecondary, fontSize = 12.sp)
            }
            AnimatedVisibility(
                visible = preview != null && !previewLoading && previewError == null,
                enter = fadeIn(tween(180)) + expandVertically(tween(200)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tocca l’anteprima per ingrandire", fontSize = 11.sp, color = TextMuted)
                    CloudButton(
                        label = "Ingrandisci anteprima",
                        icon = "search",
                        onClick = { zoom = true },
                        secondary = true,
                    )
                }
            }
        }

        CloudCard("Periodo e turni") {
            PdfAction(
                icon = "calendar",
                title = "${range.first.format(formatter)} - ${range.second.format(formatter)}",
                subtitle = "Cambia periodo",
            ) { dateDialog = true }
            SettingsDivider()
            PdfAction(
                icon = "layers",
                title = options.filterShiftId?.let { ShiftRepository.getShiftDefinition(it)?.name } ?: "Tutti i turni",
                subtitle = "Filtra modelli",
            ) { filterDialog = true }
        }

        CloudCard("Contenuti", "Anteprima aggiornata automaticamente") {
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
                placeholder = { Text("Una nota per chi riceve il prospetto") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = ObsidianSurfaceContainer,
                    unfocusedContainerColor = ObsidianSurfaceContainer,
                    focusedBorderColor = ObsidianPrimary,
                    unfocusedBorderColor = ObsidianBorderSubtle,
                    focusedPlaceholderColor = TextSecondary,
                    unfocusedPlaceholderColor = TextSecondary,
                    cursorColor = ObsidianPrimary,
                ),
                shape = RoundedCornerShape(14.dp),
            )
            Text(
                text = "Il PDF usa fondo bianco e testo scuro per la lettura e la stampa.",
                fontSize = 11.sp,
                color = TextMuted,
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
        SelectorSheet("Turni da includere", { filterDialog = false }) { close ->
            Text(
                text = "Scegli un modello oppure includi tutti i turni del lavoro attivo.",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            val models = ShiftRepository.shiftDefinitions.filter { it.workProfileId == ShiftRepository.activeWorkProfileId }
            val entries = listOf(null to "Tutti i turni") + models.map { it.id to it.name }
            entries.forEach { (id, label) ->
                val selected = options.filterShiftId == id
                val shape = RoundedCornerShape(14.dp)
                val rowBg by animateColorAsState(
                    targetValue = if (selected) ObsidianPrimarySubtle else ObsidianSurfaceContainer,
                    animationSpec = tween(180),
                    label = "pdfFilterBg",
                )
                val rowBorder by animateColorAsState(
                    targetValue = if (selected) ObsidianPrimary else ObsidianBorderSubtle,
                    animationSpec = tween(180),
                    label = "pdfFilterBorder",
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(shape)
                        .background(rowBg)
                        .border(1.dp, rowBorder, shape)
                        .bouncyClickable {
                            update(options.copy(filterShiftId = id))
                            close()
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val model = models.firstOrNull { it.id == id }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(model?.color ?: ObsidianSurfaceHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (model == null) {
                            SiteIcon("layers", null, Modifier.size(19.dp), BrandInk)
                        } else {
                            Text(
                                text = model.shortCode.take(4),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = catalogInk(model.color),
                            )
                        }
                    }
                    Text(label, Modifier.weight(1f), color = TextPrimary, fontSize = 14.sp)
                    if (selected) SiteIcon("check", null, Modifier.size(20.dp), BrandInk)
                }
            }
        }
    }
}

@Composable
private fun PdfAction(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .bouncyClickable {
                scope.launch {
                    progress.snapTo(0f)
                    progress.animateTo(1f, tween(560))
                    progress.snapTo(0f)
                }
                onClick()
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteIconMotion(icon, null, tint = BrandInk) { progress.value }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextMuted, fontSize = 11.sp)
        }
        SiteIcon("chevron-right", null, Modifier.size(18.dp), TextMuted)
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
        Text(label, Modifier.weight(1f).padding(end = 12.dp), color = TextPrimary, fontSize = 13.sp)
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
    // Keep the draft until the exit animation finishes, then commit once.
    var pending by remember { mutableStateOf<PdfExportOptions?>(null) }
    SelectorSheet(
        title = when (options.mode) {
            PdfExportMode.MESE -> "Scegli il mese"
            PdfExportMode.ANNO -> "Scegli l’anno"
            else -> "Periodo da esportare"
        },
        onDismiss = { pending?.let(save) ?: dismiss() },
    ) { close ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (options.mode) {
                PdfExportMode.MESE, PdfExportMode.ANNO -> {
                    val monthly = options.mode == PdfExportMode.MESE
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val navShape = RoundedCornerShape(12.dp)
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(navShape)
                                .background(ObsidianSurfaceContainer)
                                .border(1.dp, ObsidianBorderSubtle, navShape)
                                .bouncyClickable {
                                    if (monthly && month.year > 1900) {
                                        month = month.minusYears(1)
                                    } else if (!monthly) {
                                        yearPage = (yearPage - 12).coerceAtLeast(1900)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            SiteIcon("chevron-left", "Periodo precedente", Modifier.size(18.dp), TextPrimary)
                        }
                        Text(
                            text = if (monthly) month.year.toString() else "$yearPage – ${minOf(2200, yearPage + 11)}",
                            modifier = Modifier.weight(1f),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(navShape)
                                .background(ObsidianSurfaceContainer)
                                .border(1.dp, ObsidianBorderSubtle, navShape)
                                .bouncyClickable {
                                    if (monthly && month.year < 2200) {
                                        month = month.plusYears(1)
                                    } else if (!monthly) {
                                        yearPage = (yearPage + 12).coerceAtMost(2196)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            SiteIcon("chevron-right", "Periodo successivo", Modifier.size(18.dp), TextPrimary)
                        }
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
                                val borderTone by animateColorAsState(
                                    targetValue = if (chosen) ObsidianPrimary else ObsidianBorderSubtle,
                                    animationSpec = tween(180),
                                    label = "pdfPeriodCellBorder",
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(cellShape)
                                        .background(tone)
                                        .border(1.dp, borderTone, cellShape)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
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
                                        color = if (chosen) OnBrand else TextPrimary,
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
                            val chipShape = RoundedCornerShape(13.dp)
                            val chipBg by animateColorAsState(
                                targetValue = if (selected) ObsidianPrimarySubtle else ObsidianSurfaceContainer,
                                animationSpec = tween(180),
                                label = "pdfRangeChipBg",
                            )
                            val chipBorder by animateColorAsState(
                                targetValue = if (selected) ObsidianPrimary else ObsidianBorderSubtle,
                                animationSpec = tween(180),
                                label = "pdfRangeChipBorder",
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(chipShape)
                                    .background(chipBg)
                                    .border(1.dp, chipBorder, chipShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
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
            error?.let { Text(it, color = ColorNegative, fontSize = 12.sp) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CloudButton("Annulla", "x", close, modifier = Modifier.weight(1f), secondary = true)
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
                            pending = changed
                            close()
                        } catch (exception: IllegalArgumentException) {
                            error = exception.message
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private data class PdfPreview(val file: File, val bitmap: Bitmap, val width: Int, val height: Int) {
    val paperLabel: String
        get() = "${when (minOf(width, height)) { 595 -> "A4"; 842 -> "A3"; 1191 -> "A2"; 1684 -> "A1"; else -> "Foglio" }} · ${if (height >= width) "verticale" else "orizzontale"} · 1 pagina"
}

@Composable
private fun PdfPreviewDialog(
    preview: PdfPreview,
    busy: Boolean = false,
    onShare: () -> Unit = {},
    onClose: () -> Unit,
) {
    var scale by remember(preview) { mutableStateOf(1f) }
    var offset by remember(preview) { mutableStateOf(Offset.Zero) }
    val gestures = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 4f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.dp, ObsidianBorderSubtle),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = preview.paperLabel,
                        modifier = Modifier.weight(1f),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                    val closeShape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(closeShape)
                            .background(ObsidianSurfaceContainer)
                            .border(1.dp, ObsidianBorderSubtle, closeShape)
                            .bouncyClickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        SiteIcon("x", "Chiudi anteprima", Modifier.size(18.dp), ColorNegative)
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianSurfaceContainer)
                        .border(1.dp, ObsidianBorderSubtle, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = preview.bitmap.asImageBitmap(),
                        contentDescription = "Documento PDF",
                        modifier = Modifier
                            .fillMaxSize()
                            .transformable(gestures)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                        contentScale = ContentScale.Fit,
                    )
                }
                Text("Allarga con due dita per leggere i dettagli", fontSize = 11.sp, color = TextSecondary)
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
