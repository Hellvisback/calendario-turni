@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.turni.calendario.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.turni.calendario.model.*
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.components.TurniEraserIcon
import com.turni.calendario.ui.components.SiteIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalTime
import java.util.UUID

/** Approved defaults; existing custom colors remain untouched. */
val catalogPastelColors = listOf(0xFFF2C46DL, 0xFFEE9C9CL, 0xFFBAAEF6L, 0xFF83CDB7L, 0xFF94C4EFL, 0xFFAAB9CEL)
internal fun catalogInk(color: Color): Color {
    val dark = Color(0xFF171923)
    val luminance = color.luminance()
    val darkContrast = (luminance + .05f) / (dark.luminance() + .05f)
    val lightContrast = 1.05f / (luminance + .05f)
    return if (darkContrast >= lightContrast) dark else Color.White
}

@Composable
internal fun CatalogGroup(title: String, outlined: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(if (outlined) 19.dp else 16.dp), color = MaterialTheme.colorScheme.surface,
        border = if(outlined) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=.3f)) else null,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal=if(outlined)13.dp else 10.dp,vertical=10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
internal fun CatalogToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val color by animateColorAsState(if(checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
    val offset by androidx.compose.animation.core.animateDpAsState(if(checked)18.dp else 2.dp,spring(.9f,450f))
    Row(Modifier.fillMaxWidth().heightIn(min = 38.dp).toggleable(value=checked,role=Role.Switch,onValueChange=onCheckedChange), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp), fontSize = 12.sp)
        Box(Modifier.width(40.dp).height(24.dp).clip(CircleShape).background(color)) {
            Box(Modifier.offset(x=offset,y=2.dp).size(20.dp).background(if(checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,CircleShape))
        }
    }
}

/** Matches the site's external label, compact filled input and square typography. */
@Composable
private fun CatalogField(label:String,value:String,change:(String)->Unit,modifier:Modifier=Modifier,
    numeric:Boolean=false,error:Boolean=false,height:androidx.compose.ui.unit.Dp=40.dp,centered:Boolean=false) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(label,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextField(value=value,onValueChange=change,singleLine=true,
            textStyle=TextStyle(color=MaterialTheme.colorScheme.onSurface,fontFamily=MaterialTheme.typography.bodyMedium.fontFamily,
                fontSize=if(centered)17.sp else 13.sp,fontWeight=if(centered)FontWeight.SemiBold else FontWeight.Normal,
                textAlign=if(centered)TextAlign.Center else TextAlign.Start),
            keyboardOptions=KeyboardOptions(keyboardType=if(numeric)KeyboardType.Number else KeyboardType.Text),
            cursorBrush=androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            modifier=Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp,if(error)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha=.25f),RoundedCornerShape(12.dp))
                .padding(horizontal=10.dp).semantics{contentDescription=label},
            decorationBox={inner->Box(Modifier.fillMaxSize(),contentAlignment=if(centered)Alignment.Center else Alignment.CenterStart){inner()}})
    }
}

@Composable
internal fun CatalogButton(label:String,action:()->Unit,modifier:Modifier=Modifier,primary:Boolean=false,enabled:Boolean=true,icon:String?=null) {
    val colors=MaterialTheme.colorScheme
    val phase=remember{Animatable(0f)};val scope=rememberCoroutineScope()
    Box(modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(if(primary)colors.primary else colors.surfaceVariant)
        .bouncyClickable(enabled=enabled,onClick={scope.launch{phase.snapTo(0f);phase.animateTo(1f,tween(620));phase.snapTo(0f)};action()}).padding(horizontal=10.dp),contentAlignment=Alignment.Center) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        if(icon!=null)SiteIcon(icon,null,Modifier.size(17.dp),if(primary)colors.onPrimary else colors.onSurface,phase.value)
        Text(label,fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=(if(primary)colors.onPrimary else colors.onSurface).copy(alpha=if(enabled)1f else .4f))
        }
    }
}

