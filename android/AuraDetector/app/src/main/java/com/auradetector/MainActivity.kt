package com.auradetector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.auradetector.ui.screens.SettingsScreen
import com.auradetector.ui.theme.AuraDetectorTheme
import com.auradetector.ui.theme.DarkNavy

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AuraDetectorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkNavy
                ) {
                    // For now, just show the SettingsScreen
                    SettingsScreen(
                        onConnect = { config ->
                            // TODO: Navigate to ScannerScreen
                        }
                    )
                }
            }
        }
    }
}
