param(
    [switch]$Connected,
    [switch]$NoLint
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\env.ps1"
Push-Location "$PSScriptRoot\.."
try {
    $tasks = @(':core:model:test', ':core:dsp:test', ':app:assembleDebug')
    if (-not $NoLint) { $tasks += ':app:lintDebug' }
    if ($Connected) { $tasks += ':app:connectedDebugAndroidTest' }
    & .\gradlew.bat $tasks
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
