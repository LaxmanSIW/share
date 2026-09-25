// Smoke tests for the async spine.
#include "fin/app/async.hpp"
#include "fin/app/ui_watchdog.hpp"

#include <QApplication>
#include <QCoreApplication>
#include <QThreadPool>
#include <QTimer>
#include <gtest/gtest.h>

using namespace fin::app;

TEST(AsyncSmoke, DebouncerCoalescesBursts) {
  // Construct a QApplication since Debouncer uses QTimer.
  int argc = 1;
  char arg0[] = "test";
  char* argv[1] = {arg0};
  QApplication app(argc, argv);

  Debouncer d(std::chrono::milliseconds(50));
  int count = 0;
  for (int i = 0; i < 10; ++i) d.trigger([&count] { ++count; });
  // After 50ms the timer fires once.
  QTimer::singleShot(200, &app, [] {});  // keep app alive
  QTimer::singleShot(300, &app, [&] { app.quit(); });
  app.exec();
  EXPECT_EQ(count, 1);
}

int main(int argc, char** argv) {
  ::testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}
