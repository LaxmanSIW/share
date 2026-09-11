import { useState, useEffect } from "react";
import {
  Plus,
  Pencil,
  Trash2,
  FileText,
  Printer,
  ChevronLeft,
  ArrowLeft,
} from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { trpc } from "@/providers/trpc";
import { useToast } from "@/hooks/use-toast";
import { DataTable, type ColumnDef } from "@/components/ui/data-table";
import { DeleteConfirmDialog } from "@/components/ui/delete-confirm-dialog";

// Helper to convert number to Indian currency words
function numberToWords(num: number): string {
  const a = [
    "",
    "One",
    "Two",
    "Three",
    "Four",
    "Five",
    "Six",
    "Seven",
    "Eight",
    "Nine",
    "Ten",
    "Eleven",
    "Twelve",
    "Thirteen",
    "Fourteen",
    "Fifteen",
    "Sixteen",
    "Seventeen",
    "Eighteen",
    "Nineteen",
  ];
  const b = [
    "",
    "",
    "Twenty",
    "Thirty",
    "Forty",
    "Fifty",
    "Sixty",
    "Seventy",
    "Eighty",
    "Ninety",
  ];

  function g(n: number): string {
    if (n < 20) return a[n];
    const digit = n % 10;
    return b[Math.floor(n / 10)] + (digit ? " " + a[digit] : "");
  }

  function h(n: number): string {
    if (n < 100) return g(n);
    const hundred = Math.floor(n / 100);
    const rest = n % 100;
    return a[hundred] + " Hundred" + (rest ? " " + g(rest) : "");
  }

  const amt = Math.floor(num);
  if (amt === 0) return "Zero Rupees Only";

  let str = "";
  let temp = amt;

  if (Math.floor(temp / 10000000) > 0) {
    str += h(Math.floor(temp / 10000000)) + " Crore ";
    temp %= 10000000;
  }
  if (Math.floor(temp / 100000) > 0) {
    str += h(Math.floor(temp / 100000)) + " Lakh ";
    temp %= 100000;
  }
  if (Math.floor(temp / 1000) > 0) {
    str += h(Math.floor(temp / 1000)) + " Thousand ";
    temp %= 1000;
  }
  if (temp > 0) {
    str += h(temp);
  }

  return "Rs. " + str.trim() + " Only";
}

interface BillItemInput {
  itemId: number;
  qty: number;
  discountPercent: number;
  listPrice?: number;
}

interface BillFormData {
  buyerId: number;
  billDate: string;
  dueDate: string | null;
  placeOfSupply: string;
  reverseCharge: "Yes" | "No";
  items: BillItemInput[];
  roundOff: number;
  transportId: number | null;
  parcel: number;
}

const emptyForm = (): BillFormData => {
  const bd = new Date();
  const billDate = bd.toISOString().split("T")[0];
  bd.setMonth(bd.getMonth() + 3);
  const dueDate = bd.toISOString().split("T")[0];
  return {
    buyerId: 0,
    billDate,
    dueDate,
    placeOfSupply: "",
    reverseCharge: "No",
    items: [],
    roundOff: 0,
    transportId: null,
    parcel: 1,
  };
};

function checkIsInterState(pos: string, company: any) {
  if (!pos || !company || !company.state || !company.stateCode) return false;
  const p = pos.toLowerCase();
  return (
    !p.includes(company.state.toLowerCase()) &&
    !p.includes(company.stateCode.toLowerCase())
  );
}

