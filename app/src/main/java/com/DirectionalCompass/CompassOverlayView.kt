package com.DirectionalCompass

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.graphics.Typeface
import androidx.appcompat.widget.AppCompatTextView

/**
 * 单个方向字的悬浮窗视图（TextView 子类）。
 *
 * 负责：
 * - 样式渲染：字号 / 颜色 / 加粗 / 深色圆角背景
 * - 手势识别：按下后位移超过 touchSlop 判定为拖拽，否则视为点击。
 *   拖动过程把「相对按下点的位移」上报给监听者，由 OverlayService
 *   负责帧合并更新窗口位置。
 *
 * 共享背景说明：8 个方向字共享同一个 GradientDrawable 实例，
 * 只需在设置变化时重设一次颜色，减少对象创建与内存占用。
 */
class CompassLabelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    companion object {
        private var sharedBg: GradientDrawable? = null
    }

    /** 手势回调接口，由 OverlayService 实现 */
    interface Listener {
        fun onDragStart()
        fun onDrag(dx: Int, dy: Int)
        fun onDragEnd()
        fun onClick()
    }

    var listener: Listener? = null

    /** 按下时的屏幕坐标，用于计算相对位移 */
    private var downRawX = 0f
    private var downRawY = 0f

    /** 是否已判定为拖拽（超过触摸阈值） */
    private var moved = false

    /** 触摸阈值：位移超过此值才算拖拽，否则按点击处理。
     *  使用系统标准阈值，适配不同屏幕密度与无障碍设置。 */
    private val touchSlop: Float = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    init {
        gravity = Gravity.CENTER
        applyStyle()
    }

    /** 从 Prefs 读取最新设置并应用样式 */
    fun applyStyle() {
        textSize = Prefs.textSizeSp.toFloat()
        setTextColor(Prefs.textColor)
        setTypeface(null, if (Prefs.bold) Typeface.BOLD else Typeface.NORMAL)
        if (Prefs.bgStyle == Prefs.BG_DARK) {
            // 8 个字共享一个背景 drawable
            var d = sharedBg
            if (d == null) {
                d = GradientDrawable()
                d.cornerRadius = dp(10).toFloat()
                sharedBg = d
            }
            d.setColor(Color.argb(Prefs.bgAlpha, 0, 0, 0))
            background = d
            val p = dp(6)
            setPadding(p, p / 2, p, p / 2)
        } else {
            background = null
            setPadding(0, 0, 0, 0)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                moved = false
                downRawX = event.rawX
                downRawY = event.rawY
                parent?.requestDisallowInterceptTouchEvent(true)
                listener?.onDragStart()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                // 超过阈值才判定为拖拽，避免误触
                if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                    moved = true
                }
                if (moved) {
                    listener?.onDrag(dx.toInt(), dy.toInt())
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 拖拽结束保存位置；未移动则视为点击（回到设置页）
                if (moved) {
                    listener?.onDragEnd()
                } else {
                    listener?.onClick()
                }
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
