# PadMax Server for Windows

## Requirements

- Windows 10/11
- .NET 8 SDK/runtime
- ViGEmBus driver installed

## Run

```powershell
dotnet restore
dotnet run -c Release
```

For unsafe local testing only:

```powershell
dotnet run -c Release -- --plain
```

## Publish

```powershell
dotnet publish -c Release -r win-x64 --self-contained false
```

The server displays a 6-digit pairing code on startup. Enter that code in the Android app after discovery.
