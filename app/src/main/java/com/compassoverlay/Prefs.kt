package com.compassoverlay

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
    }

    fun isTablet(context: Context): Boolean =
        (context.resources.configuration.smallestScreenWidthDp ?: 0) >= 600

    private var isDefaultsApplied: Boolean
        get() = sp.getBoolean(KEY_DEFAULTS_APPLIED, false)
        set(v) = sp.edit().putBoolean(KEY_DEFAULTS_APPLIED, v).apply()

    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

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

    fun labelX(dir: String): Int = sp.getInt("${dir}_x", -1)

    fun labelY(dir: String): Int = sp.getInt("${dir}_y", -1)

    fun setLabelPos(dir: String, x: Int, y: Int) {
        sp.edit().putInt("${dir}_x", x).putInt("${dir}_y", y).apply()
    }

    var showNorth: Boolean
        get() = sp.getBoolean("show_north", true)
        set(v) = sp.edit().putBoolean("show_north", v).apply()

    var showSouth: Boolean
        get() = sp.getBoolean("show_south", true)
        set(v) = sp.edit().putBoolean("show_south", v).apply()

    var showWest: Boolean
        get() = sp.getBoolean("show_west", true)
        set(v) = sp.edit().putBoolean("show_west", v).apply()

    var showEast: Boolean
        get() = sp.getBoolean("show_east", true)
        set(v) = sp.edit().putBoolean("show_east", v).apply()

    var showNortheast: Boolean
        get() = sp.getBoolean("show_northeast", true)
        set(v) = sp.edit().putBoolean("show_northeast", v).apply()

    var showSoutheast: Boolean
        get() = sp.getBoolean("show_southeast", true)
        set(v) = sp.edit().putBoolean("show_southeast", v).apply()

    var showNorthwest: Boolean
        get() = sp.getBoolean("show_northwest", true)
        set(v) = sp.edit().putBoolean("show_northwest", v).apply()

    var showSouthwest: Boolean
        get() = sp.getBoolean("show_southwest", true)
        set(v) = sp.edit().putBoolean("show_southwest", v).apply()

    fun showDir(dir: String): Boolean = when (dir) {
        DIR_NORTH -> showNorth
        DIR_SOUTH -> showSouth
        DIR_WEST -> showWest
        DIR_EAST -> showEast
        DIR_NORTHEAST -> showNortheast
        DIR_SOUTHEAST -> showSoutheast
        DIR_NORTHWEST -> showNorthwest
        DIR_SOUTHWEST -> showSouthwest
        else -> true
    }

    fun setShowDir(dir: String, show: Boolean) {
        when (dir) {
            DIR_NORTH -> showNorth = show
            DIR_SOUTH -> showSouth = show
            DIR_WEST -> showWest = show
            DIR_EAST -> showEast = show
            DIR_NORTHEAST -> showNortheast = show
            DIR_SOUTHEAST -> showSoutheast = show
            DIR_NORTHWEST -> showNorthwest = show
            DIR_SOUTHWEST -> showSouthwest = show
        }
    }
}
