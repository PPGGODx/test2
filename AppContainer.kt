package com.hybridmusic.player.di

import android.content.Context
import com.hybridmusic.player.data.local.AppDatabase
import com.hybridmusic.player.data.local.ZongStorageManager
import com.hybridmusic.player.data.remote.cloud.GoogleDriveSyncManager
import com.hybridmusic.player.data.remote.innertube.InnerTubeClient
import com.hybridmusic.player.data.repository.MusicRepository
import com.hybridmusic.player.player.PlayerController
import com.hybridmusic.player.util.NetworkMonitor
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val streamHeaderInterceptor = Interceptor { chain ->
        val original = chain.request()
        val urlStr = original.url.toString()
        if (urlStr.contains("googlevideo.com")) {
            val matchedUa = InnerTubeClient.resolveUserAgentForUrl(urlStr)
            val updated = original.newBuilder()
                .header("User-Agent", matchedUa)
                .build()
            chain.proceed(updated)
        } else {
            chain.proceed(original)
        }
    }

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(streamHeaderInterceptor)
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val database: AppDatabase = AppDatabase.getInstance(context)
    val networkMonitor: NetworkMonitor = NetworkMonitor(context)
    val innerTubeClient: InnerTubeClient = InnerTubeClient(httpClient)
    val driveSyncManager: GoogleDriveSyncManager = GoogleDriveSyncManager(context)
    val zongStorageManager: ZongStorageManager = ZongStorageManager(context).apply {
        ensureZongFolderExists()
    }

    val musicRepository: MusicRepository = MusicRepository(
        context = context,
        musicDao = database.musicDao(),
        innerTubeClient = innerTubeClient,
        driveSyncManager = driveSyncManager,
        zongStorageManager = zongStorageManager,
        networkMonitor = networkMonitor,
        httpClient = httpClient
    )

    val playerController: PlayerController = PlayerController(
        context = context,
        repository = musicRepository
    )
}
