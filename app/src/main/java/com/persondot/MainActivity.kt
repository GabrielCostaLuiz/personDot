package com.persondot

import android.content.Context
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = UiPrefs(getSharedPreferences("persondot", Context.MODE_PRIVATE))
        setContent {
            PersonDotTheme(darkTheme = prefs.dark) {
                val vm: FinanceViewModel = viewModel()
                MainScreen(viewModel = vm, prefs = remember { prefs })
            }
        }
    }
}
