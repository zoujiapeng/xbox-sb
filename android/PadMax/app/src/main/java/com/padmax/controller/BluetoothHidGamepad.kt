package com.padmax.controller

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@SuppressLint("MissingPermission")
class BluetoothHidGamepad(private val context: Context) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "PadMax-BluetoothHID") }
    private val adapter: BluetoothAdapter? = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private var hid: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    private var registered = false
    private var pendingConnect: BluetoothDevice? = null
    var onStatus: ((String) -> Unit)? = null

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            this@BluetoothHidGamepad.registered = registered
            onStatus?.invoke(if (registered) "Bluetooth HID registered" else "Bluetooth HID unregistered")
            if (registered) {
                pendingConnect?.let { device ->
                    pendingConnect = null
                    connect(device)
                }
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            connectedDevice = if (state == BluetoothProfile.STATE_CONNECTED) device else null
            val name = runCatching { device.name }.getOrNull() ?: device.address
            onStatus?.invoke("Bluetooth HID $name state=$state")
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            hid?.replyReport(device, type, id, ByteArray(9))
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hid = proxy as BluetoothHidDevice
            registerApp()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hid = null
                registered = false
            }
        }
    }

    fun hasPermission(): Boolean {
        return Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    fun start() {
        require(hasPermission()) { "BLUETOOTH_CONNECT permission is required" }
        val ok = adapter?.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE) ?: false
        if (!ok) onStatus?.invoke("Bluetooth HID profile is not available on this device")
    }

    private fun registerApp() {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "PadMax Pro Gamepad",
            "Android phone as low latency game controller",
            "PadMax",
            BluetoothHidDevice.SUBCLASS2_GAMEPAD,
            descriptor
        )
        val ok = hid?.registerApp(sdp, null, null, executor, callback) ?: false
        onStatus?.invoke(if (ok) "Registering Bluetooth HID…" else "Bluetooth HID registerApp failed")
    }

    fun bondedDevices(): List<BluetoothDevice> {
        if (!hasPermission()) return emptyList()
        return adapter?.bondedDevices?.toList().orEmpty()
    }

    fun connect(device: BluetoothDevice): Boolean {
        if (!registered) {
            pendingConnect = device
            onStatus?.invoke("Bluetooth HID registering; connection queued")
            return true
        }
        return hid?.connect(device) ?: false
    }

    fun send(state0: ControllerState) {
        val device = connectedDevice ?: return
        val state = state0.immutableCopy().sanitize()
        val report = ByteArray(9)
        val buttons = state.buttons.toInt() and 0xFFFF
        report[0] = (buttons and 0xff).toByte()
        report[1] = ((buttons ushr 8) and 0xff).toByte()
        report[2] = axisByte(state.lx)
        report[3] = axisByte(-state.ly)
        report[4] = axisByte(state.rx)
        report[5] = axisByte(-state.ry)
        report[6] = triggerByte(state.lt)
        report[7] = triggerByte(state.rt)
        report[8] = hatValue(state.buttons).toByte()
        hid?.sendReport(device, 1, report)
    }

    private fun axisByte(v: Float): Byte = (v.coerceIn(-1f, 1f) * 127f).roundToInt().toByte()
    private fun triggerByte(v: Float): Byte = (v.coerceIn(0f, 1f) * 255f).roundToInt().toByte()

    private fun hatValue(buttons: Long): Int {
        val up = buttons and Buttons.DPAD_UP != 0L
        val down = buttons and Buttons.DPAD_DOWN != 0L
        val left = buttons and Buttons.DPAD_LEFT != 0L
        val right = buttons and Buttons.DPAD_RIGHT != 0L
        return when {
            up && right -> 1
            right && down -> 3
            down && left -> 5
            left && up -> 7
            up -> 0
            right -> 2
            down -> 4
            left -> 6
            else -> 8
        }
    }

    override fun close() {
        runCatching { hid?.unregisterApp() }
        runCatching { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid) }
        executor.shutdownNow()
    }

    companion object {
        private fun b(vararg values: Int): ByteArray = values.map { it.toByte() }.toByteArray()
        val descriptor: ByteArray = b(
            0x05,0x01,0x09,0x05,0xA1,0x01,0x85,0x01,0x05,0x09,0x19,0x01,0x29,0x10,0x15,0x00,
            0x25,0x01,0x75,0x01,0x95,0x10,0x81,0x02,0x05,0x01,0x09,0x30,0x09,0x31,0x09,0x33,
            0x09,0x34,0x15,0x81,0x25,0x7F,0x75,0x08,0x95,0x04,0x81,0x02,0x09,0x32,0x09,0x35,
            0x15,0x00,0x26,0xFF,0x00,0x75,0x08,0x95,0x02,0x81,0x02,0x09,0x39,0x15,0x00,0x25,
            0x07,0x35,0x00,0x46,0x3B,0x01,0x65,0x14,0x75,0x04,0x95,0x01,0x81,0x42,0x75,0x04,
            0x95,0x01,0x81,0x03,0xC0
        )
    }
}
