package com.aipromptbridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aipromptbridge.designsystem.AipbTheme
import com.aipromptbridge.feature.settings.SetupScreen

/** Launcher activity. Currently hosts the minimal setup screen; the full Settings screen is README Phase 5. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AipbApp).container
        setContent {
            AipbTheme {
                SetupScreen(container)
            }
        }
    }
}
