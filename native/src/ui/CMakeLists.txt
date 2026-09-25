# src/ui/CMakeLists.txt — UI layer
find_package(Qt6 REQUIRED COMPONENTS Core Gui Widgets Svg SvgWidgets)
find_package(nlohmann_json REQUIRED)

qt_add_library(fin_ui STATIC
  src/icon_helper.cpp
  src/ui_theme.cpp
  src/title_bar.cpp
  src/sidebar.cpp
  src/studio_app.cpp
  src/auth/auth_view.cpp
  src/auth/auth_widgets.cpp
  src/chat/chatbot_panel.cpp
  src/chat/chat_widgets.cpp
  src/widgets/data_grid.cpp
  src/widgets/toast.cpp
  src/widgets/dialog_helper.cpp
  src/widgets/foundation.cpp
  src/widgets/bill_item_row.cpp
  src/widgets/bill_preview_pane.cpp
  src/views/dashboard_view.cpp
  src/views/buyers_view.cpp
  src/views/items_view.cpp
  src/views/bills_view.cpp
  src/views/create_bill_view.cpp
  src/views/suppliers_view.cpp
  src/views/reports_view.cpp
  src/views/reports_builders.cpp
  src/views/settings_view.cpp
  src/views/categories_view.cpp
  src/views/create_purchase_view.cpp
  src/views/expenses_view.cpp
  src/views/financials_view.cpp
  src/views/history_view.cpp
  src/views/label_history_view.cpp
  src/views/purchases_view.cpp
  src/views/stock_analysis_view.cpp
  src/views/templates_view.cpp
  src/views/transactions_view.cpp
  src/views/transports_view.cpp
  src/views/variables_view.cpp
  src/views/template_designer.cpp
  src/views/designer_canvas.cpp
  src/views/designer_ruler.cpp
  src/views/designer_toolbar.cpp
  src/views/designer_property_inspector.cpp
  src/views/designer_interaction.cpp
  src/panels/knowledge_hub_panel.cpp
  src/panels/mcp_settings_panel.cpp
  src/dialogs/all_dialogs.cpp
)
target_include_directories(fin_ui PUBLIC include)
target_link_libraries(fin_ui
  PUBLIC
    fin::domain
    fin::services
    fin::app
    fin::db
    Qt6::Core
    Qt6::Gui
    Qt6::Widgets
    Qt6::Svg
    Qt6::SvgWidgets
  PRIVATE
    nlohmann_json::nlohmann_json
)

# Embed the global QSS file as a Qt resource so UiTheme::apply_theme() can load
# it via ":/qss/globalfile.qss".
qt_add_resources(fin_ui "qss"
  PREFIX "/qss"
  FILES
    ../../resources/css/globalfile.qss
)
