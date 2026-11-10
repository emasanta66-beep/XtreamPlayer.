package app.xtreamplayer.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Rational
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.xtreamplayer.AppViewModel
import app.xtreamplayer.R
import app.xtreamplayer.data.DlnaClient
import app.xtreamplayer.data.DlnaDevice
import app.xtreamplayer.data.EpgItem
import app.xtreamplayer.data.Episode
import app.xtreamplayer.data.Http
import app.xtreamplayer.data.PlayItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: AppViewModel, item: PlayItem) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audio = remember(context) { context.getSystemService(AudioManager::class.java) }

    var error by remember { mutableStateOf<String?>(null) }
    var epg by remember { mutableStateOf<List<EpgItem>>(emptyList()) }
    var usingAlt by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
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
        // Si el celular no puede decodificar un audio, se usa FFmpeg (EXTENSION_RENDERER_MODE_ON);
        // y si un decodificador falla, se prueba otro en vez de quedarse sin sonido.
        val renderers = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                playWhenReady = true
                // Subtítulos: se activa solo el idioma que elegiste la última vez.
                when (val pref = vm.subtitleLang) {
                    null -> {}
                    "off" -> trackSelectionParameters = trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                    else -> trackSelectionParameters = trackSelectionParameters.buildUpon()
                        .setPreferredTextLanguage(pref)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
                }
            }
    }

    // Pistas de audio y subtítulos disponibles (se actualizan cuando el reproductor las descubre).
    var tracks by remember { mutableStateOf(exoPlayer.currentTracks) }
    val audioOptions = remember(tracks, context) { tracks.options(C.TRACK_TYPE_AUDIO, context) }
    val textOptions = remember(tracks, context) { tracks.options(C.TRACK_TYPE_TEXT, context) }

    // Capítulo siguiente (solo en series; null si es el último o si no es un episodio).
    var nextEpisode by remember { mutableStateOf<Episode?>(null) }
    LaunchedEffect(item.resumeKey) { nextEpisode = vm.nextEpisode(item) }

    // En vivo: canales de la misma categoría (para siguiente/anterior y para el panel "Canales").
    LaunchedEffect(item.isLive) { if (item.isLive) vm.ensureLive() }
    val channels = remember(vm.live.items, item.streamId) {
        if (item.isLive) vm.channelsInCategoryOf(item.streamId) else emptyList()
    }
    val channelIndex = channels.indexOfFirst { it.id == item.streamId }
    // Siguiente y anterior dan la vuelta: después del último canal viene el primero.
    val nextChannel = if (channelIndex >= 0 && channels.size > 1) channels[(channelIndex + 1) % channels.size] else null
    val prevChannel =
        if (channelIndex >= 0 && channels.size > 1) channels[(channelIndex - 1 + channels.size) % channels.size] else null
    var showChannels by remember { mutableStateOf(false) }

    // Menú ⋮ (arriba a la derecha), calidad y estado de los controles propios.
    var showMenu by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    var qualityAuto by remember { mutableStateOf(true) }
    var muted by remember {
        mutableStateOf(
            audio.isStreamMute(AudioManager.STREAM_MUSIC) ||
                audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0,
        )
    }
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var videoW by remember { mutableIntStateOf(0) }
    var videoH by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var seeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableStateOf(0f) }
    // Aviso de ±10 s que aparece al hacer doble toque (0 = oculto).
    var seekFlash by remember { mutableStateOf(0L) }
    var pokes by remember { mutableIntStateOf(0) }
    // Gestos verticales: mitad izquierda = brillo, mitad derecha = volumen.
    var brightness by remember {
        mutableStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f)
    }
    var gestureLabel by remember { mutableStateOf("") }
    var gestureFraction by remember { mutableStateOf(0f) }
    var gestureVisible by remember { mutableStateOf(false) }
    fun poke() {
        pokes++
    }
    val qualityOptions = remember(tracks, context) { tracks.qualityOptions(context) }

    // D-pad / mando de TV: el reproductor recibe las teclas cuando no hay un botón con el foco.
    val playerFocus = remember { FocusRequester() }
    val topBarFocus = remember { FocusRequester() }
    val bottomBarFocus = remember { FocusRequester() }
    var playerFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { playerFocus.requestFocus() }
    }
    // Al ocultarse los controles, el foco vuelve al reproductor para que el D-pad siga funcionando.
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) runCatching { playerFocus.requestFocus() }
    }

    // Enviar películas y episodios a una TV DLNA (la mayoría de LG son compatibles).
    val castScope = rememberCoroutineScope()
    val dlna = remember(context) { DlnaClient(context.applicationContext) }
    var showCast by remember { mutableStateOf(false) }
    var castBusy by remember { mutableStateOf(false) }
    var castDevice by remember { mutableStateOf<DlnaDevice?>(null) }
    var castResumeAt by remember { mutableStateOf(0L) }
    var castDurationMs by remember { mutableStateOf(0L) }

    // Valores actuales para cuando se cierra el reproductor: el DisposableEffect no se
    // recrea al entrar o salir de la TV, así que no debe quedarse con el valor inicial.
    val castDeviceNow by rememberUpdatedState(castDevice)
    val castBusyNow by rememberUpdatedState(castBusy)

    // Tamaño de imagen y orientación (se conservan al cambiar de canal o capítulo).
    val videoMode = vm.videoMode.coerceIn(0, videoModes.lastIndex)
    val landscape = vm.playerLandscape

    // El botón ⏭ pasa al canal o capítulo siguiente; en vivo, ⏮ al canal anterior.
    fun seekBy(deltaMs: Long) {
        val d = exoPlayer.duration
        val t = exoPlayer.currentPosition + deltaMs
        exoPlayer.seekTo(if (d != C.TIME_UNSET && d > 0) t.coerceIn(0L, d) else t.coerceAtLeast(0L))
        poke()
    }

    fun togglePlay() {
        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun showControls() {
        controlsVisible = true
        poke()
    }

    fun playNext() {
        nextChannel?.let { vm.switchChannel(it) } ?: nextEpisode?.let { vm.playNextEpisode(item, it) }
    }

    /** Brillo de la ventana (0.05..1). No toca el brillo del sistema: solo dura mientras se ve el reproductor. */
    fun applyBrightness(value: Float) {
        brightness = value.coerceIn(0.05f, 1f)
        activity?.window?.let { window ->
            val attributes = window.attributes
            attributes.screenBrightness = brightness
            window.attributes = attributes
        }
    }

    /** Volumen multimedia del sistema (0..1), el mismo que mueven los botones físicos. */
    fun musicVolume(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    /** Ajusta el volumen multimedia del sistema; a cero se marca como silenciado. */
    fun applyVolume(value: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val index = (value.coerceIn(0f, 1f) * max).roundToInt()
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
        muted = index == 0
    }

    // Mantiene el icono de silencio al día aunque el volumen se cambie con los botones físicos.
    DisposableEffect(audio) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                muted = audio.isStreamMute(AudioManager.STREAM_MUSIC) ||
                    audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    // Controles propios: se ocultan solos a los 4 s mientras se reproduce.
    LaunchedEffect(controlsVisible, isPlaying, showMenu, showTracks, showQuality, pokes) {
        if (controlsVisible && isPlaying && !showMenu && !showTracks && !showQuality) {
            delay(4000)
            controlsVisible = false
        }
    }
    val showOverlay = controlsVisible && !inPip && castDevice == null && !castBusy
    // El aviso de ±10 s desaparece solo.
    LaunchedEffect(seekFlash) {
        if (seekFlash != 0L) {
            delay(750)
            seekFlash = 0L
        }
    }
    // El aviso de brillo/volumen también se oculta solo.
    LaunchedEffect(gestureVisible, gestureLabel) {
        if (gestureVisible) {
            delay(900)
            gestureVisible = false
        }
    }
    LaunchedEffect(showOverlay) {
        if (!showOverlay) return@LaunchedEffect
        while (true) {
            if (!seeking) positionMs = exoPlayer.currentPosition
            val d = exoPlayer.duration
            durationMs = if (d == C.TIME_UNSET) 0L else d
            delay(500)
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

    // Al terminar un capítulo pasa solo al siguiente. Si todavía no se conocía cuál es
    // (por ejemplo, falló la carga de la serie), lo busca en ese momento.
    var advancing by remember { mutableStateOf(false) }
    fun advanceToNextEpisode() {
        if (advancing || item.isLive) return
        advancing = true
        castScope.launch {
            val next = nextEpisode ?: vm.nextEpisode(item)
            val opened = next != null && vm.playNextEpisode(item, next)
            if (!opened) advancing = false
        }
    }

    // Errores: primero se prueba el formato alternativo (en vivo), luego se muestra el error.
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED) advanceToNextEpisode()
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                videoW = size.width
                videoH = size.height
            }

            override fun onPlayerError(e: PlaybackException) {
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    exoPlayer.seekToDefaultPosition()
                    exoPlayer.prepare()
                    return
                }
                // Algunos servidores cortan la conexión justo al final del archivo: si ya casi
                // terminaba y hay capítulo siguiente, se sigue en vez de mostrar un error.
                val total = exoPlayer.duration
                if (!item.isLive && nextEpisode != null && total != C.TIME_UNSET && total > 0 &&
                    exoPlayer.currentPosition >= total - 15_000
                ) {
                    advanceToNextEpisode()
                    return
                }
                if (item.isLive && item.altUrl != null && !usingAlt) {
                    usingAlt = true
                } else {
                    error = context.getString(R.string.player_play_error, e.errorCodeName)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            val duration = exoPlayer.duration
            val position = exoPlayer.currentPosition
            // Si se está enviando a la TV, el reproductor local está detenido: no se pisa el avance guardado.
            if (castDeviceNow == null && !castBusyNow && item.resumeKey != null &&
                duration != C.TIME_UNSET && duration > 0 && position > 0
            ) {
                vm.saveProgress(item, position, duration)
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
            // El brillo ajustado con el gesto solo dura mientras se ve el reproductor.
            activity?.window?.let { window ->
                val attributes = window.attributes
                attributes.screenBrightness = -1f
                window.attributes = attributes
            }
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

    fun sendToTv(device: DlnaDevice) {
        if (castBusy) return
        castBusy = true

        val startAt = exoPlayer.currentPosition.takeIf { it > 0 } ?: item.startPositionMs
        val duration = exoPlayer.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
        castResumeAt = startAt
        castDurationMs = duration

        // Se detiene el reproductor local para liberar la conexión con el servidor
        // (muchas listas permiten una sola conexión) antes de que la TV abra el archivo.
        exoPlayer.stop()
        if (item.resumeKey != null && duration > 0) {
            vm.saveProgress(item, startAt, duration)
        }

        castScope.launch {
            val result = dlna.cast(device, item.remoteUrl ?: currentUrl, item.title)
            castBusy = false
            result.onSuccess {
                castDevice = device
                Toast.makeText(
                    context,
                    context.getString(R.string.player_playing_on, device.name),
                    Toast.LENGTH_LONG,
                ).show()
                // La TV no avisa a Android del minuto que lleva: se le pregunta cada 30 s.
                castScope.launch {
                    while (castDevice?.controlUrl == device.controlUrl) {
                        delay(30_000)
                        val info = dlna.position(device).getOrNull() ?: continue
                        if (info.positionMs > 0) {
                            val total = if (info.durationMs > 0) info.durationMs else castDurationMs
                            if (total > 0) vm.saveProgress(item, info.positionMs, total)
                        }
                    }
                }
            }.onFailure {
                // Si la TV falla, se vuelve a cargar en el teléfono en el mismo punto.
                exoPlayer.setMediaItem(
                    MediaItem.fromUri(currentUrl),
                    if (startAt > 0) startAt else C.TIME_UNSET,
                )
                exoPlayer.prepare()
                exoPlayer.play()
                Toast.makeText(
                    context,
                    context.getString(R.string.player_cast_error),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    fun stopCasting() {
        val device = castDevice ?: return
        castDevice = null
        castScope.launch {
            val info = dlna.position(device).getOrNull()
            dlna.stop(device).onFailure {
                Toast.makeText(
                    context,
                    context.getString(R.string.player_cast_stop_error),
                    Toast.LENGTH_LONG,
                ).show()
            }
            val start = info?.positionMs?.takeIf { it > 0 } ?: castResumeAt
            if (info != null && info.positionMs > 0) {
                val total = if (info.durationMs > 0) info.durationMs else castDurationMs
                if (total > 0) vm.saveProgress(item, info.positionMs, total)
            }
            exoPlayer.setMediaItem(
                MediaItem.fromUri(currentUrl),
                if (start > 0) start else C.TIME_UNSET,
            )
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Atajos de D-pad y de mando de TV (OK, flechas, play/pausa, siguiente/anterior).
            // Va en la raíz para que también funcionen con el foco en un botón de las barras.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Los diálogos y el panel de canales navegan con sus propias teclas.
                if (showMenu || showTracks || showQuality || showChannels || showCast) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        togglePlay()
                        true
                    }
                    Key.MediaNext -> {
                        if (nextChannel == null && nextEpisode == null) false else {
                            playNext()
                            true
                        }
                    }
                    Key.MediaPrevious -> {
                        val prev = prevChannel
                        if (prev == null) false else {
                            vm.switchChannel(prev)
                            true
                        }
                    }
                    Key.Menu -> {
                        showMenu = true
                        true
                    }
                    else -> {
                        // Con los controles visibles y un botón enfocado, las flechas navegan por los botones.
                        if (controlsVisible && !playerFocused) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                                if (controlsVisible) togglePlay() else showControls()
                                true
                            }
                            Key.DirectionLeft -> {
                                showControls()
                                if (!item.isLive) {
                                    seekBy(-10_000L)
                                    seekFlash = -10_000L
                                }
                                true
                            }
                            Key.DirectionRight -> {
                                showControls()
                                if (!item.isLive) {
                                    seekBy(10_000L)
                                    seekFlash = 10_000L
                                }
                                true
                            }
                            Key.DirectionUp -> {
                                if (controlsVisible) {
                                    runCatching { topBarFocus.requestFocus() }
                                } else {
                                    showControls()
                                }
                                true
                            }
                            Key.DirectionDown -> {
                                if (controlsVisible) {
                                    runCatching { bottomBarFocus.requestFocus() }
                                } else {
                                    showControls()
                                }
                                true
                            }
                            else -> false
                        }
                    }
                }
            }
            .pointerInput(Unit) {} // evita que los toques lleguen a la pantalla de debajo
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // los controles son propios (más abajo)
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            update = { view ->
                view.resizeMode = videoModes[videoMode].resizeMode
                // Mientras la TV reproduce, el teléfono puede apagar la pantalla.
                view.keepScreenOn = castDevice == null
            },
        )

        // Un toque muestra u oculta los controles; doble toque a los lados salta 10 s.
        if (!inPip && castDevice == null && !castBusy) {
            Box(
                Modifier
                    .fillMaxSize()
                    .focusRequester(playerFocus)
                    .onFocusChanged { playerFocused = it.isFocused }
                    .focusable()
                    // Arrastrar en vertical: mitad izquierda brillo, mitad derecha volumen.
                    .pointerInput(Unit) {
                        var mode = 0
                        // El volumen se cuantiza por pasos (1/15); se acumula en continuo para no perder el gesto.
                        var startVolume = 0f
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                mode = if (offset.x < size.width / 2f) 1 else 2
                                if (mode == 2) startVolume = musicVolume()
                            },
                            onDragEnd = { gestureVisible = false },
                            onDragCancel = { gestureVisible = false },
                        ) { change, dragAmount ->
                            change.consume()
                            val delta = -dragAmount / size.height.toFloat()
                            when (mode) {
                                1 -> {
                                    applyBrightness(brightness + delta)
                                    gestureLabel = context.getString(
                                        R.string.player_brightness,
                                        (brightness * 100).roundToInt(),
                                    )
                                    gestureFraction = brightness
                                }
                                2 -> {
                                    startVolume = (startVolume + delta).coerceIn(0f, 1f)
                                    applyVolume(startVolume)
                                    gestureLabel = context.getString(
                                        R.string.player_volume,
                                        (startVolume * 100).roundToInt(),
                                    )
                                    gestureFraction = startVolume
                                }
                            }
                            if (mode != 0) gestureVisible = true
                        }
                    }
                    .pointerInput(item.isLive) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = { o ->
                                if (!item.isLive) {
                                    val delta = if (o.x < size.width / 2f) -10_000L else 10_000L
                                    seekBy(delta)
                                    seekFlash = delta
                                }
                            },
                        )
                    }
            )
        }

        PlayerBuffering(
            visible = buffering && error == null && showOverlayOrPlaying(inPip, castDevice != null, castBusy),
            modifier = Modifier.align(Alignment.Center),
        )

        // Aviso rápido de −10 s / +10 s al hacer doble toque.
        PlayerSeekFlash(
            deltaMs = seekFlash,
            modifier = Modifier.align(Alignment.Center),
        )

        // Nivel de brillo/volumen al arrastrar en vertical.
        PlayerGestureHint(
            visible = gestureVisible,
            label = gestureLabel,
            fraction = gestureFraction,
            modifier = Modifier.align(Alignment.Center),
        )

        // Barra superior: volver, canales (en vivo), título, resolución, información y menú.
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            PlayerTopBar(
                title = item.title,
                isLive = item.isLive,
                hasChannels = channels.size > 1,
                epg = epg,
                timeFormat = timeFormat,
                videoHeight = videoH,
                videoModeLabel = stringResource(videoModes[videoMode].labelRes),
                audioAvailable = audioOptions.size > 1 || textOptions.isNotEmpty(),
                casting = castBusy,
                menuOpen = showMenu,
                onMenuOpenChange = { showMenu = it },
                onBack = { vm.back() },
                onChannels = { showChannels = true },
                onQuality = { showQuality = true },
                onTracks = { showTracks = true },
                onCycleVideoMode = { vm.chooseVideoMode((videoMode + 1) % videoModes.size) },
                onCast = { showCast = true },
                onPoke = { poke() },
                backFocus = topBarFocus,
            )
        }

        // Barra inferior: pausa, ±10 s, tiempo, volumen, subtítulos, velocidad, PiP y orientación.
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerBottomBar(
                isLive = item.isLive,
                isPlaying = isPlaying,
                hasPrevChannel = prevChannel != null,
                hasNextChannel = nextChannel != null,
                hasNextEpisode = nextEpisode != null,
                hasTracks = audioOptions.size > 1 || textOptions.isNotEmpty(),
                supportsPip = supportsPip,
                muted = muted,
                positionMs = positionMs,
                durationMs = durationMs,
                seeking = seeking,
                seekValue = seekValue,
                speedLabel = speedLabels[speedIndex],
                onPrevChannel = { prevChannel?.let { vm.switchChannel(it) } },
                onNext = {
                    nextChannel?.let { vm.switchChannel(it) }
                        ?: nextEpisode?.let { vm.playNextEpisode(item, it) }
                },
                onPlayPause = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                onSeek = { seekBy(it) },
                onSeekChange = {
                    seeking = true
                    seekValue = it
                },
                onSeekFinished = {
                    exoPlayer.seekTo(seekValue.toLong())
                    positionMs = seekValue.toLong()
                    seeking = false
                },
                onMute = {
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
                    muted = audio.isStreamMute(AudioManager.STREAM_MUSIC) ||
                        audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
                },
                onTracks = { showTracks = true },
                onCycleSpeed = { speedIndex = (speedIndex + 1) % speeds.size },
                onPip = { enterPip() },
                onPoke = { poke() },
                playPauseFocus = bottomBarFocus,
            )
        }

        if (showQuality && !inPip) {
            val vf = exoPlayer.videoFormat
            val info = if (vf == null || vf.height <= 0) {
                stringResource(R.string.player_loading_info)
            } else {
                buildString {
                    append("${vf.width}×${vf.height} (${resolutionLabel(vf.height)})")
                    if (vf.frameRate > 0f) append(" · ${vf.frameRate.toInt()} fps")
                    vf.sampleMimeType?.substringAfter('/')?.let { append(" · ${it.uppercase(Locale.ROOT)}") }
                    if (vf.bitrate > 0) append(" · %.1f Mbps".format(vf.bitrate / 1_000_000f))
                }
            }
            QualityDialog(
                info = info,
                options = qualityOptions,
                auto = qualityAuto,
                onAuto = {
                    qualityAuto = true
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                        .build()
                },
                onPick = { o ->
                    qualityAuto = false
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(TrackSelectionOverride(o.group.mediaTrackGroup, o.index))
                        .build()
                },
                onDismiss = { showQuality = false },
            )
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
                        vm.subtitleLang = "off"
                    } else {
                        o.group.getTrackFormat(o.index).language
                            ?.takeIf { it.isNotBlank() && it != "und" }
                            ?.let { vm.subtitleLang = it }
                        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(TrackSelectionOverride(o.group.mediaTrackGroup, o.index))
                    }
                    exoPlayer.trackSelectionParameters = builder.build()
                },
                onDismiss = { showTracks = false },
            )
        }

        // Panel lateral con los canales de la misma categoría: tocar uno lo reproduce.
        if (showChannels && !inPip && channels.isNotEmpty()) {
            BackHandler { showChannels = false }
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { showChannels = false } }
            )
            ChannelPanel(
                title = vm.liveCategoryName(channels.first().categoryId)
                    ?: stringResource(R.string.player_channels),
                channels = channels,
                currentId = item.streamId,
                onPick = { ch -> vm.switchChannel(ch) },
                onClose = { showChannels = false },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        if (!inPip) {
            error?.let { message ->
                PlayerErrorCard(
                    message = message,
                    onRetry = {
                        usingAlt = false
                        attempt++
                    },
                    onBack = { vm.back() },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        if (showCast) {
            CastDialog(
                title = item.title,
                onDismiss = { showCast = false },
                onCast = { device -> sendToTv(device) },
            )
        }

        if (castBusy && !inPip) {
            PlayerCastBusyCard(modifier = Modifier.align(Alignment.Center))
        }

        // Mientras la TV reproduce, el teléfono queda como mando: se oculta el video local.
        castDevice?.let { device ->
            if (!inPip) {
                PlayerCastingCard(
                    deviceName = device.name,
                    onStop = { stopCasting() },
                    onBack = { vm.back() },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

/** Tamaños de imagen del reproductor; el botón "Imagen" recorre esta lista. */
private class VideoMode(@androidx.annotation.StringRes val labelRes: Int, val resizeMode: Int)

private val videoModes = listOf(
    VideoMode(R.string.player_image_original, AspectRatioFrameLayout.RESIZE_MODE_FIT), // todo el video, con barras si hace falta
    VideoMode(R.string.player_image_zoom, AspectRatioFrameLayout.RESIZE_MODE_ZOOM), // llena la pantalla recortando los bordes
    VideoMode(R.string.player_image_stretch, AspectRatioFrameLayout.RESIZE_MODE_FILL), // llena la pantalla deformando la imagen
    VideoMode(R.string.player_image_width, AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH),
    VideoMode(R.string.player_image_height, AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT),
)

/** Verdadero cuando tiene sentido mostrar buffering: ni en PiP ni enviando a la TV. */
private fun showOverlayOrPlaying(inPip: Boolean, casting: Boolean, castBusy: Boolean) =
    !inPip && !casting && !castBusy

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
