// fin/services/pdf_export.hpp — PdfExportService (port of Java PdfExportService.java)
//
// Generates PDF invoices / reports / labels from a fin::model::Bill + a
// Template. Skill rule (template-designer §6): one display list → screen | PDF
// | printer. This service takes the same display list the template designer
// renders on screen and emits it via QPdfWriter.
#pragma once
#include "fin/model/bill.hpp"
#include "fin/model/buyer_supplier.hpp"

#include <filesystem>
#include <functional>
#include <string>

namespace fin::services {

struct PdfExportOptions {
  std::filesystem::path output_path;
  bool include_company_letterhead{true};
  bool include_e_way_bill_qr{false};
  std::string paper_size{"A4"};
  bool landscape{false};
  double margin_mm{15.0};
};

class PdfExportService {
 public:
  /// Render a bill to PDF using its assigned template.
  /// Skill rule §1: this is CPU + I/O heavy — caller must run it off the UI
  /// thread (export_pool) and call `done` on the UI thread.
  /// Returns true on success.
  static bool export_bill(const fin::model::Bill& bill,
                           const fin::model::Template& tpl,
                           const PdfExportOptions& opts,
                           std::function<void(bool ok, std::string error)> done);

  /// Export a list of bills to a multi-page PDF (each bill on its own page).
  static bool export_bills_batch(const std::vector<fin::model::Bill>& bills,
                                   const PdfExportOptions& opts,
                                   std::function<void(int done, int failed)> on_progress);

  /// Export a GST summary report to PDF (used by Reports view).
  static bool export_gst_report(const std::string& report_title,
                                  const std::string& html_body,
                                  const PdfExportOptions& opts);
};

} // namespace fin::services
