# CMakeLists.txt — InvoiceStudio Native (cross-platform: Windows + Linux + macOS)
cmake_minimum_required(VERSION 3.25)

project(InvoiceStudioNative
  VERSION 4.0.0
  DESCRIPTION "InvoiceStudio Native - C++/Qt6 port"
  LANGUAGES CXX C)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTOUIC ON)
set(CMAKE_AUTORCC ON)
set(CMAKE_AUTOUIC_SEARCH_PATHS src)

set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)

# Default to Release if no build type specified
if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
  set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
endif()

# === Find Qt6 ===
# CMAKE_PREFIX_PATH should be set by the caller (build.bat or cmake command)
find_package(Qt6 REQUIRED COMPONENTS Core Gui Widgets Svg SvgWidgets Network Concurrent PrintSupport)
qt_standard_project_setup()

# === Platform-specific compiler flags ===
# Use CMake's built-in CXX_FLAGS per config + add extra warnings
if(MSVC)
  add_compile_options(/W4 /permissive- /utf-8 /Zc:__cplusplus /Zc:preprocessor /bigobj /wd4127 /wd4456 /wd4189 /wd4100 /wd4505 /wd4267 /wd4244 /wd4305 /EHsc)
else()
  add_compile_options(-Wall -Wextra -Wno-pedantic -Wno-shadow -Wno-conversion -Wno-error)
endif()

# === Third-party includes (headers only - no vcpkg needed) ===
add_library(fin_third_party INTERFACE)
target_include_directories(fin_third_party INTERFACE
  ${CMAKE_SOURCE_DIR}/third_party
  ${CMAKE_SOURCE_DIR}/third_party/spdlog/include
  ${CMAKE_SOURCE_DIR}/third_party/fmt/include
  ${CMAKE_SOURCE_DIR}/third_party/nlohmann
)

# === SQLite ===
# On Windows: download pre-built sqlite3 if not found
# On Linux: use system sqlite3
find_package(SQLite3 QUIET)
if(NOT SQLite3_FOUND)
  find_library(SQLITE3_LIBRARY sqlite3)
  find_path(SQLITE3_INCLUDE_DIR sqlite3.h)
endif()
# If still not found, try to download on Windows
if(NOT SQLITE3_LIBRARY AND WIN32)
  message(STATUS "SQLite3 not found - downloading pre-built...")
  include(${CMAKE_SOURCE_DIR}/cmake/download_sqlite3.cmake OPTIONAL)
endif()

# === Domain layer (pure C++, no Qt) ===
add_library(fin_domain STATIC
  src/domain/src/currency.cpp
  src/domain/src/rounding.cpp
  src/domain/src/money.cpp
  src/domain/src/allocate.cpp
  src/domain/src/ledger.cpp
  src/domain/src/enums.cpp
  src/domain/src/bill.cpp
  src/domain/src/purchase_stock.cpp
)
target_include_directories(fin_domain PUBLIC src/domain/include)

# === App layer ===
qt_add_library(fin_app STATIC
  src/app/src/async.cpp
  src/app/src/ui_watchdog.cpp
  src/app/src/app_dirs.cpp
  src/app/src/log.cpp
  src/app/src/executors.cpp
  src/app/src/formatters.cpp
  src/app/include/fin/app/app_dirs.hpp
  src/app/include/fin/app/async.hpp
  src/app/include/fin/app/executors.hpp
  src/app/include/fin/app/formatters.hpp
  src/app/include/fin/app/log.hpp
  src/app/include/fin/app/ui_watchdog.hpp
  src/app/include/fin/app/util.hpp
)
target_include_directories(fin_app PUBLIC src/app/include)
target_link_libraries(fin_app PUBLIC fin_domain Qt6::Core Qt6::Gui Qt6::Widgets Qt6::Concurrent)
target_link_libraries(fin_app PRIVATE fin_third_party)

