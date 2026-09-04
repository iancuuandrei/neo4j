param(
    [Parameter(Mandatory = $true)] [string]$Distribution,
    [Parameter(Mandatory = $true)] [string]$Manifest,
    [Parameter(Mandatory = $true)] [string]$OutputCsv,
    [Parameter(Mandatory = $true)] [string]$LogPrefix,
    [int]$Warmups = 1,
    [int]$Repetitions = 1,
    [int]$Seed = 20260904,
    [string]$ProfileJsonl,
    [string]$ProfileSummary
)

$ErrorActionPreference = 'Stop'
$distributionPath = (Resolve-Path -LiteralPath $Distribution).Path
$manifestPath = (Resolve-Path -LiteralPath $Manifest).Path
$scriptPath = Join-Path $PSScriptRoot 'cypher_http_benchmark.py'
$outputPath = [System.IO.Path]::GetFullPath($OutputCsv)
$logPath = [System.IO.Path]::GetFullPath($LogPrefix)
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $outputPath), (Split-Path -Parent $logPath) | Out-Null

function Get-DistributionProcesses {
    Get-CimInstance Win32_Process | Where-Object {
        $_.Name -in @('java.exe', 'powershell.exe') -and
        $_.CommandLine -and
        $_.CommandLine.Contains($distributionPath, [StringComparison]::OrdinalIgnoreCase)
    }
}

function Stop-DistributionProcesses {
    $processes = @(Get-DistributionProcesses)
    $parentIds = @($processes | ForEach-Object { [int]$_.ParentProcessId })
    foreach ($process in @($processes | Where-Object { $parentIds -notcontains [int]$_.ProcessId })) {
        Stop-Process -Id $process.ProcessId -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 2
    foreach ($process in @(Get-DistributionProcesses)) {
        Stop-Process -Id $process.ProcessId -ErrorAction SilentlyContinue
    }
}

if (@(Get-DistributionProcesses).Count -ne 0) {
    throw "Distribution already has running processes: $distributionPath"
}

$stdoutLog = "$logPath.out.log"
$stderrLog = "$logPath.err.log"
$launcher = $null
try {
    $launcher = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', 'bin\neo4j.bat console' `
        -WorkingDirectory $distributionPath -RedirectStandardOutput $stdoutLog `
        -RedirectStandardError $stderrLog -WindowStyle Hidden -PassThru

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

    $benchmarkArguments = @(
        '-u', $scriptPath, $manifestPath, $outputPath,
        '--warmups', $Warmups, '--repetitions', $Repetitions, '--seed', $Seed
    )
    if ($ProfileJsonl -or $ProfileSummary) {
        if (-not $ProfileJsonl -or -not $ProfileSummary) {
            throw 'ProfileJsonl and ProfileSummary must be supplied together.'
        }
        $profileJsonlPath = [IO.Path]::GetFullPath($ProfileJsonl)
        $profileSummaryPath = [IO.Path]::GetFullPath($ProfileSummary)
        $benchmarkArguments += @('--profile-jsonl', $profileJsonlPath, '--profile-summary', $profileSummaryPath)
    }
    & python @benchmarkArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Benchmark exited with code $LASTEXITCODE"
    }
} finally {
    Stop-DistributionProcesses
    if ($launcher -and -not $launcher.HasExited) {
        Stop-Process -Id $launcher.Id -ErrorAction SilentlyContinue
    }
}
