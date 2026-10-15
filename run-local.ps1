# Starts the backend on Windows. Run it from the "backend" folder:
#   .\run-local.ps1
# It reads settings from the .env file in this repository and then starts Spring Boot.

$envFile = Join-Path $PSScriptRoot ".env"
if (-not (Test-Path $envFile)) {
    Write-Host "Cannot find .env. Copy .env.example to .env and edit it first." -ForegroundColor Red
    exit 1
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $index = $line.IndexOf("=")
    if ($index -lt 1) { return }
    $name = $line.Substring(0, $index).Trim()
    $value = $line.Substring($index + 1).Trim()
    [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
}

Write-Host "Environment loaded. Starting the backend on http://localhost:8080 ..." -ForegroundColor Green
mvn spring-boot:run
