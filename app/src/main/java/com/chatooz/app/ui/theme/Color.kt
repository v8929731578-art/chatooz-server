package com.chatooz.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ─── Chatooz Primary Brand ───────────────────────────────────────
val IndigoPrimary    = Color(0xFF6366F1)
val IndigoLight      = Color(0xFF818CF8)
val IndigoDark       = Color(0xFF4F46E5)
val VioletAccent     = Color(0xFF8B5CF6)
val VioletLight      = Color(0xFFA78BFA)
val PinkAccent       = Color(0xFFEC4899)
val CyberBlue        = Color(0xFF38BDF8)
val CyberPurple      = Color(0xFF9333EA)

// ─── Accent Colors ───────────────────────────────────────────────
val EmeraldPrimary   = Color(0xFF10B981)
val EmeraldAccent    = Color(0xFF10B981)
val EmeraldLight     = Color(0xFF34D399)
val CyanAccent       = Color(0xFF06B6D4)
val RoseAccent       = Color(0xFFF43F5E)
val AmberAccent      = Color(0xFFF59E0B)

// ─── Modern Gradients ─────────────────────────────────────────────
val BrandGradientPrimary = Brush.linearGradient(
    colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFEC4899))
)

val BrandGradientLuminous = Brush.linearGradient(
    colors = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFFD946EF))
)

val SentBubbleGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF6366F1), Color(0xFF7C3AED))
)

val StoryRingGradient = Brush.sweepGradient(
    colors = listOf(
        Color(0xFF6366F1),
        Color(0xFF8B5CF6),
        Color(0xFFEC4899),
        Color(0xFFF59E0B),
        Color(0xFF38BDF8),
        Color(0xFF6366F1)
    )
)

val FloatingDockGradient = Brush.verticalGradient(
    colors = listOf(Color(0xF0111827), Color(0xFA080C14))
)

val GlassCardGradientDark = Brush.verticalGradient(
    colors = listOf(Color(0x22FFFFFF), Color(0x0AFFFFFF))
)

// ─── Dark Theme Backgrounds ─────────────────────────────────────
val DarkBg           = Color(0xFF080C14)
val DarkSurface      = Color(0xFF0F172A)
val DarkCard         = Color(0xFF162032)
val DarkDivider      = Color(0xFF1E293B)
val DarkElevated     = Color(0xFF1A263D)
val DarkGlassBorder  = Color(0x25FFFFFF)
val DarkGlassInner   = Color(0x12FFFFFF)

// ─── Light Theme Backgrounds ─────────────────────────────────────
val LightBg          = Color(0xFFF8FAFC)
val LightSurface     = Color(0xFFFFFFFF)
val LightCard        = Color(0xFFF1F5F9)
val LightDivider     = Color(0xFFE2E8F0)
val LightElevated    = Color(0xFFFFFFFF)
val LightGlassBorder = Color(0x18000000)
val LightGlassInner  = Color(0x08000000)

// ─── Text Colors ─────────────────────────────────────────────────
val TextPrimDark     = Color(0xFFF8FAFC)
val TextSecDark      = Color(0xFF94A3B8)
val TextPrimLight    = Color(0xFF0F172A)
val TextSecLight     = Color(0xFF64748B)

// ─── Chat bubble colors ───────────────────────────────────────────
val BubbleSentDark   = Color(0xFF6366F1)
val BubbleRecvDark   = Color(0xFF1E293B)
val BubbleSentLight  = Color(0xFF6366F1)
val BubbleRecvLight  = Color(0xFFFFFFFF)

// ─── Misc ─────────────────────────────────────────────────────────
val TickBlue         = Color(0xFF38BDF8)
val TickGray         = Color(0xFF94A3B8)
val OnlineGreen      = Color(0xFF10B981)
val UnreadBadge      = Color(0xFF6366F1)
val MissedRed        = Color(0xFFF43F5E)
