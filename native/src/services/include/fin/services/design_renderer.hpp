// fin/services/design_renderer.hpp — Template element rendering (port of Java DesignObjectRenderer 737 + RenderContext 100 + CustomComponentManager 200 + VariableGrouper 125 + TemplatePackageService 300 + SvgVectorParser 407 + MonoImage 100)
//
// Renders template elements into the display list (skill §5: display list
// commands emitted by a pure function, renderable by any backend). The Java
// original's DesignObjectRenderer is a 737-line switch on ElementType that
// builds a JavaFX scene-graph fragment per element; in C++ we emit display
// list commands instead (skill §6: don't have screen + PDF diverge).
#pragma once
#include "fin/services/template_engine.hpp"

#include <filesystem>
#include <map>
#include <string>
#include <vector>

namespace fin::services {

/// RenderContext: shared state passed through the render pipeline (font
/// cache, image cache, expression evaluator, locale).
class RenderContext {
 public:
  /// Lookup a font by id; returns the default font if not found.
  std::string lookup_font(const std::string& id) const;

  /// Cache an image resource (decoded bytes keyed by SHA256 id).
  void cache_image(const std::string& resource_id, std::vector<std::uint8_t> bytes);

  /// Lookup an image resource.
  const std::vector<std::uint8_t>* lookup_image(const std::string& resource_id) const;

 private:
  std::map<std::string, std::vector<std::uint8_t>> image_cache_;
  std::map<std::string, std::string> font_map_;
};

/// DesignObjectRenderer: per-element display-list emission. Takes a single
/// element + the render context, appends commands to the display list.
class DesignObjectRenderer {
 public:
  /// Render one element into the display list. Returns warnings (e.g. "font
  /// not found, used default").
  static std::vector<std::string> render(const TemplateElement& element,
                                          RenderContext& ctx,
                                          DisplayList& out);
};

/// CustomComponentManager: user-defined compound elements (e.g. a "Buyer
/// Address Block" composed of multiple text lines). A custom component is a
/// serialized TemplateElement list with stable ids; the user can drag-drop it
/// from a side panel into the canvas.
class CustomComponentManager {
 public:
  struct CustomComponent {
    std::string id;
    std::string display_name;
    std::vector<TemplateElement> elements;
  };
  static std::vector<CustomComponent> all();
  static bool save(const CustomComponent& c);
  static bool erase(const std::string& id);
};

/// VariableGrouper: groups variables into categories for the variable picker.
/// The Java original groups built-in variables into "Buyer", "Transport",
/// "Payment", "Item", "Totals" categories.
class VariableGrouper {
 public:
  struct Group { std::string name; std::vector<std::string> variable_keys; };
  static std::vector<Group> groups();
};

/// TemplatePackageService: ZIP-based template import/export (skill §10:
/// container = ZIP, canonical JSON, versioned migrations, signed via Ed25519).
class TemplatePackageService {
 public:
  /// Export a template as a .istp ZIP file at the given path. The ZIP contains
  /// template.json + resources/<sha256>.<ext>.
  static bool export_to(const std::filesystem::path& zip_path,
                         const Template& tpl,
                         const std::map<std::string, std::vector<std::uint8_t>>& resources);

  /// Import a .istp ZIP file into a Template. Refuses newer major versions.
  static bool import_from(const std::filesystem::path& zip_path,
                          Template& out_tpl,
                          std::map<std::string, std::vector<std::uint8_t>>& out_resources);
};

/// SvgVectorParser: parses SVG XML into a QPainterPath-like representation.
/// Used by the template designer for shape elements (line, rect, ellipse,
/// polygon, star, path). Phase 5d skeleton: real impl uses QtSvg's
/// QSvgRenderer for parsing + a path extractor for the display list.
class SvgVectorParser {
 public:
  struct Path_ { std::string fill_color; std::string stroke_color; std::string d; };
  static std::vector<Path_> parse(const std::string& svg_xml);
};

/// MonoImage: monochrome 1-bit-per-pixel image (used for label printing
/// where colour is irrelevant and we need minimum byte size).
class MonoImage {
 public:
  MonoImage(int w, int h);
  void set_pixel(int x, int y, bool on);
  bool get_pixel(int x, int y) const;
  std::vector<std::uint8_t> to_packed_bytes() const;  // 8 pixels per byte, MSB first
  int width() const noexcept { return w_; }
  int height() const noexcept { return h_; }
 private:
  int w_, h_;
  std::vector<std::uint8_t> bytes_;  // row-major, 1 bpp packed
};

} // namespace fin::services
