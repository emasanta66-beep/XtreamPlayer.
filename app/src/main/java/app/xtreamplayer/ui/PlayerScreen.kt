package app.xtreamplayer.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.util.Rational
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.xtreamplayer.AppViewModel
import app.xtreamplayer.data.EpgItem
import app.xtreamplayer.data.Episode
import app.xtreamplayer.data.Http
import app.xtreamplayer.data.PlayItem
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: AppViewModel, item: PlayItem) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var error by remember { mutableStateOf<String?>(null) }
    var epg by remember { mutableStateOf<List<EpgItem>>(emptyList()) }
    var usingAlt by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    var landscape by remember { mutableStateOf(true) }
    var showTracks by remember { mutableStateOf(false) }
    var speedIndex by remember { mutableIntStateOf(2) }
    val componentActivity = remember(activity) { activity as? ComponentActivity }
    var inPip by remember { mutableStateOf(componentActivity?.isInPictureInPictureMode == true) }
    val supportsPip = remember(activity) {
        activity?.packageManager?.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) == true
    }
    val speeds = remember { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f) }
    val speedLabels = remember { listOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x") }

    val exoPlayer = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Http.UA)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply { playWhenReady = true }
    }

    // Pistas de audio y subtítulos disponibles (se actualizan cuando el reproductor las descubre).
    var tracks by remember { mutableStateOf(exoPlayer.currentTracks) }
    val audioOptions = remember(tracks) { tracks.options(C.TRACK_TYPE_AUDIO) }
    val textOptions = remember(tracks) { tracks.options(C.TRACK_TYPE_TEXT) }

    // Capítulo siguiente (solo en series; null si es el último o si no es un episodio).
    var nextEpisode by remember { mutableStateOf<Episode?>(null) }
    LaunchedEffect(item.resumeKey) { nextEpisode = vm.nextEpisode(item) }

    // Imagen: "Original" ajusta el video entero a la pantalla; "Ampliada" la llena recortando los bordes.
    var zoomed by remember { mutableStateOf(false) }

    // Player que ve la barra de controles: el botón ⏭ se activa si hay capítulo siguiente y lo reproduce.
    val controlsPlayer = remember(exoPlayer, nextEpisode, item) {
        val next = nextEpisode
        object : ForwardingPlayer(exoPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                val builder = super.getAvailableCommands().buildUpon()
                if (next != null) {
                    builder.add(Player.COMMAND_SEEK_TO_NEXT)
                    builder.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                } else {
                    builder.remove(Player.COMMAND_SEEK_TO_NEXT)
                    builder.remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                }
                return builder.build()
            }

            override fun isCommandAvailable(command: Int): Boolean =
                getAvailableCommands().contains(command)

            override fun hasNextMediaItem(): Boolean = next != null

            override fun seekToNext() {
                if (next != null) vm.playNextEpisode(item, next)
            }

            override fun seekToNextMediaItem() = seekToNext()
        }
    }

    // Velocidad de reproducción (botón "Velocidad ...").
    LaunchedEffect(speedIndex) { exoPlayer.setPlaybackSpeed(speeds[speedIndex]) }
    // Al entrar en picture-in-picture se cierra el diálogo de pistas.
    LaunchedEffect(inPip) { if (inPip) showTracks = false }

    // Carga (o recarga) el contenido cuando cambia la URL o se pulsa "Reintentar".
    val currentUrl = if (usingAlt) item.altUrl ?: item.url else item.url
    LaunchedEffect(currentUrl, attempt) {
        error = null
        val start = if (item.isLive || attempt > 0) 0L else item.startPositionMs
        exoPlayer.setMediaItem(MediaItem.fromUri(currentUrl), if (start > 0) start else C.TIME_UNSET)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    // Errores: primero se prueba el formato alternativo (en vivo), luego se muestra el error.
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }

            override fun onPlayerError(e: PlaybackException) {
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    exoPlayer.seekToDefaultPosition()
                    exoPlayer.prepare()
                    return
                }
                if (item.isLive && item.altUrl != null && !usingAlt) {
                    usingAlt = true
                } else {
                    error = "No se pudo reproducir este contenido (${e.errorCodeName})."
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            val duration = exoPlayer.duration
            if (item.resumeKey != null && duration != C.TIME_UNSET && duration > 0) {
                vm.saveProgress(item, exoPlayer.currentPosition, duration)
            }
            exoPlayer.release()
        }
    }

    // Pausa al salir de la app, salvo que se haya quedado en picture-in-picture.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && activity?.isInPictureInPictureMode != true) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Sigue el estado de picture-in-picture para ocultar los controles propios.
    DisposableEffect(componentActivity) {
        val act = componentActivity
        if (act == null) {
            onDispose { }
        } else {
            val observer = Consumer<PictureInPictureModeChangedInfo> { info ->
                inPip = info.isInPictureInPictureMode
            }
            act.addOnPictureInPictureModeChangedListener(observer)
            onDispose { act.removeOnPictureInPictureModeChangedListener(observer) }
        }
    }

    // Pantalla completa mientras se reproduce.
    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(landscape) {
        activity?.requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    // Guía de programación (solo canales en vivo), refrescada cada 2 minutos.
    LaunchedEffect(item.streamId) {
        val id = item.streamId
        if (id != null) {
            while (true) {
                epg = vm.epgFor(id)
                delay(120_000)
            }
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    fun enterPip() {
        val act = activity ?: return
        if (!supportsPip || act.isInPictureInPictureMode) return
        val size = exoPlayer.videoSize
        val ratio = if (size.width > 0 && size.height > 0) {
            Rational(size.width, size.height)
        } else {
            Rational(16, 9)
        }
        act.enterPictureInPictureMode(
            PictureInPictureParams.Builder().setAspectRatio(ratio).build()
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {} // evita que los toques lleguen a la pantalla de debajo
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = controlsPlayer
                    useController = true
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    setShowRewindButton(true)
                    setShowFastForwardButton(true)
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == View.VISIBLE
                        }
                    )
                }
            },
            update = { view ->
                // Al cambiar el player de controles (por ejemplo cuando se conoce el capítulo siguiente) se refresca la barra.
                if (view.player !== controlsPlayer) view.player = controlsPlayer
                view.resizeMode = if (zoomed) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                // En la ventana pequeña no se muestran los controles de PlayerView.
                view.useController = !inPip
                if (inPip) view.hideController()
            },
        )

        AnimatedVisibility(
            visible = controlsVisible && !inPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .displayCutoutPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                IconButton(onClick = { vm.back() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
                Column(Modifier.weight(1f).padding(top = 6.dp)) {
                    Text(
                        item.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val now = System.currentTimeMillis()
                    val current = epg.firstOrNull { now in it.start..it.end } ?: epg.firstOrNull()
                    val next = current?.let { c -> epg.firstOrNull { it.start >= c.end } }
                    if (current != null) {
                        Text(
                            "Ahora: ${current.title} (${timeFormat.format(current.start)}–${timeFormat.format(current.end)})",
                            color = Color(0xCCFFFFFF),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (next != null) {
                        Text(
                            "Después: ${next.title} (${timeFormat.format(next.start)})",
                            color = Color(0x99FFFFFF),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Acciones del reproductor, encima de la barra de progreso de PlayerView.
        AnimatedVisibility(
            visible = controlsVisible && !inPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 8.dp, end = 8.dp, top = 28.dp, bottom = 60.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (audioOptions.size > 1 || textOptions.isNotEmpty()) {
                    TextButton(onClick = { showTracks = true }) {
                        Text("Audio y subs", color = Color.White)
                    }
                }
                TextButton(onClick = { speedIndex = (speedIndex + 1) % speeds.size }) {
                    Text("Velocidad ${speedLabels[speedIndex]}", color = Color.White)
                }
                TextButton(onClick = { zoomed = !zoomed }) {
                    Text(if (zoomed) "Imagen: Ampliada" else "Imagen: Original", color = Color.White)
                }
                TextButton(onClick = { landscape = !landscape }) {
                    Text(if (landscape) "Vertical" else "Horizontal", color = Color.White)
                }
                if (supportsPip) {
                    TextButton(onClick = { enterPip() }) {
                        Text("PiP", color = Color.White)
                    }
                }
            }
        }

        if (showTracks && !inPip) {
            TrackDialog(
                audio = if (audioOptions.size > 1) audioOptions else emptyList(),
                text = textOptions,
                onAudio = { o ->
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(TrackSelectionOverride(o.group.mediaTrackGroup, o.index))
                        .build()
                },
                onText = { o ->
                    val builder = exoPlayer.trackSelectionParameters.buildUpon()
                    if (o == null) {
                        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    } else {
                        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(TrackSelectionOverride(o.group.mediaTrackGroup, o.index))
                    }
                    exoPlayer.trackSelectionParameters = builder.build()
                },
                onDismiss = { showTracks = false },
            )
        }

        if (!inPip) {
            error?.let { message ->
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color(0xE6000000), RoundedCornerShape(12.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(message, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = {
                            usingAlt = false
                            attempt++
                        }) { Text("Reintentar") }
                        OutlinedButton(onClick = { vm.back() }) { Text("Volver") }
                    }
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
