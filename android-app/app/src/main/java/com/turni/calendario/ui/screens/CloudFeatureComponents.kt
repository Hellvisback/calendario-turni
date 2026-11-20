package com.turni.calendario.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.R
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.ActionFeedback
import com.turni.calendario.ui.components.MagicalActionFeedbackHUD
import com.turni.calendario.ui.components.SiteIcon
import com.turni.calendario.ui.components.SiteIconMotion
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.theme.BrandInk
import com.turni.calendario.ui.theme.ObsidianBackground
import com.turni.calendario.ui.theme.ObsidianBorder
import com.turni.calendario.ui.theme.ObsidianBorderSubtle
import com.turni.calendario.ui.theme.ObsidianPrimary
import com.turni.calendario.ui.theme.ObsidianSurface
import com.turni.calendario.ui.theme.ObsidianSurfaceContainer
import com.turni.calendario.ui.theme.ObsidianSurfaceHigh
import com.turni.calendario.ui.theme.OnBrand
import com.turni.calendario.ui.theme.TextPrimary
import com.turni.calendario.ui.theme.TextSecondary
import com.turni.calendario.ui.theme.TurniThemeState
import kotlinx.coroutines.launch

@Composable
internal fun CloudFeatureScaffold(
    title: String,
    onDismiss: () -> Unit,
    feedback: ActionFeedback? = null,
    onDismissFeedback: () -> Unit = {},
    scrollResetKey: Any? = null,
    subtitle: String? = null,
    headerAction: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val scroll = rememberScrollState()
    LaunchedEffect(scrollResetKey) {
        if (scrollable) {
            scroll.scrollTo(0)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .statusBarsPadding()
            .let { if (bottomBar == null) it.navigationBarsPadding() else it },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val backShape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(backShape)
                        .background(ObsidianSurface)
                        .border(1.dp, ObsidianBorderSubtle, backShape)
                        .bouncyClickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    SiteIcon(
                        name = "arrow-left",
                        contentDescription = "Indietro",
                        modifier = Modifier.size(20.dp),
                        tint = TextPrimary,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 22.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (headerAction != null) {
                    headerAction()
                }
            }

            val bodyModifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .let { if (scrollable) it.verticalScroll(scroll) else it }
                .padding(horizontal = 16.dp)

            Column(
                modifier = bodyModifier,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
                if (scrollable) {
                    Spacer(Modifier.height(if (bottomBar == null) 24.dp else 12.dp))
                }
            }

            if (bottomBar != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianBackground)
                        .navigationBarsPadding(),
                ) {
                    SettingsDivider()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        bottomBar()
                    }
                }
            }
        }
        MagicalActionFeedbackHUD(feedback, onDismissFeedback, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
internal fun CloudCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianBorderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
            content()
        }
    }
}

/**
 * Sliding segmented bar with an animated Cobalt pill and zero click-flash.
 *
 * Uses `indication = null` and 180ms color interpolation on each item's label/icon
 * so the foreground tint transitions in lockstep with the sliding pill.
 */
@Composable
internal fun SlidingSegmentBar(
    items: List<Pair<String, String?>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 46.dp,
) {
    if (items.isEmpty()) return
    val safeSelected = selectedIndex.coerceIn(0, items.lastIndex)
    val haptic = LocalHapticFeedback.current
    val fontScale = LocalDensity.current.fontScale
    val trackShape = RoundedCornerShape(16.dp)
    val pillShape = RoundedCornerShape(12.dp)
    val trackPadding = 4.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(trackShape)
            .background(ObsidianSurfaceContainer)
            .border(1.dp, ObsidianBorderSubtle, trackShape)
            .padding(trackPadding)
            .selectableGroup(),
    ) {
        val segmentWidth = maxWidth / items.size
        val isCompact = items.size >= 4 || segmentWidth < 96.dp
        val labelFontSize = when {
            items.size >= 4 && (segmentWidth < 86.dp || fontScale > 1.1f) -> 11.sp
            items.size >= 4 -> 11.5.sp
            else -> 12.sp
        }
        val iconSize = if (isCompact) 14.dp else 16.dp
        val iconGap = if (isCompact) 3.dp else 6.dp
        val horizontalPadding = if (isCompact) 3.dp else 6.dp
        val maxLabelLength = items.maxOf { it.first.length }
        val estimatedMaxLabelDp = (maxLabelLength * labelFontSize.value * 0.62f * fontScale).dp
        val showIcons = items.any { it.second != null } &&
            (estimatedMaxLabelDp + iconSize + iconGap + horizontalPadding * 2 <= segmentWidth)

        val pillOffset by animateDpAsState(
            targetValue = segmentWidth * safeSelected,
            animationSpec = spring(
                dampingRatio = 0.85f,
                stiffness = Spring.StiffnessMedium,
            ),
            label = "slidingSegmentPillOffset",
        )

        Box(
            modifier = Modifier
                .offset(x = pillOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(pillShape)
                .background(ObsidianPrimary.copy(alpha = if (enabled) 1f else 0.5f)),
        )

        Row(modifier = Modifier.fillMaxSize()) {
            items.forEachIndexed { index, (label, iconKey) ->
                val isSelected = index == safeSelected
                val interactionSource = remember(index) { MutableInteractionSource() }
                val targetInk = (if (isSelected) OnBrand else TextSecondary)
                    .copy(alpha = if (enabled) 1f else 0.5f)
                val ink by animateColorAsState(
                    targetValue = targetInk,
                    animationSpec = tween(durationMillis = 180),
                    label = "slidingSegmentInk",
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(pillShape)
                        .semantics { selected = isSelected }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = enabled,
                            role = Role.Tab,
                        ) {
                            if (enabled && index != safeSelected) {
                                try {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } catch (_: Exception) {
                                }
                                onSelect(index)
                            }
                        }
                        .padding(horizontal = horizontalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (showIcons && iconKey != null) {
                        SiteIcon(
                            name = iconKey,
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = ink,
                        )
                        Spacer(Modifier.width(iconGap))
                    }
                    Text(
                        text = label,
                        color = ink,
                        fontSize = labelFontSize,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TurniSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = 320f,
        ),
        label = "turniSwitchThumbOffset",
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) ObsidianPrimary else ObsidianSurfaceHigh,
        animationSpec = tween(durationMillis = 200),
        label = "turniSwitchTrackColor",
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) ObsidianPrimary else ObsidianBorder,
        animationSpec = tween(durationMillis = 200),
        label = "turniSwitchBorderColor",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) OnBrand else TextSecondary,
        animationSpec = tween(durationMillis = 200),
        label = "turniSwitchThumbColor",
    )

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { next ->
                    try {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } catch (_: Exception) {
                    }
                    onCheckedChange(next)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 28.dp)
                .alpha(if (enabled) 1f else 0.48f)
                .clip(CircleShape)
                .background(trackColor)
                .border(1.dp, borderColor, CircleShape),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(thumbColor),
            )
        }
    }
}

