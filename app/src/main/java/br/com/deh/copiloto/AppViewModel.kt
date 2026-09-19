package br.com.deh.copiloto

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.deh.copiloto.capture.*
import br.com.deh.copiloto.core.*
import br.com.deh.copiloto.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.Locale

data class Analysis(val offer: Offer, val assumptions: Assumptions, val prediction: Prediction, val settings: Settings, val demo: Boolean, val source: String, val savedId: String? = null, val createdAt: Long = System.currentTimeMillis())
class AppViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    val app = application as CopilotoApp; val repository = app.repository
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private var stack by mutableStateOf(saved.get<IntArray>("navigation")?.toList()?.takeIf { it.isNotEmpty() && it.first() == 0 && it.all { n -> n in 0..11 } } ?: listOf(0))
    var tab: Int
        get() = stack.last()
        set(value) { stack = ProductNavigation.open(stack, value); saved["navigation"] = stack.toIntArray() }
    val canGoBack get() = stack.size > 1
    fun back() { stack = ProductNavigation.back(stack); saved["navigation"] = stack.toIntArray() }
    var panelReading by mutableStateOf<PanelReading?>(null); private set
    var panelBusy by mutableStateOf(false); private set
    var panelText by mutableStateOf(""); private set
    var panelGeneration = 0L
    fun readPanel(uri: Uri) = task {
        if (panelBusy) return@task
        panelBusy = true; panelReading = null; panelText = ""
        try {
            val bitmap = withContext(Dispatchers.IO) { decodePhoto(app, uri) }
            try { panelText = OcrEngine.readPanelText(bitmap); panelReading = PanelParser.parse(panelText) }
            finally { bitmap.recycle() }
            panelGeneration++
            message(if (panelReading?.unambiguous == true) "Consumo identificado. Confira antes de salvar." else "Confira a leitura ou digite o consumo médio.")
        } finally { panelBusy = false }
    }
    suspend fun saveFuel(price: Double, value: Double, unit: ConsumptionUnit, name: String) {
        val s = repository.settings.value
        val kml = Fuel.kmPerLiter(value, unit)
        repository.settingsStore.save(s.copy(cost = Fuel.update(s.cost, price, kml), fuelName = name))
        analysis = null
        message("Consumo e combustível atualizados. Corridas anteriores preservadas.")
    }
    val fields = mutableStateMapOf<String, String>()
    var analysis by mutableStateOf<Analysis?>(null); private set
    var roadRoute by mutableStateOf<DrivingRoute?>(null); private set
    var demo by mutableStateOf(false); private set
    var source by mutableStateOf("Manual"); private set
    var diagnostic by mutableStateOf<OcrReading?>(null); private set
    var diagnosticBitmap by mutableStateOf<Bitmap?>(null); private set
    var busy by mutableStateOf(false); private set
    var analysisLatencyMs by mutableStateOf<Long?>(null); private set
    private var generation = 0L
    private var zoneSampleCount = 0
    private var savingOffer = false
    private val actualDrafts = mutableMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>>()
    fun actualDraft(id: String): androidx.compose.runtime.snapshots.SnapshotStateMap<String, String> = actualDrafts.getOrPut(id) {
        mutableStateMapOf<String, String>().apply {
            putAll(saved.get<HashMap<String, String>>("actual-$id") ?: hashMapOf("fare" to "", "km" to "", "min" to "", "paid" to "", "productive" to "", "extra" to "0", "zone" to repository.offers.value.firstOrNull { it.id == id }?.offer()?.destination.orEmpty(), "idle" to "", "returnKm" to "", "returnMin" to "", "arrived" to "0"))
        }
    }
    fun persistActual(id: String) { saved["actual-$id"] = HashMap(actualDraft(id)) }
    fun clearActual(id: String) { actualDrafts.remove(id); saved.remove<HashMap<String, String>>("actual-$id") }
    init {
        val draft = saved.get<HashMap<String, String>>("offerDraft")
        fresh()
        if (draft != null) {
            fields.putAll(draft)
            demo = saved.get<Boolean>("draftDemo") ?: false
            source = saved.get<String>("draftSource") ?: "Rascunho restaurado • confira os dados"
            persistDraft() // Keep the restored draft available for another process recreation.
        }
    }
    private fun persistDraft() { saved["offerDraft"] = HashMap(fields); saved["draftDemo"] = demo; saved["draftSource"] = source }
    fun message(s: String) { messages.tryEmit(s) }
    fun task(block: suspend () -> Unit) = viewModelScope.launch { try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { message(e.message ?: "Não foi possível concluir") } }
    fun fresh() {
        saved.remove<HashMap<String, String>>("offerDraft")
        generation++; zoneSampleCount = 0
        fields.clear(); fields.putAll(mapOf("fare" to "", "pickupKm" to "", "pickupMin" to "", "tripKm" to "", "tripMin" to "", "origin" to "", "destination" to "", "wait" to "3", "idle" to "5", "returnKm" to "2", "returnMin" to "5", "operational" to "0", "detourKm" to "0", "detourMin" to "0", "extra" to "0", "highway" to "0"))
        val a = repository.settings.value.defaults
        fields["wait"] = a.boardingWaitMin.toString(); fields["idle"] = a.nextOfferWaitMin.toString(); fields["returnKm"] = a.repositionKm.toString(); fields["returnMin"] = a.repositionMin.toString()
        // Imported bitmaps may still be owned by a native OCR task or Compose draw.
        // Let Android reclaim them after their last owner releases the reference.
        demo = false; source = "Manual"; analysis = null; roadRoute = null; diagnostic = null; diagnosticBitmap = null
    }
    fun change(key: String, value: String) { fields[key] = value; analysis = null; if (key in listOf("destination", "returnKm", "returnMin")) roadRoute = null; if (key in listOf("destination", "wait", "idle")) zoneSampleCount = 0; persistDraft() }
    private fun load(o: Offer) { fields["fare"] = String.format(Locale.US, "%.2f", o.fareCents / 100.0); fields["pickupKm"] = o.pickupKm.toString(); fields["pickupMin"] = o.pickupMin.toString(); fields["tripKm"] = o.tripKm.toString(); fields["tripMin"] = o.tripMin.toString(); fields["origin"] = o.origin; fields["destination"] = o.destination }
    fun example() { fresh(); demo = true; source = "Demonstração"; load(Offer(3800, 1.4, 4.0, 12.0, 24.0, destination = "Centro (exemplo)")); tab = 1; calculate() }
    fun calculate() {
        try {
            fun n(k: String) = decimal(fields[k].orEmpty())
            val quality = if (source.contains("OCR")) diagnostic?.parsed?.offer?.quality ?: ReadingQuality.ORDERED else ReadingQuality.CONFIRMED
            val o = Offer(cents(n("fare")), n("pickupKm"), n("pickupMin"), n("tripKm"), n("tripMin"), fields["origin"].orEmpty(), fields["destination"].orEmpty(), source, quality)
            var a = Assumptions(n("wait"), n("operational"), n("returnKm"), n("returnMin"), n("idle"), n("detourKm"), n("detourMin"), cents(n("extra")), n("highway") / 100, zoneSamples = zoneSampleCount)
            roadRoute?.takeIf { System.currentTimeMillis() - it.calculatedAt in 0..3_600_000 }?.let { a = RouteEconomics.applyReposition(a, it) }
            val s = repository.settings.value
            analysis = Analysis(o, a, Economics.evaluate(o, a, s.cost, s.policy), s, demo, source); persistDraft()
        } catch (e: Exception) { analysis = null; message(e.message ?: "Confira os campos numéricos") }
    }
    fun invalidateRoute() { roadRoute = null; analysis = null; zoneSampleCount = 0 }
    fun applyRoute(route: DrivingRoute, destination: String) { roadRoute = route; fields["destination"] = destination; fields["returnKm"] = route.distanceKm.toString(); fields["returnMin"] = route.durationMin.toString(); analysis = null; calculate() }
    fun learn() { try { val a = repository.learned(fields["destination"].orEmpty(), repository.settings.value.defaults); zoneSampleCount = a.zoneSamples; fields["wait"] = a.boardingWaitMin.toString(); fields["idle"] = a.nextOfferWaitMin.toString(); if (roadRoute == null) { fields["returnKm"] = a.repositionKm.toString(); fields["returnMin"] = a.repositionMin.toString() }; calculate(); message(if (a.zoneSamples >= 5) "Espera calibrada com ${a.zoneSamples} observações do mesmo período" else "Poucas observações neste período; mantidas as premissas") } catch (e: Exception) { message(e.message.orEmpty()) } }
    fun save() = task {
        val a = analysis ?: return@task
        if (a.demo) { message("Demonstração não é registrada no histórico"); return@task }
        if (a.savedId != null || savingOffer) return@task
        savingOffer = true
        try {
        val id = repository.save(a.offer, a.assumptions, a.settings.cost, a.prediction, a.source, diagnostic?.latencyMs ?: 0)
        if (analysis === a) analysis = a.copy(savedId = id)
        message("Oferta registrada. Ganhos reais entram somente após concluir a corrida")
        } finally { savingOffer = false }
    }
    fun parseText(text: String) { fresh(); analysisLatencyMs = null; demo = true; source = "OCR • demonstração"; val parsed = OfferParser.parse(text); diagnostic = OcrReading(parsed, emptyList(), 0); parsed.offer?.let { load(it); calculate() }; message(parsed.warning) }
    fun importImage(uri: Uri) = task {
        val started = android.os.SystemClock.elapsedRealtime()
        analysisLatencyMs = null
        busy = true
        try {
            fresh(); source = "OCR • imagem"; tab = 1
            val expectedGeneration = generation
            val bitmap = withContext(Dispatchers.IO) {
                val resolver = app.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Imagem inválida" }
                var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
                resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: error("Não foi possível abrir a imagem")
            }
            if (generation != expectedGeneration) return@task
            diagnosticBitmap = bitmap; val result = OcrEngine.read(bitmap)
            if (generation != expectedGeneration) return@task
            diagnostic = result
            result.parsed.offer?.let { o ->
                load(o); calculate()
                val snapshot = analysis
                val route = (app.routing.forDestination(o.destination) as? RoutingResult.Found)?.route
                if (route != null && analysis === snapshot) applyRoute(route, o.destination)
            }
            message(result.parsed.warning)
        } finally { analysisLatencyMs = android.os.SystemClock.elapsedRealtime() - started; busy = false }
    }
}
