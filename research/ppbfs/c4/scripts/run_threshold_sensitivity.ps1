param(
    [Parameter(Mandatory = $true)] [string]$Experiment,
    [Parameter(Mandatory = $true)] [string]$BaselineSha,
    [Parameter(Mandatory = $true)] [string]$H4Sha,
    [Parameter(Mandatory = $true)] [string]$H8Sha,
    [Parameter(Mandatory = $true)] [string]$H16Sha,
    [Parameter(Mandatory = $true)] [string]$H4Distribution,
    [Parameter(Mandatory = $true)] [string]$H8Distribution,
    [Parameter(Mandatory = $true)] [string]$H16Distribution,
    [Parameter(Mandatory = $true)] [string]$Dataset,
    [Parameter(Mandatory = $true)] [string]$Manifest,
    [Parameter(Mandatory = $true)] [string]$Config,
    [Parameter(Mandatory = $true)] [string]$CommonScriptsDirectory,
    [Parameter(Mandatory = $true)] [string]$OutputDirectory,
    [int]$Forks = 3,
    [int]$Warmups = 1,
    [int]$Repetitions = 3,
    [int]$SeedBase = 20260906,
    [ValidateSet(1, 2)] [int]$ShortestCount = 2,
    [string]$QueryFile
)

$ErrorActionPreference = 'Stop'
if ($Forks -lt 2) { throw 'At least two independent JVM forks are required.' }
if ($Warmups -lt 1 -or $Repetitions -lt 1) { throw 'Warmups and repetitions must be positive.' }

$outputPath = [IO.Path]::GetFullPath($OutputDirectory)
if ([IO.Path]::GetPathRoot($outputPath) -ne 'D:\') {
    throw "Benchmark artifacts must be stored on D:, not $outputPath"
}

$commonScripts = (Resolve-Path -LiteralPath $CommonScriptsDirectory).Path
$runScript = Join-Path $commonScripts 'run_server_fork.ps1'
$metadataScript = Join-Path $commonScripts 'capture_run_metadata.ps1'
$resolvedDataset = (Resolve-Path -LiteralPath $Dataset).Path
$resolvedManifest = (Resolve-Path -LiteralPath $Manifest).Path
$resolvedConfig = (Resolve-Path -LiteralPath $Config).Path

$variants = @(
    [pscustomobject]@{ Name = 'h4'; Sha = $H4Sha; Distribution = (Resolve-Path -LiteralPath $H4Distribution).Path },
    [pscustomobject]@{ Name = 'h8'; Sha = $H8Sha; Distribution = (Resolve-Path -LiteralPath $H8Distribution).Path },
    [pscustomobject]@{ Name = 'h16'; Sha = $H16Sha; Distribution = (Resolve-Path -LiteralPath $H16Distribution).Path }
)

foreach ($largePath in @($resolvedDataset) + @($variants.Distribution)) {
    if ([IO.Path]::GetPathRoot($largePath) -ne 'D:\') {
        throw "Large benchmark inputs must be stored on D:, not $largePath"
    }
}

New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
$protocolPath = Join-Path $outputPath "$Experiment.protocol.json"
if (Test-Path -LiteralPath $protocolPath) {
    throw "Append-only protection: protocol already exists at $protocolPath"
}

$protocol = [ordered]@{
    schemaVersion = 1
    experiment = $Experiment
    createdAt = (Get-Date).ToUniversalTime().ToString('o')
    baselineSha = $BaselineSha
    variants = @($variants | ForEach-Object { [ordered]@{ label = $_.Name; sha = $_.Sha; distribution = $_.Distribution } })
    forks = $Forks
    warmups = $Warmups
    repetitions = $Repetitions
    shortestCount = $ShortestCount
    queryFile = if ($QueryFile) { (Resolve-Path -LiteralPath $QueryFile).Path } else { $null }
    seedBase = $SeedBase
    ordering = 'seeded Fisher-Yates variant order within each three-way fork'
    manifest = $resolvedManifest
    dataset = $resolvedDataset
    config = $resolvedConfig
}
$protocol | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $protocolPath -Encoding utf8NoBOM

for ($fork = 1; $fork -le $Forks; $fork++) {
    $seed = $SeedBase + $fork - 1
    $random = [Random]::new($seed)
    $order = [Collections.ArrayList]::new()
    foreach ($variant in $variants) { [void]$order.Add($variant) }
    for ($index = $order.Count - 1; $index -gt 0; $index--) {
        $swap = $random.Next($index + 1)
        $value = $order[$index]
        $order[$index] = $order[$swap]
        $order[$swap] = $value
    }

    foreach ($variant in $order) {
        $stem = "$Experiment-$($variant.Name)-fork$fork-seed$seed"
        $csvPath = Join-Path $outputPath "$stem.csv"
        $logPrefix = Join-Path $outputPath "$stem-server"
        $metadataPath = Join-Path $outputPath "$stem.metadata.json"
        foreach ($path in @($csvPath, $metadataPath, "$logPrefix.out.log", "$logPrefix.err.log")) {
            if (Test-Path -LiteralPath $path) { throw "Append-only protection: output already exists at $path" }
        }

        & $runScript -Distribution $variant.Distribution -Manifest $resolvedManifest `
            -OutputCsv $csvPath -LogPrefix $logPrefix -Warmups $Warmups `
            -Repetitions $Repetitions -Seed $seed -ShortestCount $ShortestCount -QueryFile $QueryFile
        if ($LASTEXITCODE -ne 0) { throw "$stem benchmark failed with exit code $LASTEXITCODE" }

        & $metadataScript -Experiment $Experiment -BaselineSha $BaselineSha `
            -VariantSha $variant.Sha -Distribution $variant.Distribution `
            -Dataset $resolvedDataset -QueryManifest $resolvedManifest `
            -Config $resolvedConfig -Output $metadataPath -Warmups $Warmups `
            -Repetitions $Repetitions -Seed $seed
        if ($LASTEXITCODE -ne 0) { throw "$stem metadata capture failed with exit code $LASTEXITCODE" }
        if ($QueryFile) {
            $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json -AsHashtable
            $resolvedQueryFile = (Resolve-Path -LiteralPath $QueryFile).Path
            $metadata.inputs.queryFile = [ordered]@{
                file = $resolvedQueryFile
                sha256 = (Get-FileHash -LiteralPath $resolvedQueryFile -Algorithm SHA256).Hash
            }
            $metadata | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $metadataPath -Encoding utf8NoBOM
        }
    }
}

Write-Host "Completed $Forks synchronized H=4/H=8/H=16 JVM forks in $outputPath"
