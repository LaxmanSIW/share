package com.invoicestudio.service;

import com.invoicestudio.db.*;
import com.invoicestudio.model.Buyer;
import com.invoicestudio.model.ItemRecord;
import com.invoicestudio.model.UserSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class AuthAndDataPartitioningTest {

    private static DatabaseManager db;
    private static AuthDao authDao;
    private static BuyerDao buyerDao;
    private static ItemDao itemDao;
    private static final String TEST_AUTH_DB = "test_auth_partition.db";

    @BeforeAll
    static void setUp() {
        new File(TEST_AUTH_DB).delete();
        db = DatabaseManager.initCustom("jdbc:sqlite:" + TEST_AUTH_DB);
        authDao = new AuthDao(db);
        buyerDao = new BuyerDao(db);
        itemDao = new ItemDao(db);
    }

    @AfterAll
    static void tearDown() {
        AuthSessionManager.clear();
        new File(TEST_AUTH_DB).delete();
    }

    @Test
    void testUserSessionModel() {
        long futureTime = System.currentTimeMillis() + 60_000L;
        UserSession session = new UserSession("uid_123", "alice@example.com", "Alice Smith", "tok_id", "tok_refresh", futureTime, true);

        assertEquals("uid_123", session.getUserId());
        assertEquals("alice@example.com", session.getEmail());
        assertEquals("Alice Smith", session.getDisplayName());
        assertEquals("tok_id", session.getIdToken());
        assertEquals("tok_refresh", session.getRefreshToken());
        assertTrue(session.isRememberMe());
        assertFalse(session.isExpired());

        // Test expired condition
        session.setExpiresAtMillis(System.currentTimeMillis() - 1000L);
        assertTrue(session.isExpired());
    }

    @Test
    void testAuthDaoSessionPersistence() {
        authDao.clearSession();
        assertNull(authDao.getActiveSession());

        UserSession session = new UserSession("uid_test_1", "bob@example.com", "Bob Jones", "id_tok_1", "ref_tok_1", System.currentTimeMillis() + 3600_000L, true);
        authDao.saveSession(session);

        UserSession loaded = authDao.getActiveSession();
        assertNotNull(loaded);
        assertEquals("uid_test_1", loaded.getUserId());
        assertEquals("bob@example.com", loaded.getEmail());
        assertEquals("Bob Jones", loaded.getDisplayName());
        assertEquals("id_tok_1", loaded.getIdToken());
        assertTrue(loaded.isRememberMe());

        // Update tokens
        authDao.updateTokens("uid_test_1", "new_id_tok", "new_ref_tok", System.currentTimeMillis() + 7200_000L);
        UserSession updated = authDao.getActiveSession();
        assertEquals("new_id_tok", updated.getIdToken());
        assertEquals("new_ref_tok", updated.getRefreshToken());

        // Clear session
        authDao.clearSession();
        assertNull(authDao.getActiveSession());
    }

    @Test
    void testAuthSessionManagerListeners() {
        AtomicBoolean listenerFired = new AtomicBoolean(false);
        var listener = (java.util.function.Consumer<UserSession>) s -> listenerFired.set(true);

        AuthSessionManager.addSessionChangeListener(listener);
        try {
            UserSession session = new UserSession("uid_c", "charlie@example.com", "Charlie", "tok", "ref", System.currentTimeMillis() + 3600_000L, true);
            AuthSessionManager.setActiveSession(session);

            assertTrue(listenerFired.get());
            assertEquals("uid_c", AuthSessionManager.getCurrentUserId());
            assertEquals("charlie@example.com", AuthSessionManager.getCurrentUserEmail());
            assertEquals("Charlie", AuthSessionManager.getCurrentUserDisplayName());
            assertTrue(AuthSessionManager.isLoggedIn());
        } finally {
            AuthSessionManager.removeSessionChangeListener(listener);
            AuthSessionManager.clear();
        }
    }

    @Test
    void testMultiUserDataPartitioning() {
        // Step 1: User Alpha logs in and creates buyer & item
        UserSession userAlpha = new UserSession("user_alpha", "alpha@company.com", "Alpha User", "tok_a", "ref_a", System.currentTimeMillis() + 3600_000L, true);
        AuthSessionManager.setActiveSession(userAlpha);

        Buyer buyerAlpha = new Buyer();
        buyerAlpha.setId("byr_alpha_01");
        buyerAlpha.setName("Alpha Corp");
        buyerDao.saveBuyer(buyerAlpha);

        ItemRecord itemAlpha = new ItemRecord("itm_alpha_01", "Alpha Custom Widget", "9901", "PCS", 100.0, 18.0);
        itemDao.save(itemAlpha);

        // Verify Alpha sees their data
        List<Buyer> alphaBuyers = buyerDao.getAllBuyers();
        assertTrue(alphaBuyers.stream().anyMatch(b -> "Alpha Corp".equals(b.getName())));

        List<ItemRecord> alphaItems = itemDao.getAllItems();
        assertTrue(alphaItems.stream().anyMatch(i -> "Alpha Custom Widget".equals(i.getName())));

        // Step 2: User Beta logs in and creates buyer & item
        UserSession userBeta = new UserSession("user_beta", "beta@company.com", "Beta User", "tok_b", "ref_b", System.currentTimeMillis() + 3600_000L, true);
        AuthSessionManager.setActiveSession(userBeta);

        Buyer buyerBeta = new Buyer();
        buyerBeta.setId("byr_beta_01");
        buyerBeta.setName("Beta Logistics");
        buyerDao.saveBuyer(buyerBeta);

        ItemRecord itemBeta = new ItemRecord("itm_beta_01", "Beta Heavy Machinery", "9902", "SET", 500.0, 18.0);
        itemDao.save(itemBeta);

        // Verify Beta sees Beta's data but NOT Alpha's data
        List<Buyer> betaBuyers = buyerDao.getAllBuyers();
        assertTrue(betaBuyers.stream().anyMatch(b -> "Beta Logistics".equals(b.getName())));
        assertFalse(betaBuyers.stream().anyMatch(b -> "Alpha Corp".equals(b.getName())),
                "Beta user should not see Alpha user's private buyers");

        List<ItemRecord> betaItems = itemDao.getAllItems();
        assertTrue(betaItems.stream().anyMatch(i -> "Beta Heavy Machinery".equals(i.getName())));
        assertFalse(betaItems.stream().anyMatch(i -> "Alpha Custom Widget".equals(i.getName())),
                "Beta user should not see Alpha user's private items");

        // Step 3: Switch back to Alpha, verify Beta's data is not visible to Alpha
        AuthSessionManager.setActiveSession(userAlpha);
        List<Buyer> alphaRecheck = buyerDao.getAllBuyers();
        assertTrue(alphaRecheck.stream().anyMatch(b -> "Alpha Corp".equals(b.getName())));
        assertFalse(alphaRecheck.stream().anyMatch(b -> "Beta Logistics".equals(b.getName())),
                "Alpha user should not see Beta user's private buyers");

        // Clean up
        AuthSessionManager.clear();
    }
}
