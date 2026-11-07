package app.xtreamplayer

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import app.xtreamplayer.ui.AccountsScreen
import app.xtreamplayer.ui.AppTheme
import app.xtreamplayer.ui.BrowseScreen
import app.xtreamplayer.ui.PlayerScreen
import app.xtreamplayer.ui.SeriesScreen
import app.xtreamplayer.ui.UpdateDialog

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Busca una versión nueva al abrir la app (como máximo una vez cada 3 días).
        if (savedInstanceState == null) vm.checkUpdate()
        setContent {
            AppTheme {
                Root(vm)
            }
        }
    }

    /** Al pulsar Inicio mientras se reproduce, la app pasa a picture-in-picture. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val watching = vm.stack.lastOrNull() is Screen.Player
        if (watching &&
            !isInPictureInPictureMode &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }
    }
}

@Composable
private fun Root(vm: AppViewModel) {
    val holder = rememberSaveableStateHolder()
    val screen = vm.stack.last()

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    if (vm.askNotifPermission) {
        LaunchedEffect(Unit) {
            vm.consumeNotifRequest()
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = vm.stack.size > 1) { vm.back() }

    // SaveableStateProvider conserva el scroll/pestaña de cada pantalla al navegar (p. ej. al volver del reproductor).
    holder.SaveableStateProvider(screen.key) {
        when (screen) {
            is Screen.Accounts -> AccountsScreen(vm)
            is Screen.Browse -> BrowseScreen(vm)
            is Screen.SeriesPage -> SeriesScreen(vm, screen.series)
            is Screen.Player -> PlayerScreen(vm, screen.item)
        }
    }

    // El aviso de actualización no se muestra encima de un video.
    if (screen !is Screen.Player) UpdateDialog(vm)
}
