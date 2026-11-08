package cn.stb.feature.chemistry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.stb.core.data.AssetReader
import cn.stb.core.designsystem.SearchField
import cn.stb.core.model.SubjectId
import cn.stb.core.model.ToolId
import cn.stb.core.model.ToolSpec
import cn.stb.core.registry.ToolHost
import cn.stb.core.registry.ToolProvider

class ChemistryToolProvider : ToolProvider {
    override val spec = ToolSpec(
        id = ToolId("chemistry.equation"),
        subjectId = SubjectId("chemistry"),
        title = "化学方程式",
        summary = "165 条方程式 · 配平 · 元素周期表",
        glyph = "式",
        keywords = listOf("化学", "方程式", "配平", "反应", "氧化", "元素周期表", "元素"),
    )

    @Composable
    override fun Content(host: ToolHost) {
        ChemistryScreen(onBack = host::close)
    }
}

private val ChemAccent = Color(0xFF6B4A8F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChemistryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ChemistryRepository(AssetReader(context)) }
    val all = remember { repository.equations() }
    val types = remember { repository.types() }

    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("全部") }

    val filtered = remember(query, type) {
        all.filter { eq ->
            (type == "全部" || eq.type == type) &&
                (query.isBlank() || listOf(
                    eq.reactantText, eq.productText, eq.condition, eq.note, eq.type,
                ).any { it.contains(query.trim(), ignoreCase = true) })
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("化学方程式")
                        Text(
                            text = "${all.size} 条 · 支持自动配平",
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
            Column(modifier = Modifier.widthIn(max = 1040.dp).fillMaxHeight()) {
            TabRow(
                selectedTabIndex = tab,
                modifier = Modifier.widthIn(max = 520.dp),
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = ChemAccent,
            ) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("方程式库") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("配平器") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("元素周期表") })
            }

            when (tab) {
                0 -> LibraryTab(
                    query = query,
                    onQueryChange = { query = it },
                    type = type,
                    onTypeChange = { type = it },
                    types = types,
                    equations = filtered,
                )
                1 -> BalancerTab()
                else -> PeriodicTab()
            }
            }
        }
    }
}

