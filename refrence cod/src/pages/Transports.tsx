import { useState } from "react";
import { Plus, Pencil, Trash2, Truck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { trpc } from "@/providers/trpc";
import { useToast } from "@/hooks/use-toast";
import { DataTable, type ColumnDef } from "@/components/ui/data-table";
import { DeleteConfirmDialog } from "@/components/ui/delete-confirm-dialog";

interface TransportFormData {
  name: string;
  phone: string;
  vehicleNumber: string;
}

const emptyForm: TransportFormData = {
  name: "",
  phone: "",
  vehicleNumber: "",
};

export default function Transports() {
  const { toast } = useToast();
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<TransportFormData>({ ...emptyForm });

  const [deleteTarget, setDeleteTarget] = useState<any | null>(null);

  const utils = trpc.useUtils();
  const { data, isLoading } = trpc.transport.list.useQuery();

  const createMutation = trpc.transport.create.useMutation({
    onSuccess: () => {
      utils.transport.list.invalidate();
      toast({ title: "Success", description: "Transport agency created successfully" });
      setModalOpen(false);
      setForm({ ...emptyForm });
    },
    onError: (err) => toast({ title: "Error", description: err.message, variant: "destructive" }),
  });

  const updateMutation = trpc.transport.update.useMutation({
    onSuccess: () => {
      utils.transport.list.invalidate();
      toast({ title: "Success", description: "Transport agency updated successfully" });
      setModalOpen(false);
      setEditingId(null);
      setForm({ ...emptyForm });
    },
    onError: (err) => toast({ title: "Error", description: err.message, variant: "destructive" }),
  });

  const deleteMutation = trpc.transport.delete.useMutation({
    onSuccess: () => {
      utils.transport.list.invalidate();
      toast({ title: "Success", description: "Transport agency deleted and logged to audit trail" });
      setDeleteTarget(null);
    },
    onError: (err) => toast({ title: "Error", description: err.message, variant: "destructive" }),
  });

  const openEdit = (item: any) => {
    setEditingId(item.id);
    setForm({
      name: item.name,
      phone: item.phone || "",
      vehicleNumber: item.vehicleNumber || "",
    });
    setModalOpen(true);
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name.trim()) {
      toast({ title: "Validation Error", description: "Transport name is required", variant: "destructive" });
      return;
    }

    const payload = {
      name: form.name.trim(),
      phone: form.phone.trim() || null,
      vehicleNumber: form.vehicleNumber.trim() || null,
    };

    if (editingId) {
      updateMutation.mutate({ id: editingId, ...payload });
    } else {
      createMutation.mutate(payload);
    }
  };

  const columns: ColumnDef<any>[] = [
    {
      key: "name",
      label: "Transport Name",
      sortable: true,
      filterable: true,
      render: (row) => (
        <span className="font-semibold text-[#1e2a4a] text-sm">{row.name}</span>
      ),
    },
    {
      key: "phone",
      label: "Phone / Contact",
      sortable: true,
      filterable: true,
      render: (row) => (
        <span className="font-mono text-xs text-slate-600">{row.phone || "—"}</span>
      ),
    },
    {
      key: "vehicleNumber",
      label: "Vehicle Number",
      sortable: true,
      filterable: true,
      render: (row) => (
        <span className="font-mono uppercase text-xs text-slate-600">{row.vehicleNumber || "—"}</span>
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
            title="Edit Transport"
          >
            <Pencil className="w-3.5 h-3.5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            onClick={() => setDeleteTarget(row)}
            className="h-8 w-8 text-slate-500 hover:text-red-600 hover:bg-red-50"
            title="Delete Transport"
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
            <Truck className="w-6 h-6 text-[#c4703f]" /> Transport Directory
          </h1>
          <p className="text-[#6b7280] text-sm">Manage logistics partners, vehicles, and contact details.</p>
        </div>
        <Button
          onClick={() => {
            setEditingId(null);
            setForm({ ...emptyForm });
            setModalOpen(true);
          }}
          className="bg-[#c4703f] hover:bg-[#b05e2f] text-white self-end sm:self-auto gap-1.5"
        >
          <Plus className="w-4 h-4" /> New Transport
        </Button>
      </div>

      {/* Main Unified Table */}
      <DataTable
        title="Transport Agencies"
        subtitle="Manage transport agencies and vehicle details"
        columns={columns}
        data={data?.transports || []}
        loading={isLoading}
        searchPlaceholder="Search transport or vehicle..."
      />

      {/* Transport Create/Edit Modal */}
      <Dialog open={modalOpen} onOpenChange={setModalOpen}>
        <DialogContent className="sm:max-w-md bg-[#fbfaf7]">
          <DialogHeader>
            <DialogTitle className="text-xl font-bold text-[#1e2a4a]">
              {editingId ? "Edit Transport Details" : "Create New Transport Partner"}
            </DialogTitle>
          </DialogHeader>
          <form onSubmit={handleSave} className="space-y-4 pt-2">
            <div className="space-y-1.5">
              <Label htmlFor="name" className="text-[#1e2a4a]">Transport Name / Agency</Label>
              <Input
                id="name"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="e.g. Speed Cargo Logistics"
                required
                className="bg-white border-[#dfd5c6] focus-visible:ring-[#c4703f]"
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <Label htmlFor="phone" className="text-[#1e2a4a]">Contact Phone</Label>
                <Input
                  id="phone"
                  value={form.phone}
                  onChange={(e) => setForm({ ...form, phone: e.target.value })}
                  placeholder="e.g. +91 9876543210"
                  className="bg-white border-[#dfd5c6] focus-visible:ring-[#c4703f]"
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="vehicleNumber" className="text-[#1e2a4a]">Vehicle Number</Label>
                <Input
                  id="vehicleNumber"
                  value={form.vehicleNumber}
                  onChange={(e) => setForm({ ...form, vehicleNumber: e.target.value })}
                  placeholder="e.g. DL-1CA-1234"
                  className="bg-white border-[#dfd5c6] focus-visible:ring-[#c4703f] uppercase"
                />
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
                {createMutation.isPending || updateMutation.isPending ? "Saving..." : "Save Transport"}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <DeleteConfirmDialog
        open={!!deleteTarget}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        entityName="Transport Agency"
        itemIdentifier={deleteTarget?.name}
        warningMessage="Are you sure you want to delete this transport agency? Invoices linked to this transport will remain unaffected."
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
