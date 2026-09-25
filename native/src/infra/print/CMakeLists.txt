# src/infra/print/CMakeLists.txt — Phase 3 placeholder
find_package(Qt6 REQUIRED COMPONENTS Core Gui PrintSupport)
qt_add_library(fin_print STATIC)
target_include_directories(fin_print PUBLIC include)
target_link_libraries(fin_print PUBLIC fin::domain Qt6::Core Qt6::Gui Qt6::PrintSupport)
