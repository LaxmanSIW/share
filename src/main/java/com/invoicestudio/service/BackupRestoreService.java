package com.invoicestudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.invoicestudio.db.*;
import com.invoicestudio.model.*;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BackupRestoreService {

    private final DatabaseManager db;
    private final SettingsDao settingsDao;
    private final TemplateDao templateDao;
    private final BillDao billDao;
    private final BuyerDao buyerDao;
    private final ItemDao itemDao;
    private final VariableDao variableDao;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public BackupRestoreService(DatabaseManager db) {
        this.db = db;
        this.settingsDao = new SettingsDao(db);
        this.templateDao = new TemplateDao(db);
        this.billDao = new BillDao(db);
        this.buyerDao = new BuyerDao(db);
        this.itemDao = new ItemDao(db);
        this.variableDao = new VariableDao(db);
    }

    public static class BackupData {
        public String version = "1.0";
        public String exportedAt = Instant.now().toString();
        public Settings settings;
        public List<Template> templates;
        public List<Bill> bills;
        public List<Buyer> buyers;
        public List<ItemRecord> items;
        public List<VariableDef> variables;
    }

    public void exportBackup(File destination) throws IOException {
        BackupData data = new BackupData();
        data.settings = settingsDao.getSettings();
        data.templates = templateDao.getAllTemplates();
        data.bills = billDao.getAllBills();
        data.buyers = buyerDao.getAllBuyers();
        data.items = itemDao.getAllItems();
        data.variables = variableDao.getAllVariables();

        mapper.writeValue(destination, data);
    }

    public void restoreBackup(File source) throws IOException {
        BackupData data = mapper.readValue(source, BackupData.class);

        if (data.settings != null) {
            settingsDao.saveSettings(data.settings);
        }
        if (data.templates != null) {
            for (Template t : data.templates) {
                templateDao.saveTemplate(t);
            }
        }
        if (data.buyers != null) {
            for (Buyer b : data.buyers) {
                buyerDao.saveBuyer(b);
            }
        }
        if (data.items != null) {
            for (ItemRecord it : data.items) {
                itemDao.saveItem(it);
            }
        }
        if (data.bills != null) {
            for (Bill b : data.bills) {
                billDao.saveBill(b);
            }
        }
        if (data.variables != null) {
            for (VariableDef v : data.variables) {
                variableDao.saveVariable(v);
            }
        }
    }

    public static class RestoreResult {
        public int billCount;
        public int buyerCount;
        public int itemCount;
        public int templateCount;
    }

    public RestoreResult restoreFromFile(File source) throws IOException {
        BackupData data = mapper.readValue(source, BackupData.class);
        restoreBackup(source);
        RestoreResult r = new RestoreResult();
        r.billCount = data.bills != null ? data.bills.size() : 0;
        r.buyerCount = data.buyers != null ? data.buyers.size() : 0;
        r.itemCount = data.items != null ? data.items.size() : 0;
        r.templateCount = data.templates != null ? data.templates.size() : 0;
        return r;
    }

    public void exportToFile(File destination) throws IOException {
        exportBackup(destination);
    }
}
