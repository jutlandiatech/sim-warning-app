$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'use-android-tools.ps1')
$projectRoot = Split-Path $PSScriptRoot -Parent
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle-simwarning'
$trustStore = Join-Path $projectRoot '.tooling\java-trust\cacerts'
$pemFile = Join-Path $projectRoot '.tooling\java-trust\windows-roots.pem'
if (Test-Path -LiteralPath $trustStore) {
    $env:JAVA_OPTS = '-Djavax.net.ssl.trustStore="' + $trustStore + '" -Djavax.net.ssl.trustStorePassword=changeit'
}
if (Test-Path -LiteralPath $pemFile) { $env:NODE_EXTRA_CA_CERTS = $pemFile }
$codeCommand = Get-Command code.cmd -ErrorAction SilentlyContinue
$codePath = if ($codeCommand) { $codeCommand.Source } else { $null }
if (-not $codePath) {
    $codePath = @(
        (Join-Path $env:LOCALAPPDATA 'Programs\Microsoft VS Code\bin\code.cmd'),
        (Join-Path $env:ProgramFiles 'Microsoft VS Code\bin\code.cmd')
    ) | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if (-not $codePath) { throw "VS Code CLI was not found on PATH or in a standard installation directory." }
& $codePath --new-window (Join-Path $projectRoot 'SimWarning.code-workspace')
exit $LASTEXITCODE
