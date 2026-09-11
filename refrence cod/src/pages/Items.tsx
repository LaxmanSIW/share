import { useState } from "react";
import { Plus, Pencil, Trash2, Tag } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { trpc } from "@/providers/trpc";
import { useToast } from "@/hooks/use-toast";
import { DataTable, type ColumnDef } from "@/components/ui/data-table";
import { DeleteConfirmDialog } from "@/components/ui/delete-confirm-dialog";

interface ItemFormData {
  name: string;
  hsnCode: string;
  listPrice: number;
  unit: string;
  taxPercent: number;
  categoryId: string | undefined;
}

const emptyForm: ItemFormData = {
  name: "",
  hsnCode: "62034200",
  listPrice: 0,
  unit: "Pcs.",
  taxPercent: 5,
  categoryId: undefined,
};

export default function Items() {
  const { toast } = useToast();
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<ItemFormData>({ ...emptyForm });

  const [deleteTarget, setDeleteTarget] = useState<any | null>(null);

  const utils = trpc.useUtils();
  const { data, isLoading } = trpc.item.list.useQuery();
  const { data: catData } = trpc.category.list.useQuery();
  const categories = catData?.categories || [];

  const createMutation = trpc.item.create.useMutation({
    onSuccess: () => {
      utils.item.list.invalidate();
      toast({ title: "Success", description: "Item created successfully" });
      setModalOpen(false);
      setForm({ ...emptyForm });
    },
    onError: (err) => toast({ title: "Error", description: err.message, variant: "destructive" }),
  });

  const updateMutation = trpc.item.update.useMutation({
    onSuccess: () => {
      utils.item.list.invalidate();
      toast({ title: "Success", description: "Item updated successfully" });
      setModalOpen(false);
      setEditingId(null);
      setForm({ ...emptyForm });
    },
    onError: (err) => toast({ title: "Error", description: err.message, variant: "destructive" }),
  });

  const deleteMutation = trpc.item.delete.useMutation({
    onSuccess: () => {
      utils.item.list.invalidate();
      toast({ title: "Success", description: "Item deleted and logged to audit trail" });
      setDeleteTarget(null);
    },
    onError: (err) => toast({ title: "Error", description: err.message, variant: "destructive" }),
  });

  const openEdit = (item: any) => {
    setEditingId(item.id);
    setForm({
      name: item.name,
      hsnCode: item.hsnCode,
      listPrice: parseFloat(item.listPrice) || 0,
      unit: item.unit,
      taxPercent: parseFloat(item.taxPercent) || 0,
      categoryId: item.categoryId ? String(item.categoryId) : undefined,
    });
    setModalOpen(true);
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name.trim()) {
      toast({ title: "Validation Error", description: "Item name is required", variant: "destructive" });
      return;
    }
    
    const payload = {
      ...form,
      categoryId: form.categoryId ? parseInt(form.categoryId) : null,
    };

    if (editingId) {
      updateMutation.mutate({ id: editingId, ...payload });
    } else {
      createMutation.mutate(payload);
    }
  };

  const categoryOptions = [
    { label: "Uncategorized", value: "" },
    ...categories.map((c: any) => ({ label: c.name, value: c.name })),
  ];

  const columns: ColumnDef<any>[] = [
    {
      key: "name",
      label: "Item Name",
      sortable: true,
      filterable: true,
      render: (row) => (
        <span className="font-semibold text-[#1e2a4a] text-sm">{row.name}</span>
      ),
    },
    {
      key: "categoryName",
      label: "Category",
      sortable: true,
      filterable: true,
      valueMode: "predefined",
      options: categoryOptions,
      render: (row) => (
        row.categoryName ? (
          <span className="px-2.5 py-0.5 text-xs font-medium rounded-full bg-[#e8e0d4] text-[#1e2a4a]">
            {row.categoryName}
          </span>
        ) : (
          <span className="text-slate-400 italic text-xs">Uncategorized</span>
        )
      ),
    },
    {
      key: "hsnCode",
      label: "HSN Code",
      sortable: true,
      filterable: true,
      render: (row) => (
        <span className="font-mono text-xs text-slate-600">{row.hsnCode}</span>
      ),
    },
    {
      key: "listPrice",
      label: "List Price",
      sortable: true,
      filterable: true,
      className: "text-right",
      headerClassName: "text-right",
      render: (row) => (
        <span className="font-mono font-semibold text-[#1e2a4a]">
          ₹{parseFloat(row.listPrice).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
        </span>
      ),
    },
    {
      key: "unit",
      label: "Unit",
      sortable: true,
      filterable: true,
      render: (row) => (
        <span className="text-slate-600 text-xs">{row.unit}</span>
      ),
    },
    {
      key: "taxPercent",
      label: "GST Rate",
      sortable: true,
      filterable: true,
      className: "text-right",
      headerClassName: "text-right",
      render: (row) => (
        <span className="font-mono text-slate-600 text-xs">{parseFloat(row.taxPercent)}%</span>
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
        <div className="flex items-center justify-end gap-1.5" onClick={(e) => e.stopPropagation()}>
          <Button
            variant="ghost"
            size="icon"
            onClick={() => openEdit(row)}
            className="h-8 w-8 text-slate-500 hover:text-[#c4703f] hover:bg-orange-50"
            title="Edit Item"
          >
            <Pencil className="w-3.5 h-3.5" />
          </Button>
          {row.name?.trim().toLowerCase() === "trousers" ? (
            <Button
              variant="ghost"
              size="icon"
              disabled
              className="h-8 w-8 text-slate-300 cursor-not-allowed opacity-40"
              title="Default system item 'Trousers' cannot be deleted"
            >
              <Trash2 className="w-3.5 h-3.5" />
            </Button>
          ) : (
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setDeleteTarget(row)}
              className="h-8 w-8 text-slate-500 hover:text-red-600 hover:bg-red-50"
              title="Delete Item"
            >
              <Trash2 className="w-3.5 h-3.5" />
            </Button>
          )}
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
            <Tag className="w-6 h-6 text-[#c4703f]" /> Item Catalog
          </h1>
          <p className="text-[#6b7280] text-sm">Manage products, HSN codes, GST rates, and price lists.</p>
        </div>
        <Button
          onClick={() => {
            setEditingId(null);
            setForm({ ...emptyForm });
            setModalOpen(true);
          }}
          className="bg-[#c4703f] hover:bg-[#b05e2f] text-white self-end sm:self-auto gap-1.5"
        >
          <Plus className="w-4 h-4" /> New Item
        </Button>
      </div>

      {/* Main Unified Table */}
      <DataTable
        title="Product Master List"
        subtitle="Catalog of garments, trousers, pricing, and category mappings"
        columns={columns}
        data={data?.items || []}
        loading={isLoading}
        searchPlaceholder="Search name, HSN code, or category..."
      />

      {/* Item Create/Edit Modal */}
      <Dialog open={modalOpen} onOpenChange={setModalOpen}>
        <DialogContent className="sm:max-w-md bg-[#fbfaf7]">
          <DialogHeader>
            <DialogTitle className="text-xl font-bold text-[#1e2a4a]">
              {editingId ? "Edit Catalog Item" : "Create New Catalog Item"}
            </DialogTitle>
          </DialogHeader>
          <form onSubmit={handleSave} className="space-y-4 pt-2">
            <div className="space-y-1.5">
              <Label htmlFor="name" className="text-[#1e2a4a]">Item Name / Description</Label>
              <Input
                id="name"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="e.g. Denim Trousers Slim Fit"
                required
                className="bg-white border-[#dfd5c6] focus-visible:ring-[#c4703f]"
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="category" className="text-[#1e2a4a]">Category</Label>
              <Select
                value={form.categoryId || "none"}
                onValueChange={(val) => setForm({ ...form, categoryId: val === "none" ? undefined : val })}
              >
                <SelectTrigger className="bg-white border-[#dfd5c6]">
                  <SelectValue placeholder="Select Category" />
                </SelectTrigger>
                <SelectContent className="bg-white">
                  <SelectItem value="none">Uncategorized</SelectItem>
                  {categories.map((cat: any) => (
                    <SelectItem key={cat.id} value={String(cat.id)}>
                      {cat.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <Label htmlFor="hsnCode" className="text-[#1e2a4a]">HSN/SAC Code</Label>
                <Input
                  id="hsnCode"
                  value={form.hsnCode}
                  onChange={(e) => setForm({ ...form, hsnCode: e.target.value })}
                  placeholder="e.g. 62034200"
                  required
                  className="bg-white border-[#dfd5c6] focus-visible:ring-[#c4703f]"
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="unit" className="text-[#1e2a4a]">Unit of Measure</Label>
                <Select
                  value={form.unit}
                  onValueChange={(val) => setForm({ ...form, unit: val })}
                >
                  <SelectTrigger className="bg-white border-[#dfd5c6]">
                    <SelectValue placeholder="Select Unit" />
                  </SelectTrigger>
                  <SelectContent className="bg-white">
                    <SelectItem value="Pcs.">Pcs. (Pieces)</SelectItem>
                    <SelectItem value="Mtrs">Mtrs (Meters)</SelectItem>
                    <SelectItem value="Kg">Kg (Kilograms)</SelectItem>
                    <SelectItem value="Box">Box</SelectItem>
                    <SelectItem value="Doz">Doz (Dozen)</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <Label htmlFor="listPrice" className="text-[#1e2a4a]">List Price (₹)</Label>
                <Input
                  id="listPrice"
                  type="number"
                  step="0.01"
                  value={form.listPrice}
                  onChange={(e) => setForm({ ...form, listPrice: parseFloat(e.target.value) || 0 })}
                  placeholder="e.g. 800.00"
                  required
                  className="bg-white border-[#dfd5c6] focus-visible:ring-[#c4703f]"
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="taxPercent" className="text-[#1e2a4a]">GST Rate (%)</Label>
                <Select
                  value={String(form.taxPercent)}
                  onValueChange={(val) => setForm({ ...form, taxPercent: parseFloat(val) })}
                >
                  <SelectTrigger className="bg-white border-[#dfd5c6]">
                    <SelectValue placeholder="Select GST" />
                  </SelectTrigger>
                  <SelectContent className="bg-white">
                    <SelectItem value="0">0% (GST Exempted)</SelectItem>
                    <SelectItem value="5">5% GST</SelectItem>
                    <SelectItem value="12">12% GST</SelectItem>
                    <SelectItem value="18">18% GST</SelectItem>
                    <SelectItem value="28">28% GST</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="flex justify-end gap-3 pt-4 border-t border-gray-100">
              <Button
                type="button"
                variant="outline"
                onClick={() => setModalOpen(false)}
                className="border-[#dfd5c6] hover:bg-gray-100 text-[#1e2a4a]"
              >
                Cancel
              </Button>
              <Button
                type="submit"
                className="bg-[#c4703f] hover:bg-[#b05e2f] text-white"
                disabled={createMutation.isPending || updateMutation.isPending}
              >
                {createMutation.isPending || updateMutation.isPending ? "Saving..." : "Save Item"}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <DeleteConfirmDialog
        open={!!deleteTarget}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        entityName="Item"
        itemIdentifier={deleteTarget?.name}
        warningMessage="Are you sure you want to delete this catalog item? Past bills containing this item will retain historical line-item snapshots."
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