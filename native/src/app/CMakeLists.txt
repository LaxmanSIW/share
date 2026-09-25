# src/app/CMakeLists.txt — async spine + watchdog + main app shell
find_package(Qt6 REQUIRED COMPONENTS Core Gui Widgets Concurrent)
find_package(spdlog REQUIRED)
find_package(fmt REQUIRED)

qt_standard_project_setup()

qt_add_library(fin_app STATIC
  src/async.cpp
  src/ui_watchdog.cpp
  src/app_dirs.cpp
  src/log.cpp
  src/executors.cpp
  src/formatters.cpp
)
target_include_directories(fin_app PUBLIC
  ${CMAKE_CURRENT_SOURCE_DIR}/include
)
target_link_libraries(fin_app
  PUBLIC
    fin::domain
    Qt6::Core
    Qt6::Gui
    Qt6::Widgets
    Qt6::Concurrent
  PRIVATE
    spdlog::spdlog
    fmt::fmt
)
target_link_libraries(fin_app PRIVATE fin::build_flags)

# === Main executable (Phase 7: launches StudioApp, supports --smoke) ===
qt_add_executable(invoicestudio
  src/main.cpp
  src/smoke_test.cpp
)
target_link_libraries(invoicestudio
  PRIVATE
    fin_ui
    fin_app
)
set_target_properties(invoicestudio PROPERTIES
  WIN32_EXECUTABLE ON
  MACOSX_BUNDLE ON
)

# === CPack packaging (skill §11: installers for Windows/macOS/Linux) ===
include(CPack)

if(WIN32)
  # WiX MSI installer (skill §11)
  install(TARGETS invoicestudio
    RUNTIME DESTINATION bin
    COMPONENT Runtime)
  # windeployqt: copy Qt6 DLLs + plugins next to the executable.
  # Real impl: qt_generate_deploy_app_script() in CMake 3.21+ or a custom script.
  # Also ship the QSS resource bundle.
  install(FILES
    ${CMAKE_SOURCE_DIR}/resources/css/globalfile.qss
    DESTINATION share/invoicestudio/css
    COMPONENT Runtime)
  # Vendor runtime
  set(CPACK_GENERATOR WIX ZIP)
  set(CPACK_PACKAGE_NAME "InvoiceStudio")
  set(CPACK_PACKAGE_VENDOR "InvoiceStudio")
  set(CPACK_PACKAGE_VERSION "4.0.0")
  set(CPACK_PACKAGE_FILE_NAME "InvoiceStudio-4.0.0-${CMAKE_SYSTEM_NAME}-x64")
  set(CPACK_RESOURCE_FILE_LICENSE ${CMAKE_SOURCE_DIR}/LICENSE)
  set(CPACK_WIX_PRODUCT_GUID "8F4F7B3A-2E9F-4C0F-9F4B-7C8E9D5F4F3A")
  set(CPACK_WIX_UPGRADE_GUID "1F4F7B3A-2E9F-4C0F-9F4B-7C8E9D5F4F3B")
elseif(APPLE)
  # macOS app bundle + DMG (skill §11)
  install(TARGETS invoicestudio
    BUNDLE DESTINATION .
    COMPONENT Runtime)
  set(CPACK_GENERATOR DragNDrop ZIP)
  set(CPACK_PACKAGE_NAME "InvoiceStudio")
  set(CPACK_PACKAGE_VENDOR "InvoiceStudio")
  set(CPACK_PACKAGE_VERSION "4.0.0")
  set(CPACK_DMG_FORMAT "UDBZ")
  set(CPACK_DMG_VOLUME_NAME "InvoiceStudio 4.0.0")
else()
  # Linux: AppImage (skill §11)
  install(TARGETS invoicestudio
    RUNTIME DESTINATION bin
    COMPONENT Runtime)
  install(FILES
    ${CMAKE_SOURCE_DIR}/resources/css/globalfile.qss
    DESTINATION share/invoicestudio/css
    COMPONENT Runtime)
  set(CPACK_GENERATOR DEB RPM TGZ)
  set(CPACK_PACKAGE_NAME "invoicestudio")
  set(CPACK_PACKAGE_VENDOR "InvoiceStudio")
  set(CPACK_PACKAGE_VERSION "4.0.0")
  set(CPACK_DEBIAN_PACKAGE_DEPENDS "libqt6-core6, libqt6-widgets6, libqt6-svg6, libsqlite3-0, libspdlog1.12, libfmt9")
endif()

set(CPACK_PACKAGE_DESCRIPTION_SUMMARY "InvoiceStudio — Native C++/Qt6 invoice & accounting desktop app")
set(CPACK_PACKAGE_HOMEPAGE_URL "https://github.com/LaxmanSIW/share")
