package com.turni.calendario

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.turni.calendario.ui.screens.MainScreen
import com.turni.calendario.ui.theme.CalendarioTurniTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalendarioTurniTheme {
                MainScreen()
            }
        }
    }
}
