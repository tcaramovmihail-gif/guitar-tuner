param(
    [string]$SdkPath = 'D:\Android\sdk',
    [string]$AvdName = 'GuitarTuner_API36'
)

$ErrorActionPreference = 'Stop'

if (-not $env:JAVA_HOME -and -not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'Install JDK 17 first and set JAVA_HOME.'
}

$cmdlineToolsVersion = '16111833'
$cmdlineToolsUrl = "https://dl.google.com/android/repository/commandlinetools-win-${cmdlineToolsVersion}_latest.zip"

if (-not (Test-Path "$SdkPath\cmdline-tools\latest\bin\sdkmanager.bat")) {
    Write-Host "Installing Android command-line tools into $SdkPath ..."
    $tmp = Join-Path $env:TEMP 'commandlinetools.zip'
    $extract = Join-Path $env:TEMP 'commandlinetools-extract'
    Invoke-WebRequest -Uri $cmdlineToolsUrl -OutFile $tmp -UseBasicParsing
    if (Test-Path $extract) { Remove-Item -Recurse -Force $extract }
    New-Item -ItemType Directory -Force -Path $extract | Out-Null
    tar -xf $tmp -C $extract
    New-Item -ItemType Directory -Force -Path "$SdkPath\cmdline-tools" | Out-Null
    if (Test-Path "$SdkPath\cmdline-tools\latest") {
        Remove-Item -Recurse -Force "$SdkPath\cmdline-tools\latest"
    }
    Move-Item "$extract\cmdline-tools" "$SdkPath\cmdline-tools\latest"
}

$env:ANDROID_HOME = $SdkPath
$env:ANDROID_SDK_ROOT = $SdkPath
$android = "$SdkPath\cmdline-tools\latest\bin\android.exe"

# Environment for future terminals
[Environment]::SetEnvironmentVariable('ANDROID_HOME', $SdkPath, 'User')
[Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT', $SdkPath, 'User')
$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
$parts = $userPath -split ';' | Where-Object { $_ }
foreach ($p in @("$SdkPath\platform-tools", "$SdkPath\cmdline-tools\latest\bin", "$SdkPath\emulator")) {
    if ($parts -notcontains $p) { $parts += $p }
}
[Environment]::SetEnvironmentVariable('Path', ($parts -join ';'), 'User')

& $android sdk install 'platform-tools'
& $android sdk install 'platforms;android-36'
& $android sdk install 'build-tools;36.0.0'
& $android sdk install 'emulator'
& $android sdk install 'system-images;android-36;google_apis;x86_64'
& $android sdk install 'extras;google;Android_Emulator_Hypervisor_Driver'

$avdmanager = "$SdkPath\cmdline-tools\latest\bin\avdmanager.bat"
$existing = & "$SdkPath\emulator\emulator.exe" -list-avds
if ($existing -notcontains $AvdName) {
    'no' | & $avdmanager create avd -n $AvdName -k 'system-images;android-36;google_apis;x86_64' -d pixel_6 --force
}

Write-Host ''
Write-Host 'Bootstrap complete.'
Write-Host 'Next: . .\scripts\env.ps1'
Write-Host 'If the emulator reports no hypervisor, install AEHD elevated:'
Write-Host "  $SdkPath\extras\google\Android_Emulator_Hypervisor_Driver\silent_install.bat"
