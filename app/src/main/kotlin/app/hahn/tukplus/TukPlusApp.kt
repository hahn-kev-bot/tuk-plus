package app.hahn.tukplus

import android.app.Application
import app.hahn.tukplus.logging.AppLogging
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TukPlusApp : Application() {
    /** Made first, so that the session log exists before anything else runs. */
    @Inject lateinit var logging: AppLogging

    override fun onCreate() {
        super.onCreate()
        logging.log.i("app", "created")
    }
}
