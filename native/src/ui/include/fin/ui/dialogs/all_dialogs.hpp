// fin/ui/dialogs/all_dialogs.hpp — 6 dialogs (consolidated)
//
// Port of Java: ExpenseReportDialog, ExpenseAccountsDialog, CustomColorChooserDialog,
// LabelBulkPrintDialog, LabelStripPreviewDialog, PrintPreviewDialog.
#pragma once
#include <QDialog>
#include <QString>
#include <QColor>
#include <functional>

namespace fin::ui {

class ExpenseReportDialog {
 public:
  static void open(QWidget* parent = nullptr);
};

class ExpenseAccountsDialog {
 public:
  static void open(QWidget* parent = nullptr);
};

class CustomColorChooserDialog {
 public:
  static void open(const QColor& initial, std::function<void(QColor)> on_done, QWidget* parent = nullptr);
};

class LabelBulkPrintDialog {
 public:
  static void open(QWidget* parent = nullptr);
};

class LabelStripPreviewDialog {
 public:
  static void open(QWidget* parent = nullptr);
};

class PrintPreviewDialog {
 public:
  static void open(QWidget* parent = nullptr);
};

} // namespace fin::ui
