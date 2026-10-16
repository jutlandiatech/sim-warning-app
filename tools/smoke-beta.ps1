param([string]$DeviceSerial = 'emulator-5554', [switch]$SkipRestart)
$ErrorActionPreference = 'Stop'
if ($DeviceSerial -notlike 'emulator-*') { throw 'Radio/restart smoke checks are restricted to the isolated emulator.' }
. (Join-Path $PSScriptRoot 'use-android-tools.ps1')
$projectRoot = Split-Path $PSScriptRoot -Parent
$outputFolder = Join-Path $projectRoot '.tooling\beta-smoke'
New-Item -ItemType Directory -Force -Path $outputFolder | Out-Null
$events = [System.Collections.Generic.List[object]]::new()
function Record($Name, $Data) {
    $events.Add(@{name=$Name;observedAtUtc=[DateTime]::UtcNow.ToString('o');data=$Data})
    @{events=@($events.ToArray())} | ConvertTo-Json -Depth 9 | Set-Content (Join-Path $outputFolder 'events.json')
    Write-Output $Name
}
function Snapshot {
    $lines = @(& adb -s $DeviceSerial shell dumpsys activity service app.simwarning/.platform.MonitorService)
    $result = @{}
    foreach($line in $lines) { if($line -match '^\s*(running|connectivity|status|desiredEnabled|nextDeadlineElapsedMs|episodeSinceElapsedMs|visibleWarning|lastWarningPostElapsedMs)=(.*)$') { $result[$Matches[1]]=$Matches[2].Trim() } }
    return $result
}
function Wait-State([scriptblock]$Predicate, [int]$Seconds) {
    $deadline = [DateTime]::UtcNow.AddSeconds($Seconds)
    do {
        $snapshot = Snapshot
        if (& $Predicate $snapshot) { return $snapshot }
        Start-Sleep -Seconds 2
    } while([DateTime]::UtcNow -lt $deadline)
    throw ('State wait timed out: ' + ($snapshot | ConvertTo-Json -Compress))
}
function Warning-Key {
    return @(& adb -s $DeviceSerial shell cmd notification list | Where-Object { $_ -match '^0\|app\.simwarning\|1002\|null\|\d+$' })
}
try {
    $beta = Join-Path $env:USERPROFILE '.gradle-simwarning\outputs\SimWarning\app\outputs\apk\beta\app-beta.apk'
    & adb -s $DeviceSerial install -r $beta
    if($LASTEXITCODE -ne 0) { throw 'Beta install failed' }
    & adb -s $DeviceSerial shell am start -W -n app.simwarning/.MainActivity
    & adb -s $DeviceSerial shell cmd connectivity airplane-mode disable
    & adb -s $DeviceSerial emu gsm voice home
    & adb -s $DeviceSerial emu gsm data home
    & adb -s $DeviceSerial shell svc data enable
    $healthy = Wait-State { param($s) $s.status -eq 'CONNECTED' } 50
    Record 'beta-connected' @{apkSha256=(Get-FileHash $beta).Hash;state=$healthy}
    & adb -s $DeviceSerial shell screencap -p /sdcard/Download/sim-warning-main-beta.png
    & adb -s $DeviceSerial pull /sdcard/Download/sim-warning-main-beta.png (Join-Path $outputFolder 'main-beta.png')
    & adb -s $DeviceSerial shell input keyevent KEYCODE_HOME
    & adb -s $DeviceSerial shell svc data disable
    $deadline=[DateTime]::UtcNow.AddSeconds(10)
    $samples=0
    do { $snapshot=Snapshot; if($snapshot.status -ne 'CONNECTED'){throw 'Turning mobile data off incorrectly changed healthy service state'}; $samples++; Start-Sleep -Seconds 2 }while([DateTime]::UtcNow -lt $deadline)
    Record 'mobile-data-off-still-connected' @{samples=$samples;state=$snapshot}
    & adb -s $DeviceSerial shell svc data enable
    & adb -s $DeviceSerial shell svc wifi enable
    $wifi=@(& adb -s $DeviceSerial shell cmd wifi status)
    & adb -s $DeviceSerial emu gsm voice unregistered
    & adb -s $DeviceSerial emu gsm data unregistered
    $loss=Wait-State {param($s) $s.status -eq 'LOSS_GRACE' -and $s.connectivity -eq 'DISCONNECTED'} 12
    if((Warning-Key).Count -ne 0){throw 'Warning appeared during ordinary loss grace'}
    Record 'ordinary-loss-grace' @{state=$loss;wifiConnected=(($wifi -join "`n") -match 'Wifi is connected')}
    $visual=Wait-State {param($s) $s.status -eq 'OUTAGE' -and $s.visibleWarning -eq 'CONNECTION_LOSS'} 45
    $onset=[long]$visual.episodeSinceElapsedMs
    $visualDelay=[long]$visual.lastWarningPostElapsedMs - $onset
    if($visualDelay -lt 30000 -or (Warning-Key).Count -ne 1){throw 'Visual warning timing/posting failed'}
    Record 'visual-warning-posted-with-app-closed' @{state=$visual;delayMs=$visualDelay}
    Write-Output 'Waiting for the real 2-minute alert deadline; production timing is unchanged.'
    $alert=Wait-State {param($s) $s.lastWarningPostElapsedMs -ne 'null' -and [long]$s.lastWarningPostElapsedMs -ge ($onset+120000)} 105
    $key=@(Warning-Key)[0]
    $details=@(& adb -s $DeviceSerial shell cmd notification get ("'"+$key+"'"))
    $details | Set-Content (Join-Path $outputFolder 'outage-notification.txt')
    Record 'two-minute-alert-posted' @{state=$alert;delayMs=([long]$alert.lastWarningPostElapsedMs-$onset);notification=$details}
    & adb -s $DeviceSerial shell cmd statusbar expand-notifications
    & adb -s $DeviceSerial shell uiautomator dump /sdcard/Download/sim-warning-notification.xml
    & adb -s $DeviceSerial shell screencap -p /sdcard/Download/sim-warning-outage.png
    & adb -s $DeviceSerial pull /sdcard/Download/sim-warning-outage.png (Join-Path $outputFolder 'outage-notification.png')
    & adb -s $DeviceSerial shell cmd statusbar collapse
    & adb -s $DeviceSerial emu gsm voice home
    & adb -s $DeviceSerial emu gsm data home
    $recovering=Wait-State {param($s) $s.status -eq 'RECOVERING'} 12
    if((Warning-Key).Count -ne 1){throw 'Warning cleared before stable recovery confirmation'}
    Record 'warning-retained-during-recovery' $recovering
    $recovered=Wait-State {param($s) $s.status -eq 'CONNECTED'} 45
    if((Warning-Key).Count -ne 0){throw 'Warning remained after stable recovery'}
    Record 'recovered-quietly' $recovered
    if(-not $SkipRestart) {
        $oldBoot=(& adb -s $DeviceSerial shell cat /proc/sys/kernel/random/boot_id).Trim()
        & adb -s $DeviceSerial reboot
        $bootDeadline=[DateTime]::UtcNow.AddSeconds(240)
        $booted=$false
        do {
            $newBoot=(& adb -s $DeviceSerial shell cat /proc/sys/kernel/random/boot_id 2>$null)
            $complete=(& adb -s $DeviceSerial shell getprop sys.boot_completed 2>$null)
            if($newBoot -and $newBoot.Trim() -ne $oldBoot -and $complete -eq '1'){ $booted=$true; break }
            Start-Sleep -Seconds 2
        }while([DateTime]::UtcNow -lt $bootDeadline)
        if(-not $booted){throw 'Emulator restart did not complete'}
        $resumed=Wait-State {param($s) $s.running -eq 'true' -and $s.desiredEnabled -eq 'true'} 30
        Record 'enabled-monitoring-resumed-after-real-reboot' $resumed
    }
    Record 'PASS' @{scope='Awake API35 emulator only; sound output routed by Android, not acoustically verified; no battery/Doze claim.'}
} catch {
    Record 'FAIL' @{message=$_.Exception.Message;state=(Snapshot)}
    throw
} finally {
    & adb -s $DeviceSerial emu gsm voice home 2>$null
    & adb -s $DeviceSerial emu gsm data home 2>$null
    & adb -s $DeviceSerial shell svc data enable 2>$null
}
