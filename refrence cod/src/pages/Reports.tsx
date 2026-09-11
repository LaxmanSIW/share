import { useState } from "react";
import {
  AlertCircle,
  FileText,
  Download,
  BarChart3,
  TrendingUp,
  Truck,
  Tag,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { trpc } from "@/providers/trpc";
import { useTableState } from "@/hooks/useTableState";
import { SortableHeader } from "@/components/SortableHeader";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
} from "recharts";

type ReportTab =
  | "outstanding"
  | "statement"
  | "movement"
  | "sales"
  | "items"
  | "itemMovement"
  | "categories"
  | "gst"
  | "transport";

export default function Reports() {
  const [activeTab, setActiveTab] = useState<ReportTab>("outstanding");

  const tabs = [
    { key: "outstanding" as ReportTab, label: "Outstanding Report", icon: AlertCircle },
    { key: "statement" as ReportTab, label: "Buyer Statement", icon: FileText },
    { key: "movement" as ReportTab, label: "Trouser Movement", icon: BarChart3 },
    { key: "sales" as ReportTab, label: "Sales (Monthly / Weekly)", icon: TrendingUp },
    { key: "itemMovement" as ReportTab, label: "Item Movement", icon: BarChart3 },
    { key: "items" as ReportTab, label: "Item Performance", icon: BarChart3 },
    { key: "categories" as ReportTab, label: "Category Breakdown", icon: Tag },
    { key: "gst" as ReportTab, label: "GST / Tax Summary", icon: FileText },
    { key: "transport" as ReportTab, label: "Transport Performance", icon: Truck },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold text-[#1e2a4a]">Reports & Analytics</h1>
        <p className="text-sm text-[#3d4f6f] mt-1">Comprehensive financial and item movement analytics</p>
      </div>

      {/* Tabs */}
      <div className="flex gap-2 border-b border-[#d9cfc0] overflow-x-auto pb-0">
        {tabs.map((tab) => {
          const Icon = tab.icon;
          return (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`flex items-center gap-2 px-4 py-3 text-sm font-medium border-b-2 whitespace-nowrap transition-all ${
                activeTab === tab.key
                  ? "border-[#c4703f] text-[#c4703f]"
                  : "border-transparent text-[#3d4f6f] hover:text-[#1e2a4a]"
              }`}
            >
              <Icon className="w-4 h-4" />
              {tab.label}
            </button>
          );
        })}
      </div>

      {/* Content */}
      {activeTab === "outstanding" && <OutstandingReport />}
      {activeTab === "statement" && <BuyerStatementReport />}
      {activeTab === "movement" && <TrouserMovementReport />}
      {activeTab === "sales" && <SalesReport />}
      {activeTab === "itemMovement" && <ItemMovementReport />}
      {activeTab === "items" && <ItemPerformanceReport />}
      {activeTab === "categories" && <CategoryStatisticsReport />}
      {activeTab === "gst" && <GstTaxSummaryReport />}
      {activeTab === "transport" && <TransportPerformanceReport />}
    </div>
  );
}

function Label({ children, className, ...props }: React.LabelHTMLAttributes<HTMLLabelElement>) {
  return <label className={`text-sm font-medium ${className}`} {...props}>{children}</label>;
}

