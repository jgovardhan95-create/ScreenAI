package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.automation.AiWebAutomationController
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.NeonCyan

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiAutomationBrowserScreen(
    initialUrl: String = "https://infy.onwingspan.com",
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var urlBarText by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf("Loading...") }
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(false) }

    // Automation UI States
    var automationQuery by remember { mutableStateOf("Spring 5 Basics") }
    var automationStatus by remember { mutableStateOf<String?>(null) }
    var detectedCourseName by remember { mutableStateOf<String?>(null) }
    var isExecutingAction by remember { mutableStateOf(false) }
    var isPanelExpanded by remember { mutableStateOf(true) }

    BackHandler {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            onNavigateBack()
        }
    }

    val portalShortcuts = listOf(
        Pair("🏢 Infosys Wingspan", "https://infy.onwingspan.com"),
        Pair("🔍 Google", "https://www.google.com"),
        Pair("🍃 Spring Docs", "https://spring.io/guides"),
        Pair("📚 Baeldung Java", "https://www.baeldung.com")
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Top Bar with Title and Back
        TopAppBar(
            title = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "AI Automation Browser",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NeonCyan.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Web Runner",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = pageTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("browser_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // 2. Navigation & URL Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { webViewInstance?.goBack() },
                        enabled = webViewInstance?.canGoBack() == true,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Web Back",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { webViewInstance?.goForward() },
                        enabled = webViewInstance?.canGoForward() == true,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Web Forward",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { webViewInstance?.reload() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    OutlinedTextField(
                        value = urlBarText,
                        onValueChange = { urlBarText = it },
                        leadingIcon = {
                            Icon(
                                imageVector = if (currentUrl.startsWith("https://")) Icons.Default.Lock else Icons.Default.Language,
                                contentDescription = null,
                                tint = if (currentUrl.startsWith("https://")) EmeraldPulse else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = {
                                val destination = if (urlBarText.startsWith("http://") || urlBarText.startsWith("https://")) {
                                    urlBarText
                                } else {
                                    "https://www.google.com/search?q=" + urlBarText.trim()
                                }
                                webViewInstance?.loadUrl(destination)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Go",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("browser_url_input")
                    )
                }

                // Quick Portal Chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    portalShortcuts.forEach { (label, url) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.clickable {
                                urlBarText = url
                                currentUrl = url
                                webViewInstance?.loadUrl(url)
                            }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Loading Progress Bar
        if (isLoading) {
            LinearProgressIndicator(
                progress = { loadingProgress },
                color = NeonCyan,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 3. Android WebView Container
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewInstance = this

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            userAgentString = userAgentString.replace("; wv", "") // modern browser UA
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                url?.let {
                                    currentUrl = it
                                    urlBarText = it
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                url?.let {
                                    currentUrl = it
                                    urlBarText = it
                                }
                                view?.title?.let { pageTitle = it }

                                // Auto extract course title if on a course/assessment page
                                view?.let { wv ->
                                    AiWebAutomationController.extractCourseInfo(wv) { success, title ->
                                        if (success && title.isNotBlank() && title != "Google" && title != "Infosys Springboard") {
                                            detectedCourseName = title
                                        }
                                    }
                                }
                            }

                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                handler?.proceed() // Proceed safely for internal portals
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadingProgress = newProgress / 100f
                                if (newProgress == 100) isLoading = false
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                title?.let { pageTitle = it }
                            }
                        }

                        loadUrl(initialUrl)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("in_app_webview")
            )

            // 4. Floating AI Automation Panel (Overlay inside browser)
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                    ),
                    border = BorderStroke(
                        width = 1.2.dp,
                        brush = Brush.horizontalGradient(
                            listOf(NeonCyan.copy(alpha = 0.7f), ElectricViolet.copy(alpha = 0.7f))
                        )
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("browser_ai_automation_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Header with Toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(NeonCyan.copy(alpha = 0.2f))
                                ) {
                                    Text(text = "⚡", fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "AI DOM Automation Runner",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonCyan
                                    )
                                    Text(
                                        text = "Direct webpage script injection & data extraction",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = { isPanelExpanded = !isPanelExpanded },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPanelExpanded) Icons.Default.Close else Icons.Default.AutoAwesome,
                                    contentDescription = "Toggle Panel",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        AnimatedVisibility(visible = isPanelExpanded) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                // Detected Course Badge
                                if (detectedCourseName != null) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = EmeraldPulse.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, EmeraldPulse.copy(alpha = 0.45f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.School,
                                                contentDescription = null,
                                                tint = EmeraldPulse,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Detected Course: $detectedCourseName",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EmeraldPulse,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                // Input box for search / topic
                                OutlinedTextField(
                                    value = automationQuery,
                                    onValueChange = { automationQuery = it },
                                    placeholder = {
                                        Text(
                                            "e.g., Spring 5 Basics, Java, Microservices...",
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
                                        .height(48.dp)
                                        .testTag("browser_automation_query_input")
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Automation Action Buttons
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Button 1: Auto Search & Fill
                                    Button(
                                        onClick = {
                                            val wv = webViewInstance ?: return@Button
                                            isExecutingAction = true
                                            automationStatus = "Searching topic: $automationQuery..."
                                            AiWebAutomationController.performAutoSearch(wv, automationQuery) { success, msg ->
                                                isExecutingAction = false
                                                automationStatus = msg
                                            }
                                        },
                                        enabled = !isExecutingAction && automationQuery.isNotBlank(),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Auto Search Topic", style = MaterialTheme.typography.labelSmall)
                                    }

                                    // Button 2: Extract Course Name
                                    FilledTonalButton(
                                        onClick = {
                                            val wv = webViewInstance ?: return@FilledTonalButton
                                            isExecutingAction = true
                                            automationStatus = "Extracting course from page..."
                                            AiWebAutomationController.extractCourseInfo(wv) { success, title ->
                                                isExecutingAction = false
                                                if (success) {
                                                    detectedCourseName = title
                                                    automationStatus = "Course Found: $title"
                                                } else {
                                                    automationStatus = title
                                                }
                                            }
                                        },
                                        enabled = !isExecutingAction,
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.School,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Extract Course Name", style = MaterialTheme.typography.labelSmall)
                                    }

                                    // Button 3: Auto-Next Module
                                    OutlinedButton(
                                        onClick = {
                                            val wv = webViewInstance ?: return@OutlinedButton
                                            isExecutingAction = true
                                            automationStatus = "Clicking Next / Continue..."
                                            AiWebAutomationController.clickNextOrContinue(wv) { success, msg ->
                                                isExecutingAction = false
                                                automationStatus = msg
                                            }
                                        },
                                        enabled = !isExecutingAction,
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FastForward,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Next / Continue", style = MaterialTheme.typography.labelSmall)
                                    }

                                    // Button 4: Filter Keyword
                                    OutlinedButton(
                                        onClick = {
                                            val wv = webViewInstance ?: return@OutlinedButton
                                            isExecutingAction = true
                                            automationStatus = "Filtering for: $automationQuery..."
                                            AiWebAutomationController.filterOrSelectCourse(wv, automationQuery) { success, msg ->
                                                isExecutingAction = false
                                                automationStatus = msg
                                            }
                                        },
                                        enabled = !isExecutingAction && automationQuery.isNotBlank(),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FilterList,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Select Course", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                // Status Banner
                                if (automationStatus != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        ) {
                                            if (isExecutingAction) {
                                                CircularProgressIndicator(
                                                    strokeWidth = 2.dp,
                                                    color = NeonCyan,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = EmeraldPulse,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = automationStatus!!,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.destroy()
        }
    }
}
