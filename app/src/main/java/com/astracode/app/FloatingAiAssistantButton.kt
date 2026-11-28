package com.astracode.app

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
internal fun FloatingAiAssistantButton(
    preferences: SharedPreferences,
    modifier: Modifier = Modifier,
    onOpenChat: (TransformOrigin) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val buttonSize = 60.dp
        val maxX = (maxWidth - buttonSize).coerceAtLeast(0.dp).value
        val maxY = (maxHeight - buttonSize).coerceAtLeast(0.dp).value
        val defaultX = (maxX - 14.dp.value).coerceAtLeast(8.dp.value)
        // Keep the default position above the app's bottom navigation and Android gesture area.
        val defaultY = (maxY - 116.dp.value).coerceAtLeast(8.dp.value)

        var savedX by rememberSaveable {
            mutableStateOf(
                if (preferences.contains(KEY_X)) preferences.getFloat(KEY_X, defaultX) else defaultX,
            )
        }
        var savedY by rememberSaveable {
            mutableStateOf(
                if (preferences.contains(KEY_Y)) preferences.getFloat(KEY_Y, defaultY) else defaultY,
            )
        }
        val x = savedX.coerceIn(0f, maxX)
        val y = savedY.coerceIn(0f, maxY)

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        with(density) { x.dp.roundToPx() },
                        with(density) { y.dp.roundToPx() },
                    )
                }
                .size(buttonSize)
                .pointerInput(maxWidth, maxHeight) {
                    detectDragGesturesAfterLongPress { change, dragAmount ->
                        change.consume()
                        val dx = with(density) { dragAmount.x.toDp().value }
                        val dy = with(density) { dragAmount.y.toDp().value }
                        savedX = (savedX + dx).coerceIn(0f, maxX)
                        savedY = (savedY + dy).coerceIn(0f, maxY)
                        preferences.edit()
                            .putFloat(KEY_X, savedX)
                            .putFloat(KEY_Y, savedY)
                            .apply()
                    }
                }
                .clickable {
                    val width = maxWidth.value.coerceAtLeast(1f)
                    val height = maxHeight.value.coerceAtLeast(1f)
                    onOpenChat(
                        TransformOrigin(
                            pivotFractionX = ((x + buttonSize.value / 2f) / width).coerceIn(0f, 1f),
                            pivotFractionY = ((y + buttonSize.value / 2f) / height).coerceIn(0f, 1f),
                        ),
                    )
                }
                .semantics { contentDescription = "Open AstraCode AI assistant" }
                .testTag("ai-floating-button"),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 12.dp,
                tonalElevation = 5.dp,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(27.dp),
                    )
                }
            }
        }
    }
}

private const val KEY_X = "ai_floating_button_x_dp"
private const val KEY_Y = "ai_floating_button_y_dp"
