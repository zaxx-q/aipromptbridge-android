package com.aipromptbridge.designsystem

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aipromptbridge.core.diff.WordDiff

/**
 * Design-system gallery. Open this file in Android Studio's Split/Design view to compare against
 * design/stitch/*/screen.png without running the app. Each preview renders in light AND dark.
 */
@Preview(name = "Light", showBackground = true, backgroundColor = 0xFFFAF8FF)
@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF0F1424, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class ThemePreviews

@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    AipbTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                content()
            }
        }
    }
}

@ThemePreviews
@Composable
private fun HeaderRowPreview() = PreviewFrame {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SegmentedToggle(listOf(ToggleOption("Edit", "edit_note"), ToggleOption("Chat", "chat")), selectedIndex = 0, onSelect = {})
        ProfileChip("Fast · gemini-3.5-flash", modifier = Modifier.weight(1f))
    }
}

@ThemePreviews
@Composable
private fun ActionChipsPreview() = PreviewFrame {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionChip("Fix Grammar", "spellcheck", selected = true, onClick = {})
        ActionChip("Humanizer", "face", selected = false, onClick = {})
        ActionChip("Rewrite", "edit_note", selected = false, onClick = {})
    }
}

@ThemePreviews
@Composable
private fun DiffResultPreview() = PreviewFrame {
    // Same example as the Stitch "done" screen: insertions = highlighter yellow, deletions = struck red.
    val diff = WordDiff.diff(
        "I wanted to follow up on the proposal sent last Thursday. Please let me know if we have any questions.",
        "I wanted to follow up on the proposal we sent last Thursday. Please let me know if you have any questions.",
    )
    Panel { DiffText(diff) }
    Text("${WordDiff.changeCount(diff)} changes", style = MaterialTheme.typography.bodyMedium)
}

@ThemePreviews
@Composable
private fun ButtonsPreview() = PreviewFrame {
    PrimaryButton("Replace selection", onClick = {}, modifier = Modifier.fillMaxWidth(), iconId = "check")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton("Copy", onClick = {}, icon = AipbIcons.Copy, modifier = Modifier.weight(1f))
        SecondaryButton("Retry", onClick = {}, icon = AipbIcons.Retry, modifier = Modifier.weight(1f))
    }
    TextAction("Stop", onClick = {})
}

@ThemePreviews
@Composable
private fun InputsAndLoadingPreview() = PreviewFrame {
    InstructionField(value = "", onValueChange = {}, placeholder = "Or tell it what to change…", onSend = {})
    Panel { SkeletonLines() }
}
