package br.com.deh.copiloto

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import br.com.deh.copiloto.capture.*
import br.com.deh.copiloto.ui.AppRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK && r.data != null) ContextCompat.startForegroundService(this, Intent(this, CaptureService::class.java).putExtra("consent", r.data))
        else vm.message("Captura não autorizada")
    }
    private val overlayPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { if (Settings.canDrawOverlays(this)) capture() else vm.message("Ative a sobreposição para ver a análise sobre a oferta") }
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { requestProjection() }
    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importImage) }
    private val csv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if (uri != null) vm.task { val text = vm.repository.csv(); withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("Não foi possível exportar") }; vm.message("Histórico exportado") } }
    private fun requestProjection() { consent.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent()) }
    private fun capture() {
        if (!vm.repository.settings.value.onboarded) { vm.tab = 4; vm.message("Confirme seus custos e mínimos antes de iniciar"); return }
        if (!Settings.canDrawOverlays(this)) { overlayPermission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return }
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS) else requestProjection()
    }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { AppRoot(vm, ::capture, { stopService(Intent(this, CaptureService::class.java)); Unit }, { imagePicker.launch(arrayOf("image/*")) }, { csv.launch("Dehpilot-historico.csv") }) } }
    override fun onStart() { super.onStart(); CaptureBus.appVisible = true }
    override fun onStop() { CaptureBus.appVisible = false; super.onStop() }
}
