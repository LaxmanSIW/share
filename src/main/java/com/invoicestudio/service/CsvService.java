package com.invoicestudio.service;

import com.invoicestudio.model.Bill;
import com.invoicestudio.model.Buyer;
import com.invoicestudio.model.BuyerFieldDef;
import com.invoicestudio.model.DocType;
import com.invoicestudio.model.Transport;

import java.util.*;
import java.util.regex.Pattern;

public class CsvService {

    private static final Pattern GSTIN_PATTERN = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^([6-9]\\d{9}|0[6-9]\\d{9}|91[6-9]\\d{9})$");

    public static boolean isValidGstin(String gst) {
        if (gst == null) return false;
        return GSTIN_PATTERN.matcher(gst.trim().toUpperCase()).matches();
    }

    public static boolean isValidPhone(String phone) {
        if (phone == null) return false;
        String d = phone.replaceAll("[^0-9]", "");
        return PHONE_PATTERN.matcher(d).matches();
    }

    public static String csvCell(Object val) {
        String s = val != null ? String.valueOf(val) : "";
        if (s.contains("\"") || s.contains(",") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    public static String csvRow(List<?> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(csvCell(cells.get(i)));
        }
        sb.append("\r\n");
        return sb.toString();
    }

    public static List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null || text.isBlank()) return rows;
        String src = text.startsWith("\uFEFF") ? text.substring(1) : text;

        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        int len = src.length();

