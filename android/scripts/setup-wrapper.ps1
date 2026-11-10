Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
  throw 'Gradle CLI tidak ditemukan. Install Gradle yang kompatibel atau gunakan Android Studio. Gradle wrapper JAR belum dibuat.'
}
gradle wrapper --gradle-version 8.14.3 --distribution-type bin
Write-Host 'Wrapper sudah dibuat. Coba: .\gradlew.bat :app:assembleDebug'
