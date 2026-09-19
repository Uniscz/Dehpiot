package br.com.deh.copiloto.core

import kotlin.math.abs
import kotlin.math.max

/** Fuel OCR never calls the offer parser. Candidates always need human confirmation. */
enum class ConsumptionUnit(val label: String) { KM_L("km/L"), L_100KM("L/100 km") }
data class ConsumptionCandidate(val value: Double, val unit: ConsumptionUnit, val context: String) {
    val kmPerLiter: Double get() = Fuel.kmPerLiter(value, unit)
}
data class PanelReading(val candidates: List<ConsumptionCandidate>, val message: String) {
    val unambiguous: Boolean get() = candidates.size == 1
}
object Fuel {
    fun kmPerLiter(value: Double, unit: ConsumptionUnit): Double {
        require(value.isFinite() && value > 0) { "Informe um consumo maior que zero" }
        val result = if (unit == ConsumptionUnit.KM_L) value else 100.0 / value
        require(result in 1.0..60.0) { "Confira o consumo: valor fora da faixa de 1 a 60 km/L" }
        return result
    }
    fun perKm(price: Double, kml: Double): Double {
        require(price.isFinite() && price > 0 && price <= 100) { "Confira o preço por litro" }
        kmPerLiter(kml, ConsumptionUnit.KM_L)
        return price / kml
    }
    fun update(cost: VehicleCost, price: Double, kml: Double): VehicleCost {
        val rate = perKm(price, kml)
        // The explicit simple mode becomes fuel-only; detailed mode retains every reserve.
        return cost.copy(fuelPrice = price, cityKml = kml, highwayKml = if (cost.detailed) cost.highwayKml else kml, simplePerKm = rate, fuelBasedSimple = true)
    }
}
object PanelParser {
    private val pattern = Regex("(?<![\\d.,])([0-9]{1,3}(?:[.,][0-9]{1,2})?)\\s*(km\\s*/\\s*l|l\\s*/\\s*100\\s*km)(?![a-z])", RegexOption.IGNORE_CASE)
    fun parse(text: String): PanelReading {
        val cleaned = text.replace('\u00a0', ' ')
        val candidates = pattern.findAll(cleaned).mapNotNull { match ->
            val lineStart = cleaned.lastIndexOf('\n', match.range.first).let { if (it < 0) 0 else it + 1 }
            val lineEnd = cleaned.indexOf('\n', match.range.last).let { if (it < 0) cleaned.length else it }
            val context = cleaned.substring(lineStart, lineEnd).trim()
            if (Regex("instant|instantâneo|instantaneo", RegexOption.IGNORE_CASE).containsMatchIn(context)) return@mapNotNull null
            runCatching {
                val unit = if (match.groupValues[2].startsWith("km", true)) ConsumptionUnit.KM_L else ConsumptionUnit.L_100KM
                ConsumptionCandidate(decimal(match.groupValues[1]), unit, context).also { it.kmPerLiter }
            }.getOrNull()
        }.distinctBy { it.value to it.unit }.toList()
        return PanelReading(candidates, when (candidates.size) {
            0 -> "Não conseguimos identificar o consumo com segurança. Digite o consumo médio."
            1 -> "Confira se este é o consumo médio do painel antes de confirmar."
            else -> "Encontramos mais de um consumo. Escolha a média correta ou digite o valor."
        })
    }
}

/** Stack ids are retained for compatibility with existing ViewModel callers. */
object ProductNavigation {
    val main = setOf(0, 2, 3, 5, 6)
    fun open(stack: List<Int>, target: Int): List<Int> {
        require(target in 0..11)
        if (stack.lastOrNull() == target) return stack
        if (target == 0) return listOf(0)
        if (target in main) return listOf(0, target)
        return (stack.ifEmpty { listOf(0) } + target).takeLast(20).let { if (it.first() == 0) it else listOf(0) + it }
    }
    fun back(stack: List<Int>) = if (stack.size > 1) stack.dropLast(1) else listOf(0)
}

