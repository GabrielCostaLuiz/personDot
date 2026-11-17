package com.persondot.ui

import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.persondot.ui.dot.DotDesign
import com.persondot.ui.dot.dotById

/** Preferências visuais do aparelho (tema, Dot e dicas). Ficam em SharedPreferences, não no banco de dados financeiro. */
@Stable
class UiPrefs(private val sp: SharedPreferences) {
    var dark by mutableStateOf(sp.getBoolean("dark", true)); private set
    var dotId by mutableStateOf(sp.getString("dot_id", "original") ?: "original"); private set
    var dotName by mutableStateOf(sp.getString("dot_name", "Dot") ?: "Dot"); private set
    /** Cor própria do Dot (ARGB). null = a cor padrão do desenho escolhido. */
    var dotColor by mutableStateOf<Long?>(if (sp.contains("dot_color")) sp.getLong("dot_color", 0L) else null); private set
    var tips by mutableStateOf(sp.getBoolean("dot_tips", true)); private set

    val design: DotDesign
        get() = dotById(dotId).let { d -> dotColor?.let { c -> d.copy(color = Color(c.toInt())) } ?: d }

    fun updateDark(v: Boolean) { dark = v; sp.edit().putBoolean("dark", v).apply() }
    fun updateDot(id: String) { dotId = id; sp.edit().putString("dot_id", id).apply() }
    fun updateDotName(n: String) { val v = n.trim().ifEmpty { "Dot" }.take(16); dotName = v; sp.edit().putString("dot_name", v).apply() }
    fun updateDotColor(c: Color?) {
        dotColor = c?.toArgb()?.toLong()
        sp.edit().apply { if (c == null) remove("dot_color") else putLong("dot_color", c.toArgb().toLong()) }.apply()
    }
    fun updateTips(v: Boolean) { tips = v; sp.edit().putBoolean("dot_tips", v).apply() }
}
