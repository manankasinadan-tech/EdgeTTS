package top.initsnow.edge_tts_android.ui.theme

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
    primary = BrandSecondaryDark,
    onPrimary = BrandPrimaryDark,
    primaryContainer = BrandPrimary,
    onPrimaryContainer = BrandSecondaryDark,
    secondary = BrandAccentDark,
    onSecondary = BrandPrimaryDark,
    secondaryContainer = BrandSurfaceVariantDark,
    onSecondaryContainer = Color.White,
    tertiary = BrandSecondary,
    background = BrandSurfaceDark,
    onBackground = BrandOnSurfaceDark,
    surface = BrandSurfaceDark,
    onSurface = BrandOnSurfaceDark,
    surfaceVariant = BrandSurfaceVariantDark,
    onSurfaceVariant = Color(0xFFBAC8D0),
    outline = BrandOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandSurfaceVariant,
    onPrimaryContainer = BrandPrimary,
    secondary = BrandAccent,
    onSecondary = Color.White,
    secondaryContainer = BrandSecondary,
    onSecondaryContainer = BrandPrimary,
    tertiary = BrandSecondary,
    background = BrandSurface,
    onBackground = BrandOnSurface,
    surface = BrandSurface,
    onSurface = BrandOnSurface,
    surfaceVariant = BrandSurfaceVariant,
    onSurfaceVariant = BrandPrimary,
    outline = BrandOutline
)

@Composable
fun EdgeTtsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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
        content = content
    )
}
