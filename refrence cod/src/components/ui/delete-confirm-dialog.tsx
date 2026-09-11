import { useState, useEffect } from "react";
import { AlertTriangle, Archive } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";

interface DeleteConfirmDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  entityName: string;
  itemIdentifier?: string;
  warningMessage?: string;
  onConfirm: (reason: string) => void | Promise<void>;
  isPending?: boolean;
  requireReason?: boolean;
}

export function DeleteConfirmDialog({
  open,
  onOpenChange,
  entityName,
  itemIdentifier,
  warningMessage,
  onConfirm,
  isPending = false,
  requireReason = true,
}: DeleteConfirmDialogProps) {
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    if (open) {
      setReason("");
      setError("");
    }
  }, [open]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    if (requireReason && !reason.trim()) {
      setError("Please provide a reason for deleting/archiving this record.");
      return;
    }

    try {
      await onConfirm(reason.trim());
      onOpenChange(false);
    } catch (err: any) {
      setError(err.message || "Failed to process request");
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md bg-white border border-slate-200 text-slate-900 shadow-xl">
        <DialogHeader className="gap-2">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full bg-red-100 flex items-center justify-center text-red-600 shrink-0">
              <AlertTriangle className="w-5 h-5" />
            </div>
            <div>
              <DialogTitle className="text-lg font-bold text-slate-900">
                Confirm Archive / Delete {entityName}
              </DialogTitle>
              {itemIdentifier && (
                <p className="text-xs font-semibold text-slate-600 mt-0.5">
                  Record: <span className="text-[#c4703f] font-mono">{itemIdentifier}</span>
                </p>
              )}
            </div>
          </div>
          <DialogDescription className="text-slate-600 text-sm mt-2">
            {warningMessage ||
              `Are you sure you want to delete this ${entityName.toLowerCase()}? An entry will be saved in the system audit logs with your reason.`}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4 mt-2">
          <div className="space-y-2">
            <Label htmlFor="archive-reason" className="text-xs font-semibold text-slate-700">
              Reason for Deletion / Archiving {requireReason && <span className="text-red-500">*</span>}
            </Label>
            <Textarea
              id="archive-reason"
              placeholder={`Enter detailed reason (e.g. "Duplicate entry created by mistake", "Customer cancelled order")...`}
              value={reason}
              onChange={(e) => {
                setReason(e.target.value);
                if (error) setError("");
              }}
              rows={3}
              className="border-slate-300 text-sm focus-visible:ring-[#c4703f]"
            />
            {error && <p className="text-xs text-red-600 font-medium">{error}</p>}
          </div>

          <DialogFooter className="gap-2 sm:gap-0 pt-2 border-t border-slate-100">
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={isPending}
              className="border-slate-300 text-slate-700 hover:bg-slate-50"
            >
              Cancel
            </Button>
            <Button
              type="submit"
              disabled={isPending}
              className="bg-red-600 hover:bg-red-700 text-white gap-2"
            >
              {isPending ? (
                <span>Archiving...</span>
              ) : (
                <>
                  <Archive className="w-4 h-4" />
                  <span>Confirm Delete</span>
                </>
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
