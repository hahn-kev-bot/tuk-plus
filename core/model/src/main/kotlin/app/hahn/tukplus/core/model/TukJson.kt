package app.hahn.tukplus.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * JSON settings for the Tuk API.
 *
 * The API uses snake_case keys, sends many optional fields, and is not strict about
 * types. Model properties use camelCase and are mapped with [JsonNamingStrategy.SnakeCase].
 */
val TukJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    namingStrategy = JsonNamingStrategy.SnakeCase
}
