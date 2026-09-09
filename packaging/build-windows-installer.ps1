#Requires -Version 5
<#
.SYNOPSIS
  Builds the InvoiceStudio Windows installer (MSI) with a bundled Java runtime,
  a desktop shortcut and a Start-menu entry - the normal "setup file" experience.

.USAGE
  .\build-windows-installer.ps1              # MSI installer  -> packaging\dist\*.msi
  .\build-windows-installer.ps1 -AppImage    # plain app folder (no installer),
                                             # usable with packaging\InvoiceStudio.iss
                                             # to get a classic setup.exe instead

.PREREQUISITES (on the Windows build machine)
  - JDK 21 on PATH (jpackage ships inside it): https://adoptium.net/temurin/releases/?version=21
  - Maven on PATH: https://maven.apache.org/download.cgi
  - WiX Toolset 3.x (MSI output only): https://wixtoolset.org/releases/
    (installer needs candle.exe/light.exe on PATH)

.NOTES
  - The MSI bundles a private Java 21 runtime: end users need NOTHING installed.
  - The app stores its database in %APPDATA%\InvoiceStudio (never in Program Files).
  - The build MUST run on Windows: JavaFX platform natives and the bundled JRE
    always match the OS the packaging runs on.
#>
param([switch]$AppImage)
$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
foreach ($tool in @("java", "jpackage", "mvn")) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        throw "$tool not found on PATH - see PREREQUISITES in this script's header"
    }
}

# --- 1) Build the fat jar (shade plugin pulls the Windows JavaFX natives) ----
Write-Host "== mvn package ==" -ForegroundColor Cyan
Push-Location $Root
try {
    mvn -B -ntp package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "mvn package failed" }
} finally {
    Pop-Location
}
$Jar = Join-Path $Root "target\invoice-studio-desktop-2.0.1.jar"
if (-not (Test-Path $Jar)) { throw "fat jar missing: $Jar" }

# --- 2) Stage jpackage input: ONLY the shaded jar (target/ holds originals) --
$InputDir = Join-Path $PSScriptRoot "input"
Remove-Item $InputDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $InputDir | Out-Null
Copy-Item $Jar $InputDir

$Common = @(
    "--name", "InvoiceStudio",
    "--description", "Billing & Invoice Design Studio",
    "--app-version", "2.0.1",
    "--vendor", "InvoiceStudio",
    "--icon", (Join-Path $PSScriptRoot "InvoiceStudio.ico"),
    "--input", $InputDir,
    "--main-jar", "invoice-studio-desktop-2.0.1.jar",
    "--main-class", "com.invoicestudio.Launcher"
)

if ($AppImage) {
    # --- 3a) Plain folder (feed to Inno Setup for a classic setup.exe) --------
    $Dest = Join-Path $PSScriptRoot "app-image"
    Remove-Item $Dest -Recurse -Force -ErrorAction SilentlyContinue
    jpackage @Common --type app-image --dest $Dest
    if ($LASTEXITCODE -ne 0) { throw "jpackage app-image failed" }
    Write-Host "OK: $Dest\InvoiceStudio\  (then: ISCC packaging\InvoiceStudio.iss)" -ForegroundColor Green
} else {
    # --- 3b) MSI: desktop icon + start menu + dir chooser, admin install -----
    if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
        throw "WiX Toolset 3.x not on PATH (candle.exe) - needed for MSI. " +
              "Install from https://wixtoolset.org or run with -AppImage."
    }
    $Dest = Join-Path $PSScriptRoot "dist"
    Remove-Item $Dest -Recurse -Force -ErrorAction SilentlyContinue
    jpackage @Common --type msi --win-shortcut --win-menu --win-dir-chooser --dest $Dest
    if ($LASTEXITCODE -ne 0) { throw "jpackage msi failed" }
    $msi = Get-ChildItem $Dest -Filter *.msi | Select-Object -First 1
    Write-Host "OK installer: $($msi.FullName)" -ForegroundColor Green
    Write-Host "Double-click it -> UAC -> next -> desktop icon appears. No Java needed on the target PC." -ForegroundColor Green
}
