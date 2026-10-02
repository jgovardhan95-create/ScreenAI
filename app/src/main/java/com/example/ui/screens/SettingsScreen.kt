package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AppSettings
import com.example.data.GeminiModelOption
import com.example.data.ResponseLength
import com.example.data.ThemePreference
import com.example.network.GeminiApiClient
import com.example.ui.ApiVerificationState
import com.example.ui.overlay.GlowingFloatingAiBubble
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldPulse
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    apiVerificationState: ApiVerificationState,
    statusBanner: String?,
    onSelectModel: (GeminiModelOption) -> Unit,
    onSelectResponseLength: (ResponseLength) -> Unit,
    onSelectTheme: (ThemePreference) -> Unit,
    onUpdateButtonSize: (Int) -> Unit,
    onUpdateButtonOpacity: (Float) -> Unit,
    onUpdateVibration: (Boolean) -> Unit,
    onVerifyApiConnection: () -> Unit,
    onClearTemporaryData: () -> Unit,
    onNavigateBack: () -> Unit,
    onSaveCustomApiKey: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler {
        onNavigateBack()
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .testTag("settings_screen_list")
        ) {
            // Top Bar
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home"
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Configure Gemini Vision, floating overlay, and privacy controls",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

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
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            // 1. Gemini API Configuration
            item {
                var apiKeyInput by remember(settings.customApiKey) {
                    mutableStateOf(settings.customApiKey)
                }
                SettingsSectionCard(
                    icon = Icons.Default.Key,
                    title = "Gemini API Configuration"
                ) {
                    val isConfigured = GeminiApiClient.isApiKeyConfigured(settings.customApiKey)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Gemini API Status",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = GeminiApiClient.getMaskedApiKeyStatus(settings.customApiKey),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isConfigured) EmeraldPulse else AmberWarning
                            )
                        }
                        Icon(
                            imageVector = if (isConfigured) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = if (isConfigured) EmeraldPulse else AmberWarning
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Gemini API Key (AIza...)") },
                        placeholder = { Text("Paste key from aistudio.google.com/app/apikey") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_api_key_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { onSaveCustomApiKey(apiKeyInput) },
                            enabled = apiKeyInput.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_api_key_button")
                        ) {
                            Text("Save & Verify Key")
                        }
                        if (settings.customApiKey.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    apiKeyInput = ""
                                    onSaveCustomApiKey("")
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Clear")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onVerifyApiConnection,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("verify_gemini_api_button")
                    ) {
                        if (apiVerificationState is ApiVerificationState.Verifying) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing Gemini Vision Endpoint...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Gemini API Connection")
                        }
                    }

                    when (apiVerificationState) {
                        is ApiVerificationState.Success -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = apiVerificationState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldPulse
                            )
                        }
                        is ApiVerificationState.Error -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${apiVerificationState.title}: ${apiVerificationState.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> Unit
                    }
                }
            }

            // 2. AI Model Selection
            item {
                SettingsSectionCard(
                    icon = Icons.Default.Memory,
                    title = "AI Model Selection"
                ) {
                    GeminiModelOption.entries.forEach { option ->
                        val selected = settings.modelOption == option
                        Surface(
                            onClick = { onSelectModel(option) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("model_option_${option.modelId}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { onSelectModel(option) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = option.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = option.badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = option.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Response Length (Short / Medium / Detailed)
            item {
                SettingsSectionCard(
                    icon = Icons.AutoMirrored.Filled.ShortText,
                    title = "Response Length"
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ResponseLength.entries.forEach { length ->
                            FilterChip(
                                selected = settings.responseLength == length,
                                onClick = { onSelectResponseLength(length) },
                                label = { Text(length.label) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("response_length_${length.name.lowercase()}")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = settings.responseLength.promptInstruction,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 4. Appearance Theme (Dark / Light / System)
            item {
                SettingsSectionCard(
                    icon = Icons.Default.DarkMode,
                    title = "Theme Appearance"
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ThemePreference.entries.forEach { theme ->
                            FilterChip(
                                selected = settings.themePreference == theme,
                                onClick = { onSelectTheme(theme) },
                                label = { Text(theme.label) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("theme_pref_${theme.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // 5. Floating Button Size, Opacity & Vibration
            item {
                SettingsSectionCard(
                    icon = Icons.Default.Tune,
                    title = "Floating AI Button Customization"
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Button Size: ${settings.floatingButtonSizeDp} dp",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Slider(
                                value = settings.floatingButtonSizeDp.toFloat(),
                                onValueChange = { onUpdateButtonSize(it.roundToInt()) },
                                valueRange = 44f..78f,
                                modifier = Modifier.testTag("slider_button_size")
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Button Opacity: ${(settings.floatingButtonOpacity * 100).roundToInt()}%",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Slider(
                                value = settings.floatingButtonOpacity,
                                onValueChange = { onUpdateButtonOpacity(it) },
                                valueRange = 0.35f..1.0f,
                                modifier = Modifier.testTag("slider_button_opacity")
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Live interactive preview of the glowing floating AI button
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Live Preview",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            GlowingFloatingAiBubble(
                                sizeDp = settings.floatingButtonSizeDp,
                                opacity = settings.floatingButtonOpacity,
                                isProcessing = false,
                                onDragBy = { _, _ -> },
                                onClick = {}
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Vibration,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Haptic Vibration Feedback",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Subtle vibration when opening the AI panel or receiving answers",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = settings.vibrationEnabled,
                            onCheckedChange = onUpdateVibration,
                            modifier = Modifier.testTag("switch_vibration")
                        )
                    }
                }
            }

            // 6. Privacy Information & Clear Temporary Data
            item {
                SettingsSectionCard(
                    icon = Icons.Default.PrivacyTip,
                    title = "Privacy & Temporary Data"
                ) {
                    Text(
                        text = "• Strictly On-Demand Capture: ScreenAI never continuously records or uploads screenshots in the background.\n" +
                            "• Explicit User Action: The screen is read only after you tap an AI mode button.\n" +
                            "• No Permanent Storage: Captured frames are processed in memory and discarded unless you explicitly tap the Save button.\n" +
                            "• Official Android Security: Uses Android's standard MediaProjection permission and respects FLAG_SECURE private windows.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onClearTemporaryData,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clear_temp_data_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Clear Temporary Data",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 7. About Section
            item {
                SettingsSectionCard(
                    icon = Icons.Default.Info,
                    title = "About ScreenAI Assistant"
                ) {
                    Text(
                        text = "ScreenAI Assistant v1.0",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Built with Kotlin, Jetpack Compose, Android Foreground Overlay Service, MediaProjection API, Room, DataStore, and Google Gemini Vision.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
