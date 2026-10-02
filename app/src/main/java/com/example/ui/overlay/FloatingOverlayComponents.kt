package com.example.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AiMode
import com.example.data.AppSettings
import com.example.prompt.PromptGenerator
import com.example.service.CapturePhase
import com.example.service.OverlayDisplayState
import com.example.service.OverlayUiState
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonCyan

@Composable
fun FloatingAssistantOverlayRoot(
    uiState: OverlayUiState,
    settings: AppSettings,
    onDragBy: (Float, Float) -> Unit,
    onTapBubble: () -> Unit,
    onMinimizePanel: () -> Unit,
    onClosePanelToBubble: () -> Unit,
    onSelectMode: (AiMode) -> Unit,
    onCustomQueryChange: (String) -> Unit,
    onSubmitCustomQuery: (String) -> Unit,
    onCopyResponse: () -> Unit,
    onRegenerate: (Boolean) -> Unit,
    onSaveScreenshotExplicitly: () -> Unit,
    onInlineApiKeyChange: (String) -> Unit = {},
    onSaveInlineApiKeyAndRetry: (String) -> Unit = {}
) {
    // When capturing the screen, hide the overlay completely so it never obscures underlying app content
    if (uiState.isOverlayHiddenForCapture) {
        Box(modifier = Modifier.size(1.dp).alpha(0f))
        return
    }

    when (uiState.displayState) {
        OverlayDisplayState.BUBBLE -> {
            GlowingFloatingAiBubble(
                sizeDp = settings.floatingButtonSizeDp,
                opacity = settings.floatingButtonOpacity,
                isProcessing = uiState.capturePhase == CapturePhase.CAPTURING_SCREEN ||
                    uiState.capturePhase == CapturePhase.ANALYZING_WITH_GEMINI,
                onDragBy = onDragBy,
                onClick = onTapBubble
            )
        }

        OverlayDisplayState.MINIMIZED_PILL -> {
            MinimizedFloatingPill(
                uiState = uiState,
                onDragBy = onDragBy,
                onExpand = onTapBubble,
                onClose = onClosePanelToBubble
            )
        }

        OverlayDisplayState.EXPANDED_PANEL -> {
            FloatingAiPanelCard(
                uiState = uiState,
                onDragBy = onDragBy,
                onMinimize = onMinimizePanel,
                onClose = onClosePanelToBubble,
                onSelectMode = onSelectMode,
                onCustomQueryChange = onCustomQueryChange,
                onSubmitCustomQuery = onSubmitCustomQuery,
                onCopyResponse = onCopyResponse,
                onRegenerate = onRegenerate,
                onSaveScreenshotExplicitly = onSaveScreenshotExplicitly,
                onInlineApiKeyChange = onInlineApiKeyChange,
                onSaveInlineApiKeyAndRetry = onSaveInlineApiKeyAndRetry
            )
        }
    }
}

