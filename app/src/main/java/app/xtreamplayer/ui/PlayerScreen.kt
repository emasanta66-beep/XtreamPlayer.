package app.xtreamplayer.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.util.Rational
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import app.xtreamplayer.data.Channel
import app.xtreamplayer.data.DlnaClient
import app.xtreamplayer.data.DlnaDevice
import app.xtreamplayer.data.EpgItem
import app.xtreamplayer.data.Episode
import app.xtreamplayer.data.Http
import app.xtreamplayer.data.PlayItem
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val audioOptions = remember(tracks) { tracks.options(C.TRACK_TYPE_AUDIO) }
    val textOptions = remember(tracks) { tracks.options(C.TRACK_TYPE_TEXT) }

    // Capítulo siguiente (solo en series; null si es el último o si no es un episodio).
    var nextEpisode by remember { mutableStateOf<Episode?>(null) }
    LaunchedEffect(item.resumeKey) { nextEpisode = vm.nextEpisode(item) }

    // En vivo: canales de la misma categoría (para ⏭/⏮ y para el panel "Canales").
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
    var muted by remember { mutableStateOf(false) }
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
    fun poke() {
        pokes++
    }
    val qualityOptions = remember(tracks) { tracks.qualityOptions() }

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

    // ⏭ pasa al canal o capítulo siguiente; en vivo, ⏮ al canal anterior.
    fun seekBy(deltaMs: Long) {
        val d = exoPlayer.duration
        val t = exoPlayer.currentPosition + deltaMs
        exoPlayer.seekTo(if (d != C.TIME_UNSET && d > 0) t.coerceIn(0L, d) else t.coerceAtLeast(0L))
        poke()
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
                    error = "No se pudo reproducir este contenido (${e.errorCodeName})."
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
                Toast.makeText(context, "Reproduciendo en ${device.name}", Toast.LENGTH_LONG).show()
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
                    "No se pudo enviar a la TV. Comprueba que esté encendida y en la misma Wi-Fi.",
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
                Toast.makeText(context, "No se pudo detener la TV; usa su mando.", Toast.LENGTH_LONG).show()
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

        if (buffering && error == null && showOverlayOrPlaying(inPip, castDevice != null, castBusy)) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(44.dp),
                color = Color.White,
                strokeWidth = 3.dp,
            )
        }

        // Aviso rápido de −10 s / +10 s al hacer doble toque.
        AnimatedVisibility(
            visible = seekFlash != 0L,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Palette.Scrim)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (seekFlash < 0) "−10 s" else "+10 s",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Barra superior: volver, canales (en vivo), título, resolución, información y menú ⋮.
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
                videoModeLabel = videoModes[videoMode].label,
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
                    muted = !muted
                    exoPlayer.volume = if (muted) 0f else 1f
                },
                onTracks = { showTracks = true },
                onCycleSpeed = { speedIndex = (speedIndex + 1) % speeds.size },
                onPip = { enterPip() },
                onPoke = { poke() },
            )
        }

        if (showQuality && !inPip) {
            val vf = exoPlayer.videoFormat
            val info = if (vf == null || vf.height <= 0) {
                "Cargando información del video…"
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
                title = vm.liveCategoryName(channels.first().categoryId) ?: "Canales",
                channels = channels,
                currentId = item.streamId,
                onPick = { ch -> vm.switchChannel(ch) },
                onClose = { showChannels = false },
                modifier = Modifier.align(Alignment.CenterEnd),
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

        if (showCast) {
            CastDialog(
                title = item.title,
                onDismiss = { showCast = false },
                onCast = { device -> sendToTv(device) },
            )
        }

        if (castBusy && !inPip) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xE6000000), RoundedCornerShape(12.dp))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                )
                Spacer(Modifier.height(12.dp))
                Text("Enviando a TV…", color = Color.White)
            }
        }

        // Mientras la TV reproduce, el teléfono queda como mando: se oculta el video local.
        castDevice?.let { device ->
            if (!inPip) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xF2000000))
                        .displayCutoutPadding()
                        .pointerInput(Unit) { detectTapGestures { } }
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "Reproduciendo en TV",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        device.name,
                        color = Color(0xFFB3E5FC),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Usa el mando de la TV para pausar o cambiar el volumen. Si vuelves atrás, la TV seguirá reproduciendo.",
                        color = Color(0xCCFFFFFF),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { stopCasting() }) { Text("Detener TV") }
                        OutlinedButton(onClick = { vm.back() }) { Text("Volver") }
                    }
                }
            }
        }
    }
}

/** Tamaños de imagen del reproductor; el botón "Imagen" recorre esta lista. */
private class VideoMode(val label: String, val resizeMode: Int)

private val videoModes = listOf(
    VideoMode("Original", AspectRatioFrameLayout.RESIZE_MODE_FIT), // todo el video, con barras si hace falta
    VideoMode("Ampliar", AspectRatioFrameLayout.RESIZE_MODE_ZOOM), // llena la pantalla recortando los bordes
    VideoMode("Estirar", AspectRatioFrameLayout.RESIZE_MODE_FILL), // llena la pantalla deformando la imagen
    VideoMode("Al ancho", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH),
    VideoMode("Al alto", AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT),
)

@Composable
private fun ChannelPanel(
    title: String,
    channels: List<Channel>,
    currentId: Int?,
    onPick: (Channel) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val index = channels.indexOfFirst { it.id == currentId }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (index - 2).coerceAtLeast(0))
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(Palette.Surface)
            .displayCutoutPadding()
            .pointerInput(Unit) {} // los toques dentro del panel no lo cierran
            .padding(top = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(channels, key = { it.id }) { ch ->
                val current = ch.id == currentId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (current) Color(0x33FFFFFF) else Color.Transparent)
                        .clickable { if (current) onClose() else onPick(ch) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = ch.icon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        ch.name,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (current) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Reproduciendo", tint = Color.White)
                    }
                }
            }
        }
    }
}

/** Botón redondo de la barra de controles. */
@Composable
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
