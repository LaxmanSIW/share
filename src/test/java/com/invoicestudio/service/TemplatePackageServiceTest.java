package com.invoicestudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.invoicestudio.db.DatabaseManager;
import com.invoicestudio.db.TemplateDao;
import com.invoicestudio.model.PageConfig;
import com.invoicestudio.model.PageSizeName;
import com.invoicestudio.model.Template;
import com.invoicestudio.model.TemplateElement;
import com.invoicestudio.model.UserSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the template download / upload feature.
 *
 * They prove the two promises the UI makes:
 *  1. one OR several templates round-trip through a portable JSON file with
 *     page config, elements and label mode intact;
 *  2. an upload never overwrites work that already exists (fresh id + a
 *     renamed copy instead of a silent replace).
 */
class TemplatePackageServiceTest {

    private static final String TEST_DB = "test_template_package.db";
    private static DatabaseManager db;
    private static TemplateDao dao;
    private static TemplatePackageService service;

    @BeforeAll
    static void setUp() {
        new File(TEST_DB).delete();
        AuthSessionManager.setActiveSession(new UserSession(
                "user_tpl_pkg", "tpl@invoicestudio.test", "Template Tester",
                "id_tok", "ref_tok", System.currentTimeMillis() + 3_600_000L, true));
        db = DatabaseManager.initCustom("jdbc:sqlite:" + TEST_DB);
        dao = new TemplateDao(db);
        service = new TemplatePackageService(db);
    }

    @AfterAll
    static void tearDown() {
        AuthSessionManager.clear();
        new File(TEST_DB).delete();
    }

    // ---------------------------------------------------------------- helpers

