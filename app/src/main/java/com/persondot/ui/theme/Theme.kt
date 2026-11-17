package com.persondot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PremiumDarkColorScheme = darkColorScheme(
    primary = CyberGreen,
    onPrimary = Color.Black,
    secondary = CyberTeal,
    onSecondary = Color.Black,
    tertiary = PulseViolet,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = Color.White,
    onSurface = Color.White,
    error = ImpulseAmber,
    onError = Color.White,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = SecondaryText,
    outline = BorderColor
)

private val PremiumLightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = CyberGreen,
    onPrimary = Color.White,
    secondary = CyberTeal,
    onSecondary = Color.White,
    tertiary = PulseViolet,
    background = LightBackground,
    surface = LightSurface,
    onBackground = Color(0xFF09090B),
    onSurface = Color(0xFF09090B),
    error = ImpulseAmber,
    onError = Color.White,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightSecondaryText,
    outline = LightBorderColor
)

@Composable
fun PersonDotTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false, // Disable dynamic colors to keep precise design consistency
    content: @Composable () -> Unit
) {
    // Dynamically update states before rendering
    if (darkTheme) {
        DarkBackground = Color(0xFF0A0A0A)
        DarkSurface = Color(0xFF121214)
        DarkSurfaceElevated = Color(0xFF1C1C1F)
        BorderColor = Color(0xFF1C1C1F)
        CardBorderGlance = Color(0xFF2E2E33)
        SecondaryText = Color(0xFFA1A1AA)
        AppTextWhite = Color.White
    } else {
        DarkBackground = LightBackground
        DarkSurface = LightSurface
        DarkSurfaceElevated = LightSurfaceElevated
        BorderColor = LightBorderColor
        CardBorderGlance = LightCardBorderGlance
        SecondaryText = LightSecondaryText
        AppTextWhite = Color(0xFF09090B) // Dark slate for premium light mode text
    }

    val colorScheme = if (darkTheme) PremiumDarkColorScheme else PremiumLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
