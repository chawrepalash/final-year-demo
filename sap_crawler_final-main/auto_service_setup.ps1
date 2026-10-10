# SAP 1-Click Auto Service Setup and POJO Generator (PowerShell)
param(
    [Parameter(Position=0)]
    [string]$ServiceName = "usagerecords",

    [Parameter(Position=1)]
    [string]$ServiceUrl = "https://sandbox.api.sap.com/subscriptionbilling/api/usage-record/v1/usage-records?paging=OFFSET"
)

Write-Host "=================================================================="
Write-Host "  SAP 1-Click Auto Service Setup and POJO Generator (PowerShell)  "
Write-Host "=================================================================="
Write-Host ""
Write-Host "Service:  $ServiceName"
Write-Host "Endpoint: $ServiceUrl"
Write-Host ""

# 1. Resolve Maven Command
$mvnCmd = "mvn"
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    $ideaMvn = "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2023.3.2\plugins\maven\lib\maven3\bin\mvn.cmd"
    if (Test-Path $ideaMvn) {
        $mvnCmd = $ideaMvn
    } else {
        Write-Error "[ERROR] Maven not found. Please install Maven or check IntelliJ path."
        exit 1
    }
}

# 2. Database Defaults
$dbUser = if ($env:DB_USER) { $env:DB_USER } else { "postgres" }
$dbPass = if ($env:DB_PASS) { $env:DB_PASS } else { "flash" }
$dbName = if ($env:DB_NAME) { $env:DB_NAME } else { "sap_bills" }

# 3. Compile Setup Manager
Write-Host "[1/2] Compiling Setup Manager..."
& $mvnCmd compile
if ($LASTEXITCODE -ne 0) {
    Write-Error "[ERROR] Maven compilation failed!"
    exit 1
}

# 4. Run Auto Service Manager via Environment Variables (100% immune to URL shell corruption)
Write-Host ""
Write-Host "[2/2] Running Auto Service Manager for '$ServiceName'..."

$env:SERVICE_NAME = $ServiceName
$env:SERVICE_URL = $ServiceUrl

& $mvnCmd exec:java "-Dexec.mainClass=projectsap.sap.common.SapAutoServiceManager" "-Ddb.user=$dbUser" "-Ddb.pass=$dbPass" "-Ddb.name=$dbName"

Write-Host ""
Write-Host "=================================================================="
Write-Host "  Done! Check src/main/java/projectsap/sap/$ServiceName/model"
Write-Host "=================================================================="
