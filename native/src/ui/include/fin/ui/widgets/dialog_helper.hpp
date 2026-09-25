// fin/ui/widgets/dialog_helper.hpp — Async dialogs (port of Java DialogHelper.java)
//
// Skill rule §8: NEVER use exec() inside slots. These helpers use open() +
// signals so the user can click "Post" twice without re-entrancy bugs.
#pragma once
#include <QString>
#include <functional>
class QDialog;

namespace fin::ui {

class DialogHelper {
 public:
  /// Show a confirmation dialog with the given title + message.
  /// Calls on_done(true) if user clicks OK, on_done(false) if Cancel.
  /// Modeless — uses open() + signals so the user can interact with the
  /// main window while the dialog is up.
  static void confirm(const QString& title, const QString& message,
                       std::function<void(bool)> on_done);

  /// Show a confirmation dialog with a danger-style "Delete" button (red).
  static void confirm_delete(const QString& title, const QString& message,
                              std::function<void(bool)> on_done);

  /// Show an informational dialog (single "OK" button).
  static void info(const QString& title, const QString& message,
                    std::function<void()> on_done = {});

  /// Show an error dialog with the given exception message.
  static void error(const QString& title, const QString& message,
                     std::function<void()> on_done = {});

  /// Show a text input dialog. Returns the user's input via on_done; empty
  /// string if Cancel.
  static void input(const QString& title, const QString& label, const QString& default_value,
                     std::function<void(QString)> on_done);
};

} // namespace fin::ui
