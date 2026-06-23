package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.logging.InAppLogger
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_advanced_debugging_clear_logs
import nuvio.composeapp.generated.resources.settings_advanced_debugging_clear_logs_description
import nuvio.composeapp.generated.resources.settings_advanced_debugging_copy_logs
import nuvio.composeapp.generated.resources.settings_advanced_debugging_copy_logs_description
import nuvio.composeapp.generated.resources.settings_advanced_debugging_empty
import nuvio.composeapp.generated.resources.settings_advanced_debugging_log_viewer_description
import nuvio.composeapp.generated.resources.settings_advanced_section_debugging
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.debugLogsSettingsContent(
    isTablet: Boolean,
) {
    item {
        DebugLogsSection(isTablet = isTablet)
    }
}

@Composable
private fun DebugLogsSection(isTablet: Boolean) {
    val clipboardManager = LocalClipboardManager.current
    val logLines by InAppLogger.lines.collectAsState()
    val logText = logLines.joinToString(separator = "\n")

    SettingsSection(
        title = stringResource(Res.string.settings_advanced_section_debugging),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = stringResource(Res.string.settings_advanced_debugging_copy_logs),
                description = stringResource(
                    Res.string.settings_advanced_debugging_copy_logs_description,
                    logLines.size,
                ),
                enabled = logLines.isNotEmpty(),
                isTablet = isTablet,
                onClick = {
                    clipboardManager.setText(AnnotatedString(logText))
                },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.settings_advanced_debugging_clear_logs),
                description = stringResource(Res.string.settings_advanced_debugging_clear_logs_description),
                enabled = logLines.isNotEmpty(),
                isTablet = isTablet,
                onClick = InAppLogger::clear,
            )
            SettingsGroupDivider(isTablet = isTablet)
            DebugLogTextPanel(
                text = logText.ifBlank { stringResource(Res.string.settings_advanced_debugging_empty) },
                isEmpty = logLines.isEmpty(),
                isTablet = isTablet,
            )
        }
    }
}

@Composable
private fun DebugLogTextPanel(
    text: String,
    isEmpty: Boolean,
    isTablet: Boolean,
) {
    SelectionContainer {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (isTablet) 320.dp else 260.dp, max = if (isTablet) 560.dp else 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (isEmpty) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontFamily = FontFamily.Monospace,
        )
    }
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = stringResource(Res.string.settings_advanced_debugging_log_viewer_description),
        modifier = Modifier.padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = 0.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(16.dp))
}
