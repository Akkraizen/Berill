package dev.akkraizen.berill.plugins.nutrition.data

import dev.akkraizen.berill.api.*
import dev.akkraizen.berill.plugins.nutrition.NutritionPlugin
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.*

internal class NutritionHealth(private val ctx: PluginContext) : HealthDataSink, HealthSummarySource {
  private val days = ctx.data.collection(NutritionPlugin.HEALTH, HealthDay.serializer())
  override fun accept(day: HealthDay) {
    days.save(day.date, day)
    val weight = day.weightKg ?: return
    val current = ctx.data.raw(NutritionPlugin.WEIGHTS).get(day.date)
    if (current != null && current.fields["source"]?.jsonPrimitive?.contentOrNull != "health_connect") return
    ctx.data.raw(NutritionPlugin.WEIGHTS).patch(day.date, mapOf("date" to JsonPrimitive(day.date), "kg" to JsonPrimitive(weight), "source" to JsonPrimitive("health_connect")))
  }
  override fun day(date: LocalDate): HealthDay? {
    val day = days.get(date.toString()) ?: return null
    if (ctx.capabilities.all<HealthSummarySource>().none { it !is NutritionHealth }) return day
    return day.weightKg?.let { HealthDay(day.date, weightKg = it) }
  }
}
