package com.turni.calendario.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.turni.calendario.model.WorkProfile
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.components.siteVector
import com.turni.calendario.ui.screens.workProfileIcon
import com.turni.calendario.ui.theme.CalendarioTurniTheme
import com.turni.calendario.ui.theme.TurniThemeState
import com.turni.calendario.ui.theme.turniPalette

/**
 * Lightweight dialog activity allowing each home screen widget instance to display
 * either the app's active work profile or a dedicated work profile.
 */
class WidgetWorkProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        setResult(RESULT_CANCELED)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val currentProfileId = WidgetUpdateManager.getWidgetWorkProfileId(this, appWidgetId)
        val activeProfile = ShiftRepository.activeWorkProfile
        val profiles = ShiftRepository.workProfiles.toList()

        setContent {
            CalendarioTurniTheme {
                WidgetWorkProfileDialog(
                    currentProfileId = currentProfileId,
                    activeProfileName = activeProfile.name,
                    activeProfileIconKey = activeProfile.iconKey,
                    profiles = profiles,
                    onSelect = { selectedId ->
                        WidgetUpdateManager.setWidgetWorkProfileId(this, appWidgetId, selectedId)
                        WidgetUpdateManager.updateWidgetById(this, appWidgetId)
                        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        setResult(RESULT_OK, result)
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@Composable
private fun WidgetWorkProfileDialog(
    currentProfileId: String?,
    activeProfileName: String,
    activeProfileIconKey: String,
    profiles: List<WorkProfile>,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = turniPalette(TurniThemeState.dark)
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.94f)
        ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color(palette.surface),
                tonalElevation = 0.dp,
                shadowElevation = 16.dp,
                border = BorderStroke(1.dp, Color(palette.border)),
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .widthIn(max = 400.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(Color(palette.primaryContainer))
                        ) {
                            Icon(
                                imageVector = siteVector("briefcase"),
                                contentDescription = null,
                                tint = Color(palette.primaryInk),
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Lavoro del widget",
                                color = Color(palette.text),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Scegli quale calendario mostrare su questo widget",
                                color = Color(palette.secondaryText),
                                fontSize = 12.5.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProfileOptionCard(
                            title = "Segui lavoro attivo dell'app",
                            subtitle = "Attuale: $activeProfileName",
                            icon = workProfileIcon(activeProfileIconKey),
                            selected = currentProfileId == null,
                            onClick = { onSelect(null) }
                        )
                        profiles.forEach { profile ->
                            ProfileOptionCard(
                                title = profile.name,
                                subtitle = profile.companyOrDepartment.ifBlank { null },
                                icon = workProfileIcon(profile.iconKey),
                                selected = currentProfileId == profile.id,
                                onClick = { onSelect(profile.id) }
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(palette.surfaceHigh),
                        border = BorderStroke(1.dp, Color(palette.border)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .bouncyClickable(onClick = onDismiss)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Chiudi",
                                color = Color(palette.primaryInk),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileOptionCard(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = turniPalette(TurniThemeState.dark)
    val container = if (selected) Color(palette.primaryContainer) else Color(palette.surfaceHigh)
    val borderColor = if (selected) Color(palette.primary) else Color(palette.border)
    val titleColor = if (selected) Color(palette.onPrimaryContainer) else Color(palette.text)
    val subtitleColor = if (selected) Color(palette.onPrimaryContainer).copy(alpha = 0.8f) else Color(palette.secondaryText)
    val iconTileColor = if (selected) Color(palette.primary).copy(alpha = 0.22f) else Color(palette.surface)
    val iconTint = if (selected) Color(palette.primaryInk) else Color(palette.secondaryText)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .bouncyClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(iconTileColor)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(19.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = titleColor,
                    fontSize = 14.5.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = subtitleColor,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selected) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(palette.primary))
                ) {
                    Icon(
                        imageVector = siteVector("check"),
                        contentDescription = null,
                        tint = Color(palette.onPrimary),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
