package com.padmax.controller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class GamepadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    var onStateChanged: ((ControllerState) -> Unit)? = null
    var sensorFusion: SensorFusion? = null
    var hapticEnabled: Boolean = true

    private enum class Kind { BUTTON, STICK_LEFT, STICK_RIGHT, DPAD, TRIGGER_LT, TRIGGER_RT }
    private data class Control(
        val id: String,
        val label: String,
        val kind: Kind,
        val button: Long = 0,
        val rect: RectF = RectF(),
        val cx: Float = 0f,
        val cy: Float = 0f,
        val r: Float = 0f
    )

    private data class PointerInfo(var control: Control, var x: Float, var y: Float)

    private val controls = mutableListOf<Control>()
    private val pointers = SparseArray<PointerInfo>()
    private val state = ControllerState()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(48, 0, 229, 255) }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.argb(190, 0, 229, 255) }
    private val active = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(110, 0, 229, 255) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 32f; isFakeBoldText = true }
    private val smallText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220,255,255,255); textAlign = Paint.Align.CENTER; textSize = 23f }

    init {
        setBackgroundColor(Color.rgb(5, 7, 10))
        isFocusable = true
        keepScreenOn = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildDefaultLayout(w.toFloat(), h.toFloat())
    }

    private fun buildDefaultLayout(w: Float, h: Float) {
        controls.clear()
        val base = min(w, h)
        val stickR = base * 0.145f
        val btnR = base * 0.055f
        val gap = btnR * 1.55f
        fun circle(id: String, label: String, kind: Kind, button: Long, cx: Float, cy: Float, r: Float = btnR) {
            controls += Control(id, label, kind, button, RectF(cx - r, cy - r, cx + r, cy + r), cx, cy, r)
        }
        fun rect(id: String, label: String, kind: Kind, button: Long, left: Float, top: Float, right: Float, bottom: Float) {
            val rr = RectF(left, top, right, bottom)
            controls += Control(id, label, kind, button, rr, rr.centerX(), rr.centerY(), min(rr.width(), rr.height()) / 2f)
        }

        circle("ls", "L", Kind.STICK_LEFT, 0, w * 0.17f, h * 0.67f, stickR)
        circle("rs", "R", Kind.STICK_RIGHT, 0, w * 0.57f, h * 0.67f, stickR)
        circle("dpad", "+", Kind.DPAD, 0, w * 0.34f, h * 0.68f, stickR * 0.82f)

        val abx = w * 0.82f
        val aby = h * 0.59f
        circle("Y", "Y", Kind.BUTTON, Buttons.Y, abx, aby - gap)
        circle("A", "A", Kind.BUTTON, Buttons.A, abx, aby + gap)
        circle("X", "X", Kind.BUTTON, Buttons.X, abx - gap, aby)
        circle("B", "B", Kind.BUTTON, Buttons.B, abx + gap, aby)

        rect("LB", "LB", Kind.BUTTON, Buttons.LB, w * 0.05f, h * 0.06f, w * 0.25f, h * 0.18f)
        rect("RB", "RB", Kind.BUTTON, Buttons.RB, w * 0.75f, h * 0.06f, w * 0.95f, h * 0.18f)
        rect("LT", "LT", Kind.TRIGGER_LT, Buttons.LB, w * 0.05f, h * 0.20f, w * 0.25f, h * 0.33f)
        rect("RT", "RT", Kind.TRIGGER_RT, Buttons.RB, w * 0.75f, h * 0.20f, w * 0.95f, h * 0.33f)

        circle("BACK", "BACK", Kind.BUTTON, Buttons.BACK, w * 0.43f, h * 0.16f, btnR * 0.82f)
        circle("START", "START", Kind.BUTTON, Buttons.START, w * 0.52f, h * 0.16f, btnR * 0.82f)
        circle("GUIDE", "◎", Kind.BUTTON, Buttons.GUIDE, w * 0.475f, h * 0.29f, btnR * 0.86f)
        circle("L3", "L3", Kind.BUTTON, Buttons.LS, w * 0.17f, h * 0.91f, btnR * 0.72f)
        circle("R3", "R3", Kind.BUTTON, Buttons.RS, w * 0.57f, h * 0.91f, btnR * 0.72f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        controls.forEach { c -> drawControl(canvas, c, isControlActive(c)) }
        drawStatus(canvas)
    }

    private fun drawControl(canvas: Canvas, c: Control, pressed: Boolean) {
        val paint = if (pressed) active else fill
        if (c.kind == Kind.BUTTON || c.kind == Kind.STICK_LEFT || c.kind == Kind.STICK_RIGHT || c.kind == Kind.DPAD) {
            canvas.drawCircle(c.cx, c.cy, c.r, paint)
            canvas.drawCircle(c.cx, c.cy, c.r, stroke)
            if (c.kind == Kind.STICK_LEFT) drawStickThumb(canvas, c, state.lx, state.ly)
            if (c.kind == Kind.STICK_RIGHT) drawStickThumb(canvas, c, state.rx, state.ry)
            if (c.kind == Kind.DPAD) drawDpadGlyph(canvas, c)
            canvas.drawText(c.label, c.cx, c.cy + text.textSize * 0.35f, if (c.label.length > 2) smallText else text)
        } else {
            canvas.drawRoundRect(c.rect, 24f, 24f, paint)
            canvas.drawRoundRect(c.rect, 24f, 24f, stroke)
            val v = if (c.kind == Kind.TRIGGER_LT) state.lt else state.rt
            val filled = RectF(c.rect.left, c.rect.bottom - c.rect.height() * v, c.rect.right, c.rect.bottom)
            canvas.drawRoundRect(filled, 24f, 24f, active)
            canvas.drawText(c.label, c.cx, c.cy + text.textSize * 0.35f, text)
        }
    }

    private fun drawStickThumb(canvas: Canvas, c: Control, x: Float, y: Float) {
        val tx = c.cx + x * c.r * 0.55f
        val ty = c.cy - y * c.r * 0.55f
        canvas.drawCircle(tx, ty, c.r * 0.28f, active)
        canvas.drawCircle(tx, ty, c.r * 0.28f, stroke)
    }

    private fun drawDpadGlyph(canvas: Canvas, c: Control) {
        canvas.drawLine(c.cx - c.r * 0.55f, c.cy, c.cx + c.r * 0.55f, c.cy, stroke)
        canvas.drawLine(c.cx, c.cy - c.r * 0.55f, c.cx, c.cy + c.r * 0.55f, stroke)
    }

    private fun drawStatus(canvas: Canvas) {
        val s = "PadMax Pro 120Hz • LAN / Bluetooth HID • Low latency mode"
        canvas.drawText(s, width / 2f, height - 18f, smallText)
    }

    private fun isControlActive(c: Control): Boolean {
        for (i in 0 until pointers.size()) if (pointers.valueAt(i).control.id == c.id) return true
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val x = event.getX(idx); val y = event.getY(idx)
                findControl(x, y)?.let { c ->
                    pointers.put(event.getPointerId(idx), PointerInfo(c, x, y))
                    if (hapticEnabled) Haptics.click(context)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    pointers[id]?.let { p -> p.x = event.getX(i); p.y = event.getY(i) }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val idx = event.actionIndex
                pointers.remove(event.getPointerId(idx))
                if (event.actionMasked == MotionEvent.ACTION_CANCEL) pointers.clear()
            }
        }
        recomputeState()
        invalidate()
        return true
    }

    private fun findControl(x: Float, y: Float): Control? {
        controls.asReversed().forEach { c ->
            if (c.kind == Kind.TRIGGER_LT || c.kind == Kind.TRIGGER_RT || c.kind == Kind.BUTTON && c.rect.width() > c.r * 2.2f) {
                if (c.rect.contains(x, y)) return c
            } else if (hypot(x - c.cx, y - c.cy) <= c.r * 1.15f) return c
        }
        return null
    }

    private fun recomputeState() {
        state.buttons = 0
        state.lx = 0f; state.ly = 0f; state.rx = 0f; state.ry = 0f
        state.lt = 0f; state.rt = 0f
        for (i in 0 until pointers.size()) {
            val p = pointers.valueAt(i)
            when (p.control.kind) {
                Kind.BUTTON -> state.buttons = state.buttons or p.control.button
                Kind.STICK_LEFT -> normalizedStick(p.control, p.x, p.y).also { state.lx = it.first; state.ly = it.second }
                Kind.STICK_RIGHT -> normalizedStick(p.control, p.x, p.y).also { state.rx = it.first; state.ry = it.second }
                Kind.DPAD -> applyDpad(p.control, p.x, p.y)
                Kind.TRIGGER_LT -> state.lt = triggerValue(p.control, p.y)
                Kind.TRIGGER_RT -> state.rt = triggerValue(p.control, p.y)
            }
        }
        sensorFusion?.applyTo(state)
        state.timestampNs = System.nanoTime()
        onStateChanged?.invoke(state.immutableCopy())
    }

    private fun normalizedStick(c: Control, x: Float, y: Float): Pair<Float, Float> {
        val dx = (x - c.cx) / c.r
        val dy = (y - c.cy) / c.r
        val mag = hypot(dx, dy)
        if (mag < 0.08f) return 0f to 0f
        val clamped = if (mag > 1f) 1f / mag else 1f
        return (dx * clamped).coerceIn(-1f, 1f) to (-dy * clamped).coerceIn(-1f, 1f)
    }

    private fun applyDpad(c: Control, x: Float, y: Float) {
        val dx = x - c.cx; val dy = y - c.cy
        if (hypot(dx, dy) < c.r * 0.22f) return
        val angle = atan2(dy, dx); val ux = cos(angle); val uy = sin(angle)
        if (uy < -0.38f) state.buttons = state.buttons or Buttons.DPAD_UP
        if (uy > 0.38f) state.buttons = state.buttons or Buttons.DPAD_DOWN
        if (ux < -0.38f) state.buttons = state.buttons or Buttons.DPAD_LEFT
        if (ux > 0.38f) state.buttons = state.buttons or Buttons.DPAD_RIGHT
    }

    private fun triggerValue(c: Control, y: Float): Float {
        if (abs(y - c.rect.centerY()) < c.rect.height() * 0.42f) return 1f
        return (1f - ((y - c.rect.top) / c.rect.height())).coerceIn(0f, 1f)
    }

    fun releaseAll() { pointers.clear(); recomputeState(); invalidate() }
}
