package com.invoicestudio.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PresetTemplates {

    public static List<TableColumn> defaultItemColumns() {
        List<TableColumn> cols = new ArrayList<>();
        cols.add(new TableColumn("sr", "Sr", 7, "center"));
        cols.add(new TableColumn("desc", "Description of Goods", 37, "left"));
        cols.add(new TableColumn("hsn", "HSN", 12, "center"));
        cols.add(new TableColumn("qty", "Qty", 9, "right"));
        cols.add(new TableColumn("unit", "Unit", 9, "center"));
        cols.add(new TableColumn("rate", "Rate", 12, "right"));
        cols.add(new TableColumn("gst", "GST%", 7, "right"));
        cols.add(new TableColumn("amount", "Amount", 14, "right"));
        return cols;
    }

    public static List<Template> getAllPresets() {
        List<Template> list = new ArrayList<>();
        list.add(buildClassic());
        list.add(buildModern());
        list.add(buildCompactA5());
        list.add(buildMinimal());
        list.add(buildThermal80());
        list.add(buildThermal58());
        return list;
    }

    public static Template buildClassic() {
        String now = Instant.now().toString();
        Template t = new Template("tpl_classic", "GST Tax Invoice — Classic",
                new PageConfig(PageSizeName.A4, 210, 297, "portrait", new PageConfig.Margins(8, 8, 8, 8)),
                new ArrayList<>());
        t.setCreatedAt(now);
        t.setUpdatedAt(now);

        List<TemplateElement> el = t.getElements();
        int z = 0;

        // Header cream rect
        TemplateElement r1 = new TemplateElement();
        r1.setId(uid()); r1.setType(ElementType.RECT); r1.setX(0); r1.setY(0); r1.setW(210); r1.setH(30);
        r1.setBg("#f4f1ea"); r1.setZIndex(z++); el.add(r1);

        // Business Logo
        TemplateElement img = new TemplateElement();
        img.setId(uid()); img.setType(ElementType.IMAGE); img.setX(8); img.setY(4); img.setW(26); img.setH(22);
        img.setObjectFit("contain"); img.setUseBusinessLogo(true); img.setZIndex(z++); el.add(img);

        // Business details
        TemplateElement bName = new TemplateElement();
        bName.setId(uid()); bName.setType(ElementType.TEXT); bName.setX(40); bName.setY(4); bName.setW(110); bName.setH(8);
        bName.setText("{{business_name}}"); bName.setFontSize(17); bName.setFontWeight(700); bName.setColor("#1a1a1a");
        bName.setLetterSpacing(1.2); bName.setZIndex(z++); el.add(bName);

        TemplateElement bAddr = new TemplateElement();
        bAddr.setId(uid()); bAddr.setType(ElementType.TEXT); bAddr.setX(40); bAddr.setY(12.5); bAddr.setW(110); bAddr.setH(10);
        bAddr.setText("{{business_address}}"); bAddr.setFontSize(8); bAddr.setColor("#444444"); bAddr.setLineHeight(1.4);
        bAddr.setZIndex(z++); el.add(bAddr);

        TemplateElement bGst = new TemplateElement();
        bGst.setId(uid()); bGst.setType(ElementType.TEXT); bGst.setX(40); bGst.setY(23.5); bGst.setW(130); bGst.setH(5);
        bGst.setText("GSTIN: {{business_gst}}   |   Phone: {{business_phone}}   |   {{business_email}}");
        bGst.setFontSize(8); bGst.setColor("#555555"); bGst.setZIndex(z++); el.add(bGst);

        // Dark title banner
        TemplateElement r2 = new TemplateElement();
        r2.setId(uid()); r2.setType(ElementType.RECT); r2.setX(0); r2.setY(30); r2.setW(210); r2.setH(11);
        r2.setBg("#1c1c1c"); r2.setZIndex(z++); el.add(r2);

        TemplateElement title = new TemplateElement();
        title.setId(uid()); title.setType(ElementType.TEXT); title.setX(0); title.setY(30); title.setW(210); title.setH(11);
        title.setText("TAX INVOICE"); title.setFontSize(12.5); title.setFontWeight(700); title.setColor("#f4f1ea");
        title.setAlign("center"); title.setVAlign("middle"); title.setLetterSpacing(4); title.setZIndex(z++); el.add(title);

        // Bill to box
        TemplateElement r3 = new TemplateElement();
        r3.setId(uid()); r3.setType(ElementType.RECT); r3.setX(0); r3.setY(41); r3.setW(128); r3.setH(34);
        r3.setBg("#fafafa"); r3.setBorderWidth(0.3); r3.setBorderColor("#c9c4b8"); r3.setZIndex(z++); el.add(r3);

        TemplateElement billToLbl = new TemplateElement();
        billToLbl.setId(uid()); billToLbl.setType(ElementType.TEXT); billToLbl.setX(5); billToLbl.setY(44); billToLbl.setW(60); billToLbl.setH(4.5);
        billToLbl.setText("BILL TO"); billToLbl.setFontSize(8); billToLbl.setFontWeight(700); billToLbl.setColor("#8a7a4a");
        billToLbl.setLetterSpacing(2); billToLbl.setZIndex(z++); el.add(billToLbl);

        TemplateElement byrName = new TemplateElement();
        byrName.setId(uid()); byrName.setType(ElementType.TEXT); byrName.setX(5); byrName.setY(49); byrName.setW(118); byrName.setH(6);
        byrName.setText("{{buyer_name}}"); byrName.setFontSize(11.5); byrName.setFontWeight(700); byrName.setColor("#1a1a1a");
        byrName.setZIndex(z++); el.add(byrName);

        TemplateElement byrAddr = new TemplateElement();
        byrAddr.setId(uid()); byrAddr.setType(ElementType.TEXT); byrAddr.setX(5); byrAddr.setY(55.5); byrAddr.setW(118); byrAddr.setH(10);
        byrAddr.setText("{{buyer_address}}"); byrAddr.setFontSize(8.5); byrAddr.setColor("#444444"); byrAddr.setLineHeight(1.4);
        byrAddr.setZIndex(z++); el.add(byrAddr);

        TemplateElement byrGst = new TemplateElement();
        byrGst.setId(uid()); byrGst.setType(ElementType.TEXT); byrGst.setX(5); byrGst.setY(66.5); byrGst.setW(118); byrGst.setH(5);
        byrGst.setText("GSTIN: {{buyer_gst}}   |   Ph: {{buyer_phone}}"); byrGst.setFontSize(8); byrGst.setColor("#555555");
        byrGst.setZIndex(z++); el.add(byrGst);

        // Invoice details box
        TemplateElement r4 = new TemplateElement();
        r4.setId(uid()); r4.setType(ElementType.RECT); r4.setX(128); r4.setY(41); r4.setW(82); r4.setH(34);
        r4.setBg("#fafafa"); r4.setBorderWidth(0.3); r4.setBorderColor("#c9c4b8"); r4.setZIndex(z++); el.add(r4);

        TemplateElement invLbl = new TemplateElement();
        invLbl.setId(uid()); invLbl.setType(ElementType.TEXT); invLbl.setX(133); invLbl.setY(44); invLbl.setW(34); invLbl.setH(4.5);
        invLbl.setText("INVOICE DETAILS"); invLbl.setFontSize(8); invLbl.setFontWeight(700); invLbl.setColor("#8a7a4a");
        invLbl.setLetterSpacing(2); invLbl.setZIndex(z++); el.add(invLbl);

        addInvRow(el, z, 133, 49.5, "Invoice No:", "{{invoice_no}}", true); z += 2;
        addInvRow(el, z, 133, 55, "Date:", "{{invoice_date}}", false); z += 2;
        addInvRow(el, z, 133, 60.5, "PO No:", "{{po_no}}", false); z += 2;
        addInvRow(el, z, 133, 66, "Transport:", "{{transport_name}}", false); z += 2;

        // Items table
        TemplateElement table = new TemplateElement();
        table.setId(uid()); table.setType(ElementType.TABLE); table.setX(0); table.setY(75); table.setW(210); table.setH(20);
        table.setColumns(defaultItemColumns()); table.setHeaderBg("#efe9db"); table.setHeaderColor("#1a1a1a");
        table.setRowHeight(7); table.setFontSize(8.5); table.setBorderStyle("grid"); table.setShowZebra(true);
        table.setZIndex(z++); el.add(table);

        // Totals Box
        TemplateElement r5 = new TemplateElement();
        r5.setId(uid()); r5.setType(ElementType.RECT); r5.setX(128); r5.setY(175); r5.setW(82); r5.setH(40);
        r5.setBg("#fafafa"); r5.setBorderWidth(0.3); r5.setBorderColor("#c9c4b8"); r5.setZIndex(z++); el.add(r5);

        addTotalRow(el, z, 133, 178.5, "Subtotal", "{{subtotal}}", false); z += 2;
        addTotalRow(el, z, 133, 184.5, "CGST", "{{cgst}}", false); z += 2;
        addTotalRow(el, z, 133, 190.5, "SGST", "{{sgst}}", false); z += 2;
        addTotalRow(el, z, 133, 196.5, "Round Off", "{{round_off}}", false); z += 2;

        // Grand Total dark bar
        TemplateElement r6 = new TemplateElement();
        r6.setId(uid()); r6.setType(ElementType.RECT); r6.setX(130); r6.setY(202.5); r6.setW(78); r6.setH(10);
        r6.setBg("#1c1c1c"); r6.setZIndex(z++); el.add(r6);

        TemplateElement gtLbl = new TemplateElement();
        gtLbl.setId(uid()); gtLbl.setType(ElementType.TEXT); gtLbl.setX(133); gtLbl.setY(202.5); gtLbl.setW(45); gtLbl.setH(10);
        gtLbl.setText("GRAND TOTAL"); gtLbl.setFontSize(9.5); gtLbl.setFontWeight(700); gtLbl.setColor("#f4f1ea");
        gtLbl.setVAlign("middle"); gtLbl.setZIndex(z++); el.add(gtLbl);

        TemplateElement gtVal = new TemplateElement();
        gtVal.setId(uid()); gtVal.setType(ElementType.TEXT); gtVal.setX(165); gtVal.setY(202.5); gtVal.setW(40); gtVal.setH(10);
        gtVal.setText("{{grand_total}}"); gtVal.setFontSize(10.5); gtVal.setFontWeight(700); gtVal.setColor("#f4f1ea");
        gtVal.setAlign("right"); gtVal.setVAlign("middle"); gtVal.setZIndex(z++); el.add(gtVal);

        // Amount in words
        TemplateElement r7 = new TemplateElement();
        r7.setId(uid()); r7.setType(ElementType.RECT); r7.setX(0); r7.setY(175); r7.setW(128); r7.setH(14);
        r7.setBg("#fafafa"); r7.setBorderWidth(0.3); r7.setBorderColor("#c9c4b8"); r7.setZIndex(z++); el.add(r7);

        TemplateElement aiwLbl = new TemplateElement();
        aiwLbl.setId(uid()); aiwLbl.setType(ElementType.TEXT); aiwLbl.setX(5); aiwLbl.setY(177); aiwLbl.setW(60); aiwLbl.setH(4.5);
        aiwLbl.setText("AMOUNT IN WORDS"); aiwLbl.setFontSize(7.5); aiwLbl.setFontWeight(700); aiwLbl.setColor("#8a7a4a");
        aiwLbl.setLetterSpacing(1.5); aiwLbl.setZIndex(z++); el.add(aiwLbl);

        TemplateElement aiwVal = new TemplateElement();
        aiwVal.setId(uid()); aiwVal.setType(ElementType.TEXT); aiwVal.setX(5); aiwVal.setY(182); aiwVal.setW(118); aiwVal.setH(6);
        aiwVal.setText("{{amount_in_words}}"); aiwVal.setFontSize(9); aiwVal.setItalic(true); aiwVal.setFontWeight(600);
        aiwVal.setColor("#1a1a1a"); aiwVal.setZIndex(z++); el.add(aiwVal);

        // Bank Details box
        TemplateElement r8 = new TemplateElement();
        r8.setId(uid()); r8.setType(ElementType.RECT); r8.setX(0); r8.setY(189); r8.setW(128); r8.setH(26);
        r8.setBg("#fafafa"); r8.setBorderWidth(0.3); r8.setBorderColor("#c9c4b8"); r8.setZIndex(z++); el.add(r8);

        TemplateElement bankLbl = new TemplateElement();
        bankLbl.setId(uid()); bankLbl.setType(ElementType.TEXT); bankLbl.setX(5); bankLbl.setY(191); bankLbl.setW(60); bankLbl.setH(4.5);
        bankLbl.setText("BANK DETAILS"); bankLbl.setFontSize(7.5); bankLbl.setFontWeight(700); bankLbl.setColor("#8a7a4a");
        bankLbl.setLetterSpacing(1.5); bankLbl.setZIndex(z++); el.add(bankLbl);

        TemplateElement bankVal = new TemplateElement();
        bankVal.setId(uid()); bankVal.setType(ElementType.TEXT); bankVal.setX(5); bankVal.setY(196); bankVal.setW(118); bankVal.setH(18);
        bankVal.setText("Bank: {{bank_name}}\nA/C: {{bank_account}}    IFSC: {{bank_ifsc}}\nUPI: {{bank_upi}}");
        bankVal.setFontSize(8); bankVal.setColor("#444444"); bankVal.setLineHeight(1.5); bankVal.setZIndex(z++); el.add(bankVal);

        // Terms
        TemplateElement termsLbl = new TemplateElement();
        termsLbl.setId(uid()); termsLbl.setType(ElementType.TEXT); termsLbl.setX(0); termsLbl.setY(219); termsLbl.setW(128); termsLbl.setH(4.5);
        termsLbl.setText("TERMS & CONDITIONS"); termsLbl.setFontSize(7.5); termsLbl.setFontWeight(700); termsLbl.setColor("#8a7a4a");
        termsLbl.setLetterSpacing(1.5); termsLbl.setZIndex(z++); el.add(termsLbl);

        TemplateElement termsVal = new TemplateElement();
        termsVal.setId(uid()); termsVal.setType(ElementType.TEXT); termsVal.setX(0); termsVal.setY(224); termsVal.setW(128); termsVal.setH(24);
        termsVal.setText("{{terms}}"); termsVal.setFontSize(7.5); termsVal.setColor("#555555"); termsVal.setLineHeight(1.5);
        termsVal.setZIndex(z++); el.add(termsVal);

        // Signatory
        TemplateElement sigLine = new TemplateElement();
        sigLine.setId(uid()); sigLine.setType(ElementType.LINE); sigLine.setX(145); sigLine.setY(258); sigLine.setW(55); sigLine.setH(0.4);
        sigLine.setBorderWidth(0.4); sigLine.setBorderColor("#1a1a1a"); sigLine.setZIndex(z++); el.add(sigLine);

        TemplateElement sigFor = new TemplateElement();
        sigFor.setId(uid()); sigFor.setType(ElementType.TEXT); sigFor.setX(132); sigFor.setY(246); sigFor.setW(78); sigFor.setH(5);
        sigFor.setText("For {{business_name}}"); sigFor.setFontSize(9); sigFor.setFontWeight(600); sigFor.setAlign("center");
        sigFor.setZIndex(z++); el.add(sigFor);

        TemplateElement sigAuth = new TemplateElement();
        sigAuth.setId(uid()); sigAuth.setType(ElementType.TEXT); sigAuth.setX(132); sigAuth.setY(261); sigAuth.setW(78); sigAuth.setH(5);
        sigAuth.setText("Authorised Signatory"); sigAuth.setFontSize(8); sigAuth.setAlign("center"); sigAuth.setColor("#555555");
        sigAuth.setZIndex(z++); el.add(sigAuth);

        // Footer
        TemplateElement foot = new TemplateElement();
        foot.setId(uid()); foot.setType(ElementType.TEXT); foot.setX(0); foot.setY(288); foot.setW(210); foot.setH(6);
        foot.setText("This is a computer generated invoice  •  {{copy_label}}  •  Page {{page_no}} of {{page_count}}");
        foot.setFontSize(7); foot.setAlign("center"); foot.setColor("#999999"); foot.setRepeatOnPages(true);
        foot.setZIndex(z++); el.add(foot);

        return t;
    }

    public static Template buildModern() {
        String now = Instant.now().toString();
        Template t = new Template("tpl_modern", "GST Tax Invoice — Modern",
                new PageConfig(PageSizeName.A4, 210, 297, "portrait", new PageConfig.Margins(8, 8, 8, 8)),
                new ArrayList<>());
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        List<TemplateElement> el = t.getElements();
        int z = 0;

        TemplateElement r1 = new TemplateElement();
        r1.setId(uid()); r1.setType(ElementType.RECT); r1.setX(0); r1.setY(0); r1.setW(210); r1.setH(26); r1.setBg("#181818");
        r1.setZIndex(z++); el.add(r1);

        TemplateElement goldRule = new TemplateElement();
        goldRule.setId(uid()); goldRule.setType(ElementType.RECT); goldRule.setX(0); goldRule.setY(26); goldRule.setW(210); goldRule.setH(1.2);
        goldRule.setBg("#d9a13b"); goldRule.setZIndex(z++); el.add(goldRule);

        TemplateElement img = new TemplateElement();
        img.setId(uid()); img.setType(ElementType.IMAGE); img.setX(10); img.setY(5); img.setW(24); img.setH(16);
        img.setObjectFit("contain"); img.setUseBusinessLogo(true); img.setZIndex(z++); el.add(img);

        TemplateElement bName = new TemplateElement();
        bName.setId(uid()); bName.setType(ElementType.TEXT); bName.setX(40); bName.setY(5); bName.setW(100); bName.setH(8);
        bName.setText("{{business_name}}"); bName.setFontSize(16); bName.setFontWeight(700); bName.setColor("#f5f2ea");
        bName.setZIndex(z++); el.add(bName);

        TemplateElement bAddr = new TemplateElement();
        bAddr.setId(uid()); bAddr.setType(ElementType.TEXT); bAddr.setX(40); bAddr.setY(13.5); bAddr.setW(100); bAddr.setH(10);
        bAddr.setText("{{business_address}}"); bAddr.setFontSize(8); bAddr.setColor("#b8b2a4"); bAddr.setLineHeight(1.4);
        bAddr.setZIndex(z++); el.add(bAddr);

        TemplateElement docType = new TemplateElement();
        docType.setId(uid()); docType.setType(ElementType.TEXT); docType.setX(140); docType.setY(5); docType.setW(60); docType.setH(6);
        docType.setText("{{doc_type}}"); docType.setFontSize(13); docType.setFontWeight(700); docType.setAlign("right");
        docType.setColor("#d9a13b"); docType.setZIndex(z++); el.add(docType);

        TemplateElement bMeta = new TemplateElement();
        bMeta.setId(uid()); bMeta.setType(ElementType.TEXT); bMeta.setX(140); bMeta.setY(12); bMeta.setW(60); bMeta.setH(10);
        bMeta.setText("GSTIN: {{business_gst}}\nPh: {{business_phone}} · {{business_email}}");
        bMeta.setFontSize(7.5); bMeta.setAlign("right"); bMeta.setColor("#b8b2a4"); bMeta.setLineHeight(1.5);
        bMeta.setZIndex(z++); el.add(bMeta);

        // Buyer box
        TemplateElement r2 = new TemplateElement();
        r2.setId(uid()); r2.setType(ElementType.RECT); r2.setX(8); r2.setY(34); r2.setW(194); r2.setH(30);
        r2.setBg("#f7f5f0"); r2.setBorderWidth(0.3); r2.setBorderColor("#d9d2c2"); r2.setZIndex(z++); el.add(r2);

        TemplateElement billTo = new TemplateElement();
        billTo.setId(uid()); billTo.setType(ElementType.TEXT); billTo.setX(12); billTo.setY(37); billTo.setW(90); billTo.setH(4.5);
        billTo.setText("BILL TO"); billTo.setFontSize(7.5); billTo.setFontWeight(700); billTo.setColor("#8a7a4a");
        billTo.setZIndex(z++); el.add(billTo);

        TemplateElement byrName = new TemplateElement();
        byrName.setId(uid()); byrName.setType(ElementType.TEXT); byrName.setX(12); byrName.setY(42); byrName.setW(90); byrName.setH(6);
        byrName.setText("{{buyer_name}}"); byrName.setFontSize(11); byrName.setFontWeight(700); byrName.setZIndex(z++); el.add(byrName);

        TemplateElement byrAddr = new TemplateElement();
        byrAddr.setId(uid()); byrAddr.setType(ElementType.TEXT); byrAddr.setX(12); byrAddr.setY(48.5); byrAddr.setW(90); byrAddr.setH(10);
        byrAddr.setText("{{buyer_address}}"); byrAddr.setFontSize(8); byrAddr.setColor("#444444"); byrAddr.setLineHeight(1.4);
        byrAddr.setZIndex(z++); el.add(byrAddr);

        addInvRow(el, z, 110, 37, "Invoice No", "{{invoice_no}}", true); z += 2;
        addInvRow(el, z, 110, 42.5, "Date", "{{invoice_date}}", false); z += 2;
        addInvRow(el, z, 110, 48, "PO No", "{{po_no}}", false); z += 2;
        addInvRow(el, z, 110, 53.5, "Place of Supply", "{{buyer_state}}", false); z += 2;

        // Table
        TemplateElement table = new TemplateElement();
        table.setId(uid()); table.setType(ElementType.TABLE); table.setX(8); table.setY(70); table.setW(194); table.setH(20);
        table.setColumns(defaultItemColumns()); table.setHeaderBg("#181818"); table.setHeaderColor("#f5f2ea");
        table.setRowHeight(7); table.setFontSize(8.5); table.setBorderStyle("rows"); table.setShowZebra(true);
        table.setZIndex(z++); el.add(table);

        // Totals
        addTotalRow(el, z, 110, 185, "Subtotal", "{{subtotal}}", false); z += 2;
        addTotalRow(el, z, 110, 191, "CGST", "{{cgst}}", false); z += 2;
        addTotalRow(el, z, 110, 197, "SGST", "{{sgst}}", false); z += 2;

        TemplateElement r3 = new TemplateElement();
        r3.setId(uid()); r3.setType(ElementType.RECT); r3.setX(110); r3.setY(204); r3.setW(88); r3.setH(11);
        r3.setBg("#181818"); r3.setZIndex(z++); el.add(r3);

        TemplateElement totLbl = new TemplateElement();
        totLbl.setId(uid()); totLbl.setType(ElementType.TEXT); totLbl.setX(114); totLbl.setY(204); totLbl.setW(40); totLbl.setH(11);
        totLbl.setText("TOTAL"); totLbl.setFontSize(10); totLbl.setFontWeight(700); totLbl.setColor("#d9a13b");
        totLbl.setVAlign("middle"); totLbl.setZIndex(z++); el.add(totLbl);

        TemplateElement totVal = new TemplateElement();
        totVal.setId(uid()); totVal.setType(ElementType.TEXT); totVal.setX(152); totVal.setY(204); totVal.setW(42); totVal.setH(11);
        totVal.setText("{{grand_total}}"); totVal.setFontSize(11); totVal.setFontWeight(700); totVal.setColor("#f5f2ea");
        totVal.setAlign("right"); totVal.setVAlign("middle"); totVal.setZIndex(z++); el.add(totVal);

        // Words & bank
        TemplateElement aiwVal = new TemplateElement();
        aiwVal.setId(uid()); aiwVal.setType(ElementType.TEXT); aiwVal.setX(8); aiwVal.setY(190); aiwVal.setW(94); aiwVal.setH(12);
        aiwVal.setText("{{amount_in_words}}"); aiwVal.setFontSize(8.5); aiwVal.setItalic(true); aiwVal.setLineHeight(1.4);
        aiwVal.setZIndex(z++); el.add(aiwVal);

        TemplateElement bankVal = new TemplateElement();
        bankVal.setId(uid()); bankVal.setType(ElementType.TEXT); bankVal.setX(8); bankVal.setY(209); bankVal.setW(94); bankVal.setH(16);
        bankVal.setText("{{bank_name}}\nA/C {{bank_account}} · {{bank_ifsc}}\nUPI: {{bank_upi}}");
        bankVal.setFontSize(7.5); bankVal.setColor("#444444"); bankVal.setLineHeight(1.5); bankVal.setZIndex(z++); el.add(bankVal);

        TemplateElement foot = new TemplateElement();
        foot.setId(uid()); foot.setType(ElementType.TEXT); foot.setX(0); foot.setY(288); foot.setW(210); foot.setH(6);
        foot.setText("{{copy_label}}  •  Page {{page_no}} of {{page_count}}"); foot.setFontSize(7); foot.setAlign("center");
        foot.setColor("#999999"); foot.setRepeatOnPages(true); foot.setZIndex(z++); el.add(foot);

        return t;
    }

    public static Template buildCompactA5() {
        String now = Instant.now().toString();
        Template t = new Template("tpl_compact_a5", "Compact GST Invoice (A5)",
                new PageConfig(PageSizeName.A5, 148, 210, "portrait", new PageConfig.Margins(6, 6, 6, 6)),
                new ArrayList<>());
        t.setCreatedAt(now); t.setUpdatedAt(now);
        List<TemplateElement> el = t.getElements();
        int z = 0;

        TemplateElement bName = new TemplateElement();
        bName.setId(uid()); bName.setType(ElementType.TEXT); bName.setX(6); bName.setY(5); bName.setW(90); bName.setH(7);
        bName.setText("{{business_name}}"); bName.setFontSize(13); bName.setFontWeight(700); bName.setZIndex(z++); el.add(bName);

        TemplateElement bAddr = new TemplateElement();
        bAddr.setId(uid()); bAddr.setType(ElementType.TEXT); bAddr.setX(6); bAddr.setY(12.5); bAddr.setW(90); bAddr.setH(9);
        bAddr.setText("{{business_address}}"); bAddr.setFontSize(6.8); bAddr.setColor("#444444"); bAddr.setLineHeight(1.35);
        bAddr.setZIndex(z++); el.add(bAddr);

        TemplateElement docType = new TemplateElement();
        docType.setId(uid()); docType.setType(ElementType.TEXT); docType.setX(100); docType.setY(5); docType.setW(42); docType.setH(6);
        docType.setText("{{doc_type}}"); docType.setFontSize(11); docType.setFontWeight(700); docType.setAlign("right");
        docType.setZIndex(z++); el.add(docType);

        TemplateElement line1 = new TemplateElement();
        line1.setId(uid()); line1.setType(ElementType.LINE); line1.setX(6); line1.setY(27.5); line1.setW(136); line1.setH(0.5);
        line1.setBorderWidth(0.6); line1.setZIndex(z++); el.add(line1);

        TemplateElement byrName = new TemplateElement();
        byrName.setId(uid()); byrName.setType(ElementType.TEXT); byrName.setX(6); byrName.setY(35); byrName.setW(76); byrName.setH(5);
        byrName.setText("{{buyer_name}}"); byrName.setFontSize(9.5); byrName.setFontWeight(700); byrName.setZIndex(z++); el.add(byrName);

        TemplateElement byrAddr = new TemplateElement();
        byrAddr.setId(uid()); byrAddr.setType(ElementType.TEXT); byrAddr.setX(6); byrAddr.setY(40.5); byrAddr.setW(76); byrAddr.setH(8);
        byrAddr.setText("{{buyer_address}}"); byrAddr.setFontSize(7); byrAddr.setColor("#444444"); byrAddr.setZIndex(z++); el.add(byrAddr);

        addInvRow(el, z, 88, 30.5, "No:", "{{invoice_no}}", true); z += 2;
        addInvRow(el, z, 88, 35.5, "Date:", "{{invoice_date}}", false); z += 2;

        TemplateElement table = new TemplateElement();
        table.setId(uid()); table.setType(ElementType.TABLE); table.setX(6); table.setY(56); table.setW(136); table.setH(20);
        List<TableColumn> c = new ArrayList<>();
        c.add(new TableColumn("sr", "#", 7, "center"));
        c.add(new TableColumn("desc", "Description", 43, "left"));
        c.add(new TableColumn("qty", "Qty", 11, "right"));
        c.add(new TableColumn("rate", "Rate", 15, "right"));
        c.add(new TableColumn("gst", "GST%", 10, "right"));
        c.add(new TableColumn("amount", "Amount", 14, "right"));
        table.setColumns(c); table.setHeaderBg("#efe9db"); table.setHeaderColor("#1a1a1a");
        table.setRowHeight(6); table.setFontSize(7.5); table.setBorderStyle("grid"); table.setShowZebra(true);
        table.setZIndex(z++); el.add(table);

        addTotalRow(el, z, 84, 130, "Subtotal", "{{subtotal}}", false); z += 2;
        addTotalRow(el, z, 84, 135.5, "CGST", "{{cgst}}", false); z += 2;
        addTotalRow(el, z, 84, 141, "SGST", "{{sgst}}", false); z += 2;

        TemplateElement rTot = new TemplateElement();
        rTot.setId(uid()); rTot.setType(ElementType.RECT); rTot.setX(82); rTot.setY(147); rTot.setW(60); rTot.setH(8);
        rTot.setBg("#1c1c1c"); rTot.setZIndex(z++); el.add(rTot);

        TemplateElement totLbl = new TemplateElement();
        totLbl.setId(uid()); totLbl.setType(ElementType.TEXT); totLbl.setX(84); totLbl.setY(147); totLbl.setW(28); totLbl.setH(8);
        totLbl.setText("TOTAL"); totLbl.setFontSize(8); totLbl.setFontWeight(700); totLbl.setColor("#f4f1ea"); totLbl.setVAlign("middle");
        totLbl.setZIndex(z++); el.add(totLbl);

        TemplateElement totVal = new TemplateElement();
        totVal.setId(uid()); totVal.setType(ElementType.TEXT); totVal.setX(112); totVal.setY(147); totVal.setW(28); totVal.setH(8);
        totVal.setText("{{grand_total}}"); totVal.setFontSize(9); totVal.setFontWeight(700); totVal.setColor("#f4f1ea");
        totVal.setAlign("right"); totVal.setVAlign("middle"); totVal.setZIndex(z++); el.add(totVal);

        TemplateElement foot = new TemplateElement();
        foot.setId(uid()); foot.setType(ElementType.TEXT); foot.setX(0); foot.setY(201); foot.setW(148); foot.setH(5);
        foot.setText("{{copy_label}} · Page {{page_no}}/{{page_count}}"); foot.setFontSize(6); foot.setAlign("center");
        foot.setColor("#999999"); foot.setRepeatOnPages(true); foot.setZIndex(z++); el.add(foot);

        return t;
    }

    public static Template buildMinimal() {
        String now = Instant.now().toString();
        Template t = new Template("tpl_minimal", "Minimal Invoice",
                new PageConfig(PageSizeName.A4, 210, 297, "portrait", new PageConfig.Margins(8, 8, 8, 8)),
                new ArrayList<>());
        t.setCreatedAt(now); t.setUpdatedAt(now);
        List<TemplateElement> el = t.getElements();
        int z = 0;

        TemplateElement bName = new TemplateElement();
        bName.setId(uid()); bName.setType(ElementType.TEXT); bName.setX(8); bName.setY(28); bName.setW(100); bName.setH(8);
        bName.setText("{{business_name}}"); bName.setFontSize(15); bName.setFontWeight(700); bName.setZIndex(z++); el.add(bName);

        TemplateElement bAddr = new TemplateElement();
        bAddr.setId(uid()); bAddr.setType(ElementType.TEXT); bAddr.setX(8); bAddr.setY(36.5); bAddr.setW(100); bAddr.setH(10);
        bAddr.setText("{{business_address}}"); bAddr.setFontSize(8); bAddr.setColor("#555555"); bAddr.setLineHeight(1.4);
        bAddr.setZIndex(z++); el.add(bAddr);

        TemplateElement docType = new TemplateElement();
        docType.setId(uid()); docType.setType(ElementType.TEXT); docType.setX(130); docType.setY(28); docType.setW(72); docType.setH(7);
        docType.setText("{{doc_type}}"); docType.setFontSize(14); docType.setAlign("right"); docType.setLetterSpacing(3);
        docType.setZIndex(z++); el.add(docType);

        addInvRow(el, z, 130, 36.5, "Inv #", "{{invoice_no}}", true); z += 2;
        addInvRow(el, z, 130, 42, "Date", "{{invoice_date}}", false); z += 2;

        TemplateElement line1 = new TemplateElement();
        line1.setId(uid()); line1.setType(ElementType.LINE); line1.setX(8); line1.setY(58); line1.setW(194); line1.setH(0.35);
        line1.setBorderWidth(0.35); line1.setBorderColor("#222222"); line1.setZIndex(z++); el.add(line1);

        TemplateElement byrName = new TemplateElement();
        byrName.setId(uid()); byrName.setType(ElementType.TEXT); byrName.setX(8); byrName.setY(68); byrName.setW(100); byrName.setH(6);
        byrName.setText("{{buyer_name}}"); byrName.setFontSize(11); byrName.setFontWeight(600); byrName.setZIndex(z++); el.add(byrName);

        TemplateElement table = new TemplateElement();
        table.setId(uid()); table.setType(ElementType.TABLE); table.setX(8); table.setY(96); table.setW(194); table.setH(20);
        table.setColumns(defaultItemColumns()); table.setHeaderBg("#ffffff"); table.setHeaderColor("#1a1a1a");
        table.setRowHeight(7); table.setFontSize(8.5); table.setBorderStyle("rows"); table.setShowZebra(false);
        table.setZIndex(z++); el.add(table);

        addTotalRow(el, z, 130, 190, "Subtotal", "{{subtotal}}", false); z += 2;
        addTotalRow(el, z, 130, 196, "CGST", "{{cgst}}", false); z += 2;
        addTotalRow(el, z, 130, 202, "SGST", "{{sgst}}", false); z += 2;

        addTotalRow(el, z, 130, 212, "TOTAL", "{{grand_total}}", true); z += 2;

        return t;
    }

    public static Template buildThermal80() {
        String now = Instant.now().toString();
        PageConfig p = new PageConfig(PageSizeName.THERMAL_80, 80, 132, "portrait", new PageConfig.Margins(3, 2, 3, 2));
        p.setAutoHeight(true);
        Template t = new Template("tpl_thermal80", "Thermal POS Receipt (80mm)", p, new ArrayList<>());
        t.setCreatedAt(now); t.setUpdatedAt(now);
        List<TemplateElement> el = t.getElements();
        int z = 0;

        TemplateElement bName = new TemplateElement();
        bName.setId(uid()); bName.setType(ElementType.TEXT); bName.setX(4); bName.setY(4); bName.setW(72); bName.setH(7);
        bName.setText("{{business_name}}"); bName.setFontSize(12); bName.setFontWeight(700); bName.setAlign("center");
        bName.setZIndex(z++); el.add(bName);

        TemplateElement bAddr = new TemplateElement();
        bAddr.setId(uid()); bAddr.setType(ElementType.TEXT); bAddr.setX(4); bAddr.setY(11.5); bAddr.setW(72); bAddr.setH(8);
        bAddr.setText("{{business_address}}"); bAddr.setFontSize(6.2); bAddr.setAlign("center"); bAddr.setColor("#444444");
        bAddr.setZIndex(z++); el.add(bAddr);

        TemplateElement bGst = new TemplateElement();
        bGst.setId(uid()); bGst.setType(ElementType.TEXT); bGst.setX(4); bGst.setY(20); bGst.setW(72); bGst.setH(4);
        bGst.setText("GSTIN: {{business_gst}} · Ph: {{business_phone}}"); bGst.setFontSize(6.2); bGst.setAlign("center");
        bGst.setZIndex(z++); el.add(bGst);

        TemplateElement line1 = new TemplateElement();
        line1.setId(uid()); line1.setType(ElementType.LINE); line1.setX(4); line1.setY(25.5); line1.setW(72); line1.setH(0.3);
        line1.setBorderWidth(0.3); line1.setZIndex(z++); el.add(line1);

        TemplateElement invNo = new TemplateElement();
        invNo.setId(uid()); invNo.setType(ElementType.TEXT); invNo.setX(4); invNo.setY(27.5); invNo.setW(36); invNo.setH(4);
        invNo.setText("Invoice: {{invoice_no}}"); invNo.setFontSize(7); invNo.setFontWeight(700);
        invNo.setZIndex(z++); el.add(invNo);

        TemplateElement invDate = new TemplateElement();
        invDate.setId(uid()); invDate.setType(ElementType.TEXT); invDate.setX(40); invDate.setY(27.5); invDate.setW(36); invDate.setH(4);
        invDate.setText("{{invoice_date}}"); invDate.setFontSize(7); invDate.setAlign("right");
        invDate.setZIndex(z++); el.add(invDate);

        TemplateElement byr = new TemplateElement();
        byr.setId(uid()); byr.setType(ElementType.TEXT); byr.setX(4); byr.setY(32); byr.setW(40); byr.setH(4);
        byr.setText("{{buyer_name}}"); byr.setFontSize(7.5); byr.setFontWeight(700); byr.setZIndex(z++); el.add(byr);

        TemplateElement table = new TemplateElement();
        table.setId(uid()); table.setType(ElementType.TABLE); table.setX(2); table.setY(41); table.setW(76); table.setH(25.4);
        List<TableColumn> c = new ArrayList<>();
        c.add(new TableColumn("sr", "#", 6, "center"));
        c.add(new TableColumn("desc", "Item", 46, "left"));
        c.add(new TableColumn("qty", "Qty", 12, "right"));
        c.add(new TableColumn("rate", "Rate", 16, "right"));
        c.add(new TableColumn("amount", "Amt", 20, "right"));
        table.setColumns(c); table.setHeaderBg("#ffffff"); table.setHeaderColor("#1a1a1a");
        table.setRowHeight(4.8); table.setFontSize(6.4); table.setBorderStyle("rows"); table.setShowZebra(false);
        table.setZIndex(z++); el.add(table);

        TemplateElement totLbl = new TemplateElement();
        totLbl.setId(uid()); totLbl.setType(ElementType.TEXT); totLbl.setX(4); tabShift(totLbl, 80); totLbl.setW(30); totLbl.setH(6);
        totLbl.setText("TOTAL"); totLbl.setFontSize(10); totLbl.setFontWeight(700); totLbl.setVAlign("middle");
        totLbl.setZIndex(z++); el.add(totLbl);

        TemplateElement totVal = new TemplateElement();
        totVal.setId(uid()); totVal.setType(ElementType.TEXT); totVal.setX(34); tabShift(totVal, 80); totVal.setW(44); totVal.setH(6);
        totVal.setText("{{grand_total}}"); totVal.setFontSize(11); totVal.setFontWeight(700); totVal.setAlign("right");
        totVal.setVAlign("middle"); totVal.setZIndex(z++); el.add(totVal);

        // QR code
        TemplateElement qr = new TemplateElement();
        qr.setId(uid()); qr.setType(ElementType.QRCODE); qr.setX(29); tabShift(qr, 91.5); qr.setW(22); qr.setH(22);
        qr.setQrSource("upi_amount"); qr.setQrColor("#111111"); qr.setZIndex(z++); el.add(qr);

        TemplateElement qrText = new TemplateElement();
        qrText.setId(uid()); qrText.setType(ElementType.TEXT); qrText.setX(2); tabShift(qrText, 114); qrText.setW(76); qrText.setH(3.6);
        qrText.setText("Scan to pay via UPI — {{bank_upi}}"); qrText.setFontSize(6.2); qrText.setAlign("center");
        qrText.setZIndex(z++); el.add(qrText);

        TemplateElement bar = new TemplateElement();
        bar.setId(uid()); bar.setType(ElementType.BARCODE); bar.setX(20); tabShift(bar, 120); bar.setW(40); bar.setH(10);
        bar.setBarcodeData("{{invoice_no}}"); bar.setBarcodeColor("#111111"); bar.setBarcodeShowText(true);
        bar.setZIndex(z++); el.add(bar);

        return t;
    }

    public static Template buildThermal58() {
        String now = Instant.now().toString();
        PageConfig p = new PageConfig(PageSizeName.THERMAL_58, 58, 132, "portrait", new PageConfig.Margins(2, 1, 2, 1));
        p.setAutoHeight(true);
        Template t = new Template("tpl_thermal58", "Thermal Mini Receipt (58mm)", p, new ArrayList<>());
        t.setCreatedAt(now); t.setUpdatedAt(now);
        List<TemplateElement> el = t.getElements();
        int z = 0;

        TemplateElement bName = new TemplateElement();
        bName.setId(uid()); bName.setType(ElementType.TEXT); bName.setX(2); bName.setY(3); bName.setW(54); bName.setH(6);
        bName.setText("{{business_name}}"); bName.setFontSize(9.5); bName.setFontWeight(700); bName.setAlign("center");
        bName.setZIndex(z++); el.add(bName);

        TemplateElement bAddr = new TemplateElement();
        bAddr.setId(uid()); bAddr.setType(ElementType.TEXT); bAddr.setX(2); bAddr.setY(9.5); bAddr.setW(54); bAddr.setH(7);
        bAddr.setText("{{business_address}}"); bAddr.setFontSize(5.2); bAddr.setAlign("center"); bAddr.setColor("#444444");
        bAddr.setZIndex(z++); el.add(bAddr);

        TemplateElement invNo = new TemplateElement();
        invNo.setId(uid()); invNo.setType(ElementType.TEXT); invNo.setX(2); invNo.setY(27); invNo.setW(30); invNo.setH(3.6);
        invNo.setText("Inv: {{invoice_no}}"); invNo.setFontSize(6); invNo.setFontWeight(700);
        invNo.setZIndex(z++); el.add(invNo);

        TemplateElement table = new TemplateElement();
        table.setId(uid()); table.setType(ElementType.TABLE); table.setX(1); table.setY(43); table.setW(56); table.setH(23);
        List<TableColumn> c = new ArrayList<>();
        c.add(new TableColumn("sr", "#", 7, "center"));
        c.add(new TableColumn("desc", "Item", 55, "left"));
        c.add(new TableColumn("qty", "Qty", 13, "right"));
        c.add(new TableColumn("amount", "Amt", 25, "right"));
        table.setColumns(c); table.setHeaderBg("#ffffff"); table.setHeaderColor("#1a1a1a");
        table.setRowHeight(4.4); table.setFontSize(5.4); table.setBorderStyle("rows"); table.setShowZebra(false);
        table.setZIndex(z++); el.add(table);

        TemplateElement totLbl = new TemplateElement();
        totLbl.setId(uid()); totLbl.setType(ElementType.TEXT); totLbl.setX(2); tabShift(totLbl, 78); totLbl.setW(22); totLbl.setH(5.5);
        totLbl.setText("TOTAL"); totLbl.setFontSize(8); totLbl.setFontWeight(700); totLbl.setVAlign("middle");
        totLbl.setZIndex(z++); el.add(totLbl);

        TemplateElement totVal = new TemplateElement();
        totVal.setId(uid()); totVal.setType(ElementType.TEXT); totVal.setX(24); tabShift(totVal, 78); totVal.setW(32); totVal.setH(5.5);
        totVal.setText("{{grand_total}}"); totVal.setFontSize(9); totVal.setFontWeight(700); totVal.setAlign("right");
        totVal.setVAlign("middle"); totVal.setZIndex(z++); el.add(totVal);

        TemplateElement qr = new TemplateElement();
        qr.setId(uid()); qr.setType(ElementType.QRCODE); qr.setX(20); tabShift(qr, 88.5); qr.setW(18); qr.setH(18);
        qr.setQrSource("upi_amount"); qr.setQrColor("#111111"); qr.setZIndex(z++); el.add(qr);

        return t;
    }

    private static void tabShift(TemplateElement el, double y) {
        el.setY(y);
    }

    private static void addInvRow(List<TemplateElement> el, int z, double x, double y, String label, String val, boolean boldVal) {
        TemplateElement l = new TemplateElement();
        l.setId(uid()); l.setType(ElementType.TEXT); l.setX(x); l.setY(y); l.setW(40); l.setH(5);
        l.setText(label); l.setFontSize(8.5); l.setColor("#666666"); l.setZIndex(z); el.add(l);

        TemplateElement v = new TemplateElement();
        v.setId(uid()); v.setType(ElementType.TEXT); v.setX(x + 32); v.setY(y); v.setW(40); v.setH(5);
        v.setText(val); v.setFontSize(8.5); if (boldVal) v.setFontWeight(700); v.setAlign("right");
        v.setColor("#1a1a1a"); v.setZIndex(z + 1); el.add(v);
    }

    private static void addTotalRow(List<TemplateElement> el, int z, double x, double y, String label, String val, boolean bold) {
        TemplateElement l = new TemplateElement();
        l.setId(uid()); l.setType(ElementType.TEXT); l.setX(x); l.setY(y); l.setW(40); l.setH(5);
        l.setText(label); l.setFontSize(9); if (bold) l.setFontWeight(700); l.setColor("#555555"); l.setZIndex(z); el.add(l);

        TemplateElement v = new TemplateElement();
        v.setId(uid()); v.setType(ElementType.TEXT); v.setX(x + 32); v.setY(y); v.setW(42); v.setH(5);
        v.setText(val); v.setFontSize(9.5); if (bold) v.setFontWeight(700); v.setAlign("right");
        v.setZIndex(z + 1); el.add(v);
    }

    private static String uid() {
        return "el_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
