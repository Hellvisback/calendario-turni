package com.turni.calendario.alarm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.ui.theme.CalendarioTurniTheme

class ShiftAlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        setContent {
            CalendarioTurniTheme {
                val ringing by ShiftAlarmService.ringing.collectAsState()
                LaunchedEffect(ringing) { if (!ringing) finish() }
                fun dismiss() {
                    stopService(Intent(this@ShiftAlarmActivity, ShiftAlarmService::class.java))
                    finish()
                }
                BackHandler { dismiss() }
                val transition = rememberInfiniteTransition()
                val angle by transition.animateFloat(-9f, 9f, infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse))
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(28.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(112.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Alarm, null, Modifier.size(52.dp).rotate(angle), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(30.dp))
                    Text("SVEGLIA TURNISYNC", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text(intent.getStringExtra("shift_name") ?: "Turno", color = MaterialTheme.colorScheme.onBackground, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    Text(intent.getStringExtra("message") ?: "È ora di prepararti", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(42.dp))
                    Button(onClick = { dismiss() }, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().height(64.dp)) {
                        Icon(Icons.Outlined.Stop, null)
                        Spacer(Modifier.width(10.dp))
                        Text("Ferma sveglia", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