@Composable
internal fun CatalogIconAction(name:String,label:String,action:()->Unit,modifier:Modifier=Modifier,tint:Color=MaterialTheme.colorScheme.onSurface) {
    val phase=remember{Animatable(0f)};val scope=rememberCoroutineScope()
    Box(modifier.width(40.dp).height(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable {
        scope.launch{phase.snapTo(0f);phase.animateTo(1f,tween(620));phase.snapTo(0f)};action()
    },contentAlignment=Alignment.Center) { SiteIcon(name,label,Modifier.size(20.dp),tint,phase.value) }
}

/** One real sheet, with finite spring entry/exit and stable independent scrolling. */
@Composable
internal fun CatalogEditorSheet(title: String, onDismiss: () -> Unit,heightFraction:Float=.82f,
    subtitle:String="Modello per il tuo calendario",fullPage:Boolean=false,
    content: @Composable ColumnScope.((() -> Unit) -> Unit) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val headerProgress=remember{Animatable(0f)}
    val finish: (() -> Unit) -> Unit = { action ->
        if (!closing) {
            closing = true
            visible = false
            scope.launch { delay(280); action() }
        }
    }
    LaunchedEffect(Unit) { visible = true;headerProgress.animateTo(1f,tween(720));headerProgress.snapTo(0f) }
    BackHandler { finish(onDismiss) }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .24f)).clickable(enabled = !closing) { finish(onDismiss) })
        }
        AnimatedVisibility(visible, modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = .88f, stiffness = Spring.StiffnessLow)) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()) {
            Surface(Modifier.fillMaxWidth().fillMaxHeight(heightFraction), shape = RoundedCornerShape(topStart = if(fullPage)0.dp else 24.dp, topEnd = if(fullPage)0.dp else 24.dp), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.navigationBarsPadding().imePadding()) {
                    Row(Modifier.fillMaxWidth().background(if(heightFraction>.9f)MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background).padding(horizontal=14.dp,vertical=14.dp), verticalAlignment = Alignment.CenterVertically) {
                        if(fullPage || heightFraction>.9f)CatalogIconAction("arrow-left","Indietro",{finish(onDismiss)},Modifier.size(40.dp))
                        else SiteIcon("pencil",null,Modifier.size(28.dp),MaterialTheme.colorScheme.onSurfaceVariant,headerProgress.value)
                        Column(Modifier.weight(1f).padding(start=8.dp)) {
                            Text(title, fontWeight = FontWeight.Bold, fontSize = if(fullPage)25.sp else if(heightFraction>.9f)21.sp else 18.sp)
                            if(subtitle.isNotBlank())Text(subtitle,fontSize=if(heightFraction>.9f)11.sp else 10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=3.dp))
                        }
                        if(!fullPage&&heightFraction<.9f)CatalogIconAction("x","Chiudi",{finish(onDismiss)},Modifier.size(38.dp))
                    }
                    content(finish)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalColorPickerRow(selectedColorHex: Long, showNames:Boolean=false, onColorSelected: (Long) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            catalogPastelColors.forEachIndexed { index,value ->
                Column(Modifier.weight(1f).bouncyClickable{onColorSelected(value)},horizontalAlignment=Alignment.CenterHorizontally) {
                    Box(Modifier.size(if(showNames)34.dp else 32.dp).clip(RoundedCornerShape(if(showNames)11.dp else 9.dp)).background(Color(value)),contentAlignment=Alignment.Center) {
                        if(value==selectedColorHex)SiteIcon("check","Selezionato",Modifier.size(16.dp),catalogInk(Color(value)))
                    }
                    if(showNames)Text(listOf("Ambra","Corallo","Lavanda","Menta","Azzurro","Perla")[index],fontSize=9.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=5.dp))
                }
            }
        }
        if(showNames) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Colore personalizzato", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(colorToHex(Color(selectedColorHex)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(selectedColorHex)).bouncyClickable { showPicker = true }, contentAlignment = Alignment.Center) {
            SiteIcon("pipette", "Scegli colore personalizzato",Modifier.size(17.dp),catalogInk(Color(selectedColorHex)))
        }
        } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Bottom) {
            Column(Modifier.width(58.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text("Colore",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(selectedColorHex)).bouncyClickable{showPicker=true},contentAlignment=Alignment.Center) {
                    SiteIcon("pipette","Scegli colore",Modifier.size(17.dp),catalogInk(Color(selectedColorHex)))
                }
            }
            var hexDraft by remember(selectedColorHex){mutableStateOf(colorToHex(Color(selectedColorHex)))}
            CatalogField("HEX",hexDraft,{hexDraft=it.take(7).uppercase();if(Regex("#[0-9A-F]{6}").matches(hexDraft))parseHexToColor(hexDraft)?.let{value->onColorSelected(colorToLong(value))}},Modifier.weight(1f),error=!Regex("#[0-9A-F]{6}").matches(hexDraft))
        }
    }
    if (showPicker) CustomColorPickerDialog(selectedColorHex, { showPicker = false }) { onColorSelected(it); showPicker = false }
}

