package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AiMode
import com.example.data.AnalysisHistoryItem
import com.example.data.AppSettings
import com.example.service.OverlayUiState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.NeonCyan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    settings: AppSettings,
    overlayState: OverlayUiState,
    hasOverlayPermission: Boolean,
    hasScreenCapturePermission: Boolean,
    hasNotificationPermission: Boolean,
    recentHistory: List<AnalysisHistoryItem>,
    statusBanner: String?,
    onStartFloatingAssistant: () -> Unit,
    onStopFloatingAssistant: () -> Unit,
    onOpenFloatingPanelNow: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestScreenCapturePermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenOnboardingGuide: () -> Unit,
    onOpenTestScreenAi: () -> Unit,
    onOpenWorkInOneTime: () -> Unit,
    onOpenAiBrowser: (String) -> Unit = {},
    onOpenSettings: () -> Unit,
    onDeleteHistoryItem: (Int) -> Unit,
    onShowBanner: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .testTag("home_screen_list")
        ) {
            // 1. Futuristic Hero Card with Title & Tagline
            item {
                HeroHeaderCard(
                    isAssistantActive = overlayState.isServiceRunning,
                    activeModelName = settings.modelOption.displayName,
                    onOpenGuide = onOpenOnboardingGuide
                )
            }

            // Optional Status Banner
            if (!statusBanner.isNullOrBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = EmeraldPulse.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, EmeraldPulse.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusBanner,
                            style = MaterialTheme.typography.bodyMedium,
                            color = EmeraldPulse,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // 2. Primary Action Buttons Card (Start Floating Assistant, Test Screen AI, Settings)
            item {
                PrimaryActionsSection(
                    isServiceRunning = overlayState.isServiceRunning,
                    onStartFloatingAssistant = onStartFloatingAssistant,
                    onStopFloatingAssistant = onStopFloatingAssistant,
                    onOpenFloatingPanelNow = onOpenFloatingPanelNow,
                    onOpenTestScreenAi = onOpenTestScreenAi,
                    onOpenSettings = onOpenSettings
                )
            }

            // 3. Work in One Time Feature Highlight Card
            item {
                WorkInOneTimePromoCard(onOpen = onOpenWorkInOneTime)
            }

            // 4. In-App AI Automation Browser Promo Card
            item {
                AiAutomationBrowserPromoCard(
                    onOpenInfosys = { onOpenAiBrowser("https://infy.onwingspan.com") },
                    onOpenGeneric = { onOpenAiBrowser("https://www.google.com") }
                )
            }

            // 5. Required Android Permissions Readiness Card
            item {
                PermissionsChecklistCard(
                    hasOverlayPermission = hasOverlayPermission,
                    hasScreenCapturePermission = hasScreenCapturePermission,
                    hasNotificationPermission = hasNotificationPermission,
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onRequestScreenCapturePermission = onRequestScreenCapturePermission,
                    onRequestNotificationPermission = onRequestNotificationPermission
                )
            }

            // 4. Five AI Modes Overview Strip
            item {
                AiModesShowcaseCard(onTryInPlayground = onOpenTestScreenAi)
            }

            // 5. Privacy Commitment Banner
            item {
                PrivacyPledgeBanner()
            }

            // 6. Recent Temporary Screen Analyses
            if (recentHistory.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Screen Analyses (Temporary)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(recentHistory, key = { it.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        onCopy = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cb?.setPrimaryClip(ClipData.newPlainText("ScreenAI History", item.aiResponse))
                            onShowBanner("Copied analysis to clipboard")
                        },
                        onDelete = { onDeleteHistoryItem(item.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun HeroHeaderCard(
    isAssistantActive: Boolean,
    activeModelName: String,
    onOpenGuide: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.5.dp,
            brush = Brush.linearGradient(
                listOf(NeonCyan.copy(alpha = 0.7f), ElectricViolet.copy(alpha = 0.6f))
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_banner),
                contentDescription = "ScreenAI Holographic Illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0B0F19).copy(alpha = 0.45f),
                                Color(0xFF0B0F19).copy(alpha = 0.85f),
                                Color(0xFF0B0F19).copy(alpha = 0.96f)
                            )
                        )
                    )
            )

            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .padding(20.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isAssistantActive) {
                            EmeraldPulse.copy(alpha = 0.2f)
                        } else {
                            NeonCyan.copy(alpha = 0.18f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isAssistantActive) EmeraldPulse else NeonCyan
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isAssistantActive) EmeraldPulse else NeonCyan)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isAssistantActive) "Floating Overlay Active" else activeModelName,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }

                    TextButton(
                        onClick = onOpenGuide,
                        modifier = Modifier.testTag("how_it_works_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "How it works",
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "How It Works",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonCyan
                        )
                    }
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.app_title_short),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFE2E8F0)
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryActionsSection(
    isServiceRunning: Boolean,
    onStartFloatingAssistant: () -> Unit,
    onStopFloatingAssistant: () -> Unit,
    onOpenFloatingPanelNow: () -> Unit,
    onOpenTestScreenAi: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Primary Button 1: Start Floating Assistant
        Button(
            onClick = onStartFloatingAssistant,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(vertical = 16.dp, horizontal = 20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("start_floating_assistant_button")
        ) {
            Icon(
                imageVector = if (isServiceRunning) Icons.Default.AutoAwesome else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (isServiceRunning) {
                    "Start Floating Assistant (Running)"
                } else {
                    stringResource(R.string.btn_start_assistant)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Extra quick controls when the Floating Assistant service is already running
        AnimatedVisibility(visible = isServiceRunning) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(
                    onClick = onOpenFloatingPanelNow,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("expand_floating_panel_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open AI Panel")
                }

                OutlinedButton(
                    onClick = onStopFloatingAssistant,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("stop_floating_assistant_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.StopCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop Overlay")
                }
            }
        }

        // Primary Buttons 2 & 3: Test Screen AI & Settings
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilledTonalButton(
                onClick = onOpenTestScreenAi,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                contentPadding = PaddingValues(vertical = 15.dp, horizontal = 16.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("test_screen_ai_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Science,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_test_screen_ai),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                contentPadding = PaddingValues(vertical = 15.dp, horizontal = 16.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_settings),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PermissionsChecklistCard(
    hasOverlayPermission: Boolean,
    hasScreenCapturePermission: Boolean,
    hasNotificationPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestScreenCapturePermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Required Android Permissions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val readyCount = listOf(
                    hasOverlayPermission,
                    hasScreenCapturePermission,
                    hasNotificationPermission
                ).count { it }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (readyCount == 3) {
                        EmeraldPulse.copy(alpha = 0.16f)
                    } else {
                        AmberWarning.copy(alpha = 0.16f)
                    }
                ) {
                    Text(
                        text = "$readyCount / 3 Granted",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (readyCount == 3) EmeraldPulse else AmberWarning,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            PermissionRowItem(
                icon = Icons.Default.Layers,
                title = "Display Over Other Apps",
                subtitle = "Allows the draggable AI button and panel to float above apps.",
                isGranted = hasOverlayPermission,
                actionLabel = "Grant Overlay",
                testTag = "perm_overlay_button",
                onAction = onRequestOverlayPermission
            )

            Spacer(modifier = Modifier.height(10.dp))

            PermissionRowItem(
                icon = Icons.Default.ScreenshotMonitor,
                title = "Screen Capture (MediaProjection)",
                subtitle = "Captures a single frame only when you tap an AI mode.",
                isGranted = hasScreenCapturePermission,
                actionLabel = "Authorize",
                testTag = "perm_capture_button",
                onAction = onRequestScreenCapturePermission
            )

            Spacer(modifier = Modifier.height(10.dp))

            PermissionRowItem(
                icon = Icons.Default.NotificationsActive,
                title = "Foreground Service Notification",
                subtitle = "Keeps the floating assistant alive and provides a 1-tap Stop action.",
                isGranted = hasNotificationPermission,
                actionLabel = "Allow",
                testTag = "perm_notification_button",
                onAction = onRequestNotificationPermission
            )
        }
    }
}

@Composable
private fun PermissionRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    actionLabel: String,
    testTag: String,
    onAction: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isGranted) {
                            EmeraldPulse.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    )
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) EmeraldPulse else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isGranted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Granted",
                        tint = EmeraldPulse,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Ready",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldPulse
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag(testTag)
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun AiModesShowcaseCard(onTryInPlayground: () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "5 Intelligent Screen AI Modes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onTryInPlayground) {
                    Text("Try Sandbox →", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            AiMode.entries.forEach { mode ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = mode.emoji)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = mode.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = mode.shortDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyPledgeBanner() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PrivacyTip,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Privacy-First Screen Capture",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ScreenAI never records or uploads your screen in the background. A single frame is read in memory only when you explicitly tap an AI action, and is discarded unless you choose to save it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HistoryItemCard(
    item: AnalysisHistoryItem,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = item.modeEmoji)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.modeTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatter.format(Date(item.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy response",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete history item",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.aiResponse,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WorkInOneTimePromoCard(onOpen: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F172A)
        ),
        border = BorderStroke(
            width = 1.5.dp,
            brush = Brush.horizontalGradient(
                listOf(NeonCyan.copy(alpha = 0.85f), ElectricViolet.copy(alpha = 0.85f))
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("work_in_one_time_promo_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NeonCyan.copy(alpha = 0.2f))
                    ) {
                        Text(text = "⚡", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Work in One Time",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                        Text(
                            text = "Autonomous Background Action Flow",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EmeraldPulse.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, EmeraldPulse.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "NEW",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldPulse,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Enter any task in plain text (e.g. 'open browser and download movie', 'find coffee shops', 'open wifi settings'). ScreenAI plans the steps, executes them in the background, and displays the full live execution flow.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
            )

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onOpen,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_work_in_one_time_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Task Runner Flow ➔", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AiAutomationBrowserPromoCard(
    onOpenInfosys: () -> Unit,
    onOpenGeneric: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0A192F)
        ),
        border = BorderStroke(
            width = 1.5.dp,
            brush = Brush.horizontalGradient(
                listOf(NeonCyan.copy(alpha = 0.9f), EmeraldPulse.copy(alpha = 0.7f))
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_automation_browser_promo_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(EmeraldPulse.copy(alpha = 0.2f))
                    ) {
                        Text(text = "🌐", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "AI Automation Browser",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPulse
                        )
                        Text(
                            text = "In-App Web Runner • DOM Automation",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NeonCyan.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "NEW",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Runs directly inside the app with persistent logins! Automate course searches on Infosys Wingspan, inject scripts to filter topics, and extract exact course titles.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
            )

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onOpenInfosys,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_infosys_browser_button")
                ) {
                    Text("🏢 Open Infosys Wingspan", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onOpenGeneric,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Open Browser")
                }
            }
        }
    }
}


