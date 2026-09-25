cmake_minimum_required(VERSION 3.25)

project(InvoiceStudioNative
  VERSION 4.0.0
  DESCRIPTION "Native C++/Qt6 port of InvoiceStudio (JavaFX original)"
  LANGUAGES CXX C
)

# === C++20 baseline (adopt C++23 features only when all CI compilers support them) ===
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_C_STANDARD 11)

# === Output dirs ===
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)
set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)

# === Default to RelWithDebInfo so symbols exist for crash dumps ===
if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
  set(CMAKE_BUILD_TYPE RelWithDebInfo CACHE STRING "" FORCE)
endif()

# === Export compile_commands.json for clangd / clang-tidy ===
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# === Position-Independent Code (hardening) ===
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# === CTest: include BEFORE add_subdirectory() or subdir add_test() calls vanish ===
include(CTest)
enable_testing()

# === Build flags INTERFACE library: warnings + sanitizers + hardening, applied to every target ===
add_subdirectory(cmake)

# === Layered libraries — dependency direction enforced by CMake target linkage ===
# domain (pure C++, no Qt) -> app (Qt-based spine) -> services (business logic)
# -> infra (db, pdf, print) -> ui -> main executable
add_subdirectory(src/domain)
add_subdirectory(src/app)
add_subdirectory(src/services)
add_subdirectory(src/infra/db)
add_subdirectory(src/infra/pdf)
add_subdirectory(src/infra/print)
add_subdirectory(src/ui)

# === Tests ===
add_subdirectory(tests/domain)
add_subdirectory(tests/async)
add_subdirectory(tests/smoke)
