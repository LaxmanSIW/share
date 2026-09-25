// fin/services/printing.hpp — Printing services (consolidated)
//
// Port of Java: PrintingService (300), PrintOptions (100), RawPrintTransport (150),
// JavaxRawPrintTransport (150). The Java original uses JNA to call into
// Win32 raw printing APIs; in C++ we use Qt PrintSupport (cross-platform).
#pragma once
#include <filesystem>
#include <functional>
#include <string>
#include <vector>

namespace fin::services {

struct PrintOptions {
  std::string printer_name;     // empty = system default
  int         copies{1};
  bool        collate{true};
  bool        duplex{false};
  bool        color{false};       // labels are usually monochrome
  int         paper_size_mm_w{210};
  int         paper_size_mm_h{297};
  int         margins_mm{5};
  std::string page_range;         // "1-3,5,7-9" or empty for all
};

class RawPrintTransport {
 public:
  /// Send raw bytes to a printer port (LPT1:, COM1:, USB, etc.). Returns true on success.
  /// Phase 5d: real impl uses QSerialPort on Windows COM ports, QFile on
  /// Windows LPT ports, libudev on Linux for USB printer enumeration.
  static bool send_raw(const std::string& port_name, const std::vector<std::uint8_t>& bytes);
};

class PrintingService {
 public:
  /// Print a PDF file via the system print dialog. Returns true on success.
  /// Skill §1 (responsive-ui): run on the export_pool; deliver result on UI thread.
  static bool print_pdf(const std::filesystem::path& pdf_path, const PrintOptions& opts);

  /// Print raw TSPL commands to a thermal label printer. No system print dialog
  /// (label printers don't use the OS print spooler for raw commands).
  static bool print_tspl(const std::vector<std::uint8_t>& tspl_commands, const std::string& printer_name);

  /// Show a print preview dialog with the given PDF.
  static bool show_preview(const std::filesystem::path& pdf_path);
};

} // namespace fin::services
