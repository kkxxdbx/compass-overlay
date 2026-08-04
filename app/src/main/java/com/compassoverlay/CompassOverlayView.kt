package com.compassoverlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.graphics.Typeface
import android.widget.TextView

class CompassLabelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextView(context, attrs) {

    companion object {
        private var sharedBg: GradientDrawable? = null
    }

    interface Listener {
        fun onDragStart()
        fun onDrag(dx: Int, dy: Int)
        fun onDragEnd()
        fun onClick()
    }

    var listener: Listener? = null

    private var downRawX = 0f
    private var downRawY = 0f
    private var moved = false

    private val touchSlop = 8f

    init {
        gravity = Gravity.CENTER
        applyStyle()
    }

    fun applyStyle() {
        textSize = Prefs.textSizeSp.toFloat()
        setTextColor(Prefs.textColor)
        setTypeface(null, if (Prefs.bold) Typeface.BOLD else Typeface.NORMAL)
        if (Prefs.bgStyle == Prefs.BG_DARK) {
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
                if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                    moved = true
                }
                if (moved) {
                    listener?.onDrag(dx.toInt(), dy.toInt())
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
