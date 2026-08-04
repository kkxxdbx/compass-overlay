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

/**
 * 悬浮窗前台服务：管理 8 个方向字窗口（每个方向字一个独立小窗），
 * 负责布局、拖拽帧合并、间距缩放、屏幕夹边与横竖屏适配。
 *
 * 关键设计：
 * - 每个方向字是独立的悬浮窗，而不是一个大容器 —— 空白区域不会拦截
 *   游戏触摸，也从根源上避免了文字被 Surface 裁剪的问题。
 * - 拖动采用 Choreographer 帧回调合并批量 updateViewLayout，保证跟手，
 *   避免逐事件更新造成的卡顿。
 */
class OverlayService : Service() {

    companion object {
        @Volatile
        var instance: OverlayService? = null
            private set

        fun isRunning(): Boolean = instance != null

        /** 根据最新设置重建全部窗口（设置页在 onPause 时调用） */
        fun refresh() {
            instance?.rebuildAll()
        }

        /** 切换为十字模式：只显示四正向字 */
        fun arrangeCross() {
            instance?.arrangeCross()
        }

        /** 切换为八方模式：显示全部 8 个方向字 */
        fun arrangeEight() {
            instance?.arrangeEight()
        }

        /** 按上次选择的模式重新排列 */
        fun reArrange() {
            instance?.applyArrange()
        }

        /** 间距滑块松手后，按包围盒中心等比缩放各窗口位置 */
        fun scaleSpacing(oldGap: Int, newGap: Int) {
            instance?.scaleSpacingInternal(oldGap, newGap)
        }
    }

    /**
     * 单个方向字窗口的封装。
     * [startAllX]/[startAllY] 记录拖动起始坐标，用于整体移动时还原初始位置。
     */
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

    /** 拖动期间是否有位移变化（避免重复做无效 IPC 更新） */
    private var dirty = false
    private var currentDx = 0
    private var currentDy = 0

    /** 距上一次整体刷新已走过的帧数 */
    private var frameCount = 0

    /** 屏幕刷新率（60/90/120/144Hz 等），用于自适应节流 */
    private val refreshRateHz: Int by lazy {
        val dm = getSystemService(DisplayManager::class.java)
        (dm?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate ?: 60f)
            .roundToInt().coerceIn(60, 240)
    }

    /**
     * 跟随间隔：非锚点方向字每隔多少帧同步一次位置。
     * 例如 144Hz 屏 => 间隔 2 帧（约 72Hz），锚点字仍每帧跟手，
     * 在保证跟手感的同时降低多窗口 IPC 开销。
     */
    private val followInterval: Int by lazy {
        kotlin.math.max(1, (refreshRateHz / 60f).roundToInt())
    }

    /**
     * 帧回调驱动的主循环：
     * 每帧把累积的位移（dirty）应用到窗口。锚点字每帧更新保证跟手，
     * 其余字按 followInterval 节流，整体移动时全部一起联动。
     */
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!dragging) return
            if (dirty) {
                dirty = false
                val anchor = activeLabel
                if (anchor != null) updatePos(anchor, currentDx, currentDy)
                if (Prefs.groupMove) {
                    frameCount++
                    if (frameCount >= followInterval) {
                        frameCount = 0
                        labels.forEach { l -> if (l !== anchor) updatePos(l, currentDx, currentDy) }
                    }
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
                NotificationChannel(channelId, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notify_title))
            .setContentText(getString(R.string.notify_text))
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

    /**
     * 按当前设置创建 8 个方向字窗口。
     * 隐藏的方向字不创建窗口；已有保存位置则恢复，否则放到屏幕中心十字排布。
     */
    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        val wm = getSystemService(WindowManager::class.java)
        this.wm = wm

        // Android 8.0+ 必须使用 TYPE_APPLICATION_OVERLAY
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val dirs = listOf(
            Prefs.DIR_NORTH to getString(R.string.dir_north),
            Prefs.DIR_SOUTH to getString(R.string.dir_south),
            Prefs.DIR_WEST to getString(R.string.dir_west),
            Prefs.DIR_EAST to getString(R.string.dir_east),
            Prefs.DIR_NORTHEAST to getString(R.string.dir_northeast),
            Prefs.DIR_SOUTHEAST to getString(R.string.dir_southeast),
            Prefs.DIR_NORTHWEST to getString(R.string.dir_northwest),
            Prefs.DIR_SOUTHWEST to getString(R.string.dir_southwest)
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
        // 独立小窗：WRAP_CONTENT + 不拦截触摸，空白区域直接透传给下层游戏
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
            // 首次使用该方向字：默认放到屏幕中心十字，并写入设置
            val (ix, iy) = defaultCrossPos(dir)
            p.x = ix
            p.y = iy
            Prefs.setLabelPos(dir, ix, iy)
        }

        val label = Label(dir, v, p)
        v.listener = object : CompassLabelView.Listener {
            override fun onDragStart() {
                activeLabel = label
                // 记录所有标签（或仅当前标签）的起始位置，供位移换算
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
                // 位移无变化时跳过，避免无意义的重绘
                if (dx == currentDx && dy == currentDy) return
                currentDx = dx
                currentDy = dy
                dirty = true
                // 首次移动才启动帧回调，避免空闲时持续占帧
                if (!dragging) {
                    dragging = true
                    frameCount = 0
                    Choreographer.getInstance().postFrameCallback(frameCallback)
                }
            }

            override fun onDragEnd() {
                dragging = false
                // 松手时把累积位移一次性应用到所有应联动的标签
                if (Prefs.groupMove) {
                    labels.forEach { l -> updatePos(l, currentDx, currentDy) }
                } else {
                    activeLabel?.let { l -> updatePos(l, currentDx, currentDy) }
                }
                activeLabel = null
                // 保存所有标签位置
                labels.forEach { l ->
                    Prefs.setLabelPos(l.dir, l.params.x, l.params.y)
                }
            }

            override fun onClick() {
                dragging = false
                activeLabel = null
                // 点击任意方向字回到设置页
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

    /** 把单个窗口移到 startPos + dx/dy 处 */
    private fun updatePos(l: Label, dx: Int, dy: Int) {
        l.params.x = l.startAllX + dx
        l.params.y = l.startAllY + dy
        try {
            wm?.updateViewLayout(l.view, l.params)
        } catch (_: Exception) {
        }
    }

    /**
     * 计算某个方向字在屏幕中心的默认十字坐标。
     * 正方向按间距 gap 偏移，斜对角按 gap/√2 偏移，形成正八边形。
     */
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

    /** 十字模式：隐藏四个斜对角方向字，只保留上下左右，并重排 */
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

    /**
     * 间距缩放：以当前全部标签的包围盒中心为原点，按新间距/旧间距
     * 比例等比放大或缩小各标签位置，保留用户手动拖过的相对布局。
     */
    private fun scaleSpacingInternal(oldGap: Int, newGap: Int) {
        if (oldGap <= 0 || newGap == oldGap) return
        if (labels.isEmpty()) return
        val k = newGap.toFloat() / oldGap
        // 计算包围盒
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

    /** 把越界的窗口夹回屏幕内（横竖屏切换后调用） */
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
