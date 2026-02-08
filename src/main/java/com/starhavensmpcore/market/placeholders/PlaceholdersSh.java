package com.starhavensmpcore.market.placeholders;

import com.starhavensmpcore.core.StarhavenSMPCore;
import com.starhavensmpcore.market.db.DatabaseManager;

public class PlaceholdersSh extends Placeholders {

    public PlaceholdersSh(StarhavenSMPCore plugin, DatabaseManager databaseManager) {
        super(plugin, databaseManager);
    }

    @Override
    public String getIdentifier() {
        return "sh";
    }
}
