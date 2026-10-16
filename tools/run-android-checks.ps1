param([string]$DeviceSerial = 'emulator-5554', [switch]$KeepEmulatorState)
$ErrorActionPreference = 'Stop'
if ($DeviceSerial -notlike 'emulator-*') { throw 'This staged suite resets app data and is restricted to an emulator.' }
$projectRoot = Split-Path $PSScriptRoot -Parent
$evidenceFolder = Join-Path $projectRoot '.tooling\android-checks'
New-Item -ItemType Directory -Force -Path $evidenceFolder | Out-Null
$runner = Join-Path $PSScriptRoot 'run-ui-tests.ps1'
$stages = @(
    @{Name='ui-and-adapters'; Class=''; Clean=$true},
    @{Name='service-lifecycle'; Class='app.simwarning.platform.MonitoringLifecycleTest'; Clean=$false},
    @{Name='permission-denial'; Class='app.simwarning.platform.RealPermissionFlowTest#U08_actualDenialLeavesSetupIncomplete'; Clean=$true},
    @{Name='permission-grant'; Class='app.simwarning.platform.RealPermissionFlowTest#U10_actualGrantsCompleteSetupAndStartService'; Clean=$true}
)
foreach ($stage in $stages) {
    Write-Output ('Running ' + $stage.Name)
    $runnerArguments = @('-NoProfile','-ExecutionPolicy','Bypass','-File',$runner,'-DeviceSerial',$DeviceSerial,'-EvidencePath',(Join-Path $evidenceFolder ($stage.Name + '.txt')))
    if ($stage.Class) { $runnerArguments += @('-TestClass', $stage.Class) }
    if ($stage.Clean) { $runnerArguments += '-CleanAppData' }
    & powershell.exe @runnerArguments
    if ($LASTEXITCODE -ne 0) { throw ('Android checks failed at stage: ' + $stage.Name) }
}
if (-not $KeepEmulatorState) {
    . (Join-Path $PSScriptRoot 'use-android-tools.ps1')
    & adb -s $DeviceSerial shell pm clear app.simwarning
    if ($LASTEXITCODE -ne 0) { throw 'Tests passed but final fixture reset failed.' }
}
Write-Output ('All four stages passed. Logs: ' + $evidenceFolder)
