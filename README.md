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

普通用户只需要三步：

1. 双击 `ViGEmBus_1.22.0_x64_x86_arm64.exe` 安装虚拟手柄驱动（首次使用，Windows 端需要管理员权限）。
2. 启动 Windows 服务端（发布后直接双击 EXE；开发时）：

```powershell
cd desktop/windows/PadMax.Server.Windows
dotnet restore
dotnet run -c Release
```

3. 电脑会弹出一个窗口，显示大二维码和“手机打开 PadMax，扫码即可开始游戏”。
4. 手机与电脑连同一个 Wi‑Fi，打开 PadMax App。App 会**自动**依次尝试 USB 数据线、局域网发现；连不上时会**自动**弹出扫码界面。
5. 用 App 扫电脑窗口里的二维码，立即进入手柄界面（部分手机用系统相机扫也能自动跳回 App）。

无需手动输入 IP、端口或配对码。若电脑没装驱动，窗口里会有一个“一键安装虚拟手柄驱动”按钮。

面向开发者/调试的开关：

```powershell
dotnet run -c Release -- --nogui   # 命令行模式，打印 ASCII 二维码，便于无界面环境
dotnet run -c Release -- --plain   # 局域网明文（仅本地可信网络测试用）
```

6. 打开 Windows“游戏控制器”面板或 Steam 测试手柄输入。

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

## 开源许可证

本项目采用 [BSD 3-Clause](LICENSE) 许可证，任何人可免费使用、修改和分发（含商业用途），只需保留版权声明。

> 第三方组件：仓库内附带的 ViGEmBus 驱动安装包与 `vigem.nupkg` 遵循其各自的开源许可证（ViGEmBus 为 BSD-3-Clause），版权归原作者所有。