export default function Bills() {
  const { toast } = useToast();
  const [viewMode, setViewMode] = useState<"list" | "form" | "print">("list");
  const [selectedBillId, setSelectedBillId] = useState<number | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<BillFormData>(emptyForm());

  // Nested item creation state
  const [newItemModalOpen, setNewItemModalOpen] = useState(false);
  const [newItemForm, setNewItemForm] = useState({
    name: "",
    hsnCode: "",
    listPrice: 0,
    unit: "Pcs.",
    taxPercent: 18,
  });

  const utils = trpc.useUtils();
  const { data: billsData, isLoading: billsLoading } =
    trpc.bill.list.useQuery();
  const { data: buyersData } = trpc.buyer.list.useQuery();
  const { data: itemsData } = trpc.item.list.useQuery();
  const { data: companyData } = trpc.settings.getCompany.useQuery();
  const { data: transportsData } = trpc.transport.list.useQuery();
  const transports = transportsData?.transports || [];

  const { data: nextBillData } = trpc.bill.getNextBillNumber.useQuery(
    undefined,
    { enabled: viewMode === "form" && !editingId }
  );

  const createMutation = trpc.bill.create.useMutation({
    onSuccess: (res) => {
      utils.bill.list.invalidate();
      toast({ title: "Success", description: "Invoice created successfully" });
      if (res && res.id) {
        setSelectedBillId(res.id);
        setViewMode("print");
        setTimeout(() => {
          window.focus();
          window.print();
        }, 600);
      } else {
        setViewMode("list");
      }
      setForm(emptyForm());
    },
    onError: (err) =>
      toast({
        title: "Error",
        description: err.message,
        variant: "destructive",
      }),
  });

  const updateMutation = trpc.bill.update.useMutation({
    onSuccess: (res) => {
      utils.bill.list.invalidate();
      toast({ title: "Success", description: "Invoice updated successfully" });
      if (res && res.id) {
        setSelectedBillId(res.id);
        setViewMode("print");
        setTimeout(() => {
          window.focus();
          window.print();
        }, 600);
      } else {
        setViewMode("list");
      }
      setEditingId(null);
      setForm(emptyForm());
    },
    onError: (err) =>
      toast({
        title: "Error",
        description: err.message,
        variant: "destructive",
      }),
  });

  const [deleteTarget, setDeleteTarget] = useState<any | null>(null);

  const deleteMutation = trpc.bill.delete.useMutation({
    onSuccess: () => {
      utils.bill.list.invalidate();
      toast({
        title: "Success",
        description: "Invoice deleted and logged to audit trail",
      });
      setDeleteTarget(null);
    },
    onError: (err) =>
      toast({
        title: "Error",
        description: err.message,
        variant: "destructive",
      }),
  });

  const createItemMutation = trpc.item.create.useMutation({
    onSuccess: (res) => {
      utils.item.list.invalidate();
      toast({ title: "Success", description: "New item added to catalog" });
      setNewItemModalOpen(false);
      if (res.item) {
        handleAddLineItem(res.item.id, Number(res.item.listPrice) || 0);
      }
      setNewItemForm({
        name: "",
        hsnCode: "",
        listPrice: 0,
        unit: "Pcs.",
        taxPercent: 18,
      });
    },
    onError: (err) =>
      toast({
        title: "Error",
        description: err.message,
        variant: "destructive",
      }),
  });

  const handleCreateNewItem = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newItemForm.name.trim() || !newItemForm.hsnCode.trim()) {
      toast({
        title: "Error",
        description: "All fields are required",
        variant: "destructive",
      });
      return;
    }
    createItemMutation.mutate(newItemForm);
  };

  const openCreate = () => {
    setEditingId(null);
    const init = emptyForm();
    if (companyData && (companyData as any).state) {
      init.placeOfSupply = (companyData as any).state;
    }
    const lastBuyerId = localStorage.getItem("lastBuyerId");
    if (lastBuyerId) {
      init.buyerId = parseInt(lastBuyerId, 10);
    }
    const bd = new Date(init.billDate);
    bd.setMonth(bd.getMonth() + 3);
    init.dueDate = bd.toISOString().split("T")[0];
    setForm(init);
    setViewMode("form");
  };

  const openEdit = (bill: any) => {
    setEditingId(bill.id);
    setForm({
      buyerId: bill.buyerId,
      billDate: bill.billDate,
      dueDate: bill.dueDate,
      placeOfSupply: bill.placeOfSupply,
      reverseCharge: bill.reverseCharge,
      items: bill.items.map((it: any) => ({
        itemId: it.itemId,
        qty: it.qty,
        discountPercent: parseFloat(it.discountPercent) || 0,
        listPrice: parseFloat(it.listPrice) || 0,
      })),
      roundOff: parseFloat(bill.roundOff) || 0,
      transportId: bill.transportId || null,
      parcel: bill.parcel !== undefined ? (bill.parcel ?? 1) : 1,
    });
    setViewMode("form");
  };

  const openPrint = (billId: number) => {
    setSelectedBillId(billId);
    setViewMode("print");
  };

  const handleAddLineItem = (itemId: number, customPrice?: number) => {
    if (form.items.some((line) => line.itemId === itemId)) {
      toast({
        title: "Duplicate Line",
        description: "This item is already in the invoice list.",
      });
      return;
    }
    const catItem = itemsData?.items?.find((it: any) => it.id === itemId);
    const price =
      customPrice !== undefined
        ? customPrice
        : catItem
          ? parseFloat(catItem.listPrice) || 0
          : 0;
    setForm((prev) => ({
      ...prev,
      items: [
        ...prev.items,
        { itemId, qty: 1, discountPercent: 0, listPrice: price },
      ],
    }));
  };

  const handleUpdateLineItem = (
    index: number,
    key: keyof BillItemInput,
    value: number
  ) => {
    const updated = [...form.items];
    updated[index] = { ...updated[index], [key]: value };
    setForm((prev) => ({ ...prev, items: updated }));
  };

  const handleRemoveLineItem = (index: number) => {
    setForm((prev) => ({
      ...prev,
      items: prev.items.filter((_, i) => i !== index),
    }));
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    if (form.buyerId === 0) {
      toast({
        title: "Validation Error",
        description: "Please select a buyer",
        variant: "destructive",
      });
      return;
    }
    if (form.items.length === 0) {
      toast({
        title: "Validation Error",
        description: "Please add at least one line item",
        variant: "destructive",
      });
      return;
    }

    if (editingId) {
      updateMutation.mutate({ id: editingId, ...form });
    } else {
      localStorage.setItem("lastBuyerId", form.buyerId.toString());
      createMutation.mutate(form);
    }
  };

  // Dynamic calculations
  const selectedBuyer = buyersData?.items?.find(
    (b: any) => b.id === form.buyerId
  );
  const calculatedStats = () => {
    let subtotal = 0;
    let totalDiscount = 0;
    let totalTax = 0;
    let cgstTotal = 0;
    let sgstTotal = 0;
    let igstTotal = 0;

    const isInterState = checkIsInterState(form.placeOfSupply, companyData);

    form.items.forEach((line) => {
      const item = itemsData?.items?.find((it: any) => it.id === line.itemId);
      if (item) {
        const price =
          line.listPrice !== undefined
            ? line.listPrice
            : parseFloat(item.listPrice) || 0;
        const gross = price * line.qty;
        const disc = gross * (line.discountPercent / 100);
        const taxable = gross - disc;
        const tax = taxable * ((parseFloat(item.taxPercent) || 0) / 100);

        subtotal += gross;
        totalDiscount += disc;
        totalTax += tax;

        if (isInterState) {
          igstTotal += tax;
        } else {
          cgstTotal += tax / 2;
          sgstTotal += tax / 2;
        }
      }
    });

    const netSubtotal = subtotal - totalDiscount;
    const rawTotal = netSubtotal + totalTax;
    const rounded = Math.round(rawTotal);
    const autoRoundOff = rounded - rawTotal;

    return {
      subtotal,
      totalDiscount,
      netSubtotal,
      cgstTotal,
      sgstTotal,
      igstTotal,
      totalTax,
      rawTotal,
      autoRoundOff,
      totalAmount: rounded,
    };
  };

  const stats = calculatedStats();
  const activePrintBill = billsData?.bills?.find(
    (b: any) => b.id === selectedBillId
  );

  const triggerNativePrint = () => {
    window.print();
  };

  useEffect(() => {
    if (selectedBuyer) {
      setForm((prev) => ({
        ...prev,
        placeOfSupply: selectedBuyer.state || (companyData as any)?.state || "",
        transportId:
          selectedBuyer.defaultTransportId || prev.transportId || null,
      }));
    }
  }, [form.buyerId, selectedBuyer]);

  // ---------- FORM VIEW ----------
  if (viewMode === "form") {
    return (
      <div className="space-y-6">
        {/* Header */}
        <div className="flex items-center gap-3">
          <Button
            variant="ghost"
            size="icon"
            onClick={() => setViewMode("list")}
            className="h-9 w-9 border border-[#dfd5c6] text-[#1e2a4a] hover:bg-gray-100"
          >
            <ArrowLeft className="w-4 h-4" />
          </Button>
          <div>
            <h1 className="text-2xl font-bold text-[#1e2a4a]">
              {editingId ? (
                "Edit Tax Invoice"
              ) : (
                <>
                  Create Tax Invoice{" "}
                  {nextBillData?.nextBillNumber && (
                    <span className="text-[#c4703f] ml-2 font-mono text-xl">
                      #{nextBillData.nextBillNumber}
                    </span>
                  )}
                </>
              )}
            </h1>
            <p className="text-[#6b7280] text-sm">
              Add company details, buyer info, catalog products, and taxes.
            </p>
          </div>
        </div>

        <form onSubmit={handleSave} className="space-y-6">
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Left side: Invoice Meta & Buyer */}
            <div className="lg:col-span-2 space-y-6">
              {/* Card 1: Header details */}
              <Card className="border-none shadow-sm bg-white p-5 rounded-xl space-y-4">
                <h3 className="font-semibold text-sm uppercase text-[#c4703f] tracking-wider border-b border-gray-100 pb-2">
                  Invoice Specifications
                </h3>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="space-y-1.5">
                    <Label className="text-[#1e2a4a]">
                      Select Billing Buyer
                    </Label>
                    <Select
                      value={String(form.buyerId)}
                      onValueChange={(val) =>
                        setForm((prev) => ({ ...prev, buyerId: parseInt(val) }))
                      }
                    >
                      <SelectTrigger className="bg-white border-[#dfd5c6]">
                        <SelectValue placeholder="Choose Buyer..." />
                      </SelectTrigger>
                      <SelectContent className="bg-white">
                        {buyersData?.items?.map((b: any) => (
                          <SelectItem key={b.id} value={String(b.id)}>
                            {b.companyName}{" "}
                            {b.gstNumber ? `(${b.gstNumber})` : ""}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>

                  <div className="space-y-1.5">
                    <Label className="text-[#1e2a4a]">Place of Supply</Label>
                    <Input
                      value={form.placeOfSupply}
                      onChange={(e) =>
                        setForm((prev) => ({
                          ...prev,
                          placeOfSupply: e.target.value,
                        }))
                      }
                      placeholder="e.g. Uttar Pradesh"
                      className="bg-white border-[#dfd5c6]"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="space-y-1.5">
                    <Label className="text-[#1e2a4a]">Invoice Date</Label>
                    <Input
                      type="date"
                      value={form.billDate}
                      onChange={(e) =>
                        setForm((prev) => ({
                          ...prev,
                          billDate: e.target.value,
                        }))
                      }
                      className="bg-white border-[#dfd5c6]"
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label className="text-[#1e2a4a]">Due Date</Label>
                    <Input
                      type="date"
                      value={form.dueDate || ""}
                      onChange={(e) =>
                        setForm((prev) => ({
                          ...prev,
                          dueDate: e.target.value || null,
                        }))
                      }
                      className="bg-white border-[#dfd5c6]"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 border-t border-gray-100 pt-3">
                  <div className="space-y-1.5">
                    <Label className="text-[#1e2a4a]">Transport Partner</Label>
                    <Select
                      value={form.transportId ? String(form.transportId) : "NA"}
                      onValueChange={(val) =>
                        setForm((prev) => ({
                          ...prev,
                          transportId: val === "NA" ? null : parseInt(val),
                        }))
                      }
                    >
                      <SelectTrigger className="bg-white border-[#dfd5c6]">
                        <SelectValue placeholder="Choose Transport..." />
                      </SelectTrigger>
                      <SelectContent className="bg-white">
                        <SelectItem value="NA">
                          NA (No Transport / Use Default)
                        </SelectItem>
                        {transports.map((t: any) => (
                          <SelectItem key={t.id} value={String(t.id)}>
                            {t.name}{" "}
                            {t.vehicleNumber ? `(${t.vehicleNumber})` : ""}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>

                  <div className="space-y-1.5">
                    <Label className="text-[#1e2a4a]">Parcel Quantity</Label>
                    <Input
                      type="number"
                      min="0"
                      value={form.parcel}
                      onChange={(e) =>
                        setForm((prev) => ({
                          ...prev,
                          parcel: parseInt(e.target.value) || 0,
                        }))
                      }
                      className="bg-white border-[#dfd5c6]"
                    />
                  </div>
                </div>
              </Card>

              {/* Card 2: Invoice Line Items */}
              <Card className="border-none shadow-sm bg-white p-5 rounded-xl space-y-4">
                <div className="flex justify-between items-center border-b border-gray-100 pb-2">
                  <h3 className="font-semibold text-sm uppercase text-[#c4703f] tracking-wider">
                    Line Item Details
                  </h3>
                  <div className="flex gap-2">
                    {/* Select Item Trigger Dropdown */}
                    <Select
                      onValueChange={(val) => handleAddLineItem(parseInt(val))}
                    >
                      <SelectTrigger className="w-[180px] h-8 text-xs bg-[#fbfaf7] border-[#dfd5c6]">
                        <SelectValue placeholder="Add Line Item..." />
                      </SelectTrigger>
                      <SelectContent className="bg-white text-xs">
                        {itemsData?.items?.map((it: any) => (
                          <SelectItem key={it.id} value={String(it.id)}>
                            {it.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>

                    {/* Quick Add New Catalog Item button */}
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => setNewItemModalOpen(true)}
                      className="h-8 text-xs border-[#dfd5c6] text-[#c4703f] hover:bg-orange-50"
                    >
                      + Custom Item
                    </Button>
                  </div>
                </div>

                {form.items.length === 0 ? (
                  <div className="text-center py-10 text-gray-400 text-sm">
                    No line items added yet. Click "Add Line Item" above to
                    populate products.
                  </div>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs">
                      <thead>
                        <tr className="border-b border-gray-200 text-[#1e2a4a] pb-2 font-semibold">
                          <th className="py-2 pr-2">Sr.</th>
                          <th className="py-2">Item Description</th>
                          <th className="py-2 font-mono">HSN/SAC</th>
                          <th className="py-2 text-right">Qty</th>
                          <th className="py-2 pl-3">Unit</th>
                          <th className="py-2 text-right">List Price</th>
                          <th className="py-2 text-right">Disc %</th>
                          <th className="py-2 text-right">GST %</th>
                          <th className="py-2 text-right">Total (₹)</th>
                          <th className="py-2 text-center">Action</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-100">
                        {form.items.map((line, idx) => {
                          const catItem = itemsData?.items?.find(
                            (it: any) => it.id === line.itemId
                          );
                          if (!catItem) return null;

                          const price =
                            line.listPrice !== undefined
                              ? line.listPrice
                              : parseFloat(catItem.listPrice) || 0;
                          const gross = price * line.qty;
                          const disc = gross * (line.discountPercent / 100);
                          const taxRate = parseFloat(catItem.taxPercent) || 0;
                          const taxable = gross - disc;
                          const lineTax = taxable * (taxRate / 100);
                          const total = taxable + lineTax;

                          return (
                            <tr key={idx} className="hover:bg-gray-50/50">
                              <td className="py-3 pr-2 text-gray-500">
                                {idx + 1}
                              </td>
                              <td className="py-3 font-medium text-[#1e2a4a]">
                                {catItem.name}
                              </td>
                              <td className="py-3 font-mono text-gray-500">
                                {catItem.hsnCode}
                              </td>
                              <td className="py-3 text-right">
                                <Input
                                  type="number"
                                  min="1"
                                  value={line.qty}
                                  onChange={(e) =>
                                    handleUpdateLineItem(
                                      idx,
                                      "qty",
                                      parseInt(e.target.value) || 1
                                    )
                                  }
                                  className="w-16 h-7 text-right p-1 text-xs border-[#dfd5c6] bg-white font-mono"
                                />
                              </td>
                              <td className="py-3 pl-3 text-gray-500">
                                {catItem.unit}
                              </td>
                              <td className="py-3 text-right font-mono">
                                <div className="flex items-center justify-end gap-1">
                                  <span className="text-gray-400 text-[10px]">
                                    ₹
                                  </span>
                                  <Input
                                    type="number"
                                    step="0.01"
                                    min="0"
                                    value={
                                      line.listPrice !== undefined
                                        ? line.listPrice
                                        : price
                                    }
                                    onChange={(e) =>
                                      handleUpdateLineItem(
                                        idx,
                                        "listPrice",
                                        parseFloat(e.target.value) || 0
                                      )
                                    }
                                    className="w-20 h-7 text-right p-1 text-xs border-[#dfd5c6] bg-white font-mono"
                                  />
                                </div>
                              </td>
                              <td className="py-3 text-right">
                                <Input
                                  type="number"
                                  min="0"
                                  max="100"
                                  value={line.discountPercent}
                                  onChange={(e) =>
                                    handleUpdateLineItem(
                                      idx,
                                      "discountPercent",
                                      parseFloat(e.target.value) || 0
                                    )
                                  }
                                  className="w-16 h-7 text-right p-1 text-xs border-[#dfd5c6] bg-white font-mono"
                                />
                              </td>
                              <td className="py-3 text-right font-mono text-gray-500">
                                {catItem.taxPercent}%
                              </td>
                              <td className="py-3 text-right font-semibold font-mono text-[#1e2a4a]">
                                ₹
                                {total.toLocaleString("en-IN", {
                                  minimumFractionDigits: 2,
                                  maximumFractionDigits: 2,
                                })}
                              </td>
                              <td className="py-3 text-center">
                                <Button
                                  type="button"
                                  variant="ghost"
                                  size="icon"
                                  onClick={() => handleRemoveLineItem(idx)}
                                  className="h-6 w-6 text-gray-400 hover:text-red-500"
                                >
                                  <Trash2 className="w-3.5 h-3.5" />
                                </Button>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </Card>
            </div>

            {/* Right side: Calculations / Totals panel */}
            <div className="space-y-6">
              <Card className="border-none shadow-sm bg-white p-5 rounded-xl space-y-4">
                <h3 className="font-semibold text-sm uppercase text-[#1e2a4a] tracking-wider border-b border-gray-100 pb-2">
                  Invoice Financial Summary
                </h3>

                <div className="space-y-3.5 text-xs text-gray-600">
                  <div className="flex justify-between">
                    <span>Gross Subtotal:</span>
                    <span className="font-semibold font-mono text-[#1e2a4a]">
                      ₹{stats.subtotal.toFixed(2)}
                    </span>
                  </div>
                  <div className="flex justify-between text-red-600">
                    <span>Discount Deducted:</span>
                    <span className="font-semibold font-mono">
                      -₹{stats.totalDiscount.toFixed(2)}
                    </span>
                  </div>
                  <div className="border-t border-dashed border-gray-100 my-1"></div>
                  <div className="flex justify-between">
                    <span>Net Taxable Amount:</span>
                    <span className="font-semibold font-mono text-[#1e2a4a]">
                      ₹{stats.netSubtotal.toFixed(2)}
                    </span>
                  </div>

                  {stats.cgstTotal > 0 && (
                    <div className="flex justify-between text-gray-500 pl-3">
                      <span>CGST Total:</span>
                      <span className="font-mono">
                        ₹{stats.cgstTotal.toFixed(2)}
                      </span>
                    </div>
                  )}
                  {stats.sgstTotal > 0 && (
                    <div className="flex justify-between text-gray-500 pl-3">
                      <span>SGST Total:</span>
                      <span className="font-mono">
                        ₹{stats.sgstTotal.toFixed(2)}
                      </span>
                    </div>
                  )}
                  {stats.igstTotal > 0 && (
                    <div className="flex justify-between text-gray-500 pl-3">
                      <span>IGST Total:</span>
                      <span className="font-mono text-orange-600">
                        ₹{stats.igstTotal.toFixed(2)}
                      </span>
                    </div>
                  )}

                  <div className="flex justify-between">
                    <span>Total Tax (GST):</span>
                    <span className="font-semibold font-mono text-[#1e2a4a]">
                      ₹{stats.totalTax.toFixed(2)}
                    </span>
                  </div>

                  <div className="flex justify-between items-center">
                    <span>Manual / Auto Roundoff:</span>
                    <span className="font-mono text-gray-500 font-medium">
                      ₹{stats.autoRoundOff.toFixed(2)}
                    </span>
                  </div>

                  <div className="border-t border-gray-200 pt-3 flex justify-between items-center text-sm font-bold text-[#1e2a4a]">
                    <span>Grand Total Due:</span>
                    <span className="text-[#c4703f] font-mono text-base">
                      ₹
                      {stats.totalAmount.toLocaleString("en-IN", {
                        minimumFractionDigits: 2,
                        maximumFractionDigits: 2,
                      })}
                    </span>
                  </div>

                  <div className="text-[10px] text-gray-400 text-center leading-relaxed font-serif italic pt-2">
                    "{numberToWords(stats.totalAmount)}"
                  </div>
                </div>

                <div className="pt-4 border-t border-gray-100 flex flex-col gap-2">
                  <Button
                    type="submit"
                    className="w-full bg-[#c4703f] hover:bg-[#b05e2f] text-white font-semibold"
                    disabled={
                      createMutation.isPending || updateMutation.isPending
                    }
                  >
                    {createMutation.isPending || updateMutation.isPending
                      ? "Generating Invoice..."
                      : editingId
                        ? "Update Invoice"
                        : "Generate Invoice"}
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setViewMode("list")}
                    className="w-full border-[#dfd5c6] hover:bg-gray-100 text-[#1e2a4a]"
                  >
                    Cancel / Go Back
                  </Button>
                </div>
              </Card>
            </div>
          </div>
        </form>

        {/* Nested Add Item Dialog */}
        <Dialog open={newItemModalOpen} onOpenChange={setNewItemModalOpen}>
          <DialogContent className="sm:max-w-md bg-[#fbfaf7]">
            <DialogHeader>
              <DialogTitle className="text-[#1e2a4a] font-bold">
                Quick Catalog Addition
              </DialogTitle>
            </DialogHeader>
            <form onSubmit={handleCreateNewItem} className="space-y-4 pt-2">
              <div className="space-y-1">
                <Label htmlFor="nested-name">Item Name / Description</Label>
                <Input
                  id="nested-name"
                  value={newItemForm.name}
                  onChange={(e) =>
                    setNewItemForm((prev) => ({
                      ...prev,
                      name: e.target.value,
                    }))
                  }
                  placeholder="e.g. Silk Shirt"
                  required
                  className="bg-white border-[#dfd5c6]"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <Label htmlFor="nested-hsn">HSN/SAC Code</Label>
                  <Input
                    id="nested-hsn"
                    value={newItemForm.hsnCode}
                    onChange={(e) =>
                      setNewItemForm((prev) => ({
                        ...prev,
                        hsnCode: e.target.value,
                      }))
                    }
                    placeholder="39231020"
                    required
                    className="bg-white border-[#dfd5c6]"
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="nested-unit">Unit</Label>
                  <Select
                    value={newItemForm.unit}
                    onValueChange={(val) =>
                      setNewItemForm((prev) => ({ ...prev, unit: val }))
                    }
                  >
                    <SelectTrigger className="bg-white border-[#dfd5c6]">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent className="bg-white">
                      <SelectItem value="Pcs.">Pcs.</SelectItem>
                      <SelectItem value="Mtrs">Mtrs</SelectItem>
                      <SelectItem value="Kg">Kg</SelectItem>
                      <SelectItem value="Box">Box</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <Label htmlFor="nested-price">List Price (₹)</Label>
                  <Input
                    id="nested-price"
                    type="number"
                    step="0.01"
                    value={newItemForm.listPrice}
                    onChange={(e) =>
                      setNewItemForm((prev) => ({
                        ...prev,
                        listPrice: parseFloat(e.target.value) || 0,
                      }))
                    }
                    required
                    className="bg-white border-[#dfd5c6]"
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="nested-tax">GST Rate (%)</Label>
                  <Select
                    value={String(newItemForm.taxPercent)}
                    onValueChange={(val) =>
                      setNewItemForm((prev) => ({
                        ...prev,
                        taxPercent: parseFloat(val),
                      }))
                    }
                  >
                    <SelectTrigger className="bg-white border-[#dfd5c6]">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent className="bg-white">
                      <SelectItem value="0">0%</SelectItem>
                      <SelectItem value="5">5%</SelectItem>
                      <SelectItem value="12">12%</SelectItem>
                      <SelectItem value="18">18%</SelectItem>
                      <SelectItem value="28">28%</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>

              <div className="flex justify-end gap-2 pt-4 border-t border-gray-100">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setNewItemModalOpen(false)}
                  className="border-[#dfd5c6]"
                >
                  Cancel
                </Button>
                <Button
                  type="submit"
                  className="bg-[#c4703f] hover:bg-[#b05e2f] text-white"
                  disabled={createItemMutation.isPending}
                >
                  {createItemMutation.isPending
                    ? "Adding..."
                    : "Add to Catalog & Invoice"}
                </Button>
              </div>
            </form>
          </DialogContent>
        </Dialog>
      </div>
    );
  }

  // ---------- PRINT VIEW (with updated layout matching screenshot) ----------
  if (viewMode === "print" && activePrintBill) {
    const totalWords = numberToWords(
      parseFloat(activePrintBill.totalAmount || "0")
    );

    // Limit print items to max 7 items to strictly fit on 1 page
    const printItems = (activePrintBill.items || []).slice(0, 7);

    const totQty = printItems.reduce(
      (sum: number, it: any) => sum + (parseFloat(it.qty || "0") || 0),
      0
    );

    const finalComp = {
      companyName: companyData?.companyName || "leelamani tradelink",
      brandName: (companyData as any)?.brandName || "KAPTON",
      tagline:
        (companyData as any)?.tagline ||
        "Mfg. OF ALL TYPES JEANS & COTTON",
      address:
        companyData?.address ||
        "F-6, SHREE PADMAVATI COMPLEX GHEEKANTA ROAD,AHMEDABAD-1 (GUJ.)",
      phone: companyData?.phone || "9426549684 , 9409146255",
      email: companyData?.email || "company@gmail.com",
      gstNumber: companyData?.gstNumber || "24CAMPD5245A1Z7",
      bankName: companyData?.bankName || "KOTAK MAHINDRA BANK",
      accountNumber: companyData?.accountNumber || "0412953444",
      ifscCode: companyData?.ifscCode || "KKBK0002569",
      branchName: companyData?.branchName || "Ahmedabad",
      authorizedSignatory:
        companyData?.authorizedSignatory || "Authorised Signature",
      city: (companyData as any)?.city || "ahmedabad",
      stateCode: (companyData as any)?.stateCode || "24",
      logoUrl: (companyData as any)?.logoUrl || "",
      arbitrationRules:
        (companyData as any)?.arbitrationRules ||
        "THIS SALE IS SUBJECT TO THE SALE DISPUTE AND ARBITRATION RULES OF THE GUJARAT GARMENT MANUFACTURERS ASSOCIATION.",
      terms: companyData?.terms || [
        "1. Payment throught Cheque or Cash.",
        "2. Goods once sold will not be taken back.",
        "3. GST will be charged extra as applicable.",
        "4. Intrest @ 18% will be charged after 1 month.",
        "5. Payment by A/c. Payee Draft / Cheque only in favour of LEELAMANI TRADELINK",
        "6. Goods are sent on your account & risk.",
        "7. Subject to ahmedabad Jurisdiction.",
      ],
    };

    const compWords = finalComp.companyName.trim().split(" ");
    const firstCompWord = compWords[0] || "leelamani";
    const restCompWords = compWords.slice(1).join(" ") || "tradelink";

    const fmt = (val: number) =>
      val.toLocaleString("en-IN", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      });

    return (
      <div className="space-y-6">
        {/* Actions bar – hidden during print */}
        <div className="flex justify-between items-center print:hidden bg-white p-4 rounded-xl border border-[#ebdcc5]/40 shadow-sm">
          <Button
            variant="ghost"
            onClick={() => setViewMode("list")}
            className="border border-[#dfd5c6] text-[#1e2a4a] hover:bg-gray-100"
          >
            <ChevronLeft className="w-4 h-4 mr-1" /> Back to List
          </Button>
          <Button
            onClick={triggerNativePrint}
            className="bg-[#c4703f] hover:bg-[#b05e2f] text-white flex items-center gap-1.5 font-semibold"
          >
            <Printer className="w-4 h-4" /> Print / Save PDF
          </Button>
        </div>

        {/* Printable Invoice Container */}
        <div className="bg-white text-black p-4 md:p-6 rounded-xl shadow-lg border border-gray-300 max-w-[850px] mx-auto print:border-none print:shadow-none print:p-0 print:m-0 font-serif leading-tight">
          <div className="print-container break-inside-avoid page-break-after-avoid">
            {/* Top Motto */}
            <div className="text-center text-[10px] font-bold text-black py-0.5">
              || Shree Ganeshay Namh ||
            </div>

            {/* Outer Box Border - Strictly single A4 page height */}
            <div className="border border-black box-border text-black bg-white flex flex-col justify-between h-[260mm] max-h-[260mm] overflow-hidden text-xs">
              <div className="flex-1 flex flex-col justify-between">
                {/* Header: TAX INVOICE */}
                <div className="border-b border-black text-center py-0.5 bg-white">
                  <h1 className="text-xs font-bold uppercase tracking-wider text-black">
                    TAX INVOICE
                  </h1>
                </div>

                {/* Company Block */}
                <div className="border-b border-black p-2 flex justify-between items-start text-black">
                  <div className="pl-4">
                    <h2 className="text-3xl font-black text-black leading-none font-sans lowercase">
                      {firstCompWord}
                    </h2>
                    {restCompWords && (
                      <div className="text-xl font-bold text-black leading-none mt-0.5 font-sans lowercase">
                        {restCompWords}
                      </div>
                    )}
                    <div className="bg-black text-white text-[9px] font-bold px-3 py-0.5 rounded-full uppercase inline-block mt-1 print:bg-black print:text-white">
                      {finalComp.tagline}
                    </div>
                  </div>
                  <div className="text-right text-xs font-bold leading-tight max-w-[340px] pt-0.5 pr-2">
                    <div>F-6, SHREE PADMAVATI COMPLEX</div>
                    <div>GHEEKANTA ROAD,AHMEDABAD-1 (GUJ.)</div>
                    <div className="mt-0.5">MO. : {finalComp.phone}</div>
                  </div>
                </div>

                {/* Invoice & Transport Details Grid */}
                <div className="border-b border-black grid grid-cols-2 text-xs font-bold">
                  {/* Left Col */}
                  <div className="border-r border-black">
                    <div className="flex justify-between items-center px-2 py-0.5 border-b border-black">
                      <span>Invoice No :</span>
                      <span className="w-1/2 text-center">{activePrintBill.billNumber}</span>
                    </div>
                    <div className="flex justify-between items-center px-2 py-0.5 border-b border-black">
                      <span>Invoice Date :</span>
                      <span className="w-1/2 text-center">
                        {activePrintBill.billDate
                          ? new Date(activePrintBill.billDate).toLocaleDateString("en-IN")
                          : "N/A"}
                      </span>
                    </div>
                    <div className="flex justify-between items-center px-2 py-0.5">
                      <span>State :</span>
                      <span className="w-1/2 text-center">STATE CODE : {finalComp.stateCode}</span>
                    </div>
                  </div>
                  {/* Right Col */}
                  <div>
                    <div className="flex justify-between items-center px-2 py-0.5 border-b border-black">
                      <span>Transport :</span>
                      <span className="w-1/2 text-center">
                        {activePrintBill.transportName || "NATIONAL"}
                      </span>
                    </div>
                    <div className="flex justify-between items-center px-2 py-0.5 border-b border-black">
                      <span>Date of Supply :</span>
                      <span className="w-1/2 text-center">
                        {activePrintBill.billDate
                          ? new Date(activePrintBill.billDate).toLocaleDateString("en-IN")
                          : "N/A"}
                      </span>
                    </div>
                    <div className="flex justify-between items-center px-2 py-0.5">
                      <span>Place of Supply :</span>
                      <span className="w-1/2 text-center">{activePrintBill.placeOfSupply || ""}</span>
                    </div>
                  </div>
                </div>

                {/* Receiver & Consignee Details Grid */}
                <div className="border-b border-black grid grid-cols-2 text-xs font-bold">
                  {/* Left Col: Receiver */}
                  <div className="border-r border-black flex flex-col justify-between">
                    <div className="px-2 py-0.5 underline uppercase text-[11px]">
                      Details of Receiver / Billed to :
                    </div>
                    <div className="px-2 py-0.5 border-t border-black border-b border-black">
                      <span>NAME : </span>
                      <span className="uppercase">{activePrintBill.buyerName}</span>
                    </div>
                    <div className="px-2 py-0.5 border-b border-black leading-tight break-words min-h-[30px]">
                      <span>ADD. : </span>
                      <span className="uppercase">{activePrintBill.buyerAddress || "—"}</span>
                    </div>
                    <div className="px-2 py-0.5 border-b border-black">
                      <span>GSTIN : </span>
                      <span>{activePrintBill.buyerGst || "—"}</span>
                    </div>
                    <div className="flex justify-between px-2 py-0.5">
                      <span>STATE : {(activePrintBill as any).buyerState || "UTTAR PRADESH"}</span>
                      <span>STATE CODE : {(activePrintBill as any).buyerStateCode || "09"}</span>
                    </div>
                  </div>

                  {/* Right Col: Consignee */}
                  <div className="flex flex-col justify-between">
                    <div className="px-2 py-0.5 underline uppercase text-[11px]">
                      Details of Consignee / Shipped to :
                    </div>
                    <div className="px-2 py-0.5 border-t border-black border-b border-black">
                      <span>NAME : </span>
                      <span className="uppercase">{activePrintBill.buyerName}</span>
                    </div>
                    <div className="px-2 py-0.5 border-b border-black leading-tight break-words min-h-[30px]">
                      <span>ADD. : </span>
                      <span className="uppercase">{activePrintBill.buyerAddress || "—"}</span>
                    </div>
                    <div className="px-2 py-0.5 border-b border-black">
                      <span>GSTIN : </span>
                      <span>{activePrintBill.buyerGst || "—"}</span>
                    </div>
                    <div className="flex justify-between px-2 py-0.5">
                      <span>STATE : {(activePrintBill as any).buyerState || "UTTAR PRADESH"}</span>
                      <span>STATE CODE : {(activePrintBill as any).buyerStateCode || "09"}</span>
                    </div>
                  </div>
                </div>

                {/* Items Table - Flexibly expands downwards so PARCEL QUANTITY row touches Bank Info */}
                <div className="border-b border-black flex-1 flex flex-col justify-between min-h-[220px]">
                  <table className="w-full text-xs text-left border-collapse font-serif flex-1 flex flex-col justify-between">
                    <thead>
                      <tr className="border-b border-black text-[11px] font-bold text-black uppercase bg-white flex w-full">
                        <th className="py-1 px-1 border-r border-black w-[5%] text-center">
                          NO.
                        </th>
                        <th className="py-1 px-2 border-r border-black w-[45%]">
                          DESCRIPTION OF GOODS
                        </th>
                        <th className="py-1 px-1.5 border-r border-black w-[10%] text-center">
                          SIZE
                        </th>
                        <th className="py-1 px-1.5 border-r border-black w-[10%] text-center">
                          HSN NO.
                        </th>
                        <th className="py-1 px-1.5 border-r border-black w-[8%] text-center">
                          QTY
                        </th>
                        <th className="py-1 px-2 border-r border-black w-[10%] text-right">
                          RATE
                        </th>
                        <th className="py-1 px-2 text-right w-[12%]">AMOUNT</th>
                      </tr>
                    </thead>
                    <tbody className="flex-1 flex flex-col">
                      {printItems.map(
                        (it: any, index: number) => {
                          const price = parseFloat(it.listPrice || "0");
                          const qty = parseFloat(it.qty || "0");
                          const gross = price * qty;
                          const discountPercent = parseFloat(
                            it.discountPercent || "0"
                          );
                          const discAmount = gross * (discountPercent / 100);
                          const taxable = gross - discAmount;
                          return (
                            <tr
                              key={index}
                              className="text-xs flex w-full min-h-[24px]"
                            >
                              <td className="py-0.5 px-1 border-r border-black w-[5%] text-center font-bold">
                                {index + 1}
                              </td>
                              <td className="py-0.5 px-2 border-r border-black w-[45%] font-bold uppercase">
                                {it.name}
                              </td>
                              <td className="py-0.5 px-1.5 border-r border-black w-[10%] text-center font-bold">
                                {it.size || it.sizeName || "28X36"}
                              </td>
                              <td className="py-0.5 px-1.5 border-r border-black w-[10%] text-center font-bold">
                                {it.hsnCode || "6203"}
                              </td>
                              <td className="py-0.5 px-1.5 border-r border-black w-[8%] text-center font-bold">
                                {it.qty}
                              </td>
                              <td className="py-0.5 px-2 border-r border-black w-[10%] text-right font-bold">
                                {fmt(price)}
                              </td>
                              <td className="py-0.5 px-2 text-right w-[12%] font-bold">
                                {fmt(parseFloat(it.amount || taxable.toString()))}
                              </td>
                            </tr>
                          );
                        }
                      )}
                      {/* Fill empty space by stretching empty rows down to the tfoot */}
                      {Array.from({
                        length: Math.max(0, 7 - printItems.length),
                      }).map((_, i) => (
                        <tr key={`empty-${i}`} className="text-xs flex w-full flex-1 min-h-[20px]">
                          <td className="py-0.5 px-1 border-r border-black w-[5%]"></td>
                          <td className="py-0.5 px-2 border-r border-black w-[45%]"></td>
                          <td className="py-0.5 px-1.5 border-r border-black w-[10%]"></td>
                          <td className="py-0.5 px-1.5 border-r border-black w-[10%]"></td>
                          <td className="py-0.5 px-1.5 border-r border-black w-[8%]"></td>
                          <td className="py-0.5 px-2 border-r border-black w-[10%]"></td>
                          <td className="py-0.5 px-2 text-right w-[12%]"></td>
                        </tr>
                      ))}
                    </tbody>
                    <tfoot>
                      <tr className="border-t border-black font-bold text-xs uppercase bg-white flex w-full">
                        <td className="py-1 px-2 border-r border-black w-[60%] text-left font-bold">
                          PARCEL QUANTITY :
                        </td>
                        <td className="py-1 px-1.5 border-r border-black w-[10%] text-center font-bold">
                          {activePrintBill.parcel || 2}
                        </td>
                        <td className="py-1 px-1.5 border-r border-black w-[8%] text-center font-bold">
                          {totQty}
                        </td>
                        <td className="py-1 px-2 border-r border-black w-[10%] text-center font-bold">
                          TOTAL
                        </td>
                        <td className="py-1 px-2 text-right w-[12%] font-bold">
                          {fmt(parseFloat(activePrintBill.subtotal || "0"))}
                        </td>
                      </tr>
                    </tfoot>
                  </table>
                </div>

                {/* Bank Info + Tax Breakdown Section - Directly Attached to PARCEL QUANTITY row */}
                <div className="border-b border-black grid grid-cols-12 text-xs font-bold">
                  {/* Left: Bank Details */}
                  <div className="col-span-7 border-r border-black flex flex-col justify-between">
                    <div className="p-1.5 space-y-0.5">
                      <div className="flex">
                        <span className="w-28">BANK NAME</span>
                        <span>: {finalComp.bankName}</span>
                      </div>
                      <div className="flex">
                        <span className="w-28">A/c NO.</span>
                        <span>: {finalComp.accountNumber}</span>
                      </div>
                      <div className="flex">
                        <span className="w-28">IFSC CODE</span>
                        <span>: {finalComp.ifscCode}</span>
                      </div>
                    </div>
                    <div className="border-t border-black p-1.5 font-bold text-sm tracking-wide">
                      GST NO. {finalComp.gstNumber}
                    </div>
                  </div>

                  {/* Right: Tax Breakdown */}
                  <div className="col-span-5 flex flex-col justify-between">
                    <div className="divide-y divide-black text-xs font-bold">
                      {(() => {
                        const cgstAmt = parseFloat(
                          activePrintBill.cgstAmount || "0"
                        );
                        const sgstAmt = parseFloat(
                          activePrintBill.sgstAmount || "0"
                        );
                        const igstAmt = parseFloat(
                          activePrintBill.igstAmount || "0"
                        );
                        const roundOff = parseFloat(
                          activePrintBill.roundOff || "0"
                        );
                        return (
                          <>
                            <div className="flex justify-between px-2 py-0.5">
                              <span>Add : CGST : 2.5 %</span>
                              <span>{fmt(cgstAmt)}</span>
                            </div>
                            <div className="flex justify-between px-2 py-0.5">
                              <span>Add : SGST : 2.5 %</span>
                              <span>{fmt(sgstAmt)}</span>
                            </div>
                            <div className="flex justify-between px-2 py-0.5">
                              <span>Add : IGST : 5 %</span>
                              <span>{fmt(igstAmt)}</span>
                            </div>
                            <div className="flex justify-between px-2 py-0.5">
                              <span>ROUND OFF AMOUNT</span>
                              <span>{fmt(roundOff)}</span>
                            </div>
                          </>
                        );
                      })()}
                    </div>

                    <div className="bg-[#808080] text-black font-bold text-xs px-2 py-1 flex justify-between items-center border-t border-black print:bg-[#808080]">
                      <span>GRAND TOTAL</span>
                      <span className="font-bold text-sm">
                        {fmt(parseFloat(activePrintBill.totalAmount || "0"))}
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Bottom Footer Section */}
              <div className="relative">
                <div className="p-2 flex justify-between items-end min-h-[100px] pb-2">
                  {/* Terms & Conditions */}
                  <div className="w-1/2 space-y-0.5 pr-2">
                    <div className="font-bold underline uppercase text-xs">
                      Terms & Conditions :
                    </div>
                    <ol className="list-none space-y-0.5 font-bold text-[8.5px] leading-tight">
                      {(finalComp.terms || []).map((term: string, i: number) => (
                        <li key={i}>{term}</li>
                      ))}
                    </ol>
                  </div>

                  {/* Brand Center Logo / Mark */}
                  <div className="absolute left-1/2 bottom-2 -translate-x-1/2 text-center flex flex-col items-center justify-center">
                    <div className="w-7 h-7 relative mb-0.5 flex items-center justify-center">
                      <svg
                        className="w-7 h-7 text-black"
                        viewBox="0 0 100 100"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="12"
                        strokeLinecap="square"
                      >
                        {/* Stem */}
                        <line x1="30" y1="10" x2="30" y2="90" />
                        {/* Upper branch */}
                        <line x1="30" y1="50" x2="75" y2="10" />
                        {/* Lower branch */}
                        <line x1="30" y1="50" x2="75" y2="90" />
                      </svg>
                    </div>
                    <span className="font-serif font-normal text-2xl tracking-wider text-black leading-none uppercase">
                      {finalComp.brandName}
                    </span>
                  </div>

                  {/* Signatory */}
                  <div className="w-1/3 text-right flex flex-col justify-between h-full pt-1">
                    <div className="font-bold text-xs">
                      For, {finalComp.companyName.toUpperCase()}
                    </div>
                    <div className="pt-8">
                      <div className="font-bold text-xs">
                        Authorised Signature
                      </div>
                    </div>
                  </div>
                </div>

                {/* Bottom Arbitration Rule Banner */}
                <div className="border-t border-black text-center py-0.5 font-bold text-[8px] uppercase tracking-wider bg-white">
                  {finalComp.arbitrationRules}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // ---------- LIST VIEW (Main Dashboard) ----------
  const billColumns: ColumnDef<any>[] = [
    {
      key: "billNumber",
      label: "Bill Number",
      sortable: true,
      filterable: true,
      render: (row) => (
        <span className="font-semibold text-[#1e2a4a] font-mono text-sm">
          {row.billNumber}
        </span>
      ),
    },
    {
      key: "billDate",
      label: "Invoice Date",
      sortable: true,
      filterable: true,
      render: (row) => (
        <span className="text-slate-600 text-xs font-mono">
          {new Date(row.billDate).toLocaleDateString("en-IN", {
            day: "2-digit",
            month: "short",
            year: "numeric",
          })}
        </span>
      ),
    },
    {
      key: "buyerName",
      label: "Buyer Company",
      sortable: true,
      filterable: true,
      render: (row) => (
        <span className="font-semibold text-[#1e2a4a] text-sm">
          {row.buyerName}
        </span>
      ),
    },
    {
      key: "buyerGst",
      label: "GSTIN",
      sortable: true,
      filterable: true,
      render: (row) => (
        <span className="font-mono text-xs text-slate-600">
          {row.buyerGst || "N/A"}
        </span>
      ),
    },
    {
      key: "totalAmount",
      label: "Invoice Total",
      sortable: true,
      className: "text-right",
      headerClassName: "text-right",
      render: (row) => (
        <span className="font-mono font-bold text-[#c4703f] text-sm">
          ₹
          {parseFloat(row.totalAmount).toLocaleString("en-IN", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
          })}
        </span>
      ),
    },
    {
      key: "actions",
      label: "Actions",
      sortable: false,
      filterable: false,
      headerClassName: "text-right",
      className: "text-right",
      render: (row) => (
        <div
          className="flex items-center justify-end gap-1"
          onClick={(e) => e.stopPropagation()}
        >
          <Button
            variant="ghost"
            size="icon"
            onClick={() => openPrint(row.id)}
            className="h-8 w-8 text-slate-500 hover:text-green-600 hover:bg-green-50"
            title="View / Print Tax Invoice"
          >
            <Printer className="w-3.5 h-3.5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            onClick={() => openEdit(row)}
            className="h-8 w-8 text-slate-500 hover:text-[#c4703f] hover:bg-orange-50"
            title="Edit Invoice"
          >
            <Pencil className="w-3.5 h-3.5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            onClick={() => setDeleteTarget(row)}
            className="h-8 w-8 text-slate-500 hover:text-red-600 hover:bg-red-50"
            title="Delete Invoice"
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
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold text-[#1e2a4a] flex items-center gap-2">
            <FileText className="w-6 h-6 text-[#c4703f]" /> Tax Invoices
          </h1>
          <p className="text-[#6b7280] text-sm">
            Create and print legal tax bills split with dynamic CGST, SGST, and
            IGST.
          </p>
        </div>
        <Button
          onClick={openCreate}
          className="bg-[#c4703f] hover:bg-[#b05e2f] text-white font-semibold self-end sm:self-auto gap-1.5"
        >
          <Plus className="w-4 h-4" /> New Bill
        </Button>
      </div>

      {/* Main Unified Table */}
      <DataTable
        title="Tax Invoice Register"
        subtitle="Manage and print official GST invoices for CC buyers"
        columns={billColumns}
        data={billsData?.bills || []}
        loading={billsLoading}
        searchPlaceholder="Search bill number, buyer name, GSTIN..."
      />

      {/* Delete Confirmation Dialog */}
      <DeleteConfirmDialog
        open={!!deleteTarget}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        entityName="Invoice"
        itemIdentifier={deleteTarget?.billNumber}
        warningMessage="Are you sure you want to delete this invoice? The linked sale transaction will be soft-deleted and an entry added to audit logs."
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