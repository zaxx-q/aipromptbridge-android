package com.aipromptbridge.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * THE ONLY FILE that references the icon library. PromptAction.icon / PromptModifier.icon are string ids
 * (generated from the original app's emoji icons) resolved here. Unknown ids fall back to a neutral glyph.
 *
 * If material-icons-extended is unavailable, replace the right-hand sides with Material Symbols vector assets
 * (Android Studio: New > Vector Asset) — nothing else in the app needs to change.
 */
object AipbIcons {
    // ---- fixed UI icons
    val Close: ImageVector get() = Icons.Outlined.Close
    val Copy: ImageVector get() = Icons.Outlined.ContentCopy
    val Retry: ImageVector get() = Icons.Outlined.Refresh
    val Send: ImageVector get() = Icons.Outlined.ArrowUpward
    val Tune: ImageVector get() = Icons.Outlined.Tune
    val Settings: ImageVector get() = Icons.Outlined.Settings
    val Share: ImageVector get() = Icons.Outlined.Share
    val Crop: ImageVector get() = Icons.Outlined.Crop
    val Fullscreen: ImageVector get() = Icons.Outlined.Fullscreen
    val Check: ImageVector get() = Icons.Outlined.Check
    val Back: ImageVector get() = Icons.AutoMirrored.Outlined.ArrowBack
    val Delete: ImageVector get() = Icons.Outlined.Delete
    val Search: ImageVector get() = Icons.Outlined.Search
    val Add: ImageVector get() = Icons.Outlined.Add
    val ExpandMore: ImageVector get() = Icons.Outlined.ExpandMore
    val Quote: ImageVector get() = Icons.Outlined.TextFields
    val Info: ImageVector get() = Icons.Outlined.Info
    val Chevron: ImageVector get() = Icons.AutoMirrored.Outlined.KeyboardArrowRight
    val ArrowUp: ImageVector get() = Icons.Outlined.ArrowUpward
    val ArrowDown: ImageVector get() = Icons.Outlined.ArrowDownward
    val Palette: ImageVector get() = Icons.Outlined.Palette
    val Test: ImageVector get() = Icons.Outlined.Science
    val Revert: ImageVector get() = Icons.Outlined.History
    val Edit: ImageVector get() = Icons.Outlined.Edit
    val Key: ImageVector get() = Icons.Outlined.Key
    val Provider: ImageVector get() = Icons.Outlined.Public
    val Snip: ImageVector get() = Icons.Outlined.Crop
    val TextSelection: ImageVector get() = Icons.Outlined.TextFields
    val Wand: ImageVector get() = Icons.Outlined.AutoFixHigh

    /**
     * Icon ids offered by the action icon picker: one id per distinct glyph (aliases such as "reply" -> chat are left out).
     * Every id here is resolved by [byId]; keep the two in sync when adding icons.
     */
    val pickerIds: List<String> = listOf(
        "auto_fix_high", "spellcheck", "edit_note", "swap_horiz", "mood", "face", "child_care", "lightbulb",
        "psychology", "summarize", "library_books", "table_chart", "translate", "public", "code", "bug_report",
        "build", "rate_review", "work", "chat", "content_cut", "description", "image", "visibility",
        "info", "key", "check", "add", "tune", "arrow_forward",
    )

    // ---- action / modifier icons by id
    fun byId(id: String): ImageVector = when (id) {
        "lightbulb" -> Icons.Outlined.Lightbulb
        "child_care" -> Icons.Outlined.ChildCare
        "mood" -> Icons.Outlined.Mood
        "public" -> Icons.Outlined.Public
        "summarize" -> Icons.Outlined.Summarize
        "library_books" -> Icons.Outlined.LibraryBooks
        "table_chart" -> Icons.Outlined.TableChart
        "key" -> Icons.Outlined.Key
        "spellcheck" -> Icons.Outlined.Spellcheck
        "auto_fix_high" -> Icons.Outlined.AutoFixHigh
        "edit_note" -> Icons.Outlined.EditNote
        "swap_horiz" -> Icons.Outlined.SwapHoriz
        "work" -> Icons.Outlined.Work
        "chat", "reply" -> Icons.Outlined.Chat
        "content_cut" -> Icons.Outlined.ContentCut
        "face" -> Icons.Outlined.Face
        "arrow_forward" -> Icons.AutoMirrored.Outlined.ArrowForward
        "check" -> Icons.Outlined.Check
        "translate" -> Icons.Outlined.Translate
        "code" -> Icons.Outlined.Code
        "rate_review" -> Icons.Outlined.RateReview
        "bug_report" -> Icons.Outlined.BugReport
        "build" -> Icons.Outlined.Build
        "description" -> Icons.Outlined.Description
        "image" -> Icons.Outlined.Image
        "visibility" -> Icons.Outlined.Visibility
        "psychology" -> Icons.Outlined.Psychology
        "help", "info" -> Icons.Outlined.Info
        "bolt" -> Icons.Outlined.AutoFixHigh // TODO: swap for a real bolt glyph once Material Symbols assets are added
        "add" -> Icons.Outlined.Add
        "tune", "format_list_numbered" -> Icons.Outlined.Tune
        else -> Icons.Outlined.AutoFixHigh
    }
}
