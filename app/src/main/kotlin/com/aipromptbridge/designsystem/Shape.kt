package com.aipromptbridge.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Controls (buttons, chips, fields, panels) = 12dp. Bottom sheets = 28dp top corners. */
val AipbShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
