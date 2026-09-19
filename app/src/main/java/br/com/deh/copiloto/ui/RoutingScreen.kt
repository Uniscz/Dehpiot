package br.com.deh.copiloto.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.*
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.deh.copiloto.AppViewModel
import br.com.deh.copiloto.core.*
import br.com.deh.copiloto.routing.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.*
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.module.http.HttpRequestUtil
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

@Composable fun RoutingPanel(vm: AppViewModel, apply: (DrivingRoute, String) -> Unit) {
    val repo = vm.app.routing; val context = LocalContext.current; val scope = rememberCoroutineScope()
    var coordinatesOpen by rememberSaveable { mutableStateOf(false) }
    var config by remember { mutableStateOf(repo.settings) }
    var fromName by rememberSaveable { mutableStateOf(vm.fields["destination"].orEmpty()) }; var toName by rememberSaveable { mutableStateOf(config.returnPlace) }
    val initialFrom = remember { repo.resolve(fromName) }; val initialTo = remember { repo.resolve(toName) }
    var fromLat by rememberSaveable { mutableStateOf(initialFrom?.point?.latitude?.toString().orEmpty()) }; var fromLon by rememberSaveable { mutableStateOf(initialFrom?.point?.longitude?.toString().orEmpty()) }
    var toLat by rememberSaveable { mutableStateOf(initialTo?.point?.latitude?.toString().orEmpty()) }; var toLon by rememberSaveable { mutableStateOf(initialTo?.point?.longitude?.toString().orEmpty()) }
    var heading by rememberSaveable { mutableStateOf(initialFrom?.heading?.toString().orEmpty()) }; var editingFrom by rememberSaveable { mutableStateOf(true) }; var showMap by rememberSaveable { mutableStateOf(false) }
    var route by remember { mutableStateOf<DrivingRoute?>(null) }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }; var job by remember { mutableStateOf<Job?>(null) }
    var addresses by remember { mutableStateOf<List<Address>>(emptyList()) }; var settingsOpen by remember { mutableStateOf(false) }
    fun invalidate() { route = null; error = ""; busy = false; job?.cancel() }
    fun pick(p: GeoPoint) { invalidate(); if (editingFrom) { fromLat = p.latitude.toString(); fromLon = p.longitude.toString() } else { toLat = p.latitude.toString(); toLon = p.longitude.toString() } }
    fun saveConfig(s: RoutingSettings) { try { repo.save(s); config = s; invalidate() } catch (e: Exception) { vm.message(e.message.orEmpty()) } }
    fun point(from: Boolean): GeoPoint = GeoPoint(decimal(if (from) fromLat else toLat), decimal(if (from) fromLon else toLon))
    fun currentPoint(from: Boolean) = runCatching { point(from) }.getOrNull()
    Section("RETORNO VIÁRIO REAL") {
        Note("Do destino da corrida até a região para reposicionar ou encerrar o turno. A distância em linha reta serve apenas para comparar o acesso.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(editingFrom, { editingFrom = true }, label = { Text("Destino da corrida") }); FilterChip(!editingFrom, { editingFrom = false }, label = { Text("Região de retorno") }) }
        Field(if (editingFrom) "Nome/endereço do destino" else "Nome/endereço da região ou casa", if (editingFrom) fromName else toName, { invalidate(); addresses = emptyList(); if (editingFrom) { fromName = it; fromLat = ""; fromLon = "" } else { toName = it; toLat = ""; toLon = "" } }, false)
        if (Geocoder.isPresent()) OutlinedButton({
            val query = if (editingFrom) fromName else toName; val from = editingFrom
            if (query.isNotBlank()) { busy = true; job = scope.launch { try { val found = withContext(Dispatchers.IO) { @Suppress("DEPRECATION") Geocoder(context, java.util.Locale("pt", "BR")).getFromLocationName(query, 4).orEmpty() }; if (editingFrom == from) addresses = found; if (found.isEmpty()) error = "Endereço não encontrado; confirme coordenadas no mapa" } catch (e: CancellationException) { throw e } catch (e: Exception) { error = "Busca indisponível: informe coordenadas ou escolha no mapa" } finally { busy = false } } }
        }, enabled = !busy) { Text("Buscar endereço e escolher resultado") }
        addresses.forEach { a -> OutlinedButton({ pick(GeoPoint(a.latitude, a.longitude)); addresses = emptyList(); showMap = true }, Modifier.fillMaxWidth()) { Text(a.getAddressLine(0).orEmpty()) } }
        TextButton({ coordinatesOpen = !coordinatesOpen }) { Text(if (coordinatesOpen) "Ocultar coordenadas" else "Coordenadas e sentido da via") }
        if (coordinatesOpen) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Field("Latitude", if (editingFrom) fromLat else toLat, { invalidate(); if (editingFrom) fromLat = it else toLat = it }, modifier = Modifier.weight(1f)); Field("Longitude", if (editingFrom) fromLon else toLon, { invalidate(); if (editingFrom) fromLon = it else toLon = it }, modifier = Modifier.weight(1f)) }
        if (editingFrom) { Field("Sentido de saída • 0–359° (opcional)", heading, { invalidate(); heading = it }); Note("Em pista dividida, confirme o lado correto e informe o sentido quando conhecido. 0° norte, 90° leste, 180° sul, 270° oeste.") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({ showMap = !showMap }) { Text(if (showMap) "Fechar mapa" else "Abrir mapa") }; LocationButton(vm) { pick(it); showMap = true } }
        if (showMap) { Note("Toque no ponto exato ${if (editingFrom) "do desembarque" else "da região de retorno"}. Marcadores: início verde e retorno azul."); RoadMap(config.tileUrl, currentPoint(true), currentPoint(false), route, ::pick) }
        OutlinedButton({
            try { val name = (if (editingFrom) fromName else toName).trim(); require(name.isNotEmpty()) { "Dê um nome ao ponto" }; val p = SavedPlace(name, point(editingFrom), if (editingFrom && heading.isNotBlank()) decimal(heading) else null); val places = config.places.filterNot { normalized(it.name) == normalized(name) } + p; saveConfig(config.copy(places = places, returnPlace = if (editingFrom) config.returnPlace else name)); vm.message("Ponto confirmado salvo") } catch (e: Exception) { vm.message(e.message ?: "Confira as coordenadas") }
        }) { Text(if (editingFrom) "Salvar destino confirmado" else "Salvar como região de retorno") }
        if (config.places.isNotEmpty()) { Note("Pontos confirmados"); config.places.forEach { p -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({ invalidate(); if (editingFrom) { fromName = p.name; fromLat = p.point.latitude.toString(); fromLon = p.point.longitude.toString(); heading = p.heading?.toString().orEmpty() } else { toName = p.name; toLat = p.point.latitude.toString(); toLon = p.point.longitude.toString(); saveConfig(config.copy(returnPlace = p.name)) } }, Modifier.weight(1f)) { Text(p.name) }; TextButton({ saveConfig(config.copy(places = config.places.filterNot { it.name == p.name }, returnPlace = if (config.returnPlace == p.name) "" else config.returnPlace)) }) { Text("Excluir") } } } }
        Toggle("Evitar balsas (Valhalla)", config.avoidFerries) { saveConfig(config.copy(avoidFerries = it, provider = if (it) "Valhalla" else config.provider)) }
        Action(if (busy) "Consultando rede viária…" else "Calcular rota de carro", !busy) {
            try {
                val query = RouteQuery(point(true), point(false), heading.takeIf(String::isNotBlank)?.let(::decimal), true, config.avoidFerries)
                route = null; error = ""; busy = true
                job = scope.launch { try { when (val result = repo.route(query)) { is RoutingResult.Found -> { route = result.route; showMap = true }; is RoutingResult.Unavailable -> error = result.reason } } finally { busy = false } }
            } catch (e: Exception) { vm.message(e.message ?: "Informe os dois pontos") }
        }
        if (error.isNotBlank()) Note("$error. A análise regional permanece incompleta; não substituímos a rota por distância geográfica.", Amber)
        route?.let { r ->
            Text("${fmt(r.distanceKm, 2)} km por vias • ${fmt(r.durationMin, 0)} min", color = Lime, style = MaterialTheme.typography.titleLarge)
            Note("Proximidade geográfica: ${fmt(r.straightLineKm, 2)} km${r.accessRatio?.let { " • acesso ${fmt(it, 1)}×" }.orEmpty()}\n${r.provider}${if (r.cached) " • cache válido" else ""}")
            if (r.difficultAccess) Note("ACESSO DIFÍCIL: estar perto no mapa exige ${fmt(r.distanceKm, 1)} km reais de condução. Esses quilômetros entram nos cálculos.", Amber)
            if (r.snapDistanceMeters > 30) Note("O motor ajustou um ponto em até ${fmt(r.snapDistanceMeters, 0)} m para encontrar a via. Confira o lado da pista.", Amber)
            Note("Duração estimada sem trânsito ao vivo. ${if (r.tollRoad == true) "Há indicação de via com pedágio. " else ""}Tarifas confiáveis de pedágio/balsa não estão incluídas: informe despesas não reembolsadas no campo Extra.")
            r.maneuvers.take(20).forEach { Note("• $it") }
            Action("Usar rota no cálculo da corrida") { apply(r, fromName.ifBlank { "Destino confirmado" }) }
        }
        TextButton({ settingsOpen = !settingsOpen }) { Text("Provedor e conexão") }
        if (settingsOpen) {
            Toggle("Consultar rotas online", config.enabled) { saveConfig(config.copy(enabled = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("OSRM", "Valhalla").forEach { p -> FilterChip(config.provider == p, { saveConfig(config.copy(provider = p, avoidFerries = if (p == "OSRM") false else config.avoidFerries)) }, label = { Text(p) }) } }
            var osrm by remember { mutableStateOf(config.osrmEndpoint) }; var valhalla by remember { mutableStateOf(config.valhallaEndpoint) }; var tiles by remember { mutableStateOf(config.tileUrl) }
            Field("Servidor OSRM • HTTPS", osrm, { osrm = it }, false); Field("Servidor Valhalla • HTTPS", valhalla, { valhalla = it }, false); Field("Mapa • template HTTPS z/x/y", tiles, { tiles = it }, false)
            OutlinedButton({ saveConfig(config.copy(osrmEndpoint = osrm, valhallaEndpoint = valhalla, tileUrl = tiles)) }) { Text("Salvar servidores") }
            OutlinedButton({ scope.launch { repo.clearCache(); vm.message("Cache de rotas apagado") } }) { Text("Limpar cache de rotas") }
            Note("Serviços públicos têm limite de uso e podem ficar indisponíveis. Requisições são espaçadas e rotas ficam em cache por 1 hora. Você pode configurar um servidor próprio compatível.")
        }
        TextButton({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright"))) }) { Text("© OpenStreetMap contributors • MapLibre") }
        TextButton({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/fixthemap"))) }) { Text("Informar problema nos dados do mapa") }
    }
}
@Composable private fun LocationButton(vm: AppViewModel, pick: (GeoPoint) -> Unit) {
    val context = LocalContext.current; val manager = remember { context.getSystemService(LocationManager::class.java) }; val scope = rememberCoroutineScope(); var listener by remember { mutableStateOf<LocationListener?>(null) }; var waiting by remember { mutableStateOf(false) }; val currentPick by rememberUpdatedState(pick)
    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { vm.message("Autorize localização precisa para confirmar o lado da via"); return }
        try { val provider = if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER; waiting = true
            val l = object : LocationListener { override fun onLocationChanged(location: Location) { listener?.let(manager::removeUpdates); listener = null; waiting = false; currentPick(GeoPoint(location.latitude, location.longitude)); if (location.accuracy > 50) vm.message("GPS com precisão de ${location.accuracy.toInt()} m: confirme o ponto no mapa") }; override fun onProviderEnabled(provider: String) {}; override fun onProviderDisabled(provider: String) {}; @Deprecated("Deprecated in Java") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {} }
            listener = l; manager.requestLocationUpdates(provider, 0, 0f, l, Looper.getMainLooper()); scope.launch { delay(15000); if (listener === l) { manager.removeUpdates(l); listener = null; waiting = false; vm.message("GPS sem posição recente; selecione no mapa") } }
        } catch (e: Exception) { waiting = false; vm.message("Ative a localização ou selecione o ponto no mapa") }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { start() }
    DisposableEffect(Unit) { onDispose { listener?.let(manager::removeUpdates) } }
    OutlinedButton({ if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) start() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }, enabled = !waiting) { Text(if (waiting) "GPS…" else "Meu GPS") }
}
private object MapNetwork {
    private var ready = false
    @Synchronized fun setup(context: Context) { if (!ready) { HttpRequestUtil.setOkHttpClient(OkHttpClient.Builder().cache(Cache(File(context.cacheDir, "map-http"), 50L * 1024 * 1024)).addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", CLIENT_AGENT).build()) }.build()); ready = true } }
}
data class PersonalPin(val point: GeoPoint, val color: String, val name: String)
@Composable fun RoadMap(tiles: String, from: GeoPoint?, to: GeoPoint?, route: DrivingRoute?, pick: (GeoPoint) -> Unit, personal: List<PersonalPin> = emptyList()) {
    val context = LocalContext.current; val owner = LocalLifecycleOwner.current; val selected by rememberUpdatedState(pick)
    val view = remember { MapLibre.getInstance(context); MapNetwork.setup(context); MapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }; var ready by remember { mutableStateOf(false) }
    DisposableEffect(view, owner) {
        val observer = LifecycleEventObserver { _, event -> when (event) { Lifecycle.Event.ON_START -> view.onStart(); Lifecycle.Event.ON_RESUME -> view.onResume(); Lifecycle.Event.ON_PAUSE -> view.onPause(); Lifecycle.Event.ON_STOP -> view.onStop(); else -> Unit } }
        owner.lifecycle.addObserver(observer); if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStart(); if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onResume()
        onDispose { owner.lifecycle.removeObserver(observer); view.onPause(); view.onStop(); view.onDestroy() }
    }
    AndroidView(factory = { view.apply { getMapAsync { m -> map = m; m.setPrefetchesTiles(false); m.addOnMapClickListener { selected(GeoPoint(it.latitude, it.longitude)); true } } } }, modifier = Modifier.fillMaxWidth().height(320.dp))
    LaunchedEffect(map, tiles) {
        map?.let { m ->
            ready = false
            val style = JSONObject().put("version", 8).put("sources", JSONObject().put("osm", JSONObject().put("type", "raster").put("tiles", JSONArray().put(tiles)).put("tileSize", 256).put("attribution", "© OpenStreetMap contributors"))).put("layers", JSONArray().put(JSONObject().put("id", "osm").put("type", "raster").put("source", "osm")))
            m.setStyle(Style.Builder().fromJson(style.toString())) { s ->
                s.addSource(GeoJsonSource("route", "{\"type\":\"FeatureCollection\",\"features\":[]}"))
                s.addLayer(org.maplibre.android.style.layers.LineLayer("road", "route").withProperties(org.maplibre.android.style.layers.PropertyFactory.lineColor("#C7F66B"), org.maplibre.android.style.layers.PropertyFactory.lineWidth(5f)).withFilter(org.maplibre.android.style.expressions.Expression.eq(org.maplibre.android.style.expressions.Expression.geometryType(), org.maplibre.android.style.expressions.Expression.literal("LineString"))))
                s.addLayer(org.maplibre.android.style.layers.CircleLayer("points", "route").withProperties(org.maplibre.android.style.layers.PropertyFactory.circleRadius(7f), org.maplibre.android.style.layers.PropertyFactory.circleColor(org.maplibre.android.style.expressions.Expression.get("color")), org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor("#FFFFFF"), org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth(2f)).withFilter(org.maplibre.android.style.expressions.Expression.eq(org.maplibre.android.style.expressions.Expression.geometryType(), org.maplibre.android.style.expressions.Expression.literal("Point"))))
                ready = true
            }
        }
    }
    LaunchedEffect(map, ready, from, to, route, personal) {
        if (!ready) return@LaunchedEffect
        val m = map ?: return@LaunchedEffect; val features = JSONArray()
        fun point(p: GeoPoint) = JSONArray().put(p.longitude).put(p.latitude)
        fun feature(type: String, coords: JSONArray, color: String = "#C7F66B") = JSONObject().put("type", "Feature").put("properties", JSONObject().put("color", color)).put("geometry", JSONObject().put("type", type).put("coordinates", coords))
        personal.forEach { features.put(feature("Point", point(it.point), it.color)) }
        route?.let { features.put(feature("LineString", JSONArray(it.geometry.map(::point)))) }
        from?.let { features.put(feature("Point", point(it))) }; to?.let { features.put(feature("Point", point(it), "#7EC8FF")) }
        m.style?.getSourceAs<GeoJsonSource>("route")?.setGeoJson(JSONObject().put("type", "FeatureCollection").put("features", features).toString())
        val points = route?.geometry ?: (listOfNotNull(from, to) + personal.map { it.point })
        if (points.size > 1 && points.distinct().size > 1) m.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points.map { LatLng(it.latitude, it.longitude) }).build(), 40))
        else { val p = points.firstOrNull() ?: GeoPoint(-26.9, -48.66); m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.latitude, p.longitude), if (points.isEmpty()) 8.0 else 15.0)) }
    }
}
