// fin/services/tspl.hpp — TSPL printer command language (port of Java TsplCommandBuilder + TsplPrintService)
//
// Thermal printer language for label printers (Zebra/ZPL, TSC/TSPL, SATO).
// Skill §6 (template-designer §6): same display list → screen | printer.
// This service converts the layout display list to TSPL commands.
#pragma once
#include <cstdint>
#include <string>
#include <vector>

namespace fin::services {

enum class TsplDirection : std::uint8_t { Normal = 0, Mirror = 1 };
enum class TsplSpeed : std::uint8_t { Auto = 0, Speed1, Speed1_5, Speed2, Speed3, Speed4 };

struct TsplOptions {
  int           width_mm{40};
  int           height_mm{15};
  int           gap_mm{2};
  int           copies{1};
  int           density{8};         // 0-15
  TsplDirection direction{TsplDirection::Normal};
  TsplSpeed     speed{TsplSpeed::Auto};
};

class TsplCommandBuilder {
 public:
  void clear();
  void size_mm(int w, int h);
  void gap_mm(int g);
  void density(int d);
  void speed(TsplSpeed s);
  void direction(TsplDirection d);
  void text(int x, int y, const std::string& font, int point, const std::string& content);
  void barcode(int x, int y, const std::string& symbology, int height, const std::string& content);
  void qr(int x, int y, int cell_size, const std::string& content);
  void line(int x, int y, int w, int h);
  void box(int x, int y, int w, int h, int thickness);
  void print(int count = 1);

  /// Returns the assembled TSPL command stream as a string (one command per line).
  std::string str() const;

 private:
  std::string stream_;
};

class TsplPrintService {
 public:
  /// Send TSPL commands directly to a printer via raw port I/O (port_name like
  /// "LPT1:", "COM1:", "USB001"). Returns true on success.
  /// Phase 5d: real impl uses Qt PrintSupport + raw write APIs.
  static bool send(const std::string& port_name, const std::string& tspl_commands);
};

} // namespace fin::services
