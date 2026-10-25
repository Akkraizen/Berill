package dev.akkraizen.berill.core.secure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidVault(context: Context) : SecretVault {
  private val prefs = context.applicationContext.getSharedPreferences("berill_secrets", Context.MODE_PRIVATE)

  private fun key(): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey(ALIAS, null) as? SecretKey)?.let {
      return it
    }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    generator.init(
      KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
    )
    return generator.generateKey()
  }

  override fun read(name: String): String? {
    val stored = prefs.getString(name, null) ?: return null
    return runCatching {
      val bytes = Base64.decode(stored, Base64.NO_WRAP)
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
      cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES).decodeToString()
    }
      .getOrNull()
  }

  override fun write(name: String, value: String?) {
    if (value.isNullOrEmpty()) {
      prefs.edit().remove(name).apply()
      return
    }
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, key())
    val iv = cipher.iv
    val encrypted = iv + cipher.doFinal(value.encodeToByteArray())
    prefs.edit().putString(name, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
  }

  private companion object {
    const val ALIAS = "berill_secrets"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val IV_BYTES = 12
  }
}
