import { useEffect, useMemo, useRef, useState } from 'react';

export default function ReusableTable({
  columns = [],
  data = [],
  title = 'Workflow List',
  loading = false,
  errorMessage = '',
  pageSizeOptions = [10, 25, 50, 100, 150, 200, 250],
  initialPageSize = 50,
  rowAction
}) {
  const containerRef = useRef(null);
  const [storedList, setStoredList] = useState([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(initialPageSize);
  const [searchText, setSearchText] = useState('');
  const [activeFilterKey, setActiveFilterKey] = useState('');
  const [sortKey, setSortKey] = useState('');
  const [sortDirection, setSortDirection] = useState('');
  const [filterDraft, setFilterDraft] = useState({});
  const [appliedFilters, setAppliedFilters] = useState({});

  // Sync data changes
  useEffect(() => {
    setStoredList([...(data || [])]);
    setPageSize(initialPageSize);
    setCurrentPage(1);
  }, [data, initialPageSize]);

  // Close filter dropdown on outside click
  useEffect(() => {
    function handleOutsideClick(event) {
      if (!containerRef.current) return;
      if (!containerRef.current.contains(event.target)) {
        setActiveFilterKey('');
      }
    }
    document.addEventListener('click', handleOutsideClick);
    return () => document.removeEventListener('click', handleOutsideClick);
  }, []);

  // --- Helper functions ---
  const getValue = (row, key) => {
    return key.split('.').reduce((acc, part) => {
      if (acc && typeof acc === 'object' && part in acc) {
        return acc[part];
      }
      return undefined;
    }, row);
  };

  const getColumnOption = (column, value) => {
    return column?.options?.find((option) => String(option.value) === String(value));
  };

  // A column is "predefined" if it has options and valueMode === 'predefined'
  const isPredefinedColumn = (column) => {
    return !!column && column.valueMode === 'predefined' && column.options?.length > 0;
  };

  const shouldReplaceWithIcon = (column) => {
    return !!column && isPredefinedColumn(column) && column.replaceWithIcon;
  };

  const getDisplayText = (column, row) => {
    const value = getValue(row, column.key);
    if (isPredefinedColumn(column)) {
      const option = getColumnOption(column, value);
      if (!option) return String(value ?? '');
      return column.replaceWithIcon ? (option.icon || option.label) : option.label;
    }
    return String(value ?? '');
  };

  const getRawDisplayText = (column, row) => {
    const value = getValue(row, column.key);
    if (isPredefinedColumn(column)) {
      const option = getColumnOption(column, value);
      return option?.label ?? String(value ?? '');
    }
    return String(value ?? '');
  };

  const getDisplayClass = (column, row) => {
    const value = getValue(row, column.key);
    if (isPredefinedColumn(column)) {
      const option = getColumnOption(column, value);
      return option?.textColor || '';
    }
    return '';
  };

  const ensureDraft = (key, column) => {
    return (
      filterDraft[key] ||
      appliedFilters[key] || {
        operator: isPredefinedColumn(column) ? 'equals' : 'contains',
        value: ''
      }
    );
  };

  const matchesFilter = (column, source, operator, expected) => {
    const predefinedSource = isPredefinedColumn(column)
      ? (getColumnOption(column, source)?.value ?? source)
      : source;
    const left = String(predefinedSource ?? '').toLowerCase();
    const right = String(expected ?? '').toLowerCase();
    switch (operator) {
      case 'equals':
        return left === right;
      case 'startsWith':
        return left.startsWith(right);
      case 'endsWith':
        return left.endsWith(right);
      default:
        return left.includes(right);
    }
  };

  const normalizeSortValue = (column, value) => {
    if (column?.valueMode === 'predefined') {
      const option = getColumnOption(column, value);
      return String(option?.label ?? value ?? '').toLowerCase();
    }
    if (value == null) return '';
    if (column?.type === 'number') {
      const num = Number(value);
      return Number.isNaN(num) ? 0 : num;
    }
    if (column?.type === 'date') {
      const date = Date.parse(String(value));
      return Number.isNaN(date) ? 0 : date;
    }
    // Fallback: try date, then number, then string
    const stringValue = String(value);
    const genericDate = Date.parse(stringValue);
    if (!Number.isNaN(genericDate)) return genericDate;
    const numericValue = Number(value);
    if (!Number.isNaN(numericValue) && stringValue !== '') return numericValue;
    return stringValue.toLowerCase();
  };

  // --- Filtered & sorted list ---
  const filteredList = useMemo(() => {
    let result = [...storedList];
    const search = searchText.trim().toLowerCase();
    if (search) {
      result = result.filter((row) =>
        columns.some((column) => {
          const searchableText = getRawDisplayText(column, row).toLowerCase();
          return searchableText.includes(search);
        })
      );
    }

    Object.entries(appliedFilters).forEach(([key, filter]) => {
      result = result.filter((row) => {
        const column = columns.find((col) => col.key === key);
        return matchesFilter(column, getValue(row, key), filter.operator, filter.value);
      });
    });

    if (sortKey && sortDirection) {
      const sortColumn = columns.find((col) => col.key === sortKey);
      result.sort((a, b) => {
        const aValue = normalizeSortValue(sortColumn, getValue(a, sortKey));
        const bValue = normalizeSortValue(sortColumn, getValue(b, sortKey));
        if (aValue < bValue) return sortDirection === 'asc' ? -1 : 1;
        if (aValue > bValue) return sortDirection === 'asc' ? 1 : -1;
        return 0;
      });
    }
    return result;
  }, [storedList, searchText, columns, appliedFilters, sortKey, sortDirection]);

  const totalPages = Math.max(1, Math.ceil(filteredList.length / pageSize));

  const pagedList = useMemo(() => {
    const safePage = Math.min(currentPage, totalPages);
    const start = (safePage - 1) * pageSize;
    const end = start + pageSize;
    return filteredList.slice(start, end);
  }, [filteredList, currentPage, totalPages, pageSize]);

  // Clamp current page
  useEffect(() => {
    if (currentPage > totalPages) {
      setCurrentPage(totalPages);
    }
  }, [currentPage, totalPages]);

  const clearAll = () => {
    setSearchText('');
    setCurrentPage(1);
    setActiveFilterKey('');
    setSortKey('');
    setSortDirection('');
    setFilterDraft({});
    setAppliedFilters({});
  };

  const onSortClick = (column) => {
    if (column.sortable === false) return;
    const key = column.key;
    if (sortKey !== key) {
      setSortKey(key);
      setSortDirection('asc');
    } else if (sortDirection === 'asc') {
      setSortDirection('desc');
    } else if (sortDirection === 'desc') {
      setSortKey('');
      setSortDirection('');
    } else {
      setSortDirection('asc');
    }
  };

  const getSortIndicator = (column) => {
    if (sortKey !== column.key) return '';
    return sortDirection === 'asc' ? '▲' : sortDirection === 'desc' ? '▼' : '';
  };

  const onFilterToggle = (column, event) => {
    event.stopPropagation();
    const key = column.key;
    setActiveFilterKey((prev) => (prev === key ? '' : key));
    setFilterDraft((prev) => ({
      ...prev,
      [key]:
        prev[key] ||
        appliedFilters[key] || {
          operator: isPredefinedColumn(column) ? 'equals' : 'contains',
          value: ''
        }
    }));
  };

  const applyFilter = (column) => {
    const key = column.key;
    const draft = ensureDraft(key, column);
    if (!String(draft.value ?? '').trim()) {
      const updated = { ...appliedFilters };
      delete updated[key];
      setAppliedFilters(updated);
    } else {
      setAppliedFilters((prev) => ({
        ...prev,
        [key]: { ...draft, value: String(draft.value).trim() }
      }));
    }
    setCurrentPage(1);
    setActiveFilterKey('');
  };

  const clearFilter = (column) => {
    const key = column.key;
    const updated = { ...appliedFilters };
    delete updated[key];
    setAppliedFilters(updated);
    setFilterDraft((prev) => ({
      ...prev,
      [key]: {
        operator: isPredefinedColumn(column) ? 'equals' : 'contains',
        value: ''
      }
    }));
    setCurrentPage(1);
    setActiveFilterKey('');
  };

  const hasFilter = (column) => {
    return !!appliedFilters[column.key]?.value;
  };

  const getVisiblePages = () => {
    const pages = [];
    let start = Math.max(1, currentPage - 2);
    let end = Math.min(totalPages, start + 4);
    start = Math.max(1, end - 4);
    for (let i = start; i <= end; i++) {
      pages.push(i);
    }
    return pages;
  };

  return (
    <div className="min-h-screen bg-black text-white p-4 md:p-6" ref={containerRef}>
      <div className="mx-auto max-w-[1900px] rounded-sm border border-brand-border bg-brand-panel shadow-panel overflow-hidden">
        {/* Header */}
        <div className="border-b border-brand-border bg-brand-panelHeader px-4 py-3 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div>
            <div className="text-lg font-semibold tracking-wide text-white">{title}</div>
            <div className="text-xs text-brand-muted mt-1">
              Loaded {storedList.length} items · Showing {filteredList.length} items
            </div>
          </div>
          <div className="flex items-center gap-3 flex-wrap">
            <input
              type="text"
              value={searchText}
              onChange={(e) => {
                setSearchText(e.target.value);
                setCurrentPage(1);
              }}
              placeholder="Live search workflows..."
              className="w-[320px] max-w-full rounded-full border border-[#346176] bg-white/95 px-4 py-2 text-sm text-slate-800 outline-none placeholder:text-slate-400"
            />
            <button
              type="button"
              onClick={clearAll}
              className="rounded-full border border-white/20 px-4 py-2 text-xs font-semibold uppercase tracking-wide text-white/90 hover:bg-white/10"
            >
              Clear
            </button>
          </div>
        </div>

        {/* Table */}
        <div className="table-scrollbar overflow-x-auto">
          <table className="min-w-full border-collapse text-[13px]">
            <thead className="bg-brand-gridHeader text-white">
              <tr>
                {columns.map((column) => {
                  const draft = ensureDraft(column.key, column);
                  return (
                    <th
                      key={column.key}
                      className="relative border-r border-brand-border px-3 py-3 text-left text-[12px] font-bold uppercase tracking-wide last:border-r-0"
                      style={{ width: column.width || undefined }}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <button
                          type="button"
                          className="flex items-center gap-1 text-left"
                          onClick={() => onSortClick(column)}
                        >
                          <span>{column.label}</span>
                          <span className="text-[10px] text-white/70">{getSortIndicator(column)}</span>
                        </button>
                        {column.filterable !== false && (
                          <button
                            type="button"
                            onClick={(e) => onFilterToggle(column, e)}
                            className={`text-[14px] leading-none text-[#d4c7a7] hover:text-white ${
                              hasFilter(column) ? 'text-white' : ''
                            }`}
                            title="Filter"
                          >
                            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="w-4 h-4">
                              <path fillRule="evenodd" d="M3.792 2.653A.75.75 0 014.5 2.25h15a.75.75 0 01.708 1.153L14.25 12.34v5.918a.75.75 0 01-.364.643l-3 1.84a.75.75 0 01-1.136-.643v-7.71L3.792 3.403a.75.75 0 010-.75z" clipRule="evenodd" />
                            </svg>
                          </button>
                        )}
                      </div>

                      {activeFilterKey === column.key && (
                        <div
                          className="absolute right-2 top-[calc(100%+6px)] z-20 w-64 rounded-md border border-slate-200 bg-white p-3 text-slate-800 shadow-2xl"
                          onClick={(e) => e.stopPropagation()}
                        >
                          <div className="mb-2 text-xs font-semibold text-slate-500">Filter</div>
                          <select
                            value={draft.operator}
                            onChange={(e) =>
                              setFilterDraft((prev) => ({
                                ...prev,
                                [column.key]: { ...ensureDraft(column.key, column), operator: e.target.value }
                              }))
                            }
                            className="mb-2 w-full rounded border border-slate-300 px-2 py-2 text-sm"
                          >
                            {!isPredefinedColumn(column) && <option value="contains">Contains</option>}
                            <option value="equals">Equals</option>
                            {!isPredefinedColumn(column) && <option value="startsWith">Starts with</option>}
                            {!isPredefinedColumn(column) && <option value="endsWith">Ends with</option>}
                          </select>

                          {isPredefinedColumn(column) ? (
                            <select
                              value={draft.value}
                              onChange={(e) =>
                                setFilterDraft((prev) => ({
                                  ...prev,
                                  [column.key]: { ...ensureDraft(column.key, column), value: e.target.value }
                                }))
                              }
                              className="w-full rounded border border-slate-300 px-2 py-2 text-sm"
                            >
                              <option value="">Select...</option>
                              {(column.options || []).map((option) => (
                                <option key={option.value} value={option.value}>
                                  {option.label}
                                </option>
                              ))}
                            </select>
                          ) : (
                            <input
                              type="text"
                              value={draft.value}
                              onChange={(e) =>
                                setFilterDraft((prev) => ({
                                  ...prev,
                                  [column.key]: { ...ensureDraft(column.key, column), value: e.target.value }
                                }))
                              }
                              className="w-full rounded border border-slate-300 px-2 py-2 text-sm"
                              placeholder="Filter value"
                            />
                          )}

                          <div className="mt-3 flex justify-end gap-2">
                            <button
                              type="button"
                              onClick={() => clearFilter(column)}
                              className="rounded border border-slate-300 px-3 py-1 text-sm text-slate-700"
                            >
                              Clear
                            </button>
                            <button
                              type="button"
                              onClick={() => applyFilter(column)}
                              className="rounded bg-slate-900 px-3 py-1 text-sm text-white"
                            >
                              Apply
                            </button>
                          </div>
                        </div>
                      )}
                    </th>
                  );
                })}
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr>
                  <td colSpan={columns.length || 1} className="px-4 py-12 text-center text-white/70">
                    Loading data...
                  </td>
                </tr>
              )}
              {!loading && errorMessage && (
                <tr>
                  <td colSpan={columns.length || 1} className="px-4 py-12 text-center text-red-300">
                    {errorMessage}
                  </td>
                </tr>
              )}
              {!loading &&
                !errorMessage &&
                pagedList.map((row, index) => (
                  <tr
                    key={row.id ?? index}
                    className="border-t border-brand-border bg-brand-panel hover:bg-[#124760]"
                  >
                    {columns.map((column) => (
                      <td
                        key={column.key}
                        className="border-r border-brand-border px-3 py-3 last:border-r-0"
                        style={{ width: column.width || undefined }}
                      >
                        {column.key === 'name' ? (
                          <div className="flex items-center justify-between gap-3">
                            <div className="min-w-0 flex items-center gap-3">
                              <div
                                className="truncate text-[14px] leading-none text-[#fff]"
                                title={getRawDisplayText(column, row)}
                              >
                                {getDisplayText(column, row) || '-'}
                              </div>
                              {row.isNew && (
                                <span className="text-[12px] uppercase tracking-wide text-[#51cc35]">NEW</span>
                              )}
                            </div>
                            <button
                              type="button"
                              onClick={() => rowAction?.(row)}
                              className="text-[18px] text-[#d8c7b7]"
                            >
                              ➤
                            </button>
                          </div>
                        ) : (
                          <div
                            className={`text-center ${
                              shouldReplaceWithIcon(column)
                                ? 'text-[24px] leading-none font-semibold'
                                : 'font-medium'
                            }`}
                            title={getRawDisplayText(column, row)}
                          >
                            <span className={getDisplayClass(column, row)}>
                              {getDisplayText(column, row) || '-'}
                            </span>
                          </div>
                        )}
                      </td>
                    ))}
                  </tr>
                ))}
              {!loading && !errorMessage && pagedList.length === 0 && (
                <tr>
                  <td colSpan={columns.length || 1} className="px-4 py-10 text-center text-white/70">
                    No records found
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        <div className="flex flex-col gap-4 border-t border-brand-border bg-brand-panel px-3 py-3 md:flex-row md:items-center md:justify-between">
          <div className="text-sm text-[#b9c8d0]">
            Page {Math.min(currentPage, totalPages)} of {totalPages} ({filteredList.length} items)
          </div>
          <div className="flex flex-wrap items-center justify-center gap-3">
            <button
              type="button"
              onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
              disabled={currentPage === 1}
              className="h-11 w-11 rounded-full bg-[#88a0ab] text-xl text-[#1b4152] disabled:opacity-40"
            >
              ‹
            </button>
            {getVisiblePages().map((page) => (
              <button
                key={page}
                type="button"
                onClick={() => setCurrentPage(page)}
                className={`h-11 w-11 rounded-full text-sm ${
                  page === currentPage
                    ? 'bg-[#1b4152] text-white'
                    : 'bg-[#88a0ab] text-[#1b4152]'
                }`}
              >
                {page}
              </button>
            ))}
            <button
              type="button"
              onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
              disabled={currentPage === totalPages}
              className="h-11 w-11 rounded-full bg-[#88a0ab] text-xl text-[#1b4152] disabled:opacity-40"
            >
              ›
            </button>
            <button
              type="button"
              onClick={() => setCurrentPage(totalPages)}
              disabled={currentPage === totalPages}
              className="h-11 w-11 rounded-full bg-[#88a0ab] text-xl text-[#164152] disabled:opacity-40"
            >
              »
            </button>
          </div>
          <div className="flex items-center gap-3 self-end md:self-auto">
            <select
              value={pageSize}
              onChange={(e) => {
                setPageSize(Number(e.target.value));
                setCurrentPage(1);
              }}
              className="rounded-full border border-[#d9dde2] bg-white px-5 py-2 text-sm text-[#21415b] outline-none"
            >
              {pageSizeOptions.map((size) => (
                <option key={size} value={size}>
                  {size}
                </option>
              ))}
            </select>
            <span className="text-sm text-[#6f8b99]">items per page</span>
          </div>
        </div>
      </div>
    </div>
  );
}