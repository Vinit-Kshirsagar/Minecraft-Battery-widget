package com.example.minecraft_battery

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import android.widget.RemoteViews

class BatteryWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE = "com.example.minecraft_battery.UPDATE_WIDGET"
        const val UPDATE_INTERVAL_MS = 60_000L
    }

    private val heartIds = listOf(
        R.id.heart_1, R.id.heart_2, R.id.heart_3, R.id.heart_4, R.id.heart_5,
        R.id.heart_6, R.id.heart_7, R.id.heart_8, R.id.heart_9, R.id.heart_10
    )

    enum class HeartState { FULL, HALF, CRACKED, EMPTY }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleUpdates(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelUpdates(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val shouldUpdate = intent.action in listOf(
            Intent.ACTION_BATTERY_CHANGED,
            Intent.ACTION_BATTERY_LOW,
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED,
            ACTION_UPDATE
        )
        if (shouldUpdate) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, BatteryWidgetProvider::class.java)
            )
            for (id in ids) updateWidget(context, manager, id)
        }
    }

    // ── Scheduling ───────────────────────────────────────────────────────────

    private fun scheduleUpdates(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + UPDATE_INTERVAL_MS,
            UPDATE_INTERVAL_MS,
            getUpdatePendingIntent(context)
        )
    }

    private fun cancelUpdates(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(getUpdatePendingIntent(context))
    }

    private fun getUpdatePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BatteryWidgetProvider::class.java).apply {
            action = ACTION_UPDATE
        }
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ── Widget Update ────────────────────────────────────────────────────────

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.battery_widget)
        val batteryInfo = getBatteryInfo(context)
        val heartStates = getHeartStates(batteryInfo.level)

        for ((index, state) in heartStates.withIndex()) {
            val drawable = when (state) {
                HeartState.FULL    -> R.drawable.heart_full
                HeartState.HALF    -> R.drawable.heart_half
                HeartState.CRACKED -> R.drawable.heart_cracked
                HeartState.EMPTY   -> R.drawable.heart_empty
            }
            views.setImageViewResource(heartIds[index], drawable)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    // ── Heart Logic ──────────────────────────────────────────────────────────

     private fun getHeartStates(level: Int): List<HeartState> {
    // Units digit 0-1  → treat as 0  (full hearts only, no partial)
    // Units digit 2-5  → treat as half heart (.5)
    // Units digit 6-9  → treat as cracked heart (.5 but damaged)
    val units = level % 10
    val filledHalves = when {
        units in 2..9 -> (level / 10) * 2 + 1  // add a half heart for this decade
        else          -> (level / 10) * 2        // units 0-1: no partial heart
    }

    return (1..10).map { heartIndex ->
        val remaining = filledHalves - (heartIndex - 1) * 2
        when {
            remaining >= 2 -> HeartState.FULL
            remaining == 1 -> {
                if (units in 6..9) HeartState.CRACKED else HeartState.HALF
            }
            else -> HeartState.EMPTY
        }
    }
}

    // ── Battery Info ─────────────────────────────────────────────────────────

    data class BatteryInfo(val level: Int, val isCharging: Boolean)

    private fun getBatteryInfo(context: Context): BatteryInfo {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter)

        val level = intent?.let {
            val raw = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (raw >= 0 && scale > 0) (raw * 100 / scale) else -1
        }?.takeIf { it >= 0 } ?: run {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                         status == BatteryManager.BATTERY_STATUS_FULL

        return BatteryInfo(level.coerceIn(0, 100), isCharging)
    }
}