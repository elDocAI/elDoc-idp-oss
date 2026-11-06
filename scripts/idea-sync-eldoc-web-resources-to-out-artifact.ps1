# The script lives in scripts/, so the project root is its parent directory.
$projectRoot = Split-Path -Parent $PSScriptRoot
$sourceDir = "$projectRoot\modules\eldoc-idp-web\src\main\webapp"
$targetDir = "$projectRoot\out\artifacts\eldoc"

Write-Host "Source: $sourceDir"
Write-Host "Target: $targetDir"
Write-Host "Watching for changes... Press Ctrl+C to stop."

$watcher = New-Object System.IO.FileSystemWatcher $sourceDir
$watcher.IncludeSubdirectories = $true
$watcher.EnableRaisingEvents = $true
$watcher.NotifyFilter = [System.IO.NotifyFilters]::LastWrite -bor [System.IO.NotifyFilters]::FileName

while ($true) {
    $result = $watcher.WaitForChanged([System.IO.WatcherChangeTypes]::All, 500)

    if ($result.TimedOut) { continue }

    $relativePath = $result.Name

    # Skip IDEA temp files
    if ($relativePath -like "*~") { continue }

    $fullPath = Join-Path $sourceDir $relativePath

    # Skip directories
    if (Test-Path $fullPath -PathType Container) { continue }

    # Skip if file doesn't exist
    if (-not (Test-Path $fullPath)) { continue }

    $destination = Join-Path $targetDir $relativePath
    $destFolder = Split-Path $destination -Parent

    if (-not (Test-Path $destFolder)) {
        New-Item -ItemType Directory -Path $destFolder -Force | Out-Null
    }

    Start-Sleep -Milliseconds 200
    Copy-Item $fullPath -Destination $destination -Force

    Write-Host "Synced: $relativePath"
}