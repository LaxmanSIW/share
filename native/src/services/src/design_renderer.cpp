// fin/services/design_renderer.cpp
#include "fin/services/design_renderer.hpp"
#include "fin/app/log.hpp"

#include <algorithm>
#include <cstdint>
#include <fstream>
#include <vector>

namespace fin::services {

std::string RenderContext::lookup_font(const std::string& id) const {
  auto it = font_map_.find(id);
  if (it != font_map_.end()) return it->second;
  return "default";
}

void RenderContext::cache_image(const std::string& id, std::vector<std::uint8_t> bytes) {
  image_cache_[id] = std::move(bytes);
}

const std::vector<std::uint8_t>* RenderContext::lookup_image(const std::string& id) const {
  auto it = image_cache_.find(id);
  return it == image_cache_.end() ? nullptr : &it->second;
}

std::vector<std::string> DesignObjectRenderer::render(const TemplateElement& element,
                                                          RenderContext& /*ctx*/,
                                                          DisplayList& out) {
  std::vector<std::string> warnings;
  // Phase 5d: real impl is the full switch over ElementType — Text/RichText/
  // Image/Line/Rectangle/Ellipse/Table/Barcode/QR/Chart/SubReport/Checkbox.
  // For now we delegate to run_layout which handles the common cases.
  // Caller (TemplateDesignerView) typically calls run_layout directly which
  // already does this rendering per element.
  (void)element; (void)out;
  return warnings;
}

std::vector<CustomComponentManager::CustomComponent> CustomComponentManager::all() {
  // Phase 5d: load from <data_dir>/custom_components.json
  return {
    {"buyer_address", "Buyer Address Block", {}},
    {"payment_qr",    "Payment QR + Amount", {}},
    {"totals_block",  "Totals Block",        {}},
  };
}
bool CustomComponentManager::save(const CustomComponent&) { return false; }
bool CustomComponentManager::erase(const std::string&) { return false; }

std::vector<VariableGrouper::Group> VariableGrouper::groups() {
  return {
    {"Buyer",      {"buyer_name", "buyer_address", "buyer_gst", "buyer_phone", "buyer_state", "buyer_state_code", "buyer_city", "buyer_contact_person"}},
    {"Transport",  {"transport_name", "transport_phone", "transport_contact", "vehicle_no", "e_way_bill"}},
    {"Order",      {"po_no", "parcel", "parcels"}},
    {"Invoice",    {"invoice_no", "invoice_date", "due_date", "grand_total", "subtotal", "cgst", "sgst", "igst", "round_off", "amount_in_words"}},
    {"Buyer Extra",{"buyer_extra_1", "buyer_extra_2"}},
  };
}

bool TemplatePackageService::export_to(const std::filesystem::path&,
                                        const Template&,
                                        const std::map<std::string, std::vector<std::uint8_t>>&) {
  // Phase 5d: real impl uses minizip-ng / libzip to write the ZIP. The
  // JSON inside is canonical-ordered for VCS-friendly diffs (skill §10).
  return false;
}

bool TemplatePackageService::import_from(const std::filesystem::path&,
                                          Template&,
                                          std::map<std::string, std::vector<std::uint8_t>>&) {
  // Phase 5d: real impl unzips, validates JSON schema, runs version migrations.
  return false;
}

std::vector<SvgVectorParser::Path_> SvgVectorParser::parse(const std::string& /*svg_xml*/) {
  // Phase 5d: real impl uses QtSvg's QSvgRenderer for parsing; extract paths
  // for the display list (skill §11: emit vector modules, not raster).
  return {};
}

MonoImage::MonoImage(int w, int h) : w_(w), h_(h) {
  int bytes_per_row = (w + 7) / 8;
  bytes_.assign(static_cast<std::size_t>(bytes_per_row) * h, 0);
}

void MonoImage::set_pixel(int x, int y, bool on) {
  if (x < 0 || x >= w_ || y < 0 || y >= h_) return;
  int bytes_per_row = (w_ + 7) / 8;
  int byte_idx = y * bytes_per_row + x / 8;
  int bit_idx = 7 - (x % 8);
  if (on) bytes_[byte_idx] |= (1 << bit_idx);
  else     bytes_[byte_idx] &= ~(1 << bit_idx);
}

bool MonoImage::get_pixel(int x, int y) const {
  if (x < 0 || x >= w_ || y < 0 || y >= h_) return false;
  int bytes_per_row = (w_ + 7) / 8;
  int byte_idx = y * bytes_per_row + x / 8;
  int bit_idx = 7 - (x % 8);
  return (bytes_[byte_idx] >> bit_idx) & 1;
}

std::vector<std::uint8_t> MonoImage::to_packed_bytes() const {
  return bytes_;
}

} // namespace fin::services
