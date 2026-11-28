@file:OptIn(ExperimentalFoundationApi::class)

package com.astracode.app.ui.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.astracode.app.ui.components.AText
import com.astracode.app.ui.components.AstraBadge
import com.astracode.app.ui.components.PulseDot
import com.astracode.app.ui.components.borderBottom
import com.astracode.app.ui.components.borderEnd
import com.astracode.app.ui.components.borderTop
import com.astracode.app.ui.components.panel
import com.astracode.app.ui.components.pressScale
import com.astracode.app.ui.theme.AstraIcons
import kotlinx.coroutines.launch


/** LazyColumn item index of each drawer section (see item order in [AstraDrawerContent]). */
private val DrawerSection.listIndex: Int
    get() = when (this) {
        DrawerSection.Files -> 2
        // The source HTML links "Commands" to #section-commands, which does not exist; File Operations is the closest block.
        DrawerSection.Commands -> 3
        DrawerSection.Lsp -> 4
        DrawerSection.Prefs -> 5
    }

/**
 * Drawer body. Width is supplied by the caller (86% of screen, max 340dp) so ModalDrawerSheet owns sizing.
 * Stateless: everything comes from [state], everything goes out through [onEvent].
 */
@Composable
fun AstraDrawerContent(
    state: DrawerUiState,
    onEvent: (DrawerEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(
        modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .borderEnd(MaterialTheme.colorScheme.outlineVariant),
    ) {
        DrawerTop(
            state = state,
            onClose = onClose,
            onSection = { section ->
                onEvent(DrawerEvent.SectionSelected(section))
                scope.launch { listState.animateScrollToItem(section.listIndex) }
            },
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "project") {
                ProjectCard(state.project) { onEvent(DrawerEvent.Action(DrawerAction.CreateWorkspace)) }
            }
            item(key = "workspaces") { WorkspacesSection(state, onEvent) }
            item(key = "files") { Box(Modifier.padding(top = 4.dp)) { FilesSection(state, onEvent) } }
            item(key = "fileops") { FileOpsSection(state, onEvent) }
            item(key = "lsp") { Box(Modifier.padding(top = 4.dp)) { LspSection(state, onEvent) } }
            item(key = "prefs") { Box(Modifier.padding(top = 4.dp)) { PrefsSection(state, onEvent) } }
        }

        ToolDock(state, onEvent)
    }
}

// ───────────────────────────────────────────── Top: brand header + category rail ─────────────────────────────────────

@Composable
private fun DrawerTop(
    state: DrawerUiState,
    onClose: () -> Unit,
    onSection: (DrawerSection) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.95f))
            .borderBottom(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandBadge()
                Column(Modifier.weight(1f, fill = false)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AText("AstraCode", MaterialTheme.colorScheme.onSurface, 15f, weight = FontWeight.Bold, mono = false, letterSpacing = -0.375f)
                        AstraBadge(
                            text = state.versionLabel,
                            fg = MaterialTheme.colorScheme.primary,
                            bg = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            border = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        PulseDot(MaterialTheme.colorScheme.primary, 6.dp)
                        AText(state.syncLabel, MaterialTheme.colorScheme.onSurfaceVariant, 11f, Modifier.weight(1f, fill = false))
                    }
                }
            }
            // -mr-1, 40dp touch target
            Box(
                Modifier
                    .offset(x = 4.dp)
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AstraIcons.CloseBold, "Close drawer", Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val selected = state.selectedSection
            NavChip("Files", AstraIcons.Folder, MaterialTheme.colorScheme.onSurfaceVariant, selected == DrawerSection.Files, null) { onSection(DrawerSection.Files) }
            NavChip("Commands", AstraIcons.Bolt, MaterialTheme.colorScheme.tertiary, selected == DrawerSection.Commands, null) { onSection(DrawerSection.Commands) }
            NavChip("LSP", AstraIcons.Pencil, MaterialTheme.colorScheme.secondary, selected == DrawerSection.Lsp, null) { onSection(DrawerSection.Lsp) }
            NavChip("Prefs", AstraIcons.Gear, MaterialTheme.colorScheme.onSurfaceVariant, selected == DrawerSection.Prefs, null) { onSection(DrawerSection.Prefs) }
        }
    }
}

