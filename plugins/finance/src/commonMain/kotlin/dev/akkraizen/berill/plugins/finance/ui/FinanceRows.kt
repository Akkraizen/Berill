package dev.akkraizen.berill.plugins.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.ddMM
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.ProgressLine
import dev.akkraizen.berill.api.ui.feedback.color
import dev.akkraizen.berill.api.ui.formatRub
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.plugins.finance.data.Account
import dev.akkraizen.berill.plugins.finance.data.Operation
import dev.akkraizen.berill.plugins.finance.data.balance
import dev.akkraizen.berill.plugins.finance.data.kindIcon
import dev.akkraizen.berill.plugins.finance.data.kindLabel
import dev.akkraizen.berill.plugins.finance.i18n.FinanceText
import kotlin.math.roundToInt

@Composable
internal fun AccountRow(account: Account, balance: Double, onClick: () -> Unit) {
  Column(
    Modifier.fillMaxWidth()
      .interactive(MaterialTheme.shapes.medium, bleed = 8.dp, onClick = onClick)
      .padding(vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(account.title, style = MaterialTheme.typography.titleSmall)
        val meta = listOfNotNull(account.bank, account.rate?.let { "$it%" }).joinToString(" · ")
        if (meta.isNotEmpty())
          Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Text(formatRub(balance), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
    val goal = account.goal
    if (goal != null && goal > 0) {
      val pct = (balance / goal * 100).roundToInt().coerceIn(0, 100)
      ProgressLine(pct / 100f)
      Text(
        FinanceText.ofGoal(pct, formatRub(goal))(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
internal fun CategoryBar(category: String, sum: Double, fraction: Float) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Row {
      Text(category, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
      Text(formatRub(sum), style = MaterialTheme.typography.bodyMedium)
    }
    Box(
      Modifier.fillMaxWidth()
        .height(8.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
      Box(
        Modifier.fillMaxHeight()
          .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
          .clip(RoundedCornerShape(4.dp))
          .background(MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
      )
    }
  }
}

@Composable
internal fun OperationRow(op: Operation, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().interactive(bleed = 8.dp, onClick = onClick).padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Text(
      op.date.toDateOrNull()?.ddMM() ?: op.date,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.width(44.dp)
    )
    Ic(kindIcon(op.kind), tint = kindTone(op.kind).color())
    Text(
      listOfNotNull(op.category ?: kindLabel(op.kind), op.account, op.comment)
        .joinToString(" · "),
      modifier = Modifier.weight(1f),
      maxLines = 2,
      overflow = TextOverflow.Ellipsis
    )
    Text(
      formatRub(op.signedForWallet, signed = true),
      color = kindTone(op.kind).color(),
      style = MaterialTheme.typography.titleSmall
    )
  }
}
