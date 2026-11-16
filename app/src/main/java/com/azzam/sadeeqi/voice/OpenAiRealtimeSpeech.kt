package com.azzam.sadeeqi.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Streams OpenAI Realtime PCM speech using a short-lived client secret.
 * The permanent API key remains on the backend and is never shipped in the APK.
 */
class OpenAiRealtimeSpeech(
    private val baseUrl: String,
    private val accessTokenProvider: () -> String
) : Closeable {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
    private val worker = Executors.newSingleThreadExecutor()
    private val generation = AtomicLong(0)
    @Volatile private var activeSocket: WebSocket? = null
    @Volatile private var activeTrack: AudioTrack? = null

    fun speak(
        text: String,
        voice: String,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        val requestGeneration = generation.incrementAndGet()
        worker.execute {
            var player: AudioTrack? = null
            val finished = CountDownLatch(1)
            var failure: String? = null
            val totalBytesWritten = AtomicLong(0)
            try {
                require(baseUrl.startsWith("https://")) { "عنوان خادم صديقي غير آمن." }
                val accessToken = accessTokenProvider()
                require(accessToken.isNotBlank()) { "سجّل الدخول مجدداً لتشغيل الصوت السحابي." }
                val tokenRequest = Request.Builder()
                    .url(baseUrl.trimEnd('/') + "/v1/voice/session")
                    .header("Authorization", "Bearer $accessToken")
                    .header("Content-Type", "application/json")
                    .post(JSONObject().put("voice", voice).toString().toRequestBody("application/json".toMediaType()))
                    .build()
                val clientSecret = http.newCall(tokenRequest).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IllegalStateException(
                            runCatching { JSONObject(body).optString("error") }
                                .getOrDefault("تعذر تهيئة الصوت السحابي.")
                        )
                    }
                    JSONObject(body).optString("client_secret")
                        .takeIf { it.isNotBlank() }
                        ?: throw IllegalStateException("لم يصل رمز جلسة الصوت من الخادم.")
                }
                if (requestGeneration != generation.get()) return@execute

                val minimumBuffer = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(SAMPLE_RATE / 2)
                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minimumBuffer)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                player = audioTrack
                activeTrack = audioTrack
                if (requestGeneration != generation.get()) return@execute
                audioTrack.play()

                val socketRequest = Request.Builder()
                    .url("wss://api.openai.com/v1/realtime?model=gpt-realtime-2.1-mini")
                    .header("Authorization", "Bearer $clientSecret")
                    .build()
                val listener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                        // Realtime WebSocket expects user text as a conversation item,
                        // followed by a separate response.create event. "input" is not
                        // part of the response.create payload.
                        val userMessage = JSONObject()
                            .put("type", "conversation.item.create")
                            .put(
                                "item",
                                JSONObject()
                                    .put("type", "message")
                                    .put("role", "user")
                                    .put(
                                        "content",
                                        org.json.JSONArray().put(
                                            JSONObject().put("type", "input_text").put("text", text)
                                        )
                                    )
                            )
                        val responseRequest = JSONObject().put("type", "response.create")
                        if (requestGeneration != generation.get() ||
                            !webSocket.send(userMessage.toString()) ||
                            !webSocket.send(responseRequest.toString())
                        ) {
                            failure = "تم إلغاء طلب الصوت."
                            finished.countDown()
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, textMessage: String) {
                        if (requestGeneration != generation.get()) return
                        try {
                            val event = JSONObject(textMessage)
                            when (event.optString("type")) {
                                "response.output_audio.delta" -> {
                                    val encoded = event.optString("delta")
                                    if (encoded.isNotBlank()) {
                                        val pcm = Base64.decode(encoded, Base64.DEFAULT)
                                        var offset = 0
                                        while (offset < pcm.size && requestGeneration == generation.get()) {
                                            val written = audioTrack.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_BLOCKING)
                                            if (written <= 0) break
                                            offset += written
                                            totalBytesWritten.addAndGet(written.toLong())
                                        }
                                    }
                                }
                                "response.done" -> finished.countDown()
                                "error" -> {
                                    failure = event.optJSONObject("error")?.optString("message")
                                        ?: "فشل توليد الصوت."
                                    finished.countDown()
                                }
                            }
                        } catch (error: Exception) {
                            failure = error.message ?: "تعذر معالجة بيانات الصوت."
                            finished.countDown()
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) = Unit

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                        failure = t.message ?: "تعذر الاتصال بخدمة الصوت."
                        finished.countDown()
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        if (finished.count > 0) {
                            failure = "انتهت جلسة الصوت قبل اكتمال الرد."
                            finished.countDown()
                        }
                    }
                }
                val socket = http.newWebSocket(socketRequest, listener)
                activeSocket = socket
                // Cancellation may occur while the WebSocket handshake is in flight.
                if (requestGeneration != generation.get()) {
                    socket.cancel()
                    return@execute
                }
                if (!finished.await(75, TimeUnit.SECONDS)) {
                    failure = "انتهت مهلة توليد الصوت."
                    socket.cancel()
                }
                if (requestGeneration == generation.get() && failure == null) {
                    val expectedFrames = totalBytesWritten.get() / 2L
                    val drainDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
                    while (expectedFrames > 0L && audioTrack.playbackHeadPosition.toLong() < expectedFrames &&
                        requestGeneration == generation.get() && System.nanoTime() < drainDeadline) {
                        Thread.sleep(20)
                    }
                    if (expectedFrames == 0L || player.playbackHeadPosition.toLong() < expectedFrames) {
                        failure = "لم يكتمل تشغيل الصوت المستلم."
                    } else {
                        audioTrack.stop()
                        onDone()
                    }
                }
                if (requestGeneration == generation.get() && failure != null) {
                    onError(failure ?: "تعذر تشغيل الصوت السحابي.")
                }
                socket.close(1000, "speech complete")
            } catch (error: Exception) {
                if (requestGeneration == generation.get()) {
                    onError(error.message ?: "تعذر تشغيل الصوت السحابي.")
                }
            } finally {
                runCatching { player?.pause() }
                runCatching { player?.flush() }
                runCatching { player?.release() }
                if (activeTrack === player) activeTrack = null
                activeSocket = null
            }
        }
    }

    fun cancel() {
        generation.incrementAndGet()
        activeSocket?.cancel()
        activeSocket = null
        activeTrack?.let { track ->
            runCatching { track.pause() }
            runCatching { track.flush() }
        }
    }

    override fun close() {
        cancel()
        worker.shutdownNow()
        http.dispatcher.executorService.shutdown()
    }

    companion object {
        private const val SAMPLE_RATE = 24000
    }
}
