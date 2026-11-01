package dev.akkraizen.berill.api.ui.display

import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import dev.akkraizen.berill.api.*
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.today
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate

@Composable
fun HealthSummary(ctx: PluginContext, date: LocalDate = today()) {
  val sources by remember(ctx) { ctx.capabilities.observe<HealthSummarySource>() }.collectAsState(ctx.capabilities.all<HealthSummarySource>())
  var days by remember(date, sources) { mutableStateOf(emptyList<HealthDay>()) }
  LaunchedEffect(ctx, date, sources) { while (true) {
    days = sources.mapNotNull { it.day(date) }
    delay(5000)
  } }
  if (days.isNotEmpty()) SectionCard("Health Connect", Icons.Outlined.MonitorHeart) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    days.forEach { day ->
      day.steps?.let { HealthMetric(Tr("connections.steps")() + ": $it", Icons.Outlined.DirectionsWalk) }
      day.sleepMinutes?.let { HealthMetric(Tr("connections.sleep")() + ": " + Tr("ui.minutes", "m" to it)(), Icons.Outlined.Bedtime) }
      day.weightKg?.let { HealthMetric("$it " + Tr("connections.kilograms")(), Icons.Outlined.MonitorWeight) }
    }
  }
  }
}

@Composable
private fun HealthMetric(label: String, icon: ImageVector) {
  Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(icon, null, Modifier.size(20.dp))
      Text(label, style = MaterialTheme.typography.labelLarge)
    }
  }
}
