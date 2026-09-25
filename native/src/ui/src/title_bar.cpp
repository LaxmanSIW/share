// fin/ui/title_bar.cpp
#include "fin/ui/title_bar.hpp"
#include "fin/ui/icon_helper.hpp"

#include <QMainWindow>
#include <QHBoxLayout>
#include <QVBoxLayout>
#include <QMouseEvent>
#include <QApplication>
#include <QGuiApplication>
#include <QScreen>
#include <QWindow>

namespace fin::ui {

TitleBar::TitleBar(QMainWindow* parent)
  : QFrame(parent), window_(parent) {
  setProperty("class", "title-bar");
  setFixedHeight(36);
  auto* l = new QHBoxLayout(this);
  l->setContentsMargins(12, 0, 4, 0);
  l->setSpacing(8);

  // Brand icon (gold square + receipt glyph)
  auto* brand = new QFrame(this);
  brand->setFixedSize(20, 20);
  brand->setStyleSheet("background-color: #D9A13B; border-radius: 4px;");
  brand->setAccessibleName("InvoiceStudio brand");
  l->addWidget(brand);

  title_label_ = new QLabel("InvoiceStudio", this);
  title_label_->setProperty("class", "title-text");
  l->addWidget(title_label_);
  l->addStretch();

  // Window buttons (icon-only, square)
  min_btn_ = new QPushButton(this);
  min_btn_->setProperty("class", "title-button");
  min_btn_->setIcon(IconHelper::icon(IconHelper::ICON_EXPAND, 12, QColor("#94A3B8")));
  min_btn_->setIconSize(QSize(12, 12));
  min_btn_->setFixedSize(36, 28);
  min_btn_->setCursor(Qt::PointingHandCursor);
  connect(min_btn_, &QPushButton::clicked, this, &TitleBar::onMinimize);
  l->addWidget(min_btn_);

  max_btn_ = new QPushButton(this);
  max_btn_->setProperty("class", "title-button");
  max_btn_->setIcon(IconHelper::icon(IconHelper::ICON_EXPAND, 12, QColor("#94A3B8")));
  max_btn_->setIconSize(QSize(12, 12));
  max_btn_->setFixedSize(36, 28);
  max_btn_->setCursor(Qt::PointingHandCursor);
  connect(max_btn_, &QPushButton::clicked, this, &TitleBar::onMaximizeRestore);
  l->addWidget(max_btn_);

  close_btn_ = new QPushButton(this);
  close_btn_->setObjectName("closeButton");
  close_btn_->setProperty("class", "title-button");
  close_btn_->setIcon(IconHelper::icon(IconHelper::ICON_CLOSE, 12, QColor("#94A3B8")));
  close_btn_->setIconSize(QSize(12, 12));
  close_btn_->setFixedSize(46, 28);
  close_btn_->setCursor(Qt::PointingHandCursor);
  connect(close_btn_, &QPushButton::clicked, this, &TitleBar::onClose);
  l->addWidget(close_btn_);
}

void TitleBar::setTitle(const QString& title) {
  title_label_->setText(title);
}

void TitleBar::mousePressEvent(QMouseEvent* e) {
  if (e->button() == Qt::LeftButton) {
    dragging_ = true;
    drag_offset_ = e->globalPosition().toPoint() - window_->pos();
  }
  QFrame::mousePressEvent(e);
}

void TitleBar::mouseMoveEvent(QMouseEvent* e) {
  if (dragging_ && (e->buttons() & Qt::LeftButton)) {
    window_->move(e->globalPosition().toPoint() - drag_offset_);
  }
  QFrame::mouseMoveEvent(e);
}

void TitleBar::mouseDoubleClickEvent(QMouseEvent* /*e*/) {
  onMaximizeRestore();
}

void TitleBar::onMinimize() {
  window_->showMinimized();
}

void TitleBar::onMaximizeRestore() {
  if (window_->isMaximized()) window_->showNormal();
  else                          window_->showMaximized();
}

void TitleBar::onClose() {
  window_->close();
}

} // namespace fin::ui
