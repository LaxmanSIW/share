// fin/services/tspl.cpp
#include "fin/services/tspl.hpp"
#include "fin/app/log.hpp"

#include <sstream>

namespace fin::services {

void TsplCommandBuilder::clear() { stream_.clear(); }
void TsplCommandBuilder::size_mm(int w, int h) {
  std::ostringstream s; s << "SIZE " << w << " mm," << h << " mm\n";
  stream_ += s.str();
}
void TsplCommandBuilder::gap_mm(int g) {
  std::ostringstream s; s << "GAP " << g << " mm,0 mm\n";
  stream_ += s.str();
}
void TsplCommandBuilder::density(int d) {
  std::ostringstream s; s << "DENSITY " << d << "\n";
  stream_ += s.str();
}
void TsplCommandBuilder::speed(TsplSpeed s) {
  static const char* names[] = {"AUTO", "1", "1.5", "2", "3", "4"};
  std::ostringstream os; os << "SPEED " << names[static_cast<int>(s)] << "\n";
  stream_ += os.str();
}
void TsplCommandBuilder::direction(TsplDirection d) {
  stream_ += d == TsplDirection::Mirror ? "DIRECTION 1\n" : "DIRECTION 0\n";
}
void TsplCommandBuilder::text(int x, int y, const std::string& font, int point, const std::string& content) {
  std::ostringstream s;
  s << "TEXT " << x << "," << y << ",\"" << font << "\"," << point << ",\"" << content << "\"\n";
  stream_ += s.str();
}
void TsplCommandBuilder::barcode(int x, int y, const std::string& symbology, int height, const std::string& content) {
  std::ostringstream s;
  s << "BARCODE " << x << "," << y << ",\"" << symbology << "\"," << height << ",\"" << content << "\"\n";
  stream_ += s.str();
}
void TsplCommandBuilder::qr(int x, int y, int cell_size, const std::string& content) {
  std::ostringstream s;
  s << "QRCODE " << x << "," << y << ",M," << cell_size << ",\"" << content << "\"\n";
  stream_ += s.str();
}
void TsplCommandBuilder::line(int x, int y, int w, int h) {
  std::ostringstream s;
  s << "BAR " << x << "," << y << "," << w << "," << h << "\n";
  stream_ += s.str();
}
void TsplCommandBuilder::box(int x, int y, int w, int h, int thickness) {
  std::ostringstream s;
  s << "BOX " << x << "," << y << "," << w << "," << h << "," << thickness << "\n";
  stream_ += s.str();
}
void TsplCommandBuilder::print(int count) {
  std::ostringstream s; s << "PRINT " << count << "\n";
  stream_ += s.str();
}

std::string TsplCommandBuilder::str() const {
  return stream_;
}

bool TsplPrintService::send(const std::string& /*port_name*/, const std::string& /*tspl_commands*/) {
  // Phase 5d: real impl uses QSerialPort or QFile("LPT1:") on Windows, libudev
  // on Linux for USB printers.
  fin::app::log::info("TsplPrintService: send (stubbed)");
  return false;
}

} // namespace fin::services
