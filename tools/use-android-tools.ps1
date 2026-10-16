# Dot-source this file in a terminal: . .\tools\use-android-tools.ps1
# Sets only the current process environment; avoids requiring an app restart.
if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
}
if (-not $env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME }
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr' }
$gradleHome = if ($env:SIMWARNING_GRADLE_HOME) {
    $env:SIMWARNING_GRADLE_HOME
} else {
    Join-Path $env:LOCALAPPDATA 'SimWarning\Tools\gradle-9.3.1'
}
$androidToolPaths = @(
    "$env:JAVA_HOME\bin",
    "$env:ANDROID_HOME\platform-tools",
    "$env:ANDROID_HOME\cmdline-tools\latest\bin",
    "$env:ANDROID_HOME\emulator",
    (Join-Path $gradleHome 'bin')
) | Where-Object { Test-Path -LiteralPath $_ }
$env:Path = ($androidToolPaths -join ';') + ';' + $env:Path
$env:GRADLE_OPTS = '-Xmx512m -Dorg.gradle.daemon=false -Dorg.gradle.workers.max=1'