data class GoalProgress(val current: Double, val remaining: Double, val fraction: Float, val hourly: Double?, val minutesRemaining: Double?, val requiredHourly: Double?)
object GoalMath {
    fun calculate(current: Double, target: Double, onlineMinutes: Double, minutesUntilDeadline: Double? = null): GoalProgress {
        require(listOf(current, target, onlineMinutes).all { it.isFinite() } && target > 0 && onlineMinutes >= 0)
        val remaining = max(0.0, target - current)
        val hourly = if (onlineMinutes >= 15 && current > 0) current / onlineMinutes * 60 else null
        return GoalProgress(current, remaining, (current / target).toFloat().coerceIn(0f, 1f), hourly,
            if (remaining == 0.0) 0.0 else hourly?.let { remaining / it * 60 },
            minutesUntilDeadline?.takeIf { it.isFinite() && it > 0 }?.let { remaining / it * 60 })
    }
}

data class ObservedCycle(val at: Long, val fare: Long, val variable: Long, val fixed: Long, val km: Double, val paidKm: Double, val minutes: Double, val productive: Double, val zone: String)
data class OnlineInterval(val start: Long, val end: Long?, val fixedPerHour: Double?)
data class PeriodSummary(val count: Int, val gross: Long, val costs: Long, val net: Long, val km: Double, val paidKm: Double, val cycleMinutes: Double, val onlineMinutes: Double, val productiveMinutes: Double) {
    val denominatorMinutes get() = if (onlineMinutes > 0) onlineMinutes else cycleMinutes
    val hourly get() = if (denominatorMinutes > 0) net / 100.0 / denominatorMinutes * 60 else 0.0
    val perKm get() = if (km > 0) net / 100.0 / km else 0.0
    val emptyKm get() = (km - paidKm).coerceAtLeast(0.0)
    val productivity get() = if (denominatorMinutes > 0) (productiveMinutes / denominatorMinutes).coerceIn(0.0, 1.0) else 0.0
}
object ProductMetrics {
    fun summary(cycles: List<ObservedCycle>, sessions: List<OnlineInterval>, from: Long, until: Long): PeriodSummary {
        require(until >= from)
        val selected = cycles.filter { it.at >= from && it.at < until }
        val relevant = sessions.filter { it.start < until && (it.end ?: until) > from }
        // Clip intervals and avoid double-counting overlap, including old imported sessions.
        var cursor = from; var online = 0.0; var fixed = 0.0
        relevant.sortedBy { it.start }.forEach { s ->
            val start = maxOf(from, s.start, cursor); val end = minOf(until, s.end ?: until)
            if (end > start) { val minutes = (end - start) / 60000.0; online += minutes; fixed += minutes / 60 * (s.fixedPerHour ?: 0.0); cursor = end }
        }
        // Legacy sessions lack a snapshot: use recorded cycle costs, never today's settings.
        val hasReliableSessionCost = relevant.isNotEmpty() && relevant.all { it.fixedPerHour != null }
        val fixedCents = if (hasReliableSessionCost) cents(fixed) else selected.sumOf { it.fixed }
        val gross = selected.sumOf { it.fare }; val costs = selected.sumOf { it.variable } + fixedCents
        return PeriodSummary(selected.size, gross, costs, gross - costs, selected.sumOf { it.km }, selected.sumOf { it.paidKm }, selected.sumOf { it.minutes }, online, selected.sumOf { it.productive })
    }
    fun market(scores: List<Pair<Long, Int>>, now: Long): MarketSignal? {
        val boundary = now - 25 * 60_000L
        val recent = scores.filter { it.first > boundary && it.first <= now }.map { it.second }
        val previous = scores.filter { it.first > now - 50 * 60_000L && it.first <= boundary }.map { it.second }
        if (recent.size < 5 || previous.size < 5 || previous.average() <= 0) return null
        return MarketSignal((recent.average() / previous.average() - 1) * 100, recent.size + previous.size)
    }
    fun calibration(predictedAndActual: List<Pair<Double, Double>>): Calibration? {
        val pairs = predictedAndActual.filter { it.first.isFinite() && it.second.isFinite() }
        if (pairs.isEmpty()) return null
        val mae = pairs.map { abs(it.first - it.second) }.average()
        val relative = pairs.filter { abs(it.second) >= 1.0 }.map { abs(it.first - it.second) / abs(it.second) * 100 }.takeIf { it.isNotEmpty() }?.average()
        return Calibration(pairs.size, mae, relative)
    }
}
data class MarketSignal(val changePercent: Double, val samples: Int)
data class Calibration(val count: Int, val absoluteHourlyError: Double, val relativeErrorPercent: Double?)