        while (i < len) {
            char c = src.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < len && src.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                        continue;
                    }
                    inQuotes = false;
                    i++;
                    continue;
                }
                field.append(c);
                i++;
                continue;
            }

            if (c == '"') {
                inQuotes = true;
                i++;
                continue;
            }
            if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
                i++;
                continue;
            }
            if (c == '\n') {
                row.add(field.toString());
                rows.add(row);
                row = new ArrayList<>();
                field.setLength(0);
                i++;
                continue;
            }
            if (c == '\r') {
                i++;
                continue;
            }
            field.append(c);
            i++;
        }

        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    public static String exportBuyers(List<Buyer> buyers, List<BuyerFieldDef> customFields) {
        return exportBuyers(buyers, customFields, List.of());
    }

    public static String exportBuyers(List<Buyer> buyers, List<BuyerFieldDef> customFields, List<Transport> transports) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        List<String> header = new ArrayList<>(List.of("Name", "Address", "GSTIN", "Phone", "State", "State Code",
                "Opening Balance", "City", "Contact Person", "Default Transport"));
        if (customFields != null) {
            for (BuyerFieldDef cf : customFields) {
                header.add(cf.getLabel());
            }
        }
        sb.append(csvRow(header));

        // Build transport ID → name lookup
        Map<String, String> transportNames = new HashMap<>();
        if (transports != null) {
            for (Transport t : transports) {
                if (t.getId() != null) transportNames.put(t.getId(), t.getName());
            }
        }

        for (Buyer b : buyers) {
            List<String> row = new ArrayList<>();
            row.add(b.getName());
            row.add(b.getAddress());
            row.add(b.getGst());
            row.add(b.getPhone());
            row.add(b.getState());
            row.add(b.getEffectiveStateCode());
            row.add(b.getOpeningBalance() != 0 ? String.valueOf(b.getOpeningBalance()) : "");
            row.add(b.getCity());
            row.add(b.getContactPerson());
            row.add(transportNames.getOrDefault(b.getDefaultTransportId(), ""));
            if (customFields != null) {
                for (BuyerFieldDef cf : customFields) {
                    row.add(b.getCustom().getOrDefault(cf.getKey(), ""));
                }
            }
            sb.append(csvRow(row));
        }
        return sb.toString();
    }

    public static String exportBills(List<Bill> bills) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append(csvRow(List.of("Bill No", "Date", "Doc Type", "Status", "Buyer Name", "Buyer GSTIN", "Subtotal", "Discount", "Taxable", "CGST", "SGST", "IGST", "Grand Total")));

        for (Bill b : bills) {
            sb.append(csvRow(List.of(
                b.getBillNo(),
                b.getDate(),
                b.getDocType().getLabel(),
                b.getStatus().getLabel(),
                b.getVariables().getOrDefault("buyer_name", ""),
                b.getVariables().getOrDefault("buyer_gst", ""),
                String.format("%.2f", b.getTotals().getSubtotal()),
                String.format("%.2f", b.getTotals().getDiscount()),
                String.format("%.2f", b.getTotals().getTaxable()),
                String.format("%.2f", b.getTotals().getCgst()),
                String.format("%.2f", b.getTotals().getSgst()),
                String.format("%.2f", b.getTotals().getIgst()),
                String.format("%.2f", b.getTotals().getGrandTotal())
            )));
        }
        return sb.toString();
    }

    public static String exportGstSummary(List<Bill> bills) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append(csvRow(List.of("Month", "Bills", "Subtotal", "Discount", "Taxable Value", "CGST", "SGST", "IGST", "Round Off", "Grand Total")));

        Map<String, double[]> monthly = new TreeMap<>();
        for (Bill b : bills) {
            if (b.getDocType() != DocType.INVOICE || b.getStatus() == com.invoicestudio.model.BillStatus.CANCELLED) continue;
            String month = b.getDate() != null && b.getDate().length() >= 7 ? b.getDate().substring(0, 7) : "Unknown";
            double[] arr = monthly.computeIfAbsent(month, k -> new double[9]);
            arr[0] += 1; // bills count
            arr[1] += b.getTotals().getSubtotal();
            arr[2] += b.getTotals().getDiscount();
            arr[3] += b.getTotals().getTaxable();
            arr[4] += b.getTotals().getCgst();
            arr[5] += b.getTotals().getSgst();
            arr[6] += b.getTotals().getIgst();
            arr[7] += b.getTotals().getRoundOff();
            arr[8] += b.getTotals().getGrandTotal();
        }

        double totalBills = 0;
        double[] grand = new double[8];

        for (Map.Entry<String, double[]> e : monthly.entrySet()) {
            double[] d = e.getValue();
            totalBills += d[0];
            for (int i = 0; i < 8; i++) grand[i] += d[i + 1];

            sb.append(csvRow(List.of(
                e.getKey(),
                (int) d[0],
                String.format("%.2f", d[1]),
                String.format("%.2f", d[2]),
                String.format("%.2f", d[3]),
                String.format("%.2f", d[4]),
                String.format("%.2f", d[5]),
                String.format("%.2f", d[6]),
                String.format("%.2f", d[7]),
                String.format("%.2f", d[8])
            )));
        }

        sb.append(csvRow(List.of(
            "TOTAL",
            (int) totalBills,
            String.format("%.2f", grand[0]),
            String.format("%.2f", grand[1]),
            String.format("%.2f", grand[2]),
            String.format("%.2f", grand[3]),
            String.format("%.2f", grand[4]),
            String.format("%.2f", grand[5]),
            String.format("%.2f", grand[6]),
            String.format("%.2f", grand[7])
        )));

        return sb.toString();
    }

    public static String getSampleBuyerCsv(List<BuyerFieldDef> customFields) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        List<String> header = new ArrayList<>(List.of("Name", "Address", "GSTIN", "Phone", "State", "State Code",
                "Opening Balance", "City", "Contact Person", "Default Transport"));
        if (customFields != null) {
            for (BuyerFieldDef cf : customFields) header.add(cf.getLabel());
        }
        sb.append(csvRow(header));

        List<String> sampleRow = new ArrayList<>(List.of("Acme Enterprises", "101 Industrial Area, Phase 2, Pune",
                "27AAAAA0000A1Z5", "9876543210", "Maharashtra", "27",
                "5000.00", "Pune", "Mr. Sharma", "Shree Ganesh Transport"));
        if (customFields != null) {
            for (BuyerFieldDef ignored : customFields) sampleRow.add("Sample Value");
        }
        sb.append(csvRow(sampleRow));
        return sb.toString();
    }
}
