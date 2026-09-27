package app.hahn.tukplus.logging

import android.content.Context
import androidx.core.content.edit
import app.hahn.tukplus.core.network.DeviceUuid
import java.security.SecureRandom

/**
 * Values that are made one time per install:
 * - the device uuid for the API (api-reference §2);
 * - the salt that [app.hahn.tukplus.core.logging.Redactor] uses for hashes.
 */
class InstallIds(context: Context) {
    private val prefs = context.getSharedPreferences("install_ids", Context.MODE_PRIVATE)

    /** v1 UUID, with its creation time (the web app keeps both). */
    val deviceUuid: String = prefs.getString(KEY_UUID, null) ?: DeviceUuid.newV1().toString().also {
        prefs.edit { putString(KEY_UUID, it).putLong(KEY_UUID_CREATED, System.currentTimeMillis()) }
    }

    val deviceUuidCreatedAt: Long get() = prefs.getLong(KEY_UUID_CREATED, 0)

    val logSalt: String = prefs.getString(KEY_SALT, null) ?: ByteArray(16).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }
        .also { prefs.edit { putString(KEY_SALT, it) } }

    private companion object {
        const val KEY_UUID = "device_uuid"
        const val KEY_UUID_CREATED = "device_uuid_created"
        const val KEY_SALT = "log_salt"
    }
}
