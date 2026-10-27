package dev.akkraizen.berill.core.secure

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference

internal object MacKeychain {
  @Suppress("FunctionName")
  private interface Security : Library {
    fun SecKeychainAddGenericPassword(
      keychain: Pointer?,
      serviceNameLength: Int,
      serviceName: ByteArray,
      accountNameLength: Int,
      accountName: ByteArray,
      passwordLength: Int,
      passwordData: ByteArray,
      itemRef: PointerByReference?
    ): Int

    fun SecKeychainFindGenericPassword(
      keychainOrArray: Pointer?,
      serviceNameLength: Int,
      serviceName: ByteArray,
      accountNameLength: Int,
      accountName: ByteArray,
      passwordLength: IntByReference?,
      passwordData: PointerByReference?,
      itemRef: PointerByReference?
    ): Int

    fun SecKeychainItemModifyAttributesAndData(itemRef: Pointer, attrList: Pointer?, length: Int, data: ByteArray): Int

    fun SecKeychainItemDelete(itemRef: Pointer): Int

    fun SecKeychainItemFreeContent(attrList: Pointer?, data: Pointer?): Int
  }

  @Suppress("FunctionName")
  private interface CoreFoundation : Library {
    fun CFRelease(ref: Pointer)
  }

  private const val ERR_ITEM_NOT_FOUND = -25300
  private val service = "Berill".encodeToByteArray()
  private val security by lazy { Native.load("Security", Security::class.java) }
  private val coreFoundation by lazy { Native.load("CoreFoundation", CoreFoundation::class.java) }

  fun read(name: String): String? {
    val account = name.encodeToByteArray()
    val length = IntByReference()
    val data = PointerByReference()
    val status = security.SecKeychainFindGenericPassword(
      null, service.size, service, account.size, account, length, data, null
    )
    if (status == ERR_ITEM_NOT_FOUND) return null
    check(status == 0) { "SecKeychainFindGenericPassword: $status" }
    val pointer = data.value ?: return null
    try {
      return pointer.getByteArray(0, length.value).decodeToString()
    } finally {
      security.SecKeychainItemFreeContent(null, pointer)
    }
  }

  fun write(name: String, value: String?) {
    val account = name.encodeToByteArray()
    val item = PointerByReference()
    val found = security.SecKeychainFindGenericPassword(
      null, service.size, service, account.size, account, null, null, item
    )
    check(found == 0 || found == ERR_ITEM_NOT_FOUND) { "SecKeychainFindGenericPassword: $found" }
    val existing = item.value.takeIf { found == 0 }
    try {
      when {
        value.isNullOrEmpty() -> existing?.let { check(security.SecKeychainItemDelete(it) == 0) { "SecKeychainItemDelete failed" } }
        existing != null -> {
          val bytes = value.encodeToByteArray()
          val status = security.SecKeychainItemModifyAttributesAndData(existing, null, bytes.size, bytes)
          check(status == 0) { "SecKeychainItemModifyAttributesAndData: $status" }
        }
        else -> {
          val bytes = value.encodeToByteArray()
          val status = security.SecKeychainAddGenericPassword(
            null, service.size, service, account.size, account, bytes.size, bytes, null
          )
          check(status == 0) { "SecKeychainAddGenericPassword: $status" }
        }
      }
    } finally {
      existing?.let(coreFoundation::CFRelease)
    }
  }
}
