package com.sammy.fbili.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.sammy.fbili.data.ThemeMode

val Pink = Color(0xFFFB7299)
val PinkDark = Color(0xFFF291AC)

private val LightColors = lightColorScheme(
    primary = Pink,
    onPrimary = Color.White,
    secondary = Color(0xFF00A1D6),
    onSecondary = Color.White,
    background = Color(0xFFF6F7F8),
    onBackground = Color(0xFF18191C),
    surface = Color.White,
    onSurface = Color(0xFF18191C),
    surfaceVariant = Color(0xFFF1F2F3),
    onSurfaceVariant = Color(0xFF61666D),
    outline = Color(0xFFE3E5E7),
)

private val DarkColors = darkColorScheme(
    primary = PinkDark,
    onPrimary = Color(0xFF301A21),
    secondary = Color(0xFF57C2DE),
    onSecondary = Color(0xFF003547),
    background = Color(0xFF101014),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF17171C),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF202027),
    onSurfaceVariant = Color(0xFFA5A9B5),
    outline = Color(0xFF2A2A33),
)

@Composable
fun FBiliTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
