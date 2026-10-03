package com.padmax.controller

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.zxing.integration.android.IntentIntegrator
import java.net.InetAddress

class MainActivity : Activity() {
    private var sender: StateSender? = null
    private var sensorFusion: SensorFusion? = null
    private var bluetoothHid: BluetoothHidGamepad? = null
    private var selectedServer: PadMaxServer? = null
    private lateinit var status: TextView
    private lateinit var hostEdit: EditText
    private lateinit var portEdit: EditText
    private lateinit var pairEdit: EditText
    private var motionEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        requestBtPermissionsIfNeeded()
        showConnectScreen()
        autoConnect()
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= 30) {
            val controller = window.decorView.windowInsetsController ?: return
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = 0x00000400 or 0x00001000 or 0x00000004 or 0x00000002
        }
    }

    private fun requestBtPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31) requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), 42)
    }

    private fun showConnectScreen() {
        closeRuntime()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36,28,36,24); setBackgroundColor(Color.rgb(5,7,10)) }
        val title = TextView(this).apply { text="PadMax Pro"; textSize=30f; setTextColor(Color.WHITE); gravity=Gravity.CENTER_HORIZONTAL }
        val subtitle = TextView(this).apply { text="LAN UDP 120Hz + ViGEm/uinput + Bluetooth HID Gamepad"; textSize=14f; setTextColor(Color.rgb(160,240,255)); gravity=Gravity.CENTER_HORIZONTAL }
        hostEdit = EditText(this).apply { hint="PC IP，例如 192.168.1.10"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); setSingleLine(true) }
        portEdit = EditText(this).apply { hint="端口"; setText("28550"); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); setSingleLine(true) }
        pairEdit = EditText(this).apply { hint="配对码。Windows 服务端默认需要；Linux 明文可留空"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); setSingleLine(true) }
        status = TextView(this).apply { text="先在电脑运行 PadMax 服务器，然后点击发现服务器。"; setTextColor(Color.LTGRAY); textSize=13f }
        val discover=Button(this).apply{text="发现局域网服务器"}; val connectLan=Button(this).apply{text="连接局域网并进入手柄"}
        val btStart=Button(this).apply{text="启动 Bluetooth HID 模式"}; val btConnect=Button(this).apply{text="连接第一个已配对蓝牙主机并进入手柄"}
        val scan=Button(this).apply{text="扫码连接（推荐）"}
        discover.setOnClickListener { discoverServers() }; connectLan.setOnClickListener { connectLan() }; btStart.setOnClickListener { startBluetoothHidOnly(true) }; btConnect.setOnClickListener { startBluetoothHidOnly(false) }; scan.setOnClickListener { startQrScan() }
        root.addView(title); root.addView(subtitle); root.addView(scan); root.addView(hostEdit); root.addView(portEdit); root.addView(pairEdit)
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}; row.addView(discover,LinearLayout.LayoutParams(0,-2,1f)); row.addView(connectLan,LinearLayout.LayoutParams(0,-2,1f)); root.addView(row)
        val row2=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}; row2.addView(btStart,LinearLayout.LayoutParams(0,-2,1f)); row2.addView(btConnect,LinearLayout.LayoutParams(0,-2,1f)); root.addView(row2)
        root.addView(status,LinearLayout.LayoutParams(-1,0,1f)); setContentView(ScrollView(this).apply{addView(root)}); hideSystemUi()
    }

    private fun discoverServers() {
        status.text="正在广播发现…"
        Thread {
            val servers=runCatching{DiscoveryClient.discover(1200)}.getOrDefault(emptyList())
            runOnUiThread {
                if(servers.isEmpty()) status.text="未发现服务器。检查 PC 防火墙、同一 Wi‑Fi、服务器是否运行。"
                else { selectedServer=servers.first(); hostEdit.setText(selectedServer!!.host); portEdit.setText(selectedServer!!.port.toString()); status.text="发现 ${servers.size} 个服务器，已选择 ${selectedServer!!.name}。" }
            }
        }.start()
    }

    private fun connectLan() {
        val host=hostEdit.text.toString().trim(); val port=portEdit.text.toString().trim().toIntOrNull()?:28550
        if(host.isBlank()){status.text="请输入 PC IP 或先发现服务器。";return}
        runCatching{InetAddress.getByName(host)}.onFailure{status.text="IP/主机名无效：${it.message}";return}
        val server=selectedServer
        val crypto=if(server?.secure==true&&server.salt.isNotEmpty()){val code=pairEdit.text.toString().trim();if(code.isBlank()){status.text="此服务器启用了安全模式，请输入 6 位配对码。";return};CryptoBox.fromPairCode(code,server.salt)}else null
        savePrefs(host,port,if(server?.secure==true)pairEdit.text.toString().trim().ifBlank{null}else null,server?.salt?:ByteArray(0))
        sender=UdpStateSender(host,port,crypto=crypto).also{it.start()}; showGamepadScreen("LAN ${host}:${port}${if(crypto!=null)" secure" else " plain"}")
    }

    private fun startBluetoothHidOnly(stayOnScreen:Boolean) {
        val hid=bluetoothHid?:BluetoothHidGamepad(this).also{bluetoothHid=it}; hid.onStatus={msg->runOnUiThread{status.text=msg+"\n"+status.text}}
        if(!hid.hasPermission()){status.text="缺少 BLUETOOTH_CONNECT 权限，请授权后重试。";requestBtPermissionsIfNeeded();return}
        runCatching{hid.start()}.onFailure{status.text="Bluetooth HID 启动失败：${it.message}"}; val bonded=hid.bondedDevices()
        status.text=if(bonded.isEmpty())"Bluetooth HID 已请求启动。没有已配对主机。" else "已配对设备：\n"+bonded.joinToString("\n"){(it.name?:"Unknown")+" "+it.address}
        if(!stayOnScreen&&bonded.isNotEmpty()){hid.connect(bonded.first());showGamepadScreen("Bluetooth HID → ${bonded.first().name?:bonded.first().address}")}
    }

    private fun showGamepadScreen(mode:String) {
        sensorFusion=SensorFusion(this)
        val gamepad=GamepadView(this).apply{sensorFusion=this@MainActivity.sensorFusion;onStateChanged={s->sender?.update(s);bluetoothHid?.send(s)}}
        val overlay=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(10,8,10,8);setBackgroundColor(Color.argb(90,0,0,0))}
        val back=Button(this).apply{text="返回";setOnClickListener{showConnectScreen()}}
        val motion=Button(this).apply{text="Motion OFF";setOnClickListener{motionEnabled=!motionEnabled;text=if(motionEnabled)"Motion ON" else "Motion OFF";if(motionEnabled)sensorFusion?.start() else sensorFusion?.stop()}}
        val label=TextView(this).apply{text="  $mode";setTextColor(Color.WHITE);textSize=14f}; overlay.addView(back);overlay.addView(motion);overlay.addView(label,LinearLayout.LayoutParams(0,-2,1f))
        val frame=FrameLayout(this);frame.addView(gamepad,FrameLayout.LayoutParams(-1,-1));frame.addView(overlay,FrameLayout.LayoutParams(-1,-2,Gravity.TOP));setContentView(frame);hideSystemUi()
    }

    private fun autoConnect() {
        Thread {
            var usb: TcpStateSender? = null
            try { usb = TcpStateSender("127.0.0.1", 28550).also { it.connect() } } catch (_: Exception) {}
            if (usb != null) {
                val t = usb
                runOnUiThread {
                    if (sender != null) { t.close(); return@runOnUiThread }
                    sender = t.also { it.start() }
                    status.text = "已通过数据线(USB/adb)自动连接"
                    showGamepadScreen("USB 数据线 127.0.0.1:28550")
                }
                return@Thread
            }
            runOnUiThread { if (sender == null) status.text = "未检测到数据线模式，正在自动发现局域网服务器…" }
            val servers = runCatching { DiscoveryClient.discover(1200) }.getOrDefault(emptyList())
            if (servers.isEmpty()) {
                runOnUiThread { if (sender == null) { status.text = "未检测到数据线，也未自动发现服务器，打开扫码…"; startQrScan() } }
                return@Thread
            }
            val prefs = getSharedPreferences("padmax", MODE_PRIVATE)
            val savedHost = prefs.getString("host", null)
            val savedSalt = prefs.getString("salt", null)
            val savedCode = prefs.getString("code", null)
            val server = servers.firstOrNull { it.host == savedHost } ?: servers.first()
            runOnUiThread {
                if (sender != null) return@runOnUiThread
                selectedServer = server
                hostEdit.setText(server.host); portEdit.setText(server.port.toString())
                val saltB64 = Base64.encodeToString(server.salt, Base64.NO_WRAP)
                val crypto = if (server.secure && savedCode != null && savedSalt == saltB64) runCatching { CryptoBox.fromPairCode(savedCode, server.salt) }.getOrNull() else null
                if (!server.secure || crypto != null) {
                    if (savedCode != null) pairEdit.setText(savedCode)
                    savePrefs(server.host, server.port, savedCode, server.salt)
                    sender = UdpStateSender(server.host, server.port, crypto = crypto).also { it.start() }
                    status.text = "已自动连接 ${server.host}:${server.port}"
                    showGamepadScreen("LAN ${server.host}:${server.port}${if (crypto != null) " secure" else " plain"}")
                } else {
                    status.text = "发现 ${server.name}（安全模式）。请扫描电脑上显示的二维码即可连接。"
                    startQrScan()
                }
            }
        }.start()
    }

    private fun savePrefs(host: String, port: Int, code: String?, salt: ByteArray) {
        getSharedPreferences("padmax", MODE_PRIVATE).edit()
            .putString("host", host).putInt("port", port)
            .putString("code", code).putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .apply()
    }

    private fun startQrScan() {
        runCatching {
            IntentIntegrator(this)
                .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                .setPrompt("扫描电脑上 PadMax 显示的二维码")
                .setBeepEnabled(false)
                .setOrientationLocked(true)
                .initiateScan()
        }.onFailure { status.text = "无法启动扫码：${it.message}" }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            val contents = result.contents
            if (contents.isNullOrBlank()) status.text = "已取消扫码。可手动输入地址或点击“扫码连接”重试。" else connectFromQr(contents)
        } else super.onActivityResult(requestCode, resultCode, data)
    }

    private fun connectFromQr(text: String) {
        val uri = runCatching { Uri.parse(text.trim()) }.getOrNull()
        if (uri == null || uri.scheme != "padmax") { status.text = "二维码无法识别：$text"; return }
        val host = uri.getQueryParameter("h"); val port = uri.getQueryParameter("p")?.toIntOrNull()
        if (host.isNullOrBlank() || port == null) { status.text = "二维码缺少地址信息。"; return }
        val code = uri.getQueryParameter("c")
        val saltB64 = uri.getQueryParameter("s")
        val salt = if (saltB64.isNullOrBlank()) ByteArray(0) else runCatching { Base64.decode(saltB64, Base64.NO_WRAP) }.getOrDefault(ByteArray(0))
        val crypto = if (salt.isNotEmpty() && !code.isNullOrBlank()) runCatching { CryptoBox.fromPairCode(code, salt) }.getOrNull() else null
        if (salt.isNotEmpty() && crypto == null) { status.text = "二维码配对信息无效。"; return }
        selectedServer = null
        hostEdit.setText(host); portEdit.setText(port.toString()); if (!code.isNullOrBlank()) pairEdit.setText(code)
        savePrefs(host, port, code, salt)
        closeRuntime()
        sender = UdpStateSender(host, port, crypto = crypto).also { it.start() }
        showGamepadScreen("扫码 Wi‑Fi $host:$port${if (crypto != null) " secure" else " plain"}")
    }

    private fun closeRuntime(){sender?.close();sender=null;sensorFusion?.close();sensorFusion=null;motionEnabled=false}
    override fun onDestroy(){super.onDestroy();closeRuntime();bluetoothHid?.close();bluetoothHid=null}
}
