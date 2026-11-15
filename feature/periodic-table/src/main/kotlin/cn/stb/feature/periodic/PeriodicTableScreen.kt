package cn.stb.feature.periodic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.stb.core.data.AssetReader
import cn.stb.core.model.SubjectId
import cn.stb.core.model.ToolIcon
import cn.stb.core.model.ToolId
import cn.stb.core.model.ToolSpec
import cn.stb.core.registry.ToolHost
import cn.stb.core.registry.ToolProvider

class PeriodicTableToolProvider : ToolProvider {
    override val spec = ToolSpec(
        id = ToolId("chemistry.periodic"),
        subjectId = SubjectId("chemistry"),
        title = "元素周期表",
        summary = "118 种元素 · 点按查看详情",
        glyph = "表",
        icon = ToolIcon.ATOM,
        keywords = listOf("化学", "元素", "元素周期表", "周期表", "原子", "原子序数", "相对原子质量"),
    )

    @Composable
    override fun Content(host: ToolHost) {
        PeriodicTableScreen(onBack = host::close)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodicTableScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ElementRepository(AssetReader(context)) }
    val elements = remember { repository.elements() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("元素周期表")
                        Text(
                            text = "118 种元素 · 点按查看详情",
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PeriodicTable(elements = elements, modifier = Modifier.weight(1f))
            CategoryLegend(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}
