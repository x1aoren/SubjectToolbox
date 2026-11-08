package cn.stb.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.stb.core.model.AppGraph
import cn.stb.core.model.HomeContent
import cn.stb.core.model.ToolId
import cn.stb.core.registry.HomeComposer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    /**
     * null 表示「配置还没读出来」，与「所有工具都被隐藏」是两回事。
     * 不区分这两者，首帧就会闪一下空态提示。
     */
    val content: StateFlow<HomeContent?> = combine(
        AppGraph.settings.settings,
        AppGraph.usage.recents,
    ) { settings, recents ->
        HomeComposer.compose(AppGraph.catalog, settings, recents)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    fun togglePin(id: ToolId) {
        viewModelScope.launch {
            AppGraph.settings.update { current ->
                val pinned = current.home.pinnedToolIds
                val next = if (id.value in pinned) pinned - id.value else pinned + id.value
                current.copy(home = current.home.copy(pinnedToolIds = next))
            }
        }
    }
}