@Composable
private fun BrandBadge() {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .size(36.dp)
            .shadow(12.dp, shape, ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerLow)))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AstraIcons.Cube, null, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun NavChip(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    selected: Boolean,
    accent: Color?,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh
    val border = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else (accent?.copy(alpha = 0.3f) ?: MaterialTheme.colorScheme.outlineVariant)
    val text = if (selected) MaterialTheme.colorScheme.primary else (accent ?: MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
        Modifier
            .panel(RoundedCornerShape(8.dp), bg, border)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(14.dp), if (selected) MaterialTheme.colorScheme.primary else iconTint)
        AText(label, text, 11f, weight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ───────────────────────────────────────────────── Project card ──────────────────────────────────────────────────────

@Composable
private fun ProjectCard(project: ProjectSummaryUi, onCreate: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.surfaceContainerHigh)))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .panel(RoundedCornerShape(8.dp), MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AstraIcons.ProjectCube, null, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AText(project.name, MaterialTheme.colorScheme.onSurface, 12f, Modifier.weight(1f, fill = false), FontWeight.Bold)
                        FileBadgePill(project.badge, bold = true, hPad = 4.dp)
                    }
                    Row(
                        Modifier.padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(AstraIcons.GitCircle, null, Modifier.size(12.dp), MaterialTheme.colorScheme.secondary)
                            AText(project.branch, MaterialTheme.colorScheme.secondary, 10f)
                        }
                        AText("•", MaterialTheme.colorScheme.onSurfaceVariant, 10f)
                        AText(project.path, MaterialTheme.colorScheme.primary, 10f, Modifier.weight(1f, fill = false))
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(32.dp)
                    .panel(RoundedCornerShape(8.dp), MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
                    .clickable(onClick = onCreate),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AstraIcons.Plus, "Create Workspace", Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                AstraBadge("${project.modifiedCount} mod", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f), MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f), size = 10f)
                if (project.autoSync) {
                    Row(
                        Modifier
                            .panel(RoundedCornerShape(4.dp), MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(AstraIcons.Sync, null, Modifier.size(10.dp), MaterialTheme.colorScheme.primary)
                        AText("Auto-Sync", MaterialTheme.colorScheme.primary, 10f)
                    }
                }
            }
            AText(project.localSizeLabel, MaterialTheme.colorScheme.onSurfaceVariant, 9f)
        }
    }
}

@Composable
private fun FileBadgePill(badge: FileBadge, bold: Boolean, hPad: androidx.compose.ui.unit.Dp = 6.dp) {
    AstraBadge(
        text = badge.label,
        fg = MaterialTheme.colorScheme.onSurface,
        bg = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = MaterialTheme.colorScheme.outlineVariant,
        weight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
        hPad = hPad,
    )
}

// ───────────────────────────────────────────────── Shared pieces ─────────────────────────────────────────────────────

@Composable
private fun SectionTitle(
    icon: ImageVector,
    tint: Color,
    title: String,
    titleColor: Color,
    weight: FontWeight,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(14.dp), tint)
            AText(title.uppercase(), titleColor, 11f, weight = weight, letterSpacing = 0.55f)
        }
        trailing()
    }
}

@Composable
private fun Accordion(
    leading: @Composable () -> Unit,
    title: String,
    hint: (@Composable () -> Unit)?,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val rotation = animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Column(Modifier.fillMaxWidth().panel(shape, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.outlineVariant)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                leading()
                AText(title.uppercase(), MaterialTheme.colorScheme.onSurfaceVariant, 12f, weight = FontWeight.SemiBold, letterSpacing = 0.6f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                hint?.invoke()
                Icon(
                    AstraIcons.ChevronDown, null,
                    Modifier.size(16.dp).graphicsLayer { rotationZ = rotation.value },
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)))
                content()
            }
        }
    }
}

// ─────────────────────────────────────────────────── Workspaces ──────────────────────────────────────────────────────

