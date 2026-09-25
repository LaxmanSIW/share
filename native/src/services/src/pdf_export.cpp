// fin/services/pdf_export.cpp
//
// Phase 3b skeleton: the real implementation uses QPdfWriter + QPainter to
// render the bill display list onto PDF pages. For now this is a stub that
// writes a minimal PDF so the API surface is exercised.
#include "fin/services/pdf_export.hpp"
#include "fin/app/log.hpp"
#include "fin/app/executors.hpp"

#include <QApplication>
#include <QFile>
#include <QIODevice>
#include <QTimer>

#include <fstream>
#include <string>

namespace fin::services {

namespace {

// A minimal PDF that just shows the bill number as text. Real impl will use
// QPdfWriter + QPainter to draw the full invoice layout.
bool write_minimal_pdf(const std::filesystem::path& path, std::string_view content) {
  std::ofstream out(path, std::ios::binary | std::ios::trunc);
  if (!out.is_open()) return false;
  // Smallest valid PDF with one page of text content. Real impl uses QPdfWriter.
  std::string pdf;
  pdf.reserve(1024);
  pdf += "%PDF-1.4\n";
  pdf += "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n";
  pdf += "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n";
  pdf += "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 842]/Contents 4 0 R/Resources<</Font<</F1 5 0 R>>>>>>endobj\n";
  std::string stream = "BT /F1 12 Tf 50 750 Td (";
  stream += std::string(content);
  stream += ") Tj ET";
  pdf += "4 0 obj<</Length " + std::to_string(stream.size()) + ">>stream\n" + stream + "\nendstream\nendobj\n";
  pdf += "5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj\n";
  pdf += "xref\n0 6\n0000000000 65535 f \n0000000009 00000 n \n0000000058 00000 n \n0000000115 00000 n \n";
  pdf += "0000000230 00000 n \n0000000300 00000 n \ntrailer<</Size 6/Root 1 0 R>>\nstartxref\n373\n%%EOF";
  out << pdf;
  return true;
}

} // namespace

bool PdfExportService::export_bill(const fin::model::Bill& bill,
                                     const fin::model::Template& tpl,
                                     const PdfExportOptions& opts,
                                     std::function<void(bool ok, std::string error)> done) {
  // Skill rule §1: run on export_pool; deliver result on UI thread.
  // For now we call write_minimal_pdf synchronously and dispatch done.
  std::string content = "Invoice " + bill.bill_no + " (template: " + tpl.name + ")";
  bool ok = write_minimal_pdf(opts.output_path, content);
  std::string err = ok ? "" : "write_minimal_pdf failed";
  QTimer::singleShot(0, qApp, [ok, err, done = std::move(done)]() mutable {
    done(ok, err);
  });
  return ok;
}

bool PdfExportService::export_bills_batch(const std::vector<fin::model::Bill>& bills,
                                          const PdfExportOptions& opts,
                                          std::function<void(int done, int failed)> on_progress) {
  int ok_count = 0;
  int fail_count = 0;
  for (const auto& bill : bills) {
    // For batch, we use a single multi-page PDF; for now write per-bill.
    if (write_minimal_pdf(opts.output_path, bill.bill_no)) ++ok_count;
    else                                       ++fail_count;
    if (on_progress) on_progress(ok_count, fail_count);
  }
  return fail_count == 0;
}

bool PdfExportService::export_gst_report(const std::string& report_title,
                                            const std::string& html_body,
                                            const PdfExportOptions& opts) {
  // Phase 3b: real impl renders the HTML body to PDF via QTextDocument.
  return write_minimal_pdf(opts.output_path, report_title + " | " + html_body);
}

} // namespace fin::services
