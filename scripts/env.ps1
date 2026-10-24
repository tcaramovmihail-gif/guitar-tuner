# Dot-source this file:  . .\scripts\env.ps1
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { 'D:\Android\sdk' }
$jdk = 'C:\Program Files\Zulu\zulu-17'
$gradle = 'D:\Android\gradle\gradle-8.13\bin'

$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
if (Test-Path $jdk) { $env:JAVA_HOME = $jdk }
if (Test-Path $gradle) {
    $env:Path = "$sdk\platform-tools;$sdk\cmdline-tools\latest\bin;$sdk\emulator;$gradle;$env:Path"
} else {
    $env:Path = "$sdk\platform-tools;$sdk\cmdline-tools\latest\bin;$sdk\emulator;$env:Path"
}

Write-Host "ANDROID_HOME=$env:ANDROID_HOME"
Write-Host "JAVA_HOME=$env:JAVA_HOME"
