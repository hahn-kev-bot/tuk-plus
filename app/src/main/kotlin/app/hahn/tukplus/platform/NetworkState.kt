package app.hahn.tukplus.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Facts about the current network. */
class NetworkState(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    /** True on Wi-Fi or another network that is not metered. Background menu prefetch uses it (PLAN.md §5.4). */
    fun isUnmetered(): Boolean {
        val caps = manager?.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
