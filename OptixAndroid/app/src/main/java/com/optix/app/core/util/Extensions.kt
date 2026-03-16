package com.optix.app.core.util

import java.text.DecimalFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Number formatting extensions
 */
fun Double.formatPrice(): String {
    return "₹${DecimalFormat("#,##,##0.00").format(this)}"
}

fun Double.formatPriceShort(): String {
    return "₹${DecimalFormat("#,##0.##").format(this)}"
}

fun Double.formatPercent(): String {
    val sign = if (this >= 0) "+" else ""
    return "$sign${String.format("%.2f", this)}%"
}

fun Double.formatChange(): String {
    val sign = if (this >= 0) "+" else ""
    return "$sign${String.format("%.2f", this)}"
}

fun Long.formatNumber(): String {
    return when {
        this >= 10000000 -> String.format("%.2fCr", this / 10000000.0)
        this >= 100000 -> String.format("%.2fL", this / 100000.0)
        this >= 1000 -> String.format("%.2fK", this / 1000.0)
        else -> this.toString()
    }
}

fun Long.formatOI(): String {
    return formatNumber()
}

/**
 * Date formatting extensions
 */
fun LocalDate.formatExpiry(): String {
    return this.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy"))
}

fun LocalDate.formatShort(): String {
    return this.format(DateTimeFormatter.ofPattern("dd MMM"))
}

fun LocalDateTime.formatTimestamp(): String {
    return this.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
}

fun LocalDateTime.formatTime(): String {
    return this.format(DateTimeFormatter.ofPattern("HH:mm"))
}

fun LocalDateTime.timeAgo(): String {
    val now = LocalDateTime.now()
    val minutes = ChronoUnit.MINUTES.between(this, now)
    val hours = ChronoUnit.HOURS.between(this, now)
    val days = ChronoUnit.DAYS.between(this, now)

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> formatShort()
    }
}

private fun LocalDateTime.formatShort(): String {
    return this.format(DateTimeFormatter.ofPattern("dd MMM"))
}

/**
 * String extensions
 */
fun String.capitalizeWords(): String {
    return split(" ").joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.uppercase() }
    }
}
