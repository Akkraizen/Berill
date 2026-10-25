package dev.akkraizen.berill.core.external

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Base64
import dalvik.system.InMemoryDexClassLoader
import dev.akkraizen.berill.sdk.ExternalPlugin
import dev.akkraizen.berill.sdk.protocol.FromRunner
import dev.akkraizen.berill.sdk.protocol.ProtocolJson
import dev.akkraizen.berill.sdk.protocol.ToRunner
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

internal const val KEY_SESSION = "session"
internal const val KEY_JSON = "json"

class PluginRunnerService : Service() {
  private val sessions = HashMap<String, Session>()
  private lateinit var messenger: Messenger

  override fun onCreate() {
    super.onCreate()
    val thread = HandlerThread("plugin-ipc").apply { start() }
    messenger = Messenger(
      Handler(thread.looper) { msg ->
        handle(msg)
        true
      }
    )
  }

  override fun onBind(intent: Intent): IBinder = messenger.binder

  private fun handle(msg: Message) {
    val data = msg.data ?: return
    val key = data.getString(KEY_SESSION) ?: return
    val json = data.getString(KEY_JSON) ?: return
    val session = sessions.getOrPut(key) { Session() }
    msg.replyTo?.let { session.replyTo = it }
    val message = runCatching { ProtocolJson.decodeFromString(ToRunner.serializer(), json) }.getOrNull()
      ?: return
    session.runtime.onMessage(message)
  }

  private class Session {
    @Volatile
    var replyTo: Messenger? = null
    private val dex = ByteArrayOutputStream()

    val runtime = RunnerRuntime(
      platform = ThreadRunnerPlatform(),
      send = { message ->
        val out = Message.obtain()
        out.data = Bundle().apply {
          putString(KEY_JSON, ProtocolJson.encodeToString(FromRunner.serializer(), message))
        }
        runCatching { replyTo?.send(out) }
      },
      onCode = { code -> dex.write(Base64.decode(code.chunk, Base64.NO_WRAP)) },
      instantiate = { load ->
        val bytes = dex.toByteArray()
        dex.reset()
        require(bytes.isNotEmpty()) { "no classes.dex" }
        val loader = InMemoryDexClassLoader(
          ByteBuffer.wrap(bytes),
          FilteringClassLoader(ExternalPlugin::class.java.classLoader!!)
        )
        val cls = loader.loadClass(load.entry)
        require(ExternalPlugin::class.java.isAssignableFrom(cls)) {
          "${load.entry} is not an ExternalPlugin"
        }
        cls.getDeclaredConstructor().newInstance() as ExternalPlugin
      }
    )
  }
}
