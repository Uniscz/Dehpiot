package br.com.deh.copiloto.capture

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.*
import android.hardware.display.*
import android.media.ImageReader
import android.media.projection.*
import android.os.*
import android.speech.tts.TextToSpeech
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import br.com.deh.copiloto.*
import br.com.deh.copiloto.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

data class CaptureState(val running: Boolean = false, val paused: Boolean = false, val message: String = "Leitura desligada", val latency: Long = 0, val valid: Int = 0)
data class LivePrediction(val prediction: Prediction, val at: Long = System.currentTimeMillis())
object CaptureBus { val latest = MutableStateFlow<LivePrediction?>(null); val state = MutableStateFlow(CaptureState()); @Volatile var appVisible = false }
class CaptureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var projection: MediaProjection? = null; private var display: VirtualDisplay? = null; private var reader: ImageReader? = null
    private val thread = HandlerThread("dehpilot-frames"); private lateinit var handler: Handler
    private var overlay: OverlayCard? = null; private var tts: TextToSpeech? = null
    private val busy = AtomicBoolean(); private var lastFrame = 0L; private var lastHash = 0L; private var lastOcr = 0L
    private var signature = ""; private var savedAt = 0L; private var expires = 0L; private var routeJob: Job? = null
    private var knownRoute: DrivingRoute? = null; private var currentId: String? = null
    private val app get() = application as CopilotoApp
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate(); thread.start(); handler = Handler(thread.looper)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("capture", "Leitura de ofertas", NotificationManager.IMPORTANCE_LOW))
        overlay = OverlayCard(this) { pause() }
        scope.launch { while (isActive) { delay(1000); if (SystemClock.elapsedRealtime() > expires || CaptureBus.appVisible || CaptureBus.state.value.paused) withContext(Dispatchers.Main) { overlay?.hide() } } }
    }
    private fun notification(): Notification {
        fun action(name: String) = PendingIntent.getService(this, name.hashCode(), Intent(this, CaptureService::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, "capture").setSmallIcon(br.com.deh.copiloto.R.drawable.ic_copiloto).setContentTitle("Dehpilot • leitura local").setContentText(if (CaptureBus.state.value.paused) "Pausado" else "Analisando ofertas. Toque em Parar para encerrar.")
            .setContentIntent(PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)).setOngoing(true)
            .addAction(0, if (CaptureBus.state.value.paused) "Retomar" else "Pausar", action("PAUSE")).addAction(0, "Parar", action("STOP")).build()
    }
    private fun pause() {
        CaptureBus.state.value = CaptureBus.state.value.copy(paused = !CaptureBus.state.value.paused)
        overlay?.hide(); expires = 0; routeJob?.cancel(); getSystemService(NotificationManager::class.java).notify(17, notification())
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (intent?.action == "PAUSE") { pause(); return START_NOT_STICKY }
        if (projection != null) return START_NOT_STICKY
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("consent", Intent::class.java) else @Suppress("DEPRECATION") intent?.getParcelableExtra("consent")
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        try {
            CaptureBus.state.value = CaptureState(running = true, message = "Aguardando oferta legível")
            if (Build.VERSION.SDK_INT >= 29) startForeground(17, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(17, notification())
            projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(Activity.RESULT_OK, data)
            projection!!.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { Handler(mainLooper).post { stopSelf() } }
                override fun onCapturedContentResize(width: Int, height: Int) { configure(width, height, false) }
                override fun onCapturedContentVisibilityChanged(isVisible: Boolean) { if (!isVisible) { expires = 0; Handler(mainLooper).post { overlay?.hide() } } }
            }, handler)
            val metrics = resources.displayMetrics; configure(metrics.widthPixels, metrics.heightPixels, true)
        } catch (e: Exception) { CaptureBus.state.value = CaptureState(message = "Não foi possível iniciar: ${e.message}"); stopSelf() }
        return START_NOT_STICKY
    }
    @Synchronized private fun configure(w: Int, h: Int, initial: Boolean) {
        if (w <= 0 || h <= 0 || (!initial && display == null)) return
        val scale = minOf(1.0, 1080.0 / w, 2400.0 / h); val width = (w * scale).roundToInt(); val height = (h * scale).roundToInt()
        val next = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        next.setOnImageAvailableListener({ r -> frame(r) }, handler)
        if (initial) display = projection!!.createVirtualDisplay("DehpilotLocalOCR", width, height, resources.displayMetrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, next.surface, null, handler)
        else { display?.resize(width, height, resources.displayMetrics.densityDpi); display?.surface = next.surface }
        reader?.close(); reader = next
    }
    private fun frame(r: ImageReader) {
        val image = runCatching { r.acquireLatestImage() }.getOrNull() ?: return
        val now = SystemClock.elapsedRealtime(); val s = app.repository.settings.value
        if (CaptureBus.appVisible || CaptureBus.state.value.paused || now - lastFrame < s.intervalMs || !busy.compareAndSet(false, true)) { image.close(); return }
        lastFrame = now
        val bitmap: Bitmap
        try {
            val plane = image.planes[0]; val padded = Bitmap.createBitmap(plane.rowStride / plane.pixelStride, image.height, Bitmap.Config.ARGB_8888); padded.copyPixelsFromBuffer(plane.buffer)
            val top = (image.height * s.cropTop).toInt().coerceIn(0, image.height - 1)
            bitmap = Bitmap.createBitmap(padded, 0, top, image.width, image.height - top); if (padded !== bitmap) padded.recycle()
        } catch (_: Exception) { busy.set(false); image.close(); return }
        image.close()
        var hash = 7L
        for (y in 0 until 28) for (x in 0 until 20) hash = hash * 31 + (bitmap.getPixel(x * bitmap.width / 20, y * bitmap.height / 28) and 0x00E0E0E0)
        if (hash == lastHash && now - lastOcr < 4000) { bitmap.recycle(); busy.set(false); return }
        lastHash = hash; lastOcr = now
        scope.launch {
            try {
                val read = OcrEngine.read(bitmap); if (!CaptureBus.state.value.running || CaptureBus.state.value.paused || CaptureBus.appVisible) return@launch
                val o = read.parsed.offer
                val context = normalized(read.parsed.text)
                if (o == null || listOf("aceitar", "selecionar", "uber", "99pop", "99plus", "viagem", "corrida").none(context::contains)) { CaptureBus.state.value = CaptureBus.state.value.copy(message = read.parsed.warning, latency = read.latencyMs); return@launch }
                val returnPoint = app.routing.resolve(app.routing.settings.returnPlace)?.point
                val key = "${o.fareCents}|${o.pickupKm}|${o.pickupMin}|${o.tripKm}|${o.tripMin}|${o.destination}|$returnPoint"
                var a = app.repository.learned(o.destination, s.defaults.copy(routeVerified = false, routeProvider = "", routeAsOf = 0, straightLineKm = null, accessWarning = false))
                val isNew = key != signature || now - savedAt > 45_000
                if (isNew) { signature = key; savedAt = now; knownRoute = null; routeJob?.cancel() }
                knownRoute?.takeIf { it.query.to == returnPoint && System.currentTimeMillis() - it.calculatedAt in 0..3_600_000 }?.let { a = RouteEconomics.applyReposition(a, it) }
                val p = Economics.evaluate(o, a, s.cost, s.policy)
                if (isNew) currentId = app.repository.save(o, a, s.cost, p, "LIVE_OCR", read.latencyMs)
                expires = SystemClock.elapsedRealtime() + 12_000
                CaptureBus.latest.value = LivePrediction(p)
                CaptureBus.state.value = CaptureBus.state.value.copy(message = p.label, latency = read.latencyMs, valid = CaptureBus.state.value.valid + 1)
                withContext(Dispatchers.Main) { if (!CaptureBus.appVisible && !CaptureBus.state.value.paused) overlay?.show(p); if (isNew) feedback(p) }
                if (isNew && o.destination.isNotBlank()) {
                    val id = currentId; val base = a
                    routeJob = scope.launch {
                        val route = (app.routing.forDestination(o.destination) as? RoutingResult.Found)?.route ?: return@launch
                        if (signature != key) return@launch
                        knownRoute = route; val enriched = RouteEconomics.applyReposition(base, route); val predicted = Economics.evaluate(o, enriched, s.cost, s.policy)
                        if (signature == key && SystemClock.elapsedRealtime() < expires) CaptureBus.latest.value = LivePrediction(predicted)
                        if (id != null) app.repository.dao.enrich(id, br.com.deh.copiloto.data.Codec.assumptions(enriched).toString(), predicted.netCents, predicted.hourly, predicted.perKm, predicted.score, predicted.totalKm, predicted.totalMin)
                        withContext(Dispatchers.Main) { if (signature == key && SystemClock.elapsedRealtime() < expires && !CaptureBus.appVisible && !CaptureBus.state.value.paused) overlay?.show(predicted) }
                    }
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { CaptureBus.state.value = CaptureBus.state.value.copy(message = "Leitura a conferir: ${e.message}") } finally { bitmap.recycle(); busy.set(false) }
        }
    }
    private fun feedback(p: Prediction) {
        val s = app.repository.settings.value
        if (s.haptic) @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
        if (s.voice) {
            if (tts == null) tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) { tts?.language = Locale("pt", "BR"); tts?.speak("${p.label}. ${p.hourly.roundToInt()} reais por hora.", TextToSpeech.QUEUE_FLUSH, null, "offer") } }
            else tts?.speak("${p.label}. ${p.hourly.roundToInt()} reais por hora.", TextToSpeech.QUEUE_FLUSH, null, "offer")
        }
    }
    override fun onDestroy() {
        CaptureBus.latest.value = null
        CaptureBus.state.value = CaptureState(message = "Leitura encerrada"); routeJob?.cancel(); scope.cancel(); overlay?.hide(); tts?.stop(); tts?.shutdown()
        display?.release(); reader?.close(); projection?.stop(); projection = null; thread.quitSafely(); stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy()
    }
}
private class OverlayCard(private val context: Context, private val pause: () -> Unit) {
    private val wm = context.getSystemService(WindowManager::class.java); private var view: LinearLayout? = null; private var expanded = false; private var prediction: Prediction? = null; private var rendered: Pair<Prediction, Boolean>? = null
    private val params = WindowManager.LayoutParams((238 * context.resources.displayMetrics.density).toInt(), WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_SECURE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 12; y = 130 }
    fun hide() { view?.let { runCatching { wm.removeView(it) } }; view = null }
    fun show(p: Prediction) {
        prediction = p
        if (view != null && rendered == (p to expanded)) return
        val root = view ?: LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(22, 16, 22, 16); background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(13, 21, 28)); cornerRadius = 24f; setStroke(2, Color.rgb(139, 218, 203)) }
            var ox = 0; var oy = 0; var sx = 0f; var sy = 0f; var moved = false
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { ox = params.x; oy = params.y; sx = event.rawX; sy = event.rawY; moved = false }
                    MotionEvent.ACTION_MOVE -> { moved = moved || kotlin.math.abs(event.rawX - sx) + kotlin.math.abs(event.rawY - sy) > 12; params.x = (ox + event.rawX - sx).toInt().coerceIn(0, maxOf(0, context.resources.displayMetrics.widthPixels - params.width)); params.y = (oy + event.rawY - sy).toInt().coerceIn(0, maxOf(0, context.resources.displayMetrics.heightPixels - height)); runCatching { wm.updateViewLayout(this, params) } }
                    MotionEvent.ACTION_UP -> if (!moved) v.performClick()
                }; true
            }
            setOnClickListener { expanded = !expanded; prediction?.let(::show) }
            try { wm.addView(this, params); view = this } catch (_: Exception) { return }
        }
        root.removeAllViews()
        fun label(text: String, size: Float, color: Int) = TextView(context).apply { this.text = text; textSize = size; setTextColor(color); setPadding(0, 5, 0, 5); root.addView(this) }
        val color = if (p.score >= 60) Color.rgb(139, 218, 203) else if (p.score >= 45) Color.rgb(255, 206, 118) else Color.rgb(255, 145, 135)
        label("${p.score}  ${p.label}", 25f, color)
        label(String.format(Locale("pt", "BR"), "R$ %.2f/h  •  %.2f/km", p.hourly, p.perKm), 19f, Color.WHITE)
        label(p.reasons.firstOrNull { it.startsWith("Acesso") || it.startsWith("Retorno estimado") } ?: p.confidence, 12f, Color.LTGRAY)
        rendered = p to expanded
        if (expanded) { label(p.reasons.take(3).joinToString("\n"), 12f, Color.WHITE); label("Pausar leitura", 14f, color).setOnClickListener { pause() } }
    }
}
