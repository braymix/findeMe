package com.uwbcompass.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier

/** Simple clickable wrapper kept in one place so screens read cleanly. */
fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
