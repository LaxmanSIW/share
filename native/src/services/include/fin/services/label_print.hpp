// fin/services/label_print.hpp — Label printing services (consolidated)
//
// Port of Java: LabelPrintService (567), LabelGeometryService (250),
// LabelRenderUtil (200), LabelPresets (150), BulkPrintStateStore (100).
//
// TSPL printing: TsplPrintService (464) + TsplCommandBuilder (250) — thermal
// printer language for label printers (Zebra / TSC / SATO).
#pragma once
#include "fin/money.hpp"
#include "fin/model/purchase_stock.hpp"

#include <filesystem>
#include <functional>
#include <string>
#include <vector>

namespace fin::services {

struct LabelConfig {
  double width_mm{40.0};
  double height_mm{15.0};
  int    columns{3};
  int    rows{1};
  double gap_x_mm{2.0};
  double gap_y_mm{0.0};
  bool   continuous_roll{false};
};

struct LabelPreset {
  std::string name;
  LabelConfig config;
};

class LabelPresets {
 public:
  static std::vector<LabelPreset> all();
  static LabelPreset by_name(std::string_view name);
};

class LabelGeometryService {
 public:
  /// Compute the (x, y) of each label on a sheet, given the label config +
  /// the number of labels per row/column.
  struct Point { double x_mm; double y_mm; };
  static std::vector<Point> label_positions(const LabelConfig& cfg, int total_labels);
  static double sheet_width_mm(const LabelConfig& cfg);
  static double sheet_height_mm(const LabelConfig& cfg);
};

class LabelPrintService {
 public:
  /// Print a strip preview (used by LabelStripPreviewDialog) to a QImage.
  /// The strip is a horizontal layout of one row of labels.
  struct QImage;  // opaque; full impl uses Qt's QImage
  static void print_strip_preview(const LabelConfig& cfg, const std::vector<std::string>& labels);

  /// Bulk print: print `total_copies` of a template with variable data
  /// per label (one row of CSV per label). Progress is reported via on_progress.
  /// Skill §3.7: chunked printing — one strip row per UI pulse via
  /// Platform.runLater / QTimer::singleShot chain.
  static bool bulk_print(const std::string& template_id, const LabelConfig& cfg,
                          const std::vector<std::vector<std::string>>& rows,
                          const std::string& printer_name,
                          std::function<void(int done, int failed)> on_progress);
};

/// BulkPrintStateStore: persists bulk print state across runs so a crashed
/// print can resume from where it left off (skill §8: shutdown graceful).
class BulkPrintStateStore {
 public:
  struct State {
    std::string template_id;
    int         last_completed_row{0};
    int         total_rows{0};
    std::string printer_name;
  };
  static State load(const std::string& template_id);
  static bool save(const State& s);
  static bool clear(const std::string& template_id);
};

} // namespace fin::services
