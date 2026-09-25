// fin/ui/widgets/foundation.cpp — Implementation of all foundation widgets
#include "fin/ui/widgets/foundation.hpp"
#include "fin/ui/widgets/toast.hpp"
#include "fin/ui/icon_helper.hpp"
#include "fin/ui/studio_app.hpp"
#include "fin/app/log.hpp"

#include <QApplication>
#include <QClipboard>
#include <QHBoxLayout>
#include <QVBoxLayout>
#include <QLabel>
#include <QMouseEvent>
#include <QPainter>
#include <QAction>
#include <QMainWindow>
#include <QScreen>
#include <QGuiApplication>

#include <unordered_map>

namespace fin::ui {

// ============================================================
// AppShortcuts
// ============================================================

AppShortcuts::AppShortcuts(StudioApp* app, QObject* parent)
  : QObject(parent), app_(app) {}

void AppShortcuts::register_shortcut(const QString& id, const QKeySequence& key,
                                       std::function<void()> callback,
                                       const QString& display_label) {
  if (auto it = actions_.find(id); it != actions_.end()) {
    delete it->second;
    actions_.erase(it);
  }
  auto* action = new QAction(key, display_label.isEmpty() ? id : display_label, this);
  action->setShortcutContext(Qt::ApplicationShortcut);
  // Wire to the callback.
  QObject::connect(action, &QAction::triggered, this, [cb = std::move(callback)]() {
    if (cb) cb();
  });
  if (app_) {
    app_->addAction(action);  // QMainWindow takes ownership of the QAction
  } else {
    qApp->addAction(action);
  }
  actions_[id] = action;
  entries_.push_back({id, key, display_label.isEmpty() ? id : display_label});
}

std::vector<AppShortcuts::ShortcutEntry> AppShortcuts::all() const {
  return entries_;
}

// ============================================================
// WindowStateManager
// ============================================================

void WindowStateManager::save(QMainWindow* window, const QString& key) {
  QSettings s;
  s.beginGroup(key);
  s.setValue("geometry", window->saveGeometry());
  s.setValue("state",     window->saveState());
  s.endGroup();
}

bool WindowStateManager::restore(QMainWindow* window, const QString& key) {
  QSettings s;
  s.beginGroup(key);
  bool ok = false;
  auto geom = s.value("geometry").toByteArray();
  if (!geom.isEmpty()) {
    window->restoreGeometry(geom);
    ok = true;
  }
  auto state = s.value("state").toByteArray();
  if (!state.isEmpty()) window->restoreState(state);
  s.endGroup();
  return ok;
}

void WindowStateManager::save_dialog(QWidget* dlg, const QString& key) {
  QSettings s;
  s.beginGroup("Dialogs");
  s.setValue(key, dlg->saveGeometry());
  s.endGroup();
}

bool WindowStateManager::restore_dialog(QWidget* dlg, const QString& key) {
  QSettings s;
  s.beginGroup("Dialogs");
  bool ok = false;
  auto g = s.value(key).toByteArray();
  if (!g.isEmpty()) {
    dlg->restoreGeometry(g);
    ok = true;
  }
  s.endGroup();
  return ok;
}

// ============================================================
// UserProfilePill
// ============================================================

UserProfilePill::UserProfilePill(QWidget* parent) : QWidget(parent) {
  setProperty("class", "user-pill");
  setFixedHeight(56);
  setCursor(Qt::PointingHandCursor);
  auto* l = new QHBoxLayout(this);
  l->setContentsMargins(12, 8, 12, 8);
  l->setSpacing(10);

  avatar_ = new QLabel("?", this);
  avatar_->setFixedSize(32, 32);
  avatar_->setAlignment(Qt::AlignCenter);
  avatar_->setStyleSheet("background-color: #64748B; color: #0B0E13; border-radius: 16px; font-weight: bold; font-size: 12px;");
  l->addWidget(avatar_);

  auto* name_box = new QVBoxLayout();
  name_box->setSpacing(0);
  name_ = new QLabel("Not signed in", this);
  name_->setStyleSheet("color: #F4F4F5; font-size: 12px; font-weight: bold;");
  email_ = new QLabel("", this);
  email_->setStyleSheet("color: #94A3B8; font-size: 11px;");
  name_box->addWidget(name_);
  name_box->addWidget(email_);
  l->addLayout(name_box);
  l->addStretch();
}

void UserProfilePill::set_user(const QString& display_name, const QString& email) {
  name_->setText(display_name);
  email_->setText(email);
  // Initials from display name (first 2 letters of first 2 words).
  QStringList parts = display_name.split(' ', Qt::SkipEmptyParts);
  QString initials;
  for (int i = 0; i < std::min(2, parts.size()); ++i) {
    if (!parts[i].isEmpty()) initials.append(parts[i][0].toUpper());
  }
  if (initials.isEmpty()) initials = "?";
  avatar_->setText(initials);
  signed_in_ = !display_name.isEmpty();
  if (signed_in_) {
    avatar_->setStyleSheet("background-color: #D9A13B; color: #0B0E13; border-radius: 16px; font-weight: bold; font-size: 12px;");
  } else {
    avatar_->setStyleSheet("background-color: #64748B; color: #0B0E13; border-radius: 16px; font-weight: bold; font-size: 12px;");
  }
}

void UserProfilePill::set_signed_in(bool signed_in) {
  signed_in_ = signed_in;
  if (!signed_in) {
    name_->setText("Not signed in");
    email_->setText("");
    avatar_->setText("?");
    avatar_->setStyleSheet("background-color: #64748B; color: #0B0E13; border-radius: 16px; font-weight: bold; font-size: 12px;");
  }
}

void UserProfilePill::set_avatar_color(const QString& hex) {
  avatar_->setStyleSheet(QString("background-color: %1; color: #0B0E13; border-radius: 16px; font-weight: bold; font-size: 12px;").arg(hex));
}

void UserProfilePill::mousePressEvent(QMouseEvent* e) {
  if (e->button() == Qt::LeftButton) {
    emit clicked();
  }
  QWidget::mousePressEvent(e);
}

// ============================================================
// ModelStatusDot
// ============================================================

ModelStatusDot::ModelStatusDot(QWidget* parent) : QWidget(parent) {
  setFixedSize(12, 12);
}

void ModelStatusDot::set_status(Status s) {
  status_ = s;
  update();
}

void ModelStatusDot::paintEvent(QPaintEvent* /*e*/) {
  QPainter p(this);
  p.setRenderHint(QPainter::Antialiasing, true);
  QColor color;
  switch (status_) {
    case Status::Online:  color = QColor("#10B981"); break;
    case Status::Offline: color = QColor("#64748B"); break;
    case Status::Error:    color = QColor("#EF4444"); break;
    case Status::Checking: color = QColor("#F59E0B"); break;
  }
  p.setBrush(color);
  p.setPen(Qt::NoPen);
  p.drawEllipse(rect().adjusted(1, 1, -1, -1));
}

// ============================================================
// ViewEpochTracker
// ============================================================

void ViewEpochTracker::mark_view_fresh(const QString& view_id) {
  view_epochs_[view_id] = global_epoch();
}

bool ViewEpochTracker::is_view_fresh(const QString& view_id) const {
  auto it = view_epochs_.find(view_id);
  if (it == view_epochs_.end()) return false;
  return it->second == global_epoch();
}

// ============================================================
// DataManager
// ============================================================

DataManager& DataManager::instance() {
  static DataManager inst;
  return inst;
}

void DataManager::invalidate() {
  epochs_.bump_global();
  // Phase 5+: queued refreshes of visible stale views go here.
}

// ============================================================
// CopyButtonFactory
// ============================================================

QPushButton* CopyButtonFactory::make(QString text_to_copy, QWidget* parent) {
  auto* btn = new QPushButton(parent);
  btn->setProperty("class", "icon-button");
  btn->setIcon(IconHelper::icon(IconHelper::ICON_COPY, 14, QColor("#94A3B8")));
  btn->setIconSize(QSize(14, 14));
  btn->setFixedSize(28, 28);
  btn->setCursor(Qt::PointingHandCursor);
  btn->setToolTip("Copy to clipboard");
  QObject::connect(btn, &QPushButton::clicked, btn, [text = std::move(text_to_copy)]() {
    QApplication::clipboard()->setText(text);
    Toast::success("Copied to clipboard");
  });
  return btn;
}

QPushButton* CopyButtonFactory::make_bound(const QLineEdit* source, QWidget* parent) {
  auto* btn = new QPushButton(parent);
  btn->setProperty("class", "icon-button");
  btn->setIcon(IconHelper::icon(IconHelper::ICON_COPY, 14, QColor("#94A3B8")));
  btn->setIconSize(QSize(14, 14));
  btn->setFixedSize(28, 28);
  btn->setCursor(Qt::PointingHandCursor);
  btn->setToolTip("Copy to clipboard");
  // Capture the source pointer weakly via QPointer; if the source is destroyed
  // before the button, the click becomes a no-op.
  QPointer<const QLineEdit> src = source;
  QObject::connect(btn, &QPushButton::clicked, btn, [src]() {
    if (!src) return;
    QApplication::clipboard()->setText(src->text());
    Toast::success("Copied to clipboard");
  });
  return btn;
}

} // namespace fin::ui