@Composable
private fun WorkspacesSection(state: DrawerUiState, onEvent: (DrawerEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle(AstraIcons.Grid, MaterialTheme.colorScheme.primary, "Workspaces", MaterialTheme.colorScheme.onSurfaceVariant, FontWeight.SemiBold) {
            AstraBadge(
                "Hold for menu", MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), null,
                size = 10f, weight = FontWeight.Normal,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.workspaces.forEach { ws ->
                WorkspaceRow(
                    ws = ws,
                    highlighted = state.optionsTarget?.id == ws.id,
                    onClick = { onEvent(DrawerEvent.WorkspaceClicked(ws.id)) },
                    onLongClick = { onEvent(DrawerEvent.WorkspaceLongPressed(ws.id)) },
                )
            }
        }
    }
}

@Composable
private fun WorkspaceRow(
    ws: WorkspaceUi,
    highlighted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val isActive = ws.isActive || ws.kind == WorkspaceKind.Active
    val bg = when {
        highlighted -> MaterialTheme.colorScheme.primaryContainer
        isActive -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val borderColor = when {
        highlighted -> MaterialTheme.colorScheme.secondary
        isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val activeColor = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .panel(shape, bg, borderColor, borderWidth = if (highlighted) 2.dp else 1.dp)
            .heightIn(min = 48.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("drawer-workspace-" + ws.id)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                isActive -> Box(
                    Modifier.size(10.dp)
                        .drawBehind { drawCircle(activeColor.copy(alpha = 0.2f), radius = size.minDimension / 2f + 4.dp.toPx()) }
                        .background(activeColor, CircleShape),
                )
                ws.kind == WorkspaceKind.Private -> Icon(AstraIcons.Lock, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                ws.kind == WorkspaceKind.Managed -> Icon(AstraIcons.Folder, null, Modifier.size(16.dp), MaterialTheme.colorScheme.secondary)
                else -> Icon(AstraIcons.Link, null, Modifier.size(16.dp), MaterialTheme.colorScheme.secondary)
            }
            Column(Modifier.weight(1f, fill = false)) {
                AText(
                    ws.name, MaterialTheme.colorScheme.onSurface, 12f,
                    weight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                    mono = false,
                )
                if (isActive) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        PulseDot(MaterialTheme.colorScheme.primary, 5.dp)
                        AText(ws.subtitle, MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), 10f)
                    }
                } else {
                    when (ws.kind) {
                        WorkspaceKind.Active -> AText(ws.subtitle, MaterialTheme.colorScheme.primary, 10f)
                        WorkspaceKind.Private -> AText(ws.subtitle, MaterialTheme.colorScheme.onSurfaceVariant, 10f)
                        WorkspaceKind.Managed -> AText(ws.subtitle, MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f), 10f)
                        WorkspaceKind.RemoteBookmark -> AText(ws.subtitle, MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f), 10f)
                    }
                }
            }
        }
        if (isActive) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (ws.modifiedCount > 0) AstraBadge("${ws.modifiedCount}", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f), MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                Icon(AstraIcons.Check, null, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
            }
        } else {
            when (ws.kind) {
                WorkspaceKind.Active -> Icon(AstraIcons.Check, null, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                WorkspaceKind.Private -> AText(ws.trailing, MaterialTheme.colorScheme.onSurfaceVariant, 10f)
                WorkspaceKind.Managed, WorkspaceKind.RemoteBookmark -> Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(4.dp)).clickable(onClick = onLongClick),
                    contentAlignment = Alignment.Center,
                ) { Icon(AstraIcons.MoreVert, "Workspace menu", Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

// ───────────────────────────────────────────────────── Files ─────────────────────────────────────────────────────────

@Composable
private fun FilesSection(state: DrawerUiState, onEvent: (DrawerEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle(AstraIcons.Folder, MaterialTheme.colorScheme.secondary, "File Explorer", MaterialTheme.colorScheme.secondary, FontWeight.Bold) {
            AstraBadge(
                "${state.fileCount} files", MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                size = 10f, weight = FontWeight.Bold, hPad = 8.dp, radius = 50.dp,
            )
        }
        Column(Modifier.fillMaxWidth().panel(RoundedCornerShape(12.dp), MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.outlineVariant)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .borderBottom(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AText("ACTIVE BUFFERS (${state.buffers.size})", MaterialTheme.colorScheme.onSurfaceVariant, 10f)
                AText(state.projectTypeLabel, MaterialTheme.colorScheme.primary, 10f, weight = FontWeight.Medium)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f))
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.buffers.forEach { buffer ->
                    BufferRow(
                        buffer = buffer,
                        onClick = { onEvent(DrawerEvent.BufferClicked(buffer.name, buffer.path)) },
                        onClose = { onEvent(DrawerEvent.BufferClosed(buffer.name, buffer.path)) },
                    )
                }
            }
            Column(Modifier.fillMaxWidth().borderTop(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))) {
                NavLinkRow(AstraIcons.Folder, "Open Explorer", { onEvent(DrawerEvent.Action(DrawerAction.OpenExplorer)) }) {
                    AText("Browse workspace", MaterialTheme.colorScheme.onSurfaceVariant, 10f)
                }
                NavDivider()
                NavLinkRow(AstraIcons.Clock, "Recent Files", { onEvent(DrawerEvent.Action(DrawerAction.RecentFiles)) }) {
                    AText("Alt+P", MaterialTheme.colorScheme.onSurfaceVariant, 10f)
                }
                NavDivider()
                NavLinkRow(AstraIcons.Search, "Find in Files", { onEvent(DrawerEvent.Action(DrawerAction.FindInFiles)) }) {
                    AText(
                        ".*regex", MaterialTheme.colorScheme.secondary, 10f,
                        Modifier
                            .panel(RoundedCornerShape(4.dp), MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                NavDivider()
                NavLinkRow(AstraIcons.ChevronsRight, "Go to Line", { onEvent(DrawerEvent.Action(DrawerAction.GoToLine)) }) {
                    AText(":line", MaterialTheme.colorScheme.onSurfaceVariant, 10f)
                }
            }
        }
    }
}

@Composable
private fun BufferRow(buffer: BufferUi, onClick: () -> Unit, onClose: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val rowModifier = if (buffer.isActive) {
        Modifier.panel(shape, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    } else {
        Modifier.clip(shape)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(rowModifier)
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .testTag("drawer-buffer-" + buffer.path.replace("/", "__"))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileBadgePill(buffer.badge, bold = true, hPad = if (buffer.badge == FileBadge.Toml) 4.dp else 6.dp)
            AText(
                buffer.name,
                if (buffer.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                12f,
                Modifier.weight(1f, fill = false),
                if (buffer.isActive) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (buffer.isDirty) {
                AstraBadge("M", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f), MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
            }
            if (buffer.isActive) {
                Box(
                    Modifier
                        .size(8.dp)
                        .shadow(8.dp, CircleShape, ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            } else if (buffer.trailing.isNotBlank()) {
                AText(buffer.trailing, MaterialTheme.colorScheme.onSurfaceVariant, 10f)
            }
            Box(
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onClose)
                    .testTag("drawer-buffer-close-" + buffer.path.replace("/", "__")),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AstraIcons.Close, "Close buffer", Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NavDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)))
}

@Composable
private fun NavLinkRow(icon: ImageVector, label: String, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), MaterialTheme.colorScheme.secondary)
            AText(label, MaterialTheme.colorScheme.onSurface, 12f, weight = FontWeight.Medium, mono = false)
        }
        trailing()
    }
}

// ───────────────────────────────────────────────── File operations ───────────────────────────────────────────────────

@Composable
private fun FileOpsSection(state: DrawerUiState, onEvent: (DrawerEvent) -> Unit) {
    Accordion(
        leading = { Icon(AstraIcons.EditSquare, null, Modifier.size(14.dp), MaterialTheme.colorScheme.primary) },
        title = "File Operations",
        hint = { AText("Quick Actions", MaterialTheme.colorScheme.onSurfaceVariant, 10f) },
        expanded = state.fileOpsExpanded,
        onToggle = { onEvent(DrawerEvent.ToggleFileOps) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FileOpTile("New File", AstraIcons.PlusLines, MaterialTheme.colorScheme.primary, Modifier.weight(1f), null) {
                    onEvent(DrawerEvent.Action(DrawerAction.NewFile))
                }
                FileOpTile("New Folder", AstraIcons.FolderPlus, MaterialTheme.colorScheme.secondary, Modifier.weight(1f), null) {
                    onEvent(DrawerEvent.Action(DrawerAction.NewFolder))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FileOpTile(
                    "Save", AstraIcons.Save, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f),
                    trailing = { AText("^S", MaterialTheme.colorScheme.onSurfaceVariant, 9f) },
                ) { onEvent(DrawerEvent.Action(DrawerAction.Save)) }
                FileOpTile(
                    "Save All", AstraIcons.SaveAll, MaterialTheme.colorScheme.primary, Modifier.weight(1f),
                    trailing = {
                        AstraBadge(
                            "${state.unsavedCount} mod", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), null,
                            weight = FontWeight.Bold, hPad = 4.dp,
                        )
                    },
                ) { onEvent(DrawerEvent.Action(DrawerAction.SaveAll)) }
            }
        }
    }
}

