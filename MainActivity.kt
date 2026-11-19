package com.hybridmusic.player

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hybridmusic.player.ui.screens.MainScreen
import com.hybridmusic.player.ui.theme.HybridMusicTheme
import com.hybridmusic.player.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val app = application as HybridMusicApp
        val container = app.container

        setContent {
            val viewModel: MainViewModel = viewModel(
                factory = MainViewModel.Factory(
                    repository = container.musicRepository,
                    playerController = container.playerController,
                    driveSyncManager = container.driveSyncManager,
                    networkMonitor = container.networkMonitor
                )
            )

            val permissionsLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) {
                viewModel.scanZongFolder(showBanner = false)
            }

            LaunchedEffect(Unit) {
                val perms = mutableListOf<String>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                    perms.add(Manifest.permission.READ_MEDIA_AUDIO)
                } else {
                    perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                        perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                }
                permissionsLauncher.launch(perms.toTypedArray())
                viewModel.scanZongFolder(showBanner = false)
            }

            HybridMusicTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}
