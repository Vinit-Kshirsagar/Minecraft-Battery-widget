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
import android.util.Log
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class BatteryWidgetProvider : AppWidgetProvider() {

    companion object {
        const val TAG                   = "BatteryWidget"
        const val ACTION_UPDATE         = "com.example.minecraft_battery.UPDATE_WIDGET"
        const val UPDATE_INTERVAL_MS    = 60_000L
        const val PREF_MY_CODE          = "flutter.my_code"
        const val PREF_PARTNER_CODE     = "flutter.partner_code"
        const val PREF_PARTNER_BATTERY  = "partner_battery_cache"
        const val PREF_MY_LABEL         = "my_label"
        const val PREF_PARTNER_LABEL    = "partner_label"
    }

    private val heartIds = listOf(
        R.id.heart_1, R.id.heart_2, R.id.heart_3, R.id.heart_4, R.id.heart_5,
        R.id.heart_6, R.id.heart_7, R.id.heart_8, R.id.heart_9, R.id.heart_10
    )

    private val partnerHeartIds = listOf(
        R.id.partner_heart_1, R.id.partner_heart_2, R.id.partner_heart_3,
        R.id.partner_heart_4, R.id.partner_heart_5, R.id.partner_heart_6,
        R.id.partner_heart_7, R.id.partner_heart_8, R.id.partner_heart_9,
        R.id.partner_heart_10
    )

    enum class HeartState { FULL, HALF, CRACKED, EMPTY }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "onEnabled")
        scheduleUpdates(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "onDisabled")
        cancelUpdates(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate: ${appWidgetIds.toList()}")
        for (id in appWidgetIds) triggerUpdate(context, appWidgetManager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "onReceive: ${intent.action}")
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
            for (id in ids) triggerUpdate(context, manager, id)
        }
    }

    // ── Scheduling ────────────────────────────────────────────────────────────

    private fun scheduleUpdates(context: Context) {
        try {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.setRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + UPDATE_INTERVAL_MS,
                UPDATE_INTERVAL_MS,
                getPendingIntent(context)
            )
            Log.d(TAG, "Updates scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "scheduleUpdates failed: ${e.message}")
        }
    }

    private fun cancelUpdates(context: Context) {
        try {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .cancel(getPendingIntent(context))
        } catch (e: Exception) {
            Log.e(TAG, "cancelUpdates failed: ${e.message}")
        }
    }

    private fun getPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BatteryWidgetProvider::class.java).apply {
            action = ACTION_UPDATE
        }
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ── Trigger update ────────────────────────────────────────────────────────

    private fun triggerUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        try {
            val batteryInfo = getBatteryInfo(context)
            Log.d(TAG, "Battery: ${batteryInfo.level}%, charging: ${batteryInfo.isCharging}")

            val prefs = context.getSharedPreferences(
                "FlutterSharedPreferences", Context.MODE_PRIVATE)

            val myCode           = prefs.getString(PREF_MY_CODE, null)
            val partnerCode      = prefs.getString(PREF_PARTNER_CODE, null)
            val cachedPartner    = prefs.getInt(PREF_PARTNER_BATTERY, -1)
            val myLabel          = prefs.getString(PREF_MY_LABEL, "Y") ?: "Y"
            val partnerLabel     = prefs.getString(PREF_PARTNER_LABEL, "P") ?: "P"

            Log.d(TAG, "myCode=$myCode partnerCode=$partnerCode " +
                       "cachedPartner=$cachedPartner myLabel=$myLabel partnerLabel=$partnerLabel")

            // Render immediately with cached data
            renderWidget(
                context, appWidgetManager, appWidgetId,
                batteryInfo.level, cachedPartner, partnerCode != null,
                myLabel, partnerLabel
            )

            // Skip network if app has never been opened
            if (myCode == null) {
                Log.d(TAG, "myCode null — skipping network")
                return
            }

            // Push + fetch in background
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d(TAG, "Pushing battery to Supabase")
                    pushBattery(myCode, batteryInfo.level)

                    var partnerBattery = cachedPartner
                    if (partnerCode != null) {
                        Log.d(TAG, "Fetching partner battery")
                        val fetched = fetchPartnerBattery(partnerCode)
                        Log.d(TAG, "Partner battery fetched: $fetched")
                        if (fetched != null) {
                            partnerBattery = fetched
                            prefs.edit()
                                .putInt(PREF_PARTNER_BATTERY, fetched)
                                .apply()
                        }
                    }

                    withContext(Dispatchers.Main) {
                        renderWidget(
                            context, appWidgetManager, appWidgetId,
                            batteryInfo.level, partnerBattery, partnerCode != null,
                            myLabel, partnerLabel
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Network coroutine failed: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "triggerUpdate failed: ${e.message}", e)
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────

    private fun renderWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        myBattery: Int,
        partnerBattery: Int,
        isPaired: Boolean,
        myLabel: String,
        partnerLabel: String
    ) {
        try {
            Log.d(TAG, "renderWidget: my=$myBattery partner=$partnerBattery " +
                       "paired=$isPaired myLabel=$myLabel partnerLabel=$partnerLabel")

            val views = RemoteViews(context.packageName, R.layout.battery_widget)

            // Labels
            views.setTextViewText(R.id.myLabel, myLabel.take(1).uppercase())
            views.setTextViewText(R.id.partnerLabel, partnerLabel.take(1).uppercase())

            // My hearts
            val myStates = getHeartStates(myBattery.coerceAtLeast(0))
            for ((i, state) in myStates.withIndex()) {
                views.setImageViewResource(heartIds[i], drawableFor(state))
            }

            // Partner hearts
            if (isPaired && partnerBattery >= 0) {
                Log.d(TAG, "Showing partner row")
                views.setViewVisibility(R.id.partnerRow, android.view.View.VISIBLE)
                val partnerStates = getHeartStates(partnerBattery)
                for ((i, state) in partnerStates.withIndex()) {
                    views.setImageViewResource(partnerHeartIds[i], drawableFor(state))
                }
            } else {
                Log.d(TAG, "Hiding partner row")
                views.setViewVisibility(R.id.partnerRow, android.view.View.GONE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
            Log.d(TAG, "renderWidget success")
        } catch (e: Exception) {
            Log.e(TAG, "renderWidget failed: ${e.message}", e)
        }
    }

    private fun drawableFor(state: HeartState): Int = when (state) {
        HeartState.FULL    -> R.drawable.heart_full
        HeartState.HALF    -> R.drawable.heart_half
        HeartState.CRACKED -> R.drawable.heart_cracked
        HeartState.EMPTY   -> R.drawable.heart_empty
    }

    // ── Heart logic ───────────────────────────────────────────────────────────

    private fun getHeartStates(level: Int): List<HeartState> {
        val units = level % 10
        val filledHalves = if (units >= 2) (level / 10) * 2 + 1 else (level / 10) * 2
        return (1..10).map { i ->
            val remaining = filledHalves - (i - 1) * 2
            when {
                remaining >= 2 -> HeartState.FULL
                remaining == 1 -> if (units >= 6) HeartState.CRACKED else HeartState.HALF
                else           -> HeartState.EMPTY
            }
        }
    }

    // ── Battery info ──────────────────────────────────────────────────────────

    data class BatteryInfo(val level: Int, val isCharging: Boolean)

    private fun getBatteryInfo(context: Context): BatteryInfo {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter)
        val level = intent?.let {
            val raw   = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
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

    // ── Supabase ──────────────────────────────────────────────────────────────

    private fun pushBattery(code: String, battery: Int) {
        try {
            val conn = (URL("${Secrets.SUPABASE_URL}/rest/v1/users")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", Secrets.SUPABASE_KEY)
                setRequestProperty("Authorization", "Bearer ${Secrets.SUPABASE_KEY}")
                setRequestProperty("Prefer", "resolution=merge-duplicates")
                doOutput        = true
                connectTimeout  = 5000
                readTimeout     = 5000
            }
            val body = JSONObject().apply {
                put("code", code)
                put("battery", battery)
                put("updated_at", java.util.Date().toString())
            }.toString()
            conn.outputStream.use { it.write(body.toByteArray()) }
            val responseCode = conn.responseCode
            Log.d(TAG, "pushBattery response: $responseCode")
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "pushBattery failed: ${e.message}")
        }
    }

    private fun fetchPartnerBattery(partnerCode: String): Int? {
        return try {
            val conn = (URL("${Secrets.SUPABASE_URL}/rest/v1/users?code=eq.$partnerCode&select=battery")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("apikey", Secrets.SUPABASE_KEY)
                setRequestProperty("Authorization", "Bearer ${Secrets.SUPABASE_KEY}")
                connectTimeout = 5000
                readTimeout    = 5000
            }
            val response = conn.inputStream.bufferedReader().readText()
            val responseCode = conn.responseCode
            Log.d(TAG, "fetchPartnerBattery response: $responseCode body: $response")
            conn.disconnect()
            val arr = JSONArray(response)
            if (arr.length() > 0) arr.getJSONObject(0).getInt("battery") else null
        } catch (e: Exception) {
            Log.e(TAG, "fetchPartnerBattery failed: ${e.message}")
            null
        }
    }
}