param(
    [ValidateSet('debug', 'release', 'bundle')]
    [string]$Mode = 'debug'
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\env.ps1"
Push-Location "$PSScriptRoot\.."
try {
    switch ($Mode) {
        'debug'   { & .\gradlew.bat :app:assembleDebug }
        'release' { & .\gradlew.bat :app:assembleRelease }
        'bundle'  { & .\gradlew.bat :app:bundleRelease }
    }
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
