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
import android.graphics.BitmapFactory
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
        const val TAG                  = "BatteryWidget"
        const val ACTION_UPDATE        = "com.example.minecraft_battery.UPDATE_WIDGET"
        const val UPDATE_INTERVAL_MS   = 60_000L
        const val PREF_MY_CODE         = "flutter.my_code"
        const val PREF_PARTNER_CODE    = "flutter.partner_code"
        const val PREF_PARTNER_BATTERY = "partner_battery_cache"
        const val PREF_MY_LABEL        = "flutter.my_label"
        const val PREF_PARTNER_LABEL   = "flutter.partner_label"
        const val PREF_HEART_SIZE      = "flutter.heart_size"
    }

    enum class HeartState { FULL, HALF, CRACKED, EMPTY }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

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
        Log.d(TAG, "onUpdate: ${appWidgetIds.toList()}")
        for (id in appWidgetIds) triggerUpdate(context, appWidgetManager, id)
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
        } catch (e: Exception) { Log.e(TAG, "scheduleUpdates: ${e.message}") }
    }

    private fun cancelUpdates(context: Context) {
        try {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .cancel(getPendingIntent(context))
        } catch (e: Exception) { Log.e(TAG, "cancelUpdates: ${e.message}") }
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
            val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)

            val myCode        = prefs.getString(PREF_MY_CODE, null)
            val partnerCode   = prefs.getString(PREF_PARTNER_CODE, null)
            val cachedPartner = prefs.getInt(PREF_PARTNER_BATTERY, -1)
            val myLabel       = prefs.getString(PREF_MY_LABEL, "Y") ?: "Y"
            val partnerLabel  = prefs.getString(PREF_PARTNER_LABEL, "P") ?: "P"
            val heartSizeDp   = parseFlutterDouble(prefs.getString(PREF_HEART_SIZE, null), 30.0)
            val heartSizePx   = (heartSizeDp * context.resources.displayMetrics.density)
                                    .toInt().coerceIn(10, 200)

            Log.d(TAG, "Battery=${batteryInfo.level}% size=${heartSizeDp}dp myCode=$myCode partnerCode=$partnerCode")

            renderWidget(
                context, appWidgetManager, appWidgetId,
                batteryInfo.level, cachedPartner, partnerCode != null,
                myLabel, partnerLabel, heartSizePx
            )

            if (myCode == null) return

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    pushBattery(myCode, batteryInfo.level)

                    var partnerBattery = cachedPartner
                    if (partnerCode != null) {
                        val fetched = fetchPartnerBattery(partnerCode)
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
                } catch (e: Exception) { Log.e(TAG, "Network: ${e.message}", e) }
            }
        } catch (e: Exception) { Log.e(TAG, "triggerUpdate: ${e.message}", e) }
    }

    // ── Render — draws everything onto one bitmap ─────────────────────────────

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
            // Load heart bitmaps scaled to heartSizePx
            val hearts = loadHeartBitmaps(context, heartSizePx)

            // Initial is 75% the size of a heart
            val initialSizePx = (heartSizePx * 0.75f).toInt().coerceAtLeast(8)

            // Row width: initial + gap + 10 hearts
            val gap       = (heartSizePx * 0.05f).toInt().coerceAtLeast(1)
            val rowWidth  = initialSizePx + gap + (heartSizePx * 10)
            val rowHeight = heartSizePx

            val rows      = if (isPaired && partnerBattery >= 0) 2 else 1
            val rowGap    = (heartSizePx * 0.15f).toInt()
            val bmpWidth  = rowWidth
            val bmpHeight = rowHeight * rows + if (rows > 1) rowGap else 0

            val bmp    = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // Draw my row
            drawRow(
                canvas, hearts,
                myLabel.firstOrNull()?.uppercaseChar()?.toString() ?: "Y",
                getHeartStates(myBattery.coerceAtLeast(0)),
                initialSizePx, heartSizePx, gap,
                y = 0
            )

            // Draw partner row if paired
            if (isPaired && partnerBattery >= 0) {
                drawRow(
                    canvas, hearts,
                    partnerLabel.firstOrNull()?.uppercaseChar()?.toString() ?: "P",
                    getHeartStates(partnerBattery),
                    initialSizePx, heartSizePx, gap,
                    y = rowHeight + rowGap
                )
            }

            // Recycle scaled bitmaps
            hearts.values.forEach { it.recycle() }

            val views = RemoteViews(context.packageName, R.layout.battery_widget)
            views.setImageViewBitmap(R.id.widgetCanvas, bmp)
            appWidgetManager.updateAppWidget(appWidgetId, views)
            Log.d(TAG, "renderWidget success — ${bmpWidth}x${bmpHeight}px heartSize=${heartSizePx}px")
        } catch (e: Exception) { Log.e(TAG, "renderWidget: ${e.message}", e) }
    }

    private fun drawRow(
        canvas: Canvas,
        hearts: Map<HeartState, Bitmap>,
        label: String,
        states: List<HeartState>,
        initialSizePx: Int,
        heartSizePx: Int,
        gap: Int,
        y: Int
    ) {
        // Vertically centre the initial relative to hearts
        val initialTop = ((heartSizePx - initialSizePx) / 2f).toInt()

        // Draw the initial letter bitmap
        val initialBmp = makeInitialBitmap(label, initialSizePx)
        canvas.drawBitmap(initialBmp, 0f, (y + initialTop).toFloat(), null)
        initialBmp.recycle()

        // Draw hearts
        var x = initialSizePx + gap
        for (state in states) {
            hearts[state]?.let { canvas.drawBitmap(it, x.toFloat(), y.toFloat(), null) }
            x += heartSizePx
        }
    }

    // ── Load heart PNGs scaled to target size ─────────────────────────────────

    private fun loadHeartBitmaps(context: Context, sizePx: Int): Map<HeartState, Bitmap> {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        fun load(resId: Int): Bitmap {
            val src = BitmapFactory.decodeResource(context.resources, resId, opts)
            return Bitmap.createScaledBitmap(src, sizePx, sizePx, false).also {
                if (it !== src) src.recycle()
            }
        }
        return mapOf(
            HeartState.FULL    to load(R.drawable.heart_full),
            HeartState.HALF    to load(R.drawable.heart_half),
            HeartState.CRACKED to load(R.drawable.heart_cracked),
            HeartState.EMPTY   to load(R.drawable.heart_empty)
        )
    }

    // ── Initial bitmap — white fill, black outline ────────────────────────────

    private fun makeInitialBitmap(letter: String, sizePx: Int): Bitmap {
        // Render at 3x then scale down for crisp edges
        val upscale    = 3
        val canvasSize = sizePx * upscale
        val bmp        = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
        val c          = Canvas(bmp)
        val ts         = canvasSize * 0.70f   // text size — renamed to avoid Paint.textSize clash
        val strokeW    = canvasSize * 0.15f

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color       = Color.BLACK
            textSize    = ts
            typeface    = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign   = Paint.Align.CENTER
            style       = Paint.Style.STROKE
            strokeWidth = strokeW
            strokeJoin  = Paint.Join.ROUND
            strokeCap   = Paint.Cap.ROUND
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = Color.WHITE
            textSize  = ts
            typeface  = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            style     = Paint.Style.FILL
        }

        val x = canvasSize / 2f
        val y = canvasSize / 2f - (strokePaint.ascent() + strokePaint.descent()) / 2f
        c.drawText(letter, x, y, strokePaint)
        c.drawText(letter, x, y, fillPaint)

        return Bitmap.createScaledBitmap(bmp, sizePx, sizePx, true).also { bmp.recycle() }
    }

    // ── Flutter double decoder ────────────────────────────────────────────────
    // Flutter stores doubles with a base64 prefix; strip non-numeric prefix and parse.

    private fun parseFlutterDouble(raw: String?, fallback: Double): Double {
        if (raw == null) return fallback
        return try {
            val numeric = raw.dropWhile { !it.isDigit() && it != '-' }
            numeric.toDouble()
        } catch (e: Exception) {
            Log.w(TAG, "parseFlutterDouble failed for '$raw', using $fallback")
            fallback
        }
    }

    // ── Heart logic ───────────────────────────────────────────────────────────

    private fun getHeartStates(level: Int): List<HeartState> {
        val units        = level % 10
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
        } catch (e: Exception) { Log.e(TAG, "pushBattery: ${e.message}") }
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
        } catch (e: Exception) { Log.e(TAG, "fetchPartner: ${e.message}"); null }
    }
}