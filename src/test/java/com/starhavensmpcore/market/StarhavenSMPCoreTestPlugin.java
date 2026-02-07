package com.starhavensmpcore.market;

import com.starhavensmpcore.core.StarhavenSMPCore;
import com.starhavensmpcore.market.db.DatabaseManager;

public class StarhavenSMPCoreTestPlugin extends StarhavenSMPCore {
    private DatabaseManager testDatabaseManager;

    @Override
    public void onEnable() {
        // Avoid production initialization during tests.
    }

    @Override
    public void onDisable() {
        // No-op for tests.
    }

    public void setDatabaseManager(DatabaseManager databaseManager) {
        this.testDatabaseManager = databaseManager;
    }

    @Override
    public DatabaseManager getDatabaseManager() {
        return testDatabaseManager;
    }
}
