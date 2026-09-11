// lib/utils.ts

/**
 * Returns the last day of the month as a YYYY-MM-DD string
 */
export function getEndOfMonthString(year: number, month: number): string {
  const end = new Date(year, month, 0); // Day 0 of next month = Last day of current month
  return `${year}-${String(month).padStart(2, "0")}-${String(end.getDate()).padStart(2, "0")}`;
}

/**
 * Returns the first day of the month as a YYYY-MM-DD string
 */
export function getStartOfMonthString(year: number, month: number): string {
  return `${year}-${String(month).padStart(2, "0")}-01`;
}

/**
 * Returns an object with start and end date strings for a given month/year
 */
export function getMonthRange(year: number, month: number) {
  return {
    startStr: getStartOfMonthString(year, month),
    endStr: getEndOfMonthString(year, month),
  };
}