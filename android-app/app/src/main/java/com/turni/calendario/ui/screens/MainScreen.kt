package com.turni.calendario.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
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
    val editMode = remember { mutableStateOf(false) }
    val editRequest = remember { mutableStateOf(0) }
    var subScreen by remember { mutableStateOf(false) }
    var rotation by remember { mutableStateOf<ShiftRotation?>(null) }
    val savedScreens=rememberSaveableStateHolder()
    var reportVisited by remember { mutableStateOf(false) }
    if(route=="report")reportVisited=true
    LaunchedEffect(Unit){kotlinx.coroutines.delay(900);reportVisited=true}
    LaunchedEffect(com.turni.calendario.repository.ShiftRepository.selectedDateFromWidget) {
        if(com.turni.calendario.repository.ShiftRepository.selectedDateFromWidget!=null){route="calendar";subScreen=false}
    }
    BackHandler(route != "calendar" && !subScreen) { route = "calendar" }
    Box(Modifier.fillMaxSize().background(ObsidianBackground)) {
        AnimatedContent(if(route=="calendar"||route=="report")"primary" else route, transitionSpec = {
            // Destinations fill the same viewport: avoid remeasuring both trees on every slide frame.
            fadeIn(tween(220)) with fadeOut(tween(140)) using null
        }) { destination ->
            savedScreens.SaveableStateProvider(destination) {
            Box(Modifier.fillMaxSize().background(ObsidianBackground)) {
                when (destination) {
                    "primary", "calendar" -> Unit
                    "report" -> Unit // Report composition is retained outside the transition tree.
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
        RetainedDestination(visible=route=="calendar") {
            savedScreens.SaveableStateProvider("retained-calendar") {
                CalendarDestination(active=route=="calendar",editRequest=editRequest,
                    onEditModeActiveChanged={editMode.value=it;if(!it)editRequest.value=0},onOpenSettings={route="altro"})
            }
        }
        if(reportVisited) {
            RetainedDestination(visible=route=="report") {
                ReportScreen(active=route=="report",onOpenSettings={route="altro"})
            }
        }
        var loadWarning by remember { mutableStateOf(com.turni.calendario.repository.LocalDatabaseStore.loadError) }
        com.turni.calendario.ui.components.AnimatedStatusPopup(message=loadWarning,
            type=com.turni.calendario.ui.components.PopupType.ERROR,onDismiss={loadWarning=null},
            modifier=Modifier.align(Alignment.TopCenter).statusBarsPadding())
        MainDockVisibility(visible = { (route == "calendar" || route == "report" || (route=="altro"&&!subScreen)) && !editMode.value },
            enter = slideInVertically(spring(.92f, 380f)) { it } + fadeIn(tween(220)),
            exit = slideOutVertically(spring(1f, 520f)) { it } + fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomCenter)) {
            TurniDock(calendarSelected = route == "calendar",primarySelected=route!="altro",
                onCalendar = { route = "calendar"; subScreen = false },
                onStatistics = { route = "report"; subScreen = false }, onEdit = { editMode.value=true;editRequest.value++ },
                onModels = { route = "turni"; subScreen = false },
                modifier = Modifier.navigationBarsPadding().padding(bottom = 0.dp))
        }
    }
}

/** Editing state invalidates its destination and dock, never the root navigation tree. */
@Composable
private fun CalendarDestination(active:Boolean,editRequest:State<Int>,onEditModeActiveChanged:(Boolean)->Unit,onOpenSettings:()->Unit){
    CalendarScreen(active=active,editRequest=editRequest.value,onEditModeActiveChanged=onEditModeActiveChanged,onOpenSettings=onOpenSettings)
}
@Composable
private fun MainDockVisibility(visible:()->Boolean,enter:EnterTransition,exit:ExitTransition,modifier:Modifier,content:@Composable AnimatedVisibilityScope.()->Unit){
    AnimatedVisibility(visible=visible(),enter=enter,exit=exit,modifier=modifier,content=content)
}

/** Retain state with a short arrival motion, without two full-screen alpha buffers. */
@Composable
private fun RetainedDestination(visible:Boolean,content:@Composable ()->Unit) {
    // Prepare the first visible frame during composition, not in a later effect.
    // A retained, inactive page must never flash its previous resting position.
    val arrival=remember(visible){Animatable(if(visible)1f else 0f)}
    LaunchedEffect(arrival){if(visible)arrival.animateTo(0f,tween(240,easing=androidx.compose.animation.core.FastOutSlowInEasing))}
    val hiddenSemantics=if(visible)Modifier else Modifier.clearAndSetSemantics{}
    Box(Modifier.fillMaxSize().then(hiddenSemantics).zIndex(if(visible)0f else -1f).graphicsLayer{translationY=arrival.value*14.dp.toPx();clip=true}.layout{measurable,constraints->
        val child=measurable.measure(constraints)
        layout(child.width,child.height){child.place(if(visible)0 else child.width*2,0)}
    }) { content() }
}
