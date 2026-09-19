package br.com.deh.copiloto.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import br.com.deh.copiloto.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.json.JSONArray
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.*
import java.util.UUID

data class Settings(val vehicle: String = "Meu veículo", val cost: VehicleCost = VehicleCost(), val policy: Policy = Policy(), val defaults: Assumptions = Assumptions(), val dailyGoal: Double = 250.0, val goalNet: Boolean = true, val onboarded: Boolean = false, val voice: Boolean = false, val haptic: Boolean = false, val cropTop: Double = .30, val intervalMs: Long = 850, val retentionDays: Int = 90, val goalDeadline: Long = 0, val shiftMode: String = "WAITING", val fuelName: String = "Etanol")
object Codec {
    fun cost(c: VehicleCost) = JSONObject().put("detailed", c.detailed).put("simple", c.simplePerKm).put("fuel", c.fuelPrice).put("city", c.cityKml).put("highway", c.highwayKml).put("maintenance", c.maintenance).put("tires", c.tires).put("oil", c.oil).put("depreciation", c.depreciation).put("other", c.other).put("fixed", c.fixedPerHour).put("fuelBasedSimple", c.fuelBasedSimple)
    fun cost(j: JSONObject) = VehicleCost(j.optBoolean("detailed"), j.optDouble("simple", .9), j.optDouble("fuel", 6.2), j.optDouble("city", 10.0), j.optDouble("highway", 13.0), j.optDouble("maintenance", .12), j.optDouble("tires", .04), j.optDouble("oil", .03), j.optDouble("depreciation", .09), j.optDouble("other", 0.0), j.optDouble("fixed", 0.0), j.optBoolean("fuelBasedSimple", false))
    fun assumptions(a: Assumptions) = JSONObject().put("wait", a.boardingWaitMin).put("operational", a.operationalMin).put("returnKm", a.repositionKm).put("returnMin", a.repositionMin).put("idle", a.nextOfferWaitMin).put("detourKm", a.detourKm).put("detourMin", a.detourMin).put("extra", a.extraCostCents).put("highway", a.highwayFraction).put("samples", a.zoneSamples).put("verified", a.routeVerified).put("provider", a.routeProvider).put("asOf", a.routeAsOf).put("geo", a.straightLineKm).put("access", a.accessWarning)
    fun assumptions(j: JSONObject) = Assumptions(j.optDouble("wait", 3.0), j.optDouble("operational", 0.0), j.optDouble("returnKm", 2.0), j.optDouble("returnMin", 5.0), j.optDouble("idle", 5.0), j.optDouble("detourKm", 0.0), j.optDouble("detourMin", 0.0), j.optLong("extra", 0), j.optDouble("highway", 0.0), j.optInt("samples", 0), j.optBoolean("verified"), j.optString("provider"), j.optLong("asOf"), if (j.has("geo") && !j.isNull("geo")) j.getDouble("geo") else null, j.optBoolean("access"))
    fun offer(o: Offer) = JSONObject().put("fare", o.fareCents).put("pickupKm", o.pickupKm).put("pickupMin", o.pickupMin).put("tripKm", o.tripKm).put("tripMin", o.tripMin).put("origin", o.origin).put("destination", o.destination).put("platform", o.platform).put("quality", o.quality.name)
    fun offer(j: JSONObject) = Offer(j.getLong("fare"), j.getDouble("pickupKm"), j.getDouble("pickupMin"), j.getDouble("tripKm"), j.getDouble("tripMin"), j.optString("origin"), j.optString("destination"), j.optString("platform", "Manual"), ReadingQuality.valueOf(j.optString("quality", "CONFIRMED")))
    fun settings(s: Settings) = JSONObject().put("vehicle", s.vehicle).put("cost", cost(s.cost)).put("minH", s.policy.minHourly).put("minK", s.policy.minPerKm).put("strategy", s.policy.strategy.name).put("defaults", assumptions(s.defaults)).put("goal", s.dailyGoal).put("goalNet", s.goalNet).put("onboarded", s.onboarded).put("voice", s.voice).put("haptic", s.haptic).put("crop", s.cropTop).put("interval", s.intervalMs).put("retention", s.retentionDays).put("deadline", s.goalDeadline).put("shiftMode", s.shiftMode).put("fuelName", s.fuelName)
    fun settings(j: JSONObject) = Settings(j.optString("vehicle", "Meu veículo"), cost(j.optJSONObject("cost") ?: JSONObject()), Policy(j.optDouble("minH", 35.0), j.optDouble("minK", 1.2), Strategy.valueOf(j.optString("strategy", "BALANCED"))), assumptions(j.optJSONObject("defaults") ?: JSONObject()), j.optDouble("goal", 250.0), j.optBoolean("goalNet", true), j.optBoolean("onboarded"), j.optBoolean("voice"), j.optBoolean("haptic"), j.optDouble("crop", .3), j.optLong("interval", 850), j.optInt("retention", 90), j.optLong("deadline", 0), j.optString("shiftMode", "WAITING"), j.optString("fuelName", "Etanol"))
}
data class CostChange(val at: Long, val cost: VehicleCost)
private val Context.settingsStore by preferencesDataStore("personal_settings")
class SettingsStore(private val context: Context) {
    private val key = stringPreferencesKey("settings")
    private val historyKey = stringPreferencesKey("cost_history")
    val costHistory = context.settingsStore.data.map { p ->
        val array = runCatching { JSONArray(p[historyKey] ?: "[]") }.getOrDefault(JSONArray())
        (0 until array.length()).mapNotNull { i -> runCatching { val j = array.getJSONObject(i); CostChange(j.getLong("at"), Codec.cost(j.getJSONObject("cost"))) }.getOrNull() }
    }
    val flow = context.settingsStore.data.map { p -> runCatching { Codec.settings(JSONObject(p[key] ?: "{}")) }.getOrDefault(Settings()) }
    suspend fun clearCostHistory() { context.settingsStore.edit { it.remove(historyKey) } }
    suspend fun save(s: Settings) { s.cost.validate(); s.policy.validate(); s.defaults.validate(); require(s.dailyGoal > 0 && s.dailyGoal.isFinite()); context.settingsStore.edit { preferences ->
            val old = preferences[key]?.let { runCatching { Codec.settings(JSONObject(it)) }.getOrNull() }
            if (old == null || old.cost != s.cost) {
                val previous = runCatching { JSONArray(preferences[historyKey] ?: "[]") }.getOrDefault(JSONArray())
                val next = JSONArray()
                for (i in maxOf(0, previous.length() - 364) until previous.length()) next.put(previous.getJSONObject(i))
                next.put(JSONObject().put("at", System.currentTimeMillis()).put("cost", Codec.cost(s.cost)))
                preferences[historyKey] = next.toString()
            }
            preferences[key] = Codec.settings(s).toString()
        } }
}
@Entity(tableName = "offers")
data class OfferRow(@PrimaryKey val id: String = UUID.randomUUID().toString(), val createdAt: Long = System.currentTimeMillis(), val offerJson: String, val assumptionsJson: String, val costJson: String, val predictedNet: Long, val predictedHourly: Double, val predictedPerKm: Double, val score: Int, val totalKm: Double, val totalMin: Double, val status: String = "OBSERVED", val source: String = "Manual", val latencyMs: Long = 0, val model: String = Economics.MODEL_VERSION) {
    fun offer() = Codec.offer(JSONObject(offerJson))
    fun assumptions() = Codec.assumptions(JSONObject(assumptionsJson))
    fun cost() = Codec.cost(JSONObject(costJson))
}
@Entity(tableName = "rides", indices = [Index(value = ["offerId"], unique = true)], foreignKeys = [ForeignKey(entity = OfferRow::class, parentColumns = ["id"], childColumns = ["offerId"], onDelete = ForeignKey.CASCADE)])
data class RideRow(@PrimaryKey val id: String = UUID.randomUUID().toString(), val offerId: String, val completedAt: Long = System.currentTimeMillis(), val arrivedAt: Long = System.currentTimeMillis(), val fareCents: Long, val totalKm: Double, val totalMin: Double, val paidKm: Double, val productiveMin: Double, val variableCostCents: Long, val fixedCostCents: Long, val netCents: Long, val zone: String, val idleMin: Double? = null, val returnKm: Double? = null, val returnMin: Double? = null)
@Entity(tableName = "sessions") data class SessionRow(@PrimaryKey val id: String = UUID.randomUUID().toString(), val start: Long = System.currentTimeMillis(), val end: Long? = null, val fixedPerHour: Double? = null)
@Dao interface AppDao {
    @Query("SELECT * FROM offers ORDER BY createdAt DESC LIMIT 10000") fun offers(): Flow<List<OfferRow>>
    @Query("SELECT * FROM rides ORDER BY completedAt DESC LIMIT 10000") fun rides(): Flow<List<RideRow>>
    @Query("SELECT * FROM sessions ORDER BY start DESC") fun sessions(): Flow<List<SessionRow>>
    @Query("SELECT * FROM offers ORDER BY createdAt DESC") suspend fun offersForExport(): List<OfferRow>
    @Query("SELECT * FROM rides ORDER BY completedAt DESC") suspend fun ridesForExport(): List<RideRow>
    @Insert suspend fun insert(o: OfferRow)
    @Insert suspend fun insert(r: RideRow)
    @Insert suspend fun insert(s: SessionRow)
    @Query("SELECT * FROM offers WHERE id=:id") suspend fun get(id: String): OfferRow?
    @Query("UPDATE offers SET status=:status WHERE id=:id AND status!='COMPLETED'") suspend fun status(id: String, status: String): Int
    @Query("UPDATE offers SET assumptionsJson=:a, predictedNet=:net, predictedHourly=:h, predictedPerKm=:k, score=:score, totalKm=:km, totalMin=:min WHERE id=:id AND status='OBSERVED'") suspend fun enrich(id: String, a: String, net: Long, h: Double, k: Double, score: Int, km: Double, min: Double): Int
    @Query("SELECT * FROM sessions WHERE `end` IS NULL LIMIT 1") suspend fun active(): SessionRow?
    @Query("UPDATE sessions SET `end`=:now WHERE id=:id") suspend fun close(id: String, now: Long)
    @Query("DELETE FROM offers WHERE id=:id") suspend fun delete(id: String)
    @Query("DELETE FROM offers WHERE createdAt<:before") suspend fun prune(before: Long)
    @Query("DELETE FROM sessions WHERE `end`<:before") suspend fun pruneSessions(before: Long)
    @Query("DELETE FROM offers") suspend fun clearOffers()
    @Query("DELETE FROM sessions") suspend fun clearSessions()
}
@Database(entities = [OfferRow::class, RideRow::class, SessionRow::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE sessions ADD COLUMN fixedPerHour REAL") }
        }
    }
}
class Repository(val db: AppDatabase, val settingsStore: SettingsStore, scope: CoroutineScope) {
    val dao = db.dao()
    val costHistory = settingsStore.costHistory.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val settings = settingsStore.flow.stateIn(scope, SharingStarted.Eagerly, Settings())
    val offers = dao.offers().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val rides = dao.rides().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val sessions = dao.sessions().stateIn(scope, SharingStarted.Eagerly, emptyList())
    suspend fun save(o: Offer, a: Assumptions, c: VehicleCost, p: Prediction, source: String, latency: Long = 0): String {
        val row = OfferRow(offerJson = Codec.offer(o).toString(), assumptionsJson = Codec.assumptions(a).toString(), costJson = Codec.cost(c).toString(), predictedNet = p.netCents, predictedHourly = p.hourly, predictedPerKm = p.perKm, score = p.score, totalKm = p.totalKm, totalMin = p.totalMin, source = source, latencyMs = latency)
        dao.insert(row); return row.id
    }
    suspend fun complete(id: String, fare: Long, km: Double, min: Double, paidKm: Double, productive: Double, extra: Long, zone: String, idle: Double?, returnKm: Double?, returnMin: Double?, arrivedAt: Long = System.currentTimeMillis()) = db.withTransaction {
        val row = requireNotNull(dao.get(id)); require(row.status != "COMPLETED") { "Corrida já concluída" }
        require(fare >= 0 && km.isFinite() && km > 0 && min.isFinite() && min > 0 && extra >= 0 && paidKm.isFinite() && paidKm in 0.0..km && productive.isFinite() && productive in 0.0..min)
        require(listOfNotNull(idle, returnKm, returnMin).all { it.isFinite() && it >= 0 }); require(returnKm == null || returnKm <= km); require(idle == null || idle <= min); require(returnMin == null || returnMin <= min)
        require((returnKm ?: 0.0) == 0.0 || (returnMin ?: 0.0) > 0)
        val cost = row.cost(); val variable = cents(km * cost.rate(row.assumptions().highwayFraction)) + extra; val fixed = cents(min / 60 * cost.fixedPerHour)
        dao.insert(RideRow(offerId = id, arrivedAt = arrivedAt, fareCents = fare, totalKm = km, totalMin = min, paidKm = paidKm, productiveMin = productive, variableCostCents = variable, fixedCostCents = fixed, netCents = fare - variable - fixed, zone = zone, idleMin = idle, returnKm = returnKm, returnMin = returnMin))
        dao.status(id, "COMPLETED")
    }
    suspend fun startSession() {
        val rate = settingsStore.flow.first().cost.fixedPerHour
        db.withTransaction { if (dao.active() == null) dao.insert(SessionRow(fixedPerHour = rate)) }
    }
    suspend fun endSession() = db.withTransaction { dao.active()?.let { dao.close(it.id, System.currentTimeMillis()) } }
    suspend fun toggleSession() = db.withTransaction { val active = dao.active(); if (active == null) dao.insert(SessionRow(fixedPerHour = settings.value.cost.fixedPerHour)) else dao.close(active.id, System.currentTimeMillis()) }
    suspend fun clear() { db.withTransaction { dao.clearOffers(); dao.clearSessions() }; settingsStore.clearCostHistory() }
    fun learned(zone: String, base: Assumptions): Assumptions = Learning.estimate(rides.value.mapNotNull { r -> if (r.idleMin != null && r.returnKm != null && r.returnMin != null) ZoneSample(r.zone, r.arrivedAt, r.idleMin, r.returnKm, r.returnMin) else null }, zone, System.currentTimeMillis(), base)
    suspend fun csv(): String = db.withTransaction {
        fun cell(v: Any?): String { var s = v?.toString() ?: ""; if (v is String && s.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) s = "'$s"; return "\"${s.replace("\"", "\"\"")}\"" }
        val real = dao.ridesForExport().associateBy { it.offerId }
        "\uFEFFid;data;status;fonte;destino;valor_previsto;liquido_previsto;score;km_previstos;min_previstos;rota_verificada;provedor;valor_real;liquido_real;km_reais;min_reais;modelo\r\n" + dao.offersForExport().joinToString("\r\n") { r ->
            val o = r.offer(); val a = r.assumptions(); val actual = real[r.id]
            listOf(r.id, Instant.ofEpochMilli(r.createdAt).toString(), r.status, r.source, o.destination, o.fareCents / 100.0, r.predictedNet / 100.0, r.score, r.totalKm, r.totalMin, a.routeVerified, a.routeProvider, actual?.fareCents?.div(100.0), actual?.netCents?.div(100.0), actual?.totalKm, actual?.totalMin, r.model).joinToString(";", transform = ::cell)
        }
    }
}