@Composable
private fun LibraryTab(
    query: String,
    onQueryChange: (String) -> Unit,
    type: String,
    onTypeChange: (String) -> Unit,
    types: List<String>,
    equations: List<ChemEquation>,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            SearchField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = "搜反应物、生成物或条件，如 Fe、点燃",
                modifier = Modifier.fillMaxWidth(),
                showClearButton = true,
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("全部") + types) { t ->
                    FilterChip(
                        selected = t == type,
                        onClick = { onTypeChange(t) },
                        label = { Text(t, fontSize = 12.sp) },
                    )
                }
            }
        }

        if (equations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "没有匹配的方程式",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(equations, key = { it.reactantText + "->" + it.productText + it.condition }) { eq ->
                    EquationCard(eq)
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun EquationCard(eq: ChemEquation) {
    val balanced = remember(eq) {
        Balancer.balance(eq.reactantText + " -> " + eq.productText)
    }
    val coefficients = (balanced as? BalanceResult.Success)?.coefficients

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = eq.type,
                    style = MaterialTheme.typography.labelMedium,
                    color = ChemAccent,
                )
                if (eq.condition.isNotBlank()) {
                    Text(
                        text = " · ${eq.condition}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = buildEquation(coefficients, eq.reactants, eq.products, eq.arrow),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 26.sp,
            )
            if (eq.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = eq.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BalancerTab() {
    var input by rememberSaveable { mutableStateOf("Fe + O2 -> Fe3O4") }
    var result by remember { mutableStateOf<BalanceResult?>(null) }
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; result = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("输入方程式，用 -> 分隔两侧") },
            placeholder = { Text("如：C3H8 + O2 -> CO2 + H2O") },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            minLines = 2,
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                val r = Balancer.balance(input)
                result = r
                // 配平成功后把输入规范化：统一箭头、补齐加号两侧空格、去掉多余空白
                if (r is BalanceResult.Success) input = plainBalanced(r, unicodeSubscripts = false)
            }) { Text("配平") }
            listOf("H2 + O2 -> H2O", "Fe + O2 -> Fe3O4", "Al + HCl -> AlCl3 + H2").forEach { sample ->
                FilterChip(
                    selected = input == sample,
                    onClick = { input = sample; result = null },
                    label = { Text(sample.substringBefore(" ->"), fontSize = 11.sp) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        when (val r = result) {
            null -> Text(
                text = "按元素守恒列线性方程组，高斯消元求零空间，再取最小正整数解。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is BalanceResult.Failure -> Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    text = r.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }

            is BalanceResult.Success -> Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "配平结果",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(plainBalanced(r, unicodeSubscripts = true)))
                            },
                        ) { Text("复制结果", fontSize = 12.sp) }
                    }
                    Spacer(Modifier.height(12.dp))
                    // 逐项排版：系数与化学式绑定成一个块，空格与换行交给 FlowRow 自动处理
                    EquationFlow(r)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "系数：" + r.coefficients.joinToString(" : "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            text = "只处理分子方程式。离子方程式与氧化还原半反应需要额外的电荷守恒列，留作后续扩展。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        )
    }
}

/** 把配平结果排成会自动换行的方程式，+ 与 → 两侧的空格由布局统一处理。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EquationFlow(result: BalanceResult.Success) {
    val style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif)

    @Composable
    fun term(index: Int, formula: String) {
        val coefficient = result.coefficients.getOrNull(index) ?: 1
        Text(
            text = buildAnnotatedString {
                if (coefficient != 1) append(coefficient.toString())
                append(subscripted(formula))
            },
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 32.sp,
        )
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        result.reactants.forEachIndexed { index, formula ->
            if (index > 0) Text("+", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
            term(index, formula)
        }
        Text("→", style = style, color = ChemAccent)
        result.products.forEachIndexed { index, formula ->
            if (index > 0) Text("+", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
            term(result.reactants.size + index, formula)
        }
    }
}

/** 纯文本版本，unicodeSubscripts=true 时用 Unicode 下标（适合粘到聊天/文档里）。 */
private fun plainBalanced(result: BalanceResult.Success, unicodeSubscripts: Boolean): String = buildString {
    fun side(formulas: List<String>, offset: Int) {
        formulas.forEachIndexed { index, formula ->
            if (index > 0) append(" + ")
            val coefficient = result.coefficients.getOrNull(offset + index) ?: 1
            if (coefficient != 1) append(coefficient)
            append(if (unicodeSubscripts) toUnicodeSubscripts(formula) else formula)
        }
    }
    side(result.reactants, 0)
    append(" → ")
    side(result.products, result.reactants.size)
}

private val UNICODE_SUBSCRIPTS = "₀₁₂₃₄₅₆₇₈₉"

private fun toUnicodeSubscripts(formula: String): String = buildString {
    formula.forEach { c ->
        append(if (c.isDigit()) UNICODE_SUBSCRIPTS[c.digitToInt()] else c)
    }
}

/* ------------------------------------------------------------------
 * 元素周期表
 * ---------------------------------------------------------------- */

@Composable
private fun PeriodicTab() {
    val context = LocalContext.current
    val repository = remember { ElementRepository(AssetReader(context)) }
    val elements = remember { repository.elements() }

    Column(modifier = Modifier.fillMaxSize()) {
        PeriodicTable(elements = elements, modifier = Modifier.weight(1f))
        CategoryLegend(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

/* ------------------------------------------------------------------
 * 排版：化学式里的数字要变成下标，否则 H2O 会被读成 H 二 O
 * ---------------------------------------------------------------- */

private fun subscripted(formula: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < formula.length) {
        val c = formula[i]
        if (c.isDigit()) {
            var j = i
            while (j < formula.length && formula[j].isDigit()) j++
            withStyle(SpanStyle(baselineShift = BaselineShift.Subscript)) {
                append(formula.substring(i, j))
            }
            i = j
        } else {
            append(c)
            i++
        }
    }
}

private fun buildEquation(
    coefficients: List<Int>?,
    reactants: List<String>,
    products: List<String>,
    arrow: String = "→",
): AnnotatedString = buildAnnotatedString {
    reactants.forEachIndexed { index, formula ->
        if (index > 0) append("  +  ")
        val coefficient = coefficients?.getOrNull(index) ?: 1
        if (coefficient != 1) append(coefficient.toString())
        append(subscripted(formula))
    }
    append("   $arrow   ")
    products.forEachIndexed { index, formula ->
        if (index > 0) append("  +  ")
        val coefficient = coefficients?.getOrNull(reactants.size + index) ?: 1
        if (coefficient != 1) append(coefficient.toString())
        append(subscripted(formula))
    }
}