@Composable
internal fun CatalogWorkIcon(key:String,modifier:Modifier=Modifier,tint:Color=MaterialTheme.colorScheme.onSurfaceVariant) {
    val scale=remember{Animatable(1f)}
    LaunchedEffect(key){scale.snapTo(.82f);scale.animateTo(1.12f,tween(220));scale.animateTo(1f,spring(.8f,350f))}
    val name=when(key){"business"->"building";"people","groups"->"users";"computer","laptop"->"laptop";"factory","build","wrench"->"wrench";"hospital","medical","health"->"hospital";"shipping","truck","local_shipping"->"truck";"transit","train"->"train";"security"->"shield";"store"->"store";else->"briefcase-business"}
    SiteIcon(name,null,modifier.graphicsLayer{scaleX=scale.value;scaleY=scale.value},tint)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomColorPickerDialog(initialColorHex: Long, onDismiss: () -> Unit, onColorSelected: (Long) -> Unit) {
    val initialHsv = remember(initialColorHex) { FloatArray(3).also { android.graphics.Color.colorToHSV(initialColorHex.toInt(), it) } }
    var hue by remember { mutableStateOf(initialHsv[0]) }
    var saturation by remember { mutableStateOf(initialHsv[1]) }
    var brightness by remember { mutableStateOf(initialHsv[2]) }
    var hex by remember { mutableStateOf(colorToHex(Color(initialColorHex))) }
    val selected = hsvToColor(hue, saturation, brightness)
    val valid = Regex("#[0-9a-fA-F]{6}").matches(hex)
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun finish(action: () -> Unit) {
        if (!closing) {
            closing = true
            visible = false
            scope.launch { delay(220); action() }
        }
    }
    LaunchedEffect(Unit) { visible = true }
    fun updateHex() { hex = colorToHex(hsvToColor(hue, saturation, brightness)) }
    Dialog(onDismissRequest = { finish(onDismiss) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.BottomCenter) {
        Box(Modifier.fillMaxSize().clickable{finish(onDismiss)})
        AnimatedVisibility(visible, enter = slideInVertically(spring(dampingRatio = .9f, stiffness = Spring.StiffnessLow)) { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        Surface(Modifier.fillMaxWidth().heightIn(max=560.dp), shape = RoundedCornerShape(topStart=24.dp,topEnd=24.dp), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.navigationBarsPadding().padding(start=16.dp,end=16.dp,top=16.dp,bottom=26.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Colore personalizzato",fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                        Text("Palette e personalizzazione",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=3.dp))
                    }
                    IconButton(onClick = { finish(onDismiss) }, enabled = !closing,modifier=Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) { SiteIcon("x", "Chiudi",Modifier.size(18.dp)) }
                }
                Row(Modifier.fillMaxWidth().padding(bottom=4.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    catalogPastelColors.forEach { value->Box(Modifier.weight(1f).heightIn(max=42.dp).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color(value)).bouncyClickable {
                        val hsv=FloatArray(3);android.graphics.Color.colorToHSV(value.toInt(),hsv);hue=hsv[0];saturation=hsv[1];brightness=hsv[2];updateHex()
                    },contentAlignment=Alignment.Center){if(colorToLong(selected)==value)SiteIcon("check",null,Modifier.size(17.dp),catalogInk(Color(value)))} }
                }
                NativeColorPlane(hue,saturation,brightness){s,v->saturation=s;brightness=v;updateHex()}
                Text("Tonalità", fontSize = 12.sp)
                Box(Modifier.fillMaxWidth().height(36.dp),contentAlignment=Alignment.Center) {
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red))))
                    Slider(hue, { hue = it; updateHex() }, valueRange = 0f..360f,
                        colors=SliderDefaults.colors(activeTrackColor=Color.Transparent,inactiveTrackColor=Color.Transparent))
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                CatalogField("HEX",hex, {
                    hex = it.take(7).uppercase()
                    if (Regex("#[0-9A-F]{6}").matches(hex)) {
                        val hsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(hex), hsv)
                        if (hsv[1] > 0f) hue = hsv[0]
                        saturation = hsv[1]; brightness = hsv[2]
                    }
                },Modifier.weight(1f),error=!valid,height=42.dp)
                Box(Modifier.width(58.dp).height(42.dp).clip(RoundedCornerShape(12.dp)).background(selected))
                }
                Text(if(valid)"" else "Inserisci un colore HEX valido",fontSize=10.sp,color=MaterialTheme.colorScheme.error,modifier=Modifier.height(19.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CatalogButton("Annulla",{finish(onDismiss)},Modifier.weight(1f),enabled=!closing)
                    CatalogButton("Imposta",{parseHexToColor(hex)?.let{value->finish{onColorSelected(colorToLong(value))}}},Modifier.weight(1f),primary=true,enabled=valid&&!closing)
                }
            }
        }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftEditorScreen(shift: ShiftDefinition, onDismiss: () -> Unit, onSave: (ShiftDefinition) -> Unit, onDelete: (String) -> Unit) {
    var name by remember(shift.id) { mutableStateOf(shift.name) }
    var code by remember(shift.id) { mutableStateOf(shift.shortCode.take(4)) }
    var start by remember(shift.id) { mutableStateOf(shift.startTime) }
    var end by remember(shift.id) { mutableStateOf(shift.endTime) }
    var allDay by remember(shift.id) { mutableStateOf(shift.isAllDay) }
    var hasBreak by remember(shift.id) { mutableStateOf(shift.breakMinutes > 0) }
    var pause by remember(shift.id) { mutableStateOf(shift.breakMinutes.toString()) }
    var breakStart by remember(shift.id) { mutableStateOf(shift.breakStartTime ?: "12:00") }
    var color by remember(shift.id) { mutableStateOf(shift.colorHex) }
    var workId by remember(shift.id) { mutableStateOf(shift.workProfileId) }
    var category by remember(shift.id) { mutableStateOf(shift.category) }
    var workMenu by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    val existing = ShiftRepository.shiftDefinitions.any { it.id == shift.id }
    fun validTime(value: String) = runCatching { LocalTime.parse(value) }.isSuccess
    val validCode = code.matches(Regex("[A-Z0-9]{1,4}"))
    val valid = name.isNotBlank() && validCode && (allDay || (validTime(start) && validTime(end) && (!hasBreak || (validTime(breakStart) && (pause.toIntOrNull() ?: -1) in 0..720))))
    fun draft() = shift.copy(name = name.trim(), shortCode = code, startTime = start, endTime = end, colorHex = color, workProfileId = workId, category = category, isAllDay = allDay, breakMinutes = if (hasBreak && !allDay) pause.toIntOrNull() ?: 0 else 0, breakStartTime = if (hasBreak && !allDay) breakStart else null)
    CatalogEditorSheet(if (existing) "Modifica turno" else "Nuovo turno", onDismiss) { finish ->
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surfaceVariant,Color(color),.10f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(color)), contentAlignment = Alignment.Center) { Text(code, color = catalogInk(Color(color)), fontWeight = FontWeight.Bold,fontSize=11.sp) }
                Column(Modifier.padding(start = 9.dp)) { Text(name.ifBlank { "Il tuo turno" }, fontWeight = FontWeight.Bold,fontSize=14.sp); Text(if (allDay) "Tutto il giorno" else "$start – $end", fontSize = 11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=4.dp)) }
            }
            CatalogGroup("Identità") {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    CatalogField("Nome del turno",name,{name=it.take(60)},Modifier.weight(2f))
                    CatalogField("Sigla",code,{code=it.uppercase().filter{ch->ch in 'A'..'Z'||ch in '0'..'9'}.take(4)},Modifier.weight(1f),error=!validCode)
                }
                if (ShiftRepository.workProfiles.size > 1) Box {
                    OutlinedButton(onClick = { workMenu = !workMenu }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Icon(getWorkProfileIcon(ShiftRepository.workProfiles.firstOrNull { it.id == workId }?.iconKey ?: "work"), null, Modifier.size(18.dp))
                        Text(ShiftRepository.workProfiles.firstOrNull { it.id == workId }?.name ?: "Seleziona lavoro", Modifier.weight(1f).padding(horizontal = 10.dp), textAlign = TextAlign.Start, fontSize = 13.sp)
                        Icon(Icons.Default.ExpandMore, null)
                    }
                    DropdownMenu(expanded = workMenu, onDismissRequest = { workMenu = false }) {
                        ShiftRepository.workProfiles.forEach { profile -> DropdownMenuItem(text = { Text(profile.name) }, onClick = { workId = profile.id; workMenu = false }) }
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ShiftCategory.LAVORO to "Lavoro", ShiftCategory.RIPOSO to "Riposo", ShiftCategory.ASSENZA to "Assenza", ShiftCategory.ALTRO to "Altro").forEach { (value, label) ->
                        Surface(shape = RoundedCornerShape(10.dp), color = if (category == value) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background, modifier = Modifier.bouncyClickable { category = value }) {
                            Text(label, Modifier.padding(horizontal = 8.dp, vertical = 6.dp), color = if (category == value) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        }
                    }
                }
            }
            CatalogGroup("Orario") {
                CatalogToggle("Tutto il giorno", allDay) { allDay = it }
                AnimatedVisibility(!allDay, enter = expandVertically(spring()) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CatalogTimeField("Inizio", start, { start = it }, Modifier.weight(1f))
                        CatalogTimeField("Fine", end, { end = it }, Modifier.weight(1f))
                    }
                }
            }
            AnimatedVisibility(!allDay) {
                CatalogGroup("Pausa") {
                    CatalogToggle("Sottrai la pausa", hasBreak) { hasBreak = it }
                    AnimatedVisibility(hasBreak) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CatalogTimeField("Dalle", breakStart, { breakStart = it }, Modifier.weight(1f))
                            CatalogField("Minuti",pause,{pause=it.filter(Char::isDigit).take(3)},Modifier.weight(1f),numeric=true)
                        }
                    }
                    AnimatedVisibility(hasBreak) {
                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            listOf(15,30,60).forEach { value->CatalogButton("$value min",{pause=value.toString()},Modifier.weight(1f)) }
                        }
                    }
                }
            }
            CatalogGroup("Colore") { UniversalColorPickerRow(color) { color = it } }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal=14.dp,vertical=10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (existing) {
                CatalogIconAction("copy","Duplica turno",{
                    val base = code.ifBlank { "TUR" }
                    var suffix = 2
                    var candidate: String
                    do { val tail = (suffix++).toString(); candidate = base.take((4 - tail.length).coerceAtLeast(0)) + tail } while (ShiftRepository.shiftDefinitions.any { it.shortCode.equals(candidate, true) })
                    val copy = draft().copy(id = UUID.randomUUID().toString(), name = "$name copia", shortCode = candidate.take(4))
                    finish { onSave(copy) }
                })
                CatalogIconAction("trash-2","Elimina turno",{deleteConfirm=true},tint=MaterialTheme.colorScheme.error)
            }
            CatalogButton("Salva turno",{if(valid)finish{onSave(draft())}},Modifier.weight(1f),primary=true,enabled=valid,icon="check")
        }
        if (deleteConfirm) CatalogDeleteDialog("Eliminare questo turno?", { deleteConfirm = false }) { deleteConfirm = false; finish { onDelete(shift.id) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogTimeField(label: String, value: String, change: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier,verticalArrangement=Arrangement.spacedBy(5.dp)) {
    Text(label,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Box(modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).border(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.25f),RoundedCornerShape(12.dp)).bouncyClickable {
        val current = runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.of(6, 0))
        android.app.TimePickerDialog(context, { _, h, m -> change(String.format(java.util.Locale.ROOT, "%02d:%02d", h, m)) }, current.hour, current.minute, true).show()
    },contentAlignment=Alignment.Center) { Text(value,fontSize=13.sp) }
    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        listOf(-15,15).forEach { delta->
            Box(Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceVariant).bouncyClickable {
                runCatching{LocalTime.parse(value).plusMinutes(delta.toLong())}.onSuccess{change(it.toString())}
            },contentAlignment=Alignment.Center) { Icon(if(delta<0)Icons.Default.Remove else Icons.Default.Add,if(delta<0)"Anticipa $label di 15 minuti" else "Posticipa $label di 15 minuti",Modifier.size(14.dp)) }
        }
    }
    }
}

