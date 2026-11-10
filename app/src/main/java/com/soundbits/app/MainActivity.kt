package com.soundbits.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.soundbits.app.playback.PlayerViewModel
import com.soundbits.app.ui.shell.AppRoot
import com.soundbits.app.ui.theme.SoundBitsTheme

class MainActivity : ComponentActivity() {

    // Activity-scoped: sobrevive a rotaciones y es la única conexión MediaController de la app.
    private val playerViewModel: PlayerViewModel by viewModels { AppViewModelProvider.Factory }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* la reproducción funciona igual */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { SoundBitsTheme { AppRoot(playerViewModel) } }
    }
}
