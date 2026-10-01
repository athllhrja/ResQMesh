package com.resqmesh.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resqmesh.R
import com.resqmesh.domain.model.Message
import com.resqmesh.domain.model.MessageStatus
import java.util.Locale

@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier,
) {
    val outgoing = message.isOutgoing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = when {
                message.isSos -> MaterialTheme.colorScheme.errorContainer
                outgoing -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.fillMaxWidth(0.82f),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (message.isSos) {
                        "${stringResource(R.string.home_sos)}  ${message.peerId}"
                    } else {
                        message.peerId.toString()
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = clockLabel(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = statusLabel(message),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun statusLabel(message: Message): String {
    val status = when (message.status) {
        MessageStatus.DELIVERED, MessageStatus.ACKED -> stringResource(R.string.status_delivered)
        MessageStatus.PENDING_FORWARD -> stringResource(R.string.status_pending)
        MessageStatus.IN_TRANSIT -> stringResource(R.string.status_transit)
        MessageStatus.AWAITING_FRAGMENTS -> stringResource(R.string.status_awaiting)
        MessageStatus.EXPIRED -> stringResource(R.string.status_expired)
        MessageStatus.FAILED -> stringResource(R.string.status_failed)
    }
    return if (message.hopCount > 0) {
        stringResource(R.string.status_hops, message.hopCount) + " · $status"
    } else {
        status
    }
}

fun clockLabel(epochMillis: Long): String {
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
    return String.format(
        Locale.US,
        "%02d:%02d",
        calendar.get(java.util.Calendar.HOUR_OF_DAY),
        calendar.get(java.util.Calendar.MINUTE),
    )
}
