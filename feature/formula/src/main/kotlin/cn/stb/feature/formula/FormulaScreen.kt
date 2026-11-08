package cn.stb.feature.formula

import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cn.stb.core.data.AssetReader
import cn.stb.core.model.SubjectId
import cn.stb.core.model.ToolId
import cn.stb.core.model.ToolSpec
import cn.stb.core.registry.ToolHost
import cn.stb.core.registry.ToolProvider
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.Locale

class FormulaToolProvider : ToolProvider {
    override val spec = ToolSpec(
        id = ToolId("other.formula"),
        subjectId = SubjectId("other"),
        title = "公式大全",
        summary = "初高中数理化 · 变量说明 · 万能搜索",
        glyph = "公",
        keywords = listOf("公式", "数学", "物理", "化学", "初中", "高中", "速查", "定理"),
    )

    @Composable
    override fun Content(host: ToolHost) {
        FormulaScreen(onBack = host::close)
    }
}

private val formulaSubjects = listOf("数学", "物理", "化学")

private val subjectAccents = mapOf(
    "数学" to 0xFF34558B,
    "物理" to 0xFF1F6F78,
    "化学" to 0xFF6B4A8F,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormulaScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { FormulaRepository(AssetReader(context)) }
    val rawJson = remember { repository.rawJson() }
    val total = remember { repository.count() }

    var subject by rememberSaveable { mutableStateOf(formulaSubjects.first()) }
    var query by rememberSaveable { mutableStateOf("") }
    val searching = query.isNotBlank()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("公式大全")
                        Text(
                            text = "$total 条 · 初中 / 高中",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(modifier = Modifier.widthIn(max = 1060.dp).fillMaxHeight()) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("搜索数学、物理、化学公式，如「勾股」「sin」") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "清空",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )

                    if (searching) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "在数学、物理、化学中搜索「${query.trim()}」",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        TabRow(
                            selectedTabIndex = formulaSubjects.indexOf(subject),
                            modifier = Modifier.widthIn(max = 520.dp),
                            containerColor = MaterialTheme.colorScheme.background,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ) {
                            formulaSubjects.forEach { name ->
                                Tab(
                                    selected = name == subject,
                                    onClick = { subject = name },
                                    text = { Text(name) },
                                )
                            }
                        }
                    }
                }

                FormulaWebHost(
                    rawJson = rawJson,
                    subject = subject,
                    query = query,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * 公式排版交给页面里的 KaTeX：分式、根式、上下标都能按教材习惯显示。
 * 列表也由同一页面渲染，避免一个卡片一个 WebView 带来的滚动卡顿。
 */
@Composable
private fun FormulaWebHost(
    rawJson: String,
    subject: String,
    query: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val fontScale = (MaterialTheme.typography.bodyMedium.fontSize.value / 14f)
        .coerceIn(0.8f, 1.6f)

    val themeJson = remember(colors, dark, fontScale) { buildThemeJson(colors, dark, fontScale) }
    val viewJson = remember(subject, query) {
        JSONObject()
            .put("subject", subject)
            .put("query", query)
            .toString()
    }
    val backgroundArgb = colors.background.toArgb()

    var webView by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(backgroundArgb)
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.textZoom = 100
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    settings.forceDark = WebSettings.FORCE_DARK_OFF
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        ready = true
                    }
                }
                loadUrl("file:///android_asset/content/formula/index.html")
            }.also { webView = it }
        },
        update = { it.setBackgroundColor(backgroundArgb) },
    )

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    LaunchedEffect(ready, themeJson) {
        if (!ready) return@LaunchedEffect
        webView?.evaluateJavascript("FormulaUI.setTheme($themeJson)", null)
    }

    LaunchedEffect(ready, rawJson) {
        if (!ready) return@LaunchedEffect
        webView?.evaluateJavascript(
            "FormulaUI.setData(JSON.parse(${JSONObject.quote(rawJson)}).formulas)",
            null,
        )
    }

    LaunchedEffect(ready, viewJson) {
        if (!ready) return@LaunchedEffect
        if (query.isNotBlank()) delay(140)
        webView?.evaluateJavascript("FormulaUI.setView($viewJson)", null)
    }
}

private fun buildThemeJson(colors: ColorScheme, dark: Boolean, scale: Float): String {
    val accents = JSONObject()
    subjectAccents.forEach { (name, argb) ->
        val base = Color(argb)
        val shown = if (dark) lerp(base, Color.White, 0.42f) else base
        accents.put(name, shown.hex())
    }

    return JSONObject()
        .put("bg", colors.background.hex())
        .put("surface", colors.surface.hex())
        .put("surfaceVariant", colors.surfaceVariant.hex())
        .put("onSurface", colors.onSurface.hex())
        .put("onSurfaceVariant", colors.onSurfaceVariant.hex())
        .put("outline", colors.outlineVariant.hex())
        .put("primary", colors.primary.hex())
        .put("scale", scale.toDouble())
        .put("accents", accents)
        .toString()
}

private fun Color.hex(): String = String.format(Locale.US, "#%06X", toArgb() and 0xFFFFFF)
