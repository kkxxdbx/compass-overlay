package com.compassoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    companion object {
        @Volatile
        var instance: OverlayService? = null
            private set

        fun isRunning(): Boolean = instance != null

        fun refresh() {
            instance?.rebuildAll()
        }

        fun arrangeCross() {
            instance?.arrangeCross()
        }

        fun arrangeEight() {
            instance?.arrangeEight()
        }

        fun reArrange() {
            instance?.applyArrange()
        }
    }

    private data class Label(
        val dir: String,
        val view: CompassLabelView,
        val params: WindowManager.LayoutParams,
        var startX: Int = 0,
        var startY: Int = 0
    )

    private val labels = mutableListOf<Label>()
    private var wm: WindowManager? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundWithNotification()
        showOverlay()
    }

    private fun startForegroundWithNotification() {
        val channelId = "compass_overlay"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "方向罗盘悬浮窗", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("方向罗盘已开启")
            .setContentText("点击打开设置，北南西东可单独拖动")
            .setSmallIcon(R.drawable.ic_stat_compass)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        val wm = getSystemService(WindowManager::class.java)
        this.wm = wm

        val dirs = listOf(
            Prefs.DIR_NORTH to "北",
            Prefs.DIR_SOUTH to "南",
            Prefs.DIR_WEST to "西",
            Prefs.DIR_EAST to "东",
            Prefs.DIR_NORTHEAST to "东北",
            Prefs.DIR_SOUTHEAST to "东南",
            Prefs.DIR_NORTHWEST to "西北",
            Prefs.DIR_SOUTHWEST to "西南"
        )
        for ((dir, text) in dirs) {
            if (!Prefs.showDir(dir)) continue
            addLabel(dir, text)
        }
    }

    private fun addLabel(dir: String, text: String) {
        val v = CompassLabelView(this)
        v.text = text
        v.applyStyle()
        v.listener = object : CompassLabelView.Listener {
            override fun onDragStart() {
                labels.forEach {
                    it.startX = it.params.x
                    it.startY = it.params.y
                }
            }

            override fun onDrag(dx: Int, dy: Int) {
                val targets = if (Prefs.groupMove) labels else labels.filter { it.dir == dir }
                targets.forEach {
                    it.params.x = it.startX + dx
                    it.params.y = it.startY + dy
                    try {
                        wm?.updateViewLayout(it.view, it.params)
                    } catch (_: Exception) {
                    }
                }
            }

            override fun onDragEnd() {
                labels.forEach {
                    Prefs.setLabelPos(it.dir, it.params.x, it.params.y)
                }
            }

            override fun onClick() {
                val i = Intent(this@OverlayService, MainActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
            }
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START

        val x = Prefs.labelX(dir)
        val y = Prefs.labelY(dir)
        if (x >= 0 && y >= 0) {
            p.x = x
            p.y = y
        } else {
            val (dx, dy) = defaultCrossPos(dir)
            p.x = dx
            p.y = dy
            Prefs.setLabelPos(dir, dx, dy)
        }

        labels.add(Label(dir, v, p))
        try {
            wm?.addView(v, p)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun defaultCrossPos(dir: String): Pair<Int, Int> {
        val dm = resources.displayMetrics
        val gap = (Prefs.spacingDp * dm.density).toInt()
        val diag = (gap / 1.414).toInt()
        val labelSize = (Prefs.textSizeSp * dm.density * 1.5f).toInt()
        val cx = ((dm.widthPixels - labelSize) / 2).toInt()
        val cy = ((dm.heightPixels - labelSize) / 2).toInt()
        return when (dir) {
            Prefs.DIR_NORTH -> cx to (cy - gap)
            Prefs.DIR_SOUTH -> cx to (cy + gap)
            Prefs.DIR_WEST -> (cx - gap) to cy
            Prefs.DIR_EAST -> (cx + gap) to cy
            Prefs.DIR_NORTHEAST -> (cx + diag) to (cy - diag)
            Prefs.DIR_SOUTHEAST -> (cx + diag) to (cy + diag)
            Prefs.DIR_NORTHWEST -> (cx - diag) to (cy - diag)
            Prefs.DIR_SOUTHWEST -> (cx - diag) to (cy + diag)
            else -> cx to cy
        }
    }

    fun arrangeCross() {
        Prefs.lastArrange = Prefs.ARRANGE_CROSS
        val dm = resources.displayMetrics
        val gap = (Prefs.spacingDp * dm.density).toInt()
        val labelSize = (Prefs.textSizeSp * dm.density * 1.5f).toInt()
        val cx = ((dm.widthPixels - labelSize) / 2).toInt()
        val cy = ((dm.heightPixels - labelSize) / 2).toInt()
        Prefs.setLabelPos(Prefs.DIR_NORTH, cx, cy - gap)
        Prefs.setLabelPos(Prefs.DIR_SOUTH, cx, cy + gap)
        Prefs.setLabelPos(Prefs.DIR_WEST, cx - gap, cy)
        Prefs.setLabelPos(Prefs.DIR_EAST, cx + gap, cy)
        rebuildAll()
    }

    fun arrangeEight() {
        Prefs.lastArrange = Prefs.ARRANGE_EIGHT
        val dm = resources.displayMetrics
        val gap = (Prefs.spacingDp * dm.density).toInt()
        val diag = (gap / 1.414).toInt()
        val labelSize = (Prefs.textSizeSp * dm.density * 1.5f).toInt()
        val cx = ((dm.widthPixels - labelSize) / 2).toInt()
        val cy = ((dm.heightPixels - labelSize) / 2).toInt()
        Prefs.setLabelPos(Prefs.DIR_NORTH, cx, cy - gap)
        Prefs.setLabelPos(Prefs.DIR_SOUTH, cx, cy + gap)
        Prefs.setLabelPos(Prefs.DIR_WEST, cx - gap, cy)
        Prefs.setLabelPos(Prefs.DIR_EAST, cx + gap, cy)
        Prefs.setLabelPos(Prefs.DIR_NORTHEAST, cx + diag, cy - diag)
        Prefs.setLabelPos(Prefs.DIR_SOUTHEAST, cx + diag, cy + diag)
        Prefs.setLabelPos(Prefs.DIR_NORTHWEST, cx - diag, cy - diag)
        Prefs.setLabelPos(Prefs.DIR_SOUTHWEST, cx - diag, cy + diag)
        rebuildAll()
    }

    fun applyArrange() {
        when (Prefs.lastArrange) {
            Prefs.ARRANGE_CROSS -> arrangeCross()
            else -> arrangeEight()
        }
    }

    private fun rebuildAll() {
        val w = wm ?: return
        labels.forEach {
            try {
                w.removeView(it.view)
            } catch (_: Exception) {
            }
        }
        labels.clear()
        showOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance = null
        wm?.let { w ->
            labels.forEach {
                try {
                    w.removeView(it.view)
                } catch (_: Exception) {
                }
            }
        }
        labels.clear()
        super.onDestroy()
    }
}
