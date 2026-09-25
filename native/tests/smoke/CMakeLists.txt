find_package(Qt6 REQUIRED COMPONENTS Core Widgets)
enable_testing()
# Headless smoke test (QT_QPA_PLATFORM=offscreen) — runs the app with --smoke and asserts it exits cleanly.
# Phase 5+ once the views are wired.
