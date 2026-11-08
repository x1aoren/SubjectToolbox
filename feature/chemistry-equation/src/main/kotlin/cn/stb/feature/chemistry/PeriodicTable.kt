package cn.stb.feature.chemistry

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.stb.core.data.AssetReader

/** 一个元素格子需要的数据。 */
data class ChemElement(
    val z: Int,
    val symbol: String,
    val zh: String,
    val en: String,
    val mass: String,
    val cat: String,
    val group: Int?,
    val period: Int?,
    val x: Int,
    val y: Int,
    val phase: String,
    val melt: Double?,
    val boil: Double?,
    val density: Double?,
    val config: String,
)

/** 从 assets 读 118 个元素，解析一次后缓存。 */
class ElementRepository(private val assets: AssetReader) {
    private var cached: List<ChemElement>? = null

    fun elements(): List<ChemElement> = cached ?: load().also { cached = it }

    private fun load(): List<ChemElement> = buildList {
        val array = assets.jsonObject("content/chemistry/elements.json").getJSONArray("elements")
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            add(
                ChemElement(
                    z = o.getInt("z"),
                    symbol = o.getString("symbol"),
                    zh = o.getString("zh"),
                    en = o.getString("en"),
                    mass = o.getString("mass"),
                    cat = o.getString("cat"),
                    group = if (o.isNull("group")) null else o.getInt("group"),
                    period = if (o.isNull("period")) null else o.getInt("period"),
                    x = o.getInt("x"),
                    y = o.getInt("y"),
                    phase = o.optString("phase"),
                    melt = if (o.isNull("melt")) null else o.getDouble("melt"),
                    boil = if (o.isNull("boil")) null else o.getDouble("boil"),
                    density = if (o.isNull("density")) null else o.getDouble("density"),
                    config = o.optString("config"),
                )
            )
        }
    }
}

/** 分类配色：全部用浅色底 + 深色字，避免在浅色主题下出现低对比度。 */
internal fun categoryColor(cat: String): Color = when (cat) {
    "alkali" -> Color(0xFFE9C4A8)
    "alkaline" -> Color(0xFFEEDCB4)
    "transition" -> Color(0xFFB0C6DF)
    "post" -> Color(0xFFBACCAE)
    "metalloid" -> Color(0xFFCDCFB0)
    "nonmetal" -> Color(0xFFABD4C1)
    "noble" -> Color(0xFFD6C4E0)
    "lanthanide" -> Color(0xFFE7C6D0)
    "actinide" -> Color(0xFFDFCDC5)
    else -> Color(0xFFDADADA)
}

internal fun categoryLabel(cat: String): String = when (cat) {
    "alkali" -> "碱金属"
    "alkaline" -> "碱土金属"
    "transition" -> "过渡金属"
    "post" -> "主族金属"
    "metalloid" -> "类金属"
    "nonmetal" -> "非金属"
    "noble" -> "稀有气体"
    "lanthanide" -> "镧系"
    "actinide" -> "锕系"
    else -> "未知性质"
}

private val CELL_W = 56.dp
private val CELL_H = 62.dp
private const val COLS = 18
private const val ROWS = 10
private const val LANTHANIDE_ROW = 9
private const val ACTINIDE_ROW = 10

/** 单列在表格下方的两个系列：主表第 6/7 周期第 3 族留出对应占位格。 */
private data class Series(val row: Int, val label: String, val range: String, val cat: String)

private val LANTHANIDE = Series(LANTHANIDE_ROW, "镧系", "57–71", "lanthanide")
private val ACTINIDE = Series(ACTINIDE_ROW, "锕系", "89–103", "actinide")

private fun seriesOf(row: Int): Series? = when (row) {
    LANTHANIDE_ROW -> LANTHANIDE
    ACTINIDE_ROW -> ACTINIDE
    else -> null
}

/**
 * 元素周期表。
 *
 * 按数据的 x/y 坐标排 18 列 × 10 行：第 8 行是分隔空行，镧系（57–71）在第 9 行、
 * 锕系（89–103）在第 10 行。主表第 6/7 周期第 3 族用占位格标出这两个系列被抽走的位置，
 * 下方两行左侧再加系列名，避免「看起来少了两行」。
 * 横纵两个方向都可滚动：屏幕偏矮时最下面两行不会被裁掉。
 */
