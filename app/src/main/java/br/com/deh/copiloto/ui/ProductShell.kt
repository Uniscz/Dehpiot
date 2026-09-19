@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package br.com.deh.copiloto.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import br.com.deh.copiloto.AppViewModel
import br.com.deh.copiloto.capture.CaptureBus
import br.com.deh.copiloto.core.*
import br.com.deh.copiloto.data.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable fun rememberDraft(key: String, init: () -> SnapshotStateMap<String, String>): SnapshotStateMap<String, String> {
    val saver = listSaver<SnapshotStateMap<String, String>, String>(save = { m -> m.flatMap { listOf(it.key, it.value) } }, restore = { list -> mutableStateMapOf<String, String>().apply { list.chunked(2).forEach { put(it[0], it[1]) } } })
    return rememberSaveable(key, saver = saver, init = init)
}
fun RideRow.observation() = ObservedCycle(completedAt, fareCents, variableCostCents, fixedCostCents, totalKm, paidKm, totalMin, productiveMin, zone)
fun SessionRow.interval() = OnlineInterval(start, end, fixedPerHour)
fun dayStart(date: LocalDate = LocalDate.now()) = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
fun clock(time: Long) = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
fun elapsed(minutes: Double) = "${minutes.toInt() / 60}h ${minutes.toInt() % 60}min"
@Composable fun liveNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) { now = System.currentTimeMillis(); delay(15000) }
        }
    }
    return now
}
@Composable fun AppRoot(vm: AppViewModel, capture: () -> Unit, stop: () -> Unit, import: () -> Unit, export: () -> Unit) {
    val settings by vm.repository.settings.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }; val holder = rememberSaveableStateHolder()
    var backProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = vm.canGoBack) { progress ->
        try { progress.collect { backProgress = it.progress }; vm.back() } finally { backProgress = 0f }
    }
    LaunchedEffect(vm) { vm.messages.collect { snack.showSnackbar(it) } }
    val tabs = listOf(0 to "Hoje", 3 to "Mapa", 2 to "Histórico", 5 to "Análises", 6 to "Mais")
    val haptic = LocalHapticFeedback.current
    MaterialTheme(colorScheme = darkColorScheme(primary = Lime, onPrimary = Ink, background = Ink, surface = Panel, onSurface = Cream, onBackground = Cream, secondary = Amber, error = Coral, onSurfaceVariant = Muted, surfaceVariant = Panel, outline = Muted.copy(alpha = .4f))) {
        Scaffold(containerColor = Ink, snackbarHost = { SnackbarHost(snack) }, topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (vm.canGoBack) IconButton({ vm.back() }, Modifier.semantics { contentDescription = "Voltar" }) { Text("‹", fontSize = 32.sp) }
                else BrandMark(Modifier.size(40.dp).padding(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("DEHPILOT", fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(when (vm.tab) { 0 -> "Seu turno, com clareza"; 1 -> "Analisar corrida"; 2 -> "Seu histórico"; 3 -> "Onde vale a pena"; 4 -> "Preferências"; 5 -> "Seu desempenho"; 7 -> "Consumo do veículo"; 8 -> "Modo direção"; 9 -> "Diagnóstico"; 10 -> "Primeiros passos"; 11 -> "Leitura de ofertas"; else -> "Seu Dehpilot" }, fontSize = 11.sp, color = Muted)
                }
                if (vm.tab == 0) TextButton({ vm.tab = 8 }) { Text("Dirigir") }
            }
        }, bottomBar = {
            if (vm.tab != 8 && vm.tab != 10) NavigationBar(containerColor = Ink, tonalElevation = 0.dp) {
                tabs.forEach { (id, label) -> NavigationBarItem(selected = vm.tab == id, onClick = { if (settings.haptic) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.tab = id }, icon = { NavMark(id, vm.tab == id) }, label = { Text(label, fontSize = 11.sp) }) }
            }
        }) { padding ->
            Box(Modifier.padding(padding).graphicsLayer { scaleX = 1 - backProgress * .035f; scaleY = 1 - backProgress * .035f; translationX = backProgress * 36.dp.toPx() }) {
                Crossfade(targetState = vm.tab, animationSpec = tween(160), label = "page") { page ->
                    holder.SaveableStateProvider(page) {
                        when (page) {
                            0 -> TodayScreen(vm, settings, capture, stop)
                            1 -> Analyzer(vm, import)
                            2 -> History(vm, export)
                            3 -> PersonalMap(vm)
                            4 -> SettingsScreen(vm, settings)
                            5 -> AnalyticsScreen(vm)
                            6 -> MoreScreen(vm, export)
                            7 -> FuelScreen(vm)
                            8 -> DrivingScreen(vm, capture, stop)
                            9 -> DiagnosticsScreen(vm)
                            10 -> OnboardingScreen(vm, import)
                            11 -> PermissionsScreen(vm, capture, stop)
                        }
                    }
                }
            }
        }
    }
}
@Composable fun BrandMark(modifier: Modifier = Modifier) { Canvas(modifier) {
    val w = size.width; val h = size.height
    drawLine(Lime, Offset(w * .2f, h * .9f), Offset(w * .5f, h * .1f), w * .13f, StrokeCap.Round)
    drawLine(Lime, Offset(w * .5f, h * .1f), Offset(w * .8f, h * .9f), w * .13f, StrokeCap.Round)
    drawLine(Cream, Offset(w * .5f, h * .7f), Offset(w * .5f, h * .95f), w * .08f, StrokeCap.Round)
} }
@Composable fun NavMark(id: Int, selected: Boolean) { Canvas(Modifier.size(23.dp)) {
    val c = if (selected) Lime else Muted; val w = size.width; val h = size.height; val stroke = 1.8.dp.toPx()
    when (id) {
        0 -> { drawArc(c, 150f, 240f, false, style = Stroke(stroke)); drawLine(c, Offset(w / 2, h / 2), Offset(w * .78f, h * .24f), stroke, StrokeCap.Round) }
        3 -> { drawCircle(c, w * .33f, Offset(w / 2, h * .4f), style = Stroke(stroke)); drawLine(c, Offset(w * .3f, h * .64f), Offset(w / 2, h * .95f), stroke); drawLine(c, Offset(w * .7f, h * .64f), Offset(w / 2, h * .95f), stroke) }
        2 -> { drawCircle(c, w * .42f, style = Stroke(stroke)); drawLine(c, Offset(w / 2, h * .2f), Offset(w / 2, h / 2), stroke); drawLine(c, Offset(w / 2, h / 2), Offset(w * .73f, h * .62f), stroke) }
        5 -> (0..2).forEach { i -> drawLine(c, Offset(w * (.2f + i * .3f), h * .9f), Offset(w * (.2f + i * .3f), h * (.6f - i * .23f)), stroke * 2, StrokeCap.Round) }
        else -> (0..2).forEach { drawCircle(c, stroke, Offset(w * (.15f + it * .35f), h / 2)) }
    }
} }
@Composable fun Eyebrow(text: String, color: Color = Muted) { Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.3.sp) }
@Composable fun Metric(value: String, label: String, modifier: Modifier = Modifier) { Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(value, fontSize = 23.sp, fontWeight = FontWeight.SemiBold); Note(label) } }
@Composable fun MenuRow(title: String, subtitle: String, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(vertical = 17.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp); Note(subtitle) }
        Text("›", color = Muted, fontSize = 26.sp)
    }; HorizontalDivider(color = Muted.copy(alpha = .15f))
}
@Composable fun Sparkline(values: List<Double>, description: String, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(72.dp).semantics { contentDescription = description }) {
        if (values.size < 2) { drawLine(Muted.copy(alpha = .25f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx()); return@Canvas }
        val low = minOf(0.0, values.min()); val high = maxOf(.01, values.max()); val span = (high - low).coerceAtLeast(.01)
        val path = Path()
        values.forEachIndexed { i, value -> val x = i.toFloat() / (values.size - 1) * size.width; val y = size.height - 5.dp.toPx() - ((value - low) / span).toFloat() * (size.height - 10.dp.toPx()); if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
        drawPath(path, Lime, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}
@Composable fun TodayScreen(vm: AppViewModel, s: Settings, capture: () -> Unit, stop: () -> Unit) {
    val rides by vm.repository.rides.collectAsStateWithLifecycle(); val sessions by vm.repository.sessions.collectAsStateWithLifecycle(); val offers by vm.repository.offers.collectAsStateWithLifecycle(); val captureState by CaptureBus.state.collectAsStateWithLifecycle()
    val online = hasValidatedConnection()
    val now = liveNow(); val from = dayStart(); val active = sessions.any { it.end == null }
    val stats = remember(rides, sessions, from, now) { ProductMetrics.summary(rides.map { it.observation() }, sessions.map { it.interval() }, from, now + 1) }
    val progress = GoalMath.calculate((if (s.goalNet) stats.net else stats.gross) / 100.0, s.dailyGoal, stats.denominatorMinutes, s.goalDeadline.takeIf { it > now }?.let { (it - now) / 60000.0 })
    val animated by animateFloatAsState(progress.fraction, tween(600), label = "goal")
    val market = ProductMetrics.market(offers.map { it.createdAt to it.score }, now)
    var showGoal by rememberSaveable { mutableStateOf(false) }; var showState by rememberSaveable { mutableStateOf(false) }; var endConfirm by rememberSaveable { mutableStateOf(false) }
    Page {
        if (!online) Note("Sem conexão confirmada. OCR e cálculos continuam locais; novas rotas podem ficar indisponíveis.", Amber)
        if (!s.onboarded) {
            Eyebrow("BEM-VINDO AO SEU DEHPILOT", Lime)
            Text("Saiba quanto uma corrida realmente vale.", fontSize = 32.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold)
            Note("Configure combustível, consumo e meta. O resto começa com a primeira corrida.")
            Action("Configurar meu veículo") { vm.tab = 10 }
            TextButton({ vm.example() }) { Text("Testar demonstração") }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(if (!active) "SEM TURNO" else when (s.shiftMode) { "HOME" -> "VOLTANDO PARA CASA"; "RIDING" -> "CORRIDA EM ANDAMENTO"; "REPOSITIONING" -> "REPOSICIONANDO"; else -> "EM TURNO" }, if (active) Lime else Muted)
            Spacer(Modifier.weight(1f)); if (active) TextButton({ showState = true }) { Text("Alterar") }
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Eyebrow("LÍQUIDO HOJE")
            Text(money(stats.net), fontSize = 49.sp, lineHeight = 54.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp, color = if (stats.net < 0) Coral else Cream)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text("R$ ${fmt(stats.hourly)}", color = Lime, fontSize = 30.sp, fontWeight = FontWeight.Medium)
                Text(" / hora", color = Muted, modifier = Modifier.padding(bottom = 3.dp))
                Spacer(Modifier.weight(1f)); Text(elapsed(stats.onlineMinutes), color = Muted)
            }
            Note(if (stats.onlineMinutes > 0) "Sobre o tempo online registrado" else "Sobre os ciclos concluídos • inicie um turno para medir espera")
            val trend = rides.filter { it.completedAt >= from }.sortedBy { it.completedAt }.runningFold(0.0) { sum, r -> sum + r.netCents / 100.0 }
            Sparkline(trend, "Evolução do líquido dos ciclos concluídos hoje; custo fixo ocioso não distribuído na curva")
        }
        Surface(color = Panel, shape = RoundedCornerShape(24.dp), onClick = { showGoal = true }) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth()) { Text("Meta ${if (s.goalNet) "líquida" else "bruta"}", Modifier.weight(1f)); Text("${(progress.fraction * 100).toInt()}%", color = Lime, fontWeight = FontWeight.Bold) }
                LinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth().height(7.dp), color = Lime, trackColor = Ink)
                Text(if (progress.remaining == 0.0) "Meta atingida" else "Faltam R$ ${fmt(progress.remaining)}", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Note("R$ ${fmt(progress.current)} de R$ ${fmt(s.dailyGoal)}")
                val minutesRemaining = progress.minutesRemaining
                Note(when { progress.remaining == 0.0 -> "Você pode encerrar ou definir uma nova meta."; minutesRemaining == null -> "A previsão aparece com pelo menos 15 minutos e saldo positivo."; minutesRemaining > 1440 -> "No ritmo atual, a meta exigiria mais de 24 horas. Reavalie sua meta."; else -> "No ritmo registrado: aproximadamente ${clock(now + (minutesRemaining * 60000).toLong())}. Estimativa, sem garantia." })
                progress.requiredHourly?.let { Note("Para o horário escolhido: R$ ${fmt(it)}/h daqui em diante", Amber) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) { Metric(money(stats.gross), "Recebido", Modifier.weight(1f)); Metric(money(stats.costs), "Custo estimado", Modifier.weight(1f)) }
        HorizontalDivider(color = Muted.copy(alpha = .15f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) { Metric("${fmt(stats.productivity * 100, 0)}%", "Tempo produtivo", Modifier.weight(1f)); Metric("${fmt(stats.emptyKm, 1)} km", "Sem passageiro", Modifier.weight(1f)) }
        if (market != null) {
            Eyebrow(if (market.changePercent <= -20) "OFERTAS ENFRAQUECENDO" else if (market.changePercent >= 20) "MELHORA NAS OFERTAS" else "OFERTAS ESTÁVEIS", if (market.changePercent <= -20) Amber else Lime)
            Text("${if (market.changePercent >= 0) "+" else ""}${fmt(market.changePercent, 0)}% no score médio", fontSize = 22.sp)
            Note("${market.samples} ofertas observadas em duas janelas de 25 min. Não representa toda a demanda do mercado.")
        } else { Eyebrow("AINDA ESTAMOS APRENDENDO"); Note("O contexto das ofertas aparece com pelo menos 5 leituras em cada janela de 25 minutos.") }
        if (active && s.shiftMode == "WAITING") {
            val since = maxOf(sessions.firstOrNull { it.end == null }?.start ?: now, offers.firstOrNull { it.predictedHourly >= s.policy.minHourly && it.predictedPerKm >= s.policy.minPerKm }?.createdAt ?: 0)
            if (captureState.running && !captureState.paused && now - since > 15 * 60000) Note("Há ${((now - since) / 60000)} min sem oferta REGISTRADA acima dos seus mínimos. Confira a leitura antes de concluir que o mercado piorou.", Amber)
        }
        if (active) {
            Action("Analisar oferta") { vm.tab = 1 }
            OutlinedButton({ vm.tab = 8 }, Modifier.fillMaxWidth()) { Text("Abrir modo direção") }
            TextButton({ endConfirm = true }) { Text("Encerrar turno") }
        } else Action("Iniciar turno") { if (!s.onboarded) vm.tab = 10 else vm.task { vm.app.routing.endHome(); vm.invalidateRoute(); vm.repository.settingsStore.save(s.copy(shiftMode = "WAITING")); vm.repository.startSession(); vm.message("Turno iniciado") } }
        TextButton({ vm.tab = 11 }) { Text(if (captureState.running) "Leitura ativa • gerenciar" else "Ativar leitura de ofertas") }
        MenuRow("Combustível e consumo", "${s.fuelName} R$ ${fmt(s.cost.fuelPrice)}/L • ${fmt(s.cost.cityKml, 1)} km/L") { vm.tab = 7 }
    }
    if (showGoal) GoalSheet(vm, s) { showGoal = false }
    if (showState) ModalBottomSheet(onDismissRequest = { showState = false }, containerColor = Panel) {
        Column(Modifier.padding(24.dp)) { Text("Como está seu turno?", style = MaterialTheme.typography.headlineSmall); Note("Estado informado por você; não inferimos movimento pelo telefone.")
            listOf("WAITING" to "Aguardando oferta", "RIDING" to "Corrida em andamento", "REPOSITIONING" to "Reposicionando", "HOME" to "Voltando para casa").forEach { (mode, label) -> MenuRow(label, if (mode == "HOME") "Confirme sua casa como região de retorno no mapa" else "Ajusta o contexto da Home") { vm.task { if (mode == "HOME") {
                        showState = false; vm.tab = 3; vm.message("Confirme sua casa e ative o retorno no mapa")
                    } else {
                        vm.repository.settingsStore.save(s.copy(shiftMode = mode)); showState = false; vm.message("Estado do turno atualizado")
                    } } } }
        }
    }
    if (endConfirm) AlertDialog(onDismissRequest = { endConfirm = false }, title = { Text("Encerrar o turno?") }, text = { Text("O tempo online vai parar. Corridas ainda não concluídas continuam no histórico para você conferir.") }, confirmButton = { TextButton({ vm.task { vm.repository.endSession(); stop(); endConfirm = false; vm.message("Turno encerrado. Confira seu resultado nas análises."); vm.tab = 5 } }) { Text("Encerrar") } }, dismissButton = { TextButton({ endConfirm = false }) { Text("Continuar trabalhando") } })
}
@Composable fun GoalSheet(vm: AppViewModel, settings: Settings, close: () -> Unit) {
    var target by rememberSaveable { mutableStateOf(settings.dailyGoal.toString()) }; var net by rememberSaveable { mutableStateOf(settings.goalNet) }; var time by rememberSaveable { mutableStateOf(if (settings.goalDeadline > System.currentTimeMillis()) clock(settings.goalDeadline) else "") }
    ModalBottomSheet(onDismissRequest = close, containerColor = Panel) {
        Column(Modifier.padding(24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Seu destino de hoje", style = MaterialTheme.typography.headlineSmall)
            Field("Meta • R$", target, { target = it }); Toggle("Usar lucro líquido", net, { net = it }); Field("Horário desejado • HH:mm (opcional)", time, { time = it }, false)
            Note("Se o horário já passou, usaremos o próximo dia. Previsões dependem do ritmo observado.")
            Action("Salvar meta") { vm.task {
                val goal = decimal(target); require(goal > 0)
                val deadline = if (time.isBlank()) 0 else { val local = LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm")); val now = ZonedDateTime.now(); var at = now.toLocalDate().atTime(local).atZone(now.zone); if (!at.isAfter(now)) at = at.plusDays(1); at.toInstant().toEpochMilli() }
                vm.repository.settingsStore.save(vm.repository.settings.value.copy(dailyGoal = goal, goalNet = net, goalDeadline = deadline)); vm.message("Meta atualizada"); close()
            } }
        }
    }
}
