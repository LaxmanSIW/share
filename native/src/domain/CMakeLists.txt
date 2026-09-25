# src/domain/CMakeLists.txt — Pure C++ domain library, no Qt.
fin_add_library(fin_domain STATIC
  PUBLIC_INCLUDES
    ${CMAKE_CURRENT_SOURCE_DIR}/include
  SOURCES
    src/currency.cpp
    src/rounding.cpp
    src/money.cpp
    src/allocate.cpp
    src/ledger.cpp
    src/enums.cpp
    src/bill.cpp
    src/purchase_stock.cpp
)
target_link_libraries(fin_domain PUBLIC fin::build_flags)
