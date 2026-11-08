package cn.stb.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.stb.core.designsystem.GlyphBadge
import cn.stb.core.model.AppGraph
import cn.stb.core.model.AppSettings
import cn.stb.core.model.Subject
import cn.stb.core.model.ToolSpec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayManagerScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val catalog = AppGraph.catalog
    val hiddenCount = settings.visibility.hiddenSubjectIds.size + settings.visibility.hiddenToolIds.size

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("显示管理") },
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
            LazyColumn(
                modifier = Modifier.widthIn(max = 860.dp).fillMaxHeight(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            item {
                Text(
                    text = "关掉学科会连带隐藏它下面的所有工具；单项隐藏优先于置顶——" +
                        "被隐藏的工具即使还留在「我的常用」里也不会显示，但记录会保留，重新打开即复原。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            catalog.subjects.forEach { subject ->
                item(key = subject.id.value) {
                    SubjectBlock(
                        subject = subject,
                        settings = settings,
                        onSubjectHidden = { hidden -> viewModel.setSubjectHidden(subject.id.value, hidden) },
                        onToolHidden = { tool, hidden -> viewModel.setToolHidden(tool.id.value, hidden) },
                    )
                }
            }

            item {
                Column {
                    OutlinedButton(
                        onClick = { viewModel.resetVisibility() },
                        enabled = hiddenCount > 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (hiddenCount > 0) "恢复全部显示（$hiddenCount 项被隐藏）" else "所有内容都已显示")
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            }
        }
    }
}

@Composable
private fun SubjectBlock(
    subject: Subject,
    settings: AppSettings,
    onSubjectHidden: (Boolean) -> Unit,
    onToolHidden: (ToolSpec, Boolean) -> Unit,
) {
    val subjectVisible = subject.id.value !in settings.visibility.hiddenSubjectIds
    val accent = Color(subject.accent)
    val tools = AppGraph.catalog.toolsOf(subject.id)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphBadge(glyph = subject.short, accent = accent, size = 38.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(subject.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (subjectVisible) "${tools.size} 个工具" else "学科已隐藏",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = subjectVisible, onCheckedChange = { onSubjectHidden(!it) })
            }

            tools.forEach { tool ->
                if (subjectVisible) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 66.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    )
                    val toolVisible = tool.id.value !in settings.visibility.hiddenToolIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 66.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = tool.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = toolVisible, onCheckedChange = { onToolHidden(tool, !it) })
                    }
                }
            }
        }
    }
}
