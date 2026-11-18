package com.persondot.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.RemoteViews
import androidx.compose.ui.graphics.asAndroidBitmap
import com.persondot.MainActivity
import com.persondot.R
import com.persondot.data.local.AppDatabase
import com.persondot.data.repository.FinanceRepository
import com.persondot.domain.*
import com.persondot.ui.UiPrefs
import com.persondot.ui.dot.Mood
import com.persondot.ui.dot.drawDot
import com.persondot.ui.dot.dotById
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Ações que os widgets pedem ao app (entregues como extra da Intent e tratadas em [MainActivity]). */
object WidgetAction {
    const val EXTRA = "widget_action"
    const val EXPENSE = "expense"
    const val CHAT = "chat"
}

/** O que os widgets mostram, calculado dos mesmos números do app. */
private data class Snapshot(val free: String, val line: String, val next: String, val mood: Mood, val design: com.persondot.ui.dot.DotDesign)

private suspend fun snapshot(ctx: Context): Snapshot {
    val prefs = UiPrefs(ctx.getSharedPreferences("persondot", Context.MODE_PRIVATE))
    val design = prefs.design
    val data = FinanceRepository(AppDatabase.get(ctx).financeDao()).data.first()
    if (data.isEmpty) return Snapshot("—", "Abra o app para começar", "", Mood.SLEEPY, design)
    val c = data.core()
    val score = data.health().score
    val mood = if (score >= 75) Mood.HAPPY else if (score >= 50) Mood.NEUTRAL else Mood.WORRIED
    val now = monthKey(java.time.LocalDate.now())
    val plan = data.monthPlan(now)
    val line = "Saúde $score · a pagar no mês ${brl0(plan.aPagar)}"
    return Snapshot(brl0(c.free), line, data.norte().firstOrNull()?.title.orEmpty(), mood, design)
}

/** Desenha o Dot de verdade (o mesmo do app: degradê, brilho, olhos e boca) num bitmap, já que widgets não rodam Compose. */
private fun dotBitmap(px: Int, design: com.persondot.ui.dot.DotDesign, mood: Mood): Bitmap {
    val img = androidx.compose.ui.graphics.ImageBitmap(px, px)
    val canvas = androidx.compose.ui.graphics.Canvas(img)
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
        androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr, canvas, androidx.compose.ui.geometry.Size(px.toFloat(), px.toFloat())
    ) { drawDot(design, mood, 0.5f, 1f, 0f, 0f, 0f, 0f, false) }
    return img.asAndroidBitmap()
}

private fun openQuick(ctx: Context, code: Int): PendingIntent {
    val i = Intent(ctx, QuickExpenseActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK }
    return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

private fun open(ctx: Context, action: String?, code: Int): PendingIntent {
    val i = Intent(ctx, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        action?.let { putExtra(WidgetAction.EXTRA, it) }
    }
    return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

/** Atualiza todos os widgets do Dot (chamado quando os números mudam e a cada 30 minutos). */
fun updateDotWidgets(ctx: Context) {
    val app = ctx.applicationContext
    CoroutineScope(Dispatchers.IO).launch {
        val snap = snapshot(app)
        val mgr = AppWidgetManager.getInstance(app)
        val dot = dotBitmap(240, snap.design, snap.mood)
        for (id in mgr.getAppWidgetIds(ComponentName(app, DotInfoWidget::class.java))) {
            val v = RemoteViews(app.packageName, R.layout.widget_info)
            v.setImageViewBitmap(R.id.w_dot, dot)
            v.setTextViewText(R.id.w_value, snap.free)
            v.setTextViewText(R.id.w_line, snap.line)
            v.setTextViewText(R.id.w_next, snap.next)
            v.setOnClickPendingIntent(R.id.w_root, open(app, null, 1))
            v.setOnClickPendingIntent(R.id.w_add, openQuick(app, 2))
            v.setOnClickPendingIntent(R.id.w_chat, open(app, WidgetAction.CHAT, 3))
            mgr.updateAppWidget(id, v)
        }
        for (id in mgr.getAppWidgetIds(ComponentName(app, DotQuickWidget::class.java))) {
            val v = RemoteViews(app.packageName, R.layout.widget_quick)
            v.setImageViewBitmap(R.id.w_dot, dot)
            v.setTextViewText(R.id.w_value, snap.free)
            v.setOnClickPendingIntent(R.id.w_dot, open(app, WidgetAction.CHAT, 4))
            v.setOnClickPendingIntent(R.id.w_add, openQuick(app, 5))
            v.setOnClickPendingIntent(R.id.w_value, open(app, null, 6))
            mgr.updateAppWidget(id, v)
        }
    }
}

/** Widget grande: o Dot, o livre de verdade, a saúde, o próximo passo e atalhos. */
class DotInfoWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = updateDotWidgets(context)
}

/** Widget pequeno: o Dot, o livre de verdade e o botão de gasto rápido. */
class DotQuickWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = updateDotWidgets(context)
}
