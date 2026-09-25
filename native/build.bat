@echo off
REM ============================================================================
REM build.bat — One-click build for InvoiceStudio Native on Windows
REM ============================================================================
REM Usage: Double-click this file or run from Command Prompt:
REM   build.bat [Debug|Release]
REM
REM Prerequisites:
REM   - Qt 6.7+ installed (default path: C:\Qt)
REM   - Visual Studio 2022 (MSVC) with CMake and Ninja
REM   - Git (for downloading third_party headers)
REM
REM This script will:
REM   1. Find your Qt installation automatically
REM   2. Download third_party headers (nlohmann/json, spdlog, fmt) if missing
REM   3. Configure CMake
REM   4. Build the invoicestudio.exe
REM ============================================================================

setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ============================================================
echo   InvoiceStudio Native — Windows Build Script
echo ============================================================
echo.

REM --- Configuration ---
set "BUILD_TYPE=%1"
if "%BUILD_TYPE%"=="" set "BUILD_TYPE=Release"
set "BUILD_DIR=build-windows"

echo Build type: %BUILD_TYPE%
echo Build dir:  %BUILD_DIR%
echo.

REM --- Step 1: Find Qt ---
echo [1/5] Finding Qt installation...

set "QT_FOUND=0"
for %%Q in (
  "C:\Qt\6.7.3\msvc2022_64"
  "C:\Qt\6.7.2\msvc2022_64"
  "C:\Qt\6.7.1\msvc2022_64"
  "C:\Qt\6.7.0\msvc2022_64"
  "C:\Qt\6.8.0\msvc2022_64"
  "C:\Qt\6.6.3\msvc2022_64"
) do (
  if exist "%%~Q\bin\qmake.exe" (
    set "QT_DIR=%%~Q"
    set "QT_FOUND=1"
    echo   Found Qt at: %%~Q
    goto :qt_found
  )
)

REM Check QT_PREFIX_PATH env var
if defined QT_PREFIX_PATH (
  if exist "%QT_PREFIX_PATH%\bin\qmake.exe" (
    set "QT_DIR=%QT_PREFIX_PATH%"
    set "QT_FOUND=1"
    echo   Found Qt (QT_PREFIX_PATH): %QT_PREFIX_PATH%
    goto :qt_found
  )
)

echo   ERROR: Qt 6.7+ not found!
echo   Please install Qt from https://www.qt.io/download
echo   Or set QT_PREFIX_PATH environment variable to your Qt path
echo   Expected path: C:\Qt\6.7.x\msvc2022_64
pause
exit /b 1

:qt_found
set "CMAKE_PREFIX_PATH=%QT_DIR%"

REM --- Step 2: Setup MSVC environment ---
echo [2/5] Setting up MSVC environment...

REM Try to find Visual Studio
set "VS_FOUND=0"
for %%V in (
  "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat"
  "C:\Program Files\Microsoft Visual Studio\2022\Professional\VC\Auxiliary\Build\vcvars64.bat"
  "C:\Program Files\Microsoft Visual Studio\2022\Enterprise\VC\Auxiliary\Build\vcvars64.bat"
  "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
) do (
  if exist "%%~V" (
    call "%%~V" >nul 2>&1
    set "VS_FOUND=1"
    echo   MSVC environment loaded
    goto :vs_found
  )
)

echo   WARNING: Visual Studio 2022 not found in standard paths.
echo   Make sure you have MSVC + CMake + Ninja in your PATH.
echo   Or install Visual Studio 2022 Build Tools from:
echo   https://visualstudio.microsoft.com/downloads/

:vs_found

REM --- Step 3: Download third_party headers if missing ---
echo [3/5] Checking third_party dependencies...

