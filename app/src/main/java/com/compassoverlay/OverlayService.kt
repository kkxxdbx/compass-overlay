package com.compassoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Choreographer
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlin.math.roundToInt

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

        fun scaleSpacing(oldGap: Int, newGap: Int) {
            instance?.scaleSpacingInternal(oldGap, newGap)
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
    private var activeLabel: Label? = null
    private var dragging = false
    private var dirty = false
    private var currentDx = 0
    private var currentDy = 0
    private var frameCount = 0

    private val refreshRateHz: Int by lazy {
        val dm = getSystemService(DisplayManager::class.java)
        (dm?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate ?: 60f)
            .roundToInt().coerceIn(60, 240)
    }

    private val followInterval: Int by lazy {
        kotlin.math.max(1, (refreshRateHz / 60f).roundToInt())
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!dragging) return
            if (dirty) {
                dirty = false
                val anchor = activeLabel
                if (anchor != null) updatePos(anchor, currentDx, currentDy)
                frameCount++
                if (frameCount >= followInterval) {
                    frameCount = 0
                    labels.forEach { l -> if (l !== anchor) updatePos(l, currentDx, currentDy) }
                }
            }
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
            .setContentText("点击打开设置：整体移动、十字/八方模式、调间距字号")
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
        labels.forEach { l -> l.view.post { clampToScreen() } }
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
                activeLabel = label
                if (Prefs.groupMove) {
                    labels.forEach { l ->
                        l.startAllX = l.params.x
                        l.startAllY = l.params.y
                    }
                } else {
                    label.startAllX = label.params.x
                    label.startAllY = label.params.y
                }
            }

            override fun onDrag(dx: Int, dy: Int) {
                if (dx == currentDx && dy == currentDy) return
                currentDx = dx
                currentDy = dy
                dirty = true
                if (!dragging) {
                    dragging = true
                    frameCount = 0
                    Choreographer.getInstance().postFrameCallback(frameCallback)
                }
            }

            override fun onDragEnd() {
                dragging = false
                labels.forEach { l -> updatePos(l, currentDx, currentDy) }
                activeLabel = null
                labels.forEach { l ->
                    Prefs.setLabelPos(l.dir, l.params.x, l.params.y)
                }
            }

            override fun onClick() {
                dragging = false
                activeLabel = null
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

    private fun updatePos(l: Label, dx: Int, dy: Int) {
        l.params.x = l.startAllX + dx
        l.params.y = l.startAllY + dy
        try {
            wm?.updateViewLayout(l.view, l.params)
        } catch (_: Exception) {
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
        Prefs.setShowDir(Prefs.DIR_NORTHEAST, false)
        Prefs.setShowDir(Prefs.DIR_SOUTHEAST, false)
        Prefs.setShowDir(Prefs.DIR_NORTHWEST, false)
        Prefs.setShowDir(Prefs.DIR_SOUTHWEST, false)
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
        Prefs.setShowDir(Prefs.DIR_NORTHEAST, true)
        Prefs.setShowDir(Prefs.DIR_SOUTHEAST, true)
        Prefs.setShowDir(Prefs.DIR_NORTHWEST, true)
        Prefs.setShowDir(Prefs.DIR_SOUTHWEST, true)
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

    private fun scaleSpacingInternal(oldGap: Int, newGap: Int) {
        if (oldGap <= 0 || newGap == oldGap) return
        if (labels.isEmpty()) return
        val k = newGap.toFloat() / oldGap
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        labels.forEach { l ->
            if (l.params.x < minX) minX = l.params.x
            if (l.params.y < minY) minY = l.params.y
            if (l.params.x > maxX) maxX = l.params.x
            if (l.params.y > maxY) maxY = l.params.y
        }
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f
        labels.forEach { l ->
            val nx = (centerX + (l.params.x - centerX) * k).toInt()
            val ny = (centerY + (l.params.y - centerY) * k).toInt()
            l.params.x = nx
            l.params.y = ny
            Prefs.setLabelPos(l.dir, nx, ny)
            try {
                wm?.updateViewLayout(l.view, l.params)
            } catch (_: Exception) {
            }
        }
    }

    private fun clampToScreen() {
        val dm = resources.displayMetrics
        val sw = dm.widthPixels
        val sh = dm.heightPixels
        labels.forEach { l ->
            val lw = l.view.width
            val lh = l.view.height
            var nx = l.params.x
            var ny = l.params.y
            if (nx < 0) nx = 0
            if (ny < 0) ny = 0
            if (lw > 0 && nx + lw > sw) nx = (sw - lw).coerceAtLeast(0)
            if (lh > 0 && ny + lh > sh) ny = (sh - lh).coerceAtLeast(0)
            if (nx != l.params.x || ny != l.params.y) {
                l.params.x = nx
                l.params.y = ny
                Prefs.setLabelPos(l.dir, nx, ny)
                try {
                    wm?.updateViewLayout(l.view, l.params)
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        clampToScreen()
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
        dragging = false
        activeLabel = null
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
