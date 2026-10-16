$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$sdkInstallRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$downloadRoot = Join-Path $env:TEMP 'sim-warning-android-tools-15859902'
$zipPath = Join-Path $downloadRoot 'commandlinetools.zip'
$latestTools = Join-Path $sdkInstallRoot 'cmdline-tools\latest'
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:JAVA_OPTS = '-Xmx512m'
New-Item -ItemType Directory -Path $downloadRoot -Force | Out-Null
if (-not (Test-Path -LiteralPath (Join-Path $latestTools 'bin\sdkmanager.bat'))) {
    Write-Output 'Downloading official Android command-line tools...'
    Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' -OutFile $zipPath
    $expectedHash = '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a'
    if ((Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash -ne $expectedHash) { throw 'Android command-line tools checksum mismatch.' }
    Write-Output 'Checksum verified. Extracting tools...'
    Expand-Archive -LiteralPath $zipPath -DestinationPath (Join-Path $downloadRoot 'unpacked') -Force
    New-Item -ItemType Directory -Path $latestTools -Force | Out-Null
    Copy-Item -Path (Join-Path $downloadRoot 'unpacked\cmdline-tools\*') -Destination $latestTools -Recurse -Force
}
$androidCli = Join-Path $latestTools 'bin\android.exe'
Write-Output 'Installing SDK platform-tools, API 36 platform, build-tools and emulator...'
foreach ($sdkPackage in @('platform-tools', 'platforms;android-36', 'build-tools;36.0.0', 'emulator')) {
    'y' | & $androidCli --no-metrics "--sdk=$sdkInstallRoot" sdk install $sdkPackage
    if ($LASTEXITCODE -ne 0) { throw "SDK package installation failed: $sdkPackage" }
}
foreach ($requiredFile in @('platform-tools\adb.exe', 'platforms\android-36\android.jar', 'platforms\android-36\source.properties', 'build-tools\36.0.0\aapt2.exe', 'emulator\emulator.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $sdkInstallRoot $requiredFile))) { throw "Expected SDK file missing: $requiredFile" }
}
[Environment]::SetEnvironmentVariable('ANDROID_HOME', $sdkInstallRoot, 'User')
$existingUserPath = [Environment]::GetEnvironmentVariable('Path', 'User')
$pathEntries = @($existingUserPath -split ';' | Where-Object { $_ })
foreach ($entry in @((Join-Path $sdkInstallRoot 'platform-tools'), (Join-Path $latestTools 'bin'), (Join-Path $sdkInstallRoot 'emulator'))) {
    if ($pathEntries -notcontains $entry) { $pathEntries += $entry }
}
[Environment]::SetEnvironmentVariable('Path', ($pathEntries -join ';'), 'User')
Write-Output 'Installed SDK packages:'
& $androidCli --no-metrics "--sdk=$sdkInstallRoot" sdk list
& (Join-Path $sdkInstallRoot 'platform-tools\adb.exe') version
