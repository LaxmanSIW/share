// fin/services/printing.cpp
#include "fin/services/printing.hpp"
#include "fin/app/log.hpp"

#include <QPrinter>
#include <QPrintDialog>
#include <QPrintPreviewDialog>
#include <QFile>
#include <QFileInfo>

namespace fin::services {

bool RawPrintTransport::send_raw(const std::string& port_name, const std::vector<std::uint8_t>& /*bytes*/) {
  // Phase 5d: real impl:
  //   Windows LPT: QFile("\\\\?\\LPT1", QIODevice::WriteOnly) + write(bytes)
  //   Windows COM: QSerialPort("COM1")
  //   Linux USB:   QFile("/dev/usb/lp0")
  //   Linux PARPORT: QFile("/dev/lp0")
  fin::app::log::infof("RawPrintTransport::send_raw (stubbed) port={}", port_name);
  return false;
}

bool PrintingService::print_pdf(const std::filesystem::path& pdf_path, const PrintOptions& opts) {
  QPrinter printer;
  if (!opts.printer_name.empty()) {
    printer.setPrinterName(QString::fromStdString(opts.printer_name));
  }
  printer.setCopyCount(opts.copies);
  printer.setCollateCopies(opts.collate);
  printer.setDuplex(opts.duplex ? QPrinter::DuplexLongSide : QPrinter::DuplexNone);
  printer.setColorMode(opts.color ? QPrinter::Color : QPrinter::GrayScale);

  QPrintDialog dialog(&printer);
  if (dialog.exec() != QDialog::Accepted) return false;

  // Phase 5d: real impl uses QPdfDocument + QPainter to render pages.
  (void)pdf_path;
  return true;
}

bool PrintingService::print_tspl(const std::vector<std::uint8_t>& commands, const std::string& printer_name) {
  return RawPrintTransport::send_raw(printer_name, commands);
}

bool PrintingService::show_preview(const std::filesystem::path& pdf_path) {
  if (!std::filesystem::exists(pdf_path)) return false;
  QPrinter printer;
  QPrintPreviewDialog dialog(&printer);
  // Phase 5d: real impl wires paintRequested to render the PDF page-by-page.
  dialog.exec();
  return true;
}

} // namespace fin::services
