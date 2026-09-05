param(
    [Parameter(Mandatory = $true)] [string]$Experiment,
    [Parameter(Mandatory = $true)] [string]$BaselineSha,
    [Parameter(Mandatory = $true)] [string]$BaselineVariantSha,
    [Parameter(Mandatory = $true)] [string]$CandidateSha,
    [Parameter(Mandatory = $true)] [string]$BaselineDistribution,
    [Parameter(Mandatory = $true)] [string]$CandidateDistribution,
    [Parameter(Mandatory = $true)] [string]$Manifest,
    [Parameter(Mandatory = $true)] [string]$OutputDirectory,
    [string]$BaselineLabel = 'b0',
    [string]$CandidateLabel = 'c1',
    [string[]]$OnlyLimit,
    [string]$QueryFile
)

$ErrorActionPreference = 'Stop'
$outputPath = [IO.Path]::GetFullPath($OutputDirectory)
if ([IO.Path]::GetPathRoot($outputPath) -ne 'D:\') {
    throw "Memory-limit artifacts must be stored on D:, not $outputPath"
}

$manifestPath = (Resolve-Path -LiteralPath $Manifest).Path
$baselinePath = (Resolve-Path -LiteralPath $BaselineDistribution).Path
$candidatePath = (Resolve-Path -LiteralPath $CandidateDistribution).Path
foreach ($largePath in @($baselinePath, $candidatePath)) {
    if ([IO.Path]::GetPathRoot($largePath) -ne 'D:\') {
        throw "Large benchmark inputs must be stored on D:, not $largePath"
    }
}

$protocolPath = Join-Path $outputPath "$Experiment.protocol.json"
if (Test-Path -LiteralPath $protocolPath) {
    throw "Append-only protection: protocol already exists at $protocolPath"
}
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null

$cases = @(Import-Csv -LiteralPath $manifestPath)
if ($cases.Count -eq 0) {
    throw 'Memory-limit manifest is empty.'
}
$limits = @($cases | ForEach-Object { $_.limit.ToLowerInvariant() } | Select-Object -Unique)
if ($OnlyLimit) {
    $requestedLimits = @($OnlyLimit | ForEach-Object { $_.ToLowerInvariant() })
    $missingLimits = @($requestedLimits | Where-Object { $limits -notcontains $_ })
    if ($missingLimits.Count -ne 0) {
        throw "Requested limits are absent from the manifest: $($missingLimits -join ', ')"
    }
    $limits = @($limits | Where-Object { $requestedLimits -contains $_ })
}
$variants = @(
    [pscustomobject]@{ Name = $BaselineLabel; Sha = $BaselineVariantSha; Distribution = $baselinePath },
    [pscustomobject]@{ Name = $CandidateLabel; Sha = $CandidateSha; Distribution = $candidatePath }
)

$protocol = [ordered]@{
    schemaVersion = 1
    experiment = $Experiment
    createdAt = (Get-Date).ToUniversalTime().ToString('o')
    baselineSha = $BaselineSha
    baselineVariantSha = $BaselineVariantSha
    candidateSha = $CandidateSha
    manifest = $manifestPath
    isolation = 'fresh Neo4j process and isolated NEO4J_CONF directory per variant and fixed limit'
    limits = $limits
    variants = @($variants | ForEach-Object { [ordered]@{ name = $_.Name; sha = $_.Sha; distribution = $_.Distribution } })
}
$protocol | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $protocolPath -Encoding utf8NoBOM

$probeScript = Join-Path $PSScriptRoot 'cypher_http_memory_limits.py'

function Get-DistributionProcesses([string]$DistributionPath) {
    Get-CimInstance Win32_Process | Where-Object {
        $_.Name -in @('java.exe', 'powershell.exe', 'cmd.exe') -and
        $_.CommandLine -and
        $_.CommandLine.Contains($DistributionPath, [StringComparison]::OrdinalIgnoreCase)
    }
}

