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
import androidx.media3.common.util.UnstableApi
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

    // Menú de opciones (Audio y subs, Velocidad, Canales, Imagen...): solo aparece al tocar el engranaje.
    var showMenu by remember { mutableStateOf(false) }
    var gearFound by remember { mutableStateOf(true) }
    // El menú se cierra cuando se ocultan los controles.
    LaunchedEffect(controlsVisible) { if (!controlsVisible) showMenu = false }

    // Enviar películas y episodios a una TV DLNA (la mayoría de LG son compatibles).
    val castScope = rememberCoroutineScope()
    val dlna = remember(context) { DlnaClient(context.applicationContext) }
    var showCast by remember { mutableStateOf(false) }
    var castBusy by remember { mutableStateOf(false) }
    var castDevice by remember { mutableStateOf<DlnaDevice?>(null) }
    var castResumeAt by remember { mutableStateOf(0L) }
    var castDurationMs by remember { mutableStateOf(0L) }

    // Tamaño de imagen y orientación (se conservan al cambiar de canal o capítulo).
    val videoMode = vm.videoMode.coerceIn(0, videoModes.lastIndex)
    val landscape = vm.playerLandscape

    // Player que ve la barra de controles: ⏭ pasa al capítulo o canal siguiente, y en vivo ⏮ al canal anterior.
    val controlsPlayer = remember(exoPlayer, nextEpisode, nextChannel, prevChannel, item) {
        val nextEp = nextEpisode
        val onNext: (() -> Unit)? = when {
            nextChannel != null -> ({ vm.switchChannel(nextChannel) })
            nextEp != null -> ({ vm.playNextEpisode(item, nextEp) })
            else -> null
        }
        val onPrev: (() -> Unit)? = prevChannel?.let { ch -> ({ vm.switchChannel(ch) }) }
        object : ForwardingPlayer(exoPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                val builder = super.getAvailableCommands().buildUpon()
                if (onNext != null) {
                    builder.add(Player.COMMAND_SEEK_TO_NEXT)
                    builder.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                } else {
                    builder.remove(Player.COMMAND_SEEK_TO_NEXT)
                    builder.remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                }
                if (onPrev != null) {
                    builder.add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    builder.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                }
                return builder.build()
            }

            override fun isCommandAvailable(command: Int): Boolean =
                getAvailableCommands().contains(command)

            override fun hasNextMediaItem(): Boolean = onNext != null

            override fun seekToNext() {
                onNext?.invoke()
            }

            override fun seekToNextMediaItem() = seekToNext()

            override fun hasPreviousMediaItem(): Boolean = onPrev != null || super.hasPreviousMediaItem()

            override fun seekToPrevious() {
                if (onPrev != null) onPrev.invoke() else super.seekToPrevious()
            }

            override fun seekToPreviousMediaItem() {
                if (onPrev != null) onPrev.invoke() else super.seekToPreviousMediaItem()
            }
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
            val position = exoPlayer.currentPosition
            // Si se está enviando a la TV, el reproductor local está detenido: no se pisa el avance guardado.
            if (castDevice == null && !castBusy && item.resumeKey != null &&
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
            val result = dlna.cast(device, currentUrl, item.title)
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
                    player = controlsPlayer
                    useController = true
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    setShowRewindButton(true)
                    setShowFastForwardButton(true)
                    // El engranaje de los controles abre/cierra nuestro menú en vez del menú predeterminado.
                    val gear = findViewById<View>(androidx.media3.ui.R.id.exo_settings)
                    if (gear != null) {
                        gear.setOnClickListener { showMenu = !showMenu }
                    } else {
                        gearFound = false
                    }
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
                view.resizeMode = videoModes[videoMode].resizeMode
                // Con el menú abierto los controles no se ocultan solos.
                view.controllerShowTimeoutMs = if (showMenu) 0 else 5000
                // Mientras la TV reproduce, el teléfono puede apagar la pantalla.
                view.keepScreenOn = castDevice == null
                // En la ventana pequeña no se muestran los controles de PlayerView.
                view.useController = !inPip && castDevice == null && !castBusy
                if (inPip || castDevice != null || castBusy) view.hideController()
            },
        )

        AnimatedVisibility(
            visible = controlsVisible && !inPip && castDevice == null && !castBusy,
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
            visible = controlsVisible && !inPip && castDevice == null && !castBusy && (showMenu || !gearFound),
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
                if (item.isLive && channels.size > 1) {
                    TextButton(onClick = { showChannels = true }) {
                        Text("Canales", color = Color.White)
                    }
                }
                TextButton(onClick = { vm.chooseVideoMode((videoMode + 1) % videoModes.size) }) {
                    Text("Imagen: ${videoModes[videoMode].label}", color = Color.White)
                }
                TextButton(onClick = { vm.playerLandscape = !vm.playerLandscape }) {
                    Text(if (landscape) "Vertical" else "Horizontal", color = Color.White)
                }
                if (!item.isLive) {
                    TextButton(
                        onClick = {
                            showMenu = false
                            showCast = true
                        },
                        enabled = !castBusy,
                    ) {
                        Text(if (castBusy) "Enviando…" else "Enviar a TV", color = Color.White)
                    }
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
            .background(Color(0xF2101018))
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

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
