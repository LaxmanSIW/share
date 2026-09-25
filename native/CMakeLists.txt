# CMakeLists.txt — lite build for the sandbox (no vcpkg)
set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTOUIC ON)
set(CMAKE_AUTORCC ON)
set(CMAKE_AUTOUIC_SEARCH_PATHS src)
# Uses third_party/ headers for spdlog + fmt + nlohmann/json instead of vcpkg.
cmake_minimum_required(VERSION 3.25)

project(InvoiceStudioLite
  VERSION 4.0.0
  DESCRIPTION "Lite build of InvoiceStudio Native (sandbox verification)"
  LANGUAGES CXX C)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Output dirs
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)

# Find Qt6 (installed via aqtinstall)
set(CMAKE_PREFIX_PATH "/home/z/my-project/qt/6.7.3/gcc_64")
find_package(Qt6 REQUIRED COMPONENTS Core Gui Widgets Svg SvgWidgets Network Concurrent PrintSupport)

qt_standard_project_setup()

# Domain layer (pure C++)
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
target_compile_options(fin_domain PRIVATE -Wall -Wextra -Wno-pedantic -Wno-shadow -Wno-conversion -O2 -Wno-error)

# Third-party includes (no vcpkg)
add_library(fin_third_party INTERFACE)
target_include_directories(fin_third_party INTERFACE
  ${CMAKE_SOURCE_DIR}/third_party
  ${CMAKE_SOURCE_DIR}/third_party/spdlog/include
  ${CMAKE_SOURCE_DIR}/third_party/fmt/include
  ${CMAKE_SOURCE_DIR}/third_party/nlohmann
)

# SQLite — use system lib
find_library(SQLITE3_LIBRARY sqlite3)
find_path(SQLITE3_INCLUDE_DIR sqlite3.h)

# App layer (Qt + spdlog)
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
target_compile_options(fin_app PRIVATE -Wall -Wextra -Wno-pedantic -Wno-shadow -Wno-conversion -O2 -Wno-error)

# Services layer (Qt + nlohmann/json)
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
target_compile_options(fin_services PRIVATE -Wall -Wextra -Wno-pedantic -Wno-shadow -Wno-conversion -O2 -Wno-error)

# DB layer (sqlite3)
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
target_include_directories(fin_db PRIVATE ${SQLITE3_INCLUDE_DIR})
target_link_libraries(fin_db PRIVATE ${SQLITE3_LIBRARY})
target_compile_options(fin_db PRIVATE -Wall -Wextra -Wno-pedantic -Wno-shadow -Wno-conversion -O2 -Wno-error)

# UI layer (Qt widgets + svg)
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
target_compile_options(fin_ui PRIVATE -Wall -Wextra -Wno-pedantic -Wno-shadow -Wno-conversion -O2 -Wno-error)

# Embed the global QSS file as a Qt resource.
qt_add_resources(fin_ui "qss"
  PREFIX "/qss"
  FILES resources/css/globalfile.qss
)

# Main executable
qt_add_executable(invoicestudio
  src/app/src/main.cpp
  src/app/src/smoke_test.cpp
)
target_link_libraries(invoicestudio PRIVATE fin_ui fin_app)
target_compile_options(invoicestudio PRIVATE -Wall -Wextra -Wno-pedantic -Wno-shadow -Wno-conversion -O2 -Wno-error)
