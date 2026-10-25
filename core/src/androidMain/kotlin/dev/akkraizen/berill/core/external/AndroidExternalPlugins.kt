package dev.akkraizen.berill.core.external

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Base64
import dev.akkraizen.berill.sdk.protocol.FromRunner
import dev.akkraizen.berill.sdk.protocol.PLUGIN_DEX
import dev.akkraizen.berill.sdk.protocol.PluginManifest
import dev.akkraizen.berill.sdk.protocol.ProtocolJson
import dev.akkraizen.berill.sdk.protocol.ToRunner
import java.io.File

private const val CHUNK_BYTES = 192 * 1024

class AndroidExternalPlugins(context: Context, dir: File) : PluginPackageStore(dir, PLUGIN_DEX) {
  private val app = context.applicationContext

  override fun codeProblems(code: ByteArray?): List<String> =
    if (code == null) listOf("нет $PLUGIN_DEX — плагин собран без поддержки Android") else emptyList()

  override val launcher: RunnerLauncher = object : RunnerLauncher {
    override fun start(manifest: PluginManifest, language: String, listener: RunnerListener): RunnerConnection =
      AndroidRunnerConnection(
        app,
        manifest,
        File(File(dir, manifest.id), PLUGIN_DEX),
        language,
        listener
      )
  }
}

private class AndroidRunnerConnection(
  private val context: Context,
  private val manifest: PluginManifest,
  dexFile: File,
  language: String,
  private val listener: RunnerListener
) : RunnerConnection {
  private val lock = Any()
  private var remote: Messenger? = null
  private val queue = ArrayList<ToRunner>()
  private var closed = false
  private val thread = HandlerThread("plugin-${manifest.id}").apply { start() }
  private val incoming = Messenger(
    Handler(thread.looper) { msg ->
      msg.data?.getString(KEY_JSON)?.let { json ->
        runCatching { ProtocolJson.decodeFromString(FromRunner.serializer(), json) }
          .getOrNull()
          ?.let { listener.onMessage(it) }
      }
      true
    }
  )

  private val serviceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
      val pending: List<ToRunner>
      synchronized(lock) {
        remote = binder?.let { Messenger(it) }
        pending = queue.toList()
        queue.clear()
      }
      pending.forEach { send(it) }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      synchronized(lock) { remote = null }
      if (!closed) listener.onExit("disconnected")
    }

    override fun onBindingDied(name: ComponentName?) {
      if (!closed) listener.onExit("binding died")
    }
  }

  init {
    val intent = Intent(context, PluginRunnerService::class.java)
    val bound = if (Build.VERSION.SDK_INT >= 29) {
      context.bindIsolatedService(
        intent,
        Context.BIND_AUTO_CREATE,
        "plugin_${manifest.id}",
        context.mainExecutor,
        serviceConnection
      )
    } else {
      context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    require(bound) { "service is not available" }
    val dex = dexFile.readBytes()
    var offset = 0
    while (offset < dex.size) {
      val end = minOf(dex.size, offset + CHUNK_BYTES)
      send(ToRunner.Code(Base64.encodeToString(dex, offset, end - offset, Base64.NO_WRAP)))
      offset = end
    }
    send(ToRunner.Load(manifest.entry, language))
  }

  override fun send(message: ToRunner) {
    val target = synchronized(lock) {
      if (closed) return
      remote
        ?: run {
          queue += message
          return
        }
    }
    val msg = Message.obtain()
    msg.data = Bundle().apply {
      putString(KEY_SESSION, manifest.id)
      putString(KEY_JSON, ProtocolJson.encodeToString(ToRunner.serializer(), message))
    }
    msg.replyTo = incoming
    runCatching { target.send(msg) }
      .onFailure { if (!closed) listener.onExit("send failed: ${it.message}") }
  }

  override fun close() {
    synchronized(lock) {
      closed = true
      remote = null
    }
    runCatching { context.unbindService(serviceConnection) }
    thread.quitSafely()
  }
}
