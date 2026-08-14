package com.DirectionalCompass

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

object Prefs {
    private const val FILE = "compass_overlay"

    const val DIR_NORTH = "north"
    const val DIR_SOUTH = "south"
    const val DIR_WEST = "west"
    const val DIR_EAST = "east"
    const val DIR_NORTHEAST = "northeast"
    const val DIR_SOUTHEAST = "southeast"
    const val DIR_NORTHWEST = "northwest"
    const val DIR_SOUTHWEST = "southwest"

    const val BG_NONE = 0
    const val BG_DARK = 1

    const val ARRANGE_FREE = "free"
    const val ARRANGE_CROSS = "cross"
    const val ARRANGE_EIGHT = "eight"

    private const val KEY_DEFAULTS_APPLIED = "defaults_applied"
    private const val KEY_RESET_V16 = "reset_v16"

    lateinit var sp: SharedPreferences
        private set

    fun init(context: Context) {
        if (!::sp.isInitialized) {
            sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
        if (!isDefaultsApplied) {
            val isNewInstall = !sp.contains("spacing") && !sp.contains("text_size")
            if (isNewInstall) {
                spacingDp = 40
                textSizeSp = 12
            }
            isDefaultsApplied = true
        }
        if (!isResetV16) {
            if (isTablet(context)) {
                spacingDp = 40
                textSizeSp = 12
            }
            isResetV16 = true
        }
    }

    fun isTablet(context: Context): Boolean =
        (context.resources.configuration.smallestScreenWidthDp ?: 0) >= 600

    private var isDefaultsApplied: Boolean
        get() = sp.getBoolean(KEY_DEFAULTS_APPLIED, false)
        set(v) = sp.edit().putBoolean(KEY_DEFAULTS_APPLIED, v).apply()

    private var isResetV16: Boolean
        get() = sp.getBoolean(KEY_RESET_V16, false)
        set(v) = sp.edit().putBoolean(KEY_RESET_V16, v).apply()

    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    /** 新手引导是否已完成，完成前冷启动先进 OnboardingActivity */
    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    var textSizeSp: Int
        get() = sp.getInt("text_size", 16)
        set(v) = sp.edit().putInt("text_size", v).apply()

    var bold: Boolean
        get() = sp.getBoolean("bold", true)
        set(v) = sp.edit().putBoolean("bold", v).apply()

    var textColor: Int
        get() = sp.getInt("text_color", Color.WHITE)
        set(v) = sp.edit().putInt("text_color", v).apply()

    var bgStyle: Int
        get() = sp.getInt("bg_style", BG_DARK)
        set(v) = sp.edit().putInt("bg_style", v).apply()

    var bgAlpha: Int
        get() = sp.getInt("bg_alpha", 150)
        set(v) = sp.edit().putInt("bg_alpha", v).apply()

    var spacingDp: Int
        get() = sp.getInt("spacing", 80)
        set(v) = sp.edit().putInt("spacing", v).apply()

    var groupMove: Boolean
        get() = sp.getBoolean("group_move", false)
        set(v) = sp.edit().putBoolean("group_move", v).apply()

    var lastArrange: String
        get() = sp.getString("last_arrange", ARRANGE_EIGHT)!!
        set(v) = sp.edit().putString("last_arrange", v).apply()

    /** 是否已提示过「允许后台运行」引导（仅首次提示一次） */
    var batteryPrompted: Boolean
        get() = sp.getBoolean("battery_prompted", false)
        set(v) = sp.edit().putBoolean("battery_prompted", v).apply()

    fun labelX(dir: String): Int = sp.getInt("${dir}_x", -1)

    fun labelY(dir: String): Int = sp.getInt("${dir}_y", -1)

    fun setLabelPos(dir: String, x: Int, y: Int) {
        sp.edit().putInt("${dir}_x", x).putInt("${dir}_y", y).apply()
    }

    /** 全部 8 个方向常量，用于通用遍历 */
    val ALL_DIRS = listOf(
        DIR_NORTH, DIR_SOUTH, DIR_WEST, DIR_EAST,
        DIR_NORTHEAST, DIR_SOUTHEAST, DIR_NORTHWEST, DIR_SOUTHWEST
    )

    fun showDir(dir: String): Boolean = sp.getBoolean("show_$dir", true)

    fun setShowDir(dir: String, show: Boolean) {
        sp.edit().putBoolean("show_$dir", show).apply()
    }
}
