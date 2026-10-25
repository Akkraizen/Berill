package dev.akkraizen.berill.core.external

import dev.akkraizen.berill.sdk.protocol.PluginClassRules
import dev.akkraizen.berill.sdk.protocol.RESERVED_PACKAGES
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.zip.ZipInputStream

object BytecodeVerifier {
  private val deniedClassMethods = setOf(
    "forName",
    "getClassLoader",
    "getDeclaredMethod",
    "getDeclaredMethods",
    "getDeclaredField",
    "getDeclaredFields",
    "getDeclaredConstructor",
    "getDeclaredConstructors",
    "getMethod",
    "getMethods",
    "getField",
    "getFields",
    "getConstructor",
    "getConstructors",
    "newInstance",
    "getResource",
    "getResourceAsStream",
    "getProtectionDomain",
    "getModule",
    "getEnclosingMethod",
    "getEnclosingConstructor",
    "getRecordComponents",
    "getDeclaredClasses",
    "getClasses",
    "getPackage",
    "getNestMembers",
    "getPermittedSubclasses"
  )

  private val allowedInvoke = setOf(
    "java/lang/invoke/LambdaMetafactory.metafactory",
    "java/lang/invoke/LambdaMetafactory.altMetafactory",
    "java/lang/invoke/StringConcatFactory.makeConcatWithConstants",
    "java/lang/invoke/StringConcatFactory.makeConcat"
  )

  fun verifyJar(jar: ByteArray): List<String> {
    val classes = LinkedHashMap<String, ByteArray>()
    val problems = mutableListOf<String>()
    ZipInputStream(ByteArrayInputStream(jar)).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        if (entry.isDirectory) continue
        val name = entry.name
        if (name.endsWith(".class")) {
          if (name == "module-info.class" || name.startsWith("META-INF/")) continue
          val bytes = zip.readBytes()
          if (bytes.size > 4 * 1024 * 1024) problems += "$name: класс слишком большой"
          classes[name.removeSuffix(".class")] = bytes
        }
      }
    }
    if (classes.isEmpty()) problems += "в plugin.jar нет классов"
    val own = classes.keys
    for ((internalName, bytes) in classes) {
      val dotted = internalName.replace('/', '.')
      if (RESERVED_PACKAGES.any { dotted.startsWith(it) })
        problems += "$dotted: класс в зарезервированном пакете"
      problems +=
        runCatching { verifyClass(internalName, bytes, own) }
          .getOrElse { listOf("$dotted: не читается (${it.message})") }
      if (problems.size > 50) break
    }
    return problems.distinct()
  }

  private fun verifyClass(className: String, bytes: ByteArray, own: Set<String>): List<String> {
    val input = DataInputStream(ByteArrayInputStream(bytes))
    require(input.readInt() == 0xCAFEBABE.toInt()) { "не class-файл" }
    input.readUnsignedShort()
    input.readUnsignedShort()
    val count = input.readUnsignedShort()
    val tags = IntArray(count)
    val utf8 = arrayOfNulls<String>(count)
    val refA = IntArray(count)
    val refB = IntArray(count)
    var i = 1
    while (i < count) {
      val tag = input.readUnsignedByte()
      tags[i] = tag
      when (tag) {
        1 -> utf8[i] = input.readUTF()
        3,
        4 -> input.readInt()

        5,
        6 -> {
          input.readLong()
          i++
        }

        7,
        8,
        16,
        19,
        20 -> refA[i] = input.readUnsignedShort()

        9,
        10,
        11,
        12,
        17,
        18 -> {
          refA[i] = input.readUnsignedShort()
          refB[i] = input.readUnsignedShort()
        }

        15 -> {
          input.readUnsignedByte()
          refA[i] = input.readUnsignedShort()
        }

        else -> error("неизвестный тег $tag")
      }
      i++
    }
    val problems = mutableListOf<String>()

    fun classAt(index: Int): String? = if (tags.getOrNull(index) == 7) utf8.getOrNull(refA[index]) else null

    fun elementType(name: String): String? {
      var n = name
      while (n.startsWith("[")) n = n.substring(1)
      if (n.length == 1) return null
      if (n.startsWith("L") && n.endsWith(";")) n = n.substring(1, n.length - 1)
      return n
    }

    val self = className.replace('/', '.')
    for (k in 1 until count) {
      when (tags[k]) {
        7 -> {
          val type = elementType(utf8[refA[k]] ?: continue) ?: continue
          if (type !in own && !PluginClassRules.isAllowed(type.replace('/', '.'))) {
            problems += "$self: использует запрещённый класс ${type.replace('/', '.')}"
          }
        }

        9,
        10,
        11 -> {
          val owner = classAt(refA[k])?.let(::elementType) ?: continue
          val nat = refB[k]
          val member = utf8.getOrNull(refA.getOrNull(nat) ?: 0) ?: continue
          if (owner == "java/lang/Class" && member in deniedClassMethods) {
            problems += "$self: рефлексия Class.$member запрещена"
          }
          if (owner.startsWith("java/lang/invoke/") && "$owner.$member" !in allowedInvoke) {
            problems += "$self: вызов ${owner.replace('/', '.')}.$member запрещён"
          }
        }
      }
    }
    return problems
  }
}
