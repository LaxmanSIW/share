import { useState, useCallback } from "react";
import { useDropzone } from "react-dropzone";
import Papa from "papaparse";
import {
  Upload,
  Download,
  CheckCircle2,
  AlertTriangle,
  X,
  FileSpreadsheet,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { trpc } from "@/providers/trpc";
import { useToast } from "@/hooks/use-toast";

interface CsvRow {
  companyName?: string;
  contactPerson?: string;
  phone?: string;
  gstNumber?: string;
  creditLimit?: number;
  address?: string;
  city?: string;
  state?: string;
  stateCode?: string;
  buyer_name?: string;
  book_type?: string;
  transaction_date?: string;
  due_date?: string;
  transaction_type?: string;
  quantity?: string;
  amount?: string;
  check_number?: string;
  include_in_reporting?: string;
}

interface ParsedRow extends CsvRow {
  rowNumber: number;
  errors: string[];
  isValid: boolean;
}





export default function BulkUpload() {
  const { toast } = useToast();
  const [parsedRows, setParsedRows] = useState<ParsedRow[]>([]);
  const [fileName, setFileName] = useState<string>("");
  const [activeTab, setActiveTab] = useState<"Transactions" | "Buyers">("Transactions");
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState<any>(null);

  const utils = trpc.useUtils();

  const bulkCreateMutation = trpc.transaction.bulkCreate.useMutation({
    onSuccess: (result) => {
      setImportResult(result);
      utils.transaction.list.invalidate();
      utils.dashboard.invalidate();
      utils.buyer.list.invalidate();
      toast({
        title: "Import Complete",
        description: `${result.imported} transactions imported, ${result.newBuyers} new buyers created`,
      });
      setImporting(false);
    },
    onError: (err) => {
      toast({ title: "Import Failed", description: err.message, variant: "destructive" });
      setImporting(false);
    }
  });

  const importBuyerMutation = trpc.buyer.bulkCreate.useMutation({
    onSuccess: (result) => {
      setImportResult({ imported: result.imported, newBuyers: 0, failed: result.failed, errors: [] });
      utils.buyer.list.invalidate();
      toast({
        title: "Import Complete",
        description: `${result.imported} buyers imported, ${result.failed} failed`,
      });
      setImporting(false);
    },
    onError: (err) => {
      toast({ title: "Import Failed", description: err.message, variant: "destructive" });
      setImporting(false);
    }
  });

  const downloadTemplate = () => {
    const headers = activeTab === "Transactions" 
      ? ["buyer_name", "book_type", "transaction_date", "due_date", "transaction_type", "quantity", "amount", "check_number", "include_in_reporting"]
      : ["companyName", "contactPerson", "phone", "gstNumber", "creditLimit", "address", "city", "state", "stateCode"];
    
    const data = activeTab === "Transactions"
      ? [{
          buyer_name: "Example Buyer Pvt Ltd",
          book_type: "CC",
          transaction_date: "2024-03-01",
          transaction_type: "sale",
          quantity: "50",
          amount: "25000",
        }]
      : [{
          companyName: "ABC Garments",
          contactPerson: "John Doe",
          phone: "9876543210",
          gstNumber: "27AABCU9603R1ZM",
          creditLimit: "500000",
          address: "123 Main St",
          city: "Mumbai",
          state: "Maharashtra",
          stateCode: "MH",
        }];

    const csv = Papa.unparse({ fields: headers, data: data as any });
    const blob = new Blob([csv], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `alpha-${activeTab.toLowerCase()}-template.csv`;
    a.click();
  };

  const validateRow = (row: CsvRow, rowNumber: number, activeTab: string): ParsedRow => {
    const errors: string[] = [];
    
    if (activeTab === "Buyers") {
      const companyName = row.companyName || (row as any)["company name"] || (row as any).companyname || (row as any)["Company Name"] || "";
      if (!companyName?.trim()) errors.push("Company Name is required");
      
      const creditLimit = parseFloat(row.creditLimit || (row as any)["credit limit"] || (row as any)["Credit Limit"] || "0") || 0;

      return {
        ...row,
        companyName,
        creditLimit,
        contactPerson: row.contactPerson || (row as any)["contact person"] || (row as any)["Contact Person"] || "",
        phone: row.phone || (row as any).Phone || "",
        gstNumber: row.gstNumber || (row as any).GST || (row as any)["GST Number"] || "",
        address: row.address || (row as any).Address || "",
        city: row.city || (row as any).City || "",
        state: row.state || (row as any).State || "",
        stateCode: row.stateCode || (row as any)["State Code"] || "",
        rowNumber,
        errors,
        isValid: errors.length === 0,
      };
    } else {
      if (!row.buyer_name?.trim()) errors.push("Buyer name is required");
      if (!row.book_type || !["CC", "CS"].includes(row.book_type.toUpperCase())) {
        errors.push("Book type must be CC or CS");
      }
      if (!row.transaction_date?.trim()) errors.push("Transaction date is required");
      if (!row.transaction_type || !["sale", "payment"].includes(row.transaction_type)) {
        errors.push("Transaction type must be sale or payment");
      }
      const amount = parseFloat(row.amount || "0");
      if (isNaN(amount) || amount <= 0) errors.push("Amount must be a positive number");
      
      const issale = row.transaction_type === "sale";
      const qty = parseInt(row.quantity || "0") || 0;
      if (issale && qty <= 0) errors.push("Quantity is required for sales");

      return {
        ...row,
        rowNumber,
        errors,
        isValid: errors.length === 0,
      };
    }
  };

  const onDrop = useCallback((acceptedFiles: File[]) => {
    const file = acceptedFiles[0];
    if (!file) return;

    setFileName(file.name);
    setImportResult(null);

    Papa.parse(file, {
      header: true,
      skipEmptyLines: true,
      complete: (results) => {
        const rows: ParsedRow[] = results.data.map((row: any, index: number) =>
          validateRow(row, index + 2, activeTab)
        );
        setParsedRows(rows);
      },
      error: (err) => {
        toast({ title: "Parse Error", description: err.message, variant: "destructive" });
      },
    });
  }, [toast, activeTab]);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: { "text/csv": [".csv"] },
    multiple: false,
  });

  const handleImport = () => {
    const validRows = parsedRows.filter((r) => r.isValid);
    if (validRows.length === 0) {
      toast({ title: "No valid rows", description: "Please fix errors before importing", variant: "destructive" });
      return;
    }
    setImporting(true);

    if (activeTab === "Buyers") {
      const buyers = validRows.map((row) => ({
        companyName: row.companyName!,
        contactPerson: row.contactPerson || "",
        phone: row.phone || "",
        gstNumber: row.gstNumber || "",
        creditLimit: row.creditLimit || 0,
        address: row.address || "",
        city: row.city || "",
        state: row.state || "",
        stateCode: row.stateCode || "",
      }));
      importBuyerMutation.mutate({ buyers });
    } else {
      const transactions = validRows.map((row) => ({
        buyerName: row.buyer_name!.trim(),
        bookType: row.book_type!.toUpperCase() as "CC" | "CS",
        transactionDate: row.transaction_date!.trim(),
        dueDate: row.due_date?.trim() || undefined,
        transactionType: row.transaction_type as "sale" | "payment",
        quantity: parseInt(row.quantity || "0"),
        amount: parseFloat(row.amount || "0"),
        checkNumber: row.check_number?.trim() || undefined,
        includeInReporting: row.include_in_reporting?.toUpperCase() !== "FALSE",
      }));
      bulkCreateMutation.mutate({ transactions });
    }
  };

  const validCount = parsedRows.filter((r) => r.isValid).length;
  const invalidCount = parsedRows.filter((r) => !r.isValid).length;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold text-[#1e2a4a]">Bulk Upload</h1>
          <p className="text-sm text-[#3d4f6f] mt-1">Import data via CSV</p>
        </div>
      </div>

      <div className="flex gap-2 border-b border-[#e8e0d4] pb-2">
        <button
          onClick={() => { setActiveTab("Transactions"); setParsedRows([]); setFileName(""); setImportResult(null); }}
          className={`px-4 py-2 text-sm font-semibold transition-colors border-b-2 ${
            activeTab === "Transactions" ? "border-[#c4703f] text-[#c4703f]" : "border-transparent text-[#3d4f6f] hover:text-[#1e2a4a]"
          }`}
        >
          Transactions
        </button>
        <button
          onClick={() => { setActiveTab("Buyers"); setParsedRows([]); setFileName(""); setImportResult(null); }}
          className={`px-4 py-2 text-sm font-semibold transition-colors border-b-2 ${
            activeTab === "Buyers" ? "border-[#c4703f] text-[#c4703f]" : "border-transparent text-[#3d4f6f] hover:text-[#1e2a4a]"
          }`}
        >
          Buyers
        </button>
      </div>

      {/* Template Download */}
      <Card className="border-[#d9cfc0] bg-white">
        <CardHeader>
          <CardTitle className="text-base font-semibold text-[#1e2a4a]">Step 1: Download Template</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-[#3d4f6f]">
            Download the CSV template file and fill in your transaction data. Ensure all required fields are completed.
          </p>

          {/* Field Reference Table */}
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="bg-[#f5f0e8] border-b border-[#e8e0d4]">
                  <th className="text-left py-2 px-3 font-semibold">Column</th>
                  <th className="text-left py-2 px-3 font-semibold">Required</th>
                  <th className="text-left py-2 px-3 font-semibold">Format</th>
                  <th className="text-left py-2 px-3 font-semibold">Example</th>
                </tr>
              </thead>
              <tbody>
                {activeTab === "Transactions" ? (
                  <>
                    <tr className="border-b border-[#f5f0e8]"><td className="py-1.5 px-3 font-mono">buyer_name</td><td className="py-1.5 px-3 text-red-600 font-semibold">Yes</td><td className="py-1.5 px-3">Text</td><td className="py-1.5 px-3 font-mono">ABC Garments</td></tr>
                    <tr className="border-b border-[#f5f0e8]"><td className="py-1.5 px-3 font-mono">book_type</td><td className="py-1.5 px-3 text-red-600 font-semibold">Yes</td><td className="py-1.5 px-3">CC or CS</td><td className="py-1.5 px-3 font-mono">CC</td></tr>
                    <tr className="border-b border-[#f5f0e8]"><td className="py-1.5 px-3 font-mono">transaction_date</td><td className="py-1.5 px-3 text-red-600 font-semibold">Yes</td><td className="py-1.5 px-3">DD/MM/YYYY</td><td className="py-1.5 px-3 font-mono">26/07/2026</td></tr>
                    <tr className="border-b border-[#f5f0e8]"><td className="py-1.5 px-3 font-mono">due_date</td><td className="py-1.5 px-3 text-[#3d4f6f]">No</td><td className="py-1.5 px-3">DD/MM/YYYY</td><td className="py-1.5 px-3 font-mono">25/08/2026</td></tr>
                    <tr className="border-b border-[#f5f0e8]"><td className="py-1.5 px-3 font-mono">transaction_type</td><td className="py-1.5 px-3 text-red-600 font-semibold">Yes</td><td className="py-1.5 px-3">sale or payment</td><td className="py-1.5 px-3 font-mono">sale</td></tr>
                    <tr className="border-b border-[#f5f0e8]"><td className="py-1.5 px-3 font-mono">quantity</td><td className="py-1.5 px-3 text-[#3d4f6f]">For sales</td><td className="py-1.5 px-3">Number</td><td className="py-1.5 px-3 font-mono">50</td></tr>
                    <tr className="border-b border-[#f5f0e8]"><td className="py-1.5 px-3 font-mono">amount</td><td className="py-1.5 px-3 text-red-600 font-semibold">Yes</td><td className="py-1.5 px-3">Decimal</td><td className="py-1.5 px-3 font-mono">5000.00</td></tr>
                    <tr className="border-b border-[#f5f0e8]"><td className="py-1.5 px-3 font-mono">check_number</td><td className="py-1.5 px-3 text-[#3d4f6f]">No</td><td className="py-1.5 px-3">Text</td><td className="py-1.5 px-3 font-mono">CHQ123456</td></tr>
                    <tr className="border-b border-[#f5f0e8]"><td className="py-1.5 px-3 font-mono">include_in_reporting</td><td className="py-1.5 px-3 text-[#3d4f6f]">No</td><td className="py-1.5 px-3">TRUE or FALSE</td><td className="py-1.5 px-3 font-mono">TRUE</td></tr>
                  </>
                ) : (
                  <>
                    <tr className="border-b border-[#f5f0e8]"><td className="py-1.5 px-3 font-mono">companyName</td><td className="py-1.5 px-3 text-red-600 font-semibold">Yes</td><td className="py-1.5 px-3">Text</td><td className="py-1.5 px-3 font-mono">ABC Garments</td></tr>
                    <tr className="border-b border-[#f5f0e8]"><td className="py-1.5 px-3 font-mono">contactPerson</td><td className="py-1.5 px-3 text-[#3d4f6f]">No</td><td className="py-1.5 px-3">Text</td><td className="py-1.5 px-3 font-mono">John Doe</td></tr>
                    <tr className="border-b border-[#f5f0e8]"><td className="py-1.5 px-3 font-mono">phone</td><td className="py-1.5 px-3 text-[#3d4f6f]">No</td><td className="py-1.5 px-3">Text</td><td className="py-1.5 px-3 font-mono">9876543210</td></tr>
                    <tr className="border-b border-[#f5f0e8]"><td className="py-1.5 px-3 font-mono">gstNumber</td><td className="py-1.5 px-3 text-[#3d4f6f]">No</td><td className="py-1.5 px-3">Text</td><td className="py-1.5 px-3 font-mono">27AAAAA0000A1Z5</td></tr>
                    <tr className="border-b border-[#f5f0e8]"><td className="py-1.5 px-3 font-mono">creditLimit</td><td className="py-1.5 px-3 text-[#3d4f6f]">No</td><td className="py-1.5 px-3">Decimal</td><td className="py-1.5 px-3 font-mono">100000</td></tr>
                    <tr className="border-b border-[#f5f0e8]"><td className="py-1.5 px-3 font-mono">address</td><td className="py-1.5 px-3 text-[#3d4f6f]">No</td><td className="py-1.5 px-3">Text</td><td className="py-1.5 px-3 font-mono">123 Market St</td></tr>
                    <tr className="border-b border-[#f5f0e8]"><td className="py-1.5 px-3 font-mono">city</td><td className="py-1.5 px-3 text-[#3d4f6f]">No</td><td className="py-1.5 px-3">Text</td><td className="py-1.5 px-3 font-mono">Mumbai</td></tr>
                    <tr className="border-b border-[#f5f0e8]"><td className="py-1.5 px-3 font-mono">state</td><td className="py-1.5 px-3 text-[#3d4f6f]">No</td><td className="py-1.5 px-3">Text</td><td className="py-1.5 px-3 font-mono">Maharashtra</td></tr>
                    <tr className="border-b border-[#f5f0e8]"><td className="py-1.5 px-3 font-mono">stateCode</td><td className="py-1.5 px-3 text-[#3d4f6f]">No</td><td className="py-1.5 px-3">Text</td><td className="py-1.5 px-3 font-mono">27</td></tr>
                  </>
                )}
              </tbody>
            </table>
          </div>

          <Button onClick={downloadTemplate} variant="outline" className="border-[#c4703f] text-[#c4703f] hover:bg-[#c4703f]/10">
            <Download className="w-4 h-4 mr-2" />
            Download CSV Template
          </Button>
        </CardContent>
      </Card>

      {/* Upload Zone */}
      {!importResult && (
        <Card className="border-[#d9cfc0] bg-white">
          <CardHeader>
            <CardTitle className="text-base font-semibold text-[#1e2a4a]">Step 2: Upload CSV File</CardTitle>
          </CardHeader>
          <CardContent>
            {!fileName ? (
              <div
                {...getRootProps()}
                className={`border-2 border-dashed rounded-xl h-[200px] flex flex-col items-center justify-center cursor-pointer transition-colors ${
                  isDragActive
                    ? "border-[#c4703f] bg-[#c4703f]/5"
                    : "border-[#d9cfc0] hover:border-[#c4703f]/50 hover:bg-[#f5f0e8]/50"
                }`}
              >
                <input {...getInputProps()} />
                <Upload className={`w-12 h-12 mb-3 ${isDragActive ? "text-[#c4703f]" : "text-[#d9cfc0]"}`} />
                <p className="text-sm text-[#1e2a4a] font-medium">
                  {isDragActive ? "Drop the CSV file here" : "Drag & drop your CSV file here"}
                </p>
                <p className="text-xs text-[#3d4f6f] mt-1">or click to browse</p>
              </div>
            ) : (
              <div className="space-y-4">
                <div className="flex items-center gap-3 p-4 bg-[#f5f0e8] rounded-lg">
                  <FileSpreadsheet className="w-8 h-8 text-green-600" />
                  <div className="flex-1">
                    <p className="text-sm font-medium text-[#1e2a4a]">{fileName}</p>
                    <p className="text-xs text-[#3d4f6f]">{parsedRows.length} rows found</p>
                  </div>
                  <button onClick={() => { setFileName(""); setParsedRows([]); }} className="p-1.5 rounded hover:bg-[#e8e0d4] transition-colors">
                    <X className="w-4 h-4 text-[#3d4f6f]" />
                  </button>
                </div>

                {/* Validation Summary */}
                {parsedRows.length > 0 && (
                  <div className="flex gap-4">
                    <div className="flex items-center gap-2 px-3 py-2 bg-green-50 rounded-lg">
                      <CheckCircle2 className="w-4 h-4 text-green-600" />
                      <span className="text-sm text-green-700 font-medium">{validCount} valid</span>
                    </div>
                    {invalidCount > 0 && (
                      <div className="flex items-center gap-2 px-3 py-2 bg-red-50 rounded-lg">
                        <AlertTriangle className="w-4 h-4 text-red-600" />
                        <span className="text-sm text-red-700 font-medium">{invalidCount} invalid</span>
                      </div>
                    )}
                  </div>
                )}

                {/* Preview Table */}
                {parsedRows.length > 0 && (
                  <div className="overflow-x-auto max-h-[400px]">
                    {activeTab === "Transactions" ? (
                    <table className="w-full text-xs">
                      <thead className="sticky top-0">
                        <tr className="bg-[#f5f0e8] border-b border-[#e8e0d4]">
                          <th className="text-left py-2 px-2 font-semibold">Row</th>
                          <th className="text-left py-2 px-2 font-semibold">Buyer</th>
                          <th className="text-left py-2 px-2 font-semibold">Book</th>
                          <th className="text-left py-2 px-2 font-semibold">Date</th>
                          <th className="text-left py-2 px-2 font-semibold">Type</th>
                          <th className="text-right py-2 px-2 font-semibold">Qty</th>
                          <th className="text-right py-2 px-2 font-semibold">Amount</th>
                          <th className="text-left py-2 px-2 font-semibold">Status</th>
                        </tr>
                      </thead>
                      <tbody>
                        {parsedRows.slice(0, 20).map((row) => (
                          <tr key={row.rowNumber} className={`border-b border-[#f5f0e8] ${!row.isValid ? "bg-red-50" : ""}`}>
                            <td className="py-2 px-2 font-mono text-[#3d4f6f]">{row.rowNumber}</td>
                            <td className="py-2 px-2 font-medium text-[#1e2a4a]">{row.buyer_name}</td>
                            <td className="py-2 px-2">
                              <span className={`px-1.5 py-0.5 rounded text-[10px] font-semibold ${row.book_type?.toUpperCase() === "CC" ? "bg-blue-100 text-blue-700" : row.book_type?.toUpperCase() === "CS" ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"}`}>
                                {row.book_type?.toUpperCase() || "?"}
                              </span>
                            </td>
                            <td className="py-2 px-2 text-[#3d4f6f]">{row.transaction_date}</td>
                            <td className="py-2 px-2">
                              <span className={`px-1.5 py-0.5 rounded text-[10px] font-semibold ${row.transaction_type === "sale" ? "bg-red-100 text-red-700" : row.transaction_type === "payment" ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"}`}>
                                {row.transaction_type || "?"}
                              </span>
                            </td>
                            <td className="py-2 px-2 text-right font-mono">{row.quantity}</td>
                            <td className="py-2 px-2 text-right font-mono">{row.amount}</td>
                            <td className="py-2 px-2">
                              {row.isValid ? (
                                <CheckCircle2 className="w-4 h-4 text-green-500" />
                              ) : (
                                <div className="flex items-center gap-1 text-red-600">
                                  <AlertTriangle className="w-3 h-3" />
                                  <span className="text-[10px]">{row.errors[0]}</span>
                                </div>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
) : (
                    <table className="w-full text-xs">
                      <thead className="sticky top-0">
                        <tr className="bg-[#f5f0e8] border-b border-[#e8e0d4]">
                          <th className="text-left py-2 px-2 font-semibold">Row</th>
                          <th className="text-left py-2 px-2 font-semibold">Company Name</th>
                          <th className="text-left py-2 px-2 font-semibold">Contact</th>
                          <th className="text-left py-2 px-2 font-semibold">Phone</th>
                          <th className="text-left py-2 px-2 font-semibold">GST</th>
                          <th className="text-left py-2 px-2 font-semibold">Status</th>
                        </tr>
                      </thead>
                      <tbody>
                        {parsedRows.slice(0, 20).map((row) => (
                          <tr key={row.rowNumber} className={`border-b border-[#f5f0e8] ${!row.isValid ? "bg-red-50" : ""}`}>
                            <td className="py-2 px-2 font-mono text-[#3d4f6f]">{row.rowNumber}</td>
                            <td className="py-2 px-2 font-medium text-[#1e2a4a]">{row.companyName}</td>
                            <td className="py-2 px-2 text-[#3d4f6f]">{row.contactPerson}</td>
                            <td className="py-2 px-2 text-[#3d4f6f]">{row.phone}</td>
                            <td className="py-2 px-2 text-[#3d4f6f]">{row.gstNumber}</td>
                            <td className="py-2 px-2">
                              {row.isValid ? (
                                <CheckCircle2 className="w-4 h-4 text-green-500" />
                              ) : (
                                <div className="flex items-center gap-1 text-red-600">
                                  <AlertTriangle className="w-3 h-3" />
                                  <span className="text-[10px]">{row.errors[0]}</span>
                                </div>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
)}
                    {parsedRows.length > 20 && (
                      <p className="text-xs text-[#3d4f6f] text-center py-2">
                        Showing first 20 of {parsedRows.length} rows
                      </p>
                    )}
                  </div>
                )}

                {validCount > 0 && (
                  <Button
                    onClick={handleImport}
                    disabled={importing}
                    className="bg-[#c4703f] hover:bg-[#a85d32] text-white w-full"
                  >
                    {importing ? "Importing..." : `Import ${validCount} Valid Rows`}
                  </Button>
                )}
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* Import Results */}
      {importResult && (
        <Card className="border-green-300 bg-green-50">
          <CardHeader>
            <CardTitle className="text-base font-semibold text-green-800 flex items-center gap-2">
              <CheckCircle2 className="w-5 h-5" />
              Import Complete
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="grid grid-cols-3 gap-4">
              <div className="bg-white rounded-lg p-4 text-center">
                <p className="text-2xl font-bold text-green-600">{importResult.imported}</p>
                <p className="text-xs text-[#3d4f6f]">{activeTab === "Transactions" ? "Transactions Imported" : "Buyers Imported"}</p>
              </div>
              <div className="bg-white rounded-lg p-4 text-center">
                <p className="text-2xl font-bold text-blue-600">{importResult.newBuyers}</p>
                {activeTab === "Transactions" && <p className="text-xs text-[#3d4f6f]">New Buyers Created</p>}
              </div>
              <div className="bg-white rounded-lg p-4 text-center">
                <p className="text-2xl font-bold text-red-600">{importResult.failed}</p>
                <p className="text-xs text-[#3d4f6f]">Failed Imports</p>
              </div>
            </div>
            {importResult.errors?.length > 0 && (
              <div className="bg-white rounded-lg p-4">
                <p className="text-sm font-medium text-red-600 mb-2">Errors:</p>
                <div className="space-y-1 max-h-[150px] overflow-y-auto">
                  {importResult.errors.map((err: any, idx: number) => (
                    <p key={idx} className="text-xs text-red-600">Row {err.row}: {err.message}</p>
                  ))}
                </div>
              </div>
            )}
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => { setImportResult(null); setFileName(""); setParsedRows([]); }} className="border-[#d9cfc0]">
                Upload Another File
              </Button>
              <Button onClick={() => window.location.href = activeTab === "Transactions" ? "/transactions" : "/buyers"} className="bg-[#c4703f] hover:bg-[#a85d32] text-white">
                {activeTab === "Transactions" ? "View Transactions" : "View Buyers"}
              </Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
