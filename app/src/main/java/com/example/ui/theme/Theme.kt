package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GoldAmber,
    secondary = CyberBlue,
    tertiary = LidarCopper,
    background = EarthSlateBackground,
    surface = DarkSteelSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFF5F5F7),
    onSurface = Color(0xFFF5F5F7)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force Dark theme for high-contrast tactical feel
    dynamicColor: Boolean = false, // Disable dynamic colors for cohesive visual styling
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}


