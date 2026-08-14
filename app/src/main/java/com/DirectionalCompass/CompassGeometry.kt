package com.DirectionalCompass

/**
 * 罗盘布局几何计算的纯函数集合（不依赖 Android 运行时，便于单元测试）。
 * 坐标系与悬浮窗一致：屏幕左上角为原点，x 向右、y 向下。
 */
object CompassGeometry {

    /** 估算单个方向字标签的边长（用于居中计算） */
    fun labelSize(textSizeSp: Int, density: Float): Int =
        (textSizeSp * density * 1.5f).toInt()

    /** 计算某个方向字在屏幕中心的默认十字坐标 */
    fun defaultCrossPos(
        dir: String,
        widthPixels: Int,
        heightPixels: Int,
        gap: Int,
        diag: Int,
        labelSize: Int
    ): Pair<Int, Int> {
        val cx = (widthPixels - labelSize) / 2
        val cy = (heightPixels - labelSize) / 2
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

    /** 以包围盒中心为原点，按比例 [k] 缩放各点坐标（保留相对布局） */
    fun scaleSpacing(points: List<Pair<Int, Int>>, k: Float): List<Pair<Int, Int>> {
        if (points.isEmpty() || k <= 0f) return points
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        points.forEach { (x, y) ->
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f
        return points.map { (x, y) ->
            (centerX + (x - centerX) * k).toInt() to (centerY + (y - centerY) * k).toInt()
        }
    }
}
