# src/infra/db/CMakeLists.txt — Data layer
find_package(Qt6 REQUIRED COMPONENTS Core)
find_package(SQLite3 REQUIRED)
find_package(nlohmann_json REQUIRED)

qt_add_library(fin_db STATIC
  src/database_manager.cpp
  src/bill_dao.cpp
  src/daos.cpp
  src/daos2.cpp
)
target_include_directories(fin_db
  PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)
target_link_libraries(fin_db
  PUBLIC
    fin::domain
    fin::services
    Qt6::Core
  PRIVATE
    ${SQLite3_LIBRARIES}
    nlohmann_json::nlohmann_json
)
target_include_directories(fin_db PRIVATE ${SQLite3_INCLUDE_DIRS})