@Composable
internal fun SettingsDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = ObsidianBorderSubtle,
    )
}

@Composable
internal fun CloudButton(
    label: String,
    icon: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
) {
    val iconMotion = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val buttonShape = RoundedCornerShape(14.dp)
    val fill = when {
        destructive -> if (TurniThemeState.dark) Color(0xFF442330) else Color(0xFFFFE9ED)
        secondary -> ObsidianSurfaceHigh
        else -> ObsidianPrimary
    }
    val ink = when {
        destructive -> if (TurniThemeState.dark) Color(0xFFFFADB6) else Color(0xFF9C2940)
        secondary -> TextPrimary
        else -> catalogInk(fill)
    }
    val contentAlpha = if (enabled) 1f else 0.45f
    Surface(
        shape = buttonShape,
        color = fill,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clip(buttonShape)
            .bouncyClickable(enabled) {
                scope.launch {
                    iconMotion.snapTo(0f)
                    iconMotion.animateTo(1f, tween(340))
                }
                onClick()
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            SiteIconMotion(icon, null, Modifier.size(18.dp), ink.copy(alpha = contentAlpha)) {
                iconMotion.value
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = ink.copy(alpha = contentAlpha),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Google branding stays recognizable in both application themes. */
@Composable
internal fun GoogleSignInButton(
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val buttonShape = RoundedCornerShape(24.dp)
    Surface(
        color = Color.White,
        shape = buttonShape,
        border = BorderStroke(1.dp, Color(0xFF747775)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(buttonShape)
            .bouncyClickable(enabled, onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.google_g),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (loading) "Collegamento…" else "Accedi con Google",
                color = Color(0xFF1F1F1F).copy(alpha = if (enabled) 1f else 0.55f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
internal fun GoogleAccountCard(
    access: GoogleAccess,
    onFeedback: (ActionFeedback) -> Unit,
    enabled: Boolean = true,
    feature: GoogleFeature = GoogleFeature.PROFILE,
    explanation: String? = null,
    onConnected: ((String) -> Unit)? = null,
) {
    var showAccessDetails by remember { mutableStateOf(false) }
    val profile = ShiftRepository.googleAccountProfile
    fun connect() {
        access.authorize(feature, true) { token ->
            onConnected?.invoke(token) ?: onFeedback(ActionFeedback("Account Google collegato"))
        }
    }
    CloudCard(
        title = "Account Google",
        subtitle = if (profile == null) {
            explanation ?: "Accedi con il tuo account. I tuoi dati rimangono nel tuo spazio Google."
        } else {
            null
        },
    ) {
        if (profile == null) {
            GoogleSignInButton(enabled && !access.busy, access.busy, ::connect)
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianSurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.google_g),
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .padding(2.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = profile.displayName,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = profile.email,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = ::connect,
                    enabled = enabled && !access.busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = BrandInk),
                ) {
                    Text("Cambia account", color = BrandInk, fontSize = 12.sp)
                }
                TextButton(
                    onClick = {
                        access.disconnect {
                            onFeedback(ActionFeedback("Account Google scollegato"))
                        }
                    },
                    enabled = enabled && !access.busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = BrandInk),
                ) {
                    Text("Disconnetti", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
        TextButton(
            onClick = { showAccessDetails = !showAccessDetails },
            colors = ButtonDefaults.textButtonColors(contentColor = BrandInk),
        ) {
            Text("Quali dati autorizzo?", fontSize = 12.sp)
        }
        AnimatedVisibility(
            visible = showAccessDetails,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = when (feature) {
                        GoogleFeature.DRIVE -> "Google conferma nome ed email e permette a TurniSync di usare il proprio spazio privato su Drive. Gli altri file di Drive non vengono letti. Collegare l’account non avvia un backup."
                        GoogleFeature.CALENDAR -> "Google conferma nome ed email, mostra i calendari e chiede il permesso di gestire eventi. Nell’app scegli la destinazione: TurniSync gestisce gli eventi che ha creato, riconoscibili dal proprio contrassegno."
                        else -> "Google conferma nome ed email dell’account scelto. La password rimane gestita da Google."
                    },
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Text(
                    text = "Durante la beta l’accesso può essere limitato agli account di prova. Il consenso Google e le eventuali limitazioni dipendono dalla configurazione del progetto; cambiare account non trasferisce automaticamente i dati.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}
