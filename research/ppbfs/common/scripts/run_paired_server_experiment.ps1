param(
    [Parameter(Mandatory = $true)] [string]$Experiment,
    [Parameter(Mandatory = $true)] [string]$BaselineSha,
    [Parameter(Mandatory = $true)] [string]$BaselineVariantSha,
    [Parameter(Mandatory = $true)] [string]$CandidateSha,
    [string]$BaselineLabel = 'b0',
    [string]$CandidateLabel = 'candidate',
    [Parameter(Mandatory = $true)] [string]$BaselineDistribution,
    [Parameter(Mandatory = $true)] [string]$CandidateDistribution,
    [Parameter(Mandatory = $true)] [string]$Dataset,
    [Parameter(Mandatory = $true)] [string]$Manifest,
    [Parameter(Mandatory = $true)] [string]$Config,
    [Parameter(Mandatory = $true)] [string]$OutputDirectory,
    [int]$Forks = 5,
    [int]$Warmups = 1,
    [int]$Repetitions = 10,
    [int]$SeedBase = 20260904,
    [ValidateSet(1, 2)] [int]$ShortestCount = 2,
    [string]$QueryFile
)

$ErrorActionPreference = 'Stop'

if ($Forks -lt 2) {
    throw 'At least two independent JVM forks are required.'
}
if ($Warmups -lt 1 -or $Repetitions -lt 1) {
    throw 'Warmups and repetitions must both be positive.'
}

$outputPath = [IO.Path]::GetFullPath($OutputDirectory)
if ([IO.Path]::GetPathRoot($outputPath) -ne 'D:\') {
    throw "Benchmark artifacts must be stored on D:, not $outputPath"
}

$runScript = Join-Path $PSScriptRoot 'run_server_fork.ps1'
$metadataScript = Join-Path $PSScriptRoot 'capture_run_metadata.ps1'
$resolvedDataset = (Resolve-Path -LiteralPath $Dataset).Path
$resolvedManifest = (Resolve-Path -LiteralPath $Manifest).Path
$resolvedConfig = (Resolve-Path -LiteralPath $Config).Path
$resolvedBaseline = (Resolve-Path -LiteralPath $BaselineDistribution).Path
$resolvedCandidate = (Resolve-Path -LiteralPath $CandidateDistribution).Path

foreach ($largePath in @($resolvedDataset, $resolvedBaseline, $resolvedCandidate)) {
    if ([IO.Path]::GetPathRoot($largePath) -ne 'D:\') {
        throw "Large benchmark inputs must be stored on D:, not $largePath"
    }
}

New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
$protocolPath = Join-Path $outputPath "$Experiment.protocol.json"
if (Test-Path -LiteralPath $protocolPath) {
    throw "Append-only protection: protocol already exists at $protocolPath"
}

$variants = @(
    [pscustomobject]@{ Name = $BaselineLabel; Sha = $BaselineVariantSha; Distribution = $resolvedBaseline },
    [pscustomobject]@{ Name = $CandidateLabel; Sha = $CandidateSha; Distribution = $resolvedCandidate }
)

$protocol = [ordered]@{
    schemaVersion = 1
    experiment = $Experiment
    createdAt = (Get-Date).ToUniversalTime().ToString('o')
    baselineSha = $BaselineSha
    baselineVariantSha = $BaselineVariantSha
    candidateSha = $CandidateSha
    baselineLabel = $BaselineLabel
    candidateLabel = $CandidateLabel
    forks = $Forks
    warmups = $Warmups
    repetitions = $Repetitions
    shortestCount = $ShortestCount
    queryFile = if ($QueryFile) { (Resolve-Path -LiteralPath $QueryFile).Path } else { $null }
    seedBase = $SeedBase
    ordering = 'seeded random variant order within each paired fork'
    manifest = $resolvedManifest
    dataset = $resolvedDataset
    config = $resolvedConfig
}
$protocol | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $protocolPath -Encoding utf8NoBOM

for ($fork = 1; $fork -le $Forks; $fork++) {
    $seed = $SeedBase + $fork - 1
    $order = @($variants)
    if (([Random]::new($seed)).Next(2) -eq 1) {
        [array]::Reverse($order)
    }

    foreach ($variant in $order) {
        $stem = "$Experiment-$($variant.Name)-fork$fork-seed$seed"
        $csvPath = Join-Path $outputPath "$stem.csv"
        $logPrefix = Join-Path $outputPath "$stem-server"
        $metadataPath = Join-Path $outputPath "$stem.metadata.json"
        $reserved = @($csvPath, $metadataPath, "$logPrefix.out.log", "$logPrefix.err.log")
        foreach ($path in $reserved) {
            if (Test-Path -LiteralPath $path) {
                throw "Append-only protection: output already exists at $path"
            }
        }

        & $runScript -Distribution $variant.Distribution -Manifest $resolvedManifest `
            -OutputCsv $csvPath -LogPrefix $logPrefix -Warmups $Warmups `
            -Repetitions $Repetitions -Seed $seed -ShortestCount $ShortestCount -QueryFile $QueryFile
        if ($LASTEXITCODE -ne 0) {
            throw "$stem benchmark failed with exit code $LASTEXITCODE"
        }

        & $metadataScript -Experiment $Experiment -BaselineSha $BaselineSha `
            -VariantSha $variant.Sha -Distribution $variant.Distribution `
            -Dataset $resolvedDataset -QueryManifest $resolvedManifest `
            -Config $resolvedConfig -Output $metadataPath -Warmups $Warmups `
            -Repetitions $Repetitions -Seed $seed
        if ($QueryFile) {
            $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json -AsHashtable
            $resolvedQueryFile = (Resolve-Path -LiteralPath $QueryFile).Path
            $metadata.inputs.queryFile = [ordered]@{
                file = $resolvedQueryFile
                sha256 = (Get-FileHash -LiteralPath $resolvedQueryFile -Algorithm SHA256).Hash
            }
            $metadata | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $metadataPath -Encoding utf8NoBOM
        }
        if ($LASTEXITCODE -ne 0) {
            throw "$stem metadata capture failed with exit code $LASTEXITCODE"
        }
    }
}

Write-Host "Completed $Forks paired JVM forks in $outputPath"
