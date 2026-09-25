#include "fin/ui/widgets/data_grid.hpp"
#include <QKeyEvent>
#include <QHeaderView>
#include <QModelIndex>
#include <QItemSelectionModel>
#include <QHBoxLayout>

namespace fin::ui {

DataGrid::DataGrid(QWidget* parent) : QTableView(parent) {
  // Skill rule §7: ResizeToContents + stretchLastSection = auto-fit + fill width.
  // This is set ONCE in the constructor; no per-section calls (which crash on
  // offscreen Qt when no model is set yet).
  horizontalHeader()->setSectionResizeMode(QHeaderView::ResizeToContents);
  horizontalHeader()->setStretchLastSection(true);
  horizontalHeader()->setHighlightSections(false);
  verticalHeader()->setVisible(false);
  verticalHeader()->setSectionResizeMode(QHeaderView::Fixed);
  verticalHeader()->setDefaultSectionSize(36);
  setShowGrid(false);
  setAlternatingRowColors(true);
  setSelectionBehavior(QAbstractItemView::SelectRows);
  setSelectionMode(QAbstractItemView::SingleSelection);
  setEditTriggers(QAbstractItemView::NoEditTriggers);
  setFocusPolicy(Qt::NoFocus);
  connect(this, &QTableView::doubleClicked, this, [this](const QModelIndex& idx) {
    if (idx.isValid() && row_double_click_) row_double_click_(idx.row());
  });
}

void DataGrid::set_columns(const QList<ColumnSpec>& specs) {
  // No-op: ResizeToContents mode (set in constructor) handles column widths.
  // Per-section setSectionResizeMode crashes on offscreen Qt before model is set.
  // The column headers come from the model's horizontalHeaderLabels.
}

int DataGrid::selected_row() const {
  auto idx = currentIndex();
  return idx.isValid() ? idx.row() : -1;
}

void DataGrid::keyPressEvent(QKeyEvent* e) {
  if ((e->key() == Qt::Key_Enter || e->key() == Qt::Key_Return) && row_double_click_) {
    int row = selected_row();
    if (row >= 0) row_double_click_(row);
    return;
  }
  QTableView::keyPressEvent(e);
}

} // namespace fin::ui
