package com.hybridmusic.player.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hybridmusic.player.HybridMusicApp

class SongDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_VIDEO_ID = "video_id"
    }

    override suspend fun doWork(): Result {
        val videoId = inputData.getString(KEY_VIDEO_ID) ?: return Result.failure()
        val app = applicationContext as HybridMusicApp
        val success = app.container.musicRepository.performSongDownloadNow(videoId)
        return if (success) Result.success() else Result.retry()
    }
}

class CloudBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as HybridMusicApp
        val result = app.container.musicRepository.syncWithGoogleDriveNow()
        return if (result.isSuccess) Result.success() else Result.retry()
    }
}
