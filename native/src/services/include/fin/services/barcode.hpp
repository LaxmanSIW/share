// fin/services/barcode.hpp — BarcodeService (port of Java BarcodeService.java)
//
// Generates barcode images for invoices, labels, and asset tags.
// Skill rule §11 (designer §11): use Zint to generate; ZXing-cpp to read/
// verify in tests. Emit vector modules into the display list (crisp at any
// DPI); enforce quiet zones and minimum module size.
#pragma once
#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

namespace fin::services {

enum class BarcodeSymbology : std::uint8_t {
  Code128 = 0,
  Code39  = 1,
  Ean13   = 2,
  Ean8    = 3,
  UpcA    = 4,
  UpcE    = 5,
  Itf14   = 6,
  QrCode  = 7,
  DataMatrix = 8,
  Pdf417  = 9,
  Aztec   = 10,
};

struct BarcodeOptions {
  BarcodeSymbology symbology{BarcodeSymbology::Code128};
  double width_mm{40.0};
  double height_mm{15.0};
  bool   include_text{true};
  double quiet_zone_mm{2.5};  // minimum 2.5 mm on each side (GS1 spec)
};

struct BarcodeImage {
  std::vector<std::uint8_t> rgba_pixels;  // RGBA, row-major, width × height
  int width{0};
  int height{0};
  bool ok{false};
  std::string error;
};

class BarcodeService {
 public:
  /// Generate a barcode as a raster image. The result is a tightly-cropped
  /// pixel buffer; the caller (template designer) embeds it into the display
  /// list at the desired size and DPI.
  static BarcodeImage generate(std::string_view data, const BarcodeOptions& opts);

  /// Generate as SVG (vector) — preferred for the template designer since
  /// the modules stay crisp at any zoom level.
  static std::string generate_svg(std::string_view data, const BarcodeOptions& opts);

  /// Verify a generated barcode by attempting to decode it (used in CI tests).
  /// Returns the decoded string if successful, or empty on failure.
  static std::string verify_decode(std::string_view svg, BarcodeSymbology expected);

  /// Returns the canonical name for a symbology (used in the designer's
  /// property panel dropdown).
  static const char* symbology_name(BarcodeSymbology s) noexcept;
};

} // namespace fin::services
