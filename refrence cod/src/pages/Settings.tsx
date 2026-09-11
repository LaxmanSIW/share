import { INDIA_STATES } from "@/lib/states";
import { useMemo, useState, useEffect } from "react";
import { AlertTriangle, RefreshCw, BookOpen, Users, Building2, Landmark } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { trpc } from "@/providers/trpc";
import { useToast } from "@/hooks/use-toast";

export default function Settings() {
  const { toast } = useToast();
  const utils = trpc.useUtils();
  const [resettingBuyer, setResettingBuyer] = useState(false);
  const [resettingTransaction, setResettingTransaction] = useState(false);

  // Core Company Settings State
  const [compName, setCompName] = useState("");
  const [address, setAddress] = useState("");
  const [stateCode, setStateCode] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [gst, setGst] = useState("");
  const [bank, setBank] = useState("");
  const [acct, setAcct] = useState("");
  const [ifsc, setIfsc] = useState("");
  const [branch, setBranch] = useState("");
  const [signatory, setSignatory] = useState("");
  const [termsText, setTermsText] = useState("");
  const [startingBillNumber, setStartingBillNumber] = useState("");
  const [initialized, setInitialized] = useState(false);

  const summaryQuery = trpc.settings.summary.useQuery(undefined, {
    refetchOnWindowFocus: false,
  });
  const { data: companyData } = trpc.settings.getCompany.useQuery();

  const resetBuyerMutation = trpc.settings.resetBuyerIds.useMutation({
    onSuccess: (data) => {
      setResettingBuyer(false);
      toast({ title: "Buyer IDs reset", description: `Next buyer ID is now ${data.nextBuyerId}` });
      void summaryQuery.refetch();
    },
    onError: (err) => {
      setResettingBuyer(false);
      toast({ title: "Error", description: err.message, variant: "destructive" });
    },
  });

  const resetTransactionMutation = trpc.settings.resetTransactionIds.useMutation({
    onSuccess: (data) => {
      setResettingTransaction(false);
      toast({ title: "Transaction IDs reset", description: `Next transaction ID is now ${data.nextTransactionId}` });
      void summaryQuery.refetch();
    },
    onError: (err) => {
      setResettingTransaction(false);
      toast({ title: "Error", description: err.message, variant: "destructive" });
    },
  });

  const updateCompanyMutation = trpc.settings.updateCompany.useMutation({
    onSuccess: () => {
      toast({ title: "Success", description: "Company details updated successfully" });
      void utils.settings.getCompany.invalidate();
    },
    onError: (err) => {
      toast({ title: "Error", description: err.message, variant: "destructive" });
    },
  });

  // Populate company edit form once retrieved
  useEffect(() => {
    if (companyData && !initialized) {
      setCompName(companyData.companyName || "");
      setAddress(companyData.address || "");
      setStateCode((companyData as any).stateCode || "");
      setPhone(companyData.phone || "");
      setEmail(companyData.email || "");
      setGst(companyData.gstNumber || "");
      setBank(companyData.bankName || "");
      setAcct(companyData.accountNumber || "");
      setIfsc(companyData.ifscCode || "");
      setBranch(companyData.branchName || "");
      setSignatory(companyData.authorizedSignatory || "");
      setTermsText((companyData.terms || []).join("\n"));
      setStartingBillNumber(companyData.startingBillNumber || "0001");
      setInitialized(true);
    }
  }, [companyData, initialized]);

  const handleCompanySave = (e: React.FormEvent) => {
    e.preventDefault();
    if (!compName.trim()) {
      toast({ title: "Validation Error", description: "Company Name is required", variant: "destructive" });
      return;
    }
    const termsArray = termsText
      .split("\n")
      .map((t) => t.trim())
      .filter(Boolean);

    updateCompanyMutation.mutate({
      companyName: compName,
      address,
      stateCode,
      state: INDIA_STATES.find(s => s.code === stateCode)?.name,
      phone,
      email,
      gstNumber: gst,
      bankName: bank,
      accountNumber: acct,
      ifscCode: ifsc,
      branchName: branch,
      authorizedSignatory: signatory,
      terms: termsArray,
      startingBillNumber: startingBillNumber || "0001",
    });
  };

  const summary = useMemo(
    () =>
      summaryQuery.data ?? {
        buyerCount: 0,
        transactionCount: 0,
        nextBuyerId: 1,
        nextTransactionId: 1,
      },
    [summaryQuery.data]
  );

  return (
    <div className="min-h-screen bg-[#f5f0e8] p-4 lg:p-6 text-[#1e2a4a]">
      <div className="mx-auto max-w-5xl space-y-6">
        {/* Title Card */}
        <div className="rounded-2xl border border-[#d9cfc0] bg-white p-6 shadow-sm">
          <h1 className="text-2xl font-semibold tracking-tight">System Configuration</h1>
          <p className="text-sm text-[#3d4f6f]">Manage legal billing identity, banking info, and system counters.</p>
        </div>

        {/* Company Settings Card */}
        <Card className="border-[#d9cfc0] shadow-sm bg-white">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-[#1e2a4a]">
              <Building2 className="h-5 w-5 text-[#c4703f]" />
              Company Billing & Identity Details
            </CardTitle>
            <CardDescription>
              Configure information that is printed on your tax invoices.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleCompanySave} className="space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="companyName">Company Name / Legal Entity</Label>
                  <Input
                    id="companyName"
                    value={compName}
                    onChange={(e) => setCompName(e.target.value)}
                    placeholder="e.g. Alpha Wholesale"
                    required
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="gstNumber">GSTIN (GST Registration Number)</Label>
                  <Input
                    id="gstNumber"
                    value={gst}
                    onChange={(e) => setGst(e.target.value)}
                    placeholder="e.g. 09AAAAA1234A1Z2"
                    required
                    className="font-mono"
                  />
                </div>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="address">Official Billing Address</Label>
                <Textarea
                  id="address"
                  value={address}
                  onChange={(e) => setAddress(e.target.value)}
                  placeholder="Street details, City, State, ZIP..."
                  required
                  rows={2}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="stateCode">State / State Code (for GST)</Label>
                <select
                  id="stateCode"
                  value={stateCode}
                  onChange={(e) => setStateCode(e.target.value)}
                  className="flex h-9 w-full rounded-md border border-gray-300 bg-white px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                  required
                >
                  <option value="" disabled>Select State</option>
                  {INDIA_STATES.map((s) => (
                    <option key={s.code} value={s.code}>{s.name} ({s.code})</option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="phone">Contact Number</Label>
                  <Input
                    id="phone"
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    placeholder="+91 9999999999"
                    required
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="email">Support Email Address</Label>
                  <Input
                    id="email"
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="billing@company.com"
                    required
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="startingBillNumber">Starting Bill Number</Label>
                  <Input
                    id="startingBillNumber"
                    value={startingBillNumber}
                    onChange={(e) => setStartingBillNumber(e.target.value)}
                    placeholder="e.g. 00001 or 001"
                    required
                    className="font-mono font-bold text-orange-700 bg-orange-50/50"
                  />
                </div>
              </div>

              {/* Bank Card Section */}
              <div className="rounded-xl border border-gray-100 bg-[#fbfaf7] p-4 space-y-4">
                <h4 className="font-semibold text-xs uppercase tracking-wider text-[#c4703f] flex items-center gap-1.5 border-b border-gray-200 pb-1.5">
                  <Landmark className="w-4 h-4" /> Settlement Bank Details
                </h4>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="space-y-1.5">
                    <Label htmlFor="bankName">Bank Name</Label>
                    <Input
                      id="bankName"
                      value={bank}
                      onChange={(e) => setBank(e.target.value)}
                      placeholder="e.g. ICICI Bank"
                      required
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label htmlFor="accountNumber">Account Number</Label>
                    <Input
                      id="accountNumber"
                      value={acct}
                      onChange={(e) => setAcct(e.target.value)}
                      placeholder="e.g. 1234567890"
                      required
                      className="font-mono"
                    />
                  </div>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div className="space-y-1.5">
                    <Label htmlFor="ifscCode">IFSC Code</Label>
                    <Input
                      id="ifscCode"
                      value={ifsc}
                      onChange={(e) => setIfsc(e.target.value)}
                      placeholder="e.g. ICIC11222"
                      required
                      className="font-mono uppercase"
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label htmlFor="branchName">Branch Location</Label>
                    <Input
                      id="branchName"
                      value={branch}
                      onChange={(e) => setBranch(e.target.value)}
                      placeholder="e.g. Noida Sector 62"
                      required
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label htmlFor="authorizedSignatory">Authorized Signatory Title/Name</Label>
                    <Input
                      id="authorizedSignatory"
                      value={signatory}
                      onChange={(e) => setSignatory(e.target.value)}
                      placeholder="e.g. Add Name"
                      required
                    />
                  </div>
                </div>
              </div>

              {/* Terms and Conditions Section */}
              <div className="space-y-1.5">
                <Label htmlFor="terms">Terms & Conditions (One rule per line)</Label>
                <Textarea
                  id="terms"
                  value={termsText}
                  onChange={(e) => setTermsText(e.target.value)}
                  placeholder="e.g. 1. Goods once sold will not be taken back."
                  rows={3}
                  className="font-serif text-sm"
                />
              </div>

              <div className="flex justify-end pt-2">
                <Button
                  type="submit"
                  className="bg-[#c4703f] hover:bg-[#a85d32] text-white font-semibold px-6"
                  disabled={updateCompanyMutation.isPending}
                >
                  {updateCompanyMutation.isPending ? "Updating identity..." : "Save Company Profile"}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>

        {/* Counters & Diagnostic Controls */}
        <div className="grid gap-6 lg:grid-cols-2">
          <Card className="border-[#d9cfc0] shadow-sm bg-white">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-[#1e2a4a]">
                <Users className="h-5 w-5 text-[#c4703f]" />
                Buyer ID counter
              </CardTitle>
              <CardDescription>Recompute the next buyer ID from the highest existing buyer record.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="rounded-lg border border-[#d9cfc0] bg-[#f5f0e8] p-4 text-sm text-[#3d4f6f]">
                <p>
                  <span className="font-medium text-[#1e2a4a]">Current buyer count:</span> {summary.buyerCount}
                </p>
                <p>
                  <span className="font-medium text-[#1e2a4a]">Next buyer ID:</span> {summary.nextBuyerId}
                </p>
              </div>
              <Button
                className="w-full bg-[#c4703f] hover:bg-[#a85d32] text-white font-semibold"
                onClick={() => {
                  setResettingBuyer(true);
                  resetBuyerMutation.mutate();
                }}
                disabled={resettingBuyer || resetBuyerMutation.isPending}
              >
                <RefreshCw className="mr-2 h-4 w-4" />
                {resettingBuyer || resetBuyerMutation.isPending ? "Resetting..." : "Reset buyer IDs"}
              </Button>
            </CardContent>
          </Card>

          <Card className="border-[#d9cfc0] shadow-sm bg-white">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-[#1e2a4a]">
                <BookOpen className="h-5 w-5 text-[#c4703f]" />
                Transaction ID counter
              </CardTitle>
              <CardDescription>Recompute the next transaction ID from the highest existing transaction record.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="rounded-lg border border-[#d9cfc0] bg-[#f5f0e8] p-4 text-sm text-[#3d4f6f]">
                <p>
                  <span className="font-medium text-[#1e2a4a]">Current transaction count:</span> {summary.transactionCount}
                </p>
                <p>
                  <span className="font-medium text-[#1e2a4a]">Next transaction ID:</span> {summary.nextTransactionId}
                </p>
              </div>
              <Button
                className="w-full bg-[#1e2a4a] hover:bg-[#152038] text-white font-semibold"
                onClick={() => {
                  setResettingTransaction(true);
                  resetTransactionMutation.mutate();
                }}
                disabled={resettingTransaction || resetTransactionMutation.isPending}
              >
                <RefreshCw className="mr-2 h-4 w-4" />
                {resettingTransaction || resetTransactionMutation.isPending ? "Resetting..." : "Reset transaction IDs"}
              </Button>
            </CardContent>
          </Card>
        </div>

        <Card className="border-[#d9cfc0] shadow-sm bg-white">
          <CardContent className="flex items-start gap-3 py-5">
            <AlertTriangle className="mt-0.5 h-5 w-5 text-[#c4703f]" />
            <div className="text-sm text-[#3d4f6f]">
              <p className="font-medium text-[#1e2a4a]">Counter Logic</p>
              <p className="mt-1">
                Each reset reads the highest existing record ID in the selected table and sets the next ID to that value plus one. If there are no rows, the next ID starts at 1.
              </p>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
