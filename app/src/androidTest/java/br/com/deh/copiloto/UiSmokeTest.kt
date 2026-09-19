package br.com.deh.copiloto

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import br.com.deh.copiloto.data.Settings
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Run only on a disposable emulator: each test starts with empty user data. */
class UiSmokeTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Before fun emptyPersonalData() {
        runBlocking { val app = ui.activity.application as CopilotoApp; app.repository.clear(); app.repository.settingsStore.save(Settings()) }
        ui.waitForIdle()
    }
    private fun back() { ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }; ui.waitForIdle() }
    @Test fun demoIsIsolatedAndRoadRoutingIsReachable() {
        ui.onNodeWithText("Testar demonstração").performScrollTo().performClick()
        ui.onNodeWithText("DEMONSTRAÇÃO").assertExists()
        ui.onNodeWithText("Registrar oferta").assertDoesNotExist()
        ui.onNodeWithText("Mapa", useUnmergedTree = true).performClick()
        ui.onNodeWithText("Calcular retorno viário real").performScrollTo().performClick()
        ui.onNodeWithText("RETORNO VIÁRIO REAL").assertExists()
        ui.onNodeWithText("Destino da corrida").assertExists()
        back()
        ui.onNodeWithText("Histórico", useUnmergedTree = true).performClick()
        ui.onNodeWithText("Seu histórico começa com a próxima corrida analisada.").assertExists()
    }
    @Test fun internalBackReturnsToMoreAndThenHome() {
        ui.onNodeWithText("Mais").performClick()
        ui.onNodeWithText("Combustível e consumo").performClick()
        ui.onNodeWithText("Cada quilômetro\nconta.").assertExists()
        back(); ui.onNodeWithText("Feito para\no seu ritmo.").assertExists()
        back(); ui.onNodeWithText("Seu turno, com clareza").assertExists()
    }
    @Test fun manualFuelFallbackCanBeSaved() {
        ui.onNodeWithText("Mais").performClick(); ui.onNodeWithText("Combustível e consumo").performClick()
        ui.onNodeWithText("Etanol • R$/L").performScrollTo().performTextReplacement("4,29")
        ui.onNodeWithText("Consumo médio • km/L").performScrollTo().performTextReplacement("9,4")
        ui.onNodeWithText("Confirmar e salvar consumo").performScrollTo().performClick()
        ui.waitUntil(5000) { (ui.activity.application as CopilotoApp).repository.settings.value.cost.fuelPrice == 4.29 }
        ui.onNodeWithText("R$ 0,456/km").assertExists()
    }
    @Test fun analysisDraftSurvivesNavigationBack() {
        ui.onNodeWithText("Testar demonstração").performScrollTo().performClick()
        ui.onNodeWithText("Valor da corrida • R$").performScrollTo().performTextReplacement("52,00")
        ui.onNodeWithText("Diagnóstico OCR").performScrollTo().performClick()
        back(); ui.onNodeWithText("Valor da corrida • R$").performScrollTo()
        ui.onNodeWithText("52,00").assertExists()
    }
    @Test fun newUserHasNoInventedMarketOrRegionalHistory() {
        ui.onNodeWithText("Análises", useUnmergedTree = true).performClick()
        ui.onNodeWithText("Ainda estamos conhecendo seu trabalho.").performScrollTo().assertExists()
        ui.onNodeWithText("Registrar oferta").assertDoesNotExist()
    }
    @Test fun onboardingSystemBackReturnsToPreviousStep() {
        ui.onNodeWithText("Configurar meu veículo").performScrollTo().performClick()
        ui.onNodeWithText("Vamos começar").performScrollTo().performClick()
        ui.onNodeWithText("PASSO 2 DE 5").assertExists()
        back(); ui.onNodeWithText("PASSO 1 DE 5").assertExists()
    }
}
