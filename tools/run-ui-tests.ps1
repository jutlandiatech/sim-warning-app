param(
    [string]$DeviceSerial = 'emulator-5554',
    [string]$TestClass = '',
    [string]$EvidencePath = '',
    [switch]$CleanAppData
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'use-android-tools.ps1')
$outputRoot = Join-Path $env:USERPROFILE '.gradle-simwarning\outputs\SimWarning\app\outputs\apk'
$appApk = Join-Path $outputRoot 'debug\app-debug.apk'
$testApk = Join-Path $outputRoot 'androidTest\debug\app-debug-androidTest.apk'
foreach ($apk in @($appApk, $testApk)) {
    if (-not (Test-Path -LiteralPath $apk)) { throw "Missing APK: $apk. Run tools/run-gradle.ps1 :app:assembleDebug :app:assembleDebugAndroidTest first." }
}
$deviceState = & adb -s $DeviceSerial get-state
if ($LASTEXITCODE -ne 0 -or $deviceState -ne 'device') { throw "Device $DeviceSerial is not ready." }
$bootCompleted = & adb -s $DeviceSerial shell getprop sys.boot_completed
if ($LASTEXITCODE -ne 0 -or $bootCompleted -ne '1') { throw "Device $DeviceSerial has not finished booting. Wait for sys.boot_completed=1 before running tests." }
foreach ($apk in @($appApk, $testApk)) {
    & adb -s $DeviceSerial install -r -t $apk
    if ($LASTEXITCODE -ne 0) { throw "Could not install $apk" }
}
if ($CleanAppData) {
    if ($DeviceSerial -notlike 'emulator-*') { throw 'Automatic data reset is restricted to emulator test fixtures.' }
    & adb -s $DeviceSerial shell pm clear app.simwarning
    if ($LASTEXITCODE -ne 0) { throw 'Could not reset emulator app data.' }
    $sdk = [int](& adb -s $DeviceSerial shell getprop ro.build.version.sdk)
    $permissions = @('android.permission.READ_PHONE_STATE')
    if ($sdk -ge 33) { $permissions += 'android.permission.POST_NOTIFICATIONS' }
    foreach ($permission in $permissions) {
        & adb -s $DeviceSerial shell pm revoke app.simwarning $permission
        if ($LASTEXITCODE -ne 0) { throw ('Could not reset runtime permission: ' + $permission) }
        & adb -s $DeviceSerial shell pm clear-permission-flags app.simwarning $permission user-set user-fixed
        if ($LASTEXITCODE -ne 0) { throw ('Could not reset permission-dialog flags: ' + $permission) }
    }
}
if (-not $TestClass) {
    $TestClass = 'app.simwarning.AppNavigationTest,app.simwarning.platform.AndroidPlatformTest,app.simwarning.setup.SetupFlowTest,app.simwarning.setup.SetupAccessibilityTest,app.simwarning.setup.TextCatalogTest'
}
$instrumentArguments = @('-s', $DeviceSerial, 'shell', 'am', 'instrument', '-w', '-r')
if ($TestClass) { $instrumentArguments += @('-e', 'class', $TestClass) }
$instrumentArguments += 'app.simwarning.test/androidx.test.runner.AndroidJUnitRunner'
$result = @(& adb @instrumentArguments 2>&1)
$commandExit = $LASTEXITCODE
$result | ForEach-Object { Write-Output $_ }
if ($EvidencePath) { $result | Set-Content -LiteralPath $EvidencePath -Encoding utf8 }
# am instrument often exits zero even when JUnit failed. Require an explicit success summary.
$resultText = $result -join "`n"
if ($commandExit -ne 0 -or $resultText -notmatch 'OK \(\d+ tests?\)' -or $resultText -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') { exit 1 }
