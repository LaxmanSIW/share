// fin/ui/panels/knowledge_hub_panel.hpp — Knowledge hub UI panel
//
// Port of Java KnowledgeHubPanel.java. Browse + search knowledge base articles
// (loaded from KnowledgeRepository). Article view with markdown rendering.
#pragma once
#include <QFrame>
#include <QLineEdit>
class QListView;
class QTextBrowser;
class QLabel;

namespace fin::ui {

class KnowledgeHubPanel : public QFrame {
  Q_OBJECT
 public:
  explicit KnowledgeHubPanel(QWidget* parent = nullptr);
  void refresh();
 private:
  QLineEdit*    search_;
  QListView*    categories_;
  QListView*    articles_;
  QTextBrowser* content_;
  QLabel*       title_;
};

} // namespace fin::ui
