// fin/ui/widgets/foundation.hpp — Consolidated foundation widgets
//
// Port of Java: AppShortcuts (99), ShortcutCatalog, ShortcutManager (200),
// ShortcutsDialog (100), ShortcutsPanel (150), WindowResizeHelper (115),
// WindowStateManager (120), UserProfilePill (85), ModelStatusDot (60),
// ViewEpochTracker (100), DataManager (200), CopyButtonFactory (80).
//
// Consolidated into one header because they're small, interdependent, and
// the Java original split them only due to Java's one-public-class-per-file
// rule.
#pragma once
#include <QAction>
#include <QKeySequence>
#include <QLabel>
#include <QObject>
#include <QPointer>
#include <QPushButton>
#include <QLineEdit>
#include <QAction>
#include <QKeySequence>
#include <QSettings>
#include <QString>
#include <QWidget>
#include <QMainWindow>
#include <functional>
#include <unordered_map>
#include <atomic>

namespace fin::ui {

class StudioApp;

// === AppShortcuts: keyboard shortcut registry + QActions wired to the shell ===

class AppShortcuts : public QObject {
  Q_OBJECT
 public:
  explicit AppShortcuts(StudioApp* app, QObject* parent = nullptr);

  /// Register a shortcut with the given key sequence + callback.
  /// Skill §3.5: handlers are O(1), allocation-free (per skill rule §3.5b).
  void register_shortcut(const QString& id, const QKeySequence& key,
                          std::function<void()> callback,
                          const QString& display_label = {});

  /// Returns a list of all registered shortcuts for the ShortcutsDialog.
  struct ShortcutEntry {
    QString id;
    QKeySequence key;
    QString label;
  };
  std::vector<ShortcutEntry> all() const;

 private:
  StudioApp* app_;
  std::unordered_map<QString, QAction*> actions_;
  std::vector<ShortcutEntry> entries_;
};

// === ShortcutCatalog: the static list of standard InvoiceStudio shortcuts ===
// (mirrors the Java ShortcutCatalog — kept here as compile-time data)
struct ShortcutCatalog {
  // Global navigation
  static constexpr const char* NAV_DASHBOARD    = "Ctrl+1";
  static constexpr const char* NAV_BILLS         = "Ctrl+2";
  static constexpr const char* NAV_CREATE_BILL   = "Ctrl+N";
  static constexpr const char* NAV_BUYERS        = "Ctrl+3";
  static constexpr const char* NAV_ITEMS         = "Ctrl+4";
  static constexpr const char* NAV_REPORTS        = "Ctrl+5";
  static constexpr const char* NAV_SETTINGS       = "Ctrl+Comma";
  // Document actions
  static constexpr const char* DOC_SAVE          = "Ctrl+S";
  static constexpr const char* DOC_SAVE_PRINT     = "Ctrl+P";
  static constexpr const char* DOC_SAVE_PDF       = "Ctrl+Shift+P";
  static constexpr const char* DOC_DELETE         = "Delete";
  // Edit actions
  static constexpr const char* EDIT_UNDO          = "Ctrl+Z";
  static constexpr const char* EDIT_REDO          = "Ctrl+Y";
  static constexpr const char* EDIT_COPY         = "Ctrl+C";
  static constexpr const char* EDIT_PASTE        = "Ctrl+V";
  static constexpr const char* EDIT_CUT          = "Ctrl+X";
  static constexpr const char* EDIT_SEARCH       = "Ctrl+F";
  // View actions
  static constexpr const char* VIEW_TOGGLE_CHATBOT = "Ctrl+J";
  static constexpr const char* VIEW_ZOOM_IN       = "Ctrl++";
  static constexpr const char* VIEW_ZOOM_OUT      = "Ctrl+-";
  static constexpr const char* VIEW_ZOOM_100      = "Ctrl+0";
};

// === WindowStateManager: persist/restore window geometry ===

class WindowStateManager {
 public:
  /// Save window geometry under the given key (e.g. "StudioApp").
  static void save(QMainWindow* window, const QString& key);
  /// Restore window geometry; returns true if a saved geometry existed.
  static bool restore(QMainWindow* window, const QString& key);
  /// Save a dialog's geometry (used by all modal/modeless dialogs).
  static void save_dialog(QWidget* dlg, const QString& key);
  static bool restore_dialog(QWidget* dlg, const QString& key);
};

// === UserProfilePill: sidebar footer with avatar + display name + email ===

class UserProfilePill : public QWidget {
  Q_OBJECT
 public:
  explicit UserProfilePill(QWidget* parent = nullptr);
  void set_user(const QString& display_name, const QString& email);
  void set_signed_in(bool signed_in);
  void set_avatar_color(const QString& hex_color);
 signals:
  void clicked();
 protected:
  void mousePressEvent(QMouseEvent* e) override;
 private:
  QLabel* avatar_;
  QLabel* name_;
  QLabel* email_;
  bool signed_in_{false};
};

// === ModelStatusDot: small green/red/yellow dot showing AI provider status ===

class ModelStatusDot : public QWidget {
  Q_OBJECT
 public:
  enum class Status { Online, Offline, Error, Checking };
  explicit ModelStatusDot(QWidget* parent = nullptr);
  void set_status(Status s);
 protected:
  void paintEvent(QPaintEvent* e) override;
 private:
  Status status_{Status::Checking};
};

// === ViewEpochTracker: per-view freshness counter ===
//
// Skill rule (Java SKILL.md applied log 2026-09-16):
// "stale-while-revalidate nav: dataEpoch counter bumped on every invalidation;
//  cached views paint instantly, only re-refresh when the epoch moved; fast
//  A→B nav queues refreshers."

class ViewEpochTracker {
 public:
  /// Returns the current global data epoch (incremented on every invalidation).
  std::uint64_t global_epoch() const noexcept { return global_epoch_.load(std::memory_order_acquire); }

  /// Bump the global epoch — call after any DAO write (insert/update/delete).
  void bump_global() noexcept { global_epoch_.fetch_add(1, std::memory_order_release); }

  /// Record the epoch this view was last refreshed at.
  void mark_view_fresh(const QString& view_id);

  /// Returns true if the view's last-refreshed epoch matches the global.
  bool is_view_fresh(const QString& view_id) const;

 private:
  std::atomic<std::uint64_t> global_epoch_{0};
  std::unordered_map<QString, std::uint64_t> view_epochs_;
};

// === DataManager: data cache + epoch counter (single connection surface) ===

class DataManager {
 public:
  /// Returns the singleton instance.
  static DataManager& instance();

  ViewEpochTracker& epochs() { return epochs_; }

  /// Bump the global epoch (call after any write). Queues background refreshes
  /// of any visible view whose view-epoch is now stale.
  void invalidate();

 private:
  ViewEpochTracker epochs_;
};

// === CopyButtonFactory: clipboard copy button with visual feedback ===

class CopyButtonFactory {
 public:
  /// Make a small icon-only copy button that copies `text` to the clipboard
  /// and shows a brief "Copied!" toast.
  static QPushButton* make(QString text_to_copy, QWidget* parent = nullptr);
  /// Make a copy button bound to a source widget (e.g. QLineEdit) — copies
  /// the source's text when clicked.
  static QPushButton* make_bound(const QLineEdit* source, QWidget* parent = nullptr);
};

} // namespace fin::ui
