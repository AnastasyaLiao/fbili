package com.sammy.fbili.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.sammy.fbili.data.ThemeMode

// 黑白主题：浅色用近黑强调色，深色用近白强调色 + 纯黑底（OLED 省电）
val Ink = Color(0xFF18191C)
val InkInverse = Color(0xFFF3F4F6)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    secondary = Color(0xFF444B53),
    onSecondary = Color.White,
    background = Color(0xFFF6F7F8),
    onBackground = Color(0xFF18191C),
    surface = Color.White,
    onSurface = Color(0xFF18191C),
    surfaceVariant = Color(0xFFF1F2F3),
    onSurfaceVariant = Color(0xFF61666D),
    secondaryContainer = Color(0xFFE6E7E9),
    onSecondaryContainer = Color(0xFF18191C),
    outline = Color(0xFFE3E5E7),
)

private val DarkColors = darkColorScheme(
    primary = InkInverse,
    onPrimary = Color(0xFF101014),
    secondary = Color(0xFFB7BCC4),
    onSecondary = Color(0xFF18191C),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF0E0E11),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF1A1A1F),
    onSurfaceVariant = Color(0xFFA5A9B5),
    secondaryContainer = Color(0xFF232329),
    onSecondaryContainer = Color(0xFFEDEDED),
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
