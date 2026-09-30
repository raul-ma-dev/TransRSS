package com.rama.rss.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    // A lighter red keeps interactive text readable on dark surfaces.
    primary = Color(0xFFFF8A91),
    onPrimary = Color(0xFF3D0005),
    primaryContainer = Color(0xFFA61219),
    onPrimaryContainer = Color.White,
    inversePrimary = Color(0xFFA61219),
    secondary = Color(0xFFE0BFC1),
    secondaryContainer = Color(0xFF4A292D),
    tertiary = Color(0xFFFFB3B7),
    tertiaryContainer = Color(0xFF4C2429),
    background = Color(0xFF181113),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF181113),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF302124),
    onSurfaceVariant = Color(0xFFD7BFC2),
    surfaceContainerLow = Color(0xFF24191C),
    surfaceContainer = Color(0xFF2B1E21),
    surfaceContainerHighest = Color(0xFF39272B),
    outline = Color(0xFFA88B8F),
    outlineVariant = Color(0xFF51363B)
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun RssTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}