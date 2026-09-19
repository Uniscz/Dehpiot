package br.com.deh.copiloto.routing

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import br.com.deh.copiloto.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

const val OSRM_DEFAULT = "https://routing.openstreetmap.de/routed-car"
const val VALHALLA_DEFAULT = "https://valhalla1.openstreetmap.de"
const val CLIENT_AGENT = "DehpilotPersonal/0.2 (br.com.deh.copiloto; personal Android route planner)"
fun validEndpoint(value: String): String {
    val u = URI(value.trim()); require(u.scheme == "https" && !u.host.isNullOrEmpty() && u.userInfo == null && u.query == null && u.fragment == null) { "Use uma URL HTTPS de servidor, sem credenciais ou parâmetros" }
    return value.trim().trimEnd('/')
}
class RoutingHttp {
    private val mutex = Mutex(); private var last = 0L
    suspend fun get(url: String): JSONObject = mutex.withLock {
        val elapsed = SystemClock.elapsedRealtime() - last; if (elapsed < 1500) delay(1500 - elapsed)
        last = SystemClock.elapsedRealtime()
        withContext(Dispatchers.IO) {
            val c = URL(url).openConnection() as HttpsURLConnection
            try {
                c.connectTimeout = 5000; c.readTimeout = 7000; c.instanceFollowRedirects = false
                c.setRequestProperty("User-Agent", CLIENT_AGENT); c.setRequestProperty("X-Client-Id", "br.com.deh.copiloto.personal"); c.setRequestProperty("Accept", "application/json")
                val code = c.responseCode
                if (code == 429) error("Limite do servidor. Aguarde antes de tentar novamente")
                if (code !in listOf(200, 400)) error("Servidor de rotas indisponível (HTTP $code)")
                val stream = if (code == 200) c.inputStream else c.errorStream
                val bytes = stream.use { input -> val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192); var total = 0
                    while (true) { val count = input.read(buffer); if (count < 0) break; total += count; require(total <= 5_000_000) { "Resposta de rota excedeu o limite" }; output.write(buffer, 0, count) }; output.toByteArray() }
                JSONObject(bytes.toString(Charsets.UTF_8))
            } finally { c.disconnect() }
        }
    }
}
abstract class FreeRoutingProvider : RoutingProvider {
    final override val capabilities = RouteCapabilities()
    protected suspend fun safe(block: suspend () -> DrivingRoute): RoutingResult = try { RoutingResult.Found(block()) } catch (e: CancellationException) { throw e } catch (e: Exception) { RoutingResult.Unavailable(e.message ?: "Não foi possível obter rota viária") }
}
class OsrmRoutingProvider(private val http: RoutingHttp, endpoint: String = OSRM_DEFAULT) : FreeRoutingProvider() {
    private val endpoint = validEndpoint(endpoint)
    override val id = "OSRM"
    fun url(q: RouteQuery): String {
        require(!q.avoidFerries) { "Para excluir balsas, selecione Valhalla" }
        fun coord(p: GeoPoint) = String.format(Locale.US, "%.6f,%.6f", p.longitude, p.latitude)
        return "$endpoint/route/v1/driving/${coord(q.from)};${coord(q.to)}?overview=full&geometries=geojson&steps=true&continue_straight=true&radiuses=100;100&approaches=${if (q.curbApproach) "curb;curb" else "unrestricted;unrestricted"}" + (q.departureHeading?.let { "&bearings=${it.toInt()},25;" } ?: "")
    }
    override suspend fun calculateRoute(query: RouteQuery) = safe { decode(query, http.get(url(query))) }
    fun decode(q: RouteQuery, j: JSONObject): DrivingRoute {
        require(j.optString("code") == "Ok") { when (j.optString("code")) { "NoRoute" -> "Não há rota de carro entre esses pontos"; "NoSegment" -> "Ponto distante da via: confirme a posição no mapa"; else -> "OSRM: ${j.optString("message", "resposta inválida")}" } }
        val r = j.getJSONArray("routes").getJSONObject(0)
        val geometry = coordinates(r.getJSONObject("geometry").getJSONArray("coordinates"))
        val maneuvers = mutableListOf<String>(); val refs = mutableSetOf<String>(); var highway = false; var toll: Boolean? = null
        val legs = r.getJSONArray("legs")
        for (i in 0 until legs.length()) {
            val steps = legs.getJSONObject(i).getJSONArray("steps")
            for (n in 0 until steps.length()) {
                val s = steps.getJSONObject(n); val ref = s.optString("ref"); if (ref.isNotBlank()) refs.add(ref)
                if (Regex("BR[- ]?\\d").containsMatchIn(ref)) highway = true
                val type = s.optJSONObject("maneuver")?.optString("type").orEmpty(); val mod = s.optJSONObject("maneuver")?.optString("modifier").orEmpty()
                val action = when { s.optString("mode") == "ferry" -> "Balsa: confira travessia, espera e tarifa"; mod == "uturn" -> "Retorno"; type == "on ramp" -> "Acesso à rodovia"; type == "off ramp" -> "Saída da rodovia"; type == "fork" -> "Bifurcação"; type == "roundabout" -> "Rotatória"; type == "turn" -> "Conversão ${if (mod.contains("left")) "à esquerda" else if (mod.contains("right")) "à direita" else ""}"; else -> "" }
                if (action.isNotBlank()) maneuvers.add(listOf(action, s.optString("name"), ref).filter(String::isNotBlank).joinToString(" • "))
                val intersections = s.optJSONArray("intersections") ?: JSONArray()
                for (v in 0 until intersections.length()) { val classes = intersections.getJSONObject(v).optJSONArray("classes")?.toString().orEmpty(); if (classes.contains("motorway")) highway = true; if (classes.contains("toll")) toll = true }
            }
        }
        val waypoints = j.optJSONArray("waypoints") ?: JSONArray(); var snap = 0.0
        for (i in 0 until waypoints.length()) snap = maxOf(snap, waypoints.getJSONObject(i).optDouble("distance", 0.0))
        return DrivingRoute(q, r.getDouble("distance") / 1000, r.getDouble("duration") / 60, geometry, "OSRM • ${URI(endpoint).host}", System.currentTimeMillis(), maneuvers.distinct(), refs.toList(), highway, toll, snapDistanceMeters = snap)
    }
}
class ValhallaRoutingProvider(private val http: RoutingHttp, endpoint: String = VALHALLA_DEFAULT) : FreeRoutingProvider() {
    private val endpoint = validEndpoint(endpoint); override val id = "Valhalla"
    fun url(q: RouteQuery): String {
        fun point(p: GeoPoint, heading: Double?) = JSONObject().put("lat", p.latitude).put("lon", p.longitude).put("search_cutoff", 100).put("radius", 50).put("preferred_side", if (q.curbApproach) "same" else "either").also { if (heading != null) it.put("heading", heading.toInt()).put("heading_tolerance", 25) }
        val payload = JSONObject().put("locations", JSONArray().put(point(q.from, q.departureHeading)).put(point(q.to, null))).put("costing", "auto").put("units", "kilometers").put("language", "pt-BR").put("shape_format", "geojson")
        if (q.avoidFerries) payload.put("costing_options", JSONObject().put("auto", JSONObject().put("exclude_ferries", true)))
        return "$endpoint/route?json=${URLEncoder.encode(payload.toString(), "UTF-8")}" 
    }
    override suspend fun calculateRoute(query: RouteQuery) = safe { decode(query, http.get(url(query))) }
    fun decode(q: RouteQuery, j: JSONObject): DrivingRoute {
        require(!j.has("error")) { "Valhalla: ${j.optString("error")}" }
        val trip = j.getJSONObject("trip"); require(trip.optInt("status", -1) == 0) { "Não foi possível calcular a rota de carro" }
        val summary = trip.getJSONObject("summary"); val legs = trip.getJSONArray("legs"); val points = mutableListOf<GeoPoint>(); val maneuvers = mutableListOf<String>(); val refs = mutableSetOf<String>()
        for (i in 0 until legs.length()) {
            val leg = legs.getJSONObject(i); val shape = leg.get("shape")
            points.addAll(if (shape is JSONObject) coordinates(shape.getJSONArray("coordinates")) else decodePolyline(shape.toString()))
            val steps = leg.optJSONArray("maneuvers") ?: JSONArray()
            for (n in 0 until steps.length()) { val s = steps.getJSONObject(n); val instruction = s.optString("instruction"); if (instruction.isNotBlank()) maneuvers.add(instruction); val names = s.optJSONArray("street_names") ?: JSONArray(); for (k in 0 until names.length()) { val name = names.getString(k); if (Regex("[A-Z]{2}[- ]?\\d").containsMatchIn(name)) refs.add(name) } }
        }
        val locations = trip.optJSONArray("locations") ?: JSONArray(); var snap = 0.0
        for (i in 0 until minOf(locations.length(), 2)) { val p = locations.getJSONObject(i); val ll = GeoPoint(p.getDouble("lat"), p.getDouble("lon")); snap = maxOf(snap, Geo.distance(if (i == 0) q.from else q.to, ll) * 1000) }
        return DrivingRoute(q, summary.getDouble("length"), summary.getDouble("time") / 60, points, "Valhalla • ${URI(endpoint).host}", System.currentTimeMillis(), maneuvers, refs.toList(), summary.optBoolean("has_highway") || refs.any { it.startsWith("BR") }, if (summary.has("has_toll")) summary.getBoolean("has_toll") else null, snapDistanceMeters = snap)
    }
}
fun coordinates(a: JSONArray): List<GeoPoint> = (0 until a.length()).map { val p = a.getJSONArray(it); GeoPoint(p.getDouble(1), p.getDouble(0)) }
fun decodePolyline(s: String): List<GeoPoint> {
    var index = 0; var lat = 0; var lon = 0; val out = mutableListOf<GeoPoint>()
    fun part(): Int { var result = 0; var shift = 0; var b: Int
        do { require(index < s.length && shift <= 30) { "Geometria inválida" }; b = s[index++].code - 63; result = result or ((b and 31) shl shift); shift += 5 } while (b >= 32)
        return if (result and 1 != 0) (result shr 1).inv() else result shr 1
    }
    while (index < s.length) { lat += part(); lon += part(); out.add(GeoPoint(lat / 1e6, lon / 1e6)) }
    return out
}
data class SavedPlace(val name: String, val point: GeoPoint, val heading: Double? = null)
data class RoutingSettings(val provider: String = "OSRM", val osrmEndpoint: String = OSRM_DEFAULT, val valhallaEndpoint: String = VALHALLA_DEFAULT, val enabled: Boolean = true, val avoidFerries: Boolean = false, val places: List<SavedPlace> = emptyList(), val returnPlace: String = "", val tileUrl: String = "https://tile.openstreetmap.org/{z}/{x}/{y}.png", val homePlace: String = "", val workReturnPlace: String = "", val returningHome: Boolean = false)
class RoutingRepository(context: Context) {
    private val configFile = AtomicFile(File(context.filesDir, "routing.json")); private val cacheFile = AtomicFile(File(context.cacheDir, "road-routes.json"))
    private val mutex = Mutex(); private val http = RoutingHttp()
    @Volatile var settings: RoutingSettings = loadSettings(); private set
    private val cache = linkedMapOf<String, DrivingRoute>()
    init { runCatching { val j = JSONObject(cacheFile.openRead().bufferedReader().use { it.readText() }); j.keys().forEach { key -> val r = decodeRoute(j.getJSONObject(key)); if (System.currentTimeMillis() - r.calculatedAt in 0..3_600_000) cache[key] = r } } }
    private fun write(file: AtomicFile, text: String) { val stream = file.startWrite(); try { stream.write(text.toByteArray()); file.finishWrite(stream) } catch (e: Exception) { file.failWrite(stream); throw e } }
    private fun point(p: GeoPoint) = JSONArray().put(p.longitude).put(p.latitude)
    private fun place(p: SavedPlace) = JSONObject().put("name", p.name).put("point", point(p.point)).put("heading", p.heading)
    private fun loadSettings(): RoutingSettings = runCatching {
        val j = JSONObject(configFile.openRead().bufferedReader().use { it.readText() }); val a = j.optJSONArray("places") ?: JSONArray()
        RoutingSettings(j.optString("provider", "OSRM"), j.optString("osrm", OSRM_DEFAULT), j.optString("valhalla", VALHALLA_DEFAULT), j.optBoolean("enabled", true), j.optBoolean("avoidFerries"), (0 until a.length()).map { val p = a.getJSONObject(it); val ll = p.getJSONArray("point"); SavedPlace(p.getString("name"), GeoPoint(ll.getDouble(1), ll.getDouble(0)), if (p.has("heading") && !p.isNull("heading")) p.getDouble("heading") else null) }, j.optString("return"), j.optString("tiles", "https://tile.openstreetmap.org/{z}/{x}/{y}.png"), j.optString("home"), j.optString("workReturn"), j.optBoolean("returningHome"))
    }.getOrDefault(RoutingSettings())
    @Synchronized fun save(requested: RoutingSettings) {
        // A manually changed return point must not silently keep claiming home mode.
        val s = if (requested.returningHome && (requested.returnPlace != requested.homePlace || requested.places.none { it.name == requested.homePlace }))
            requested.copy(returningHome = false, workReturnPlace = "") else requested
        validEndpoint(s.osrmEndpoint); validEndpoint(s.valhallaEndpoint)
        require(URI(s.tileUrl.replace("{z}", "0").replace("{x}", "0").replace("{y}", "0")).scheme == "https") { "Mapa precisa usar HTTPS" }
        require(s.places.all { it.name.isNotBlank() && (it.heading == null || it.heading in 0.0..<360.0) }); require(s.places.map { normalized(it.name) }.distinct().size == s.places.size)
        val j = JSONObject().put("provider", s.provider).put("osrm", s.osrmEndpoint).put("valhalla", s.valhallaEndpoint).put("enabled", s.enabled).put("avoidFerries", s.avoidFerries).put("places", JSONArray(s.places.map(::place))).put("return", s.returnPlace).put("tiles", s.tileUrl).put("home", s.homePlace).put("workReturn", s.workReturnPlace).put("returningHome", s.returningHome)
        write(configFile, j.toString()); settings = s
    }
    fun beginHome() {
        val current = settings
        require(resolve(current.homePlace) != null) { "Escolha um ponto confirmado como casa no mapa" }
        save(current.copy(returnPlace = current.homePlace, workReturnPlace = if (current.returningHome) current.workReturnPlace else current.returnPlace, returningHome = true))
    }
    fun endHome() {
        val current = settings
        if (current.returningHome) save(current.copy(returnPlace = current.workReturnPlace, workReturnPlace = "", returningHome = false))
    }
    fun resolve(name: String): SavedPlace? = settings.places.singleOrNull { normalized(it.name) == normalized(name) }
    fun key(q: RouteQuery, s: RoutingSettings = settings): String = "${s.provider}|${if (s.provider == "Valhalla") s.valhallaEndpoint else s.osrmEndpoint}|${q.from}|${q.to}|${q.departureHeading}|${q.curbApproach}|${q.avoidFerries}"
    suspend fun route(q: RouteQuery): RoutingResult = mutex.withLock {
        val s = settings; val key = key(q, s)
        cache[key]?.takeIf { System.currentTimeMillis() - it.calculatedAt in 0..3_600_000 }?.let { return@withLock RoutingResult.Found(it.copy(cached = true)) }
        if (!s.enabled) return@withLock RoutingResult.Unavailable("Rotas online desativadas e nenhuma rota viária válida em cache")
        val p: RoutingProvider = if (s.provider == "Valhalla") ValhallaRoutingProvider(http, s.valhallaEndpoint) else OsrmRoutingProvider(http, s.osrmEndpoint)
        val result = p.calculateRoute(q)
        if (result is RoutingResult.Found) {
            cache[key] = result.route; while (cache.size > 60) cache.remove(cache.keys.first())
            withContext(Dispatchers.IO) { runCatching { write(cacheFile, JSONObject(cache.mapValues { encodeRoute(it.value) }).toString()) } }
        }
        result
    }
    suspend fun forDestination(name: String): RoutingResult {
        val from = resolve(name) ?: return RoutingResult.Unavailable("Destino sem coordenadas confirmadas: abra Rotas e confirme o ponto", false)
        val to = resolve(settings.returnPlace) ?: return RoutingResult.Unavailable("Defina uma região de retorno em Rotas", false)
        return route(RouteQuery(from.point, to.point, from.heading, avoidFerries = settings.avoidFerries))
    }
    suspend fun clearCache() = mutex.withLock { cache.clear(); cacheFile.delete() }
    private fun encodeRoute(r: DrivingRoute) = JSONObject().put("from", point(r.query.from)).put("to", point(r.query.to)).put("heading", r.query.departureHeading).put("curb", r.query.curbApproach).put("ferries", r.query.avoidFerries).put("distance", r.distanceKm).put("duration", r.durationMin).put("geometry", JSONArray(r.geometry.map(::point))).put("provider", r.provider).put("time", r.calculatedAt).put("maneuvers", JSONArray(r.maneuvers)).put("refs", JSONArray(r.roadRefs)).put("highway", r.highway).put("toll", r.tollRoad).put("snap", r.snapDistanceMeters)
    private fun decodeRoute(j: JSONObject): DrivingRoute {
        fun p(key: String): GeoPoint { val a = j.getJSONArray(key); return GeoPoint(a.getDouble(1), a.getDouble(0)) }
        fun strings(key: String): List<String> { val a = j.optJSONArray(key) ?: JSONArray(); return (0 until a.length()).map(a::getString) }
        val q = RouteQuery(p("from"), p("to"), if (j.has("heading") && !j.isNull("heading")) j.getDouble("heading") else null, j.optBoolean("curb", true), j.optBoolean("ferries"))
        return DrivingRoute(q, j.getDouble("distance"), j.getDouble("duration"), coordinates(j.getJSONArray("geometry")), j.getString("provider"), j.getLong("time"), strings("maneuvers"), strings("refs"), j.optBoolean("highway"), if (j.has("toll") && !j.isNull("toll")) j.getBoolean("toll") else null, snapDistanceMeters = j.optDouble("snap", 0.0))
    }
}
