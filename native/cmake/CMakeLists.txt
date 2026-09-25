# === Build flags INTERFACE library ===
# Applied to every target via target_link_libraries(<tgt> PRIVATE fin_build_flags)
#
# Skill guidance:
#  - Warnings: -Wall -Wextra -Wpedantic -Wshadow -Wconversion (gcc/clang),
#              /W4 /permissive- /utf-8 /Zc:__cplusplus (MSVC)
#  - Hardening: -fstack-protector-strong -D_FORTIFY_SOURCE=2 -fPIE -pie (gcc/clang)
#  - Sanitizers: ASan + UBSan in dev preset; TSan only for explicit -DUSE_TSAN=ON
#  - Debug symbols: -g (split-dwarf on Linux for faster links); /Zi on MSVC
#  - No -march=native: baseline x86-64-v2; SIMD via runtime dispatch only

add_library(fin_build_flags INTERFACE)
add_library(fin::build_flags ALIAS fin_build_flags)

target_compile_features(fin_build_flags INTERFACE cxx_std_20)

# === Compiler-specific warning flags ===
if(MSVC)
  target_compile_options(fin_build_flags INTERFACE
    /W4 /permissive- /utf-8 /Zc:__cplusplus /Zc:preprocessor /Zc:inline
    /w14242 # conversion from 'int' to 'unsigned', possible loss of data
    /w14263 # member function declared but not defined
    /w14266 # no dynamic exception for function
    /w14296 # conversion error
    /w14311 # pointer truncation from 64-bit to 32-bit
    /w14545 # expression before comma evaluates to function
    /w14619 # pragma warning: there is no warning number
    /w14826 # size_t to 32-bit
  )
else()
  target_compile_options(fin_build_flags INTERFACE
    -Wall -Wextra -Wpedantic -Wshadow -Wconversion -Wnon-virtual-dtor
    -Wold-style-cast -Woverloaded-virtual -Wnull-dereference -Wdouble-promotion
    -Wformat=2 -Wimplicit-fallthrough -Werror
  )
endif()

# === Hardening (Linux/macOS) ===
if(NOT MSVC)
  target_compile_options(fin_build_flags INTERFACE
    -fstack-protector-strong -fno-omit-frame-pointer
  )
  target_link_options(fin_build_flags INTERFACE
    -fPIE -pie -Wl,-z,relro,-z,now,-z,noexecstack
  )
  target_compile_definitions(fin_build_flags INTERFACE
    _FORTIFY_SOURCE=2
  )
endif()

# === Sanitizers — opt-in via CMakePresets ===
option(FIN_SANITIZE "Enable AddressSanitizer + UndefinedBehaviorSanitizer" OFF)
option(FIN_TSAN "Enable ThreadSanitizer (conflicts with ASan)" OFF)
option(FIN_COVERAGE "Enable code coverage (gcov/llvm-cov)" OFF)

if(FIN_TSAN AND FIN_SANITIZE)
  message(FATAL_ERROR "FIN_TSAN and FIN_SANITIZE are mutually exclusive; pick one.")
endif()

if(FIN_SANITIZE)
  if(MSVC)
    target_compile_options(fin_build_flags INTERFACE /fsanitize=address)
  else()
    target_compile_options(fin_build_flags INTERFACE
      -fsanitize=address,undefined -fno-sanitize-recover=undefined
      -fsanitize-address-use-after-scope
    )
    target_link_options(fin_build_flags INTERFACE
      -fsanitize=address,undefined
    )
  endif()
endif()

if(FIN_TSAN)
  if(MSVC)
    message(WARNING "ThreadSanitizer is not supported on MSVC; ignoring FIN_TSAN.")
  else()
    target_compile_options(fin_build_flags INTERFACE -fsanitize=thread -fno-sanitize-recover=thread)
    target_link_options(fin_build_flags INTERFACE -fsanitize=thread)
  endif()
endif()

if(FIN_COVERAGE)
  if(MSVC)
    message(WARNING "Coverage flags not configured for MSVC yet.")
  else()
    target_compile_options(fin_build_flags INTERFACE --coverage -O0 -g)
    target_link_options(fin_build_flags INTERFACE --coverage)
  endif()
endif()

# === Helper: layered library creation ===
include(${CMAKE_CURRENT_LIST_DIR}/fin_add_library.cmake)

# === RelWithDebInfo / Release: LTO ===
if(NOT CMAKE_CXX_COMPILER_ID STREQUAL "MSVC")
  set(CMAKE_INTERPROCEDURAL_OPTIMIZATION_RELEASE ON)
  set(CMAKE_INTERPROCEDURAL_OPTIMIZATION_RELWITHDEBINFO ON)
endif()

# === RPath so binaries run from build/bin/ without install ===
if(NOT MSVC)
  set(CMAKE_BUILD_RPATH_USE_ORIGIN ON)
endif()
