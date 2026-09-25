@echo off
REM ============================================================================
REM run.bat — One-click run for InvoiceStudio Native on Windows
REM ============================================================================
REM Usage: Double-click this file to launch InvoiceStudio.
REM
REM This script will:
REM   1. Find the Qt installation (for plugins + DLLs)
REM   2. Set up PATH and QT_PLUGIN_PATH
REM   3. Create the data directory if missing
REM   4. Launch invoicestudio.exe
REM ============================================================================

setlocal enabledelayedexpansion

REM --- Find the directory of this batch file ---
cd /d "%~dp0"

echo ============================================================
echo   InvoiceStudio Native — Starting...
echo ============================================================
echo.

REM --- Find Qt ---
set "QT_FOUND=0"
for %%Q in (
  "C:\Qt\6.7.3\msvc2022_64"
  "C:\Qt\6.7.2\msvc2022_64"
  "C:\Qt\6.7.1\msvc2022_64"
  "C:\Qt\6.7.0\msvc2022_64"
  "C:\Qt\6.8.0\msvc2022_64"
  "C:\Qt\6.6.3\msvc2022_64"
) do (
  if exist "%%~Q\bin\Qt6Core.dll" (
    set "QT_DIR=%%~Q"
    set "QT_FOUND=1"
    goto :qt_found
  )
)

REM Check QT_PREFIX_PATH env var
if defined QT_PREFIX_PATH (
  if exist "%QT_PREFIX_PATH%\bin\Qt6Core.dll" (
    set "QT_DIR=%QT_PREFIX_PATH%"
    set "QT_FOUND=1"
    goto :qt_found
  )
)

REM Try to find Qt relative to the exe
for %%I in ("%~dp0..\..\..\..") do (
  if exist "%%~I\bin\Qt6Core.dll" (
    set "QT_DIR=%%~I"
    set "QT_FOUND=1"
    goto :qt_found
  )
)

echo WARNING: Qt DLLs not found in standard paths.
echo The app may crash if Qt is not in your PATH.
echo Set QT_PREFIX_PATH to your Qt installation path.
echo.

:qt_found
if "%QT_FOUND%"=="1" (
  echo Qt found: %QT_DIR%
  set "PATH=%QT_DIR%\bin;%PATH%"
  set "QT_PLUGIN_PATH=%QT_DIR%\plugins"
)

REM --- Create data directory ---
set "APPDATA_DIR=%APPDATA%\InvoiceStudio"
if not exist "%APPDATA_DIR%" (
  mkdir "%APPDATA_DIR%"
  echo Data directory created: %APPDATA_DIR%
)
set "INVOICESTUDIO_DATA_DIR=%APPDATA_DIR%"
set "INVOICESTUDIO_DEBUG=1"

REM --- Find the executable ---
set "EXE_PATH="
if exist "invoicestudio.exe" (
  set "EXE_PATH=invoicestudio.exe"
) else if exist "bin\invoicestudio.exe" (
  set "EXE_PATH=bin\invoicestudio.exe"
) else if exist "build-windows\bin\invoicestudio.exe" (
  set "EXE_PATH=build-windows\bin\invoicestudio.exe"
) else (
  echo ERROR: invoicestudio.exe not found!
  echo Please run build.bat first to build the application.
  pause
  exit /b 1
)

echo Executable: %EXE_PATH%
echo Data dir:   %INVOICESTUDIO_DATA_DIR%
echo.

REM --- Launch ---
echo Launching InvoiceStudio...
echo.
"%EXE_PATH%"

if %ERRORLEVEL% neq 0 (
  echo.
  echo InvoiceStudio exited with code %ERRORLEVEL%
  echo If the app crashed, check that Qt is installed and the PATH is set correctly.
  pause
)

endlocal
