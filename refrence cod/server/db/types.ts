// ============================================================
// Shared literal types
// ============================================================
export type BookType = "cs" | "cc" | "CS" | "CC" | string;
export type TransactionType = "sale" | "payment";

// ─── User ────────────────────────────────────────────────
export interface User {
  id: number;
  unionId: string | null;
  username: string;
  password: string;
  name: string | null;
  email: string | null;
  avatar: string | null;
  // `role` is a free TEXT column in the DB (no CHECK constraint) —
  // this union is an app-level convention, not DB-enforced. Widen to
  // `string` if you expect other roles beyond these two.
  role: "admin" | "staff" | null;
  createdAt: string;
  updatedAt: string;
  lastSignInAt: string | null;
}

export type InsertUser = Omit<User, "id" | "createdAt" | "updatedAt">;

// ─── Transport ───────────────────────────────────────────
export interface Transport {
  id: number;
  name: string;
  phone: string | null;
  vehicleNumber: string | null;
  createdAt: string;
  updatedAt: string;
}

export type InsertTransport = Omit<Transport, "id" | "createdAt" | "updatedAt">;

// ─── Buyer ───────────────────────────────────────────────
export interface Buyer {
  id: number;
  companyName: string;
  contactPerson: string | null;
  phone: string | null;
  gstNumber: string | null;
  creditLimit: number;
  riskScore: number | null;
  address: string | null;
  city: string | null;
  state: string | null;
  stateCode: string | null;
  defaultTransportId: number | null;
  // defaultTransportName removed — join against Transport via
  // defaultTransportId instead of a duplicated display column.
  openingBalance: number;
  createdAt: string;
  updatedAt: string;
}

export type InsertBuyer = Omit<Buyer, "id" | "createdAt" | "updatedAt">;

// ─── Item Category ───────────────────────────────────────
// ============================================================
// Item Categories
// ============================================================

export interface ItemCategory {
  id: number;
  name: string;
  createdAt: string; // Stored as TEXT in SQLite
}

// For creating a new category (omit auto-generated fields)
export type InsertItemCategory = Omit<ItemCategory, "id" | "createdAt">;

// For updating a category (all fields optional except id)
export type UpdateItemCategory = Partial<InsertItemCategory>;

// ============================================================
// Items
// ============================================================

export interface Item {
  id: number;
  categoryId: number | null; // Linked to itemCategories.id
  name: string;
  hsnCode: string;
  listPrice: number;
  unit: string;
  taxPercent: number;
  createdAt: string;
  updatedAt: string;
}

// For creating a new item (omit auto-generated fields)
export type InsertItem = Omit<Item, "id" | "createdAt" | "updatedAt">;

// For updating an item (all fields optional except id)
export type UpdateItem = Partial<InsertItem>;



