// fin/ui/widgets/data_grid.hpp — Custom QTableView with auto-fit columns
//
// USER REQUIREMENT 3: "make sure column size are according to its contrnt
// nothing should be hidden". This widget wraps QTableView and:
//   - Sets all columns to ResizeToContents by default (auto-fit content)
//   - Provides a stretch-to-window last column so width is filled
//   - Disables the native grid cell borders (we paint our own)
//   - Alternating row colors for readability
//   - Right-click context menu hook (delete/edit)
//
// Skill rule (responsive-ui §7): QTableView + custom QAbstractTableModel with
// paged cache; we never use QTableWidget (which allocates a node per cell).
#pragma once
#include <QTableView>
#include <QHeaderView>
#include <QString>
#include <QList>
#include <functional>

namespace fin::ui {

class DataGrid : public QTableView {
  Q_OBJECT
 public:
  explicit DataGrid(QWidget* parent = nullptr);

  /// Configure columns: widths + titles + alignment. Skill: avoid
  /// resizeColumnsToContents() (which scans all rows); instead set explicit
  /// widths or use ResizeToContents which Qt handles incrementally.
  struct ColumnSpec {
    QString title;
    int     width{120};              // <0 = stretch
    Qt::Alignment alignment{Qt::AlignLeft | Qt::AlignVCenter};
  };
  void set_columns(const QList<ColumnSpec>& specs);

  /// Set the row double-click handler (for "edit" action).
  void set_row_double_click(std::function<void(int row)> cb) { row_double_click_ = std::move(cb); }

  /// Returns the selected row index, or -1 if none.
  int selected_row() const;

 protected:
  void keyPressEvent(QKeyEvent* e) override;

 private:
  std::function<void(int row)> row_double_click_;
};

} // namespace fin::ui
