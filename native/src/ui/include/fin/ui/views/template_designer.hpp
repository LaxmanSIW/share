// fin/ui/views/template_designer.hpp — Template Designer canvas (port of Java TemplateDesigner.java)
//
// Skill rule (template-designer-and-rendering §6-9):
//   6. Canvas = display-list render + separate interaction overlay + R-tree hit testing
//   7. Live preview: debounce → snapshot → LatestOnly layout → swap; keep last good render
//   8. Undo/redo via QUndoStack
//   9. File format: ZIP + canonical JSON + versioned migrations
//
// USER REQUIREMENT #7: ruler supports negative axis (the C++ ruler_ticks()
// in template_engine.hpp already accepts negative ranges; the ruler widget
// below renders accordingly, with the origin draggable to any position).
#pragma once
#include <QFrame>
#include <QScrollArea>
#include <QLabel>
#include <QGraphicsView>
#include <QGraphicsScene>
#include <memory>

namespace fin::services { struct Template; }

namespace fin::ui {

class DesignerCanvas;
class DesignerRuler;
class DesignerToolbar;
class DesignerPropertyInspector;

class TemplateDesignerView : public QFrame {
  Q_OBJECT
 public:
  explicit TemplateDesignerView(QWidget* parent = nullptr);

  /// Load a template into the designer.
  void load_template(std::shared_ptr<fin::services::Template> tpl);

  /// Get the current editable template (for save/export).
  std::shared_ptr<fin::services::Template> current_template() const { return template_; }

 private:
  void build_toolbar_();
  void build_central_area_();
  void build_property_inspector_();
  void build_status_bar_();

  DesignerToolbar*             toolbar_{nullptr};
  DesignerRuler*               h_ruler_{nullptr};
  DesignerRuler*               v_ruler_{nullptr};
  DesignerCanvas*              canvas_{nullptr};
  DesignerPropertyInspector*   inspector_{nullptr};
  QLabel*                       status_label_{nullptr};

  std::shared_ptr<fin::services::Template> template_;
};

} // namespace fin::ui
