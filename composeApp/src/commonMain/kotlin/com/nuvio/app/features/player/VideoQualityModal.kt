package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.streams.StreamItem
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_player_quality
import org.jetbrains.compose.resources.stringResource

@Composable
fun VideoQualityModal(
    visible: Boolean,
    videoTracks: List<VideoTrack>,
    selectedIndex: Int,
    onTrackSelected: (Int) -> Unit,
    availableStreams: List<StreamItem> = emptyList(),
    activeStreamUrl: String = "",
    onStreamSelected: (StreamItem) -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerOverlayScaffold(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
        contentPadding = PaddingValues(start = 44.dp, end = 44.dp, top = 28.dp, bottom = 64.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val railWidth = minOf(maxWidth, 444.dp)
            val railMaxHeight = (maxHeight - 64.dp).coerceAtLeast(120.dp).coerceAtMost(620.dp)

            Column(
                modifier = Modifier
                    .width(railWidth)
                    .fillMaxHeight()
                    .align(Alignment.BottomStart),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = stringResource(Res.string.compose_player_quality),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                val showTracks = videoTracks.isNotEmpty()
                val allTracks = if (videoTracks.any { it.index == -1 }) {
                    videoTracks
                } else if (showTracks) {
                    listOf(
                        VideoTrack(
                            index = -1,
                            id = "auto",
                            label = "Auto",
                            isSelected = selectedIndex == -1 || videoTracks.none { it.isSelected },
                        ),
                    ) + videoTracks
                } else {
                    emptyList()
                }

                val playableStreams = availableStreams.filter { !it.playableDirectUrl.isNullOrBlank() }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = railMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    if (allTracks.isNotEmpty()) {
                        if (playableStreams.isNotEmpty()) {
                            item(key = "header-quality") {
                                Text(
                                    text = "Resolution",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                                )
                            }
                        }
                        items(allTracks, key = { "track:${it.index}:${it.id}" }) { track ->
                            val isSelected = if (track.index == -1) {
                                selectedIndex == -1 || (videoTracks.none { it.isSelected } && selectedIndex !in videoTracks.map { it.index })
                            } else {
                                track.index == selectedIndex || track.isSelected
                            }
                            VideoQualityRow(
                                track = track,
                                isSelected = isSelected,
                                onClick = {
                                    onTrackSelected(track.index)
                                    onDismiss()
                                },
                            )
                        }
                    }

                    if (playableStreams.isNotEmpty()) {
                        if (allTracks.isNotEmpty()) {
                            item(key = "header-servers") {
                                Text(
                                    text = "Sources / Servers",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                )
                            }
                        }
                        items(playableStreams, key = { "stream:${it.playableDirectUrl}:${it.streamLabel}" }) { stream ->
                            val isSelected = stream.playableDirectUrl == activeStreamUrl
                            StreamServerRow(
                                stream = stream,
                                isSelected = isSelected,
                                onClick = {
                                    onStreamSelected(stream)
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoQualityRow(
    track: VideoTrack,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val backgroundColor = if (isSelected) {
        tokens.colors.accent.copy(alpha = 0.25f)
    } else {
        tokens.colors.surface.copy(alpha = 0.6f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.label,
                color = if (isSelected) tokens.colors.accent else Color.White,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.bitrate > 0) {
                val rateStr = if (track.bitrate >= 1_000_000) {
                    "${track.bitrate / 1_000_000} Mbps"
                } else {
                    "${track.bitrate / 1000} kbps"
                }
                Text(
                    text = rateStr,
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = tokens.colors.accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun StreamServerRow(
    stream: StreamItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val backgroundColor = if (isSelected) {
        tokens.colors.accent.copy(alpha = 0.25f)
    } else {
        tokens.colors.surface.copy(alpha = 0.6f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stream.streamLabel,
                color = if (isSelected) tokens.colors.accent else Color.White,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            stream.streamSubtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = tokens.colors.accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
