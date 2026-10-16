param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArguments
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'use-android-tools.ps1')
$projectRoot = Split-Path $PSScriptRoot -Parent
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle-simwarning'
$trustStore = Join-Path $projectRoot '.tooling\java-trust\cacerts'
if (Test-Path -LiteralPath $trustStore) {
    $env:JAVA_OPTS = '-Djavax.net.ssl.trustStore="' + $trustStore + '" -Djavax.net.ssl.trustStorePassword=changeit'
}
Push-Location $projectRoot
try {
    & (Join-Path $projectRoot 'gradlew.bat') @GradleArguments --console=plain --project-cache-dir (Join-Path $env:GRADLE_USER_HOME 'project-cache') "-Pkotlin.project.persistent.dir=$(Join-Path $env:GRADLE_USER_HOME 'kotlin-state')"
    exit $LASTEXITCODE
} finally { Pop-Location }
