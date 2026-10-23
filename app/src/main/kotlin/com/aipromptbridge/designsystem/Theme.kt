package com.aipromptbridge.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalAipbExtraColors = staticCompositionLocalOf { LightExtraColors }

/** App theme. Follows the system light/dark setting. No dynamic colour on purpose: the brand look is the product. */
@Composable
fun AipbTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAipbExtraColors provides if (darkTheme) DarkExtraColors else LightExtraColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AipbTypography,
            shapes = AipbShapes,
            content = content,
        )
    }
}

/** Access to the extra semantic colours: `Aipb.extra.insertBackground`. */
object Aipb {
    val extra: AipbExtraColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAipbExtraColors.current
}
