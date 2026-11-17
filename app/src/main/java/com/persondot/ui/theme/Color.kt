package com.persondot.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// Dynamic Theme Swapping Colors (backed by Compose state for seamless reactive updates)
var DarkBackground by mutableStateOf(Color(0xFF0A0A0A))
var DarkSurface by mutableStateOf(Color(0xFF121214))
var DarkSurfaceElevated by mutableStateOf(Color(0xFF1C1C1F))
var BorderColor by mutableStateOf(Color(0xFF1C1C1F))
var CardBorderGlance by mutableStateOf(Color(0xFF2E2E33))
var SecondaryText by mutableStateOf(Color(0xFFA1A1AA))
var AppTextWhite by mutableStateOf(Color.White)

// Bento Light Color Palette equivalents for reference
val LightBackground = Color(0xFFF4F4F5)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFE4E4E7)
val LightSecondaryText = Color(0xFF71717A)
val LightBorderColor = Color(0xFFE4E4E7)
val LightCardBorderGlance = Color(0xFFD4D4D8)

// Fixed Accent & Theme colors
val CyberGreen = Color(0xFF10B981)   // Success, wealth growth, discipline
val CyberTeal = Color(0xFF06B6D4)    // Financial flows, stability, PJ
val PulseViolet = Color(0xFF8B5CF6)  // Car goal, motivation, luxury
val ImpulseAmber = Color(0xFFEF4444) // Risk warning, impulse buying color

// Chart and Emotional colors
val DopamineBlue = Color(0xFF3B82F6)
val AnxietyPurple = Color(0xFFD946EF)
val SocialOrange = Color(0xFFF97316)
val BoredomCyan = Color(0xFF14B8A6)
