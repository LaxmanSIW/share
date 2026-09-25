@echo off
REM ============================================================================
REM build.bat - One-click build for InvoiceStudio Native on Windows
REM ============================================================================
REM Usage: Double-click this file, or from command prompt: build.bat [Debug|Release]
REM
REM Prerequisites:
REM   - Qt 6.7+ installed (default: C:\Qt)
REM   - Visual Studio 2022 with C++ workload (includes CMake + MSVC)
REM   - Git (for downloading third_party headers)
REM ============================================================================

setlocal enabledelayedexpansion
chcp 65001 >nul 2>&1
cd /d "%~dp0"

echo ============================================================
echo   InvoiceStudio Native - Windows Build Script
echo ============================================================
echo.

REM --- Configuration ---
set "BUILD_TYPE=%~1"
if "%BUILD_TYPE%"=="" set "BUILD_TYPE=Release"
set "BUILD_DIR=build-windows"

echo Build type: %BUILD_TYPE%
echo Build dir:  %BUILD_DIR%
echo.

REM --- Step 1: Find Qt ---
echo [1/6] Finding Qt installation...

set "QT_FOUND=0"

REM Check QT_PREFIX_PATH env var first
if defined QT_PREFIX_PATH (
  if exist "%QT_PREFIX_PATH%\lib\cmake\Qt6\Qt6Config.cmake" (
    set "QT_DIR=%QT_PREFIX_PATH%"
    set "QT_FOUND=1"
    echo   Found Qt (QT_PREFIX_PATH): %QT_PREFIX_PATH%
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
    if exist "%%~P\lib\cmake\Qt6\Qt6Config.cmake" (
      set "QT_DIR=%%~P"
      set "QT_FOUND=1"
      echo   Found Qt at: %%~P
      goto :qt_found
    )
  )
)

echo   ERROR: Qt 6.7+ not found!
echo.
echo   Please install Qt from: https://www.qt.io/download
echo   Or set QT_PREFIX_PATH environment variable to your Qt path.
echo   Example: set QT_PREFIX_PATH=C:\Qt\6.7.3\msvc2022_64
echo.
pause
exit /b 1

:qt_found

REM --- Step 2: Find CMake + Ninja ---
echo [2/6] Finding CMake and Ninja...

where cmake >nul 2>&1
if %ERRORLEVEL% neq 0 (
  echo   CMake not found in PATH. Searching Visual Studio...
  for %%V in (
    "C:\Program Files\Microsoft Visual Studio\2022\Community\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe"
    "C:\Program Files\Microsoft Visual Studio\2022\Professional\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe"
    "C:\Program Files\Microsoft Visual Studio\2022\Enterprise\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe"
    "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe"
  ) do (
    if exist "%%~V" (
      set "CMAKE_CMD=%%~V"
      echo   Found CMake: %%~V
      goto :cmake_found
    )
  )
  echo   ERROR: CMake not found!
  echo   Install Visual Studio 2022 with C++ workload (includes CMake).
  echo.
  pause
  exit /b 1
) else (
  set "CMAKE_CMD=cmake"
  echo   CMake: found in PATH
)

:cmake_found

where ninja >nul 2>&1
if %ERRORLEVEL% neq 0 (
  echo   Ninja not found in PATH. Searching Visual Studio...
  for %%V in (
    "C:\Program Files\Microsoft Visual Studio\2022\Community\Common7\IDE\CommonExtensions\Microsoft\CMake\Ninja\ninja.exe"
    "C:\Program Files\Microsoft Visual Studio\2022\Professional\Common7\IDE\CommonExtensions\Microsoft\CMake\Ninja\ninja.exe"
    "C:\Program Files\Microsoft Visual Studio\2022\Enterprise\Common7\IDE\CommonExtensions\Microsoft\CMake\Ninja\ninja.exe"
    "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\Common7\IDE\CommonExtensions\Microsoft\CMake\Ninja\ninja.exe"
  ) do (
    if exist "%%~V" (
      set "PATH=%%~dpV;%PATH%"
      echo   Found Ninja: %%~V
      goto :ninja_found
    )
  )
  echo   WARNING: Ninja not found. Will try Visual Studio generator instead.
  set "GENERATOR=-G \"Visual Studio 17 2022\" -A x64"
  goto :ninja_found_or_skip
) else (
  echo   Ninja: found in PATH
  set "GENERATOR=-G Ninja"
)

:ninja_found
set "GENERATOR=-G Ninja"
:ninja_found_or_skip

REM --- Step 3: Setup MSVC environment ---
echo [3/6] Setting up MSVC environment...

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
    echo   MSVC environment loaded: %%~V
    goto :vs_found
  )
)

echo   WARNING: Visual Studio 2022 not found in standard paths.
echo   Make sure MSVC is in your PATH.
echo.

