package app.hahn.tukplus.di

import android.content.Context
import app.hahn.tukplus.BuildConfig
import app.hahn.tukplus.core.logging.Redactor
import app.hahn.tukplus.core.logging.TukLog
import app.hahn.tukplus.core.network.TukApi
import app.hahn.tukplus.logging.AppLogging
import app.hahn.tukplus.logging.InstallIds
import app.hahn.tukplus.logging.LogShare
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

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
    @Singleton
    fun logShare(@ApplicationContext context: Context, logging: AppLogging) = LogShare(context, logging)

    /** One client for the whole app, so the HTTP/2 connection is reused (about 300 ms per call instead of about 1 s). */
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
}
