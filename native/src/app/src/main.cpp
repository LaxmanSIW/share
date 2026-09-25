// src/app/src/main.cpp — Phase 7: launches StudioApp or runs --smoke
#include "fin/ui/studio_app.hpp"
#include "fin/app/app_dirs.hpp"
#include "fin/app/log.hpp"

#include <QApplication>
#include <QCommandLineParser>
#include <QTimer>

int main(int argc, char** argv) {
  QApplication app(argc, argv);
  app.setApplicationName("InvoiceStudio");
  app.setApplicationVersion("4.0.0");
  app.setOrganizationName("InvoiceStudio");

  QCommandLineParser parser;
  parser.setApplicationDescription("InvoiceStudio — Native C++/Qt6 invoice & accounting");
  parser.addHelpOption();
  parser.addVersionOption();
  QCommandLineOption data_dir_opt({"d", "data-dir"},
    "Use <dir> as the per-user data directory (tests / portable).", "dir");
  QCommandLineOption smoke_opt("smoke",
    "Run headless smoke test (skill §7 layer 3). Exits 0 on success.");
  parser.addOption(data_dir_opt);
  parser.addOption(smoke_opt);
  parser.process(app);

  if (parser.isSet(data_dir_opt)) {
    fin::app::set_data_dir_override(std::filesystem::path(parser.value(data_dir_opt).toStdString()));
  }

  fin::app::log::info("InvoiceStudio launching…");

  if (parser.isSet(smoke_opt)) {
    // External smoke_test.cpp's run_smoke() — declared in the smoke_test.cpp
    // translation unit. We forward to it via QMetaObject::invokeMethod on
    // startup. Phase 7: real impl links smoke_test.cpp in the executable.
    fin::app::log::info("Smoke test requested — running.");
    extern int run_smoke();
    QTimer::singleShot(0, qApp, [] {
      int rc = run_smoke();
      qApp->exit(rc);
    });
    return app.exec();
  }

  fin::ui::StudioApp shell;
  shell.startup();

  return app.exec();
}
