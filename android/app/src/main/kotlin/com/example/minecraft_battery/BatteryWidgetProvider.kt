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
        const val UPDATE_INTERVAL_MS = 60_000L // every 60 seconds
    }

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
        val trigger = intent.action == Intent.ACTION_BATTERY_CHANGED ||
                      intent.action == Intent.ACTION_BATTERY_LOW ||
                      intent.action == Intent.ACTION_POWER_CONNECTED ||
                      intent.action == Intent.ACTION_POWER_DISCONNECTED ||
                      intent.action == ACTION_UPDATE

        if (trigger) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, BatteryWidgetProvider::class.java)
            )
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }
    }

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

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.battery_widget)
        val batteryInfo = getBatteryInfo(context)
        val hearts = generateHearts(batteryInfo.level)
        val heartColor = getHeartColor(batteryInfo.level)
        val statusLabel = buildStatusLabel(batteryInfo)

        views.setTextViewText(R.id.heartsText, hearts)
        views.setTextColor(R.id.heartsText, heartColor)
        views.setTextViewText(R.id.percentageText, statusLabel)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    // --- Battery ---

    data class BatteryInfo(
        val level: Int,        // 0–100
        val isCharging: Boolean,
        val temperature: Float // Celsius
    )

    private fun getBatteryInfo(context: Context): BatteryInfo {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter)

        val level = intent?.let {
            val raw = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (raw >= 0 && scale > 0) (raw * 100 / scale) else -1
        } ?: run {
            // Fallback to BatteryManager API
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                         status == BatteryManager.BATTERY_STATUS_FULL

        val rawTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val temperature = rawTemp / 10f // tenths of a degree → Celsius

        return BatteryInfo(level.coerceIn(0, 100), isCharging, temperature)
    }

    // --- Heart Rendering ---

    private fun generateHearts(level: Int): String {
        // Minecraft uses 10 hearts = 20 half-hearts
        // Map 0–100% to 0–20 half-heart units
        val totalHalfHearts = 20
        val filledHalves = Math.round(level / 100f * totalHalfHearts).toInt()

        val fullHearts = filledHalves / 2
        val hasHalf = filledHalves % 2 == 1
        val emptyHearts = 10 - fullHearts - (if (hasHalf) 1 else 0)

        return buildString {
            repeat(fullHearts) { append("♥") }
            if (hasHalf) append("❥")
            repeat(emptyHearts) { append("♡") }
        }
    }

    private fun getHeartColor(level: Int): Int {
        return when {
            level > 50 -> 0xFFE03030.toInt()  // Healthy red
            level > 25 -> 0xFFE08020.toInt()  // Warning orange
            else       -> 0xFFE0E020.toInt()  // Critical yellow (Minecraft flashes yellow at low health)
        }
    }

    // --- Status Label ---

    private fun buildStatusLabel(info: BatteryInfo): String {
        val chargingIcon = if (info.isCharging) " ⚡" else ""
        return "${info.level}%$chargingIcon"
    }
}