@Composable
fun PeriodicTable(
    elements: List<ChemElement>,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<ChemElement?>(null) }
    val grid = remember(elements) {
        Array(ROWS + 1) { arrayOfNulls<ChemElement>(COLS + 1) }.apply {
            elements.forEach { if (it.y in 1..ROWS && it.x in 1..COLS) this[it.y][it.x] = it }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // 算格子尺寸时必须把间距和内边距都扣掉，否则最后一列/最后一行会被挤出屏幕
        val gap = 3.dp
        val hPadding = 16.dp
        val cellW = ((maxWidth - hPadding * 2 - gap * (COLS - 1)) / COLS).coerceAtLeast(40.dp)
        val cellH = ((maxHeight - hPadding - gap * (ROWS - 1)) / ROWS).coerceAtLeast(46.dp)

        // 横向 + 纵向都能滚：屏幕偏矮时，第 9/10 行不再被直接裁掉
        Row(modifier = Modifier.fillMaxHeight().horizontalScroll(rememberScrollState())) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                for (row in 1..ROWS) {
                    val series = seriesOf(row)
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        // 下方两行的前两格合并成「镧系 / 锕系」标签
                        if (series != null) {
                            SeriesLabel(series, cellW * 2 + gap, cellH)
                        }
                        for (col in (if (series != null) 3 else 1)..COLS) {
                            val element = grid[row][col]
                            val marker = when {
                                row == 6 && col == 3 -> LANTHANIDE
                                row == 7 && col == 3 -> ACTINIDE
                                else -> null
                            }
                            when {
                                element != null -> ElementCell(element, cellW, cellH) { selected = element }
                                marker != null -> SeriesMarker(marker, cellW, cellH)
                                else -> Spacer(Modifier.size(cellW, cellH))
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { element ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { selected = null },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("知道了") } },
            title = { Text("${element.zh}（${element.symbol}）") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailRow("原子序数", element.z.toString())
                    DetailRow("英文名", element.en)
                    DetailRow("相对原子质量", element.mass)
                    DetailRow("分类", categoryLabel(element.cat))
                    if (element.period != null) DetailRow("周期", "第 ${element.period} 周期")
                    if (element.group != null) DetailRow("族", "第 ${element.group} 族")
                    if (element.phase.isNotBlank()) DetailRow("常温物态", element.phase)
                    element.melt?.let { DetailRow("熔点", "$it ℃") }
                    element.boil?.let { DetailRow("沸点", "$it ℃") }
                    element.density?.let { DetailRow("密度", "$it g/cm³") }
                    if (element.config.isNotBlank()) {
                        Text(
                            "核外电子排布",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            text = element.config,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
    }
}

/** 主表第 6/7 周期第 3 族的占位格：说明这一段被抽到表格下方，并指向对应行。 */
@Composable
private fun SeriesMarker(series: Series, width: Dp, height: Dp) {
    Surface(
        modifier = Modifier.size(width, height),
        shape = RoundedCornerShape(6.dp),
        color = categoryColor(series.cat).copy(alpha = 0.55f),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = series.range,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2B2B26),
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
            )
            Text(
                text = "${series.label} ↓",
                fontSize = 10.sp,
                color = Color(0xFF4A4238),
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
            )
        }
    }
}

/** 下方两行左侧的系列名标签，占前两列。 */
@Composable
private fun SeriesLabel(series: Series, width: Dp, height: Dp) {
    Surface(
        modifier = Modifier.size(width, height),
        shape = RoundedCornerShape(6.dp),
        color = categoryColor(series.cat),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = series.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2B2B26),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
            Text(
                text = series.range,
                fontSize = 10.sp,
                color = Color(0xFF4A4238),
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
            )
        }
    }
}

@Composable
private fun ElementCell(
    element: ChemElement,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(width, height),
        shape = RoundedCornerShape(6.dp),
        color = categoryColor(element.cat),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = element.z.toString(),
                fontSize = 8.sp,
                color = Color(0xFF5A4A3A),
                modifier = Modifier.align(Alignment.Start),
                lineHeight = 10.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = element.symbol,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2B2B26),
                lineHeight = 20.sp,
            )
            Text(
                text = element.zh,
                fontSize = 10.sp,
                color = Color(0xFF4A4238),
                lineHeight = 12.sp,
            )
            Spacer(Modifier.height(1.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 底部的分类图例。 */
@Composable
fun CategoryLegend(modifier: Modifier = Modifier) {
    val cats = listOf("nonmetal", "noble", "alkali", "alkaline", "metalloid", "transition", "post", "lanthanide", "actinide")
    Column(modifier = modifier) {
        cats.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                row.forEach { cat ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .background(categoryColor(cat), RoundedCornerShape(2.dp)),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(categoryLabel(cat), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Text(
            text = "第 6、7 周期第 3 族的 57–71（镧系）与 89–103（锕系）已单独列在表格下方两行，可上下滚动查看。",
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
