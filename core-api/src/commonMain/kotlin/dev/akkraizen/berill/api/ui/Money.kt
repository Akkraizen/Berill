package dev.akkraizen.berill.api.ui

import kotlin.math.abs
import kotlin.math.roundToLong

fun formatRub(amount: Double, signed: Boolean = false): String {
  val rounded = amount.roundToLong()
  val digits = abs(rounded).toString().reversed().chunked(3).joinToString(" ").reversed()
  val sign = when {
    rounded < 0 -> "−"
    signed && rounded > 0 -> "+"
    else -> ""
  }
  return "$sign$digits ₽"
}

fun parseAmount(text: String): Double? = text.replace(" ", "").replace(" ", "").replace(',', '.').toDoubleOrNull()
