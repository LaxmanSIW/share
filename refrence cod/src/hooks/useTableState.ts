import { useState, useMemo, useCallback } from "react";

type SortConfig<T> = {
  key: keyof T | string;
  direction: "asc" | "desc";
} | null;

export function useTableState<T>({
  data,
  searchFields,
  defaultSortKey,
  defaultSortDirection = "asc" as const,
}: {
  data: T[];
  searchFields: (keyof T | string)[];
  defaultSortKey?: keyof T | string;
  defaultSortDirection?: "asc" | "desc";
}) {
  const [search, setSearch] = useState("");
  const [sortConfig, setSortConfig] = useState<SortConfig<T>>(
    defaultSortKey ? { key: defaultSortKey, direction: defaultSortDirection } : null
  );
  const [page, setPage] = useState(1);
  const [pageSize] = useState(25);

  // Live search filtering
  const filteredData = useMemo(() => {
    if (!search.trim()) return data;
    const searchLower = search.toLowerCase().trim();
    return data.filter((item) =>
      searchFields.some((field) => {
        const value = getNestedValue(item, field as string);
        if (value == null) return false;
        return String(value).toLowerCase().includes(searchLower);
      })
    );
  }, [data, search, searchFields]);

  // Sorting
  const sortedData = useMemo(() => {
    if (!sortConfig) return filteredData;

    return [...filteredData].sort((a, b) => {
      const aVal = getNestedValue(a, sortConfig.key as string);
      const bVal = getNestedValue(b, sortConfig.key as string);

      // Handle null/undefined
      if (aVal == null && bVal == null) return 0;
      if (aVal == null) return sortConfig.direction === "asc" ? -1 : 1;
      if (bVal == null) return sortConfig.direction === "asc" ? 1 : -1;

      // Detect type and sort accordingly
      if (typeof aVal === "number" && typeof bVal === "number") {
        return sortConfig.direction === "asc" ? aVal - bVal : bVal - aVal;
      }

      if (typeof aVal === "string" && typeof bVal === "string") {
        // Try to parse as numbers (for currency/decimal strings)
        const aNum = parseFloat(aVal.replace(/[^0-9.-]/g, ""));
        const bNum = parseFloat(bVal.replace(/[^0-9.-]/g, ""));
        if (!isNaN(aNum) && !isNaN(bNum) && aVal !== bVal) {
          return sortConfig.direction === "asc" ? aNum - bNum : bNum - aNum;
        }
        return sortConfig.direction === "asc"
          ? aVal.localeCompare(bVal)
          : bVal.localeCompare(aVal);
      }

      // Default string comparison
      const aStr = String(aVal);
      const bStr = String(bVal);
      return sortConfig.direction === "asc"
        ? aStr.localeCompare(bStr)
        : bStr.localeCompare(aStr);
    });
  }, [filteredData, sortConfig]);

  // Pagination (disabled when search is active)
  const isSearchActive = search.trim().length > 0;
  const paginatedData = useMemo(() => {
    if (isSearchActive) return sortedData; // Show all when searching
    const start = (page - 1) * pageSize;
    return sortedData.slice(start, start + pageSize);
  }, [sortedData, isSearchActive, page, pageSize]);

  const totalPages = Math.ceil(sortedData.length / pageSize);

  const handleSort = useCallback(
    (key: keyof T | string) => {
      setSortConfig((current) => {
        if (current?.key === key) {
          // Toggle direction
          if (current.direction === "asc") {
            return { key, direction: "desc" };
          }
          // Third click removes sort
          return null;
        }
        return { key, direction: "asc" };
      });
      setPage(1);
    },
    []
  );

  const handleSearch = useCallback((value: string) => {
    setSearch(value);
    setPage(1);
  }, []);

  return {
    search,
    setSearch: handleSearch,
    sortConfig,
    handleSort,
    page,
    setPage,
    pageSize,
    totalPages,
    isSearchActive,
    filteredData: paginatedData,
    totalFiltered: sortedData.length,
  };
}

// Helper to get nested values using dot notation
function getNestedValue(obj: any, path: string): any {
  return path.split(".").reduce((acc, part) => acc?.[part], obj);
}