@Composable
private fun CatalogDeleteDialog(title: String, dismiss: () -> Unit, confirm: () -> Unit) {
    NativeBottomDialog(dismiss,bottomInset=24.dp){close->
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            SiteIcon("trash",null,Modifier.size(24.dp),MaterialTheme.colorScheme.error)
            Text(title,Modifier.weight(1f).padding(start=10.dp),fontSize=18.sp,fontWeight=FontWeight.SemiBold)
            CatalogIconAction("x","Chiudi",close,Modifier.size(32.dp))
        }
        Text("Questa operazione non può essere annullata.",Modifier.padding(vertical=16.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            CatalogButton("Annulla",close,Modifier.weight(1f))
            Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.error).bouncyClickable{close();confirm()},contentAlignment=Alignment.Center){
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){SiteIcon("trash",null,Modifier.size(17.dp),MaterialTheme.colorScheme.onError);Text("Elimina",fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onError)}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotationMatrixEditorScreen(rotation: ShiftRotation, onDismiss: () -> Unit, onSave: (ShiftRotation) -> Unit, onApply: (ShiftRotation) -> Unit, onDelete: ((String) -> Unit)? = null) {
    var title by remember(rotation.id) { mutableStateOf(rotation.name) }
    val sequence = remember(rotation.id) { mutableStateListOf<String>().apply { addAll(rotation.daySequence.ifEmpty { listOf("") }) } }
    var brush by remember { mutableStateOf(ShiftRepository.shiftDefinitions.firstOrNull()?.id ?: "") }
    var favorite by remember(rotation.id) { mutableStateOf(ShiftRepository.defaultRotationId == rotation.id) }
    val starMotion = remember { Animatable(0f) }
    val starScope = rememberCoroutineScope()
    var deleteConfirm by remember { mutableStateOf(false) }
    val existing=ShiftRepository.rotations.any{it.id==rotation.id}
    CatalogEditorSheet(if(existing)"Modifica rotazione" else "Nuova rotazione", onDismiss,heightFraction=1f,
        subtitle="Scegli un turno e componi la sequenza",fullPage=true) { finish ->
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            CatalogGroup("Nome della rotazione",outlined=true) { CatalogField("Nome",title,{title=it.take(60)},Modifier.fillMaxWidth()) }
            CatalogGroup("Lunghezza del ciclo",outlined=true) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
                    Text("${sequence.size}",fontSize=25.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                    Text(if(sequence.size==1)" giorno" else " giorni",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                listOf(listOf(-1,1),listOf(-7,7)).forEach { row->Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    row.forEach { delta->CatalogButton((if(delta<0)"− " else "+ ")+(if(kotlin.math.abs(delta)==7)"1 settimana" else "1 giorno"),{
                        val target=(sequence.size+delta).coerceIn(1,70)
                        while(sequence.size<target)sequence.add("")
                        while(sequence.size>target)sequence.removeAt(sequence.lastIndex)
                    },Modifier.weight(1f),enabled=if(delta<0)sequence.size>1 else sequence.size<70) }
                } }
            }
            CatalogStep(1,"Scegli il turno")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                CatalogPaintChip("","",MaterialTheme.colorScheme.surfaceVariant,brush.isEmpty()){brush=""}
                ShiftRepository.shiftDefinitions.forEach { def->CatalogPaintChip(def.shortCode,def.name,def.color,brush==def.id){brush=def.id} }
            }
            CatalogStep(2,"Tocca i giorni da compilare")
            Surface(shape=RoundedCornerShape(15.dp),color=MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().heightIn(max=330.dp).verticalScroll(rememberScrollState()).padding(7.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        Spacer(Modifier.width(19.dp))
                        listOf("L","M","M","G","V","S","D").forEach { day->Text(day,Modifier.weight(1f),fontSize=10.sp,textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    for(week in 0 until (sequence.size+6)/7)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text("S${week+1}",Modifier.width(19.dp),fontSize=9.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        for(day in 0..6) {
                            val index=week*7+day
                            val def=sequence.getOrNull(index)?.let{ShiftRepository.getShiftDefinition(it)}
                            if(index>=sequence.size)Spacer(Modifier.weight(1f).height(38.dp))
                            else {
                                val fill by animateColorAsState(def?.color?:MaterialTheme.colorScheme.surfaceVariant,spring(1f,420f))
                                Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(7.dp)).background(fill)
                                    .then(if(index==sequence.lastIndex)Modifier.border(1.5.dp,MaterialTheme.colorScheme.primary,RoundedCornerShape(7.dp))else Modifier)
                                    .bouncyClickable{sequence[index]=brush},contentAlignment=Alignment.Center) {
                                    Text(def?.shortCode?.take(4)?:"—",fontSize=10.sp,fontWeight=FontWeight.SemiBold,color=if(def!=null)catalogInk(fill) else MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center,maxLines=1)
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable {
                favorite=!favorite
                starScope.launch{starMotion.snapTo(0f);starMotion.animateTo(1f,tween(720));starMotion.snapTo(0f)}
            },verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(if(favorite)Color(0xFFE4A52E).copy(alpha=.15f)else MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center) {
                    CatalogDefaultStar(favorite,"Rotazione predefinita",Modifier.size(21.dp),starMotion.value)
                }
                Text("Rotazione predefinita",Modifier.padding(start=10.dp),fontSize=12.sp,fontWeight=FontWeight.Medium)
            }
            if(existing&&onDelete!=null)CatalogButton("Elimina rotazione",{deleteConfirm=true},Modifier.fillMaxWidth())
        }
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(14.dp)) {
            CatalogButton("Salva",{
                val saved = rotation.copy(name = title.trim(), totalDays = sequence.size, daySequence = sequence.toList())
                finish { onSave(saved); if (favorite) ShiftRepository.setDefaultRotation(saved.id) else if (ShiftRepository.defaultRotationId == saved.id) ShiftRepository.setDefaultRotation("") }
            },Modifier.weight(1f),primary=true,enabled=title.isNotBlank()&&sequence.any{it.isNotEmpty()},icon="check")
        }
        if (deleteConfirm && onDelete != null) CatalogDeleteDialog("Eliminare la rotazione?", { deleteConfirm = false }) { deleteConfirm = false; finish { onDelete(rotation.id) } }
    }
}

@Composable
private fun CatalogPaintChip(code: String, name: String, color: Color, selected: Boolean, action: () -> Unit) {
    val font=remember{Animatable(12f)};val phase=remember{Animatable(0f)};val scope=rememberCoroutineScope()
    Column(Modifier.width(if(code.isEmpty())42.dp else 76.dp).height(66.dp).clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant).border(2.dp,if(selected)color else Color.Transparent,RoundedCornerShape(12.dp))
        .clickable {
            scope.launch{font.snapTo(13.5f);font.animateTo(12f,spring(.8f,300f))}
            scope.launch{phase.snapTo(0f);phase.animateTo(1f,tween(720));phase.snapTo(0f)}
            action()
        }.padding(5.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        Box(Modifier.width(36.dp).height(32.dp).clip(RoundedCornerShape(9.dp)).background(if(code.isEmpty())Color.Transparent else color),contentAlignment=Alignment.Center) {
            if(code.isEmpty())SiteIcon("eraser","Gomma",Modifier.size(24.dp),MaterialTheme.colorScheme.onSurface,phase.value)
            else Text(code.take(4),color=catalogInk(color),fontSize=font.value.sp,fontWeight=FontWeight.Bold,maxLines=1,textAlign=TextAlign.Center)
        }
        if(code.isNotEmpty())Text(name,fontSize=10.sp,maxLines=1,textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=5.dp))
    }
}

@Composable
private fun CatalogStep(number:Int,label:String) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(23.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center){Text("$number",fontSize=11.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)}
        Text(label,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkProfileEditorScreen(profile: WorkProfile, isNew: Boolean, onDismiss: () -> Unit, onSave: (WorkProfile) -> Unit, onDelete: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var department by remember(profile.id) { mutableStateOf(profile.companyOrDepartment) }
    var icon by remember(profile.id) { mutableStateOf(profile.iconKey) }
    var color by remember(profile.id) { mutableStateOf(profile.colorHex) }
    var advanced by remember(profile.id) { mutableStateOf(profile.enableAdvancedCalc) }
    var active by remember(profile.id) { mutableStateOf(profile.isDefault) }
    var contract by remember(profile.id) { mutableStateOf(profile.linkedCcnl) }
    var hours by remember(profile.id) { mutableStateOf(profile.weeklyHours) }
    var rest by remember(profile.id) { mutableStateOf(profile.minRestHoursBetweenShifts) }
    var night by remember(profile.id) { mutableStateOf(profile.nightSurchargePercent) }
    var holiday by remember(profile.id) { mutableStateOf(profile.holidaySurchargePercent) }
    var nightHoliday by remember(profile.id) { mutableStateOf(profile.nightHolidaySurchargePercent) }
    var overtime by remember(profile.id) { mutableStateOf(profile.overtimeDaySurchargePercent) }
    var overtimeNight by remember(profile.id) { mutableStateOf(profile.overtimeNightSurchargePercent) }
    var documentName by remember(profile.id) { mutableStateOf(profile.ccnlDocumentName) }
    var documentUri by remember(profile.id) { mutableStateOf(profile.ccnlDocumentUri) }
    var documentError by remember { mutableStateOf<String?>(null) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var showContracts by remember { mutableStateOf(false) }
    var stagedDocument by remember { mutableStateOf<File?>(null) }
    DisposableEffect(Unit) { onDispose { stagedDocument?.delete() } }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            var title = "Documento CCNL.pdf"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) title = cursor.getString(index)
            }
            val temp = File.createTempFile("ccnl-draft-", ".pdf", context.cacheDir)
            try {
                val input = context.contentResolver.openInputStream(uri) ?: error("Documento non disponibile")
                input.use { source -> temp.outputStream().use { target -> source.copyTo(target) } }
                check(temp.length() > 0) { "Il documento è vuoto" }
                stagedDocument?.delete()
                stagedDocument = temp
                documentName = title
                documentUri = temp.absolutePath
                documentError = null
            } catch (error: Exception) { temp.delete(); throw error }
        }.onFailure { documentError = "Impossibile aprire il documento. Riprova." }
    }
    val iconKeys = listOf("work", "business", "people", "computer", "factory", "hospital", "shipping", "transit")
    CatalogEditorSheet(if (isNew) "Nuovo lavoro" else "Modifica lavoro", onDismiss,heightFraction=.96f,subtitle="Calendario e impostazioni del profilo") { finish ->
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 13.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.25f),RoundedCornerShape(18.dp)).padding(13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(11.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(color).copy(alpha=.17f)),contentAlignment=Alignment.Center) {
                    CatalogWorkIcon(icon,Modifier.size(24.dp),MaterialTheme.colorScheme.onSurface)
                }
                Column {Text(name.ifBlank{"Il tuo lavoro"},fontSize=16.sp,fontWeight=FontWeight.SemiBold);Text(department.ifBlank{"Un calendario dedicato"},fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=4.dp))}
            }
            CatalogGroup("Identità",outlined=true) {
                CatalogField("Nome lavoro",name,{name=it.take(60)},Modifier.fillMaxWidth(),height=44.dp)
                CatalogField("Reparto o settore",department,{department=it.take(80)},Modifier.fillMaxWidth(),height=44.dp)
                Text("Icona del lavoro", fontSize = 12.sp)
                iconKeys.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { key ->
                            Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp)).background(if (icon == key) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background).border(if (icon == key) 2.dp else 0.dp, if (icon == key) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(14.dp)).semantics { contentDescription=when(key){"work"->"Valigetta";"business"->"Edificio";"people"->"Persone";"computer"->"Computer";"factory"->"Strumenti";"hospital"->"Salute";"shipping"->"Trasporto";else->"Treno"} }.bouncyClickable { icon = key }, contentAlignment = Alignment.Center) {
                                CatalogWorkIcon(key,Modifier.size(22.dp),if(icon==key)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            CatalogGroup("Colore del lavoro",outlined=true) { UniversalColorPickerRow(color,showNames=true) { color = it } }
            CatalogGroup("Orario e riposo",outlined=true) {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    CatalogField("Ore settimanali",hours.toString(),{it.toIntOrNull()?.takeIf{n->n in 1..60}?.let{n->hours=n}},Modifier.weight(1f),numeric=true,height=44.dp,centered=true)
                    CatalogField("Riposo minimo · ore",rest.toString(),{it.toIntOrNull()?.takeIf{n->n in 1..24}?.let{n->rest=n}},Modifier.weight(1f),numeric=true,height=44.dp,centered=true)
                }
            }
            CatalogGroup("Parametri CCNL",outlined=true) {
                CatalogButton(contract.name,{showContracts=true},Modifier.fillMaxWidth())
                CatalogToggle("Parametri CCNL avanzati", advanced) { advanced = it }
                Text(if(advanced)"Maggiorazioni personalizzate attive" else "Modalità semplice · le maggiorazioni restano conservate",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                AnimatedVisibility(advanced, enter = expandVertically(spring()) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            CatalogField("Notturno (%)",night.toString(),{it.toIntOrNull()?.takeIf{n->n in 0..200}?.let{n->night=n}},Modifier.weight(1f),numeric=true,centered=true)
                            CatalogField("Festivo (%)",holiday.toString(),{it.toIntOrNull()?.takeIf{n->n in 0..200}?.let{n->holiday=n}},Modifier.weight(1f),numeric=true,centered=true)
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            CatalogField("Notturno festivo (%)",nightHoliday.toString(),{it.toIntOrNull()?.takeIf{n->n in 0..200}?.let{n->nightHoliday=n}},Modifier.weight(1f),numeric=true,centered=true)
                            CatalogField("Straordinario diurno (%)",overtime.toString(),{it.toIntOrNull()?.takeIf{n->n in 0..200}?.let{n->overtime=n}},Modifier.weight(1f),numeric=true,centered=true)
                        }
                        CatalogField("Straordinario notturno/festivo (%)",overtimeNight.toString(),{it.toIntOrNull()?.takeIf{n->n in 0..200}?.let{n->overtimeNight=n}},Modifier.fillMaxWidth(),numeric=true,centered=true)
                    }
                }
            }
            CatalogGroup("Documento CCNL",outlined=true) {
                Text(documentName ?: "Nessun documento selezionato", fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CatalogButton(if(documentUri==null)"Scegli PDF" else "Sostituisci PDF",{documentPicker.launch(arrayOf("application/pdf"))},Modifier.weight(1f))
                    if (documentUri != null) {
                        IconButton(onClick = { openWorkProfilePdf(context, profile.copy(ccnlDocumentUri = documentUri, ccnlDocumentName = documentName)) }) { Icon(Icons.Default.OpenInNew, "Apri documento") }
                        IconButton(onClick = { stagedDocument?.delete(); stagedDocument = null; documentName = null; documentUri = null }) { Icon(Icons.Default.DeleteOutline, "Rimuovi documento", tint = MaterialTheme.colorScheme.error) }
                    }
                }
                if (documentError != null) Text(documentError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            CatalogGroup("Calendario",outlined=true) { CatalogToggle("Imposta come lavoro attivo", active) { active = it } }
            if(!isNew&&onDelete!=null&&ShiftRepository.workProfiles.size>1)CatalogButton("Elimina lavoro",{deleteConfirm=true},Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal=14.dp,vertical=12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CatalogButton("Salva lavoro",{
                runCatching {
                    var savedUri = documentUri
                    stagedDocument?.let { staged ->
                        val destination = File(context.filesDir, "ccnl_${profile.id}.pdf")
                        staged.copyTo(destination, overwrite = true)
                        savedUri = destination.absolutePath
                    }
                    val saved = profile.copy(name = name.trim(), companyOrDepartment = department.trim(), iconKey = icon, colorHex = color, enableAdvancedCalc = advanced, isDefault = active, ccnlCode = contract.code, ccnlName = contract.name, weeklyHours = hours, minRestHoursBetweenShifts = rest, nightSurchargePercent = night, holidaySurchargePercent = holiday, nightHolidaySurchargePercent = nightHoliday, overtimeDaySurchargePercent = overtime, overtimeNightSurchargePercent = overtimeNight, ccnlDocumentName = documentName, ccnlDocumentUri = savedUri)
                    finish { onSave(saved) }
                }.onFailure { documentError = "Impossibile salvare il documento. Riprova." }
            },Modifier.weight(1f),primary=true,enabled=name.isNotBlank())
        }
        if (deleteConfirm && onDelete != null) CatalogDeleteDialog("Eliminare questo lavoro?", { deleteConfirm = false }) { deleteConfirm = false; finish { onDelete(profile.id) } }
        if (showContracts) AlertDialog(onDismissRequest = { showContracts = false }, title = { Text("Contratto di riferimento") }, text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                CcnlCatalog.presets.forEach { preset ->
                    TextButton(onClick = {
                        contract = preset; hours = preset.standardWeeklyHours; rest = preset.minRestHoursBetweenShifts
                        night = preset.nightSurchargePercent; holiday = preset.holidaySurchargePercent; nightHoliday = preset.nightHolidaySurchargePercent
                        overtime = preset.overtimeDaySurchargePercent; overtimeNight = preset.overtimeNightSurchargePercent
                        showContracts = false
                    }, modifier = Modifier.fillMaxWidth()) { Text(preset.name, Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
                }
            }
        }, confirmButton = { TextButton(onClick = { showContracts = false }) { Text("Chiudi") } })
    }
}

