package dev.akkraizen.berill.core.external

import dev.akkraizen.berill.sdk.protocol.PluginClassRules
import dev.akkraizen.berill.sdk.protocol.ToRunner
import java.net.URL
import java.util.Collections
import java.util.Enumeration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class ThreadRunnerPlatform : RunnerPlatform {
  private val worker = Executors.newSingleThreadExecutor { r ->
    Thread(r, "plugin-worker").apply { isDaemon = true }
  }
  private val slots = ConcurrentHashMap<Long, ArrayBlockingQueue<ToRunner.Reply>>()

  override fun execute(task: () -> Unit) {
    worker.execute { runCatching(task) }
  }

  override fun slot(callId: Long): ReplySlot {
    val queue = ArrayBlockingQueue<ToRunner.Reply>(1)
    slots[callId] = queue
    return object : ReplySlot {
      override fun await(timeoutMs: Long): ToRunner.Reply? =
        try {
          queue.poll(timeoutMs, TimeUnit.MILLISECONDS)
        } finally {
          slots.remove(callId)
        }
    }
  }

  override fun complete(reply: ToRunner.Reply) {
    slots[reply.callId]?.offer(reply)
  }
}

internal class FilteringClassLoader(parent: ClassLoader) : ClassLoader(parent) {
  override fun loadClass(name: String, resolve: Boolean): Class<*> {
    if (!PluginClassRules.isAllowed(name))
      throw ClassNotFoundException("Berill: plugins may not use $name")
    return super.loadClass(name, resolve)
  }

  override fun getResource(name: String?): URL? = null

  override fun getResources(name: String?): Enumeration<URL> = Collections.emptyEnumeration()
}
