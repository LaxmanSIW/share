#pragma once
#include <QToolBar>
class QAction;
namespace fin::ui {
class DesignerToolbar : public QToolBar {
  Q_OBJECT
 public:
  explicit DesignerToolbar(QWidget* parent = nullptr);
 signals:
  void tool_select();
  void tool_hand();
  void tool_text();
  void tool_image();
  void tool_rectangle();
  void tool_ellipse();
  void tool_line();
  void tool_table();
  void tool_barcode();
  void tool_qr();
  void undo();
  void redo();
  void zoom_in();
  void zoom_out();
  void zoom_fit();
  void zoom_100();
};
} // namespace fin::ui
