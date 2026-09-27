package app.hahn.tukplus.platform

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MenuView { LIST, GRID }

/** The user's choice of menu layout (docs/design.md). Kept on the device. */
class MenuViewPreference(context: Context) {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
    private val _view = MutableStateFlow(
        runCatching { MenuView.valueOf(prefs.getString(KEY, null) ?: MenuView.LIST.name) }.getOrDefault(MenuView.LIST),
    )
    val view: StateFlow<MenuView> = _view.asStateFlow()

    fun set(view: MenuView) {
        _view.value = view
        prefs.edit { putString(KEY, view.name) }
    }

    private companion object {
        const val KEY = "menu_view"
    }
}
