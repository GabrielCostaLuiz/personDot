package com.persondot.widget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.persondot.data.model.categoryList
import com.persondot.ui.UiPrefs
import com.persondot.ui.components.ExpenseDialog
import com.persondot.ui.theme.PersonDotTheme
import com.persondot.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.delay

/**
 * O "+ Gasto" do widget: uma janelinha transparente sobre a tela inicial, só com o lançamento do gasto.
 * Salva direto (e desconta da conta escolhida) sem abrir o app.
 */
class QuickExpenseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = UiPrefs(getSharedPreferences("persondot", MODE_PRIVATE))
        setContent {
            PersonDotTheme(darkTheme = prefs.dark) {
                val vm: FinanceViewModel = viewModel()
                val data by vm.data.collectAsState()
                var closing by remember { mutableStateOf(false) }
                LaunchedEffect(closing) { if (closing) { delay(900); finish() } }   // dá tempo de gravar antes de fechar
                ExpenseDialog(
                    null, data.accounts, data.settings.categoryList,
                    onSave = { e, acc -> vm.saveExpense(e, acc); Toast.makeText(this@QuickExpenseActivity, "Gasto salvo", Toast.LENGTH_SHORT).show() },
                    onDelete = null
                ) { closing = true }
            }
        }
    }
}
