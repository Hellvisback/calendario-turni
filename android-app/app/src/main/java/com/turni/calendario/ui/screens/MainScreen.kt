package com.turni.calendario.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.turni.calendario.model.ShiftRotation
import com.turni.calendario.ui.components.TurniDock
import com.turni.calendario.ui.theme.*

sealed class NavItem(val route: String, val label: String, val icon: ImageVector) {
    object Calendario : NavItem("calendar", "Calendario", Icons.Default.CalendarMonth)
    object Report : NavItem("report", "Statistiche", Icons.Default.BarChart)
    object Turni : NavItem("turni", "Turni", Icons.Default.Schedule)
    object Altro : NavItem("altro", "Impostazioni", Icons.Default.Settings)
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainScreen() {
    var route by rememberSaveable { mutableStateOf("calendar") }
    var editMode by remember { mutableStateOf(false) }
    var editRequest by remember { mutableStateOf(0) }
    var subScreen by remember { mutableStateOf(false) }
    var rotation by remember { mutableStateOf<ShiftRotation?>(null) }
    val savedScreens=rememberSaveableStateHolder()
    BackHandler(route != "calendar" && !subScreen) { route = "calendar" }
    Box(Modifier.fillMaxSize().background(ObsidianBackground)) {
        AnimatedContent(route, transitionSpec = {
            (slideInHorizontally(spring(.95f, 400f)) { it / 12 } + fadeIn(tween(180))) with
                fadeOut(tween(100)) using SizeTransform(clip = false)
        }) { destination ->
            savedScreens.SaveableStateProvider(destination) {
            Box(Modifier.fillMaxSize().background(ObsidianBackground)) {
                when (destination) {
                    "calendar" -> CalendarScreen(editRequest = editRequest,
                        onEditModeActiveChanged = { editMode = it; if(!it)editRequest=0 }, onOpenSettings = { route = "altro" })
                    "report" -> ReportScreen(onOpenSettings = { route = "altro" })
                    "turni" -> TurniModelsScreen(onEditorActiveChanged = { subScreen = it },
                        initialTab = if (rotation != null) 2 else 0, initialEditingRotation = rotation,
                        onClearInitialRotation = { rotation = null },
                        onDismiss = { route = "calendar"; subScreen = false })
                    else -> SettingsScreen(onSubScreenActiveChanged = { subScreen = it },
                        onNavigateToTurni = { rotation = it; route = "turni"; subScreen = false },
                        onDismiss = { route = "calendar"; subScreen = false })
                }
            }
            }
        }
        var loadWarning by remember { mutableStateOf(com.turni.calendario.repository.LocalDatabaseStore.loadError) }
        com.turni.calendario.ui.components.AnimatedStatusPopup(message=loadWarning,
            type=com.turni.calendario.ui.components.PopupType.ERROR,onDismiss={loadWarning=null},
            modifier=Modifier.align(Alignment.TopCenter).statusBarsPadding())
        AnimatedVisibility(visible = (route == "calendar" || route == "report" || (route=="altro"&&!subScreen)) && !editMode,
            enter = slideInVertically(spring(.92f, 380f)) { it } + fadeIn(tween(220)),
            exit = slideOutVertically(spring(1f, 520f)) { it } + fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomCenter)) {
            TurniDock(calendarSelected = route == "calendar",primarySelected=route!="altro",
                onCalendar = { route = "calendar"; subScreen = false },
                onStatistics = { route = "report"; subScreen = false }, onEdit = { editMode=true;editRequest++ },
                onModels = { route = "turni"; subScreen = false },
                modifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp))
        }
    }
}
