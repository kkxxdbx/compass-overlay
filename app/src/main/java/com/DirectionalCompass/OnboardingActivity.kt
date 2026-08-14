package com.DirectionalCompass

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import android.graphics.drawable.GradientDrawable

/**
 * 首次启动新手引导：权限引导页 → 8 步功能引导页 → 主界面。
 *
 * 冷启动时由 MainActivity 检查 Prefs.onboarded，未完成则先进入本页。
 * 权限引导页仅需引导悬浮窗权限（读取应用列表为安装时自动授予，仅展示）；
 * 全部完成后写入 Prefs.onboarded 并跳转 MainActivity。
 */
class OnboardingActivity : AppCompatActivity() {

    private data class Step(
        val titleRes: Int,
        val descRes: Int,
        val iconRes: Int
    )

    private val steps = listOf(
        Step(R.string.step_switch_title, R.string.step_switch_desc, R.drawable.ic_stat_compass),
        Step(R.string.step_arrange_title, R.string.step_arrange_desc, R.drawable.ic_nav_grid),
        Step(R.string.step_game_title, R.string.step_game_desc, R.drawable.game_genshin),
        Step(R.string.step_style_title, R.string.step_style_desc, R.drawable.ic_nav_palette),
        Step(R.string.step_color_title, R.string.step_color_desc, R.drawable.ic_nav_palette),
        Step(R.string.step_bg_title, R.string.step_bg_desc, R.drawable.ic_nav_palette),
        Step(R.string.step_spacing_title, R.string.step_spacing_desc, R.drawable.ic_nav_grid),
        Step(R.string.step_groupmove_title, R.string.step_groupmove_desc, R.drawable.ic_compass)
    )

    private var currentStep = 0
    private var syncingNext = false

    private lateinit var overlayStatus: TextView
    private lateinit var btnNextToTutorial: MaterialButton

    /** 步骤图标彩色圆底（循环使用，避免重复单调） */
    private val iconBgs = intArrayOf(
        R.drawable.chip_pink,
        R.drawable.chip_mint,
        R.drawable.chip_blue,
        R.drawable.chip_lemon,
        R.drawable.chip_lavender
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        // 已引导过：不再展示向导，直接以横屏进入主界面
        if (Prefs.onboarded) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_onboarding)

        overlayStatus = findViewById(R.id.txtOverlayStatus)
        btnNextToTutorial = findViewById(R.id.btnNextToTutorial)

        findViewById<View>(R.id.permissionOverlay).setOnClickListener { requestOverlayPermission() }
        findViewById<View>(R.id.btnGrantOverlay).setOnClickListener { requestOverlayPermission() }
        btnNextToTutorial.setOnClickListener {
            findViewById<View>(R.id.permissionPage).visibility = View.GONE
            findViewById<View>(R.id.tutorialPage).visibility = View.VISIBLE
            currentStep = 0
            renderStep()
        }

        findViewById<MaterialButton>(R.id.btnPrevStep).setOnClickListener {
            if (currentStep > 0) {
                currentStep--
                renderStep()
            }
        }
        findViewById<MaterialButton>(R.id.btnNextStep).setOnClickListener {
            if (currentStep < steps.size - 1) {
                currentStep++
                renderStep()
            } else {
                finishOnboarding()
            }
        }

        refreshPermissionStatus()
    }

    /** 跳转系统设置申请悬浮窗权限；永久拒绝时也无法在此拉起弹窗，需用户手动开启 */
    private fun requestOverlayPermission() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.onboard_perm_hint, Toast.LENGTH_SHORT).show()
        }
    }

    /** 悬浮窗权限是否已授予 */
    private fun hasOverlay(): Boolean = Settings.canDrawOverlays(this)

    /** 刷新权限状态，onResume 时调用（用户从系统设置返回时即时更新） */
    private fun refreshPermissionStatus() {
        val granted = hasOverlay()
        syncingNext = true
        val btn = findViewById<TextView>(R.id.btnGrantOverlay)
        if (granted) {
            overlayStatus.text = getString(R.string.onboard_perm_granted)
            overlayStatus.setTextColor(ContextCompat.getColor(this, R.color.status_on))
            btn.text = getString(R.string.onboard_perm_granted)
            btn.setBackgroundResource(R.drawable.btn_capsule_on)
        } else {
            overlayStatus.text = getString(R.string.onboard_perm_not_granted)
            overlayStatus.setTextColor(ContextCompat.getColor(this, R.color.status_off))
            btn.text = getString(R.string.onboard_perm_grant)
            btn.setBackgroundResource(R.drawable.btn_capsule)
        }
        btnNextToTutorial.isEnabled = granted
        syncingNext = false
    }

    /** 渲染当前功能引导步骤（带错峰淡入过渡） */
    private fun renderStep() {
        val step = steps[currentStep]
        findViewById<TextView>(R.id.txtTutorialTitle).text =
            getString(R.string.onboard_tutorial_title)
        findViewById<TextView>(R.id.txtStepCount).text =
            getString(R.string.onboard_step, currentStep + 1, steps.size)
        findViewById<ImageView>(R.id.imgStepIcon).setImageResource(step.iconRes)
        findViewById<ImageView>(R.id.imgStepIcon).setBackgroundResource(
            iconBgs[currentStep % iconBgs.size]
        )
        findViewById<TextView>(R.id.txtStepTitle).text = getString(step.titleRes)
        findViewById<TextView>(R.id.txtStepDesc).text = getString(step.descRes)

        val prev = findViewById<MaterialButton>(R.id.btnPrevStep)
        prev.isEnabled = currentStep > 0
        val next = findViewById<MaterialButton>(R.id.btnNextStep)
        next.text = getString(
            if (currentStep == steps.size - 1) R.string.onboard_finish else R.string.onboard_next
        )

        renderStepIndicator()

        // 内容错峰淡入 + 轻微上移，避免切换生硬
        val d = resources.displayMetrics.density
        val icon = findViewById<View>(R.id.imgStepIcon)
        val title = findViewById<View>(R.id.txtStepTitle)
        val desc = findViewById<View>(R.id.txtStepDesc)
        listOf(icon, title, desc).forEach { it.alpha = 0f; it.translationY = 18f * d }
        icon.animate().alpha(1f).translationY(0f).setDuration(220).start()
        title.animate().alpha(1f).translationY(0f).setDuration(220).setStartDelay(60).start()
        desc.animate().alpha(1f).translationY(0f).setDuration(220).setStartDelay(120).start()
    }

    /** 底部步骤小圆点指示 */
    private fun renderStepIndicator() {
        val container = findViewById<LinearLayout>(R.id.stepIndicator)
        container.removeAllViews()
        val d = resources.displayMetrics.density
        steps.forEachIndexed { index, _ ->
            val dot = View(this)
            val size = (8 * d).toInt()
            val margin = (5 * d).toInt()
            val lp = LinearLayout.LayoutParams(size, size)
            lp.rightMargin = margin
            dot.layoutParams = lp
            val bg = GradientDrawable()
            bg.shape = GradientDrawable.OVAL
            bg.setColor(
                ContextCompat.getColor(
                    this,
                    if (index == currentStep) R.color.accent else R.color.card_stroke
                )
            )
            dot.background = bg
            container.addView(dot)
        }
    }

    /** 引导完成：持久化标记并进入主界面（竖屏 → 横屏，系统自动播放旋转过渡） */
    private fun finishOnboarding() {
        Prefs.onboarded = true
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
    }
}
