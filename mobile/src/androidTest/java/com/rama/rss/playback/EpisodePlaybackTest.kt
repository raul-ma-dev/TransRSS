package com.rama.rss.playback

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSession
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rama.rss.MainActivity
import com.rama.rss.R
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpisodePlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun serviceKeepsPlayingInBackgroundWithScreenOffAndNotificationControlsWork() {
        val audio = createPlaybackTestAudio(context)
        val power = context.getSystemService(PowerManager::class.java)
        val notifications = context.getSystemService(NotificationManager::class.java)
        val wasInteractive = power.isInteractive
        var controller: MediaController? = null
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                controller = connect()
                val player = requireNotNull(controller)
                instrumentation.runOnMainSync {
                    player.setMediaItem(
                        MediaItem.Builder().setMediaId("background-test").setUri(Uri.fromFile(audio))
                            .setMediaMetadata(MediaMetadata.Builder().setTitle("Prueba de segundo plano").build())
                            .build()
                    )
                    player.prepare()
                    player.play()
                }
                await("El servicio debe reproducir el audio") { player.isPlaying && player.currentPosition > 0 }
                await("Debe publicarse una notificación multimedia") { playbackNotification(notifications) != null }
                scenario.moveToState(Lifecycle.State.CREATED)
                var previousPosition = 0L
                instrumentation.runOnMainSync { previousPosition = player.currentPosition }
                await("El audio debe avanzar con la actividad detenida") { player.currentPosition > previousPosition + 500 }
                shell("input keyevent KEYCODE_SLEEP")
                await("La pantalla debe estar apagada") { !power.isInteractive }
                instrumentation.runOnMainSync { previousPosition = player.currentPosition }
                await("El audio debe seguir avanzando con la pantalla apagada") { player.isPlaying && player.currentPosition > previousPosition + 1_000 }

                val notification = requireNotNull(playbackNotification(notifications))
                @Suppress("DEPRECATION")
                val token = notification.extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
                assertNotNull("La notificación debe incluir la sesión multimedia", token)
                val systemController = android.media.session.MediaController(context, requireNotNull(token))
                systemController.transportControls.pause()
                await("La sesión de la notificación debe poder pausar el audio") { !player.playWhenReady }
                instrumentation.runOnMainSync { previousPosition = player.currentPosition }
                SystemClock.sleep(600)
                instrumentation.runOnMainSync {
                    assertEquals("La posición debe conservarse durante la pausa", previousPosition, player.currentPosition)
                }
                systemController.transportControls.play()
                await("La sesión de la notificación debe poder reanudar el audio") { player.isPlaying && player.currentPosition > previousPosition + 500 }
                val stop = requireNotNull(playbackNotification(notifications)).actions.firstOrNull {
                    it.title.toString() == context.getString(R.string.playback_stop)
                }
                assertNotNull("La notificación debe ofrecer Detener", stop)
                requireNotNull(stop).actionIntent.send()
                await("Detener debe vaciar el reproductor") { player.mediaItemCount == 0 && !player.isPlaying }
                await("Detener debe retirar la notificación") { playbackNotification(notifications) == null }
            }
        } finally {
            if (wasInteractive) shell("input keyevent KEYCODE_WAKEUP")
            instrumentation.runOnMainSync {
                controller?.run {
                    stop()
                    clearMediaItems()
                    release()
                }
            }
            assertTrue("Debe eliminarse el audio temporal de prueba", audio.delete())
        }
    }

    @Test
    fun connectingAndReconnectingDoNotAutoplayOrResetPlayback() {
        val audio = createPlaybackTestAudio(context)
        var first: MediaController? = null
        var second: MediaController? = null
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                first = connect()
                val initial = requireNotNull(first)
                instrumentation.runOnMainSync {
                    assertFalse(initial.isPlaying)
                    assertEquals(0, initial.mediaItemCount)
                    initial.setMediaItem(MediaItem.fromUri(Uri.fromFile(audio)))
                    initial.prepare()
                    initial.play()
                }
                await("El audio debe iniciar por una acción explícita") { initial.isPlaying && initial.currentPosition > 500 }
                var previousPosition = 0L
                instrumentation.runOnMainSync {
                    previousPosition = initial.currentPosition
                    initial.release()
                }
                first = null
                second = connect()
                val reconnected = requireNotNull(second)
                await("Reconectar no debe interrumpir ni reiniciar el audio") {
                    reconnected.isPlaying && reconnected.currentPosition > previousPosition
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                (second ?: first)?.run {
                    stop()
                    clearMediaItems()
                    release()
                }
            }
            assertTrue(audio.delete())
        }
    }

    private fun connect(): MediaController {
        lateinit var future: com.google.common.util.concurrent.ListenableFuture<MediaController>
        instrumentation.runOnMainSync {
            future = MediaController.Builder(
                context, SessionToken(context, ComponentName(context, EpisodePlaybackService::class.java))
            ).buildAsync()
        }
        return future.get(10, TimeUnit.SECONDS)
    }

    private fun playbackNotification(manager: NotificationManager): Notification? =
        manager.activeNotifications.firstOrNull {
            it.notification.extras.getString(Notification.EXTRA_TITLE) == "Prueba de segundo plano"
        }?.notification

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            var satisfied = false
            instrumentation.runOnMainSync { satisfied = condition() }
            if (satisfied) return
            SystemClock.sleep(100)
        }
        throw AssertionError(message)
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }

}

internal fun createPlaybackTestAudio(context: Context): File {
    val samples = 8_000 * 60
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        .put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
        .putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000).putShort(2).putShort(16)
        .put("data".toByteArray()).putInt(samples * 2).array()
    return File.createTempFile("episode-playback-", ".wav", context.cacheDir).apply {
        outputStream().use {
            it.write(header)
            it.write(ByteArray(samples * 2))
        }
    }
}
