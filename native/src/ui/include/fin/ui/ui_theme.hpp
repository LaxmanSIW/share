// fin/ui/ui_theme.hpp — UiTheme factory helpers (port of Java UiTheme.java)
//
// The Java original's golden rule: NEVER call node.setStyle(...). Inline styles
// in Qt have higher precedence than stylesheet :hover rules, which is why
// hover states died in the v2 app. Everything here attaches QSS classes from
// globalfile.qss; dynamic variation is expressed with extra style classes
// (setProperty("class", "accent-gold")), never inline styles.
#pragma once
#include <QFrame>
#include <QLabel>
#include <QLineEdit>
#include <QPushButton>
#include <QHBoxLayout>
#include <QVBoxLayout>
#include <QSpacerItem>
#include <QWidget>
#include <QString>

#include <string_view>

namespace fin::ui {

class UiTheme {
 public:
  // === Layout ===

  /// Standard page container with consistent padding — every view root uses this.
  static QFrame* page(QWidget* parent = nullptr);

  /// Card container (surface + border + hover lift via QSS).
  static QFrame* card(int spacing = 12, QWidget* parent = nullptr);

  /// Horizontal row inside a card.
  static QWidget* row(int spacing, QWidget* parent = nullptr);

  /// Flexible horizontal spacer (for pushing widgets apart in HBox).
  static QSpacerItem* hspacer();

  /// Flexible vertical spacer.
  static QSpacerItem* vspacer();

  // === Typography ===

  static QLabel* pageTitle(const QString& text, QWidget* parent = nullptr);
  static QLabel* pageSubtitle(const QString& text, QWidget* parent = nullptr);
  static QLabel* headingLarge(const QString& text, QWidget* parent = nullptr);
  static QLabel* cardTitle(const QString& text, QWidget* parent = nullptr);
  static QLabel* cardValue(const QString& text, QWidget* parent = nullptr);
  static QLabel* muted(const QString& text, QWidget* parent = nullptr);
  static QLabel* micro(const QString& text, QWidget* parent = nullptr);
  static QLabel* bold(const QString& text, QWidget* parent = nullptr);

  // === Status badges (used in tables) ===

  enum class Badge { Warning, Success, Error, Accent, Neutral };
  static QLabel* badge(const QString& text, Badge kind, QWidget* parent = nullptr);

  // === Buttons ===

  static QPushButton* primaryButton(const QString& text, QWidget* parent = nullptr);
  static QPushButton* ghostButton(const QString& text, QWidget* parent = nullptr);
  static QPushButton* iconButton(std::string_view icon_name, int size = 16,
                                  const QString& tooltip = {}, QWidget* parent = nullptr);
  static QPushButton* primaryIconButton(std::string_view icon_name, const QString& text,
                                        QWidget* parent = nullptr);

  // === Inputs ===

  static QLineEdit* lineEdit(const QString& placeholder = {}, QWidget* parent = nullptr);

  // === Apply QSS theme globally ===

  /// Load resources/css/globalfile.qss from the bundled Qt resource file
  /// (:/qss/globalfile.qss) or from the filesystem fallback path, and set it
  /// as the application-wide stylesheet. Called once at startup.
  static void apply_theme(class QApplication* app);
};

} // namespace fin::ui
