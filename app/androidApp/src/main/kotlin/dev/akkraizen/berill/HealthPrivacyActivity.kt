package dev.akkraizen.berill

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class HealthPrivacyActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(TextView(this).apply {
      setPadding(32, 48, 32, 32)
      text = "Berill · Health Connect\n\nBerill reads weight, steps and sleep only with your permission. The last 7 days are saved locally and displayed in Nutrition, Workouts and Day. If you configured synchronization, these imported records are also sent to your configured server using the same sync and encryption settings. Berill does not write to Health Connect. You can revoke access at any time in Android Health Connect settings."
    })
  }
}
