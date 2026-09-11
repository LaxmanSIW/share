import { ArrowUp, ArrowDown } from "lucide-react";

interface SortableHeaderProps {
  label: string;
  sortKey: any;
  currentSortKey: any;
  sortDirection: "asc" | "desc" | null;
  onSort: (key: any) => void;
  align?: "left" | "center" | "right";
  className?: string;
}

export function SortableHeader({
  label,
  sortKey,
  currentSortKey,
  sortDirection,
  onSort,
  align = "left",
  className = "",
}: SortableHeaderProps) {
  const isActive = currentSortKey === sortKey;

  const alignClass = align === "center" ? "text-center" : align === "right" ? "text-right" : "text-left";

  return (
    <th
      onClick={() => onSort(sortKey)}
      className={`py-3 px-4 font-semibold uppercase tracking-wider cursor-pointer hover:bg-[#e8e0d4] transition-colors select-none ${alignClass} ${className}`}
    >
      <div className={`flex items-center gap-1 ${align === "center" ? "justify-center" : align === "right" ? "justify-end" : ""}`}>
        <span>{label}</span>
        {isActive && sortDirection === "asc" && (
          <ArrowUp className="w-3.5 h-3.5 text-[#c4703f]" />
        )}
        {isActive && sortDirection === "desc" && (
          <ArrowDown className="w-3.5 h-3.5 text-[#c4703f]" />
        )}
      </div>
    </th>
  );
}
