@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package br.com.deh.copiloto.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.deh.copiloto.AppViewModel
import br.com.deh.copiloto.capture.CaptureBus
import br.com.deh.copiloto.core.*
import br.com.deh.copiloto.data.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

val Ink = Color(0xFF0D151C); val Panel = Color(0xFF17232E); val Lime = Color(0xFF8BDACB); val Cream = Color(0xFFF4F5F2); val Muted = Color(0xFFA5B4C0); val Amber = Color(0xFFFFCE76); val Coral = Color(0xFFFF9187)
fun fmt(v: Double, digits: Int = 2) = String.format(Locale("pt", "BR"), "%.${digits}f", v)
fun money(cents: Long) = "R$ ${fmt(cents / 100.0)}"
@Composable fun Section(title: String, content: @Composable ColumnScope.() -> Unit) { Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().animateContentSize()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(title, color = Muted, style = MaterialTheme.typography.labelLarge); content() } } }
@Composable fun Note(text: String, color: Color = Muted) { Text(text, color = color, style = MaterialTheme.typography.bodyMedium) }
@Composable fun Field(label: String, value: String, change: (String) -> Unit, number: Boolean = true, modifier: Modifier = Modifier) { OutlinedTextField(value, change, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text), singleLine = true, shape = RoundedCornerShape(14.dp), modifier = modifier.fillMaxWidth()) }
@Composable fun Toggle(label: String, value: Boolean, change: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(value, change) } }
@Composable fun Action(text: String, enabled: Boolean = true, click: () -> Unit) { Button(click, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp)) { Text(text, fontWeight = FontWeight.Bold) } }
@Composable fun Page(content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content) }
@Composable fun FullDialog(title: String, close: () -> Unit, content: @Composable ColumnScope.() -> Unit) { Dialog(close, DialogProperties(usePlatformDefaultWidth = false)) { Surface(Modifier.fillMaxSize(), color = Ink) { Column(Modifier.safeDrawingPadding()) { Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold); TextButton(close) { Text("Fechar") } }; Page(content) } } } }
@Composable fun Analyzer(vm: AppViewModel, import: () -> Unit) {
    var routes by remember { mutableStateOf(false) }; var advanced by rememberSaveable { mutableStateOf(false) }
    val resultRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(vm.analysis) { if (vm.analysis != null) { delay(80); resultRequester.bringIntoView() } }
    fun value(k: String) = vm.fields[k].orEmpty()
    Page {
        Text("A corrida vale?", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        if (vm.demo) Section("DEMONSTRAÇÃO") { Note("Simulação isolada do histórico e dos ganhos.", Amber); OutlinedButton({ vm.fresh() }) { Text("Nova oferta real") } }
        Section("OFERTA") {
            OutlinedButton(import, Modifier.fillMaxWidth(), enabled = !vm.busy) { Text(if (vm.busy) "Lendo imagem…" else "Importar print para OCR") }
            Field("Valor da corrida • R$", value("fare"), { vm.change("fare", it) })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Field("Busca • km", value("pickupKm"), { vm.change("pickupKm", it) }, modifier = Modifier.weight(1f)); Field("Busca • min", value("pickupMin"), { vm.change("pickupMin", it) }, modifier = Modifier.weight(1f)) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Field("Viagem • km", value("tripKm"), { vm.change("tripKm", it) }, modifier = Modifier.weight(1f)); Field("Viagem • min", value("tripMin"), { vm.change("tripMin", it) }, modifier = Modifier.weight(1f)) }
            Field("Origem (opcional)", value("origin"), { vm.change("origin", it) }, false); Field("Destino / ponto confirmado", value("destination"), { vm.change("destination", it) }, false)
        }
        Section("DEPOIS DO DESEMBARQUE") {
            Note("O retorno entra no custo e na duração do ciclo. Calcule a rota entre o destino e sua região de trabalho ou casa.")
            Action("Calcular retorno viário real") { routes = true }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Field("Retorno • km", value("returnKm"), { vm.change("returnKm", it) }, modifier = Modifier.weight(1f)); Field("Retorno • min", value("returnMin"), { vm.change("returnMin", it) }, modifier = Modifier.weight(1f)) }
            Note(vm.roadRoute?.let { "Rota confirmada • ${it.provider}" } ?: "Campos de retorno são estimativas até calcular uma rota.", if (vm.roadRoute != null) Lime else Amber)
            Field("Espera por próxima oferta • min", value("idle"), { vm.change("idle", it) }); Field("Espera de embarque • min", value("wait"), { vm.change("wait", it) })
            OutlinedButton({ vm.learn() }) { Text("Usar histórico pessoal deste período") }
            TextButton({ advanced = !advanced }) { Text(if (advanced) "Ocultar ajustes" else "Custos extras e atrasos") }
            if (advanced) { Field("Atraso operacional • min", value("operational"), { vm.change("operational", it) }); Field("Desvio adicional • km", value("detourKm"), { vm.change("detourKm", it) }); Field("Desvio adicional • min", value("detourMin"), { vm.change("detourMin", it) }); Field("Pedágio / balsa / extra não reembolsado • R$", value("extra"), { vm.change("extra", it) }); Field("Percentual rodoviário do ciclo • %", value("highway"), { vm.change("highway", it) }); Note("O percentual rodoviário ajusta o consumo detalhado. Não some novamente um desvio que já está na rota.") }
        }
        Action("Analisar ciclo completo") { vm.calculate() }
        vm.analysis?.let { a -> Column(Modifier.bringIntoViewRequester(resultRequester), verticalArrangement = Arrangement.spacedBy(12.dp)) { PredictionCard(a.prediction, a.assumptions, a.settings.policy); if (!a.demo) Action(if (a.savedId == null) "Registrar oferta" else "Oferta registrada", a.savedId == null) { vm.save() }; TextButton({ vm.tab = 8 }) { Text("Ver em modo direção") } } }
        TextButton({ vm.tab = 9 }) { Text("Diagnóstico OCR") }

    }
    if (routes) FullDialog("Rota de retorno", { routes = false }) { RoutingPanel(vm) { r, name -> vm.applyRoute(r, name); routes = false } }
}
@Composable fun SelectionContainerCompat(s: String) { androidx.compose.foundation.text.selection.SelectionContainer { Text(s, style = MaterialTheme.typography.bodySmall, color = Muted) } }
@Composable fun PredictionCard(p: Prediction, a: Assumptions, policy: Policy) {
    val color = if (p.score >= 60) Lime else if (p.score >= 45) Amber else Coral
    var details by rememberSaveable { mutableStateOf(false) }
    Section("ANÁLISE DO CICLO") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("${p.score}", color = color, fontSize = 62.sp, fontWeight = FontWeight.Bold)
            Column { Text(p.label, color = color, fontWeight = FontWeight.Bold); Note(p.confidence) }
        }
        Text("R$ ${fmt(p.hourly)}/h", fontSize = 38.sp, fontWeight = FontWeight.Bold)
        Text("R$ ${fmt(p.perKm)}/km • ${money(p.netCents)} líquidos", fontSize = 19.sp)
        Note("Retorno ${fmt(a.repositionKm, 1)} km • ${fmt(a.repositionMin, 0)} min${if (a.routeVerified) " por vias" else " estimados"}", if (a.routeVerified) Muted else Amber)
        p.reasons.firstOrNull { it.startsWith("Acesso") || it.startsWith("Abaixo") }?.let { Note(it, Amber) }
        TextButton({ details = !details }) { Text(if (details) "Menos detalhes" else "Por que este score?") }
        if (details) {
            Note("${fmt(p.totalKm, 1)} km • ${fmt(p.totalMin, 0)} min no ciclo\nCusto variável ${money(p.variableCostCents)} • fixo ${money(p.fixedCostCents)}")
            Note("Estratégia: ${when (policy.strategy) { Strategy.BALANCED -> "considera o menor desempenho entre hora e km"; Strategy.HOUR -> "prioriza o mínimo por hora"; Strategy.KM -> "prioriza o mínimo por km" }}. Mínimos: R$ ${fmt(policy.minHourly)}/h e R$ ${fmt(policy.minPerKm)}/km.")
            Note("Cenários de espera: R$ ${fmt(p.lowHourly)} a R$ ${fmt(p.highHourly)}/h. Não são intervalos de confiança estatística.")
            Text("Avaliação do destino: ${p.destinationScore?.toString() ?: "aguardando rota"}", fontWeight = FontWeight.Bold)
            Note("Tempo após desembarque equivale a ${money(RouteEconomics.opportunityCostCents(a, policy))} no seu mínimo/h. É uma comparação; não descontamos esse valor novamente do lucro.")
            p.reasons.forEach { Note("• $it") }
            Note("${a.zoneSamples} observações usadas para estimar espera regional.")
        }
    }
}
@Composable fun History(vm: AppViewModel, export: () -> Unit) {
    val offers by vm.repository.offers.collectAsStateWithLifecycle()
    val rides by vm.repository.rides.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var actualId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val actuals = remember(rides) { rides.associateBy { it.offerId } }
    val filtered = remember(offers, query, filter) { offers.filter { (filter == "ALL" || it.status == filter) && normalized(it.offer().destination + " " + it.offer().origin).contains(normalized(query)) } }
    LazyColumn(Modifier.fillMaxSize(), state = rememberLazyListState(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Decisões que\nviram aprendizado.", fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(export) { Text("Exportar CSV") }
            if (offers.size >= 10000) Note("Mostrando as 10 mil ofertas mais recentes. O CSV inclui todos os registros ainda armazenados.")
            Field("Buscar origem ou destino", query, { query = it }, false)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL" to "Todas", "ACCEPTED" to "Aceitas", "COMPLETED" to "Concluídas", "DISMISSED" to "Ignoradas").forEach { (status, title) -> FilterChip(filter == status, { filter = status }, label = { Text(title) }) }
            }
        }
        if (filtered.isEmpty()) item {
            Text(if (offers.isEmpty()) "Seu histórico começa com a próxima corrida analisada." else "Nenhuma oferta neste filtro.", style = MaterialTheme.typography.titleLarge)
            Note("Uma oferta analisada não é receita. Registre o resultado real para acompanhar seus ganhos.")
            Action("Simular corrida") { vm.example() }
        }
        items(filtered, key = { it.id }) { row ->
            val offer = row.offer(); val real = actuals[row.id]
            Column(Modifier.fillMaxWidth().clickable { detailId = row.id }.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(money(real?.fareCents ?: offer.fareCents), Modifier.weight(1f), fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text(if (real == null) "Score ${row.score}" else "Concluída", color = if (real == null) Muted else Lime)
                }
                Text(offer.destination.ifBlank { "Destino não informado" }, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Note("${clock(row.createdAt)} • ${Instant.ofEpochMilli(row.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM"))} • ${offer.origin.ifBlank { "Origem não informada" }}")
                val hourly = real?.let { it.netCents / 100.0 / it.totalMin * 60 } ?: row.predictedHourly
                val perKm = real?.let { it.netCents / 100.0 / it.totalKm } ?: row.predictedPerKm
                Text("${money(real?.netCents ?: row.predictedNet)} líquidos", color = Lime, fontWeight = FontWeight.Medium)
                Note("R$ ${fmt(hourly)}/h • R$ ${fmt(perKm)}/km • ${if (real != null) "resultado informado" else "previsão"}")
                Note(when (row.status) { "ACCEPTED" -> "Aceita • falta resultado real"; "DISMISSED" -> "Ignorada"; "COMPLETED" -> "Toque para comparar previsão e resultado"; else -> "Analisada • decisão não registrada" })
            }
            HorizontalDivider(color = Muted.copy(alpha = .18f))
        }
    }
    offers.firstOrNull { it.id == detailId }?.let { row ->
        FullDialog("Detalhes da corrida", { detailId = null }) {
            val offer = row.offer(); val real = actuals[row.id]
            Text("${offer.origin.ifBlank { "Origem não informada" }} → ${offer.destination.ifBlank { "Destino não informado" }}", style = MaterialTheme.typography.titleLarge)
            Eyebrow("PREVISTO • SCORE ${row.score}")
            Text("R$ ${fmt(row.predictedHourly)}/h", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Note("${money(row.predictedNet)} líquidos • R$ ${fmt(row.predictedPerKm)}/km\n${fmt(row.totalKm, 1)} km • ${fmt(row.totalMin, 0)} min no ciclo")
            Note(if (row.assumptions().routeVerified) "Retorno viário: ${row.assumptions().routeProvider}" else "Retorno estimado; rota não confirmada", if (row.assumptions().routeVerified) Muted else Amber)
            if (real != null) {
                val hourly = real.netCents / 100.0 / real.totalMin * 60
                HorizontalDivider(); Eyebrow("RESULTADO INFORMADO", Lime)
                Text("R$ ${fmt(hourly)}/h", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Lime)
                Note("Recebido ${money(real.fareCents)} • líquido ${money(real.netCents)}\n${fmt(real.totalKm, 1)} km • ${fmt(real.totalMin, 0)} min")
                if (kotlin.math.abs(row.predictedHourly) >= 1) Note("Variação em relação à previsão: ${fmt((hourly - row.predictedHourly) / kotlin.math.abs(row.predictedHourly) * 100, 1)}%")
                Note("Custo preservado da análise: R$ ${fmt(row.cost().rate(row.assumptions().highwayFraction), 3)}/km. Custos não são despesas importadas do banco.")
            } else {
                Action("Aceitei esta corrida") { vm.task { vm.repository.dao.status(row.id, "ACCEPTED"); val settings = vm.repository.settings.value; vm.repository.settingsStore.save(settings.copy(shiftMode = "RIDING")); vm.message("Decisão registrada. Informe o resultado ao terminar.") } }
                OutlinedButton({ vm.task { vm.repository.dao.status(row.id, "DISMISSED"); vm.message("Oferta marcada como ignorada") } }, Modifier.fillMaxWidth()) { Text("Ignorei esta corrida") }
                Action("Informar resultado real") { actualId = row.id; detailId = null }
                Note("Estas ações apenas registram sua decisão. Não controlam o Uber ou 99.")
            }
            TextButton({ deleteId = row.id }) { Text("Excluir registro", color = Coral) }
        }
    }
    offers.firstOrNull { it.id == actualId }?.let { row -> ActualDialog(vm, row) { actualId = null } }
    deleteId?.let { id -> AlertDialog({ deleteId = null }, title = { Text("Excluir oferta e resultado?") }, text = { Text("O registro será removido deste aparelho.") }, confirmButton = { TextButton({ vm.task { vm.repository.dao.delete(id); deleteId = null; detailId = null; vm.message("Registro excluído") } }) { Text("Excluir") } }, dismissButton = { TextButton({ deleteId = null }) { Text("Cancelar") } }) }
}
@Composable fun ActualDialog(vm: AppViewModel, row: OfferRow, close: () -> Unit) {
    val f = vm.actualDraft(row.id)
    var operational by rememberSaveable { mutableStateOf(false) }
    FullDialog("Resultado observado", close) {
        Note("Informe valores medidos do ciclo completo, incluindo retorno. Previsões não são preenchidas como se fossem resultados reais.")
        val inputFields = listOf("fare" to "Recebido • R$", "km" to "Km totais do ciclo", "min" to "Minutos totais do ciclo", "paid" to "Km com passageiro", "productive" to "Minutos com passageiro", "extra" to "Extra não reembolsado • R$", "zone" to "Região do desembarque", "arrived" to "Desembarque ocorreu há quantos minutos?", "idle" to "Espera medida até nova oferta • min (opcional)", "returnKm" to "Retorno medido • km (opcional)", "returnMin" to "Retorno medido • min (opcional)")
        inputFields.take(5).forEach { (key, label) -> Field(label, f[key].orEmpty(), { f[key] = it; vm.persistActual(row.id) }) }
        TextButton({ operational = !operational }) { Text(if (operational) "Ocultar detalhes" else "Extras e aprendizado do destino") }
        if (operational) inputFields.drop(5).forEach { (key, label) -> Field(label, f[key].orEmpty(), { f[key] = it; vm.persistActual(row.id) }, key != "zone") }
        Action("Concluir e registrar ganhos") { vm.task {
            fun n(k: String) = decimal(f[k].orEmpty())
            fun optional(k: String) = f[k]?.takeIf(String::isNotBlank)?.let(::decimal)
            require(n("arrived") in 0.0..10080.0)
            vm.repository.complete(row.id, cents(n("fare")), n("km"), n("min"), n("paid"), n("productive"), cents(n("extra")), f["zone"].orEmpty(), optional("idle"), optional("returnKm"), optional("returnMin"), System.currentTimeMillis() - (n("arrived") * 60000).toLong()); vm.clearActual(row.id); vm.repository.settingsStore.save(vm.repository.settings.value.copy(shiftMode = if (vm.app.routing.settings.returningHome) "HOME" else "WAITING")); close(); vm.message("Corrida concluída sem duplicar os ganhos")
        } }
    }
}
@Composable fun SettingsScreen(vm: AppViewModel, s: Settings) {
    val f = rememberDraft("settings-${s.hashCode()}") { mutableStateMapOf("vehicle" to s.vehicle, "simple" to s.cost.simplePerKm.toString(), "fuel" to s.cost.fuelPrice.toString(), "city" to s.cost.cityKml.toString(), "highway" to s.cost.highwayKml.toString(), "maintenance" to s.cost.maintenance.toString(), "tires" to s.cost.tires.toString(), "oil" to s.cost.oil.toString(), "depreciation" to s.cost.depreciation.toString(), "other" to s.cost.other.toString(), "fixed" to s.cost.fixedPerHour.toString(), "minH" to s.policy.minHourly.toString(), "minK" to s.policy.minPerKm.toString(), "goal" to s.dailyGoal.toString(), "wait" to s.defaults.boardingWaitMin.toString(), "idle" to s.defaults.nextOfferWaitMin.toString(), "returnKm" to s.defaults.repositionKm.toString(), "returnMin" to s.defaults.repositionMin.toString()) }
    var fuelBased by rememberSaveable(s) { mutableStateOf(s.cost.fuelBasedSimple) }; var detailed by rememberSaveable(s) { mutableStateOf(s.cost.detailed) }; var strategy by rememberSaveable(s) { mutableStateOf(s.policy.strategy) }; var netGoal by rememberSaveable(s) { mutableStateOf(s.goalNet) }; var voice by rememberSaveable(s) { mutableStateOf(s.voice) }; var haptic by rememberSaveable(s) { mutableStateOf(s.haptic) }; var crop by rememberSaveable(s) { mutableFloatStateOf(s.cropTop.toFloat()) }; var interval by rememberSaveable(s) { mutableFloatStateOf(s.intervalMs.toFloat()) }; var retention by rememberSaveable(s) { mutableIntStateOf(s.retentionDays) }; var clear by remember { mutableStateOf(false) }
    Page {
        Text("Seu ponto de equilíbrio", fontSize = 29.sp, fontWeight = FontWeight.Bold); TextButton({ vm.tab = 7 }) { Text("Fotografar painel ou atualizar combustível") }
        Section("VEÍCULO E CUSTOS") {
            Field("Veículo", f["vehicle"].orEmpty(), { f["vehicle"] = it }, false); Toggle("Custo detalhado", detailed, { detailed = it }); if (!detailed) Toggle("Calcular com combustível e consumo", fuelBased, { fuelBased = it })
            val entries = if (detailed) listOf("fuel" to "Combustível • R$/litro", "city" to "Consumo urbano • km/l", "highway" to "Consumo rodoviário • km/l", "maintenance" to "Manutenção • R$/km", "tires" to "Pneus • R$/km", "oil" to "Óleo • R$/km", "depreciation" to "Depreciação • R$/km", "other" to "Outros • R$/km") else if (fuelBased) listOf("fuel" to "Combustível • R$/litro", "city" to "Consumo médio • km/L") else listOf("simple" to "Custo por km informado anteriormente")
            entries.forEach { (k, label) -> Field(label, f[k].orEmpty(), { f[k] = it }) }; Field("Custo fixo • R$/hora online", f["fixed"].orEmpty(), { f["fixed"] = it })
            val preview = runCatching {
                fun value(k: String) = decimal(f[k].orEmpty())
                VehicleCost(detailed, value("simple"), value("fuel"), value("city"), value("highway"), value("maintenance"), value("tires"), value("oil"), value("depreciation"), value("other"), value("fixed"), fuelBased).rate()
            }.getOrNull()
            Eyebrow("CUSTO ESTIMADO POR KM")
            Text(preview?.let { "R$ ${fmt(it, 3)}/km" } ?: "Confira os valores", fontSize = 29.sp, color = Lime)
            Note("Custo variável urbano. O custo fixo/h é somado pelo tempo. Mudanças no custo fixo passam a valer no próximo turno.")
        }
        Section("MÍNIMOS E ESTRATÉGIA") { Field("Líquido mínimo • R$/hora", f["minH"].orEmpty(), { f["minH"] = it }); Field("Líquido mínimo • R$/km", f["minK"].orEmpty(), { f["minK"] = it }); Strategy.entries.forEach { mode -> FilterChip(strategy == mode, { strategy = mode }, label = { Text(when (mode) { Strategy.BALANCED -> "Balanceado"; Strategy.HOUR -> "Máximo R$/hora"; Strategy.KM -> "Máximo R$/km" }) }) }; Field("Meta diária • R$", f["goal"].orEmpty(), { f["goal"] = it }); Toggle("Meta pelo ganho líquido", netGoal, { netGoal = it }) }
        Section("PREMISSAS INICIAIS") { listOf("wait" to "Espera no embarque • min", "idle" to "Espera por nova oferta • min", "returnKm" to "Retorno estimado • km", "returnMin" to "Retorno estimado • min").forEach { (k, label) -> Field(label, f[k].orEmpty(), { f[k] = it }) }; Note("Essas premissas não substituem rotas. Em Regiões, defina casa como retorno para incluir o deslocamento final ao avaliar corridas.") }
        Section("LEITURA E PRIVACIDADE") { Toggle("Alertas por voz", voice, { voice = it }); Toggle("Vibração ao ler nova oferta", haptic, { haptic = it }); Note("Ignorar ${fmt(crop * 100.0, 0)}% do topo da tela"); Slider(crop, { crop = it }, valueRange = 0f..0.65f); Note("Intervalo entre quadros: ${interval.toLong()} ms"); Slider(interval, { interval = it }, valueRange = 650f..2000f); Note("Retenção local: $retention dias"); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(30, 90, 365).forEach { d -> FilterChip(retention == d, { retention = d }, label = { Text("$d") }) } }; Note("OCR e histórico ficam no aparelho. Rotas enviam coordenadas ao servidor escolhido; o mapa solicita imagens dos trechos visualizados. Sem conta, anúncios ou envio de capturas.") }
        Action("Salvar configuração") { vm.task {
            fun n(k: String) = decimal(f[k].orEmpty())
            val c = VehicleCost(detailed, if (fuelBased) Fuel.perKm(n("fuel"), n("city")) else n("simple"), n("fuel"), n("city"), n("highway"), n("maintenance"), n("tires"), n("oil"), n("depreciation"), n("other"), n("fixed"), fuelBased)
            val updated = Settings(f["vehicle"].orEmpty(), c, Policy(n("minH"), n("minK"), strategy), Assumptions(boardingWaitMin = n("wait"), repositionKm = n("returnKm"), repositionMin = n("returnMin"), nextOfferWaitMin = n("idle")), n("goal"), netGoal, true, voice, haptic, crop.toDouble(), interval.toLong(), retention, s.goalDeadline, s.shiftMode, s.fuelName)
            vm.repository.settingsStore.save(updated); vm.message("Custos e mínimos salvos"); vm.tab = 0
        } }
        TextButton({ clear = true }) { Text("Apagar histórico local", color = Coral) }
        Note("Dehpilot 0.2.0 • Android 8 ou superior\nDecisão assistida. Nenhuma aceitação, recusa ou toque automático em outros aplicativos.")
    }
    if (clear) AlertDialog({ clear = false }, title = { Text("Apagar todo o histórico?") }, text = { Text("Ofertas, corridas, turnos e histórico de custos serão excluídos. Exporte o CSV antes se quiser guardá-los.") }, confirmButton = { TextButton({ vm.task { vm.repository.clear(); clear = false } }) { Text("Apagar") } }, dismissButton = { TextButton({ clear = false }) { Text("Cancelar") } })
}
