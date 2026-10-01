package com.resqmesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.resqmesh.domain.model.SignalStrength

@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color, CircleShape),
    )
}

@Composable
fun StatusDot(
    signal: SignalStrength,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    StatusDot(
        color = signal.dotColor(),
        modifier = modifier,
        size = size,
    )
}

@Composable
fun SignalStrength.dotColor(): Color = when (this) {
    SignalStrength.STRONG -> MaterialTheme.colorScheme.primary
    SignalStrength.MEDIUM -> Color(0xFF9A8C24)
    SignalStrength.WEAK -> MaterialTheme.colorScheme.error
    SignalStrength.UNKNOWN -> MaterialTheme.colorScheme.outline
}
