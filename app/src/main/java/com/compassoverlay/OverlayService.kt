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
import android.view.Choreographer
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
        var startAllX: Int = 0,
        var startAllY: Int = 0
    )

    private val labels = mutableListOf<Label>()
    private var wm: WindowManager? = null
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private var currentDx = 0
    private var currentDy = 0
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!dragging) return
            applyFrame()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

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

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

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
            addLabel(wm, type, dir, text)
        }
    }

    private fun addLabel(wm: WindowManager, type: Int, dir: String, text: String) {
        val v = CompassLabelView(this)
        v.text = text
        v.applyStyle()
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
        val screenX = Prefs.labelX(dir)
        val screenY = Prefs.labelY(dir)
        if (screenX >= 0 && screenY >= 0) {
            p.x = screenX
            p.y = screenY
        } else {
            val (ix, iy) = defaultCrossPos(dir)
            p.x = ix
            p.y = iy
            Prefs.setLabelPos(dir, ix, iy)
        }

        val label = Label(dir, v, p)
        v.listener = object : CompassLabelView.Listener {
            override fun onDragStart() {
                startX = label.params.x
                startY = label.params.y
                if (Prefs.groupMove) {
                    labels.forEach { l ->
                        l.startAllX = l.params.x
                        l.startAllY = l.params.y
                    }
                    dragging = true
                    currentDx = 0
                    currentDy = 0
                    Choreographer.getInstance().postFrameCallback(frameCallback)
                }
            }

            override fun onDrag(dx: Int, dy: Int) {
                currentDx = dx
                currentDy = dy
                if (!Prefs.groupMove) {
                    label.params.x = startX + dx
                    label.params.y = startY + dy
                    try {
                        wm?.updateViewLayout(label.view, label.params)
                    } catch (_: Exception) {
                    }
                }
            }

            override fun onDragEnd() {
                if (Prefs.groupMove) {
                    dragging = false
                    applyFrame()
                }
                labels.forEach { l ->
                    Prefs.setLabelPos(l.dir, l.params.x, l.params.y)
                }
            }

            override fun onClick() {
                val i = Intent(this@OverlayService, MainActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
            }
        }

        labels.add(label)
        try {
            wm.addView(label.view, label.params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyFrame() {
        val w = wm ?: return
        labels.forEach { l ->
            l.params.x = l.startAllX + currentDx
            l.params.y = l.startAllY + currentDy
            try {
                w.updateViewLayout(l.view, l.params)
            } catch (_: Exception) {
            }
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
        labels.forEach { l ->
            try {
                w.removeView(l.view)
            } catch (_: Exception) {
            }
        }
        labels.clear()
        showOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance = null
        val w = wm
        labels.forEach { l ->
            try {
                w?.removeView(l.view)
            } catch (_: Exception) {
            }
        }
        labels.clear()
        super.onDestroy()
    }
}
