package pl.pomocdrogowa.zlecenia.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/** Nagrywanie wiadomości głosowej do pliku .m4a (AAC). */
class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    fun start() {
        stopAndDiscard()
        val out = File(mediaDir(context), "voice_${System.currentTimeMillis()}.m4a")
        val r = newRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(64_000)
            setAudioSamplingRate(44_100)
            setOutputFile(out.absolutePath)
            prepare()
            start()
        }
        recorder = r
        file = out
        startedAt = SystemClock.elapsedRealtime()
    }

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
        else @Suppress("DEPRECATION") MediaRecorder()

    fun elapsedMs(): Long = if (isRecording) SystemClock.elapsedRealtime() - startedAt else 0L

    /** Kończy nagranie i zwraca plik oraz długość, albo null jeśli nagranie było za krótkie. */
    fun stop(): Pair<File, Long>? {
        val r = recorder ?: return null
        val duration = elapsedMs()
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        recorder = null
        val f = file
        file = null
        if (!ok || f == null || duration < 700) {
            f?.delete()
            return null
        }
        return f to duration
    }

    fun stopAndDiscard() {
        recorder?.let { r ->
            runCatching { r.stop() }
            r.release()
        }
        recorder = null
        file?.delete()
        file = null
    }
}

fun mediaDir(context: Context): File = File(context.cacheDir, "media").apply { mkdirs() }
