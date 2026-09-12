package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF004D5A),
    onPrimaryContainer = CyanPrimary,
    secondary = PurpleAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF321B66),
    onSecondaryContainer = Color(0xFFD1C4E9),
    tertiary = GreenAccent,
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = Color(0xFFECEFF4),
    surface = DarkSurface,
    onSurface = Color(0xFFECEFF4),
    surfaceVariant = DarkCard,
    onSurfaceVariant = Color(0xFFB0BEC5),
    outline = DarkBorder
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF007A8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2EBF2),
    onPrimaryContainer = Color(0xFF00363D),
    secondary = Color(0xFF5E35B1),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE7F6),
    onSecondaryContainer = Color(0xFF311B92),
    tertiary = Color(0xFF00897B),
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF1A202C),
    surface = LightSurface,
    onSurface = Color(0xFF1A202C),
    surfaceVariant = LightCard,
    onSurfaceVariant = Color(0xFF4A5568),
    outline = LightBorder
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
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