// ─── Outstanding Report ──────────────────────────────────
function OutstandingReport() {
  const [bookType, setBookType] = useState<"ALL" | "CC" | "CS">("ALL");
  const [riskLevel, setRiskLevel] = useState<"ALL" | "High" | "Medium" | "Low">("ALL");

  const { data, isLoading } = trpc.report.outstanding.useQuery({
    bookType,
    riskLevel,
  });

  const table = useTableState({
    data: (data as any[]) || [],
    searchFields: ["companyName"],
    defaultSortKey: "outstanding",
    defaultSortDirection: "desc",
  });

  const totalOutstanding = table.filteredData?.reduce((sum: number, item: any) => sum + (item.outstanding || 0), 0) || 0;

  const exportCSV = () => {
    if (!data) return;
    const headers = ["Buyer", "Book Type", "Total Parcels", "Total Sales", "Total Paid", "Outstanding", "Days Overdue", "Risk"];
    const rows = table.filteredData.map((item: any) => [
      item.companyName,
      item.bookType,
      item.totalParcels || 0,
      item.totalsales,
      item.totalPaid,
      item.outstanding,
      item.daysOverdue,
      `${item.riskScore} (${item.riskLevel})`,
    ]);
    const csv = [headers, ...rows].map((r) => r.join(",")).join("\n");
    const blob = new Blob([csv], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `outstanding-report-${new Date().toISOString().split("T")[0]}.csv`;
    a.click();
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-3">
        <div className="flex items-center gap-2 bg-[#e8e0d4] rounded-full p-1">
          {(["ALL", "CC", "CS"] as const).map((type) => (
            <button
              key={type}
              onClick={() => setBookType(type)}
              className={`px-3 py-1 rounded-full text-xs font-medium transition-all ${
                bookType === type ? "bg-white text-[#1e2a4a] shadow-sm" : "text-[#3d4f6f]"
              }`}
            >
              {type === "ALL" ? "All Books" : `Alpha ${type}`}
            </button>
          ))}
        </div>
        <div className="flex items-center gap-2 bg-[#e8e0d4] rounded-full p-1">
          {(["ALL", "High", "Medium", "Low"] as const).map((level) => (
            <button
              key={level}
              onClick={() => setRiskLevel(level)}
              className={`px-3 py-1 rounded-full text-xs font-medium transition-all ${
                riskLevel === level ? "bg-white text-[#1e2a4a] shadow-sm" : "text-[#3d4f6f]"
              }`}
            >
              {level === "ALL" ? "All Risk" : level}
            </button>
          ))}
        </div>
        <Button variant="outline" size="sm" onClick={exportCSV} className="border-[#d9cfc0] text-xs ml-auto">
          <Download className="w-3 h-3 mr-1" />
          Export CSV
        </Button>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <Card className="border-[#d9cfc0] bg-white">
          <CardContent className="p-4">
            <p className="text-xs text-[#3d4f6f] uppercase">Total Outstanding</p>
            <p className="text-2xl font-semibold font-mono text-red-600 mt-1">
              ₹ {totalOutstanding.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
            </p>
          </CardContent>
        </Card>
        <Card className="border-[#d9cfc0] bg-white">
          <CardContent className="p-4">
            <p className="text-xs text-[#3d4f6f] uppercase">Filtered Buyers</p>
            <p className="text-2xl font-semibold font-mono text-[#1e2a4a] mt-1">{table.filteredData?.length || 0}</p>
          </CardContent>
        </Card>
      </div>

      <Card className="border-[#d9cfc0] bg-white">
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-[#f5f0e8] border-b border-[#e8e0d4] text-xs text-[#3d4f6f] uppercase tracking-wider">
                  <SortableHeader label="Buyer" sortKey="companyName" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} />
                  <SortableHeader label="Total Parcels" sortKey="totalParcels" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="center" />
                  <SortableHeader label="Sales" sortKey="totalsales" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                  <SortableHeader label="Paid" sortKey="totalPaid" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                  <SortableHeader label="Outstanding" sortKey="outstanding" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                  <SortableHeader label="Days Overdue" sortKey="daysOverdue" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                  <SortableHeader label="Risk" sortKey="riskScore" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="center" />
                </tr>
              </thead>
              <tbody>
                {isLoading ? (
                  Array.from({ length: 5 }).map((_, i) => (
                    <tr key={i}>
                      {Array.from({ length: 7 }).map((_, j) => (
                        <td key={j} className="py-3 px-4"><div className="h-4 bg-[#e8e0d4] rounded animate-pulse" /></td>
                      ))}
                    </tr>
                  ))
                ) : (
                  table.filteredData?.map((item: any) => (
                    <tr key={item.buyerId} className="border-b border-[#f5f0e8] hover:bg-[#f5f0e8]/50">
                      <td className="py-3 px-4 text-sm font-medium text-[#1e2a4a]">{item.companyName}</td>
                      <td className="py-3 px-4 text-sm font-bold text-center text-orange-700 font-mono">{item.totalParcels || 0}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono">₹ {item.totalsales.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono">₹ {item.totalPaid.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono font-semibold text-red-600">
                        ₹ {item.outstanding.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                      </td>
                      <td className="py-3 px-4 text-sm text-right font-mono">
                        {item.daysOverdue > 0 ? <span className="text-red-600">{item.daysOverdue} days</span> : <span className="text-green-600">On time</span>}
                      </td>
                      <td className="py-3 px-4 text-center">
                        <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-bold ${
                          item.riskScore <= 3 ? "bg-red-500 text-white" :
                          item.riskScore <= 7 ? "bg-yellow-500 text-white" : "bg-green-500 text-white"
                        }`}>
                          {item.riskScore} - {item.riskLevel}
                        </span>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

// ─── Buyer Statement Report ──────────────────────────────
function BuyerStatementReport() {
  const [selectedBuyerId, setSelectedBuyerId] = useState<number | null>(null);
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");

  const { data: buyersList } = trpc.buyer.list.useQuery({});
  const { data: statement } = trpc.report.buyerStatement.useQuery(
    { buyerId: selectedBuyerId!, startDate: startDate || undefined, endDate: endDate || undefined },
    { enabled: !!selectedBuyerId }
  );

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
    const csv = [headers, ...rows].map((r) => r.join(",")).join("\n");
    const blob = new Blob([csv], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `statement-${statement.buyer.companyName}-${new Date().toISOString().split("T")[0]}.csv`;
    a.click();
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-3 items-end">
        <div className="space-y-1">
          <Label className="text-xs text-[#3d4f6f]">Buyer</Label>
          <select
            value={selectedBuyerId || ""}
            onChange={(e) => setSelectedBuyerId(Number(e.target.value) || null)}
            className="px-3 py-2 rounded-md border border-[#d9cfc0] bg-white text-sm min-w-[200px]"
          >
            <option value="">Select buyer...</option>
            {buyersList?.items?.map((b: any) => (
              <option key={b.id} value={b.id}>{b.companyName}</option>
            ))}
          </select>
        </div>
        <div className="space-y-1">
          <Label className="text-xs text-[#3d4f6f]">From</Label>
          <Input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} className="w-40 bg-white border-[#d9cfc0]" />
        </div>
        <div className="space-y-1">
          <Label className="text-xs text-[#3d4f6f]">To</Label>
          <Input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} className="w-40 bg-white border-[#d9cfc0]" />
        </div>
        <div className="flex gap-2 ml-auto">
          <Button variant="outline" size="sm" onClick={exportCSV} disabled={!statement} className="border-[#d9cfc0] text-xs">
            <Download className="w-3 h-3 mr-1" /> CSV
          </Button>
        </div>
      </div>

      {statement && (
        <Card className="border-[#d9cfc0] bg-white">
          <CardHeader className="pb-2">
            <CardTitle className="text-base font-semibold text-[#1e2a4a]">{statement.buyer.companyName}</CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="bg-[#f5f0e8] border-b border-[#e8e0d4] text-xs text-[#3d4f6f] uppercase">
                    <th className="py-3 px-4 text-left font-semibold">Date</th>
                    <th className="py-3 px-4 text-left font-semibold">Description</th>
                    <th className="py-3 px-4 text-center font-semibold">Book</th>
                    <th className="py-3 px-4 text-right font-semibold">Debit</th>
                    <th className="py-3 px-4 text-right font-semibold">Credit</th>
                    <th className="py-3 px-4 text-right font-semibold">Balance</th>
                  </tr>
                </thead>
                <tbody>
                  {statement.items.map((item: any) => (
                    <tr key={item.id} className="border-b border-[#f5f0e8]">
                      <td className="py-3 px-4 text-sm font-mono">{new Date(item.date).toLocaleDateString("en-IN")}</td>
                      <td className="py-3 px-4 text-sm text-[#1e2a4a]">{item.description}</td>
                      <td className="py-3 px-4 text-center"><span className="text-xs font-bold px-2 py-0.5 rounded bg-blue-100 text-blue-700">{item.bookType}</span></td>
                      <td className="py-3 px-4 text-sm text-right font-mono text-red-600">{item.debit ? `₹ ${item.debit.toLocaleString("en-IN", { minimumFractionDigits: 2 })}` : "-"}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono text-green-600">{item.credit ? `₹ ${item.credit.toLocaleString("en-IN", { minimumFractionDigits: 2 })}` : "-"}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono font-semibold">₹ {item.balance.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

// ─── Trouser Movement Report ─────────────────────────────
function TrouserMovementReport() {
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [bookType, setBookType] = useState<"ALL" | "CC" | "CS">("ALL");
  const [groupBy, setGroupBy] = useState<"Day" | "Week" | "Month" | "Buyer">("Day");

  const { data, isLoading } = trpc.report.trouserMovement.useQuery(
    {
      startDate: startDate || undefined,
      endDate: endDate || undefined,
      bookType,
      groupBy,
    },
    { enabled: true }
  );

  const exportCSV = () => {
    if (!data?.items) return;
    const headers = groupBy === "Buyer" ? ["Buyer", "CC Qty", "CS Qty", "Total", "Cumulative"] : ["Date", "CC Qty", "CS Qty", "Total", "Cumulative"];
    const rows = data.items.map((item: any) => groupBy === "Buyer"
      ? [item.buyer, item.ccQuantity, item.csQuantity, item.total, item.cumulative]
      : [item.date, item.ccQuantity, item.csQuantity, item.total, item.cumulative]
    );
    const csv = [headers, ...rows].map((r) => r.join(",")).join("\n");
    const blob = new Blob([csv], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `trouser-movement-${groupBy.toLowerCase()}-${new Date().toISOString().split("T")[0]}.csv`;
    a.click();
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-3">
        <div className="flex items-center gap-2 bg-[#e8e0d4] rounded-full p-1">
          {(["ALL", "CC", "CS"] as const).map((type) => (
            <button key={type} onClick={() => setBookType(type)} className={`px-3 py-1 rounded-full text-xs font-medium transition-all ${bookType === type ? "bg-white text-[#1e2a4a] shadow-sm" : "text-[#3d4f6f]"}`}>
              {type === "ALL" ? "All" : type}
            </button>
          ))}
        </div>
        <select value={groupBy} onChange={(e) => setGroupBy(e.target.value as any)} className="px-3 py-1.5 rounded-md border border-[#d9cfc0] bg-white text-sm">
          {(["Day", "Week", "Month", "Buyer"] as const).map((g) => <option key={g} value={g}>{g}</option>)}
        </select>
        <Input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} className="w-36 bg-white border-[#d9cfc0] text-sm" />
        <Input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} className="w-36 bg-white border-[#d9cfc0] text-sm" />
        <Button variant="outline" size="sm" onClick={exportCSV} disabled={!data?.items} className="border-[#d9cfc0] text-xs ml-auto">
          <Download className="w-3 h-3 mr-1" /> CSV
        </Button>
      </div>

      {data?.summary && (
        <div className="grid grid-cols-4 gap-4">
          <Card className="border-[#d9cfc0] bg-white"><CardContent className="p-4">
            <p className="text-xs text-[#3d4f6f] uppercase">Total Pieces</p>
            <p className="text-xl font-semibold font-mono">{data.summary.totalPieces.toLocaleString("en-IN")}</p>
          </CardContent></Card>
          <Card className="border-[#d9cfc0] bg-white"><CardContent className="p-4">
            <p className="text-xs text-[#3d4f6f] uppercase">Avg per Day</p>
            <p className="text-xl font-semibold font-mono">{data.summary.averagePerDay}</p>
          </CardContent></Card>
          <Card className="border-[#d9cfc0] bg-white"><CardContent className="p-4">
            <p className="text-xs text-[#3d4f6f] uppercase">Peak</p>
            <p className="text-sm font-semibold">{data.summary.peakDay}</p>
          </CardContent></Card>
          <Card className="border-[#d9cfc0] bg-white"><CardContent className="p-4">
            <p className="text-xs text-[#3d4f6f] uppercase">Peak Count</p>
            <p className="text-xl font-semibold font-mono">{data.summary.peakCount.toLocaleString("en-IN")}</p>
          </CardContent></Card>
        </div>
      )}

      {data?.items && data.items.length > 0 && (
        <Card className="border-[#d9cfc0] bg-white p-4">
          <ResponsiveContainer width="100%" height={350}>
            <BarChart data={data.items}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e8e0d4" />
              <XAxis dataKey={groupBy === "Buyer" ? "buyer" : "date"} tick={{ fontSize: 11, fill: "#3d4f6f" }} />
              <YAxis tick={{ fontSize: 11, fill: "#3d4f6f" }} />
              <Tooltip contentStyle={{ backgroundColor: "white", border: "1px solid #d9cfc0", borderRadius: "8px", fontSize: "12px" }} />
              <Legend wrapperStyle={{ fontSize: "12px" }} />
              <Bar dataKey="ccQuantity" name="CC" fill="#3b82f6" radius={[2, 2, 0, 0]} />
              <Bar dataKey="csQuantity" name="CS" fill="#22c55e" radius={[2, 2, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </Card>
      )}

      <Card className="border-[#d9cfc0] bg-white">
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-[#f5f0e8] border-b border-[#e8e0d4] text-xs text-[#3d4f6f] uppercase">
                  <th className="py-3 px-4 text-left font-semibold">{groupBy === "Buyer" ? "Buyer" : "Date"}</th>
                  <th className="py-3 px-4 text-right font-semibold">CC Qty</th>
                  <th className="py-3 px-4 text-right font-semibold">CS Qty</th>
                  <th className="py-3 px-4 text-right font-semibold">Total</th>
                  <th className="py-3 px-4 text-right font-semibold">Cumulative</th>
                </tr>
              </thead>
              <tbody>
                {isLoading ? Array.from({ length: 5 }).map((_, i) => <tr key={i}>{Array.from({ length: 5 }).map((_, j) => <td key={j} className="py-3 px-4"><div className="h-4 bg-[#e8e0d4] rounded animate-pulse" /></td>)}</tr>) :
                  data?.items?.map((item: any, i: number) => (
                    <tr key={i} className="border-b border-[#f5f0e8] hover:bg-[#f5f0e8]/50">
                      <td className="py-3 px-4 text-sm text-[#1e2a4a]">{groupBy === "Buyer" ? item.buyer : new Date(item.date).toLocaleDateString("en-IN")}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono">{item.ccQuantity.toLocaleString("en-IN")}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono">{item.csQuantity.toLocaleString("en-IN")}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono font-semibold">{item.total.toLocaleString("en-IN")}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono text-[#3d4f6f]">{item.cumulative.toLocaleString("en-IN")}</td>
                    </tr>
                  ))
                }
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

// ─── Sales (Monthly / Weekly) Report ─────────────────────
function SalesReport() {
  const [period, setPeriod] = useState<"Monthly" | "Weekly">("Monthly");
  const [paymentType, setPaymentType] = useState<"ALL" | "CC" | "CS">("ALL");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [buyerId, setBuyerId] = useState<number | null>(null);

  const { data: buyersList } = trpc.buyer.list.useQuery({});
  const { data: salesData, isLoading } = trpc.report.salesPeriod.useQuery(
    {
      period,
      paymentType: paymentType as any,
      startDate: startDate || undefined,
      endDate: endDate || undefined,
      buyerId: buyerId || undefined,
    },
    { enabled: true }
  );

  const table = useTableState({
    data: (salesData?.items as any[]) || [],
    searchFields: ["period"],
    defaultSortKey: "period",
    defaultSortDirection: "desc",
  });

  return (
    <div className="space-y-4">
      {/* Filters */}
      <div className="flex flex-wrap gap-3 items-end">
        <div className="flex items-center gap-2 bg-[#e8e0d4] rounded-full p-1">
          {(["Monthly", "Weekly"] as const).map((p) => (
            <button
              key={p}
              onClick={() => setPeriod(p)}
              className={`px-3 py-1 rounded-full text-xs font-medium transition-all ${
                period === p ? "bg-white text-[#1e2a4a] shadow-sm" : "text-[#3d4f6f]"
              }`}
            >
              {p}
            </button>
          ))}
        </div>
        <div className="flex items-center gap-2 bg-[#e8e0d4] rounded-full p-1">
          {(["ALL", "CC", "CS"] as const).map((pt) => (
            <button
              key={pt}
              onClick={() => setPaymentType(pt)}
              className={`px-3 py-1 rounded-full text-xs font-medium transition-all ${
                paymentType === pt ? "bg-white text-[#1e2a4a] shadow-sm" : "text-[#3d4f6f]"
              }`}
            >
              {pt === "ALL" ? "All" : pt}
            </button>
          ))}
        </div>
        <div className="space-y-1">
          <Label className="text-xs text-[#3d4f6f]">From</Label>
          <Input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} className="w-36 bg-white border-[#d9cfc0] text-sm h-8" />
        </div>
        <div className="space-y-1">
          <Label className="text-xs text-[#3d4f6f]">To</Label>
          <Input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} className="w-36 bg-white border-[#d9cfc0] text-sm h-8" />
        </div>
        <div className="space-y-1">
          <Label className="text-xs text-[#3d4f6f]">Buyer</Label>
          <select value={buyerId || ""} onChange={(e) => setBuyerId(Number(e.target.value) || null)} className="px-3 py-1.5 rounded-md border border-[#d9cfc0] bg-white text-sm h-8 min-w-[160px]">
            <option value="">All Buyers</option>
            {buyersList?.items?.map((b: any) => (
              <option key={b.id} value={b.id}>{b.companyName}</option>
            ))}
          </select>
        </div>
      </div>

      {/* Summary Cards */}
      {salesData?.summary && (
        <div className="grid grid-cols-4 gap-4">
          <Card className="border-[#d9cfc0] bg-white"><CardContent className="p-4">
            <p className="text-xs text-[#3d4f6f] uppercase">Total Sales</p>
            <p className="text-xl font-semibold font-mono text-[#c4703f]">
              ₹ {salesData.summary.totalsales.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
            </p>
          </CardContent></Card>
          <Card className="border-[#d9cfc0] bg-white"><CardContent className="p-4">
            <p className="text-xs text-[#3d4f6f] uppercase">Total Payments</p>
            <p className="text-xl font-semibold font-mono text-green-600">
              ₹ {salesData.summary.totalpayments.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
            </p>
          </CardContent></Card>
          <Card className="border-[#d9cfc0] bg-white"><CardContent className="p-4">
            <p className="text-xs text-[#3d4f6f] uppercase">Net Amount</p>
            <p className="text-xl font-semibold font-mono text-[#1e2a4a]">
              ₹ {salesData.summary.netAmount.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
            </p>
          </CardContent></Card>
          <Card className="border-[#d9cfc0] bg-white"><CardContent className="p-4">
            <p className="text-xs text-[#3d4f6f] uppercase">Periods</p>
            <p className="text-xl font-semibold font-mono">{salesData.summary.periodCount}</p>
          </CardContent></Card>
        </div>
      )}

      {/* Chart */}
      {salesData?.items && salesData.items.length > 0 && (
        <Card className="border-[#d9cfc0] bg-white p-4">
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={salesData.items}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e8e0d4" />
              <XAxis dataKey="period" tick={{ fontSize: 11, fill: "#3d4f6f" }} />
              <YAxis tick={{ fontSize: 11, fill: "#3d4f6f" }} tickFormatter={(v) => `₹${(v / 1000).toFixed(0)}K`} />
              <Tooltip formatter={(value: number) => [`₹ ${value.toLocaleString("en-IN", { minimumFractionDigits: 2 })}`, ""]} contentStyle={{ backgroundColor: "white", border: "1px solid #d9cfc0", borderRadius: "8px", fontSize: "12px" }} />
              <Legend wrapperStyle={{ fontSize: "12px" }} />
              <Bar dataKey="totalsales" name="Sales" fill="#c4703f" radius={[2, 2, 0, 0]} />
              <Bar dataKey="totalpayments" name="Payments" fill="#4a9b6b" radius={[2, 2, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </Card>
      )}

      {/* Table */}
      <Card className="border-[#d9cfc0] bg-white">
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-[#f5f0e8] border-b border-[#e8e0d4] text-xs text-[#3d4f6f] uppercase">
                  <SortableHeader label="Period" sortKey="period" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} />
                  <SortableHeader label="CC Sales" sortKey="ccsales" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                  <SortableHeader label="CS Sales" sortKey="cssales" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                  <SortableHeader label="Total Sales" sortKey="totalsales" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                  <SortableHeader label="Payments" sortKey="totalpayments" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                  <SortableHeader label="Net" sortKey="netAmount" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                </tr>
              </thead>
              <tbody>
                {isLoading ? Array.from({ length: 5 }).map((_, i) => <tr key={i}>{Array.from({ length: 6 }).map((_, j) => <td key={j} className="py-3 px-4"><div className="h-4 bg-[#e8e0d4] rounded animate-pulse" /></td>)}</tr>) :
                  table.filteredData?.map((item: any, i: number) => (
                    <tr key={i} className="border-b border-[#f5f0e8] hover:bg-[#f5f0e8]/50">
                      <td className="py-3 px-4 text-sm font-medium text-[#1e2a4a]">{item.period}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono">₹ {item.ccsales.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono">₹ {item.cssales.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono font-semibold text-[#c4703f]">₹ {item.totalsales.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono text-green-600">₹ {item.totalpayments.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono font-semibold">₹ {item.netAmount.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                    </tr>
                  ))
                }
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

// ─── Item Performance Report ─────────────────────
function ItemPerformanceReport() {
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");

  const { data, isLoading } = trpc.report.itemPerformance.useQuery({
    startDate: startDate || undefined,
    endDate: endDate || undefined,
  });

  const table = useTableState({
    data: (data as any[]) || [],
    searchFields: ["name", "hsnCode", "categoryName"],
    defaultSortKey: "totalQty",
    defaultSortDirection: "desc",
  });

  const totalQuantity = table.filteredData?.reduce((sum: number, item: any) => sum + (item.totalQty || 0), 0) || 0;
  const totalRevenue = table.filteredData?.reduce((sum: number, item: any) => sum + (item.totalRevenue || 0), 0) || 0;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-3 items-end">
        <div className="space-y-1">
          <Label className="text-xs text-[#3d4f6f]">From Date</Label>
          <Input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} className="w-40 bg-white border-[#d9cfc0] text-sm h-8" />
        </div>
        <div className="space-y-1">
          <Label className="text-xs text-[#3d4f6f]">To Date</Label>
          <Input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} className="w-40 bg-white border-[#d9cfc0] text-sm h-8" />
        </div>
      </div>

      <div className="grid grid-cols-3 gap-4">
        <Card className="border-[#d9cfc0] bg-white">
          <CardContent className="p-4">
            <p className="text-xs text-[#3d4f6f] uppercase">Total Quantity Sold</p>
            <p className="text-2xl font-semibold font-mono text-[#1e2a4a] mt-1">{totalQuantity.toLocaleString("en-IN")} Pcs.</p>
          </CardContent>
        </Card>
        <Card className="border-[#d9cfc0] bg-white">
          <CardContent className="p-4">
            <p className="text-xs text-[#3d4f6f] uppercase">Total Revenue Generated</p>
            <p className="text-2xl font-semibold font-mono text-green-600 mt-1">₹ {totalRevenue.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</p>
          </CardContent>
        </Card>
        <Card className="border-[#d9cfc0] bg-white">
          <CardContent className="p-4">
            <p className="text-xs text-[#3d4f6f] uppercase">Unique Products Sold</p>
            <p className="text-2xl font-semibold font-mono text-[#c4703f] mt-1">{table.filteredData?.filter((i: any) => i.totalQty > 0).length || 0}</p>
          </CardContent>
        </Card>
      </div>

      {/* Table */}
      <Card className="border-[#d9cfc0] bg-white">
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-[#f5f0e8] border-b border-[#e8e0d4] text-xs text-[#3d4f6f] uppercase">
                  <SortableHeader label="Item Name" sortKey="name" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} />
                  <SortableHeader label="Category" sortKey="categoryName" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} />
                  <SortableHeader label="HSN" sortKey="hsnCode" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} />
                  <SortableHeader label="Qty Sold" sortKey="totalQty" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                  <SortableHeader label="Gross Sales" sortKey="totalsales" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                  <SortableHeader label="Discount" sortKey="totalDiscount" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                  <SortableHeader label="Tax" sortKey="totalTax" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                  <SortableHeader label="Total Revenue" sortKey="totalRevenue" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                </tr>
              </thead>
              <tbody>
                {isLoading ? Array.from({ length: 5 }).map((_, i) => <tr key={i}>{Array.from({ length: 8 }).map((_, j) => <td key={j} className="py-3 px-4"><div className="h-4 bg-[#e8e0d4] rounded animate-pulse" /></td>)}</tr>) :
                  table.filteredData?.map((item: any, i: number) => (
                    <tr key={i} className="border-b border-[#f5f0e8] hover:bg-[#f5f0e8]/50">
                      <td className="py-3 px-4 text-sm font-medium text-[#1e2a4a]">{item.name}</td>
                      <td className="py-3 px-4 text-sm">
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded text-xs font-semibold bg-[#f5f0e8] text-[#1e2a4a] border border-[#d9cfc0]">
                          {item.categoryName || "Uncategorized"}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-sm font-mono text-[#3d4f6f]">{item.hsnCode}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono font-semibold">{item.totalQty.toLocaleString("en-IN")}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono">₹ {item.totalsales.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono text-red-600">₹ {item.totalDiscount.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono text-[#3d4f6f]">₹ {item.totalTax.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono font-semibold text-green-600">₹ {item.totalRevenue.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                    </tr>
                  ))
                }
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

// ─── Item Movement Report ─────────────────────
function ItemMovementReport() {
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");

  const { data, isLoading } = trpc.report.itemMovement.useQuery({
    startDate: startDate || undefined,
    endDate: endDate || undefined,
  });

  const table = useTableState({
    data: (data as any[]) || [],
    searchFields: ["name", "hsnCode", "categoryName"],
    defaultSortKey: "totalQty",
    defaultSortDirection: "desc",
  });

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-3 items-end">
        <div className="space-y-1">
          <Label className="text-xs text-[#3d4f6f]">From Date</Label>
          <Input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} className="w-40 bg-white border-[#d9cfc0] text-sm h-8" />
        </div>
        <div className="space-y-1">
          <Label className="text-xs text-[#3d4f6f]">To Date</Label>
          <Input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} className="w-40 bg-white border-[#d9cfc0] text-sm h-8" />
        </div>
      </div>

      <Card className="border-[#d9cfc0] bg-white">
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-[#f5f0e8] border-b border-[#e8e0d4] text-xs text-[#3d4f6f] uppercase">
                  <SortableHeader label="Item Name" sortKey="name" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} />
                  <SortableHeader label="Category" sortKey="categoryName" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} />
                  <SortableHeader label="HSN" sortKey="hsnCode" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} />
                  <SortableHeader label="Qty Sold" sortKey="totalQty" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                  <SortableHeader label="Total Revenue" sortKey="totalRevenue" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                </tr>
              </thead>
              <tbody>
                {isLoading ? Array.from({ length: 5 }).map((_, i) => <tr key={i}>{Array.from({ length: 5 }).map((_, j) => <td key={j} className="py-3 px-4"><div className="h-4 bg-[#e8e0d4] rounded animate-pulse" /></td>)}</tr>) :
                  table.filteredData?.map((item: any, i: number) => (
                    <tr key={i} className="border-b border-[#f5f0e8] hover:bg-[#f5f0e8]/50">
                      <td className="py-3 px-4 text-sm font-medium text-[#1e2a4a]">{item.name}</td>
                      <td className="py-3 px-4 text-sm">
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded text-xs font-semibold bg-[#f5f0e8] text-[#1e2a4a] border border-[#d9cfc0]">
                          {item.categoryName || "Uncategorized"}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-sm font-mono text-[#3d4f6f]">{item.hsnCode}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono font-semibold">{item.totalQty.toLocaleString("en-IN")}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono font-semibold text-green-600">₹ {item.totalRevenue.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                    </tr>
                  ))
                }
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

// ─── Category Statistics Report ─────────────────────
function CategoryStatisticsReport() {
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");

  const { data, isLoading } = trpc.report.categoryStatistics.useQuery({
    startDate: startDate || undefined,
    endDate: endDate || undefined,
  });

  const COLORS = ["#1e2a4a", "#c4703f", "#10b981", "#8b5cf6", "#f59e0b", "#ec4899", "#6366f1"];

  const table = useTableState({
    data: ((data as any)?.items as any[]) || [],
    searchFields: ["categoryName"],
    defaultSortKey: "totalRevenue",
    defaultSortDirection: "desc",
  });

  const exportCSV = () => {
    if (!data?.items) return;
    const headers = ["Category Name", "Total Products", "Quantity Sold (Pcs)", "Total Revenue (₹)", "Revenue Share %"];
    const rows = data.items.map((item: any) => [
      item.categoryName,
      item.itemCount,
      item.totalQty,
      item.totalRevenue,
      `${item.revenuePercent || item.percentageContribution}%`,
    ]);
    const csv = [headers, ...rows].map((r) => r.join(",")).join("\n");
    const blob = new Blob([csv], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `category-statistics-${new Date().toISOString().split("T")[0]}.csv`;
    a.click();
  };

  const topCategory = data?.items && data.items.length > 0 ? data.items[0] : null;

  return (
    <div className="space-y-4">
      {/* Filters & Actions Header */}
      <div className="flex flex-wrap gap-3 items-end justify-between">
        <div className="flex flex-wrap gap-3 items-end">
          <div className="space-y-1">
            <Label className="text-xs text-[#3d4f6f]">Search Category</Label>
            <Input
              type="text"
              placeholder="Search category..."
              value={table.search}
              onChange={(e) => table.setSearch(e.target.value)}
              className="w-48 bg-white border-[#d9cfc0] text-sm h-8"
            />
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-[#3d4f6f]">From Date</Label>
            <Input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} className="w-36 bg-white border-[#d9cfc0] text-sm h-8" />
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-[#3d4f6f]">To Date</Label>
            <Input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} className="w-36 bg-white border-[#d9cfc0] text-sm h-8" />
          </div>
        </div>

        <Button variant="outline" size="sm" onClick={exportCSV} disabled={!data?.items} className="border-[#d9cfc0] text-xs h-8">
          <Download className="w-3.5 h-3.5 mr-1" /> Export CSV
        </Button>
      </div>

      {/* KPI Summary Cards */}
      {data && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <Card className="border-[#d9cfc0] bg-white"><CardContent className="p-4">
            <p className="text-xs text-[#3d4f6f] uppercase font-semibold">Total Category Revenue</p>
            <p className="text-xl font-bold font-mono text-green-600">
              ₹ {(data.totalRevenue || 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
            </p>
          </CardContent></Card>
          <Card className="border-[#d9cfc0] bg-white"><CardContent className="p-4">
            <p className="text-xs text-[#3d4f6f] uppercase font-semibold">Total Volume Sold</p>
            <p className="text-xl font-bold font-mono text-[#c4703f]">
              {(data.totalPieces || 0).toLocaleString("en-IN")} Pcs.
            </p>
          </CardContent></Card>
          <Card className="border-[#d9cfc0] bg-white"><CardContent className="p-4">
            <p className="text-xs text-[#3d4f6f] uppercase font-semibold">Active Categories</p>
            <p className="text-xl font-bold font-mono text-[#1e2a4a]">
              {data.totalCategories || 0}
            </p>
          </CardContent></Card>
          <Card className="border-[#d9cfc0] bg-white"><CardContent className="p-4">
            <p className="text-xs text-[#3d4f6f] uppercase font-semibold">Top Category</p>
            <p className="text-base font-bold text-[#1e2a4a] truncate">
              {topCategory ? `${topCategory.categoryName} (${topCategory.revenuePercent || topCategory.percentageContribution}%)` : "N/A"}
            </p>
          </CardContent></Card>
        </div>
      )}

      {/* Charts */}
      {table.filteredData && table.filteredData.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <Card className="border-[#d9cfc0] bg-white p-4">
            <div className="text-sm font-bold text-[#1e2a4a] mb-2 flex items-center justify-between">
              <span>Category Revenue Share</span>
              <span className="text-xs font-normal text-slate-500">Distribution by ₹ Revenue</span>
            </div>
            <ResponsiveContainer width="100%" height={260}>
              <PieChart>
                <Pie
                  data={table.filteredData}
                  dataKey="totalRevenue"
                  nameKey="categoryName"
                  cx="50%"
                  cy="50%"
                  outerRadius={85}
                  innerRadius={35}
                  paddingAngle={3}
                  label={(entry) => `${entry.categoryName} (${entry.revenuePercent || entry.percentageContribution}%)`}
                >
                  {table.filteredData.map((_, index) => (
                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip formatter={(value: number) => [`₹ ${value.toLocaleString("en-IN", { minimumFractionDigits: 2 })}`, "Revenue"]} />
              </PieChart>
            </ResponsiveContainer>
          </Card>

          <Card className="border-[#d9cfc0] bg-white p-4">
            <div className="text-sm font-bold text-[#1e2a4a] mb-2 flex items-center justify-between">
              <span>Category Volume (Pcs)</span>
              <span className="text-xs font-normal text-slate-500">Quantity Sold</span>
            </div>
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={table.filteredData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e8e0d4" />
                <XAxis dataKey="categoryName" tick={{ fontSize: 11, fill: "#3d4f6f" }} />
                <YAxis tick={{ fontSize: 11, fill: "#3d4f6f" }} />
                <Tooltip formatter={(value: number) => [`${value} Pcs.`, "Quantity Sold"]} />
                <Bar dataKey="totalQty" fill="#c4703f" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </Card>
        </div>
      )}

      {/* Styled Table */}
      <Card className="border-[#d9cfc0] bg-white shadow-sm">
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-[#f5f0e8] border-b border-[#d9cfc0] text-xs text-[#3d4f6f] uppercase font-semibold">
                  <SortableHeader label="Category Name" sortKey="categoryName" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} />
                  <SortableHeader label="Total Products" sortKey="itemCount" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="center" />
                  <SortableHeader label="Quantity Sold" sortKey="totalQty" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                  <SortableHeader label="Total Revenue" sortKey="totalRevenue" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                  <SortableHeader label="Revenue % Share" sortKey="revenuePercent" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                </tr>
              </thead>
              <tbody>
                {isLoading ? (
                  Array.from({ length: 5 }).map((_, i) => (
                    <tr key={i}>
                      {Array.from({ length: 5 }).map((_, j) => (
                        <td key={j} className="py-3 px-4"><div className="h-4 bg-[#e8e0d4] rounded animate-pulse" /></td>
                      ))}
                    </tr>
                  ))
                ) : table.filteredData && table.filteredData.length > 0 ? (
                  table.filteredData.map((item: any, i: number) => {
                    const share = item.revenuePercent || item.percentageContribution || 0;
                    return (
                      <tr key={i} className="border-b border-[#f5f0e8] hover:bg-[#f9f6f0] transition-colors">
                        <td className="py-3 px-4 text-sm">
                          <span className="inline-flex items-center px-2.5 py-1 rounded-md text-xs font-semibold bg-[#f5f0e8] text-[#1e2a4a] border border-[#d9cfc0]">
                            {item.categoryName}
                          </span>
                        </td>
                        <td className="py-3 px-4 text-sm text-center font-mono text-slate-600">{item.itemCount}</td>
                        <td className="py-3 px-4 text-sm text-right font-mono font-semibold">{item.totalQty.toLocaleString("en-IN")} Pcs.</td>
                        <td className="py-3 px-4 text-sm text-right font-mono font-semibold text-green-600">₹ {item.totalRevenue.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                        <td className="py-3 px-4 text-sm text-right">
                          <div className="flex items-center justify-end gap-2">
                            <div className="w-16 bg-[#e8e0d4] rounded-full h-2 overflow-hidden">
                              <div
                                className="bg-[#c4703f] h-full rounded-full"
                                style={{ width: `${Math.min(100, share)}%` }}
                              />
                            </div>
                            <span className="font-mono text-xs font-bold text-[#c4703f] min-w-[36px] text-right">
                              {share}%
                            </span>
                          </div>
                        </td>
                      </tr>
                    );
                  })
                ) : (
                  <tr>
                    <td colSpan={5} className="py-8 text-center text-sm text-slate-500">
                      No category statistics found for the selected criteria.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

// ─── GST / Tax Summary Report ─────────────────────────────
function GstTaxSummaryReport() {
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");

  const { data, isLoading } = trpc.report.gstTaxReport.useQuery({
    startDate: startDate || undefined,
    endDate: endDate || undefined,
  });

  const table = useTableState({
    data: (data as any[]) || [],
    searchFields: ["period"],
    defaultSortKey: "monthKey",
    defaultSortDirection: "desc",
  });

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-3 items-end">
        <div className="space-y-1">
          <Label className="text-xs text-[#3d4f6f]">From Date</Label>
          <Input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} className="w-40 bg-white border-[#d9cfc0] text-sm h-8" />
        </div>
        <div className="space-y-1">
          <Label className="text-xs text-[#3d4f6f]">To Date</Label>
          <Input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} className="w-40 bg-white border-[#d9cfc0] text-sm h-8" />
        </div>
      </div>

      <Card className="border-[#d9cfc0] bg-white">
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-[#f5f0e8] border-b border-[#e8e0d4] text-xs text-[#3d4f6f] uppercase">
                  <SortableHeader label="Month" sortKey="monthKey" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} />
                  <SortableHeader label="Invoices" sortKey="billCount" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="center" />
                  <SortableHeader label="Taxable Value" sortKey="taxableAmount" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                  <SortableHeader label="CGST (₹)" sortKey="cgst" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                  <SortableHeader label="SGST (₹)" sortKey="sgst" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                  <SortableHeader label="IGST (₹)" sortKey="igst" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                  <SortableHeader label="Total GST (₹)" sortKey="totalTax" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                  <SortableHeader label="Total Amount (₹)" sortKey="totalAmount" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                </tr>
              </thead>
              <tbody>
                {isLoading ? Array.from({ length: 5 }).map((_, i) => <tr key={i}>{Array.from({ length: 8 }).map((_, j) => <td key={j} className="py-3 px-4"><div className="h-4 bg-[#e8e0d4] rounded animate-pulse" /></td>)}</tr>) :
                  table.filteredData?.map((item: any, i: number) => (
                    <tr key={i} className="border-b border-[#f5f0e8] hover:bg-[#f5f0e8]/50">
                      <td className="py-3 px-4 text-sm font-medium text-[#1e2a4a]">{item.period}</td>
                      <td className="py-3 px-4 text-sm text-center font-mono">{item.billCount}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono">₹ {item.taxableAmount.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono text-[#c4703f]">₹ {item.cgst.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono text-[#c4703f]">₹ {item.sgst.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono text-[#c4703f]">₹ {item.igst.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono font-semibold text-green-700">₹ {item.totalTax.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono font-semibold text-[#1e2a4a]">₹ {item.totalAmount.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                    </tr>
                  ))
                }
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

// ─── Transport Performance Report ──────────────────────────
function TransportPerformanceReport() {
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");

  const { data, isLoading } = trpc.report.transportPerformance.useQuery({
    startDate: startDate || undefined,
    endDate: endDate || undefined,
  });

  const table = useTableState({
    data: (data as any[]) || [],
    searchFields: ["name", "vehicleNumber"],
    defaultSortKey: "totalShipments",
    defaultSortDirection: "desc",
  });

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-3 items-end">
        <div className="space-y-1">
          <Label className="text-xs text-[#3d4f6f]">From Date</Label>
          <Input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} className="w-40 bg-white border-[#d9cfc0] text-sm h-8" />
        </div>
        <div className="space-y-1">
          <Label className="text-xs text-[#3d4f6f]">To Date</Label>
          <Input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} className="w-40 bg-white border-[#d9cfc0] text-sm h-8" />
        </div>
      </div>

      <Card className="border-[#d9cfc0] bg-white">
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-[#f5f0e8] border-b border-[#e8e0d4] text-xs text-[#3d4f6f] uppercase font-semibold">
                  <SortableHeader label="Transport Partner" sortKey="name" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} />
                  <SortableHeader label="Vehicle Number" sortKey="vehicleNumber" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="center" />
                  <SortableHeader label="Contact Phone" sortKey="contactPhone" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="center" />
                  <SortableHeader label="Shipments Handled" sortKey="totalShipments" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="center" />
                  <SortableHeader label="Total Parcels" sortKey="totalParcels" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="center" />
                  <SortableHeader label="Taxable Cargo Value" sortKey="totalTaxable" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                  <SortableHeader label="Total Cargo Value" sortKey="totalGoodsValue" currentSortKey={table.sortConfig?.key || null} sortDirection={table.sortConfig?.direction || null} onSort={table.handleSort} align="right" />
                </tr>
              </thead>
              <tbody>
                {isLoading ? Array.from({ length: 3 }).map((_, i) => <tr key={i}>{Array.from({ length: 7 }).map((_, j) => <td key={j} className="py-3 px-4"><div className="h-4 bg-[#e8e0d4] rounded animate-pulse" /></td>)}</tr>) :
                  table.filteredData?.map((item: any, i: number) => (
                    <tr key={i} className="border-b border-[#f5f0e8] hover:bg-[#f5f0e8]/50">
                      <td className="py-3 px-4 text-sm font-medium text-[#1e2a4a]">{item.name}</td>
                      <td className="py-3 px-4 text-sm font-mono text-center">{item.vehicleNumber}</td>
                      <td className="py-3 px-4 text-sm font-mono text-center">{item.contactPhone}</td>
                      <td className="py-3 px-4 text-sm font-mono text-center font-semibold text-[#c4703f]">{item.totalShipments}</td>
                      <td className="py-3 px-4 text-sm font-mono text-center font-bold text-orange-700">{item.totalParcels || 0}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono">₹ {item.totalTaxable.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                      <td className="py-3 px-4 text-sm text-right font-mono font-semibold text-green-700">₹ {item.totalGoodsValue.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                    </tr>
                  ))
                }
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
