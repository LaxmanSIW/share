// fin/services/label_print.cpp
#include "fin/services/label_print.hpp"
#include "fin/app/log.hpp"

namespace fin::services {

std::vector<LabelPreset> LabelPresets::all() {
  // Phase 5d skeleton: common label sizes (A4 24-up, continuous rolls).
  return {
    {"A4 24-up 70×35mm",  {70, 35, 3, 8, 2, 0, false}},
    {"A4 65-up 38×21mm",  {38, 21, 5, 13, 2, 0, false}},
    {"Roll 40×15mm",       {40, 15, 1, 1, 0, 0, true}},
    {"Roll 50×20mm",       {50, 20, 1, 1, 0, 0, true}},
    {"Roll 100×50mm",      {100, 50, 1, 1, 0, 0, true}},
  };
}

LabelPreset LabelPresets::by_name(std::string_view name) {
  for (const auto& p : all()) {
    if (p.name == name) return p;
  }
  return {};
}

std::vector<LabelGeometryService::Point> LabelGeometryService::label_positions(const LabelConfig& cfg, int total) {
  std::vector<Point> out;
  out.reserve(total);
  double x = 0, y = 0;
  for (int i = 0; i < total; ++i) {
    out.push_back({x, y});
    x += cfg.width_mm + cfg.gap_x_mm;
    if ((i + 1) % cfg.columns == 0) {
      x = 0;
      y += cfg.height_mm + cfg.gap_y_mm;
    }
  }
  return out;
}

double LabelGeometryService::sheet_width_mm(const LabelConfig& cfg) {
  return cfg.columns * cfg.width_mm + (cfg.columns - 1) * cfg.gap_x_mm;
}

double LabelGeometryService::sheet_height_mm(const LabelConfig& cfg) {
  return cfg.rows * cfg.height_mm + (cfg.rows - 1) * cfg.gap_y_mm;
}

void LabelPrintService::print_strip_preview(const LabelConfig&, const std::vector<std::string>&) {
  // Phase 5d: real impl uses QPainter to render the strip preview into a QImage.
}

bool LabelPrintService::bulk_print(const std::string& template_id, const LabelConfig& /*cfg*/,
                                    const std::vector<std::vector<std::string>>& rows,
                                    const std::string& printer_name,
                                    std::function<void(int, int)> on_progress) {
  int ok = 0, fail = 0;
  for (std::size_t i = 0; i < rows.size(); ++i) {
    // Skill §3.7: chunked printing — yield to the UI thread between strips.
    // Phase 5d skeleton: real impl calls TsplCommandBuilder + RawPrintTransport
    // to send the actual printer commands.
    ++ok;
    if (on_progress) on_progress(ok, fail);
  }
  (void)template_id;
  (void)printer_name;
  return fail == 0;
}

BulkPrintStateStore::State BulkPrintStateStore::load(const std::string&) {
  return {};
}
bool BulkPrintStateStore::save(const State&) { return false; }
bool BulkPrintStateStore::clear(const std::string&) { return false; }

} // namespace fin::services
