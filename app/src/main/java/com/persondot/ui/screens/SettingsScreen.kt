package com.persondot.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.persondot.BuildConfig
import com.persondot.domain.*
import com.persondot.ui.components.*
import com.persondot.ui.theme.*
import com.persondot.ui.viewmodel.FinanceViewModel
import com.persondot.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object NoRelease : UpdateState
    data class Available(val release: Updater.Release) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class Error(val message: String) : UpdateState
}

@Composable
fun SettingsScreen(data: FinanceData, vm: FinanceViewModel, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<String?>(null) }
    var update by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(vm.exportJson().toByteArray()) } } }
                .onSuccess { toast("Backup salvo.") }.onFailure { toast("Não consegui salvar o backup.") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } } }.getOrNull()
            if (text == null) toast("Não consegui ler o arquivo.")
            else vm.importJson(text) { r -> toast(if (r.isSuccess) "Dados importados." else "Arquivo inválido. Use um backup do PersonDot.") }
        }
    }

    fun checkUpdate() = scope.launch {
        update = UpdateState.Checking
        update = try {
            val r = Updater.latest()
            when { r == null -> UpdateState.NoRelease; Updater.isNewer(r) -> UpdateState.Available(r); else -> UpdateState.UpToDate }
        } catch (e: Exception) { UpdateState.Error("Sem conexão com o GitHub. Tente de novo.") }
    }
    fun downloadAndInstall(r: Updater.Release) = scope.launch {
        update = UpdateState.Downloading(0f)
        try {
            val apk = Updater.download(ctx, r) { update = UpdateState.Downloading(it) }
            update = UpdateState.Available(r)
            if (Updater.install(ctx, apk) == Updater.InstallResult.NEEDS_PERMISSION) toast("Permita instalar apps do PersonDot e toque em baixar de novo.")
        } catch (e: Exception) { update = UpdateState.Error("Falha no download. Tente de novo.") }
    }

    val s = data.settings
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)) {
        item {
            BentoCard {
                SectionHeader("Parâmetros do plano", "editar") { dialog = "settings" }
                StatRow("Renda mensal", brl(s.income), sub = "cai no dia ${s.incomeDay}")
                StatRow("Multiplicador", "× ${s.multiplier.toInt()}", sub = "200 = 6% real ao ano · 250 = mais conservador")
                StatRow("Rendimento real esperado", "${"%.1f".format(s.realRate * 100)}% ao ano")
                StatRow("Reserva de emergência", "${s.reserveMonths} meses de essenciais")
                StatRow("Aporte mensal padrão", brl(s.aporteLongo), sub = "IPCA+ começa em ${mLabel(s.ipcaStart.ifBlank { null })}")
            }
        }
        item {
            BentoCard {
                SectionHeader("Backup dos dados")
                Text("Seus dados ficam só neste aparelho. Exporte de vez em quando; o mesmo arquivo leva tudo para outro celular.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Exportar", { exportLauncher.launch("persondot-${LocalDate.now()}.json") })
                    SoftButton("Importar", { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
                }
            }
        }
        item {
            BentoCard {
                SectionHeader("Atualização do app")
                Text("Versão instalada: ${BuildConfig.VERSION_NAME}", color = AppTextWhite, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                when (val u = update) {
                    UpdateState.Idle -> Text("Toque para ver se há uma versão nova.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    UpdateState.Checking -> Row { CircularProgressIndicator(Modifier.size(18.dp), color = CyberGreen, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Procurando…", color = SecondaryText) }
                    UpdateState.UpToDate -> Text("Você já está na versão mais recente.", color = CyberGreen)
                    UpdateState.NoRelease -> Text("Nenhuma versão publicada ainda.", color = SecondaryText)
                    is UpdateState.Available -> {
                        Text("Nova versão: ${u.release.tag}", color = CyberGreen, fontWeight = FontWeight.Bold)
                        if (u.release.notes.isNotBlank()) Text(u.release.notes.take(300), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    is UpdateState.Downloading -> { Text("Baixando… ${(u.progress * 100).toInt()}%", color = SecondaryText); Spacer(Modifier.height(6.dp)); MetricProgress(u.progress * 100.0) }
                    is UpdateState.Error -> Text(u.message, color = ImpulseAmber)
                }
                Spacer(Modifier.height(10.dp))
                val avail = update as? UpdateState.Available
                if (avail != null) PrimaryButton("Baixar e instalar ${avail.release.tag}", { downloadAndInstall(avail.release) })
                else SoftButton("Procurar atualização", { checkUpdate() })
            }
        }
        item {
            BentoCard {
                SectionHeader("Zona de perigo")
                Text("Apaga todas as contas, dívidas, custos e lançamentos deste aparelho. Exporte um backup antes.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                SoftButton("Apagar tudo", { dialog = "reset" }, danger = true)
            }
        }
    }

    when (dialog) {
        "settings" -> SettingsDialog(s, { vm.saveSettings(it) }) { dialog = null }
        "reset" -> AlertDialog(
            onDismissRequest = { dialog = null }, containerColor = DarkSurface,
            title = { Text("Apagar tudo?", color = AppTextWhite) },
            text = { Text("Isso não pode ser desfeito. Só continue se você já exportou um backup.", color = SecondaryText) },
            confirmButton = { TextButton(onClick = { vm.resetAll(); dialog = null }) { Text("Apagar", color = ImpulseAmber) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancelar", color = SecondaryText) } }
        )
    }
}
