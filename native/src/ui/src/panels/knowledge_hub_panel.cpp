// fin/ui/panels/knowledge_hub_panel.cpp
#include "fin/ui/panels/knowledge_hub_panel.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/icon_helper.hpp"
#include "fin/ui/chat/chat_widgets.hpp"  // ChatMarkdownRenderer
#include "fin/services/chatbot.hpp"
#include "fin/services/knowledge_seed.hpp"

#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QSplitter>
#include <QListView>
#include <QStringListModel>
#include <QTextBrowser>
#include <QLabel>
#include <QLineEdit>
#include <QPushButton>
#include <QTimer>

namespace fin::ui {

KnowledgeHubPanel::KnowledgeHubPanel(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(24, 24, 24, 24);
  l->setSpacing(16);

  // Header
  auto* header = UiTheme::row(12, this);
  auto* tb = new QVBoxLayout();
  tb->setSpacing(2);
  tb->addWidget(UiTheme::pageTitle("Knowledge Hub"));
  tb->addWidget(UiTheme::pageSubtitle("How-to guides + FAQs + tutorials"));
  header->layout()->addLayout(tb);
  header->layout()->addItem(UiTheme::hspacer());
  l->addWidget(header);

  // Search row
  search_ = UiTheme::lineEdit("Search articles…", this);
  search_->setClearButtonEnabled(true);
  l->addWidget(search_);

  // Body: 3-column splitter (categories | articles | content)
  auto* split = new QSplitter(Qt::Horizontal, this);
  split->setHandleWidth(1);

  // Categories (left)
  auto* cat_card = UiTheme::card(8, split);
  cat_card->layout()->addWidget(UiTheme::cardTitle("Categories", cat_card));
  categories_ = new QListView(cat_card);
  categories_->setEditTriggers(QAbstractItemView::NoEditTriggers);
  cat_card->layout()->addWidget(categories_);
  split->addWidget(cat_card);

  // Articles (middle)
  auto* art_card = UiTheme::card(8, split);
  art_card->layout()->addWidget(UiTheme::cardTitle("Articles", art_card));
  articles_ = new QListView(art_card);
  articles_->setEditTriggers(QAbstractItemView::NoEditTriggers);
  art_card->layout()->addWidget(articles_);
  split->addWidget(art_card);

  // Content (right)
  auto* content_card = UiTheme::card(8, split);
  auto* content_l = static_cast<QVBoxLayout*>(content_card->layout());
  title_ = UiTheme::cardTitle("Select an article", content_card);
  content_l->addWidget(title_);
  content_ = new QTextBrowser(content_card);
  content_->setOpenExternalLinks(true);
  content_->setStyleSheet("QTextBrowser { background-color: transparent; color: #F4F4F5; border: none; }");
  content_l->addWidget(content_, 1);
  split->addWidget(content_card);

  split->setStretchFactor(0, 1);
  split->setStretchFactor(1, 2);
  split->setStretchFactor(2, 4);
  l->addWidget(split, 1);

  // Wire article selection → render markdown in content_.
  connect(articles_, &QListView::clicked, this, [this](const QModelIndex& idx){
    auto title = idx.data(Qt::DisplayRole).toString();
    auto articles = fin::services::KnowledgeRepository::search("");
    for (const auto& a : articles) {
      if (QString::fromStdString(a.title) == title) {
        title_->setText(QString::fromStdString(a.title));
        content_->setHtml(ChatMarkdownRenderer::to_html(QString::fromStdString(a.body_markdown)));
        return;
      }
    }
  });

  // Wire search.
  connect(search_, &QLineEdit::textChanged, this, [this](const QString& q){
    auto results = fin::services::KnowledgeRepository::search(q.toStdString());
    QStringList titles;
    for (const auto& a : results) titles << QString::fromStdString(a.title);
    articles_->setModel(new QStringListModel(titles, this));
  });

  QTimer::singleShot(0, this, [this]{ refresh(); });
}

void KnowledgeHubPanel::refresh() {
  // Seed the knowledge base on first run.
  fin::services::KnowledgeSeed::seed_if_missing();
  fin::services::KnowledgeRepository::load();

  // Populate categories list.
  auto cats = fin::services::KnowledgeRepository::categories();
  QStringList cat_list;
  cat_list << "(All)";
  for (const auto& c : cats) cat_list << QString::fromStdString(c);
  categories_->setModel(new QStringListModel(cat_list, this));

  // Populate articles list (initially empty search → show all).
  auto articles = fin::services::KnowledgeRepository::search("");
  QStringList titles;
  for (const auto& a : articles) titles << QString::fromStdString(a.title);
  articles_->setModel(new QStringListModel(titles, this));
}

} // namespace fin::ui
