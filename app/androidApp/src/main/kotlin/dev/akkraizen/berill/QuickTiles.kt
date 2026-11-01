package dev.akkraizen.berill

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

abstract class BerillQuickTile(private val command: String) : TileService() {
  override fun onClick() {
    super.onClick()
    val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      .putExtra("berill.quick", command)
    if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, command.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    else { @Suppress("DEPRECATION") startActivityAndCollapse(intent) }
  }
}
class WaterTile : BerillQuickTile("water")
class HabitTile : BerillQuickTile("habit")
class ExpenseTile : BerillQuickTile("expense")
