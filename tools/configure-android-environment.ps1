$ErrorActionPreference = 'Stop'
$sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$javaRoot = 'C:\Program Files\Android\Android Studio\jbr'
$gradleRoot = Join-Path $env:LOCALAPPDATA 'SimWarning\Tools\gradle-9.3.1'
foreach ($checkPath in @("$sdkRoot\platform-tools\adb.exe", "$sdkRoot\cmdline-tools\latest\bin\android.exe", "$javaRoot\bin\java.exe", "$gradleRoot\bin\gradle.bat")) {
    if (-not (Test-Path -LiteralPath $checkPath)) { throw "Missing tool: $checkPath" }
}
[Environment]::SetEnvironmentVariable('ANDROID_HOME', $sdkRoot, 'User')
[Environment]::SetEnvironmentVariable('JAVA_HOME', $javaRoot, 'User')
$entries = @([Environment]::GetEnvironmentVariable('Path', 'User') -split ';' | Where-Object { $_ })
foreach ($newEntry in @("$sdkRoot\platform-tools", "$sdkRoot\cmdline-tools\latest\bin", "$sdkRoot\emulator", "$javaRoot\bin", "$gradleRoot\bin")) {
    if ($entries -notcontains $newEntry) { $entries += $newEntry }
}
[Environment]::SetEnvironmentVariable('Path', ($entries -join ';'), 'User')
Write-Output 'User ANDROID_HOME, JAVA_HOME and PATH configured. New terminals inherit these settings.'
& "$sdkRoot\platform-tools\adb.exe" version
& "$sdkRoot\platform-tools\adb.exe" devices -l
& "$sdkRoot\emulator\emulator.exe" -version
& "$sdkRoot\emulator\emulator.exe" -accel-check
Write-Output 'Installed platform directories:'
Get-ChildItem -LiteralPath "$sdkRoot\platforms" -Directory | Select-Object -ExpandProperty Name
