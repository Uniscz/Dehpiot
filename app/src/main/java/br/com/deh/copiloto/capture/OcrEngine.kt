package br.com.deh.copiloto.capture

import android.graphics.Bitmap
import br.com.deh.copiloto.core.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrReading(val parsed: ParsedOffer, val lines: List<OcrLine>, val latencyMs: Long)
object OcrEngine {
    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val mutex = Mutex()
    suspend fun readPanelText(bitmap: Bitmap): String = mutex.withLock {
        withContext(NonCancellable) {
            suspendCancellableCoroutine { continuation ->
                client.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { continuation.resume(it.text) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
            }
        }
    }
    suspend fun read(bitmap: Bitmap): OcrReading = mutex.withLock {
        val started = android.os.SystemClock.elapsedRealtime()
        // Native ML Kit tasks must finish before the caller can recycle this bitmap.
        withContext(NonCancellable) {
            suspendCancellableCoroutine { continuation ->
                client.process(InputImage.fromBitmap(bitmap, 0)).addOnSuccessListener { text ->
                    val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { l -> l.boundingBox?.let { b -> OcrLine(l.text, b.left, b.top, b.right, b.bottom) } }
                    continuation.resume(OcrReading(OfferParser.parse(OfferParser.merge(lines)), lines, android.os.SystemClock.elapsedRealtime() - started))
                }.addOnFailureListener { continuation.resumeWithException(it) }
            }
        }
    }
}
