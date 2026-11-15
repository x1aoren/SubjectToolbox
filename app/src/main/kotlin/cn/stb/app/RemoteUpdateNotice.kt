package cn.stb.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.stb.core.data.AppVersion
import cn.stb.core.model.AppGraph
import kotlinx.coroutines.launch

/**
 * 云端版本提示。
 *
 * 学校平板只能从专门的应用市场安装，所以这里只提示「有新版本」，不提供下载地址，
 * 也不自动下载。同一个远端版本只提示一次；用户确认后写入 AppSettings.lastSeenRemoteVersion。
 */
@Composable
fun RemoteUpdateNotice() {
    val context = LocalContext.current
    val currentVersion = remember(context) { AppVersion.name(context) }
    val cloud by AppGraph.cloud.snapshot.collectAsStateWithLifecycle()
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    val release = cloud.release
    val remoteVersion = release?.latestVersion.orEmpty()
    var dismissed by rememberSaveable(remoteVersion) { mutableStateOf(false) }

    if (dismissed) return
    if (settings == null) return
    if (release == null || !release.updateAvailable) return
    if (remoteVersion.isBlank() || remoteVersion == currentVersion) return
    if (settings?.lastSeenRemoteVersion == remoteVersion) return

    fun markSeen() {
        dismissed = true
        scope.launch {
            AppGraph.settings.update { current ->
                current.copy(lastSeenRemoteVersion = remoteVersion)
            }
        }
    }

    AlertDialog(
        onDismissRequest = ::markSeen,
        title = {
            Text(
                if (release.forceUpdate) "需要更新到 v$remoteVersion"
                else "发现新版本 v$remoteVersion",
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "学校平板请通过应用市场更新；更新前可以继续使用当前版本。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (release.updateNotes.isNotBlank()) {
                    Text(
                        text = release.updateNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                if (release.updateRequired) {
                    Text(
                        text = "当前版本低于学校要求的最低版本，请尽快更新。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::markSeen) { Text("知道了") }
        },
    )
}