# === Services layer ===
qt_add_library(fin_services STATIC
  src/services/src/auth_session.cpp
  src/services/src/billing.cpp
  src/services/src/financial.cpp
  src/services/src/purchase.cpp
  src/services/src/recurring.cpp
  src/services/src/backup_restore.cpp
  src/services/src/csv.cpp
  src/services/src/barcode.cpp
  src/services/src/firebase_auth.cpp
  src/services/src/ai_chat.cpp
  src/services/src/mcp.cpp
  src/services/src/mcp_tools.cpp
  src/services/src/chatbot.cpp
  src/services/src/pdf_export.cpp
  src/services/src/knowledge_seed.cpp
  src/services/src/template_engine.cpp
  src/services/src/label_print.cpp
  src/services/src/tspl.cpp
  src/services/src/design_renderer.cpp
  src/services/src/printing.cpp
  src/services/src/expenses.cpp
  src/services/include/fin/services/ai_chat.hpp
  src/services/include/fin/services/auth_session.hpp
  src/services/include/fin/services/backup_restore.hpp
  src/services/include/fin/services/barcode.hpp
  src/services/include/fin/services/billing.hpp
  src/services/include/fin/services/chatbot.hpp
  src/services/include/fin/services/csv.hpp
  src/services/include/fin/services/design_renderer.hpp
  src/services/include/fin/services/expenses.hpp
  src/services/include/fin/services/financial.hpp
  src/services/include/fin/services/firebase_auth.hpp
  src/services/include/fin/services/knowledge_seed.hpp
  src/services/include/fin/services/label_print.hpp
  src/services/include/fin/services/mcp.hpp
  src/services/include/fin/services/mcp_tools.hpp
  src/services/include/fin/services/pdf_export.hpp
  src/services/include/fin/services/printing.hpp
  src/services/include/fin/services/purchase.hpp
  src/services/include/fin/services/recurring.hpp
  src/services/include/fin/services/template_engine.hpp
  src/services/include/fin/services/tspl.hpp
)
target_include_directories(fin_services PUBLIC
  src/services/include
  src/infra/db/include
)
target_link_libraries(fin_services PUBLIC fin_domain fin_app Qt6::Core Qt6::Network Qt6::Concurrent Qt6::PrintSupport)
target_link_libraries(fin_services PRIVATE fin_third_party)

# === DB layer ===
qt_add_library(fin_db STATIC
  src/infra/db/src/database_manager.cpp
  src/infra/db/src/bill_dao.cpp
  src/infra/db/src/daos.cpp
  src/infra/db/src/daos2.cpp
  src/infra/db/include/fin/db/bill_dao.hpp
  src/infra/db/include/fin/db/dao.hpp
  src/infra/db/include/fin/db/daos.hpp
  src/infra/db/include/fin/db/daos2.hpp
  src/infra/db/include/fin/db/database_manager.hpp
  src/infra/db/include/fin/db/json.hpp
)
target_include_directories(fin_db PUBLIC src/infra/db/include)
target_link_libraries(fin_db PUBLIC fin_domain fin_services Qt6::Core)
target_link_libraries(fin_db PRIVATE fin_third_party)
if(SQLite3_FOUND)
  target_link_libraries(fin_db PRIVATE SQLite::SQLite3)
elseif(SQLITE3_LIBRARY)
  target_include_directories(fin_db PRIVATE ${SQLITE3_INCLUDE_DIR})
  target_link_libraries(fin_db PRIVATE ${SQLITE3_LIBRARY})
endif()

