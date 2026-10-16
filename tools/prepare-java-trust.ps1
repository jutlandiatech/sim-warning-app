# Build a project-local Java trust store using roots already trusted by Windows.
# Never disables TLS validation or modifies Windows / Android Studio trust stores.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$trustDirectory = Join-Path $projectRoot '.tooling\java-trust'
$javaRoot = 'C:\Program Files\Android\Android Studio\jbr'
New-Item -ItemType Directory -Path $trustDirectory -Force | Out-Null
$trustStore = Join-Path $trustDirectory 'cacerts'
Copy-Item -LiteralPath (Join-Path $javaRoot 'lib\security\cacerts') -Destination $trustStore -Force
$imported = @{}
$pemRoots = @()
foreach ($hostName in @('services.gradle.org', 'plugins.gradle.org', 'repo.maven.apache.org', 'dl.google.com')) {
    $tcpClient = New-Object Net.Sockets.TcpClient
    try {
        $tcpClient.Connect($hostName, 443)
        # Default validation uses Windows trust; no permissive callback is supplied.
        $tlsStream = New-Object Net.Security.SslStream ($tcpClient.GetStream()), $false
        try {
            $tlsStream.AuthenticateAsClient($hostName)
            $certificate = New-Object Security.Cryptography.X509Certificates.X509Certificate2 $tlsStream.RemoteCertificate
            $chain = New-Object Security.Cryptography.X509Certificates.X509Chain
            try {
                if (-not $chain.Build($certificate)) { throw "Windows did not trust the certificate chain for $hostName" }
                $root = $chain.ChainElements[$chain.ChainElements.Count - 1].Certificate
                $trustedWindowsRoot = @(Get-ChildItem Cert:\CurrentUser\Root,Cert:\LocalMachine\Root | Where-Object Thumbprint -EQ $root.Thumbprint)
                if ($trustedWindowsRoot.Count -eq 0) { throw "Validated root for $hostName is absent from Windows trusted root stores." }
                if (-not $imported.ContainsKey($root.Thumbprint)) {
                    $certFile = Join-Path $trustDirectory ($root.Thumbprint + '.cer')
                    [IO.File]::WriteAllBytes($certFile, $root.Export([Security.Cryptography.X509Certificates.X509ContentType]::Cert))
                    & (Join-Path $javaRoot 'bin\keytool.exe') -importcert -noprompt -alias ('windows-' + $root.Thumbprint) -file $certFile -keystore $trustStore -storepass changeit
                    if ($LASTEXITCODE -ne 0) { throw 'Java trust-store import failed.' }
                    $imported[$root.Thumbprint] = $true
                    $pemRoots += '-----BEGIN CERTIFICATE-----' + "`n" + [Convert]::ToBase64String($root.RawData, [Base64FormattingOptions]::InsertLineBreaks) + "`n-----END CERTIFICATE-----"
                }
                Write-Output "Validated Windows-trusted TLS chain for $hostName"
            } finally { $chain.Dispose(); $certificate.Dispose() }
        } finally { $tlsStream.Dispose() }
    } finally { $tcpClient.Dispose() }
}
Set-Content -LiteralPath (Join-Path $trustDirectory 'windows-roots.pem') -Value ($pemRoots -join "`n") -Encoding ascii
$gradleUserHome = Join-Path $env:USERPROFILE '.gradle-simwarning'
New-Item -ItemType Directory -Path $gradleUserHome -Force | Out-Null
$portableTrustPath = $trustStore.Replace('\', '/')
Set-Content -LiteralPath (Join-Path $gradleUserHome 'gradle.properties') -Encoding ascii -Value @(
    "systemProp.javax.net.ssl.trustStore=$portableTrustPath",
    'systemProp.javax.net.ssl.trustStorePassword=changeit'
)
Write-Output 'Project-local Java TLS trust prepared. No machine trust settings changed.'
