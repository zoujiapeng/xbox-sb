# PadMax Pro

PadMax Pro 是一套“Android 手机 = 低延迟游戏手柄”的成品级源码包，目标是替代主流手柄：

- Android 原生客户端：虚拟双摇杆、ABXY、D-Pad、肩键、模拟扳机、Start/Back/Home、L3/R3、陀螺仪/加速度计。
- 局域网低延迟模式：UDP 120Hz 状态流，自动发现 PC 服务器，支持配对码派生 AES-GCM 会话密钥。
- Windows 服务器：基于 ViGEmBus / Nefarius.ViGEm.Client 创建虚拟 Xbox 360 手柄，可被 Steam、Xbox Game Pass、Epic、模拟器和大多数 PC 游戏识别。
- Linux 服务器：基于 `/dev/uinput` 创建 evdev 虚拟手柄，已包含可编译 C 实现。
- Bluetooth HID 模式：Android 9+ 使用系统 `BluetoothHidDevice` API 将手机注册为蓝牙 HID Gamepad（设备/系统兼容性取决于手机 ROM 与主机 HID 支持）。

> 说明：Windows 内核虚拟手柄依赖 ViGEmBus 或其继任驱动。驱动不能由普通源码包“凭空替代”，必须在用户机器上安装并获得系统授权。Android APK 与 Windows EXE 的正式发布还需要签名证书、CI/CD、真机/Windows 驱动环境测试。

## 仓库结构

```text
android/PadMax/                         Android 原生客户端
  app/src/main/java/com/padmax/controller
    MainActivity.kt                     连接 UI + 游戏手柄界面入口
    GamepadView.kt                      自绘高性能多点触控手柄
    UdpStateSender.kt                   低延迟 UDP 传输
    PacketCodec.kt                      二进制协议与 AES-GCM 封包
    BluetoothHidGamepad.kt              Android Bluetooth HID Gamepad 模式
    DiscoveryClient.kt                  局域网服务器发现
    SensorFusion.kt                     陀螺仪/加速度计采集

desktop/windows/PadMax.Server.Windows/  Windows 服务器，ViGEm 虚拟 Xbox 360
desktop/linux/                          Linux uinput 服务器
protocol/PADMAX_PROTOCOL.md             协议说明
docs/PRODUCT_SPEC.md                    产品与测试说明
```

## 快速运行：Windows + Android

1. Windows 安装 ViGEmBus。由于 ViGEmBus 已归档，推荐同时关注 Nefarius 的继任项目；现有 ViGEmBus 仍可用于兼容多数工具。
2. 安装 .NET 8 SDK。
3. 进入 `desktop/windows/PadMax.Server.Windows`：

```powershell
dotnet restore
dotnet run -c Release
```

4. 用 Android Studio 打开 `android/PadMax`，连接 Android 手机，运行 App。
5. 在 App 中点击“发现服务器”，选择服务器，输入 PC 端显示的 6 位配对码，点击“连接局域网”。
6. 打开 Windows “游戏控制器”面板或 Steam 测试手柄输入。

## 快速运行：Linux + Android

```bash
cd desktop/linux
make
sudo ./padmaxd-linux
```

Linux 版本默认使用明文局域网 UDP，适合本地可信网络。需要访问 `/dev/uinput`，通常需要 root 或 udev 权限。

## 发布级路线

本包已经把核心协议、移动端输入、Windows 虚拟 XInput、Linux uinput、蓝牙 HID 模式和低延迟传输做成可继续工程化发布的代码。要变成商店级产品，还需要：

- Android App 签名、隐私政策、目标 SDK 升级、Google Play 内测。
- Windows 安装器：自动检测 ViGEmBus/继任驱动、安装依赖、添加防火墙规则。
- QA：100+ 游戏兼容性测试、蓝牙 HID 机型兼容矩阵、延迟测试报告。
- UI/UX：布局编辑器、云端布局分享、可视化延迟曲线、手柄皮肤商店。
