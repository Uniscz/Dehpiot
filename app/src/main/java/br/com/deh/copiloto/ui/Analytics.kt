package br.com.deh.copiloto.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.deh.copiloto.AppViewModel
import br.com.deh.copiloto.core.*
import br.com.deh.copiloto.data.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable fun AnalyticsScreen(vm: AppViewModel) {
    val rides by vm.repository.rides.collectAsStateWithLifecycle(); val offers by vm.repository.offers.collectAsStateWithLifecycle(); val sessions by vm.repository.sessions.collectAsStateWithLifecycle(); val costs by vm.repository.costHistory.collectAsStateWithLifecycle()
    var days by rememberSaveable { mutableIntStateOf(7) }; var dayOffset by rememberSaveable { mutableIntStateOf(0) }
    val now = liveNow(); val endDate = LocalDate.now().minusDays(dayOffset.toLong()); val firstDate = endDate.minusDays(days - 1L); val from = dayStart(firstDate); val until = minOf(now + 1, dayStart(endDate.plusDays(1)))
    val chosen = remember(rides, from, until) { rides.filter { it.completedAt >= from && it.completedAt < until } }
    val metrics = remember(rides, sessions, from, until) { ProductMetrics.summary(rides.map { it.observation() }, sessions.map { it.interval() }, from, until) }
    val pairs = chosen.mapNotNull { actual -> offers.firstOrNull { it.id == actual.offerId }?.let { it.predictedHourly to (actual.netCents / 100.0 / actual.totalMin * 60) } }
    val calibration = ProductMetrics.calibration(pairs)
    Page {
        Eyebrow("O QUE SEU TRABALHO ESTÁ RENDENDO")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(1 to "Dia", 7 to "7 dias", 30 to "30 dias").forEach { (n, label) -> FilterChip(days == n, { days = n }, label = { Text(label) }) } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton({ dayOffset += days }) { Text("‹ Anterior") }
            Text(endDate.format(DateTimeFormatter.ofPattern("dd/MM")), modifier = Modifier.padding(top = 12.dp))
            TextButton({ dayOffset = (dayOffset - days).coerceAtLeast(0) }, enabled = dayOffset > 0) { Text("Próximo ›") }
        }
        Text(money(metrics.net), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = if (metrics.net < 0) Coral else Cream)
        Note("Líquido de ${firstDate.format(DateTimeFormatter.ofPattern("dd/MM"))} a ${endDate.format(DateTimeFormatter.ofPattern("dd/MM"))} • ${metrics.count} ciclos concluídos")
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) { Metric("R$ ${fmt(metrics.hourly)}", "Por hora", Modifier.weight(1f)); Metric("R$ ${fmt(metrics.perKm)}", "Por km total", Modifier.weight(1f)) }
        Note(if (metrics.onlineMinutes > 0) "R$/h considera o tempo online registrado. Confira se todos os turnos foram abertos e encerrados." else "Sem turno registrado: R$/h considera apenas os ciclos concluídos, não toda a espera do dia.")
        if (chosen.isEmpty()) {
            Spacer(Modifier.height(12.dp)); Text("Ainda estamos conhecendo seu trabalho.", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            Note("Registre o resultado real de algumas corridas. Seus melhores horários e destinos aparecerão aqui, com a quantidade de observações.")
            Action("Abrir histórico") { vm.tab = 2 }; TextButton({ vm.example() }) { Text("Simular corrida") }
        } else {
            HorizontalDivider(color = Muted.copy(alpha = .2f)); Eyebrow("PARA ONDE FOI O DINHEIRO?")
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) { Metric(money(metrics.gross), "Recebido", Modifier.weight(1f)); Metric(money(metrics.costs), "Custos estimados", Modifier.weight(1f)) }
            Eyebrow("QUAIS DIAS RENDERAM MAIS?")
            val daily = (0 until days).map { i -> val date = firstDate.plusDays(i.toLong()); date to ProductMetrics.summary(rides.map { it.observation() }, sessions.map { it.interval() }, dayStart(date), minOf(now + 1, dayStart(date.plusDays(1)))) }
            ProfitBars(daily.map { it.second.net / 100.0 })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Note(firstDate.format(DateTimeFormatter.ofPattern("dd/MM"))); Note("Líquido • R$"); Note(endDate.format(DateTimeFormatter.ofPattern("dd/MM"))) }
            daily.filter { it.second.count > 0 || it.second.onlineMinutes > 0 }.takeLast(7).forEach { (date, day) ->
                Row(Modifier.fillMaxWidth().clickable { dayOffset = java.time.temporal.ChronoUnit.DAYS.between(date, LocalDate.now()).toInt(); days = 1 }.padding(vertical = 8.dp)) { Text(date.format(DateTimeFormatter.ofPattern("dd/MM")), Modifier.weight(1f)); Text(money(day.net), Modifier.weight(1f)); Text("R$ ${fmt(day.hourly)}/h", color = Lime) }
            }
            Eyebrow("QUANTO DO SEU TRABALHO FOI PRODUTIVO?")
            val productive by animateFloatAsState(metrics.productivity.toFloat(), tween(500), label = "productivity")
            LinearProgressIndicator(progress = { productive }, modifier = Modifier.fillMaxWidth().height(8.dp), color = Lime, trackColor = Panel)
            Text("${fmt(metrics.productivity * 100, 0)}% do tempo com passageiro", fontSize = 22.sp)
            Note("Produtivo ${elapsed(metrics.productiveMinutes)} • demais etapas ${elapsed((metrics.denominatorMinutes - metrics.productiveMinutes).coerceAtLeast(0.0))}\nPago ${fmt(metrics.paidKm, 1)} km • vazio ${fmt(metrics.emptyKm, 1)} km")
            val previous = ProductMetrics.summary(rides.map { it.observation() }, sessions.map { it.interval() }, dayStart(firstDate.minusDays(days.toLong())), from)
            if (previous.count >= 5 && metrics.count >= 5 && previous.km > 0 && metrics.km > 0) {
                val delta = (metrics.emptyKm / metrics.km - previous.emptyKm / previous.km) * 100
                Note("A parcela de km vazio ${if (delta >= 0) "subiu" else "caiu"} ${fmt(abs(delta), 1)} pontos percentuais em relação ao período anterior (${previous.count} ciclos).", if (delta > 0) Amber else Lime)
            }
            val regions = chosen.filter { it.zone.isNotBlank() }.groupBy { normalized(it.zone) }.values.filter { it.size >= 5 }
            val regionOrder = regions.sortedByDescending { rs -> rs.sumOf { it.netCents } / 100.0 / rs.sumOf { it.totalMin } * 60 }
            Eyebrow("QUAIS DESTINOS RENDERAM MELHOR?")
            if (regionOrder.isEmpty()) Note("Ainda estamos aprendendo. Precisamos de pelo menos 5 resultados na mesma região.")
            else {
                RegionFinding("Maior R$/h observado", regionOrder.first())
                if (regionOrder.size > 1) RegionFinding("Menor R$/h observado", regionOrder.last())
                Note("Comparação dos ciclos que terminaram nessas regiões. Não prova que o destino causou o resultado e não prevê passageiros agora.")
            }
            Eyebrow("QUANDO VOCÊ GANHOU MAIS?")
            val hours = chosen.groupBy { Instant.ofEpochMilli(it.arrivedAt).atZone(ZoneId.systemDefault()).hour / 2 * 2 }.filter { (_, rs) -> rs.size >= 5 && rs.map { Instant.ofEpochMilli(it.arrivedAt).atZone(ZoneId.systemDefault()).toLocalDate() }.distinct().size >= 3 }
            val ordered = hours.entries.sortedByDescending { (_, rs) -> rs.sumOf { it.netCents } / rs.sumOf { it.totalMin } }
            if (ordered.isEmpty()) Note("Precisamos de 5 ciclos em pelo menos 3 dias no mesmo intervalo. Horários são os desembarques informados.")
            else {
                listOfNotNull(ordered.firstOrNull(), ordered.lastOrNull()?.takeIf { ordered.size > 1 }).forEachIndexed { index, (hour, rs) -> Text("${if (index == 0) "Melhor" else "Pior"} intervalo: ${hour}h–${hour + 2}h", fontSize = 21.sp); Note("R$ ${fmt(rs.sumOf { it.netCents } / 100.0 / rs.sumOf { it.totalMin } * 60)}/h nos ciclos • ${rs.size} observações") }
            }
        }
        Eyebrow("O DEHPILOT ESTÁ PREVENDO BEM?")
        if (calibration == null) Note("Registre um resultado real para comparar com a previsão. Não existe precisão medida ainda.")
        else {
            Text("R$ ${fmt(calibration.absoluteHourlyError)}/h", fontSize = 29.sp, fontWeight = FontWeight.SemiBold)
            Note("Erro absoluto médio • ${calibration.count} previsões comparadas neste período")
            calibration.relativeErrorPercent?.let { Note("Erro relativo médio: ${fmt(it, 1)}%. Exclui resultados reais entre −R$ 1/h e R$ 1/h para evitar percentuais enganosos.") }
            Note(if (calibration.count < 10) "Poucos dados. Isso ainda não permite concluir que a previsão é confiável." else "Uma amostra maior permite acompanhar o erro, mas não garante a próxima previsão.")
            Note("A espera regional aprende com medições completas. Esta tela mede erro; não altera os pesos do score automaticamente.")
        }
        if (costs.size >= 2) {
            Eyebrow("COMO MUDOU O CUSTO POR KM?")
            val entries = costs.takeLast(12); Sparkline(entries.map { it.cost.rate() }, "Histórico das últimas ${entries.size} alterações de custo estimado por km")
            val delta = entries.last().cost.rate() - entries[entries.lastIndex - 1].cost.rate()
            Note("Último ajuste: ${if (delta >= 0) "+" else "−"}R$ ${fmt(abs(delta), 3)}/km. Valor atual R$ ${fmt(entries.last().cost.rate(), 3)}/km.")
        }
        Note("Resultados dependem do que você registra. Custos são estimativas com as configurações preservadas. Dias seguem o horário local; ciclos são atribuídos ao dia da conclusão.")
    }
}
@Composable private fun RegionFinding(label: String, rides: List<RideRow>) {
    Text(rides.first().zone, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    Note("$label • R$ ${fmt(rides.sumOf { it.netCents } / 100.0 / rides.sumOf { it.totalMin } * 60)}/h • ${rides.size} ciclos")
    val waits = rides.mapNotNull { it.idleMin }; val returns = rides.mapNotNull { it.returnKm }
    if (waits.size >= 5) Note("${fmt(waits.average(), 0)} min médios até a próxima oferta • ${waits.size} medições")
    if (returns.size >= 5) Note("Retorno vazio médio: ${fmt(returns.average(), 1)} km • ${returns.size} medições")
}
@Composable private fun ProfitBars(values: List<Double>) {
    Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = "Lucro por dia, em reais: ${values.joinToString { fmt(it) }}" }) {
        if (values.isEmpty()) return@Canvas
        val low = minOf(0.0, values.min()); val high = maxOf(.01, values.max()); val span = high - low
        val zero = (high / span).toFloat() * size.height
        drawLine(Muted.copy(alpha = .4f), Offset(0f, zero), Offset(size.width, zero), 1.dp.toPx())
        val width = size.width / values.size
        values.forEachIndexed { index, value ->
            val height = (abs(value) / span).toFloat() * size.height
            drawRoundRect(if (value >= 0) Lime else Coral, Offset(index * width + width * .14f, if (value >= 0) zero - height else zero), Size(width * .72f, height.coerceAtLeast(1f)), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        }
    }
}