:vs_found

REM --- Step 4: Download third_party headers if missing ---
echo [4/6] Checking third_party dependencies...

if not exist "third_party\nlohmann\json.hpp" (
  echo   Downloading nlohmann/json...
  if not exist "third_party\nlohmann" mkdir "third_party\nlohmann"
  powershell -Command "try { Invoke-WebRequest -Uri 'https://github.com/nlohmann/json/releases/download/v3.11.3/json.hpp' -OutFile 'third_party\nlohmann\json.hpp' -UseBasicParsing } catch { Write-Host 'Download failed' }" 2>nul
  if exist "third_party\nlohmann\json.hpp" (
    echo   nlohmann/json: OK
  ) else (
    echo   WARNING: Could not download nlohmann/json. Trying git...
    git clone --depth 1 https://github.com/nlohmann/json.git third_party\json_src 2>nul
    if exist "third_party\json_src\single_include\nlohmann\json.hpp" (
      copy /Y "third_party\json_src\single_include\nlohmann\json.hpp" "third_party\nlohmann\json.hpp" >nul
      rmdir /S /Q "third_party\json_src" 2>nul
      echo   nlohmann/json: OK (via git)
    ) else (
      echo   ERROR: Cannot get nlohmann/json. Please download manually.
      pause
      exit /b 1
    )
  )
) else (
  echo   nlohmann/json: already present
)

if not exist "third_party\fmt\include\fmt\core.h" (
  echo   Downloading fmt...
  git clone --depth 1 --branch 10.2.1 https://github.com/fmtlib/fmt.git third_party\fmt_src 2>nul
  if exist "third_party\fmt_src\include\fmt\core.h" (
    if not exist "third_party\fmt\include\fmt" mkdir "third_party\fmt\include\fmt"
    xcopy /E /I /Y "third_party\fmt_src\include\fmt\*" "third_party\fmt\include\fmt\" >nul 2>&1
    rmdir /S /Q "third_party\fmt_src" 2>nul
    echo   fmt: OK
  ) else (
    echo   WARNING: Could not download fmt. Will use stub.
  )
) else (
  echo   fmt: already present
)

if not exist "third_party\spdlog\include\spdlog\spdlog.h" (
  echo   Downloading spdlog...
  git clone --depth 1 --branch v1.13.0 https://github.com/gabime/spdlog.git third_party\spdlog_src 2>nul
  if exist "third_party\spdlog_src\include\spdlog\spdlog.h" (
    if not exist "third_party\spdlog\include" mkdir "third_party\spdlog\include"
    xcopy /E /I /Y "third_party\spdlog_src\include\spdlog\*" "third_party\spdlog\include\spdlog\" >nul 2>&1
    rmdir /S /Q "third_party\spdlog_src" 2>nul
    echo   spdlog: OK
  ) else (
    echo   WARNING: Could not download spdlog. Will use stub.
  )
) else (
  echo   spdlog: already present
)

REM --- Step 5: Configure CMake ---
echo [5/6] Configuring CMake...

if exist "%BUILD_DIR%" rmdir /S /Q "%BUILD_DIR%" 2>nul
mkdir "%BUILD_DIR%"

echo   Running: %CMAKE_CMD% %GENERATOR% -DCMAKE_BUILD_TYPE=%BUILD_TYPE% -DCMAKE_PREFIX_PATH="%QT_DIR%" ...
echo.

%CMAKE_CMD% %GENERATOR% ^
  -S . -B "%BUILD_DIR%" ^
  -DCMAKE_BUILD_TYPE=%BUILD_TYPE% ^
  -DCMAKE_PREFIX_PATH="%QT_DIR%"

if %ERRORLEVEL% neq 0 (
  echo.
  echo ============================================================
  echo   ERROR: CMake configuration failed! (exit code %ERRORLEVEL%)
  echo ============================================================
  echo.
  echo   Common fixes:
  echo   - Make sure Qt 6.7+ is installed
  echo   - Make sure Visual Studio 2022 with C++ workload is installed
  echo   - Try: set QT_PREFIX_PATH=C:\Qt\6.7.x\msvc2022_64
  echo.
  pause
  exit /b 1
)

REM --- Step 6: Build ---
echo [6/6] Building invoicestudio.exe...
echo.

%CMAKE_CMD% --build "%BUILD_DIR%" --config %BUILD_TYPE% --parallel

if %ERRORLEVEL% neq 0 (
  echo.
  echo ============================================================
  echo   ERROR: Build failed! (exit code %ERRORLEVEL%)
  echo ============================================================
  echo.
  echo   Check the error messages above.
  echo   If you see missing headers, make sure third_party deps were downloaded.
  echo.
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
if exist "run.bat" copy /Y "run.bat" "%BUILD_DIR%\bin\run.bat" >nul 2>&1

pause
