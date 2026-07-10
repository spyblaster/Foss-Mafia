package org.fossify.messages.mafia

import com.google.gson.Gson

/**
 * Shared Gson instance for the mafia feature, instead of every Activity
 * creating its own.
 */
val mafiaGson: Gson by lazy { Gson() }

private val PERSIAN_DIGITS = arrayOf("۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹")

/**
 * Converts a western-arabic int to Persian digits, e.g. 12 -> "۱۲".
 */
fun Int.toPersianDigits(): String =
    toString().map { if (it.isDigit()) PERSIAN_DIGITS[it - '0'] else it.toString() }.joinToString("")
