package com.starhavensmpcore.placeholderapi;

import com.starhavensmpcore.core.StarhavenSMPCore;
import com.starhavensmpcore.market.db.DatabaseManager;

public class MarketPlaceholderSh extends MarketPlaceholder {

    public MarketPlaceholderSh(StarhavenSMPCore plugin, DatabaseManager databaseManager) {
        super(plugin, databaseManager);
    }

    @Override
    public String getIdentifier() {
        return "sh";
    }
}
