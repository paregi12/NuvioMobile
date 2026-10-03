package com.nuvio.app.features.anilist.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.anilist.AniListLibraryMenuPrefs
import com.nuvio.app.features.anilist.AniListSortBy
import com.nuvio.app.core.ui.floatingNavigationBarPadding
import com.nuvio.app.core.ui.LocalNuvioNavBarScrollState
import com.nuvio.app.core.ui.nuvio

// ── Colour palette ──────────────────────────────────────────────────────────
private enum class MenuTab { SORT, OPEN_BY }

// ─────────────────────────────────────────────────────────────────────────────
// Public entry point: wrap your screen content + FAB in this composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AniListLibraryActionMenu(
    animeAddons: List<ManagedAddon>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val prefs by AniListLibraryMenuPrefs.state.collectAsStateWithLifecycle()
    val tokens = MaterialTheme.nuvio
    val navBarLabelVisibility = LocalNuvioNavBarScrollState.current?.labelVisibility ?: 1f

    var menuOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // ── Main content ───────────────────────────────────────────────────
        content()

        // ── Dim backdrop when menu open ───────────────────────────────────
        AnimatedVisibility(
            visible = menuOpen,
            enter = fadeIn(tween(150)),
            exit  = fadeOut(tween(150)),
            modifier = Modifier
                .matchParentSize()
                .zIndex(1f)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) { menuOpen = false }
            )
        }

        // ── FAB + popup column anchored bottom-end ────────────────────────
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp,
                    bottom = floatingNavigationBarPadding().calculateBottomPadding() + (8.dp * navBarLabelVisibility),
                )
                .zIndex(2f)
        ) {
            // Popup
            AnimatedVisibility(
                visible = menuOpen,
                enter = fadeIn(tween(180)) + scaleIn(
                    initialScale = 0.85f,
                    transformOrigin = TransformOrigin(1f, 1f),
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
                ),
                exit = fadeOut(tween(140)) + scaleOut(
                    targetScale = 0.85f,
                    transformOrigin = TransformOrigin(1f, 1f),
                    animationSpec = tween(140)
                ),
            ) {
                AniListMenuPopup(
                    prefs = prefs,
                    animeAddons = animeAddons,
                    onDismiss = { menuOpen = false },
                    modifier = Modifier
                        .widthIn(min = 300.dp, max = 340.dp)
                        .shadow(24.dp, RoundedCornerShape(20.dp))
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // FAB
            Surface(
                modifier = Modifier
                    .height(48.dp)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Open library options",
                        onClick = { menuOpen = !menuOpen },
                    ),
                shape = RoundedCornerShape(50),
                color = if (menuOpen) tokens.colors.accent else tokens.colors.surfaceElevated,
                contentColor = tokens.colors.textPrimary,
                shadowElevation = tokens.elevation.overlay,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Open with", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Popup card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AniListMenuPopup(
    prefs: com.nuvio.app.features.anilist.AniListLibraryMenuPrefsState,
    animeAddons: List<ManagedAddon>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(MenuTab.SORT) }
    val colors = MaterialTheme.nuvio.colors

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceElevated)
            .padding(16.dp)
    ) {
        // Segmented selector
        MenuSegmentedSelector(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Content — crossfade between Sort and Open By
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(150))
            },
            label = "menu_tab_content"
        ) { tab ->
            when (tab) {
                MenuTab.SORT -> SortContent(
                    prefs = prefs,
                    onOptionSelected = { sortBy ->
                        AniListLibraryMenuPrefs.setSortBy(sortBy)
                        onDismiss()
                    },
                    onToggleDirection = {
                        AniListLibraryMenuPrefs.setSortAscending(!prefs.sortAscending)
                    }
                )
                MenuTab.OPEN_BY -> OpenByContent(
                    addons = animeAddons,
                    selectedUrl = prefs.openByCatalogUrl,
                    onSelected = { url ->
                        AniListLibraryMenuPrefs.setOpenByCatalogUrl(url)
                        onDismiss()
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Segmented selector with animated pill
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MenuSegmentedSelector(
    selectedTab: MenuTab,
    onTabSelected: (MenuTab) -> Unit,
) {
    val tabs = MenuTab.entries
    val selectedIndex = tabs.indexOf(selectedTab)
    val colors = MaterialTheme.nuvio.colors

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50.dp))
            .background(colors.surface)
            .padding(4.dp)
    ) {
        val tabWidthDp = maxWidth / tabs.size
        val pillOffsetDp by animateDpAsState(
            targetValue = tabWidthDp * selectedIndex,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
            label = "pill"
        )

        // Pill background
        Box(
            modifier = Modifier
                .offset(x = pillOffsetDp)
                .width(tabWidthDp)
                .height(40.dp)
                .shadow(4.dp, RoundedCornerShape(50.dp))
                .clip(RoundedCornerShape(50.dp))
                .background(colors.accent)
        )

        // Tab labels
        Row(modifier = Modifier.fillMaxWidth()) {
            tabs.forEach { tab ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(tabWidthDp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(50.dp))
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) { onTabSelected(tab) }
                ) {
                    Text(
                        text = if (tab == MenuTab.SORT) "Sort" else "Open By",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = if (tab == selectedTab) colors.textPrimary else colors.textMuted
                        )
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sort content
// ─────────────────────────────────────────────────────────────────────────────

private data class SortOption(val label: String, val sortBy: AniListSortBy, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun SortContent(
    prefs: com.nuvio.app.features.anilist.AniListLibraryMenuPrefsState,
    onOptionSelected: (AniListSortBy) -> Unit,
    onToggleDirection: () -> Unit,
) {
    val colors = MaterialTheme.nuvio.colors
    val options = listOf(
        SortOption("Last Updated", AniListSortBy.LAST_UPDATED, Icons.Rounded.Tune),
        SortOption("Score",        AniListSortBy.SCORE,        Icons.Rounded.Tune),
        SortOption("Title",        AniListSortBy.TITLE,        Icons.Rounded.Tune),
        SortOption("Release Date", AniListSortBy.RELEASE_DATE, Icons.Rounded.Tune),
    )

    Column {
        // Header row: "Sort By" + direction toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sort By",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(colors.overlaySelected)
                    .clickable { onToggleDirection() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (prefs.sortAscending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (prefs.sortAscending) "Ascending" else "Descending",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = colors.accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        options.forEach { option ->
            SortOptionRow(
                label = option.label,
                isSelected = prefs.sortBy == option.sortBy,
                onClick = { onOptionSelected(option.sortBy) }
            )
        }
    }
}

@Composable
private fun SortOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.nuvio.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50.dp))
            .background(if (isSelected) colors.overlaySelected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = colors.textPrimary,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Open By content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OpenByContent(
    addons: List<ManagedAddon>,
    selectedUrl: String?,
    onSelected: (String?) -> Unit,
) {
    val colors = MaterialTheme.nuvio.colors
    Column {
        Text(
            text = "Open By",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        )
        Spacer(modifier = Modifier.height(12.dp))

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            // "None" row — default (title search)
            OpenByRow(
                name = "None (Search by Title)",
                logoUrl = null,
                isSelected = selectedUrl == null,
                onClick = { onSelected(null) }
            )

            addons.forEach { addon ->
                val manifest = addon.manifest ?: return@forEach
                OpenByRow(
                    name = addon.displayTitle,
                    logoUrl = manifest.logoUrl,
                    isSelected = selectedUrl == manifest.transportUrl,
                    onClick = { onSelected(manifest.transportUrl) }
                )
            }
        }
    }
}

@Composable
private fun OpenByRow(
    name: String,
    logoUrl: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.nuvio.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50.dp))
            .background(if (isSelected) colors.overlaySelected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surface),
            contentAlignment = Alignment.Center
        ) {
            if (logoUrl != null) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = name,
                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp))
                )
            } else {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = colors.accent,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = colors.textPrimary,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
