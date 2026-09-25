// fin/ui/designer_interaction.cpp — R-tree hit testing + snap + undo + clipboard
//
// Phase 6 skeleton: ElementIndex uses linear scan for now (acceptable for
// templates with ~hundreds of elements; for thousands, swap in
// boost::geometry::index::rtree without changing the API).
#include "fin/ui/designer_interaction.hpp"
#include "fin/app/log.hpp"

#include <QApplication>
#include <QClipboard>
#include <QMimeData>
#include <QJsonDocument>
#include <QJsonArray>
#include <QJsonObject>
#include <QUndoStack>

#include <algorithm>
#include <cmath>

namespace fin::ui {

// ============================================================
// ElementIndex — linear scan; O(N) per hit-test. Acceptable for typical
// templates (10-200 elements). For thousands, swap to Boost rtree.
// ============================================================

void ElementIndex::insert(const std::string& element_id,
                           fin::services::Fixed x, fin::services::Fixed y,
                           fin::services::Fixed w, fin::services::Fixed h) {
  entries_.push_back({element_id, x.to_mm(), y.to_mm(), w.to_mm(), h.to_mm()});
}

std::string ElementIndex::hit_test(double x_mm, double y_mm) const {
  // Iterate in reverse so topmost (last-drawn, highest z-order) wins.
  for (auto it = entries_.rbegin(); it != entries_.rend(); ++it) {
    const auto& e = *it;
    if (x_mm >= e.x && x_mm <= e.x + e.w &&
        y_mm >= e.y && y_mm <= e.y + e.h) {
      return e.id;
    }
  }
  return {};
}

std::vector<std::string> ElementIndex::query_rect(double x1_mm, double y1_mm,
                                                    double x2_mm, double y2_mm) const {
  std::vector<std::string> out;
  double lo_x = std::min(x1_mm, x2_mm);
  double hi_x = std::max(x1_mm, x2_mm);
  double lo_y = std::min(y1_mm, y2_mm);
  double hi_y = std::max(y1_mm, y2_mm);
  for (const auto& e : entries_) {
    if (e.x < hi_x && e.x + e.w > lo_x &&
        e.y < hi_y && e.y + e.h > lo_y) {
      out.push_back(e.id);
    }
  }
  return out;
}

// ============================================================
// Snapper — grid pitch + smart guides
// ============================================================

double Snapper::snap_to_grid(double mm) const {
  if (!grid_enabled_) return mm;
  // Round to nearest grid pitch.
  return std::round(mm / grid_pitch_mm_) * grid_pitch_mm_;
}

Snapper::SnapResult Snapper::snap_with_guides(double mm, bool /*horizontal*/,
                                                const std::vector<double>& candidates,
                                                double threshold_mm) const {
  SnapResult r{mm, false, 0.0};
  if (!guides_enabled_) {
    r.position_mm = snap_to_grid(mm);
    return r;
  }
  // Find nearest candidate within threshold.
  double best_delta = threshold_mm;
  double best_pos = mm;
  bool found = false;
  for (double c : candidates) {
    double d = std::abs(c - mm);
    if (d < best_delta) {
      best_delta = d;
      best_pos = c;
      found = true;
    }
  }
  if (found) {
    r.snapped = true;
    r.guide_pos_mm = best_pos;
    r.position_mm = best_pos;
  } else {
    r.position_mm = snap_to_grid(mm);
  }
  return r;
}

// ============================================================
// Undo commands
// ============================================================

MoveElementCommand::MoveElementCommand(std::string element_id,
                                        double old_x, double old_y,
                                        double new_x, double new_y,
                                        QUndoCommand* parent)
  : QUndoCommand(parent), element_id_(std::move(element_id)),
    old_x_(old_x), old_y_(old_y), new_x_(new_x), new_y_(new_y) {
  setText(QString("Move %1").arg(QString::fromStdString(element_id_)));
}

void MoveElementCommand::undo() {
  // Phase 6: apply old_x_/old_y_ to the template element with element_id_.
  // The actual mutation goes through a DesignerCanvas callback that the
  // StudioApp wires up when it constructs the TemplateDesignerView.
  fin::app::log::debugf("MoveElementCommand::undo {} ({},{}) → ({},{})",
                         element_id_, new_x_, new_y_, old_x_, old_y_);
}

void MoveElementCommand::redo() {
  fin::app::log::debugf("MoveElementCommand::redo {} ({},{}) → ({},{})",
                         element_id_, old_x_, old_y_, new_x_, new_y_);
}

int MoveElementCommand::id() const { return 1; }

bool MoveElementCommand::mergeWith(const QUndoCommand* other) {
  // Skill §8: merge consecutive drags into one command (so dragging an
  // element across the canvas produces ONE undo step, not 50).
  if (other->id() != id()) return false;
  const auto* o = static_cast<const MoveElementCommand*>(other);
  if (o->element_id_ != element_id_) return false;
  new_x_ = o->new_x_;
  new_y_ = o->new_y_;
  return true;
}

ResizeElementCommand::ResizeElementCommand(std::string element_id,
                                            double old_x, double old_y, double old_w, double old_h,
                                            double new_x, double new_y, double new_w, double new_h,
                                            QUndoCommand* parent)
  : QUndoCommand(parent), element_id_(std::move(element_id)),
    old_x_(old_x), old_y_(old_y), old_w_(old_w), old_h_(old_h),
    new_x_(new_x), new_y_(new_y), new_w_(new_w), new_h_(new_h) {
  setText(QString("Resize %1").arg(QString::fromStdString(element_id_)));
}

void ResizeElementCommand::undo() {
  fin::app::log::debugf("ResizeElementCommand::undo {}", element_id_);
}
void ResizeElementCommand::redo() {
  fin::app::log::debugf("ResizeElementCommand::redo {}", element_id_);
}

CreateElementCommand::CreateElementCommand(std::string element_id,
                                            fin::services::TemplateElement element,
                                            QUndoCommand* parent)
  : QUndoCommand(parent), element_id_(std::move(element_id)), element_(std::move(element)) {
  setText(QString("Create %1").arg(QString::fromStdString(element_id_)));
}

void CreateElementCommand::undo() {
  fin::app::log::debugf("CreateElementCommand::undo {}", element_id_);
}
void CreateElementCommand::redo() {
  fin::app::log::debugf("CreateElementCommand::redo {}", element_id_);
}

DeleteElementCommand::DeleteElementCommand(std::string element_id,
                                            fin::services::TemplateElement element,
                                            QUndoCommand* parent)
  : QUndoCommand(parent), element_id_(std::move(element_id)), element_(std::move(element)) {
  setText(QString("Delete %1").arg(QString::fromStdString(element_id_)));
}

void DeleteElementCommand::undo() {
  fin::app::log::debugf("DeleteElementCommand::undo {}", element_id_);
}
void DeleteElementCommand::redo() {
  fin::app::log::debugf("DeleteElementCommand::redo {}", element_id_);
}

// ============================================================
// Clipboard
// ============================================================

void DesignerClipboard::copy(const std::vector<fin::services::TemplateElement>& elements) {
  // Skill §8: serialise as JSON with a private MIME type.
  QJsonArray arr;
  for (const auto& el : elements) {
    QJsonObject o;
    o["id"] = QString::fromStdString(el.id);
    o["type"] = static_cast<int>(el.type);
    o["x"] = el.bounds.origin.x.to_mm();
    o["y"] = el.bounds.origin.y.to_mm();
    o["w"] = el.bounds.size.w.to_mm();
    o["h"] = el.bounds.size.h.to_mm();
    o["text"] = QString::fromStdString(el.text);
    arr.append(o);
  }
  QJsonDocument doc(arr);
  QByteArray data = doc.toJson(QJsonDocument::Compact);
  auto* mime = new QMimeData();
  mime->setData("application/x-invoicestudio-template-elements", data);
  mime->setText(data);  // plain-text fallback so other apps can read it
  QApplication::clipboard()->setMimeData(mime);
}

std::vector<fin::services::TemplateElement> DesignerClipboard::paste() {
  std::vector<fin::services::TemplateElement> out;
  const QMimeData* mime = QApplication::clipboard()->mimeData();
  if (!mime) return out;
  QByteArray data = mime->data("application/x-invoicestudio-template-elements");
  if (data.isEmpty()) return out;
  auto doc = QJsonDocument::fromJson(data);
  if (!doc.isArray()) return out;
  for (const auto& v : doc.array()) {
    auto o = v.toObject();
    fin::services::TemplateElement el;
    // Generate a new id so pasted elements don't collide with originals.
    el.id = QUuid::createUuid().toString(QUuid::WithoutBraces).toStdString();
    el.type = static_cast<fin::services::ElementType>(o.value("type").toInt());
    // Offset by 5mm so pasted elements don't overlap the originals (skill §8).
    el.bounds.origin.x = fin::services::Fixed::from_mm(o.value("x").toDouble() + 5.0);
    el.bounds.origin.y = fin::services::Fixed::from_mm(o.value("y").toDouble() + 5.0);
    el.bounds.size.w   = fin::services::Fixed::from_mm(o.value("w").toDouble());
    el.bounds.size.h   = fin::services::Fixed::from_mm(o.value("h").toDouble());
    el.text = o.value("text").toString().toStdString();
    out.push_back(el);
  }
  return out;
}

bool DesignerClipboard::has_template_elements() {
  const QMimeData* mime = QApplication::clipboard()->mimeData();
  return mime && mime->hasFormat("application/x-invoicestudio-template-elements");
}

} // namespace fin::ui
