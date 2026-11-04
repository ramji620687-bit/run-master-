package com.runmaster.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NeonLime = Color(0xFFB7F34A)
val DeepBlack = Color(0xFF090B0A)
val SurfaceDark = Color(0xFF121614)
val SurfaceLight = Color(0xFF1A211D)
val BorderDark = Color(0xFF232C27)
val MutedText = Color(0xFF9AA39E)

private val DarkColorScheme = darkColorScheme(
    primary = NeonLime,
    onPrimary = Color.Black,
    background = DeepBlack,
    onBackground = Color.White,
    surface = SurfaceDark,
    onSurface = Color.White,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = MutedText,
    outline = BorderDark
)

@Composable
fun RunMasterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, content = content)
}
