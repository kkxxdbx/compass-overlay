package com.compassoverlay

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch

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
            if (checked) {
                enableOverlay()
            } else {
                stopService(Intent(this, OverlayService::class.java))
            }
        }

        switchNorth.setOnCheckedChangeListener { _, c -> onDirToggled(Prefs.DIR_NORTH, c) }
        switchSouth.setOnCheckedChangeListener { _, c -> onDirToggled(Prefs.DIR_SOUTH, c) }
        switchWest.setOnCheckedChangeListener { _, c -> onDirToggled(Prefs.DIR_WEST, c) }
        switchEast.setOnCheckedChangeListener { _, c -> onDirToggled(Prefs.DIR_EAST, c) }
        switchNE.setOnCheckedChangeListener { _, c -> onDirToggled(Prefs.DIR_NORTHEAST, c) }
        switchSE.setOnCheckedChangeListener { _, c -> onDirToggled(Prefs.DIR_SOUTHEAST, c) }
        switchNW.setOnCheckedChangeListener { _, c -> onDirToggled(Prefs.DIR_NORTHWEST, c) }
        switchSW.setOnCheckedChangeListener { _, c -> onDirToggled(Prefs.DIR_SOUTHWEST, c) }

        switchBold.setOnCheckedChangeListener { _, checked ->
            Prefs.bold = checked
            applyAndRefresh()
        }

        switchGroupMove.setOnCheckedChangeListener { _, checked ->
            Prefs.groupMove = checked
        }

        radioBg.setOnCheckedChangeListener { _, id ->
            Prefs.bgStyle = if (id == R.id.radioBgNone) Prefs.BG_NONE else Prefs.BG_DARK
            seekBgAlpha.isEnabled = Prefs.bgStyle == Prefs.BG_DARK
            applyAndRefresh()
        }

        seekSize.min = sizeMin
        seekSize.max = 40
        seekSize.progress = Prefs.textSizeSp
        seekSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    Prefs.textSizeSp = progress
                    applyAndRefresh()
                }
            }

            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })

        seekBgAlpha.progress = Prefs.bgAlpha
        seekBgAlpha.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    Prefs.bgAlpha = progress
                    applyAndRefresh()
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
                    OverlayService.arrangeCross()
                } else {
                    Prefs.lastArrange = Prefs.ARRANGE_CROSS
                    Prefs.setShowDir(Prefs.DIR_NORTHEAST, false)
                    Prefs.setShowDir(Prefs.DIR_SOUTHEAST, false)
                    Prefs.setShowDir(Prefs.DIR_NORTHWEST, false)
                    Prefs.setShowDir(Prefs.DIR_SOUTHWEST, false)
                }
                R.id.btnArrangeEight -> if (OverlayService.isRunning()) {
                    OverlayService.arrangeEight()
                } else {
                    Prefs.lastArrange = Prefs.ARRANGE_EIGHT
                    Prefs.setShowDir(Prefs.DIR_NORTHEAST, true)
                    Prefs.setShowDir(Prefs.DIR_SOUTHEAST, true)
                    Prefs.setShowDir(Prefs.DIR_NORTHWEST, true)
                    Prefs.setShowDir(Prefs.DIR_SOUTHWEST, true)
                }
            }
            syncUi()
        }

        buildColorRow()
        syncUi()
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
                d.setStroke(dp(3), 0xFF2196F3.toInt())
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
        val toggleArrange = findViewById<MaterialButtonToggleGroup>(R.id.toggleArrange)

        switchEnabled.isChecked = OverlayService.isRunning()
        switchNorth.isChecked = Prefs.showNorth
        switchSouth.isChecked = Prefs.showSouth
        switchWest.isChecked = Prefs.showWest
        switchEast.isChecked = Prefs.showEast
        switchNE.isChecked = Prefs.showNortheast
        switchSE.isChecked = Prefs.showSoutheast
        switchNW.isChecked = Prefs.showNorthwest
        switchSW.isChecked = Prefs.showSouthwest
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
        syncingToggle = true
        toggleArrange.check(
            if (Prefs.lastArrange == Prefs.ARRANGE_CROSS) R.id.btnArrangeCross else R.id.btnArrangeEight
        )
        syncingToggle = false
    }

    private fun enableOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, OverlayService::class.java))
        } else {
            startService(Intent(this, OverlayService::class.java))
        }
    }

    private fun applyAndRefresh() {
        if (OverlayService.isRunning()) {
            OverlayService.refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        Prefs.init(this)
        syncUi()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
