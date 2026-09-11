import { useState, useEffect, useCallback } from "react";
import {
  Plus,
  Pencil,
  Trash2,
  Save,
  FileText,
  BookOpen,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { trpc } from "@/providers/trpc";
import { useToast } from "@/hooks/use-toast";
import { DataTable, type ColumnDef } from "@/components/ui/data-table";
import { DeleteConfirmDialog } from "@/components/ui/delete-confirm-dialog";

interface TransactionFormData {
  buyerId: number;
  bookType: "CC" | "CS";
  transactionType: "sale" | "payment";
  transactionDate: string;
  dueDate: string;
  totalQuantity: number;
  amount: number;
  checkNumber: string;
  includeInReporting: boolean;
  billNumber?: string;
}

const emptyForm: TransactionFormData = {
  buyerId: 0,
  bookType: "CC",
  transactionType: "sale",
  transactionDate: new Date().toLocaleDateString("en-IN", { day: "2-digit", month: "2-digit", year: "numeric" }).replace(/\//g, "/"),
  dueDate: "",
  totalQuantity: 0,
  amount: 0,
  checkNumber: "",
  includeInReporting: true,
  billNumber: undefined,
};

function SmartDateInput({
  value,
  onChange,
  placeholder,
}: {
  value: string;
  onChange: (val: string) => void;
  placeholder?: string;
}) {
  const [localValue, setLocalValue] = useState(value);

  useEffect(() => {
    setLocalValue(value);
  }, [value]);

  const handleBlur = () => {
    const parsed = parseSmartDate(localValue);
    if (parsed) {
      setLocalValue(parsed);
      onChange(parsed);
    }
  };

  return (
    <Input
      value={localValue}
      onChange={(e) => setLocalValue(e.target.value)}
      onBlur={handleBlur}
      placeholder={placeholder || "DD/MM/YYYY"}
      className="bg-white border-[#dfd5c6] focus-visible:ring-[#c4703f]"
    />
  );
}

function parseSmartDate(dateStr: string): string | null {
  if (!dateStr) return null;
  if (dateStr.includes("/") || dateStr.includes("-")) {
    const parts = dateStr.split(/[\/\-]/);
    if (parts.length === 3) {
      const day = parts[0].padStart(2, "0");
      const month = parts[1].padStart(2, "0");
      const year = parts[2].length === 2 ? `20${parts[2]}` : parts[2];
      return `${day}/${month}/${year}`;
    }
  }
  const clean = dateStr.replace(/\D/g, "");
  if (clean.length === 6 || clean.length === 8) {
    const day = clean.substring(0, 2);
    const month = clean.substring(2, 4);
    const year = clean.length === 6 ? `20${clean.substring(4, 6)}` : clean.substring(4, 8);
    return `${day}/${month}/${year}`;
  }
  return null;
}

export default function Transactions() {
  const { toast } = useToast();
  const [bookTypeFilter, setBookTypeFilter] = useState<"ALL" | "CC" | "CS">("ALL");
  const [txTypeFilter, setTxTypeFilter] = useState<"ALL" | "sale" | "payment">("ALL");
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<TransactionFormData>({ ...emptyForm });
  const [deleteTarget, setDeleteTarget] = useState<any | null>(null);

  const utils = trpc.useUtils();

  const { data: apiData, isLoading } = trpc.transaction.list.useQuery({
    bookType: bookTypeFilter as any,
    transactionType: txTypeFilter as any,
  });

  const { data: buyersList } = trpc.buyer.list.useQuery({});

  const createMutation = trpc.transaction.create.useMutation({
    onSuccess: () => {
      utils.transaction.list.invalidate();
      utils.dashboard.invalidate();
      toast({ title: "Success", description: "Transaction recorded successfully" });
      setModalOpen(false);
      setForm({ ...emptyForm });
    },
    onError: (err) => toast({ title: "Error", description: err.message, variant: "destructive" }),
  });

  const updateMutation = trpc.transaction.update.useMutation({
    onSuccess: () => {
      utils.transaction.list.invalidate();
      utils.dashboard.invalidate();
      toast({ title: "Success", description: "Transaction updated successfully" });
      setModalOpen(false);
      setEditingId(null);
      setForm({ ...emptyForm });
    },
    onError: (err) => toast({ title: "Error", description: err.message, variant: "destructive" }),
  });

  const deleteMutation = trpc.transaction.delete.useMutation({
    onSuccess: () => {
      utils.transaction.list.invalidate();
      utils.dashboard.invalidate();
      toast({ title: "Success", description: "Transaction archived successfully" });
      setDeleteTarget(null);
    },
    onError: (err) => toast({ title: "Error", description: err.message, variant: "destructive" }),
  });

  // Enable Check Number ONLY for: Payment + CC
  const isCheckNumberDisabled = !(form.transactionType === "payment" && form.bookType === "CC");
  
  // Disable Include in Reporting for: Any Payment OR Sale + CC
  const isReportingDisabled = form.transactionType === "payment" || (form.transactionType === "sale" && form.bookType === "CC");
  
  const reportingCheckboxValue = isReportingDisabled 
    ? (form.transactionType === "sale" && form.bookType === "CC") 
    : form.includeInReporting;

  const handleSave = useCallback(() => {
    if (!form.buyerId || form.amount <= 0) {
      toast({ title: "Validation Error", description: "Please select a buyer and valid amount", variant: "destructive" });
      return;
    }
    const isPayment = form.transactionType === "payment";
    const qty = isPayment ? 0 : form.totalQuantity;
    
    const checkNum = isCheckNumberDisabled ? "" : form.checkNumber;
    const includeRep = isReportingDisabled 
      ? (form.transactionType === "sale" && form.bookType === "CC")
      : form.includeInReporting;

    if (editingId) {
      updateMutation.mutate({
        id: editingId,
        buyerId: form.buyerId,
        bookType: form.bookType,
        transactionDate: form.transactionDate,
        dueDate: form.dueDate || undefined,
        amount: form.amount,
        totalQuantity: qty,
        checkNumber: checkNum || undefined,
        transactionType: form.transactionType,
        includeInReporting: includeRep,
      });
    } else {
      createMutation.mutate({
        buyerId: form.buyerId,
        bookType: form.bookType,
        transactionDate: form.transactionDate,
        dueDate: form.dueDate || undefined,
        amount: form.amount,
        totalQuantity: qty,
        checkNumber: checkNum || undefined,
        transactionType: form.transactionType,
        includeInReporting: includeRep,
      });
    }
  }, [form, editingId, createMutation, updateMutation, toast, isCheckNumberDisabled, isReportingDisabled]);

  const openEdit = (tx: any) => {
    setEditingId(tx.id);
    setForm({
      buyerId: tx.buyerId,
      bookType: tx.bookType as "CC" | "CS",
      transactionType: tx.transactionType as "sale" | "payment",
      transactionDate: tx.transactionDate ? new Date(tx.transactionDate).toLocaleDateString("en-IN") : "",
      dueDate: tx.dueDate ? new Date(tx.dueDate).toLocaleDateString("en-IN") : "",
      totalQuantity: tx.totalQuantity || 0,
      amount: parseFloat(tx.amount as string),
      checkNumber: tx.checkNumber || "",
      includeInReporting: tx.includeInReporting ?? true,
      billNumber: tx.billNumber || undefined,
    });
    setModalOpen(true);
  };

  const formatCurrency = (amount: string | number) => {
    const val = typeof amount === "string" ? parseFloat(amount) : amount;
    return `₹ ${val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  };

  const handleBookTypeChange = (bt: "CC" | "CS") => {
    const checkDisabled = !(form.transactionType === "payment" && bt === "CC");
    const repDisabled = form.transactionType === "payment" || (form.transactionType === "sale" && bt === "CC");
    setForm({ 
      ...form, 
      bookType: bt,
      checkNumber: checkDisabled ? "" : form.checkNumber,
      includeInReporting: repDisabled ? (form.transactionType === "sale" ? true : false) : form.includeInReporting
    });
  };

  const handleTxTypeChange = (tt: "sale" | "payment") => {
    const checkDisabled = !(tt === "payment" && form.bookType === "CC");
    const repDisabled = tt === "payment" || (tt === "sale" && form.bookType === "CC");
    setForm({
      ...form,
      transactionType: tt,
      totalQuantity: tt === "payment" ? 0 : form.totalQuantity,
      checkNumber: checkDisabled ? "" : form.checkNumber,
      includeInReporting: repDisabled ? (tt === "sale" ? true : false) : form.includeInReporting,
    });
  };

  const columns: ColumnDef<any>[] = [
    {
      key: "id",
      label: "ID",
      sortable: true,
      width: "60px",
      render: (row) => <span className="font-mono text-xs text-slate-500">#{row.id}</span>,
    },
    {
      key: "companyName",
      label: "Buyer / Firm",
      sortable: true,
      filterable: true,
      render: (row) => <span className="font-semibold text-[#1e2a4a] text-sm">{row.companyName || "Unknown"}</span>,
    },
    {
      key: "bookType",
      label: "Book",
      sortable: true,
      filterable: true,
      valueMode: "predefined",
      options: [
        { label: "CC Book", value: "CC" },
        { label: "CS Book", value: "CS" },
      ],
      className: "text-center",
      headerClassName: "text-center",
      render: (row) => (
        <span
          className={`inline-flex px-2 py-0.5 rounded-full text-xs font-bold ${
            row.bookType === "CC" ? "bg-blue-100 text-blue-700" : "bg-green-100 text-green-700"
          }`}
        >
          {row.bookType}
        </span>
      ),
    },
    {
      key: "transactionType",
      label: "Type",
      sortable: true,
      filterable: true,
      valueMode: "predefined",
      options: [
        { label: "Sale", value: "sale" },
        { label: "Payment", value: "payment" },
      ],
      className: "text-center",
      headerClassName: "text-center",
      render: (row) => (
        <span
          className={`inline-flex px-2 py-0.5 rounded-full text-xs font-bold ${
            row.transactionType === "sale" ? "bg-[#c4703f]/15 text-[#c4703f]" : "bg-emerald-100 text-emerald-800"
          }`}
        >
          {row.transactionType === "sale" ? "Sale" : "Payment"}
        </span>
      ),
    },
    {
      key: "transactionDate",
      label: "Tx Date",
      sortable: true,
      filterable: true,
      render: (row) => (
        <span className="font-mono text-xs text-slate-600">
          {row.transactionDate ? new Date(row.transactionDate).toLocaleDateString("en-IN") : "-"}
        </span>
      ),
    },
    {
      key: "dueDate",
      label: "Due Date",
      sortable: true,
      filterable: true,
      render: (row) => (
        <span className="font-mono text-xs text-slate-600">
          {row.dueDate ? new Date(row.dueDate).toLocaleDateString("en-IN") : "-"}
        </span>
      ),
    },
    {
      key: "totalQuantity",
      label: "Qty",
      sortable: true,
      className: "text-right font-mono",
      headerClassName: "text-right",
      render: (row) => <span className="font-mono text-xs text-slate-700">{row.totalQuantity || 0}</span>,
    },
    {
      key: "amount",
      label: "Amount",
      sortable: true,
      className: "text-right font-mono",
      headerClassName: "text-right",
      render: (row) => (
        <span
          className={`font-mono text-xs font-semibold ${
            row.transactionType === "sale" ? "text-red-600" : "text-emerald-600"
          }`}
        >
          {row.transactionType === "sale" ? "+" : "-"} {formatCurrency(row.amount)}
        </span>
      ),
    },
    {
      key: "checkNumber",
      label: "Check No.",
      sortable: true,
      filterable: true,
      render: (row) => <span className="font-mono text-xs text-slate-600">{row.checkNumber || "—"}</span>,
    },
    {
      key: "billNumber",
      label: "Bill No.",
      sortable: true,
      filterable: true,
      render: (row) => (
        row.billNumber ? (
          <span className="font-mono text-xs font-semibold text-blue-600">{row.billNumber}</span>
        ) : (
          <span className="text-xs text-slate-400">—</span>
        )
      ),
    },
    {
      key: "actions",
      label: "Actions",
      sortable: false,
      filterable: false,
      headerClassName: "text-center",
      className: "text-center",
      render: (row) => (
        <div className="flex items-center justify-center gap-1" onClick={(e) => e.stopPropagation()}>
          <Button
            variant="ghost"
            size="icon"
            onClick={() => openEdit(row)}
            className="h-8 w-8 text-slate-500 hover:text-[#c4703f] hover:bg-orange-50"
            title="Edit Transaction"
          >
            <Pencil className="w-3.5 h-3.5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            onClick={() => setDeleteTarget(row)}
            className="h-8 w-8 text-slate-500 hover:text-red-600 hover:bg-red-50"
            title="Archive Transaction"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-[#1e2a4a] flex items-center gap-2">
            <BookOpen className="w-6 h-6 text-[#c4703f]" /> Transactions & Ledger
          </h1>
          <p className="text-[#6b7280] text-sm">Alpha CC (Credit) & CS (Cash) Ledger entries.</p>
        </div>
        <Button
          onClick={() => {
            setEditingId(null);
            setForm({ ...emptyForm, transactionDate: new Date().toLocaleDateString("en-IN").replace(/\//g, "/") });
            setModalOpen(true);
          }}
          className="bg-[#c4703f] hover:bg-[#b05e2f] text-white gap-1.5 self-end sm:self-auto"
        >
          <Plus className="w-4 h-4" /> New Transaction
        </Button>
      </div>

      {/* Unified Table */}
      <DataTable
        title="Transaction Records"
        subtitle="Manage daily sales, receipts, and check payments"
        columns={columns}
        data={apiData?.items || []}
        loading={isLoading}
        defaultSortKey="id"
        defaultSortDirection="desc"
        searchPlaceholder="Search buyer, check number, bill number..."
        quickFilterSlot={
          <div className="flex items-center gap-1.5 bg-slate-200/70 p-1 rounded-lg">
            {(["ALL", "CC", "CS"] as const).map((book) => (
              <button
                key={book}
                onClick={() => setBookTypeFilter(book)}
                className={`px-3 py-1 rounded-md text-xs font-semibold transition-all ${
                  bookTypeFilter === book
                    ? "bg-[#1e2a4a] text-white shadow-xs"
                    : "text-slate-600 hover:text-slate-900"
                }`}
              >
                {book === "ALL" ? "All Books" : `${book} Book`}
              </button>
            ))}
          </div>
        }
        headerActionsSlot={
          <select
            value={txTypeFilter}
            onChange={(e) => setTxTypeFilter(e.target.value as any)}
            className="h-9 px-3 rounded-md border border-slate-300 bg-white text-xs text-slate-700 outline-none focus:border-[#c4703f]"
          >
            <option value="ALL">All Types (Sales & Payments)</option>
            <option value="sale">Sales Only</option>
            <option value="payment">Payments Received Only</option>
          </select>
        }
      />

      {/* Create/Edit Modal */}
      {modalOpen && (
        <Dialog open={modalOpen} onOpenChange={setModalOpen}>
          <DialogContent className="max-w-lg max-h-[90vh] overflow-y-auto bg-[#fbfaf7]">
            <DialogHeader>
              <DialogTitle className="text-xl font-bold text-[#1e2a4a] flex items-center gap-2">
                <FileText className="w-5 h-5 text-[#c4703f]" />
                {editingId ? "Edit Transaction Entry" : "Record New Transaction"}
              </DialogTitle>
            </DialogHeader>
            <div className="space-y-4 pt-2">
              {form.billNumber && (
                <div className="space-y-1">
                  <Label className="text-[#1e2a4a]">Linked Tax Invoice</Label>
                  <Input
                    value={form.billNumber}
                    readOnly
                    className="bg-[#f0e8dc] border-[#dfd5c6] font-mono text-blue-700 font-semibold cursor-not-allowed"
                  />
                  <p className="text-xs text-slate-500">Transaction linked to tax invoice. Financial fields are managed via Bills.</p>
                </div>
              )}

              <div className="space-y-1.5">
                <Label className="text-[#1e2a4a]">Buyer / Firm Name *</Label>
                <select
                  value={form.buyerId}
                  onChange={(e) => setForm({ ...form, buyerId: Number(e.target.value) })}
                  className="w-full h-9 px-3 rounded-md border border-[#dfd5c6] bg-white text-sm focus:border-[#c4703f] outline-none"
                  disabled={!!form.billNumber}
                >
                  <option value={0}>Select buyer...</option>
                  {buyersList?.items?.map((b: any) => (
                    <option key={b.id} value={b.id}>{b.companyName}</option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label className="text-[#1e2a4a]">Book Type *</Label>
                  <div className="flex gap-2">
                    {(["CC", "CS"] as const).map((bt) => (
                      <button
                        key={bt}
                        type="button"
                        onClick={() => handleBookTypeChange(bt)}
                        className={`flex-1 py-1.5 rounded-md text-xs font-semibold transition-all ${
                          form.bookType === bt
                            ? "bg-[#c4703f] text-white"
                            : "bg-white border border-[#dfd5c6] text-[#3d4f6f] hover:bg-slate-100"
                        }`}
                        disabled={!!form.billNumber}
                      >
                        {bt} Book
                      </button>
                    ))}
                  </div>
                </div>
                <div className="space-y-1.5">
                  <Label className="text-[#1e2a4a]">Transaction Type *</Label>
                  <div className="flex gap-2">
                    {(["sale", "payment"] as const).map((tt) => (
                      <button
                        key={tt}
                        type="button"
                        onClick={() => handleTxTypeChange(tt)}
                        className={`flex-1 py-1.5 rounded-md text-xs font-semibold transition-all ${
                          form.transactionType === tt
                            ? tt === "sale" ? "bg-red-600 text-white" : "bg-emerald-600 text-white"
                            : "bg-white border border-[#dfd5c6] text-[#3d4f6f] hover:bg-slate-100"
                        }`}
                        disabled={!!form.billNumber}
                      >
                        {tt === "sale" ? "Sale" : "Payment"}
                      </button>
                    ))}
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label className="text-[#1e2a4a]">Transaction Date *</Label>
                  <SmartDateInput
                    value={form.transactionDate}
                    onChange={(val) => setForm({ ...form, transactionDate: val })}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label className="text-[#1e2a4a]">Due Date</Label>
                  <SmartDateInput
                    value={form.dueDate}
                    onChange={(val) => setForm({ ...form, dueDate: val })}
                    placeholder="DD/MM/YYYY (optional)"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label className="text-[#1e2a4a]">Amount (₹) *</Label>
                  <Input
                    type="number"
                    value={form.amount || ""}
                    onChange={(e) => setForm({ ...form, amount: parseFloat(e.target.value) || 0 })}
                    placeholder="0.00"
                    className="bg-white border-[#dfd5c6] text-right font-mono focus-visible:ring-[#c4703f]"
                  />
                </div>
                <div className="space-y-1.5">
                  <Label className="text-[#1e2a4a]">Trouser Quantity</Label>
                  <Input
                    type="number"
                    value={form.transactionType === "payment" ? "0" : (form.totalQuantity || "")}
                    onChange={(e) => setForm({ ...form, totalQuantity: parseInt(e.target.value) || 0 })}
                    placeholder="0"
                    disabled={form.transactionType === "payment"}
                    className="bg-white border-[#dfd5c6] text-right font-mono disabled:opacity-50 focus-visible:ring-[#c4703f]"
                  />
                </div>
              </div>

              <div className="space-y-1.5">
                <Label className={`text-[#1e2a4a] ${isCheckNumberDisabled ? "opacity-50" : ""}`}>Check Number</Label>
                <Input
                  value={isCheckNumberDisabled ? "" : form.checkNumber}
                  onChange={(e) => setForm({ ...form, checkNumber: e.target.value })}
                  placeholder={isCheckNumberDisabled ? "Not applicable" : "Enter Check Number"}
                  className="bg-white border-[#dfd5c6] font-mono disabled:opacity-50 focus-visible:ring-[#c4703f]"
                  disabled={isCheckNumberDisabled}
                />
              </div>

              <div className="flex items-center gap-2 pt-1">
                <input
                  type="checkbox"
                  id="includeInReporting"
                  checked={reportingCheckboxValue}
                  disabled={isReportingDisabled}
                  onChange={(e) => setForm({ ...form, includeInReporting: e.target.checked })}
                  className="w-4 h-4 rounded border-[#dfd5c6] text-[#c4703f] focus:ring-[#c4703f]"
                />
                <Label htmlFor="includeInReporting" className={`text-xs text-slate-700 cursor-pointer ${isReportingDisabled ? "opacity-50" : ""}`}>
                  Include in sales matrix reporting
                </Label>
              </div>

              <div className="flex justify-end gap-3 pt-4 border-t border-gray-100">
                <Button variant="outline" onClick={() => { setModalOpen(false); setEditingId(null); }} className="border-[#dfd5c6]">
                  Cancel
                </Button>
                <Button
                  onClick={handleSave}
                  disabled={createMutation.isPending || updateMutation.isPending}
                  className="bg-[#c4703f] hover:bg-[#b05e2f] text-white gap-1.5"
                >
                  <Save className="w-4 h-4" />
                  {editingId ? "Update Entry" : "Save Entry"}
                </Button>
              </div>
            </div>
          </DialogContent>
        </Dialog>
      )}

      {/* Delete Confirmation Dialog */}
      <DeleteConfirmDialog
        open={!!deleteTarget}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        entityName="Transaction"
        itemIdentifier={`#${deleteTarget?.id} (${deleteTarget?.companyName})`}
        warningMessage="This will soft-delete/archive the transaction from active ledgers. A detailed log will be recorded in audit history."
        isPending={deleteMutation.isPending}
        onConfirm={(reason) => {
          if (deleteTarget) {
            deleteMutation.mutate({ id: deleteTarget.id, reason });
          }
        }}
      />
    </div>
  );
}