package com.DirectionalCompass

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.widget.Toast

/**
 * 游戏快捷启动：维护常用游戏的候选包名与关键词列表，
 * 兼容国服 / 国际服 / 渠道服的不同包名，点击图标直接拉起游戏。
 */
object GameLauncher {

    data class Game(
        val name: String,
        /** 已知的精确包名（国服 / 国际服 / 渠道服），命中任一即视为已安装 */
        val packages: List<String>,
        /** 关键词兜底：包名包含任意关键词也视为该游戏（覆盖未知渠道包名） */
        val keywords: List<String>
    )

    /**
     * 五款游戏。包名按地区/渠道列出多个候选：
     * - 原神：国服 com.miHoYo.Yuanshen，国际 com.miHoYo.GenshinImpact
     * - 鸣潮：国服/官服 com.kurogame.mingchao，国际 com.kurogame.wuthering.waves
     * - 崩坏星穹铁道：国服 com.miHoYo.hkrpg，国际 com.Cognosphere.StarRail
     * - 异环：com.hotta.neverness 系（Perfect World 旗下，关键词 hotta/neverness）
     * - 绝区零：国服 com.miHoYo.ZenlessZoneZero / com.miHoYo.Nap，国际 com.Cognosphere.ZenlessZoneZero
     */
    val GAMES = listOf(
        Game("genshin", listOf("com.miHoYo.Yuanshen", "com.miHoYo.GenshinImpact"), listOf("genshin", "yuanshen")),
        Game("wuthering", listOf("com.kurogame.mingchao", "com.kurogame.wuthering.waves"), listOf("kurogame", "wuthering", "mingchao")),
        Game("starrail", listOf("com.miHoYo.hkrpg", "com.Cognosphere.StarRail"), listOf("hkrpg", "starrail")),
        Game("neverness", listOf("com.hottagames.nte", "com.hotta.neverness"), listOf("hottagames", "hotta", "neverness", "nte")),
        Game("zzz", listOf("com.miHoYo.ZenlessZoneZero", "com.miHoYo.Nap", "com.Cognosphere.ZenlessZoneZero"), listOf("zenlesszonezero", "zenless"))
    )

    /**
     * 解析某款游戏在当前设备上实际安装的包名。
     * 先精确匹配候选包名；未命中再用关键词遍历已安装应用兜底。
     * 返回 null 表示未安装。
     */
    fun resolvePackage(context: Context, game: Game): String? {
        val pm = context.packageManager
        // 精确候选包名
        for (pkg in game.packages) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }
        // 关键词兜底（覆盖渠道服等未知包名）
        val installed = try {
            pm.getInstalledApplications(PackageManager.MATCH_ALL).map { it.packageName }
        } catch (_: Exception) {
            return null
        }
        for (pkg in installed) {
            val lower = pkg.lowercase()
            if (game.keywords.any { lower.contains(it) }) {
                return pkg
            }
        }
        return null
    }

    /** 获取游戏图标；未安装时返回 null */
    fun iconOf(context: Context, pkg: String): Drawable? = try {
        context.packageManager.getApplicationIcon(pkg)
    } catch (_: Exception) {
        null
    }

    /** 启动游戏；未安装时提示 */
    fun launch(context: Context, game: Game) {
        val pkg = resolvePackage(context, game) ?: run {
            Toast.makeText(context, context.getString(R.string.game_not_installed), Toast.LENGTH_SHORT).show()
            return
        }
        val pm = context.packageManager
        val launch = pm.getLaunchIntentForPackage(pkg)
        if (launch == null) {
            Toast.makeText(context, context.getString(R.string.game_not_installed), Toast.LENGTH_SHORT).show()
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(launch)
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.game_not_installed), Toast.LENGTH_SHORT).show()
        }
    }
}
