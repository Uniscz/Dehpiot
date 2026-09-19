package br.com.deh.copiloto.core

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.*

fun decimal(value: String): Double {
    val s = value.trim().replace("R$", "").replace(" ", "").let { if (',' in it) it.replace(".", "").replace(',', '.') else it }
    return s.toDouble().also { require(it.isFinite()) { "Número inválido" } }
}
fun cents(value: Double): Long {
    require(value.isFinite() && abs(value) < 1e9)
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
}
enum class Strategy { BALANCED, HOUR, KM }
enum class ReadingQuality { CONFIRMED, CONTEXTUAL, ORDERED, INCOMPLETE }
data class VehicleCost(
    val detailed: Boolean = false, val simplePerKm: Double = .90,
    val fuelPrice: Double = 6.20, val cityKml: Double = 10.0, val highwayKml: Double = 13.0,
    val maintenance: Double = .12, val tires: Double = .04, val oil: Double = .03,
    val depreciation: Double = .09, val other: Double = 0.0, val fixedPerHour: Double = 0.0, val fuelBasedSimple: Boolean = false
) {
    fun validate() { require(listOf(simplePerKm, fuelPrice, maintenance, tires, oil, depreciation, other, fixedPerHour).all { it.isFinite() && it >= 0 }); require(cityKml.isFinite() && cityKml > 0 && highwayKml.isFinite() && highwayKml > 0) }
    fun rate(highwayFraction: Double = 0.0): Double {
        validate(); require(highwayFraction in 0.0..1.0)
        return if (!detailed) { if (fuelBasedSimple) fuelPrice / cityKml else simplePerKm } else fuelPrice * ((1 - highwayFraction) / cityKml + highwayFraction / highwayKml) + maintenance + tires + oil + depreciation + other
    }
}
data class Policy(val minHourly: Double = 35.0, val minPerKm: Double = 1.20, val strategy: Strategy = Strategy.BALANCED) {
    fun validate() { require(minHourly.isFinite() && minHourly > 0 && minPerKm.isFinite() && minPerKm > 0) }
}
data class Offer(val fareCents: Long, val pickupKm: Double, val pickupMin: Double, val tripKm: Double, val tripMin: Double, val origin: String = "", val destination: String = "", val platform: String = "Manual", val quality: ReadingQuality = ReadingQuality.CONFIRMED) {
    fun validate() {
        require(fareCents in 1..10_000_000) { "Confira o valor da corrida" }
        require(listOf(pickupKm, pickupMin, tripKm, tripMin).all { it.isFinite() && it >= 0 }) { "Confira distâncias e tempos" }
        require(tripKm > 0 && tripMin > 0 && pickupKm <= 3000 && tripKm <= 3000 && pickupMin <= 2880 && tripMin <= 2880) { "Viagem precisa ter distância e tempo válidos" }
        require(pickupKm == 0.0 || pickupMin > 0)
    }
}
data class Assumptions(
    val boardingWaitMin: Double = 3.0, val operationalMin: Double = 0.0,
    val repositionKm: Double = 2.0, val repositionMin: Double = 5.0, val nextOfferWaitMin: Double = 5.0,
    val detourKm: Double = 0.0, val detourMin: Double = 0.0, val extraCostCents: Long = 0,
    val highwayFraction: Double = 0.0, val zoneSamples: Int = 0,
    val routeVerified: Boolean = false, val routeProvider: String = "", val routeAsOf: Long = 0,
    val straightLineKm: Double? = null, val accessWarning: Boolean = false
) {
    fun validate() {
        require(listOf(boardingWaitMin, operationalMin, repositionKm, repositionMin, nextOfferWaitMin, detourKm, detourMin).all { it.isFinite() && it >= 0 && it <= 3000 })
        require(extraCostCents >= 0 && highwayFraction in 0.0..1.0)
        require(repositionKm == 0.0 || repositionMin > 0) { "Informe a duração do retorno" }
        require(detourKm == 0.0 || detourMin > 0) { "Informe a duração do desvio" }
    }
}
data class Prediction(val fareCents: Long, val variableCostCents: Long, val fixedCostCents: Long, val netCents: Long, val totalKm: Double, val totalMin: Double, val hourly: Double, val perKm: Double, val score: Int, val label: String, val lowHourly: Double, val highHourly: Double, val reasons: List<String>, val confidence: String, val destinationScore: Int?)
object Economics {
    const val MODEL_VERSION = "economics-1.1-road"
    fun score(ratio: Double): Int {
        val a = listOf(0.0 to 0.0, .5 to 25.0, 1.0 to 60.0, 1.5 to 85.0, 2.0 to 100.0)
        val r = ratio.coerceIn(0.0, 2.0)
        val (lo, hi) = a.zipWithNext().first { r <= it.second.first }
        return (lo.second + (r - lo.first) / (hi.first - lo.first) * (hi.second - lo.second)).roundToInt()
    }
    fun evaluate(o: Offer, a: Assumptions, c: VehicleCost, policy: Policy): Prediction {
        o.validate(); a.validate(); c.validate(); policy.validate()
        val km = o.pickupKm + o.tripKm + a.repositionKm + a.detourKm
        val time = o.pickupMin + o.tripMin + a.boardingWaitMin + a.operationalMin + a.repositionMin + a.nextOfferWaitMin + a.detourMin
        val variable = cents(km * c.rate(a.highwayFraction)) + a.extraCostCents
        val fixed = cents(time / 60 * c.fixedPerHour)
        val net = o.fareCents - variable - fixed
        val h = net / 100.0 / time * 60; val k = net / 100.0 / km
        val ratio = when (policy.strategy) { Strategy.BALANCED -> min(h / policy.minHourly, k / policy.minPerKm); Strategy.HOUR -> h / policy.minHourly; Strategy.KM -> k / policy.minPerKm }
        val score = score(ratio)
        fun scenario(f: Double): Double {
            val sk = o.pickupKm + o.tripKm + a.detourKm + a.repositionKm * if (a.routeVerified) 1.0 else f
            val st = o.pickupMin + o.tripMin + a.detourMin + a.operationalMin + f * (a.boardingWaitMin + a.repositionMin + a.nextOfferWaitMin)
            return (o.fareCents - cents(sk * c.rate(a.highwayFraction)) - a.extraCostCents - cents(st / 60 * c.fixedPerHour)) / 100.0 / st * 60
        }
        val scenarios = listOf(scenario(.5), scenario(1.5), h)
        val reasons = buildList {
            if (a.accessWarning) add("Acesso difícil: a rota de condução é muito maior que a proximidade no mapa")
            if (a.routeVerified) add("Retorno viário: ${a.routeProvider}") else add("Retorno estimado; análise regional incompleta até calcular a rota")
            if (h < policy.minHourly) add("Abaixo do seu mínimo por hora")
            if (k < policy.minPerKm) add("Abaixo do seu mínimo por km")
            if (o.pickupMin > o.tripMin / 2) add("Busca consome parte relevante do ciclo")
            if (a.zoneSamples < 5) add("Histórico pessoal ainda insuficiente para prever espera")
            if (o.quality == ReadingQuality.ORDERED) add("OCR inferiu a ordem dos trechos; confira os números")
        }
        return Prediction(o.fareCents, variable, fixed, net, km, time, h, k, score,
            when { score >= 90 -> "EXCELENTE"; score >= 75 -> "MUITO BOA"; score >= 60 -> "BOA"; score >= 45 -> "NO LIMITE"; else -> "FRACA" }, scenarios.min(), scenarios.max(), reasons,
            if (o.quality == ReadingQuality.ORDERED) "Leitura a conferir" else if (a.routeVerified) "Rota viária • duração sem trânsito ao vivo" else "Estimativa limitada", RouteEconomics.destinationScore(o, a, c, policy))
    }
}
