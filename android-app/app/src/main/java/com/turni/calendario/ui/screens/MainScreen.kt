
package com.turni.calendario.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.tween
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.components.FluidGlassContainer
import com.turni.calendario.ui.components.LiquidGlassLensNavBar
import com.turni.calendario.ui.theme.*

sealed class NavItem(val route: String, val label: String, val icon: ImageVector) {
    object Calendario : NavItem("calendar", "Calendario", Icons.Default.CalendarMonth)
    object Report : NavItem("report", "Report", Icons.Default.BarChart)
    object Turni : NavItem("turni", "Turni", Icons.Default.Schedule)
    object Altro : NavItem("altro", "Altro", Icons.Default.Tune)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MainScreen() {
    var selectedItem by remember { mutableStateOf<NavItem>(NavItem.Calendario) }
    var isSubScreenActive by remember { mutableStateOf(false) }
    var isCalendarEditModeActive by remember { mutableStateOf(false) }
    var targetRotationToEdit by remember { mutableStateOf<com.turni.calendario.model.ShiftRotation?>(null) }

    // Intercetta il tasto indietro di sistema: se siamo in un altro tab, torna a Calendario
    BackHandler(enabled = selectedItem != NavItem.Calendario && !isSubScreenActive && !isCalendarEditModeActive) {
        selectedItem = NavItem.Calendario
    }

    val navItems = listOf(
        NavItem.Calendario,
        NavItem.Report,
        NavItem.Turni,
        NavItem.Altro
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        // Contenuto principale a TUTTO SCHERMO con transizione orizzontale fluida continua (Zero Scatti)
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = selectedItem,
                transitionSpec = {
                    val fromIndex = navItems.indexOf(initialState)
                    val toIndex = navItems.indexOf(targetState)
                    val isForward = toIndex >= fromIndex

                    if (isForward) {
                        (slideInHorizontally(
                            initialOffsetX = { fullWidth -> (fullWidth * 0.45f).toInt() },
                            animationSpec = spring(
                                dampingRatio = 0.86f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) + fadeIn(animationSpec = tween(220))).with(
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> (-fullWidth * 0.45f).toInt() },
                                animationSpec = spring(
                                    dampingRatio = 0.86f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + fadeOut(animationSpec = tween(180))
                        )
                    } else {
                        (slideInHorizontally(
                            initialOffsetX = { fullWidth -> (-fullWidth * 0.45f).toInt() },
                            animationSpec = spring(
                                dampingRatio = 0.86f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) + fadeIn(animationSpec = tween(220))).with(
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> (fullWidth * 0.45f).toInt() },
                                animationSpec = spring(
                                    dampingRatio = 0.86f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + fadeOut(animationSpec = tween(180))
                        )
                    }
                }
            ) { currentNav ->
                when (currentNav) {
                    NavItem.Calendario -> CalendarScreen(
                        onEditModeActiveChanged = { isCalendarEditModeActive = it }
                    )
                    NavItem.Report -> ReportScreen()
                    NavItem.Turni -> TurniModelsScreen(
                        onEditorActiveChanged = { isSubScreenActive = it },
                        initialTab = if (targetRotationToEdit != null) 2 else 0,
                        initialEditingRotation = targetRotationToEdit,
                        onClearInitialRotation = { targetRotationToEdit = null }
                    )
                    NavItem.Altro -> SettingsScreen(
                        onSubScreenActiveChanged = { isSubScreenActive = it },
                        onNavigateToTurni = { rotation ->
                            targetRotationToEdit = rotation
                            selectedItem = NavItem.Turni
                            isSubScreenActive = false
                        }
                    )
                }
            }
        }

        // Floating Navigation Dock (Liquid Glass Optical Lens Bar)
        AnimatedVisibility(
            visible = !isSubScreenActive && !isCalendarEditModeActive,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                LiquidGlassLensNavBar(
                    navItems = navItems,
                    selectedItem = selectedItem,
                    onItemSelected = { item ->
                        selectedItem = item
                        isSubScreenActive = false
                    }
                )
            }
        }
    }
}
