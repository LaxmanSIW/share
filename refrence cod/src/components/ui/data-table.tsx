import { useState, useMemo, useRef, useEffect, type ReactNode } from "react";
import { Search, Filter, ArrowUpDown, ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight, RotateCcw, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

export interface ColumnDef<T> {
  key: string;
  label: string;
  sortable?: boolean;
  filterable?: boolean;
  valueMode?: "predefined" | "text";
  options?: { label: string; value: string | number }[];
  render?: (row: T, index: number) => ReactNode;
  width?: string;
  className?: string;
  headerClassName?: string;
}

interface DataTableProps<T> {
  columns: ColumnDef<T>[];
  data: T[];
  title?: string;
  subtitle?: string;
  loading?: boolean;
  errorMessage?: string;
  quickFilterSlot?: ReactNode;
  headerActionsSlot?: ReactNode;
  searchPlaceholder?: string;
  initialPageSize?: number;
  pageSizeOptions?: number[];
  defaultSortKey?: string;
  defaultSortDirection?: "asc" | "desc";
  onRowClick?: (row: T) => void;
  emptyMessage?: string;
}

export function DataTable<T extends Record<string, any>>({
  columns,
  data = [],
  title,
  subtitle,
  loading = false,
  errorMessage = "",
  quickFilterSlot,
  headerActionsSlot,
  searchPlaceholder = "Search records...",
  initialPageSize = 25,
  pageSizeOptions = [10, 25, 50, 100],
  defaultSortKey,
  defaultSortDirection,
  onRowClick,
  emptyMessage = "No records found",
}: DataTableProps<T>) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(initialPageSize);
  const [searchText, setSearchText] = useState("");
  const [activeFilterKey, setActiveFilterKey] = useState("");
  const [sortKey, setSortKey] = useState(defaultSortKey || "");
  const [sortDirection, setSortDirection] = useState<"asc" | "desc" | "">(defaultSortDirection || "");
  const [filterDraft, setFilterDraft] = useState<Record<string, { operator: string; value: string }>>({});
  const [appliedFilters, setAppliedFilters] = useState<Record<string, { operator: string; value: string }>>({});

  useEffect(() => {
    setCurrentPage(1);
  }, [data, pageSize]);

  // Close filter dropdown on click outside
  useEffect(() => {
    function handleOutsideClick(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setActiveFilterKey("");
      }
    }
    document.addEventListener("mousedown", handleOutsideClick);
    return () => document.removeEventListener("mousedown", handleOutsideClick);
  }, []);

  const getValue = (row: T, key: string): any => {
    return key.split(".").reduce((acc, part) => {
      if (acc && typeof acc === "object" && part in acc) {
        return acc[part];
      }
      return undefined;
    }, row);
  };

  const getRawDisplayText = (column: ColumnDef<T>, row: T): string => {
    const value = getValue(row, column.key);
    if (column.valueMode === "predefined" && column.options) {
      const option = column.options.find((opt) => String(opt.value) === String(value));
      return option?.label ?? String(value ?? "");
    }
    return String(value ?? "");
  };

  const filteredList = useMemo(() => {
    let result = [...data];
    const search = searchText.trim().toLowerCase();

    if (search) {
      result = result.filter((row) =>
        columns.some((col) => {
          const val = getRawDisplayText(col, row).toLowerCase();
          return val.includes(search);
        })
      );
    }

    Object.entries(appliedFilters).forEach(([key, filter]) => {
      if (!filter.value) return;
      result = result.filter((row) => {
        const val = String(getValue(row, key) ?? "").toLowerCase();
        const target = filter.value.toLowerCase();
        switch (filter.operator) {
          case "equals":
            return val === target;
          case "startsWith":
            return val.startsWith(target);
          case "endsWith":
            return val.endsWith(target);
          default:
            return val.includes(target);
        }
      });
    });

    if (sortKey && sortDirection) {
      result.sort((a, b) => {
        const valA = getValue(a, sortKey);
        const valB = getValue(b, sortKey);

        if (typeof valA === "number" && typeof valB === "number") {
          return sortDirection === "asc" ? valA - valB : valB - valA;
        }

        const strA = String(valA ?? "").toLowerCase();
        const strB = String(valB ?? "").toLowerCase();
        return sortDirection === "asc"
          ? strA.localeCompare(strB, undefined, { numeric: true })
          : strB.localeCompare(strA, undefined, { numeric: true });
      });
    }

    return result;
  }, [data, searchText, columns, appliedFilters, sortKey, sortDirection]);

  const totalPages = Math.max(1, Math.ceil(filteredList.length / pageSize));

  const pagedList = useMemo(() => {
    const safePage = Math.min(currentPage, totalPages);
    const start = (safePage - 1) * pageSize;
    return filteredList.slice(start, start + pageSize);
  }, [filteredList, currentPage, totalPages, pageSize]);

  const handleSort = (column: ColumnDef<T>) => {
    if (column.sortable === false) return;
    if (sortKey !== column.key) {
      setSortKey(column.key);
      setSortDirection("asc");
    } else if (sortDirection === "asc") {
      setSortDirection("desc");
    } else {
      setSortKey("");
      setSortDirection("");
    }
  };

  const handleApplyFilter = (column: ColumnDef<T>) => {
    const key = column.key;
    const draft = filterDraft[key];
    if (!draft || !draft.value.trim()) {
      const updated = { ...appliedFilters };
      delete updated[key];
      setAppliedFilters(updated);
    } else {
      setAppliedFilters((prev) => ({
        ...prev,
        [key]: { operator: draft.operator || "contains", value: draft.value.trim() },
      }));
    }
    setCurrentPage(1);
    setActiveFilterKey("");
  };

  const handleClearFilter = (column: ColumnDef<T>) => {
    const key = column.key;
    const updated = { ...appliedFilters };
    delete updated[key];
    setAppliedFilters(updated);
    setFilterDraft((prev) => {
      const next = { ...prev };
      delete next[key];
      return next;
    });
    setCurrentPage(1);
    setActiveFilterKey("");
  };

  const clearAllFilters = () => {
    setSearchText("");
    setSortKey("");
    setSortDirection("");
    setFilterDraft({});
    setAppliedFilters({});
    setCurrentPage(1);
  };

  const activeFilterCount = Object.keys(appliedFilters).length + (searchText ? 1 : 0);

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden" ref={containerRef}>
      {/* Top Header / Bar */}
      <div className="p-4 bg-slate-50/70 border-b border-slate-200 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div>
          {title && <h3 className="text-base font-bold text-[#1e2a4a]">{title}</h3>}
          {subtitle ? (
            <p className="text-xs text-slate-500 mt-0.5">{subtitle}</p>
          ) : (
            <p className="text-xs text-slate-500 mt-0.5">
              Showing <span className="font-semibold text-slate-700">{filteredList.length}</span> of{" "}
              <span className="font-semibold text-slate-700">{data.length}</span> total entries
            </p>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          {quickFilterSlot}

          {/* Search bar */}
          <div className="relative min-w-[240px] flex-1 sm:flex-none">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <Input
              type="text"
              value={searchText}
              onChange={(e) => {
                setSearchText(e.target.value);
                setCurrentPage(1);
              }}
              placeholder={searchPlaceholder}
              className="pl-9 pr-8 h-9 text-xs bg-white border-slate-300 focus-visible:ring-[#c4703f]"
            />
            {searchText && (
              <button
                type="button"
                onClick={() => setSearchText("")}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}
          </div>

          {activeFilterCount > 0 && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={clearAllFilters}
              className="h-9 px-2.5 text-xs text-slate-600 border-slate-300 hover:bg-slate-100 gap-1.5"
            >
              <RotateCcw className="w-3.5 h-3.5" />
              Reset ({activeFilterCount})
            </Button>
          )}

          {headerActionsSlot}
        </div>
      </div>

      {/* Main Table */}
      <div className="overflow-x-auto min-h-[250px]">
        <table className="w-full text-left text-xs font-sans border-collapse">
          <thead className="bg-[#f5f0e8] border-b border-[#d9cfc0] text-[#3d4f6f]">
            <tr>
              {columns.map((column) => {
                const hasApplied = !!appliedFilters[column.key]?.value;
                const isSorted = sortKey === column.key;

                return (
                  <th
                    key={column.key}
                    className={`relative border-r border-[#e8e0d4] px-3.5 py-3 font-semibold uppercase tracking-wider last:border-r-0 select-none text-[#3d4f6f] ${
                      column.headerClassName || ""
                    }`}
                    style={{ width: column.width || undefined }}
                  >
                    <div className="flex items-center justify-between gap-1.5">
                      <button
                        type="button"
                        onClick={() => handleSort(column)}
                        className={`flex items-center gap-1.5 hover:text-[#1e2a4a] transition-colors text-left font-semibold ${
                          column.sortable === false ? "cursor-default hover:text-[#3d4f6f]" : "cursor-pointer"
                        }`}
                      >
                        <span>{column.label}</span>
                        {column.sortable !== false && (
                          <ArrowUpDown
                            className={`w-3 h-3 transition-opacity ${
                              isSorted ? "text-[#c4703f] opacity-100" : "opacity-40 text-[#8c9ba5]"
                            }`}
                          />
                        )}
                      </button>

                      {column.filterable !== false && (
                        <button
                          type="button"
                          onClick={() => {
                            setActiveFilterKey((prev) => (prev === column.key ? "" : column.key));
                            setFilterDraft((prev) => ({
                              ...prev,
                              [column.key]: prev[column.key] || appliedFilters[column.key] || {
                                operator: column.valueMode === "predefined" ? "equals" : "contains",
                                value: "",
                              },
                            }));
                          }}
                          className={`p-1 rounded hover:bg-[#e8e0d4] transition-colors ${
                            hasApplied ? "text-[#c4703f] bg-[#e8e0d4]" : "text-[#8c9ba5] hover:text-[#1e2a4a]"
                          }`}
                          title="Filter column"
                        >
                          <Filter className="w-3.5 h-3.5" />
                        </button>
                      )}
                    </div>

                    {/* Filter Dropdown Popover */}
                    {activeFilterKey === column.key && (
                      <div
                        className="absolute right-2 top-[calc(100%+4px)] z-30 w-60 p-3 rounded-lg bg-white border border-[#d9cfc0] text-[#1e2a4a] shadow-xl normal-case font-normal"
                        onClick={(e) => e.stopPropagation()}
                      >
                        <div className="flex items-center justify-between mb-2">
                          <span className="text-xs font-bold text-[#1e2a4a]">Filter {column.label}</span>
                          <button
                            onClick={() => setActiveFilterKey("")}
                            className="text-slate-400 hover:text-slate-600"
                          >
                            <X className="w-3.5 h-3.5" />
                          </button>
                        </div>

                        {column.valueMode === "predefined" && column.options ? (
                          <select
                            value={filterDraft[column.key]?.value || ""}
                            onChange={(e) =>
                              setFilterDraft((prev) => ({
                                ...prev,
                                [column.key]: { operator: "equals", value: e.target.value },
                              }))
                            }
                            className="w-full h-8 text-xs border border-[#d9cfc0] rounded px-2 bg-white text-[#1e2a4a] outline-none focus:border-[#c4703f]"
                          >
                            <option value="">-- All --</option>
                            {column.options.map((opt) => (
                              <option key={opt.value} value={opt.value}>
                                {opt.label}
                              </option>
                            ))}
                          </select>
                        ) : (
                          <Input
                            type="text"
                            placeholder="Type to filter..."
                            value={filterDraft[column.key]?.value || ""}
                            onChange={(e) =>
                              setFilterDraft((prev) => ({
                                ...prev,
                                [column.key]: { operator: "contains", value: e.target.value },
                              }))
                            }
                            className="h-8 text-xs border-[#d9cfc0] focus-visible:ring-[#c4703f]"
                          />
                        )}

                        <div className="flex items-center justify-end gap-2 mt-3 pt-2 border-t border-[#f0e8dc]">
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() => handleClearFilter(column)}
                            className="h-7 px-2 text-[11px] text-[#3d4f6f] hover:bg-[#f5f0e8]"
                          >
                            Clear
                          </Button>
                          <Button
                            type="button"
                            size="sm"
                            onClick={() => handleApplyFilter(column)}
                            className="h-7 px-3 text-[11px] bg-[#1e2a4a] hover:bg-[#2a3a5c] text-white"
                          >
                            Apply
                          </Button>
                        </div>
                      </div>
                    )}
                  </th>
                );
              })}
            </tr>
          </thead>

          <tbody className="divide-y divide-[#f0e8dc] bg-white">
            {loading && (
              <tr>
                <td colSpan={columns.length} className="p-8 text-center text-[#3d4f6f] font-medium">
                  Loading table data...
                </td>
              </tr>
            )}

            {!loading && errorMessage && (
              <tr>
                <td colSpan={columns.length} className="p-8 text-center text-red-600 font-medium">
                  {errorMessage}
                </td>
              </tr>
            )}

            {!loading && !errorMessage && pagedList.length === 0 && (
              <tr>
                <td colSpan={columns.length} className="p-8 text-center text-[#3d4f6f] font-medium">
                  {emptyMessage}
                </td>
              </tr>
            )}

            {!loading &&
              !errorMessage &&
              pagedList.map((row, index) => (
                <tr
                  key={row.id ?? index}
                  onClick={() => onRowClick?.(row)}
                  className={`transition-colors ${
                    onRowClick ? "cursor-pointer hover:bg-[#f9f6f0]" : "hover:bg-[#f9f6f0]"
                  }`}
                >
                  {columns.map((column) => (
                    <td
                      key={column.key}
                      className={`px-3.5 py-2.5 text-[#1e2a4a] align-middle ${column.className || ""}`}
                    >
                      {column.render
                        ? column.render(row, index)
                        : getRawDisplayText(column, row) || "-"}
                    </td>
                  ))}
                </tr>
              ))}
          </tbody>
        </table>
      </div>

      {/* Pagination Footer */}
      <div className="p-3 bg-[#f5f0e8]/50 border-t border-[#d9cfc0] flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between text-xs text-[#3d4f6f]">
        <div className="flex items-center gap-2">
          <span>Rows per page:</span>
          <select
            value={pageSize}
            onChange={(e) => {
              setPageSize(Number(e.target.value));
              setCurrentPage(1);
            }}
            className="h-8 px-2 border border-slate-300 rounded bg-white font-medium text-slate-700 outline-none focus:border-[#c4703f]"
          >
            {pageSizeOptions.map((sz) => (
              <option key={sz} value={sz}>
                {sz}
              </option>
            ))}
          </select>
        </div>

        <div className="flex items-center justify-between sm:justify-end gap-3">
          <span>
            Page <span className="font-semibold text-slate-900">{currentPage}</span> of{" "}
            <span className="font-semibold text-slate-900">{totalPages}</span>
          </span>

          <div className="flex items-center gap-1">
            <Button
              variant="outline"
              size="icon"
              onClick={() => setCurrentPage(1)}
              disabled={currentPage === 1}
              className="h-8 w-8 text-slate-600 disabled:opacity-40"
              title="First Page"
            >
              <ChevronsLeft className="w-4 h-4" />
            </Button>
            <Button
              variant="outline"
              size="icon"
              onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
              disabled={currentPage === 1}
              className="h-8 w-8 text-slate-600 disabled:opacity-40"
              title="Previous Page"
            >
              <ChevronLeft className="w-4 h-4" />
            </Button>
            <Button
              variant="outline"
              size="icon"
              onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
              disabled={currentPage === totalPages}
              className="h-8 w-8 text-slate-600 disabled:opacity-40"
              title="Next Page"
            >
              <ChevronRight className="w-4 h-4" />
            </Button>
            <Button
              variant="outline"
              size="icon"
              onClick={() => setCurrentPage(totalPages)}
              disabled={currentPage === totalPages}
              className="h-8 w-8 text-slate-600 disabled:opacity-40"
              title="Last Page"
            >
              <ChevronsRight className="w-4 h-4" />
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
