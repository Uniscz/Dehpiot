package br.com.deh.copiloto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.deh.copiloto.capture.OcrEngine
import br.com.deh.copiloto.core.*
import br.com.deh.copiloto.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Use a disposable emulator: preference tests restore settings, but append cost-history entries. */
@RunWith(AndroidJUnit4::class)
class DehpilotIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)
    @Test fun migratesExistingSessionsWithoutInventingHistoricalFixedPrice() {
        migration.createDatabase("dehpilot-migration", 1).apply {
            execSQL("INSERT INTO sessions(id, start, `end`) VALUES('existing', 1000, 2000)")
            close()
        }
        migration.runMigrationsAndValidate("dehpilot-migration", 2, true, AppDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT id, fixedPerHour FROM sessions").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals("existing", cursor.getString(0)); assertTrue(cursor.isNull(1)) }
        }
    }
    @Test fun fuelAndUnitCostSurviveSettingsSerialization() {
        val initial = Settings(cost = Fuel.update(VehicleCost(), 4.29, Fuel.kmPerLiter(8.0, ConsumptionUnit.L_100KM)), goalDeadline = 123456, fuelName = "Etanol")
        val restored = Codec.settings(JSONObject(Codec.settings(initial).toString()))
        assertEquals(initial, restored); assertEquals(4.29 / 12.5, restored.cost.rate(), 1e-8)
    }
    @Test fun legacySimpleCostIsNotReinterpretedAsFuel() {
        val legacy = JSONObject("{\"simple\":1.12,\"fuel\":4.29,\"city\":9.4}")
        val decoded = Codec.cost(legacy)
        assertFalse(decoded.fuelBasedSimple); assertEquals(1.12, decoded.rate(), 0.0)
    }
    @Test fun priceConsumptionAndCostHistoryArePersistedTogether() = runBlocking {
        val store = SettingsStore(context); val original = store.flow.first()
        try {
            store.save(original.copy(cost = Fuel.update(original.cost, 4.29, 9.4)))
            val loaded = withTimeout(5000) { store.flow.first { it.cost.fuelPrice == 4.29 && it.cost.cityKml == 9.4 } }
            assertEquals(4.29, loaded.cost.fuelPrice, 0.0); assertEquals(9.4, loaded.cost.cityKml, 0.0)
            val history = store.costHistory.first(); assertEquals(loaded.cost, history.last().cost)
        } finally { store.save(original) }
    }
    @Test fun embeddedPanelOcrIsSeparateFromOfferParser() = runBlocking {
        val bitmap = Bitmap.createBitmap(900, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 64f }
        canvas.drawText("Consumo medio", 40f, 100f, paint); canvas.drawText("9,4 km/L", 40f, 220f, paint)
        try {
            val text = OcrEngine.readPanelText(bitmap); val reading = PanelParser.parse(text)
            assertEquals(text, 1, reading.candidates.size); assertEquals(9.4, reading.candidates.single().kmPerLiter, .001)
            assertNull(OfferParser.parse(text).offer)
        } finally { bitmap.recycle() }
    }
    @Test fun screenshot99ReadsAmountAndBothSegments() = runBlocking {
        val bitmap = InstrumentationRegistry.getInstrumentation().context.assets.open("offer-99-user.jpg").use { BitmapFactory.decodeStream(it) }
        requireNotNull(bitmap)
        try {
            val result = OcrEngine.read(bitmap)
            assertNotNull(result.parsed.text, result.parsed.offer)
            val offer = result.parsed.offer!!
            assertEquals(3250L, offer.fareCents); assertEquals(2.3, offer.pickupKm, .001); assertEquals(21.6, offer.tripKm, .001)
        } finally { bitmap.recycle() }
    }
    @Test fun newFuelDoesNotRepriceOldOfferAtCompletion() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = SettingsStore(context); val original = store.flow.first(); val repo = Repository(db, store, scope)
        try {
            val offer = Offer(5000, 1.0, 3.0, 10.0, 20.0); val assumptions = Assumptions()
            val oldCost = Fuel.update(VehicleCost(), 4.29, 9.4)
            val id = repo.save(offer, assumptions, oldCost, Economics.evaluate(offer, assumptions, oldCost, Policy()), "TEST")
            store.save(original.copy(cost = Fuel.update(VehicleCost(), 7.0, 8.0)))
            repo.complete(id, 5000, 20.0, 50.0, 10.0, 20.0, 0, "Centro", null, null, null)
            assertEquals(cents(20 * (4.29 / 9.4)), db.dao().rides().first().single().variableCostCents)
        } finally { store.save(original); scope.cancel(); db.close() }
    }
    @Test fun repeatedStartAndEndDoNotCreateOrCloseUnexpectedSessions() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO); val repo = Repository(db, SettingsStore(context), scope)
        try {
            repo.startSession(); repo.startSession()
            assertEquals(1, db.dao().sessions().first().size)
            repo.endSession(); repo.endSession()
            assertNull(db.dao().active()); assertEquals(1, db.dao().sessions().first().size)
        } finally { scope.cancel(); db.close() }
    }
    @Test fun homeModePersistsAndRestoresWorkRegion() {
        val repo = br.com.deh.copiloto.routing.RoutingRepository(context); val previous = repo.settings
        try {
            val places = listOf(br.com.deh.copiloto.routing.SavedPlace("Trabalho teste", GeoPoint(-26.9, -48.68)), br.com.deh.copiloto.routing.SavedPlace("Casa teste", GeoPoint(-26.8, -48.65)))
            repo.save(previous.copy(places = places, returnPlace = "Trabalho teste", homePlace = "Casa teste", returningHome = false, workReturnPlace = ""))
            repo.beginHome(); repo.beginHome()
            val restored = br.com.deh.copiloto.routing.RoutingRepository(context)
            assertTrue(restored.settings.returningHome); assertEquals("Casa teste", restored.settings.returnPlace)
            restored.endHome(); assertEquals("Trabalho teste", restored.settings.returnPlace)
        } finally { repo.save(previous) }
    }
    @Test fun restoredOfferDraftSurvivesAnotherViewModelCreation() {
        val app = context.applicationContext as CopilotoApp
        val state = androidx.lifecycle.SavedStateHandle(mapOf("offerDraft" to hashMapOf("fare" to "52,00"), "draftDemo" to false, "draftSource" to "Manual"))
        val first = AppViewModel(app, state)
        assertEquals("52,00", first.fields["fare"])
        val next = AppViewModel(app, state)
        assertEquals("52,00", next.fields["fare"])
    }
    @Test fun changingReturnPointDoesNotFalselyClaimHomeMode() {
        val repo = br.com.deh.copiloto.routing.RoutingRepository(context); val original = repo.settings
        try {
            val places = listOf(br.com.deh.copiloto.routing.SavedPlace("Casa teste", GeoPoint(-26.8, -48.65)), br.com.deh.copiloto.routing.SavedPlace("Trabalho teste", GeoPoint(-26.9, -48.68)))
            repo.save(original.copy(places = places, homePlace = "Casa teste", returnPlace = "Trabalho teste", returningHome = true))
            assertFalse(repo.settings.returningHome)
        } finally { repo.save(original) }
    }
    @Test fun sessionPersistsFixedRateAtStart() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO); val store = SettingsStore(context); val original = store.flow.first()
        val repo = Repository(db, store, scope)
        try {
            store.save(original.copy(cost = original.cost.copy(fixedPerHour = 12.0)))
            withTimeout(5000) { repo.settings.first { it.cost.fixedPerHour == 12.0 } }
            repo.toggleSession(); assertEquals(12.0, db.dao().active()!!.fixedPerHour!!, 0.0)
            store.save(original.copy(cost = original.cost.copy(fixedPerHour = 20.0)))
            assertEquals(12.0, db.dao().active()!!.fixedPerHour!!, 0.0)
        } finally { store.save(original); scope.cancel(); db.close() }
    }
}
