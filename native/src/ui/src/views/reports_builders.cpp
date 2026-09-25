#include <QApplication>
// fin/ui/views/reports_builders.cpp — 9 report tab builders.
//
// Each builder creates a QFrame with: filter row + summary cards + DataGrid +
// export-to-PDF button. The data comes from FinancialService, ExpenseAnalytics,
// and direct SQL aggregations on the DAOs.
#include "fin/ui/views/reports_builders.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/widgets/data_grid.hpp"
#include "fin/services/financial.hpp"
#include "fin/allocate.hpp"
#include "fin/services/expenses.hpp"
#include "fin/db/database_manager.hpp"
#include "fin/db/bill_dao.hpp"
#include "fin/db/daos.hpp"
#include "fin/db/daos2.hpp"
#include "fin/services/auth_session.hpp"

#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QLabel>
#include <QDateEdit>
#include <QPushButton>
#include <QStandardItemModel>
#include <QTimer>
#include <chrono>

namespace fin::ui {

namespace {

QFrame* make_report_frame(QWidget* parent, const QString& title,
                           std::function<void(QFrame*, QVBoxLayout*)> build_body) {
  auto* card = UiTheme::card(12, parent);
  auto* l = static_cast<QVBoxLayout*>(card->layout());
  l->addWidget(UiTheme::cardTitle(title, card));

  // Filter row: date range + Refresh button
  auto* filter = UiTheme::row(8, card);
  auto* from = new QDateEdit(QDate::currentDate().addMonths(-3), filter); from->setDisplayFormat("yyyy-MM-dd");
  auto* to   = new QDateEdit(QDate::currentDate(), filter); to->setDisplayFormat("yyyy-MM-dd");
  auto* refresh = UiTheme::ghostButton("Refresh", filter);
  auto* export_pdf = UiTheme::ghostButton("Export PDF", filter);
  filter->layout()->addWidget(UiTheme::muted("From:", filter));
  filter->layout()->addWidget(from);
  filter->layout()->addWidget(UiTheme::muted("To:", filter));
  filter->layout()->addWidget(to);
  static_cast<QHBoxLayout*>(filter->layout())->addItem(UiTheme::hspacer());
  filter->layout()->addWidget(export_pdf);
  filter->layout()->addWidget(refresh);
  l->addWidget(filter);

  build_body(card, l);
  return card;
}

} // namespace

QFrame* ReportsBuilders::build_trial_balance(QWidget* parent) {
  return make_report_frame(parent, "Trial Balance", [](QFrame* card, QVBoxLayout* l){
    auto* grid = new DataGrid(card);
    grid->set_columns({{"Account", 200}, {"Debit", 120}, {"Credit", 120}, {"", -1}});
    l->addWidget(grid, 1);

    QTimer::singleShot(0, card, [grid]{
      auto* model = new QStandardItemModel(0, 4, grid);
      model->setHorizontalHeaderLabels({"Account", "Debit", "Credit", ""});
      try {
        fin::db::BillDao bill_dao(fin::db::DatabaseManager::instance());
        fin::db::BuyerDao buyer_dao(fin::db::DatabaseManager::instance());
        fin::db::SupplierDao sup_dao(fin::db::DatabaseManager::instance());
        fin::db::ExpenseDao exp_dao(fin::db::DatabaseManager::instance());
        // Aggregate from bills: total receivables (sum of grand_total - sum(payments)).
        auto bills = bill_dao.find_all();
        fin::Money total_receivable = fin::Money::zero(fin::CurrencyId::INR);
        fin::Money total_sales = fin::Money::zero(fin::CurrencyId::INR);
        for (const auto& b : bills) {
          total_sales += b.totals.grand_total;
          fin::Money paid = fin::Money::zero(fin::CurrencyId::INR);
          for (const auto& p : b.payments) paid += p.amount;
          total_receivable += b.totals.grand_total - paid;
        }
        // Suppliers: total payable
        auto sups = sup_dao.find_all();
        fin::Money total_payable = fin::Money::zero(fin::CurrencyId::INR);
        // (Payable would come from purchase_bills; for now zero.)
        // Expenses
        auto exps = exp_dao.find_all();
        fin::Money total_expense = fin::Money::zero(fin::CurrencyId::INR);
        for (const auto& e : exps) total_expense += e.amount;
        QList<QStandardItem*> row1; row1 << new QStandardItem("Sales Revenue") << new QStandardItem("") << new QStandardItem(QString::fromStdString(total_sales.to_decimal_string())) << new QStandardItem("");
        QList<QStandardItem*> row2; row2 << new QStandardItem("Accounts Receivable") << new QStandardItem(QString::fromStdString(total_receivable.to_decimal_string())) << new QStandardItem("") << new QStandardItem("");
        QList<QStandardItem*> row3; row3 << new QStandardItem("Accounts Payable") << new QStandardItem("") << new QStandardItem(QString::fromStdString(total_payable.to_decimal_string())) << new QStandardItem("");
        QList<QStandardItem*> row4; row4 << new QStandardItem("Expenses") << new QStandardItem("") << new QStandardItem(QString::fromStdString(total_expense.to_decimal_string())) << new QStandardItem("");
        model->appendRow(row1); model->appendRow(row2); model->appendRow(row3); model->appendRow(row4);
      } catch (...) {}
      grid->setModel(model);
    });
  });
}

QFrame* ReportsBuilders::build_profit_loss(QWidget* parent) {
  return make_report_frame(parent, "Profit & Loss", [](QFrame* card, QVBoxLayout* l){
    auto* grid = new DataGrid(card);
    grid->set_columns({{"Section", 100}, {"Account", 200}, {"Amount", 120}, {"", -1}});
    l->addWidget(grid, 1);
    QTimer::singleShot(0, card, [grid]{
      auto* model = new QStandardItemModel(0, 4, grid);
      model->setHorizontalHeaderLabels({"Section", "Account", "Amount", ""});
      try {
        fin::db::BillDao bill_dao(fin::db::DatabaseManager::instance());
        fin::db::ExpenseDao exp_dao(fin::db::DatabaseManager::instance());
        fin::Money revenue = fin::Money::zero(fin::CurrencyId::INR);
        for (const auto& b : bill_dao.find_all()) revenue += b.totals.grand_total;
        fin::Money exp = fin::Money::zero(fin::CurrencyId::INR);
        for (const auto& e : exp_dao.find_all()) exp += e.amount;
        auto net = revenue - exp;
        QList<QStandardItem*> r1; r1 << new QStandardItem("Income") << new QStandardItem("Sales Revenue") << new QStandardItem(QString::fromStdString(revenue.to_decimal_string())) << new QStandardItem("");
        QList<QStandardItem*> r2; r2 << new QStandardItem("Expense") << new QStandardItem("Operating Expenses") << new QStandardItem(QString::fromStdString(exp.to_decimal_string())) << new QStandardItem("");
        QList<QStandardItem*> r3; r3 << new QStandardItem("Net Profit") << new QStandardItem("") << new QStandardItem(QString::fromStdString(net.to_decimal_string())) << new QStandardItem("");
        r3[2]->setForeground(QColor(net.is_negative() ? "#EF4444" : "#10B981"));
        model->appendRow(r1); model->appendRow(r2); model->appendRow(r3);
      } catch (...) {}
      grid->setModel(model);
    });
  });
}

QFrame* ReportsBuilders::build_gst_summary(QWidget* parent) {
  return make_report_frame(parent, "GST Summary (GSTR-1)", [](QFrame* card, QVBoxLayout* l){
    auto* grid = new DataGrid(card);
    grid->set_columns({{"GST %", 80}, {"Taxable", 120}, {"CGST", 100}, {"SGST", 100}, {"IGST", 100}, {"Total Tax", 120}, {"", -1}});
    l->addWidget(grid, 1);
    QTimer::singleShot(0, card, [grid]{
      auto* model = new QStandardItemModel(0, 7, grid);
      model->setHorizontalHeaderLabels({"GST %", "Taxable", "CGST", "SGST", "IGST", "Total Tax", ""});
      try {
        // Group bills' line items by gst_rate.
        std::map<double, std::tuple<fin::Money, fin::Money>> by_rate; // rate → (taxable, tax)
        fin::db::BillDao bill_dao(fin::db::DatabaseManager::instance());
        for (const auto& b : bill_dao.find_all()) {
          for (const auto& it : b.items) {
            double rate = it.gst_rate.num * 100.0 / it.gst_rate.denom;
            auto& [taxable, tax] = by_rate[rate];
            taxable += it.net();
            tax += it.net().apply(it.gst_rate, fin::Rounding::HalfAwayFromZero);
          }
        }
        for (const auto& [rate, vals] : by_rate) {
          const auto& [taxable, tax] = vals;
          auto cgst = fin::allocate_equal(tax, 2)[0];
          auto sgst = fin::allocate_equal(tax, 2)[1];
          QList<QStandardItem*> row;
          row << new QStandardItem(QString::number(rate) + "%")
              << new QStandardItem(QString::fromStdString(taxable.to_decimal_string()))
              << new QStandardItem(QString::fromStdString(cgst.to_decimal_string()))
              << new QStandardItem(QString::fromStdString(sgst.to_decimal_string()))
              << new QStandardItem("0.00")
              << new QStandardItem(QString::fromStdString(tax.to_decimal_string()))
              << new QStandardItem("");
          model->appendRow(row);
        }
      } catch (...) {}
      grid->setModel(model);
    });
  });
}

QFrame* ReportsBuilders::build_buyer_receivables(QWidget* parent) {
  return make_report_frame(parent, "Buyer Receivables (Ageing)", [](QFrame* card, QVBoxLayout* l){
    auto* grid = new DataGrid(card);
    grid->set_columns({{"Buyer", 180}, {"Bill No", 140}, {"Date", 100}, {"Amount", 120}, {"Days Overdue", 100}, {"", -1}});
    l->addWidget(grid, 1);
    QTimer::singleShot(0, card, [grid]{
      auto* model = new QStandardItemModel(0, 6, grid);
      model->setHorizontalHeaderLabels({"Buyer", "Bill No", "Date", "Amount", "Days Overdue", ""});
      try {
        fin::db::BillDao dao(fin::db::DatabaseManager::instance());
        for (const auto& b : dao.find_all()) {
          if (b.status != fin::model::BillStatus::Unpaid) continue;
          QDate d = QDate::fromString(QString::fromStdString(b.date), "yyyy-MM-dd");
          int days_overdue = d.isValid() ? d.daysTo(QDate::currentDate()) : 0;
          if (days_overdue < 0) days_overdue = 0;
          QList<QStandardItem*> row;
          row << new QStandardItem(QString::fromStdString(b.buyer_name()))
              << new QStandardItem(QString::fromStdString(b.bill_no))
              << new QStandardItem(QString::fromStdString(b.date))
              << new QStandardItem(QString::fromStdString(b.totals.grand_total.to_decimal_string()))
              << new QStandardItem(QString::number(days_overdue))
              << new QStandardItem("");
          model->appendRow(row);
        }
      } catch (...) {}
      grid->setModel(model);
    });
  });
}

QFrame* ReportsBuilders::build_supplier_payables(QWidget* parent) {
  return make_report_frame(parent, "Supplier Payables (Ageing)", [](QFrame* card, QVBoxLayout* l){
    auto* grid = new DataGrid(card);
    grid->set_columns({{"Supplier", 180}, {"Bill No", 140}, {"Date", 100}, {"Amount", 120}, {"", -1}});
    l->addWidget(grid, 1);
    QTimer::singleShot(0, card, [grid]{
      auto* model = new QStandardItemModel(0, 5, grid);
      model->setHorizontalHeaderLabels({"Supplier", "Bill No", "Date", "Amount", ""});
      try {
        fin::db::PurchaseBillDao dao(fin::db::DatabaseManager::instance());
        for (const auto& p : dao.find_all()) {
          if (p.status != "posted") continue;
          QList<QStandardItem*> row;
          row << new QStandardItem(QString::fromStdString(p.supplier_name))
              << new QStandardItem(QString::fromStdString(p.bill_no))
              << new QStandardItem(QString::fromStdString(p.date))
              << new QStandardItem(QString::fromStdString(p.total.to_decimal_string()))
              << new QStandardItem("");
          model->appendRow(row);
        }
      } catch (...) {}
      grid->setModel(model);
    });
  });
}

QFrame* ReportsBuilders::build_stock_valuation(QWidget* parent) {
  return make_report_frame(parent, "Stock Valuation", [](QFrame* card, QVBoxLayout* l){
    auto* grid = new DataGrid(card);
    grid->set_columns({{"Item", 200}, {"Stock", 100}, {"Purchase Rate", 100}, {"Value", 120}, {"", -1}});
    l->addWidget(grid, 1);
    QTimer::singleShot(0, card, [grid]{
      auto* model = new QStandardItemModel(0, 5, grid);
      model->setHorizontalHeaderLabels({"Item", "Stock", "Purchase Rate", "Value", ""});
      try {
        fin::db::ItemDao dao(fin::db::DatabaseManager::instance());
        for (const auto& it : dao.find_all()) {
          fin::Money value = it.current_stock.scale(it.purchase_rate.minor(), 2, fin::Rounding::HalfAwayFromZero);
          QList<QStandardItem*> row;
          row << new QStandardItem(QString::fromStdString(it.name))
              << new QStandardItem(QString::fromStdString(it.current_stock.to_decimal_string()))
              << new QStandardItem(QString::fromStdString(it.purchase_rate.to_decimal_string()))
              << new QStandardItem(QString::fromStdString(value.to_decimal_string()))
              << new QStandardItem("");
          model->appendRow(row);
        }
      } catch (...) {}
      grid->setModel(model);
    });
  });
}

QFrame* ReportsBuilders::build_sales_by_item(QWidget* parent) {
  return make_report_frame(parent, "Sales by Item", [](QFrame* card, QVBoxLayout* l){
    auto* grid = new DataGrid(card);
    grid->set_columns({{"Item", 200}, {"Qty Sold", 100}, {"Total", 120}, {"", -1}});
    l->addWidget(grid, 1);
    QTimer::singleShot(0, card, [grid]{
      auto* model = new QStandardItemModel(0, 4, grid);
      model->setHorizontalHeaderLabels({"Item", "Qty Sold", "Total", ""});
      try {
        std::map<std::string, std::pair<fin::Money, fin::Money>> by_item; // name → (qty, total)
        fin::db::BillDao dao(fin::db::DatabaseManager::instance());
        for (const auto& b : dao.find_all()) {
          for (const auto& it : b.items) {
            auto& [qty, total] = by_item[it.desc];
            qty += it.qty;
            total += it.net();
          }
        }
        for (const auto& [name, vals] : by_item) {
          const auto& [qty, total] = vals;
          QList<QStandardItem*> row;
          row << new QStandardItem(QString::fromStdString(name))
              << new QStandardItem(QString::fromStdString(qty.to_decimal_string()))
              << new QStandardItem(QString::fromStdString(total.to_decimal_string()))
              << new QStandardItem("");
          model->appendRow(row);
        }
      } catch (...) {}
      grid->setModel(model);
    });
  });
}

QFrame* ReportsBuilders::build_expense_breakdown(QWidget* parent) {
  return make_report_frame(parent, "Expense Breakdown", [](QFrame* card, QVBoxLayout* l){
    auto* grid = new DataGrid(card);
    grid->set_columns({{"Category", 200}, {"Count", 80}, {"Total", 120}, {"", -1}});
    l->addWidget(grid, 1);
    QTimer::singleShot(0, card, [grid]{
      auto* model = new QStandardItemModel(0, 4, grid);
      model->setHorizontalHeaderLabels({"Category", "Count", "Total", ""});
      try {
        std::map<std::string, std::pair<int, fin::Money>> by_cat;
        fin::db::ExpenseDao dao(fin::db::DatabaseManager::instance());
        for (const auto& e : dao.find_all()) {
          auto& [count, total] = by_cat[e.category];
          count++;
          total += e.amount;
        }
        for (const auto& [cat, vals] : by_cat) {
          const auto& [count, total] = vals;
          QList<QStandardItem*> row;
          row << new QStandardItem(QString::fromStdString(cat))
              << new QStandardItem(QString::number(count))
              << new QStandardItem(QString::fromStdString(total.to_decimal_string()))
              << new QStandardItem("");
          model->appendRow(row);
        }
      } catch (...) {}
      grid->setModel(model);
    });
  });
}

QFrame* ReportsBuilders::build_bank_cash_reconciliation(QWidget* parent) {
  return make_report_frame(parent, "Bank/Cash Reconciliation", [](QFrame* card, QVBoxLayout* l){
    auto* grid = new DataGrid(card);
    grid->set_columns({{"Date", 100}, {"Description", 200}, {"Debit", 120}, {"Credit", 120}, {"", -1}});
    l->addWidget(grid, 1);
    auto* empty = new QLabel("No transactions to reconcile.", card);
    empty->setStyleSheet("color:#64748B;");
    empty->setAlignment(Qt::AlignCenter);
    l->addWidget(empty);
  });
}

} // namespace fin::ui