@Composable
private fun FileOpTile(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier,
    trailing: (@Composable () -> Unit)?,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(source, 0.98f)
            .panel(RoundedCornerShape(8.dp), MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            .heightIn(min = 46.dp)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), iconTint)
            AText(label, MaterialTheme.colorScheme.onSurface, 12f, weight = FontWeight.Medium, mono = false)
        }
        trailing?.invoke()
    }
}

// ─────────────────────────────────────────────────────── LSP ─────────────────────────────────────────────────────────

@Composable
private fun LspSection(state: DrawerUiState, onEvent: (DrawerEvent) -> Unit) {
    Accordion(
        leading = { PulseDot(MaterialTheme.colorScheme.primary, 8.dp) },
        title = state.lspName,
        hint = {
            AstraBadge(
                state.lspStatus, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                size = 10f, weight = FontWeight.Normal,
            )
        },
        expanded = state.lspExpanded,
        onToggle = { onEvent(DrawerEvent.ToggleLsp) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LspTile("Go to Definition", "F12", Modifier.weight(1f)) {
                    onEvent(DrawerEvent.Action(DrawerAction.GoToDefinition))
                }
                LspTile("Find References", "Shift+F12", Modifier.weight(1f)) {
                    onEvent(DrawerEvent.Action(DrawerAction.FindReferences))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LspTile("Hover", "Current symbol", Modifier.weight(1f)) {
                    onEvent(DrawerEvent.Action(DrawerAction.Hover))
                }
                Spacer(Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LspTile("Configure", "Server profile", Modifier.weight(1f)) {
                    onEvent(DrawerEvent.Action(DrawerAction.ConfigureLanguageServer))
                }
                if (state.lspStatus.equals("Ready", ignoreCase = true)) {
                    LspTile("Disconnect", "Stop process", Modifier.weight(1f)) {
                        onEvent(DrawerEvent.Action(DrawerAction.DisconnectLanguageServer))
                    }
                } else {
                    LspTile("Connect", "Current file", Modifier.weight(1f)) {
                        onEvent(DrawerEvent.Action(DrawerAction.ConnectLanguageServer))
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .panel(RoundedCornerShape(8.dp), MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.lspStatus.equals("Ready", ignoreCase = true)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AText("Diagnostics:", MaterialTheme.colorScheme.onSurfaceVariant, 11f)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AstraBadge("${state.lspErrors} err", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.error.copy(alpha = 0.2f), null, size = 10f, weight = FontWeight.Bold)
                            AstraBadge("${state.lspWarnings} warn", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f), null, size = 10f, weight = FontWeight.Bold)
                        }
                    }
                } else {
                    AText("Diagnostics unavailable until a language server is connected.", MaterialTheme.colorScheme.onSurfaceVariant, 10f, mono = false)
                }
            }
        }
    }
}

