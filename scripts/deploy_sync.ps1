<#
.SYNOPSIS
    Kit-IME Artifact Deployment & Sync Script 🐾
.DESCRIPTION
    Builds the Kit-IME APK, runs unit test verification, and prepares artifacts
    for local testing on OnePlus 11 5G (NA) or remote VPS artifact distribution.
#>

param(
    [string]$TargetDevice = "OnePlus11",
    [switch]$InstallAdb,
    [string]$VpsRemoteDestination = ""
)

$ErrorActionPreference = "Stop"

Write-Host "🐾 [1/4] Starting Kit-IME Unit Tests..." -ForegroundColor Cyan
& .\gradlew test --stacktrace

Write-Host "✨ [2/4] Assembling Debug APK..." -ForegroundColor Green
& .\gradlew assembleDebug

$ApkPath = "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $ApkPath) {
    $ApkItem = Get-Item $ApkPath
    Write-Host "💜 [3/4] Built APK: $($ApkItem.FullName) ($([math]::Round($ApkItem.Length / 1MB, 2)) MB)" -ForegroundColor Yellow
} else {
    Write-Error "APK output not found at $ApkPath"
}

if ($InstallAdb) {
    Write-Host "📲 [4/4] Installing to connected Android device via ADB..." -ForegroundColor Magenta
    & adb install -r $ApkPath
    Write-Host "✨ Installed successfully to device." -ForegroundColor Green
}

if ($VpsRemoteDestination) {
    Write-Host "🚀 Syncing APK to VPS Destination: $VpsRemoteDestination..." -ForegroundColor Cyan
    scp $ApkPath "$VpsRemoteDestination"
}

Write-Host "🐾 Done in style! Kit-IME ready." -ForegroundColor Green