function Stop-DistributionProcesses([string]$DistributionPath) {
    $processes = @(Get-DistributionProcesses $DistributionPath)
    $parentIds = @($processes | ForEach-Object { [int]$_.ParentProcessId })
    foreach ($process in @($processes | Where-Object { $parentIds -notcontains [int]$_.ProcessId })) {
        Stop-Process -Id $process.ProcessId -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 2
    foreach ($process in @(Get-DistributionProcesses $DistributionPath)) {
        Stop-Process -Id $process.ProcessId -ErrorAction SilentlyContinue
    }
}

foreach ($limit in $limits) {
    $safeLimit = $limit -replace '[^A-Za-z0-9_-]', '_'
    foreach ($variant in $variants) {
        $distributionPath = $variant.Distribution
        if (@(Get-DistributionProcesses $distributionPath).Count -ne 0) {
            throw "Distribution already has running processes: $distributionPath"
        }

        $stem = "$Experiment-$($variant.Name)-$safeLimit"
        $confPath = Join-Path $outputPath "configs\$stem"
        $jsonlPath = Join-Path $outputPath "$stem.jsonl"
        $stdoutLog = Join-Path $outputPath "$stem-server.out.log"
        $stderrLog = Join-Path $outputPath "$stem-server.err.log"
        foreach ($reserved in @($confPath, $jsonlPath, $stdoutLog, $stderrLog)) {
            if (Test-Path -LiteralPath $reserved) {
                throw "Append-only protection: output already exists at $reserved"
            }
        }

        Copy-Item -LiteralPath (Join-Path $distributionPath 'conf') -Destination $confPath -Recurse
        Add-Content -LiteralPath (Join-Path $confPath 'neo4j.conf') `
            -Value "`n# PPBFS near-limit experiment: $Experiment`ndb.memory.transaction.max=$limit" `
            -Encoding utf8

        $launcher = $null
        try {
            $oldNeo4jConf = $env:NEO4J_CONF
            $env:NEO4J_CONF = $confPath
            try {
                $launcher = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', 'bin\neo4j.bat console' `
                    -WorkingDirectory $distributionPath -RedirectStandardOutput $stdoutLog `
                    -RedirectStandardError $stderrLog -WindowStyle Hidden -PassThru
            } finally {
                if ($null -eq $oldNeo4jConf) {
                    Remove-Item Env:\NEO4J_CONF -ErrorAction SilentlyContinue
                } else {
                    $env:NEO4J_CONF = $oldNeo4jConf
                }
            }

            $deadline = (Get-Date).AddMinutes(3)
            $ready = $false
            do {
                Start-Sleep -Seconds 2
                try {
                    $response = Invoke-WebRequest -Uri 'http://127.0.0.1:7474/' -TimeoutSec 2 -UseBasicParsing
                    $ready = $response.StatusCode -eq 200
                } catch {
                    $ready = $false
                }
            } while (-not $ready -and -not $launcher.HasExited -and (Get-Date) -lt $deadline)
            if (-not $ready) {
                throw "Neo4j did not become ready; inspect $stdoutLog and $stderrLog"
            }

            $role = if ($variant.Name -eq $BaselineLabel) { 'baseline' } else { 'candidate' }
            $probeArguments = @('-u', $probeScript, $manifestPath, $jsonlPath, '--limit', $limit, '--role', $role)
            if ($QueryFile) { $probeArguments += @('--query-file', (Resolve-Path -LiteralPath $QueryFile).Path) }
            & python @probeArguments
            if ($LASTEXITCODE -ne 0) {
                throw "$stem probe exited with code $LASTEXITCODE"
            }
        } finally {
            Stop-DistributionProcesses $distributionPath
            if ($launcher -and -not $launcher.HasExited) {
                Stop-Process -Id $launcher.Id -ErrorAction SilentlyContinue
            }
        }
    }
}

Write-Host "Completed $($variants.Count) variants across $($limits.Count) fixed memory limits in $outputPath"
