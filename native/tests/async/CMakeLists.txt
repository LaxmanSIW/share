find_package(Qt6 REQUIRED COMPONENTS Core Widgets Concurrent)
find_package(GTest REQUIRED)
enable_testing()
add_executable(fin_async_tests
  test_async.cpp
)
target_link_libraries(fin_async_tests PRIVATE fin::app GTest::gtest GTest::gtest_main Qt6::Core Qt6::Widgets Qt6::Concurrent fin::build_flags)
include(GoogleTest)
gtest_discover_tests(fin_async_tests)
