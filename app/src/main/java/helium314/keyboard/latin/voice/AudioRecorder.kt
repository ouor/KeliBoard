// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Records 16 kHz mono 16-bit PCM, which every supported service accepts as WAV. */
class AudioRecorder {
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private val pcm = ByteArrayOutputStream()
    @Volatile private var running = false

    /** Caller must hold RECORD_AUDIO. [onLimitReached] is called on the recording thread. */
    @SuppressLint("MissingPermission")
    fun start(onLimitReached: () -> Unit) {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) throw VoiceInputException("Microphone not available")
        val record = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer * 4)
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw VoiceInputException("Microphone not available")
        }
        this.record = record
        pcm.reset()
        running = true
        record.startRecording()
        thread = Thread {
            val buffer = ByteArray(minBuffer)
            while (running) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) synchronized(pcm) { pcm.write(buffer, 0, read) }
                if (pcm.size() >= MAX_BYTES) {
                    onLimitReached()
                    break
                }
            }
        }.apply { start() }
    }

    /** Stops recording and returns the audio as WAV. */
    fun stop(): ByteArray {
        release()
        return synchronized(pcm) { wav(pcm.toByteArray()) }
    }

    fun release() {
        running = false
        thread?.join(1000)
        thread = null
        record?.run {
            try { stop() } catch (_: IllegalStateException) { }
            release()
        }
        record = null
    }

    companion object {
        const val SAMPLE_RATE = 16000
        // 16-bit mono: 32 kB per second
        const val MAX_BYTES = SAMPLE_RATE * 2 * 60 * 5

        fun durationMillis(wav: ByteArray) = (wav.size - 44).coerceAtLeast(0) * 1000L / (SAMPLE_RATE * 2)

        internal fun wav(pcm: ByteArray): ByteArray {
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVE".toByteArray())
                .put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1) // PCM, mono
                .putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort(2).putShort(16)
                .put("data".toByteArray()).putInt(pcm.size)
            return header.array() + pcm
        }
    }
}
