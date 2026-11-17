package com.persondot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.persondot.data.Backup
import com.persondot.data.local.AppDatabase
import com.persondot.data.repository.FinanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/** Debug apenas. adb shell am broadcast -n com.persondot.app/com.persondot.DebugImportReceiver --es file seed.json (arquivo em files/). */
class DebugImportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("file") ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = FinanceRepository(AppDatabase.get(context).financeDao())
                repo.replaceAll(Backup.fromJson(File(context.filesDir, name).readText()))
                Log.i("PersonDot", "debug import ok")
            } catch (e: Exception) { Log.e("PersonDot", "debug import falhou", e) }
            finally { pending.finish() }
        }
    }
}
