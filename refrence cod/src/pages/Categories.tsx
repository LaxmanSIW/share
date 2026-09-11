import { useState } from "react";
import { Plus, Pencil, Trash2, FolderTree } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { trpc } from "@/providers/trpc";
import { useToast } from "@/hooks/use-toast";
import { DataTable, type ColumnDef } from "@/components/ui/data-table";
import { DeleteConfirmDialog } from "@/components/ui/delete-confirm-dialog";

interface CategoryFormData {
  name: string;
}

const emptyForm: CategoryFormData = {
  name: "",
};

export default function Categories() {
  const { toast } = useToast();
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<CategoryFormData>({ ...emptyForm });

  // Delete modal state
  const [deleteTarget, setDeleteTarget] = useState<any | null>(null);

  const utils = trpc.useUtils();
  const { data, isLoading } = trpc.category.list.useQuery();

  const createMutation = trpc.category.create.useMutation({
    onSuccess: () => {
      utils.category.list.invalidate();
      toast({ title: "Success", description: "Category created successfully" });
      setModalOpen(false);
      setForm({ ...emptyForm });
    },
    onError: (err) => toast({ title: "Error", description: err.message, variant: "destructive" }),
  });

  const updateMutation = trpc.category.update.useMutation({
    onSuccess: () => {
      utils.category.list.invalidate();
      toast({ title: "Success", description: "Category updated successfully" });
      setModalOpen(false);
      setEditingId(null);
      setForm({ ...emptyForm });
    },
    onError: (err) => toast({ title: "Error", description: err.message, variant: "destructive" }),
  });

  const deleteMutation = trpc.category.delete.useMutation({
    onSuccess: () => {
      utils.category.list.invalidate();
      toast({ title: "Success", description: "Category deleted and archived in logs" });
      setDeleteTarget(null);
    },
    onError: (err) => toast({ title: "Error", description: err.message, variant: "destructive" }),
  });

  const openEdit = (category: any) => {
    setEditingId(category.id);
    setForm({
      name: category.name,
    });
    setModalOpen(true);
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name.trim()) {
      toast({ title: "Validation Error", description: "Category name is required", variant: "destructive" });
      return;
    }

    if (editingId) {
      updateMutation.mutate({ id: editingId, ...form });
    } else {
      createMutation.mutate(form);
    }
  };

  const columns: ColumnDef<any>[] = [
    {
      key: "name",
      label: "Category Name",
      sortable: true,
      filterable: true,
      render: (row) => (
        <span className="font-semibold text-[#1e2a4a] text-sm">{row.name}</span>
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
            title="Edit Category"
          >
            <Pencil className="w-3.5 h-3.5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            onClick={() => setDeleteTarget(row)}
            className="h-8 w-8 text-slate-500 hover:text-red-600 hover:bg-red-50"
            title="Delete Category"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold text-[#1e2a4a] flex items-center gap-2">
            <FolderTree className="w-6 h-6 text-[#c4703f]" /> Item Categories
          </h1>
          <p className="text-[#6b7280] text-sm">Manage product categories for reporting and cataloging.</p>
        </div>
        <Button
          onClick={() => {
            setEditingId(null);
            setForm({ ...emptyForm });
            setModalOpen(true);
          }}
          className="bg-[#c4703f] hover:bg-[#b05e2f] text-white self-end sm:self-auto gap-1.5"
        >
          <Plus className="w-4 h-4" /> New Category
        </Button>
      </div>

      {/* Main Unified Table */}
      <DataTable
        title="Category Catalog"
        subtitle="Manage product categories used in items and sales analytics"
        columns={columns}
        data={data?.categories || []}
        loading={isLoading}
        searchPlaceholder="Search category name..."
      />

      {/* Category Create/Edit Modal */}
      <Dialog open={modalOpen} onOpenChange={setModalOpen}>
        <DialogContent className="sm:max-w-md bg-[#fbfaf7]">
          <DialogHeader>
            <DialogTitle className="text-xl font-bold text-[#1e2a4a]">
              {editingId ? "Edit Category" : "Create New Category"}
            </DialogTitle>
          </DialogHeader>
          <form onSubmit={handleSave} className="space-y-4 pt-2">
            <div className="space-y-1.5">
              <Label htmlFor="name" className="text-[#1e2a4a]">Category Name</Label>
              <Input
                id="name"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="e.g. Cotton Trousers"
                required
                className="bg-white border-[#dfd5c6] focus-visible:ring-[#c4703f]"
              />
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
                {createMutation.isPending || updateMutation.isPending ? "Saving..." : "Save Category"}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <DeleteConfirmDialog
        open={!!deleteTarget}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        entityName="Category"
        itemIdentifier={deleteTarget?.name}
        warningMessage="Are you sure you want to delete this category? Items in this category will be marked as Uncategorized."
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