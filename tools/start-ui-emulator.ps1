param([string]$AvdName = 'SimWarning_API35')
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'use-android-tools.ps1')
if ((& adb devices) -match '^emulator-\d+\s+(device|offline|unauthorized)\b') {
    Write-Output 'An emulator is already listed by ADB (possibly still booting). No additional emulator was started.'
    exit 0
}
$osInfo = Get-CimInstance Win32_OperatingSystem
$availableMb = [math]::Round($osInfo.FreePhysicalMemory / 1024)
if ($availableMb -lt 3000) { throw "Only $availableMb MB RAM is available. Finish Gradle builds and free memory before starting the emulator." }
$logDirectory = Join-Path (Split-Path $PSScriptRoot -Parent) '.tooling\emulator'
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
$emulatorArguments = @('-avd', $AvdName, '-memory', '1024', '-no-window', '-no-audio', '-no-boot-anim', '-no-snapshot', '-gpu', 'host', '-feature', '-Vulkan')
$process = Start-Process -FilePath (Join-Path $env:ANDROID_HOME 'emulator\emulator.exe') -ArgumentList $emulatorArguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDirectory 'stdout.log') -RedirectStandardError (Join-Path $logDirectory 'stderr.log')
Write-Output "Started headless emulator PID $($process.Id). Wait for adb -s emulator-5554 shell getprop sys.boot_completed to return 1."
Write-Output 'Run UI tests after boot. Stop it with adb -s emulator-5554 emu kill before another memory-heavy build.'
