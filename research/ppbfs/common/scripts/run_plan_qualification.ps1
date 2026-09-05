param(
    [Parameter(Mandatory = $true)] [string]$Distribution,
    [Parameter(Mandatory = $true)] [string]$Manifest,
    [Parameter(Mandatory = $true)] [string]$SourceRoot,
    [Parameter(Mandatory = $true)] [string]$OutputJsonl,
    [Parameter(Mandatory = $true)] [string]$LogPrefix
)

$ErrorActionPreference = 'Stop'
$distributionPath = (Resolve-Path -LiteralPath $Distribution).Path
$manifestPath = (Resolve-Path -LiteralPath $Manifest).Path
$sourceRootPath = (Resolve-Path -LiteralPath $SourceRoot).Path
$scriptPath = Join-Path $PSScriptRoot 'qualify_cypher_plans.py'
$outputPath = [IO.Path]::GetFullPath($OutputJsonl)
$logPath = [IO.Path]::GetFullPath($LogPrefix)
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

$launcher = $null
try {
    $launcher = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', 'bin\neo4j.bat console' `
        -WorkingDirectory $distributionPath -RedirectStandardOutput "$logPath.out.log" `
        -RedirectStandardError "$logPath.err.log" -WindowStyle Hidden -PassThru
    $deadline = (Get-Date).AddMinutes(3)
    $ready = $false
    do {
        Start-Sleep -Seconds 2
        try {
            $ready = (Invoke-WebRequest -Uri 'http://127.0.0.1:7474/' -TimeoutSec 2 -UseBasicParsing).StatusCode -eq 200
        } catch {
            $ready = $false
        }
    } while (-not $ready -and -not $launcher.HasExited -and (Get-Date) -lt $deadline)
    if (-not $ready) {
        throw "Neo4j did not become ready; inspect $logPath.out.log and $logPath.err.log"
    }
    & python -u $scriptPath $manifestPath $sourceRootPath $outputPath
    if ($LASTEXITCODE -ne 0) {
        throw "Plan qualifier exited with code $LASTEXITCODE"
    }
} finally {
    Stop-DistributionProcesses
    if ($launcher -and -not $launcher.HasExited) {
        Stop-Process -Id $launcher.Id -ErrorAction SilentlyContinue
    }
}
