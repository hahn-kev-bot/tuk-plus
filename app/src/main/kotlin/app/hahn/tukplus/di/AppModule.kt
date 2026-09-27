package app.hahn.tukplus.di

import android.content.Context
import app.hahn.tukplus.BuildConfig
import app.hahn.tukplus.core.data.BrowseRepository
import app.hahn.tukplus.core.data.FileResponseCache
import app.hahn.tukplus.core.data.Prefetcher
import app.hahn.tukplus.core.data.RecentShops
import app.hahn.tukplus.core.data.ResourceStore
import app.hahn.tukplus.core.data.SearchRepository
import app.hahn.tukplus.core.data.ShopRepository
import app.hahn.tukplus.core.logging.Redactor
import app.hahn.tukplus.core.logging.TukLog
import app.hahn.tukplus.core.network.TukApi
import app.hahn.tukplus.logging.AppLogging
import app.hahn.tukplus.logging.InstallIds
import app.hahn.tukplus.logging.LogShare
import app.hahn.tukplus.platform.LocationProvider
import app.hahn.tukplus.platform.MenuViewPreference
import app.hahn.tukplus.platform.NetworkState
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.io.File
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

/** A scope that lives as long as the app process. For background loading. */
@Qualifier
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun installIds(@ApplicationContext context: Context) = InstallIds(context)

    @Provides
    @Singleton
    fun appLogging(@ApplicationContext context: Context, ids: InstallIds) = AppLogging(context, ids)

    @Provides
    fun tukLog(logging: AppLogging): TukLog = logging.log

    @Provides
    fun redactor(logging: AppLogging): Redactor = logging.redactor

    @Provides
    fun clock(logging: AppLogging): Clock = logging.clock

    @Provides
    @Singleton
    fun logShare(@ApplicationContext context: Context, logging: AppLogging) = LogShare(context, logging)

    @Provides
    @Singleton
    @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** One client for the whole app, so the HTTP/2 connection is reused (about 0.3 s per call instead of about 1 s). */
    @Provides
    @Singleton
    fun okHttp(ids: InstallIds, log: TukLog, redactor: Redactor): OkHttpClient = TukApi.createClient(
        deviceUuid = { ids.deviceUuid },
        userAgent = "TukPlus/${BuildConfig.VERSION_NAME} (Android)",
        log = log,
        redactor = redactor,
    )

    @Provides
    @Singleton
    fun tukApi(client: OkHttpClient) = TukApi(client)

    @Provides
    @Singleton
    fun resourceStore(@ApplicationContext context: Context, api: TukApi, @AppScope scope: CoroutineScope, clock: Clock, log: TukLog) =
        ResourceStore(
            cache = FileResponseCache(File(context.noBackupFilesDir, "api-cache")),
            fetch = api::fetchText,
            scope = scope,
            clock = clock,
            log = log,
        )

    @Provides
    @Singleton
    fun browse(store: ResourceStore, api: TukApi) = BrowseRepository(store, api.endpoints)

    @Provides
    @Singleton
    fun shops(store: ResourceStore, api: TukApi, browse: BrowseRepository) = ShopRepository(store, api.endpoints, browse)

    @Provides
    @Singleton
    fun search(store: ResourceStore, api: TukApi) = SearchRepository(store, api.endpoints)

    @Provides
    @Singleton
    fun recentShops(@ApplicationContext context: Context) = RecentShops(File(context.filesDir, "recent_shops.txt"))

    @Provides
    @Singleton
    fun menuViewPreference(@ApplicationContext context: Context) = MenuViewPreference(context)

    @Provides
    @Singleton
    fun networkState(@ApplicationContext context: Context) = NetworkState(context)

    @Provides
    @Singleton
    fun location(@ApplicationContext context: Context, log: TukLog) = LocationProvider(context, log)

    @Provides
    @Singleton
    fun prefetcher(
        api: TukApi,
        browse: BrowseRepository,
        shops: ShopRepository,
        recent: RecentShops,
        @AppScope scope: CoroutineScope,
        log: TukLog,
        network: NetworkState,
    ) = Prefetcher(api, browse, shops, recent, scope, log, mayUseDataFreely = network::isUnmetered)
}
