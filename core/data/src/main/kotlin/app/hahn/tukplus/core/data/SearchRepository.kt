package app.hahn.tukplus.core.data

import app.hahn.tukplus.core.model.BusinessAutocomplete
import app.hahn.tukplus.core.model.MenuItemSearchHit
import app.hahn.tukplus.core.network.Endpoints

/** Server search (api-reference §5.4). Results are cached for 10 minutes. */
class SearchRepository(private val store: ResourceStore, private val endpoints: Endpoints) {
    fun shops(text: String): CachedResource<BusinessAutocomplete> =
        store.resource(endpoints.autocompleteBusiness(text.trim()), Policies.SEARCH)

    fun menuItems(text: String): CachedResource<List<MenuItemSearchHit>> =
        store.resource(endpoints.searchMenuItems(text.trim()), Policies.SEARCH)

    companion object {
        /** Like the web app: the server search starts at 3 characters. */
        const val MIN_SERVER_CHARS = 3
    }
}
