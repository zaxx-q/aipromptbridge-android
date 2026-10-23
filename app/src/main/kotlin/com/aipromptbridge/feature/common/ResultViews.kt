package com.aipromptbridge.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aipromptbridge.designsystem.AipbIcons
import com.aipromptbridge.designsystem.Panel
import com.aipromptbridge.designsystem.PrimaryButton
import com.aipromptbridge.designsystem.SecondaryButton

/**
 * Error panel shared by TextEdit and Snip. When the cause is a missing key, offers a direct route to setup
 * (a real, actionable error instead of a generic failure).
 */
@Composable
fun ErrorPanel(
    message: String,
    missingApiKey: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    AipbIcons.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Text(message, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (missingApiKey) {
            PrimaryButton("Open setup", onClick = onOpenSettings, modifier = Modifier.fillMaxWidth())
        } else {
            SecondaryButton("Retry", onClick = onRetry, icon = AipbIcons.Retry, modifier = Modifier.fillMaxWidth())
        }
    }
}
