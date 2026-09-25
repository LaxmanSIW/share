@echo off
REM ============================================================================
REM run.bat - One-click run for InvoiceStudio Native on Windows
REM ============================================================================
REM Usage: Double-click this file to launch InvoiceStudio.
REM ============================================================================

setlocal enabledelayedexpansion
chcp 65001 >nul 2>&1
cd /d "%~dp0"

echo ============================================================
echo   InvoiceStudio Native - Starting...
echo ============================================================
echo.

REM --- Find Qt ---
set "QT_FOUND=0"
set "QT_DIR="

REM Check QT_PREFIX_PATH env var first
if defined QT_PREFIX_PATH (
  if exist "%QT_PREFIX_PATH%\bin\Qt6Core.dll" (
    set "QT_DIR=%QT_PREFIX_PATH%"
    set "QT_FOUND=1"
    goto :qt_found
  )
)

REM Check common paths
for %%V in (6.7.3 6.7.2 6.7.1 6.7.0 6.8.0 6.6.3) do (
  for %%P in (
    "C:\Qt\%%V\msvc2022_64"
    "C:\Qt\%%V\msvc2019_64"
    "D:\Qt\%%V\msvc2022_64"
    "C:\Qt\%%V\mingw_64"
  ) do (
    if exist "%%~P\bin\Qt6Core.dll" (
      set "QT_DIR=%%~P"
      set "QT_FOUND=1"
      goto :qt_found
    )
  )
)

REM Try to find Qt relative to the exe (next to build-windows)
for %%I in ("%~dp0..\..\..") do (
  if exist "%%~I\bin\Qt6Core.dll" (
    set "QT_DIR=%%~I"
    set "QT_FOUND=1"
    goto :qt_found
  )
)

echo   WARNING: Qt DLLs not found in standard paths.
echo   The app may crash if Qt is not in your PATH.
echo   Set QT_PREFIX_PATH to your Qt installation path.
echo   Example: set QT_PREFIX_PATH=C:\Qt\6.7.3\msvc2022_64
echo.

:qt_found
if "%QT_FOUND%"=="1" (
  echo   Qt found: %QT_DIR%
  set "PATH=%QT_DIR%\bin;%PATH%"
  set "QT_PLUGIN_PATH=%QT_DIR%\plugins"
) else (
  echo   Trying to run without Qt in PATH (may fail)...
)

REM --- Create data directory ---
set "APPDATA_DIR=%APPDATA%\InvoiceStudio"
if not exist "%APPDATA_DIR%" (
  mkdir "%APPDATA_DIR%"
  echo   Data directory created: %APPDATA_DIR%
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
  REM Search parent directories
  for /r "%~dp0..\.." %%F in (invoicestudio.exe) do (
    if exist "%%~F" (
      set "EXE_PATH=%%~F"
      goto :found_exe
    )
  )
  echo.
  echo   ERROR: invoicestudio.exe not found!
  echo   Please run build.bat first to build the application.
  echo.
  pause
  exit /b 1
)
:found_exe

echo   Executable: %EXE_PATH%
echo   Data dir:   %INVOICESTUDIO_DATA_DIR%
echo.

REM --- Launch ---
echo   Launching InvoiceStudio...
echo.
"%EXE_PATH%"

if %ERRORLEVEL% neq 0 (
  echo.
  echo   InvoiceStudio exited with code %ERRORLEVEL%
  echo   If the app crashed:
  echo   - Check that Qt is installed and in PATH
  echo   - Try: set QT_PREFIX_PATH=C:\Qt\6.7.x\msvc2022_64
  echo.
  pause
)

endlocal
