package com.aipromptbridge.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * Tokens come from design/stitch/editorial_precision/DESIGN.md (light) and from the dark prompt used in Stitch.
 * Direction: "copy editor's blue pencil" — cool paper-tinted neutrals, ONE cobalt accent used only for primary
 * actions / selected states / focus. Never use the accent decoratively.
 */

internal val LightColors = lightColorScheme(
    primary = Color(0xFF2447E0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = Color(0xFF00115A),
    secondary = Color(0xFF545E7A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E2FF),
    onSecondaryContainer = Color(0xFF111C34),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF151B2F),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF151B2F),
    onSurfaceVariant = Color(0xFF444655),
    surfaceContainerLowest = Color(0xFFFFFFFF), // bottom-sheet surface
    surfaceContainerLow = Color(0xFFF3F3FF),
    surfaceContainer = Color(0xFFEBEDFF),       // chips, panels
    surfaceContainerHigh = Color(0xFFE3E7FF),
    surfaceContainerHighest = Color(0xFFDCE1FD),
    outline = Color(0xFF747687),
    outlineVariant = Color(0xFFC4C5D8),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    inverseSurface = Color(0xFF2A3045),
    inverseOnSurface = Color(0xFFEFF0FF),
    inversePrimary = Color(0xFFBAC3FF),
    surfaceTint = Color(0xFF2447E0),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF8EA3FF),
    onPrimary = Color(0xFF0B1024),
    primaryContainer = Color(0xFF2A3C9A),
    onPrimaryContainer = Color(0xFFDDE2FF),
    secondary = Color(0xFFA7B0CC),
    onSecondary = Color(0xFF1B2236),
    secondaryContainer = Color(0xFF222A45),
    onSecondaryContainer = Color(0xFFDDE2FF),
    background = Color(0xFF0F1424),
    onBackground = Color(0xFFE8ECF8),
    surface = Color(0xFF0F1424),
    onSurface = Color(0xFFE8ECF8),
    onSurfaceVariant = Color(0xFFA7B0CC),
    surfaceContainerLowest = Color(0xFF171D32), // bottom-sheet surface
    surfaceContainerLow = Color(0xFF171D32),
    surfaceContainer = Color(0xFF222A45),       // chips, panels
    surfaceContainerHigh = Color(0xFF2A3352),
    surfaceContainerHighest = Color(0xFF333D5E),
    outline = Color(0xFF6B7694),
    outlineVariant = Color(0xFF333D5E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE8ECF8),
    inverseOnSurface = Color(0xFF151B2F),
    inversePrimary = Color(0xFF2447E0),
    surfaceTint = Color(0xFF8EA3FF),
)

/** Semantic colours that Material's ColorScheme has no slot for (diff marks, success, sheet scrim). */
@Immutable
data class AipbExtraColors(
    val insertBackground: Color,
    val insertText: Color,
    val deleteText: Color,
    val success: Color,
    val scrim: Color,
)

internal val LightExtraColors = AipbExtraColors(
    insertBackground = Color(0xFFFFF1A8), // highlighter yellow
    insertText = Color(0xFF151B2F),
    deleteText = Color(0xFFB3261E),       // muted red, struck through
    success = Color(0xFF1E7B4F),
    scrim = Color(0x66151B2F),            // 40% navy
)

internal val DarkExtraColors = AipbExtraColors(
    insertBackground = Color(0x40FFE45C), // yellow at 25%
    insertText = Color(0xFFE8ECF8),
    deleteText = Color(0xFFFFB4AB),
    success = Color(0xFF7FD6A6),
    scrim = Color(0x99000000),
)
