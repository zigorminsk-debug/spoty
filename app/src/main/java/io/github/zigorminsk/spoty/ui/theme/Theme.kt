package io.github.zigorminsk.spoty.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.zigorminsk.spoty.data.settings.ThemeMode

// Фирменные цвета: тёмный графит + лаймовый акцент (сочетание духа Спотифая и Я.Музыки).
val Accent = Color(0xFFC6F432)
val AccentDark = Color(0xFF9BC516)
val BackgroundDark = Color(0xFF0E0F12)
val SurfaceDark = Color(0xFF17181C)
val SurfaceHigh = Color(0xFF212328)
val TextPrimaryDark = Color(0xFFF4F5F7)
val TextSecondaryDark = Color(0xFFA6ABB5)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF14160A),
    primaryContainer = Color(0xFF2E3618),
    onPrimaryContainer = Color(0xFFDFF78F),
    secondary = Color(0xFF9BE8E3),
    onSecondary = Color(0xFF0B1514),
    secondaryContainer = Color(0xFF1E3250),
    onSecondaryContainer = Color(0xFFD7E3FF),
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = BackgroundDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = Color(0xFF2A2D33),
    surfaceContainerLow = Color(0xFF131418),
    surfaceContainerLowest = Color(0xFF0A0B0D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    outline = Color(0xFF3A3E46),
    outlineVariant = Color(0xFF282B31),
    inverseSurface = TextPrimaryDark,
    inverseOnSurface = Color(0xFF1B1C20),
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF566500),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9EC7C),
    onPrimaryContainer = Color(0xFF181E00),
    secondary = Color(0xFF006964),
    background = Color(0xFFFBFBF4),
    onBackground = Color(0xFF1B1C17),
    surface = Color(0xFFFBFBF4),
    onSurface = Color(0xFF1B1C17),
    surfaceVariant = Color(0xFFE4E3D4),
    onSurfaceVariant = Color(0xFF53564A),
    surfaceContainer = Color(0xFFF0F0E6),
    surfaceContainerHigh = Color(0xFFEAEADF),
)

val SpotyTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    )
}

@Composable
fun SpotyTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, typography = SpotyTypography, content = content)
}