@Composable
fun GlowingFloatingAiBubble(
    sizeDp: Int,
    opacity: Float,
    isProcessing: Boolean,
    onDragBy: (Float, Float) -> Unit,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bubble_glow")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val outerSize = (sizeDp + 14).dp
    val coreSize = sizeDp.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(outerSize)
            .alpha(opacity)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragBy(dragAmount.x, dragAmount.y)
                }
            }
            .testTag("floating_ai_bubble")
    ) {
        // Glowing outer aura
        Box(
            modifier = Modifier
                .size(coreSize)
                .scale(pulseScale)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeonCyan.copy(alpha = 0.55f),
                            ElectricViolet.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Core futuristic AI orb button
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color(0xFF0B0F19),
            shadowElevation = 12.dp,
            border = BorderStroke(
                width = 2.dp,
                brush = Brush.linearGradient(listOf(NeonCyan, ElectricViolet))
            ),
            modifier = Modifier.size(coreSize)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1A2942),
                            Color(0xFF0B0F19)
                        )
                    )
                )
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        color = NeonCyan,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size((sizeDp * 0.52f).dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Open ScreenAI Floating Panel",
                        tint = NeonCyan,
                        modifier = Modifier.size((sizeDp * 0.46f).dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MinimizedFloatingPill(
    uiState: OverlayUiState,
    onDragBy: (Float, Float) -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 14.dp,
        border = BorderStroke(
            width = 1.5.dp,
            brush = Brush.horizontalGradient(listOf(NeonCyan, ElectricViolet))
        ),
        modifier = Modifier
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragBy(dragAmount.x, dragAmount.y)
                }
            }
            .testTag("floating_minimized_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when (uiState.capturePhase) {
                    CapturePhase.ANALYZING_WITH_GEMINI -> "Analyzing..."
                    CapturePhase.RESULT_READY -> "${uiState.activeMode.emoji} Answer Ready"
                    else -> "ScreenAI"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable { onExpand() }
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onExpand,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("pill_expand_button")
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInFull,
                    contentDescription = "Expand Panel",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("pill_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close to Bubble",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FloatingAiPanelCard(
    uiState: OverlayUiState,
    onDragBy: (Float, Float) -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    onSelectMode: (AiMode) -> Unit,
    onCustomQueryChange: (String) -> Unit,
    onSubmitCustomQuery: (String) -> Unit,
    onCopyResponse: () -> Unit,
    onRegenerate: (Boolean) -> Unit,
    onSaveScreenshotExplicitly: () -> Unit,
    onInlineApiKeyChange: (String) -> Unit = {},
    onSaveInlineApiKeyAndRetry: (String) -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
        border = BorderStroke(
            width = 1.5.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    NeonCyan.copy(alpha = 0.75f),
                    ElectricViolet.copy(alpha = 0.65f)
                )
            )
        ),
        modifier = Modifier
            .widthIn(min = 290.dp, max = 344.dp)
            .heightIn(max = 490.dp)
            .shadow(18.dp, RoundedCornerShape(24.dp))
            .testTag("floating_ai_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // 1. Draggable Panel Header with Minimize & Close buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDragBy(dragAmount.x, dragAmount.y)
                        }
                    }
                    .padding(bottom = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(NeonCyan, ElectricViolet))
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF0B0F19),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ScreenAI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.DragIndicator,
                                contentDescription = "Drag Panel",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = EmeraldPulse,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (uiState.capturePhase) {
                                    CapturePhase.CAPTURING_SCREEN -> "Capturing screen now..."
                                    CapturePhase.ANALYZING_WITH_GEMINI -> "Gemini Vision active..."
                                    else -> "On-demand capture only"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = if (uiState.capturePhase == CapturePhase.CAPTURING_SCREEN) {
                                    NeonCyan
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("overlay_minimize_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Minimize Panel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("overlay_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Panel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2. Five AI Action Modes Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                AiMode.entries.forEach { mode ->
                    val isSelected = uiState.activeMode == mode &&
                        (uiState.capturePhase != CapturePhase.IDLE || uiState.isAskInputExpanded)
                    Surface(
                        onClick = { onSelectMode(mode) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            }
                        ),
                        modifier = Modifier.testTag("overlay_mode_${mode.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)
                        ) {
                            Text(text = mode.emoji, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = mode.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }

            // 3. Ask AI Custom Instruction Input & Quick Suggestion Chips
            AnimatedVisibility(
                visible = uiState.activeMode == AiMode.ASK_AI && uiState.isAskInputExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = "Quick prompts or type your own:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PromptGenerator.quickAskSuggestions.forEach { suggestion ->
                            Surface(
                                onClick = {
                                    onCustomQueryChange(suggestion)
                                    onSubmitCustomQuery(suggestion)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                                border = BorderStroke(
                                    0.8.dp,
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                                )
                            ) {
                                Text(
                                    text = suggestion,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = uiState.customAskText,
                            onValueChange = onCustomQueryChange,
                            placeholder = {
                                Text(
                                    "e.g., Find the error in this code...",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("overlay_ask_input")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FilledTonalIconButton(
                            onClick = { onSubmitCustomQuery(uiState.customAskText) },
                            enabled = uiState.customAskText.isNotBlank(),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("overlay_ask_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Custom Prompt"
                            )
                        }
                    }
                }
            }

            // 4. Feedback Banner (Copied / Saved / Ready)
            uiState.feedbackBanner?.let { banner ->
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = EmeraldPulse.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, EmeraldPulse.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = banner,
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldPulse,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            Spacer(modifier = Modifier.height(8.dp))

            // 5. Dynamic Content Body: Idle Prompt / Loading Animation / Error / Scrollable Answer
            when (uiState.capturePhase) {
                CapturePhase.IDLE -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "Tap any mode above to capture & analyze this screen",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "✨ Answer • 📖 Explain • 📝 Summarize • 🔍 Read • 💡 Ask AI",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                CapturePhase.CAPTURING_SCREEN,
                CapturePhase.ANALYZING_WITH_GEMINI -> {
                    OverlayLoadingIndicator(
                        phase = uiState.capturePhase,
                        mode = uiState.activeMode,
                        modelBadge = uiState.modelBadge
                    )
                }

                CapturePhase.ERROR -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = uiState.errorTitle ?: "Analysis Error",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = uiState.errorMessage ?: "Could not analyze screen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (uiState.needsApiKeyInput) {
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = uiState.inlineApiKeyDraft,
                                    onValueChange = onInlineApiKeyChange,
                                    placeholder = {
                                        Text(
                                            "Paste Gemini API Key (AIza...)",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("overlay_inline_api_key_input")
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            } else {
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (uiState.needsApiKeyInput) {
                                    Button(
                                        onClick = { onSaveInlineApiKeyAndRetry(uiState.inlineApiKeyDraft) },
                                        enabled = uiState.inlineApiKeyDraft.isNotBlank(),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier
                                            .height(38.dp)
                                            .testTag("overlay_save_api_key_button")
                                    ) {
                                        Text("Save Key & Answer", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { onRegenerate(true) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Retry Capture", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                CapturePhase.RESULT_READY -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Mini captured screen header bar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                uiState.lastCapturedPreview?.let { bmp ->
                                    if (!bmp.isRecycled) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Captured Screen Preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(width = 24.dp, height = 36.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                                    RoundedCornerShape(6.dp)
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                }
                                Column {
                                    Text(
                                        text = "${uiState.activeMode.emoji} ${uiState.activeMode.title} Result",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (uiState.modelBadge.isNotEmpty()) {
                                        Text(
                                            text = uiState.modelBadge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Explicit Save Screenshot Button (Privacy rule: only save if user explicitly taps)
                            if (uiState.lastCapturedPreview != null) {
                                IconButton(
                                    onClick = onSaveScreenshotExplicitly,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .testTag("overlay_save_capture_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Save Captured Screenshot",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }

                        // Scrollable AI Answer Box
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .heightIn(min = 90.dp, max = 210.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp)
                                    .testTag("overlay_response_scroll")
                            ) {
                                FormattedAiResponseText(text = uiState.aiResponse)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Action Buttons Row: Copy, Regenerate, Recapture
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = onCopyResponse,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("overlay_copy_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy AI Response",
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Copy", style = MaterialTheme.typography.labelMedium)
                            }

                            OutlinedButton(
                                onClick = { onRegenerate(false) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.15f)
                                    .height(38.dp)
                                    .testTag("overlay_regenerate_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Regenerate Response",
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Regenerate", style = MaterialTheme.typography.labelMedium)
                            }

                            FilledTonalIconButton(
                                onClick = { onRegenerate(true) },
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("overlay_recapture_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Capture New Screen Frame",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayLoadingIndicator(
    phase: CapturePhase,
    mode: AiMode,
    modelBadge: String
) {
    val transition = rememberInfiniteTransition(label = "loading_pulse")
    val alphaAnim by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = alphaAnim * 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("overlay_loading_indicator")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            CircularProgressIndicator(
                color = NeonCyan,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (phase == CapturePhase.CAPTURING_SCREEN) {
                        "Capturing current screen..."
                    } else {
                        "${mode.emoji} Generating ${mode.title}..."
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (phase == CapturePhase.CAPTURING_SCREEN) {
                        "Reading visible content on demand"
                    } else {
                        "Vision model (${modelBadge.ifEmpty { "Gemini" }}) is analyzing text, code & visuals"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FormattedAiResponseText(text: String) {
    val lines = text.lines()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.isBlank() -> {
                    Spacer(modifier = Modifier.height(2.dp))
                }
                line.startsWith("Output:", ignoreCase = true) ||
                    line.startsWith("Answer:", ignoreCase = true) ||
                    line.startsWith("TL;DR:", ignoreCase = true) -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = line.replace("**", ""),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
                line.startsWith("#") -> {
                    val cleaned = line.trimStart('#', ' ').replace("**", "")
                    Text(
                        text = cleaned,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                line.startsWith("```") -> {
                    // Skip raw markdown fence delimiter line
                }
                else -> {
                    Text(
                        text = line.replace("**", ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
