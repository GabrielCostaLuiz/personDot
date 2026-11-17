package com.persondot

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.persondot.ui.screens.MainScreen
import com.persondot.ui.theme.PersonDotTheme
import com.persondot.ui.viewmodel.FinanceViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("persondot", Context.MODE_PRIVATE)
        setContent {
            val dark = remember { mutableStateOf(prefs.getBoolean("dark", true)) }
            val dotId = remember { mutableStateOf(prefs.getString("dot_id", "original") ?: "original") }
            val dotName = remember { mutableStateOf(prefs.getString("dot_name", "Dot") ?: "Dot") }
            val tips = remember { mutableStateOf(prefs.getBoolean("dot_tips", true)) }
            PersonDotTheme(darkTheme = dark.value) {
                val vm: FinanceViewModel = viewModel()
                MainScreen(
                    viewModel = vm, isDarkTheme = dark.value,
                    onToggleTheme = { dark.value = !dark.value; prefs.edit().putBoolean("dark", dark.value).apply() },
                    dotId = dotId.value, dotName = dotName.value,
                    onDotChange = { dotId.value = it; prefs.edit().putString("dot_id", it).apply() },
                    onDotNameChange = { dotName.value = it; prefs.edit().putString("dot_name", it).apply() },
                    tipsEnabled = tips.value,
                    onTipsChange = { tips.value = it; prefs.edit().putBoolean("dot_tips", it).apply() }
                )
            }
        }
    }
}
