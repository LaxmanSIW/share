package com.invoicestudio.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ComponentPreset {

    public enum PresetType {
        INVOICE_HEADER("Business Header", "Header with business name, address, contact, and logo"),
        CUSTOMER_ADDRESS("Bill To / Customer Block", "Customer name, billing address, GSTIN, and contact details"),
        INVOICE_TOTALS("Invoice Totals Card", "Summary block showing subtotal, taxes, round-off, and grand total"),
        BANK_DETAILS("Bank & Payment Info", "Bank account name, number, IFSC code, and UPI ID"),
        PAYMENT_TERMS("Terms & Conditions", "Terms of sale, declarations, and notes"),
        SIGNATURE_SECTION("Authorized Signatory", "Signature block for business endorsement"),
        DOCUMENT_FOOTER("Document Footer", "Paging and computer-generated invoice disclaimer");

        private final String title;
        private final String description;

        PresetType(String title, String description) {
            this.title = title;
            this.description = description;
        }

        public String getTitle() { return title; }
        public String getDescription() { return description; }
    }

    private static String uid() {
        return "el_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    public static List<TemplateElement> createComponent(PresetType type, double startX, double startY) {
        List<TemplateElement> list = new ArrayList<>();
        String gid = "grp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        switch (type) {
            case INVOICE_HEADER -> {
                // Background banner
                TemplateElement bg = new TemplateElement();
                bg.setId(uid()); bg.setType(ElementType.RECT); bg.setGroupId(gid);
                bg.setX(startX); bg.setY(startY); bg.setW(194); bg.setH(28);
                bg.setBg("#f8fafc"); bg.setBorderWidth(0.3); bg.setBorderColor("#e2e8f0");
                bg.setBorderRadius(3.0); bg.setName("Header Card");
                list.add(bg);

                // Business Logo
                TemplateElement logo = new TemplateElement();
                logo.setId(uid()); logo.setType(ElementType.IMAGE); logo.setGroupId(gid);
                logo.setX(startX + 4); logo.setY(startY + 3); logo.setW(22); logo.setH(22);
                logo.setUseBusinessLogo(true); logo.setObjectFit("contain"); logo.setName("Company Logo");
                list.add(logo);

                // Business Name
                TemplateElement name = new TemplateElement();
                name.setId(uid()); name.setType(ElementType.TEXT); name.setGroupId(gid);
                name.setX(startX + 30); name.setY(startY + 3); name.setW(110); name.setH(7);
                name.setText("{{business_name}}"); name.setFontSize(14); name.setFontWeight(700);
                name.setColor("#0f172a"); name.setName("Business Name");
                list.add(name);

                // Business Address & Contact
                TemplateElement addr = new TemplateElement();
                addr.setId(uid()); addr.setType(ElementType.TEXT); addr.setGroupId(gid);
                addr.setX(startX + 30); addr.setY(startY + 10.5); name.setW(110); addr.setH(14);
                addr.setText("{{business_address}}\nGSTIN: {{business_gst}}   |   Phone: {{business_phone}}");
                addr.setFontSize(8); addr.setColor("#475569"); addr.setName("Business Details");
                list.add(addr);

                // Doc Type Badge
                TemplateElement badge = new TemplateElement();
                badge.setId(uid()); badge.setType(ElementType.TEXT); badge.setGroupId(gid);
                badge.setX(startX + 145); badge.setY(startY + 6); badge.setW(45); badge.setH(15);
                badge.setText("{{doc_type}}\n{{invoice_no}}"); badge.setFontSize(10); badge.setFontWeight(700);
                badge.setAlign("right"); badge.setColor("#2563eb"); badge.setName("Doc Info");
                list.add(badge);
            }
            case CUSTOMER_ADDRESS -> {
                TemplateElement bg = new TemplateElement();
                bg.setId(uid()); bg.setType(ElementType.RECT); bg.setGroupId(gid);
                bg.setX(startX); bg.setY(startY); bg.setW(95); bg.setH(34);
                bg.setBg("#ffffff"); bg.setBorderWidth(0.3); bg.setBorderColor("#cbd5e1");
                bg.setBorderRadius(2.5); bg.setName("Customer Box");
                list.add(bg);

                TemplateElement title = new TemplateElement();
                title.setId(uid()); title.setType(ElementType.TEXT); title.setGroupId(gid);
                title.setX(startX + 4); title.setY(startY + 3); title.setW(87); title.setH(5);
                title.setText("BILL TO"); title.setFontSize(8); title.setFontWeight(700);
                title.setColor("#64748b"); title.setLetterSpacing(1.5); title.setName("Bill To Label");
                list.add(title);

                TemplateElement bName = new TemplateElement();
                bName.setId(uid()); bName.setType(ElementType.TEXT); bName.setGroupId(gid);
                bName.setX(startX + 4); bName.setY(startY + 8); bName.setW(87); bName.setH(6);
                bName.setText("{{buyer_name}}"); bName.setFontSize(10.5); bName.setFontWeight(700);
                bName.setColor("#0f172a"); bName.setName("Buyer Name");
                list.add(bName);

                TemplateElement bAddr = new TemplateElement();
                bAddr.setId(uid()); bAddr.setType(ElementType.TEXT); bAddr.setGroupId(gid);
                bAddr.setX(startX + 4); bAddr.setY(startY + 14.5); bAddr.setW(87); bAddr.setH(16);
                bAddr.setText("{{buyer_address}}\nGSTIN: {{buyer_gstin}}   |   Phone: {{buyer_phone}}");
                bAddr.setFontSize(7.5); bAddr.setColor("#334155"); bAddr.setName("Buyer Address");
                list.add(bAddr);
            }
            case INVOICE_TOTALS -> {
                TemplateElement bg = new TemplateElement();
                bg.setId(uid()); bg.setType(ElementType.RECT); bg.setGroupId(gid);
                bg.setX(startX); bg.setY(startY); bg.setW(75); bg.setH(32);
                bg.setBg("#f8fafc"); bg.setBorderWidth(0.3); bg.setBorderColor("#cbd5e1");
                bg.setBorderRadius(2.5); bg.setName("Totals Box");
                list.add(bg);

                TemplateElement labels = new TemplateElement();
                labels.setId(uid()); labels.setType(ElementType.TEXT); labels.setGroupId(gid);
                labels.setX(startX + 4); labels.setY(startY + 3); labels.setW(36); labels.setH(26);
                labels.setText("Taxable Amount:\nCGST:\nSGST:\nRound Off:\nGrand Total:");
                labels.setFontSize(8); labels.setColor("#475569"); labels.setName("Totals Labels");
                list.add(labels);

                TemplateElement vals = new TemplateElement();
                vals.setId(uid()); vals.setType(ElementType.TEXT); vals.setGroupId(gid);
                vals.setX(startX + 40); vals.setY(startY + 3); vals.setW(31); vals.setH(26);
                vals.setText("{{taxable}}\n{{cgst}}\n{{sgst}}\n{{round_off}}\n{{grand_total}}");
                vals.setFontSize(8); vals.setFontWeight(700); vals.setAlign("right");
                vals.setColor("#0f172a"); vals.setName("Totals Values");
                list.add(vals);
            }
            case BANK_DETAILS -> {
                TemplateElement bg = new TemplateElement();
                bg.setId(uid()); bg.setType(ElementType.RECT); bg.setGroupId(gid);
                bg.setX(startX); bg.setY(startY); bg.setW(95); bg.setH(26);
                bg.setBg("#ffffff"); bg.setBorderWidth(0.3); bg.setBorderColor("#e2e8f0");
                bg.setBorderRadius(2.5); bg.setName("Bank Info Box");
                list.add(bg);

                TemplateElement t = new TemplateElement();
                t.setId(uid()); t.setType(ElementType.TEXT); t.setGroupId(gid);
                t.setX(startX + 4); t.setY(startY + 3); t.setW(87); t.setH(4);
                t.setText("BANK & PAYMENT DETAILS"); t.setFontSize(7.5); t.setFontWeight(700);
                t.setColor("#64748b"); t.setLetterSpacing(1.0); t.setName("Bank Header");
                list.add(t);

                TemplateElement details = new TemplateElement();
                details.setId(uid()); details.setType(ElementType.TEXT); details.setGroupId(gid);
                details.setX(startX + 4); details.setY(startY + 8); details.setW(87); details.setH(15);
                details.setText("Bank: {{bank_name}}   |   A/C: {{bank_account}}\nIFSC: {{bank_ifsc}}   |   UPI ID: {{bank_upi}}");
                details.setFontSize(7.5); details.setColor("#334155"); details.setName("Bank Details");
                list.add(details);
            }
            case PAYMENT_TERMS -> {
                TemplateElement bg = new TemplateElement();
                bg.setId(uid()); bg.setType(ElementType.RECT); bg.setGroupId(gid);
                bg.setX(startX); bg.setY(startY); bg.setW(110); bg.setH(22);
                bg.setBg("#fbfbfb"); bg.setBorderWidth(0.3); bg.setBorderColor("#e5e7eb");
                bg.setBorderRadius(2.0); bg.setName("Terms Box");
                list.add(bg);

                TemplateElement t = new TemplateElement();
                t.setId(uid()); t.setType(ElementType.TEXT); t.setGroupId(gid);
                t.setX(startX + 4); t.setY(startY + 2.5); t.setW(102); t.setH(4);
                t.setText("TERMS & CONDITIONS"); t.setFontSize(7.5); t.setFontWeight(700);
                t.setColor("#6b7280"); t.setName("Terms Title");
                list.add(t);

                TemplateElement txt = new TemplateElement();
                txt.setId(uid()); txt.setType(ElementType.TEXT); txt.setGroupId(gid);
                txt.setX(startX + 4); txt.setY(startY + 7); txt.setW(102); txt.setH(12);
                txt.setText("{{terms}}"); txt.setFontSize(7); txt.setColor("#4b5563"); txt.setName("Terms Content");
                list.add(txt);
            }
            case SIGNATURE_SECTION -> {
                TemplateElement line = new TemplateElement();
                line.setId(uid()); line.setType(ElementType.LINE); line.setGroupId(gid);
                line.setX(startX); line.setY(startY + 15); line.setW(50); line.setH(1);
                line.setBorderWidth(0.4); line.setBorderColor("#94a3b8"); line.setName("Signature Line");
                list.add(line);

                TemplateElement forTxt = new TemplateElement();
                forTxt.setId(uid()); forTxt.setType(ElementType.TEXT); forTxt.setGroupId(gid);
                forTxt.setX(startX); forTxt.setY(startY); forTxt.setW(50); forTxt.setH(5);
                forTxt.setText("For {{business_name}}"); forTxt.setFontSize(8); forTxt.setFontWeight(700);
                forTxt.setAlign("center"); forTxt.setColor("#1e293b"); forTxt.setName("Sign For");
                list.add(forTxt);

                TemplateElement signTxt = new TemplateElement();
                signTxt.setId(uid()); signTxt.setType(ElementType.TEXT); signTxt.setGroupId(gid);
                signTxt.setX(startX); signTxt.setY(startY + 17); signTxt.setW(50); signTxt.setH(5);
                signTxt.setText("Authorized Signatory"); signTxt.setFontSize(7.5);
                signTxt.setAlign("center"); signTxt.setColor("#64748b"); signTxt.setName("Sign Label");
                list.add(signTxt);
            }
            case DOCUMENT_FOOTER -> {
                TemplateElement div = new TemplateElement();
                div.setId(uid()); div.setType(ElementType.DIVIDER); div.setGroupId(gid);
                div.setX(startX); div.setY(startY); div.setW(194); div.setH(1);
                div.setBorderWidth(0.3); div.setBorderColor("#cbd5e1"); div.setName("Footer Divider");
                list.add(div);

                TemplateElement note = new TemplateElement();
                note.setId(uid()); note.setType(ElementType.TEXT); note.setGroupId(gid);
                note.setX(startX); note.setY(startY + 2); note.setW(130); note.setH(5);
                note.setText("This is a computer generated invoice and requires no physical signature.");
                note.setFontSize(7); note.setColor("#94a3b8"); note.setName("Computer Note");
                list.add(note);

                TemplateElement page = new TemplateElement();
                page.setId(uid()); page.setType(ElementType.PAGENO); page.setGroupId(gid);
                page.setX(startX + 144); page.setY(startY + 2); page.setW(50); page.setH(5);
                page.setText("Page {{page_no}} of {{page_count}}"); page.setFontSize(7);
                page.setAlign("right"); page.setColor("#94a3b8"); page.setName("Paging");
                list.add(page);
            }
        }
        return list;
    }
}
