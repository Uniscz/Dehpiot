package br.com.deh.copiloto.core

import java.text.Normalizer
import java.time.*

fun normalized(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").trim().replace(Regex("\\s+"), " ")
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)
data class ParsedOffer(val offer: Offer?, val warning: String, val text: String)
object OfferParser {
    private val money = Regex("R\\$\\s*([\\d.]+[,\\.]\\d{2})", RegexOption.IGNORE_CASE)
    private val distance = Regex("(\\d+(?:[,.]\\d+)?)\\s*(km|quil[oô]metros?|m)\\b", RegexOption.IGNORE_CASE)
    private val minutes = Regex("(?:(\\d+)\\s*h(?:oras?)?\\s*)?(\\d+(?:[,.]\\d+)?)\\s*min", RegexOption.IGNORE_CASE)
    private val hourOnly = Regex("(\\d+)\\s*h(?:oras?)?\\b", RegexOption.IGNORE_CASE)
    fun merge(lines: List<OcrLine>): String {
        val rows = mutableListOf<MutableList<OcrLine>>()
        lines.sortedBy { it.top }.forEach { l ->
            val row = rows.lastOrNull()
            if (row != null && kotlin.math.abs(row.first().top - l.top) < maxOf(5, (l.bottom - l.top) / 2)) row.add(l) else rows.add(mutableListOf(l))
        }
        return rows.joinToString("\n") { row -> row.sortedBy { it.left }.joinToString(" ") { it.text } }
    }
    fun parse(text: String): ParsedOffer {
        val rows = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val fares = rows.filterNot { normalized(it).let { row -> listOf("saldo", "ganhos", "bonus", "gorjeta", "extra").any(row::contains) } }
            .flatMap { row -> money.findAll(row).filterNot { match ->
                val suffix = row.substring(match.range.last + 1).trimStart()
                Regex("^(?:/\\s*|por\\s+)(?:km|h|hora)\\b", RegexOption.IGNORE_CASE).containsMatchIn(suffix)
            }.mapNotNull { match -> runCatching { cents(decimal(match.groupValues[1])) }.getOrNull() }.toList() }.distinct()

        if (fares.size != 1) return ParsedOffer(null, "Valor ausente ou ambíguo; confira a oferta", text)
        data class Pairing(val km: Double, val min: Double, val index: Int, val pickup: Boolean, val trip: Boolean)
        val pairs = rows.mapIndexedNotNull { i, row ->
            val d = distance.find(row) ?: return@mapIndexedNotNull null
            val t = minutes.find(row)
            val duration = if (t != null) (t.groupValues[1].toDoubleOrNull() ?: 0.0) * 60 + decimal(t.groupValues[2]) else hourOnly.find(row)?.groupValues?.get(1)?.toDouble()?.times(60) ?: return@mapIndexedNotNull null
            val s = normalized(row)
            val pickup = listOf("busca", "buscar", "embarque", "passageiro", "ate voce", "chegada").any(s::contains)
            Pairing(decimal(d.groupValues[1]) / if (d.groupValues[2].lowercase() == "m") 1000 else 1, duration, i, pickup, !pickup && listOf("viagem", "corrida", "trajeto", "percurso").any(s::contains))
        }
        if (pairs.size != 2) return ParsedOffer(null, "Leitura incompleta: preciso do valor, km e tempo da busca e da viagem", text)
        val pick = pairs.singleOrNull { it.pickup } ?: pairs.singleOrNull { !it.trip } ?: pairs.first()
        val trip = pairs.first { it !== pick }
        val quality = if (pick.pickup || trip.trip) ReadingQuality.CONTEXTUAL else ReadingQuality.ORDERED
        fun address(label: String, index: Int): String {
            rows.firstOrNull { normalized(it).startsWith("$label:") }?.let { return it.substringAfter(':').trim().take(100) }
            val street = Regex("(?:^|[) ]+)(rua|r\\.|av\\.|avenida|rodovia|estrada|travessa|BR[- ]?\\d)\\s*", RegexOption.IGNORE_CASE)
            val current = rows[index]
            val inline = street.find(current)?.let { current.substring(it.range.first).trimStart(' ', ')') }
            val next = rows.getOrNull(index + 1)
            val found = inline ?: next?.takeIf { street.find(it)?.range?.first == 0 } ?: return ""
            val continuation = if (inline != null) next else rows.getOrNull(index + 2)
            val extra = continuation?.takeIf { line ->
                (Regex("^[0-9]+[, -]+[^0-9]").containsMatchIn(line) || Regex("^[\\p{L} .'-]+,?\\s*[0-9]+[, -]").containsMatchIn(line)) && minutes.find(line) == null && distance.find(line) == null
            }
            return listOfNotNull(found, extra).joinToString(" ").take(160)
        }

        val o = Offer(fares.single(), pick.km, pick.min, trip.km, trip.min, address("origem", pick.index), address("destino", trip.index), if (normalized(text).contains("uber")) "Uber" else if (text.contains("99")) "99" else "OCR", quality)
        return try { o.validate(); ParsedOffer(o, if (quality == ReadingQuality.ORDERED) "Trechos inferidos pela ordem; confira" else "Campos identificados; confira antes de registrar", text) } catch (e: IllegalArgumentException) { ParsedOffer(null, e.message ?: "Campos inválidos", text) }
    }
}
data class ZoneSample(val zone: String, val arrivedAt: Long, val idleMin: Double, val repositionKm: Double, val repositionMin: Double)
object Learning {
    fun bucket(at: Long, zone: ZoneId = ZoneId.systemDefault()): String { val d = Instant.ofEpochMilli(at).atZone(zone); return "${d.dayOfWeek.value >= 6}:${d.hour / 4}" }
    fun estimate(samples: List<ZoneSample>, zone: String, at: Long, prior: Assumptions): Assumptions {
        val valid = samples.filter { normalized(it.zone) == normalized(zone) && bucket(it.arrivedAt) == bucket(at) && listOf(it.idleMin, it.repositionKm, it.repositionMin).all { v -> v.isFinite() && v in 0.0..180.0 } }
        if (valid.size < 5) return prior.copy(zoneSamples = valid.size)
        fun shrink(values: List<Double>, p: Double): Double { val s = values.sorted(); val m = (s[(s.size - 1) / 2] + s[s.size / 2]) / 2; return (s.size * m + 5 * p) / (s.size + 5) }
        return prior.copy(nextOfferWaitMin = shrink(valid.map { it.idleMin }, prior.nextOfferWaitMin), repositionKm = if (prior.routeVerified) prior.repositionKm else shrink(valid.map { it.repositionKm }, prior.repositionKm), repositionMin = if (prior.routeVerified) prior.repositionMin else shrink(valid.map { it.repositionMin }, prior.repositionMin), zoneSamples = valid.size)
    }
}
