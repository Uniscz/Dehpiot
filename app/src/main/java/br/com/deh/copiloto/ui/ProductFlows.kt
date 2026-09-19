@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package br.com.deh.copiloto.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.deh.copiloto.AppViewModel
import br.com.deh.copiloto.capture.CaptureBus
import br.com.deh.copiloto.core.*
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun FuelScreen(vm: AppViewModel) {
    val settings by vm.repository.settings.collectAsStateWithLifecycle()
    val history by vm.repository.costHistory.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var price by rememberSaveable { mutableStateOf(settings.cost.fuelPrice.toString()) }
    var consumption by rememberSaveable { mutableStateOf(settings.cost.cityKml.toString()) }
    var name by rememberSaveable { mutableStateOf(settings.fuelName) }
    var unit by rememberSaveable { mutableStateOf(ConsumptionUnit.KM_L) }
    var cameraUri by rememberSaveable { mutableStateOf("") }
    var confirmed by rememberSaveable { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { confirmed = false; selected = -1; vm.readPanel(uri) } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success -> if (success && cameraUri.isNotEmpty()) { confirmed = false; selected = -1; vm.readPanel(Uri.parse(cameraUri)) } else vm.message("Foto cancelada. Você pode digitar o consumo.") }
    val rate = runCatching { Fuel.perKm(decimal(price), Fuel.kmPerLiter(decimal(consumption), unit)) }.getOrNull()
    Page {
        Eyebrow("O CUSTO COMEÇA AQUI", Lime)
        Text("Cada quilômetro\nconta.", fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 41.sp)
        Note("Use o consumo médio do computador de bordo. A leitura acontece no aparelho e só é aplicada depois da sua confirmação.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton({
                runCatching {
                    val dir = File(context.cacheDir, "panel").apply { mkdirs() }
                    dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
                    val file = File.createTempFile("panel-", ".jpg", dir)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
                    cameraUri = uri.toString(); camera.launch(uri)
                }.onFailure { vm.message("Câmera indisponível. Escolha uma foto ou digite o consumo.") }
            }, Modifier.weight(1f), enabled = !vm.panelBusy) { Text("Fotografar painel") }
            OutlinedButton({ gallery.launch(arrayOf("image/*")) }, Modifier.weight(1f), enabled = !vm.panelBusy) { Text("Galeria") }
        }
        if (vm.panelBusy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Note("Lendo o painel no aparelho…") }
        vm.panelReading?.let { reading ->
            Section("CONFIRA A LEITURA") {
                Note(reading.message, if (reading.unambiguous) Lime else Amber)
                reading.candidates.forEachIndexed { index, candidate ->
                    FilterChip(selected == index, onClick = { selected = index; consumption = candidate.value.toString(); unit = candidate.unit; confirmed = true }, label = { Text("${fmt(candidate.value, 1)} ${candidate.unit.label} • confirmar") })
                    Note(candidate.context)
                }
                Note("A unidade e o número podem estar incorretos. Corrija abaixo se necessário. Autonomia e hodômetro não são usados como consumo.")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { listOf("Etanol", "Gasolina").forEach { fuel -> FilterChip(name == fuel, { name = fuel }, label = { Text(fuel) }) } }
        Field("$name • R$/L", price, { price = it })
        Field("Consumo médio • ${unit.label}", consumption, { consumption = it; confirmed = true; selected = -1 })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { ConsumptionUnit.entries.forEach { u -> FilterChip(unit == u, { unit = u; confirmed = true; selected = -1 }, label = { Text(u.label) }) } }
        Eyebrow("COMBUSTÍVEL POR QUILÔMETRO")
        Text(rate?.let { "R$ ${fmt(it, 3)}/km" } ?: "Confira os valores", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Lime)
        Note(if (settings.cost.detailed) "O valor será somado às reservas de manutenção, óleo, pneus, depreciação e outros custos já configurados." else "Modo simples: inclui apenas combustível. Manutenção, pneus e depreciação podem ser adicionados nos custos detalhados.")
        if (!confirmed) Note("Confirme um candidato ou corrija o campo de consumo antes de salvar.", Amber)
        Action(if (saving) "Salvando…" else "Confirmar e salvar consumo", rate != null && confirmed && !vm.panelBusy && !saving) {
            vm.task { saving = true; try { vm.saveFuel(decimal(price), decimal(consumption), unit, name); confirmed = true } finally { saving = false } }
        }
        MenuRow("Reservas e custos detalhados", "Manutenção, pneus, óleo, depreciação e custos fixos") { vm.tab = 4 }
        if (history.isNotEmpty()) {
            Eyebrow("HISTÓRICO DE CUSTOS")
            history.takeLast(8).reversed().forEach { change ->
                Row(Modifier.fillMaxWidth()) { Column(Modifier.weight(1f)) { Text(Instant.ofEpochMilli(change.at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))); Note("R$ ${fmt(change.cost.fuelPrice)}/L • ${fmt(change.cost.cityKml, 1)} km/L") }; Text("R$ ${fmt(change.cost.rate(), 3)}/km", color = Lime) }
            }
            Note("As corridas guardam o custo usado na análise. Atualizar o combustível não reescreve resultados anteriores.")
        }
    }
}

