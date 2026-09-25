package com.chatooz.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chatooz.app.ui.theme.TickBlue
import com.chatooz.app.ui.theme.TickGray

@Composable
fun ReadReceipt(
    status: String,
    size: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    when (status) {
        "SENDING" -> Icon(Icons.Default.Schedule, "Sending", tint = TickGray, modifier = modifier.size(size))
        "SENT"    -> Icon(Icons.Default.Check,    "Sent",    tint = TickGray, modifier = modifier.size(size))
        "DELIVERED" -> Icon(Icons.Default.DoneAll, "Delivered", tint = TickGray, modifier = modifier.size(size))
        "READ"    -> Icon(Icons.Default.DoneAll,  "Read",    tint = TickBlue, modifier = modifier.size(size))
    }
}
