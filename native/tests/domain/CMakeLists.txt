find_package(Qt6 REQUIRED COMPONENTS Core)
find_package(GTest REQUIRED)
find_package(RapidCheck REQUIRED)
find_package(nlohmann_json REQUIRED)  # used by future tests

enable_testing()
add_executable(fin_domain_tests
  test_money.cpp
)
add_executable(fin_billing_tests
  test_billing.cpp
)
add_executable(fin_recurring_tests
  test_recurring.cpp
)
add_executable(fin_template_engine_tests
  test_template_engine.cpp
)
target_link_libraries(fin_domain_tests
  PRIVATE
    fin::domain
    GTest::gtest
    GTest::gtest_main
    rapidcheck
    rapidcheck_gtest
)
target_link_libraries(fin_billing_tests
  PRIVATE
    fin::domain
    fin::services
    fin::app
    Qt6::Core
)
target_link_libraries(fin_recurring_tests
  PRIVATE
    fin::domain
    fin::services
    fin::app
    Qt6::Core
)
target_link_libraries(fin_template_engine_tests
  PRIVATE
    fin::domain
    fin::services
    Qt6::Core
)
target_link_libraries(fin_domain_tests   PRIVATE fin::build_flags)
target_link_libraries(fin_billing_tests PRIVATE fin::build_flags)
target_link_libraries(fin_recurring_tests PRIVATE fin::build_flags)
target_link_libraries(fin_template_engine_tests PRIVATE fin::build_flags)
include(GoogleTest)
gtest_discover_tests(fin_domain_tests)
gtest_discover_tests(fin_billing_tests)
gtest_discover_tests(fin_recurring_tests)
gtest_discover_tests(fin_template_engine_tests)
