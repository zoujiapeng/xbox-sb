$ErrorActionPreference = "Stop"
Set-Location "$PSScriptRoot\..\desktop\windows\PadMax.Server.Windows"
dotnet restore
dotnet publish -c Release -r win-x64 --self-contained false
Write-Host "Published to bin/Release/net8.0-windows/win-x64/publish"
