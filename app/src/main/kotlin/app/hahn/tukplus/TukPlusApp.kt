package app.hahn.tukplus

import android.app.Application
import app.hahn.tukplus.core.data.Prefetcher
import app.hahn.tukplus.logging.AppLogging
import app.hahn.tukplus.platform.Images
import app.hahn.tukplus.platform.RefreshWorker
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TukPlusApp : Application(), SingletonImageLoader.Factory {
    /** Made first, so that the session log exists before anything else runs. */
    @Inject lateinit var logging: AppLogging
    @Inject lateinit var prefetcher: Prefetcher

    override fun onCreate() {
        super.onCreate()
        logging.log.i("app", "created")
        prefetcher.onAppStart()
        RefreshWorker.schedule(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = Images.loader(context, logging.log)
}
