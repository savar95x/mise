package dev.savar.mise

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

const val SAMPLE_RATE = 16_000

/**
 * Records the mic once and uses it twice: every chunk of 16 kHz mono PCM is encoded to AAC (a small .m4a memo,
 * about 0.25 MB a minute) and, at the same time, handed to [onPcm] for live transcription.
 */
class Recorder {
    var recording by mutableStateOf(false)
        private set
    var elapsedMs by mutableLongStateOf(0L)
        private set
    /** 0..1, smoothed, for the level meter. */
    var level by mutableFloatStateOf(0f)
        private set

    @Volatile private var stopRequested = false
    private var thread: Thread? = null
    @Volatile private var error: String? = null

    @SuppressLint("MissingPermission") // the caller asks for RECORD_AUDIO first
    fun start(file: File, onPcm: (ByteArray, Int) -> Unit): Boolean {
        if (recording) return false
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        val record = runCatching {
            AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 4, 8192))
        }.getOrNull() ?: return false
        if (record.state != AudioRecord.STATE_INITIALIZED) { record.release(); return false }

        stopRequested = false
        error = null
        elapsedMs = 0
        recording = true
        thread = Thread({ loop(record, file, onPcm) }, "mise-recorder").apply { start() }
        return true
    }

    /** Blocks until the memo file is finished. Call off the main thread. Returns its length in ms, or null. */
    fun stop(): Long? {
        stopRequested = true
        thread?.join(5_000)
        thread = null
        recording = false
        level = 0f
        return if (error == null) elapsedMs else null
    }

    private fun loop(record: AudioRecord, file: File, onPcm: (ByteArray, Int) -> Unit) {
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        try {
            val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 32_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
            }
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
            muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val mux = Mux(muxer)
            val info = MediaCodec.BufferInfo()
            val buf = ByteArray(2048) // 64 ms
            var samples = 0L
            record.startRecording()
            while (!stopRequested) {
                val n = record.read(buf, 0, buf.size)
                if (n <= 0) continue
                onPcm(buf, n)
                level = level * 0.6f + rms(buf, n) * 0.4f
                var off = 0
                while (off < n) {
                    val idx = codec.dequeueInputBuffer(10_000)
                    if (idx >= 0) {
                        val inBuf = codec.getInputBuffer(idx)!!
                        inBuf.clear()
                        val len = minOf(inBuf.remaining(), n - off)
                        inBuf.put(buf, off, len)
                        codec.queueInputBuffer(idx, 0, len, samples * 1_000_000L / SAMPLE_RATE, 0)
                        samples += len / 2
                        off += len
                    }
                    drain(codec, mux, info, endOfStream = false)
                }
                elapsedMs = samples * 1000 / SAMPLE_RATE
            }
            record.stop()
            // End of stream, then empty the encoder.
            while (true) {
                val idx = codec.dequeueInputBuffer(10_000)
                if (idx >= 0) {
                    codec.queueInputBuffer(idx, 0, 0, samples * 1_000_000L / SAMPLE_RATE, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    break
                }
                drain(codec, mux, info, endOfStream = false)
            }
            drain(codec, mux, info, endOfStream = true)
            if (mux.started) muxer.stop() else error = "Nothing recorded"
        } catch (e: Exception) {
            error = e.message ?: "Recording failed"
        } finally {
            runCatching { record.release() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { muxer?.release() }
        }
    }

    private class Mux(val muxer: MediaMuxer) {
        var track = -1
        var started = false
    }

    private fun drain(codec: MediaCodec, mux: Mux, info: MediaCodec.BufferInfo, endOfStream: Boolean) {
        while (true) {
            val idx = codec.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
            when {
                idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    mux.track = mux.muxer.addTrack(codec.outputFormat)
                    mux.muxer.start()
                    mux.started = true
                }
                idx >= 0 -> {
                    val out = codec.getOutputBuffer(idx)!!
                    val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (!config && info.size > 0 && mux.started) {
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        mux.muxer.writeSampleData(mux.track, out, info)
                    }
                    codec.releaseOutputBuffer(idx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun rms(b: ByteArray, n: Int): Float {
        var sum = 0.0
        var i = 0
        while (i + 1 < n) {
            val s = (b[i].toInt() and 0xff) or (b[i + 1].toInt() shl 8)
            sum += s.toDouble() * s
            i += 2
        }
        val r = sqrt(sum / (n / 2).coerceAtLeast(1)) / 32768.0
        return (r * 6).coerceIn(0.0, 1.0).toFloat()
    }
}

/**
 * Speech to text on the phone (Android 13+), fed with our own audio through a pipe instead of letting the
 * recognizer open the mic, so recording and transcribing happen together. Uses the on-device recognizer when the
 * phone has one, else the default one. Results come back in segments, each a finished sentence or two.
 *
 * Create, [start] and destroy on the main thread; [feed] from any thread (it never blocks the recorder).
 */
class Transcriber(
    private val ctx: Context,
    private val lang: String,
    private val onPartial: (String) -> Unit,
    private val onSegment: (String) -> Unit,
    private val onDone: (error: String?) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private var readEnd: ParcelFileDescriptor? = null
    private var out: OutputStream? = null
    private val queue = LinkedBlockingQueue<ByteArray>()
    @Volatile private var alive = false
    @Volatile private var finished = false
    private var done = false

    companion object {
        fun supported(ctx: Context): Boolean =
            Build.VERSION.SDK_INT >= 33 && (SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx) || SpeechRecognizer.isRecognitionAvailable(ctx))
    }

    fun start(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        val r = when {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx) -> SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
            SpeechRecognizer.isRecognitionAvailable(ctx) -> SpeechRecognizer.createSpeechRecognizer(ctx)
            else -> return false
        }
        val pipe = runCatching { ParcelFileDescriptor.createPipe() }.getOrNull() ?: run { r.destroy(); return false }
        readEnd = pipe[0]
        out = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            if (lang.isNotBlank()) putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, SAMPLE_RATE)
            putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
        }
        r.setRecognitionListener(listener)
        recognizer = r
        alive = true
        Thread({ pump() }, "mise-transcriber").apply { isDaemon = true; start() }
        r.startListening(intent)
        return true
    }

    fun feed(buf: ByteArray, n: Int) {
        if (!alive || finished) return
        if (queue.size < 400) queue.offer(buf.copyOf(n)) // ~25 s of backlog at most; drop rather than stall
    }

    /** For files: waits instead of dropping when the recognizer falls behind (decoding is faster than real time). */
    fun feedWaiting(buf: ByteArray, n: Int) {
        while (alive && !finished && queue.size > 48) Thread.sleep(15)
        if (alive && !finished) queue.offer(buf.copyOf(n))
    }

    /** No more audio: the recognizer finishes what it has, then reports [onDone]. */
    fun finish() {
        finished = true
        queue.offer(ByteArray(0))
    }

    private fun pump() {
        try {
            while (alive) {
                val b = queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
                if (b.isEmpty()) break
                out?.write(b)
            }
        } catch (e: IOException) {
            // the recognizer stopped reading
        } finally {
            runCatching { out?.close() }
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            first(partialResults)?.let(onPartial)
        }

        override fun onSegmentResults(segmentResults: Bundle) {
            first(segmentResults)?.takeIf { it.isNotBlank() }?.let(onSegment)
            onPartial("")
        }

        override fun onResults(results: Bundle?) {
            first(results)?.takeIf { it.isNotBlank() }?.let(onSegment)
            end(null)
        }

        override fun onEndOfSegmentedSession() = end(null)

        override fun onError(error: Int) {
            end(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> null
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                        "That language isn't downloaded for offline speech"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service needs a connection"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy"
                    else -> "Live transcript stopped (error $error)"
                },
            )
        }
    }

    private fun first(b: Bundle?): String? = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun end(error: String?) {
        if (done) return
        done = true
        alive = false
        queue.offer(ByteArray(0))
        onPartial("")
        destroy()
        onDone(error)
    }

    fun destroy() {
        alive = false
        runCatching { recognizer?.destroy() }
        recognizer = null
        runCatching { readEnd?.close() }
        readEnd = null
    }
}

