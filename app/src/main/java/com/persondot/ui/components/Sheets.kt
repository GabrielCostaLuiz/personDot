package com.persondot.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.persondot.ui.theme.*

/**
 * Todo modal do app é uma folha que sobe da base: o TÍTULO fica fixo no topo, só o MEIO rola,
 * e os BOTÕES DE AÇÃO ficam fixos embaixo (acima do teclado, se ele aparecer).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    title: String, onDismiss: () -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.88f
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState, containerColor = DarkSurface, contentColor = AppTextWhite,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).imePadding()) {
            Text(title, color = AppTextWhite, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp))
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) { content() }
            if (actions != null) {
                HorizontalDivider(color = BorderColor, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
            } else Spacer(Modifier.height(16.dp))
        }
    }
}

/** Pergunta de confirmação em folha (excluir, apagar tudo...). */
@Composable
fun ConfirmSheet(title: String, text: String, confirmText: String, danger: Boolean = true, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AppSheet(title, onDismiss, actions = {
        SoftButton("Cancelar", onDismiss, Modifier.weight(1f))
        Button(
            onClick = { onConfirm(); onDismiss() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (danger) ImpulseAmber else CyberGreen, contentColor = if (danger) Color.White else Color.Black)
        ) { Text(confirmText, fontWeight = FontWeight.Bold) }
    }) { Text(text, color = SecondaryText, style = MaterialTheme.typography.bodyMedium) }
}
