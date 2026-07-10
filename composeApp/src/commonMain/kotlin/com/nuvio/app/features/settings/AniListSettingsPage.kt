package com.nuvio.app.features.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.anilist.AniListAuthRepository
import com.nuvio.app.features.anilist.AniListConnectionMode
import com.nuvio.app.features.anilist.AniListSettingsRepository
import com.nuvio.app.features.anilist.AniListSyncCoordinator
import org.jetbrains.compose.resources.stringResource
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_settings_page_anilist

internal fun LazyListScope.aniListSettingsContent(
    isTablet: Boolean
) {
    item {
        SettingsGroup(isTablet = isTablet) {
            AniListBrandIntro(isTablet = isTablet)
        }
    }

    item {
        SettingsSection(
            title = "AniList Integration Settings",
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                AniListConnectionCard(isTablet = isTablet)
            }
        }
    }
}

@Composable
private fun AniListBrandIntro(
    isTablet: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = if (isTablet) 24.dp else 16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Connect AniList",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Synchronize your anime watch history and status directly with AniList. Track your progress automatically from local playback.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AniListConnectionCard(
    isTablet: Boolean
) {
    val authUiState by AniListAuthRepository.uiState.collectAsState()
    val settingsUiState by AniListSettingsRepository.uiState.collectAsState()
    val isSyncing by AniListSyncCoordinator.isSyncing.collectAsState()
    val syncMessage by AniListSyncCoordinator.syncMessage.collectAsState()
    
    val uriHandler = LocalUriHandler.current
    var showDisconnectConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = if (isTablet) 24.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (authUiState.mode) {
            AniListConnectionMode.CONNECTED -> {
                // Profile & Connection Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!authUiState.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = authUiState.avatarUrl,
                            contentDescription = authUiState.username,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = authUiState.username?.take(1)?.uppercase() ?: "A",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = authUiState.username ?: "AniList User",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Connected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Button(
                        onClick = { showDisconnectConfirm = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Disconnect")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Sync Settings Switches
                SettingsSwitchRow(
                    title = "Enable AniList Sync",
                    description = "Master sync control for AniList watch progress.",
                    checked = settingsUiState.enableSync,
                    isTablet = isTablet,
                    onCheckedChange = { AniListSettingsRepository.setEnableSync(it) }
                )

                SettingsSwitchRow(
                    title = "Sync Watching Progress",
                    description = "Synchronize watching state changes with AniList.",
                    checked = settingsUiState.syncWatching,
                    enabled = settingsUiState.enableSync,
                    isTablet = isTablet,
                    onCheckedChange = { AniListSettingsRepository.setSyncWatching(it) }
                )

                SettingsSwitchRow(
                    title = "Auto Sync While Watching",
                    description = "Upload progress automatically while playing files.",
                    checked = settingsUiState.autoSync,
                    enabled = settingsUiState.enableSync,
                    isTablet = isTablet,
                    onCheckedChange = { AniListSettingsRepository.setAutoSync(it) }
                )

                SettingsSwitchRow(
                    title = "Sync on App Launch",
                    description = "Run a full sync check every time the app opens.",
                    checked = settingsUiState.syncOnLaunch,
                    enabled = settingsUiState.enableSync,
                    isTablet = isTablet,
                    onCheckedChange = { AniListSettingsRepository.setSyncOnLaunch(it) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Sync controls and Last Synced timestamp
                if (settingsUiState.enableSync) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Button(
                            onClick = { AniListSyncCoordinator.syncNow() },
                            enabled = !isSyncing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Syncing...")
                            } else {
                                Text("Sync Now")
                            }
                        }

                        if (!syncMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = syncMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (settingsUiState.lastSyncTimestamp > 0L) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Last Synced: Just now", // Simplification matching clock provider
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            AniListConnectionMode.DISCONNECTED -> {
                Text(
                    text = "You are not connected to AniList. Connect your account to synchronize anime shelves and tracking state.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!authUiState.errorMessage.isNullOrBlank()) {
                    Text(
                        text = authUiState.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val authUrl = AniListAuthRepository.onConnectRequested()
                        runCatching { uriHandler.openUri(authUrl) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect AniList")
                }
            }

            AniListConnectionMode.LOADING -> {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    // Confirmation Disconnect Dialog
    if (showDisconnectConfirm) {
        BasicAlertDialog(onDismissRequest = { showDisconnectConfirm = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Disconnect AniList?",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Are you sure you want to disconnect your AniList account? This will clear local anime progress syncing settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { showDisconnectConfirm = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                AniListAuthRepository.disconnect()
                                showDisconnectConfirm = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text("Disconnect")
                        }
                    }
                }
            }
        }
    }
}
