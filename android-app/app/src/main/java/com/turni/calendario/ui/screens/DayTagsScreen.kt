@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.turni.calendario.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.model.*
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.*
import com.turni.calendario.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val tagSwatches = listOf(0xFF94C4EFL, 0xFF83CDB7L, 0xFFF2C46DL, 0xFFEE9C9CL, 0xFFBAAEF6L, 0xFFB4D995L)
private val tagIconLabels = listOf("Etichetta", "Formazione", "Salute", "Studio", "Sorriso", "Obiettivo")

@Composable
internal fun DayTagChip(
    tag: DayTag,
    selected: Boolean = false,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = shiftColorStyle(Color(tag.colorHex), TurniThemeState.dark, ObsidianSurface)
    val fill by animateColorAsState(
        targetValue = if (selected) colors.codeFill else ObsidianSurfaceContainer,
        animationSpec = tween(220),
        label = "dayTagChipFill",
    )
    val ink by animateColorAsState(
        targetValue = if (selected) colors.codeInk else TextPrimary,
        animationSpec = tween(220),
        label = "dayTagChipInk",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) colors.codeInk.copy(alpha = 0.45f) else ObsidianBorderSubtle,
        animationSpec = tween(220),
        label = "dayTagChipBorder",
    )
    val phase by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(0.85f, 420f),
        label = "dayTagChipPhase",
    )
    val chipShape = RoundedCornerShape(11.dp)
    Row(
        modifier = modifier
            .heightIn(min = if (compact) 28.dp else 36.dp)
            .clip(chipShape)
            .background(fill)
            .border(if (selected) 1.5.dp else 1.dp, borderColor, chipShape)
            .then(
                if (onClick != null) {
                    Modifier
                        .semantics {
                            this.selected = selected
                            role = Role.Checkbox
                        }
                        .bouncyClickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(
                horizontal = if (compact) 7.dp else 10.dp,
                vertical = if (compact) 4.dp else 7.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SiteIcon(
            name = tag.iconKey,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (selected) ink else readablePigment(fill, shiftDisplayColor(Color(tag.colorHex))),
            progress = phase,
        )
        Text(
            text = tag.name,
            color = ink,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = if (compact) 160.dp else 220.dp),
        )
        if (selected && onClick != null) {
            SiteIcon("check", null, Modifier.size(13.dp), ink)
        }
    }
}

internal fun dayTagMutationFeedback(savedTag: DayTag): ActionFeedback {
    val removed = ShiftRepository.dayTags.none { tag -> tag.id == savedTag.id }
    val message = when {
        removed -> "Etichetta eliminata"
        savedTag.archived -> "Etichetta archiviata"
        else -> "Etichetta salvata"
    }
    return ActionFeedback(message, isDestructive = removed)
}

@Composable
internal fun DayTagsCatalogContent(
    onEditorActiveChanged: ((Boolean) -> Unit)? = null,
    showHeaderCard: Boolean = true,
    modifier: Modifier = Modifier,
    onEditTag: (DayTag) -> Unit,
    onFeedback: (ActionFeedback) -> Unit,
) {
    var showArchived by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DayTag?>(null) }
    val tags = ShiftRepository.dayTags.toList()
    val activeTags = tags.filter { !it.archived }
    val archivedTags = tags.filter { it.archived }
    val chevronAngle by animateFloatAsState(
        targetValue = if (showArchived) 180f else 0f,
        animationSpec = spring(0.85f, 380f),
        label = "archivedTagsChevron",
    )

    var wasDeleting by remember { mutableStateOf(false) }
    LaunchedEffect(pendingDelete) {
        val isDeleting = pendingDelete != null
        if (isDeleting != wasDeleting) {
            wasDeleting = isDeleting
            onEditorActiveChanged?.invoke(isDeleting)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        if (showHeaderCard) {
            item(key = "tags-intro", contentType = "tags-intro") {
                CloudCard(
                    title = "Ritrova ciò che hai fatto",
                    subtitle = "Aggiungi etichette come Formazione o Visita medica. Ogni etichetta conta i giorni nelle statistiche; puoi affiancarle una nota con i dettagli.",
                ) {}
            }
        }
        if (activeTags.isEmpty()) {
            item(key = "tags-empty", contentType = "tags-empty") {
                CloudCard("Le tue etichette") {
                    Text(
                        text = if (archivedTags.isEmpty()) {
                            "Crea la prima etichetta dal pulsante qui sotto, poi apri un giorno del calendario e tocca Note ed etichette per assegnarla."
                        } else {
                            "Tutte le etichette sono archiviate. Puoi riattivarle qui sotto o crearne una nuova."
                        },
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
        }
        items(activeTags, key = { it.id }, contentType = { "tag-row" }) { tag ->
            val usageCount = ShiftRepository.dayExtraInfo.values.count { tag.id in it.tagIds }
            TagManagementRow(
                tag = tag,
                usageCount = usageCount,
                onEdit = { onEditTag(tag) },
                onDelete = if (usageCount == 0) {
                    { pendingDelete = tag }
                } else {
                    null
                },
            )
        }
        if (archivedTags.isNotEmpty()) {
            item(key = "tags-archived-toggle", contentType = "tags-archived-toggle") {
                val toggleShape = RoundedCornerShape(14.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(toggleShape)
                        .background(ObsidianSurfaceContainer)
                        .border(1.dp, ObsidianBorderSubtle, toggleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showArchived = !showArchived }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SiteIcon("folder-open", null, Modifier.size(17.dp), BrandInk)
                    Text(
                        text = "Archiviate (${archivedTags.size})",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    SiteIcon(
                        name = "chevron-down",
                        contentDescription = if (showArchived) "Nascondi archiviate" else "Mostra archiviate",
                        modifier = Modifier.size(18.dp).rotate(chevronAngle),
                        tint = TextSecondary,
                    )
                }
            }
            item(key = "tags-archived-list", contentType = "tags-archived-list") {
                AnimatedVisibility(
                    visible = showArchived,
                    enter = expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Restano visibili sui giorni già segnati e nelle statistiche.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        archivedTags.forEach { tag ->
                            val usageCount = ShiftRepository.dayExtraInfo.values.count { tag.id in it.tagIds }
                            TagManagementRow(
                                tag = tag,
                                usageCount = usageCount,
                                onEdit = { onEditTag(tag) },
                                onDelete = if (usageCount == 0) {
                                    { pendingDelete = tag }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AppConfirmationDialog(
            title = "Eliminare questa etichetta?",
            message = "«${target.name}» non è associata a nessuna giornata. Potrai crearne una nuova quando vuoi.",
            confirmLabel = "Elimina etichetta",
            dismissLabel = "Annulla",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                try {
                    ShiftRepository.deleteUnusedDayTag(target.id)
                    onFeedback(ActionFeedback("Etichetta eliminata", isDestructive = true))
                } catch (problem: IllegalArgumentException) {
                    onFeedback(ActionFeedback(problem.message ?: "Impossibile eliminare l’etichetta", isDestructive = true))
                }
            },
        )
    }
}

@Composable
internal fun DayTagsSettingsScreen(onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf<DayTag?>(null) }
    var creating by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<ActionFeedback?>(null) }
    val tagsCount = ShiftRepository.dayTags.size

    Box(Modifier.fillMaxSize()) {
        CloudFeatureScaffold(
            title = "Etichette dei giorni",
            subtitle = "Attività e note da ritrovare nelle statistiche",
            onDismiss = onDismiss,
            feedback = feedback,
            onDismissFeedback = { feedback = null },
            scrollable = false,
            bottomBar = {
                CloudButton(
                    label = "Nuova etichetta",
                    icon = "plus",
                    onClick = { creating = true },
                    enabled = tagsCount < 500,
                )
            },
        ) {
            DayTagsCatalogContent(
                showHeaderCard = true,
                onEditTag = { editing = it },
                onFeedback = { feedback = it },
                modifier = Modifier.weight(1f),
            )
        }
        if (creating || editing != null) {
            DayTagEditor(
                original = editing,
                onClose = {
                    creating = false
                    editing = null
                },
                onSaved = { saved ->
                    creating = false
                    editing = null
                    feedback = dayTagMutationFeedback(saved)
                },
            )
        }
    }
}

@Composable
private fun TagManagementRow(
    tag: DayTag,
    usageCount: Int,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val style = shiftColorStyle(Color(tag.colorHex), TurniThemeState.dark, ObsidianSurface)
    val cardShape = RoundedCornerShape(17.dp)
    val countLabel = when {
        usageCount == 0 -> "Non ancora usata"
        usageCount == 1 -> "1 giorno segnato"
        else -> "$usageCount giorni segnati"
    }
    val subtitle = if (tag.archived) "$countLabel · Archiviata" else countLabel

    Surface(
        color = ObsidianSurface,
        shape = cardShape,
        border = BorderStroke(1.dp, ObsidianBorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .bouncyClickable(onClick = onEdit)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(style.codeFill),
                contentAlignment = Alignment.Center,
            ) {
                SiteIcon(
                    name = tag.iconKey,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = style.codeInk,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = tag.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onDelete != null) {
                TagRowIconButton(
                    icon = "trash-2",
                    description = "Elimina ${tag.name}",
                    tint = ColorNegative,
                    onClick = onDelete,
                )
            }
            TagRowIconButton(
                icon = "edit",
                description = "Modifica ${tag.name}",
                tint = BrandInk,
                onClick = onEdit,
            )
        }
    }
}

@Composable
private fun TagRowIconButton(
    icon: String,
    description: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .background(ObsidianSurfaceContainer)
            .border(1.dp, ObsidianBorderSubtle, shape)
            .bouncyClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SiteIcon(icon, description, Modifier.size(18.dp), tint)
    }
}

@Composable
internal fun DayTagEditor(
    original: DayTag?,
    onClose: () -> Unit,
    inlineContent: Boolean = false,
    inlineScope: ColumnScope? = null,
    onExit: (() -> Unit)? = null,
    onSaved: (DayTag) -> Unit,
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var name by remember(original?.id) { mutableStateOf(original?.name ?: "") }
    var icon by remember(original?.id) { mutableStateOf(original?.iconKey ?: "tag") }
    var color by remember(original?.id) { mutableStateOf(original?.colorHex ?: tagSwatches.first()) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedTag by remember { mutableStateOf<DayTag?>(null) }
    var deleting by remember { mutableStateOf(false) }

    val editor: @Composable ColumnScope.(() -> Unit) -> Unit = { close ->
        fun save(archived: Boolean) {
            if (savedTag != null) return
            val tag = DayTag(original?.id ?: UUID.randomUUID().toString(), name.trim(), icon, color, archived)
            try {
                ShiftRepository.saveDayTag(tag)
                focus.clearFocus()
                keyboard?.hide()
                savedTag = tag
                close()
            } catch (problem: IllegalArgumentException) {
                error = problem.message
            }
        }
        if (deleting && original != null) {
            AppConfirmationDialog(
                title = "Eliminare questa etichetta?",
                message = "Non è associata a nessuna giornata. Potrai crearne una nuova quando vuoi.",
                confirmLabel = "Elimina etichetta",
                dismissLabel = "Annulla",
                onDismiss = { deleting = false },
                onConfirm = {
                    deleting = false
                    try {
                        ShiftRepository.deleteUnusedDayTag(original.id)
                        savedTag = original
                        close()
                    } catch (problem: IllegalArgumentException) {
                        error = problem.message
                    }
                },
            )
        }
        if (inlineContent) {
            TagSheetHeading(
                title = if (original == null) "Nuova etichetta" else "Modifica etichetta",
                onClose = onExit ?: close,
                onBack = close,
                showClose = false,
            )
        }
        val canDeleteUnused = original != null &&
            ShiftRepository.dayExtraInfo.values.none { original.id in it.tagIds }
        val inputShape = RoundedCornerShape(13.dp)
        val inputBorder by animateColorAsState(
            targetValue = if (error != null) ColorNegative else ObsidianBorderSubtle,
            animationSpec = tween(180),
            label = "tagNameBorder",
        )
        Column(
            modifier = Modifier
                .weight(1f, fill = !inlineContent)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (inlineContent) 0.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Nome", color = TextSecondary, fontSize = 12.sp)
            BasicTextField(
                value = name,
                onValueChange = { name = it.take(40); error = null },
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(ObsidianPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(inputShape)
                    .background(ObsidianSurfaceContainer)
                    .border(1.dp, inputBorder, inputShape)
                    .padding(14.dp),
                decorationBox = { field ->
                    if (name.isBlank()) Text("Es. Formazione", color = TextSecondary, fontSize = 14.sp)
                    field()
                },
            )
            AnimatedVisibility(visible = error != null) {
                error?.let { Text(it, color = ColorNegative, fontSize = 12.sp) }
            }
            Text("Icona", color = TextSecondary, fontSize = 12.sp)
            dayTagIcons.chunked(3).forEachIndexed { row, icons ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    icons.forEachIndexed { column, key ->
                        val chosen = key == icon
                        val tileFill by animateColorAsState(
                            targetValue = if (chosen) ObsidianPrimaryContainer else ObsidianSurfaceContainer,
                            animationSpec = tween(180),
                            label = "tagIconFill",
                        )
                        val tileBorder by animateColorAsState(
                            targetValue = if (chosen) BrandInk else ObsidianBorderSubtle,
                            animationSpec = tween(180),
                            label = "tagIconBorder",
                        )
                        val tileInk by animateColorAsState(
                            targetValue = if (chosen) BrandInk else TextPrimary,
                            animationSpec = tween(180),
                            label = "tagIconInk",
                        )
                        val tileShape = RoundedCornerShape(11.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(tileShape)
                                .background(tileFill)
                                .border(if (chosen) 1.5.dp else 1.dp, tileBorder, tileShape)
                                .semantics {
                                    contentDescription = tagIconLabels[row * 3 + column]
                                    selected = chosen
                                }
                                .bouncyClickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            SiteIcon(key, null, Modifier.size(22.dp), tileInk)
                        }
                    }
                }
            }
            Text("Colore", color = TextSecondary, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                tagSwatches.forEachIndexed { index, swatch ->
                    val swatchColors = shiftColorStyle(Color(swatch), TurniThemeState.dark, ObsidianSurface)
                    val selectedSwatch = color == swatch
                    val swatchBorder by animateColorAsState(
                        targetValue = if (selectedSwatch) swatchColors.codeInk else ObsidianBorderSubtle,
                        animationSpec = tween(180),
                        label = "tagSwatchBorder",
                    )
                    val swatchShape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(swatchShape)
                            .background(swatchColors.codeFill)
                            .border(if (selectedSwatch) 2.dp else 1.dp, swatchBorder, swatchShape)
                            .semantics {
                                contentDescription = "Colore ${index + 1}"
                                selected = selectedSwatch
                            }
                            .bouncyClickable { color = swatch },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selectedSwatch) {
                            SiteIcon("check", null, Modifier.size(20.dp), swatchColors.codeInk)
                        }
                    }
                }
            }
            DayTagChip(DayTag("preview", name.ifBlank { "La tua etichetta" }, icon, color), selected = true)
            if (original != null) {
                Text(
                    text = "Le modifiche aggiornano anche i giorni già segnati. Archiviare nasconde l’etichetta dalle nuove selezioni e conserva lo storico.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                CloudButton(
                    label = if (original.archived) "Riattiva etichetta" else "Archivia etichetta",
                    icon = "folder",
                    onClick = { save(!original.archived) },
                    enabled = name.isNotBlank() && savedTag == null,
                    secondary = true,
                )
                if (inlineContent && canDeleteUnused) {
                    TextButton(
                        onClick = { deleting = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = ColorNegative),
                    ) {
                        Text("Elimina etichetta inutilizzata")
                    }
                }
            }
            if (!inlineContent) Spacer(Modifier.height(4.dp))
        }
        if (inlineContent) {
            Spacer(Modifier.height(12.dp))
            CloudButton(
                label = "Salva etichetta",
                icon = "check",
                onClick = { save(original?.archived ?: false) },
                enabled = name.isNotBlank() && savedTag == null,
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianBackground)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canDeleteUnused) {
                    val deleteShape = RoundedCornerShape(14.dp)
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(deleteShape)
                            .background(ObsidianSurfaceHigh)
                            .border(1.dp, ObsidianBorderSubtle, deleteShape)
                            .bouncyClickable { deleting = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        SiteIcon("trash-2", "Elimina etichetta inutilizzata", Modifier.size(20.dp), ColorNegative)
                    }
                }
                CloudButton(
                    label = "Salva etichetta",
                    icon = "check",
                    onClick = { save(original?.archived ?: false) },
                    enabled = name.isNotBlank() && savedTag == null,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (inlineContent) {
        with(requireNotNull(inlineScope)) {
            editor { savedTag?.let(onSaved) ?: onClose() }
        }
    } else {
        CatalogEditorSheet(
            title = if (original == null) "Nuova etichetta" else "Modifica etichetta",
            onDismiss = { savedTag?.let(onSaved) ?: onClose() },
            heightFraction = 0.86f,
            subtitle = "Attività da segnare sui giorni",
        ) { finish ->
            editor {
                finish { savedTag?.let(onSaved) ?: onClose() }
            }
        }
    }
}

@Composable
private fun TagSheetHeading(title: String, onClose: () -> Unit,onBack:(()->Unit)?=null,showClose:Boolean=true) {
    Row(Modifier.fillMaxWidth().heightIn(min=36.dp).padding(bottom=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        if(onBack!=null)PanelIcon("arrow-left","Indietro",onBack,width=36.dp,height=36.dp,glyphSize=18.dp,background=ObsidianSurfaceContainer)
        Text(title,color=TextPrimary,fontSize=14.sp,lineHeight=18.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
        if(showClose)PanelIcon("x","Chiudi note ed etichette",onClose,width=36.dp,height=36.dp,glyphSize=18.dp,tint=ColorNegative)
    }
}

@Composable
internal fun ColumnScope.DayAnnotationsContent(date:LocalDate,maxHeight:Dp,onBack:()->Unit,onClose:()->Unit,onFeedback:(ActionFeedback)->Unit) {
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    var notes by remember(date) { mutableStateOf(ShiftRepository.getDayExtraInfo(date).notes) }
    var selected by remember(date) { mutableStateOf(ShiftRepository.getDayExtraInfo(date).tagIds) }
    var creating by remember(date) { mutableStateOf(false) }
    var query by remember(date) { mutableStateOf("") }
    BackHandler { if(creating)creating=false else onBack() }
    AnimatedContent(creating,modifier=Modifier.fillMaxWidth().heightIn(max=maxHeight),
        contentAlignment=Alignment.BottomCenter,transitionSpec={
            (fadeIn(tween(180,delayMillis=70)) togetherWith fadeOut(tween(100))) using
                SizeTransform(clip=true){_,_->tween(360,easing=FastOutSlowInEasing)}
        }) { creatingPage ->
    Column(Modifier.fillMaxWidth().heightIn(max=maxHeight)) {
    if(creatingPage) {
        DayTagEditor(null,{creating=false},inlineContent=true,inlineScope=this,onExit=onClose,onSaved={tag->selected=(selected+tag.id).distinct();query="";creating=false})
    } else {
        TagSheetHeading("Note ed etichette",onClose,onBack)
        Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            val tags=ShiftRepository.dayTags.filter { !it.archived || it.id in selected }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER){it.name})
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Etichette della giornata",Modifier.weight(1f),color=TextSecondary,fontSize=12.sp)
                if(selected.isNotEmpty())Text("${selected.size} ${if(selected.size==1)"selezionata" else "selezionate"}",color=BrandInk,fontSize=11.sp)
            }
            if(tags.isEmpty())Text("Segna attività da ritrovare nelle statistiche, come Formazione o Visita medica.",color=TextSecondary,fontSize=12.sp,lineHeight=18.sp)
            if(tags.size>=7)BasicTextField(query,{query=it},singleLine=true,textStyle=TextStyle(color=TextPrimary,fontSize=12.sp),cursorBrush=SolidColor(ObsidianPrimary),
                modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(ObsidianSurfaceContainer).padding(11.dp),
                decorationBox={field->if(query.isBlank())Text("Cerca un’etichetta",color=TextSecondary,fontSize=12.sp);field()})
            val visibleTags=tags.filter{query.isBlank()||it.name.contains(query.trim(),ignoreCase=true)}
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                visibleTags.forEach { tag -> key(tag.id) {
                    DayTagChip(tag,selected=tag.id in selected,onClick={selected=if(tag.id in selected)selected-tag.id else selected+tag.id})
                } }
            }
            if(tags.isNotEmpty()&&visibleTags.isEmpty())Text("Nessuna etichetta corrisponde alla ricerca",color=TextSecondary,fontSize=12.sp)
            TextButton(onClick={creating=true},colors=ButtonDefaults.textButtonColors(contentColor=BrandInk)) { Text("+ Nuova etichetta") }
            Text("Nota della giornata",color=TextSecondary,fontSize=12.sp)
            BasicTextField(notes,{notes=it.take(500)},textStyle=TextStyle(color=TextPrimary,fontSize=12.sp,lineHeight=17.sp),
                cursorBrush=SolidColor(ObsidianPrimary),modifier=Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(13.dp)).background(ObsidianSurfaceContainer).padding(12.dp),
                decorationBox={field->if(notes.isBlank())Text("Aggiungi i dettagli, se vuoi…",color=TextSecondary,fontSize=12.sp,lineHeight=17.sp);field()})
        }
        Spacer(Modifier.height(12.dp))
        CloudButton("Salva giornata","check",{
            try {
                ShiftRepository.saveDayAnnotations(date,notes,selected)
                focus.clearFocus();keyboard?.hide();onFeedback(ActionFeedback("Note ed etichette salvate"));onBack()
            }catch(problem:IllegalArgumentException) {
                selected=selected.filter { id -> ShiftRepository.dayTags.any { it.id==id } }
                onFeedback(ActionFeedback("Le etichette sono cambiate: ricontrolla e salva",true))
            }
        })
    }
    }
    }
}

private val tagReportDateFormatter=DateTimeFormatter.ofPattern("EEE d MMM yyyy",Locale.ITALIAN)

/** Keep a single grouped surface while composing only the date rows in the viewport. */
internal fun LazyListScope.dayTagReport(metrics:List<DayTagMetric>,periodKey:String,onOpenDate:(LocalDate)->Unit) {
    val prefix="day-tags:$periodKey"
    item("$prefix:intro",contentType="tag-intro") {
        Spacer(Modifier.height(12.dp))
        Surface(color=ObsidianSurface,shape=RoundedCornerShape(topStart=20.dp,topEnd=20.dp),modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start=14.dp,end=14.dp,top=14.dp,bottom=10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Attività ed etichette",color=TextPrimary,fontSize=16.sp,lineHeight=21.sp,fontWeight=FontWeight.SemiBold)
                Text("Tutti i giorni segnati nel periodo, raggruppati per attività. Tocca una data solo se vuoi aprirla nel calendario.",color=TextSecondary,fontSize=12.sp,lineHeight=18.sp)
                if(metrics.isEmpty())Text("Nessuna attività segnata in questo periodo",color=TextSecondary,fontSize=12.sp)
            }
        }
    }
    metrics.forEachIndexed { index,metric ->
        item("$prefix:header:${metric.tag.id}",contentType="tag-header") {
            Surface(color=ObsidianSurface,modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal=14.dp)) {
                    if(index>0)HorizontalDivider(Modifier.padding(top=6.dp,bottom=12.dp),color=ObsidianBorder)
                    Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        val tone=shiftColorStyle(Color(metric.tag.colorHex),TurniThemeState.dark,ObsidianSurface)
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(tone.codeFill),contentAlignment=Alignment.Center){SiteIcon(metric.tag.iconKey,null,Modifier.size(18.dp),tone.codeInk)}
                        Text(metric.tag.name,Modifier.weight(1f),color=TextPrimary,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                        Text("${metric.dates.size} ${if(metric.dates.size==1)"giorno" else "giorni"}",color=TextPrimary,fontWeight=FontWeight.SemiBold,fontSize=13.sp)
                    }
                }
            }
        }
        items(metric.dates,key={date->"$prefix:date:${metric.tag.id}:$date"},contentType={"tag-date"}) { date ->
            val notes=buildList {
                ShiftRepository.getDayExtraInfo(date).notes.takeIf{it.isNotBlank()}?.let{add(it)}
                ShiftRepository.getShiftsForDate(date).map{it.notes}.filter{it.isNotBlank()}.distinct().forEach{add(it)}
            }.distinct()
            Surface(color=ObsidianSurface,modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=14.dp).clip(RoundedCornerShape(12.dp)).bouncyClickable{onOpenDate(date)}.padding(horizontal=8.dp,vertical=8.dp),
                    verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    SiteIcon("calendar",null,Modifier.padding(top=2.dp).size(16.dp),BrandInk)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(date.format(tagReportDateFormatter),color=TextPrimary,fontSize=13.sp,fontWeight=FontWeight.Medium)
                        notes.forEach{Text(it,color=TextSecondary,fontSize=12.sp,lineHeight=17.sp)}
                    }
                    SiteIcon("chevron-right","Apri giorno",Modifier.size(16.dp),TextSecondary)
                }
            }
        }
    }
    item("$prefix:footer",contentType="tag-footer") {
        Surface(color=ObsidianSurface,shape=RoundedCornerShape(bottomStart=20.dp,bottomEnd=20.dp),modifier=Modifier.fillMaxWidth()) {Spacer(Modifier.height(14.dp))}
        Spacer(Modifier.height(12.dp))
    }
}
