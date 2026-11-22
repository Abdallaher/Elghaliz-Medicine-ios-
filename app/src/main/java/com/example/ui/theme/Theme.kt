package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val LightMedicalColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = LightMint,
    onPrimaryContainer = EmeraldPrimary,
    secondary = GoldAccent,
    onSecondary = Color.White,
    secondaryContainer = LightMint,
    onSecondaryContainer = DeepMedicalInk,
    tertiary = GoldAccent,
    onTertiary = Color.White,
    background = CreamBackground,
    onBackground = DeepMedicalInk,
    surface = CardBackground,
    onSurface = DeepMedicalInk,
    surfaceVariant = LightMint,
    onSurfaceVariant = MutedSageText,
    outline = CardBorder,
    error = LiveMeetingCoral,
    onError = Color.White
)

private val DarkMedicalColorScheme = darkColorScheme(
    primary = GoldAccent,
    onPrimary = EmeraldPrimary,
    primaryContainer = DarkSurfaceCard,
    onPrimaryContainer = DarkTextPrimary,
    secondary = GoldAccent,
    onSecondary = EmeraldPrimary,
    secondaryContainer = DarkSurfaceCard,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = GoldAccent,
    onTertiary = Color.White,
    background = DarkCalmBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurfaceCard,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceCard,
    onSurfaceVariant = DarkTextSecondary,
    outline = EmeraldPrimary,
    error = LiveMeetingCoral,
    onError = Color.White
)

val MedicalShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun ElghalizMedicineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkMedicalColorScheme else LightMedicalColorScheme

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = MedicalShapes,
            content = content
        )
    }
}
