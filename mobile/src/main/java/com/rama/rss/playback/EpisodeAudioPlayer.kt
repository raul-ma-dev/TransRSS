package com.rama.rss.playback

import android.content.ComponentName
import android.text.Html
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.rama.rss.R
import com.rama.rss.reader.RssEntry
import com.rama.rss.ui.theme.RssTheme
import java.util.Locale
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun EpisodeAudioPlayer(entry: RssEntry, audioUrl: String, modifier: Modifier = Modifier) {
    val isPreview = LocalInspectionMode.current
    val context = LocalContext.current.applicationContext
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var connectionError by remember { mutableStateOf(false) }
    DisposableEffect(context, isPreview) {
        if (isPreview) return@DisposableEffect onDispose {}
        var disposed = false
        val future = MediaController.Builder(
            context, SessionToken(context, ComponentName(context, EpisodePlaybackService::class.java))
        ).setListener(object : MediaController.Listener {
            override fun onDisconnected(controller: MediaController) {
                if (!disposed) {
                    connectionError = true
                    Log.e("EpisodePlayback", "Playback controller disconnected")
                }
            }
        }).buildAsync()
        future.addListener({
            if (!disposed) {
                try {
                    controller = future.get()
                } catch (error: ExecutionException) {
                    connectionError = true
                    Log.e("EpisodePlayback", "Cannot connect to playback service", error)
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    connectionError = true
                    Log.e("EpisodePlayback", "Playback service connection interrupted", error)
                }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            MediaController.releaseFuture(future)
        }
    }
    val player = controller
    var state by remember(player, audioUrl, isPreview) {
        mutableStateOf(if (isPreview) EpisodePlayerState(current = true, position = 90_000, duration = 1_800_000) else player.stateFor(audioUrl))
    }
    DisposableEffect(player, audioUrl) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                state = player.stateFor(audioUrl)
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    LaunchedEffect(player, audioUrl, isPreview) {
        if (isPreview) return@LaunchedEffect
        while (isActive) {
            state = player.stateFor(audioUrl)
            delay(500)
        }
    }
    var seekPosition by remember(audioUrl) { mutableStateOf<Float?>(null) }
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.playback_title), style = MaterialTheme.typography.titleMedium)
            Row {
                TextButton(
                    enabled = (player != null || isPreview) && !connectionError,
                    onClick = {
                        if (player != null) {
                            if (state.current && state.playWhenReady) {
                                player.pause()
                            } else {
                                if (!state.current) {
                                    player.setMediaItem(
                                        MediaItem.Builder().setMediaId(audioUrl).setUri(audioUrl)
                                            .setMediaMetadata(
                                                MediaMetadata.Builder()
                                                    .setTitle(Html.fromHtml(entry.title, Html.FROM_HTML_MODE_COMPACT).toString())
                                                    .setArtist("En Caso De Que El Mundo Se Desintegre")
                                                    .build()
                                            ).build()
                                    )
                                }
                                if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                                player.play()
                            }
                        }
                    }
                ) {
                    Text(stringResource(if (state.current && state.playWhenReady) R.string.playback_pause else R.string.playback_play))
                }
                TextButton(
                    enabled = (player != null || isPreview) && state.current && !connectionError,
                    onClick = {
                        player?.stop()
                        player?.clearMediaItems()
                    }
                ) { Text(stringResource(R.string.playback_stop)) }
            }
            Slider(
                modifier = Modifier.semantics { contentDescription = context.getString(R.string.playback_progress) },
                value = seekPosition ?: state.position.toFloat().coerceIn(0f, state.duration.coerceAtLeast(1).toFloat()),
                valueRange = 0f..state.duration.coerceAtLeast(1).toFloat(),
                enabled = (player != null || isPreview) && state.current && state.duration > 0 && !connectionError,
                onValueChange = { seekPosition = it },
                onValueChangeFinished = {
                    seekPosition?.let { player?.seekTo(it.toLong()) }
                    seekPosition = null
                }
            )
            Text(
                "${playbackTime(state.position)} / ${playbackTime(state.duration)}",
                style = MaterialTheme.typography.labelMedium
            )
            if (state.buffering) Text(stringResource(R.string.playback_buffering))
            if (player == null && !connectionError && !isPreview) Text(stringResource(R.string.playback_connecting))
            if (connectionError || state.failed) {
                Text(
                    stringResource(if (connectionError) R.string.playback_connection_error else R.string.playback_error),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Preview(name = "Reproductor de episodios", group = "Artículos", showBackground = true, widthDp = 360)
@Composable
private fun EpisodeAudioPlayerPreview() {
    RssTheme(darkTheme = true, dynamicColor = false) {
        EpisodeAudioPlayer(
            RssEntry("Episodio de muestra", "", "", ""),
            "https://example.com/episode.mp3",
            Modifier.padding(16.dp)
        )
    }
}

private data class EpisodePlayerState(
    val current: Boolean = false,
    val playWhenReady: Boolean = false,
    val buffering: Boolean = false,
    val failed: Boolean = false,
    val position: Long = 0,
    val duration: Long = 0
)

private fun Player?.stateFor(mediaId: String): EpisodePlayerState {
    if (this == null || currentMediaItem?.mediaId != mediaId) return EpisodePlayerState()
    return EpisodePlayerState(
        current = true,
        playWhenReady = playWhenReady && playbackState != Player.STATE_ENDED,
        buffering = playbackState == Player.STATE_BUFFERING,
        failed = playerError != null,
        position = currentPosition.coerceAtLeast(0),
        duration = duration.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0) ?: 0
    )
}

private fun playbackTime(milliseconds: Long): String {
    val seconds = milliseconds / 1_000
    return if (seconds >= 3_600) {
        String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3_600, seconds / 60 % 60, seconds % 60)
    } else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}
