package com.persondot

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.persondot.widget.WidgetAction
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.persondot.ui.UiPrefs
import com.persondot.ui.screens.MainScreen
import com.persondot.ui.theme.PersonDotTheme
import com.persondot.ui.viewmodel.FinanceViewModel

class MainActivity : ComponentActivity() {
    private var pendingAction by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingAction = intent.getStringExtra(WidgetAction.EXTRA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingAction = intent?.getStringExtra(WidgetAction.EXTRA)
        val prefs = UiPrefs(getSharedPreferences("persondot", Context.MODE_PRIVATE))
        setContent {
            PersonDotTheme(darkTheme = prefs.dark) {
                val vm: FinanceViewModel = viewModel()
                MainScreen(viewModel = vm, prefs = remember { prefs }, launchAction = pendingAction, onLaunchHandled = { pendingAction = null })
            }
        }
    }
}
