package dev.akkraizen.berill.plugins.health

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.Today
import androidx.compose.runtime.Composable
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.Capability
import dev.akkraizen.berill.api.CollectionDaySource
import dev.akkraizen.berill.api.HomeCard
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.PluginPage
import dev.akkraizen.berill.api.QuickAction
import dev.akkraizen.berill.plugins.health.data.HealthActivity
import dev.akkraizen.berill.plugins.health.data.MedicationReminders
import dev.akkraizen.berill.plugins.health.i18n.HealthText
import dev.akkraizen.berill.plugins.health.mcp.HealthMcp
import dev.akkraizen.berill.plugins.health.ui.HealthHomeCard
import dev.akkraizen.berill.plugins.health.ui.HealthSettings
import dev.akkraizen.berill.plugins.health.ui.MedicationsPage
import dev.akkraizen.berill.plugins.health.ui.SleepPage
import dev.akkraizen.berill.plugins.health.ui.TodayPage
import dev.akkraizen.berill.plugins.health.ui.VitalsPage
import dev.akkraizen.berill.plugins.health.ui.QuickVitalDialog
import dev.akkraizen.berill.plugins.health.ui.QuickWellbeingDialog
import dev.akkraizen.berill.plugins.health.ui.WellbeingPage
import dev.akkraizen.berill.plugins.health.ui.describeSleep
import dev.akkraizen.berill.plugins.health.ui.describeVital
import dev.akkraizen.berill.plugins.health.ui.describeWellbeing

object HealthPlugin : BerillPlugin {
  const val MEDICATIONS = "health_medications"
  const val INTAKES = "health_intakes"
  const val VITALS = "health_vitals"
  const val WELLBEING = "health_wellbeing"
  const val SLEEP = "health_sleep"
  const val ACTIVITY = "health_activity"

  const val PAGE_TODAY = "today"
  const val PAGE_MEDICATIONS = "medications"
  const val PAGE_VITALS = "vitals"
  const val PAGE_WELLBEING = "wellbeing"
  const val PAGE_SLEEP = "sleep"

  override val id = "health"
  override val title: String
    get() = HealthText.title()

  override val icon = Icons.Outlined.HealthAndSafety
  override val order = 14
  override val description: String
    get() = HealthText.description()

  override val installedByDefault = true
  override val hasSettings = true

  @Composable
  override fun Settings(ctx: PluginContext) = HealthSettings(ctx)

  override val collections = listOf(MEDICATIONS, INTAKES, VITALS, WELLBEING, SLEEP, ACTIVITY)

  override val pages = listOf(
    PluginPage(PAGE_TODAY, HealthText.pageToday, Icons.Outlined.Today) { ctx -> TodayPage(ctx) },
    PluginPage(PAGE_MEDICATIONS, HealthText.pageMedications, Icons.Outlined.Medication) { ctx -> MedicationsPage(ctx) },
    PluginPage(PAGE_VITALS, HealthText.pageVitals, Icons.Outlined.MonitorHeart) { ctx -> VitalsPage(ctx) },
    PluginPage(PAGE_WELLBEING, HealthText.pageWellbeing, Icons.Outlined.SentimentSatisfied) { ctx -> WellbeingPage(ctx) },
    PluginPage(PAGE_SLEEP, HealthText.pageSleep, Icons.Outlined.Bedtime) { ctx -> SleepPage(ctx) }
  )

  override fun homeCards(ctx: PluginContext) =
    listOf(HomeCard("today", HealthText.homeCard, order = 27) { HealthHomeCard(ctx) })

  override fun quickActions(ctx: PluginContext) =
    listOf(
      QuickAction("wellbeing", HealthText.quickWellbeing, Icons.Outlined.SentimentSatisfied) {
        ctx.nav.openPlugin(id, page = PAGE_WELLBEING)
      }.withPopup { close, error -> QuickWellbeingDialog(ctx, close, error) },
      QuickAction("vitals", HealthText.quickVitals, Icons.Outlined.MonitorHeart) {
        ctx.nav.openPlugin(id, page = PAGE_VITALS)
      }.withPopup { close, error -> QuickVitalDialog(ctx, close, error) }
    )

  override fun provides(ctx: PluginContext): List<Capability> = listOf(
    HealthActivity(ctx),
    MedicationReminders(ctx),
    HealthMcp(ctx),
    CollectionDaySource(ctx, VITALS, { HealthText.pageVitals() }, describe = ::describeVital, page = PAGE_VITALS),
    CollectionDaySource(ctx, WELLBEING, { HealthText.pageWellbeing() }, describe = ::describeWellbeing, page = PAGE_WELLBEING),
    CollectionDaySource(ctx, SLEEP, { HealthText.pageSleep() }, describe = ::describeSleep, page = PAGE_SLEEP)
  )
}
