package dev.akkraizen.berill.sdk.protocol

object PluginClassRules {
  private val allowedExact = setOf(
    "java.lang.Object",
    "java.lang.String",
    "java.lang.StringBuilder",
    "java.lang.StringBuffer",
    "java.lang.CharSequence",
    "java.lang.Comparable",
    "java.lang.Iterable",
    "java.lang.Number",
    "java.lang.Integer",
    "java.lang.Long",
    "java.lang.Short",
    "java.lang.Byte",
    "java.lang.Double",
    "java.lang.Float",
    "java.lang.Boolean",
    "java.lang.Character",
    "java.lang.Math",
    "java.lang.StrictMath",
    "java.lang.Enum",
    "java.lang.Void",
    "java.lang.Record",
    "java.lang.Appendable",
    "java.lang.Cloneable",
    "java.lang.AutoCloseable",
    "java.lang.Runnable",
    "java.lang.Class",
    "java.lang.Deprecated",
    "java.lang.Override",
    "java.lang.SuppressWarnings",
    "java.lang.FunctionalInterface",
    "java.lang.SafeVarargs",
    "java.lang.Throwable",
    "java.lang.Exception",
    "java.lang.RuntimeException",
    "java.lang.Error",
    "java.lang.AssertionError",
    "java.lang.IllegalArgumentException",
    "java.lang.IllegalStateException",
    "java.lang.IndexOutOfBoundsException",
    "java.lang.ArrayIndexOutOfBoundsException",
    "java.lang.StringIndexOutOfBoundsException",
    "java.lang.NullPointerException",
    "java.lang.UnsupportedOperationException",
    "java.lang.ArithmeticException",
    "java.lang.ClassCastException",
    "java.lang.NumberFormatException",
    "java.lang.CloneNotSupportedException",
    "java.lang.InterruptedException",
    "java.lang.ArrayStoreException",
    "java.lang.NegativeArraySizeException",
    "java.lang.StackOverflowError",
    "java.lang.OutOfMemoryError",
    "java.lang.NoSuchElementException",
    "java.io.Serializable",
    "kotlin.io.ConsoleKt",
    "kotlin.io.CloseableKt"
  )

  private val allowedOverrides = listOf("kotlin.io.encoding.")

  private val deniedPrefixes = listOf(
    "kotlin.io.",
    "kotlin.concurrent.",
    "kotlin.system.",
    "kotlin.reflect.full.",
    "kotlin.reflect.jvm.",
    "dev.akkraizen.berill.sdk.protocol.",
    "java.util.concurrent.",
    "java.util.zip.",
    "java.util.jar.",
    "java.util.prefs.",
    "java.util.logging.",
    "java.util.spi.",
    "java.util.ServiceLoader",
    "java.util.Formatter",
    "java.util.Timer",
    "java.util.ResourceBundle",
    "java.util.PropertyResourceBundle",
    "java.util.Scanner",
    "java.util.Properties"
  )

  private val allowedPrefixes = listOf(
    "kotlin.",
    "kotlinx.serialization.",
    "dev.akkraizen.berill.sdk.",
    "java.util.",
    "java.text.",
    "java.math.",
    "java.time.",
    "java.nio.charset.",
    "java.lang.invoke.",
    "java.lang.annotation."
  )

  fun isAllowed(className: String): Boolean =
    when {
      className in allowedExact -> true
      allowedOverrides.any { className.startsWith(it) } -> true
      deniedPrefixes.any { className.startsWith(it) } -> false
      else -> allowedPrefixes.any { className.startsWith(it) }
    }
}
