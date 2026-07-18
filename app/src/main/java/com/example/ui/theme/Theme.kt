package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD5C7A2), // light warm sand/sage
    onPrimary = Color(0xFF3B3012),
    primaryContainer = Color(0xFF6B5F3E),
    onPrimaryContainer = Color(0xFFFBF9F6),
    secondary = Color(0xFFC4BCAB),
    onSecondary = Color(0xFF1D1B16),
    background = Color(0xFF1D1B16), // Dark warm charcoal/brown
    onBackground = Color(0xFFFBF9F6),
    surface = Color(0xFF272520),
    onSurface = Color(0xFFFBF9F6),
    surfaceVariant = Color(0xFF3B3831),
    onSurfaceVariant = Color(0xFFC4BCAB),
    outline = Color(0xFF4F4B43)
)

private val LightColorScheme = lightColorScheme(
    primary = NaturalPrimary,
    onPrimary = Color.White,
    primaryContainer = NaturalSecondary,
    onPrimaryContainer = Color(0xFF4F452C),
    secondary = Color(0xFF8C8061),
    onSecondary = Color.White,
    background = NaturalBg,
    onBackground = NaturalText,
    surface = Color.White,
    onSurface = NaturalText,
    surfaceVariant = LightClay,
    onSurfaceVariant = NaturalPrimary,
    outline = BorderClay
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disabling dynamic colors by default so Natural Tones takes precedence
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
