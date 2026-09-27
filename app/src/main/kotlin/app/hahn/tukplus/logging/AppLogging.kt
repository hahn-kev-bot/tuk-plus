package app.hahn.tukplus.logging

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.hahn.tukplus.BuildConfig
import app.hahn.tukplus.core.logging.FileLogSink
import app.hahn.tukplus.core.logging.LogFileStore
import app.hahn.tukplus.core.logging.LogLevel
import app.hahn.tukplus.core.logging.Redactor
import app.hahn.tukplus.core.logging.SessionLog
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import java.util.TimeZone

/**
 * Sets up the session log (PLAN.md §9): the day files, the `session_start` line, the
 * crash handler, and foreground/background events.
 */
class AppLogging(private val context: Context, ids: InstallIds) {
    val clock: Clock = Clock.systemDefaultZone()
    val store = LogFileStore(File(context.filesDir, "logs"))
    val redactor = Redactor(ids.logSalt)
    private val sink = FileLogSink(store, clock)
    private val crashMarker = File(store.directory, ".crashed")

    /** True when the last session ended with a crash. The UI asks the user to share the logs. */
    val crashedLastTime: Boolean = crashMarker.exists()

    val log: SessionLog = SessionLog(
        sink = sink,
        clock = clock,
        startInfo = startInfo(ids),
        echo = if (BuildConfig.DEBUG) ::echoToLogcat else null,
    )

    init {
        val deleted = store.deleteOld(LocalDate.now(clock))
        if (deleted.isNotEmpty()) log.i("app", "logs_deleted", "files" to deleted.map { it.name })
        if (crashedLastTime) log.w("app", "previous_session_crashed", "session" to crashMarker.readText().trim())
        installCrashHandler()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = log.i("app", "foreground", "network" to networkType())
            override fun onStop(owner: LifecycleOwner) {
                log.i("app", "background")
                log.flush()
            }
        })
    }

    fun clearCrashMarker() {
        crashMarker.delete()
    }

    /** Waits until all events are on disk. Call it before an export. Not on the main thread. */
    fun flushBlocking() = sink.flushBlocking()

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                log.logCrashBlocking(thread.name, throwable)
                crashMarker.writeText(log.sessionId)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun startInfo(ids: InstallIds): Map<String, Any?> = mapOf(
        "app_version" to BuildConfig.VERSION_NAME,
        "version_code" to BuildConfig.VERSION_CODE,
        "build_type" to BuildConfig.BUILD_TYPE,
        "git_commit" to BuildConfig.GIT_COMMIT,
        "android" to Build.VERSION.RELEASE,
        "sdk" to Build.VERSION.SDK_INT,
        "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
        "locale" to Locale.getDefault().toLanguageTag(),
        "time_zone" to TimeZone.getDefault().id,
        "region" to "Chiang Mai",
        "network" to networkType(),
        "device_uuid" to redactor.hash(ids.deviceUuid),
        "logged_in" to false,
    )

    private fun networkType(): String {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return "unknown"
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return "none"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        } + if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) "" else " (metered)"
    }

    private fun echoToLogcat(level: LogLevel, line: String) {
        val priority = when (level) {
            LogLevel.DEBUG -> Log.DEBUG
            LogLevel.INFO -> Log.INFO
            LogLevel.WARN -> Log.WARN
            LogLevel.ERROR -> Log.ERROR
        }
        Log.println(priority, "TukPlus", line)
    }
}
