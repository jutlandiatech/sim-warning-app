$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$specificationPath = Join-Path $repoRoot 'SPECIFICATION.md'
$architecturePath = Join-Path $repoRoot 'docs\ARCHITECTURE_UML.md'
$outputPath = Join-Path $repoRoot 'docs\Sim-Warning-Specification-and-Architecture.pdf'

$edgeCandidates = @(
    (Join-Path ${env:ProgramFiles(x86)} 'Microsoft\Edge\Application\msedge.exe'),
    (Join-Path $env:ProgramFiles 'Microsoft\Edge\Application\msedge.exe')
)
$edgePath = $edgeCandidates | Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -First 1
if (-not $edgePath) {
    throw 'Microsoft Edge was not found. Install Edge to export the documentation PDF.'
}

function Convert-InlineMarkdown {
    param(
        [Parameter(Mandatory = $true)][string]$Text,
        [Parameter(Mandatory = $true)][string]$SourcePath
    )

    $value = [System.Net.WebUtility]::HtmlEncode($Text)
    $codeSpans = [System.Collections.Generic.List[string]]::new()
    $value = [regex]::Replace($value, '`([^`]+)`', [System.Text.RegularExpressions.MatchEvaluator]{
        param($match)
        $index = $codeSpans.Count
        $codeSpans.Add("<code>$($match.Groups[1].Value)</code>")
        return "%%CODE$index%%"
    })
    $value = [regex]::Replace($value, '\[([^\]]+)\]\(([^)]+)\)', [System.Text.RegularExpressions.MatchEvaluator]{
        param($match)
        $label = $match.Groups[1].Value
        $target = [System.Net.WebUtility]::HtmlDecode($match.Groups[2].Value)
        if ($target -match '^https?://') {
            $href = [System.Net.WebUtility]::HtmlEncode($target)
        } elseif ($target.StartsWith('#')) {
            $href = $target
        } else {
            $relativePath = $target.Split('#')[0]
            $sourceDirectory = Split-Path -Parent $SourcePath
            $linkedPath = [System.IO.Path]::GetFullPath((Join-Path $sourceDirectory $relativePath))
            if ($linkedPath.StartsWith($repoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
                $repoRelativePath = $linkedPath.Substring($repoRoot.Length).TrimStart('\').Replace('\', '/')
                $href = "https://github.com/jutlandiatech2025/sim-warning-app/blob/main/$repoRelativePath"
                if ($target.Contains('#')) {
                    $href += '#' + $target.Split('#', 2)[1]
                }
            } else {
                $href = [System.Net.WebUtility]::HtmlEncode($target)
            }
        }
        return "<a href=`"$href`">$label</a>"
    })
    $value = [regex]::Replace($value, '\*\*(.+?)\*\*', '<strong>$1</strong>')
    $value = [regex]::Replace($value, '(?<!\*)\*([^*]+)\*(?!\*)', '<em>$1</em>')
    for ($index = 0; $index -lt $codeSpans.Count; $index++) {
        $value = $value.Replace("%%CODE$index%%", $codeSpans[$index])
    }
    return $value
}

function Get-HeadingSlug {
    param([string]$Text)
    $slug = $Text.ToLowerInvariant() -replace '[^a-z0-9]+', '-'
    return $slug.Trim('-')
}

function Convert-MarkdownDocument {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [switch]$StartOnNewPage
    )

    $lines = [System.IO.File]::ReadAllLines($Path)
    $html = [System.Text.StringBuilder]::new()
    if ($StartOnNewPage) {
        [void]$html.AppendLine('<div class="page-break"></div>')
    }

    $index = 0
    while ($index -lt $lines.Length) {
        $line = $lines[$index]
        if ([string]::IsNullOrWhiteSpace($line)) {
            $index++
            continue
        }

        if ($line -match '^```(.*)$') {
            $language = $Matches[1].Trim().ToLowerInvariant()
            $index++
            $block = [System.Text.StringBuilder]::new()
            while ($index -lt $lines.Length -and $lines[$index] -notmatch '^```\s*$') {
                [void]$block.AppendLine($lines[$index])
                $index++
            }
            if ($index -lt $lines.Length) { $index++ }
            $contents = [System.Net.WebUtility]::HtmlEncode($block.ToString())
            if ($language -eq 'mermaid') {
                [void]$html.AppendLine("<pre class=`"mermaid`">$contents</pre>")
            } else {
                [void]$html.AppendLine("<pre><code>$contents</code></pre>")
            }
            continue
        }

        if ($line -match '^(#{1,6})\s+(.+?)\s*#*\s*$') {
            $level = $Matches[1].Length
            $title = $Matches[2]
            $slug = Get-HeadingSlug $title
            $content = Convert-InlineMarkdown -Text $title -SourcePath $Path
            [void]$html.AppendLine("<h$level id=`"$slug`">$content</h$level>")
            $index++
            continue
        }

        if ($line -match '^\s*\|.*\|\s*$' -and
            ($index + 1) -lt $lines.Length -and
            $lines[$index + 1] -match '^\s*\|[\s:|-]+\|\s*$') {
            [void]$html.AppendLine('<table><thead>')
            $headerCells = @($line.Trim().Trim('|').Split('|') | ForEach-Object { $_.Trim() })
            [void]$html.AppendLine('<tr>')
            foreach ($cell in $headerCells) {
                $content = Convert-InlineMarkdown -Text $cell -SourcePath $Path
                [void]$html.AppendLine("<th>$content</th>")
            }
            [void]$html.AppendLine('</tr></thead><tbody>')
            $index += 2
            while ($index -lt $lines.Length -and $lines[$index] -match '^\s*\|.*\|\s*$') {
                $cells = @($lines[$index].Trim().Trim('|').Split('|') | ForEach-Object { $_.Trim() })
                [void]$html.AppendLine('<tr>')
                foreach ($cell in $cells) {
                    $content = Convert-InlineMarkdown -Text $cell -SourcePath $Path
                    [void]$html.AppendLine("<td>$content</td>")
                }
                [void]$html.AppendLine('</tr>')
                $index++
            }
            [void]$html.AppendLine('</tbody></table>')
            continue
        }

        if ($line -match '^\s*[-*+]\s+') {
            [void]$html.AppendLine('<ul>')
            while ($index -lt $lines.Length -and $lines[$index] -match '^\s*[-*+]\s+(.+)$') {
                $content = Convert-InlineMarkdown -Text $Matches[1] -SourcePath $Path
                [void]$html.AppendLine("<li>$content</li>")
                $index++
            }
            [void]$html.AppendLine('</ul>')
            continue
        }

        if ($line -match '^\s*\d+\.\s+') {
            [void]$html.AppendLine('<ol>')
            while ($index -lt $lines.Length -and $lines[$index] -match '^\s*\d+\.\s+(.+)$') {
                $content = Convert-InlineMarkdown -Text $Matches[1] -SourcePath $Path
                [void]$html.AppendLine("<li>$content</li>")
                $index++
            }
            [void]$html.AppendLine('</ol>')
            continue
        }

        if ($line -match '^\s*[-*_]{3,}\s*$') {
            [void]$html.AppendLine('<hr>')
            $index++
            continue
        }

        $paragraph = [System.Text.StringBuilder]::new()
        while ($index -lt $lines.Length -and
            -not [string]::IsNullOrWhiteSpace($lines[$index]) -and
            $lines[$index] -notmatch '^(#{1,6})\s+' -and
            $lines[$index] -notmatch '^```' -and
            $lines[$index] -notmatch '^\s*\|.*\|\s*$' -and
            $lines[$index] -notmatch '^\s*[-*+]\s+' -and
            $lines[$index] -notmatch '^\s*\d+\.\s+' -and
            $lines[$index] -notmatch '^\s*[-*_]{3,}\s*$') {
            if ($paragraph.Length -gt 0) { [void]$paragraph.Append(' ') }
            [void]$paragraph.Append($lines[$index].Trim())
            $index++
        }
        $content = Convert-InlineMarkdown -Text $paragraph.ToString() -SourcePath $Path
        [void]$html.AppendLine("<p>$content</p>")
    }

    return $html.ToString()
}

function Get-TableOfContents {
    param([string[]]$Paths)

    $items = [System.Text.StringBuilder]::new()
    foreach ($path in $Paths) {
        foreach ($line in [System.IO.File]::ReadAllLines($path)) {
            if ($line -match '^#{1,3}\s+(.+?)\s*#*\s*$') {
                $title = $Matches[1]
                $slug = Get-HeadingSlug $title
                $label = [System.Net.WebUtility]::HtmlEncode($title)
                [void]$items.AppendLine("<li><a href=`"#$slug`">$label</a></li>")
            }
        }
    }
    return $items.ToString()
}

$temporaryId = [guid]::NewGuid().ToString('N')
$htmlPath = Join-Path $env:TEMP "sim-warning-documentation-$temporaryId.html"
$profilePath = Join-Path $env:TEMP "sim-warning-edge-$temporaryId"
$domPath = Join-Path $env:TEMP "sim-warning-dom-$temporaryId.html"
$printOutputPath = Join-Path $env:TEMP "sim-warning-pdf-output-$temporaryId.log"
$renderLogPath = Join-Path $env:TEMP "sim-warning-render-$temporaryId.log"
$printLogPath = Join-Path $env:TEMP "sim-warning-print-$temporaryId.log"
$htmlUri = ([System.Uri]$htmlPath).AbsoluteUri

function Invoke-Edge {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$StandardOutputPath,
        [Parameter(Mandatory = $true)][string]$StandardErrorPath
    )

    $quotedArguments = @($Arguments | ForEach-Object { '"' + $_.Replace('"', '\"') + '"' })
    $process = Start-Process -FilePath $edgePath `
        -ArgumentList ($quotedArguments -join ' ') `
        -PassThru -Wait -NoNewWindow `
        -RedirectStandardOutput $StandardOutputPath `
        -RedirectStandardError $StandardErrorPath
    return $process.ExitCode
}

$style = @'
<style>
@page { size: A4; margin: 16mm 15mm 18mm; }
* { box-sizing: border-box; }
body { color: #17212b; font: 10pt/1.48 "Segoe UI", Arial, sans-serif; margin: 0; }
.cover { align-items: center; background: linear-gradient(140deg, #f5f9fd, #fff 70%); border-bottom: 5px solid #2769ad; display: flex; flex-direction: column; justify-content: center; min-height: 245mm; padding: 22mm; text-align: center; }
.cover .brand { color: #2769ad; font-size: 16pt; font-weight: 700; letter-spacing: .12em; text-transform: uppercase; }
.cover h1 { border: 0; color: #132b43; font-size: 32pt; line-height: 1.15; margin: 24mm 0 4mm; }
.cover .subtitle { color: #2769ad; font-size: 19pt; font-weight: 600; }
.cover .meta { color: #526273; margin-top: 18mm; }
.toc { border-bottom: 1px solid #d8e0e8; margin-bottom: 12mm; padding: 0 0 7mm; }
.toc ul { columns: 2; padding-left: 5mm; }
.toc li { break-inside: avoid; font-size: 9pt; margin: 1.5mm 0; }
h1, h2, h3, h4, h5, h6 { break-after: avoid-page; color: #183b5a; line-height: 1.25; }
h1 { border-bottom: 2px solid #2769ad; font-size: 23pt; margin: 12mm 0 6mm; padding-bottom: 2mm; }
h2 { border-bottom: 1px solid #d8e0e8; font-size: 16pt; margin: 9mm 0 3mm; padding-bottom: 1.5mm; }
h3 { font-size: 12pt; margin: 6mm 0 2mm; }
p { margin: 2.5mm 0 3mm; }
a { color: #175a9b; text-decoration: none; }
code { background: #f0f3f6; border-radius: 2px; font: 8.5pt Consolas, monospace; padding: .2mm 1mm; overflow-wrap: anywhere; }
pre { background: #f3f6f9; border: 1px solid #dce4eb; border-radius: 2mm; font: 7pt/1.3 Consolas, monospace; overflow-wrap: anywhere; padding: 3mm; white-space: pre-wrap; }
pre.mermaid { background: #fff; border: 0; break-inside: avoid; padding: 1mm; text-align: center; }
pre.mermaid svg { height: auto; max-height: 245mm; max-width: 100%; }
table { border-collapse: collapse; font-size: 8.5pt; margin: 3mm 0 5mm; width: 100%; }
thead { display: table-header-group; }
th { background: #eaf2f9; color: #183b5a; text-align: left; }
th, td { border: 1px solid #cbd7e2; padding: 1.8mm 2mm; vertical-align: top; }
tr { break-inside: avoid; }
ul, ol { margin: 2mm 0 4mm; padding-left: 7mm; }
li { margin: 1mm 0; }
hr { border: 0; border-top: 1px solid #d8e0e8; margin: 6mm 0; }
.page-break { break-before: page; }
@media screen { body { margin: 20px auto; max-width: 900px; padding: 0 24px; } }
</style>
'@

$mermaidScript = @'
<script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
<script>
mermaid.initialize({ startOnLoad: false, securityLevel: "strict", theme: "default", flowchart: { useMaxWidth: true }, sequence: { useMaxWidth: true }, class: { useMaxWidth: true } });
mermaid.run().then(function () {
  document.documentElement.setAttribute("data-mermaid-ready", "true");
}).catch(function (error) {
  document.documentElement.setAttribute("data-mermaid-error", String(error));
});
</script>
'@

try {
    $specHtml = Convert-MarkdownDocument -Path $specificationPath
    $architectureHtml = Convert-MarkdownDocument -Path $architecturePath -StartOnNewPage
    $toc = Get-TableOfContents -Paths @($specificationPath, $architecturePath)
    $cover = @'
<section class="cover">
  <div class="brand">Sim Warning</div>
  <h1>Specification &amp;<br>Architecture</h1>
  <div class="subtitle">Requirements, acceptance scenarios and UML</div>
  <div class="meta">Application version 1.0.0<br>Specification version 0.5 (15 October 2026)<br>Architecture diagrams reflect the current implementation</div>
</section>
'@
    $document = @"
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Sim Warning — Specification and Architecture</title>
$style
</head>
<body>
$cover
<nav class="toc"><h2>Contents</h2><ul>$toc</ul></nav>
$specHtml
$architectureHtml
$mermaidScript
</body>
</html>
"@
    [System.IO.File]::WriteAllText($htmlPath, $document, [System.Text.UTF8Encoding]::new($false))

    $commonArguments = @(
        '--headless=new',
        '--disable-gpu',
        '--no-first-run',
        "--user-data-dir=$profilePath",
        '--virtual-time-budget=30000'
    )
    $renderExitCode = Invoke-Edge -Arguments ($commonArguments + @('--dump-dom', $htmlUri)) `
        -StandardOutputPath $domPath -StandardErrorPath $renderLogPath
    if ($renderExitCode -ne 0) {
        $renderLog = if (Test-Path -LiteralPath $renderLogPath) { Get-Content -LiteralPath $renderLogPath -Raw } else { '' }
        throw "Edge could not render the documentation HTML: $renderLog"
    }
    $renderedHtml = [System.IO.File]::ReadAllText($domPath)
    if ($renderedHtml -notmatch 'data-mermaid-ready="true"') {
        if ($renderedHtml -match 'data-mermaid-error="([^"]+)"') {
            throw "Mermaid could not render the UML diagrams: $($Matches[1])"
        }
        throw 'Mermaid did not finish rendering the UML diagrams. Check network access and retry.'
    }
    $svgCount = ([regex]::Matches($renderedHtml, '<svg\b')).Count
    if ($svgCount -lt 6) {
        throw "Expected six rendered UML diagrams, but found $svgCount."
    }

    $printArguments = $commonArguments + @(
        '--no-pdf-header-footer',
        "--print-to-pdf=$outputPath",
        $htmlUri
    )
    $printExitCode = Invoke-Edge -Arguments $printArguments `
        -StandardOutputPath $printOutputPath -StandardErrorPath $printLogPath
    if ($printExitCode -ne 0) {
        $printLog = if (Test-Path -LiteralPath $printLogPath) { Get-Content -LiteralPath $printLogPath -Raw } else { '' }
        throw "Edge could not create the PDF: $printLog"
    }
    if (-not (Test-Path -LiteralPath $outputPath) -or (Get-Item -LiteralPath $outputPath).Length -lt 10000) {
        throw "The generated PDF is missing or unexpectedly small: $outputPath"
    }

    Write-Output "Created $outputPath"
    Write-Output "Verified $svgCount rendered UML diagrams."
} finally {
    if (Test-Path -LiteralPath $htmlPath) {
        Remove-Item -LiteralPath $htmlPath -Force
    }
    if (Test-Path -LiteralPath $profilePath) {
        Remove-Item -LiteralPath $profilePath -Recurse -Force
    }
    foreach ($logPath in @($renderLogPath, $printLogPath)) {
        if (Test-Path -LiteralPath $logPath) {
            Remove-Item -LiteralPath $logPath -Force
        }
    }
    foreach ($temporaryPath in @($domPath, $printOutputPath)) {
        if (Test-Path -LiteralPath $temporaryPath) {
            Remove-Item -LiteralPath $temporaryPath -Force
        }
    }
}
