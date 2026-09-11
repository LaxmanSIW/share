import { INDIA_STATES } from "@/lib/states";
import { useState, useEffect, useRef, useCallback } from "react";
import { useSearchParams } from "react-router";
import {
  Plus,
  Pencil,
  Trash2,
  FileText,
  Shield,
  Download,
  X,
  Users,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { trpc } from "@/providers/trpc";
import { useToast } from "@/hooks/use-toast";
import { DataTable, type ColumnDef } from "@/components/ui/data-table";
import { DeleteConfirmDialog } from "@/components/ui/delete-confirm-dialog";

interface BuyerFormData {
  companyName: string;
  contactPerson: string;
  phone: string;
  gstNumber: string;
  creditLimit: number;
  address: string;
  city: string;
  state: string;
  stateCode: string;
  defaultTransportId: number | null;
  defaultTransportName: string | null;
}

const emptyForm: BuyerFormData = {
  companyName: "",
  contactPerson: "",
  phone: "",
  gstNumber: "",
  creditLimit: 100000,
  address: "",
  city: "",
  state: "",
  stateCode: "",
  defaultTransportId: null,
  defaultTransportName: null,
};

function RiskBadge({ score, level }: { score: number; level: string }) {
  const getColors = () => {
    if (score <= 3) return "bg-red-500 text-white";
    if (score <= 7) return "bg-yellow-500 text-white";
    return "bg-green-500 text-white";
  };

  return (
    <div className="flex items-center justify-center gap-1.5">
      <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-bold font-mono ${getColors()}`}>
        {score}
      </span>
      {level === "Low" && <Shield className="w-3.5 h-3.5 text-green-500" />}
    </div>
  );
}

export default function Buyers() {
  const { toast } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<BuyerFormData>({ ...emptyForm });
  const [statementOpen, setStatementOpen] = useState(false);
  const [statementBuyerId, setStatementBuyerId] = useState<number | null>(null);
  const [panelWidth, setPanelWidth] = useState(600);
  const [deleteTarget, setDeleteTarget] = useState<any | null>(null);

  const isResizing = useRef(false);
  const utils = trpc.useUtils();

  const { data: apiData, isLoading } = trpc.buyer.list.useQuery({});
  const { data: transportsData } = trpc.transport.list.useQuery();
  const transports = transportsData?.transports || [];
  const { data: riskData } = trpc.buyer.riskAnalysis.useQuery({});

  const createMutation = trpc.buyer.create.useMutation({
    onSuccess: () => {
      utils.buyer.list.invalidate();
      toast({ title: "Success", description: "Buyer created successfully" });
      setModalOpen(false);
      setForm({ ...emptyForm });
    },
    onError: (err) => toast({ title: "Error", description: err.message, variant: "destructive" }),
  });

  const updateMutation = trpc.buyer.update.useMutation({
    onSuccess: () => {
      utils.buyer.list.invalidate();
      toast({ title: "Success", description: "Buyer updated successfully" });
      setModalOpen(false);
      setEditingId(null);
      setForm({ ...emptyForm });
    },
    onError: (err) => toast({ title: "Error", description: err.message, variant: "destructive" }),
  });

  const deleteMutation = trpc.buyer.delete.useMutation({
    onSuccess: () => {
      utils.buyer.list.invalidate();
      toast({ title: "Success", description: "Buyer deleted and logged to audit trail" });
      setDeleteTarget(null);
    },
    onError: (err) => toast({ title: "Error", description: err.message, variant: "destructive" }),
  });

  const openEdit = (buyer: any) => {
    setEditingId(buyer.id);
    setForm({
      companyName: buyer.companyName,
      contactPerson: buyer.contactPerson || "",
      phone: buyer.phone || "",
      gstNumber: buyer.gstNumber || "",
      creditLimit: parseFloat(buyer.creditLimit as string) || 0,
      address: buyer.address || "",
      city: buyer.city || "",
      state: buyer.state || "",
      stateCode: buyer.stateCode || "",
      defaultTransportId: buyer.defaultTransportId || null,
      defaultTransportName: buyer.defaultTransportName || null,
    });
    setModalOpen(true);
  };

  const handleSave = () => {
    if (!form.companyName) {
      toast({ title: "Validation Error", description: "Company name is required", variant: "destructive" });
      return;
    }
    if (editingId) {
      updateMutation.mutate({ id: editingId, ...form });
    } else {
      createMutation.mutate(form);
    }
  };

  const viewStatement = (buyerId: number) => {
    setStatementBuyerId(buyerId);
    setStatementOpen(true);
  };

  // Resizable panel handlers
  const handleResizeStart = useCallback(() => {
    isResizing.current = true;
    document.body.style.cursor = "ew-resize";
    document.body.style.userSelect = "none";
  }, []);

  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      if (!isResizing.current) return;
      const newWidth = window.innerWidth - e.clientX;
      setPanelWidth(Math.max(400, Math.min(900, newWidth)));
    };
    const handleMouseUp = () => {
      isResizing.current = false;
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
    };
    document.addEventListener("mousemove", handleMouseMove);
    document.addEventListener("mouseup", handleMouseUp);
    return () => {
      document.removeEventListener("mousemove", handleMouseMove);
      document.removeEventListener("mouseup", handleMouseUp);
    };
  }, []);

  // Pre-select buyer from URL param
  useEffect(() => {
    const buyerId = searchParams.get("id");
    if (buyerId) {
      viewStatement(Number(buyerId));
      searchParams.delete("id");
      setSearchParams(searchParams);
    }
  }, [searchParams]);

  const formatCurrency = (amount: string | number) => {
    const val = typeof amount === "string" ? parseFloat(amount) : amount;
    return `₹ ${val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
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
      label: "Company Name",
      sortable: true,
      filterable: true,
      render: (row) => <span className="font-semibold text-[#1e2a4a] text-sm">{row.companyName}</span>,
    },
    {
      key: "contactPerson",
      label: "Contact Person",
      sortable: true,
      filterable: true,
      render: (row) => <span className="text-slate-600 text-xs">{row.contactPerson || "—"}</span>,
    },
    {
      key: "phone",
      label: "Phone",
      sortable: true,
      filterable: true,
      render: (row) => <span className="font-mono text-xs text-slate-600">{row.phone || "—"}</span>,
    },
    {
      key: "gstNumber",
      label: "GSTIN",
      sortable: true,
      filterable: true,
      render: (row) => <span className="font-mono text-xs text-slate-600">{row.gstNumber || "—"}</span>,
    },
    {
      key: "city",
      label: "City / State",
      sortable: true,
      filterable: true,
      render: (row) => (
        <span className="text-xs text-slate-600">
          {row.city ? `${row.city}${row.state ? `, ${row.state}` : ""}` : row.state || "—"}
        </span>
      ),
    },
    {
      key: "creditLimit",
      label: "Credit Limit",
      sortable: true,
      className: "text-right",
      headerClassName: "text-right",
      render: (row) => <span className="font-mono text-xs text-slate-700">{formatCurrency(row.creditLimit)}</span>,
    },
    {
      key: "outstanding",
      label: "Outstanding",
      sortable: true,
      className: "text-right",
      headerClassName: "text-right",
      render: (row) => (
        <span className="font-mono text-xs font-semibold text-red-600">
          {formatCurrency(row.outstanding || 0)}
        </span>
      ),
    },
    {
      key: "totalParcels",
      label: "Parcels",
      sortable: true,
      className: "text-center",
      headerClassName: "text-center",
      render: (row) => (
        <span className="font-mono text-xs font-bold text-amber-700 bg-amber-50 px-2 py-0.5 rounded-full border border-amber-200">
          {row.totalParcels || 0}
        </span>
      ),
    },
    {
      key: "riskScore",
      label: "Risk Score",
      sortable: true,
      className: "text-center",
      headerClassName: "text-center",
      render: (row) => {
        const risk = riskData?.find((r: any) => r.buyer.id === row.id);
        return risk ? <RiskBadge score={risk.riskScore} level={risk.riskLevel} /> : <span className="text-xs text-slate-400">—</span>;
      },
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
            onClick={() => viewStatement(row.id)}
            className="h-8 w-8 text-blue-600 hover:bg-blue-50"
            title="View Statement Ledger"
          >
            <FileText className="w-3.5 h-3.5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            onClick={() => openEdit(row)}
            className="h-8 w-8 text-slate-500 hover:text-[#c4703f] hover:bg-orange-50"
            title="Edit Buyer"
          >
            <Pencil className="w-3.5 h-3.5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            onClick={() => setDeleteTarget(row)}
            className="h-8 w-8 text-slate-500 hover:text-red-600 hover:bg-red-50"
            title="Delete Buyer"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6 relative">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-[#1e2a4a] flex items-center gap-2">
            <Users className="w-6 h-6 text-[#c4703f]" /> Buyers & Customer Directory
          </h1>
          <p className="text-[#6b7280] text-sm">Manage wholesaler buyers, credit limits, ledgers, and shipping details.</p>
        </div>
        <Button
          onClick={() => {
            setEditingId(null);
            setForm({ ...emptyForm });
            setModalOpen(true);
          }}
          className="bg-[#c4703f] hover:bg-[#b05e2f] text-white gap-1.5 self-end sm:self-auto"
        >
          <Plus className="w-4 h-4" /> Add Buyer
        </Button>
      </div>

      {/* Main Unified Table */}
      <DataTable
        title="Buyer Directory & Ledger Summary"
        subtitle="Full buyer accounts, current due balances, parcel counts, and risk scores"
        columns={columns}
        data={apiData?.items || []}
        loading={isLoading}
        searchPlaceholder="Search buyer, phone, GSTIN, city, state..."
      />

      {/* Create/Edit Modal */}
      {modalOpen && (
        <Dialog open={modalOpen} onOpenChange={setModalOpen}>
          <DialogContent className="max-w-lg max-h-[90vh] overflow-y-auto bg-[#fbfaf7]">
            <DialogHeader>
              <DialogTitle className="text-xl font-bold text-[#1e2a4a]">
                {editingId ? "Edit Buyer Account" : "Add New Buyer"}
              </DialogTitle>
            </DialogHeader>
            <div className="space-y-4 pt-2">
              <div className="space-y-1.5">
                <Label className="text-[#1e2a4a]">Company / Firm Name *</Label>
                <Input value={form.companyName} onChange={(e) => setForm({ ...form, companyName: e.target.value })} placeholder="e.g. Gupta Hosiery Pvt Ltd" className="bg-white border-[#dfd5c6] focus-visible:ring-[#c4703f]" />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label className="text-[#1e2a4a]">Contact Person</Label>
                  <Input value={form.contactPerson} onChange={(e) => setForm({ ...form, contactPerson: e.target.value })} placeholder="Contact name" className="bg-white border-[#dfd5c6] focus-visible:ring-[#c4703f]" />
                </div>
                <div className="space-y-1.5">
                  <Label className="text-[#1e2a4a]">Phone</Label>
                  <Input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} placeholder="Phone number" className="bg-white border-[#dfd5c6] font-mono focus-visible:ring-[#c4703f]" />
                </div>
              </div>
              <div className="space-y-1.5">
                <Label className="text-[#1e2a4a]">GST Number (GSTIN)</Label>
                <Input value={form.gstNumber} onChange={(e) => setForm({ ...form, gstNumber: e.target.value })} placeholder="15-character GSTIN" className="bg-white border-[#dfd5c6] font-mono uppercase focus-visible:ring-[#c4703f]" />
              </div>
              <div className="space-y-1.5">
                <Label className="text-[#1e2a4a]">Address</Label>
                <Input value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} placeholder="Street address / market" className="bg-white border-[#dfd5c6] focus-visible:ring-[#c4703f]" />
              </div>
              <div className="grid grid-cols-3 gap-3">
                <div className="space-y-1.5">
                  <Label className="text-[#1e2a4a]">City</Label>
                  <Input value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} placeholder="City" className="bg-white border-[#dfd5c6] focus-visible:ring-[#c4703f]" />
                </div>
                <div className="space-y-1.5">
                  <Label className="text-[#1e2a4a]">State</Label>
                  <select
                    value={form.stateCode || ""}
                    onChange={(e) => {
                      const code = e.target.value;
                      const s = INDIA_STATES.find(x => x.code === code);
                      if (s) {
                        setForm({ ...form, stateCode: s.code, state: s.name });
                      }
                    }}
                    className="flex h-9 w-full rounded-md border border-[#dfd5c6] bg-white px-3 py-1 text-sm focus:border-[#c4703f] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[#c4703f]"
                  >
                    <option value="" disabled>Select State</option>
                    {INDIA_STATES.map((s) => (
                      <option key={s.code} value={s.code}>{s.name} ({s.code})</option>
                    ))}
                  </select>
                </div>
                <div className="space-y-1.5">
                  <Label className="text-[#1e2a4a]">State Code</Label>
                  <Input value={form.stateCode} readOnly placeholder="Code" className="bg-[#f0e8dc] border-[#dfd5c6] font-mono opacity-80" />
                </div>
              </div>
              <div className="space-y-1.5">
                <Label className="text-[#1e2a4a]">Credit Limit (₹)</Label>
                <Input type="number" value={form.creditLimit || ""} onChange={(e) => setForm({ ...form, creditLimit: parseFloat(e.target.value) || 0 })} placeholder="0.00" className="bg-white border-[#dfd5c6] text-right font-mono focus-visible:ring-[#c4703f]" />
              </div>
              <div className="space-y-1.5">
                <Label className="text-[#1e2a4a]">Default Transport</Label>
                <Select
                  value={form.defaultTransportId ? String(form.defaultTransportId) : "NA"}
                  onValueChange={(val) => {
                    if (val === "NA") {
                      setForm({ ...form, defaultTransportId: null, defaultTransportName: "NA" });
                    } else {
                      const tId = Number(val);
                      const selectedTr = transports.find((t: any) => t.id === tId);
                      setForm({
                        ...form,
                        defaultTransportId: tId,
                        defaultTransportName: selectedTr ? selectedTr.name : "NA",
                      });
                    }
                  }}
                >
                  <SelectTrigger className="bg-white border-[#dfd5c6] w-full text-left">
                    <SelectValue placeholder="Select default transport" />
                  </SelectTrigger>
                  <SelectContent className="bg-white">
                    <SelectItem value="NA">NA (No Default Transport)</SelectItem>
                    {transports.map((t: any) => (
                      <SelectItem key={t.id} value={String(t.id)}>
                        {t.name} {t.vehicleNumber ? `(${t.vehicleNumber})` : ""}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="flex gap-3 pt-4 border-t border-gray-100">
                <Button variant="outline" onClick={() => { setModalOpen(false); setEditingId(null); }} className="border-[#dfd5c6]">Cancel</Button>
                <Button onClick={handleSave} disabled={createMutation.isPending || updateMutation.isPending} className="flex-1 bg-[#c4703f] hover:bg-[#b05e2f] text-white">
                  {editingId ? "Update Buyer" : "Save Buyer"}
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
        entityName="Buyer"
        itemIdentifier={deleteTarget?.companyName}
        warningMessage="Are you sure you want to delete this buyer account? Only buyers with no active transactions or open balance can be deleted."
        isPending={deleteMutation.isPending}
        onConfirm={(reason) => {
          if (deleteTarget) {
            deleteMutation.mutate({ id: deleteTarget.id, reason });
          }
        }}
      />

      {/* Statement Side Panel */}
      {statementOpen && statementBuyerId && (
        <BuyerStatementPanel
          buyerId={statementBuyerId}
          onClose={() => setStatementOpen(false)}
          width={panelWidth}
          onResizeStart={handleResizeStart}
        />
      )}
    </div>
  );
}

// ─── Buyer Statement Panel ───────────
function BuyerStatementPanel({
  buyerId,
  onClose,
  width,
  onResizeStart,
}: {
  buyerId: number;
  onClose: () => void;
  width: number;
  onResizeStart: () => void;
}) {
  const { data: statement } = trpc.buyer.statement.useQuery({ id: buyerId });

  const exportCSV = () => {
    if (!statement) return;
    const headers = ["Date", "Description", "Book", "Debit", "Credit", "Balance"];
    const rows = statement.items.map((item: any) => [
      new Date(item.date).toLocaleDateString("en-IN"),
      item.description,
      item.bookType,
      item.debit || "",
      item.credit || "",
      item.balance,
    ]);
    const csv = [headers, ...rows].join("\n");
    const blob = new Blob([csv], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `statement-${statement.buyer.companyName}-${new Date().toISOString().split("T")[0]}.csv`;
    a.click();
  };

  const exportPDF = () => {
    if (!statement) return;
    const printWindow = window.open("", "_blank");
    if (!printWindow) return;

    const totalDebit = statement.items.reduce((sum: number, item: any) => sum + item.debit, 0);
    const totalCredit = statement.items.reduce((sum: number, item: any) => sum + item.credit, 0);

    const html = `
      <!DOCTYPE html>
      <html>
      <head>
        <title>Statement - ${statement.buyer.companyName}</title>
        <style>
          body { font-family: Arial, sans-serif; margin: 20px; color: #333; }
          h1 { font-size: 18px; margin-bottom: 5px; }
          .subtitle { font-size: 12px; color: #666; margin-bottom: 15px; }
          table { width: 100%; border-collapse: collapse; font-size: 11px; }
          th, td { border: 1px solid #ddd; padding: 6px; text-align: left; }
          th { background: #f5f5f5; font-weight: bold; }
          .num { text-align: right; font-family: monospace; }
          .summary { margin-top: 15px; font-size: 12px; }
          .summary-item { display: inline-block; margin-right: 30px; }
        </style>
      </head>
      <body>
        <h1>Buyer Statement: ${statement.buyer.companyName}</h1>
        <div class="subtitle">
          ${statement.buyer.contactPerson || ""} | ${statement.buyer.phone || ""} | ${statement.buyer.gstNumber || ""}<br/>
          Generated: ${new Date().toLocaleDateString("en-IN")}
        </div>
        <table>
          <thead>
            <tr>
              <th>Date</th>
              <th>Description</th>
              <th>Book</th>
              <th class="num">Debit</th>
              <th class="num">Credit</th>
              <th class="num">Balance</th>
            </tr>
          </thead>
          <tbody>
            ${statement.items.map((item: any) => `
              <tr>
                <td>${new Date(item.date).toLocaleDateString("en-IN")}</td>
                <td>${item.description}</td>
                <td>${item.bookType}</td>
                <td class="num">${item.debit ? "₹ " + item.debit.toLocaleString("en-IN", { minimumFractionDigits: 2 }) : ""}</td>
                <td class="num">${item.credit ? "₹ " + item.credit.toLocaleString("en-IN", { minimumFractionDigits: 2 }) : ""}</td>
                <td class="num">₹ ${item.balance.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
              </tr>
            `).join("")}
          </tbody>
        </table>
        <div class="summary">
          <div class="summary-item"><strong>Total Debit:</strong> ₹ ${totalDebit.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</div>
          <div class="summary-item"><strong>Total Credit:</strong> ₹ ${totalCredit.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</div>
          <div class="summary-item"><strong>Closing Balance:</strong> ₹ ${statement.closingBalance.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</div>
        </div>
      </body>
      </html>
    `;

    printWindow.document.write(html);
    printWindow.document.close();
    setTimeout(() => {
      printWindow.print();
    }, 200);
  };

  return (
    <>
      {/* Backdrop overlay */}
      <div className="fixed inset-0 bg-black/20 z-40" onClick={onClose} />

      {/* Resizable panel */}
      <div
        className="fixed right-0 top-0 h-full bg-white shadow-2xl z-50 flex flex-col border-l border-[#d9cfc0]"
        style={{ width: `${width}px` }}
      >
        {/* Resize handle */}
        <div
          className="absolute left-0 top-0 w-2 h-full cursor-ew-resize hover:bg-[#c4703f]/20 z-10"
          onMouseDown={onResizeStart}
        />

        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-[#e8e0d4] bg-[#f5f0e8]">
          <div>
            <h2 className="text-lg font-bold text-[#1e2a4a]">{statement?.buyer?.companyName || "Loading..."}</h2>
            <p className="text-xs text-[#3d4f6f]">
              {statement?.buyer?.contactPerson} {statement?.buyer?.phone && `| ${statement.buyer.phone}`}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={exportCSV} className="border-[#d9cfc0] text-xs">
              <Download className="w-3 h-3 mr-1" />
              CSV
            </Button>
            <Button variant="outline" size="sm" onClick={exportPDF} className="border-[#d9cfc0] text-xs">
              <FileText className="w-3 h-3 mr-1" />
              PDF
            </Button>
            <button onClick={onClose} className="p-1.5 rounded hover:bg-[#e8e0d4] transition-colors">
              <X className="w-5 h-5 text-[#3d4f6f]" />
            </button>
          </div>
        </div>

        {/* Summary */}
        {statement && (
          <div className="grid grid-cols-3 gap-0 border-b border-[#e8e0d4]">
            <div className="px-4 py-3 border-r border-[#e8e0d4]">
              <p className="text-[10px] text-[#3d4f6f] uppercase">Total Debit</p>
              <p className="text-sm font-semibold font-mono text-red-600">
                ₹ {statement.items.reduce((s: number, i: any) => s + i.debit, 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
              </p>
            </div>
            <div className="px-4 py-3 border-r border-[#e8e0d4]">
              <p className="text-[10px] text-[#3d4f6f] uppercase">Total Credit</p>
              <p className="text-sm font-semibold font-mono text-green-600">
                ₹ {statement.items.reduce((s: number, i: any) => s + i.credit, 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
              </p>
            </div>
            <div className="px-4 py-3">
              <p className="text-[10px] text-[#3d4f6f] uppercase">Closing Balance</p>
              <p className="text-sm font-semibold font-mono text-[#1e2a4a]">
                ₹ {statement.closingBalance.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
              </p>
            </div>
          </div>
        )}

        {/* Statement Table */}
        <div className="flex-1 overflow-y-auto">
          {!statement ? (
            <div className="flex items-center justify-center h-32">
              <div className="h-6 w-6 border-2 border-[#c4703f] border-t-transparent rounded-full animate-spin" />
            </div>
          ) : statement.items.length === 0 ? (
            <div className="flex items-center justify-center h-32 text-sm text-[#3d4f6f]">
              No transactions found
            </div>
          ) : (
            <table className="w-full">
              <thead className="sticky top-0 bg-white z-10">
                <tr className="border-b border-[#e8e0d4] text-[10px] text-[#3d4f6f] uppercase">
                  <th className="py-2 px-3 text-left font-semibold">Date</th>
                  <th className="py-2 px-3 text-left font-semibold">Description</th>
                  <th className="py-2 px-3 text-center font-semibold">Bk</th>
                  <th className="py-2 px-3 text-right font-semibold">Debit</th>
                  <th className="py-2 px-3 text-right font-semibold">Credit</th>
                  <th className="py-2 px-3 text-right font-semibold">Balance</th>
                </tr>
              </thead>
              <tbody>
                {statement.items.map((item: any) => (
                  <tr key={item.id} className="border-b border-[#f5f0e8] hover:bg-[#f5f0e8]/50">
                    <td className="py-2 px-3 text-xs font-mono text-[#3d4f6f]">
                      {new Date(item.date).toLocaleDateString("en-IN")}
                    </td>
                    <td className="py-2 px-3 text-xs text-[#1e2a4a]">{item.description}</td>
                    <td className="py-2 px-3 text-center">
                      <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${
                        item.bookType === "CC" ? "bg-blue-100 text-blue-700" : "bg-green-100 text-green-700"
                      }`}>
                        {item.bookType}
                      </span>
                    </td>
                    <td className="py-2 px-3 text-xs text-right font-mono text-red-600">
                      {item.debit ? `₹ ${item.debit.toLocaleString("en-IN", { minimumFractionDigits: 2 })}` : ""}
                    </td>
                    <td className="py-2 px-3 text-xs text-right font-mono text-green-600">
                      {item.credit ? `₹ ${item.credit.toLocaleString("en-IN", { minimumFractionDigits: 2 })}` : ""}
                    </td>
                    <td className="py-2 px-3 text-xs text-right font-mono font-semibold">
                      ₹ {item.balance.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </>
  );
}
