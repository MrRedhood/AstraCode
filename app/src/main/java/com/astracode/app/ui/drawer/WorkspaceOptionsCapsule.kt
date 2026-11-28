package com.astracode.app.ui.drawer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astracode.app.ui.components.AText
import com.astracode.app.ui.components.PulseDot
import com.astracode.app.ui.components.borderTop
import com.astracode.app.ui.components.panel
import com.astracode.app.ui.components.pressScale
import com.astracode.app.ui.theme.AstraIcons

private data class CapsuleItem(
    val action: WorkspaceAction,
    val title: String,
    val subtitle: String,
    val shortcut: String,
    val icon: ImageVector,
    val iconTint: Color,
    val iconBg: Color,
    val iconBorder: Color,
    val shortcutFg: Color,
    val shortcutBg: Color,
    val shortcutBorder: Color,
    val containerBg: Color,
    val containerPressed: Color,
    val containerBorder: Color,
    val titleColor: Color,
    val subtitleColor: Color,
)


@Composable
private fun capsuleItems(): List<CapsuleItem> {
    val colors = MaterialTheme.colorScheme
    val iconBackground = colors.surfaceContainerHigh
    val shortcutBackground = colors.surfaceContainer
    val rowBackground = colors.surfaceContainerLow
    return listOf(
        CapsuleItem(
            action = WorkspaceAction.Rename,
            title = "Rename", subtitle = "Change workspace name", shortcut = "F2",
            icon = AstraIcons.Pencil,
            iconTint = colors.primary, iconBg = iconBackground, iconBorder = colors.outlineVariant,
            shortcutFg = colors.onSurfaceVariant, shortcutBg = shortcutBackground, shortcutBorder = colors.outlineVariant,
            containerBg = rowBackground, containerPressed = colors.surfaceContainerHigh,
            containerBorder = colors.outlineVariant,
            titleColor = colors.onSurface, subtitleColor = colors.onSurfaceVariant,
        ),
        CapsuleItem(
            action = WorkspaceAction.DeleteWorkspace,
            title = "Delete Workspace", subtitle = "Clear or remove this workspace", shortcut = "Confirm",
            icon = AstraIcons.Trash,
            iconTint = colors.error, iconBg = iconBackground, iconBorder = colors.outlineVariant,
            shortcutFg = colors.error, shortcutBg = shortcutBackground, shortcutBorder = colors.outlineVariant,
            containerBg = rowBackground, containerPressed = colors.surfaceContainerHigh,
            containerBorder = colors.outlineVariant,
            titleColor = colors.error, subtitleColor = colors.onSurfaceVariant,
        ),
        CapsuleItem(
            action = WorkspaceAction.Cancel,
            title = "Cancel", subtitle = "Dismiss this menu", shortcut = "Esc",
            icon = AstraIcons.Close,
            iconTint = colors.onSurfaceVariant, iconBg = iconBackground, iconBorder = colors.outlineVariant,
            shortcutFg = colors.onSurfaceVariant, shortcutBg = shortcutBackground, shortcutBorder = colors.outlineVariant,
            containerBg = rowBackground, containerPressed = colors.surfaceContainerHigh,
            containerBorder = colors.outlineVariant,
            titleColor = colors.onSurface, subtitleColor = colors.onSurfaceVariant,
        ),
    )
}

/**
 * Full-screen scrim + centered capsule. Host it above the drawer (see AstraCodeApp) so the scrim covers the whole window.
 */
@Composable
fun WorkspaceOptionsOverlay(
    workspaceName: String,
    onAction: (WorkspaceAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // animate-in: fade + zoom-in-95 over 150ms
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(150)) }

    val capsuleShape = RoundedCornerShape(24.dp)
    val colors = MaterialTheme.colorScheme
    val items = capsuleItems()
    val scrimSource = remember { MutableInteractionSource() }
    val consumeSource = remember { MutableInteractionSource() }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.scrim.copy(alpha = 0.72f))
            .clickable(interactionSource = scrimSource, indication = null, onClick = onDismiss)
            .systemBarsPadding()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = progress.value
                    val s = 0.95f + 0.05f * progress.value
                    scaleX = s
                    scaleY = s
                }
                .shadow(24.dp, capsuleShape, ambientColor = Color.Black.copy(alpha = 0.8f), spotColor = Color.Black.copy(alpha = 0.8f))
                .clip(capsuleShape)
                .background(colors.surfaceContainerLow)
                .border(1.dp, colors.outlineVariant, capsuleShape)
                // swallow taps so only the scrim dismisses
                .clickable(interactionSource = consumeSource, indication = null, onClick = {}),
        ) {
            // Ambient top glow line
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, colors.secondary, Color.Transparent))),
            )

            Column(Modifier.padding(16.dp)) {
                // Header
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PulseDot(colors.secondary, 8.dp)
                        AText(
                            "WORKSPACE OPTIONS", colors.onSurface, 11f,
                            weight = FontWeight.Bold, letterSpacing = 0.55f,
                        )
                    }
                    AText(
                        workspaceName, colors.onSurfaceVariant, 10f,
                        Modifier
                            .panel(CircleShape, colors.surfaceContainerHigh, colors.outlineVariant)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Box(Modifier.padding(top = 12.dp, bottom = 12.dp).fillMaxWidth().height(1.dp).background(colors.outlineVariant))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { item ->
                        CapsuleActionRow(item) { onAction(item.action) }
                    }
                }

                // Footer hint
                Box(
                    Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .borderTop(colors.outlineVariant)
                        .padding(top = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AText("Tap anywhere outside to close", colors.onSurfaceVariant, 10f)
                }
            }
        }
    }
}

@Composable
private fun CapsuleActionRow(item: CapsuleItem, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(source, 0.98f)
            .clip(shape)
            // pressed tint replaces hover:bg-[#203147] from the web design
            .background(if (pressed) item.containerPressed else item.containerBg)
            .border(1.dp, item.containerBorder, shape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .panel(RoundedCornerShape(12.dp), item.iconBg, item.iconBorder),
                contentAlignment = Alignment.Center,
            ) {
                Icon(item.icon, null, Modifier.size(16.dp), item.iconTint)
            }
            Column(Modifier.weight(1f, fill = false)) {
                AText(item.title, item.titleColor, 12f, weight = FontWeight.SemiBold)
                AText(item.subtitle, item.subtitleColor, 10f, mono = false)
            }
        }
        AText(
            item.shortcut, item.shortcutFg, 10f,
            Modifier
                .panel(RoundedCornerShape(4.dp), item.shortcutBg, item.shortcutBorder)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}