@Composable
private fun CatalogNumberRow(label: String, value: Int, range: IntRange, step: Int = 1, update: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 8.dp), fontSize = 13.sp)
        IconButton(onClick = { update((value - step).coerceIn(range)) }, enabled = value > range.first, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Remove, "Diminuisci $label", Modifier.size(18.dp)) }
        Text("$value", Modifier.widthIn(min = 28.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = { update((value + step).coerceIn(range)) }, enabled = value < range.last, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Add, "Aumenta $label", Modifier.size(18.dp)) }
    }
}

/** Filled gold is reserved for a default rotation; the unselected state stays outline. */
@Composable
internal fun CatalogDefaultStar(selected: Boolean, description: String, modifier: Modifier = Modifier.size(21.dp), progress: Float = 0f) {
    if (selected) {
        val wave = kotlin.math.sin(progress.coerceIn(0f, 1f) * kotlin.math.PI).toFloat()
        Icon(Icons.Filled.Star, description, modifier.graphicsLayer {
            scaleX = 1f + .18f * wave
            scaleY = scaleX
            rotationZ = 10f * kotlin.math.sin(progress * 2f * kotlin.math.PI).toFloat()
        }, tint = Color(0xFFF2BC3B))
    } else SiteIcon("star", description, modifier, MaterialTheme.colorScheme.onSurfaceVariant, progress)
}
