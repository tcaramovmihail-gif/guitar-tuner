param(
    [ValidateSet('create', 'start', 'stop', 'status', 'list')]
    [string]$Action = 'status',
    [string]$AvdName = 'GuitarTuner_API36',
    [switch]$Headless
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\env.ps1"

$emu = "$env:ANDROID_HOME\emulator\emulator.exe"
$avdmanager = "$env:ANDROID_HOME\cmdline-tools\latest\bin\avdmanager.bat"
$bootDeadlineMinutes = 5

switch ($Action) {
    'list' {
        & $emu -list-avds
    }

    'create' {
        $existing = & $emu -list-avds
        if ($existing -contains $AvdName) {
            Write-Host "AVD '$AvdName' already exists."
            break
        }
        'no' | & $avdmanager create avd `
            -n $AvdName `
            -k 'system-images;android-36;google_apis;x86_64' `
            -d pixel_6 `
            --force
        Write-Host "AVD '$AvdName' created."
    }

    'start' {
        $running = & adb devices | Select-String 'emulator-\d+\s+device'
        if ($running) {
            Write-Host "An emulator is already running: $running"
            break
        }
        $emuArgs = @('-avd', $AvdName, '-no-snapshot-load', '-gpu', 'auto')
        if ($Headless) { $emuArgs += @('-no-window', '-no-audio', '-no-boot-anim') }
        $proc = Start-Process -FilePath $emu -ArgumentList $emuArgs -PassThru
        Write-Host 'Waiting for the emulator to boot...'
        $deadline = (Get-Date).AddMinutes($bootDeadlineMinutes)
        $booted = $false
        $sawOffline = $false
        while ((Get-Date) -lt $deadline) {
            Start-Sleep -Seconds 3
            $devices = & adb devices
            if ($devices | Select-String 'emulator-\d+\s+offline') { $sawOffline = $true }
            $state = (& adb shell getprop sys.boot_completed 2>$null) -join ''
            if ($state.Trim() -eq '1') { $booted = $true; break }
            if ($proc.HasExited) { break }
        }
        if ($booted) {
            Write-Host 'Emulator is ready.'
            & adb devices -l
        } else {
            Write-Warning "Emulator did not boot in $bootDeadlineMinutes minutes. Check hypervisor (WHPX/AEHD)."
            if ($sawOffline -and -not $proc.HasExited) {
                Write-Warning 'The device is visible to adb as offline and the guest kernel never starts.'
                Write-Warning 'On this host see docs/ENVIRONMENT.md (emulator limitation) and use a real phone.'
            }
            exit 1
        }
    }

    'stop' {
        $serials = (& adb devices) |
            Select-String '^(emulator-\d+)\s+(device|offline)' |
            ForEach-Object { $_.Matches[0].Groups[1].Value }
        foreach ($serial in $serials) {
            & adb -s $serial emu kill 2>$null
            Write-Host "Stopped $serial"
        }
        # If the guest is stuck offline, adb emu kill will not work - stop QEMU by AVD name.
        $avdPattern = 'avd\s+' + [regex]::Escape($AvdName) + '(\s|$)'
        $stuck = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
            Where-Object { $_.CommandLine -match $avdPattern }
        foreach ($p in $stuck) {
            Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
            Write-Host "Stopped $($p.Name) (pid $($p.ProcessId))"
        }
        if (-not $serials -and -not $stuck) {
            Write-Host 'No running emulator.'
        }
    }

    'status' {
        & adb devices -l
        Write-Host ''
        Write-Host 'AVDs:'
        & $emu -list-avds
        Write-Host ''
        Write-Host 'Acceleration:'
        & $emu -accel-check
    }
}
