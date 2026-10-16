$ErrorActionPreference = 'Stop'
$sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:JAVA_OPTS = '-Xmx512m'
$env:ANDROID_HOME = $sdkRoot
$cli = Join-Path $sdkRoot 'cmdline-tools\latest\bin\android.exe'
$imagePath = Join-Path $sdkRoot 'system-images\android-35\default\x86_64'
if (-not (Test-Path -LiteralPath (Join-Path $imagePath 'system.img'))) {
    Write-Output 'Installing Android 15 AOSP x86_64 system image (without Google Play services)...'
    'y' | & $cli --no-metrics "--sdk=$sdkRoot" sdk install 'system-images/android-35/default/x86_64'
    if ($LASTEXITCODE -ne 0) { throw 'System image installation failed.' }
}
if (-not (Test-Path -LiteralPath (Join-Path $imagePath 'system.img'))) { throw 'Installed system image not found.' }
$avdName = 'SimWarning_API35'
$avdDirectory = Join-Path $env:USERPROFILE ".android\avd\$avdName.avd"
if (-not (Test-Path -LiteralPath (Join-Path $avdDirectory 'config.ini'))) {
    'no' | & (Join-Path $sdkRoot 'cmdline-tools\latest\bin\avdmanager.bat') create avd --name $avdName --package 'system-images;android-35;default;x86_64' --device 'pixel_4' --path $avdDirectory
    if ($LASTEXITCODE -ne 0) { throw 'AVD creation failed.' }
    $configPath = Join-Path $avdDirectory 'config.ini'
    $configText = Get-Content -LiteralPath $configPath -Raw
    foreach ($setting in @(@('hw.ramSize','2048'), @('hw.cpu.ncore','2'), @('showDeviceFrame','no'), @('disk.dataPartition.size','2G'))) {
        $settingPattern = '(?m)^' + [regex]::Escape($setting[0]) + '=.*$'
        $settingLine = $setting[0] + '=' + $setting[1]
        if ($configText -match $settingPattern) { $configText = [regex]::Replace($configText, $settingPattern, $settingLine) }
        else { $configText += "`r`n$settingLine" }
    }
    Set-Content -LiteralPath $configPath -Value $configText -Encoding ascii
}
Write-Output 'Configured test emulator; it has not been launched.'
if (-not (Test-Path -LiteralPath (Join-Path $avdDirectory 'config.ini'))) { throw 'AVD config not found after creation.' }
if ((Get-Item -LiteralPath (Join-Path $imagePath 'system.img')).Length -eq 0) { throw 'System image is empty.' }
& (Join-Path $sdkRoot 'emulator\emulator.exe') -list-avds
