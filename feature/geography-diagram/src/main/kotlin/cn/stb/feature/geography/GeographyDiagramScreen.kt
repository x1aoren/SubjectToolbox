package cn.stb.feature.geography

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import cn.stb.core.designsystem.DiagramEntry
import cn.stb.core.designsystem.DiagramGalleryScreen
import cn.stb.core.designsystem.HtmlDiagramScreen
import cn.stb.core.model.SubjectId
import cn.stb.core.model.ToolId
import cn.stb.core.model.ToolSpec
import cn.stb.core.registry.ToolHost
import cn.stb.core.registry.ToolProvider

class GeographyDiagramToolProvider : ToolProvider {
    override val spec = ToolSpec(
        id = ToolId("geography.diagram"),
        subjectId = SubjectId("geography"),
        title = "交互示意图",
        summary = "地球自转 · 昼夜交替与线速度",
        glyph = "图",
        keywords = listOf(
            "地理", "地球", "自转", "地轴", "昼夜交替", "线速度", "角速度", "示意图",
        ),
    )

    @Composable
    override fun Content(host: ToolHost) {
        GeographyDiagramScreen(onBack = host::close)
    }
}

/** 地理示意图清单。以后新增一张图，往这里补一条即可。 */
private val GEOGRAPHY_DIAGRAMS = listOf(
    DiagramEntry(
        id = "earth-rotation",
        title = "地球自转交互图",
        summary = "自转方向、角速度与线速度随纬度变化，昼夜半球和太阳光照",
        glyph = "转",
        tag = "必修一",
        assetPath = "file:///android_asset/content/geography/earth-rotation.html",
        keywords = listOf(
            "地球自转", "自转", "地轴", "倾角", "23.5", "昼夜交替", "昼夜半球",
            "线速度", "角速度", "自转周期", "北极", "南极", "太阳", "纬度",
        ),
    ),
)

@Composable
fun GeographyDiagramScreen(onBack: () -> Unit) {
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    val opened = GEOGRAPHY_DIAGRAMS.firstOrNull { it.id == openedId }

    if (opened == null) {
        DiagramGalleryScreen(
            title = "地理 · 交互示意图",
            subtitle = "共 ${GEOGRAPHY_DIAGRAMS.size} 张交互图",
            accent = Color(0xFF9A6B1F),
            entries = GEOGRAPHY_DIAGRAMS,
            searchHint = "搜索示意图，如「地球」「自转」",
            onOpen = { openedId = it.id },
            onBack = onBack,
        )
    } else {
        HtmlDiagramScreen(
            assetPath = opened.assetPath,
            title = opened.title,
            subtitle = opened.summary,
            pageBackground = Color(0xFF050B16),
            onBack = { openedId = null },
        )
    }
}
