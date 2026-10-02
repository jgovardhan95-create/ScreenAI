package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.drawToBitmap
import com.example.capture.ScreenCaptureManager
import com.example.data.AiMode
import com.example.data.AppSettings
import com.example.service.OverlayStateController
import com.example.service.OverlayUiState
import com.example.ui.overlay.FloatingAssistantOverlayRoot
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonCyan
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class TestScenario(
    val title: String,
    val icon: ImageVector,
    val badge: String
) {
    JAVA_CODE("Java Output", Icons.Default.Code, "Example from Prompt • Output: 25"),
    ALGORITHM_MCQ("CS MCQ", Icons.Default.Quiz, "Multiple Choice Question"),
    MATH_PROBLEM("Math Problem", Icons.Default.Functions, "Step-by-Step Calculus"),
    SYSTEM_DIAGRAM("Diagram", Icons.Default.Hub, "Visual Architecture Flow"),
    TECH_ARTICLE("Article", Icons.AutoMirrored.Filled.MenuBook, "Paragraph Summarization")
}

@Composable
fun TestScreenAiScreen(
    settings: AppSettings,
    overlayState: OverlayUiState,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onNavigateBack()
    }

    val context = LocalContext.current
    val hostView = LocalView.current
    var selectedScenarioIndex by remember { mutableIntStateOf(0) }
    val selectedScenario = TestScenario.entries[selectedScenarioIndex]

    // Keep an up-to-date snapshot of the Test Screen AI window in ScreenCaptureManager
    // so both the system-wide floating overlay AND the in-app floating assistant can analyze it immediately!
    LaunchedEffect(selectedScenarioIndex) {
        delay(220)
        runCatching {
            if (hostView.width > 0 && hostView.height > 0) {
                val bmp = hostView.drawToBitmap(Bitmap.Config.ARGB_8888)
                ScreenCaptureManager.setInAppFallbackBitmap(bmp)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Keep memory clean when leaving Test Screen AI
        }
    }

    fun captureCurrentTestViewAndRun(mode: AiMode, customQuery: String? = null) {
        val capturedBitmap: Bitmap? = runCatching {
            if (hostView.width > 0 && hostView.height > 0) {
                hostView.drawToBitmap(Bitmap.Config.ARGB_8888).also {
                    ScreenCaptureManager.setInAppFallbackBitmap(it)
                }
            } else {
                null
            }
        }.getOrNull()

        OverlayStateController.executeAiAction(
            context = context,
            mode = mode,
            customQuery = customQuery,
            explicitBitmap = capturedBitmap,
            reuseLastImage = false
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .testTag("test_screen_ai_root")
        ) {
            // Top Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("test_screen_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home"
                        )
                    }
                    Column {
                        Text(
                            text = "Test Screen AI Sandbox",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Tap the floating AI button or any mode below to analyze this screen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Scenario Selector Tabs
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                TestScenario.entries.forEachIndexed { index, scenario ->
                    val selected = index == selectedScenarioIndex
                    FilterChip(
                        selected = selected,
                        onClick = { selectedScenarioIndex = index },
                        label = { Text(scenario.title) },
                        leadingIcon = {
                            Icon(
                                imageVector = scenario.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("scenario_chip_${scenario.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Mode Trigger Bar
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "One-Tap Screen Capture & Gemini Vision Test:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        AiMode.entries.forEach { mode ->
                            Surface(
                                onClick = {
                                    if (mode == AiMode.ASK_AI) {
                                        OverlayStateController.openPanel(context)
                                        OverlayStateController.toggleAskAiMode(context)
                                    } else {
                                        captureCurrentTestViewAndRun(mode)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                                modifier = Modifier.testTag("sandbox_trigger_${mode.id}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(text = mode.emoji, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = mode.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Simulated App Screen Content Card to be Read/Captured by ScreenAI
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                when (selectedScenario) {
                    TestScenario.JAVA_CODE -> JavaCodeChallengeCard()
                    TestScenario.ALGORITHM_MCQ -> AlgorithmMcqCard()
                    TestScenario.MATH_PROBLEM -> MathProblemCard()
                    TestScenario.SYSTEM_DIAGRAM -> SystemDiagramCard()
                    TestScenario.TECH_ARTICLE -> TechArticleCard()
                }
                Spacer(modifier = Modifier.height(120.dp))
            }
        }

        // If the system-wide overlay service isn't currently running over all apps,
        // render the draggable Floating AI Button & Panel right inside the Sandbox so the user
        // can experience the exact floating overlay UX immediately!
        if (!overlayState.isServiceRunning) {
            var offsetX by remember { mutableFloatStateOf(24f) }
            var offsetY by remember { mutableFloatStateOf(420f) }

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            ) {
                FloatingAssistantOverlayRoot(
                    uiState = overlayState,
                    settings = settings,
                    onDragBy = { dx, dy ->
                        offsetX = (offsetX + dx).coerceIn(0f, 680f)
                        offsetY = (offsetY + dy).coerceIn(80f, 1300f)
                    },
                    onTapBubble = { OverlayStateController.openPanel(context) },
                    onMinimizePanel = { OverlayStateController.minimizePanel(context) },
                    onClosePanelToBubble = { OverlayStateController.collapseToBubble(context) },
                    onSelectMode = { mode ->
                        if (mode == AiMode.ASK_AI) {
                            OverlayStateController.toggleAskAiMode(context)
                        } else {
                            captureCurrentTestViewAndRun(mode)
                        }
                    },
                    onCustomQueryChange = { OverlayStateController.updateCustomAskText(it) },
                    onSubmitCustomQuery = { query ->
                        captureCurrentTestViewAndRun(AiMode.ASK_AI, query)
                    },
                    onCopyResponse = { OverlayStateController.copyResponseToClipboard(context) },
                    onRegenerate = { recapture ->
                        if (recapture) {
                            captureCurrentTestViewAndRun(overlayState.activeMode, overlayState.customAskText)
                        } else {
                            OverlayStateController.regenerateLastAction(context, recaptureScreen = false)
                        }
                    },
                    onSaveScreenshotExplicitly = {
                        OverlayStateController.saveCapturedFrameExplicitly(context)
                    },
                    onInlineApiKeyChange = {
                        OverlayStateController.updateInlineApiKeyDraft(it)
                    },
                    onSaveInlineApiKeyAndRetry = { key ->
                        OverlayStateController.saveInlineApiKeyAndRetry(context, key)
                    }
                )
            }
        }
    }
}

@Composable
private fun JavaCodeChallengeCard() {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = NeonCyan.copy(alpha = 0.16f)
            ) {
                Text(
                    text = "CODING ASSESSMENT • QUESTION #1",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "What is the output of this Java code?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF090D16),
                border = BorderStroke(1.dp, Color(0xFF26354D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = """
                        public class ScreenAiChallenge {
                            public static void main(String[] args) {
                                int sum = 0;
                                for (int i = 1; i <= 9; i += 2) {
                                    sum += i;
                                }
                                System.out.println(sum);
                            }
                        }
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        lineHeight = 22.sp
                    ),
                    color = Color(0xFFE2E8F0),
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Hint: Tap the floating AI button → tap ✨ Answer to see ScreenAI detect this Java snippet and return \"Output: 25\" with a short explanation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AlgorithmMcqCard() {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ElectricViolet.copy(alpha = 0.18f)
            ) {
                Text(
                    text = "DATA STRUCTURES & ALGORITHMS • MCQ",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricViolet,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Which data structure guarantees O(1) average-case time complexity for both insertion and key lookup, while maintaining insertion order in Java?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(14.dp))

            val options = listOf(
                "A) TreeMap<K, V>",
                "B) LinkedHashMap<K, V>",
                "C) PriorityQueue<E>",
                "D) CopyOnWriteArrayList<E>"
            )
            options.forEach { opt ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                ) {
                    Text(
                        text = opt,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MathProblemCard() {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = EmeraldPulse.copy(alpha = 0.18f)
            ) {
                Text(
                    text = "MATHEMATICS • CALCULUS & ALGEBRA",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldPulse,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Solve for the positive root x and evaluate the definite integral:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF090D16),
                border = BorderStroke(1.dp, Color(0xFF26354D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Problem 1:  2x² - 7x - 15 = 0   (Find x > 0)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Problem 2:  ∫ from 0 to 3 of (3t² + 2t) dt",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = Color(0xFFE2E8F0)
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemDiagramCard() {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Distributed AI Microservice Architecture Diagram",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Use 📖 Explain or ✨ Answer to have Gemini Vision interpret this architecture flow diagram:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Visual Architecture Diagram rendered on screen
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF090D16),
                border = BorderStroke(1.dp, Color(0xFF26354D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    DiagramNodeBox("1. Android Client (MediaProjection Frame)", NeonCyan)
                    DiagramConnectorArrow("TLS 1.3 • Base64 JPEG Payload")
                    DiagramNodeBox("2. API Gateway & Rate Limiter", ElectricViolet)
                    DiagramConnectorArrow("Multimodal Prompt Routing")
                    DiagramNodeBox("3. Gemini Vision Model (Cross-Attention)", EmeraldPulse)
                    DiagramConnectorArrow("Structured Markdown Stream")
                    DiagramNodeBox("4. Floating Overlay UI Renderer", NeonCyan)
                }
            }
        }
    }
}

@Composable
private fun DiagramNodeBox(label: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.5.dp, accent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun DiagramConnectorArrow(caption: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Canvas(modifier = Modifier.size(width = 16.dp, height = 26.dp)) {
            drawLine(
                color = NeonCyan,
                start = Offset(size.width / 2, 0f),
                end = Offset(size.width / 2, size.height),
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
            )
            drawRoundRect(
                color = NeonCyan,
                topLeft = Offset(size.width / 2 - 4f, size.height - 6f),
                size = Size(8f, 6f),
                cornerRadius = CornerRadius(2f, 2f),
                style = Stroke(width = 2f)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF94A3B8)
        )
    }
}

@Composable
private fun TechArticleCard() {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Sparse Mixture-of-Experts (MoE) in Vision-Language Models",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = """
                    Modern multimodal transformers process high-resolution screen captures by dividing images into visual patches and projecting them into the same embedding space as text tokens. However, dense self-attention scales quadratically with sequence length.
                    
                    By employing a Sparse Mixture-of-Experts (MoE) architecture, a learned gating network dynamically routes each visual token to only the top-k specialized feed-forward expert sub-networks (such as OCR specialists, code syntax analyzers, or chart geometry decoders). This decouples total parameter capacity from per-token FLOPs, achieving up to 3.4x faster time-to-first-token latency on mobile edge queries while improving accuracy on dense UI layouts and source code snippets.
                """.trimIndent(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
