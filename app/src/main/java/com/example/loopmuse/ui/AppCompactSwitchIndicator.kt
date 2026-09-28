package com.example.loopmuse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
internal fun AppCompactSwitchIndicator(checked: Boolean) {
    Box(Modifier.width(38.dp).height(30.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.width(33.dp).height(19.dp).clip(RoundedCornerShape(10.dp))
            .background(if (checked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline))
        Box(Modifier.offset(x = if (checked) 7.dp else (-7).dp)
            .size(15.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.onPrimary))
    }
}
