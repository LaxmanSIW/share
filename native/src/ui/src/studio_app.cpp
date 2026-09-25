// fin/ui/studio_app.cpp
#include "fin/ui/studio_app.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/icon_helper.hpp"
#include "fin/ui/title_bar.hpp"
#include "fin/ui/sidebar.hpp"
#include "fin/ui/widgets/foundation.hpp"
#include "fin/ui/widgets/toast.hpp"
#include "fin/ui/views/dashboard_view.hpp"
#include "fin/ui/views/buyers_view.hpp"
#include "fin/ui/views/items_view.hpp"
#include "fin/ui/views/bills_view.hpp"
#include "fin/ui/views/create_bill_view.hpp"
#include "fin/ui/views/suppliers_view.hpp"
#include "fin/ui/views/settings_view.hpp"
#include "fin/ui/views/reports_view.hpp"
#include "fin/ui/views/categories_view.hpp"
#include "fin/ui/views/create_purchase_view.hpp"
#include "fin/ui/views/expenses_view.hpp"
#include "fin/ui/views/financials_view.hpp"
#include "fin/ui/views/history_view.hpp"
#include "fin/ui/views/label_history_view.hpp"
#include "fin/ui/views/purchases_view.hpp"
#include "fin/ui/views/stock_analysis_view.hpp"
#include "fin/ui/views/templates_view.hpp"
#include "fin/ui/views/transactions_view.hpp"
#include "fin/ui/views/transports_view.hpp"
#include "fin/ui/views/variables_view.hpp"
#include "fin/ui/views/template_designer.hpp"
#include "fin/ui/chat/chatbot_panel.hpp"
#include "fin/ui/auth/auth_view.hpp"

#include "fin/db/database_manager.hpp"
#include "fin/services/auth_session.hpp"
#include "fin/app/app_dirs.hpp"
#include "fin/app/log.hpp"
#include "fin/app/executors.hpp"

#include <QApplication>
#include <QCloseEvent>
#include <QFrame>
#include <QHBoxLayout>
#include <QSettings>
#include <QStackedWidget>
#include <QTimer>
#include <QScreen>
#include <QGuiApplication>