// ─── Transaction ─────────────────────────────────────────
// billId removed — the link now lives the other way, as
// Bill.transactionId (a transaction doesn't know if it has a bill;
// look it up via findBillByTransactionId / the billDetails view).
export interface Transaction {
  id: number;
  buyerId: number;
  bookType: BookType;
  transactionType: TransactionType;
  transactionDate: string;
  dueDate: string | null;
  amount: number;
  totalQuantity: number | null; // renamed from totalQuantity
  checkNumber: string | null; // only meaningful when bookType === "cc"
  includeInReporting: boolean;
  parcel: number | null;
  deleted: boolean;
  deletedReason: string | null;
  deletedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export type InsertTransaction = Omit<Transaction, "id" | "createdAt" | "updatedAt">;

// ─── Bill ────────────────────────────────────────────────
// Only ever exists for a Transaction with bookType="cc" AND
// transactionType="sale" (1:1 via transactionId).
// Removed: buyerId/buyerName/buyerGst/buyerAddress/buyerPhone/buyerEmail
//   (join through transactions.buyerId -> buyers instead)
// Removed: transportName (join through transportId -> transports)
// Removed: items (now the separate BillItem table below)
// Removed: totalAmount (equals subtotal + totalTax + roundOff, and
//   is already stored once as the linked Transaction.amount)
// Removed: parcel (belongs on Transaction, not Bill)
export interface Bill {
  id: number;
  transactionId: number;
  billNumber: string;
  billDate: string;
  dueDate: string | null;
  placeOfSupply: string | null;
  reverseCharge: boolean;
  transportId: number | null;
  subtotal: number;
  discountAmount: number;
  cgstAmount: number;
  sgstAmount: number;
  igstAmount: number;
  totalTax: number;
  roundOff: number;
  createdAt: string;
  updatedAt: string;
}

export type InsertBill = Omit<Bill, "id" | "createdAt" | "updatedAt">;

// ─── Bill Item ───────────────────────────────────────────
// Snapshots the transactional fields (qty/price/discount/tax/amount) so
// invoice history stays accurate even if the item master changes later.
export interface BillItem {
  id: number;
  billId: number;
  itemId: number;
  qty: number;
  listPrice: number;
  discountPercent: number;
  taxPercent: number;
  amount: number;
  createdAt: string;
}

export type InsertBillItem = Omit<BillItem, "id" | "createdAt">;

// ─── Company ─────────────────────────────────────────────
// terms is stored as a JSON-encoded array in the TEXT column
// (JSON.stringify on write, JSON.parse on read — see rowToObj in
// db-helpers.ts).
export interface Company {
  id: number;
  companyName: string;
  state: string | null;
  stateCode: string | null;
  address: string | null;
  phone: string | null;
  email: string | null;
  gstNumber: string | null;
  bankName: string | null;
  accountNumber: string | null;
  ifscCode: string | null;
  branchName: string | null;
  authorizedSignatory: string | null;
  terms: string[] | null;
  startingBillNumber: string | null;
  createdAt: string;
  updatedAt: string;
}

export type InsertCompany = Omit<Company, "id" | "createdAt" | "updatedAt">;

// ─── Audit Log ───────────────────────────────────────────
export interface AuditLog {
  id: number;
  tableName: string | null;
  recordId: number | null;
  action: string | null;
  oldValues: Record<string, unknown> | null;
  newValues: Record<string, unknown> | null;
  reason: string | null;
  userId: string | null;
  createdAt: string;
}

export type InsertAuditLog = Omit<AuditLog, "id" | "createdAt">;

// ============================================================
// Read-model / view types
// These mirror the SQL VIEWs in schema.ts. They're not tables you
// insert into — they're shapes returned by SELECT * FROM <view>.
// ============================================================

// Mirrors the `buyerBalances` view.
export interface BuyerBalance {
  buyerId: number;
  companyName: string;
  currentBalance: number;
}

// Mirrors the `buyerBookBalances` view.
export interface BuyerBookBalance {
  buyerId: number;
  bookType: BookType;
  bookBalance: number;
}

// Mirrors the `billDetails` view (bill + buyer + transport joined).
export interface BillDetailsView {
  billId: number;
  billNumber: string;
  billDate: string;
  dueDate: string | null;
  placeOfSupply: string | null;
  reverseCharge: boolean;
  subtotal: number;
  discountAmount: number;
  cgstAmount: number;
  sgstAmount: number;
  igstAmount: number;
  totalTax: number;
  roundOff: number;
  transactionId: number;
  transactionDate: string;
  transactionAmount: number;
  buyerId: number;
  buyerName: string;
  buyerGst: string | null;
  buyerAddress: string | null;
  buyerPhone: string | null;
  transportId: number | null;
  transportName: string | null;
  transportVehicleNumber: string | null;
}

// Mirrors the `billItemDetails` view (line item + item + category joined).
export interface BillItemDetailsView {
  billItemId: number;
  billId: number;
  qty: number;
  listPrice: number;
  discountPercent: number;
  taxPercent: number;
  amount: number;
  itemId: number;
  itemName: string;
  hsnCode: string | null;
  unit: string;
  categoryId: number | null;
  categoryName: string | null;
}

// ============================================================
// App-level composite shapes (not DB tables/views — assembled by
// your service layer for convenience, e.g. when printing an invoice)
// ============================================================

export interface BillWithItems extends Bill {
  items: BillItemDetailsView[];
  totalAmount: number; // = subtotal + totalTax + roundOff
}
