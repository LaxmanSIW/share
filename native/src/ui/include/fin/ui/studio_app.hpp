// fin/ui/studio_app.hpp — Application shell (port of Java StudioApp.java)
//
// Production-hardened shell (skill rule 5.2 role 1 — coordination only):
//  - Sidebar navigation (VS Code style) with active gold indicator + hover states
//    (built by Sidebar, user pill by Sidebar).
//  - View caching: heavy views are built once and refreshed on show, navigation
//    is instant instead of rebuilding the whole scene graph per click.
//  - Frameless window with custom title bar (TitleBar).
//  - Window geometry persisted between runs (WindowStateManager — basic impl).
//  - All shared DAO access via DatabaseManager::instance().
//  - Startup work that touches the DB runs on a background executor.
//
// USER REQUIREMENT: responsive height/width — the central view stack expands
// to fill available space; sidebar + title bar are fixed-width/height.
#pragma once
#include <QMainWindow>
#include <QStackedWidget>
#include <QHash>
#include <QString>
#include <memory>
#include <string>
#include <unordered_map>

namespace fin::ui {

class TitleBar;
class Sidebar;
class ChatbotPanel;
class AuthView;
class AppShortcuts;

class StudioApp : public QMainWindow {
  Q_OBJECT
 public:
  explicit StudioApp(QWidget* parent = nullptr);
  ~StudioApp() override;

  /// Bootstrap: open DB, run migrations, apply QSS theme, build UI, show window.
  void startup();

  /// Show a view by id ("dashboard", "buyers", "items", ...). Builds the view
  /// lazily on first request and caches it (stale-while-revalidate pattern).
  void show_view(const QString& id);

  /// Show the chatbot panel (overlay on the right).
  void toggle_chatbot();

  /// Show the auth view (login). Called before the main UI when no session.
  void show_auth();

 protected:
  void closeEvent(QCloseEvent* e) override;
  void resizeEvent(QResizeEvent* e) override;

 private:
  void build_shell_();
  void register_nav_();
  void load_geometry_();
  void save_geometry_();

  TitleBar*        title_bar_;
  Sidebar*         sidebar_;
  QStackedWidget*  view_stack_;
  QHash<QString, int> view_index_;  // id → index in view_stack_
  ChatbotPanel*    chatbot_panel_;
  AuthView*        auth_view_;
  AppShortcuts*    shortcuts_{nullptr};

  // Owned views (lazy-built) — keyed by id
  std::unordered_map<std::string, QWidget*> view_cache_;
};

} // namespace fin::ui
