package com.lightbox.batch.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {

    private val viewModel: BatchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val darkColors = darkColorScheme(
                primary = Color(0xFF90CAF9),
                onPrimary = Color(0xFF0D47A1),
                primaryContainer = Color(0xFF1565C0),
                onPrimaryContainer = Color(0xFFBBDEFB),
                secondary = Color(0xFFA5D6A7),
                onSecondary = Color(0xFF1B5E20),
                secondaryContainer = Color(0xFF2E7D32),
                onSecondaryContainer = Color(0xFFC8E6C9),
                tertiary = Color(0xFFCE93D8),
                onTertiary = Color(0xFF4A148C),
                background = Color(0xFF121212),
                onBackground = Color(0xFFE0E0E0),
                surface = Color(0xFF1E1E1E),
                onSurface = Color(0xFFE0E0E0),
                surfaceVariant = Color(0xFF2C2C2C),
                onSurfaceVariant = Color(0xFFBDBDBD),
                error = Color(0xFFEF9A9A),
                onError = Color(0xFFB71C1C),
                outline = Color(0xFF616161)
            )

            MaterialTheme(colorScheme = darkColors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainBatchScreen(viewModel = viewModel)
                }
            }
        }
    }
}