# === UI layer ===
qt_add_library(fin_ui STATIC
  src/ui/src/icon_helper.cpp
  src/ui/src/ui_theme.cpp
  src/ui/src/title_bar.cpp
  src/ui/src/sidebar.cpp
  src/ui/src/studio_app.cpp
  src/ui/src/auth/auth_view.cpp
  src/ui/src/auth/auth_widgets.cpp
  src/ui/src/chat/chatbot_panel.cpp
  src/ui/src/chat/chat_widgets.cpp
  src/ui/src/widgets/data_grid.cpp
  src/ui/src/widgets/toast.cpp
  src/ui/src/widgets/dialog_helper.cpp
  src/ui/src/widgets/foundation.cpp
  src/ui/src/widgets/bill_item_row.cpp
  src/ui/src/widgets/bill_preview_pane.cpp
  src/ui/src/views/dashboard_view.cpp
  src/ui/src/views/buyers_view.cpp
  src/ui/src/views/items_view.cpp
  src/ui/src/views/bills_view.cpp
  src/ui/src/views/create_bill_view.cpp
  src/ui/src/views/suppliers_view.cpp
  src/ui/src/views/reports_view.cpp
  src/ui/src/views/reports_builders.cpp
  src/ui/src/views/settings_view.cpp
  src/ui/src/views/categories_view.cpp
  src/ui/src/views/create_purchase_view.cpp
  src/ui/src/views/expenses_view.cpp
  src/ui/src/views/financials_view.cpp
  src/ui/src/views/history_view.cpp
  src/ui/src/views/label_history_view.cpp
  src/ui/src/views/purchases_view.cpp
  src/ui/src/views/stock_analysis_view.cpp
  src/ui/src/views/templates_view.cpp
  src/ui/src/views/transactions_view.cpp
  src/ui/src/views/transports_view.cpp
  src/ui/src/views/variables_view.cpp
  src/ui/src/views/template_designer.cpp
  src/ui/src/views/designer_canvas.cpp
  src/ui/src/views/designer_ruler.cpp
  src/ui/src/views/designer_toolbar.cpp
  src/ui/src/views/designer_property_inspector.cpp
  src/ui/src/views/designer_interaction.cpp
  src/ui/src/panels/knowledge_hub_panel.cpp
  src/ui/src/panels/mcp_settings_panel.cpp
  src/ui/src/dialogs/all_dialogs.cpp
  src/ui/include/fin/ui/auth/auth_view.hpp
  src/ui/include/fin/ui/auth/auth_widgets.hpp
  src/ui/include/fin/ui/chat/chat_widgets.hpp
  src/ui/include/fin/ui/chat/chatbot_panel.hpp
  src/ui/include/fin/ui/designer_interaction.hpp
  src/ui/include/fin/ui/dialogs/all_dialogs.hpp
  src/ui/include/fin/ui/icon_helper.hpp
  src/ui/include/fin/ui/panels/knowledge_hub_panel.hpp
  src/ui/include/fin/ui/panels/mcp_settings_panel.hpp
  src/ui/include/fin/ui/sidebar.hpp
  src/ui/include/fin/ui/studio_app.hpp
  src/ui/include/fin/ui/title_bar.hpp
  src/ui/include/fin/ui/ui_theme.hpp
  src/ui/include/fin/ui/views/bills_view.hpp
  src/ui/include/fin/ui/views/buyers_view.hpp
  src/ui/include/fin/ui/views/categories_view.hpp
  src/ui/include/fin/ui/views/create_bill_view.hpp
  src/ui/include/fin/ui/views/create_purchase_view.hpp
  src/ui/include/fin/ui/views/dashboard_view.hpp
  src/ui/include/fin/ui/views/designer_canvas.hpp
  src/ui/include/fin/ui/views/designer_property_inspector.hpp
  src/ui/include/fin/ui/views/designer_ruler.hpp
  src/ui/include/fin/ui/views/designer_toolbar.hpp
  src/ui/include/fin/ui/views/expenses_view.hpp
  src/ui/include/fin/ui/views/financials_view.hpp
  src/ui/include/fin/ui/views/history_view.hpp
  src/ui/include/fin/ui/views/items_view.hpp
  src/ui/include/fin/ui/views/label_history_view.hpp
  src/ui/include/fin/ui/views/purchases_view.hpp
  src/ui/include/fin/ui/views/reports_builders.hpp
  src/ui/include/fin/ui/views/reports_view.hpp
  src/ui/include/fin/ui/views/settings_view.hpp
  src/ui/include/fin/ui/views/stock_analysis_view.hpp
  src/ui/include/fin/ui/views/suppliers_view.hpp
  src/ui/include/fin/ui/views/template_designer.hpp
  src/ui/include/fin/ui/views/templates_view.hpp
  src/ui/include/fin/ui/views/transactions_view.hpp
  src/ui/include/fin/ui/views/transports_view.hpp
  src/ui/include/fin/ui/views/variables_view.hpp
  src/ui/include/fin/ui/widgets/bill_item_row.hpp
  src/ui/include/fin/ui/widgets/bill_preview_pane.hpp
  src/ui/include/fin/ui/widgets/data_grid.hpp
  src/ui/include/fin/ui/widgets/dialog_helper.hpp
  src/ui/include/fin/ui/widgets/foundation.hpp
  src/ui/include/fin/ui/widgets/toast.hpp
)
target_include_directories(fin_ui PUBLIC src/ui/include)
target_link_libraries(fin_ui PUBLIC
  fin_domain fin_services fin_app fin_db
  Qt6::Core Qt6::Gui Qt6::Widgets Qt6::Svg Qt6::SvgWidgets Qt6::Network Qt6::PrintSupport)
target_link_libraries(fin_ui PRIVATE fin_third_party)

# Embed the global QSS file as a Qt resource.
qt_add_resources(fin_ui "qss"
  PREFIX "/qss"
  FILES resources/css/globalfile.qss
)

# (compiler flags set globally via add_compile_options above)

# === Main executable ===
qt_add_executable(invoicestudio
  src/app/src/main.cpp
  src/app/src/smoke_test.cpp
)
target_link_libraries(invoicestudio PRIVATE fin_ui fin_app)
if(MSVC)
  
  set_target_properties(invoicestudio PROPERTIES WIN32_EXECUTABLE ON)
else()
  
endif()

# === Post-build: run windeployqt on Windows ===
if(WIN32)
  find_program(WINDEPLOYQT windeployqt HINTS "${Qt6_DIR}/../../../bin" "${Qt6_DIR}/bin")
  if(WINDEPLOYQT)
    add_custom_command(TARGET invoicestudio POST_BUILD
      COMMAND ${WINDEPLOYQT} --no-translations --no-system-d3d-compiler
              --no-opengl-sw --no-quick-import
              $<TARGET_FILE:invoicestudio>
      COMMENT "Running windeployqt to copy Qt DLLs..."
    )
    message(STATUS "windeployqt found: ${WINDEPLOYQT}")
  else()
    message(WARNING "windeployqt not found - Qt DLLs won't be copied automatically.")
    message(STATUS "You may need to add Qt bin directory to your PATH to run the app.")
  endif()
endif()
