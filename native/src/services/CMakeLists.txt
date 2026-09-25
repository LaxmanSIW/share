# src/services/CMakeLists.txt — Service layer
find_package(Qt6 REQUIRED COMPONENTS Core Network Concurrent)
find_package(nlohmann_json REQUIRED)

qt_add_library(fin_services STATIC
  src/auth_session.cpp
  src/billing.cpp
  src/financial.cpp
  src/purchase.cpp
  src/recurring.cpp
  src/backup_restore.cpp
  src/csv.cpp
  src/barcode.cpp
  src/firebase_auth.cpp
  src/ai_chat.cpp
  src/mcp.cpp
  src/mcp_tools.cpp
  src/chatbot.cpp
  src/pdf_export.cpp
  src/knowledge_seed.cpp
  src/template_engine.cpp
  src/label_print.cpp
  src/tspl.cpp
  src/design_renderer.cpp
  src/printing.cpp
  src/expenses.cpp
)
target_include_directories(fin_services
  PUBLIC
    include
    ../infra/db/include
)
target_link_libraries(fin_services
  PUBLIC
    fin_domain
    fin_app
    Qt6::Core
    Qt6::Network
    Qt6::Concurrent
    Qt6::PrintSupport
  PRIVATE
    fin_third_party
)
