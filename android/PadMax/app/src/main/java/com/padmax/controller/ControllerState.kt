package com.padmax.controller

import kotlin.math.max
import kotlin.math.min

object Buttons {
    const val A: Long = 1L shl 0
    const val B: Long = 1L shl 1
    const val X: Long = 1L shl 2
    const val Y: Long = 1L shl 3
    const val LB: Long = 1L shl 4
    const val RB: Long = 1L shl 5
    const val BACK: Long = 1L shl 6
    const val START: Long = 1L shl 7
    const val GUIDE: Long = 1L shl 8
    const val LS: Long = 1L shl 9
    const val RS: Long = 1L shl 10
    const val DPAD_UP: Long = 1L shl 11
    const val DPAD_DOWN: Long = 1L shl 12
    const val DPAD_LEFT: Long = 1L shl 13
    const val DPAD_RIGHT: Long = 1L shl 14
    const val MISC: Long = 1L shl 15
}

data class ControllerState(
    var buttons: Long = 0,
    var lx: Float = 0f,
    var ly: Float = 0f,
    var rx: Float = 0f,
    var ry: Float = 0f,
    var lt: Float = 0f,
    var rt: Float = 0f,
    var accelX: Float = 0f,
    var accelY: Float = 0f,
    var accelZ: Float = 0f,
    var gyroX: Float = 0f,
    var gyroY: Float = 0f,
    var gyroZ: Float = 0f,
    var timestampNs: Long = System.nanoTime()
) {
    fun sanitize(): ControllerState {
        lx = clampAxis(lx); ly = clampAxis(ly)
        rx = clampAxis(rx); ry = clampAxis(ry)
        lt = clamp01(lt); rt = clamp01(rt)
        return this
    }

    fun copyFrom(other: ControllerState) {
        buttons = other.buttons
        lx = other.lx; ly = other.ly
        rx = other.rx; ry = other.ry
        lt = other.lt; rt = other.rt
        accelX = other.accelX; accelY = other.accelY; accelZ = other.accelZ
        gyroX = other.gyroX; gyroY = other.gyroY; gyroZ = other.gyroZ
        timestampNs = other.timestampNs
    }

    fun immutableCopy(): ControllerState = copy()

    companion object {
        fun clampAxis(v: Float): Float = max(-1f, min(1f, v))
        fun clamp01(v: Float): Float = max(0f, min(1f, v))
    }
}
