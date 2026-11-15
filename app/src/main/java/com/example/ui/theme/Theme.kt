package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.core.datastore.ThemeMode

@Immutable
data class GlassTokens(
    val isDark: Boolean,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val glassSurface: Color,
    val glassBorder: Color,
    val gradientStart: Color,
    val gradientEnd: Color
)

val LocalGlassTokens = staticCompositionLocalOf {
    GlassTokens(
        isDark = false,
        backgroundTop = PureWhite,
        backgroundBottom = SoftApricot,
        glassSurface = PureWhite,
        glassBorder = SoftOrangeBorder,
        gradientStart = TangerineVibrant,
        gradientEnd = SaffronPrimary
    )
}

private val LightColorScheme = lightColorScheme(
    primary = SaffronPrimary,
    onPrimary = PureWhite,
    primaryContainer = PeachTintSurface,
    onPrimaryContainer = DeepTerracotta,
    secondary = TangerineVibrant,
    onSecondary = PureWhite,
    secondaryContainer = SoftApricot,
    onSecondaryContainer = DeepTerracotta,
    tertiary = WarmAmberAccent,
    onTertiary = InkCharcoal,
    background = PureWhite,
    onBackground = InkCharcoal,
    surface = PureWhite,
    onSurface = InkCharcoal,
    surfaceVariant = SoftApricot,
    onSurfaceVariant = SlateMuted,
    outline = SoftOrangeBorder,
    error = RoseAlert,
    onError = PureWhite
)

private val DarkColorScheme = darkColorScheme(
    primary = RoyalPurplePrimary,
    onPrimary = PureWhite,
    primaryContainer = RoyalPurpleContainer,
    onPrimaryContainer = RoyalPurpleText,
    secondary = RoyalPurpleLight,
    onSecondary = MidnightNavyBg,
    secondaryContainer = NavyCardSurface,
    onSecondaryContainer = RoyalPurpleText,
    tertiary = WarmAmberAccent,
    onTertiary = MidnightNavyBg,
    background = MidnightNavyBg,
    onBackground = RoyalPurpleText,
    surface = NavySurface,
    onSurface = RoyalPurpleText,
    surfaceVariant = NavyCardSurface,
    onSurfaceVariant = NavyMutedText,
    outline = NavyBorder,
    error = RoseAlert,
    onError = PureWhite
)

val YaadRakshakShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun YaadRakshakTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDark) DarkColorScheme else LightColorScheme
    val glassTokens = if (useDark) {
        GlassTokens(
            isDark = true,
            backgroundTop = MidnightNavyBg,
            backgroundBottom = NavySurface,
            glassSurface = NavyCardSurface.copy(alpha = 0.88f),
            glassBorder = NavyBorder,
            gradientStart = RoyalPurplePrimary,
            gradientEnd = RoyalPurpleLight
        )
    } else {
        GlassTokens(
            isDark = false,
            backgroundTop = PureWhite,
            backgroundBottom = SoftApricot,
            glassSurface = PureWhite,
            glassBorder = SoftOrangeBorder,
            gradientStart = TangerineVibrant,
            gradientEnd = SaffronPrimary
        )
    }

    CompositionLocalProvider(LocalGlassTokens provides glassTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = YaadRakshakTypography,
            shapes = YaadRakshakShapes,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
    YaadRakshakTheme(themeMode = ThemeMode.LIGHT, content = content)
}