    private static Template billTemplate(String name) {
        Template t = new Template();
        t.setId("tpl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        t.setName(name);

        PageConfig page = new PageConfig();
        page.setSizeName(PageSizeName.A4);
        t.setPage(page);

        TemplateElement band = new TemplateElement();
        band.setType(com.invoicestudio.model.ElementType.RECT);
        band.setName("Header Band");
        band.setX(10); band.setY(12); band.setW(190); band.setH(18);
        band.setBg("#D9A13B");
        t.getElements().add(band);
        return t;
    }

    private static Template labelTemplate(String name) {
        Template t = new Template();
        t.setId("tpl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        t.setName(name);
        t.setMode("label");
        t.labelOrNew().setLabelWidth(50);
        t.labelOrNew().setLabelHeight(25);
        return t;
    }

    private static File tempFile() throws IOException {
        File f = File.createTempFile("istemplates_test", ".json");
        f.deleteOnExit();
        return f;
    }

    // ------------------------------------------------------------- export side

    @Test
    void exportRejectsEmptySelection() {
        assertThrows(IOException.class, () -> service.exportTemplates(List.of(), tempFile()));
    }

    @Test
    void exportSingleTemplateWritesRecognisablePackage() throws Exception {
        Template t = billTemplate("Round Trip Single");
        dao.saveTemplate(t);

        File out = tempFile();
        int written = service.exportTemplates(List.of(t), out);
        assertEquals(1, written);

        ObjectNode root = (ObjectNode) new ObjectMapper().readTree(out);
        assertEquals(TemplatePackageService.FILE_KIND, root.get("kind").asText());
        ArrayNode arr = (ArrayNode) root.get("templates");
        assertEquals(1, arr.size());
        assertEquals("Round Trip Single", arr.get(0).get("name").asText());
        assertEquals("A4", arr.get(0).get("page").get("sizeName").asText());
        assertEquals(1, arr.get(0).get("elements").size());
    }

    @Test
    void exportMultipleTemplatesWritesEveryOne() throws Exception {
        Template a = billTemplate("Multi A");
        Template b = labelTemplate("Multi B");
        dao.saveTemplate(a);
        dao.saveTemplate(b);

        File out = tempFile();
        assertEquals(2, service.exportTemplates(List.of(a, b), out));

        List<Template> reloaded = service.readTemplates(out);
        assertEquals(2, reloaded.size());
        assertTrue(reloaded.stream().anyMatch(t -> "Multi B".equals(t.getName()) && t.isLabelMode()),
                "label mode must survive the round trip");
    }

    @Test
    void suggestedFileNameUsesTemplateNameForOneAndCountForMany() {
        assertEquals("Thermal POS 80mm.istemplates.json",
                TemplatePackageService.suggestedFileName(List.of(billTemplate("Thermal POS 80mm"))));
        // Characters a file system rejects are replaced, never emitted raw.
        String sanitized = TemplatePackageService.suggestedFileName(List.of(billTemplate("A/B: C?")));
        assertFalse(sanitized.contains("/"));
        assertFalse(sanitized.contains(":"));
        assertTrue(TemplatePackageService.suggestedFileName(List.of(billTemplate("a"), billTemplate("b")))
                .startsWith("InvoiceStudio_templates_2"));
        assertTrue(TemplatePackageService.suggestedFileName(List.of()).startsWith("InvoiceStudio_templates_0"));
    }

    // ------------------------------------------------------------- import side

    @Test
    void importRoundTripsIntoSameAccountWithoutOverwriting() throws Exception {
        Template original = billTemplate("Shared Receipt");
        dao.saveTemplate(original);

        File out = tempFile();
        service.exportTemplates(List.of(original), out);

        TemplatePackageService.ImportResult result = service.importTemplates(out);

        assertEquals(1, result.imported);
        assertEquals(1, result.renamed, "a name clash must be reported to the user");

        List<Template> all = dao.getAllTemplates();
        long sameName = all.stream().filter(t -> t.getName().equals("Shared Receipt")).count();
        assertEquals(1, sameName, "the existing template must be left untouched");
        assertTrue(all.stream().anyMatch(t -> t.getName().equals("Shared Receipt (imported)")),
                "the imported copy must get a distinct name");
        assertTrue(all.stream().map(Template::getId).distinct().count() == all.size(),
                "ids must stay unique so the copy does not overwrite the original");
    }

    @Test
    void importTwiceCreatesNumberedCopies() throws Exception {
        Template src = billTemplate("Numbered Copy");
        dao.saveTemplate(src); // the account already owns a template with this name
        File out = tempFile();
        service.exportTemplates(List.of(src), out);

        service.importTemplates(out);
        service.importTemplates(out);

        List<String> names = dao.getAllTemplates().stream().map(Template::getName).toList();
        assertTrue(names.contains("Numbered Copy"), names.toString());
        assertTrue(names.contains("Numbered Copy (imported)"), names.toString());
        assertTrue(names.contains("Numbered Copy (imported 2)"), names.toString());
    }

    @Test
    void importAcceptsFullDatabaseBackupFile() throws Exception {
        // BackupRestoreService writes {"version":..,"templates":[...]} with no "kind"
        // marker — pointing the upload at a backup must still pull the templates out.
        ObjectNode root = new ObjectMapper().createObjectNode();
        root.put("version", "1.0");
        ArrayNode arr = root.putArray("templates");
        Template t = billTemplate("From Backup File");
        arr.add(new ObjectMapper().valueToTree(t));

        File file = tempFile();
        new ObjectMapper().writeValue(file, root);

        TemplatePackageService.ImportResult result = service.importTemplates(file);
        assertEquals(1, result.imported);
        assertTrue(dao.getAllTemplates().stream().anyMatch(x -> "From Backup File".equals(x.getName())));
    }

    @Test
    void importAcceptsBareArrayFile() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arr = mapper.createArrayNode();
        arr.add(mapper.valueToTree(billTemplate("Bare Array Entry")));

        File file = tempFile();
        mapper.writeValue(file, arr);

        assertEquals(1, service.importTemplates(file).imported);
    }

    @Test
    void importRejectsUnrelatedJson() throws Exception {
        File file = tempFile();
        Files.writeString(file.toPath(), "{\"kind\":\"something.else\",\"rows\":[1,2,3]}");
        IOException ex = assertThrows(IOException.class, () -> service.importTemplates(file));
        assertTrue(ex.getMessage().toLowerCase().contains("template"), ex.getMessage());
    }

    @Test
    void importRejectsMissingFile() {
        assertThrows(IOException.class, () -> service.importTemplates(new File("does_not_exist_12345.json")));
    }
}
