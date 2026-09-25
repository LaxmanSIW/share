// fin/ui/designer_interaction.hpp — Canvas interaction helpers (port of Java TemplateDesigner.java selection/drag/snap logic)
//
// Skill rule (template-designer-and-rendering §8): the design canvas has
// separate interaction overlay + R-tree hit testing + snap-to-grid + smart
// guides + undo/redo via QUndoStack + clipboard.
//
// USER REQUIREMENT #7 already covered: ruler supports negative axis
// (designer_ruler.cpp).
#pragma once
#include "fin/services/template_engine.hpp"

#include <QPointF>
#include <QUndoStack>
#include <QRectF>
#include <unordered_map>
#include <vector>
#include <string>

namespace fin::ui {

/// R-tree hit-testing index over element bounds. Skill §6: hit testing uses
/// an R-tree (not the scene graph) so we can hit-test elements that are NOT
/// in a QGraphicsView.
///
/// Phase 6 skeleton: uses a simple linear scan for now; Phase 7 can swap in
/// Boost.Geometry rtree without changing the API.
class ElementIndex {
 public:
  void clear() { entries_.clear(); }
  void insert(const std::string& element_id, fin::services::Fixed x, fin::services::Fixed y,
              fin::services::Fixed w, fin::services::Fixed h);
  /// Find the topmost element whose bounds contain (x_mm, y_mm). Returns
  /// empty string if none. USER REQUIREMENT #7: x_mm/y_mm can be negative.
  std::string hit_test(double x_mm, double y_mm) const;
  /// Find all elements whose bounds intersect the given rect (for marquee).
  std::vector<std::string> query_rect(double x1_mm, double y1_mm, double x2_mm, double y2_mm) const;

 private:
  struct Entry {
    std::string id;
    double x, y, w, h;
  };
  std::vector<Entry> entries_;
};

/// Snap-to-grid + smart guides. Skill §8: keep sorted arrays of candidate
/// x/y values and binary-search within a screen-space threshold.
class Snapper {
 public:
  Snapper() = default;

  /// Set the grid pitch in mm (e.g. 1mm = fine, 5mm = coarse).
  void set_grid_pitch_mm(double pitch) { grid_pitch_mm_ = pitch; }
  void enable_grid(bool on) { grid_enabled_ = on; }
  void enable_guides(bool on) { guides_enabled_ = on; }

  /// Snap a single coordinate to the nearest grid line (if grid enabled).
  double snap_to_grid(double mm) const;

  /// Snap to other elements' edges/centres. Returns the snapped position + a
  /// flag indicating whether a guide was hit.
  struct SnapResult { double position_mm; bool snapped; double guide_pos_mm; };
  SnapResult snap_with_guides(double mm, bool horizontal,
                                const std::vector<double>& candidate_positions,
                                double threshold_mm = 4.0) const;

 private:
  double grid_pitch_mm_{1.0};
  bool   grid_enabled_{true};
  bool   guides_enabled_{true};
};

/// Undo stack commands for the template designer (skill §8: QUndoStack).
/// Commands store minimal deltas — never snapshot the whole document per action.
class MoveElementCommand : public QUndoCommand {
 public:
  MoveElementCommand(std::string element_id, double old_x_mm, double old_y_mm,
                      double new_x_mm, double new_y_mm,
                      QUndoCommand* parent = nullptr);
  void undo() override;
  void redo() override;
  int id() const override { return 1; }
  bool mergeWith(const QUndoCommand* other) override;
 private:
  std::string element_id_;
  double old_x_, old_y_, new_x_, new_y_;
};

class ResizeElementCommand : public QUndoCommand {
 public:
  ResizeElementCommand(std::string element_id,
                        double old_x, double old_y, double old_w, double old_h,
                        double new_x, double new_y, double new_w, double new_h,
                        QUndoCommand* parent = nullptr);
  void undo() override;
  void redo() override;
 private:
  std::string element_id_;
  double old_x_, old_y_, old_w_, old_h_;
  double new_x_, new_y_, new_w_, new_h_;
};

class CreateElementCommand : public QUndoCommand {
 public:
  CreateElementCommand(std::string element_id, fin::services::TemplateElement element,
                       QUndoCommand* parent = nullptr);
  void undo() override;
  void redo() override;
 private:
  std::string element_id_;
  fin::services::TemplateElement element_;
};

class DeleteElementCommand : public QUndoCommand {
 public:
  DeleteElementCommand(std::string element_id, fin::services::TemplateElement element,
                        QUndoCommand* parent = nullptr);
  void undo() override;
  void redo() override;
 private:
  std::string element_id_;
  fin::services::TemplateElement element_;
};

/// Clipboard: serialise selected elements as JSON with a private MIME type.
/// Skill §8: clipboard, multi-selection "mixed" values; image/plain-text
/// fallback for pasting into other apps.
class DesignerClipboard {
 public:
  /// Copy selected element ids into the clipboard.
  static void copy(const std::vector<fin::services::TemplateElement>& elements);
  /// Paste from clipboard into the designer (offsets by 5mm so pasted elements
  /// don't overlap the originals).
  static std::vector<fin::services::TemplateElement> paste();
  /// Returns true if the clipboard has template elements in our private MIME.
  static bool has_template_elements();
};

} // namespace fin::ui
