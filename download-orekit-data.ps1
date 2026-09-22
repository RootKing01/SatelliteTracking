# Script per scaricare i dati Orekit
Write-Host "📡 Downloading Orekit data..." -ForegroundColor Cyan

$url = "https://gitlab.orekit.org/orekit/orekit-data/-/archive/master/orekit-data-master.zip"
$zipFile = "orekit-data-master.zip"
$extractPath = "orekit-data"

# Scarica il file
Write-Host "⬇️  Downloading from GitLab..." -ForegroundColor Yellow
Invoke-WebRequest -Uri $url -OutFile $zipFile

# Estrai
Write-Host "📦 Extracting..." -ForegroundColor Yellow
Expand-Archive -Path $zipFile -DestinationPath "temp" -Force

# Sposta il contenuto nella directory corretta
if (Test-Path $extractPath) {
    Remove-Item -Recurse -Force $extractPath
}
Move-Item -Path "temp\orekit-data-master" -Destination $extractPath

# Pulisci
Remove-Item -Force $zipFile
Remove-Item -Recurse -Force "temp"

Write-Host "✅ Orekit data downloaded successfully!" -ForegroundColor Green
Write-Host "📁 Location: $PWD\$extractPath" -ForegroundColor Green