@Composable fun PersonalMap(vm: AppViewModel) {
    val rides by vm.repository.rides.collectAsStateWithLifecycle(); val settings by vm.repository.settings.collectAsStateWithLifecycle()
    var routing by rememberSaveable { mutableStateOf(false) }; var selected by rememberSaveable { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    val config = remember(revision) { vm.app.routing.settings }
    val groups = rides.filter { it.zone.isNotBlank() }.groupBy { normalized(it.zone) }
    val pins = config.places.mapNotNull { place -> groups[normalized(place.name)]?.let { rs ->
        val hourly = rs.sumOf { it.netCents } / 100.0 / rs.sumOf { it.totalMin } * 60
        PersonalPin(place.point, if (rs.size < 5) "#A5B4C0" else if (hourly >= settings.policy.minHourly) "#8BDACB" else "#FFCE76", place.name)
    } }
    Page {
        Text(if (config.returningHome) "O caminho de casa\ntambém tem custo." else "Onde seu trabalho\nvale mais?", fontSize = 33.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold)
        Note("Dados observados de corridas concluídas. Não é um mapa de demanda em tempo real.")
        RoadMap(config.tileUrl, null, null, null, { point -> pins.minByOrNull { Geo.distance(it.point, point) }?.takeIf { Geo.distance(it.point, point) < 2 }?.let { selected = it.name } }, pins)
        Note("Verde: R$/h histórico acima do seu mínimo • âmbar: abaixo • cinza: menos de 5 resultados. Toque perto de um ponto ou selecione abaixo.")
        if (pins.isEmpty()) Note("Seu mapa ganha contexto quando uma região do histórico corresponde a um ponto confirmado. Cadastre seus destinos frequentes.")
        if (config.returningHome) Note("Confirme sua casa como região de retorno. A análise usará a rota do destino até lá; não adicionamos um bônus artificial ao score.", Amber)
        if (config.places.isNotEmpty()) {
            Eyebrow("VOLTA PARA CASA")
            Note("Casa: ${config.homePlace.ifBlank { "escolha um ponto confirmado" }}")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                config.places.forEach { place -> FilterChip(config.homePlace == place.name, { vm.task { vm.app.routing.save(config.copy(homePlace = place.name, returnPlace = if (config.returningHome) place.name else config.returnPlace)); vm.invalidateRoute(); revision++; vm.message("Casa confirmada") } }, label = { Text(place.name) }) }
            }
            Action(if (config.returningHome) "Retomar região de trabalho" else "Voltar para casa", config.homePlace.isNotBlank() || config.returningHome) {
                vm.task {
                    if (config.returningHome) vm.app.routing.endHome() else vm.app.routing.beginHome()
                    vm.invalidateRoute(); vm.repository.settingsStore.save(settings.copy(shiftMode = if (config.returningHome) "WAITING" else "HOME"))
                    revision++; vm.message(if (config.returningHome) "Retorno à região de trabalho restaurado" else "Casa será o retorno das próximas análises")
                }
            }
        }
        Action("Calcular retorno viário real") { routing = true }
        Note("Região de retorno: ${config.returnPlace.ifBlank { "ainda não definida" }}. Pontos próximos podem exigir longos acessos pela BR.")
        val selectedRows = groups[normalized(selected)]
        if (selectedRows != null) Section("REGIÃO SELECIONADA") { RegionFinding("R$/h observado", selectedRows); if (selectedRows.size < 5) Note("Poucos dados: sem recomendação", Amber) }
        groups.entries.sortedByDescending { (_, rs) -> rs.size }.forEach { (_, rs) ->
            val hourly = rs.sumOf { it.netCents } / 100.0 / rs.sumOf { it.totalMin } * 60
            MenuRow(rs.first().zone, "${rs.size} ciclos • R$ ${fmt(hourly)}/h observado${if (rs.size < 5) " • poucos dados" else ""}") { selected = rs.first().zone }
        }
    }
    if (routing) FullDialog("Destino e retorno", { routing = false; revision++ }) { RoutingPanel(vm) { route, name -> vm.applyRoute(route, name); routing = false; revision++; vm.tab = 1 } }
}
