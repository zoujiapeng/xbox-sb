package com.padmax.controller

import android.Manifest
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
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
import java.net.InetAddress

class MainActivity : Activity() {
    private var sender: UdpStateSender? = null
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
        hideSystemUi()
        requestBtPermissionsIfNeeded()
        showConnectScreen()
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            window.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
        hostEdit = EditText(this).apply { hint="PC IP，例如 192.168.1.10"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); singleLine(true) }
        portEdit = EditText(this).apply { hint="端口"; setText("28550"); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); singleLine(true) }
        pairEdit = EditText(this).apply { hint="配对码。Windows 服务端默认需要；Linux 明文可留空"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); singleLine(true) }
        status = TextView(this).apply { text="先在电脑运行 PadMax 服务器，然后点击发现服务器。"; setTextColor(Color.LTGRAY); textSize=13f }
        val discover=Button(this).apply{text="发现局域网服务器"}; val connectLan=Button(this).apply{text="连接局域网并进入手柄"}
        val btStart=Button(this).apply{text="启动 Bluetooth HID 模式"}; val btConnect=Button(this).apply{text="连接第一个已配对蓝牙主机并进入手柄"}
        discover.setOnClickListener { discoverServers() }; connectLan.setOnClickListener { connectLan() }; btStart.setOnClickListener { startBluetoothHidOnly(true) }; btConnect.setOnClickListener { startBluetoothHidOnly(false) }
        root.addView(title); root.addView(subtitle); root.addView(hostEdit); root.addView(portEdit); root.addView(pairEdit)
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}; row.addView(discover,LinearLayout.LayoutParams(0,-2,1f)); row.addView(connectLan,LinearLayout.LayoutParams(0,-2,1f)); root.addView(row)
        val row2=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}; row2.addView(btStart,LinearLayout.LayoutParams(0,-2,1f)); row2.addView(btConnect,LinearLayout.LayoutParams(0,-2,1f)); root.addView(row2)
        root.addView(status,LinearLayout.LayoutParams(-1,0,1f)); setContentView(ScrollView(this).apply{addView(root)})
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
        val frame=FrameLayout(this);frame.addView(gamepad,FrameLayout.LayoutParams(-1,-1));frame.addView(overlay,FrameLayout.LayoutParams(-1,-2,Gravity.TOP));setContentView(frame)
    }

    private fun closeRuntime(){sender?.close();sender=null;sensorFusion?.close();sensorFusion=null;motionEnabled=false}
    override fun onDestroy(){super.onDestroy();closeRuntime();bluetoothHid?.close();bluetoothHid=null}
}
