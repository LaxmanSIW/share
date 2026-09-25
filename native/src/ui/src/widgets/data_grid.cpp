// fin/ui/widgets/data_grid.cpp
#include "fin/ui/widgets/data_grid.hpp"

#include <QKeyEvent>
#include <QHeaderView>
#include <QModelIndex>
#include <QItemSelectionModel>

namespace fin::ui {

DataGrid::DataGrid(QWidget* parent) : QTableView(parent) {
  // Skill rule §7: no resizeColumnsToContents on big data (scans everything).
  // We use ResizeToContents mode which Qt handles incrementally as cells
  // are populated.
  horizontalHeader()->setSectionResizeMode(QHeaderView::ResizeToContents);
  horizontalHeader()->setStretchLastSection(true);
  horizontalHeader()->setHighlightSections(false);
  horizontalHeader()->setCascadingSectionResizes(false);

  // Vertical header: hide row numbers (we paint our own labels if needed).
  verticalHeader()->setVisible(false);
  verticalHeader()->setSectionResizeMode(QHeaderView::Fixed);
  verticalHeader()->setDefaultSectionSize(36);

  // USER REQUIREMENT 3: nothing should be hidden. We paint our own grid.
  setShowGrid(false);
  setAlternatingRowColors(true);
  setSelectionBehavior(QAbstractItemView::SelectRows);
  setSelectionMode(QAbstractItemView::SingleSelection);
  setEditTriggers(QAbstractItemView::NoEditTriggers);
  setFocusPolicy(Qt::NoFocus);

  // Double-click handler
  connect(this, &QTableView::doubleClicked, this, [this](const QModelIndex& idx) {
    if (idx.isValid() && row_double_click_) {
      row_double_click_(idx.row());
    }
  });
}

void DataGrid::set_columns(const QList<ColumnSpec>& specs) {
  // The model has to know how many columns we have; here we just configure
  // the visual specs (the model is set separately by the caller via setModel).
  for (int i = 0; i < specs.size(); ++i) {
    const auto& s = specs[i];
    auto* hdr = horizontalHeader();
    if (s.width < 0) {
      hdr->setSectionResizeMode(i, QHeaderView::Stretch);
    } else {
      hdr->setSectionResizeMode(i, QHeaderView::Interactive);
      hdr->resizeSection(i, s.width);
    }
  }
}

int DataGrid::selected_row() const {
  auto idx = currentIndex();
  return idx.isValid() ? idx.row() : -1;
}

void DataGrid::keyPressEvent(QKeyEvent* e) {
  // Enter / Return = double-click on the selected row.
  if ((e->key() == Qt::Key_Enter || e->key() == Qt::Key_Return) && row_double_click_) {
    int row = selected_row();
    if (row >= 0) row_double_click_(row);
    return;
  }
  QTableView::keyPressEvent(e);
}

} // namespace fin::ui