if not exist "third_party\nlohmann\json.hpp" (
  echo   Downloading nlohmann/json...
  mkdir third_party\nlohmann 2>nul
  powershell -Command "Invoke-WebRequest -Uri 'https://github.com/nlohmann/json/releases/download/v3.11.3/json.hpp' -OutFile 'third_party\nlohmann\json.hpp'" 2>nul
  if exist "third_party\nlohmann\json.hpp" (
    echo   nlohmann/json: OK
  ) else (
    echo   ERROR: Failed to download nlohmann/json
    echo   Please manually download from: https://github.com/nlohmann/json/releases
    pause
    exit /b 1
  )
) else (
  echo   nlohmann/json: already present
)

if not exist "third_party\fmt\include\fmt\core.h" (
  echo   Downloading fmt...
  powershell -Command "$ProgressPreference='SilentlyContinue'; Expand-Archive -Path (New-Object Net.WebClient).DownloadData('https://github.com/fmtlib/fmt/releases/download/10.2.1/fmt-10.2.1.zip') -DestinationPath 'third_party\fmt_tmp'" 2>nul
  if not exist "third_party\fmt\include\fmt\core.h" (
    REM Try git clone as fallback
    git clone --depth 1 --branch 10.2.1 https://github.com/fmtlib/fmt.git third_party\fmt_src 2>nul
    if exist "third_party\fmt_src\include\fmt\core.h" (
      mkdir third_party\fmt\include\fmt 2>nul
      xcopy /E /I /Y third_party\fmt_src\include\fmt\* third_party\fmt\include\fmt\ >nul 2>&1
      rmdir /S /Q third_party\fmt_src 2>nul
      echo   fmt: OK
    ) else (
      echo   WARNING: Could not download fmt headers
      echo   The build will use a stub instead. Some features may be limited.
    )
  ) else (
    echo   fmt: OK
  )
) else (
  echo   fmt: already present
)

if not exist "third_party\spdlog\include\spdlog\spdlog.h" (
  echo   Downloading spdlog...
  git clone --depth 1 --branch v1.13.0 https://github.com/gabime/spdlog.git third_party\spdlog_src 2>nul
  if exist "third_party\spdlog_src\include\spdlog\spdlog.h" (
    mkdir third_party\spdlog\include 2>nul
    xcopy /E /I /Y third_party\spdlog_src\include\spdlog third_party\spdlog\include\spdlog >nul 2>&1
    rmdir /S /Q third_party\spdlog_src 2>nul
    echo   spdlog: OK
  ) else (
    echo   WARNING: Could not download spdlog headers
    echo   The build will use a stub instead.
  )
) else (
  echo   spdlog: already present
)

REM --- Step 4: Configure CMake ---
echo [4/5] Configuring CMake...

if exist "%BUILD_DIR%" rmdir /S /Q "%BUILD_DIR%" 2>nul
mkdir "%BUILD_DIR%"

cmake -G "Ninja" ^
  -S . -B "%BUILD_DIR%" ^
  -DCMAKE_BUILD_TYPE=%BUILD_TYPE% ^
  -DCMAKE_PREFIX_PATH="%QT_DIR%" ^
  -DCMAKE_CXX_FLAGS="-Wno-error -Wno-pedantic -Wno-shadow -Wno-conversion" ^
  -DOPENGL_INCLUDE_DIR="" ^
  -DOpenGL_GL_PREFERENCE=GLVND

if %ERRORLEVEL% neq 0 (
  echo.
  echo ERROR: CMake configuration failed!
  echo Please check that Qt and MSVC are properly installed.
  pause
  exit /b 1
)

REM --- Step 5: Build ---
echo [5/5] Building invoicestudio.exe...

cmake --build "%BUILD_DIR%" --config %BUILD_TYPE% --parallel

if %ERRORLEVEL% neq 0 (
  echo.
  echo ERROR: Build failed!
  echo Please check the error messages above.
  pause
  exit /b 1
)

echo.
echo ============================================================
echo   BUILD SUCCESSFUL!
echo ============================================================
echo.
echo   Executable: %BUILD_DIR%\bin\invoicestudio.exe
echo.
echo   To run: Double-click run.bat
echo.

REM Copy run.bat next to the executable
copy /Y run.bat "%BUILD_DIR%\bin\run.bat" >nul 2>&1

pause