@Composable fun MoreScreen(vm: AppViewModel, export: () -> Unit) {
    Page {
        Text("Feito para\no seu ritmo.", fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
        MenuRow("Combustível e consumo", "Fotografe o painel ou atualize o preço") { vm.tab = 7 }
        MenuRow("Simulador de corrida", "Entenda o ciclo inteiro antes de decidir") { vm.tab = 1 }
        MenuRow("Veículo, custos e estratégia", "Ajuste seus mínimos e reservas") { vm.tab = 4 }
        MenuRow("Leitura e permissões", "Ative ou encerre a leitura de ofertas") { vm.tab = 11 }
        MenuRow("Exportar histórico", "Leve seus registros em CSV") { export() }
        MenuRow("Diagnóstico OCR", "Ferramenta técnica para conferir uma leitura") { vm.tab = 9 }
        Note("Dehpilot 0.2.0 • dados pessoais no aparelho\nSuas decisões continuam suas. O aplicativo não aceita nem recusa corridas por você.")
    }
}
@Composable fun PermissionsScreen(vm: AppViewModel, capture: () -> Unit, stop: () -> Unit) {
    val status by CaptureBus.state.collectAsStateWithLifecycle()
    Page {
        Text("Você controla\na leitura.", fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
        Eyebrow(if (status.running) "LEITURA ATIVA" else "LEITURA DESLIGADA", if (status.running) Lime else Muted)
        Note(status.message)
        Text("Sobreposição", style = MaterialTheme.typography.titleLarge)
        Note("Mostra a análise sem você sair do Uber ou 99. Você pode mover o resumo na tela.")
        Text("Captura de tela", style = MaterialTheme.typography.titleLarge)
        Note("Lê ofertas enquanto a leitura estiver ativa e o Dehpilot estiver em segundo plano. O Android pede sua autorização. Pause ou pare pela notificação a qualquer momento.")
        Text("Notificações", style = MaterialTheme.typography.titleLarge)
        Note("Mantêm os controles de pausar e parar acessíveis durante o turno.")
        Text("Localização", style = MaterialTheme.typography.titleLarge)
        Note("É solicitada ao tocar em Meu GPS no mapa, para confirmar uma posição. Não é necessária para digitar ou importar uma oferta.")
        Action(if (status.running) "Parar leitura" else "Continuar para autorizar leitura") { if (status.running) stop() else capture() }
        Note("Fotos importadas e textos OCR não são enviados a servidores. Rotas e mapas usam coordenadas na internet.")
    }
}
@Composable fun DiagnosticsScreen(vm: AppViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    Page {
        Text("Diagnóstico OCR", style = MaterialTheme.typography.headlineMedium)
        Note("Ferramenta de conferência. Nunca trata texto de teste como ganho realizado.")
        vm.diagnostic?.let { reading ->
            Note("Parser: ofertas • ${reading.parsed.warning}\nOCR: ${reading.latencyMs} ms • ${reading.lines.size} regiões\nImagem + OCR + tentativa de rota: ${vm.analysisLatencyMs?.let { "$it ms" } ?: "em andamento"}")
            vm.diagnosticBitmap?.let { bitmap ->
                Box(Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height)) {
                    Image(bitmap.asImageBitmap(), "Captura com regiões do OCR destacadas", Modifier.fillMaxSize())
                    Canvas(Modifier.fillMaxSize()) {
                        val sx = size.width / bitmap.width; val sy = size.height / bitmap.height
                        reading.lines.forEach { line -> drawRect(Amber, Offset(line.left * sx, line.top * sy), Size((line.right - line.left) * sx, (line.bottom - line.top) * sy), style = Stroke(1.5.dp.toPx())) }
                    }
                }
            }
            SelectionContainerCompat(reading.parsed.text)
            reading.lines.forEach { line -> Note("${line.text} [${line.left}, ${line.top}, ${line.right}, ${line.bottom}]") }
        } ?: Note("Importe uma oferta no simulador para inspecionar a leitura.")
        OutlinedTextField(text, { text = it }, label = { Text("Texto de oferta para teste") }, minLines = 4, modifier = Modifier.fillMaxWidth())
        Action("Testar parser de oferta", text.isNotBlank()) { vm.parseText(text); vm.message("Parser executado em demonstração") }
        Action("Carregar demonstração") { vm.example() }
        if (vm.panelText.isNotBlank()) { Eyebrow("OCR DO PAINEL • PARSER SEPARADO"); SelectionContainerCompat(vm.panelText) }
    }
}
@Composable fun DrivingScreen(vm: AppViewModel, capture: () -> Unit, stop: () -> Unit) {
    val latest by CaptureBus.latest.collectAsStateWithLifecycle()
    val status by CaptureBus.state.collectAsStateWithLifecycle()
    val now = liveNow(); val local = vm.analysis?.takeIf { now - it.createdAt in 0..60_000 }
    val live = latest?.takeIf { now - it.at in 0..45_000 }
    val p = if (live != null && (local == null || live.at > local.createdAt)) live.prediction else local?.prediction
    var expanded by rememberSaveable { mutableStateOf(false) }
    Page {
        Eyebrow(if (local?.demo == true && p === local.prediction) "DEMONSTRAÇÃO • SEM GANHOS" else "LEITURA RÁPIDA", Lime)
        if (p == null) {
            Text("Pronto para\na próxima.", fontSize = 43.sp, lineHeight = 49.sp, fontWeight = FontWeight.Bold)
            Note("Uma análise recente aparece aqui. Resultados antigos são ocultados para não confundir a próxima decisão.")
            Action(if (status.running) "Leitura ativa • parar" else "Ativar leitura") { if (status.running) stop() else vm.tab = 11 }
            Note("${status.message}. O resumo também pode aparecer sobre o app de corrida.")
        } else {
            val color = if (p.score >= 60) Lime else if (p.score >= 45) Amber else Coral
            Text("${p.score}", fontSize = 100.sp, lineHeight = 108.sp, fontWeight = FontWeight.Bold, color = color)
            Text(p.label, fontSize = 26.sp, color = color, fontWeight = FontWeight.Bold)
            Text("R$ ${fmt(p.hourly)}/h", fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) { Metric("R$ ${fmt(p.perKm)}", "Líquido / km", Modifier.weight(1f)); Metric(money(p.netCents), "Lucro previsto", Modifier.weight(1f)) }
            HorizontalDivider()
            Note(p.reasons.firstOrNull { it.startsWith("Acesso") || it.startsWith("Retorno estimado") || it.startsWith("Abaixo") } ?: "Confira o destino e o caminho de saída", Amber)
            Note(if (p.score >= 60) "Dentro dos mínimos da estratégia escolhida" else "O ciclo completo reduz a rentabilidade")
            Note(p.confidence)
            OutlinedButton({ expanded = !expanded }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (expanded) "Menos detalhes" else "Entender o resultado • com o carro parado") }
            AnimatedVisibility(expanded) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Note("${fmt(p.totalKm, 1)} km • ${fmt(p.totalMin, 0)} min no ciclo"); p.reasons.forEach { Note(it) }; Note("Espera estimada: R$ ${fmt(p.lowHourly)} a R$ ${fmt(p.highHourly)}/h") } }
        }
    }
}
@Composable fun OnboardingScreen(vm: AppViewModel, import: () -> Unit) {
    val s by vm.repository.settings.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    PredictiveBackHandler(enabled = step > 0) { progress -> progress.collect { }; step-- }
    var vehicle by rememberSaveable { mutableStateOf(s.vehicle) }; var price by rememberSaveable { mutableStateOf(s.cost.fuelPrice.toString()) }; var kml by rememberSaveable { mutableStateOf(s.cost.cityKml.toString()) }; var goal by rememberSaveable { mutableStateOf(s.dailyGoal.toString()) }
    var strategy by rememberSaveable { mutableStateOf(s.policy.strategy) }
    Page {
        LinearProgressIndicator(progress = { (step + 1) / 5f }, modifier = Modifier.fillMaxWidth(), color = Lime)
        Eyebrow("PASSO ${step + 1} DE 5")
        when (step) {
            0 -> { BrandMark(Modifier.size(72.dp)); Text("Saiba quanto uma corrida realmente vale.", fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold); Note("Busca, viagem, espera e saída do destino. O valor da oferta é só o começo."); Action("Vamos começar") { step++ } }
            1 -> { Text("Seu veículo", style = MaterialTheme.typography.headlineLarge); Field("Como você chama seu carro?", vehicle, { vehicle = it }, false); Field("Consumo médio • km/L", kml, { kml = it }); TextButton({ vm.tab = 7 }) { Text("Prefiro fotografar o painel") }; Note("Se você salvou uma foto do painel, use o consumo confirmado abaixo."); TextButton({ kml = vm.repository.settings.value.cost.cityKml.toString(); price = vm.repository.settings.value.cost.fuelPrice.toString(); vm.message("Consumo salvo preenchido") }) { Text("Usar consumo salvo") }; Action("Continuar") { runCatching { Fuel.kmPerLiter(decimal(kml), ConsumptionUnit.KM_L); step++ }.onFailure { vm.message(it.message.orEmpty()) } } }
            2 -> { Text("Quanto custa rodar?", style = MaterialTheme.typography.headlineLarge); Field("${s.fuelName} • R$/litro", price, { price = it }); Note("Comece pelo combustível. Você poderá adicionar manutenção e outros custos depois."); runCatching { Fuel.perKm(decimal(price), decimal(kml)) }.getOrNull()?.let { Text("R$ ${fmt(it, 3)}/km", fontSize = 38.sp, color = Lime) }; Action("Continuar") { runCatching { Fuel.perKm(decimal(price), decimal(kml)); step++ }.onFailure { vm.message(it.message.orEmpty()) } } }
            3 -> { Text("Trabalhe com direção.", style = MaterialTheme.typography.headlineLarge); Field("Meta líquida diária • R$", goal, { goal = it }); Strategy.entries.forEach { mode -> FilterChip(strategy == mode, { strategy = mode }, label = { Text(when (mode) { Strategy.BALANCED -> "Equilibrar tempo e distância"; Strategy.HOUR -> "Priorizar ganho por hora"; Strategy.KM -> "Priorizar ganho por km" }) }) }; Action("Salvar e continuar") { vm.task { val target = decimal(goal); require(target > 0); val current = vm.repository.settings.value; vm.repository.settingsStore.save(current.copy(vehicle = vehicle.ifBlank { "Meu veículo" }, cost = Fuel.update(current.cost, decimal(price), decimal(kml)), dailyGoal = target, goalNet = true, policy = current.policy.copy(strategy = strategy), onboarded = true)); step++ } } }
            else -> { Text("Seu Dehpilot\nestá configurado.", fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold); Note("Teste uma oferta com o carro parado. As permissões são explicadas no momento em que você ativa cada recurso."); Action("Testar OCR com um print") { import() }; OutlinedButton({ vm.example() }, Modifier.fillMaxWidth()) { Text("Testar demonstração") }; TextButton({ vm.tab = 11 }) { Text("Entender permissões") }; Action("Ir para Hoje") { vm.tab = 0 } }
        }
        if (step > 0) TextButton({ step-- }) { Text("Etapa anterior") }
    }
}