@Composable
private fun LspTile(title: String, shortcut: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .panel(RoundedCornerShape(8.dp), MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        AText(title, MaterialTheme.colorScheme.secondary, 12f, weight = FontWeight.Medium, mono = false)
        AText(shortcut, MaterialTheme.colorScheme.onSurfaceVariant, 9f)
    }
}

// ───────────────────────────────────────────────────── Preferences ───────────────────────────────────────────────────

@Composable
private fun PrefsSection(state: DrawerUiState, onEvent: (DrawerEvent) -> Unit) {
    Accordion(
        leading = { Icon(AstraIcons.Gear, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant) },
        title = "Editor Settings",
        hint = null,
        expanded = state.prefsExpanded,
        onToggle = { onEvent(DrawerEvent.TogglePrefs) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AText("Font Size", MaterialTheme.colorScheme.onSurface, 12f, weight = FontWeight.Medium, mono = false)
                Row(
                    Modifier.panel(RoundedCornerShape(8.dp), MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.colorScheme.outlineVariant).padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StepButton("-") { onEvent(DrawerEvent.FontSizeStep(-1)) }
                    AText("${state.fontSize}sp", MaterialTheme.colorScheme.primary, 12f, Modifier.padding(horizontal = 4.dp))
                    StepButton("+") { onEvent(DrawerEvent.FontSizeStep(+1)) }
                }
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AText("Word Wrap", MaterialTheme.colorScheme.onSurface, 12f, weight = FontWeight.Medium, mono = false)
                AstraToggle(checked = state.wordWrap) { onEvent(DrawerEvent.WordWrapToggled) }
            }
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AText(label, MaterialTheme.colorScheme.onSurfaceVariant, 12f, weight = FontWeight.Bold)
    }
}

