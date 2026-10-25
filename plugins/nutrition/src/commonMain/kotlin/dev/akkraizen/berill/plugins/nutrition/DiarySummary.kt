package dev.akkraizen.berill.plugins.nutrition

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.feedback.ProgressLine

@Composable
internal fun SummaryCard(totals: Totals, norm: Norm) {
  val left = norm.kcal - totals.kcal
  SectionCard(NutritionText.eaten(), Icons.Outlined.LocalFireDepartment) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
      KcalRing(
        fraction = if (norm.kcal > 0) (totals.kcal / norm.kcal).toFloat() else 0f,
        over = left < 0,
        modifier = Modifier.size(132.dp)
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            totals.kcal.kcalText(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
          )
          Text(
            NutritionText.kcal(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          if (left >= 0) NutritionText.left() else NutritionText.over(),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          NutritionText.kcalValue(kotlin.math.abs(left).kcalText())(),
          style = MaterialTheme.typography.titleLarge,
          color = if (left >= 0)
            MaterialTheme.colorScheme.primary
          else
            MaterialTheme.colorScheme.error
        )
        Text(
          "${NutritionText.target()}: ${NutritionText.kcalValue(norm.kcal.toString())()}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
    MacroLine(NutritionText.protein(), totals.protein, norm.protein, MaterialTheme.colorScheme.primary)
    MacroLine(NutritionText.fat(), totals.fat, norm.fat, MaterialTheme.colorScheme.tertiary)
    MacroLine(NutritionText.carbs(), totals.carbs, norm.carbs, MaterialTheme.colorScheme.secondary)
  }
}

@Composable
internal fun MacroLine(label: String, value: Double, target: Int, color: Color) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row {
      Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
      Text(
        NutritionText.ofTarget(formatNum(value, 0), NutritionText.gramsValue(target.toString())())(),
        style = MaterialTheme.typography.labelLarge,
        color = if (value > target * 1.1)
          MaterialTheme.colorScheme.error
        else
          MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    ProgressLine(if (target > 0) (value / target).toFloat() else 0f, color = color)
  }
}

@Composable
private fun KcalRing(fraction: Float, over: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  val colors = MaterialTheme.colorScheme
  val animated by
    animateFloatAsState(fraction.coerceIn(0f, 1f), BerillMotion.slowSpatial(), label = "kcal-ring")
  val track = colors.surfaceContainerHighest
  val arc = if (over) colors.error else colors.primary
  Box(modifier, contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxSize()) {
      val stroke = 12.dp.toPx()
      val inset = stroke / 2
      val arcSize = Size(size.width - stroke, size.height - stroke)
      drawArc(
        track,
        0f,
        360f,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = arcSize,
        style = Stroke(stroke)
      )
      if (animated > 0f) {
        drawArc(
          arc,
          -90f,
          360f * animated,
          useCenter = false,
          topLeft = Offset(inset, inset),
          size = arcSize,
          style = Stroke(stroke, cap = StrokeCap.Round)
        )
      }
    }
    content()
  }
}

internal fun macroText(p: Double, f: Double, c: Double): String =
  "${NutritionText.proteinShort()} ${formatNum(p, 0)} ${NutritionText.fatShort()} ${
    formatNum(f, 0)
  } ${NutritionText.carbsShort()} ${formatNum(c, 0)}"
