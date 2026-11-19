package com.hybridmusic.player

import android.app.Application
import com.hybridmusic.player.di.AppContainer

class HybridMusicApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