/** 36x20 pill toggle from the design (primary/30 track, primary thumb when on). */
@Composable
private fun AstraToggle(checked: Boolean, onToggle: () -> Unit) {
    val thumbOffset = animateDpAsState(if (checked) 14.dp else 0.dp, label = "thumb")
    Box(
        Modifier
            .size(width = 36.dp, height = 20.dp)
            .clip(CircleShape)
            .background(if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(role = Role.Switch, onClick = onToggle)
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(thumbOffset.value.roundToPx(), 0) }
                .size(16.dp)
                .background(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
        )
    }
}

// ───────────────────────────────────────────────────── Bottom dock ───────────────────────────────────────────────────

@Composable
private fun ToolDock(state: DrawerUiState, onEvent: (DrawerEvent) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .borderTop(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f))
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DockTile(Modifier.weight(1f), "Term", MaterialTheme.colorScheme.onSurface, FontWeight.SemiBold, { onEvent(DrawerEvent.Action(DrawerAction.OpenTerminal)) }) {
                Icon(AstraIcons.Terminal, "Terminal", Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
            }
            DockTile(
                Modifier.weight(1f),
                if (state.gitAvailable) "+${state.gitAdded} -${state.gitRemoved}" else "Git",
                if (state.gitAvailable) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                FontWeight.SemiBold,
                { onEvent(DrawerEvent.Action(DrawerAction.OpenGitChanges)) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AstraIcons.GitCircle, "Git Changes", Modifier.size(14.dp), MaterialTheme.colorScheme.secondary)
                }
            }
            DockTile(Modifier.weight(1f), "Test", MaterialTheme.colorScheme.onSurface, FontWeight.SemiBold, { onEvent(DrawerEvent.Action(DrawerAction.RunTests)) }) {
                Icon(AstraIcons.Play, "Run Cargo Tests", Modifier.size(16.dp), MaterialTheme.colorScheme.tertiary)
            }
            DockTile(Modifier.weight(1f), "Config", MaterialTheme.colorScheme.onSurfaceVariant, FontWeight.Normal, { onEvent(DrawerEvent.Action(DrawerAction.OpenConfig)) }) {
                Icon(AstraIcons.Gear, "Quick Settings", Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DockTile(
    modifier: Modifier,
    label: String,
    labelColor: Color,
    weight: FontWeight,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressScale(source)
            .panel(RoundedCornerShape(12.dp), MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.outlineVariant)
            .heightIn(min = 48.dp)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(Modifier.height(4.dp))
        AText(label, labelColor, 9f, weight = weight)
    }
}