package app.hahn.tukplus.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.util.Consumer
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import app.hahn.tukplus.core.logging.TukLog
import app.hahn.tukplus.core.model.LatLon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/**
 * The user's approximate position, for distance and sort only (PLAN.md §1).
 * It uses the platform location service with coarse accuracy, so no Google Play services.
 */
class LocationProvider(private val context: Context, private val log: TukLog) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val _location = MutableStateFlow<LatLon?>(null)
    val location: StateFlow<LatLon?> = _location.asStateFlow()

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Uses the last known position at once, then asks for a current one. Does nothing without permission. */
    @SuppressLint("MissingPermission")
    fun update() {
        if (!hasPermission() || manager == null) return
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
        providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }?.let { set(it, "last_known") }
        val provider = providers.firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) } ?: return
        LocationManagerCompat.getCurrentLocation(
            manager,
            provider,
            CancellationSignal(),
            Executors.newSingleThreadExecutor(),
            Consumer<Location?> { location -> if (location != null) set(location, "current") },
        )
    }

    private fun set(location: Location, source: String) {
        _location.value = LatLon(location.latitude, location.longitude)
        // Log only the accuracy, not the position (PLAN.md §9.3 allows it, but the list does not need it).
        log.i("location", "update", "source" to source, "accuracy_m" to location.accuracy.toInt())
    }
}
