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
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AbsoluteLayout
import androidx.core.app.NotificationCompat
import kotlin.math.max

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
        val view: CompassLabelView
    )

    private val labels = mutableListOf<Label>()
    private var wm: WindowManager? = null
    private var container: AbsoluteLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var startContainerX = 0
    private var startContainerY = 0
    private var startChildX = 0
    private var startChildY = 0

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

        val c = AbsoluteLayout(this)
        c.setClipChildren(false)
        c.setClipToPadding(false)
        container = c

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val p = WindowManager.LayoutParams(
            1, 1, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = 0
        p.y = 0
        params = p

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

        try {
            wm.addView(c, p)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        finalizeBounds()
    }

    private fun addLabel(dir: String, text: String) {
        val v = CompassLabelView(this)
        v.text = text
        v.applyStyle()
        v.listener = object : CompassLabelView.Listener {
            override fun onDragStart() {
                val p = params ?: return
                val lv = v.layoutParams as AbsoluteLayout.LayoutParams
                startContainerX = p.x
                startContainerY = p.y
                startChildX = lv.x
                startChildY = lv.y
            }

            override fun onDrag(dx: Int, dy: Int) {
                val p = params ?: return
                val w = wm ?: return
                val c = container ?: return
                if (Prefs.groupMove) {
                    p.x = startContainerX + dx
                    p.y = startContainerY + dy
                    try {
                        w.updateViewLayout(c, p)
                    } catch (_: Exception) {
                    }
                    return
                }
                dragSingle(v, p, dx, dy)
            }

            override fun onDragEnd() {
                val p = params ?: return
                labels.forEach { l ->
                    val lv = l.view.layoutParams as AbsoluteLayout.LayoutParams
                    Prefs.setLabelPos(l.dir, p.x + lv.x, p.y + lv.y)
                }
            }

            override fun onClick() {
                val i = Intent(this@OverlayService, MainActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
            }
        }

        val screenX = Prefs.labelX(dir)
        val screenY = Prefs.labelY(dir)
        val (dx, dy) = if (screenX >= 0 && screenY >= 0) {
            screenX to screenY
        } else {
            defaultCrossPos(dir).also { (ix, iy) -> Prefs.setLabelPos(dir, ix, iy) }
        }
        val lp = AbsoluteLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dx,
            dy
        )
        v.layoutParams = lp
        container?.addView(v)
        labels.add(Label(dir, v))
    }

    private fun dragSingle(v: CompassLabelView, p: WindowManager.LayoutParams, dx: Int, dy: Int) {
        val w = wm ?: return
        val c = container ?: return
        var newX = startChildX + dx
        var newY = startChildY + dy
        var shiftX = 0
        var shiftY = 0
        if (newX < 0) {
            shiftX = -newX
            newX = 0
        }
        if (newY < 0) {
            shiftY = -newY
            newY = 0
        }
        if (shiftX != 0 || shiftY != 0) {
            p.x -= shiftX
            p.y -= shiftY
            labels.forEach { l ->
                val lv = l.view.layoutParams as AbsoluteLayout.LayoutParams
                lv.x += shiftX
                lv.y += shiftY
            }
            startChildX += shiftX
            startChildY += shiftY
            newX = startChildX + dx
            newY = startChildY + dy
        }
        val lpv = v.layoutParams as AbsoluteLayout.LayoutParams
        lpv.x = newX
        lpv.y = newY
        v.requestLayout()

        var maxR = 0
        var maxB = 0
        labels.forEach { l ->
            val lv = l.view.layoutParams as AbsoluteLayout.LayoutParams
            maxR = max(maxR, lv.x + l.view.width)
            maxB = max(maxB, lv.y + l.view.height)
        }
        val needResize = shiftX != 0 || shiftY != 0 || maxR > p.width || maxB > p.height
        if (needResize) {
            p.width = max(p.width, maxR)
            p.height = max(p.height, maxB)
            try {
                w.updateViewLayout(c, p)
            } catch (_: Exception) {
            }
        }
    }

    private fun finalizeBounds() {
        val p = params ?: return
        val w = wm ?: return
        val c = container ?: return
        if (labels.isEmpty()) return
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        labels.forEach { l ->
            val lv = l.view.layoutParams as AbsoluteLayout.LayoutParams
            val right = lv.x + l.view.width
            val bottom = lv.y + l.view.height
            if (lv.x < minX) minX = lv.x
            if (lv.y < minY) minY = lv.y
            if (right > maxX) maxX = right
            if (bottom > maxY) maxY = bottom
        }
        p.x += minX
        p.y += minY
        labels.forEach { l ->
            val lv = l.view.layoutParams as AbsoluteLayout.LayoutParams
            lv.x -= minX
            lv.y -= minY
        }
        container?.requestLayout()
        p.width = maxX - minX
        p.height = maxY - minY
        try {
            w.updateViewLayout(c, p)
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
        container?.let { c ->
            try {
                w.removeView(c)
            } catch (_: Exception) {
            }
        }
        container = null
        labels.clear()
        showOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance = null
        wm?.let { w ->
            container?.let { c ->
                try {
                    w.removeView(c)
                } catch (_: Exception) {
                }
            }
        }
        container = null
        labels.clear()
        super.onDestroy()
    }
}
