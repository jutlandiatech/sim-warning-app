$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'use-android-tools.ps1')
$projectRoot = Split-Path $PSScriptRoot -Parent
$appOutput = Join-Path $env:USERPROFILE '.gradle-simwarning\outputs\SimWarning\app'
$apkFolder = Join-Path $appOutput 'outputs\apk\beta'
$metadata = Get-Content -Raw (Join-Path $apkFolder 'output-metadata.json') | ConvertFrom-Json
$element = @($metadata.elements)[0]
$version = $element.versionName
if ($version -notmatch '^\d+\.\d+\.\d+-test\.\d+$') { throw 'Export is restricted to explicitly versioned test APKs.' }
$apk = Join-Path $apkFolder $element.outputFile
$hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash
$stages = Get-Content -Raw (Join-Path $projectRoot 'docs\tdd\green-platform-stages.json') | ConvertFrom-Json
if (@($stages.stages | Where-Object failures -ne 0).Count -or $stages.totalAndroidTests -ne 33) { throw 'Expected staged Android verification is missing.' }
$smoke = Get-Content -Raw (Join-Path $projectRoot 'docs\tdd\beta-smoke.json') | ConvertFrom-Json
if (-not @($smoke.events | Where-Object name -eq 'PASS').Count) { throw 'Beta smoke checks have not passed.' }
$tested = @($smoke.events | Where-Object name -eq 'beta-connected')[0]
if ($tested.data.apkSha256 -ne $hash) { throw 'Smoke evidence belongs to a different APK. Test this exact binary before export.' }
$manifest = [xml](Get-Content -Raw (Join-Path $appOutput 'intermediates\merged_manifests\beta\processBetaManifest\AndroidManifest.xml'))
$ns = 'http://schemas.android.com/apk/res/android'
if ($manifest.manifest.application.GetAttribute('debuggable', $ns) -eq 'true') { throw 'Beta must not be debuggable.' }
$activities = @($manifest.SelectNodes('/manifest/application/activity') | ForEach-Object { $_.GetAttribute('name',$ns) })
if ($activities.Count -ne 1 -or $activities[0] -ne 'app.simwarning.MainActivity') { throw 'Unexpected activity or instrumentation host in beta.' }
$permissions = @($manifest.SelectNodes('/manifest/uses-permission') | ForEach-Object { $_.GetAttribute('name',$ns) } | Sort-Object)
$approved = @('android.permission.READ_PHONE_STATE','android.permission.POST_NOTIFICATIONS','android.permission.RECEIVE_BOOT_COMPLETED','android.permission.FOREGROUND_SERVICE','android.permission.FOREGROUND_SERVICE_SPECIAL_USE','android.permission.VIBRATE','app.simwarning.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION') | Sort-Object
if (Compare-Object $approved $permissions) { throw 'Beta permissions differ from the approved minimum.' }
$buildTools = Join-Path $env:ANDROID_HOME 'build-tools\36.0.0'
$signature = @(& (Join-Path $buildTools 'apksigner.bat') verify --verbose $apk 2>&1)
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
$badging = @(& (Join-Path $buildTools 'aapt2.exe') dump badging $apk)
if ($LASTEXITCODE -ne 0 -or ($badging -match '^application-debuggable')) { throw 'APK metadata validation failed.' }
if (-not ($badging -match "^(minSdkVersion|sdkVersion):'26'") -or -not ($badging -match "^targetSdkVersion:'36'")) { throw 'Unexpected minimum/target SDK.' }
# Optimized beta resources have shortened ZIP paths. Match the actual WAV bytes, not filenames.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$approvedSounds = @((Get-FileHash (Join-Path $projectRoot 'design\connection-loss-candidate.wav')).Hash, (Get-FileHash (Join-Path $projectRoot 'design\status-unavailable-candidate.wav')).Hash)
$zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
try {
    $packagedSounds = @($zip.Entries | Where-Object FullName -match '\.wav$' | ForEach-Object {
        $stream = $_.Open(); $sha = [System.Security.Cryptography.SHA256]::Create()
        try { ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-','') } finally { $stream.Dispose(); $sha.Dispose() }
    })
} finally { $zip.Dispose() }
if (Compare-Object ($approvedSounds | Sort-Object) ($packagedSounds | Sort-Object)) { throw 'Packaged audio does not match the two approved originals.' }
$dist = Join-Path $projectRoot 'dist'
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$name = "SimWarning-$version.apk"
$destination = Join-Path $dist $name
if ((Test-Path -LiteralPath $destination) -and (Get-FileHash $destination).Hash -ne $hash) { throw 'A different binary already uses this version. Increment the test version instead of replacing it.' }
Copy-Item -LiteralPath $apk -Destination $destination -Force
"$($hash.ToLowerInvariant())  $name" | Set-Content (Join-Path $dist 'SHA256SUMS.txt')
@{version=$version;versionCode=$element.versionCode;exportedAtUtc=[DateTime]::UtcNow.ToString('o');apk=$name;sha256=$hash;bytes=(Get-Item $destination).Length;minSdk=26;targetSdk=36;debuggable=$false;signatureVerified=$true;signingPurpose='Local development/test identity, not a store release';permissions=$permissions;activities=$activities;approvedAudioBytesVerified=$true;coreTests=70;androidTests=33;smokeEvidence='docs/tdd/beta-smoke.json';knownLimits='APK_GUIDE.md'} | ConvertTo-Json -Depth 7 | Set-Content (Join-Path $dist 'BUILD_INFO.json')
$signature | Set-Content (Join-Path $dist 'SIGNATURE_VERIFICATION.txt')
$badging | Set-Content (Join-Path $dist 'APK_METADATA.txt')
Copy-Item -LiteralPath (Join-Path $projectRoot 'APK_GUIDE.md') -Destination (Join-Path $dist 'READ_ME.md') -Force
Write-Output "Exported $destination"
Write-Output "SHA-256: $hash"
