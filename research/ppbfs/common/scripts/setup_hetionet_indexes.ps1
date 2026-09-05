param([Parameter(Mandatory = $true)][string]$Distribution)

$ErrorActionPreference = 'Stop'
$distributionPath = (Resolve-Path -LiteralPath $Distribution).Path
$logPrefix = Join-Path $distributionPath 'index-setup'
$launcher = $null
try {
    $launcher = Start-Process cmd.exe -ArgumentList '/c', 'bin\neo4j.bat console' `
        -WorkingDirectory $distributionPath -RedirectStandardOutput "$logPrefix.out.log" `
        -RedirectStandardError "$logPrefix.err.log" -WindowStyle Hidden -PassThru
    $ready = $false
    $deadline = (Get-Date).AddMinutes(3)
    while (-not $ready -and -not $launcher.HasExited -and (Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 2
        try { $ready = (Invoke-WebRequest 'http://127.0.0.1:7474/' -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200 } catch {}
    }
    if (-not $ready) { throw "Neo4j did not become ready: $distributionPath" }
    $statements = @(
        'CREATE INDEX hetio_gene_id IF NOT EXISTS FOR (n:Gene) ON (n.hetioId)',
        'CREATE INDEX hetio_compound_id IF NOT EXISTS FOR (n:Compound) ON (n.hetioId)',
        'CREATE INDEX hetio_disease_id IF NOT EXISTS FOR (n:Disease) ON (n.hetioId)',
        'CALL db.awaitIndexes(300)'
    )
    foreach ($statement in $statements) {
        $body = @{ statements = @(@{ statement = $statement }) } | ConvertTo-Json -Depth 4
        $response = Invoke-RestMethod 'http://127.0.0.1:7474/db/neo4j/tx/commit' `
            -Method Post -ContentType 'application/json' -Body $body
        if ($response.errors.Count) { throw ($response.errors | ConvertTo-Json -Depth 5) }
    }
} finally {
    $processes = @(Get-CimInstance Win32_Process | Where-Object {
        $_.Name -in @('java.exe', 'cmd.exe') -and $_.CommandLine -and
        $_.CommandLine.Contains($distributionPath, [StringComparison]::OrdinalIgnoreCase)
    })
    foreach ($process in $processes) { Stop-Process -Id $process.ProcessId -ErrorAction SilentlyContinue }
    if ($launcher -and -not $launcher.HasExited) { Stop-Process -Id $launcher.Id -ErrorAction SilentlyContinue }
}
