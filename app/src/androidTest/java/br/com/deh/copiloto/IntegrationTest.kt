package br.com.deh.copiloto

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.deh.copiloto.capture.OcrEngine
import br.com.deh.copiloto.core.*
import br.com.deh.copiloto.data.*
import br.com.deh.copiloto.routing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun query(avoidFerries: Boolean = false) = RouteQuery(GeoPoint(-26.900067, -48.691499), GeoPoint(-26.906382, -48.648100), avoidFerries = avoidFerries)
    private fun fixture(name: String) = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open(name).bufferedReader().use { it.readText() })
    @Test fun embeddedOcrReadsOfferWithoutModelDownload() = runBlocking {
        val bitmap = Bitmap.createBitmap(900, 700, Bitmap.Config.ARGB_8888); val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE); val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 42f }
        listOf("UberX", "R$ 38,00", "Busca 4 min (1,4 km)", "Viagem 24 min (12 km)", "Destino: Centro", "Aceitar").forEachIndexed { i, text -> canvas.drawText(text, 40f, 80f + i * 95, paint) }
        try { val r = OcrEngine.read(bitmap); assertNotNull(r.parsed.warning + "\n" + r.parsed.text, r.parsed.offer); val o = r.parsed.offer!!; assertEquals(3800L, o.fareCents); assertEquals(1.4, o.pickupKm, .001); assertEquals(12.0, o.tripKm, .001); assertEquals(24.0, o.tripMin, .001) } finally { bitmap.recycle() }
    }
    @Test fun osrmRealResponsePreservesRoadAndFerry() {
        val r = OsrmRoutingProvider(RoutingHttp()).decode(query(), fixture("osrm-live.json"))
        assertEquals(7.1436, r.distanceKm, 1e-6); assertEquals(1060.1 / 60, r.durationMin, 1e-6); assertTrue(r.geometry.size > 100); assertTrue(r.maneuvers.any { it.contains("Balsa") }); assertFalse(r.trafficIncluded)
    }
    @Test fun valhallaRealDetourFeedsEconomicCycle() {
        val provider = ValhallaRoutingProvider(RoutingHttp()); val r = provider.decode(query(true), fixture("valhalla-live.json"))
        assertEquals(21.71, r.distanceKm, 1e-6); assertEquals(2042.662 / 60, r.durationMin, 1e-6); assertTrue(r.highway); assertTrue(r.geometry.size > 100); assertTrue(r.difficultAccess); assertTrue(r.straightLineKm < 5)
        val a = RouteEconomics.applyReposition(Assumptions(), r); val p = Economics.evaluate(Offer(5000, 1.0, 3.0, 10.0, 20.0), a, VehicleCost(simplePerKm = 1.0), Policy())
        assertEquals(32.71, p.totalKm, 1e-6); assertEquals(3271L, p.variableCostCents); assertNotNull(p.destinationScore); assertTrue(a.accessWarning)
    }
    @Test fun missingRoadNeverBecomesGeographicFallback() = runBlocking {
        val repo = RoutingRepository(context); val previous = repo.settings
        try { repo.clearCache(); repo.save(previous.copy(enabled = false)); val r = repo.route(query()); assertTrue(r is RoutingResult.Unavailable) } finally { repo.save(previous) }
    }
    @Test fun publicProvidersDoNotClaimTrafficOrTollPrices() {
        listOf(OsrmRoutingProvider(RoutingHttp()), ValhallaRoutingProvider(RoutingHttp())).forEach { assertFalse(it.supportsTraffic()); assertFalse(it.supportsTolls()); assertTrue(it.capabilities.turnRestrictions); assertTrue(it.capabilities.geometry) }
    }
    @Test fun cacheKeysIncludeDirectionHeadingAndProvider() {
        val repo = RoutingRepository(context); val q = query(); assertNotEquals(repo.key(q), repo.key(q.copy(from = q.to, to = q.from))); assertNotEquals(repo.key(q), repo.key(q.copy(departureHeading = 180.0))); assertNotEquals(repo.key(q), repo.key(q.copy(avoidFerries = true))); assertNotEquals(repo.key(q, RoutingSettings(provider = "OSRM")), repo.key(q, RoutingSettings(provider = "Valhalla")))
    }
    @Test fun noSegmentIsRejected() { try { OsrmRoutingProvider(RoutingHttp()).decode(query(), JSONObject("{\"code\":\"NoSegment\"}")); fail("NoSegment must fail") } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("Ponto")) } }
    @Test fun settingsRoundTripRetainsRouteProvenanceAndCosts() {
        val route = ValhallaRoutingProvider(RoutingHttp()).decode(query(true), fixture("valhalla-live.json")); val s = Settings(cost = VehicleCost(detailed = true, fixedPerHour = 12.0), defaults = RouteEconomics.applyReposition(Assumptions(), route), voice = true)
        assertEquals(s, Codec.settings(JSONObject(Codec.settings(s).toString())))
    }
    @Test fun completionIsAtomicAndDoesNotDuplicateOrOverwrite() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO); val repo = Repository(db, SettingsStore(context), scope)
        try {
            val o = Offer(3800, 1.4, 4.0, 12.0, 24.0); val a = Assumptions(); val c = VehicleCost(simplePerKm = 1.0); val p = Economics.evaluate(o, a, c, Policy()); val id = repo.save(o, a, c, p, "TEST")
            assertTrue(db.dao().rides().first().isEmpty()); repo.complete(id, 4000, 20.0, 45.0, 12.0, 24.0, 200, "Centro", 3.0, 6.0, 10.0)
            val ride = db.dao().rides().first().single(); assertEquals(1800L, ride.netCents); assertEquals("COMPLETED", db.dao().get(id)!!.status)
            assertEquals(0, db.dao().enrich(id, "{}", 9, 9.0, 9.0, 99, 9.0, 9.0))
            try { repo.complete(id, 5000, 20.0, 45.0, 12.0, 24.0, 0, "Centro", null, null, null); fail("Duplicate must fail") } catch (_: IllegalArgumentException) { }
            assertEquals(1, db.dao().rides().first().size); assertEquals(1800L, db.dao().rides().first().single().netCents)
        } finally { scope.cancel(); db.close() }
    }
}
