param(
    [Parameter(Mandatory = $true)] [string]$DatasetName,
    [Parameter(Mandatory = $true)] [string]$PreparedCsvDirectory,
    [Parameter(Mandatory = $true)] [string]$BaselineDistribution,
    [Parameter(Mandatory = $true)] [string]$CandidateDistribution,
    [Parameter(Mandatory = $true)] [string]$OutputRoot
)

$ErrorActionPreference = 'Stop'

$prepared = (Resolve-Path -LiteralPath $PreparedCsvDirectory).Path
$baselineTemplate = (Resolve-Path -LiteralPath $BaselineDistribution).Path
$candidateTemplate = (Resolve-Path -LiteralPath $CandidateDistribution).Path
$output = [IO.Path]::GetFullPath($OutputRoot)

foreach ($path in @($prepared, $baselineTemplate, $candidateTemplate, $output)) {
    if ([IO.Path]::GetPathRoot($path) -ne 'D:\') {
        throw "Large benchmark inputs and outputs must remain on D:, not $path"
    }
}
if (Test-Path -LiteralPath $output) {
    throw "Append-only protection: output already exists at $output"
}

$requiredCsv = @('nodes-header.csv', 'nodes.csv', 'relationships-header.csv', 'relationships.csv')
foreach ($name in $requiredCsv) {
    if (-not (Test-Path -LiteralPath (Join-Path $prepared $name) -PathType Leaf)) {
        throw "Missing prepared CSV file: $name"
    }
}

$distributionName = Split-Path -Leaf $baselineTemplate
$baselineOutput = Join-Path $output "b0\$distributionName"
$candidateOutput = Join-Path $output "c1\$distributionName"
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $baselineOutput) | Out-Null
Copy-Item -LiteralPath $baselineTemplate -Destination $baselineOutput -Recurse

$admin = Join-Path $baselineOutput 'bin\neo4j-admin.bat'
$report = Join-Path $output "$DatasetName-import.report"
& $admin database import full neo4j `
    "--nodes=$(Join-Path $prepared 'nodes-header.csv'),$(Join-Path $prepared 'nodes.csv')" `
    "--relationships=$(Join-Path $prepared 'relationships-header.csv'),$(Join-Path $prepared 'relationships.csv')" `
    '--id-type=integer' '--overwrite-destination=true' "--report-file=$report"
if ($LASTEXITCODE -ne 0) {
    throw "neo4j-admin import failed with exit code $LASTEXITCODE"
}

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

function Invoke-Cypher([string]$Statement) {
    $body = @{ statements = @(@{ statement = $Statement }) } | ConvertTo-Json -Depth 4 -Compress
    $response = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:7474/db/neo4j/tx/commit' `
        -ContentType 'application/json' -Body $body
    if ($response.errors.Count -ne 0) {
        throw "Cypher failed: $($response.errors | ConvertTo-Json -Compress)"
    }
}

$serverOut = Join-Path $output "$DatasetName-index-server.out.log"
$serverErr = Join-Path $output "$DatasetName-index-server.err.log"
$launcher = $null
try {
    $launcher = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', 'bin\neo4j.bat console' `
        -WorkingDirectory $baselineOutput -RedirectStandardOutput $serverOut `
        -RedirectStandardError $serverErr -WindowStyle Hidden -PassThru
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
        throw "Neo4j did not become ready for index creation; inspect $serverOut and $serverErr"
    }
    Invoke-Cypher 'CREATE INDEX snap_node_id IF NOT EXISTS FOR (n:SnapNode) ON (n.snapId)'
    Invoke-Cypher 'CALL db.awaitIndexes(300)'
} finally {
    Stop-DistributionProcesses $baselineOutput
    if ($launcher -and -not $launcher.HasExited) {
        Stop-Process -Id $launcher.Id -ErrorAction SilentlyContinue
    }
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $candidateOutput) | Out-Null
Copy-Item -LiteralPath $baselineOutput -Destination $candidateOutput -Recurse

$candidateRuntimeJar = @(Get-ChildItem -LiteralPath (Join-Path $candidateTemplate 'lib') -Filter '*runtime-util*.jar' -File)
$outputRuntimeJar = @(Get-ChildItem -LiteralPath (Join-Path $candidateOutput 'lib') -Filter '*runtime-util*.jar' -File)
if ($candidateRuntimeJar.Count -ne 1 -or $outputRuntimeJar.Count -ne 1) {
    throw 'Expected exactly one runtime-util JAR in each candidate distribution.'
}
$baselineRuntimeJarHash = (Get-FileHash -LiteralPath $outputRuntimeJar[0].FullName -Algorithm SHA256).Hash
Copy-Item -LiteralPath $candidateRuntimeJar[0].FullName -Destination $outputRuntimeJar[0].FullName -Force
$candidateRuntimeJarHash = (Get-FileHash -LiteralPath $outputRuntimeJar[0].FullName -Algorithm SHA256).Hash

$databasePath = Join-Path $baselineOutput 'data\databases\neo4j'
$databaseFiles = @(Get-ChildItem -LiteralPath $databasePath -File -Recurse | Sort-Object FullName)
$manifestLines = @($databaseFiles | ForEach-Object {
    $relative = [IO.Path]::GetRelativePath($databasePath, $_.FullName).Replace('\', '/')
    "$relative|$($_.Length)|$((Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash)"
})
$manifestBytes = [Text.Encoding]::UTF8.GetBytes(($manifestLines -join "`n"))
$databaseHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($manifestBytes))

$record = [ordered]@{
    schemaVersion = 1
    createdAt = (Get-Date).ToUniversalTime().ToString('o')
    dataset = $DatasetName
    preparedCsvDirectory = $prepared
    preparedCsv = @($requiredCsv | ForEach-Object {
        $file = Get-Item -LiteralPath (Join-Path $prepared $_)
        [ordered]@{ name = $_; bytes = $file.Length; sha256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash }
    })
    baselineDistribution = $baselineOutput
    candidateDistribution = $candidateOutput
    databaseManifestSha256 = $databaseHash
    databaseFileCount = $databaseFiles.Count
    databaseBytes = [int64](($databaseFiles | Measure-Object Length -Sum).Sum)
    baselineRuntimeJarSha256 = $baselineRuntimeJarHash
    candidateRuntimeJarSha256 = $candidateRuntimeJarHash
}
$record | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $output 'preparation.json') -Encoding utf8NoBOM
Write-Host "Prepared identical $DatasetName stores for B0 and C1 at $output"
