$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$gradleVersion = '9.3.1'
$toolsRoot = Join-Path $env:LOCALAPPDATA 'SimWarning\Tools'
$archivePath = Join-Path $env:TEMP "sim-warning-gradle-$gradleVersion-bin.zip"
$gradleDirectory = Join-Path $toolsRoot "gradle-$gradleVersion"
if (-not (Test-Path -LiteralPath (Join-Path $gradleDirectory 'bin\gradle.bat'))) {
    $downloadUrl = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
    Write-Output "Downloading Gradle $gradleVersion..."
    if (-not (Test-Path -LiteralPath $archivePath)) { Invoke-WebRequest -Uri $downloadUrl -OutFile $archivePath }
    $checksumContent = (Invoke-WebRequest -Uri "$downloadUrl.sha256" -UseBasicParsing).Content
    if ($checksumContent -is [byte[]]) { $checksumContent = [Text.Encoding]::UTF8.GetString($checksumContent) }
    $checksum = $checksumContent.Trim()
    if ($checksum -notmatch '^[a-fA-F0-9]{64}$') { throw 'Invalid Gradle checksum response.' }
    if ((Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash -ne $checksum) { throw 'Gradle checksum mismatch.' }
    New-Item -ItemType Directory -Path $toolsRoot -Force | Out-Null
    Expand-Archive -LiteralPath $archivePath -DestinationPath $toolsRoot -Force
}
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_OPTS = '-Xmx512m -Dorg.gradle.daemon=false -Dorg.gradle.workers.max=1'
$gradleBin = Join-Path $gradleDirectory 'bin'
$userPathEntries = @([Environment]::GetEnvironmentVariable('Path', 'User') -split ';' | Where-Object { $_ })
if ($userPathEntries -notcontains $gradleBin) { [Environment]::SetEnvironmentVariable('Path', (($userPathEntries + $gradleBin) -join ';'), 'User') }
& (Join-Path $gradleBin 'gradle.bat') --version
if ($LASTEXITCODE -ne 0) { throw 'Gradle verification failed.' }
