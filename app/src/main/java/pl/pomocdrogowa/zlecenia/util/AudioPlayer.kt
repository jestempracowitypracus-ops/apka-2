package pl.pomocdrogowa.zlecenia.util

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Jeden odtwarzacz na ekran - włączenie nowej głosówki zatrzymuje poprzednią. */
class AudioPlayer {
    private var player: MediaPlayer? = null

    var playingUrl by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set

    fun positionMs(): Int = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)

    fun toggle(url: String) {
        if (playingUrl == url) {
            stop()
            return
        }
        stop()
        playingUrl = url
        loading = true
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build(),
            )
            setDataSource(url)
            setOnPreparedListener {
                loading = false
                it.start()
            }
            setOnCompletionListener { stop() }
            setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            prepareAsync()
        }
    }

    fun stop() {
        player?.let { p ->
            runCatching { p.stop() }
            p.release()
        }
        player = null
        playingUrl = null
        loading = false
    }
}
