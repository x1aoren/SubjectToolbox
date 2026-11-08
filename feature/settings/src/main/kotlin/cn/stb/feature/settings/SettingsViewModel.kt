package cn.stb.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.stb.core.model.AppGraph
import cn.stb.core.model.AppSettings
import cn.stb.core.model.DarkMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    val settings: StateFlow<AppSettings> = AppGraph.settings.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { AppGraph.settings.update(transform) }
    }

    fun setDarkMode(mode: DarkMode) = update { it.copy(theme = it.theme.copy(darkMode = mode)) }

    fun setFontScale(scale: Float) = update { it.copy(theme = it.theme.copy(fontScale = scale)) }

    fun setShowRecents(show: Boolean) = update { it.copy(home = it.home.copy(showRecents = show)) }

    fun setSubjectHidden(subjectId: String, hidden: Boolean) = update { current ->
        val next = current.visibility.hiddenSubjectIds.toMutableSet()
        if (hidden) next += subjectId else next -= subjectId
        current.copy(visibility = current.visibility.copy(hiddenSubjectIds = next))
    }

    fun setToolHidden(toolId: String, hidden: Boolean) = update { current ->
        val next = current.visibility.hiddenToolIds.toMutableSet()
        if (hidden) next += toolId else next -= toolId
        current.copy(visibility = current.visibility.copy(hiddenToolIds = next))
    }

    fun resetVisibility() = update { current ->
        current.copy(
            visibility = current.visibility.copy(
                hiddenSubjectIds = emptySet(),
                hiddenToolIds = emptySet(),
            )
        )
    }
}
