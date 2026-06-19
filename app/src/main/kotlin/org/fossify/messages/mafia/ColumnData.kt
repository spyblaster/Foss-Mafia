package org.fossify.messages.mafia

data class ColumnData(
    val title: String,
    val values: MutableList<String>,
    var isActive: Boolean = false
)

