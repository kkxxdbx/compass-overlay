package com.DirectionalCompass

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.navigationrail.NavigationRailView

class MainActivity : AppCompatActivity() {

    private val colorPresets = intArrayOf(
        Color.WHITE, 0xFFFFEB3B.toInt(), 0xFF00E5FF.toInt(),
        0xFF76FF03.toInt(), 0xFFFF5252.toInt(), 0xFF9E9E9E.toInt(), Color.BLACK
    )

    private val sizeMin: Int
        get() = if (Prefs.isTablet(this)) 12 else 10

    private var syncingToggle = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        // 首次启动未完成引导时，先进入新手引导再回主界面
        if (!Prefs.onboarded) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_main)

        val switchEnabled = findViewById<MaterialSwitch>(R.id.switchEnabled)
        val switchNorth = findViewById<MaterialSwitch>(R.id.switchNorth)
        val switchSouth = findViewById<MaterialSwitch>(R.id.switchSouth)
        val switchWest = findViewById<MaterialSwitch>(R.id.switchWest)
        val switchEast = findViewById<MaterialSwitch>(R.id.switchEast)
        val switchNE = findViewById<MaterialSwitch>(R.id.switchNE)
        val switchSE = findViewById<MaterialSwitch>(R.id.switchSE)
        val switchNW = findViewById<MaterialSwitch>(R.id.switchNW)
        val switchSW = findViewById<MaterialSwitch>(R.id.switchSW)
        val switchBold = findViewById<MaterialSwitch>(R.id.switchBold)
        val switchGroupMove = findViewById<MaterialSwitch>(R.id.switchGroupMove)
        val radioBg = findViewById<RadioGroup>(R.id.radioBg)
        val seekSize = findViewById<SeekBar>(R.id.seekSize)
        val seekBgAlpha = findViewById<SeekBar>(R.id.seekBgAlpha)
        val seekSpacing = findViewById<SeekBar>(R.id.seekSpacing)
        val txtSpacing = findViewById<TextView>(R.id.txtSpacing)
        val toggleArrange = findViewById<MaterialButtonToggleGroup>(R.id.toggleArrange)

        switchEnabled.setOnCheckedChangeListener { _, checked ->
            if (syncingToggle) return@setOnCheckedChangeListener
            if (checked) {
                enableOverlay()
            } else {
                stopService(Intent(this, OverlayService::class.java))
            }
        }

        switchNorth.setOnCheckedChangeListener { _, c ->
            if (syncingToggle) return@setOnCheckedChangeListener
            onDirToggled(Prefs.DIR_NORTH, c)
        }
        switchSouth.setOnCheckedChangeListener { _, c ->
            if (syncingToggle) return@setOnCheckedChangeListener
            onDirToggled(Prefs.DIR_SOUTH, c)
        }
        switchWest.setOnCheckedChangeListener { _, c ->
            if (syncingToggle) return@setOnCheckedChangeListener
            onDirToggled(Prefs.DIR_WEST, c)
        }
        switchEast.setOnCheckedChangeListener { _, c ->
            if (syncingToggle) return@setOnCheckedChangeListener
            onDirToggled(Prefs.DIR_EAST, c)
        }
        switchNE.setOnCheckedChangeListener { _, c ->
            if (syncingToggle) return@setOnCheckedChangeListener
            onDirToggled(Prefs.DIR_NORTHEAST, c)
        }
        switchSE.setOnCheckedChangeListener { _, c ->
            if (syncingToggle) return@setOnCheckedChangeListener
            onDirToggled(Prefs.DIR_SOUTHEAST, c)
        }
        switchNW.setOnCheckedChangeListener { _, c ->
            if (syncingToggle) return@setOnCheckedChangeListener
            onDirToggled(Prefs.DIR_NORTHWEST, c)
        }
        switchSW.setOnCheckedChangeListener { _, c ->
            if (syncingToggle) return@setOnCheckedChangeListener
            onDirToggled(Prefs.DIR_SOUTHWEST, c)
        }

        switchBold.setOnCheckedChangeListener { _, checked ->
            if (syncingToggle) return@setOnCheckedChangeListener
            Prefs.bold = checked
            applyAndRefresh()
        }

        switchGroupMove.setOnCheckedChangeListener { _, checked ->
            if (syncingToggle) return@setOnCheckedChangeListener
            Prefs.groupMove = checked
        }

        radioBg.setOnCheckedChangeListener { _, id ->
            if (syncingToggle) return@setOnCheckedChangeListener
            Prefs.bgStyle = if (id == R.id.radioBgNone) Prefs.BG_NONE else Prefs.BG_DARK
            seekBgAlpha.isEnabled = Prefs.bgStyle == Prefs.BG_DARK
            applyAndRefresh()
        }

        seekSize.min = sizeMin
        seekSize.max = 40
        seekSize.progress = Prefs.textSizeSp
        val txtSize = findViewById<TextView>(R.id.txtSize)
        txtSize.text = getString(R.string.setting_size_value, Prefs.textSizeSp)
        seekSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    Prefs.textSizeSp = progress
                    txtSize.text = getString(R.string.setting_size_value, progress)
                    // 拖动中只刷新样式与窗口尺寸，不重建窗口，避免闪烁
                    OverlayService.refreshStyle()
                }
            }

            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })

        seekBgAlpha.progress = Prefs.bgAlpha
        val txtAlpha = findViewById<TextView>(R.id.txtAlpha)
        txtAlpha.text = getString(R.string.setting_bg_alpha_value, Prefs.bgAlpha)
        seekBgAlpha.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    Prefs.bgAlpha = progress
                    txtAlpha.text = getString(R.string.setting_bg_alpha_value, progress)
                    OverlayService.refreshStyle()
                }
            }

            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })

        seekSpacing.min = 40
        seekSpacing.max = 180
        seekSpacing.progress = Prefs.spacingDp
        txtSpacing.text = getString(R.string.spacing_value, Prefs.spacingDp)
        var spacingStart = Prefs.spacingDp
        seekSpacing.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                val v = progress
                txtSpacing.text = getString(R.string.spacing_value, v)
                if (fromUser) {
                    Prefs.spacingDp = v
                }
            }

            override fun onStartTrackingTouch(bar: SeekBar) {
                spacingStart = Prefs.spacingDp
            }

            override fun onStopTrackingTouch(bar: SeekBar) {
                val cur = Prefs.spacingDp
                if (cur != spacingStart && OverlayService.isRunning()) {
                    OverlayService.scaleSpacing(spacingStart, cur)
                }
            }
        })

        toggleArrange.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || syncingToggle) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.btnArrangeCross -> if (OverlayService.isRunning()) {
                    // 服务运行时会重置所有位置，先确认避免误触覆盖手动布局
                    confirmRearrange(Prefs.ARRANGE_CROSS)
                } else {
                    Prefs.lastArrange = Prefs.ARRANGE_CROSS
                    setDiagonalsVisible(false)
                    syncUi()
                }
                R.id.btnArrangeEight -> if (OverlayService.isRunning()) {
                    confirmRearrange(Prefs.ARRANGE_EIGHT)
                } else {
                    Prefs.lastArrange = Prefs.ARRANGE_EIGHT
                    setDiagonalsVisible(true)
                    syncUi()
                }
            }
        }

        buildColorRow()
        findViewById<TextView>(R.id.txtVersion).text =
            getString(R.string.about_version, BuildConfig.VERSION_NAME)
        bindQuickArrange()
        bindGameRow()
        syncUi()
        updateStatus()
        val rail = findViewById<NavigationRailView>(R.id.rail)
        rail.setOnItemSelectedListener { item ->
            switchPage(item.itemId)
            true
        }
        rail.selectedItemId = R.id.nav_overview
        UpdateChecker.check(this)
        promptBatteryOptimization()
    }

    /** 悬浮窗页底部的快速排列按钮 */
    private fun bindQuickArrange() {
        findViewById<MaterialButton>(R.id.btnQuickCross).setOnClickListener {
            if (OverlayService.isRunning()) {
                confirmRearrange(Prefs.ARRANGE_CROSS)
            } else {
                Prefs.lastArrange = Prefs.ARRANGE_CROSS
                setDiagonalsVisible(false)
                syncUi()
            }
        }
        findViewById<MaterialButton>(R.id.btnQuickEight).setOnClickListener {
            if (OverlayService.isRunning()) {
                confirmRearrange(Prefs.ARRANGE_EIGHT)
            } else {
                Prefs.lastArrange = Prefs.ARRANGE_EIGHT
                setDiagonalsVisible(true)
                syncUi()
            }
        }
    }

    /**
     * 悬浮窗页底部的游戏快捷启动行。
     * 每款游戏一个图标，动态解析已安装包名并显示系统图标，
     * 点击直接启动游戏；未安装则显示占位并提示。
     */
    private fun bindGameRow() {
        val row = findViewById<LinearLayout>(R.id.gameRow)
        val names = mapOf(
            "genshin" to R.string.game_genshin,
            "wuthering" to R.string.game_wuthering,
            "starrail" to R.string.game_starrail,
            "neverness" to R.string.game_neverness,
            "zzz" to R.string.game_zzz
        )
        val placeholders = mapOf(
            "genshin" to R.drawable.game_genshin,
            "wuthering" to R.drawable.game_wuthering,
            "starrail" to R.drawable.game_starrail,
            "neverness" to R.drawable.game_neverness,
            "zzz" to R.drawable.game_zzz
        )
        val iconSize = dp(44)
        val textSp = 11f
        GameLauncher.GAMES.forEach { game ->
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            val w = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
            w.weight = 1f
            w.topMargin = dp(2)
            cell.layoutParams = w

            val icon = ImageView(this)
            icon.setBackgroundResource(R.drawable.game_bg)
            icon.scaleType = ImageView.ScaleType.CENTER_CROP
            icon.clipToOutline = true
            val pkg = GameLauncher.resolvePackage(this, game)
            val installed = pkg != null
            if (installed) {
                icon.setImageDrawable(GameLauncher.iconOf(this, pkg!!))
            } else {
                icon.setImageResource(placeholders[game.name] ?: R.drawable.ic_compass)
            }
            icon.layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            cell.addView(icon)

            val label = TextView(this)
            label.text = getString(names[game.name] ?: R.string.game_not_installed)
            label.setTextColor(getColor(R.color.text_secondary))
            label.textSize = textSp
            label.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            cell.addView(label)

            cell.setOnClickListener { GameLauncher.launch(this, game) }
            row.addView(cell)
        }
    }

    /** 左侧导航切换对应页面 */
    private fun switchPage(itemId: Int) {        findViewById<View>(R.id.pageOverview).visibility =
            if (itemId == R.id.nav_overview) View.VISIBLE else View.GONE
        findViewById<View>(R.id.pageStyle).visibility =
            if (itemId == R.id.nav_style) View.VISIBLE else View.GONE
        findViewById<View>(R.id.pageArrange).visibility =
            if (itemId == R.id.nav_arrange) View.VISIBLE else View.GONE
        findViewById<View>(R.id.pageTutorial).visibility =
            if (itemId == R.id.nav_tutorial) View.VISIBLE else View.GONE
        findViewById<View>(R.id.pageAbout).visibility =
            if (itemId == R.id.nav_about) View.VISIBLE else View.GONE
        syncUi()
        updateStatus()
    }

    /** 仅设置斜角方向字的显示/隐藏（不移动任何位置） */
    private fun setDiagonalsVisible(visible: Boolean) {
        Prefs.setShowDir(Prefs.DIR_NORTHEAST, visible)
        Prefs.setShowDir(Prefs.DIR_SOUTHEAST, visible)
        Prefs.setShowDir(Prefs.DIR_NORTHWEST, visible)
        Prefs.setShowDir(Prefs.DIR_SOUTHWEST, visible)
    }

    /** 一键重排前弹出确认，防止覆盖用户手动摆放的位置 */
    private fun confirmRearrange(mode: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.rearrange_title))
            .setMessage(getString(R.string.rearrange_message))
            .setPositiveButton(getString(R.string.rearrange_confirm)) { _, _ ->
                if (mode == Prefs.ARRANGE_CROSS) OverlayService.arrangeCross()
                else OverlayService.arrangeEight()
                syncUi()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .setOnDismissListener { syncUi() }
            .show()
    }

    /**
     * 首次进入提示加入电池优化白名单（系统标准的「后台运行」权限）。
     * 未加入时悬浮窗服务容易被系统（尤其 MIUI）清理导致消失，引导用户开启。
     *
     * 本工具属于「悬浮窗常驻」类型应用，官方文档认可白名单用途；
     * 该权限仅作为引导，App 不依赖其运行。
     */
    @SuppressLint("BatteryLife")
    private fun promptBatteryOptimization() {
        if (Prefs.batteryPrompted) return
        Prefs.batteryPrompted = true
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.battery_title))
            .setMessage(getString(R.string.battery_message))
            .setPositiveButton(getString(R.string.battery_go)) { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                } catch (_: Exception) {
                }
            }
            .setNegativeButton(getString(R.string.battery_later), null)
            .show()
    }

    private fun onDirToggled(dir: String, checked: Boolean) {
        Prefs.setShowDir(dir, checked)
        if (OverlayService.isRunning()) {
            OverlayService.refresh()
        }
    }

    private fun buildColorRow() {
        val row = findViewById<LinearLayout>(R.id.colorRow)
        row.removeAllViews()
        val size = (44 * resources.displayMetrics.density).toInt()
        val margin = (8 * resources.displayMetrics.density).toInt()
        colorPresets.forEach { color ->
            val v = View(this)
            val lp = LinearLayout.LayoutParams(size, size)
            lp.rightMargin = margin
            v.layoutParams = lp
            val d = android.graphics.drawable.GradientDrawable()
            d.shape = android.graphics.drawable.GradientDrawable.OVAL
            d.setColor(color)
            if (color == Prefs.textColor) {
                d.setStroke(dp(3), getColor(R.color.accent))
            }
            v.background = d
            v.setOnClickListener {
                Prefs.textColor = color
                buildColorRow()
                applyAndRefresh()
            }
            row.addView(v)
        }
    }

    private fun syncUi() {
        val switchEnabled = findViewById<MaterialSwitch>(R.id.switchEnabled)
        val switchNorth = findViewById<MaterialSwitch>(R.id.switchNorth)
        val switchSouth = findViewById<MaterialSwitch>(R.id.switchSouth)
        val switchWest = findViewById<MaterialSwitch>(R.id.switchWest)
        val switchEast = findViewById<MaterialSwitch>(R.id.switchEast)
        val switchNE = findViewById<MaterialSwitch>(R.id.switchNE)
        val switchSE = findViewById<MaterialSwitch>(R.id.switchSE)
        val switchNW = findViewById<MaterialSwitch>(R.id.switchNW)
        val switchSW = findViewById<MaterialSwitch>(R.id.switchSW)
        val switchBold = findViewById<MaterialSwitch>(R.id.switchBold)
        val switchGroupMove = findViewById<MaterialSwitch>(R.id.switchGroupMove)
        val radioBg = findViewById<RadioGroup>(R.id.radioBg)
        val seekSize = findViewById<SeekBar>(R.id.seekSize)
        val seekBgAlpha = findViewById<SeekBar>(R.id.seekBgAlpha)
        val seekSpacing = findViewById<SeekBar>(R.id.seekSpacing)
        val txtSpacing = findViewById<TextView>(R.id.txtSpacing)
        val txtSize = findViewById<TextView>(R.id.txtSize)
        val txtAlpha = findViewById<TextView>(R.id.txtAlpha)
        val toggleArrange = findViewById<MaterialButtonToggleGroup>(R.id.toggleArrange)

        // 程序化刷新控件状态时不触发各 listener 的回调，避免重复重建窗口
        syncingToggle = true
        switchEnabled.isChecked = OverlayService.isRunning()
        switchNorth.isChecked = Prefs.showDir(Prefs.DIR_NORTH)
        switchSouth.isChecked = Prefs.showDir(Prefs.DIR_SOUTH)
        switchWest.isChecked = Prefs.showDir(Prefs.DIR_WEST)
        switchEast.isChecked = Prefs.showDir(Prefs.DIR_EAST)
        switchNE.isChecked = Prefs.showDir(Prefs.DIR_NORTHEAST)
        switchSE.isChecked = Prefs.showDir(Prefs.DIR_SOUTHEAST)
        switchNW.isChecked = Prefs.showDir(Prefs.DIR_NORTHWEST)
        switchSW.isChecked = Prefs.showDir(Prefs.DIR_SOUTHWEST)
        switchBold.isChecked = Prefs.bold
        switchGroupMove.isChecked = Prefs.groupMove
        radioBg.check(if (Prefs.bgStyle == Prefs.BG_DARK) R.id.radioBgDark else R.id.radioBgNone)
        seekSize.min = sizeMin
        seekSize.max = 40
        seekSize.progress = Prefs.textSizeSp
        seekBgAlpha.progress = Prefs.bgAlpha
        seekBgAlpha.isEnabled = Prefs.bgStyle == Prefs.BG_DARK
        seekSpacing.progress = Prefs.spacingDp
        txtSpacing.text = getString(R.string.spacing_value, Prefs.spacingDp)
        txtSize.text = getString(R.string.setting_size_value, Prefs.textSizeSp)
        txtAlpha.text = getString(R.string.setting_bg_alpha_value, Prefs.bgAlpha)
        toggleArrange.check(
            if (Prefs.lastArrange == Prefs.ARRANGE_CROSS) R.id.btnArrangeCross else R.id.btnArrangeEight
        )
        syncingToggle = false
    }

    /** 刷新顶部悬浮窗运行状态指示 */
    private fun updateStatus() {
        val running = OverlayService.isRunning()
        findViewById<TextView>(R.id.txtStatus).text =
            getString(if (running) R.string.status_running else R.string.status_stopped)
        val dot = findViewById<View>(R.id.statusDot)
        dot.backgroundTintList = ColorStateList.valueOf(
            getColor(if (running) R.color.status_on else R.color.status_off)
        )
    }

    private fun enableOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            startOverlayService()
        }
    }

    private fun startOverlayService() {
        startForegroundService(Intent(this, OverlayService::class.java))
    }

    /** 样式类设置变更：仅刷新悬浮窗样式与尺寸，不重建窗口 */
    private fun applyAndRefresh() {
        if (OverlayService.isRunning()) {
            OverlayService.refreshStyle()
        }
    }

    override fun onResume() {
        super.onResume()
        Prefs.init(this)
        syncUi()
        updateStatus()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
