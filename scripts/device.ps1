param(
    [ValidateSet('status', 'install', 'run', 'screenshot', 'logs', 'uninstall')]
    [string]$Action = 'status'
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\env.ps1"

$projectRoot = Resolve-Path "$PSScriptRoot\.."
$apk = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
$package = 'ru.eurovafel.guitartuner'
$activity = "$package/.MainActivity"

switch ($Action) {
    'status' {
        & adb devices -l
    }

    'install' {
        Push-Location $projectRoot
        try {
            & .\gradlew.bat :app:assembleDebug
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        } finally {
            Pop-Location
        }
        & adb install -r $apk
    }

    'run' {
        & adb shell am start -n $activity
    }

    'screenshot' {
        $outDir = Join-Path $projectRoot 'artifacts\screens'
        New-Item -ItemType Directory -Force -Path $outDir | Out-Null
        $name = 'screen-{0:yyyyMMdd-HHmmss}.png' -f (Get-Date)
        $remote = '/sdcard/guitar-tuner-screen.png'
        & adb shell screencap -p $remote
        & adb pull $remote (Join-Path $outDir $name) | Out-Null
        & adb shell rm $remote | Out-Null
        Write-Host (Join-Path $outDir $name)
    }

    'logs' {
        & adb logcat -v time | Select-String 'guitartuner|AndroidRuntime|FATAL EXCEPTION'
    }

    'uninstall' {
        Write-Warning "This removes app data for $package."
        & adb uninstall $package
    }
}
