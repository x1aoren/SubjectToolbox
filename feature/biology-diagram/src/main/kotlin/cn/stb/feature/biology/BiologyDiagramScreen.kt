package cn.stb.feature.biology

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

class BiologyDiagramToolProvider : ToolProvider {
    override val spec = ToolSpec(
        id = ToolId("biology.diagram"),
        subjectId = SubjectId("biology"),
        title = "交互示意图",
        summary = "细胞分裂等交互动画",
        glyph = "图",
        keywords = listOf("生物", "细胞", "细胞分裂", "有丝分裂", "减数分裂", "染色体", "示意图"),
    )

    @Composable
    override fun Content(host: ToolHost) {
        BiologyDiagramScreen(onBack = host::close)
    }
}

/** 生物示意图清单。以后新增一张图，往这里补一条即可。 */
private val BIOLOGY_DIAGRAMS = listOf(
    DiagramEntry(
        id = "cell-division",
        title = "细胞分裂交互图",
        summary = "有丝分裂 / 减数分裂动画，染色体、染色单体与 DNA 数目实时统计",
        glyph = "裂",
        tag = "必修一",
        assetPath = "file:///android_asset/content/biology/cell-division.html",
        keywords = listOf(
            "细胞", "细胞分裂", "有丝分裂", "减数分裂", "染色体", "染色单体",
            "DNA", "配子", "交叉互换", "动物细胞", "植物细胞",
        ),
    ),
)

@Composable
fun BiologyDiagramScreen(onBack: () -> Unit) {
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    val opened = BIOLOGY_DIAGRAMS.firstOrNull { it.id == openedId }

    if (opened == null) {
        DiagramGalleryScreen(
            title = "生物 · 交互示意图",
            subtitle = "共 ${BIOLOGY_DIAGRAMS.size} 张交互图",
            accent = Color(0xFF2E7D32),
            entries = BIOLOGY_DIAGRAMS,
            searchHint = "搜索示意图，如「细胞」「分裂」",
            onOpen = { openedId = it.id },
            onBack = onBack,
        )
    } else {
        HtmlDiagramScreen(
            assetPath = opened.assetPath,
            title = opened.title,
            subtitle = opened.summary,
            pageBackground = Color(0xFFEEF2F7),
            onBack = { openedId = null },
        )
    }
}
