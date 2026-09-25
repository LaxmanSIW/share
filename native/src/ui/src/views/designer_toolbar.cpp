#include <QHBoxLayout>
#include "fin/ui/views/designer_toolbar.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/icon_helper.hpp"
#include <QAction>
#include <QActionGroup>
namespace fin::ui {
DesignerToolbar::DesignerToolbar(QWidget* parent) : QToolBar(parent) {
  setProperty("class", "title-bar");
  setMovable(false);
  setIconSize(QSize(16, 16));

  auto* select = addAction(IconHelper::icon(IconHelper::ICON_CROSSHAIR, 16, QColor("#94A3B8")), "Select");
  select->setToolTip("Select tool (V)");
  connect(select, &QAction::triggered, this, &DesignerToolbar::tool_select);

  auto* hand = addAction(IconHelper::icon(IconHelper::ICON_EXPAND, 16, QColor("#94A3B8")), "Pan");
  hand->setToolTip("Pan tool (H)");
  connect(hand, &QAction::triggered, this, &DesignerToolbar::tool_hand);

  addSeparator();
  auto* text = addAction(IconHelper::icon(IconHelper::ICON_FONT, 16, QColor("#94A3B8")), "Text");
  text->setToolTip("Add text element (T)");
  connect(text, &QAction::triggered, this, &DesignerToolbar::tool_text);

  auto* rect = addAction(IconHelper::icon(IconHelper::ICON_SHAPES, 16, QColor("#94A3B8")), "Rectangle");
  rect->setToolTip("Add rectangle (R)");
  connect(rect, &QAction::triggered, this, &DesignerToolbar::tool_rectangle);

  auto* ell = addAction(IconHelper::icon(IconHelper::ICON_SHAPES, 16, QColor("#94A3B8")), "Ellipse");
  ell->setToolTip("Add ellipse (E)");
  connect(ell, &QAction::triggered, this, &DesignerToolbar::tool_ellipse);

  auto* line = addAction(IconHelper::icon(IconHelper::ICON_LINE_H, 16, QColor("#94A3B8")), "Line");
  line->setToolTip("Add line (L)");
  connect(line, &QAction::triggered, this, &DesignerToolbar::tool_line);

  auto* img = addAction(IconHelper::icon(IconHelper::ICON_MEDIA_IMAGE, 16, QColor("#94A3B8")), "Image");
  img->setToolTip("Add image (I)");
  connect(img, &QAction::triggered, this, &DesignerToolbar::tool_image);

  auto* tbl = addAction(IconHelper::icon(IconHelper::ICON_TABLE, 16, QColor("#94A3B8")), "Table");
  tbl->setToolTip("Add table");
  connect(tbl, &QAction::triggered, this, &DesignerToolbar::tool_table);

  auto* bar = addAction(IconHelper::icon(IconHelper::ICON_CODE_BARCODE, 16, QColor("#94A3B8")), "Barcode");
  bar->setToolTip("Add barcode");
  connect(bar, &QAction::triggered, this, &DesignerToolbar::tool_barcode);

  auto* qr = addAction(IconHelper::icon(IconHelper::ICON_CODE_QR, 16, QColor("#94A3B8")), "QR");
  qr->setToolTip("Add QR code");
  connect(qr, &QAction::triggered, this, &DesignerToolbar::tool_qr);

  addSeparator();
  auto* undo = addAction(IconHelper::icon(IconHelper::ICON_UNDO, 16, QColor("#94A3B8")), "Undo");
  undo->setToolTip("Undo (Ctrl+Z)");
  connect(undo, &QAction::triggered, this, &DesignerToolbar::undo);

  auto* redo = addAction(IconHelper::icon(IconHelper::ICON_REDO, 16, QColor("#94A3B8")), "Redo");
  redo->setToolTip("Redo (Ctrl+Y)");
  connect(redo, &QAction::triggered, this, &DesignerToolbar::redo);

  addSeparator();
  auto* zin = addAction(IconHelper::icon(IconHelper::ICON_PLUS, 16, QColor("#94A3B8")), "Zoom in");
  zin->setToolTip("Zoom in (Ctrl++)");
  connect(zin, &QAction::triggered, this, &DesignerToolbar::zoom_in);

  auto* zout = addAction(IconHelper::icon(IconHelper::ICON_TRASH, 16, QColor("#94A3B8")), "Zoom out");
  zout->setToolTip("Zoom out (Ctrl+-)");
  connect(zout, &QAction::triggered, this, &DesignerToolbar::zoom_out);

  auto* z100 = addAction(IconHelper::icon(IconHelper::ICON_DASHBOARD, 16, QColor("#94A3B8")), "100%");
  z100->setToolTip("Actual size");
  connect(z100, &QAction::triggered, this, &DesignerToolbar::zoom_100);

  auto* zfit = addAction(IconHelper::icon(IconHelper::ICON_EXPAND, 16, QColor("#94A3B8")), "Fit");
  zfit->setToolTip("Fit page");
  connect(zfit, &QAction::triggered, this, &DesignerToolbar::zoom_fit);
}
} // namespace fin::ui
