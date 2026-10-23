package com.aipromptbridge.feature.textedit

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import com.aipromptbridge.AipbApp
import com.aipromptbridge.MainActivity
import com.aipromptbridge.designsystem.AipbTheme
import com.aipromptbridge.feature.common.copyToClipboard

/**
 * Entry point for the TextEdit tool: registered for android.intent.action.PROCESS_TEXT, so it shows up in the
 * text-selection menu of any app that supports it. The activity uses a translucent theme and hosts a ModalBottomSheet,
 * so the sheet appears over the app the text came from.
 *
 * Contract (verified in Android docs):
 *  - EXTRA_PROCESS_TEXT           = the selected text
 *  - EXTRA_PROCESS_TEXT_READONLY  = true when the host field cannot accept a replacement
 *  - to replace the selection: setResult(RESULT_OK, Intent().putExtra(EXTRA_PROCESS_TEXT, newText)) then finish()
 */
class ProcessTextActivity : ComponentActivity() {

    private val selectedText: String by lazy {
        intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
    }
    private val isReadOnly: Boolean by lazy {
        intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
    }

    private val viewModel: TextEditViewModel by viewModels {
        TextEditViewModelFactory((application as AipbApp).container, selectedText, isReadOnly)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (selectedText.isBlank()) {
            Toast.makeText(this, "No text selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            AipbTheme {
                LaunchedEffect(Unit) {
                    viewModel.autoReplace.collect { replaceSelectionAndFinish(it) }
                }
                TextEditSheet(
                    viewModel = viewModel,
                    onDismiss = { finish() },
                    onReplace = { replaceSelectionAndFinish(it) },
                    onCopy = { copyToClipboard(it) },
                    onOpenSetup = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }

    private fun replaceSelectionAndFinish(text: String) {
        if (!isReadOnly) {
            setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text))
        }
        finish()
    }
}
