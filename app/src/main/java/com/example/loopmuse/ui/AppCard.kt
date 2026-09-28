package com.example.loopmuse.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object AppCardStyle {
    val shape = RoundedCornerShape(16.dp)
    val elevation = 2.dp
    val horizontalPadding = 12.dp
    val verticalPadding = 8.dp
    val compactHorizontalPadding = 8.dp
    val compactVerticalPadding = 6.dp

    @Composable
    fun border() = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

@Composable
internal fun AppCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: Dp = AppCardStyle.elevation,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = AppCardStyle.shape,
        colors = colors,
        border = AppCardStyle.border(),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        content = content
    )
}
