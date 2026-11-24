package com.turni.calendario.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.turni.calendario.repository.ShiftRepository
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
    profiles: List<com.turni.calendario.model.WorkProfile>,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = turniPalette(TurniThemeState.dark)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(palette.surface),
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, Color(palette.border))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(palette.primaryContainer),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WorkOutline,
                            contentDescription = null,
                            tint = Color(palette.primaryInk),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lavoro del widget",
                            color = Color(palette.text),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Scegli quale calendario mostrare",
                            color = Color(palette.secondaryText),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileOptionRow(
                        title = "Segui lavoro attivo dell'app",
                        subtitle = "Attuale: $activeProfileName",
                        selected = currentProfileId == null,
                        onClick = { onSelect(null) }
                    )
                    profiles.forEach { profile ->
                        ProfileOptionRow(
                            title = profile.name,
                            subtitle = profile.companyOrDepartment.ifBlank { null },
                            selected = currentProfileId == profile.id,
                            onClick = { onSelect(profile.id) }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Chiudi",
                            color = Color(palette.primaryInk),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileOptionRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = turniPalette(TurniThemeState.dark)
    val container = if (selected) Color(palette.primarySubtle) else Color(palette.container)
    val borderColor = if (selected) Color(palette.primary) else Color(palette.border)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = container,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Color(palette.primary),
                    unselectedColor = Color(palette.outline)
                )
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color(palette.text),
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = Color(palette.secondaryText),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Color(palette.primaryInk),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
