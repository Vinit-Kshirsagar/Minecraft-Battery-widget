package com.example.minecraft_battery

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
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
        const val PREF_MY_LABEL         = "flutter.my_label"
        const val PREF_PARTNER_LABEL    = "flutter.partner_label"
        const val PREF_HEART_SIZE       = "flutter.heart_size"
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
            Log.d(TAG, "Battery: ${batteryInfo.level}%")

            val prefs = context.getSharedPreferences(
                "FlutterSharedPreferences", Context.MODE_PRIVATE)

            val myCode        = prefs.getString(PREF_MY_CODE, null)
            val partnerCode   = prefs.getString(PREF_PARTNER_CODE, null)
            val cachedPartner = prefs.getInt(PREF_PARTNER_BATTERY, -1)
            val myLabel       = prefs.getString(PREF_MY_LABEL, "Y") ?: "Y"
            val partnerLabel  = prefs.getString(PREF_PARTNER_LABEL, "P") ?: "P"
            // Flutter stores doubles as a base64-prefixed string, not a float.
            // e.g. "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu30.0"
            // Strip the 44-char prefix and parse the numeric remainder.
            val heartSizeDp   = parseFlutterDouble(prefs.getString(PREF_HEART_SIZE, null), 30.0)
            val heartSizePx   = (heartSizeDp * context.resources.displayMetrics.density)
                .toInt().coerceAtLeast(10) // guard: 0 crashes Bitmap.createBitmap

            Log.d(TAG, "myCode=$myCode partnerCode=$partnerCode")

            renderWidget(
                context, appWidgetManager, appWidgetId,
                batteryInfo.level, cachedPartner, partnerCode != null,
                myLabel, partnerLabel, heartSizePx
            )

            if (myCode == null) {
                Log.d(TAG, "myCode null — skipping network")
                return
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    pushBattery(myCode, batteryInfo.level)

                    var partnerBattery = cachedPartner
                    if (partnerCode != null) {
                        val fetched = fetchPartnerBattery(partnerCode)
                        Log.d(TAG, "Partner battery: $fetched")
                        if (fetched != null) {
                            partnerBattery = fetched
                            prefs.edit().putInt(PREF_PARTNER_BATTERY, fetched).apply()
                        }
                    }

                    withContext(Dispatchers.Main) {
                        renderWidget(
                            context, appWidgetManager, appWidgetId,
                            batteryInfo.level, partnerBattery, partnerCode != null,
                            myLabel, partnerLabel, heartSizePx
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Network failed: ${e.message}", e)
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
        partnerLabel: String,
        heartSizePx: Int
    ) {
        try {
            val views = RemoteViews(context.packageName, R.layout.battery_widget)

            // My initial — white text, black outline, same size as hearts
            val myBitmap = makeInitialBitmap(
                myLabel.firstOrNull()?.uppercaseChar()?.toString() ?: "Y",
                heartSizePx
            )
            views.setImageViewBitmap(R.id.myLabel, myBitmap)

            // My hearts — original PNGs, no tinting
            val myStates = getHeartStates(myBattery.coerceAtLeast(0))
            for ((i, state) in myStates.withIndex()) {
                views.setImageViewResource(heartIds[i], drawableFor(state))
            }

            // Partner row
            if (isPaired && partnerBattery >= 0) {
                views.setViewVisibility(R.id.partnerRow, android.view.View.VISIBLE)

                val partBitmap = makeInitialBitmap(
                    partnerLabel.firstOrNull()?.uppercaseChar()?.toString() ?: "P",
                    heartSizePx
                )
                views.setImageViewBitmap(R.id.partnerLabel, partBitmap)

                // Partner hearts — original PNGs, no tinting
                val partnerStates = getHeartStates(partnerBattery)
                for ((i, state) in partnerStates.withIndex()) {
                    views.setImageViewResource(partnerHeartIds[i], drawableFor(state))
                }
            } else {
                views.setViewVisibility(R.id.partnerRow, android.view.View.GONE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
            Log.d(TAG, "renderWidget success")
        } catch (e: Exception) {
            Log.e(TAG, "renderWidget failed: ${e.message}", e)
        }
    }

    // ── Flutter double decoder ────────────────────────────────────────────────
    // Flutter's shared_preferences encodes doubles as:
    //   base64("This is the prefix for Double.") + the numeric string
    // The prefix is always 44 characters. We strip it and parse what remains.
    private fun parseFlutterDouble(raw: String?, fallback: Double): Double {
        if (raw == null) return fallback
        return try {
            // The prefix "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu" is 40 chars
            // but actual stored strings may vary slightly — find where digits start
            val numeric = raw.dropWhile { !it.isDigit() && it != '-' }
            numeric.toDouble()
        } catch (e: Exception) {
            Log.w(TAG, "parseFlutterDouble failed for '$raw', using $fallback")
            fallback
        }
    }

    // ── Initial bitmap — white fill, black outline, scales with heart size ────

    private fun makeInitialBitmap(letter: String, heartSizePx: Int): Bitmap {
        // Initial is 75% of heart size so all 10 hearts fit alongside it
        val initialSizePx = (heartSizePx * 0.75f).toInt().coerceAtLeast(1)
        // Draw on a 3x canvas then scale down for smooth anti-aliased edges
        val scale  = 3
        val canvas_size = initialSizePx * scale
        val bmp    = Bitmap.createBitmap(canvas_size, canvas_size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val textSize    = canvas_size * 0.68f
        val strokeWidth = canvas_size * 0.14f

        // Black outline paint
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color       = Color.BLACK
            this.textSize   = textSize
            typeface    = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign   = Paint.Align.CENTER
            style       = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeJoin  = Paint.Join.ROUND
            strokeCap   = Paint.Cap.ROUND
        }

        // White fill paint
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color       = Color.WHITE
            this.textSize   = textSize
            typeface    = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign   = Paint.Align.CENTER
            style       = Paint.Style.FILL
        }

        val x = canvas_size / 2f
        // Vertically centre the glyph
        val y = canvas_size / 2f - (strokePaint.ascent() + strokePaint.descent()) / 2f

        canvas.drawText(letter, x, y, strokePaint) // outline first
        canvas.drawText(letter, x, y, fillPaint)   // fill on top

        // Scale back down to initialSizePx — gives smooth result vs drawing small directly
        return Bitmap.createScaledBitmap(bmp, initialSizePx, initialSizePx, true)
    }

    // ── Heart logic ───────────────────────────────────────────────────────────

    private fun drawableFor(state: HeartState): Int = when (state) {
        HeartState.FULL    -> R.drawable.heart_full
        HeartState.HALF    -> R.drawable.heart_half
        HeartState.CRACKED -> R.drawable.heart_cracked
        HeartState.EMPTY   -> R.drawable.heart_empty
    }

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
                doOutput = true; connectTimeout = 5000; readTimeout = 5000
            }
            val body = JSONObject().apply {
                put("code", code); put("battery", battery)
                put("updated_at", java.util.Date().toString())
            }.toString()
            conn.outputStream.use { it.write(body.toByteArray()) }
            Log.d(TAG, "pushBattery: ${conn.responseCode}")
            conn.disconnect()
        } catch (e: Exception) { Log.e(TAG, "pushBattery failed: ${e.message}") }
    }

    private fun fetchPartnerBattery(partnerCode: String): Int? {
        return try {
            val conn = (URL("${Secrets.SUPABASE_URL}/rest/v1/users?code=eq.$partnerCode&select=battery")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("apikey", Secrets.SUPABASE_KEY)
                setRequestProperty("Authorization", "Bearer ${Secrets.SUPABASE_KEY}")
                connectTimeout = 5000; readTimeout = 5000
            }
            val response = conn.inputStream.bufferedReader().readText()
            Log.d(TAG, "fetchPartner: ${conn.responseCode} $response")
            conn.disconnect()
            val arr = JSONArray(response)
            if (arr.length() > 0) arr.getJSONObject(0).getInt("battery") else null
        } catch (e: Exception) { Log.e(TAG, "fetchPartner failed: ${e.message}"); null }
    }
}