namespace fin::ui {

StudioApp::StudioApp(QWidget* parent)
  : QMainWindow(parent),
    title_bar_(nullptr),
    sidebar_(nullptr),
    view_stack_(nullptr),
    chatbot_panel_(nullptr),
    auth_view_(nullptr),
    shortcuts_(nullptr) {
  setObjectName("StudioApp");
  setWindowFlags(Qt::FramelessWindowHint | Qt::Window);
  setAttribute(Qt::WA_TranslucentBackground, false);
  setMinimumSize(1024, 720);
}

StudioApp::~StudioApp() {
  fin::app::Executors::shutdown();
}

void StudioApp::startup() {
  fin::app::log::info("StudioApp starting up…");

  // Apply QSS theme.
  UiTheme::apply_theme(qApp);

  // Set app icon.
  // Skill: SVG only; Qt QIcon can hold SVG via QSvgRenderer.

  // Open database + run migrations. Skill §8: PRAGMA optimize; backup before
  // migrations. For startup we do this synchronously (Phase 4 — Phase 7 will
  // move it to a worker and show a splash screen).
  try {
    auto& db = fin::db::DatabaseManager::instance();
    db.open();
    fin::app::log::infof("Database ready: {}", db.file_path().string());
  } catch (const std::exception& e) {
    fin::app::log::errorf("Database init failed: {}", e.what());
  }

  // Build the shell UI.
  build_shell_();

  // Register all keyboard shortcuts (Ctrl+N new bill, Ctrl+P print, etc.).
  shortcuts_ = new AppShortcuts(this, this);
  shortcuts_->register_shortcut("nav_dashboard",  QKeySequence(ShortcutCatalog::NAV_DASHBOARD),
    [this]{ show_view("dashboard"); }, "Dashboard");
  shortcuts_->register_shortcut("nav_bills",      QKeySequence(ShortcutCatalog::NAV_BILLS),
    [this]{ show_view("bills"); }, "Invoices");
  shortcuts_->register_shortcut("nav_create_bill", QKeySequence(ShortcutCatalog::NAV_CREATE_BILL),
    [this]{ show_view("create_bill"); }, "Create Bill");
  shortcuts_->register_shortcut("nav_buyers",     QKeySequence(ShortcutCatalog::NAV_BUYERS),
    [this]{ show_view("buyers"); }, "Buyers");
  shortcuts_->register_shortcut("nav_items",      QKeySequence(ShortcutCatalog::NAV_ITEMS),
    [this]{ show_view("items"); }, "Items");
  shortcuts_->register_shortcut("nav_reports",    QKeySequence(ShortcutCatalog::NAV_REPORTS),
    [this]{ show_view("reports"); }, "Reports");
  shortcuts_->register_shortcut("nav_settings",   QKeySequence(ShortcutCatalog::NAV_SETTINGS),
    [this]{ show_view("settings"); }, "Settings");
  shortcuts_->register_shortcut("toggle_chatbot", QKeySequence(ShortcutCatalog::VIEW_TOGGLE_CHATBOT),
    [this]{ toggle_chatbot(); }, "Toggle Chatbot");
  shortcuts_->register_shortcut("doc_save",       QKeySequence(ShortcutCatalog::DOC_SAVE),
    [this]{ Toast::info("Save (Ctrl+S) — works inside Create Bill view"); }, "Save Document");

  // Default to dashboard.
  show_view("dashboard");

  // Restore window geometry.
  load_geometry_();

  show();
}

void StudioApp::build_shell_() {
  auto* central = new QFrame(this);
  central->setStyleSheet("background-color: #0B0E13;");
  setCentralWidget(central);

  auto* outer_l = new QVBoxLayout(central);
  outer_l->setContentsMargins(0, 0, 0, 0);
  outer_l->setSpacing(0);

  // Title bar (frameless window — our own chrome).
  title_bar_ = new TitleBar(this);
  title_bar_->setTitle("InvoiceStudio");
  outer_l->addWidget(title_bar_);

  // Main body: sidebar + central view stack.
  auto* body = new QFrame(central);
  body->setStyleSheet("background-color: #0B0E13;");
  auto* body_l = new QHBoxLayout(body);
  body_l->setContentsMargins(0, 0, 0, 0);
  body_l->setSpacing(0);

  sidebar_ = new Sidebar(body);
  register_nav_();
  body_l->addWidget(sidebar_);

  view_stack_ = new QStackedWidget(body);
  view_stack_->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  body_l->addWidget(view_stack_, 1);

  // Chatbot overlay (initially hidden).
  chatbot_panel_ = new ChatbotPanel(body);
  chatbot_panel_->hide();
  body_l->addWidget(chatbot_panel_);

  outer_l->addWidget(body, 1);
}

void StudioApp::register_nav_() {
  // Match the Java original's sidebar structure EXACTLY:
  // 1. Dashboard
  // 2. Invoices (History)
  // 3. Transactions
  // 4. Reports & Ledger
  // 5. --- separator ---
  // 6. Purchases
  // 7. Expenses
  // 8. --- separator ---
  // 9. Financials
  // 10. Stock & Profit
  // 11. --- separator ---
  // 12. Catalog (popup: 8 items: Buyers, Sellers, Items, Categories, Templates, Transports, Variables, Label History)
  // 13. Settings
  // --- filler ---
  // 14. + New Bill (gold button)
  // 15. User profile pill
  sidebar_->add_item({"dashboard", "Dashboard", IconHelper::ICON_DASHBOARD}, [this]{ show_view("dashboard"); });
  sidebar_->add_item({"history", "Invoices", IconHelper::ICON_HISTORY}, [this]{ show_view("history"); });
  sidebar_->add_item({"transactions", "Transactions", IconHelper::ICON_TRANSACTIONS}, [this]{ show_view("transactions"); });
  sidebar_->add_item({"reports", "Reports & Ledger", IconHelper::ICON_REPORTS}, [this]{ show_view("reports"); });
  sidebar_->add_item({"purchases", "Purchases", IconHelper::ICON_BILLING}, [this]{ show_view("purchases"); });
  sidebar_->add_item({"expenses", "Expenses", IconHelper::ICON_TAG}, [this]{ show_view("expenses"); });
  sidebar_->add_item({"financials", "Financials", IconHelper::ICON_BAR_CHART}, [this]{ show_view("financials"); });
  sidebar_->add_item({"stock_analysis", "Stock & Profit", IconHelper::ICON_TRENDING_UP}, [this]{ show_view("stock_analysis"); });

  // Catalog popup button — collapses 8 directory items into one (matches Java)
  sidebar_->add_catalog_button(
    {{"buyers", "Buyers", IconHelper::ICON_USERS},
     {"suppliers", "Sellers", IconHelper::ICON_BUSINESS},
     {"items", "Items", IconHelper::ICON_PACKAGE},
     {"categories", "Categories", IconHelper::ICON_CATEGORIES},
     {"templates", "Templates", IconHelper::ICON_TEMPLATES},
     {"transports", "Transports", IconHelper::ICON_TRANSPORT},
     {"variables", "Variables", IconHelper::ICON_VARIABLE},
     {"label_history", "Label Print History", IconHelper::ICON_HISTORY}},
    [this](const std::string& id) { show_view(QString::fromStdString(id)); }
  );

  sidebar_->add_item({"settings", "Settings", IconHelper::ICON_SETTINGS}, [this]{ show_view("settings"); });

  // + New Bill button (gold CTA at bottom — matches Java sidebar-cta)
  sidebar_->add_new_bill_button([this]{ show_view("create_bill"); });

  // User profile pill (updated on login)
  sidebar_->set_user("Guest", "Not signed in");
}

void StudioApp::show_view(const QString& id) {
  // Lazy-build + cache by id. Skill: stale-while-revalidate (viewEpochs in
  // Java) is layered on top of this in Phase 5+ when we add refresh logic.
  auto it = view_cache_.find(id.toStdString());
  if (it == view_cache_.end()) {
    QWidget* view = nullptr;
    if (id == "dashboard")    view = new DashboardView(view_stack_);
    else if (id == "bills")      view = new BillsView(view_stack_);
    else if (id == "create_bill") view = new CreateBillView(view_stack_);
    else if (id == "buyers")     view = new BuyersView(view_stack_);
    else if (id == "items")      view = new ItemsView(view_stack_);
    else if (id == "suppliers")  view = new SuppliersView(view_stack_);
    else if (id == "purchases")  view = new PurchasesView(view_stack_);
    else if (id == "create_purchase") view = new CreatePurchaseView(view_stack_);
    else if (id == "transactions") view = new TransactionsView(view_stack_);
    else if (id == "expenses")   view = new ExpensesView(view_stack_);
    else if (id == "financials") view = new FinancialsView(view_stack_);
    else if (id == "stock_analysis") view = new StockAnalysisView(view_stack_);
    else if (id == "categories") view = new CategoriesView(view_stack_);
    else if (id == "transports") view = new TransportsView(view_stack_);
    else if (id == "variables")  view = new VariablesView(view_stack_);
    else if (id == "templates")  view = new TemplatesView(view_stack_);
    else if (id == "designer")   view = new TemplateDesignerView(view_stack_);
    else if (id == "label_history") view = new LabelHistoryView(view_stack_);
    else if (id == "history")   view = new HistoryView(view_stack_);
    else if (id == "reports")    view = new ReportsView(view_stack_);
    else if (id == "settings")  view = new SettingsView(view_stack_);
    else {
      fin::app::log::warnf("StudioApp: unknown view id {}", id.toStdString());
      return;
    }
    view_index_[id] = view_stack_->addWidget(view);
    view_cache_[id.toStdString()] = view;
  }
  view_stack_->setCurrentIndex(view_index_[id]);
  sidebar_->set_active(id);
  title_bar_->setTitle("InvoiceStudio — " + id);
}

void StudioApp::toggle_chatbot() {
  if (!chatbot_panel_) return;
  chatbot_panel_->setVisible(!chatbot_panel_->isVisible());
  if (chatbot_panel_->isVisible()) chatbot_panel_->setFocus();
}

void StudioApp::show_auth() {
  if (!auth_view_) {
    auth_view_ = new AuthView(this);
    auth_view_->set_on_success([this]() {
      // On login, switch to the main shell.
      if (auth_view_) auth_view_->hide();
      show();
    });
  }
  hide();
  auth_view_->show();
}

void StudioApp::closeEvent(QCloseEvent* e) {
  save_geometry_();
  e->accept();
}

void StudioApp::resizeEvent(QResizeEvent* e) {
  QMainWindow::resizeEvent(e);
}

void StudioApp::load_geometry_() {
  QSettings s;
  s.beginGroup("StudioApp");
  auto geom = s.value("geometry").toByteArray();
  if (!geom.isEmpty()) {
    restoreGeometry(geom);
  } else {
    // Default: 80% of available screen, centered.
    auto screen = QGuiApplication::primaryScreen();
    if (screen) {
      QRect avail = screen->availableGeometry();
      int w = avail.width() * 4 / 5;
      int h = avail.height() * 4 / 5;
      int x = avail.x() + (avail.width() - w) / 2;
      int y = avail.y() + (avail.height() - h) / 2;
      setGeometry(x, y, w, h);
    }
  }
  s.endGroup();
}

void StudioApp::save_geometry_() {
  QSettings s;
  s.beginGroup("StudioApp");
  s.setValue("geometry", saveGeometry());
  s.endGroup();
}

} // namespace fin::ui
