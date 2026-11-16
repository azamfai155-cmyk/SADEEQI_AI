package com.azzam.sadeeqi.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Explicit, foreground-only wake phrase sample capture.
 * Samples stay in the app-private files directory and are never uploaded.
 */
class WakeWordSampleRecorder(context: Context) {
    private val appContext = context.applicationContext
    private val sampleDirectory = File(appContext.filesDir, "wake_word_samples")
    private val active = AtomicBoolean(false)
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var worker: Thread? = null

    fun isRecording(): Boolean = active.get()

    fun sampleFiles(): List<File> =
        sampleDirectory.listFiles { file -> file.isFile && file.extension == "wav" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    @Synchronized
    fun start(phrase: String, onFinished: (File?, String?) -> Unit): Boolean {
        if (active.get()) return false
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onFinished(null, "إذن الميكروفون غير ممنوح.")
            return false
        }
        val sampleRate = 16_000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            onFinished(null, "تعذر تهيئة تسجيل PCM على هذا الجهاز.")
            return false
        }
        if (!sampleDirectory.exists() && !sampleDirectory.mkdirs()) {
            onFinished(null, "تعذر إنشاء مجلد العينات المحلي.")
            return false
        }
        val audioRecord = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, sampleRate * 2)
            )
        }.getOrElse {
            onFinished(null, "تعذر إنشاء مسجل الصوت: ${it.message ?: "خطأ غير معروف"}")
            return false
        }
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            onFinished(null, "مسجل الصوت غير جاهز على هذا الجهاز.")
            return false
        }

        val safePhrase = if (phrase == "يا صديقي") "ya_sadeeqi" else "sadeeqi"
        val stamp = System.currentTimeMillis()
        val rawFile = File(sampleDirectory, "${safePhrase}_${stamp}.pcm.tmp")
        val wavFile = File(sampleDirectory, "${safePhrase}_${stamp}.wav")
        active.set(true)
        recorder = audioRecord
        try {
            audioRecord.startRecording()
        } catch (error: Exception) {
            active.set(false)
            recorder = null
            audioRecord.release()
            onFinished(null, "فشل بدء التسجيل: ${error.message ?: "خطأ غير معروف"}")
            return false
        }

        worker = Thread({
            var failure: String? = null
            var totalBytes = 0L
            try {
                FileOutputStream(rawFile).use { output ->
                    val buffer = ByteArray(maxOf(minBuffer, 4096))
                    val deadline = System.nanoTime() + SAMPLE_DURATION_NS
                    while (active.get() && System.nanoTime() < deadline) {
                        val count = audioRecord.read(buffer, 0, buffer.size)
                        if (count > 0) {
                            output.write(buffer, 0, count)
                            totalBytes += count
                        } else if (count < 0) {
                            throw IllegalStateException("AudioRecord read error: $count")
                        }
                    }
                }
            } catch (error: Exception) {
                failure = error.message ?: "حدث خطأ أثناء قراءة الميكروفون."
            } finally {
                active.set(false)
                runCatching { audioRecord.stop() }
                audioRecord.release()
                recorder = null
                worker = null
                val saved = if (failure == null && totalBytes > 0L) {
                    runCatching {
                        writeWav(rawFile, wavFile, totalBytes, sampleRate)
                        rawFile.delete()
                        wavFile
                    }.getOrElse {
                        failure = "تعذر حفظ ملف WAV: ${it.message ?: "خطأ"}"
                        null
                    }
                } else null
                rawFile.delete()
                onFinished(saved, failure ?: if (saved == null) "لم تُلتقط عينة صوتية." else null)
            }
        }, "SadeeqiWakeSampleCapture").apply { isDaemon = true }
        worker?.start()
        return true
    }

    fun stop() {
        if (active.compareAndSet(true, false)) {
            runCatching { recorder?.stop() }
        }
    }

    fun deleteAllSamples(): Boolean {
        if (active.get()) return false
        if (!sampleDirectory.exists()) return true
        return sampleDirectory.deleteRecursively()
    }

    private fun writeWav(raw: File, wav: File, dataSize: Long, sampleRate: Int) {
        FileOutputStream(wav).use { fileOutput ->
            BufferedOutputStream(fileOutput).use { output ->
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                val byteRate = sampleRate * CHANNELS * BITS_PER_SAMPLE / 8
                header.put("RIFF".toByteArray(Charsets.US_ASCII))
                header.putInt((36L + dataSize).toInt())
                header.put("WAVE".toByteArray(Charsets.US_ASCII))
                header.put("fmt ".toByteArray(Charsets.US_ASCII))
                header.putInt(16)
                header.putShort(1)
                header.putShort(CHANNELS.toShort())
                header.putInt(sampleRate)
                header.putInt(byteRate)
                header.putShort((CHANNELS * BITS_PER_SAMPLE / 8).toShort())
                header.putShort(BITS_PER_SAMPLE.toShort())
                header.put("data".toByteArray(Charsets.US_ASCII))
                header.putInt(dataSize.toInt())
                output.write(header.array())
                FileInputStream(raw).use { input -> input.copyTo(output) }
            }
        }
    }

    companion object {
        private const val CHANNELS = 1
        private const val BITS_PER_SAMPLE = 16
        private const val SAMPLE_DURATION_NS = 2_500_000_000L
    }
}