/** Decodes one of our memos back to 16 kHz mono PCM, for "Transcribe" on a memo recorded without a transcript. */
object Pcm {
    fun decode(file: File, sink: (ByteArray, Int) -> Unit) {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(file.absolutePath)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: throw IOException("No audio")
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!).apply { configure(fmt, null, null, 0); start() }
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            val chunk = ByteArray(8192)
            while (true) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o >= 0) {
                    val b = codec.getOutputBuffer(o)!!
                    b.position(info.offset)
                    b.limit(info.offset + info.size)
                    while (b.hasRemaining()) {
                        val n = minOf(chunk.size, b.remaining())
                        b.get(chunk, 0, n)
                        sink(chunk, n)
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            ex.release()
        }
    }
}

/** One memo plays at a time. */
object Player {
    var playing by mutableStateOf<String?>(null)
        private set
    var progress by mutableFloatStateOf(0f)
        private set

    private var mp: MediaPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null

    fun toggle(ctx: Context, memo: Memo) {
        if (playing == memo.file) { stop(); return }
        stop()
        val f = Store.memoFile(ctx, memo)
        if (!f.exists()) return
        val p = runCatching {
            MediaPlayer().apply {
                setDataSource(f.absolutePath)
                prepare()
                setOnCompletionListener { stop() }
                start()
            }
        }.getOrNull() ?: return
        mp = p
        playing = memo.file
        ticker = scope.launch {
            while (isActive) {
                val d = runCatching { p.duration }.getOrDefault(0)
                progress = if (d > 0) runCatching { p.currentPosition }.getOrDefault(0).toFloat() / d else 0f
                delay(100)
            }
        }
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        mp?.let { runCatching { it.stop() }; it.release() }
        mp = null
        playing = null
        progress = 0f
    }
}

