package cn.stb.feature.textbook

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import cn.stb.core.designsystem.ZoomStepper

private const val TEXTBOOK_URL = "https://jc.pep.com.cn/"
private const val ZOOM_STEP = 20
private const val MAX_ZOOM = 300
private const val MIN_ZOOM = 60

/**
 * 电子课本阅读器的 viewport 锁死了 user-scalable=no，WebView 原生缩放在它身上无效；
 * 阅读器自己带了一套缩放实现，直接驱动它，翻页和拖动仍然归页面处理。
 * 普通网页没有这套对象，就退回根元素的 CSS zoom。
 */
private fun WebView.stepPageZoom(direction: Int, onPercent: (Int) -> Unit) {
    val script = """
        (function (dir) {
          var book = window.BookInfo && BookInfo.getBook && BookInfo.getBook();
          var page = book && book.showPage;
          if (page && page.parameters && typeof page.animateToScale === 'function') {
            var current = Math.round((page.parameters.scale || 1) * 100);
            var next = Math.max(100, Math.min($MAX_ZOOM, current + dir * $ZOOM_STEP));
            if (next !== current) {
              var ev = { pointers: [{ pageX: window.innerWidth / 2, pageY: window.innerHeight / 2 }] };
              if (next > 100 && !page.parameters.zoomFlag) page.zoomIn(page);
              // 固定以页面中心缩放；缩小回 100% 时中心点归零，页面自然恢复居中
              page.parameters.origin = { x: 50, y: 50 };
              page.originObj(page, page.parameters.origin);
              page.animateToScale(page, next / 100, ev);
              if (next <= 100) {
                // 阅读器收尾只复位标记，拖动过的位移会留在 transform 上，手动清掉
                setTimeout(function () {
                  page.parameters.tran = { x: 0, y: 0 };
                  page.parameters.scale = 1;
                  page.parameters.toScale = 1;
                  page.css({ '-webkit-transform': 'none', transform: 'none' });
                }, 420);
              }
            }
            return String(next);
          }
          var root = document.documentElement;
          var current = parseInt(root.style.zoom) || 100;
          var next = Math.max($MIN_ZOOM, Math.min($MAX_ZOOM, current + dir * $ZOOM_STEP));
          root.style.zoom = next + '%';
          return String(next);
        })($direction)
    """.trimIndent()
    evaluateJavascript(script) { result ->
        result?.trim('"')?.toIntOrNull()?.let(onPercent)
    }
}

/**
 * 页面加载完后同步缩放：阅读器读它自己的比例，普通页面延续应用里的缩放级别。
 */
private fun WebView.syncPageZoom(percent: Int, onPercent: (Int) -> Unit) {
    val script = """
        (function (value) {
          var book = window.BookInfo && BookInfo.getBook && BookInfo.getBook();
          var page = book && book.showPage;
          if (page && page.parameters) return String(Math.round((page.parameters.scale || 1) * 100));
          document.documentElement.style.zoom = value + '%';
          return String(value);
        })($percent)
    """.trimIndent()
    evaluateJavascript(script) { result ->
        result?.trim('"')?.toIntOrNull()?.let(onPercent)
    }
}

class TextbookToolProvider : cn.stb.core.registry.ToolProvider {
    override val spec = cn.stb.core.model.ToolSpec(
        id = cn.stb.core.model.ToolId("other.textbook"),
        subjectId = cn.stb.core.model.SubjectId("other"),
        title = "电子教材",
        summary = "人教社官网 · 各科电子课本 · 需联网",
        glyph = "材",
        keywords = listOf("电子教材", "课本", "教材", "人教", "在线"),
    )

    @Composable
    override fun Content(host: cn.stb.core.registry.ToolHost) {
        TextbookScreen(onBack = host::close)
    }
}

/**
 * 电子课本入口：内嵌 WebView 访问人民教育出版社官网。
 * 顶栏返回键优先在网页历史里后退，退无可退才关闭工具。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TextbookScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var progress by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("人教社官网") }
    var zoomPercent by rememberSaveable { mutableIntStateOf(100) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    loading = false
                    pageTitle = view?.title?.takeIf { it.isNotBlank() } ?: "人教社官网"
                    view?.syncPageZoom(zoomPercent) { zoomPercent = it }
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    canGoBack = view?.canGoBack() == true
                }
            }
            setWebChromeClient(object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress
                    loading = newProgress in 1..99
                }
            })
            loadUrl(TEXTBOOK_URL)
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { webView.destroy() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("电子教材")
                        Text(
                            text = pageTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (canGoBack) webView.goBack() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    ZoomStepper(
                        percent = zoomPercent,
                        onZoomOut = { webView.stepPageZoom(-1) { zoomPercent = it } },
                        onZoomIn = { webView.stepPageZoom(1) { zoomPercent = it } },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loading) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
