# Build Guide

## Android

Open `android/PadMax` in Android Studio.

Recommended settings:

- JDK 21
- Android Gradle Plugin 8.8+
- compileSdk 35+
- Android 9+ device for Bluetooth HID mode

Debug APK:

```bash
cd android/PadMax
./gradlew assembleDebug
```

Release APK/AAB requires your own signing key.

## Windows

```powershell
cd desktop/windows/PadMax.Server.Windows
dotnet restore
dotnet run -c Release
```

The first run should be done after installing ViGEmBus/compatible virtual controller driver.

## Linux

```bash
cd desktop/linux
make
sudo ./padmaxd-linux
```
